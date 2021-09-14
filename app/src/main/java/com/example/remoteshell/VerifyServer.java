package com.example.remoteshell;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.O)
public abstract class VerifyServer extends HttpServer{

	class ConnectContext{
		ConnectContext(){
			
		}
		public void resetContext() {
			this.responseData = new byte[0];
			this._isResponseDataEncrypted = false;
		}
		public void beforeSend(Response response) {
			if(this.isSecure() && !this._isResponseDataEncrypted) {
				this.aesEncrypt.encrypt(this.responseData, 0, this.responseData.length, false);
				this._isResponseDataEncrypted = true;
			}
			
			response.setBody(this.responseData);
			this.requestData = null;
		}
		public byte[] getResponseData() {
			return this.responseData;
		}
		public void setResponseData(byte[] responseData) {
			this.responseData = responseData;
		}
		public byte[] getRequestData() {
			if(this.requestData == null) this.requestData = this.reader.getRemainingBytes();
			return requestData;
		}
		public ByteSender getWriter() {
			return this.writer;
		}
		public ByteIterator getReader() {
			return this.reader;
		}
		public boolean isSecure() {
			return this._isSecure;
		}
		private byte[] responseData = new byte[0];
		private byte[] requestData;
		private boolean _isResponseDataEncrypted = false;
		private boolean _isSecure = false;
//		private RSA rsa;
		private AesCtr aesEncrypt;
		private AesCtr aesDecrypt;
		private ByteSender writer;
		private ByteIterator reader;
	}
	
	private final static String QUERY_NAME_TOKEN = "token";
	private final static String QUERY_NAME_SECURE = "secure";
	private final static String QUERY_NAME_ESTABLISH = "establish";
	
	
	private String token;
	private Map<String, ConnectContext> contextMap = new HashMap<String, ConnectContext>();
	private Map<String, List<ConnectContext>> contextByHostMap = new HashMap<String, List<ConnectContext>>();
	protected Set<String> allowHosts;
	
	private RSA establishRSA = new RSA();

	VerifyServer(){
		super();
		this.setToken(this.generateToken());
		this.allowHosts = new HashSet<String>(Arrays.asList("*"));
	}
	VerifyServer(String token, Collection<String> allowHosts, Collection<Integer> ports){
		super(ports);
		if(token == null) token = this.generateToken();
		if(allowHosts == null) allowHosts = new HashSet<String>();
		this.allowHosts = new HashSet<String>(allowHosts);
		this.setToken(token);
	}
	
	private String generateSessionId() {
		String id = null;
		byte[] randBytes = new byte[32];
		SecureRandom crypto = new SecureRandom();
		do {
			crypto.nextBytes(randBytes);
			id = Base64.getEncoder().encodeToString(randBytes);
			id = id.replaceAll("=", "_");
		}while(contextMap.containsKey(id));
		return id;
	}
	private String generateToken() {
		return this.generateToken(32);
	}
	private String generateToken(int length) {
		String token = null;
		byte[] randBytes = new byte[length];
		SecureRandom crypto = new SecureRandom();
		crypto.nextBytes(randBytes);
		token = Base64.getEncoder().encodeToString(randBytes);
		token = token.replaceAll("=", "_");
		token = token.replaceAll("/", "-");
		return token;
	}
	private Map<String, byte[]> splitSecureData(byte[] bytes, int n) {
		Map<String, byte[]> result = new HashMap<String, byte[]>();
		int count = 0;
		int lastOffset = 0;
		int lastEqualOffset = 0;
		for(int i=1; i<bytes.length; i++) {
			if(bytes[i] == 61) { // "="
				lastEqualOffset = i;
			} else if(bytes[i-1] == 13 && bytes[i] == 10) { // "\r\n"
				result.put(
						new String(Arrays.copyOfRange(bytes, lastOffset, lastEqualOffset)),
						Arrays.copyOfRange(bytes, lastEqualOffset+1, i-1)
				);
				count++;
				lastOffset = i+1;
				if(count >= n) break;
			}
		}
		for(int i=lastOffset; i<bytes.length; i++) {
			if(bytes[i] == 61) { // "="
				lastEqualOffset = i;
				result.put(
						new String(Arrays.copyOfRange(bytes, lastOffset, lastEqualOffset)),
						Arrays.copyOfRange(bytes, lastEqualOffset+1, bytes.length)
				);
				break;
			}
		}
		return result;
	}
	private byte[] joinSecureData(String[] keys, String[] vals, byte[] data) {// [id, pubkey]  [fdkbcds]  pubkey
		int totalLen = data.length + keys[keys.length-1].length() + 1;
		for(int i=0; i<vals.length; i++) {
			totalLen += keys[i].length() + vals[i].length() + 3;
		}
		byte[] result = new byte[totalLen];
		int offset = 0;
		for(int i=0; i<vals.length; i++) {
			String s = keys[i] + "=" + vals[i] + "\r\n";
			System.arraycopy(s.getBytes(), 0, result, offset, s.length());
			offset += s.length();
		}
		String s = keys[keys.length-1] + "=";
		System.arraycopy(s.getBytes(), 0, result, offset, s.length());
		offset += s.length();
		System.arraycopy(data, 0, result, offset, data.length);
		return result;
		
	}
	
