package com.jebscape.core;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;


@ConfigGroup("jebscape")
public interface JebScapeConfig extends Config
{
	@ConfigSection(
			position = 0,
			name = "Mods",
			description = "Enable the mods you wish to use"
	)
	String modsSection = "modsSection";
	@ConfigItem(
		position = 0,
		keyName = "megaserver",
		name = "Megaserver",
		description = "Enables cross-world play with others",
		section = modsSection
	)
	default boolean enableMegaserverMod()
	{
		return true;
	}
	
	// Future feature
	@ConfigItem(
			position = 1,
			keyName = "liveHiscores",
			name = "Live Hiscores (coming soon)",
			description = "Enables tracking xp changes on the hiscores in real-time",
			section = modsSection
	)
	default boolean enableLiveHiscoresMod()
	{
		return false;
	}
}
