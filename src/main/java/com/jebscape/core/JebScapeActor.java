package com.jebscape.megaserver;

import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;

public class JebScapeActor
{
	private Client client;
	private RuneLiteObject rlObject;
	private WorldPoint worldDestinationPosition;
	private LocalPoint localDestinationPosition;
	private int tileMovementSpeed;
	private int jauDestinationOrientation;
	private int turnDirection; // negative is ccw, positive is cw
	private int rotationDelta;
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
	Animation[] animationTypes = new Animation[8];
	
	
	public void init(Client client)
	{
		this.client = client;
		this.rlObject = client.createRuneLiteObject();
	}
	public void setModel(Model model)
	{
		rlObject.setModel(model);
	}
	
	public void setAnimationPoses(Actor actor)
	{
		this.animationTypes[POSE_ANIM.IDLE.ordinal()] = client.loadAnimation(actor.getIdlePoseAnimation());
		this.animationTypes[POSE_ANIM.WALK.ordinal()] = client.loadAnimation(actor.getWalkAnimation());
		this.animationTypes[POSE_ANIM.RUN.ordinal()] = client.loadAnimation(actor.getRunAnimation());
		this.animationTypes[POSE_ANIM.WALK_ROTATE_180.ordinal()] = client.loadAnimation(actor.getWalkRotate180());
		this.animationTypes[POSE_ANIM.WALK_STRAFE_LEFT.ordinal()] = client.loadAnimation(actor.getWalkRotateLeft()); // rotate is a misnomer here
		this.animationTypes[POSE_ANIM.WALK_STRAFE_RIGHT.ordinal()] = client.loadAnimation(actor.getWalkRotateRight()); // rotate is a misnomer here
		this.animationTypes[POSE_ANIM.IDLE_ROTATE_LEFT.ordinal()] = client.loadAnimation(actor.getIdleRotateLeft());
		this.animationTypes[POSE_ANIM.IDLE_ROTATE_RIGHT.ordinal()] = client.loadAnimation(actor.getIdleRotateRight());
		rlObject.setShouldLoop(true);
	}
	
	public void spawn(WorldPoint position)
	{
		rlObject.setLocation(LocalPoint.fromWorld(client, position), position.getPlane());
		rlObject.setActive(true);
		rlObject.setAnimation(animationTypes[POSE_ANIM.IDLE.ordinal()]);
		rlObject.setShouldLoop(true);
		this.tileMovementSpeed = 0;
		this.turnDirection = 0;
	}
	
	public void despawn()
	{
		rlObject.setActive(false);
		this.tileMovementSpeed = 0;
		this.turnDirection = 0;
	}
	
	public void moveTo(WorldPoint position, int speed) // speed should be a power of 2; don't set as 3
	{
		this.worldDestinationPosition = position;
		this.localDestinationPosition = LocalPoint.fromWorld(client, position);
		
		int currentSpeed = tileMovementSpeed;
		
		// we've just need to teleport to location or have arrived at destination move
		if (speed <= 0 || speed > 4 ||
				(rlObject.getLocation().getX() == localDestinationPosition.getX() && rlObject.getLocation().getY() == localDestinationPosition.getY()))
		{
			this.tileMovementSpeed = 0;
			rlObject.setLocation(localDestinationPosition, worldDestinationPosition.getPlane());
		}
		else
		{
			this.tileMovementSpeed = speed;
		}
		
		if (currentSpeed != this.tileMovementSpeed)
		{
			rlObject.setAnimation(animationTypes[tileMovementSpeed > 2 ? 2 : tileMovementSpeed]); // idle = 0, walk = 1, run = 2 or greater
		}
	}
	