	private ConnectContext guestContextByHost(Request request, boolean useSecure) {
		ByteIterator reader = request.getReader();
		int idLen = reader.nextByte() & 0xff;
		byte[] rawId = reader.nextBytes(idLen);
		
		if(!useSecure) {
			return this.contextMap.getOrDefault(new String(rawId), null);
		}
		
		String host = request.getHeaders().getOrDefault("Host", "*");
		ConnectContext result = null;
		
		List<ConnectContext> contextList = contextByHostMap.get(host);
		if(contextList.size() == 1) {
			result = contextList.get(0);
		}else {
			for(ConnectContext context:contextList) {
				if(!context.isSecure()) continue;
				AesCtr aesDec = context.aesDecrypt.clone();
				byte[] decryptedId = aesDec.decrypt(rawId, 0, idLen, true);
				
				String id = new String(decryptedId);
				result = contextMap.getOrDefault(id, null);
				if(result == context) break;
			}
		}
		
		result.aesDecrypt.decrypt(rawId, 0, idLen, false); // must decrypt because aes ctr counter
		return result;
	}
	private void addContextByHost(Request request, ConnectContext context) {
		String host = request.getHeaders().getOrDefault("Host", "*");
		if(!contextByHostMap.containsKey(host)) {
			contextByHostMap.put(host, new ArrayList<ConnectContext>());
		}
		contextByHostMap.get(host).add(context);
	}
	
