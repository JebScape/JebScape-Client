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

import java.net.*;
import java.nio.channels.*;
import java.nio.charset.*;

public class JebScapeConnection
{
	private DatagramChannel gameChannel;
	private DatagramChannel chatChannel;
	private InetSocketAddress gameAddress;
	private InetSocketAddress chatAddress;
	
	private boolean isGameLoggedIn;
	private boolean isChatLoggedIn;
	// ticks are tracked as a cyclic value between 0 and TICKS_UNTIL_LOGOUT
	private int currentGameTick; // value representing the current tick the client thinks we should be on
	private int currentChatTick; // value representing the current tick the client thinks we should be on
	private int lastReceivedGameTick; // value representing the latest tick we've received from the server
	private int lastReceivedChatTick; // value representing the latest tick we've received from the server
	public static final int TICKS_UNTIL_LOGOUT = 16;
	public static final int GAME_SERVER_PACKETS_PER_TICK = 16;
	public static final int CHAT_SERVER_PACKETS_PER_TICK = 4;
	private long accountHash;
	private long gameAccountKey;
	private long chatAccountKey;
	private boolean isGameUsingKey;
	private boolean isChatUsingKey;
	private static final byte[] EMPTY_BYTES = new byte[96];
	private int gameSessionID = -1;
	private int chatSessionID = -1;
	private int gameServerPacketsReceived = 0x0000; // 1 bit per server packet
	private int chatServerPacketsReceived = 0x0000; // 1 bit per server packet
	private int gameNumOnlinePlayers = 0;
	private int chatNumOnlinePlayers = 0;
	
	private static final int PROTOCOL_VERSION = 2;
	private static final int EMPTY_PACKET = 0x0;
	private static final int LOGIN_PACKET = 0x1;
	private static final int GAME_PACKET = 0x2;
	private static final int CHAT_PACKET = 0x3;
	
	private static final int GAME_CLIENT_PACKET_SIZE = 64;
	private static final int CHAT_CLIENT_PACKET_SIZE = 128;
	private static final int GAME_SERVER_PACKET_SIZE = 128;
	private static final int CHAT_SERVER_PACKET_SIZE = 128;
	
	// TODO: Merge the two servers into one with 240 byte packet paylods.
	private JebScapePacket gameClientPacket = new JebScapePacket();
	private JebScapePacket chatClientPacket = new JebScapePacket();
	private JebScapePacket gameServerPacket = new JebScapePacket();
	private JebScapePacket chatServerPacket = new JebScapePacket();
	private JebScapeServerData[][] gameServerData = new JebScapeServerData[TICKS_UNTIL_LOGOUT][GAME_SERVER_PACKETS_PER_TICK];
	private JebScapeServerData[][] chatServerData = new JebScapeServerData[TICKS_UNTIL_LOGOUT][CHAT_SERVER_PACKETS_PER_TICK];
	private int[] numGameServerPacketsSent = new int[TICKS_UNTIL_LOGOUT];
	private int[] numChatServerPacketsSent = new int[TICKS_UNTIL_LOGOUT];
	
	public void init() throws Exception
	{
		gameAddress = new InetSocketAddress("game.jebscape.com", 43596);
		chatAddress = new InetSocketAddress("chat.jebscape.com", 43597);
		
		// local test server connection
		//gameAddress = new InetSocketAddress("192.168.1.148", 43596);
		//chatAddress = new InetSocketAddress("192.168.1.148", 43597);
		
		gameChannel = DatagramChannel.open(StandardProtocolFamily.INET);
		gameChannel.configureBlocking(false);
		gameChannel.bind(null);
		
		chatChannel = DatagramChannel.open(StandardProtocolFamily.INET);
		chatChannel.configureBlocking(false);
		chatChannel.bind(null);
		
		isGameLoggedIn = false;
		isChatLoggedIn = false;
		currentGameTick = 0;
		
		gameClientPacket.init(GAME_CLIENT_PACKET_SIZE);
		chatClientPacket.init(CHAT_CLIENT_PACKET_SIZE);
		
		gameServerPacket.init(GAME_SERVER_PACKET_SIZE);
		gameServerPacket.erase();
		
		chatServerPacket.init(CHAT_SERVER_PACKET_SIZE);
		chatServerPacket.erase();
		
		for (int i = 0; i < TICKS_UNTIL_LOGOUT; i++)
			for (int j = 0; j < GAME_SERVER_PACKETS_PER_TICK; j++)
				gameServerData[i][j] = new JebScapeServerData();
		
		for (int i = 0; i < TICKS_UNTIL_LOGOUT; i++)
			for (int j = 0; j < CHAT_SERVER_PACKETS_PER_TICK; j++)
				chatServerData[i][j] = new JebScapeServerData();
	}
	
