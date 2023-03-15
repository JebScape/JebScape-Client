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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.FontMetrics;
import javax.inject.Singleton;
import net.runelite.api.*;
import net.runelite.api.Perspective;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.*;

@Singleton
public class JebScapeActorIndicatorOverlay extends Overlay
{
	private JebScapeActor[] actors;
	private Client client;
	final private Color color = new Color(5, 248, 242, 218);
	
	public void init(Client client)
	{
		this.client = client;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.HIGHEST);
		setPosition(OverlayPosition.TOP_LEFT);
		setMovable(false);
	}
	
	public void setJebScapeActors(JebScapeActor[] actors)
	{
		this.actors = actors;
	}
	
	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (actors != null)
		{
			for (int i = 0; i < actors.length; ++i)
			{
				JebScapeActor actor = actors[i];
				if (actor.isActive())
				{
					String overheadText = actor.getOverheadText();
					if (overheadText != null && !overheadText.isEmpty())
					{
						Point textLocation = Perspective.localToCanvas(client, actor.getLocalLocation(), client.getPlane(), 250);
						
						if (textLocation != null)
						{
							Font chatFont = FontManager.getRunescapeSmallFont();
							
							// shift the position to center over the actor
							FontMetrics metrics = graphics.getFontMetrics(chatFont);
							// divide by 2 through bitshift
							textLocation = new Point(textLocation.getX() - (metrics.stringWidth(overheadText) >>> 1), textLocation.getY());
							
							if (textLocation != null)
							{
								graphics.setFont(chatFont);
								OverlayUtil.renderTextLocation(graphics, textLocation, overheadText, color);
							}
						}
					}
					
					String chatMessage = actor.getChatMessage();
					if (chatMessage != null && !chatMessage.isEmpty())
					{
						Point textLocation = Perspective.localToCanvas(client, actor.getLocalLocation(), client.getPlane(), 210);
						
						if (textLocation != null)
						{
							Font chatFont = FontManager.getRunescapeBoldFont();
							
							// shift the position to center over the actor
							FontMetrics metrics = graphics.getFontMetrics(chatFont);
							// divide by 2 through bitshift
							textLocation = new Point(textLocation.getX() - (metrics.stringWidth(chatMessage) >>> 1), textLocation.getY());
							
							if (textLocation != null)
							{
								graphics.setFont(chatFont);
								OverlayUtil.renderTextLocation(graphics, textLocation, chatMessage, JagexColors.YELLOW_INTERFACE_TEXT);
							}
						}
					}
				}
			}
		}
		
		return null;
	}
}
