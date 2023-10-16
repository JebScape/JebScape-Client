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
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.*;

public class JebScapeMinimapOverlay extends Overlay
{
	private JebScapeActor[] actors;
	private Client client;
	private final Color color = new Color(5, 248, 242, 218);
	
	public void init(Client client)
	{
		this.client = client;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGHEST);
		setPosition(OverlayPosition.DYNAMIC);
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
			SpritePixels[] mapDots = client.getMapDots();
			
			if (mapDots != null)
			{
				for (int i = 0; i < actors.length; i++)
				{
					JebScapeActor actor = actors[i];
					if (actor.isActive())
					{
						Point minimapPoint = Perspective.localToMinimap(client, actors[i].getLocalLocation());
						if (minimapPoint != null)
						{
							OverlayUtil.renderMinimapLocation(graphics, minimapPoint, color);
						}
					}
				}
			}
		}
		
		return null;
	}
}