	public void turnTo(int jauOrientation)
	{
		this.jauDestinationOrientation = jauOrientation;
		
		int currentTurnDirection = turnDirection;
		
		final int JAU_HALF_ROTATION = 1024;
		final int JAU_FULL_ROTATION = 2048;
		this.rotationDelta = (JAU_HALF_ROTATION + jauOrientation - rlObject.getOrientation()) % JAU_FULL_ROTATION - JAU_HALF_ROTATION;
		if (rotationDelta == -JAU_HALF_ROTATION)
			this.rotationDelta = JAU_HALF_ROTATION;
		this.turnDirection = Integer.signum(rotationDelta);
		
		if (rotationDelta != 0)
		{
			if (currentTurnDirection != turnDirection)
			{
				if (tileMovementSpeed == 0)
				{
					if (rotationDelta < 0) // counter-clockwise turn
						rlObject.setAnimation(animationTypes[POSE_ANIM.IDLE_ROTATE_LEFT.ordinal()]);
					else // clock-wise turn
						rlObject.setAnimation(animationTypes[POSE_ANIM.IDLE_ROTATE_RIGHT.ordinal()]);
				}
				else if (tileMovementSpeed == 1)
				{
					if (Math.abs(rotationDelta) == JAU_HALF_ROTATION) // 180 degree turn
						rlObject.setAnimation(animationTypes[POSE_ANIM.WALK_ROTATE_180.ordinal()]);
					//else if (rotationDelta < 0) // counter-clockwise turn
					//	rlObject.setAnimation(animationTypes[POSE_ANIM.WALK_STRAFE_LEFT.ordinal()]);
					//else // clock-wise turn
					//	rlObject.setAnimation(animationTypes[POSE_ANIM.WALK_STRAFE_RIGHT.ordinal()]);
				}
			}
		}
		else
		{
			// we've arrived at destination turn already
			this.turnDirection = 0;
			
			//if (currentTurnDirection != turnDirection && tileMovementSpeed < 2)
			//	rlObject.setAnimation(animationTypes[tileMovementSpeed]); // idle = 0, walk = 1
		}
	}
	
	public WorldPoint getWorldLocation()
	{
		return WorldPoint.fromLocal(client, rlObject.getLocation());
	}
	
	public boolean isRunning()
	{
		return tileMovementSpeed > 1;
	}
	
	public boolean isWalking()
	{
		return tileMovementSpeed == 1;
	}
	
	public boolean isMoving()
	{
		return tileMovementSpeed > 0;
	}
	
	public void onClientTick(ClientTick clientTick)
	{
		if (rlObject.isActive())
		{
			// handle moving
			// TODO: handle intermediate tile with turns (collider map?)
			// TODO: fix bug with disappearing units (active state goes off?)
			if (tileMovementSpeed > 0)
			{
				LocalPoint currentLocation = rlObject.getLocation();
				int currentX = currentLocation.getX();
				int currentY = currentLocation.getY();
				
				// if turning, move half the speed
				final int LOCAL_MOVE_SPEED = 4;
				final int LOCAL_TURN_MOVE_SPEED = 2;
				int speed = tileMovementSpeed * ((turnDirection == 0) ? LOCAL_MOVE_SPEED : LOCAL_TURN_MOVE_SPEED);
				
				int dx = Integer.signum(localDestinationPosition.getX() - currentX) * speed;
				int dy = Integer.signum(localDestinationPosition.getY() - currentY) * speed;
				rlObject.setLocation(new LocalPoint(currentX + dx, currentY + dy), worldDestinationPosition.getPlane());
				
				//if (rlObject.getLocation().getX() == currentX && rlObject.getLocation().getY() == currentY)
				//	this.tileMovementSpeed = 0;
			}
			
			// handle turning
			if (turnDirection != 0)
			{
				final int JAU_TURN_SPEED = 32;
				final int JAU_FULL_ROTATION = 2048;
				int turn = turnDirection * JAU_TURN_SPEED;
				int newOrientation = rlObject.getOrientation() + turn;
				
				if ((newOrientation - jauDestinationOrientation) % JAU_FULL_ROTATION < turn)
					newOrientation = jauDestinationOrientation;
				else
					newOrientation = newOrientation % JAU_FULL_ROTATION;
				
				rlObject.setOrientation(newOrientation);
				
				if (newOrientation == jauDestinationOrientation)
					this.turnDirection = 0;
			}
		}
	}
	
	public void printDebug()
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", Integer.toString(jauDestinationOrientation) + " " + Integer.toString(rlObject.getOrientation()) + " " + Integer.toString(rotationDelta), null);
	}
}
package com.jebscape.megaserver;
		
		import net.runelite.api.*;
		import net.runelite.api.coords.*;
		import net.runelite.api.events.*;

