package com.sn1pe2win.destiny2;

import java.util.ArrayList;
import com.sn1pe2win.BGBot.BotClient;
import com.sn1pe2win.BGBot.Logger;
import com.sn1pe2win.DataFlow.Node;
import com.sn1pe2win.DataFlow.Variable;
import com.sn1pe2win.DestinyEntityObjects.PlayerActivity;
import com.sn1pe2win.DestinyEntityObjects.Profile.CharacterComponent;
import com.sn1pe2win.DestinyEntityObjects.Profile.CharacterComponent.Character;
import com.sn1pe2win.DestinyEntityObjects.Profile.DestinyProfileComponent;
import com.sn1pe2win.core.Response;
import com.sn1pe2win.definitions.MembershipType;
import com.sn1pe2win.endpoints.GetProfile;

import discord4j.core.object.entity.Member;

public class DiscordDestinyMember {
	
	/**Type: STRING*/
	public static final String ID_VARIABLE_NAME = "destiny-member-id";
	/**Type: ARRAY*/
	public static final String PLATFORM_VARIABLE_NAME = "platform";
	
	//Destiny API stuff
	GetProfile profile;
	DestinyProfileComponent profileComponent;
	CharacterComponent characterComponent;
	
	private boolean profileLoaded = false;
	private boolean charactersLoaded = false;
	
	//Bot and Database stuff
	Member linkedMember; //May be null
	
	public BotClient botclient;
	public Node userNode;
	public Node database;
	public Node userList;
	public Node triumphList;
	
	/**will get removed in the next update (3.0.1). To prevent too much reliable, they are already marked as deprecate!*/
	@Deprecated
	public boolean isOnline = false;
	
	/**will get removed in the next update (3.0.1). To prevent too much reliable, they are already marked as deprecate!*/
	@Deprecated
	public String clanId = "";
	
	/**This only touches the discord stuff. The full destiny profile is loaded at the load() function*/
	public DiscordDestinyMember(Member member, BotClient botInstance) {
		this.linkedMember = member;
		this.botclient = botInstance;
		this.database = botInstance.database;
		if(database != null) userList = database.getCreateNode("users");
	}
	
	/**@deprecated This will also work, but you won't be able to process any discord stuff*/
	public DiscordDestinyMember() {
		this.linkedMember = null;
		this.database = null;
		botclient = null;
	}
	
	public DiscordDestinyMember(BotClient botInstance) {
		this.linkedMember = null;
		this.database = botInstance.database;
		this.botclient = botInstance;
	}
	
	/**Loads the profile from the database. The variables would be located here:<br>
	 * MembershipId: users.DISCORD-MEMBER-ID.destiny-membership-id<br>
	 * Platform:     users.DISCORD-MEMBER-ID.platform<br>
	 * If any of the named Variables are not found, it is emitted through the response*/
	public Response<DestinyProfileComponent> loadDestinyEntity() {
		if(linkedMember == null || database == null) return new Response<DestinyProfileComponent>(null, 500, "BotError", "Cannot load destiny profile from database when this instance is not linked", 0);
		
		updateRegistration();
		Variable membershipId = userNode.get(ID_VARIABLE_NAME);
		if(membershipId.isUnknown() || !membershipId.isString()) return new Response<DestinyProfileComponent>(null, 500, "BotError", "Missing variable " + ID_VARIABLE_NAME + " in user node", 0);
		Variable platform = userNode.get(PLATFORM_VARIABLE_NAME);
		if(platform.isUnknown() || !platform.isNumber()) return new Response<DestinyProfileComponent>(null, 500, "BotError", "Missing variable " + PLATFORM_VARIABLE_NAME + " in user node", 0);
		return loadDestinyProfile(Long.valueOf(membershipId.getAsString()), MembershipType.of((short) platform.getAsInt()));
	}
	
	/**Loads that specific destiny member
	 * IMPORTANT: If the response contains Bungie error 7 or ParameterParseFailure, there's
	 * a high chance that the profile id is incorrect. This is a common and misleading error message*/
	public Response<DestinyProfileComponent> loadDestinyProfile(long destinyMemberID, MembershipType platform) {	
		profileLoaded = false;
		
		if(profile == null) profile = new GetProfile(platform, destinyMemberID);
		
		Response<DestinyProfileComponent> p = profile.getDestinyProfileComponent();
		if(!p.success()) {
			return new Response<DestinyProfileComponent>(null, p.httpStatus, p.errorStatus, 
					p.errorMessage, p.errorCode);
		} else profileComponent = p.getResponseData();
		profileLoaded = p.success();
		updateRegistration();
		return p;
	}
	
