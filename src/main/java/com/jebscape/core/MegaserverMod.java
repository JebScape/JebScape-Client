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

import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.api.kit.*;
import net.runelite.client.chat.*;
import net.runelite.client.util.Text;
import static net.runelite.api.NpcID.*;
import java.nio.charset.*;
import java.util.Arrays;

public class MegaserverMod
{
	public final int MEGASERVER_MOVEMENT_UPDATE_CMD = 0x1; // 0001
	public final int LIVE_HISCORES_STATS_UPDATE_CMD = 0x2; // 0010
	public final int ADMIN_CONTROL_CMD = 0x4; // 0100
	private final int MAX_GHOSTS = 64;
	private static final int NUM_SKILLS = 24; // includes upcoming Sailing skill
	private int post200mXpAccumulator[] = new int[NUM_SKILLS];
	private int skillTypeToTrack = 0;
	private int startRankToTrack = 1;
	private static final int NUM_RANKS = 5;
	private String[] liveHiscoresPlayerNames = new String[NUM_RANKS];
	private int[] liveHiscoresLevels = new int[NUM_RANKS];
	private long[] liveHiscoresXPs = new long[NUM_RANKS];
	private boolean[] liveHiscoresOnlineStatuses = new boolean[NUM_RANKS];
	private boolean isActive = false;
	private Client client;
	private JebScapeConnection server;
	private JebScapeLiveHiscoresOverlay liveHiscoresOverlay;
	private ChatMessageManager chatMessageManager;
	private JebScapeModelLoader modelLoader = new JebScapeModelLoader();
	private int[] coreData = new int[3];
	private int[] gameSubData = new int[4];
	private int playerCapeID = 31;
	private int prevPlayerCapeID = 31;
	private int prevGameTick = -1;
	private boolean selfGhostDirty = true;
	private boolean[] ghostsDirty = new boolean[MAX_GHOSTS];
	private boolean showSelfGhost = false;
	private Model defaultGhostModel;
	private JebScapeActor selfGhost = new JebScapeActor();
	private JebScapeActor[] ghosts = new JebScapeActor[MAX_GHOSTS];
	private int[] prevSelfGhostEquipmentIDs = new int[7];
	private int[] prevSelfGhostBodyPartIDs = new int[3];
	private int[][] prevGhostModelData = new int[MAX_GHOSTS][4];
	private int[] prevGhostCapeID = new int[MAX_GHOSTS];
	private int[] ghostCapeID = new int[MAX_GHOSTS];
	private byte[] nameBytes = new byte[12];
	private byte[] chatBytes = new byte[80];
	private int[] equipmentIDs = new int[7];
	private int[] bodyPartIDs = new int[3];
	private String chatMessageToSend = "";
	private int cmdType = 0;
	private int cmdArg = 0;
	
	public void init(Client client, JebScapeConnection server, JebScapeActorIndicatorOverlay indicatorOverlay, JebScapeMinimapOverlay minimapOverlay, JebScapeLiveHiscoresOverlay liveHiscoresOverlay, ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.server = server;
		
		modelLoader.init(client);

		for (int i = 0; i < MAX_GHOSTS; i++)
		{
			ghosts[i] = new JebScapeActor();
		}

		indicatorOverlay.setJebScapeActors(ghosts);
		minimapOverlay.setJebScapeActors(ghosts);
		
		this.liveHiscoresOverlay = liveHiscoresOverlay;
		liveHiscoresOverlay.setContainsData(false);
		
		this.chatMessageManager = chatMessageManager;
	}
	
	// must only be called once logged in
	public void start()
	{
		// restart if already active
		if (isActive)
			stop();
		
		if (!server.isChatLoggedIn())
		{
			liveHiscoresOverlay.setContainsData(false);
			resetPost200mXpAccumulators();
		}

		selfGhost.init(client, chatMessageManager);
		this.prevPlayerCapeID = 31;
		this.playerCapeID = 31;
		this.selfGhostDirty = true;

		for (int i = 0; i < MAX_GHOSTS; i++)
		{
			ghosts[i].init(client, chatMessageManager);
			this.prevGhostCapeID[i] = 31;
			this.ghostCapeID[i] = 31;
			this.ghostsDirty[i] = true;
		}

		loadGhostRenderables();

		this.isActive = true;
	}
	
	public void stop()
	{
		if (!isActive)
			return;
		
		this.isActive = false;
		this.playerCapeID = 31;
		this.prevPlayerCapeID = 31;
		this.prevGameTick = -1;
		this.selfGhostDirty = true;
		this.chatMessageToSend = "";
		
		if (!server.isChatLoggedIn())
		{
			liveHiscoresOverlay.setContainsData(false);
			resetPost200mXpAccumulators();
		}
		
		selfGhost.despawn();
		for (int i = 0; i < MAX_GHOSTS; i++)
		{
			ghosts[i].despawn();
			this.prevGhostModelData[i][0] = 0;
			this.prevGhostModelData[i][1] = 0;
			this.prevGhostModelData[i][2] = 0;
			this.prevGhostModelData[i][3] = 0;
			this.prevGhostCapeID[i] = 31;
			this.ghostCapeID[i] = 31;
			this.ghostsDirty[i] = true;
		}
	}
	
