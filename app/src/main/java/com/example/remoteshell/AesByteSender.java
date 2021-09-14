package com.example.remoteshell;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class AesByteSender extends ByteSender{
	
	protected AesCtr aesEnc;
	
	AesByteSender(SocketChannel sc, AesCtr aesEnc) {
		super(sc);
		this.aesEnc = aesEnc;
	}

	public boolean send(byte b) {
		this.oneByteBuffer.clear();
		this.oneByteBuffer.put(b);
		this.aesEnc.encrypt(this.oneByteBuffer.array(), 0, 1, false);
		this.oneByteBuffer.flip();
		try {
			this.sc.write(this.oneByteBuffer);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	public boolean send(ByteBuffer buffer) {
		this.aesEnc.encrypt(buffer.array(), 0, buffer.limit(), false);
		try {
			this.sc.write(buffer);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	public boolean send(ByteBuffer[] buffers) {
		for(ByteBuffer bb:buffers) {
			this.aesEnc.encrypt(bb.array(), 0, bb.limit(), false);
		}
		try {
			this.sc.write(buffers);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	@Override
	public int write(ByteBuffer src) throws IOException {
		this.aesEnc.encrypt(src.array(), 0, src.limit(), false);
		return this.sc.write(src);
	}
}
