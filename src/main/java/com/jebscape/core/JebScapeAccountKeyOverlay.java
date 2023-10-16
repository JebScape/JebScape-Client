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
import java.awt.datatransfer.*;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.*;
import net.runelite.client.ui.overlay.tooltip.*;
import net.runelite.client.util.*;
import net.runelite.client.input.*;
import org.apache.commons.lang3.*;

public class JebScapeAccountKeyOverlay extends Overlay implements MouseListener
{
	private Client client;
	private JebScapePlugin plugin;
	@Inject
	private TooltipManager tooltipManager;
	@Inject MouseManager mouseManager;
	private final Color color = new Color(5, 248, 242, 218);
	private final Tooltip tooltip = new Tooltip("Paste Account Key");
	private final Dimension dimension = new Dimension(290, 25);
	private final PanelComponent panelComponent = new PanelComponent();
	private boolean isVisible;
	private int invalidCount;
	
	public void init(Client client, JebScapePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		this.isVisible = false;
		this.invalidCount = 0;
		
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		setPriority(OverlayPriority.HIGHEST);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setMovable(false);
		
		mouseManager.registerMouseListener(this);
		
		panelComponent.getChildren().clear();
		panelComponent.setPreferredSize(dimension);
		
		panelComponent.getChildren().add(
				TitleComponent.builder()
						.text("Click here to paste your JebScape account key.")
						.color(color)
						.build());
	}
	
	public void cleanup()
	{
		mouseManager.unregisterMouseListener(this);
	}
	
	public void hide()
	{
		this.isVisible = false;
	}
	
	public void show()
	{
		this.isVisible = true;
		this.invalidCount = 0;
	}
	
	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (isVisible)
		{
			// check if widget is being hovered over to add tooltip
			Rectangle bounds = getBounds();
			net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
			
			if (bounds.contains(mousePosition.getX(), mousePosition.getY()))
			{
				tooltipManager.add(tooltip);
			}
			
			return panelComponent.render(graphics);
		}
		
		return null;
	}
	
	@Override
	public MouseEvent mouseClicked(MouseEvent mouseEvent)
	{
		if (isVisible)
		{
			Rectangle bounds = getBounds();
			net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
			
			if (bounds.contains(mousePosition.getX(), mousePosition.getY()))
			{
				boolean validInput = false;
				try
				{
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					String pastedText = (String)clipboard.getData(DataFlavor.stringFlavor);
					if (pastedText != null)
					{
						String strippedText = StringUtils.left(pastedText.replaceAll("[^0-9]", ""), 20);
						if (strippedText.length() > 0)
						{
							Long key = Long.parseUnsignedLong(strippedText);
							if (key != null)
							{
								validInput = true;
								plugin.setRSProfileAccountKey(key);
								hide();
								this.invalidCount = 0;
							}
						}
					}
				}
				catch (Exception e)
				{
				
				}
				
				if (!validInput)
				{
					this.invalidCount++;
					
					if (invalidCount < 3)
					{
						plugin.addGameMessage("Invalid account key format. Copy your account key from jebscape.com and try again.");
					}
					else
					{
						plugin.addGameMessage("Third invalid login attempt. Please try again later.");
						hide();
					}
				}
				
				mouseEvent.consume();
			}
		}
		
		return mouseEvent;
	}
	
	@Override
	public MouseEvent mousePressed(MouseEvent mouseEvent)
	{
		if (isVisible)
		{
			Rectangle bounds = getBounds();
			net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
			
			if (bounds.contains(mousePosition.getX(), mousePosition.getY()))
			{
				mouseEvent.consume();
			}
		}
		
		return mouseEvent;
	}
	
	@Override
	public MouseEvent mouseReleased(MouseEvent mouseEvent)
	{
		Rectangle bounds = getBounds();
		net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
		
		if (bounds.contains(mousePosition.getX(), mousePosition.getY()))
		{
			mouseEvent.consume();
		}
		
		return mouseEvent;
	}
	
	@Override
	public MouseEvent mouseDragged(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}
	
	@Override
	public MouseEvent mouseMoved(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}
	
	@Override
	public MouseEvent mouseEntered(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}
	
	@Override
	public MouseEvent mouseExited(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}
}