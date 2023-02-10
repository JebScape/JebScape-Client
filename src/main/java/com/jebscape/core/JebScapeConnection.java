package com.jebscape.core;

import java.net.*;
import java.nio.channels.*;

public class JebScapeConnection
{
	private DatagramChannel gameChannel;
	private DatagramChannel chatChannel;
	private InetSocketAddress gameAddress;
	private InetSocketAddress chatAddress;
	
	private boolean isLoggedIn;
	// ticks are tracked as a cyclic value between 0 and TICKS_UNTIL_LOGOUT
	private int currentGameTick; // value representing the current tick the client thinks we should be on
	private int lastReceivedGameTick; // value representing the latest tick we've received from the server
	private int gameServerTickExecutionTime; // value in ms sent by the server for the time it took to process packets
	public static final int TICKS_UNTIL_LOGOUT = 16;
	public static final int SERVER_PACKETS_PER_TICK = 16;
	private long accountHash;
	private long accountKey;
	private boolean isUsingKey;
	private String accountName;
	private static final byte[] EMPTY_NAME_BYTES = new byte[12];
	private int gameSessionID = -1;
	private int chatSessionID = -1;
	private int gameServerPacketsReceived = 0x0000; // 1 bit per server packet
	private int chatServerPacketsReceived = 0x0000; // 1 bit per server packet
	
	private static final int PROTOCOL_VERSION = 1;
	private static final int EMPTY_PACKET = 0x0;
	private static final int LOGIN_PACKET = 0x1;
	private static final int GAME_PACKET = 0x2;
	private static final int CHAT_PACKET = 0x3;
	
	private static final int GAME_CLIENT_PACKET_SIZE = 64;
	private static final int CHAT_CLIENT_PACKET_SIZE = 128;
	private static final int GAME_SERVER_PACKET_SIZE = 128;
	private static final int CHAT_SERVER_PACKET_SIZE = 128;
	
	private JebScapePacket gameClientPacket = new JebScapePacket();
	private JebScapePacket chatClientPacket = new JebScapePacket();
	private JebScapePacket gameServerPacket = new JebScapePacket();
	private JebScapePacket chatServerPacket = new JebScapePacket();
	private JebScapeServerData[][] gameServerData = new JebScapeServerData[TICKS_UNTIL_LOGOUT][SERVER_PACKETS_PER_TICK];
	private JebScapeServerData[][] chatServerData = new JebScapeServerData[TICKS_UNTIL_LOGOUT][SERVER_PACKETS_PER_TICK];
	private int[] numGameServerPacketsSent = new int[TICKS_UNTIL_LOGOUT];
	
	public void init() throws Exception
	{
		gameAddress = new InetSocketAddress("game.jebscape.com", 43596);
		// local test server connection
		//gameAddress = new InetSocketAddress("192.168.1.148", 43596);
		//chatAddress = new InetSocketAddress("192.168.1.148", 43597);
		
		gameChannel = DatagramChannel.open();
		gameChannel.configureBlocking(false);
		gameChannel.bind(null);
		
		isLoggedIn = false;
		currentGameTick = 0;
		
		gameClientPacket.init(GAME_CLIENT_PACKET_SIZE);
		chatClientPacket.init(CHAT_CLIENT_PACKET_SIZE);
		
		gameServerPacket.init(GAME_SERVER_PACKET_SIZE);
		gameServerPacket.erase();
		
		chatServerPacket.init(CHAT_SERVER_PACKET_SIZE);
		chatServerPacket.erase();
		
		for (int i = 0; i < TICKS_UNTIL_LOGOUT; i++)
			for (int j = 0; j < SERVER_PACKETS_PER_TICK; j++)
				gameServerData[i][j] = new JebScapeServerData();
	}
	
	public boolean connect()
	{
		boolean connected;
		
		try
		{
			if (!gameChannel.isConnected())
				gameChannel.connect(gameAddress);
			
			connected = gameChannel.isConnected();
		}
		catch (Exception e)
		{
			connected = false;
		}
		
		return connected;
	}
	
	public void disconnect()
	{
		isLoggedIn = false;
		gameSessionID = -1;
		
		try
		{
			if (gameChannel.isConnected())
				gameChannel.disconnect();
		}
		catch (Exception e)
		{}
	}
	
	public boolean isConnected()
	{
		return gameChannel.isConnected();
	}
	
	public boolean isLoggedIn()
	{
		return isConnected() && isLoggedIn;
	}
	
