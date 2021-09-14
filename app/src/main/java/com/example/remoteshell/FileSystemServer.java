package com.example.remoteshell;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.Writer;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.AccessControlException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@RequiresApi(api = Build.VERSION_CODES.O)
public class FileSystemServer extends VerifyServer{
	
	private final static byte CWD = 2;
	private final static byte CHDIR = 3;
	private final static byte LISTDIR = 4;
	private final static byte REMOVE = 5;
	private final static byte RMDIR = 6;
	private final static byte MKDIR = 7;
	private final static byte MKFILE = 8;
	private final static byte MOVE = 9;
	private final static byte OPEN_FILE = 10;
	private final static byte CLOSE_FILE = 11;
	private final static byte FILE_OP = 12;
//	private final static byte TERMINATE_SERVER = 13;
	private final static byte FILE_STATE = 14;
	private final static byte FILE_STATE_SIMPLE = 15;
	private final static byte CURL = 16;
	private final static byte FETCH = 17;
	private final static byte SET_ATTRIBUTE = 18;
	
	private final static byte OPEN_MODE_READ = 1;
	private final static byte OPEN_MODE_WRITE = 2;
//	private final static byte OPEN_MODE_APPEND = 4;
	private final static byte OPEN_MODE_CREATE = 8;
	private final static byte OPEN_MODE_CREATE_IF_NOT_EXISTS = 16;
	private final static byte OPEN_MODE_DELETE_ON_CLOSE = 32;
	private final static byte OPEN_MODE_TRUNCATE_EXISTING = 64;
	
	private final static byte OPERATION_READ_ALL = 2;
	private final static byte OPERATION_READ_RANGE = 3;
	private final static byte OPERATION_OVERWRITE_ALL = 4;
	private final static byte OPERATION_OVERWRITE_RANGE = 5;
	private final static byte OPERATION_APPEND = 6;
    // private final static byte OPERATION_TRANSFER_ALL = 7;
	private final static byte OPERATION_TRANSFER_RANGE = 8;

	
	private FileOperation fileOperation;
	private Map<String, FileChannel> fileMap = new HashMap<String, FileChannel>();
	
	public static byte[] getBytesFromShort(short num) {
		byte[] result = new byte[2];
		for(int i=0; i<result.length; i++) {
			result[i] = (byte) (num & 0xff);
			num >>= 8;
		}
		return result;
	}
	public static short getShortFromBytes(byte[] bytes) {
		short result = 0;
		for(int i=0; i<bytes.length; i++) {
			result += (bytes[i] & 0xff) << (i<<3);
		}
		return result;
	}
	public static long getLongFromBytes(byte[] bytes) {
		long result = 0L;
		for(int i=0; i<bytes.length; i++) {
			result += (bytes[i] & 0xff) << (i<<3);
		}
		return result;
	}
	public static byte[] getBytesFromLong(long num) {
		byte[] result = new byte[8];
		for(int i=0; i<result.length; i++) {
			result[i] = (byte) (num & 0xffL);
			num >>= 8;
		}
		return result;
	}
	
	FileSystemServer() throws AccessControlException{
		super();
		this.fileOperation = new FileOperation(Arrays.asList(System.getProperty("user.dir")));
		FileOperation.FileResult fileResult = fileOperation.chdir(System.getProperty("user.dir"));
		if(fileResult.errorCode != 0)
			throw new AccessControlException(fileResult.message);
		
	}
	FileSystemServer(String workingDir, String token, Collection<String> allowHosts, Collection<Integer> ports) throws AccessControlException{
		super(token, allowHosts, ports);
		this.fileOperation = new FileOperation(Arrays.asList(workingDir));
		FileOperation.FileResult fileResult = fileOperation.chdir(workingDir);
		if(fileResult.errorCode != 0)
			throw new AccessControlException(fileResult.message);
	}

