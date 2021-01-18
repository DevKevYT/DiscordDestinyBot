package com.sn1pe2win.api;

import discord4j.core.object.entity.Message;

public interface Handshake {
	
	public class ResponsePayload {
		
		public final String accessToken;
		public final String refreshToken;
		public final String bungieMembership;
		public final long expires;
		public final Message requestMessage;
		
		ResponsePayload(String accessToken, String refreshToken, String bungieMembership, long expires, Message requestMessage) {
			this.accessToken = accessToken;
			this.refreshToken = refreshToken;
			this.bungieMembership = bungieMembership;
			this.requestMessage = requestMessage;
			this.expires = expires;
		}
	}
	
	/**This function is fired, when the destiny member could be found*/
	public Response<?> success(ResponsePayload data);
	
	public Response<?> error(String message);
}
