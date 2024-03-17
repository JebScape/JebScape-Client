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

import com.google.inject.Provides;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.*;
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
	private JebScapeProfilePinOverlay profilePinOverlay;
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
	private long gameAccountKey = 0;
	private long chatAccountKey = 0;
	private long accountKeySalt = 0;
	private boolean replaceAccountKeySalt = false;
	private int loginTimeout = 0;
	private int loginAttempts = 0;
	
	private final static int NUM_HASH_SALT_PAIRS = 4;
	private static class AccountHashSaltPair
	{
		long accountHash;
		long accountKeySalt;
	}
	private AccountHashSaltPair[] accountHashSaltPairs = new AccountHashSaltPair[NUM_HASH_SALT_PAIRS];
	private int accountHashSaltPairIndex = -1;
	
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
		profilePinOverlay.init(client, this);
		
		overlayManager.add(actorIndicatorOverlay);
		overlayManager.add(minimapOverlay);
		overlayManager.add(liveHiscoresOverlay);
		overlayManager.add(profilePinOverlay);
		
		for (int i = 0; i < NUM_HASH_SALT_PAIRS; i++)
		{
			accountHashSaltPairs[i] = new AccountHashSaltPair();
			accountHashSaltPairs[i].accountHash = 0;
			accountHashSaltPairs[i].accountKeySalt = 0;
		}
		
		clientThread.invoke(() ->
		{
			useMegaserverMod = true;
			megaserverMod.init(client, server, actorIndicatorOverlay, minimapOverlay, liveHiscoresOverlay, chatMessageManager);
			
			if (configManager.getConfiguration("jebscape", "showSelfGhost", boolean.class))
				megaserverMod.showSelfGhost();
			else
				megaserverMod.hideSelfGhost();
			
			if (configManager.getConfiguration("jebscape", "hideLiveHiscores", boolean.class))
				liveHiscoresOverlay.hide();
			else
				liveHiscoresOverlay.show();
			
			JebScapeConfig.JebScapeSkill skill = configManager.getConfiguration("jebscape", "selectSkillLiveHiscores", JebScapeConfig.JebScapeSkill.class);
			megaserverMod.setLiveHiscoresSkillType(skill.ordinal());
			megaserverMod.setLiveHiscoresStartRank(configManager.getConfiguration("jebscape", "startRankLiveHiscores", int.class));
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
		
		for (int i = 0; i < NUM_HASH_SALT_PAIRS; i++)
		{
			accountHashSaltPairs[i].accountHash = 0;
			accountHashSaltPairs[i].accountKeySalt = 0;
		}
		
		this.useAccountKey = false;
		this.gameAccountKey = 0;
		this.chatAccountKey = 0;
		this.accountKeySalt = 0;
		this.loginTimeout = 0;
		this.replaceAccountKeySalt = false;
		
		profilePinOverlay.cleanup();
		overlayManager.remove(profilePinOverlay);
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
			profilePinOverlay.hide();
			liveHiscoresOverlay.setContainsData(false);
			megaserverMod.resetPost200mXpAccumulators();
			this.loginTimeout = 0;
			this.accountKeySalt = 0;
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
	public void onCommandExecuted(CommandExecuted commandExecuted)
	{
		if (commandExecuted.getCommand().contentEquals("jeb"))
		{
			boolean success = false;
			String[] args = commandExecuted.getArguments();
			if (args.length == 2)
			{
				try
				{
					int cmdType = Integer.parseInt(args[0]);
					int cmdArg = Integer.parseInt(args[1]);
					
					if (megaserverMod.isActive())
						megaserverMod.onCommandExecuted(cmdType, cmdArg);
					
					success = true;
				}
				catch (Exception e)
				{
					success = false;
				}
			}
			
			if (!success)
			{
				ChatMessageBuilder message = new ChatMessageBuilder();
				message.append(ChatColorType.HIGHLIGHT).append("Invalid JebScape command.");
				chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.GAMEMESSAGE)
						.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=02f502"))
						.build());
			}
		}
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
			if (configChanged.getKey().contentEquals("showSelfGhost"))
			{
				if (config.showSelfGhost())
				{
					megaserverMod.showSelfGhost();
				}
				else
				{
					megaserverMod.hideSelfGhost();
				}
			}
			
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
				megaserverMod.setLiveHiscoresSkillType(config.selectSkillLiveHiscores().ordinal());
			}
			
			if (configChanged.getKey().contentEquals("startRankLiveHiscores"))
			{
				megaserverMod.setLiveHiscoresStartRank(config.startRankLiveHiscores());
			}
		}
	}
	
	public void setAccountKeySalt(int[] pinValues)
	{
		this.accountKeySalt = (long)pinValues[0] << 4;
		this.accountKeySalt |= (long)pinValues[1] << 16;
		this.accountKeySalt |= (long)pinValues[2] << 36;
		this.accountKeySalt |= (long)pinValues[3] << 60;
		
		profilePinOverlay.hide();
		
		clientThread.invokeLater(() ->
		{
			long accountHash = client.getAccountHash();
			boolean foundExistingPair = false;
			for (int i = 0; i < NUM_HASH_SALT_PAIRS; i++)
			{
				if (accountHashSaltPairs[i].accountHash == accountHash)
				{
					this.accountHashSaltPairs[i].accountKeySalt = accountKeySalt;
					this.accountHashSaltPairIndex = i;
					foundExistingPair = true;
					break;
				}
			}
			
			if (!foundExistingPair)
			{
				this.accountHashSaltPairIndex = (accountHashSaltPairIndex + 1) % NUM_HASH_SALT_PAIRS;
				this.accountHashSaltPairs[accountHashSaltPairIndex].accountHash = accountHash;
				this.accountHashSaltPairs[accountHashSaltPairIndex].accountKeySalt = accountKeySalt;
			}
			
			ChatMessageBuilder message;
			if (replaceAccountKeySalt)
			{
				this.replaceAccountKeySalt = false;
				if (chatAccountKey != 0)
				{
					configManager.setRSProfileConfiguration("JebScape", "KeyGame", gameAccountKey ^ client.getAccountHash() ^ accountKeySalt);
					configManager.setRSProfileConfiguration("JebScape", "Key", chatAccountKey ^ client.getAccountHash() ^ accountKeySalt);
				
					if (accountKeySalt == 0)
					{
						message = new ChatMessageBuilder();
						message.append(ChatColorType.HIGHLIGHT).append("Your JebScape PIN has been removed.");
						chatMessageManager.queue(QueuedMessage.builder()
								.type(ChatMessageType.GAMEMESSAGE)
								.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=02f502"))
								.build());
						
						message = new ChatMessageBuilder();
						message.append(ChatColorType.NORMAL).append("Click here to create a PIN to secure the JebScape login credentials stored within your RuneLite profile.");
						chatMessageManager.queue(QueuedMessage.builder()
								.type(ChatMessageType.WELCOME)
								.runeLiteFormattedMessage(message.build())
								.build());
					}
					else
					{
						message = new ChatMessageBuilder();
						message.append(ChatColorType.HIGHLIGHT).append("Your new JebScape PIN has been created. It will apply to all JebScape accounts linked to your RuneLite profile.");
						chatMessageManager.queue(QueuedMessage.builder()
								.type(ChatMessageType.GAMEMESSAGE)
								.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=02f502"))
								.build());
						
						message = new ChatMessageBuilder();
						message.append(ChatColorType.NORMAL).append("Click here if you would like to change your JebScape PIN.");
						chatMessageManager.queue(QueuedMessage.builder()
								.type(ChatMessageType.WELCOME)
								.runeLiteFormattedMessage(message.build())
								.build());
					}
				}
				else
				{
					message = new ChatMessageBuilder();
					message.append(ChatColorType.HIGHLIGHT).append("Error setting PIN. Please report this bug in the JebScape Discord server.");
					chatMessageManager.queue(QueuedMessage.builder()
							.type(ChatMessageType.GAMEMESSAGE)
							.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=f50202"))
							.build());
				}
			}
			else
			{
				message = new ChatMessageBuilder();
				message.append(ChatColorType.NORMAL).append("Attempting to log in...");
				chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.WELCOME)
						.runeLiteFormattedMessage(message.build())
						.build());
				
				server.logout();
				megaserverMod.resetPost200mXpAccumulators();
				megaserverMod.stop();
			}
		});
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
			boolean prevChatLoginStatus = server.isChatLoggedIn();
			RuneScapeProfileType rsProfileType = RuneScapeProfileType.getCurrent(client);
			
			if (!server.isGameLoggedIn() || !server.isChatLoggedIn())
			{
				long accountHash = client.getAccountHash();
				for (int i = 0; i < NUM_HASH_SALT_PAIRS; i++)
				{
					if (accountHashSaltPairs[i].accountHash == accountHash)
					{
						this.accountKeySalt = accountHashSaltPairs[i].accountKeySalt;
						this.accountHashSaltPairIndex = i;
						break;
					}
				}
			}
			
			if (!server.isGameLoggedIn())
			{
				String keyConfig = configManager.getRSProfileConfiguration("JebScape", "KeyGame");
				if (keyConfig != null)
				{
					Long key = Long.parseLong(keyConfig);
					if (key != null)
					{
						this.gameAccountKey = accountKeySalt ^ client.getAccountHash() ^ key;
					}
					else
					{
						this.gameAccountKey = 0;
					}
				}
				else
				{
					this.gameAccountKey = 0;
				}
			}
			
			if (!server.isChatLoggedIn())
			{
				String keyConfig = configManager.getRSProfileConfiguration("JebScape", "AccountKey");
				if (keyConfig != null)
				{
					ChatMessageBuilder message = new ChatMessageBuilder();
					message.append(ChatColorType.HIGHLIGHT).append("Due to a recently discovered bug with the PIN system, all JebScape accounts have been reset. " +
							"This should resolve all PIN-based login issues. Please report any further discovered bugs in the JebScape Discord server.");
					chatMessageManager.queue(QueuedMessage.builder()
							.type(ChatMessageType.GAMEMESSAGE)
							.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=d4f502"))
							.build());
				}
				
				// clear out any obsolete keys players might still have lying around
				configManager.unsetRSProfileConfiguration("JebScape", "JebScapeAccountKey");
				configManager.unsetRSProfileConfiguration("JebScape", "AccountKeyGame");
				configManager.unsetRSProfileConfiguration("JebScape", "AccountKey");
				keyConfig = configManager.getRSProfileConfiguration("JebScape", "Key");
				if (keyConfig != null)
				{
					Long key = Long.parseLong(keyConfig);
					if (key != null)
					{
						this.chatAccountKey = accountKeySalt ^ client.getAccountHash() ^ key;
					}
					else
					{
						this.chatAccountKey = 0;
					}
				}
				else
				{
					this.chatAccountKey = 0;
				}
			}
			
			// only log in whilst using standard profile to avoid cross-contamination of stats
			this.useAccountKey = rsProfileType == RuneScapeProfileType.STANDARD;
			
			// TODO: Consider processing received data from the JebScape server at a faster pace using onClientTick()
			server.onGameTick();
			
			boolean isGameLoggedIn = server.isGameLoggedIn();
			boolean isChatLoggedIn = server.isChatLoggedIn();
			
			// we want to clean up if no longer logged in
			if (!isGameLoggedIn && megaserverMod.isActive())
			{
				megaserverMod.stop();
			}
			
			if (!isChatLoggedIn)
			{
				// since chat server isn't yet connected, we shouldn't be receiving any data
				liveHiscoresOverlay.setContainsData(false);
				
				// whether we disconnected or just haven't started yet, this should be reset
				megaserverMod.resetPost200mXpAccumulators();
			}
			
			if (!isGameLoggedIn || !isChatLoggedIn)
			{
				if (loginTimeout <= 0)
				{
					// log in as a guest
					server.login(client.getAccountHash(), gameAccountKey, chatAccountKey, useAccountKey, Text.sanitize(client.getLocalPlayer().getName()));
					loginTimeout = 4; // wait 4 ticks before attempting to log in again
				}
				else
				{
					// if we just attempted to log in recently, let's wait a bit
					--loginTimeout;
				}
			}
			
			if ((isGameLoggedIn || isChatLoggedIn) && client.getAccountHash() == server.getAccountHash())
			{
				int gameDataBytesSent = 0;
				
				// we're logged in, let's play!
				if (!megaserverMod.isActive())
					megaserverMod.start();
				
				gameDataBytesSent += megaserverMod.onGameTick();
				
				// send a chat message if we've just logged in
				ChatMessageBuilder message;
				if (isGameLoggedIn && prevGameLoginStatus == false)
				{
					if (prevGameLoginStatus == false)
					{
						message = new ChatMessageBuilder();
						message.append(ChatColorType.NORMAL).append("Welcome to JebScape! There are currently " + server.getGameNumOnlinePlayers() + " players online.");
						chatMessageManager.queue(QueuedMessage.builder()
								.type(ChatMessageType.WELCOME)
								.runeLiteFormattedMessage(message.build())
								.build());
					}
					
					// if we attempted to log in with a key, let's save the key the server provided back
					if (!server.isGameGuest() && useAccountKey && this.gameAccountKey != server.getGameAccountKey())
					{
						this.gameAccountKey = server.getGameAccountKey();
						if (gameAccountKey != 0)
						{
							configManager.setRSProfileConfiguration("JebScape", "KeyGame", gameAccountKey ^ client.getAccountHash() ^ accountKeySalt);
						}
					}
				}
				
				if (isChatLoggedIn && prevChatLoginStatus == false)
				{
					// the chat key is what matters the most right now, so let's prioritize it
					boolean chatLoggedInAsGuest = server.isChatGuest();
					if (useAccountKey)
					{
						if (chatAccountKey != server.getChatAccountKey() || chatAccountKey == 0)
						{
							if (chatAccountKey == 0)
							{
								if (!chatLoggedInAsGuest)
								{
									message = new ChatMessageBuilder();
									message.append(ChatColorType.HIGHLIGHT).append("Your new JebScape account has been automatically created and linked to your OSRS account and RuneLite profile.");
									chatMessageManager.queue(QueuedMessage.builder()
											.type(ChatMessageType.GAMEMESSAGE)
											.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=02f502"))
											.build());
									
									this.accountKeySalt = 0;
									this.chatAccountKey = server.getChatAccountKey();
									this.loginAttempts = 0;
									if (chatAccountKey != 0)
									{
										configManager.setRSProfileConfiguration("JebScape", "Key", chatAccountKey ^ client.getAccountHash() ^ accountKeySalt);
									}
									
									message = new ChatMessageBuilder();
									message.append(ChatColorType.HIGHLIGHT).append("Click here to create a PIN to secure the JebScape login credentials stored within your RuneLite profile.");
									chatMessageManager.queue(QueuedMessage.builder()
											.type(ChatMessageType.GAMEMESSAGE)
											.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=d4f502"))
											.build());
								}
								else
								{
									message = new ChatMessageBuilder();
									message.append(ChatColorType.HIGHLIGHT).append("You are logged in as a guest. Only one account may be created per day.");
									chatMessageManager.queue(QueuedMessage.builder()
											.type(ChatMessageType.GAMEMESSAGE)
											.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=d4f502"))
											.build());
									this.useAccountKey = false;
								}
							}
							else if (loginAttempts == 0)
							{
								// The initial key sent was not valid; an incorrect PIN was tried, so let's get the user to enter in their PIN
								message = new ChatMessageBuilder();
								message.append(ChatColorType.HIGHLIGHT).append("You are logged in as a guest. Enter your PIN to fully log into your JebScape account.");
								chatMessageManager.queue(QueuedMessage.builder()
										.type(ChatMessageType.GAMEMESSAGE)
										.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=d4f502"))
										.build());
								
								profilePinOverlay.show();
								this.loginAttempts++;
							}
							else if (loginAttempts < 4)
							{
								// Attempted to log in with PIN but failed
								message = new ChatMessageBuilder();
								message.append(ChatColorType.HIGHLIGHT).append("Login failed. Try again. You are logged in as a guest. Enter your PIN to fully log into your JebScape account.");
								chatMessageManager.queue(QueuedMessage.builder()
										.type(ChatMessageType.GAMEMESSAGE)
										.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=d4f502"))
										.build());
								
								profilePinOverlay.show();
								this.loginAttempts++;
							}
							else
							{
								// Too many failed attempts. Give up for now.
								message = new ChatMessageBuilder();
								message.append(ChatColorType.HIGHLIGHT).append("Login attempts exhausted. Try again later. You will remain logged in as a guest.");
								chatMessageManager.queue(QueuedMessage.builder()
										.type(ChatMessageType.GAMEMESSAGE)
										.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=d4f502"))
										.build());
							}
						}
						else
						{
							message = new ChatMessageBuilder();
							message.append(ChatColorType.HIGHLIGHT).append("You successfully logged into your JebScape account.");
							chatMessageManager.queue(QueuedMessage.builder()
									.type(ChatMessageType.GAMEMESSAGE)
									.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=02f502"))
									.build());
							
							this.loginAttempts = 0;
							
							if (accountKeySalt == 0)
							{
								message = new ChatMessageBuilder();
								message.append(ChatColorType.HIGHLIGHT).append("Click here to create a PIN to secure the JebScape login credentials stored within your RuneLite profile.");
								chatMessageManager.queue(QueuedMessage.builder()
										.type(ChatMessageType.GAMEMESSAGE)
										.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=d4f502"))
										.build());
							}
							else
							{
								message = new ChatMessageBuilder();
								message.append(ChatColorType.NORMAL).append("Click here if you would like to change your JebScape PIN.");
								chatMessageManager.queue(QueuedMessage.builder()
										.type(ChatMessageType.WELCOME)
										.runeLiteFormattedMessage(message.build())
										.build());
							}
						}
					}
					else if (chatLoggedInAsGuest)
					{
						message = new ChatMessageBuilder();
						message.append(ChatColorType.HIGHLIGHT).append("You are logged in as a guest. Account login requires a standard world.");
						chatMessageManager.queue(QueuedMessage.builder()
								.type(ChatMessageType.GAMEMESSAGE)
								.runeLiteFormattedMessage(message.build().replaceAll("colHIGHLIGHT", "col=d4f502"))
								.build());
					}
				}
				
				Widget chatWidget = client.getWidget(ComponentID.CHATBOX_MESSAGE_LINES);
				if (chatWidget != null && isChatLoggedIn && !server.isChatGuest())
				{
					// TODO: Can we make this only run only when each new message is added instead of every game tick?
					if (accountKeySalt == 0)
					{
						for (Widget w: chatWidget.getDynamicChildren())
						{
							if (Text.removeTags(w.getText()).contains("Click here to create a PIN to secure the JebScape login credentials stored within your RuneLite profile."))
							{
								clientThread.invokeLater(() -> {
									w.setAction(1, "Create new JebScape PIN");
									w.setOnOpListener((JavaScriptCallback) this::clickSetNewProfilePin);
									w.setHasListener(true);
									w.setNoClickThrough(true);
									w.revalidate();
								});
							}
							else
							{
								clientThread.invokeLater(() -> {
									w.setHasListener(false);
									w.setNoClickThrough(false);
									w.revalidate();
								});
							}
						}
					}
					else
					{
						for (Widget w: chatWidget.getDynamicChildren())
						{
							if (Text.removeTags(w.getText()).contains("Click here if you would like to change your JebScape PIN.") )
							{
								clientThread.invokeLater(() -> {
									w.setAction(1, "Set New JebScape PIN");
									w.setOnOpListener((JavaScriptCallback) this::clickSetNewProfilePin);
									w.setHasListener(true);
									w.setNoClickThrough(true);
									w.revalidate();
								});
							}
							else
							{
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
	}
	
	protected void clickSetNewProfilePin(ScriptEvent ev)
	{
		this.replaceAccountKeySalt = true;
		profilePinOverlay.show();
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
