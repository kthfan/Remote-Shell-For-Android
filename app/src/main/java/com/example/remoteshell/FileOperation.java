package com.example.remoteshell;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Stream;
import java.nio.channels.Channels;
import java.util.HashSet;


@RequiresApi(api = Build.VERSION_CODES.O)
public class FileOperation {
	static class FileAttr{
		private final static String[] ATTR_NAMES = new String[] {"exists", "name", "fileType", "rwe", "size", "owner", "isHidden", "creationTime", "lastAccessTime", "modifiedTime"};
		private final String[] attrs = new String[] {null, null, null, null, null, null, null, null, null, null};
		public void setExists(boolean exists) {
			this.attrs[0] = exists ? "1" : "0";
		}
		public void setFileName(String name) {
			this.attrs[1] = "\"" + String.valueOf(name) + "\"";
		}
		public void setFileType(char fileType) {
			this.attrs[2] = "\"" + String.valueOf(fileType) + "\"";
		}
		public void setRWE(int rwe) {
			this.attrs[3] = String.valueOf(rwe);
		}
		public void setSize(long size) {
			this.attrs[4] = String.valueOf(size);
		}
		public void setOwner(String name) {
			this.attrs[5] = "\"" + name + "\"";
		}
		public void setHidden(boolean isHidden) {
			this.attrs[6] = isHidden ? "1" : "0";
		}
		public void setCreationTime(long time) {
			this.attrs[7] = String.valueOf(time);
		}
		public void setLastAccessTime(long time) {
			this.attrs[8] = String.valueOf(time);
		}
		public void setLastModifiedTime(long time) {
			this.attrs[9] = String.valueOf(time);
		}
		public String toString() {
			String result = "[";
			if(this.attrs[0] == null) return "[]";
			else  result += this.attrs[0];
			
			for(int i=1; i<this.attrs.length; i++) {
				if(this.attrs[i] == null) break;
				result += "," + this.attrs[i];
			}
			result += "]";
			return result.replace("\\","\\\\");
		}
		public static String arrayToString(FileAttr[] attrsArr) {
			String result = "[";
			if(attrsArr.length >  0)
				result += attrsArr[0].toString();
			for(int i=1; i<attrsArr.length; i++) {
				result += "," + attrsArr[i].toString();
			}
			result += "]";
			return result;
		}
	}
	class FileResult{
		public byte errorCode = 0;
		public String message = "";
		public Object payload = null;
		public String strPayload = null;
		public FileAttr attrPayload = null;
		public FileAttr[] attrArrPayload = null;
		public FileChannel fcPayload = null;
		public long longPayload = -1L;
		FileResult(){}
		FileResult(int errorCode, String message){
			this((byte) errorCode, message);
		}
		FileResult(byte errorCode, String message){
			this.errorCode = errorCode;
			if(message.trim().compareTo("") == 0) message = "Unknown error.";
			this.message = message;
		}
		FileResult(int errorCode, String message, Object payload){
			this(errorCode, message);
			this.payload = payload;
		}
		FileResult(int errorCode, String message, String strPayload){
			this(errorCode, message);
			this.strPayload = strPayload;
		}
		FileResult(int errorCode, String message, FileAttr attrPayload){
			this(errorCode, message);
			this.attrPayload = attrPayload;
		}
		FileResult(int errorCode, String message, FileAttr[] attrArrPayload){
			this(errorCode, message);
			this.attrArrPayload = attrArrPayload;
		}
		FileResult(int errorCode, String message, FileChannel fcPayload){
			this(errorCode, message);
			this.fcPayload = fcPayload;
		}
		FileResult(int errorCode, String message, long longPayload){
			this(errorCode, message);
			this.longPayload = longPayload;
		}
		
