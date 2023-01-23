package com.jebscape.core;

import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;

import static net.runelite.api.NpcID.*;

public class MegaserverMod
{
	public final int MEGASERVER_MOVEMENT_UPDATE_CMD = 0x1;
	private final int MAX_GHOSTS = 64;
	private boolean isActive = false;
	private Client client;
	private JebScapeConnection server;
	private int[] clientData = new int[3];
	private Model ghostModel;
	private WorldPoint currentServerPosition;
	private boolean renderablesLoaded = false;
	private com.jebscape.megaserver.JebScapeActor[] ghosts = new com.jebscape.megaserver.JebScapeActor[MAX_GHOSTS];
	
	
	
	public void init(Client client, JebScapeConnection server)
	{
		this.client = client;
		this.server = server;
		
		for (int i = 0; i < MAX_GHOSTS; i++)
		{
			ghosts[i] = new com.jebscape.megaserver.JebScapeActor();
			ghosts[i].init(client);
		}
	}
	
	// must only be called once logged in
	public void start()
	{
		// restart if already active
		if (isActive)
			stop();
		
		this.isActive = true;
		
		if (!renderablesLoaded)
			loadGhostRenderables();
		
		if (!server.isLoggedIn())
			server.login(client.getAccountHash(), 0, client.getLocalPlayer().getName(), false);
	}
	
	public void stop()
	{
		if (!isActive)
			return;
		
		this.isActive = false;
		
		for (int i = 0; i < MAX_GHOSTS; i++)
			ghosts[i].despawn();
	}
	
	public boolean isActive()
	{
		return isActive;
	}
	
	public void onAnimationChanged(AnimationChanged animationChanged)
	{
		// TODO: check against the local player; maybe checking this earlier will help fix some of our animation issues
	}
	
