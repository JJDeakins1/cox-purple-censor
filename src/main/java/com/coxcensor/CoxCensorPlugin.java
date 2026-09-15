package com.coxcensor;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Cox Purple Censor",
	description = "Hides CoX uniques until the reward chest is opened. New uniques get a collection-log popup and screenshot.",
	tags = {"cox", "raids", "loot", "censor", "purple", "collection log"}
)
public class CoxCensorPlugin extends Plugin
{
	private static final String SPOOFED_NOTIFICATION_TITLE = " ";
	private static final String CLOG_TEST_COMMAND = "clogtest";
	private static final String CLOG_OPEN_COMMAND = "clogopen";
	private static final int CHAT_VIEW_CLOSED = 1337;
	private static final int CHAT_TAB_GAME = 1;
	/** CS2 used by chat tabs (and the Toggle Chat plugin) to open or switch the chatbox. */
	private static final int CHATBOX_TOGGLE_SCRIPT = 175;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private CoxCensorConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CollectionLogPopupOverlay popupOverlay;

	@Inject
	private ScreenshotHelper screenshotHelper;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	@Named("developerMode")
	private boolean developerMode;

	private final List<CensoredMessage> censoredMessages = new ArrayList<>();
	private final List<CoxLootParser.LootBroadcast> trackedLoot = new ArrayList<>();
	private final Deque<PendingUnlock> pendingUnlocks = new ArrayDeque<>();

	private boolean itemReceived;
	private boolean suppressingNativeNotification;
	private String originalNotificationTitle;
	private boolean titleSpoofed;

	@Override
	protected void startUp()
	{
		popupOverlay.setPlugin(this);
		overlayManager.add(popupOverlay);
		client.refreshChat();
	}

	@Override
	protected void shutDown()
	{
		revealLoot();
		popupOverlay.reset();
		overlayManager.remove(popupOverlay);
		restoreNotificationTitle();
		suppressingNativeNotification = false;
	}

