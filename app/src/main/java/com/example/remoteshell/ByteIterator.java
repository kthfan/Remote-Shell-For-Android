package com.example.remoteshell;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class ByteIterator implements ReadableByteChannel{
	
	protected final static int CHUCK_SIZE = 1024;
	protected SocketChannel sc;
	protected ByteBuffer oneByteBuffer = ByteBuffer.allocate(1);
	protected ByteBuffer chunkSizeByteBuffer;
	protected int chuckSize;
	protected boolean notOver = true;
	protected ByteBuffer concatByteBuffer = ByteBuffer.allocate(8192);
	
	public SocketChannel getSocketChannel() {
		return this.sc;
	}
	
	ByteIterator(SocketChannel sc){
		this(sc, CHUCK_SIZE);
	}
	ByteIterator(SocketChannel sc, int chuckSize){
		this.chuckSize = chuckSize;
		this.chunkSizeByteBuffer = ByteBuffer.allocate(chuckSize);
		this.sc = sc;
	}
	
	public boolean hasNext() {
		return this.notOver;
	}
	
	public byte getByteAndConcat() {
		byte result = this.nextByte();
		concatByteBuffer.put(result);
		return result;
	}
	public byte[] getConcated() {
		
		concatByteBuffer.flip();
		byte[] result =  new byte[concatByteBuffer.limit()];
		concatByteBuffer.get(result);
		concatByteBuffer.clear();
		return result;
	}
	
	public byte nextByte() {
		try {
			this.sc.read(oneByteBuffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
		oneByteBuffer.flip();
		if(oneByteBuffer.limit() == 0) {
			this.notOver = false;
			return 0;
		}
		
		byte result = oneByteBuffer.get();
		oneByteBuffer.clear();
		return result;
	}
	
	public byte[] nextBytes(int size) {
		return this.nextByteBuffer(size).array();
	}
	public ByteBuffer nextByteBuffer(int size) {
		ByteBuffer bb = ByteBuffer.allocate(size);
		try {
			this.sc.read(bb);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(bb.position() != size) {
			this.notOver = false;
		}
		bb.flip();
		return bb;
	}
	
	public byte[] nextChuckBytes() {
		return this.nextChuckByteBuffer().array();
	}
	public ByteBuffer nextChuckByteBuffer() {
		this.chunkSizeByteBuffer.clear();
		try {
			this.sc.read(this.chunkSizeByteBuffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(this.chunkSizeByteBuffer.position() != this.chuckSize) {
			this.notOver = false;
		}
		this.chunkSizeByteBuffer.flip();
		return this.chunkSizeByteBuffer;
	}
	
	public byte[] getRemainingBytes() {
		return this.getRemainingByteBuffer().array();
	}
	public ByteBuffer[] getRemainingByteBuffers() {
		List<ByteBuffer> bbList = new ArrayList<ByteBuffer>();
		ByteBuffer bb;
		do {
			bb = ByteBuffer.allocate(this.chuckSize);
			try {
				this.sc.read(bb);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(bb.position() == 0) {
				this.notOver = false;
				return bbList.toArray(new ByteBuffer[bbList.size()]);
			}
			bb.flip();
			bbList.add(bb);
		}while(true);	
	}
	public ByteBuffer getRemainingByteBuffer() {
		ByteBuffer bb = ByteBuffer.allocate(this.chuckSize);
		ByteBuffer result = ByteBuffer.allocate(0);
		int size = 0;
		do {
			try {
				sc.read(bb);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(bb.position() == 0) {
				this.notOver = false;
				return result;
			}
			size += bb.position();
			result.flip();
			bb.flip();
			result = ByteBuffer.allocate(size).put(result).put(bb);
			bb.clear();
		}while(true);
	}
	
	@Override
	public boolean isOpen() {
		return this.notOver;
	}
	@Override
	public void close() throws IOException {
		this.notOver = false;
		this.sc.close();
	}
	@Override
	public int read(ByteBuffer dst) throws IOException {
		int result = this.sc.read(dst);
		if(result == -1) this.notOver = false;
		return result;
	}
}