		public byte[] getBytes(){
			byte[] result = null;
			int messageLen = this.message.length();
			byte[] data = new byte[0];
			int dataLen = 0;
			if(this.strPayload != null) {
				data = this.strPayload.getBytes();
			}else if(this.attrPayload != null) {
				data = this.attrPayload.toString().getBytes();
			}else if(this.attrArrPayload != null) {
				data = FileAttr.arrayToString(this.attrArrPayload).getBytes();
			}else if(this.longPayload != -1L) {
				data = FileSystemServer.getBytesFromLong(this.longPayload);
			}
			dataLen = data.length;
			result = new byte[2 + messageLen + 8 + dataLen];
			
			result[0] = this.errorCode; // set error code
			result[1] = (byte) this.message.length(); // set message length
			System.arraycopy(this.message.getBytes(), 0, result, 2, messageLen); // set message
			
			System.arraycopy(FileSystemServer.getBytesFromLong(dataLen), 0, result, 2 + messageLen, 8); // set data length
			System.arraycopy(data, 0, result, 2 + messageLen + 8, dataLen); // set data
			
			return result;
		}
	}
	
	public final static String RESULT_OK = "OK.";
	public final static String RESULT_FAIL = "Fail.";
	public final static String RESULT_NOT_EXISTS = "File not exists.";
	public final static String RESULT_PERMISSION_DENIED = "Permission denied.";
	private String _cwd;
	private Set<String> allowedPath = new HashSet<String>();
	
	private ExecutorService executorService = Executors.newCachedThreadPool();
	ReentrantLock curlLock = new ReentrantLock();
	
	FileOperation(){
		this.addAllowedPath("/");
	}
	FileOperation(Collection<String> allowedPath){
		for(String path:allowedPath) {
			this.addAllowedPath(path);
		}
	}
	
	public void addAllowedPath(String path) {
		if(path.endsWith("/")) {
			path = path.substring(0, path.length()-1);
		}
		path = path.replaceAll("\\\\", "/");
		this.allowedPath.add(path);
	}
	public void removeAllowedPath(String path) {
		if(path.endsWith("/")) {
			path = path.substring(0, path.length()-1);
		}
		this.allowedPath.remove(path);
	}
	private String getParentPath(String path) {
		final boolean isDir = path.endsWith("/");
		if(isDir) path = path.substring(0, path.length()-1);
		
		final String[] splitPath = path.split("/");
		final int splitCount = splitPath.length;
		
		return String.join("/", Arrays.copyOfRange(splitPath, 0, splitCount - 1)) + "/";
	}
	public boolean isPathAllow(String path) {
		Path child = Paths.get(path).toAbsolutePath();
		for(String allowed:this.allowedPath) {
			Path parent = Paths.get(allowed).toAbsolutePath();
			if(child.startsWith(parent)) return true;
		}
		return false;
//		if(path.endsWith("/")) {
//			path = path.substring(0, path.length()-1);
//		}
//		if(this.allowedPath.contains(path)) return true;
//		final String[] splitPath = path.split("/");
//		final int splitCount = splitPath.length;
//		for(int i=splitCount-1; i >= 0; i--) {			
//			String parentPath = String.join("/", Arrays.copyOfRange(splitPath, 0, i));
//			if(this.allowedPath.contains(parentPath)) return true;
//		}
//		return false;
	}
	private <T,R> R doLock(Lock lock, Function<T, R> func, T arg) {
		R result;
		try {
			lock.lock();
			result = func.apply(arg);
        } finally {
        	lock.unlock();
        }
		return result;
	}
	private String checkFile(Path path, String str) {
		if(!this.isPathAllow(str)) return RESULT_PERMISSION_DENIED;
		if(!Files.exists(path)) return RESULT_NOT_EXISTS;
		return RESULT_OK;
	}
	private String checkReadFile(Path path, String str) {
		if(!this.isPathAllow(str)) return RESULT_PERMISSION_DENIED;
		if(!Files.exists(path)) return RESULT_NOT_EXISTS;
		if(Files.isDirectory(path)) {
			if(!Files.isReadable(path) || !Files.isExecutable(path)) return RESULT_PERMISSION_DENIED;
		}else {
			if(!Files.isReadable(path)) return RESULT_PERMISSION_DENIED;
		}
		return RESULT_OK;
	}
	private String checkWriteFile(Path path, String str) {
		if(!this.isPathAllow(str)) return RESULT_PERMISSION_DENIED;
		if(!Files.exists(path)) return RESULT_NOT_EXISTS;
		if(Files.isDirectory(path)) {
			if(!Files.isReadable(path) || !Files.isWritable(path) || !Files.isExecutable(path)) return RESULT_PERMISSION_DENIED;
		}else {
			if(!Files.isWritable(path)) return RESULT_PERMISSION_DENIED;
		}
		return RESULT_OK;
	}
	