	public boolean isActive()
	{
		return isActive;
	}
	
	public void showSelfGhost()
	{
		this.showSelfGhost = true;
	}
	
	public void hideSelfGhost()
	{
		this.showSelfGhost = false;
	}
	
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (chatMessage.getName() != null && (chatMessage.getType() == ChatMessageType.PUBLICCHAT || chatMessage.getType() == ChatMessageType.MODCHAT))
		{
			String senderName = Text.sanitize(chatMessage.getName());
			String playerName = Text.sanitize(client.getLocalPlayer().getName());
			// check if we were the sender
			if (chatMessage.getName() != null && senderName.contentEquals(playerName))
			{
				this.chatMessageToSend = chatMessage.getMessage();
			}
		}
	}
	
	public void onCommandExecuted(int cmdType, int cmdArg)
	{
		this.cmdType = cmdType;
		this.cmdArg = cmdArg;
		this.chatMessageToSend = " ";
	}
	
	public void onAnimationChanged(AnimationChanged animationChanged)
	{
		// TODO: check against the local player; maybe checking this earlier will help fix some of our animation issues
	}
	
	public void onFakeXpDrop(FakeXpDrop fakeXpDrop)
	{
		Skill skill = fakeXpDrop.getSkill();
		this.post200mXpAccumulator[skill.ordinal()] += fakeXpDrop.getXp();
	}
	
	public void resetPost200mXpAccumulators()
	{
		Arrays.fill(this.post200mXpAccumulator, 0);
	}
	
	public void setLiveHiscoresSkillType(int skillType)
	{
		this.skillTypeToTrack = skillType;
	}
	
	public void setLiveHiscoresStartRank(int startRank)
	{
		this.startRankToTrack = startRank;
	}
	
	// returns number of game data bytes sent
	public int onGameTick()
	{
		// must occur before packets are unpacked
		liveHiscoresOverlay.onGameTick();
		
		// analyze most recent data received from the server
		JebScapeServerData[][] gameServerData = server.getRecentGameServerData();
		JebScapeServerData[][] chatServerData = server.getRecentChatServerData();
		int[] numGamePacketsSent = server.getNumGameServerPacketsSent();
		int[] numChatPacketsSent = server.getNumChatServerPacketsSent();
		int currentGameTick = server.getCurrentGameTick();
		int currentChatTick = server.getCurrentChatTick();
		final int JAU_PACKING_RATIO = 32;
		
		// this will update up to 64 ghosts with up to 16 past ticks' worth of data
		// doing this makes us more resilient to unpredictable network delays with either the OSRS server or JebScape server
		// we start with the oldest data first...
		for (int i = 0; i < server.TICKS_UNTIL_LOGOUT; i++)
		{
			// start the cycle from the earliest tick we might have data on
			int gameTick = (currentGameTick + i) % server.TICKS_UNTIL_LOGOUT;
			
			// only bother if we've received any packets for this tick
			if (numGamePacketsSent[gameTick] > 0)
			{
				boolean isFirstPacket = true;
				for (int packetID = 0; packetID < server.GAME_SERVER_PACKETS_PER_TICK; packetID++)
				{
					JebScapeServerData data = gameServerData[gameTick][packetID];
					boolean emptyPacket = data.isEmpty();
					boolean containsMegaserverCmd = false;
					int playerWorldFlags = 0;
					int playerWorld = 0;
					int playerWorldLocationX = 0;
					int playerWorldLocationY = 0;
					int playerWorldLocationPlane = 0;
					int playerPackedOrientation = 0;
					int playerAnimationID = 0;
					boolean playerIsInteracting = false;
					boolean playerIsPoseAnimation = false;
					boolean isInstanced = false;
					
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
												MEGASERVER_MOVEMENT_UPDATE_CMD) != 0;		// 8/32 bits
						playerWorldFlags = (data.coreData[0] >>> 8) & 0xFF;					// 16/32 bits
						playerWorld = (data.coreData[0] >>> 16) & 0x3FFF;					// 30/32 bits
						playerWorldLocationPlane = (data.coreData[0] >>> 30) & 0x3; 		// 32/32 bits
						
						// 16 bits world X position
						// 16 bits world Y position
						playerWorldLocationX = (data.coreData[1] & 0xFFFF);					// 16/32 bits
						playerWorldLocationY = ((data.coreData[1] >>> 16) & 0xFFFF);		// 32/32 bits
						
						// 10 bits reserved
						// 6 bits packedOrientation
						// 14 bits animationID
						// 1 bit isInteracting
						// 1 bit isPoseAnimation
						playerPackedOrientation = (data.coreData[2] >>> 10) & 0x3F;			// 16/32 bits
						playerAnimationID = (data.coreData[2] >>> 16) & 0x3FFF;				// 30/32 bits
						playerIsInteracting = ((data.coreData[2] >>> 30) & 0x1) == 0x1;		// 31/32 bits
						playerIsPoseAnimation = ((data.coreData[2] >>> 31) & 0x1) == 0x1;	// 32/32 bits
						
						// experimental implementation for instances
						isInstanced = ((playerWorldFlags >>> 0x1) & 0x1) == 0x1;
						if (client.isInInstancedRegion() && isInstanced)
						{
							// find the difference between the instance positions
							WorldPoint currentPlayerWorldPosition = client.getLocalPlayer().getWorldLocation();
							WorldPoint currentPlayerInstancePosition = WorldPoint.fromLocalInstance(client, LocalPoint.fromWorld(client, currentPlayerWorldPosition));
							int dx = playerWorldLocationX - currentPlayerInstancePosition.getX();
							int dy = playerWorldLocationY - currentPlayerInstancePosition.getY();
							
							// add this difference to where our player happens to be located in normal world space
							playerWorldLocationX = currentPlayerWorldPosition.getX() + dx;
							playerWorldLocationY = currentPlayerWorldPosition.getY() + dy;
							playerWorldLocationPlane = currentPlayerWorldPosition.getPlane();
						}
						
						// profile stats:
						/*
						if (packetID == 0)
						{
							int coreTickTime = data.subDataBlocks[6][0];
							int totalTickTime = data.subDataBlocks[6][1];
							int postTickTime = data.subDataBlocks[6][2];
							int playerCount = data.subDataBlocks[6][3];
							client.addChatMessage(ChatMessageType.TENSECTIMEOUT, "", "Core: " + coreTickTime + " Total: " + totalTickTime + " Post: " + postTickTime + " Players: " + playerCount, null);
						}
						//*/
					}
					
					if (containsMegaserverCmd && playerWorld == client.getWorld())
					{
						if (isFirstPacket && showSelfGhost && prevGameTick != gameTick)
						{
							// handle updating self ghost here
							WorldPoint ghostPosition = new WorldPoint(playerWorldLocationX, playerWorldLocationY, playerWorldLocationPlane);
							selfGhost.moveTo(ghostPosition, playerPackedOrientation * JAU_PACKING_RATIO, playerAnimationID, playerIsInteracting, playerIsPoseAnimation, isInstanced, gameTick);
							this.prevGameTick = gameTick; // this prevents packets with the same server gameTick across two different client gameTicks from repeating the same moveTo destination
							isFirstPacket = false;
						}
						
						// all ghost positional data within a packet is relative to 15 tiles SW of where the server believes the player to be
						playerWorldLocationX -= 15;
						playerWorldLocationY -= 15;
						
						// there are 4 ghosts per sub data block
						for (int j = 0; j < JebScapeServerData.SUB_DATA_BLOCK_SIZE; j++)
						{
							int ghostID = packetID * JebScapeServerData.SUB_DATA_BLOCK_SIZE + j;
							
							// all data outside the total range must necessarily have despawned ghosts
							if (packetID >= numGamePacketsSent[gameTick])
							{
								ghosts[ghostID].despawn();
								this.prevGhostModelData[ghostID][0] = 0;
								this.prevGhostModelData[ghostID][1] = 0;
								this.prevGhostModelData[ghostID][2] = 0;
								this.prevGhostModelData[ghostID][3] = 0;
								this.prevGhostCapeID[ghostID] = 31;
								this.ghostCapeID[ghostID] = 31;
							}
							else if (!emptyPacket) // within range and not empty, so let's process
							{
								// each piece of ghost data is 4 bytes
								int ghostData = data.subDataBlocks[0][j];
								
								// if the values are 0x1F (31) for each dx and dy, then the ghost has despawned
								// 10 bits combined for dx and dy; check first if despawned
								boolean despawned = (ghostData & 0x3FF) == 0x3FF; // 10 bits (if dx and dy are both all 1s)
								
								if (despawned)
								{
									ghosts[ghostID].despawn();
									this.prevGhostModelData[ghostID][0] = 0;
									this.prevGhostModelData[ghostID][1] = 0;
									this.prevGhostModelData[ghostID][2] = 0;
									this.prevGhostModelData[ghostID][3] = 0;
									this.prevGhostCapeID[ghostID] = 31;
									this.ghostCapeID[ghostID] = 31;
								}
								else
								{
									// not despawned, so let's extract the full data
									// 5 bits dx
									// 5 bits dy
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
									ghosts[ghostID].moveTo(ghostPosition, packedOrientation * JAU_PACKING_RATIO, animationID, isInteracting, isPoseAnimation, isInstanced, gameTick);
									
									if ((gameTick & 0x1) == 0x1)
									{
										boolean modelDataChanged = data.subDataBlocks[j + 1][0] != prevGhostModelData[ghostID][0];
										modelDataChanged = modelDataChanged || (data.subDataBlocks[j + 1][1] != prevGhostModelData[ghostID][1]);
										modelDataChanged = modelDataChanged || (data.subDataBlocks[j + 1][2] != prevGhostModelData[ghostID][2]);
										modelDataChanged = modelDataChanged || (data.subDataBlocks[j + 1][3] != prevGhostModelData[ghostID][3]);
										modelDataChanged = modelDataChanged || ghostCapeID[ghostID] != prevGhostCapeID[ghostID];
										modelDataChanged = modelDataChanged || ghostsDirty[ghostID];
										
										this.prevGhostModelData[ghostID][0] = data.subDataBlocks[j + 1][0];
										this.prevGhostModelData[ghostID][1] = data.subDataBlocks[j + 1][1];
										this.prevGhostModelData[ghostID][2] = data.subDataBlocks[j + 1][2];
										this.prevGhostModelData[ghostID][3] = data.subDataBlocks[j + 1][3];
										this.prevGhostCapeID[ghostID] = ghostCapeID[ghostID];
										ghostsDirty[ghostID] = false;
										
										if (modelDataChanged)
										{
											// extract ghost model data
											equipmentIDs[0] = data.subDataBlocks[j + 1][0] & 0xFFFF;
											equipmentIDs[1] = (data.subDataBlocks[j + 1][0] >>> 16) & 0xFFFF;
											
											equipmentIDs[2] = data.subDataBlocks[j + 1][1] & 0xFFFF;
											equipmentIDs[3] = (data.subDataBlocks[j + 1][1] >>> 16) & 0xFFFF;
											
											equipmentIDs[4] = data.subDataBlocks[j + 1][2] & 0xFFFF;
											equipmentIDs[5] = (data.subDataBlocks[j + 1][2] >>> 16) & 0xFFFF;
											
											equipmentIDs[6] = data.subDataBlocks[j + 1][3] & 0xFFFF;
											int isFemale = (data.subDataBlocks[j + 1][3] >>> 31) & 0x1;
											bodyPartIDs = modelLoader.unpackBodyParts((data.subDataBlocks[j + 1][3] >>> 16) & 0x7FFF, isFemale);

											Model ghostModel = modelLoader.loadPlayerGhostRenderable(equipmentIDs, bodyPartIDs, isFemale, ghostCapeID[ghostID]);
											ghosts[ghostID].setModel(ghostModel);
										}
									}
									else
									{
										// extract ghost world and name
										int ghostWorld = data.subDataBlocks[j + 1][0] & 0x3FFF;
										ghosts[ghostID].setWorld(ghostWorld);
										this.ghostCapeID[ghostID] = (data.subDataBlocks[j + 1][0] >>> 14) & 0x1F;
										
										nameBytes[0] = (byte)(data.subDataBlocks[j + 1][1] & 0xFF);
										nameBytes[1] = (byte)((data.subDataBlocks[j + 1][1] >>> 8) & 0xFF);
										nameBytes[2] = (byte)((data.subDataBlocks[j + 1][1] >>> 16) & 0xFF);
										nameBytes[3] = (byte)((data.subDataBlocks[j + 1][1] >>> 24) & 0xFF);
										
										nameBytes[4] = (byte)(data.subDataBlocks[j + 1][2] & 0xFF);
										nameBytes[5] = (byte)((data.subDataBlocks[j + 1][2] >>> 8) & 0xFF);
										nameBytes[6] = (byte)((data.subDataBlocks[j + 1][2] >>> 16) & 0xFF);
										nameBytes[7] = (byte)((data.subDataBlocks[j + 1][2] >>> 24) & 0xFF);
										
										nameBytes[8] = (byte)(data.subDataBlocks[j + 1][3] & 0xFF);
										nameBytes[9] = (byte)((data.subDataBlocks[j + 1][3] >>> 8) & 0xFF);
										nameBytes[10] = (byte)((data.subDataBlocks[j + 1][3] >>> 16) & 0xFF);
										nameBytes[11] = (byte)((data.subDataBlocks[j + 1][3] >>> 24) & 0xFF);
										
										ghosts[ghostID].setName(new String(nameBytes, StandardCharsets.UTF_8).trim());
									}
								}
							}
						}
					}
				}
			}
			
			// now let's do all this again for chat messages
			int chatTick = (currentChatTick + i) % server.TICKS_UNTIL_LOGOUT;
			
			// only bother if we've received any packets for this tick
			if (numChatPacketsSent[chatTick] > 0)
			{
				for (int packetID = 0; packetID < server.CHAT_SERVER_PACKETS_PER_TICK; packetID++)
				{
					JebScapeServerData data = chatServerData[chatTick][packetID];
					boolean emptyPacket = data.isEmpty();
					boolean containsMegaserverCmd = false;
					boolean containsLiveHiscoresCmd = false;
					int playerWorldFlags = 0;
					int playerWorld = 0;
					int playerWorldLocationX = 0;
					int playerWorldLocationY = 0;
					int playerWorldLocationPlane = 0;
					int playerAnimationState = 0;
					boolean isInstanced = false;
					
					// let's initialize our player position data
					if (!emptyPacket)
					{
						// unpack the core data
						// 8 bitflags for chat command
						// 1 bit isPVP
						// 1 bit isInstanced
						// 6 bits reserved
						// 14 bits world
						// 2 bits plane
						containsMegaserverCmd = ((data.coreData[0] & 0xFF) & // bitflag, so let's just test the one bit
								MEGASERVER_MOVEMENT_UPDATE_CMD) != 0;	// 8/32 bits
						containsLiveHiscoresCmd = ((data.coreData[0] & 0xFF) & // bitflag, so let's just test the one bit
								LIVE_HISCORES_STATS_UPDATE_CMD) != 0;	// 8/32 bits
						playerWorldFlags = (data.coreData[0] >>> 8) & 0xFF;				// 16/32 bits
						playerWorld = (data.coreData[0] >>> 16) & 0x3FFF;				// 30/32 bits
						playerWorldLocationPlane = (data.coreData[0] >>> 30) & 0x3; 	// 32/32 bits
						
						// 16 bits world X position
						// 16 bits world Y position
						playerWorldLocationX = (data.coreData[1] & 0xFFFF);				// 16/32 bits
						playerWorldLocationY = ((data.coreData[1] >>> 16) & 0xFFFF);	// 32/32
						
						playerAnimationState = data.coreData[2];
						
						// not using the third int; reserved for future use
						
						// experimental implementation for instances
						isInstanced = ((playerWorldFlags >>> 0x1) & 0x1) == 0x1;
						
						// profile stats:
						/*
						if (packetID == 0)
						{
							int coreTickTime = data.subDataBlocks[6][0];
							int totalTickTime = data.subDataBlocks[6][1];
							int postTickTime = data.subDataBlocks[6][2];
							int playerCount = data.subDataBlocks[6][3];
							client.addChatMessage(ChatMessageType.TENSECTIMEOUT, "", "Core: " + coreTickTime + " Total: " + totalTickTime + " Post: " + postTickTime + " Players: " + playerCount, null);
						}
						//*/
					}
					
					// contains chat messages
					if (containsMegaserverCmd && playerWorld == client.getWorld())
					{
						// extract chat message
						int ghostWorld = data.subDataBlocks[0][0];
						
						if (ghostWorld != 0)
						{
							// a chat message exists, so let's extract the rest
							nameBytes[0] = (byte)(data.subDataBlocks[0][1] & 0xFF);
							nameBytes[1] = (byte)((data.subDataBlocks[0][1] >>> 8) & 0xFF);
							nameBytes[2] = (byte)((data.subDataBlocks[0][1] >>> 16) & 0xFF);
							nameBytes[3] = (byte)((data.subDataBlocks[0][1] >>> 24) & 0xFF);
							
							nameBytes[4] = (byte)(data.subDataBlocks[0][2] & 0xFF);
							nameBytes[5] = (byte)((data.subDataBlocks[0][2] >>> 8) & 0xFF);
							nameBytes[6] = (byte)((data.subDataBlocks[0][2] >>> 16) & 0xFF);
							nameBytes[7] = (byte)((data.subDataBlocks[0][2] >>> 24) & 0xFF);
							
							nameBytes[8] = (byte)(data.subDataBlocks[0][3] & 0xFF);
							nameBytes[9] = (byte)((data.subDataBlocks[0][3] >>> 8) & 0xFF);
							nameBytes[10] = (byte)((data.subDataBlocks[0][3] >>> 16) & 0xFF);
							nameBytes[11] = (byte)((data.subDataBlocks[0][3] >>> 24) & 0xFF);
							
							String senderName = new String(nameBytes, StandardCharsets.UTF_8).trim();
							// now let's see if we can find the corresponding ghost for this chat message
							for (int ghostID = 0; ghostID < MAX_GHOSTS; ghostID++)
							{
								JebScapeActor ghost = ghosts[ghostID];
								String name = ghost.getName();
								if (ghost.getWorld() == ghostWorld && name != null && name.contentEquals(senderName))
								{
									// we found our ghost, let's proceed
									int index = 0;
									for (int j = 1; j < 6; j++)
									{
										for (int k = 0; k < 4; k++)
										{
											chatBytes[index++] = (byte)(data.subDataBlocks[j][k] & 0xFF);
											chatBytes[index++] = (byte)((data.subDataBlocks[j][k] >>> 8) & 0xFF);
											chatBytes[index++] = (byte)((data.subDataBlocks[j][k] >>> 16) & 0xFF);
											chatBytes[index++] = (byte)((data.subDataBlocks[j][k] >>> 24) & 0xFF);
										}
									}
									
									ghosts[ghostID].setChatMessage(new String(chatBytes, StandardCharsets.UTF_8).trim());
									break;
								}
							}
						}
					}
					
					// contains live hiscores data
					if (containsLiveHiscoresCmd)
					{
						// player coreData contains info on the current monitored player rather than oneself
						
						// unpack the sub data
						// 3 bits monitoredPlayerRankOffset (ranks 0-4; all 1 bits mean none is monitored)
						// 17 bits startingRank (the ranks we pass down are offset by this)
						// 12 bits liveHiscoresLevels[0]
						int monitoredPlayerRankOffset = data.subDataBlocks[0][0] & 0x7;			// 3/32 bits
						int startRank = (data.subDataBlocks[0][0] >>> 3) & 0x1FFFF;				// 20/32 bits
						liveHiscoresLevels[0] = (data.subDataBlocks[0][0] >>> 20) & 0xFFF;		// 32/32 bits
						
						// 12 bits liveHiscoresLevels[1]
						// 12 bits liveHiscoresLevels[2]
						// 8 bits reserved
						liveHiscoresLevels[1] = data.subDataBlocks[0][1] & 0xFFF;				// 12/32 bits
						liveHiscoresLevels[2] = (data.subDataBlocks[0][1] >>> 12) & 0xFFF;		// 24/32 bits
						
						// 12 bits liveHiscoresLevels[3]
						// 12 bits liveHiscoresLevels[4]
						// 8 bits reserved
						liveHiscoresLevels[3] = data.subDataBlocks[0][2] & 0xFFF;				// 12/32 bits
						liveHiscoresLevels[4] = (data.subDataBlocks[0][2] >>> 12) & 0xFFF;		// 24/32 bits
						
						// 5 bits skill type
						// 2 bits reserved
						// 25 bits for Overall upperXPs; 5 bits each
						// 2 bits reserved
						int skillType = data.subDataBlocks[0][3] & 0x5F;								// 5/32 bits
						//int reserved = (data.subDataBlocks[0][3] >>> 5) & 0x3;						// 7/32 bits
						liveHiscoresXPs[0] = (long)((data.subDataBlocks[0][3] >>> 7) & 0x1F) << 31;		// 12/32 bits
						liveHiscoresXPs[1] = (long)((data.subDataBlocks[0][3] >>> 12) & 0x1F) << 31;	// 17/32 bits
						liveHiscoresXPs[2] = (long)((data.subDataBlocks[0][3] >>> 17) & 0x1F) << 31;	// 22/32 bits
						liveHiscoresXPs[3] = (long)((data.subDataBlocks[0][3] >>> 22) & 0x1F) << 31;	// 27/32 bits
						liveHiscoresXPs[4] = (long)((data.subDataBlocks[0][3] >>> 27) & 0x1F) << 31;	// 32/32 bits
						
						for (int j = 0; j < NUM_RANKS; j++)
						{
							liveHiscoresXPs[j] |= data.subDataBlocks[j + 1][0] & 0x7FFFFFFF;				// 31/32 bits
							liveHiscoresOnlineStatuses[j] = ((data.subDataBlocks[j + 1][0] >>> 31) == 0x1);	// 32/32 bits
							
							nameBytes[0] = (byte)(data.subDataBlocks[j + 1][1] & 0xFF);
							nameBytes[1] = (byte)((data.subDataBlocks[j + 1][1] >>> 8) & 0xFF);
							nameBytes[2] = (byte)((data.subDataBlocks[j + 1][1] >>> 16) & 0xFF);
							nameBytes[3] = (byte)((data.subDataBlocks[j + 1][1] >>> 24) & 0xFF);
							
							nameBytes[4] = (byte)(data.subDataBlocks[j + 1][2] & 0xFF);
							nameBytes[5] = (byte)((data.subDataBlocks[j + 1][2] >>> 8) & 0xFF);
							nameBytes[6] = (byte)((data.subDataBlocks[j + 1][2] >>> 16) & 0xFF);
							nameBytes[7] = (byte)((data.subDataBlocks[j + 1][2] >>> 24) & 0xFF);
							
							nameBytes[8] = (byte)(data.subDataBlocks[j + 1][3] & 0xFF);
							nameBytes[9] = (byte)((data.subDataBlocks[j + 1][3] >>> 8) & 0xFF);
							nameBytes[10] = (byte)((data.subDataBlocks[j + 1][3] >>> 16) & 0xFF);
							nameBytes[11] = (byte)((data.subDataBlocks[j + 1][3] >>> 24) & 0xFF);
							
							liveHiscoresPlayerNames[j] = new String(nameBytes, StandardCharsets.UTF_8).trim();
						}
						
						liveHiscoresOverlay.updateSkillHiscoresData(skillType, startRank, liveHiscoresPlayerNames, liveHiscoresLevels, liveHiscoresXPs, liveHiscoresOnlineStatuses);
						
						if (startRank == 1 && liveHiscoresPlayerNames[0].contentEquals(client.getLocalPlayer().getName()))
						{
							// if our player is rank 1 in a skill, let's update their capeID accordingly
							// set to female max cape if rank 1 Overall is female
							this.playerCapeID = (skillType == 0 && client.getLocalPlayer().getPlayerComposition().getGender() == 1) ? modelLoader.femaleMaxCapeID : skillType;
						}
					}
				}
			}
		}
		
		// now let's send our data to the server for the current tick
		Player player = client.getLocalPlayer();
		WorldPoint position = player.getWorldLocation();
		boolean isPoseAnimation = player.getAnimation() == -1;
		int animationID = player.getAnimation();
		if (isPoseAnimation)
		{
			if (currentGameTick == 0 || currentGameTick == 8)
				animationID = player.getIdlePoseAnimation();
			else if ((currentGameTick & 0x1) == 0x1)
				animationID = player.getWalkAnimation();
			else
				animationID = player.getRunAnimation();
		}
		boolean isInteracting = player.getInteracting() != null;
		int packedOrientation = player.getOrientation() / JAU_PACKING_RATIO;
		boolean isPVP = WorldType.isPvpWorld(client.getWorldType());
		boolean isInstanced = client.isInInstancedRegion();
		
		if (isInstanced)
			position = WorldPoint.fromLocalInstance(client, LocalPoint.fromWorld(client, position));
		
		// populate the packet body
		// 8 bitflags for game command
		// 1 bit isPVP
		// 1 bit isInstanced
		// 6 bits reserved
		// 14 bits world
		// 2 bits plane
		coreData[0] = MEGASERVER_MOVEMENT_UPDATE_CMD & 0xFF;// 8/32 bits
		coreData[0] |= (isPVP ? 0x1 : 0x0) << 8;			// 9/32 bits
		coreData[0] |= (isInstanced ? 0x1 : 0x0) << 9;		// 10/32 bits
		coreData[0] |= (0x0 & 0x3F) << 10;					// 16/32 bits
		coreData[0] |= (client.getWorld() & 0x3FFF) << 16;	// 30/32 bits
		coreData[0] |= (client.getPlane() & 0x3) << 30;		// 32/32 bits
		
		// 16 bits world X position
		// 16 bits world Y position
		coreData[1] = position.getX() & 0xFFFF;				// 16/32 bits
		coreData[1] |= (position.getY() & 0xFFFF) << 16;	// 32/32 bits
		
		// 5 bits reserved
		// 5 bits playerCapeID
		// 6 bits packedOrientation
		// 14 bits animationID
		// 1 bit isInteracting
		// 1 bit isPoseAnimation
		coreData[2] = (0x0 & 0x3FF) << 5;					// 5/32 bits
		coreData[2] |= (playerCapeID & 0x1F) << 5;			// 10/32 bits
		coreData[2] |= (packedOrientation & 0x3F) << 10;	// 16/32 bits
		coreData[2] |= (animationID & 0x3FFF) << 16;		// 30/32 bits
		coreData[2] |= (isInteracting ? 0x1 : 0x0) << 30;	// 31/32 bits
		coreData[2] |= (isPoseAnimation ? 0x1 : 0x0) << 31;	// 32/32 bits
		
		// get player models and send them across as well
		PlayerComposition playerComposition = player.getPlayerComposition();
		int[] allEquipmentIDs = playerComposition.getEquipmentIds();
		equipmentIDs[0] = allEquipmentIDs[KitType.AMULET.ordinal()];
		equipmentIDs[1] = allEquipmentIDs[KitType.WEAPON.ordinal()];
		equipmentIDs[2] = allEquipmentIDs[KitType.TORSO.ordinal()];
		equipmentIDs[3] = allEquipmentIDs[KitType.SHIELD.ordinal()];
		equipmentIDs[4] = allEquipmentIDs[KitType.LEGS.ordinal()];
		equipmentIDs[5] = allEquipmentIDs[KitType.HANDS.ordinal()];
		equipmentIDs[6] = allEquipmentIDs[KitType.BOOTS.ordinal()];

		int hairID = playerComposition.getKitId(KitType.HAIR);
		int jawID = playerComposition.getKitId(KitType.JAW);
		int armsID = playerComposition.getKitId(KitType.ARMS);
		int isFemale = playerComposition.getGender();
		bodyPartIDs[0] = hairID >= 0 ? modelLoader.kitIDtoBodyPartMap[hairID] : (isFemale == 1 ? 12 : 0);
		bodyPartIDs[1] = jawID >= 0 ? modelLoader.kitIDtoBodyPartMap[jawID] : (isFemale == 1 ? 4 : 0);
		bodyPartIDs[2] = armsID >= 0 ? modelLoader.kitIDtoBodyPartMap[armsID] : 0;

		if (showSelfGhost)
		{
			if ((prevGameTick & 0x1) == 0x1)
			{
				boolean modelHasChanged = false;
				
				for (int i = 0; i < equipmentIDs.length; i++)
				{
					modelHasChanged = modelHasChanged || (equipmentIDs[i] != prevSelfGhostEquipmentIDs[i]);
					this.prevSelfGhostEquipmentIDs[i] = equipmentIDs[i];
				}
				
				for (int i = 0; i < bodyPartIDs.length; i++)
				{
					modelHasChanged = modelHasChanged || (bodyPartIDs[i] != prevSelfGhostBodyPartIDs[i]);
					this.prevSelfGhostBodyPartIDs[i] = bodyPartIDs[i];
				}
				
				modelHasChanged = modelHasChanged || (playerCapeID != prevPlayerCapeID);
				modelHasChanged = modelHasChanged || selfGhostDirty;
				this.prevPlayerCapeID = playerCapeID;
				this.selfGhostDirty = false;
				
				if (modelHasChanged)
				{
					selfGhost.setModel(modelLoader.loadPlayerGhostRenderable(equipmentIDs, bodyPartIDs, isFemale, playerCapeID));
				}
			}
		}
		else
		{
			selfGhost.despawn();
		}
		
		// 16 bits per equipment ID (2x)
		gameSubData[0] = equipmentIDs[0] & 0xFFFF;			// 16/32 bits
		gameSubData[0] |= (equipmentIDs[1] & 0xFFFF) << 16;	// 32/32 bits
		
		// 16 bits per equipment ID (2x)
		gameSubData[1] = equipmentIDs[2] & 0xFFFF;			// 16/32 bits
		gameSubData[1] |= (equipmentIDs[3] & 0xFFFF) << 16;	// 32/32 bits
		
		// 16 bits per equipment ID (2x)
		gameSubData[2] = equipmentIDs[4] & 0xFFFF;			// 16/32 bits
		gameSubData[2] |= (equipmentIDs[5] & 0xFFFF) << 16;	// 32/32 bits
		
		// 16 bits equipment ID
		// 15 bits packed body part IDs
		// 1 bit isFemale
		gameSubData[3] = equipmentIDs[6] & 0xFFFF;												// 16/32 bits
		gameSubData[3] |= (modelLoader.packBodyParts(bodyPartIDs, isFemale) & 0x7FFF) << 16;	// 31/32 bits
		gameSubData[3] |= (isFemale & 0x1) << 31;												// 32/32 bits

		byte[] extraChatData = new byte[96];
		
		if (!chatMessageToSend.isEmpty()) // check if we've recently sent a chat message
		{
			if (cmdType != 0)
			{
				coreData[0] |= ADMIN_CONTROL_CMD;
				extraChatData[91] = (byte)(cmdType & 0xFF);
				extraChatData[92] = (byte)(cmdArg);
				extraChatData[93] = (byte)((cmdArg) >>> 8);
				extraChatData[94] = (byte)((cmdArg) >>> 16);
				extraChatData[95] = (byte)((cmdArg) >>> 24);
				
				this.cmdType = 0;
				this.cmdArg = 0;
			}
			else
			{
				extraChatData = chatMessageToSend.getBytes(StandardCharsets.UTF_8);
			}
			
			this.chatMessageToSend = "";
		}
		else
		{
			// update the command flag sent
			coreData[0] |= LIVE_HISCORES_STATS_UPDATE_CMD;
			
			// TODO: account for Overall or custom skill
			int skillType = skillTypeToTrack;
			
			// TODO: pack monitor player type and value
			// we are going to pack these slightly differently, with 1 bit per skill
			int userInputDataA = 0; // reserved for custom JebScape skill
			int userInputDataB = 0; // reserved for upper bits of custom JebScape skill & monitored player data
			int userInputDataC = startRankToTrack & 0x1FFFF;	// 17/24 bits
			userInputDataC |= (skillType & 0x7F) << 17;			// 24/24 bits
			
			if (!server.isChatGuest()) // authenticated
			{
				// if we're not sending a chat message this tick, then let's send a stat update for the hiscores
				Skill skills[] = Skill.values();
				final int numSkills = skills.length;
				for (int i = 0; i < numSkills; i++)
				{
					int xp = client.getSkillExperience(skills[i]);
					if (xp == 200000000) // if maxed out
					{
						// let's include our accumulated fake xp drops; clamp it to be safe from buffer overflows
						post200mXpAccumulator[i] = Math.max(0, Math.min(336870911, post200mXpAccumulator[i])); // 2^29 - 1 - 200m
						xp += post200mXpAccumulator[i];
						
						// if we surpass 300m xp gained within a single login session, reset back to 0 to avoid risking an uncontrolled buffer overflow
						// the server has comparable behavior, so as long as this one packet doesn't get lost, we should be okay...
						// I would still advise the player log out before 300m xp is gained in a single session just in case
						if (post200mXpAccumulator[i] > 300000000)
						{
							resetPost200mXpAccumulators();
						}
					}
					
					xp = (xp & 0x1FFFFFFF); // use only 29 bits
					
					extraChatData[i * 4] = (byte)(xp);          	// 8/32 bits
					extraChatData[i * 4 + 1] = (byte)(xp >>> 8);	// 16/32 bits
					extraChatData[i * 4 + 2] = (byte)(xp >>> 16);	// 24/32 bits
					extraChatData[i * 4 + 3] = (byte)(xp >>> 24);	// 29/32 bits
				}
			}
			
			for (int i = 0; i < NUM_SKILLS; i++)
			{
				// we have 3 bits to spare per skill, let's pack them in one at a time
				extraChatData[i * 4 + 3] |= (byte)(((userInputDataA >>> i) & 0x1) << 5);    // 30/32 bits
				extraChatData[i * 4 + 3] |= (byte)(((userInputDataB >>> i) & 0x1) << 6);    // 31/32 bits
				extraChatData[i * 4 + 3] |= (byte)(((userInputDataC >>> i) & 0x1) << 7);    // 32/32 bits
			}
		}
		
		return server.sendGameData(coreData, gameSubData, extraChatData) ? 12 : 0; // TODO: Why are we returning 12 here?
	}
	
	public void onClientTick(ClientTick clientTick)
	{
		if (!isActive)
			return;
		
		// never unset dirty here
		boolean result = !selfGhost.onClientTick();
		this.selfGhostDirty = selfGhostDirty ? true : result;
		for (int i = 0; i < MAX_GHOSTS; i++)
		{
			// update local position and orientation
			result = !ghosts[i].onClientTick();
			this.ghostsDirty[i] = ghostsDirty[i] ? true : result;
		}
	}
	
	private void loadGhostRenderables()
	{
		this.defaultGhostModel = client.loadModel(9925); // null blank model

		selfGhost.setDefaultModel(defaultGhostModel);
		selfGhost.setPoseAnimations(client.getLocalPlayer());
		for (int i = 0; i < MAX_GHOSTS; i++)
		{
			ghosts[i].setDefaultModel(defaultGhostModel);
			ghosts[i].setPoseAnimations(client.getLocalPlayer());
		}
	}
}
