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
import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.*;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.tooltip.*;
import net.runelite.client.input.*;

public class JebScapeProfilePinOverlay extends Overlay implements MouseListener
{
	private Client client;
	private JebScapePlugin plugin;
	@Inject
	private TooltipManager tooltipManager;
	@Inject MouseManager mouseManager;
	private Color defaultBackgroundColor;
	private Color hoverColor;
	private Color pressedColor;
	private final Color cyanColor = new Color(5, 248, 242, 218);
	private final Tooltip[] pinTooltips = new Tooltip[10];
	private final Dimension dimension = new Dimension(160, 25);
	private PanelComponent panelComponent = new PanelComponent();
	private PanelComponent bottomPanelComponent = new PanelComponent();
	private PanelComponent[] pinPanelComponents = new PanelComponent[10];
	private TitleComponent titleComponent;
	private TitleComponent[] pinTitleComponents = new TitleComponent[10];
	private int[] pinValues = new int[4];
	private int currentPinIndex = 0;
	private boolean isVisible;
	
	public void init(Client client, JebScapePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		this.isVisible = false;
		
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		setPriority(OverlayPriority.HIGH);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setMovable(false);
		
		mouseManager.registerMouseListener(this);
		
		panelComponent.getChildren().clear();
		panelComponent.setPreferredSize(dimension);
		panelComponent.setOrientation(ComponentOrientation.VERTICAL);
		
		titleComponent = TitleComponent.builder()
				.text("JebScape PIN: _ _ _ _")
				.color(cyanColor)
				.preferredSize(dimension)
				.build();
		
		panelComponent.getChildren().add(titleComponent);
		
		bottomPanelComponent.getChildren().clear();
		bottomPanelComponent.setPreferredSize(dimension);
		bottomPanelComponent.setOrientation(ComponentOrientation.HORIZONTAL);
		bottomPanelComponent.setGap(new Point(7, 20));
		panelComponent.getChildren().add(bottomPanelComponent);
		this.defaultBackgroundColor = bottomPanelComponent.getBackgroundColor();
		this.hoverColor = defaultBackgroundColor.brighter();
		this.pressedColor = defaultBackgroundColor.darker();
		
		for (int i = 0; i < 10; i++)
		{
			pinTitleComponents[i] = TitleComponent.builder()
							.text(Integer.toString(i))
							.color(cyanColor)
							.preferredLocation(new Point(40 * i, 20))
							.preferredSize(new Dimension(45, 20))
							.build();
			this.pinPanelComponents[i] = new PanelComponent();
			pinPanelComponents[i].setGap(new Point(40, 20));
			pinPanelComponents[i].setPreferredSize(new Dimension(45, 20));
			pinPanelComponents[i].setOrientation(ComponentOrientation.HORIZONTAL);
			pinPanelComponents[i].getChildren().add(pinTitleComponents[i]);
			bottomPanelComponent.getChildren().add(pinPanelComponents[i]);
			pinTooltips[i] = new Tooltip("Enter " + i);
		}
		
		for (int i = 0; i < 4; i++)
		{
			pinValues[i] = -1;
		}
	}
	
	public void cleanup()
	{
		mouseManager.unregisterMouseListener(this);
	}
	
	public void hide()
	{
		this.isVisible = false;
		
		for (int i = 0; i < 4; i++)
		{
			pinValues[i] = -1;
		}
	}
	
	public void show()
	{
		this.isVisible = true;
		this.currentPinIndex = 0;
		
		for (int i = 0; i < 10; i++)
		{
			pinPanelComponents[i].setBackgroundColor(defaultBackgroundColor);
		}
		
		for (int i = 0; i < 4; i++)
		{
			pinValues[i] = -1;
		}
		
		titleComponent.setText("JebScape PIN: _ _ _ _");
	}
	
	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (isVisible)
		{
			// check if widget is being hovered over
			Rectangle bounds = getBounds();
			net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
			
			for (int i = 0; i < 10; i++)
			{
				Rectangle pinBounds = pinTitleComponents[i].getBounds();
				Rectangle newCombined = new Rectangle(bounds.x + pinBounds.x - 7, bounds.y + pinBounds.y - 7, 15, 35);
				if (newCombined.contains(mousePosition.getX(), mousePosition.getY()))
				{
					tooltipManager.add(pinTooltips[i]);
				}
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
			// check if widget is being hovered over
			Rectangle bounds = getBounds();
			net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
			
			for (int i = 0; i < 10; i++)
			{
				Rectangle pinBounds = pinTitleComponents[i].getBounds();
				Rectangle newCombined = new Rectangle(bounds.x + pinBounds.x - 7, bounds.y + pinBounds.y - 7, 15, 35);
				if (newCombined.contains(mousePosition.getX(), mousePosition.getY()))
				{
					pinPanelComponents[i].setBackgroundColor(pressedColor);
					pinValues[currentPinIndex] = i;
					this.currentPinIndex++;
					
					String pinLabelText = "JebScape PIN:";
					for (int pinIndex = 0; pinIndex < 4; pinIndex++)
					{
						if (pinIndex < currentPinIndex)
						{
							pinLabelText += " " + pinValues[pinIndex];
						}
						else
						{
							pinLabelText += " _";
						}
					}
					
					titleComponent.setText(pinLabelText);
					
					if (currentPinIndex == 4)
					{
						plugin.setAccountKeySalt(pinValues);
					}
					
					mouseEvent.consume();
				}
			}
		}
		
		return mouseEvent;
	}
	
	@Override
	public MouseEvent mousePressed(MouseEvent mouseEvent)
	{
		if (isVisible)
		{
			// check if widget is being hovered over
			Rectangle bounds = getBounds();
			net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
			
			for (int i = 0; i < 10; i++)
			{
				Rectangle pinBounds = pinTitleComponents[i].getBounds();
				Rectangle newCombined = new Rectangle(bounds.x + pinBounds.x - 7, bounds.y + pinBounds.y - 7, 15, 35);
				if (newCombined.contains(mousePosition.getX(), mousePosition.getY()))
				{
					mouseEvent.consume();
				}
			}
		}
		
		return mouseEvent;
	}
	
	@Override
	public MouseEvent mouseReleased(MouseEvent mouseEvent)
	{
		if (isVisible)
		{
			// check if widget is being hovered over
			Rectangle bounds = getBounds();
			net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
			
			for (int i = 0; i < 10; i++)
			{
				Rectangle pinBounds = pinTitleComponents[i].getBounds();
				Rectangle newCombined = new Rectangle(bounds.x + pinBounds.x - 7, bounds.y + pinBounds.y - 7, 15, 35);
				if (newCombined.contains(mousePosition.getX(), mousePosition.getY()))
				{
					mouseEvent.consume();
				}
			}
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
		if (isVisible)
		{
			// check if widget is being hovered over
			Rectangle bounds = getBounds();
			net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
			
			for (int i = 0; i < 10; i++)
			{
				Rectangle pinBounds = pinTitleComponents[i].getBounds();
				Rectangle newCombined = new Rectangle(bounds.x + pinBounds.x - 7, bounds.y + pinBounds.y - 7, 15, 35);
				if (newCombined.contains(mousePosition.getX(), mousePosition.getY()))
				{
					pinPanelComponents[i].setBackgroundColor(hoverColor);
				}
				else
				{
					pinPanelComponents[i].setBackgroundColor(defaultBackgroundColor);
				}
			}
		}
		
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