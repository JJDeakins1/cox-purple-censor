package com.coxcensor;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(CoxCensorConfig.GROUP)
public interface CoxCensorConfig extends Config
{
	String GROUP = "coxcensor";

	@ConfigItem(
		keyName = "censorChat",
		name = "Censor raid loot in chat",
		description = "Hide CoX unique names in chat until the reward chest is opened.",
		position = 1
	)
	default boolean censorChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soloOnly",
		name = "Only censor your purples",
		description = "Only hide loot when the unique belongs to you.",
		position = 2
	)
	default boolean soloOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "delayCollectionLogPopup",
		name = "Delay collection log popup until chest opens",
		description = "Hide the native collection-log popup and replay a custom one when the reward chest is opened.",
		position = 3
	)
	default boolean delayCollectionLogPopup()
	{
		return true;
	}

	@ConfigItem(
		keyName = "takeCollectionLogScreenshot",
		name = "Take collection log screenshot",
		description = "Save a screenshot of the delayed collection-log popup using RuneLite's Collection Log folder and filename.",
		position = 4
	)
	default boolean takeCollectionLogScreenshot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "openGameChatOnReveal",
		name = "Open Game chat on reveal",
		description = "When the delayed collection-log popup is shown, open chat and select the Game tab so the screenshot includes the collection-log game message.",
		position = 5
	)
	default boolean openGameChatOnReveal()
	{
		return true;
	}

	@ConfigItem(
		keyName = "darkCollectionLogPopup",
		name = "Dark collection log popup",
		description = "Use the dark grey popup instead of the default brown stone style.",
		position = 6
	)
	default boolean darkCollectionLogPopup()
	{
		return false;
	}
}
