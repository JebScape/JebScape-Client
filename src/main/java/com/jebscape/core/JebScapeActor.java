package com.jebscape.megaserver;

import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;

public class JebScapeActor
{
	private Client client;
	private RuneLiteObject rlObject;
	
	private class Target
	{
		public WorldPoint worldDestinationPosition;
		public LocalPoint localDestinationPosition;
		public int tileMovementSpeed;
		public int jauDestinationOrientation;
		public int primaryAnimationID;
		public boolean isInteracting;
		public boolean isMidPoint;
	}
	private final int MAX_TARGET_QUEUE_SIZE = 10;
	private Target[] targetQueue = new Target[MAX_TARGET_QUEUE_SIZE];
	private int currentTargetIndex;
	private int targetQueueSize;
	
	private int currentMovementSpeed;
	private int animationStall; // stalls movement animations while playing certain primary animations
	
	
	final int JAU_DIRECTIONS_5X5[][] = {{768,	768,	1024,	1280,	1280},
			{768,	768,	1024,	1280,	1280},
			{512,	512,	0,		1536,	1536},
			{256,	256,	0,		1792,	1792},
			{256,	256,	0,		1792,	1792}};
	final int CENTER_DIRECTION_INDEX = 2;
	
	private enum POSE_ANIM
	{
		IDLE,
		WALK,
		RUN,
		WALK_ROTATE_180,
		WALK_STRAFE_LEFT,
		WALK_STRAFE_RIGHT,
		IDLE_ROTATE_LEFT,
		IDLE_ROTATE_RIGHT
	}
	Animation[] animationPoses = new Animation[8];
	
	
	public void init(Client client)
	{
		this.client = client;
		this.rlObject = client.createRuneLiteObject();
		for (int i = 0; i < MAX_TARGET_QUEUE_SIZE; i++)
			targetQueue[i] = new Target();
	}
	public void setModel(Model model)
	{
		rlObject.setModel(model);
	}
	
	public void setPoseAnimations(Actor actor)
	{
		this.animationPoses[POSE_ANIM.IDLE.ordinal()] = client.loadAnimation(actor.getIdlePoseAnimation());
		this.animationPoses[POSE_ANIM.WALK.ordinal()] = client.loadAnimation(actor.getWalkAnimation());
		this.animationPoses[POSE_ANIM.RUN.ordinal()] = client.loadAnimation(actor.getRunAnimation());
		this.animationPoses[POSE_ANIM.WALK_ROTATE_180.ordinal()] = client.loadAnimation(actor.getWalkRotate180());
		this.animationPoses[POSE_ANIM.WALK_STRAFE_LEFT.ordinal()] = client.loadAnimation(actor.getWalkRotateLeft()); // rotate is a misnomer here
		this.animationPoses[POSE_ANIM.WALK_STRAFE_RIGHT.ordinal()] = client.loadAnimation(actor.getWalkRotateRight()); // rotate is a misnomer here
		this.animationPoses[POSE_ANIM.IDLE_ROTATE_LEFT.ordinal()] = client.loadAnimation(actor.getIdleRotateLeft());
		this.animationPoses[POSE_ANIM.IDLE_ROTATE_RIGHT.ordinal()] = client.loadAnimation(actor.getIdleRotateRight());
		rlObject.setShouldLoop(true);
	}
	
	public void spawn(WorldPoint position)
	{
		rlObject.setLocation(LocalPoint.fromWorld(client, position), position.getPlane());
		rlObject.setOrientation(0);
		rlObject.setAnimation(animationPoses[POSE_ANIM.IDLE.ordinal()]);
		rlObject.setShouldLoop(true);
		rlObject.setActive(true);
		this.currentMovementSpeed = 0;
		this.currentTargetIndex = 0;
		this.targetQueueSize = 0;
	}
	
	public void despawn()
	{
		rlObject.setActive(false);
		this.currentMovementSpeed = 0;
		this.currentTargetIndex = 0;
		this.targetQueueSize = 0;
	}
	
