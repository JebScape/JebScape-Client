package com.jebscape.core;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.*;
import java.util.*;

import static net.runelite.api.NpcID.GHOST_3516;

@Slf4j
@PluginDescriptor(
	name = "JebScape"
)
public class JebScapePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private JebScapeConfig config;
	
	private boolean inPvp;
	
	private ByteBuffer bufferOut;
	private ByteBuffer bufferIn;
	private DatagramChannel channel;
	private InetSocketAddress address;
	
	@Override
	protected void startUp() throws Exception
	{
		log.info("Megaserver started!");
		
		bufferOut = ByteBuffer.allocate(16);
		bufferOut.order(ByteOrder.LITTLE_ENDIAN);
		bufferIn = ByteBuffer.allocate(16);
		bufferIn.order(ByteOrder.LITTLE_ENDIAN);
		
		address = new InetSocketAddress("192.168.1.148", 43596);
		
		channel = DatagramChannel.open();
		channel.configureBlocking(true);
		channel.bind(null);
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		log.info("Megaserver stopped!");
	}
	
	
	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		/*
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			try
			{
				if (!channel.isConnected())
				{
					channel.connect(address);
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Connecting to JebScape server...", null);
				}
			}
			catch (IOException e)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Error attempting to connect to JebScape server.", null);
				log.info(e.toString());
			}
		}
		 */
	}
/*
	@Subscribe
	public void onPvpChanged(boolean newValue)
	{
		inPvp = newValue;
	}
*/
	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			if (!channel.isConnected())
			{
				try
				{
					channel.connect(address);
					
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Connecting to JebScape server...", null);
				} catch (Exception e)
				{
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Error attempting to connect to JebScape server.", null);
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", e.toString(), null);
				}
			}
			
			if (channel.isConnected() && !client.isInInstancedRegion())
			{
				//byte[] byteArray = {0x77, 0x55, 0x33, 0x11};
				WorldPoint worldLocation = client.getLocalPlayer().getWorldLocation();
				int plane = worldLocation.getPlane();
				int x = worldLocation.getX();
				int y = worldLocation.getY();
				
				bufferOut.clear();
				bufferOut.put((byte)1); // 1 byte; represents tick count
				bufferOut.put((byte)1); // 2 bytes; represents OPCODE_SET_WORLD_POSITION
				bufferOut.putLong(client.getAccountHash()); // 10 bytes
				bufferOut.put((byte)(client.getWorld() - 300)); // 11 bytes
				bufferOut.put((byte)worldLocation.getPlane()); // 12 bytes
				bufferOut.putShort((short)worldLocation.getX()); // 14 bytes
				bufferOut.putShort((short)worldLocation.getY()); // 16 bytes
				bufferOut.rewind();
				// TODO: shall we add 2 bytes for checksum?
				// TODO: need to change the endianness
				
				try
				{
					int bytesWritten = channel.write(bufferOut);
					
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sent " + bytesWritten + " bytes.", null);
				} catch (Exception e)
				{
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Failed to send message.", null);
				}
			}
		}
	}
	
	@Subscribe
	public void onRemovedFriend(RemovedFriend removedFriend)
	{
		
			client.getLocalPlayer().setOverheadText("Hi There!");
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "TESTING", null);
			RuneLiteObject ghost = client.createRuneLiteObject();
			int[] ids = client.getNpcDefinition(GHOST_3516).getModels();
			ModelData[] modelData = new ModelData[ids.length];
			for (int i = 0; i < ids.length; i++)
			{
				modelData[i] = client.loadModelData(ids[i]);
			}
			ModelData combinedModelData = client.mergeModels(modelData, ids.length);
			ghost.setModel(combinedModelData.light());
			LocalPoint point = client.getLocalPlayer().getLocalLocation();
			int plane = client.getLocalPlayer().getWorldLocation().getPlane();
			ghost.setLocation(point, plane);
			ghost.setActive(true);
			
			int x = client.getLocalPlayer().getWorldLocation().getX();
			int y = client.getLocalPlayer().getWorldLocation().getY();
			boolean isInScene = point.isInScene();
			if (isInScene)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Is in scene", null);
			}
			if (point != null)
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Plane: " + String.valueOf(plane) + " Scene: " + point.getSceneX() + ", " + point.getSceneY() + " Local: " + point.getX() + ", " + point.getY(), null);

			
			
		//int[][][] heights = client.getTileHeights();
		//Model model = client.loadModel(ids[0]);
	}
	
	@Provides
	JebScapeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(JebScapeConfig.class);
	}
}
