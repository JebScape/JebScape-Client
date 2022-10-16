package com.jebscape.core;

import com.google.inject.Provides;
import javax.inject.Inject;

import com.sun.org.apache.xpath.internal.operations.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.net.*;
import java.nio.channels.*;
import java.nio.*;

import static net.runelite.api.NpcID.GHOST_3516;
import static net.runelite.api.coords.LocalPoint.fromWorld;

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
	
	@Inject
	private Hooks hooks;
	
	
	private boolean inPvp;
	
	private WorldPoint startPoint;
	private WorldPoint midPoint;
	private final int maxGhosts = 64;
	private RuneLiteObject[] ghosts;
	private Model[] ghostModels;
	private int[] ghostAnimations;
	private WorldPoint[] ghostMidPoints;
	private WorldPoint[] ghostEndPoints;
	private LocalPoint[] ghostTargetPoints;
	private WorldArea[] ghostWorldAreas;
	
	private ByteBuffer bufferOut;
	private ByteBuffer bufferIn;
	private DatagramChannel channel;
	private InetSocketAddress address;
	private Animation[] animationTypes;
	
	private enum GHOST_ANIM
	{
		IDLE,
		WALK,
		RUN,
		WALK_ROTATE_LEFT,
		WALK_ROTATE_180,
		WALK_ROTATE_RIGHT,
		IDLE_ROTATE_LEFT,
		IDLE_ROTATE_RIGHT
	}
	
	private enum GHOST_ORIENT
	{
	
	}
	
	private enum MOVEMENT_TYPE
	{
		NO_MOVEMENT,
		WEST,
		EAST,
		SOUTH,
		NORTH,
		SOUTHWEST,
		SOUTHEAST,
		NORTHWEST,
		NORTHEAST,
		INSTANT_MOVE,
		TELEPORT
	}
	
	// these values correspond to the delta MOVEMENT_TYPE brings
	final private int[] directionMoveX =
	{
			0,
			-1,
			1,
			0,
			0,
			-1,
			1,
			-1,
			1
	};
	
	final private int[] directionMoveY =
	{
			0,
			0,
			0,
			-1,
			1,
			-1,
			-1,
			1,
			1
	};
	
	@Inject
	private ClientThread clientThread;
	
	
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
		channel.configureBlocking(false);
		channel.bind(null);
		
		/*
		clientThread.invoke(() ->
		{
		});
		
		 */
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		log.info("Megaserver stopped!");
	}
	
	
	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		// logged out
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			if (channel.isConnected())
			{
				for (int i = 0; i < maxGhosts; i++)
				{
					if (ghosts[i].isActive())
					{
						ghosts[i].setActive(false);
					}
				}
			}
		}
	}