	private String toAbsolutePath(String path) {
		path = path.replaceAll("\\\\", "/");
		
		if(path.compareTo(".") == 0) {
			return this._cwd;
		}else if(path.compareTo("..") == 0) {	
			return Paths.get(this._cwd).getParent().toString();
		}
		
		Path dir = Paths.get(path);
		if(!dir.isAbsolute()) path = this._cwd + path;
		
		Path realPath;
		try {
			realPath = Paths.get(path).toRealPath();
		} catch (IOException e) {
			realPath = Paths.get(path).toAbsolutePath();
			System.out.println("Warning: FileOperation:toAbsolutePath: "+e.toString());
		}
		return realPath.toString().replaceAll("\\\\", "/");
	}
	
	public FileResult cwd() {
		return new FileResult( 0, RESULT_OK, this._cwd);
	}
	public FileResult chdir(String path) {
		
		path = this.toAbsolutePath(path);
		Path dir = Paths.get(path);
		
		String msg = this.checkReadFile(dir, path);
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		
		if(!Files.isDirectory(dir)) return new FileResult(1, "Not a directory.");
		this._cwd = path;
		this._cwd = this._cwd.replaceAll("\\\\", "/");
		if(!this._cwd.endsWith("/")) this._cwd += "/";
		return new FileResult( 0, RESULT_OK);
	}
	
	public FileResult listDir() {
		return this.listDir(this._cwd);
	}
	public FileResult listDir(String path) {
		path = this.toAbsolutePath(path);
		Path dir = Paths.get(path);
		
		String msg = this.checkReadFile(dir, path);
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg, new FileAttr[0]);
		if(!Files.isDirectory(dir)) return new FileResult(1, "Not a directory.", new FileAttr[0]);
		