	private OpenOption[] getOpenOption(byte mode) {
		List<OpenOption> result = new ArrayList<OpenOption>();
		if((mode & OPEN_MODE_READ) != 0) result.add(StandardOpenOption.READ);
		if((mode & OPEN_MODE_WRITE) != 0) result.add(StandardOpenOption.WRITE);
//		if((mode & OPEN_MODE_APPEND) != 0) result.add(StandardOpenOption.APPEND);
		if((mode & OPEN_MODE_CREATE) != 0) result.add(StandardOpenOption.CREATE_NEW);
		if((mode & OPEN_MODE_CREATE_IF_NOT_EXISTS) != 0) result.add(StandardOpenOption.CREATE);
		if((mode & OPEN_MODE_DELETE_ON_CLOSE) != 0) result.add(StandardOpenOption.DELETE_ON_CLOSE);
		if((mode & OPEN_MODE_TRUNCATE_EXISTING) != 0) result.add(StandardOpenOption.TRUNCATE_EXISTING);
		
		return result.toArray(new OpenOption[result.size()]);
	}
	private String addFileChannel(FileChannel fc) {
		String id = this.generateFileId();
		this.fileMap.put(id, fc);
		return id;
	}
	private FileChannel removeFileChannel(String id) {
		FileChannel result = this.fileMap.remove(id);
		return result;
	}
	private String generateFileId() {
		String id = null;
		byte[] randBytes = new byte[32];
		SecureRandom crypto = new SecureRandom();
		do {
			crypto.nextBytes(randBytes);
			id = Base64.getEncoder().encodeToString(randBytes);
			id = id.replaceAll("=", "_");
		}while(fileMap.containsKey(id));
		return id;
	}
	
	
	private void doFileOperation(ConnectContext context) {
		ByteIterator reader = context.getReader();
		ByteSender writer = context.getWriter();
		FileChannel fileChannel;
		String id;
		int idLen;
		byte mode;
		long position, length, srcPosition, destPosition;
		while(reader.hasNext()) {
			mode = reader.nextByte();
			if(mode == 0 && !reader.hasNext()) break; // check is end of channel
			idLen = reader.nextByte() & 0xff;
			id = new String(reader.nextBytes(idLen));
			fileChannel = this.fileMap.get(id);
			writer.send(mode); // send mode first
			switch(mode) {
			case OPERATION_READ_ALL:
				this.fileOperation.readAll(fileChannel, writer);
				break;
			case OPERATION_READ_RANGE:
				position = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
				length = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
				this.fileOperation.readRange(fileChannel, writer, position, length);
				break;
			case OPERATION_OVERWRITE_ALL:
				length = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
				this.fileOperation.writeAll(fileChannel, writer, reader, length);
				break;
			case OPERATION_OVERWRITE_RANGE:
				position = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
				length = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
				this.fileOperation.writeRange(fileChannel, writer, reader, position, length);
				break;
			case OPERATION_APPEND:
				length = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
				this.fileOperation.appendAll(fileChannel, writer, reader, length);
				break;
			case OPERATION_TRANSFER_RANGE:
				int destIdLen = reader.nextByte() & 0xff;
				String destId = new String(reader.nextBytes(destIdLen));
				FileChannel destFileChannel = this.fileMap.get(destId);
				
				srcPosition = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
				destPosition = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
				length = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
				this.fileOperation.transferRange(fileChannel, destFileChannel, writer, srcPosition, destPosition, length);
				break;
			default:
				System.out.println("Error occurred in FileSystemServer.doFileOperation. Code: "+ mode);
				break;
			}
		}
		
	}
	
