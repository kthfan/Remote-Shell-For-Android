package com.example.remoteshell;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@RequiresApi(api = Build.VERSION_CODES.O)
public abstract class HttpServer {
	
	static class Response{
		private int statusCode = 200;
		private String reasonPhrase = "OK";
		private String httpVersion = "1.1";
		private Map<String, String> headers = new HashMap<String, String>();
		private List<String> duplicateHeaders = new ArrayList<String>();
		private byte[] body = new byte[0];
		private Map<String, String> cookie = new HashMap<String, String>();
		private Map<String, String> session;
		
		private ByteSender byteSender;
		private boolean isHeaderWrited = false;
		private boolean isBodyWrited = false;
		private boolean autoClose = true;
		
		private Function afterOnResponse;
		
		Response(Map<String, String> cookie, SocketChannel sc) {
			this.buildCookie(cookie);
			this.autoSetHeader();
			this.byteSender = new ByteSender(sc);
		}
		Response(SocketChannel sc) {
			this.autoSetHeader();
			this.byteSender = new ByteSender(sc);
		}
		Response(ByteSender writer) {
			this.autoSetHeader();
			this.byteSender = writer;
		}

		public String getResponseText() {
			return this.getHeaderText() + new String(this.body, StandardCharsets.UTF_8);
		}
		public boolean sendResponse() {
			boolean result = this.sendHeader();
			return result & this.sendBody();
		}
		public boolean sendBody() {
			if(!this.byteSender.getSocketChannel().isOpen()) {
				System.out.println("Warning: HttpServer:sendBody: Socket channel already closed.");
				return false;
			}
			boolean result = false;
			if(!this.isBodyWrited) {
				result = byteSender.send(this.body);
				this.isBodyWrited = true;
			}
			return result;
		}
		public boolean sendHeader() {
			boolean result = false;
			if(!this.isHeaderWrited) {
				result = byteSender.send(this.getHeaderBytes());
				this.isHeaderWrited = true;
			}
			return result;
		}
		public byte[] getHeaderBytes() {
			this.forcedToSetHeader();
			String resText = "";
			resText += "HTTP/" + httpVersion + " " + statusCode + " " + reasonPhrase + "\r\n"; // ; crlf to split start line and headers
			resText += this._getHeaderText(this.headers);
			resText += this.getDuplicateHeaderText(this.duplicateHeaders);
			resText += this.getCookieText(this.cookie) + "\r\n";//add multi cookies; add crlf to split headr and body
			return resText.getBytes();
		}
		public String getHeaderText() {
			return new String(this.getHeaderBytes(), StandardCharsets.UTF_8);
		}
		private String _getHeaderText(Map<String, String> headers) {
			String result = "";
			for(Entry<String, String> entry:headers.entrySet()) {
				result += entry.getKey() + ":" + entry.getValue() + "\r\n";
			}
			return result;
		}

		private void forcedToSetHeader() {
			SimpleDateFormat formatter = new SimpleDateFormat("E, dd MMMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
			headers.put("Date", formatter.format(new Date()));
			if(this.afterOnResponse != null) this.afterOnResponse.apply(null);
//			headers.put("Content-Length", String.valueOf(this.body.length));
		}
		private void autoSetHeader() {
			headers.put("Expires", "-1");
			headers.put("Cache-Control", "no-cache");
			headers.put("Content-Type", "application/octet-stream");
			headers.put("Connection", "Keep-Alive");
			headers.put("Keep-Alive", "timeout=5, max=100");
		}
		private void buildCookie(Map<String, String> cookie) {
//			if(cookie == null) return;
			for(Entry<String, String> entry:cookie.entrySet()) {
				this.cookie.put(entry.getKey(), entry.getValue());
			}
		}
		private String getDuplicateHeaderText(List<String> duplicateHeaders) {
			String result = "";
			for(String header:duplicateHeaders) {
				result += header + "\r\n";
			}
			return result;
		}
		private String getCookieText(Map<String, String> cookie) {
			String result = "";
			for(Entry<String, String> entry:cookie.entrySet()) {
				result += "Set-Cookie:" + entry.getKey() + "=" + entry.getValue() + "; SameSite=Lax \r\n";
			}
			return result;
		}
		public List<String> getDuplicateHeaders() {
			return duplicateHeaders;
		}
		public void setDuplicateHeader(String name, String value) {
			duplicateHeaders.add(name + ":" + value);
		}
		public Map<String, String> getHeaders() {
			return headers;
		}
		public void setHeader(String name, String value) {
			headers.put(name, value);
		}
		public String getHttpVersion() {
			return this.httpVersion;
		}
		public void setHttpVersion(String httpVersion) {
			this.httpVersion = httpVersion;
		}
		public int getStatusCode() {
			return statusCode;
		}
		public void setStatusCode(int statusCode) {
			this.statusCode = statusCode;
		}
		public String getReasonPhrase() {
			return reasonPhrase;
		}
		public void setReasonPhrase(String reasonPhrase) {
			this.reasonPhrase = reasonPhrase;
		}
		public void setBodyByText(String body) {
			this.body = body.getBytes(StandardCharsets.UTF_8);
		}
		public boolean isHeaderSent() {
			return this.isHeaderWrited;
		}
		public boolean isBodySent() {
			return this.isBodyWrited;
		}
		public ByteSender getWriter() {
			return this.byteSender;
		}
		public byte[] getBody() {
			return body;
		}
		public void setBody(byte[] body) {
			this.body = body;
		}
		public Map<String, String> getCookies() {
			return cookie;
		}
		
		public void setCookie(String key, String value) {
			cookie.put(key, value);
		}
		public Map<String, String> getSessions() {
			if(this.session == null) this.session = new HashMap<String, String>();
			return this.session;
		}
		public void setSession(String key, String value) {
			if(this.session == null) this.session = new HashMap<String, String>();
			this.session.put(key, value);
		}
		public boolean isAutoClose() {
			return autoClose;
		}
		public void setAutoClose(boolean autoClose) {
			this.autoClose = autoClose;
		}
	}
	
