package com.sn1pe2win.BGBot;

import com.sn1pe2win.api.Handshake.OAuthResponseData;
import com.sn1pe2win.destiny2.Definitions.MembershipType;

import discord4j.core.object.entity.User;

public class MemberLinkedEvent {
	
	boolean success = false;
	String message = "";
	String destinyMembershipId = "";
	MembershipType chosen = MembershipType.NONE;
	User requestingUser;
	OAuthResponseData responseData;
	
	MemberLinkedEvent() {
	}
	
	public boolean success() {
		return success;
	}

	public String getMessage() {
		return message;
	}

	public User getRequestingUser() {
		return requestingUser;
	}

	public OAuthResponseData getResponseData() {
		return responseData;
	}
	
	public MembershipType getRequestetPlatform() {
		return chosen;
	}
	
	public String getDestinyMembershipId() {
		return destinyMembershipId;
	}
}