	// returns number of game data bytes sent
	public int onGameTick(GameTick gameTick)
	{
		// analyze most recent data received from the server
		JebScapeServerData[][] serverData = server.getRecentGameServerData();
		int[] numPacketsSent = server.getNumGameServerPacketsSent();
		int lastReceivedTick = server.getLastReceivedGameTick();
		final int JAU_PACKING_RATIO = 32;
		
		// this will update up to 64 ghosts with up to 16 past ticks' worth of data
		// doing this makes us more resilient to unpredictable network delays with either the OSRS server or JebScape server
		// we start with the oldest data first...
		for (int i = 0; i < server.TICKS_UNTIL_LOGOUT; i++)
		{
			// start the cycle from the earliest tick we might have data on
			int tick = (lastReceivedTick + i) % server.TICKS_UNTIL_LOGOUT;
			
			// only bother if we've received any packets for this tick
			if (numPacketsSent[tick] > 0)
			{
				for (int packetID = 0; packetID < server.SERVER_PACKETS_PER_TICK; packetID++)
				{
					JebScapeServerData data = serverData[tick][packetID];
					boolean emptyPacket = data.isEmpty();
					boolean containsMegaserverCmd = false;
					int playerWorldFlags = 0;
					int playerWorld = 0;
					int playerWorldLocationX = 0;
					int playerWorldLocationY = 0;
					int playerWorldLocationPlane = 0;
					
					// let's initialize our player position data
					if (!emptyPacket)
					{
						// unpack the core data
						// 8 bitflags for game command
						// 1 bit isPVP
						// 1 bit isInstanced
						// 6 bits reserved
						// 14 bits world
						// 2 bits plane
						containsMegaserverCmd = ((data.coreData[0] & 0xFF) & // bitflag, so let's just test the one bit
												MEGASERVER_MOVEMENT_UPDATE_CMD) != 0;	// 8/32 bits
						playerWorldFlags = (data.coreData[0] >>> 8) & 0xFF;				// 16/32 bits
						playerWorld = (data.coreData[0] >>> 16) & 0x3FFF;				// 30/32 bits
						playerWorldLocationPlane = (data.coreData[0] >>> 30) & 0x3; 	// 32/32 bits
						
						// 16 bits world X position
						// 16 bits world Y position
						playerWorldLocationX = (data.coreData[1] & 0xFFFF);				// 16/32 bits
						playerWorldLocationY = ((data.coreData[1] >>> 16) & 0xFFFF);	// 32/32 bits
						
						// not using the third int; reserved for future use
					}
					
					if (containsMegaserverCmd)
					{
						// all ghost positional data within a packet is relative to 15 tiles SW of where the server believes the player to be
						playerWorldLocationX -= 15;
						playerWorldLocationY -= 15;
						
						// there are 4 ghosts per sub data block
						for (int j = 0; j < JebScapeServerData.SUB_DATA_BLOCK_SIZE; j++)
						{
							int ghostID = packetID * JebScapeServerData.SUB_DATA_BLOCK_SIZE + j;
							
							// all data outside the total range must necessarily have despawned ghosts
							if (packetID >= numPacketsSent[tick])
								ghosts[ghostID].despawn();
							else if (!emptyPacket) // within range and not empty, so let's process
							{
								// each piece of ghost data is 4 bytes
								int ghostData = data.subDataBlocks[0][j];
								
								// if the values are 0x1F (31) for each relativeX and relativeY, then the ghost has despawned
								// 10 bits combined for dx and dy; check first if despawned
								boolean despawned = (ghostData & 0x3FF) == 0x3FF; // 10 bits (if dx and dy are both all 1s)
								
								if (despawned)
									ghosts[ghostID].despawn();
								else
								{
									// not despawned, so let's extract the full data
									// 5 bits dx
									// 5 bits dx
									// 6 bits packedOrientation
									// 14 bits animationID
									// 1 bit isInteracting
									// 1 bit isPoseAnimation
									int dx = ghostData & 0x1F;										// 5/32 bits
									int dy = (ghostData >>> 5) & 0x1F;								// 10/32 bits
									int packedOrientation = (ghostData >>> 10) & 0x3F;				// 16/32 bits
									int animationID = (ghostData >>> 16) & 0x3FFF;					// 30/32 bits
									boolean isInteracting = ((ghostData >>> 30) & 0x1) == 0x1;		// 31/32 bits
									boolean isPoseAnimation = ((ghostData >>> 31) & 0x1) == 0x1;	// 32/32 bits
									
									WorldPoint ghostPosition = new WorldPoint(playerWorldLocationX + dx, playerWorldLocationY + dy, playerWorldLocationPlane);
									ghosts[ghostID].moveTo(ghostPosition, packedOrientation * JAU_PACKING_RATIO, animationID, isInteracting, isPoseAnimation);
								}
							}
						}
					}
				}
			}
		}
		
		// now let's send our data to the server for the current tick
		Player player = client.getLocalPlayer();
		WorldPoint position = player.getWorldLocation();
		boolean isPoseAnimation = player.getAnimation() == -1;
		int animationID = isPoseAnimation ? player.getPoseAnimation() : player.getAnimation();
		boolean isInteracting = player.getInteracting() != null;
		int packedOrientation = player.getOrientation() / JAU_PACKING_RATIO;
		boolean isPVP = WorldType.isPvpWorld(client.getWorldType());
		boolean isInstanced = client.isInInstancedRegion();
		
		// populate the packet body
		// 8 bitflags for game command
		// 1 bit isPVP
		// 1 bit isInstanced
		// 6 bits reserved
		// 14 bits world
		// 2 bits plane
		clientData[0] = MEGASERVER_MOVEMENT_UPDATE_CMD & 0xFF;	// 8/32 bits
		clientData[0] |= (isPVP ? 0x1 : 0x0) << 8;				// 9/32 bits
		clientData[0] |= (isInstanced ? 0x1 : 0x0) << 9;		// 10/32 bits
		clientData[0] |= (0x0 & 0x3F) << 10;					// 16/32 bits
		clientData[0] |= (client.getWorld() & 0x3FFF) << 16;	// 30/32 bits
		clientData[0] |= (client.getPlane() & 0x3) << 30;		// 32/32 bits
		
		// 16 bits world X position
		// 16 bits world Y position
		clientData[1] = position.getX() & 0xFFFF;				// 16/32 bits
		clientData[1] |= (position.getY() & 0xFFFF) << 16;		// 32/32 bits
		
		// 10 bits reserved
		// 6 bits packedOrientation
		// 14 bits animationID
		// 1 bit isInteracting
		// 1 bit isPoseAnimation
		clientData[2] = 0x0 & 0x3FF;							// 10/32 bits
		clientData[2] |= (packedOrientation & 0x3F) << 10;		// 16/32 bits
		clientData[2] |= (animationID & 0x3FFF) << 16;			// 30/32 bits
		clientData[2] |= (isInteracting ? 0x1 : 0x0) << 30;		// 31/32 bits
		clientData[2] |= (isPoseAnimation ? 0x1 : 0x0) << 31;	// 32/32 bits
		
		return server.sendGameData(clientData[0], clientData[1], clientData[2]) ? 12 : 0; // 3 * 4-byte ints = 12 bytes
	}
	
	public void onClientTick(ClientTick clientTick)
	{
		if (!isActive)
			return;
			
		for (int i = 0; i < MAX_GHOSTS; i++)
		{
			// update local position and orientation
			ghosts[i].onClientTick(clientTick);
		}
	}
	
	private void loadGhostRenderables()
	{
		// load ghost model
		int[] ids = client.getNpcDefinition(GHOST_3516).getModels();
		ModelData[] modelData = new ModelData[ids.length];
		for (int i = 0; i < ids.length; i++)
			modelData[i] = client.loadModelData(ids[i]);
		ModelData combinedModelData = client.mergeModels(modelData, ids.length);
		this.ghostModel = combinedModelData.light(); // TODO: try adding some params to this to see if it fixes some lighting issues we have
		
		Player player = client.getLocalPlayer();
		for (int i = 0; i < MAX_GHOSTS; i++)
		{
			// TODO: we need to still test if we can reuse the same model or if we need to spawn multiple versions
			ghosts[i].setModel(ghostModel);
			ghosts[i].setPoseAnimations(player);
		}
		
		renderablesLoaded = true;
	}
}