	public boolean login(long accountHash, long accountKey, String accountName, boolean useKey)
	{
		if (!gameChannel.isConnected())
			connect();
		
		if (!gameChannel.isConnected() || isLoggedIn || accountName.length() > 12)
			return false;
			
		this.accountHash = accountHash; // 8 bytes
		this.accountKey = accountKey; // 8 bytes
		this.accountName = accountName; // 12 bytes
		this.isUsingKey = useKey;
		gameServerPacketsReceived = 0xFFFF;
		
		// set the header
		// 2 bits login identifier
		// 17 bits last known game session id
		// 1 bit isUsingKey (if false, will log in as guest only)
		// 8 bits protocol version
		// 4 bits reserved
		int loginPacketHeader = LOGIN_PACKET & 0x3;					// 2/32 bits
		loginPacketHeader |= (gameSessionID & 0x1FFFF) << 2;		// 19/32 bits
		loginPacketHeader |= (isUsingKey ? 0x1 : 0x0) << 19;		// 20/32 bits
		loginPacketHeader |= (PROTOCOL_VERSION & 0xFF) << 20;		// 28/32 bits
		loginPacketHeader |= 0xF << 28;								// 32/32 bits
		
		byte[] nameBytes = accountName.getBytes();
		int strLen = accountName.length();
		int reserved = 0xFFFFFFFF;
		
		try
		{
			gameClientPacket.buffer.clear();
			gameClientPacket.buffer.putInt(loginPacketHeader);								// 4/64 bytes
			gameClientPacket.buffer.putLong(accountHash);									// 12/64 bytes
			gameClientPacket.buffer.putLong(accountKey);									// 20/64 bytes
			gameClientPacket.buffer.put(nameBytes, 0, strLen);						// up to 32/64 bytes
			if (strLen < 12)
				gameClientPacket.buffer.put(EMPTY_NAME_BYTES, 0, 12 - strLen);	// 32/64 bytes
			gameClientPacket.buffer.putInt(reserved);										// 36/64 bytes
			gameClientPacket.buffer.putInt(reserved);										// 40/64 bytes
			gameClientPacket.buffer.putInt(reserved);										// 44/64 bytes
			gameClientPacket.buffer.putInt(reserved);										// 48/64 bytes
			gameClientPacket.buffer.putInt(reserved);										// 52/64 bytes
			gameClientPacket.buffer.putInt(reserved);										// 56/64 bytes
			gameClientPacket.buffer.putInt(reserved);										// 60/64 bytes
			gameClientPacket.buffer.putInt(reserved);										// 64/64 bytes
			gameClientPacket.buffer.rewind();
			
			int bytesWritten = gameChannel.write(gameClientPacket.buffer);
			
			// TODO: add chat login
			return bytesWritten == GAME_CLIENT_PACKET_SIZE;
		}
		catch (Exception e)
		{
			return false;
		}
	}
	
	public void logout()
	{
		isLoggedIn = false;
		accountHash = 0;
		accountKey = 0;
		accountName = "";
		isUsingKey = false;
		currentGameTick = 0;
		lastReceivedGameTick = 0;
		gameSessionID = -1;
	}
	
	public long getAccountHash()
	{
		return accountHash;
	}
	
	public boolean isGuest()
	{
		return isLoggedIn && !isUsingKey;
	}
	
	// returns data on all packets received in this tick, but may include late packets from previous ticks
	// start processing from one after lastReceivedGameTick until one wraps around back to lastReceivedGameTick
	public JebScapeServerData[][] getRecentGameServerData()
	{
		return gameServerData;
	}
	
	public int[] getNumGameServerPacketsSent()
	{
		return numGameServerPacketsSent;
	}
	
	public int getLastReceivedGameTick()
	{
		return lastReceivedGameTick;
	}
	
	public int getCurrentGameTick()
	{
		return currentGameTick;
	}
	
	public int getGameServerTickExecutionTime()
	{
		return gameServerTickExecutionTime;
	}
	
	// must be 3 ints (12 bytes); in the future, this may be up to 11 ints (44 bytes)
	public boolean sendGameData(int dataA, int dataB, int dataC)
	{
		if (!gameChannel.isConnected() || !isLoggedIn)
			return false;
		
		// set the header
		// 2 bits game identifier
		// 17 bits last known game session id
		// 1 bit isUsingKey
		// 4 bits current tick
		// 8 bits reserved
		int gamePacketHeader = GAME_PACKET & 0x3;				// 2/32 bits
		gamePacketHeader |= (gameSessionID & 0x1FFFF) << 2;		// 19/32 bits
		gamePacketHeader |= (isUsingKey ? 0x1 : 0x0) << 19;		// 20/32 bits
		gamePacketHeader |= (currentGameTick & 0xF) << 20;		// 24/32 bits
		gamePacketHeader |= 0xFF << 24;							// 32/32 bits
		
		int reserved = 0xFFFFFFFF;
		
		try
		{
			gameClientPacket.buffer.clear();
			gameClientPacket.buffer.putInt(gamePacketHeader);	// 4/32 bytes
			gameClientPacket.buffer.putLong(accountHash);		// 12/64 bytes
			gameClientPacket.buffer.putLong(accountKey);		// 20/64 bytes
			gameClientPacket.buffer.putInt(dataA);				// 24/64 bytes
			gameClientPacket.buffer.putInt(dataB);				// 28/64 bytes
			gameClientPacket.buffer.putInt(dataC);				// 32/64 bytes
			gameClientPacket.buffer.putInt(reserved);			// 36/64 bytes
			gameClientPacket.buffer.putInt(reserved);			// 40/64 bytes
			gameClientPacket.buffer.putInt(reserved);			// 44/64 bytes
			gameClientPacket.buffer.putInt(reserved);			// 48/64 bytes
			gameClientPacket.buffer.putInt(reserved);			// 52/64 bytes
			gameClientPacket.buffer.putInt(reserved);			// 56/64 bytes
			gameClientPacket.buffer.putInt(reserved);			// 60/64 bytes
			gameClientPacket.buffer.putInt(reserved);			// 64/64 bytes
			gameClientPacket.buffer.rewind();
			
			int bytesWritten = gameChannel.write(gameClientPacket.buffer);
			return bytesWritten == GAME_CLIENT_PACKET_SIZE;
		}
		catch (Exception e)
		{
			return false;
		}
	}
	
