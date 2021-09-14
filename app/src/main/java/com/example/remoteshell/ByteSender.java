package com.example.remoteshell;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class ByteSender implements WritableByteChannel{
	protected SocketChannel sc;
	protected ByteBuffer oneByteBuffer = ByteBuffer.allocate(1);
	
	public SocketChannel getSocketChannel() {
		return this.sc;
	}
	
	ByteSender(SocketChannel sc){
		this.sc = sc;
	}
	
	public boolean send(byte b) {
		this.oneByteBuffer.clear();
		this.oneByteBuffer.put(b);
		this.oneByteBuffer.flip();
		try {
			this.sc.write(this.oneByteBuffer);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	public boolean send(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.limit(bytes.length);
		return this.send(bb);
	}
	public boolean send(ByteBuffer buffer) {
		try {
			this.sc.write(buffer);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	public boolean send(ByteBuffer[] buffers) {
		try {
			this.sc.write(buffers);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean isOpen() {
		return this.sc.isOpen();
	}

	@Override
	public void close() throws IOException {
		this.sc.close();
	}

	@Override
	public int write(ByteBuffer src) throws IOException {
		return this.sc.write(src);
	}
}
