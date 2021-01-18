package com.sn1pe2win.api;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sn1pe2win.BGBot.Logger;
import com.sn1pe2win.api.Handshake.ResponsePayload;

import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;

public class OAuthHandler {

	public class StateAuth {
		public final Member requestingMember;
		public final String stateToken;
		public String url;
		public Message message;
		public final Handshake listener;
		public final long expires;
		
		private StateAuth(Member m, String st, Handshake l, Message msg, long ex) {
			this.requestingMember = m;
			this.stateToken = st; 
			this.listener = l;
			this.expires = ex;
		}
	}
	
	private final int LISTEN_PORT = 1306;
	private ServerSocket listenSocket = null;
	
	private static final int RESPONSE_LENGTH = 256;
	private static final String BASE_URL = "https://www.bungie.net/de/OAuth/Authorize?response_type=code";
	private static final String ALLOWED_STATE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
	private final Random random = new Random();
	/**StateAuth states sind f�r 30 Minuten g�ltig, danach werden sie gel�scht*/
	private ArrayList<StateAuth> stateAuth = new ArrayList<OAuthHandler.StateAuth>(1);
	public final int applicationID;
	
	public OAuthHandler(int applicationID) {
		this.applicationID = applicationID;
	}
	
	/**Port is hardcoded and relies on the bohrmaschinengang.de/success.php site
	 * If success, this listener recieved a String in the following form:<br>
	 * [CODE];[STATE]
	 * If a state with the equivalent recieved state was found, a POST request is made to recieve the Token. The handshake
	 * @throws IOException */
	public void listen() throws IOException {
		if(listenSocket != null) return;
		
		Logger.log("Listening on port " + LISTEN_PORT + "...");
		listenSocket = new ServerSocket(LISTEN_PORT);
		listenSocket.setReuseAddress(true);
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					main: while(true) {
						Socket connectionSocket = listenSocket.accept();
						Logger.log("Accepting incoming connection...");
			            InputStreamReader inFromClient = new InputStreamReader(connectionSocket.getInputStream());
			            final BufferedWriter resWriter = new BufferedWriter(new OutputStreamWriter(connectionSocket.getOutputStream()));
			            int c = 0;
			            String body = "";
			            while(true) {
			                c = inFromClient.read();
			                body += (char) c;
			                if(!inFromClient.ready())  break;
			            }
			            String[] split = body.split(";");
			            final String code;
			            final String state;
			            if(split.length >= 2) {
			            	code = body.split(";")[0].replaceAll("\n", "").replaceAll("\t", "").replaceAll("\r", "");
			            	state = body.split(";")[1].replaceAll("\n", "").replaceAll("\t", "").replaceAll("\r", "");
			            } else {
			            	Logger.err("Recieved package in the wrong pattern.");
			            	send_response("Der Link ist ung�ltig oder abgelaufen. Du kannst das Fenster schlie�en", resWriter);
			            	continue;
			            }
			            
						boolean found = false;
	
			            for(StateAuth states : stateAuth) {
			            	if(states.stateToken.equals(state)) {
			            		new Thread(new Runnable() {
									@Override
									public void run() {
										Logger.log("Found matching state." + state + " with code " + code + " Sending POST");
					            		Response<JsonObject> response = BungieAPI.sendPOST("/App/oauth/token/",  
							            		"client_id=" + applicationID + 
							            		"&grant_type=authorization_code" +
							            		"&code=" + code);
							    		if(response.containsPayload()) {
							    			JsonPrimitive membershipId = response.getPayload().getAsJsonPrimitive("membership_id");
							    			JsonPrimitive access_token = response.getPayload().getAsJsonPrimitive("access_token");
							    			JsonPrimitive refresh_token = response.getPayload().getAsJsonPrimitive("refresh_token");
							    			JsonPrimitive expires = response.getPayload().getAsJsonPrimitive("refresh_expires_in");
							    			
							    			if(membershipId == null) {
							    				states.listener.error("POST request ist fehlgeschlagen " + response.toString());
							    				Response<?> res = states.listener.error("POST request ist fehlgeschlagen " + response.toString());
								    			send_response("Etwas ist schiefgelaufen: <br>" + res.errorMessage, resWriter);
							    			} else {
							    				ResponsePayload payload = new ResponsePayload(access_token != null ? access_token.getAsString() : "", 
							    						refresh_token != null ? refresh_token.getAsString() : "", 
							    						membershipId.getAsString(), 
							    						expires != null ? (long) (System.currentTimeMillis() / 1000f) + expires.getAsLong() - 1 : 0,
							    						states.message);
							    				Response<?> res = states.listener.success(payload);
							    				Logger.log("Login successfull. Sending response...");
							    				send_response(res.errorMessage, resWriter);
							    			}
							    		} else {
							    			Logger.err("Error sending POST " + response.toString() + " POST failed");
							    			Response<?> res = states.listener.error("POST request ist fehlgeschlagen " + response.toString());
							    			send_response("Etwas ist schiefgelaufen: <br>" + res.errorMessage, resWriter);
							    		}
									}
								}, "POST " + state).start();
			            		found = true;
			            		stateAuth.remove(states);
			            		continue main;
			            	}
			            }
			            
			            if(!found) {
			            	send_response("Der Link ist ung�ltig oder abgelaufen.<br>Erstelle einen neuen mit //link!", resWriter);
			            	Logger.err("Recieved state " + state + " is either expired or was never requested");
			            }
			        }
			} catch(Exception e) {
				Logger.log("Listener for states finished with an exception " + e.getLocalizedMessage());
			} finally {
				try {
					listenSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		}, "StateListener").start();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while(true) {
						synchronized (this) {
							wait(1000);
							long currentTime = System.currentTimeMillis() / 1000l;
							for(int i = 0; i < stateAuth.size(); i++) {
								if(currentTime > stateAuth.get(i).expires) {
									Logger.log("State " + stateAuth.get(i).stateToken + " for member " + (stateAuth.get(i).requestingMember != null ? stateAuth.get(i).requestingMember.getUsername() : "unknown") + " expired");
									stateAuth.get(i).listener.error("Der Link ist abgelaufen.\nBitte gib nochmal //link ein, um einen neuen Link zu generieren!");
									stateAuth.remove(i);
									i = 0;
								}
							}
						}
					}
				} catch(Exception e) {
					e.printStackTrace();
				} finally {
		        	if(listenSocket != null)
						try {
							listenSocket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
	        }
			}
		}, "ExpireManager").start();
	}
	
	/**Sends a response string, which can be 124 characters long max.*/
	private void send_response(String message, BufferedWriter writer) {
		try {
			if(message.length() > RESPONSE_LENGTH) {
				writer.write(message.substring(0, 100));
				writer.flush();
			} else if(message.length() < RESPONSE_LENGTH) { //Make the message 124 chars long by adding whitespaces
				while(message.length() <= 124) {
					message += " ";
				}
				System.out.println("Sending response...");
				writer.write(message);
				writer.flush();
			}
		} catch (IOException e) {
			Logger.err("Unable to send response " +e.getLocalizedMessage());
		}
	}
	
	private String generateState() {
		StringBuilder state = new StringBuilder();
		for(int i = 0; i < 32; i++) state.append(ALLOWED_STATE_CHARS.charAt(random.nextInt(ALLOWED_STATE_CHARS.length())));
		return state.toString();
	}
	
	private String generateURL(int applicationID, String state) {
		//?client_id=34630&response_type=code&state
		return BASE_URL + "&client_id=" + applicationID + "&state=" + state;
	}
	
	private StateAuth addState(String state, Member member, Handshake handshake, Message message, int expires) {
		for(StateAuth states : stateAuth) {
			if(state.equals(states.stateToken)) return null;
		}
		StateAuth auth = new StateAuth(member, state, handshake, message, (System.currentTimeMillis() / 1000l) + expires);
		stateAuth.add(auth);
		return auth;
	}
	
	/**Signalizes that a discord member would like to link his account.<br>
	 * Therefore a URL is generated and returned as well as a state parameter generated related
	 * to this member to validate that this specific member logged in via Oauth2.0*/
	public StateAuth requestOAUth(Member discordMember, Message message, Handshake handshake, int expires) {
		String state = generateState();
		StateAuth auth = null;
		do {
			auth = addState(state, discordMember, handshake, message, expires);
			if(auth != null) break;
			state = generateState();
		} while(auth == null);
		
		final StateAuth success = auth;
		String URL = generateURL(applicationID, state);
		success.url = URL;
		return success;
	}
}