	/**Not part of the database and can get loaded seperately<br>
	 * You can use these two functions completely independent of each other.*/
	public Response<CharacterComponent> loadDestinyCharacters() {
		charactersLoaded = false;
		if(profile == null) return new Response<CharacterComponent>(null, 500, "NotLoaded", "The profile needs to get loaded first", 0);
		
		Response<CharacterComponent> c = profile.getCharacterComponent();
		charactersLoaded = c.success();
		if(charactersLoaded) characterComponent = c.getResponseData();
		return c;
	}
	
	/**@return If this member (More specific: the discord member Id) is registered in the database as a node*/
	public boolean isRegistered() {
		if(database == null || linkedMember == null) return false;
		return database.get(linkedMember.getId().asString()).getAsNode() != null;
	}
	
	public boolean profileLoaded() {
		return profileLoaded;
	}
	
	public boolean charactersLoaded() {
		return charactersLoaded;
	}
	
	/**If the user is already registered, this function just updated possible changes for the node and the database overall.
	 * If not, the generated node structure would look like this:<br>
	 * If this user's destiny profile is not linked with {@link DiscordDestinyMember#loadDestinyEntity(String)},
	 * the generated node would be empty, but as mentioned can get updated anytime by calling this function again.*/
	public void updateRegistration() {
		if(database == null || linkedMember == null) return;
		
		Variable nodeTest = database.get("users").getAsNode().get(linkedMember.getId().asString());
		if(nodeTest.isUnknown() || !nodeTest.isNode()) {
			Logger.log("Registering user " + linkedMember.getId().asString());
			userNode = userList.addNode(linkedMember.getId().asString());
			
			if(profileComponent != null) {
				userNode.addString(ID_VARIABLE_NAME, String.valueOf(profileComponent.getUserInfo().getMembershipId()))
				.addNumber(PLATFORM_VARIABLE_NAME, profileComponent.getUserInfo().getMembershipType().id);
			} else Logger.log("User is not linked with destiny. Leaving empty user node");
		} else {
			userNode = nodeTest.getAsNode();
			if(profileComponent != null) { //It is impossible for userNode to be null here
				userNode.addString(ID_VARIABLE_NAME, String.valueOf(profileComponent.getUserInfo().getMembershipId()))
				.addNumber(PLATFORM_VARIABLE_NAME, profileComponent.getUserInfo().getMembershipType().id);
			}
		}
	}
	
	public GetProfile getDestinyProfileInfoLoader() {
		return profile;
	}
	
	public DestinyProfileComponent getProfile() {
		return profileComponent;
	}
	
	public CharacterComponent getCharacters() {
		return characterComponent;
	}
	
	public Response<PlayerActivity[]> loadRaidHistory(boolean onlySuccessfull) {
		Response<PlayerActivity[]> activity = profileComponent.getActivityHistory(4, 0);
		if(activity.success()) {
			if(!onlySuccessfull) return activity;
			else {
				ArrayList<PlayerActivity> build = new ArrayList<PlayerActivity>();
				for(PlayerActivity a : activity.getResponseData()) {
					if(a.getActivityStats().completed()) build.add(a);
				}
				return new Response<PlayerActivity[]>(build.toArray(new PlayerActivity[build.size()]));
			}
		} else return activity;
	}
	
	public Character getHighestCharacter() {
		if(!charactersLoaded()) return null;
		
		int record = 0;
		Character[] c =  characterComponent.getCharacters();
		Character chosen = c[0];
		for(int i = 0; i < c.length; i++) {
			if(c[i] != null) {
				if(c[i].getLightLevel() > record) {
					chosen = c[i];
					if(chosen == null) continue;
					record = chosen.getLightLevel();
				}
			}
		}
		return chosen;
	}
	
	public Character getLastPlayedCharacter() {
		long record = 0;
		Character[] c =  characterComponent.getCharacters();
		
		Character chosen = c[0];
		
		for(int i = 0; i < c.length; i++) {
			try {
				if(c[i] != null) {
					if(c[i].getDateLastPlayed().getTime() > record) {
						chosen = c[i];
						record = chosen.getDateLastPlayed().getTime();
					}
				}
			}catch(Exception e) {
			}
		}
		return chosen;
	}
	
	public Member linkedMember() {
		return linkedMember;
	}
	
	public String toString() {
		return linkedMember.getId().asString();
	}
}