	@Override
	void onRequest(ConnectContext context, Request request, Response response, Socket socket) {
		
		ByteIterator reader = context.getReader();
		ByteSender writer = context.getWriter();
		byte code = reader.nextByte();
		FileOperation.FileResult fileResult;
		
		switch(code) {
		case CWD:
			context.setResponseData(fileOperation.cwd().getBytes());
			break;
		case CHDIR:
			context.setResponseData(fileOperation.chdir(new String(reader.getRemainingBytes())).getBytes());
			break;
		case LISTDIR:
			if(!reader.hasNext()) context.setResponseData(fileOperation.listDir().getBytes());
			else{
				String body = new String(reader.getRemainingBytes());
				context.setResponseData(fileOperation.listDir(body).getBytes());
			}
			break;
		case REMOVE:
			context.setResponseData(fileOperation.remove(new String(reader.getRemainingBytes())).getBytes());
			break;
		case RMDIR:
			context.setResponseData(fileOperation.rmdir(new String(reader.getRemainingBytes())).getBytes());
			break;
		case MKDIR:
			context.setResponseData(fileOperation.mkdir(new String(reader.getRemainingBytes())).getBytes());
			break;
		case MKFILE:
			context.setResponseData(fileOperation.mkfile(new String(reader.getRemainingBytes())).getBytes());
			break;
		case MOVE:
			String body = new String(reader.getRemainingBytes());
			int splitIndex = body.indexOf("|");
			String fromFileName = body.substring(0, splitIndex);
			String toFileName = body.substring(splitIndex+1, body.length());
			context.setResponseData(fileOperation.move(fromFileName, toFileName).getBytes());
			break;
		case OPEN_FILE:
			byte mode = reader.nextByte();
			OpenOption[] opts = this.getOpenOption(mode);
			fileResult = fileOperation.openFile(new String(reader.getRemainingBytes()), opts);
			if(fileResult.errorCode != 0) context.setResponseData(fileResult.getBytes());
			else {
				String id = this.addFileChannel(fileResult.fcPayload);
				fileResult.strPayload = id;
				context.setResponseData(fileResult.getBytes());
			}
			break;
		case CLOSE_FILE:
			FileChannel fc1 = this.removeFileChannel(new String(reader.getRemainingBytes()));
			context.setResponseData(fileOperation.closeFile(fc1).getBytes());
			break;
		case FILE_OP:
			response.sendHeader();
			this.doFileOperation(context);
			break;
		case FILE_STATE:
			context.setResponseData(fileOperation.fileState(new String(reader.getRemainingBytes())).getBytes());
			break;
		case FILE_STATE_SIMPLE:
			context.setResponseData(fileOperation.fileStateSimple(new String(reader.getRemainingBytes())).getBytes());
			break;
		case CURL:
			response.setAutoClose(false);
			response.sendHeader();
			fileOperation.curl(reader, writer, socket.getChannel());
			break;
		case FETCH:
			response.sendHeader();
			short urlLen = FileSystemServer.getShortFromBytes(reader.nextBytes(2));
			String url = new String(reader.nextBytes(urlLen));
			long bodyLen = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
			fileOperation.fetch(url, writer, reader, bodyLen);
			break;
		case SET_ATTRIBUTE:
			short pathLen = FileSystemServer.getShortFromBytes(reader.nextBytes(2));
			String path = new String(reader.nextBytes(pathLen));
			boolean setReadOnly = reader.nextByte() == 1;
			boolean readOnly = reader.nextByte() == 1;
			byte[] toSetTime = reader.nextBytes(3);
			FileTime lastModifiedTime = null;
			FileTime lastAccessTime = null;
	        FileTime createTime = null;
			if(toSetTime[0] == 1) {
				lastModifiedTime = FileTime.fromMillis(FileSystemServer.getLongFromBytes(reader.nextBytes(8)));
			}else reader.nextBytes(8);
			if(toSetTime[1] == 1) {
				lastAccessTime = FileTime.fromMillis(FileSystemServer.getLongFromBytes(reader.nextBytes(8)));
			}else reader.nextBytes(8);
			if(toSetTime[2] == 1) {
				createTime = FileTime.fromMillis(FileSystemServer.getLongFromBytes(reader.nextBytes(8)));
			}else reader.nextBytes(8);
			context.setResponseData(fileOperation.setAttribute(path, setReadOnly, readOnly, lastModifiedTime, lastAccessTime, createTime).getBytes());
			break;
		case 0:
			response.sendHeader();
			fileOperation.test("", writer, reader);
			break;
		default:
			System.out.println("Error occurred in FileSystemServer.onRequest. Code: "+ code);
			break;
		}
	}
	
}