	@Provides
	CoxCensorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CoxCensorConfig.class);
	}

	void onPopupFullyOpen(PendingUnlock unlock)
	{
		screenshotHelper.captureCollectionLog(unlock.getItem());
	}

	void onPopupQueueFinished()
	{
		// Returned to idle after sequential popups complete.
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (!config.censorChat())
		{
			return;
		}

		switch (chatMessage.getType())
		{
			case FRIENDSCHATNOTIFICATION:
				CoxLootParser.parseFriendsChatLoot(chatMessage.getMessage()).ifPresent(this::maybeTrackLoot);
				break;
			case CLAN_MESSAGE:
			case CLAN_GUEST_MESSAGE:
			case CLAN_GIM_MESSAGE:
				CoxLootParser.parseClanLootBroadcast(chatMessage.getMessage()).ifPresent(this::maybeTrackLoot);
				CoxLootParser.parseClanCollectionLog(chatMessage.getMessage()).ifPresent(this::maybeTrackLoot);
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!"chatFilterCheck".equals(event.getEventName()) || !config.censorChat())
		{
			return;
		}

		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		Object[] objectStack = client.getObjectStack();
		int objectStackSize = client.getObjectStackSize();

		final int messageType = intStack[intStackSize - 2];
		final int messageId = intStack[intStackSize - 1];
		String message = (String) objectStack[objectStackSize - 1];

		ChatMessageType chatMessageType = ChatMessageType.of(messageType);
		MessageNode messageNode = client.getMessages().get(messageId);
		if (messageNode == null || message == null)
		{
			return;
		}

		if (chatMessageType == ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			CoxLootParser.parseFriendsChatLoot(message).ifPresent(this::maybeTrackLoot);
			String item = CoxLootParser.findUnique(message);
			if (item != null && isTrackedItem(item))
			{
				rememberAndReplace(messageNode, message, CoxLootParser.censorUnique(message, item));
			}
			return;
		}

		if (chatMessageType == ChatMessageType.GAMEMESSAGE
			&& (CoxLootParser.isCollectionLogGameMessage(message) || CoxLootParser.isValuableDropMessage(message)))
		{
			if (itemReceived && containsTrackedItem(message))
			{
				rememberOriginal(messageNode, message);
				intStack[intStackSize - 3] = 0;
			}
			return;
		}

		if (chatMessageType == ChatMessageType.CLAN_MESSAGE
			|| chatMessageType == ChatMessageType.CLAN_GUEST_MESSAGE
			|| chatMessageType == ChatMessageType.CLAN_GIM_MESSAGE)
		{
			CoxLootParser.parseClanLootBroadcast(message).ifPresent(broadcast ->
			{
				maybeTrackLoot(broadcast);
				if (isTrackedLoot(broadcast))
				{
					rememberAndReplace(messageNode, message, CoxLootParser.censorClanSpecialLoot(message));
				}
			});

			CoxLootParser.parseClanCollectionLog(message).ifPresent(broadcast ->
			{
				maybeTrackLoot(broadcast);
				if (isTrackedLoot(broadcast))
				{
					rememberOriginal(messageNode, message);
					intStack[intStackSize - 3] = 0;
				}
			});
		}
	}

	@Subscribe(priority = 1)
	public void onScriptPreFired(ScriptPreFired event)
	{
		switch (event.getScriptId())
		{
			case ScriptID.NOTIFICATION_START:
				maybeSuppressCollectionLogNotification();
				break;
			case ScriptID.NOTIFICATION_DELAY:
				if (suppressingNativeNotification)
				{
					spoofNotificationTitle();
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.NOTIFICATION_DELAY || !suppressingNativeNotification)
		{
			return;
		}

		restoreNotificationTitle();
		suppressingNativeNotification = false;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (suppressingNativeNotification)
		{
			hideNativeNotificationPaintedWidgets();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.RAIDS_REWARDS)
		{
			onRewardChestOpened();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.LOGGING_IN)
		{
			popupOverlay.reset();
			pendingUnlocks.clear();
			restoreNotificationTitle();
			suppressingNativeNotification = false;
			revealLoot();
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!developerMode)
		{
			return;
		}

		String command = event.getCommand().toLowerCase(Locale.ROOT);
		if (CLOG_TEST_COMMAND.equals(command))
		{
			handleClogTest(event.getArguments());
		}
		else if (CLOG_OPEN_COMMAND.equals(command))
		{
			onRewardChestOpened();
		}
	}

	private void handleClogTest(String[] arguments)
	{
		String joined = String.join(" ", arguments).trim();
		if (joined.isEmpty())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Usage: ::clogtest Twisted bow [--auto 5]", null);
			return;
		}

		Integer autoSeconds = null;
		String itemText = joined;
		int autoIndex = joined.toLowerCase(Locale.ROOT).lastIndexOf("--auto");
		if (autoIndex >= 0)
		{
			String after = joined.substring(autoIndex + "--auto".length()).trim();
			itemText = joined.substring(0, autoIndex).trim();
			try
			{
				autoSeconds = Integer.parseInt(after.split("\\s+")[0]);
			}
			catch (NumberFormatException ex)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Invalid --auto seconds. Usage: ::clogtest Twisted bow --auto 5", null);
				return;
			}
		}

		String item = CoxLootParser.findUnique(itemText);
		if (item == null)
		{
			item = itemText;
		}

		String player = client.getLocalPlayer() != null ? Text.removeTags(client.getLocalPlayer().getName()) : "You";
		itemReceived = true;
		trackedLoot.add(new CoxLootParser.LootBroadcast(player, item));
		pendingUnlocks.add(new PendingUnlock(player, item, Instant.now()));

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"Queued hidden collection-log popup for " + item + ". Use ::clogopen to simulate the chest.", null);

		if (autoSeconds != null)
		{
			int delay = Math.max(0, autoSeconds);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"Simulating chest open in " + delay + "s.", null);
			executor.schedule(() -> clientThread.invoke(this::onRewardChestOpened), delay, TimeUnit.SECONDS);
		}
	}

	private void onRewardChestOpened()
	{
		revealLoot();
		if (config.delayCollectionLogPopup() && !pendingUnlocks.isEmpty())
		{
			openGameChatForPopup();
			List<PendingUnlock> toShow = new ArrayList<>(pendingUnlocks);
			pendingUnlocks.clear();
			popupOverlay.enqueue(toShow);
		}
		else
		{
			pendingUnlocks.clear();
		}
	}

	private void openGameChatForPopup()
	{
		if (!config.openGameChatOnReveal())
		{
			return;
		}

		int view = client.getVarcIntValue(VarClientID.CHAT_VIEW);
		if (view != CHAT_TAB_GAME)
		{
			// Closed chat is 1337; any other value is the current tab. Do not invoke this
			// script when Game is already selected — that toggles the chatbox closed.
			client.runScript(CHATBOX_TOGGLE_SCRIPT, 1, CHAT_TAB_GAME);
			view = client.getVarcIntValue(VarClientID.CHAT_VIEW);
		}

		if (view != CHAT_TAB_GAME && view != CHAT_VIEW_CLOSED)
		{
			client.setVarcIntValue(VarClientID.CHAT_VIEW, CHAT_TAB_GAME);
			client.runScript(ScriptID.BUILD_CHATBOX);
		}

		client.refreshChat();
	}

	private void maybeSuppressCollectionLogNotification()
	{
		if (!itemReceived || !config.delayCollectionLogPopup())
		{
			return;
		}

		String topText = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
		String bottomText = client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN);
		if (topText == null || !topText.equalsIgnoreCase("Collection log"))
		{
			return;
		}

		String item = CoxLootParser.collectionLogItemName(bottomText);
		if (!isTrackedItem(item) && CoxLootParser.findUnique(bottomText) == null)
		{
			return;
		}

		String resolvedItem = isTrackedItem(item) ? item : CoxLootParser.findUnique(bottomText);
		if (resolvedItem == null)
		{
			return;
		}

		suppressingNativeNotification = true;
		hideNativeNotificationPaintedWidgets();

		String player = client.getLocalPlayer() != null ? Text.removeTags(client.getLocalPlayer().getName()) : "";
		pendingUnlocks.add(new PendingUnlock(player, resolvedItem, Instant.now()));
	}

	private void hideNativeNotificationPaintedWidgets()
	{
		Widget content = client.getWidget(InterfaceID.NotificationDisplay.CONTENT);
		if (content == null)
		{
			return;
		}
		hideWidgetTree(content, true);
	}

	private void hideWidgetTree(Widget widget, boolean skipSelf)
	{
		if (!skipSelf)
		{
			widget.setHidden(true);
		}

		hideChildren(widget.getStaticChildren());
		hideChildren(widget.getDynamicChildren());
		hideChildren(widget.getNestedChildren());
	}

	private void hideChildren(Widget[] children)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			hideWidgetTree(child, false);
		}
	}

	private void spoofNotificationTitle()
	{
		if (titleSpoofed)
		{
			return;
		}
		originalNotificationTitle = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
		client.setVarcStrValue(VarClientID.NOTIFICATION_TITLE, SPOOFED_NOTIFICATION_TITLE);
		titleSpoofed = true;
	}

	private void restoreNotificationTitle()
	{
		if (!titleSpoofed)
		{
			return;
		}
		if (originalNotificationTitle != null)
		{
			client.setVarcStrValue(VarClientID.NOTIFICATION_TITLE, originalNotificationTitle);
		}
		titleSpoofed = false;
		originalNotificationTitle = null;
	}

	private void revealLoot()
	{
		for (CensoredMessage censored : censoredMessages)
		{
			censored.node.setValue(censored.original);
			censored.node.setRuneLiteFormatMessage(censored.original);
		}
		clearCensorState();
		client.refreshChat();
	}

	private void clearCensorState()
	{
		censoredMessages.clear();
		trackedLoot.clear();
		itemReceived = false;
		client.refreshChat();
	}

	private void rememberAndReplace(MessageNode node, String original, String censored)
	{
		rememberOriginal(node, original);
		node.setValue(censored);
		node.setRuneLiteFormatMessage(censored);
	}

	private void rememberOriginal(MessageNode node, String original)
	{
		censoredMessages.add(new CensoredMessage(node, original));
	}

	private void maybeTrackLoot(CoxLootParser.LootBroadcast broadcast)
	{
		if (config.soloOnly() && !isLocalPlayer(broadcast.getPlayer()))
		{
			return;
		}
		if (isTrackedLoot(broadcast))
		{
			return;
		}
		itemReceived = true;
		trackedLoot.add(broadcast);
	}

	private boolean isTrackedItem(String item)
	{
		if (item == null)
		{
			return false;
		}
		for (CoxLootParser.LootBroadcast loot : trackedLoot)
		{
			if (item.equalsIgnoreCase(loot.getItem()) || item.contains(loot.getItem()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean containsTrackedItem(String message)
	{
		String stripped = CoxLootParser.removeTags(message);
		for (CoxLootParser.LootBroadcast loot : trackedLoot)
		{
			if (stripped.contains(loot.getItem()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isTrackedLoot(CoxLootParser.LootBroadcast broadcast)
	{
		for (CoxLootParser.LootBroadcast loot : trackedLoot)
		{
			if (loot.getItem().equals(broadcast.getItem()) && loot.getPlayer().equalsIgnoreCase(broadcast.getPlayer()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isLocalPlayer(String name)
	{
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
		{
			return false;
		}
		return Text.removeTags(client.getLocalPlayer().getName()).equalsIgnoreCase(Text.removeTags(name));
	}

	private static final class CensoredMessage
	{
		private final MessageNode node;
		private final String original;

		private CensoredMessage(MessageNode node, String original)
		{
			this.node = node;
			this.original = original;
		}
	}
}