		List<FileAttr> list = new ArrayList<FileAttr>();
		try {
			Stream<Path> stream = Files.list(dir);
			stream.forEach(f->list.add(this.fileState(f).attrPayload));
			
		} catch (IOException e) {
			e.printStackTrace();
			return new FileResult(1, e.toString(), list.toArray(new FileAttr[list.size()]));
		}
		return new FileResult(0, RESULT_OK, list.toArray(new FileAttr[list.size()]));
	}
	
	public FileResult remove(String path) {
		path = this.toAbsolutePath(path);
		Path dir = Paths.get(path);
		
		String msg = this.checkReadFile(dir, path);
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		
		try {
			return Files.deleteIfExists(dir) ? new FileResult(0, RESULT_OK) : new FileResult(1, RESULT_FAIL);
		} catch (IOException e) {
			return new FileResult(1, e.toString());
		}
	}
	
	public FileResult rmdir(String path) {
		path = this.toAbsolutePath(path);
		Path dir = Paths.get(path);
		
		String msg = this.checkWriteFile(dir, path);
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		
		FileResult result = new FileResult(0, RESULT_OK);
		try {
			Stream<Path> stream = Files.list(dir);
			final String _path = path;
			stream.forEach(f->{
				if(Files.isDirectory(f)) {
					FileResult r = this.rmdir(_path + "/" + f.getFileName().toString());
					if(r.errorCode != 0) {
						result.errorCode = r.errorCode;
						result.message = r.message;
					}
				} else this.remove(_path + "/" + f.getFileName().toString());
			});
			
			FileResult r = this.remove(path);
			if(r.errorCode != 0) {
				result.errorCode = r.errorCode;
				result.message = r.message;
			}
		} catch (IOException e) {
			return new FileResult(1, e.toString());
		}
		return result;
	}
	public FileResult mkdir(String path) {
		path = this.toAbsolutePath(path);
		Path dir = Paths.get(path);
		
		String msg = this.checkWriteFile(dir.getParent(), this.getParentPath(path));
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		
		try {
			Files.createDirectories(dir);
			return new FileResult(0, RESULT_OK);
		} catch (IOException e) {
			return new FileResult(1, e.toString());
		}
	}
	public FileResult mkfile(String path) {
		path = this.toAbsolutePath(path);
		Path file = Paths.get(path);
		
		String msg = this.checkWriteFile(file.getParent(), this.getParentPath(path));
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		
		try {
			Files.createFile(file);
			return new FileResult(0, RESULT_OK);
		} catch (IOException e) {
			return new FileResult(1, e.toString());
		}
	}
	public FileResult move(String from, String to) {
		from = this.toAbsolutePath(from);
		to = this.toAbsolutePath(to);
		Path fromPath = Paths.get(from);
		Path toPath = Paths.get(to);
		
		String msg = this.checkReadFile(fromPath, from);
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		msg = this.checkWriteFile(toPath, to);
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		msg = this.checkWriteFile(fromPath.getParent(), this.getParentPath(from));
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		msg = this.checkWriteFile(toPath.getParent(), this.getParentPath(to));
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		
		try {
			Files.move(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING );
			return new FileResult(0, RESULT_OK);
		} catch (IOException e) {
			return new FileResult(1, e.toString());
		}
	}
	
	public FileResult fileStateSimple(String path) {
		path = this.toAbsolutePath(path);
		Path file = Paths.get(path);
		return this.fileStateSimple(file);
	}
	public FileResult fileState(String path) {
		path = this.toAbsolutePath(path);
		Path file = Paths.get(path);
		return this.fileState(file);
	}
	public FileResult fileStateSimple(Path path) {
		FileAttr attrs = new FileAttr();
		
		int rwe = 0;
		char fileType = '-';
		
		attrs.setExists(Files.exists(path));
		attrs.setFileName(path.getFileName().toString());
		
		if(Files.isDirectory(path)) fileType = 'd';
		else if(Files.isRegularFile(path)) fileType = '-';
		else if(Files.isSymbolicLink(path)) fileType = 'l';
		else fileType = '?';
		attrs.setFileType(fileType);
		
		if(Files.isReadable(path)) rwe += 4;
		if(Files.isWritable(path)) rwe += 2;
		if(Files.isExecutable(path)) rwe += 1;
		attrs.setRWE(rwe);
		return new FileResult(0, RESULT_OK, attrs);
	}
	
	public FileResult fileState(Path path) {
		FileResult result = this.fileStateSimple(path);
		FileAttr attrs = result.attrPayload;
		BasicFileAttributeView basicView = 
				  Files.getFileAttributeView(path, BasicFileAttributeView.class);
		
		try {
			BasicFileAttributes basicAttribs = basicView.readAttributes();
			attrs.setSize(Files.size(path));
			attrs.setOwner(Files.getOwner(path).getName());
			attrs.setHidden(Files.isHidden(path));
			attrs.setCreationTime(basicAttribs.creationTime().toMillis());
			attrs.setLastAccessTime(basicAttribs.lastAccessTime().toMillis());
			attrs.setLastModifiedTime(basicAttribs.lastModifiedTime().toMillis());
		} catch (IOException e) {
			result.errorCode = 1;
			result.message = e.toString();
			e.printStackTrace();
		}
		return result;
	}
	
	
	public FileResult openFile(String path, OpenOption... opts) {
		path = this.toAbsolutePath(path);
		Path file = Paths.get(path);
		List<String> msgList = new ArrayList<String>();
		
		Set<OpenOption> optsSet = new HashSet<OpenOption>(Arrays.asList(opts));
		if(!optsSet.contains(StandardOpenOption.CREATE) && !optsSet.contains(StandardOpenOption.CREATE_NEW)) {
			if(optsSet.contains(StandardOpenOption.READ)) msgList.add(this.checkReadFile(file, path));
			if(optsSet.contains(StandardOpenOption.WRITE)) msgList.add(this.checkWriteFile(file, path));
		}
		if(optsSet.contains(StandardOpenOption.CREATE) || optsSet.contains(StandardOpenOption.CREATE_NEW) || optsSet.contains(StandardOpenOption.DELETE_ON_CLOSE)) {
			msgList.add(this.checkWriteFile(file.getParent(), this.getParentPath(path)));
		}
		for(String msg:msgList) {
			if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		}
		
		FileChannel fileChannel = null;
		try {
			fileChannel = FileChannel.open(file, opts);
			return new FileResult(0, RESULT_OK, fileChannel);
		} catch (IOException e) {
			e.printStackTrace();
			return new FileResult(1, e.toString());
		}
	}
	
	public FileResult closeFile(FileChannel fc) {
		try {
			fc.close();
			return new FileResult(0, RESULT_OK);
		} catch (IOException e) {
			e.printStackTrace();
			return new FileResult(1, RESULT_FAIL);
		}
	}
	
	public void readAll(FileChannel fc, ByteSender writer) {
		try {
			long readLen = fc.transferTo(0, fc.size(), writer);
			writer.send(new FileResult(0, RESULT_OK, readLen).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			writer.send(new FileResult(1, e.toString(), 0L).getBytes());
		}
	}
	public void readRange(FileChannel fc, ByteSender writer, long position, long length) {
		try {
			long readLen = fc.transferTo(position, length, writer);
			writer.send(new FileResult(0, RESULT_OK, readLen).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			writer.send(new FileResult(1, e.toString(), 0L).getBytes());
		}
	}
	public void writeAll(FileChannel fc, ByteSender writer, ByteIterator reader, long length) {
		try {
			long writtenLen = fc.transferFrom(reader, 0, length);
			writer.send(new FileResult(0, RESULT_OK, writtenLen).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			writer.send(new FileResult(1, e.toString(), 0L).getBytes());
		}
	}
	public void writeRange(FileChannel fc, ByteSender writer, ByteIterator reader, long position, long length) {
		try {
			long writtenLen = fc.transferFrom(reader, position, length);
			writer.send(new FileResult(0, RESULT_OK, writtenLen).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			writer.send(new FileResult(1, e.toString(), 0L).getBytes());
		}
	}
	public void transferRange(FileChannel srcFc, FileChannel destFc, ByteSender writer, long srcPosition, long destPosition, long length) {
		try {
			destFc.position(destPosition);
			long transferredLen = srcFc.transferTo(srcPosition, length, destFc);
			writer.send(new FileResult(0, RESULT_OK, transferredLen).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			writer.send(new FileResult(1, e.toString(), 0L).getBytes());
		}
	}
	public void appendAll(FileChannel fc, ByteSender writer, ByteIterator reader, long length) {
		try {
			long writtenLen = fc.transferFrom(reader, fc.size(), length);
			writer.send(new FileResult(0, RESULT_OK, writtenLen).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			writer.send(new FileResult(1, e.toString(), 0L).getBytes());
		}
	}
	public void curl(ByteIterator reader, ByteSender writer, SocketChannel sc) {
		final short requestNumbers = FileSystemServer.getShortFromBytes(reader.nextBytes(2));
		List<Callable<Object>> curlFuncList = new ArrayList<Callable<Object>>(requestNumbers);
		
		
		for(short i=0; i<requestNumbers; i++) {
			short urlLen = FileSystemServer.getShortFromBytes(reader.nextBytes(2));
			String url = new String(reader.nextBytes(urlLen));
			short fnLen = FileSystemServer.getShortFromBytes(reader.nextBytes(2));
			String fn = new String(reader.nextBytes(fnLen));
			long bodyLen = FileSystemServer.getLongFromBytes(reader.nextBytes(8));
			Callable func = this._curl(url, fn, reader, writer, bodyLen);
			if(func != null) curlFuncList.add(func);
		}
		
		try {
			Collection<Future<Object>> futures = this.executorService.invokeAll(curlFuncList);
			for (Future<Object> future:futures) {
			    future.get();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} finally {
			try {
				sc.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	private void sendCurlResult(ByteSender writer, int errCode, String msg, String url, String fn, long contentLength) {
		this.doLock(curlLock, a->{
			writer.send(new FileResult(errCode, msg, url).getBytes());
			writer.send(FileSystemServer.getBytesFromShort((short) fn.length()));
			writer.send(fn.getBytes());
			writer.send(FileSystemServer.getBytesFromLong(contentLength));
			return null;
		}, null);
	}
	public Callable _curl(String url, String fn, ByteIterator reader, ByteSender writer, long bodyLen){
		long[] contentLength = new long[] {0L};
		final String fileName = this.toAbsolutePath(fn);
		Path file = Paths.get(fileName);
		
		String msg = this.checkWriteFile(file.getParent(), this.getParentPath(fileName));
		if(msg.compareTo(RESULT_OK) != 0) {
			this.sendCurlResult(writer, 1, msg, url, fileName, 0L);
			return null;
		}
		if(Files.exists(file)) {
			this.sendCurlResult(writer, 1, "File already exists.", url, fileName, 0L);
			return null;
		}
		
		//solve request
		HttpServer.Request request = new HttpServer.Request();
		request.parseRequest(reader);

		Map<String, String> headers = request.getHeaders();
		
		try {
			URL urlObj = new URL(url);
			HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
			//method
			
			connection.setRequestMethod(request.getMethod());
				
			//header
			for(Entry<String, String> entry:headers.entrySet()) {
				connection.setRequestProperty(entry.getKey(), entry.getValue());
			}
			connection.setDoInput(true);
			if(bodyLen != 0) {
				connection.setDoOutput(true);
				connection.getOutputStream().write(reader.nextBytes((int) bodyLen));
			}
			
//			this.executorService.execute(new Thread(()-> {
			return ()-> {
				try (ReadableByteChannel readableBC = Channels.newChannel(urlObj.openStream());
					FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)){
					//solve response
					contentLength[0] = fileChannel.transferFrom(readableBC, 0, Long.MAX_VALUE);
					fileChannel.close();
					readableBC.close();
					this.sendCurlResult(writer, 0, RESULT_OK, url, fileName, contentLength[0]);
				} catch (IOException e) {
					this.sendCurlResult(writer, 1, e.toString(), url, fileName, contentLength[0]);
					e.printStackTrace();
				}
				return null;
			};
//			}));
			
		} catch (IOException e1) {
			this.sendCurlResult(writer, 1, e1.toString(), url, fileName, contentLength[0]);
			e1.printStackTrace();
		}
		return null;
	}
	
	public void fetch(String url, ByteSender writer, ByteIterator reader, long bodyLen) {
		HttpServer.Request request = new HttpServer.Request();
		HttpServer.Response response = new HttpServer.Response(writer);
		ReadableByteChannel readableBC = null;
		//solve request
		request.parseRequest(reader);
		Map<String, String> headers = request.getHeaders();
		try {
			URL urlObj = new URL(url);
			HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
			//method
			connection.setRequestMethod(request.getMethod());
			
			//header
			for(Entry<String, String> entry:headers.entrySet()) {
				connection.setRequestProperty(entry.getKey(), entry.getValue());
			}
			connection.setDoInput(true);
			if(bodyLen != 0) {
				connection.setDoOutput(true);
				connection.getOutputStream().write(reader.nextBytes((int) bodyLen));
			}
			
			readableBC = Channels.newChannel(urlObj.openStream());
			//solve response
			//header
			Map<String, List<String>> resHeaders = connection.getHeaderFields();
			for(Entry<String, List<String>> entry:resHeaders.entrySet()) {
				String key = entry.getKey();
				for(String val:entry.getValue()) {
					if(key == null) {
						String[] startLine = val.split(" ");
						response.setHttpVersion(startLine[0].split("/")[1]);
						response.setStatusCode(Integer.valueOf(startLine[1]));
						response.setReasonPhrase(startLine[2]);
					}else response.setDuplicateHeader(key, val);
				}
			}
			
			response.sendHeader();
			
			//response body
			long contentLength = response.getHeaderBytes().length;
			ByteBuffer bb = ByteBuffer.allocate(1024);
			do {
				readableBC.read(bb);
				if(bb.position() == 0) {
					break;
				}
				contentLength += bb.position();
				bb.flip();
				writer.write(bb);
				bb.clear();
			}while(true);
			writer.send(new FileResult(0, RESULT_OK, contentLength).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			writer.send(new FileResult(1, e.toString(), 0L).getBytes());
		}
	}
	
	public FileResult setAttribute(String path, boolean setReadOnly, boolean readOnly, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) {
		path = this.toAbsolutePath(path);
		Path file = Paths.get(path);
		
		String msg = this.checkFile(file, path);
		if(msg.compareTo(RESULT_OK) != 0) return new FileResult(1, msg);
		// Query file system
		try {
			FileStore fileStore = Files.getFileStore(file);
			if (fileStore.supportsFileAttributeView(DosFileAttributeView.class)) {
				DosFileAttributeView attrs =
			            Files.getFileAttributeView(file, DosFileAttributeView.class);
//				attrs.setArchive(false);
//				attrs.setHidden(false);
				if(setReadOnly) attrs.setReadOnly(readOnly);
//				attrs.setSystem(false);
				attrs.setTimes(lastModifiedTime, lastAccessTime, createTime);
			} else if (fileStore.supportsFileAttributeView(PosixFileAttributeView.class)) {
				PosixFileAttributeView attrs =
			            Files.getFileAttributeView(file, PosixFileAttributeView.class);
				if(setReadOnly) {
					Set<PosixFilePermission> permissions = new HashSet<PosixFilePermission>(attrs.readAttributes().permissions());
					if(readOnly) {
						permissions.remove(PosixFilePermission.OWNER_WRITE);
					}else {
						permissions.add(PosixFilePermission.OWNER_WRITE);
					}	
					attrs.setPermissions(permissions);
				}
//				attrs.setGroup(null);
//				attrs.setOwner(null);
				attrs.setTimes(lastModifiedTime, lastAccessTime, createTime);
			}
			return new FileResult(0, RESULT_OK);
		} catch (IOException e) {
			e.printStackTrace();
			return new FileResult(1, e.toString());
		}
		
		
		
	}
	
	public void test(String path, ByteSender writer, ByteIterator reader) {
		HttpServer.Request request = new HttpServer.Request();
		request.parseRequest(reader);
		try {
			URL url = new URL("http://127.0.0.1/");
			URLConnection connection = url.openConnection();
			ReadableByteChannel readableBC = null;
			readableBC = Channels.newChannel(url.openStream());
//			connection.setDoOutput(true);
//			connection.setDoInput(true);
			ByteBuffer bb = ByteBuffer.allocate(1024);
			readableBC.read(bb);
			writer.send(new FileResult(0, RESULT_OK, new String(bb.array())).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
			writer.send(new FileResult(1, e.toString(), "").getBytes());
		}
		
	}
}