	public boolean connect()
	{
		boolean connected;
		
		try
		{
			if (!gameChannel.isConnected())
				gameChannel.connect(gameAddress);
			
			if (!chatChannel.isConnected())
				chatChannel.connect(chatAddress);
			
			// we don't care as much if the chat server cannot connect, so let's proceed regardless
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
		isGameLoggedIn = false;
		isChatLoggedIn = false;
		gameSessionID = -1;
		chatSessionID = -1;
		
		try
		{
			if (chatChannel.isConnected())
				chatChannel.disconnect();
			
			if (gameChannel.isConnected())
				gameChannel.disconnect();
		}
		catch (Exception e)
		{}
	}
	
	public boolean isConnected()
	{
		return gameChannel.isConnected() && chatChannel.isConnected();
	}
	
	public boolean isLoggedIn()
	{
		return isConnected() && isGameLoggedIn && isChatLoggedIn;
	}
	
	public boolean isGameLoggedIn()
	{
		return isConnected() && isGameLoggedIn;
	}
	
	public boolean isChatLoggedIn()
	{
		return isConnected() && isChatLoggedIn;
	}
	
	public boolean login(long accountHash, long gameAccountKey, long chatAccountKey, boolean useKey, String accountName)
	{
		if (!gameChannel.isConnected() || !chatChannel.isConnected())
			connect();
			
		if (!gameChannel.isConnected() || !chatChannel.isConnected() || accountName.length() > 12)
			return false;
		
		this.accountHash = accountHash; // 8 bytes
		
		boolean success = true;
		
		if (!isGameLoggedIn)
		{
			this.gameAccountKey = gameAccountKey; // 8 bytes
			this.isGameUsingKey = useKey;
			gameServerPacketsReceived = 0xFFFF;
			
			// set the header
			// 2 bits login identifier
			// 17 bits last known game session id
			// 1 bit isUsingKey (if false, will log in as guest only)
			// 8 bits protocol version
			// 4 bits reserved
			int loginPacketHeader = LOGIN_PACKET & 0x3;					// 2/32 bits
			loginPacketHeader |= (gameSessionID & 0x1FFFF) << 2;		// 19/32 bits
			loginPacketHeader |= (isGameUsingKey ? 0x1 : 0x0) << 19;	// 20/32 bits
			loginPacketHeader |= (PROTOCOL_VERSION & 0xFF) << 20;		// 28/32 bits
			loginPacketHeader |= 0xF << 28;								// 32/32 bits
			
			byte[] nameBytes = accountName.getBytes(StandardCharsets.UTF_8);
			int strLen = accountName.length();
			long reserved = 0xFFFFFFFFFFFFFFFFL;
			
			try
			{
				gameClientPacket.buffer.clear();
				gameClientPacket.buffer.putInt(loginPacketHeader);								// 4/64 bytes
				gameClientPacket.buffer.putLong(accountHash);									// 12/64 bytes
				gameClientPacket.buffer.putLong(gameAccountKey);								// 20/64 bytes
				gameClientPacket.buffer.put(nameBytes, 0, strLen);						// up to 32/64 bytes
				if (strLen < 12)
					gameClientPacket.buffer.put(EMPTY_BYTES, 0, 12 - strLen);		// 32/64 bytes
				gameClientPacket.buffer.putLong(reserved);										// 40/64 bytes
				gameClientPacket.buffer.putLong(reserved);										// 48/64 bytes
				gameClientPacket.buffer.putLong(reserved);										// 56/64 bytes
				gameClientPacket.buffer.putLong(reserved);										// 64/64 bytes
				gameClientPacket.buffer.rewind();
				
				success = gameChannel.write(gameClientPacket.buffer) == GAME_CLIENT_PACKET_SIZE;
			}
			catch (Exception e)
			{
				// it's been observed that connections that sit idle long enough run the risk of being automatically closed, so let's recreate it just in case
				try
				{
					gameChannel = DatagramChannel.open(StandardProtocolFamily.INET);
					gameChannel.configureBlocking(false);
					gameChannel.bind(null);
					gameChannel.connect(gameAddress);
					success = gameChannel.write(gameClientPacket.buffer) == GAME_CLIENT_PACKET_SIZE;
				}
				catch (Exception exception)
				{
					return false;
				}
			}
		}
		
		if (!isChatLoggedIn)
		{
			this.chatAccountKey = chatAccountKey; // 8 bytes
			this.isChatUsingKey = useKey;
			chatServerPacketsReceived = 0xFFFF;
			
			// set the header
			// 2 bits login identifier
			// 17 bits last known game session id
			// 1 bit isUsingKey (if false, will log in as guest only)
			// 8 bits protocol version
			// 4 bits reserved
			int loginPacketHeader = LOGIN_PACKET & 0x3;					// 2/32 bits
			loginPacketHeader |= (chatSessionID & 0x1FFFF) << 2;		// 19/32 bits
			loginPacketHeader |= (isChatUsingKey ? 0x1 : 0x0) << 19;	// 20/32 bits
			loginPacketHeader |= (PROTOCOL_VERSION & 0xFF) << 20;		// 28/32 bits
			loginPacketHeader |= 0xF << 28;								// 32/32 bits
			
			byte[] nameBytes = accountName.getBytes(StandardCharsets.UTF_8);
			int strLen = accountName.length();
			long reserved = 0xFFFFFFFFFFFFFFFFL;
			
			try
			{
				chatClientPacket.buffer.clear();
				chatClientPacket.buffer.putInt(loginPacketHeader);								// 4/128 bytes
				chatClientPacket.buffer.putLong(accountHash);									// 12/128 bytes
				chatClientPacket.buffer.putLong(chatAccountKey);								// 20/128 bytes
				chatClientPacket.buffer.put(nameBytes, 0, strLen);						// up to 32/128 bytes
				if (strLen < 12)
					chatClientPacket.buffer.put(EMPTY_BYTES, 0, 12 - strLen);		// 32/128 bytes
				chatClientPacket.buffer.putLong(reserved);										// 40/129 bytes
				chatClientPacket.buffer.putLong(reserved);										// 48/128 bytes
				chatClientPacket.buffer.putLong(reserved);										// 56/129 bytes
				chatClientPacket.buffer.putLong(reserved);										// 64/128 bytes
				chatClientPacket.buffer.putLong(reserved);										// 72/128 bytes
				chatClientPacket.buffer.putLong(reserved);										// 80/128 bytes
				chatClientPacket.buffer.putLong(reserved);										// 88/128 bytes
				chatClientPacket.buffer.putLong(reserved);										// 96/128 bytes
				chatClientPacket.buffer.putLong(reserved);										// 104/128 bytes
				chatClientPacket.buffer.putLong(reserved);										// 112/128 bytes
				chatClientPacket.buffer.putLong(reserved);										// 120/128 bytes
				chatClientPacket.buffer.putLong(reserved);										// 128/128 bytes
				chatClientPacket.buffer.rewind();
				
				success = success && chatChannel.write(chatClientPacket.buffer) == CHAT_CLIENT_PACKET_SIZE;
			}
			catch (Exception e)
			{
				try
				{
					chatChannel = DatagramChannel.open(StandardProtocolFamily.INET);
					chatChannel.configureBlocking(false);
					chatChannel.bind(null);
					chatChannel.connect(chatAddress);
					success = success && chatChannel.write(chatClientPacket.buffer) == CHAT_CLIENT_PACKET_SIZE;
				}
				catch (Exception exception)
				{
					return false;
				}
			}
		}
		
		return success;
	}
	
	public void logout()
	{
		logoutGame();
		logoutChat();
	}
	
	public void logoutGame()
	{
		isGameLoggedIn = false;
		currentGameTick = 0;
		lastReceivedGameTick = 0;
		gameSessionID = -1;
		gameNumOnlinePlayers = 0;
	}
	
	public void logoutChat()
	{
		isChatLoggedIn = false;
		currentChatTick = 0;
		lastReceivedChatTick = 0;
		chatSessionID = -1;
		chatNumOnlinePlayers = 0;
	}
	
	public long getAccountHash()
	{
		return accountHash;
	}
	
	public long getGameAccountKey() { return gameAccountKey; }
	
	public long getChatAccountKey() { return chatAccountKey; }
	
	public boolean isGameGuest()
	{
		return isGameLoggedIn && !isGameUsingKey;
	}
	
	public boolean isChatGuest()
	{
		return isChatLoggedIn && !isChatUsingKey;
	}
	
	// returns data on all packets received in this tick, but may include late packets from previous ticks
	// start processing from one after lastReceivedGameTick until one wraps around back to lastReceivedGameTick
	public JebScapeServerData[][] getRecentGameServerData()
	{
		return gameServerData;
	}
	
	public JebScapeServerData[][] getRecentChatServerData()
	{
		return chatServerData;
	}
	
	public int[] getNumGameServerPacketsSent()
	{
		return numGameServerPacketsSent;
	}
	
	public int[] getNumChatServerPacketsSent()
	{
		return numChatServerPacketsSent;
	}
	
	public int getLastReceivedGameTick()
	{
		return lastReceivedGameTick;
	}
	
	public int getLastReceivedChatTick()
	{
		return lastReceivedChatTick;
	}
	
	public int getCurrentGameTick()
	{
		return currentGameTick;
	}
	
	public int getCurrentChatTick()
	{
		return currentChatTick;
	}
	
	public int getGameNumOnlinePlayers()
	{
		return gameNumOnlinePlayers;
	}
	
	public int getChatNumOnlinePlayers()
	{
		return chatNumOnlinePlayers;
	}
	
	// must be 3 ints (12 bytes); extraChatData is limited to size of 96 bytes (24 ints)
	public boolean sendGameData(int[] coreData, int[] gameSubData, byte[] extraChatData)
	{
		if (!gameChannel.isConnected() || !chatChannel.isConnected())
			return false;
		
		long reserved = 0xFFFFFFFFFFFFFFFFL;
		int bytesWritten = 0;
		
		if (isGameLoggedIn)
		{
			// set the header
			// 2 bits game identifier
			// 17 bits last known game session id
			// 1 bit isUsingKey
			// 4 bits current tick
			// 8 bits reserved
			int packetHeader = GAME_PACKET & 0x3;				// 2/32 bits
			packetHeader |= (gameSessionID & 0x1FFFF) << 2;		// 19/32 bits
			packetHeader |= (isGameUsingKey ? 0x1 : 0x0) << 19;	// 20/32 bits
			packetHeader |= (currentGameTick & 0xF) << 20;		// 24/32 bits
			packetHeader |= 0xFF << 24;							// 32/32 bits
			
			try
			{
				gameClientPacket.buffer.clear();
				gameClientPacket.buffer.putInt(packetHeader);	// 4/32 bytes
				gameClientPacket.buffer.putLong(accountHash);	// 12/64 bytes
				gameClientPacket.buffer.putLong(gameAccountKey);// 20/64 bytes
				gameClientPacket.buffer.putInt(coreData[0]);	// 24/64 bytes
				gameClientPacket.buffer.putInt(coreData[1]);	// 28/64 bytes
				gameClientPacket.buffer.putInt(coreData[2]);	// 32/64 bytes
				gameClientPacket.buffer.putInt(gameSubData[0]);	// 36/64 bytes
				gameClientPacket.buffer.putInt(gameSubData[1]);	// 40/64 bytes
				gameClientPacket.buffer.putInt(gameSubData[2]);	// 44/64 bytes
				gameClientPacket.buffer.putInt(gameSubData[3]);	// 48/64 bytes
				gameClientPacket.buffer.putLong(reserved);		// 56/64 bytes
				gameClientPacket.buffer.putLong(reserved);		// 64/64 bytes
				gameClientPacket.buffer.rewind();
				
				bytesWritten += gameChannel.write(gameClientPacket.buffer);
			}
			catch (Exception e)
			{
				// it's been observed that connections that sit idle long enough run the risk of being automatically closed, so let's recreate it just in case
				try
				{
					gameChannel = DatagramChannel.open(StandardProtocolFamily.INET);
					gameChannel.configureBlocking(false);
					gameChannel.bind(null);
					gameChannel.connect(gameAddress);
					bytesWritten += gameChannel.write(gameClientPacket.buffer);
				}
				catch (Exception exception)
				{
					return false;
				}
			}
		}
		
		if (isChatLoggedIn)
		{
			// set the header
			// 2 bits chat identifier
			// 17 bits last known chat session id
			// 1 bit isUsingKey
			// 4 bits current tick
			// 8 bits reserved
			int packetHeader = CHAT_PACKET & 0x3;				// 2/32 bits
			packetHeader |= (chatSessionID & 0x1FFFF) << 2;		// 19/32 bits
			packetHeader |= (isChatUsingKey ? 0x1 : 0x0) << 19;	// 20/32 bits
			packetHeader |= (currentChatTick & 0xF) << 20;		// 24/32 bits
			packetHeader |= 0xFF << 24;							// 32/32 bits
			
			int bytesLength = extraChatData.length;
			// cut it short if too long
			if (bytesLength > 96)
				bytesLength = 96;
			
			try
			{
				chatClientPacket.buffer.clear();
				chatClientPacket.buffer.putInt(packetHeader);									// 4/128 bytes
				chatClientPacket.buffer.putLong(accountHash);									// 12/128 bytes
				chatClientPacket.buffer.putLong(chatAccountKey);								// 20/128 bytes
				chatClientPacket.buffer.putInt(coreData[0]);										// 24/128 bytes
				chatClientPacket.buffer.putInt(coreData[1]);										// 28/128 bytes
				chatClientPacket.buffer.putInt(coreData[2]);										// 32/128 bytes
				if (bytesLength > 0)
					chatClientPacket.buffer.put(extraChatData, 0, bytesLength);			// up to 128/128 bytes
				if (bytesLength < 96)
					chatClientPacket.buffer.put(EMPTY_BYTES, 0, 96 - bytesLength);	// 128/128 bytes
				chatClientPacket.buffer.rewind();
				
				bytesWritten += chatChannel.write(chatClientPacket.buffer);
			}
			catch (Exception e)
			{
				try
				{
					chatChannel = DatagramChannel.open(StandardProtocolFamily.INET);
					chatChannel.configureBlocking(false);
					chatChannel.bind(null);
					chatChannel.connect(chatAddress);
					bytesWritten += chatChannel.write(chatClientPacket.buffer);
				}
				catch (Exception exception)
				{
					return false;
				}
			}
		}
		
		return bytesWritten == (GAME_CLIENT_PACKET_SIZE + CHAT_CLIENT_PACKET_SIZE);
	}
	
	public void onGameTick()
	{
		if (!gameChannel.isConnected() || !chatChannel.isConnected())
			return;
		
		// start from scratch on the data we're working with
		for (int i = 0; i < TICKS_UNTIL_LOGOUT; i++)
		{
			numGameServerPacketsSent[i] = 0;
			for (int j = 0; j < GAME_SERVER_PACKETS_PER_TICK; j++)
				gameServerData[i][j].clear();
			
			numChatServerPacketsSent[i] = 0;
			for (int j = 0; j < CHAT_SERVER_PACKETS_PER_TICK; j++)
				chatServerData[i][j].clear();
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
						if (!isGameLoggedIn)
						{
							// place the initial tick timing info here to serve as a baseline
							this.currentGameTick = newTick;
							this.lastReceivedGameTick = newTick;
						}
						
						// we've received an ACK from the server for our login request
						this.isGameLoggedIn = true;
						this.isGameUsingKey = newIsUsingKey; // if we made a request to log in with a key that was denied, it may allow us in as a guest anyway
						this.gameAccountKey = newIsUsingKey ? gameServerPacket.buffer.getLong() : 0;
						this.gameSessionID = newSessionID;
						this.gameNumOnlinePlayers = gameServerPacket.buffer.getInt(GAME_SERVER_PACKET_SIZE - 4); // read the last 4 bytes
					}
					
					if (isGameLoggedIn && newPacketType == GAME_PACKET && gameSessionID == newSessionID)
					{
						// place the latest tick info here
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
		
		try
		{
			// let's look into what server communications we've received...
			int bytesReceived;
			do
			{
				chatServerPacket.erase();
				chatServerPacket.buffer.clear();
				bytesReceived = chatChannel.read(chatServerPacket.buffer);
				chatServerPacket.buffer.rewind();
				
				if (bytesReceived == CHAT_SERVER_PACKET_SIZE)
				{
					int packetHeader = chatServerPacket.buffer.getInt();
					
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
						if (!isChatLoggedIn)
						{
							// place the initial tick timing info here to serve as a baseline
							this.currentChatTick = newTick;
							this.lastReceivedChatTick = newTick;
						}
						
						this.isChatLoggedIn = true;
						this.isChatUsingKey = newIsUsingKey; // if we made a request to log in with a key that was denied, it may allow us in as a guest anyway
						this.chatAccountKey = newIsUsingKey ? chatServerPacket.buffer.getLong() : 0;
						this.chatSessionID = newSessionID;
						this.chatNumOnlinePlayers = chatServerPacket.buffer.getInt(CHAT_SERVER_PACKET_SIZE - 4); // read the last 4 bytes
					}
					
					if (isChatLoggedIn && newPacketType == CHAT_PACKET && chatSessionID == newSessionID)
					{
						// place the latest tick info here
						chatServerData[newTick][newPacketID].setData(chatServerPacket);
						numChatServerPacketsSent[newTick] = newNumPacketsSent + 1; // we store in the range of 0-15 to represent 1-16
					}
				}
			} while (bytesReceived > 0);
		}
		catch (Exception e)
		{
			// not really sure what we want to do here...
		}
		
		if (isGameLoggedIn)
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
					this.currentGameTick = tick;
					this.lastReceivedGameTick = tick;
				}
			}
			
