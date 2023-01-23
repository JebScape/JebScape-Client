package com.jebscape.core;

import java.nio.*;

public class JebScapePacket
{
	public ByteBuffer buffer;
	private byte emptyBuffer[];
	
	public void init(int size)
	{
		buffer = ByteBuffer.allocateDirect(size);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		emptyBuffer = new byte[size];
		erase();
	}
	
	// turns the packet into an empty packet of all 0s
	public void erase()
	{
		buffer.clear();
		buffer.put(emptyBuffer);
		buffer.rewind();
	}
}
