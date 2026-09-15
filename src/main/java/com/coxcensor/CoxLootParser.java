package com.coxcensor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * Parses CoX unique broadcasts without relying on fixed character offsets.
 */
public final class CoxLootParser
{
	static final String[] UNIQUES = {
		"Dexterous prayer scroll",
		"Arcane prayer scroll",
		"Twisted buckler",
		"Dragon hunter crossbow",
		"Dinh's bulwark",
		"Ancestral hat",
		"Ancestral robe top",
		"Ancestral robe bottom",
		"Dragon claws",
		"Elder maul",
		"Kodai insignia",
		"Twisted bow"
	};

	private static final String[] UNIQUES_BY_LENGTH;

	static
	{
		UNIQUES_BY_LENGTH = Arrays.copyOf(UNIQUES, UNIQUES.length);
		Arrays.sort(UNIQUES_BY_LENGTH, Comparator.comparingInt(String::length).reversed());
	}

	private static final Pattern CLAN_SPECIAL_LOOT = Pattern.compile(
		"^(.+?) received special loot from a raid: (.+)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern CLAN_DROP = Pattern.compile(
		"^(.+?) received a drop: (.+)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern CLAN_COLLECTION_LOG = Pattern.compile(
		"^(.+?) received a new collection log item: (.+)$",
		Pattern.CASE_INSENSITIVE
	);

	private CoxLootParser()
	{
	}

	public static String removeTags(String message)
	{
		if (message == null)
		{
			return "";
		}
		return message.replaceAll("<[^>]*>", "");
	}

	public static String findUnique(String message)
	{
		String stripped = removeTags(message);
		for (String item : UNIQUES_BY_LENGTH)
		{
			if (stripped.contains(item))
			{
				return item;
			}
		}
		return null;
	}

	public static Optional<LootBroadcast> parseFriendsChatLoot(String message)
	{
		String item = findUnique(message);
		if (item == null)
		{
			return Optional.empty();
		}

		String stripped = removeTags(message).trim();
		String marker = " - " + item;
		int index = stripped.indexOf(marker);
		if (index <= 0)
		{
			return Optional.empty();
		}

		String player = stripped.substring(0, index).trim();
		if (player.isEmpty())
		{
			return Optional.empty();
		}
		return Optional.of(new LootBroadcast(player, item));
	}

	public static Optional<LootBroadcast> parseClanLootBroadcast(String message)
	{
		Optional<LootBroadcast> specialLoot = parseClanSpecialLoot(message);
		if (specialLoot.isPresent())
		{
			return specialLoot;
		}
		return parseClanDrop(message);
	}

	public static Optional<LootBroadcast> parseClanSpecialLoot(String message)
	{
		return parseClanBroadcast(message, CLAN_SPECIAL_LOOT, true);
	}

	public static Optional<LootBroadcast> parseClanDrop(String message)
	{
		return parseClanBroadcast(message, CLAN_DROP, false);
	}

	private static Optional<LootBroadcast> parseClanBroadcast(String message, Pattern pattern, boolean stripTrailingValue)
	{
		String stripped = removeTags(message).trim();
		if (stripTrailingValue)
		{
			stripped = stripTrailingValue(stripped);
		}
		Matcher matcher = pattern.matcher(stripped);
		if (!matcher.matches())
		{
			return Optional.empty();
		}

		String item = findUnique(matcher.group(2));
		if (item == null)
		{
			return Optional.empty();
		}
		return Optional.of(new LootBroadcast(matcher.group(1).trim(), item));
	}

	public static boolean isCollectionLogGameMessage(String message)
	{
		return removeTags(message).contains("New item added to your collection log:");
	}

	public static boolean isValuableDropMessage(String message)
	{
		return removeTags(message).contains("Valuable drop:");
	}

	public static Optional<LootBroadcast> parseClanCollectionLog(String message)
	{
		String stripped = stripTrailingValue(removeTags(message).trim());
		Matcher matcher = CLAN_COLLECTION_LOG.matcher(stripped);
		if (!matcher.matches())
		{
			return Optional.empty();
		}

		String item = findUnique(matcher.group(2));
		if (item == null)
		{
			return Optional.empty();
		}
		return Optional.of(new LootBroadcast(matcher.group(1).trim(), item));
	}

	public static String censorUnique(String message, String item)
	{
		return message.replace(item, "???");
	}

	public static String censorClanSpecialLoot(String message)
	{
		String stripped = removeTags(message);
		int colon = stripped.indexOf(':');
		if (colon < 0)
		{
			return message;
		}
		int rawColon = message.indexOf(':');
		if (rawColon < 0)
		{
			return stripped.substring(0, colon + 1) + " ???";
		}
		return message.substring(0, rawColon + 1) + " ???";
	}

	public static String collectionLogItemName(String notificationBottomText)
	{
		String stripped = removeTags(notificationBottomText).trim();
		String prefix = "New item:";
		if (stripped.regionMatches(true, 0, prefix, 0, prefix.length()))
		{
			stripped = stripped.substring(prefix.length()).trim();
		}
		return stripTrailingValue(stripped);
	}

	private static String stripTrailingValue(String text)
	{
		int paren = text.lastIndexOf(" (");
		if (paren > 0 && text.endsWith(")"))
		{
			return text.substring(0, paren).trim();
		}
		return text;
	}

	@Value
	public static class LootBroadcast
	{
		String player;
		String item;
	}
}