public class JebScapeActor
{
	private Client client;
	private RuneLiteObject rlObject;
	private WorldPoint worldDestinationPosition;
	private LocalPoint localDestinationPosition;
	private int tileMovementSpeed;
	private int jauDestinationOrientation;
	private int turnDirection; // negative is ccw, positive is cw
	private int rotationDelta;
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
	Animation[] animationTypes = new Animation[8];
	
	
	public void init(Client client)
	{
		this.client = client;
		this.rlObject = client.createRuneLiteObject();
	}
	public void setModel(Model model)
	{
		rlObject.setModel(model);
	}
	
	public void setAnimationPoses(Actor actor)
	{
		this.animationTypes[POSE_ANIM.IDLE.ordinal()] = client.loadAnimation(actor.getIdlePoseAnimation());
		this.animationTypes[POSE_ANIM.WALK.ordinal()] = client.loadAnimation(actor.getWalkAnimation());
		this.animationTypes[POSE_ANIM.RUN.ordinal()] = client.loadAnimation(actor.getRunAnimation());
		this.animationTypes[POSE_ANIM.WALK_ROTATE_180.ordinal()] = client.loadAnimation(actor.getWalkRotate180());
		this.animationTypes[POSE_ANIM.WALK_STRAFE_LEFT.ordinal()] = client.loadAnimation(actor.getWalkRotateLeft()); // rotate is a misnomer here
		this.animationTypes[POSE_ANIM.WALK_STRAFE_RIGHT.ordinal()] = client.loadAnimation(actor.getWalkRotateRight()); // rotate is a misnomer here
		this.animationTypes[POSE_ANIM.IDLE_ROTATE_LEFT.ordinal()] = client.loadAnimation(actor.getIdleRotateLeft());
		this.animationTypes[POSE_ANIM.IDLE_ROTATE_RIGHT.ordinal()] = client.loadAnimation(actor.getIdleRotateRight());
		rlObject.setShouldLoop(true);
	}
	
	public void spawn(WorldPoint position)
	{
		rlObject.setLocation(LocalPoint.fromWorld(client, position), position.getPlane());
		rlObject.setActive(true);
		rlObject.setAnimation(animationTypes[POSE_ANIM.IDLE.ordinal()]);
		rlObject.setShouldLoop(true);
		this.tileMovementSpeed = 0;
		this.turnDirection = 0;
	}
	
	public void despawn()
	{
		rlObject.setActive(false);
		this.tileMovementSpeed = 0;
		this.turnDirection = 0;
	}
	
	public void moveTo(WorldPoint position, int speed) // speed should be a power of 2; don't set as 3
	{
		this.worldDestinationPosition = position;
		this.localDestinationPosition = LocalPoint.fromWorld(client, position);
		
		int currentSpeed = tileMovementSpeed;
		
		// we've just need to teleport to location or have arrived at destination move
		if (speed <= 0 || speed > 4 ||
				(rlObject.getLocation().getX() == localDestinationPosition.getX() && rlObject.getLocation().getY() == localDestinationPosition.getY()))
		{
			this.tileMovementSpeed = 0;
			rlObject.setLocation(localDestinationPosition, worldDestinationPosition.getPlane());
		}
		else
		{
			this.tileMovementSpeed = speed;
		}
		
		if (currentSpeed != this.tileMovementSpeed)
		{
			rlObject.setAnimation(animationTypes[tileMovementSpeed > 2 ? 2 : tileMovementSpeed]); // idle = 0, walk = 1, run = 2 or greater
		}
	}
	
