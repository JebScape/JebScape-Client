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
	final private Color color = new Color(5, 248, 242, 218);
	
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