			// let's analyze what packets are missing from the most recent game tick received
			// TODO: not currently used; reanalyze to determine if this is necessary
			this.gameServerPacketsReceived = 0;
			if (numGameServerPacketsSent[lastReceivedGameTick] > 0)
				for (int packetID = 0; packetID < GAME_SERVER_PACKETS_PER_TICK; packetID++)
					this.gameServerPacketsReceived = (!gameServerData[lastReceivedGameTick][packetID].isEmpty() ? 0x1 : 0x0) << packetID;
			
			// increment current tick to prepare for the next payload
			// the gap between currentGameTick and lastReceivedGameTick shall grow if no packets are received
			this.currentGameTick = (currentGameTick + 1) % TICKS_UNTIL_LOGOUT;
			
			// if we've cycled around back to the beginning, we've timed out
			if (currentGameTick == lastReceivedGameTick)
				logoutGame();
		}
		
		if (isChatLoggedIn)
		{
			int prevReceivedChatTick = lastReceivedChatTick;
			
			// let's analyze the packets we've received to identify the most recent chat tick received
			for (int i = 0; i < TICKS_UNTIL_LOGOUT; i++)
			{
				// start at the last received tick and move forward from there
				// we include it in case some late packets arrive
				int tick = (prevReceivedChatTick + i) % TICKS_UNTIL_LOGOUT;
				if (numChatServerPacketsSent[tick] > 0)
				{
					this.currentChatTick = tick;
					this.lastReceivedChatTick = tick;
				}
			}
			
			this.chatServerPacketsReceived = 0;
			if (numChatServerPacketsSent[lastReceivedChatTick] > 0)
				for (int packetID = 0; packetID < CHAT_SERVER_PACKETS_PER_TICK; packetID++)
					this.chatServerPacketsReceived = (!chatServerData[lastReceivedChatTick][packetID].isEmpty() ? 0x1 : 0x0) << packetID;
			
			this.currentChatTick = (currentChatTick + 1) % TICKS_UNTIL_LOGOUT;
			
			// if we've cycled around back to the beginning, we've timed out
			if (currentChatTick == lastReceivedChatTick)
				logoutChat();
		}
	}
}