	public void onGameTick()
	{
		if (!gameChannel.isConnected())
			return;
		
		// start from scratch on the data we're working with
		for (int i = 0; i < TICKS_UNTIL_LOGOUT; i++)
		{
			numGameServerPacketsSent[i] = 0;
			for (int j = 0; j < SERVER_PACKETS_PER_TICK; j++)
				gameServerData[i][j].clear();
		}
		
		try
		{
			// let's look into what server communications we've received...
			int bytesReceived;
			do
			{
				gameServerPacket.erase();
				gameServerPacket.buffer.clear();
				bytesReceived = gameChannel.read(gameServerPacket.buffer);
				gameServerPacket.buffer.rewind();
				
				if (bytesReceived == GAME_SERVER_PACKET_SIZE)
				{
					int packetHeader = gameServerPacket.buffer.getInt();
					
					// validate packet header (similar schema as game packet)
					// 2 bits type identifier
					// 17 bits session id
					// 1 bit isUsingKey; logged in (0) as guest w/o key or (1) as secured account w/ key
					// 4 bits current tick
					// 4 bits number of packets sent this tick
					// 4 bits packet id
					int newPacketType = packetHeader & 0x3;							// 2/32 bits
					int newSessionID = (packetHeader >>> 2) & 0x1FFFF;				// 19/32 bits
					boolean newIsUsingKey = ((packetHeader >>> 19) & 0x1) == 0x1;	// 20/32 bits
					int newTick = (packetHeader >>> 20) & 0xF;						// 24/32 bits
					int newNumPacketsSent = (packetHeader >>> 24) & 0xF;			// 28/32 bits
					int newPacketID = (packetHeader >>> 28) & 0xF;					// 32/32 bits
					
					if (newPacketType == LOGIN_PACKET)
					{
						// we've received an ACK from the server for our login request
						isLoggedIn = true;
						isUsingKey = newIsUsingKey; // if we made a request to log in with a key that was denied, it may allow us in as a guest anyway
						gameSessionID = newSessionID;
						currentGameTick = newTick;
						lastReceivedGameTick = newTick;
						// we're not using the remaining bytes of data, so let's just proceed with normal ticking
					}
					else if (isLoggedIn && newPacketType == GAME_PACKET && gameSessionID == newSessionID)
					{
						// place the latest tick info here
						currentGameTick = newTick;
						lastReceivedGameTick = newTick;
						gameServerData[newTick][newPacketID].setData(gameServerPacket);
						numGameServerPacketsSent[newTick] = newNumPacketsSent + 1; // we store in the range of 0-15 to represent 1-16
					}
				}
			} while (bytesReceived > 0);
		}
		catch (Exception e)
		{
			// not really sure what we want to do here...
		}
		
		if (isLoggedIn)
		{
			int prevReceivedGameTick = lastReceivedGameTick;
			
			// let's analyze the packets we've received to identify the most recent game tick received
			for (int i = 0; i < TICKS_UNTIL_LOGOUT; i++)
			{
				// start at the last received tick and move forward from there
				// we include it in case some late packets arrive
				int tick = (prevReceivedGameTick + i) % TICKS_UNTIL_LOGOUT;
				if (numGameServerPacketsSent[tick] > 0)
				{
					currentGameTick = tick;
					lastReceivedGameTick = tick;
				}
			}
			
			// let's analyze what packets are missing from the most recent game tick received
			// TODO: not currently used; reanalyze to determine if this is necessary
			gameServerPacketsReceived = 0;
			if (numGameServerPacketsSent[lastReceivedGameTick] > 0)
				for (int packetID = 0; packetID < SERVER_PACKETS_PER_TICK; packetID++)
					gameServerPacketsReceived = (!gameServerData[lastReceivedGameTick][packetID].isEmpty() ? 0x1 : 0x0) << packetID;
			
			// increment current tick to prepare for the next payload
			// the gap between currentGameTick and lastReceivedGameTick shall grow if no packets are received
			currentGameTick = (currentGameTick + 1) % TICKS_UNTIL_LOGOUT;
			
			// if we've cycled around back to the beginning, we've timed out
			if (currentGameTick == lastReceivedGameTick)
				logout();
		}
	}
}