	public WorldPoint getWorldLocation()
	{
		return targetQueueSize > 0 ? targetQueue[currentTargetIndex].worldDestinationPosition : WorldPoint.fromLocal(client, rlObject.getLocation());
	}
	
	// set this every game tick for each new position (usually only up to 2 tiles out)
	// do NOT use this for pathfinding to the final destination of distant targets (you will just teleport)
	// WorldPoint position must be in scene and on same plane
	// int jauOrientation is not used if isInteracting is false; it will instead default to the angle being moved towards
	// int primaryAnimationID will not be used if -1; the previously set movement poses will be used instead
	// TODO: possible addition of stall animation variable
	public void moveTo(WorldPoint position, int jauOrientation, int primaryAnimationID, boolean isInteracting)
	{
		if (!position.isInScene(client) || position.getPlane() != client.getPlane() || jauOrientation < 0)
			return;
		
		// just clear the queue and move immediately to the destination if many ticks behind
		if (targetQueueSize >= MAX_TARGET_QUEUE_SIZE - 2)
			targetQueueSize = 0;
		
		int prevTargetIndex = (currentTargetIndex + targetQueueSize - 1) % MAX_TARGET_QUEUE_SIZE;
		int newTargetIndex = (currentTargetIndex + targetQueueSize) % MAX_TARGET_QUEUE_SIZE;
		
		// use current position if nothing is in queue
		LocalPoint prevLocalPosition;
		WorldPoint prevWorldPosition;
		if (targetQueueSize++ > 0)
		{
			prevLocalPosition = targetQueue[prevTargetIndex].localDestinationPosition;
			prevWorldPosition = targetQueue[prevTargetIndex].worldDestinationPosition;
		}
		else
		{
			prevLocalPosition = rlObject.getLocation();
			prevWorldPosition = WorldPoint.fromLocal(client, prevLocalPosition);
		}
		
		int distance = prevWorldPosition.distanceTo(position);
		if (distance == Integer.MAX_VALUE || distance > 2)
			distance = 0;
		
		if (distance > 0)
		{
			int[][] colliders = client.getCollisionMaps()[prevWorldPosition.getPlane()].getFlags();
			int colliderFlag = colliders[prevLocalPosition.getSceneX()][prevLocalPosition.getSceneY()];
			int dx = position.getX() - prevWorldPosition.getX();
			int dy = position.getY() - prevWorldPosition.getY();
			
			boolean useMidPointTile = false;
			if (distance == 1 && dx != 0 && dy != 0) // test for blockage along diagonal
			{
				// if blocked diagonally, go around in an L shape (2 options)
				int colliderToTest = -1;
				if (dx == -1 && dy == -1)
					colliderToTest = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST;
				else if (dx == -1 && dy == 1)
					colliderToTest = CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST;
				else if (dx == 1 && dy == 1)
					colliderToTest = CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST;
				else if (dx == 1 && dy == -1)
					colliderToTest = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST;
				else
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Error with collision detection. (1)", null);
				
				if (colliderToTest != -1 && (colliderFlag & colliderToTest) != 0)
				{
					useMidPointTile = true;
					distance = 2; // we are now running in an L shape
					
					// now that we've established it's blocked, let's test the priority paths (West > East > South > North)
					// if blocked, we will just accept that the untested side works... if it doesn't, then something glitched, but alas!
					if ((colliderToTest == CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST && (colliderFlag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0) ||
							(colliderToTest == CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST && (colliderFlag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0) ||
							(colliderToTest == CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST && (colliderFlag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0) ||
							(colliderToTest == CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST && (colliderFlag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0))
						dx = 0;
					else
						dy = 0;
				}
			}
			else if (distance == 2 && Math.abs(dy - dx) == 1) // test for blockage along knight-style moves
			{
				useMidPointTile = true; // we will always need a midpoint for these types of moves
				
				// do we go straight or diagonal? test straight first and fall back to diagonal if it fails
				// priority is West > East > South > North > Southwest > Southeast > Northwest > Northeast
				int colliderToTest = -1;
				if (dx == -2)
					colliderToTest = CollisionDataFlag.BLOCK_MOVEMENT_WEST;
				else if (dx == 2)
					colliderToTest = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
				else if (dy == -2)
					colliderToTest = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
				else if (dy == 2)
					colliderToTest = CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
				else
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Error with collision detection. (2)", null);
				
				if (colliderToTest != -1 && (colliderFlag & colliderToTest) != 0)
				{
					// we've established that the cardinal direction is blocked, so let's go along the diagonal
					if (Math.abs(dx) == 2)
						dx /= 2;
					else
						dy /= 2;
				}
				else
				{
					// the cardinal direction is clear (or we glitched), so let's go straight
					if (Math.abs(dx) == 2)
					{
						dx /= 2;
						dy = 0;
					}
					else
					{
						dx = 0;
						dy /= 2;
					}
				}
			}
			
			if (useMidPointTile)
			{
				WorldPoint midPoint = prevWorldPosition.dx(dx).dy(dy);
				
				// handle rotation if we have no interacting target
				if (!isInteracting)
				{
					// the actor needs to look in the direction being moved toward
					// the distance between these points should be guaranteed to be 1 here
					dx = midPoint.getX() - prevWorldPosition.getX();
					dy = midPoint.getY() - prevWorldPosition.getY();
					jauOrientation = JAU_DIRECTIONS_5X5[CENTER_DIRECTION_INDEX - dy][CENTER_DIRECTION_INDEX + dx];
				}
				
				this.targetQueue[newTargetIndex].worldDestinationPosition = midPoint;
				this.targetQueue[newTargetIndex].localDestinationPosition = LocalPoint.fromWorld(client, midPoint);
				this.targetQueue[newTargetIndex].tileMovementSpeed = distance; // can only be idle/tele (0), walk (1), or run (2)
				this.targetQueue[newTargetIndex].jauDestinationOrientation = jauOrientation;
				this.targetQueue[newTargetIndex].primaryAnimationID = primaryAnimationID;
				this.targetQueue[newTargetIndex].isInteracting = isInteracting;
				this.targetQueue[newTargetIndex].isMidPoint = true;
				
				newTargetIndex = (currentTargetIndex + targetQueueSize++) % MAX_TARGET_QUEUE_SIZE;
				prevWorldPosition = midPoint;
			}
			
			// handle rotation if we have no interacting target
			if (!isInteracting)
			{
				// the actor needs to look in the direction being moved toward
				// the distance between these points may be up to 2
				dx = position.getX() - prevWorldPosition.getX();
				dy = position.getY() - prevWorldPosition.getY();
				jauOrientation = JAU_DIRECTIONS_5X5[CENTER_DIRECTION_INDEX - dy][CENTER_DIRECTION_INDEX + dx];
			}
		}
		
		this.targetQueue[newTargetIndex].worldDestinationPosition = position;
		this.targetQueue[newTargetIndex].localDestinationPosition = LocalPoint.fromWorld(client, position);
		this.targetQueue[newTargetIndex].tileMovementSpeed = distance; // can only be idle/tele (0), walk (1), or run (2)
		this.targetQueue[newTargetIndex].jauDestinationOrientation = jauOrientation;
		this.targetQueue[newTargetIndex].primaryAnimationID = primaryAnimationID;
		this.targetQueue[newTargetIndex].isInteracting = isInteracting;
		this.targetQueue[newTargetIndex].isMidPoint = false;
	}
	
	public void onClientTick(ClientTick clientTick)
	{
		if (rlObject.isActive())
		{
			if (targetQueueSize > 0)
			{
				LocalPoint targetPosition = targetQueue[currentTargetIndex].localDestinationPosition;
				LocalPoint currentPosition = rlObject.getLocation();
				int targetOrientation = targetQueue[currentTargetIndex].jauDestinationOrientation;
				int currentOrientation = rlObject.getOrientation();
				int dx = targetPosition.getX() - currentPosition.getX();
				int dy = targetPosition.getY() - currentPosition.getY();
				
				// check if current speed and animation are correct
				if (currentMovementSpeed != targetQueue[currentTargetIndex].tileMovementSpeed)
				{
					currentMovementSpeed = targetQueue[currentTargetIndex].tileMovementSpeed;
					if (animationStall == 0 && targetQueue[currentTargetIndex].primaryAnimationID == -1)
					{
						rlObject.setAnimation(animationPoses[currentMovementSpeed]); // TODO: account for both primary animation and for interacting state
						//rlObject.setAnimation(client.loadAnimation(targetQueue[currentTargetIndex].primaryAnimationID));
						rlObject.setShouldLoop(true);
					}
				}
				//test
				if (targetQueue[currentTargetIndex].primaryAnimationID != -1)
				{
					if (!rlObject.finished())
					{
						rlObject.setAnimation(client.loadAnimation(targetQueue[currentTargetIndex].primaryAnimationID));
						rlObject.setShouldLoop(false);
					}
					else
					{
						rlObject.setAnimation(animationPoses[currentMovementSpeed]); // TODO: account for both primary animation and for interacting state
						//rlObject.setAnimation(client.loadAnimation(targetQueue[currentTargetIndex].primaryAnimationID));
						rlObject.setShouldLoop(true);
					}
				}
				
				// are we not where we need to be?
				if (dx != 0 || dy != 0)
				{
					// continue moving until we reach target
					int movementPerClientTick = 4;
					if (currentOrientation != targetOrientation && !targetQueue[currentTargetIndex].isInteracting)
						movementPerClientTick = 2;
					if (targetQueueSize > 2)
						movementPerClientTick = 6;
					if (targetQueueSize > 3)
						movementPerClientTick = 8;
					if (animationStall > 0 && targetQueueSize > 1)
					{
						movementPerClientTick = 8;
						animationStall--;
					}
					
					if (animationStall == 0)
					{
						// compute the number of local points to move this tick
						int speed = currentMovementSpeed * movementPerClientTick;
						if (Math.abs(dx) >= speed)
							dx = Integer.signum(dx) * speed;
						if (Math.abs(dy) >= speed)
							dy = Integer.signum(dy) * speed;
						
						LocalPoint newLocation = new LocalPoint(currentPosition.getX() + dx, currentPosition.getY() + dy);
						rlObject.setLocation(newLocation, targetQueue[currentTargetIndex].worldDestinationPosition.getPlane());
						
						// compute the turn we need to make
						final int JAU_FULL_ROTATION = 2048;
						final int JAU_HALF_ROTATION = 1024;
						final int JAU_TURN_SPEED = 32;
						int dJau = (targetOrientation - currentOrientation) % JAU_FULL_ROTATION;
						if (Math.abs(dJau) >= JAU_TURN_SPEED)
							dJau = Integer.signum(dJau) * JAU_TURN_SPEED;
						
						int newOrientation = (rlObject.getOrientation() + dJau) % JAU_FULL_ROTATION;
						rlObject.setOrientation(newOrientation);
					}
					
					currentPosition = rlObject.getLocation();
					dx = targetPosition.getX() - currentPosition.getX();
					dy = targetPosition.getY() - currentPosition.getY();
				}
				
				// have we arrived at our destination? let's ignore orientation for now
				if (dx == 0 && dy == 0)
				{
					// if so, pull out the next target
					currentTargetIndex = (currentTargetIndex + 1) % MAX_TARGET_QUEUE_SIZE;
					targetQueueSize--;
				}
			}
		}
	}
	
	public void printDebug()
	{
		//client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", Integer.toString(currentTargetIndex) + " " + Integer.toString(targetQueueSize) + " " + Integer.toString(targetQueue[currentTargetIndex].primaryAnimationID), null);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", Integer.toString(rlObject.getOrientation()), null);//.getX()) + ", " + rlObject.getLocation().getY(), null);
	}
}