	static class Request{
		private String method;
		private String requestTarget;
		private String path;
		private Map<String, String> parameters;
		private String httpVersion;
		private Map<String, String> headers;
		private byte[] body;
		private Map<String, String> cookie;
		private Map<String, String> session;
		
		private ByteIterator byteIter;
		
		Request(){}
		
		public boolean parseRequest(SocketChannel sc) {
			return this.parseRequest(new ByteIterator(sc));
		}
		public boolean parseRequest(ByteIterator reader) {
			this.byteIter = reader;
			this.byteIter.getConcated(); // clear cancat buffer
			List<String> lineList = new ArrayList<String>(); // start line and headers
			int lastCRLFIndex = 0;
			byte lastByte = this.byteIter.getByteAndConcat();
			if(!this.byteIter.hasNext()) return false;
			byte currentByte = this.byteIter.getByteAndConcat();
			if(!this.byteIter.hasNext()) return false;
			int i = 1;
			do {
				if(currentByte == 10 && lastByte == 13) { // if requestData[i-1:i+1] = "\r\n"
					if(i-1 == lastCRLFIndex) { // if duplicate \r\n occur, then split headers and body
						lastCRLFIndex = i+1;
						// body is lastCRLFIndex to requestData.length
						break;
					}
					byte[] tmpBytes = this.byteIter.getConcated();
					lineList.add(new String(Arrays.copyOfRange(tmpBytes, 0, tmpBytes.length-2), StandardCharsets.UTF_8));
					lastCRLFIndex = i+1;
				}
				lastByte = currentByte;
				currentByte = this.byteIter.getByteAndConcat();
				i++;
			}while(this.byteIter.hasNext());
			if(lineList.isEmpty()) { // invalid http header
				return false;
			}
			
			String startLine = lineList.remove(0);
			String[] headersLine = (String[]) lineList.toArray(new String[lineList.size()]);
			
			String[] startLineInfo = parseStartLine(startLine);
			this.method = startLineInfo[0]; this.requestTarget = startLineInfo[1]; this.httpVersion = startLineInfo[2];
			try{
				this.parseRequestTarget(this.requestTarget);
			}catch (UnsupportedEncodingException e){
				e.printStackTrace();
			}
			this.headers = parseHeaders(headersLine);
			this.cookie = parseCookie(this.headers);
			
			return true;
		}
		private String[] parseStartLine(String startLine) {
			String[] startLineArr = startLine.split(" ");
			startLineArr[2] = startLineArr[2].split("/")[1];
			
			return startLineArr;
		}
		private void parseRequestTarget(String requestTarget) throws UnsupportedEncodingException {
			if(requestTarget.compareTo("*") == 0) return;
			boolean containHttp = requestTarget.matches("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
			if(containHttp) requestTarget = requestTarget.split("/", 4)[3]; // requestTarget to be path + query
			
			// check if query exists
			int queryOffset = requestTarget.indexOf("?");
			this.path = requestTarget;
			if(queryOffset == -1) return;
			this.path = requestTarget.substring(0, queryOffset);
			
			if(queryOffset == requestTarget.length() - 1) return; // something like http://domain.com/path/?
			
			this.parameters = new HashMap<String, String>();
			requestTarget = requestTarget.substring(queryOffset + 1);
			String[] paramArr = requestTarget.split("&");
			for(String param:paramArr) {
				String[] arr = param.split("=");
				String name, value ;
				if(arr.length == 0) continue; // & &
				else if(arr.length == 1) { // ; name;
					name = URLDecoder.decode(arr[0].trim(), String.valueOf(StandardCharsets.UTF_8));
					value = "";
				}else { // ; name=john;
					name = URLDecoder.decode(arr[0].trim(), String.valueOf(StandardCharsets.UTF_8));
					value = URLDecoder.decode(arr[1].trim(), String.valueOf(StandardCharsets.UTF_8));
				}
				this.parameters.put(name, value);
			}
		}
		private Map<String, String> parseHeaders(String[] headers) {
			Map<String, String> result = new HashMap<String, String>();
			for(String line:headers) {
				String[] headerField = line.split(":", 2);
				String fieldName = this.upperCaseFieldName(headerField[0].trim());
				String fieldValue = headerField[1].trim();
				result.put(fieldName, fieldValue);
			}
			return result;
		}
		private String upperCaseFieldName(String fieldName) {
			//Convert content-length to Content-Length
			String[] arr = fieldName.split("-");
			String result = "";
			result +=  arr[0].substring(0, 1).toUpperCase() + arr[0].substring(1);
			for(int i=1; i<arr.length; i++) {
				result +=  "-" + arr[i].substring(0, 1).toUpperCase() + arr[i].substring(1);
			}
			return result;
		}
		private Map<String, String> parseCookie(Map<String, String> headers){
			Map<String, String> result = new HashMap<String, String>();
			if(!headers.containsKey("Cookie")) return result;
			String cookieText = headers.get("Cookie");
			String[] cookieArr = cookieText.split(";");
			for(String line:cookieArr) {
				String[] arr = line.split("=", 2);
				if(arr.length == 0) continue; // ; ;
				else if(arr.length == 1) result.put(arr[0].trim(), ""); // ; name;
				else result.put(arr[0].trim(), arr[1].trim()); // ; name=john;
			}
			return result;
		}
		
		private void buildBody() {
			if(byteIter.hasNext())
				this.body = byteIter.getRemainingBytes();
		}
		
		public String getMethod() {
			return method;
		}
		public String getPath() {
			return path;
		}
		public String getRequestTarget() {
			return requestTarget;
		}
		public String getHttpVersion() {
			return httpVersion;
		}
		public Map<String, String> getParameters() {
			return parameters;
		}
		public Map<String, String> getHeaders() {
			return headers;
		}
		public String getBodyText() {
			return new String(this.getBody(), StandardCharsets.UTF_8);
		}
		public byte[] getBody() {
			buildBody();
			return this.body;
		}
		public ByteIterator getReader() {
			return this.byteIter;
		}

		public Map<String, String> getCookies() {
			return cookie;
		}

		public Map<String, String> getSessions() {
			return session;
		}
	}
	
