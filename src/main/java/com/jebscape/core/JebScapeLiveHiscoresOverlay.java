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

import java.awt.*;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.*;
import net.runelite.client.ui.overlay.tooltip.*;
import net.runelite.client.util.*;

public class JebScapeLiveHiscoresOverlay extends OverlayPanel
{
	private static final String RANK_COLUMN_HEADER = "Rank";
	private static final String NAME_COLUMN_HEADER = "Name";
	private static final String LEVEL_COLUMN_HEADER = "Level";
	private static final String XP_COLUMN_HEADER = "XP";
	private Skill currentSkill;
	private int currentStartRank;
	private String[] currentPlayerNames;
	private int[] currentLevels;
	private int[] currentXPs;
	private boolean containsData;
	
	private static class SkillFrame
	{
		private Skill skill;
		private int startRank;
		private String[] playerNames;
		private int[] levels;
		private int[] XPs;
	}
	
	private final int MAX_SKILL_FRAME_QUEUE_SIZE = 10;
	private final JebScapeLiveHiscoresOverlay.SkillFrame[] skillFrameQueue = new JebScapeLiveHiscoresOverlay.SkillFrame[MAX_SKILL_FRAME_QUEUE_SIZE];
	private int currentSkillFrameIndex;
	private int skillFrameQueueSize;
	
	@Inject
	JebScapePlugin plugin;
	@Inject
	private TooltipManager tooltipManager;
	private Client client;
	private final Color color = new Color(5, 248, 242, 218);
	private final Tooltip guestTooltip = new Tooltip("Requires JebScape account to participate. See JebScape settings to configure.");
	private final Tooltip accountTooltip = new Tooltip("See JebScape settings to configure.");
	private boolean isVisible;
	
	public void init(Client client)
	{
		this.client = client;
		this.isVisible = false;
		this.containsData = false;
		this.currentSkill = Skill.AGILITY;
		this.currentSkillFrameIndex = 0;
		this.skillFrameQueueSize = 0;
		
		for (int i = 0; i < MAX_SKILL_FRAME_QUEUE_SIZE; i++)
			skillFrameQueue[i] = new JebScapeLiveHiscoresOverlay.SkillFrame();
		
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(OverlayPriority.LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setMovable(true);
		setClearChildren(true);
	}
	
	public boolean isVisible()
	{
		return isVisible;
	}
	
	public void updateSkillHiscoresData(Skill skill, int startRank, String[] playerNames, int[] levels, int[] XPs)
	{
		// just clear the queue and move immediately to the destination if many ticks behind or if skill suddenly changes
		if (skillFrameQueueSize >= MAX_SKILL_FRAME_QUEUE_SIZE - 2 || currentSkill != skill)
		{
			currentSkill = skill;
			this.skillFrameQueueSize = 0;
		}
			
		int newSkillFrameIndex = (currentSkillFrameIndex + skillFrameQueueSize++) % MAX_SKILL_FRAME_QUEUE_SIZE;
		
		// we accumulate in a buffer per JebScape packet received, which may involve more than one packet in a Jagex tick, or even none
		skillFrameQueue[newSkillFrameIndex].skill = skill;
		skillFrameQueue[newSkillFrameIndex].startRank = startRank;
		skillFrameQueue[newSkillFrameIndex].playerNames = playerNames;
		skillFrameQueue[newSkillFrameIndex].levels = levels;
		skillFrameQueue[newSkillFrameIndex].XPs = XPs;
		
		setContainsData(true);
	}
	
	public void onGameTick()
	{
		// we digest only once per Jagex game tick, thereby smoothening out the visual updates
		if (skillFrameQueueSize > 0)
		{
			currentSkill = skillFrameQueue[currentSkillFrameIndex].skill;
			currentStartRank = skillFrameQueue[currentSkillFrameIndex].startRank;
			currentPlayerNames = skillFrameQueue[currentSkillFrameIndex].playerNames;
			currentLevels = skillFrameQueue[currentSkillFrameIndex].levels;
			currentXPs = skillFrameQueue[currentSkillFrameIndex].XPs;
			
			// move forward head of queue
			currentSkillFrameIndex = (currentSkillFrameIndex + 1) % MAX_SKILL_FRAME_QUEUE_SIZE;
			skillFrameQueueSize--;
		}
	}
	
	public void setContainsData(boolean containsData)
	{
		this.containsData = containsData;
	}
	
	public void hide()
	{
		this.isVisible = false;
	}
	
	public void show()
	{
		this.isVisible = true;
	}
	
	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!isVisible || !containsData || currentXPs == null || currentXPs.length == 0)
			return super.render(graphics);
		
		setPreferredSize(new Dimension(320, 100));
		
		getPanelComponent().getChildren().add(
				TitleComponent.builder()
						.text("JebScape Live Hiscores")
						.color(color)
						.build());
		
		getPanelComponent().getChildren().add(
				TitleComponent.builder()
						.text(currentSkill.getName())
						.color(color)
						.build());
		
		RuneLiteTableComponent liveHiscoresTable = new RuneLiteTableComponent();
		
		liveHiscoresTable.setColumnAlignments(
				RuneLiteTableComponent.TableAlignment.LEFT,
				RuneLiteTableComponent.TableAlignment.CENTER,
				RuneLiteTableComponent.TableAlignment.CENTER,
				RuneLiteTableComponent.TableAlignment.RIGHT);
		
		liveHiscoresTable.addRow(
				ColorUtil.prependColorTag(RANK_COLUMN_HEADER, color),
				ColorUtil.prependColorTag(NAME_COLUMN_HEADER, color),
				ColorUtil.prependColorTag(LEVEL_COLUMN_HEADER, color),
				ColorUtil.prependColorTag(XP_COLUMN_HEADER, color));
		
		for (int i = 0; i < currentXPs.length; ++i)
		{
			if (currentPlayerNames[i] == null || currentPlayerNames[i].length() == 0)
			{
				currentPlayerNames[i] = "[No Player Ranked]";
			}
			
			liveHiscoresTable.addRow(
					Integer.toString(currentStartRank + i),
					currentPlayerNames[i],
					Integer.toString(currentLevels[i]),
					String.format("%,d", currentXPs[i])
			);
			//ColorUtil.prependColorTag(Integer.toString(playerSkillLevel), color) // TODO: use this to color green those players that are online
		}
		
		getPanelComponent().getChildren().add(liveHiscoresTable);
		
		// check if widget is being hovered over to add tooltip
		Rectangle bounds = getBounds();
		net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
		
		if (bounds.contains(mousePosition.getX(), mousePosition.getY()))
		{
			if (plugin.getUseAccountKey())
				tooltipManager.add(accountTooltip);
			else
				tooltipManager.add(guestTooltip);
		}
		
		return super.render(graphics);
	}
}
