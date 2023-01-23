package com.jebscape.core;

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

@Slf4j
@PluginDescriptor(
	name = "JebScape"
)
public class JebScapePlugin extends Plugin
{
	@Inject
	private Client client;
	
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
		
		clientThread.invoke(() ->
		{
			megaserverMod.init(client, server);
		});
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		clientThread.invoke(() ->
		{
			megaserverMod.stop();
		});
		
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
			
			if (!server.isLoggedIn())
			{
				// we want to clean up if no longer logged in
				if (megaserverMod.isActive())
					megaserverMod.stop();
				
				// log in as a guest
				server.login(client.getAccountHash(), 0, client.getLocalPlayer().getName(), false);
			}
			else if (client.getAccountHash() == server.getAccountHash())
			{
				boolean loggedInAsGuest = server.isGuest();
				int gameDataBytesSent = 0;
				
				// we're logged in, let's play!
				if (!megaserverMod.isActive())
					megaserverMod.start();
				
				gameDataBytesSent += megaserverMod.onGameTick(gameTick);
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
