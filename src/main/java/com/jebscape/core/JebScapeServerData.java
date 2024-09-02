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

// this class excludes the packet header and focuses on the remaining 124 of the total 128 bytes
public class JebScapeServerData
{
	public static final int DATA_BLOCK_SIZE = 4; // 4 bytes each
	public static final int NUM_DATA_BLOCKS = 34; // 34 blocks at 4 bytes each (544 bytes)
	public int[][] blocks = new int[NUM_DATA_BLOCKS][DATA_BLOCK_SIZE];	// 544/544 bytes

	public void setData(JebScapePacket packet)
	{
		packet.buffer.rewind();

		for (int i = 0; i < NUM_DATA_BLOCKS; i++)
			for (int j = 0; j < DATA_BLOCK_SIZE; j++)
				blocks[i][j] = packet.buffer.getInt();
	}
	
	void clear()
	{
		for (int i = 0; i < NUM_DATA_BLOCKS; i++)
			for (int j = 0; j < DATA_BLOCK_SIZE; j++)
				blocks[i][j] = 0;
	}
	
	public boolean isEmpty()
	{
		return blocks[0][0] == 0;
	}
}