	public void turnTo(int jauOrientation)
	{
		this.jauDestinationOrientation = jauOrientation;
		
		int currentTurnDirection = turnDirection;
		
		final int JAU_HALF_ROTATION = 1024;
		final int JAU_FULL_ROTATION = 2048;
		this.rotationDelta = (JAU_HALF_ROTATION + jauOrientation - rlObject.getOrientation()) % JAU_FULL_ROTATION - JAU_HALF_ROTATION;
		if (rotationDelta == -JAU_HALF_ROTATION)
			this.rotationDelta = JAU_HALF_ROTATION;
		this.turnDirection = Integer.signum(rotationDelta);
		
		if (rotationDelta != 0)
		{
			if (currentTurnDirection != turnDirection)
			{
				if (tileMovementSpeed == 0)
				{
					if (rotationDelta < 0) // counter-clockwise turn
						rlObject.setAnimation(animationTypes[POSE_ANIM.IDLE_ROTATE_LEFT.ordinal()]);
					else // clock-wise turn
						rlObject.setAnimation(animationTypes[POSE_ANIM.IDLE_ROTATE_RIGHT.ordinal()]);
				}
				else if (tileMovementSpeed == 1)
				{
					if (Math.abs(rotationDelta) == JAU_HALF_ROTATION) // 180 degree turn
						rlObject.setAnimation(animationTypes[POSE_ANIM.WALK_ROTATE_180.ordinal()]);
					//else if (rotationDelta < 0) // counter-clockwise turn
					//	rlObject.setAnimation(animationTypes[POSE_ANIM.WALK_STRAFE_LEFT.ordinal()]);
					//else // clock-wise turn
					//	rlObject.setAnimation(animationTypes[POSE_ANIM.WALK_STRAFE_RIGHT.ordinal()]);
				}
			}
		}
		else
		{
			// we've arrived at destination turn already
			this.turnDirection = 0;
			
			//if (currentTurnDirection != turnDirection && tileMovementSpeed < 2)
			//	rlObject.setAnimation(animationTypes[tileMovementSpeed]); // idle = 0, walk = 1
		}
	}
	
	public WorldPoint getWorldLocation()
	{
		return WorldPoint.fromLocal(client, rlObject.getLocation());
	}
	
	public boolean isRunning()
	{
		return tileMovementSpeed > 1;
	}
	
	public boolean isWalking()
	{
		return tileMovementSpeed == 1;
	}
	
	public boolean isMoving()
	{
		return tileMovementSpeed > 0;
	}
	
	public void onClientTick(ClientTick clientTick)
	{
		if (rlObject.isActive())
		{
			// handle moving
			// TODO: handle intermediate tile with turns (collider map?)
			// TODO: fix bug with disappearing units (active state goes off?)
			if (tileMovementSpeed > 0)
			{
				LocalPoint currentLocation = rlObject.getLocation();
				int currentX = currentLocation.getX();
				int currentY = currentLocation.getY();
				
				// if turning, move half the speed
				final int LOCAL_MOVE_SPEED = 4;
				final int LOCAL_TURN_MOVE_SPEED = 2;
				int speed = tileMovementSpeed * ((turnDirection == 0) ? LOCAL_MOVE_SPEED : LOCAL_TURN_MOVE_SPEED);
				
				int dx = Integer.signum(localDestinationPosition.getX() - currentX) * speed;
				int dy = Integer.signum(localDestinationPosition.getY() - currentY) * speed;
				rlObject.setLocation(new LocalPoint(currentX + dx, currentY + dy), worldDestinationPosition.getPlane());
				
				//if (rlObject.getLocation().getX() == currentX && rlObject.getLocation().getY() == currentY)
				//	this.tileMovementSpeed = 0;
			}
			
			// handle turning
			if (turnDirection != 0)
			{
				final int JAU_TURN_SPEED = 32;
				final int JAU_FULL_ROTATION = 2048;
				int turn = turnDirection * JAU_TURN_SPEED;
				int newOrientation = rlObject.getOrientation() + turn;
				
				if ((newOrientation - jauDestinationOrientation) % JAU_FULL_ROTATION < turn)
					newOrientation = jauDestinationOrientation;
				else
					newOrientation = newOrientation % JAU_FULL_ROTATION;
				
				rlObject.setOrientation(newOrientation);
				
				if (newOrientation == jauDestinationOrientation)
					this.turnDirection = 0;
			}
		}
	}
	
	public void printDebug()
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", Integer.toString(jauDestinationOrientation) + " " + Integer.toString(rlObject.getOrientation()) + " " + Integer.toString(rotationDelta), null);
	}
}