	protected Set<Integer> ports;
	private Map<String, Map<String, String>> sessionMap = new HashMap<String, Map<String, String>>();
	private Set<String> sessionIdSet = new HashSet<String>();
	private boolean keepRunning = true;
	
	HttpServer(){
		this.ports = new HashSet<Integer>(Arrays.asList(80));
	}
	HttpServer(Collection<Integer> ports){
		this.ports = new HashSet<Integer>(ports);
	}
	
	abstract public void onRequest(Request request, Response response, Socket socket);
	
	private void beforeOnRequest(Request request, Response response) {
		//parse session by id
		String sessionId = request.cookie.getOrDefault("__SERVER_SESSION_ID", null);
		if(sessionId == null) return;
		Map<String, String> session = sessionMap.getOrDefault(sessionId, null);
		if(session == null) return;
		request.session = session;
		response.session = session;
	}

	private String generateSessionId() {
		String sessionId = null;
		byte[] randBytes = new byte[32];
		SecureRandom crypto = new SecureRandom();
		do {
			crypto.nextBytes(randBytes);
			sessionId = Base64.getEncoder().encodeToString(randBytes);
			sessionId = sessionId.replaceAll("=", "_");
		}while(sessionIdSet.contains(sessionId));
		sessionIdSet.add(sessionId);
		return sessionId;
	}
	private void afterOnRequest(Request request, Response response) {
		// set session id if session is set
		if(request.getSessions() != null) return; // session id already set.
		Map<String, String> session = response.getSessions();
		if(session == null) return; // session in response never called.
		String sessionId = this.generateSessionId();
		this.sessionMap.put(sessionId, session);
		// set session id to cookie 	
		response.setCookie("__SERVER_SESSION_ID", sessionId);
	}
	private boolean doResponse(SocketChannel sc) {
		Request request = new Request();
		if(!request.parseRequest(sc)) {
//			Console.getInstance().log(new String(request.getReader().nextChuckBytes()));
			return false;// Invalid http request
		}
		
		if(request.getMethod() == null) return false; // Invalid http request
//		Console.getInstance().log(request.getMethod());
		Response response = new Response(request.getCookies(), sc);
		this.beforeOnRequest(request, response);
		response.afterOnResponse = t -> {
			this.afterOnRequest(request, response);
			return null;
		};
//		new Thread(() -> {
			try {
				if(sc.isOpen() && sc.isConnected()) {
					
					this.onRequest(request, response, sc.socket());
//					this.afterOnRequest(request, response);
					response.sendResponse();
				}
				
				if(response.isAutoClose()) sc.close();
			} catch (IOException e) {
				System.out.println("Warning: HttpServer:doResponse" + e.getMessage());
				try {
					sc.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
//		}).start();
		return true;
	}
//	public void startAnsyc() throws IOException, InterruptedException, ExecutionException, TimeoutException {
//		AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
//		server.bind(new InetSocketAddress("127.0.0.1", 1234));
//		
//		while(true && this.keepRunning) {
//			Future<AsynchronousSocketChannel> acceptCon = server.accept();
//			AsynchronousSocketChannel client = acceptCon.get(60, TimeUnit.SECONDS);
//			
//			if ((client!= null) && (client.isOpen())) {
//				
//				if (!this.doResponse(client)) {
//					client.close();
//	                  System.out.println("Connection closed...");
//	                  System.out.println(
//	                     "Server will keep running. " +
//	                     "Try running another client to " +
//	                     "re-establish connection");
//	               }
//				
//				ByteBuffer buffer = ByteBuffer.allocate(1024);
//				Future<Integer> readval = client.read(buffer);
//				if(buffer.array().length == 0) {
//		            client.close();
//		            return;
//		        }
//		        System.out.println("Received from client: " + new String(buffer.array()).trim());
//		        readval.get();
//		            
//		        buffer.flip();
//		        String str= "HTTP/1.1 200 OK\r\n\r\nhello even";
//		        Future<Integer> writeVal = client.write(ByteBuffer.wrap(str.getBytes()));
//		        System.out.println("Writing back to client: " +str);
//		        writeVal.get();
//		        buffer.clear();  
//	        }
//			client.close();
//		}
//		
//	}
	public void start() throws IOException {
		InetAddress host = InetAddress.getByName(null);
		Selector selector = Selector.open();
		List<ServerSocketChannel> serverSocketChannelList = new ArrayList<ServerSocketChannel>();
		for (int port : this.ports) {
			ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.bind(new InetSocketAddress(host, port));
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			serverSocketChannelList.add(serverSocketChannel);
		}
		SelectionKey key = null;
		this.keepRunning = true;
		System.out.println("Server running...");
		while (this.keepRunning) {
			if (selector.select() <= 0)
				continue;
			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			Iterator<SelectionKey> iterator = selectedKeys.iterator();
			while (iterator.hasNext() && this.keepRunning) {
				key = (SelectionKey) iterator.next();
	            iterator.remove();
	            if (key.isAcceptable()) {
	               SocketChannel sc = ((ServerSocketChannel) key.channel()).accept();
	               sc.configureBlocking(false);
	               sc.register(selector, SelectionKey.OP_READ);
	               System.out.println("Connection Accepted: " + sc.getLocalAddress() + "n");
	            }
	            if (key.isReadable()) {
	               SocketChannel sc = (SocketChannel) key.channel();
	            
	               if(!sc.isOpen() || !sc.isConnected()) {
	            	   sc.close();
	            	   continue;
	               }
	               try {
//	            	   byte[] result = this.readUntilEnd(sc);
		               
		               if (!this.doResponse(sc)) {
		                  sc.close();
		                  System.out.println("Connection closed...");
		                  System.out.println(
		                     "Server will keep running. " +
		                     "Try running another client to " +
		                     "re-establish connection");
		               }
	               }catch(IOException e) {
	            	   System.out.println(e.getMessage() + ": read fail.");
	            	   sc.close();
	               }
//	               sc.register(selector, SelectionKey.OP_WRITE);
	            }
			}
		}
		serverSocketChannelList.forEach(ssc -> {
			try {
				ssc.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}
	public void close() {
		this.keepRunning = false;
	}
}


