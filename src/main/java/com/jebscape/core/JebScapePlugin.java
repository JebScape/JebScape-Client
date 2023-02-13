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

import com.google.common.eventbus.*;
import com.google.inject.Provides;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "JebScape"
)
public class JebScapePlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private JebScapeActorIndicatorOverlay actorIndicatorOverlay;
	@Inject
	private ClientThread clientThread;
	@Inject
	private JebScapeConfig config;
	private JebScapeConnection server = new JebScapeConnection();
	private MegaserverMod megaserverMod = new MegaserverMod();
	
	@Override
	protected void startUp() throws Exception
	{
		log.info("JebScape has started!");
		
		server.init();
		server.connect();
		overlayManager.add(actorIndicatorOverlay);
		
		clientThread.invoke(() ->
		{
			megaserverMod.init(client, server, actorIndicatorOverlay);
		});
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		clientThread.invoke(() ->
		{
			megaserverMod.stop();
		});
		
		overlayManager.remove(actorIndicatorOverlay);
		server.disconnect();
		
		log.info("JebScape has stopped!");
	}
	
	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		// if no longer logged into OSRS, disconnect
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN && client.getGameState() != GameState.LOADING)
		{
			server.logout();
		}
		else if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			// reset the mod on loading screens, but not the server login status
			megaserverMod.stop();
		}
	}
	
	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (megaserverMod.isActive())
			megaserverMod.onChatMessage(chatMessage);
	}
	
	@Subscribe
	public void onAnimationChanged(AnimationChanged animationChanged)
	{
		if (megaserverMod.isActive())
			megaserverMod.onAnimationChanged(animationChanged);
	}
	
	@Subscribe
	// onGameTick() only runs upon completion of Jagex server packet processing
	public void onGameTick(GameTick gameTick)
	{
		// don't tick anything if not logged into OSRS
		if (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING)
		{
			// TODO: Consider processing received data from the JebScape server at a faster pace using onClientTick()
			server.onGameTick();
			
			if (!server.isGameLoggedIn())
			{
				// we want to clean up if no longer logged in
				if (megaserverMod.isActive())
					megaserverMod.stop();
				
				// log in as a guest
				server.login(client.getAccountHash(), 0, client.getLocalPlayer().getName(), false);
			}
			else if (client.getAccountHash() == server.getAccountHash())
			{
				// since we have a game and chat server, one may still be not logged in, so let's try again
				if (!server.isChatLoggedIn())
					server.login(client.getAccountHash(), 0, client.getLocalPlayer().getName(), false);
				
				boolean loggedInAsGuest = server.isGuest();
				int gameDataBytesSent = 0;
				
				// we're logged in, let's play!
				if (!megaserverMod.isActive())
					megaserverMod.start();
				
				gameDataBytesSent += megaserverMod.onGameTick();
			}
		}
	}
	
	@Subscribe
	public void onClientTick(ClientTick clientTick)
	{
		megaserverMod.onClientTick(clientTick);
	}
	
	@Provides
	JebScapeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(JebScapeConfig.class);
	}
}
