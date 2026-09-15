package com.coxcensor;

import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

public class CoxLootParserTest
{
	@Test
	public void parsesFriendsChatBroadcast()
	{
		Optional<CoxLootParser.LootBroadcast> loot = CoxLootParser.parseFriendsChatLoot(
			"<col=ef20ff>Karambtwo - Twisted bow</col>");

		Assert.assertTrue(loot.isPresent());
		Assert.assertEquals("Karambtwo", loot.get().getPlayer());
		Assert.assertEquals("Twisted bow", loot.get().getItem());
	}

	@Test
	public void prefersLongerUniqueNames()
	{
		Assert.assertEquals("Ancestral robe top",
			CoxLootParser.findUnique("Player - Ancestral robe top"));
	}

	@Test
	public void parsesClanSpecialLootAndStripsValue()
	{
		Optional<CoxLootParser.LootBroadcast> loot = CoxLootParser.parseClanSpecialLoot(
			"Karambtwo received special loot from a raid: Twisted bow (1,680,988,483)");

		Assert.assertTrue(loot.isPresent());
		Assert.assertEquals("Karambtwo", loot.get().getPlayer());
		Assert.assertEquals("Twisted bow", loot.get().getItem());
	}

	@Test
	public void censorsClanSpecialLootWithoutRevealingItem()
	{
		String censored = CoxLootParser.censorClanSpecialLoot(
			"Karambtwo received special loot from a raid: Twisted bow (1,680,988,483)");
		Assert.assertFalse(censored.contains("Twisted bow"));
		Assert.assertTrue(censored.endsWith(" ???"));
	}

	@Test
	public void parsesClanCollectionLogAndStripsKc()
	{
		Optional<CoxLootParser.LootBroadcast> loot = CoxLootParser.parseClanCollectionLog(
			"Karambtwo received a new collection log item: Twisted bow (507/1,587)");

		Assert.assertTrue(loot.isPresent());
		Assert.assertEquals("Karambtwo", loot.get().getPlayer());
		Assert.assertEquals("Twisted bow", loot.get().getItem());
	}

	@Test
	public void ignoresNonCoxClanCollectionLog()
	{
		Assert.assertFalse(CoxLootParser.parseClanCollectionLog(
			"Karambtwo received a new collection log item: Abyssal whip").isPresent());
	}

	@Test
	public void parsesClanDropWithSource()
	{
		Optional<CoxLootParser.LootBroadcast> loot = CoxLootParser.parseClanDrop(
			"Amber_Gim received a drop: Twisted bow (1,680,988,483 coins) from Chambers of Xeric.");

		Assert.assertTrue(loot.isPresent());
		Assert.assertEquals("Amber_Gim", loot.get().getPlayer());
		Assert.assertEquals("Twisted bow", loot.get().getItem());
	}

	@Test
	public void ignoresNonCoxClanDrop()
	{
		Assert.assertFalse(CoxLootParser.parseClanDrop(
			"Amber_Gim received a drop: Venator vestige (5,000,000 coins) from The Leviathan.").isPresent());
	}

	@Test
	public void censorsClanDropWithoutRevealingItem()
	{
		String censored = CoxLootParser.censorClanSpecialLoot(
			"Amber_Gim received a drop: Twisted bow (1,680,988,483 coins) from Chambers of Xeric.");
		Assert.assertFalse(censored.contains("Twisted bow"));
		Assert.assertTrue(censored.contains("received a drop: ???"));
	}

	@Test
	public void parseClanLootBroadcastAcceptsSpecialLootAndDrop()
	{
		Assert.assertTrue(CoxLootParser.parseClanLootBroadcast(
			"Karambtwo received special loot from a raid: Twisted bow (1,680,988,483)").isPresent());
		Assert.assertTrue(CoxLootParser.parseClanLootBroadcast(
			"Amber_Gim received a drop: Twisted bow (1,680,988,483 coins) from Chambers of Xeric.").isPresent());
		Assert.assertFalse(CoxLootParser.parseClanLootBroadcast(
			"Amber_Gim received a drop: Venator vestige (5,000,000 coins) from The Leviathan.").isPresent());
	}

	@Test
	public void extractsCollectionLogItemFromNotification()
	{
		Assert.assertEquals("Twisted bow",
			CoxLootParser.collectionLogItemName("New item: Twisted bow"));
		Assert.assertEquals("Twisted bow",
			CoxLootParser.collectionLogItemName("New item: Twisted bow (507/1,587)"));
	}

	@Test
	public void ignoresNonCoxItems()
	{
		Assert.assertFalse(CoxLootParser.parseFriendsChatLoot("Karambtwo - Tumeken's shadow").isPresent());
	}
}