/*
	@Subscribe
	public void onPvpChanged(boolean newValue)
	{
		inPvp = newValue;
	}
*/
	
	/* ORIENTATIONS:
	
South = 0 = 0x0000
Southwest = 256 = 0x100
West = 512 = 0x200
Northwest = 768 = 0x300
North = 1024 = 0x400
Northeast = 1280 = 0x500
East = 1536 = 0x600
Southeast = 1792 = 0x700

Full 360 = 2048 = 0x800

	 */
	
	private WorldPoint getPointFromDirection(WorldPoint startPosition, byte direction)
	{
		int dx = 0;
		int dy = 0;
		
		if (direction > 0 && direction < MOVEMENT_TYPE.INSTANT_MOVE.ordinal())
		{
			dx = directionMoveX[direction];
			dy = directionMoveY[direction];
		}
		
		return new WorldPoint(startPosition.getX() + dx, startPosition.getY() + dy, startPosition.getPlane());
	}
		
	private byte getMidpointDirection()
	{
		MOVEMENT_TYPE direction = MOVEMENT_TYPE.NO_MOVEMENT;
		
		// if running
		if (startPoint != null && midPoint != null)
		{
			int startX = startPoint.getX();
			int startY = startPoint.getY();
			int midX = midPoint.getX();
			int midY = midPoint.getY();
			
			int dx = Integer.signum(midX - startX);
			int dy = Integer.signum(midY - startY);
			
			switch (dx)
			{
				case (-1):
					if (dy == -1)
						direction = MOVEMENT_TYPE.SOUTHWEST;
					else if (dy == 0)
						direction = MOVEMENT_TYPE.WEST;
					else if (dy == 1)
						direction = MOVEMENT_TYPE.NORTHWEST;
					break;
				
				case (0):
					if (dy == -1)
						direction = MOVEMENT_TYPE.SOUTH;
					else if (dy == 1)
						direction = MOVEMENT_TYPE.NORTH;
					break;
				
				case (1):
					if (dy == -1)
						direction = MOVEMENT_TYPE.SOUTHEAST;
					else if (dy == 0)
						direction = MOVEMENT_TYPE.EAST;
					else if (dy == 1)
						direction = MOVEMENT_TYPE.NORTHEAST;
					break;
					
				default:
					break;
			}
		}
		
		return (byte)direction.ordinal();
	}
	
	private void faceNewDirection()
	{
		// TODO: waiting on runelite api to be updated
	}
	
	@Subscribe
	public void onClientTick(ClientTick clientTick)
	{
		// if running, let's capture the midpoint tile
		if (client.getLocalPlayer().getPoseAnimation() == client.getLocalPlayer().getRunAnimation())
		{
			LocalPoint temp = client.getLocalDestinationLocation();
			if (temp != null)
			{
				WorldPoint targetPoint = WorldPoint.fromLocal(client, temp);
				
				// if we've left the point we started running from since the previous game tick
				if (startPoint != null && !startPoint.equals(targetPoint))
				{
					midPoint = targetPoint;
				}
			}
		}
		else
		{
			midPoint = null;
		}
		
		if (ghosts[0].isActive())
		{
			// if the ghost has a target to move toward
			if (ghostTargetPoints[0] != null && ghostTargetPoints[0].isInScene() && ghostEndPoints[0] != null)
			{
				LocalPoint currentLocation = ghosts[0].getLocation();
				int distance = currentLocation.distanceTo(ghostTargetPoints[0]);
				int currentY = currentLocation.getY();
				int currentX = currentLocation.getX();
				int dy = Integer.signum(ghostTargetPoints[0].getY() - currentY);
				int dx = Integer.signum(ghostTargetPoints[0].getX() - currentX);
				
				// ghost is running
				if (ghostMidPoints[0] != null)
				{
					int animationID = GHOST_ANIM.RUN.ordinal();
					boolean moving = true;
					
					// the ghost has arrived at its target
					if (currentLocation.equals(ghostTargetPoints[0]))
					{
						// Is the current target the midpoint?
						if (ghostTargetPoints[0].equals(LocalPoint.fromWorld(client, ghostMidPoints[0])))
						{
							// switch to the endpoint to finish the second half of the run
							ghostTargetPoints[0] = LocalPoint.fromWorld(client, ghostEndPoints[0]);
						}
						else // so this is the endpoint, let's stop
						{
							moving = false;
							animationID = GHOST_ANIM.IDLE.ordinal();
							ghostEndPoints[0] = null;
							ghostTargetPoints[0] = null;
						}
					}
					
					if (moving)
					{
						LocalPoint newPoint = new LocalPoint(currentX + (8 * dx), currentY + (8 * dy));
						ghosts[0].setLocation(newPoint, client.getPlane());
					}
					
					// update animation if it's changed
					if (ghostAnimations[0] != animationID)
					{
						ghostAnimations[0] = animationID;
						ghosts[0].setAnimation(animationTypes[animationID]);
						ghosts[0].setShouldLoop(true);
					}
				}
				else // the ghost is walking or idle
				{
					int animationID = GHOST_ANIM.WALK.ordinal();
					
					// the ghost has arrived at its target
					if (currentLocation.equals(ghostTargetPoints[0]))
					{
						animationID = GHOST_ANIM.IDLE.ordinal();
						ghostEndPoints[0] = null;
						ghostTargetPoints[0] = null;
					}
					else
					{
						LocalPoint newPoint = new LocalPoint(currentX + (4 * dx), currentY + (4 * dy));
						ghosts[0].setLocation(newPoint, client.getPlane());
					}
					
					// update animation if it's changed
					if (ghostAnimations[0] != animationID)
					{
						ghostAnimations[0] = animationID;
						ghosts[0].setAnimation(animationTypes[animationID]);
						ghosts[0].setShouldLoop(true);
					}
				}
			}
			else // ghost is idle
			{
				int animationID = GHOST_ANIM.IDLE.ordinal();
				
				// update animation if it's changed
				if (ghostAnimations[0] != animationID)
				{
					ghostAnimations[0] = animationID;
					ghosts[0].setAnimation(animationTypes[animationID]);
					ghosts[0].setShouldLoop(true);
				}
				
				if (ghostEndPoints[0] != null)
				{
					ghosts[0].setLocation(LocalPoint.fromWorld(client, ghostEndPoints[0]), client.getPlane());
				}
			}
		}
	}
	
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
				
				// load ghost model
				int[] ids = client.getNpcDefinition(GHOST_3516).getModels();
				ModelData[] modelData = new ModelData[ids.length];
				for (int i = 0; i < ids.length; i++)
				{
					modelData[i] = client.loadModelData(ids[i]);
				}
				ModelData combinedModelData = client.mergeModels(modelData, ids.length);
				
				ghostModels = new Model[4];
				ghostModels[0] = combinedModelData.light();
				ghostModels[1] = combinedModelData.cloneVertices().rotateY90Ccw().light();
				ghostModels[2] = combinedModelData.cloneVertices().rotateY180Ccw().light();
				ghostModels[3] = combinedModelData.cloneVertices().rotateY270Ccw().light();
				
				// create the ghost RuneLiteObjects
				ghosts = new RuneLiteObject[maxGhosts];
				ghostAnimations = new int[maxGhosts];
				ghostMidPoints = new WorldPoint[maxGhosts];
				ghostEndPoints = new WorldPoint[maxGhosts];
				ghostTargetPoints = new LocalPoint[maxGhosts];
				for (int i = 0; i < maxGhosts; i++)
				{
					ghosts[i] = client.createRuneLiteObject();
					ghosts[i].setModel(ghostModels[0]);
					ghostAnimations[i] = GHOST_ANIM.IDLE.ordinal();
				}
				
				animationTypes = new Animation[8];
				animationTypes[GHOST_ANIM.IDLE.ordinal()] = client.loadAnimation(client.getLocalPlayer().getIdlePoseAnimation());
				animationTypes[GHOST_ANIM.WALK.ordinal()] = client.loadAnimation(client.getLocalPlayer().getWalkAnimation());
				animationTypes[GHOST_ANIM.RUN.ordinal()] = client.loadAnimation(client.getLocalPlayer().getRunAnimation());
				animationTypes[GHOST_ANIM.WALK_ROTATE_LEFT.ordinal()] = client.loadAnimation(client.getLocalPlayer().getWalkRotateLeft());
				animationTypes[GHOST_ANIM.WALK_ROTATE_180.ordinal()] = client.loadAnimation(client.getLocalPlayer().getWalkRotate180());
				animationTypes[GHOST_ANIM.WALK_ROTATE_RIGHT.ordinal()] = client.loadAnimation(client.getLocalPlayer().getWalkRotateRight());
				animationTypes[GHOST_ANIM.IDLE_ROTATE_LEFT.ordinal()] = client.loadAnimation(client.getLocalPlayer().getIdleRotateLeft());
				animationTypes[GHOST_ANIM.IDLE_ROTATE_RIGHT.ordinal()] = client.loadAnimation(client.getLocalPlayer().getIdleRotateRight());
			}
			
			if (channel.isConnected() && !client.isInInstancedRegion())
			{
				// debug: test the current location of the ghost
				WorldPoint debugLocation = WorldPoint.fromLocal(client, ghosts[0].getLocation());
				int debugX = debugLocation.getX();
				int debugY = debugLocation.getY();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Ghost is at (" + debugX + ", " + debugY + ")", null);
				
				
				
				//byte[] byteArray = {0x77, 0x55, 0x33, 0x11};
				WorldPoint worldLocation = client.getLocalPlayer().getWorldLocation();
				int orientation = client.getLocalPlayer().getOrientation();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Orientation: " + orientation, null);
				
				bufferOut.clear();
				bufferOut.put((byte)1); // 1 byte; represents OPCODE_SET_WORLD_POSITION
				bufferOut.put(getMidpointDirection()); // 2 bytes
				//bufferOut.putLong(client.getAccountHash()); // 10 bytes
				bufferOut.putInt(client.getTickCount());
				bufferOut.putInt(client.getTickCount());
				bufferOut.put((byte)(client.getWorld() - 300)); // 11 bytes
				bufferOut.put((byte)worldLocation.getPlane()); // 12 bytes
				bufferOut.putShort((short)worldLocation.getX()); // 14 bytes
				bufferOut.putShort((short)worldLocation.getY()); // 16 bytes
				bufferOut.rewind();
				// TODO: shall we add 2 bytes for checksum?
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sending  [" + worldLocation.toString() + "]{" + client.getTickCount() + "}", null);
				
				try
				{
					int bytesWritten = channel.write(bufferOut);
					
					//client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Sent " + bytesWritten + " bytes.", null);
				}
				catch (Exception e)
				{
					//client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Failed to send message.", null);
				}
				
				int bytesReceived = 0;
				try
				{
					bufferIn.clear();
					bytesReceived = channel.read(bufferIn);
					
					//client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Received " + bytesReceived + " bytes.", null);
				}
				catch (Exception e)
				{
					//client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Failed to receive message.", null);
				}
				
				// reset run position tracking
				startPoint = worldLocation;
				//midPoint = null;
				
				while (bytesReceived > 0)
				{
					//hooks.registerRenderableDrawListener(drawListener);
					
					// receive data and move ghost
					bufferIn.rewind();
					byte midPointDirection = bufferIn.get(1);
					int tickCount = bufferIn.getInt(2);
					int ghostPlane = bufferIn.get(11);
					int ghostX = bufferIn.getShort(12);
					int ghostY = bufferIn.getShort(14);
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Receiving [WorldPoint(x=" + ghostX + ", y=" + ghostY + ", plane=" + ghostPlane + "]{" + tickCount + "}", null);
					
					if (client.getTickCount() == (tickCount + 1) && worldLocation.getPlane() == ghostPlane)
					{
						// not currently moving or in the scene
						if (ghostEndPoints[0] == null)
						{
							ghostMidPoints[0] = null;
							ghostEndPoints[0] = new WorldPoint(ghostX, ghostY, ghostPlane);
							ghostTargetPoints[0] = LocalPoint.fromWorld(client, ghostEndPoints[0]);
						}
						
						WorldPoint midPosition = getPointFromDirection(ghostEndPoints[0], midPointDirection);
						if (midPosition != null && !midPosition.equals(ghostEndPoints[0]))
						{
							// if not equal to the last endpoint, we know it exists, ergo ghost is running
							ghostMidPoints[0] = midPosition;
							ghostTargetPoints[0] = LocalPoint.fromWorld(client, midPosition);
							ghostEndPoints[0] = new WorldPoint(ghostX, ghostY, ghostPlane);
						}
						else
						{
							// not running
							ghostMidPoints[0] = null;
							ghostEndPoints[0] = null;
							ghostTargetPoints[0] = null;
						}
						
						// if this is a new ghost, let's spawn it in at the target
						if (!ghosts[0].isActive())
						{
							ghosts[0].setLocation(LocalPoint.fromWorld(client, ghostX, ghostY), ghostPlane);
							ghosts[0].setActive(true);
							ghostAnimations[0] = GHOST_ANIM.IDLE.ordinal();
							ghosts[0].setAnimation(animationTypes[GHOST_ANIM.IDLE.ordinal()]);
							ghosts[0].setShouldLoop(true);
						}
						
						// we found the message we care about
						break;
					}
					
					//byte[] receivedArray = new byte[16];
					//bufferIn.get(receivedArray, 0, 16);
					
					try
					{
						bufferIn.clear();
						bytesReceived = channel.read(bufferIn);
						
						//client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Received " + bytesReceived + " bytes.", null);
					}
					catch (Exception e)
					{
						//client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Failed to receive message.", null);
					}
				}
			}
		}
	}
	
	@Provides
	JebScapeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(JebScapeConfig.class);
	}
}
