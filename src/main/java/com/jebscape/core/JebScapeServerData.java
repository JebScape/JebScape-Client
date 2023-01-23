package com.jebscape.core;

// this class excludes the packet header and focuses on the remaining 124 of the total 128 bytes
public class JebScapeServerData
{
	public static final int CORE_DATA_SIZE = 3; // contains essential player data and packet metadata (12 bytes)
	public static final int SUB_DATA_BLOCK_SIZE = 4; // 4 bytes each
	public static final int NUM_SUB_DATA_BLOCKS = 7; // 7 blocks at 4 bytes each (112 bytes)
	public int[] coreData = new int[CORE_DATA_SIZE]; 									// 16/128 bytes
	public int[][] subDataBlocks = new int[NUM_SUB_DATA_BLOCKS][SUB_DATA_BLOCK_SIZE];	// 128/128 bytes
	
	public void setData(JebScapePacket packet)
	{
		// unrolled loops
		coreData[0] = packet.buffer.getInt();
		coreData[1] = packet.buffer.getInt();
		coreData[2] = packet.buffer.getInt();
		
		subDataBlocks[0][0] = packet.buffer.getInt();
		subDataBlocks[0][1] = packet.buffer.getInt();
		subDataBlocks[0][2] = packet.buffer.getInt();
		subDataBlocks[0][3] = packet.buffer.getInt();
		
		subDataBlocks[1][0] = packet.buffer.getInt();
		subDataBlocks[1][1] = packet.buffer.getInt();
		subDataBlocks[1][2] = packet.buffer.getInt();
		subDataBlocks[1][3] = packet.buffer.getInt();
		
		subDataBlocks[2][0] = packet.buffer.getInt();
		subDataBlocks[2][1] = packet.buffer.getInt();
		subDataBlocks[2][2] = packet.buffer.getInt();
		subDataBlocks[2][3] = packet.buffer.getInt();
		
		subDataBlocks[3][0] = packet.buffer.getInt();
		subDataBlocks[3][1] = packet.buffer.getInt();
		subDataBlocks[3][2] = packet.buffer.getInt();
		subDataBlocks[3][3] = packet.buffer.getInt();
		
		subDataBlocks[4][0] = packet.buffer.getInt();
		subDataBlocks[4][1] = packet.buffer.getInt();
		subDataBlocks[4][2] = packet.buffer.getInt();
		subDataBlocks[4][3] = packet.buffer.getInt();
		
		subDataBlocks[5][0] = packet.buffer.getInt();
		subDataBlocks[5][1] = packet.buffer.getInt();
		subDataBlocks[5][2] = packet.buffer.getInt();
		subDataBlocks[5][3] = packet.buffer.getInt();
		
		subDataBlocks[6][0] = packet.buffer.getInt();
		subDataBlocks[6][1] = packet.buffer.getInt();
		subDataBlocks[6][2] = packet.buffer.getInt();
		subDataBlocks[6][3] = packet.buffer.getInt();
	}
	
	void clear()
	{
		// unrolled loops
		coreData[0] = 0;
		coreData[1] = 0;
		coreData[2] = 0;
		
		subDataBlocks[0][0] = 0;
		subDataBlocks[0][1] = 0;
		subDataBlocks[0][2] = 0;
		subDataBlocks[0][3] = 0;
		
		subDataBlocks[1][0] = 0;
		subDataBlocks[1][1] = 0;
		subDataBlocks[1][2] = 0;
		subDataBlocks[1][3] = 0;
		
		subDataBlocks[2][0] = 0;
		subDataBlocks[2][1] = 0;
		subDataBlocks[2][2] = 0;
		subDataBlocks[2][3] = 0;
		
		subDataBlocks[3][0] = 0;
		subDataBlocks[3][1] = 0;
		subDataBlocks[3][2] = 0;
		subDataBlocks[3][3] = 0;
		
		subDataBlocks[4][0] = 0;
		subDataBlocks[4][1] = 0;
		subDataBlocks[4][2] = 0;
		subDataBlocks[4][3] = 0;
		
		subDataBlocks[5][0] = 0;
		subDataBlocks[5][1] = 0;
		subDataBlocks[5][2] = 0;
		subDataBlocks[5][3] = 0;
		
		subDataBlocks[6][0] = 0;
		subDataBlocks[6][1] = 0;
		subDataBlocks[6][2] = 0;
		subDataBlocks[6][3] = 0;
	}
	
	public boolean isEmpty()
	{
		return coreData[0] == 0;
	}
}
