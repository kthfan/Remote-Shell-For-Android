package com.example.remoteshell;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class AesByteIterator extends ByteIterator{

	protected AesCtr aesDec;
	
	AesByteIterator(ByteIterator reader, AesCtr aesDec) {
		super(reader.sc, reader.chuckSize);
		this.notOver = reader.notOver;
		this.concatByteBuffer = reader.concatByteBuffer;
		this.aesDec = aesDec;
	}
	AesByteIterator(SocketChannel sc, AesCtr aesDec){
		super(sc);
		this.aesDec = aesDec;
	}
	
//	public byte getByteAndConcat() {
//		byte result = this.nextByte();
//		concatByteBuffer.put(result);
//		return result;
//	}
//	public byte[] getConcated() {
//		
//		concatByteBuffer.flip();
//		byte[] result =  new byte[concatByteBuffer.limit()];
//		concatByteBuffer.get(result);
//		concatByteBuffer.clear();
//		return result;
//	}
	
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
		
		this.aesDec.decrypt(oneByteBuffer.array(), 0, 1, false);
		byte result = oneByteBuffer.get();
		oneByteBuffer.clear();
		
		return result;
	}
	
//	public byte[] nextBytes(int size) {
//		return this.nextByteBuffer(size).array();
//	}
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
		this.aesDec.decrypt(bb.array(), 0, bb.limit(), false);
		return bb;
	}
	
//	public byte[] nextChuckBytes() {
//		return this.nextChuckByteBuffer().array();
//	}
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
		this.aesDec.decrypt(this.chunkSizeByteBuffer.array(), 0, this.chunkSizeByteBuffer.limit(), false);
		return this.chunkSizeByteBuffer;
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
			this.aesDec.decrypt(bb.array(), 0, bb.limit(), false);
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
				this.aesDec.decrypt(result.array(), 0, result.limit(), false);
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
	public int read(ByteBuffer dst) throws IOException {
		int result = this.sc.read(dst);
		if(result == -1) this.notOver = false;
		this.aesDec.decrypt(dst.array(), 0, dst.position(), false);
		return result;
	}
}
