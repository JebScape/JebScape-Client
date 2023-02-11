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
		public boolean isPoseAnimation;
		public boolean isInteracting;
		public boolean isMidPoint;
	}
	private final int MAX_TARGET_QUEUE_SIZE = 10;
	private Target[] targetQueue = new Target[MAX_TARGET_QUEUE_SIZE];
	private int currentTargetIndex;
	private int targetQueueSize;
	
	private int currentMovementSpeed;
	private int currentAnimationID;
	private int animationStall; // stalls movement animations while playing certain primary animations
	
	
	private static final int BLOCKING_DIRECTIONS_5x5[][] = {
			{CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST,	CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST,	CollisionDataFlag.BLOCK_MOVEMENT_SOUTH,	CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST,	CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST},
			{CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST,	CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST,	CollisionDataFlag.BLOCK_MOVEMENT_SOUTH,	CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST,	CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST},
			{CollisionDataFlag.BLOCK_MOVEMENT_EAST,			CollisionDataFlag.BLOCK_MOVEMENT_EAST,			0,										CollisionDataFlag.BLOCK_MOVEMENT_WEST,			CollisionDataFlag.BLOCK_MOVEMENT_WEST},
			{CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST,	CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST,	CollisionDataFlag.BLOCK_MOVEMENT_NORTH,	CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST,	CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST},
			{CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST,	CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST,	CollisionDataFlag.BLOCK_MOVEMENT_NORTH,	CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST,	CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST}};
	
	private static final int JAU_DIRECTIONS_5X5[][] = {
			{768,	768,	1024,	1280,	1280},
			{768,	768,	1024,	1280,	1280},
			{512,	512,	0,		1536,	1536},
			{256,	256,	0,		1792,	1792},
			{256,	256,	0,		1792,	1792}};
	private static final int CENTER_INDEX_5X5 = 2;
	
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
	
	public void spawn(WorldPoint position, int jauOrientation)
	{
		LocalPoint localPosition = LocalPoint.fromWorld(client, position);
		if (localPosition != null && client.getPlane() == position.getPlane())
			rlObject.setLocation(localPosition, position.getPlane());
		else
			rlObject.setLocation(new LocalPoint(0, 0), client.getPlane());
		rlObject.setOrientation(jauOrientation);
		rlObject.setAnimation(null);
		rlObject.setShouldLoop(true);
		rlObject.setActive(true);
		this.currentAnimationID = -1;
		this.currentMovementSpeed = 0;
		this.currentTargetIndex = 0;
		this.targetQueueSize = 0;
	}
	
	public void despawn()
	{
		rlObject.setActive(false);
		this.currentAnimationID = -1;
		this.currentMovementSpeed = 0;
		this.currentTargetIndex = 0;
		this.targetQueueSize = 0;
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
	}
	
	public WorldPoint getWorldLocation()
	{
		return targetQueueSize > 0 ? targetQueue[currentTargetIndex].worldDestinationPosition : WorldPoint.fromLocal(client, rlObject.getLocation());
	}
	
	// moveTo() adds target movement states to the queue for later per-frame updating for rendering in onClientTick()
	// Set this every game tick for each new position (usually only up to 2 tiles out)
	// This is not set up for pathfinding to the final destination of distant targets (you will just move there directly)
	// It will, however, handle nearby collision detection (1-2 tiles away from you) under certain scenarios
	// jauOrientation is not used if isInteracting is false; it will instead default to the angle being moved towards
	public void moveTo(WorldPoint worldPosition, int jauOrientation, int primaryAnimationID, boolean isInteracting, boolean isPoseAnimation)
	{
		// respawn this actor if it was previously despawned
		if (!rlObject.isActive())
			spawn(worldPosition, jauOrientation);
		
		// just clear the queue and move immediately to the destination if many ticks behind
		if (targetQueueSize >= MAX_TARGET_QUEUE_SIZE - 2)
			targetQueueSize = 0;
		
		int prevTargetIndex = (currentTargetIndex + targetQueueSize - 1) % MAX_TARGET_QUEUE_SIZE;
		int newTargetIndex = (currentTargetIndex + targetQueueSize) % MAX_TARGET_QUEUE_SIZE;
		LocalPoint localPosition = LocalPoint.fromWorld(client, worldPosition);
		
		if (localPosition == null)
			return;
		
		// use current position if nothing is in queue
		WorldPoint prevWorldPosition;
		if (targetQueueSize++ > 0)
		{
			prevWorldPosition = targetQueue[prevTargetIndex].worldDestinationPosition;
			// TODO: check if a different primaryAnimationID exists; if so, modify the old one with our new one (hopefully this prevents the extra tick of animation repeating)
		}
		else
		{
			prevWorldPosition = WorldPoint.fromLocal(client, rlObject.getLocation());
		}
		
		int distance = prevWorldPosition.distanceTo(worldPosition);
		if (distance > 0 && distance <= 2)
		{
			int dx = worldPosition.getX() - prevWorldPosition.getX();
			int dy = worldPosition.getY() - prevWorldPosition.getY();
			
			boolean useMidPointTile = false;
			
			if (distance == 1 && dx != 0 && dy != 0) // test for blockage along diagonal
			{
				// if blocked diagonally, go around in an L shape (2 options)
				int[][] colliders = client.getCollisionMaps()[worldPosition.getPlane()].getFlags();
				final int diagonalTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5 + dx];
				final int axisXTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5][CENTER_INDEX_5X5 + dx] | BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 + dy][CENTER_INDEX_5X5] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				final int axisYTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5] | BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5][CENTER_INDEX_5X5 - dx] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				
				int diagonalFlag = colliders[localPosition.getSceneX()][localPosition.getSceneY()];
				int axisXFlag = colliders[localPosition.getSceneX()][localPosition.getSceneY() - dy];
				int axisYFlag = colliders[localPosition.getSceneX() - dx][localPosition.getSceneY()];
				
				if ((axisXFlag & axisXTest) != 0 || (axisYFlag & axisYTest) != 0 || (diagonalFlag & diagonalTest) != 0)
				{
					// the path along the diagonal is blocked
					useMidPointTile = true;
					distance = 2; // we are now running in an L shape
					
					// if the priority East-West path is clear, we'll default to this direction
					if ((axisXFlag & axisXTest) == 0)
						dy = 0;
					else
						dx = 0;
				}
			}
			else if (distance == 2 && Math.abs(Math.abs(dy) - Math.abs(dx)) == 1) // test for blockage along knight-style moves
			{
				useMidPointTile = true; // we will always need a midpoint for these types of moves
				int[][] colliders = client.getCollisionMaps()[worldPosition.getPlane()].getFlags();
				final int diagonalTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5 + dx];
				final int axisXTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5][CENTER_INDEX_5X5 + dx] | BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 + dy][CENTER_INDEX_5X5] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				final int axisYTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5] | BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5][CENTER_INDEX_5X5 - dx] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				
				int dxSign = Integer.signum(dx);
				int dySign = Integer.signum(dy);
				int diagonalFlag = colliders[localPosition.getSceneX()][localPosition.getSceneY()];
				int axisXFlag = colliders[localPosition.getSceneX()][localPosition.getSceneY() - Integer.signum(dySign)];
				int axisYFlag = colliders[localPosition.getSceneX() - Integer.signum(dxSign)][localPosition.getSceneY()];
				
				// do we go straight or diagonal? test straight first and fall back to diagonal if it fails
				// priority is West > East > South > North > Southwest > Southeast > Northwest > Northeast
				if ((axisXFlag & axisXTest) == 0 && (axisYFlag & axisYTest) == 0 && (diagonalFlag & diagonalTest) == 0)
				{
					// the cardinal direction is clear (or we glitched), so let's go straight
					if (Math.abs(dx) == 2)
					{
						dx = dxSign;
						dy = 0;
					}
					else
					{
						dx = 0;
						dy = dySign;
					}
				}
				else
				{
					// we've established that the cardinal direction is blocked, so let's go along the diagonal
					if (Math.abs(dx) == 2)
						dx = dxSign;
					else
						dy = dySign;
				}
			}
			
			if (useMidPointTile)
			{
				WorldPoint midPoint = new WorldPoint(prevWorldPosition.getX() + dx, prevWorldPosition.getY() + dy, prevWorldPosition.getPlane());
				
				// handle rotation if we have no interacting target
				if (!isInteracting)
				{
					// the actor needs to look in the direction being moved toward
					// the distance between these points should be guaranteed to be 1 here
					dx = midPoint.getX() - prevWorldPosition.getX();
					dy = midPoint.getY() - prevWorldPosition.getY();
					jauOrientation = JAU_DIRECTIONS_5X5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5 + dx];
				}
				
				this.targetQueue[newTargetIndex].worldDestinationPosition = midPoint;
				this.targetQueue[newTargetIndex].localDestinationPosition = LocalPoint.fromWorld(client, midPoint);
				this.targetQueue[newTargetIndex].tileMovementSpeed = distance;
				this.targetQueue[newTargetIndex].jauDestinationOrientation = jauOrientation;
				this.targetQueue[newTargetIndex].primaryAnimationID = primaryAnimationID;
				this.targetQueue[newTargetIndex].isPoseAnimation = isPoseAnimation;
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
				dx = worldPosition.getX() - prevWorldPosition.getX();
				dy = worldPosition.getY() - prevWorldPosition.getY();
				jauOrientation = JAU_DIRECTIONS_5X5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5 + dx];
			}
		}
		
		this.targetQueue[newTargetIndex].worldDestinationPosition = worldPosition;
		this.targetQueue[newTargetIndex].localDestinationPosition = localPosition;
		this.targetQueue[newTargetIndex].tileMovementSpeed = distance;
		this.targetQueue[newTargetIndex].jauDestinationOrientation = jauOrientation;
		this.targetQueue[newTargetIndex].primaryAnimationID = primaryAnimationID;
		this.targetQueue[newTargetIndex].isInteracting = isInteracting;
		this.targetQueue[newTargetIndex].isPoseAnimation = isPoseAnimation;
		this.targetQueue[newTargetIndex].isMidPoint = false;
	}
	
	// onClientTick() updates the per-frame state needed for rendering actor movement
	public boolean onClientTick(ClientTick clientTick)
	{
		if (rlObject.isActive())
		{
			if (targetQueueSize > 0)
			{
				int targetPlane = targetQueue[currentTargetIndex].worldDestinationPosition.getPlane();
				LocalPoint targetPosition = targetQueue[currentTargetIndex].localDestinationPosition;
				int targetOrientation = targetQueue[currentTargetIndex].jauDestinationOrientation;
				
				if (client.getPlane() != targetPlane || targetPosition == null || !targetPosition.isInScene() || targetOrientation < 0)
				{
					// this actor is no longer in a visible area on our client, so let's despawn it
					despawn();
					return false;
				}
				
				// handle animations - there's still some jankiness, but let's return to the basics for now...
				// TODO: handle animation stalling, remove extra tick of animation that's playing, strafing when interacting, and swapping out pose animations with new ones as needed...
				int animationID = targetQueue[currentTargetIndex].primaryAnimationID;
				if (!targetQueue[currentTargetIndex].isPoseAnimation && currentAnimationID != animationID)
				{
					rlObject.setAnimation(client.loadAnimation(animationID));
					this.currentAnimationID = animationID;
				}
				else if (targetQueue[currentTargetIndex].isPoseAnimation && (currentAnimationID != -1 || currentMovementSpeed != targetQueue[currentTargetIndex].tileMovementSpeed))
				{
					int speed = targetQueue[currentTargetIndex].tileMovementSpeed;
					// we don't want to go beyond run (speed of 2)
					rlObject.setAnimation(speed > 2 ? null : animationPoses[speed]);
					this.currentAnimationID = -1;
				}
				
				this.currentMovementSpeed = targetQueue[currentTargetIndex].tileMovementSpeed;
				
				LocalPoint currentPosition = rlObject.getLocation();
				int currentOrientation = rlObject.getOrientation();
				int dx = targetPosition.getX() - currentPosition.getX();
				int dy = targetPosition.getY() - currentPosition.getY();
				
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
						
						if (speed > 0)
						{
							// only use the delta if it won't send up past the target
							if (Math.abs(dx) > speed)
								dx = Integer.signum(dx) * speed;
							if (Math.abs(dy) > speed)
								dy = Integer.signum(dy) * speed;
						}
						
						LocalPoint newLocation = new LocalPoint(currentPosition.getX() + dx, currentPosition.getY() + dy);
						rlObject.setLocation(newLocation, targetPlane);
					}
					
					currentPosition = rlObject.getLocation();
					dx = targetPosition.getX() - currentPosition.getX();
					dy = targetPosition.getY() - currentPosition.getY();
				}
				
				// compute the turn we need to make
				final int JAU_FULL_ROTATION = 2048;
				int dJau = (targetOrientation - currentOrientation) % JAU_FULL_ROTATION;
				
				if (dJau != 0)
				{
					final int JAU_HALF_ROTATION = 1024;
					final int JAU_TURN_SPEED = 32;
					int dJauCW = Math.abs(dJau);
					
					if (dJauCW > JAU_HALF_ROTATION) // use the shortest turn
						dJau = (currentOrientation - targetOrientation) % JAU_FULL_ROTATION;
					else if (dJauCW == JAU_HALF_ROTATION) // always turn right when turning around
						dJau = dJauCW;
					
					// only use the delta if it won't send up past the target
					if (Math.abs(dJau) > JAU_TURN_SPEED)
						dJau = Integer.signum(dJau) * JAU_TURN_SPEED;
					
					int newOrientation = (JAU_FULL_ROTATION + rlObject.getOrientation() + dJau) % JAU_FULL_ROTATION;
					rlObject.setOrientation(newOrientation);
					dJau = (targetOrientation - newOrientation) % JAU_FULL_ROTATION;
				}
				
				// have we arrived at our target?
				if (dx == 0 && dy == 0 && dJau == 0)
				{
					// if so, pull out the next target
					currentTargetIndex = (currentTargetIndex + 1) % MAX_TARGET_QUEUE_SIZE;
					targetQueueSize--;
				}
			}
			
			return true;
		}
		
		return false;
	}
}
