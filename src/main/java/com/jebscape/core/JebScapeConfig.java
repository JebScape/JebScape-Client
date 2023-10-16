/*
 * Copyright (c) 2023, Justin Ead (Jebrim) <jebscapeplugin@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jebscape.core;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.api.Skill;


@ConfigGroup("jebscape")
public interface JebScapeConfig extends Config
{
	@ConfigSection(
			position = 0,
			name = "Live Hiscores",
			description = "Tracks post-200m XP gains and rank changes every tick. Requires JebScape account to participate."
	)
	String liveHiscoresSection = "liveHiscoresSection";
	
	@ConfigItem(
			position = 1,
			keyName = "hideLiveHiscores",
			name = "Hide Live Hiscores",
			description = "Uncheck this to make live hiscores visible again.",
			section = liveHiscoresSection
	)
	default boolean hideLiveHiscores()
	{
		return false;
	}
	
	@ConfigItem(
			position = 2,
			keyName = "selectSkillLiveHiscores",
			name = "Select Skill",
			description = "Select the skill to watch.",
			section = liveHiscoresSection
	)
	default Skill selectSkillLiveHiscores()
	{
		return Skill.AGILITY;
	}
	
	@Range(
			min = 1,
			max = 99996
	)
	@ConfigItem(
			position = 3,
			keyName = "startRankLiveHiscores",
			name = "Starting Rank",
			description = "Enter the top rank being watched.",
			section = liveHiscoresSection
	)
	default int startRankLiveHiscores()
	{
		return 1;
	}
}