	private boolean setAllowAccessHost(Request request, Response response) {
		String origin =  request.getHeaders().get("Origin").split("/")[2];

//		// remove port
//		int portOffset = origin.indexOf(":");
//		if(portOffset != -1) origin = origin.substring(0, portOffset);

		if(this.allowHosts.contains(origin) || this.allowHosts.contains("*")) {
			response.setDuplicateHeader("Access-Control-Allow-Origin", "http://" + origin);
			return true;
		}
		return false;
	}
	private void establishSecureConnect(Request request, Response response) throws Exception {
		byte[] binData = request.getBody();
		
		if(binData.length == 0) {
			String id = this.generateSessionId();
			byte[] responseBytes;
			byte[] pubkey;
			ConnectContext context = new ConnectContext();
			context._isSecure = true;
			contextMap.put(id, context);
			
//			context.rsa = new RSA();
//			context.rsa.generateKeyPair();
			pubkey = this.establishRSA.getPublicKey();
			
			responseBytes = this.joinSecureData(new String[] {"id", "pubkey"}, new String[] {id}, pubkey);
			response.setBody(responseBytes);
		}else if(binData.length >= 3 && new String(Arrays.copyOfRange(binData, 0, 3)).compareTo("id=") == 0) {
			Map<String, byte[]> aesMap = this.splitSecureData(binData, 1);
			
			String oldId = new String(aesMap.get("id"));
			String newId = this.generateSessionId();
			ConnectContext context = contextMap.getOrDefault(oldId, null);
			contextMap.remove(oldId);
			contextMap.put(newId, context);
			
			byte[] aesKey = aesMap.get("aesKey");
			
			aesKey = this.establishRSA.decrypt(aesKey);
			
			context.aesEncrypt = new AesCtr(aesKey);
			context.aesDecrypt = new AesCtr(aesKey);
			
			byte[] responseBytes = this.joinSecureData(new String[] {"newid", "id", "none"}, new String[] {newId, oldId}, new byte[0]);

			responseBytes = context.aesEncrypt.encrypt(responseBytes);
			response.setBody(responseBytes);
			
			this.addContextByHost(request, context);
		}
		
	}
	private void establishNormalConnect(Request request, Response response){
		String id = this.generateSessionId();
		byte[] responseBytes;
		ConnectContext context = new ConnectContext();
		contextMap.put(id, context);
		responseBytes = this.joinSecureData(new String[] {"id", "none"}, new String[] {id}, new byte[0]);
		response.setBody(responseBytes);
		
		this.addContextByHost(request, context);
	}
	private void verifiedRequest(Request request, Response response, Socket socket, boolean useSecure) {
		
		ConnectContext context = this.guestContextByHost(request, useSecure);
		
		if(context.isSecure()) {
			context.writer = new AesByteSender(socket.getChannel(), context.aesEncrypt);
			context.reader = new AesByteIterator(socket.getChannel(), context.aesDecrypt);
		}else {
			context.writer = response.getWriter();
			context.reader = request.getReader();
		}
		
		context.resetContext();
		this.onRequest(context, request, response, socket);
		context.beforeSend(response);
	}
	abstract void onRequest(ConnectContext context, Request request, Response response, Socket socket);
	@Override
	public void onRequest(Request request, Response response, Socket socket) {
		Map<String, String> param = request.getParameters();
		
		if(this.setAllowAccessHost(request, response) && 
				param.containsKey(QUERY_NAME_TOKEN) && 
				param.get(QUERY_NAME_TOKEN).replaceAll(" ", "+").compareTo(this.token) == 0)
		{
			// check parameters
			boolean useSecure = false;
			boolean isEstablish = false;
			if(param.containsKey(QUERY_NAME_SECURE)) {
				String secureVal = param.get(QUERY_NAME_SECURE);
				useSecure = secureVal.compareTo("true") == 0 || secureVal.compareTo("") == 0;
			}
			if(param.containsKey(QUERY_NAME_ESTABLISH)) {
				String establishVal = param.get(QUERY_NAME_ESTABLISH);
				isEstablish = establishVal.compareTo("true") == 0 || establishVal.compareTo("") == 0;
			}
			
			// establish or request
			if(isEstablish) {
				if(useSecure)
					try {
						this.establishSecureConnect(request, response);
					} catch (Exception e) {
						e.printStackTrace();
					}
				else this.establishNormalConnect(request, response);
			}else {
				this.verifiedRequest(request, response, socket, useSecure);
			}
			
		}else {
			response.setStatusCode(401);
			response.setReasonPhrase("Unauthorized");
			response.setBodyByText("Invalid token.");
		}
		
	}
	public String getToken() {
		return token;
	}
	public void setToken(String token) {
		this.token = token;
	}
	
	@Override
	public void start() throws IOException {
		if(this.establishRSA.getPublicKey() == null || this.establishRSA.getPrivateKey() == null) {
			try {
				this.establishRSA.generateKeyPair(2024);
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		}
		super.start();
	}
	
	public void setKeyPair(byte[] publicKey, byte[] privateKeys) {
		this.establishRSA.setPublicKey(publicKey);
		this.establishRSA.setPrivateKey(privateKeys);
	}
	
}
