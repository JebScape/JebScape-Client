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
import net.runelite.api.widgets.*;
import net.runelite.api.events.*;
import net.runelite.client.chat.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.*;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.*;

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
	private JebScapeMinimapOverlay minimapOverlay;
	@Inject
	private JebScapeLiveHiscoresOverlay liveHiscoresOverlay;
	@Inject
	private JebScapeAccountKeyOverlay accountKeyOverlay;
	@Inject
	private ClientThread clientThread;
	@Inject
	private JebScapeConfig config;
	@Inject
	private ConfigManager configManager;
	@Inject
	private ChatMessageManager chatMessageManager;
	private JebScapeConnection server = new JebScapeConnection();
	private MegaserverMod megaserverMod = new MegaserverMod();
	private boolean useMegaserverMod = true;
	private boolean useAccountKey = false;
	private long accountKey = 0;
	
	@Override
	protected void startUp() throws Exception
	{
		log.info("JebScape has started!");
		
		server.init();
		if (!server.connect())
			log.debug("ERROR: JebScape datagram channel failed to connect.");
		
		actorIndicatorOverlay.init(client);
		minimapOverlay.init(client);
		liveHiscoresOverlay.init(client);
		accountKeyOverlay.init(client, this);
		
		overlayManager.add(actorIndicatorOverlay);
		overlayManager.add(minimapOverlay);
		overlayManager.add(liveHiscoresOverlay);
		overlayManager.add(accountKeyOverlay);
		
		clientThread.invoke(() ->
		{
			useMegaserverMod = true;
			megaserverMod.init(client, server, actorIndicatorOverlay, minimapOverlay, liveHiscoresOverlay);
			
			if (configManager.getConfiguration("jebscape", "hideLiveHiscores", boolean.class))
				liveHiscoresOverlay.hide();
			else
				liveHiscoresOverlay.show();
			
			megaserverMod.setLiveHiscoresSkillType(configManager.getConfiguration("jebscape", "selectSkillLiveHiscores", Skill.class));//config.selectSkillLiveHiscores());
			megaserverMod.setLiveHiscoresStartRank(configManager.getConfiguration("jebscape", "startRankLiveHiscores", int.class));//(config.startRankLiveHiscores());
		});
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		clientThread.invoke(() ->
		{
			server.logout();
			megaserverMod.resetPost200mXpAccumulators();
			megaserverMod.stop();
		});
		
		accountKeyOverlay.cleanup();
		
		overlayManager.remove(accountKeyOverlay);
		overlayManager.remove(liveHiscoresOverlay);
		overlayManager.remove(minimapOverlay);
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
			accountKeyOverlay.hide();
			liveHiscoresOverlay.setContainsData(false);
			megaserverMod.resetPost200mXpAccumulators();
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
	public void onFakeXpDrop(FakeXpDrop fakeXpDrop)
	{
		if (megaserverMod.isActive())
			megaserverMod.onFakeXpDrop(fakeXpDrop);
	}
	
	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (configChanged.getGroup().contentEquals("jebscape"))
		{
			if (configChanged.getKey().contentEquals("hideLiveHiscores"))
			{
				if (config.hideLiveHiscores())
				{
					liveHiscoresOverlay.hide();
				}
				else
				{
					liveHiscoresOverlay.show();
				}
			}
			
			if (configChanged.getKey().contentEquals("selectSkillLiveHiscores"))
			{
				megaserverMod.setLiveHiscoresSkillType(config.selectSkillLiveHiscores());
			}
			
			if (configChanged.getKey().contentEquals("startRankLiveHiscores"))
			{
				megaserverMod.setLiveHiscoresStartRank(config.startRankLiveHiscores());
			}
		}
	}
	
	public void setRSProfileAccountKey(long key)
	{
		configManager.setRSProfileConfiguration("JebScape", "JebScapeAccountKey", key ^ client.getAccountHash());
		
		// let's relog
		clientThread.invokeLater(() -> server.logout());
		
		// new login session; this also means that their fake xp drops while a guest will not be tracked
		megaserverMod.resetPost200mXpAccumulators();
	}
	
	public boolean getUseAccountKey()
	{
		return useAccountKey;
	}
	
	public void addGameMessage(String message)
	{
		clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
	}
	
	@Subscribe
	// onGameTick() only runs upon completion of Jagex server packet processing
	public void onGameTick(GameTick gameTick)
	{
		if (!useMegaserverMod && megaserverMod.isActive())
		{
			server.logout();
			megaserverMod.resetPost200mXpAccumulators();
			megaserverMod.stop();
		}
		
		// don't tick anything if not logged into OSRS
		if (useMegaserverMod && (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING))
		{
			boolean prevGameLoginStatus = server.isGameLoggedIn();
			RuneScapeProfileType rsProfileType = RuneScapeProfileType.getCurrent(client);
			
			if (!server.isGameLoggedIn() || !server.isChatLoggedIn())
			{
				String keyConfig = configManager.getRSProfileConfiguration("JebScape", "JebScapeAccountKey");
				if (keyConfig != null && rsProfileType == RuneScapeProfileType.STANDARD)
				{
					Long key = Long.parseLong(keyConfig);
					if (key != null)
					{
						this.accountKey = key ^ client.getAccountHash();
						this.useAccountKey = true;
					}
					else
					{
						this.accountKey = 0;
						this.useAccountKey = false;
					}
				}
				else
				{
					this.accountKey = 0;
					this.useAccountKey = false;
				}
			}
			
			// TODO: Consider processing received data from the JebScape server at a faster pace using onClientTick()
			server.onGameTick();
			
			if (!server.isGameLoggedIn())
			{
				// we want to clean up if no longer logged in
				if (megaserverMod.isActive())
					megaserverMod.stop();
				
				// log in as a guest
				server.login(client.getAccountHash(), accountKey, Text.sanitize(client.getLocalPlayer().getName()), useAccountKey);
			}
			else if (client.getAccountHash() == server.getAccountHash())
			{
				// since we have a game and chat server, one may still be not logged in, so let's try again
				if (!server.isChatLoggedIn())
				{
					server.login(client.getAccountHash(), accountKey, Text.sanitize(client.getLocalPlayer().getName()), useAccountKey);
					
					// since chat server isn't yet connected, we shouldn't be receiving any data
					liveHiscoresOverlay.setContainsData(false);
					
					// whether we disconnected or just haven't started yet, this should be reset
					megaserverMod.resetPost200mXpAccumulators();
				}
					
				int gameDataBytesSent = 0;
				
				// we're logged in, let's play!
				if (!megaserverMod.isActive())
					megaserverMod.start();
				
				gameDataBytesSent += megaserverMod.onGameTick();
				
				// send a chat message if we've just logged in
				if (prevGameLoginStatus == false)
				{
					ChatMessageBuilder message = new ChatMessageBuilder();
					message.append(ChatColorType.NORMAL).append("Welcome to JebScape! There are currently " + server.getGameNumOnlinePlayers() + " players online.");
					chatMessageManager.queue(QueuedMessage.builder()
							.type(ChatMessageType.WELCOME)
							.runeLiteFormattedMessage(message.build())
							.build());
					
					if (server.isGuest())
					{
						if (useAccountKey)
						{
							// login must've failed if we think we're using it but the server has told us otherwise
							message = new ChatMessageBuilder();
							message.append(ChatColorType.HIGHLIGHT).append("Login attempt failed. Invalid account details sent to server. Try again.");
							chatMessageManager.queue(QueuedMessage.builder()
									.type(ChatMessageType.GAMEMESSAGE)
									.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=f50202"))
									.build());
							
							// clear config
							configManager.unsetRSProfileConfiguration("JebScape", "JebScapeAccountKey");
							this.useAccountKey = false;
						}
						
						if (rsProfileType != RuneScapeProfileType.STANDARD)
						{
							message = new ChatMessageBuilder();
							message.append(ChatColorType.NORMAL).append("You are logged in as a guest. Account key login requires a standard world.");
							chatMessageManager.queue(QueuedMessage.builder()
									.type(ChatMessageType.GAMEMESSAGE)
									.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=d4f502"))//"col=12b500"))
									.build());
						}
						else
						{
							message = new ChatMessageBuilder();
							message.append(ChatColorType.HIGHLIGHT).append("You are logged in as a guest. Click here to create an account.");
							chatMessageManager.queue(QueuedMessage.builder()
									.type(ChatMessageType.GAMEMESSAGE)
									.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=d4f502"))//"col=12b500"))
									.build());
							this.useAccountKey = false;
						}
					}
				}
				
				Widget chatWidget = client.getWidget(WidgetInfo.CHATBOX_MESSAGE_LINES);
				if (chatWidget != null && useAccountKey == false)
				{
					for (Widget w: chatWidget.getDynamicChildren())
					{
						if (Text.removeTags(w.getText()).contains("You are logged in as a guest. Click here to create an account."))
						{
							clientThread.invokeLater(() -> {
								w.setAction(1, "Create JebScape Account");
								w.setOnOpListener((JavaScriptCallback) this::clickCreateJebScapeAccount);
								w.setHasListener(true);
								w.setNoClickThrough(true);
								w.revalidate();
							});
						} else {
							clientThread.invokeLater(() -> {
								w.setHasListener(false);
								w.setNoClickThrough(false);
								w.revalidate();
							});
						}
					}
				}
			}
		}
	}
	
	protected void clickCreateJebScapeAccount(ScriptEvent ev)
	{
		LinkBrowser.browse("www.patreon.com/jebscape/membership");
		accountKeyOverlay.show();
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
