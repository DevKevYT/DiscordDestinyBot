package com.sn1pe2win.destiny2;

import java.util.ArrayList;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sn1pe2win.BGBot.BotClient;
import com.sn1pe2win.BGBot.Logger;
import com.sn1pe2win.DataFlow.Node;
import com.sn1pe2win.DataFlow.Variable;
import com.sn1pe2win.api.BungieAPI;
import com.sn1pe2win.api.Response;
import com.sn1pe2win.destiny2.Definitions.ActivityType;
import com.sn1pe2win.destiny2.Definitions.ClassType;
import com.sn1pe2win.destiny2.Definitions.MembershipType;
import com.sn1pe2win.destiny2.EntityData.DestinyCharacterEntity;
import com.sn1pe2win.destiny2.EntityData.DestinyClanEntity;
import com.sn1pe2win.destiny2.EntityData.DestinyMemberEntity;

import discord4j.core.object.entity.Member;

public class DiscordDestinyMember {
	
	/**Type: STRING*/
	public static final String ID_VARIABLE_NAME = "destiny-member-id";
	/**Type: ARRAY*/
	public static final String PLATFORM_VARIABLE_NAME = "platform";
	
	//Destiny API stuff
	private DestinyMemberEntity entity;
	private boolean profileLoaded = false;
	private boolean charactersLoaded = false;
	
	//Bot and Database stuff
	Member linkedMember; //May be null
	
	public BotClient botclient;
	public Node userNode;
	public Node database;
	public Node userList;
	public Node triumphList;
	
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
	public Response<DestinyMemberEntity> loadDestinyEntity() {
		if(linkedMember == null || database == null) return new Response<EntityData.DestinyMemberEntity>(null, 500, "BotError", "Cannot load destiny profile from database when this instance is not linked", 0);
		
		updateRegistration();
		Variable membershipId = userNode.get(ID_VARIABLE_NAME);
		if(membershipId.isUnknown() || !membershipId.isString()) return new Response<EntityData.DestinyMemberEntity>(null, 500, "BotError", "Missing variable " + ID_VARIABLE_NAME + " in user node", 0);
		Variable platform = userNode.get(PLATFORM_VARIABLE_NAME);
		if(platform.isUnknown() || !platform.isNumber()) return new Response<EntityData.DestinyMemberEntity>(null, 500, "BotError", "Missing variable " + PLATFORM_VARIABLE_NAME + " in user node", 0);
		return loadDestinyEntity(membershipId.getAsString(), MembershipType.byId((short) platform.getAsInt()));
	}
	
	/**Loads the destiny profile from the given entity. Useful in combination with {@link DiscordDestinyMember#queryPlayers(String, MembershipType)}
	 * @param entity Should at least contain the following values (not null):<br>
	 * -{@link DestinyMemberEntity#memberUID}<br>-{@link DestinyMemberEntity#platform}
	 * @throws IllegalArgumentException If any of the above named values are null*/
	public Response<DestinyMemberEntity> loadDestinyEntity(DestinyMemberEntity entity) {
		if(entity.platform == null || entity.memberUID == null) return new Response<EntityData.DestinyMemberEntity>(entity, 500, "BotError", "Entity misses platform and memberId values", 0);
		return loadDestinyEntity(entity.memberUID, entity.platform);
	}
	
	/**Loads that specific destiny member
	 * IMPORTANT: If the response contains Bungie error 7 or ParameterParseFailure, there's
	 * a high chance that the profile id is incorrect. This is a common and misleading error message*/
	public Response<DestinyMemberEntity> loadDestinyEntity(String destinyMemberID, MembershipType platform) {	
		profileLoaded = false;
		Response<JsonObject> profile = BungieAPI.sendGet("/Destiny2/" + platform.id + "/Profile/" + destinyMemberID + "?components=100");
		if(!profile.success()) return new Response<EntityData.DestinyMemberEntity>(null, profile.httpStatus, profile.errorStatus, profile.errorMessage, profile.errorCode);
		
		if(entity == null) entity = new DestinyMemberEntity();
		entity.memberUID = destinyMemberID;
		entity.platform = platform;
		JsonObject userInfo = profile.getPayload().getAsJsonObject("Response").getAsJsonObject("profile").getAsJsonObject("data").getAsJsonObject("userInfo");
		entity.isPrivate = !userInfo.getAsJsonPrimitive("isPublic").getAsBoolean();
		entity.displayName = userInfo.getAsJsonPrimitive("displayName").getAsString();
		entity.applicablePlatforms = new MembershipType[userInfo.getAsJsonArray("applicableMembershipTypes").size()];
		for(int i = 0; i < entity.applicablePlatforms.length; i++) {
			entity.applicablePlatforms[i] = MembershipType.byId(userInfo.getAsJsonArray("applicableMembershipTypes").get(i).getAsByte());
		}
		
		updateRegistration();
		profileLoaded = true;
		return new Response<EntityData.DestinyMemberEntity>(entity);
	}
	
	/**Not part of the database and can get loaded seperately<br>
	 * You can use these two functions completely independent of each other.*/
	public Response<DestinyMemberEntity> loadDestinyCharacters(String destinyMemberID, MembershipType platform) {
		charactersLoaded = false;
		Response<JsonObject> profile = BungieAPI.sendGet("/Destiny2/" + platform.id + "/Profile/" + destinyMemberID + "?components=200");
		if(!profile.success()) return new Response<EntityData.DestinyMemberEntity>(entity, profile.httpStatus, profile.errorStatus, profile.errorMessage, profile.errorCode);
		
		if(entity == null) entity = new DestinyMemberEntity();
		Object[] set = profile.getPayload().getAsJsonObject("Response").getAsJsonObject("characters").getAsJsonObject("data").entrySet().toArray();
		entity.characters = new DestinyCharacterEntity[3];
		for(int i = 0; i < set.length; i++) {
			@SuppressWarnings("unchecked")
			JsonObject characterData = ((Entry<String, JsonElement>) set[i]).getValue().getAsJsonObject();
			if(characterData == null) continue;
			if(entity.characters[i] == null) entity.characters[i] = new DestinyCharacterEntity();
			entity.characters[i].parse(characterData);
			entity.characters[i].memberUID = destinyMemberID;
			entity.characters[i].platform = platform;
		}
		charactersLoaded = true;
		return new Response<EntityData.DestinyMemberEntity>(entity);
	}
	
	/**@return If this member (More specific: the discord member Id) is registered in the database as a node*/
	public boolean isRegistered() {
		if(database == null || linkedMember == null) return false;
		return database.get(linkedMember.getId().asString()).getAsNode() != null;
	}
	
	public void setClan(DestinyClanEntity clan) {
		if(entity == null) entity = new DestinyMemberEntity();
		entity.clan = clan;
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
			
			if(entity != null) {
				userNode.addString(ID_VARIABLE_NAME, entity.memberUID)
				.addNumber(PLATFORM_VARIABLE_NAME, entity.platform.id);
			} else Logger.log("User is not linked with destiny. Leaving empty user node");
		} else {
			userNode = nodeTest.getAsNode();
			if(entity != null) { //It is impossible for userNode to be null here
				userNode.addString(ID_VARIABLE_NAME, entity.memberUID)
				.addNumber(PLATFORM_VARIABLE_NAME, entity.platform.id);
			}
		}
	}
	
	/**@param onlySuccessfull - If the activity objective was completed.
	 * @param limit - How many entries should get loaded. A limit of 1, would return the last played activity.
	 * If the limit is higher than the activity list or the limit is 0 ir less, all reported activities are returned*/
	public Response<DestinyActivity[]> loadActivityHistory(ActivityType type, boolean onlySuccessfull, int limit, ClassType... characters) {
		//if(!charactersLoaded() || !profileLoaded()) return new Response<DestinyActivity[]>(new DestinyActivity[] {}, 500, "NoCharacterLoaded", "Characters need to get loaded before loading activity stats", 0);
		
		ArrayList<DestinyActivity> history = new ArrayList<DestinyActivity>(10);
		
		for(int i = 0; i < entity.characters.length; i++) {
			if(entity.characters[i] == null) continue;
			boolean found = false;
			for(ClassType classType : characters) {
				if(entity.characters[i].classType == classType) {
					found = true;
					break;
				}
			}
			if(!found) continue;
			if(entity.characters[i].characterID == null) 
				return new Response<DestinyActivity[]>(new DestinyActivity[] {}, 500, "NoCharacterLoaded", "The specified character needs to get loaded before loading stats", 0);
			
			int loadedPerCharacter = 0;
			int page = 0;
			boolean doPages = limit > 250 || limit <= 0;
			
			pager: while(true) {
				Response<JsonObject> historyPage = BungieAPI.sendGet("/Destiny2/" + entity.platform.id + "/Account/" + entity.memberUID + "/Character/" + entity.characters[i].characterID +  "/Stats/Activities/?mode=" + type.id+ "&count=" + (doPages ? (onlySuccessfull ? 20 : 250) : limit) + "&page=" + page);
				if(!historyPage.success()) return new Response<DestinyActivity[]>(null, historyPage.httpStatus, historyPage.errorStatus, historyPage.errorMessage, historyPage.errorCode);
				if(historyPage.getPayload().getAsJsonObject("Response").keySet().isEmpty()) break pager;
				
				JsonArray activities = historyPage.getPayload().getAsJsonObject("Response").getAsJsonArray("activities");
				
				if(activities != null) { //keine aktivität für spieler gefunden
					for(int j = 0; j < activities.size(); j++) {
						if(activities.get(j).getAsJsonObject().getAsJsonObject("values").getAsJsonObject("completed").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt() == 1 || !onlySuccessfull) {
							long referenceId = activities.get(j).getAsJsonObject().getAsJsonObject("activityDetails").getAsJsonPrimitive("referenceId").getAsLong();
							Response<ActivityDefinitions> response = ActivityDefinitions.getByReferenceId(referenceId);
							if(response.success() && response.containsPayload()) {
								DestinyActivity a = new DestinyActivity(response.getPayload(), activities.get(j).getAsJsonObject().getAsJsonObject("activityDetails").getAsJsonPrimitive("instanceId").getAsLong(), 
										activities.get(j).getAsJsonObject());
								a.character = entity.characters[i];
								history.add(a);
								loadedPerCharacter++;
								if(loadedPerCharacter == limit) break pager;
							} else Logger.err(response.toString());
						}
					}
				}
				page++;
			}
		}
		//If we selected multiple characters, sort those activities by time
		if(characters.length > 1) {
			ArrayList<DestinyActivity> sorted = new ArrayList<DestinyActivity>();
			while(!history.isEmpty()) {
				long record = 0;
				int targetIndex = -1;
				for(int i = 0; i < history.size(); i++) {
					if(history.get(i).getEntity().timestamp.getTime() >= record) {
						targetIndex = i;
						record = history.get(i).getEntity().timestamp.getTime();
					}
				}
				if(targetIndex != -1) {
					sorted.add(history.get(targetIndex));
					history.remove(targetIndex);
				}
			}
			return new Response<DestinyActivity[]>(sorted.toArray(new DestinyActivity[sorted.size()]));
		} else return new Response<DestinyActivity[]>(history.toArray(new DestinyActivity[history.size()]));
	}
	
	public Response<DestinyActivity[]> loadRaidHistory(boolean onlySuccessfull) {
		if(!charactersLoaded() || !profileLoaded()) return new Response<DestinyActivity[]>(new DestinyActivity[] {}, 500, "NoCharacterLoaded", "Characters need to get loaded before loading raid stats", 0);
		
		ArrayList<DestinyActivity> raidHistory = new ArrayList<DestinyActivity>(10);
		
		for(int i = 0; i < entity.characters.length; i++) {
			if(entity.characters[i] == null) continue;
			
			int page = 0;
			
			pager: while(true) {
				Response<JsonObject> historyPage = BungieAPI.sendGet("/Destiny2/" + entity.platform.id + "/Account/" + entity.memberUID + "/Character/" + entity.characters[i].characterID +  "/Stats/Activities/?mode=4&count=250&page=" + page);
				if(!historyPage.success()) return new Response<DestinyActivity[]>(null, historyPage.httpStatus, historyPage.errorStatus, historyPage.errorMessage, historyPage.errorCode);
				if(historyPage.getPayload().getAsJsonObject("Response").keySet().isEmpty()) break pager;
				
				JsonArray activities = historyPage.getPayload().getAsJsonObject("Response").getAsJsonArray("activities");
				
				if(activities != null) { //keine aktivität für spieler gefunden
					for(int j = 0; j < activities.size(); j++) {
						if(activities.get(j).getAsJsonObject().getAsJsonObject("values").getAsJsonObject("completed").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt() == 1 || !onlySuccessfull) {
							long referenceId = activities.get(j).getAsJsonObject().getAsJsonObject("activityDetails").getAsJsonPrimitive("referenceId").getAsLong();
							Response<ActivityDefinitions> response = ActivityDefinitions.getByReferenceId(referenceId);
							if(response.success() && response.containsPayload()) {
								raidHistory.add(new DestinyActivity(response.getPayload(), activities.get(j).getAsJsonObject().getAsJsonObject("activityDetails").getAsJsonPrimitive("instanceId").getAsLong(), 
										activities.get(j).getAsJsonObject()));
							} else Logger.err(response.toString());
						}
					}
				}
				page++;
			}
		}
		return new Response<DestinyActivity[]>(raidHistory.toArray(new DestinyActivity[raidHistory.size()]));
	}
	
	public DestinyCharacterEntity getHighestCharacter() {
		if(!charactersLoaded()) return null;
		int record = 0;
		DestinyCharacterEntity chosen = entity.characters[0];
		for(int i = 0; i < entity.characters.length; i++) {
			if(entity.characters[i] != null) {
				if(entity.characters[i].lightLevel > record) {
					chosen = entity.characters[i];
					if(chosen == null) continue;
					record = chosen.lightLevel;
				}
			}
		}
		return chosen;
	}
	
	public DestinyCharacterEntity getLastPlayedCharacter() {
		long record = 0;
		DestinyCharacterEntity chosen = entity.characters[0];
		
		for(int i = 0; i < entity.characters.length; i++) {
			if(entity.characters[i] != null) {
				if(entity.characters[i].dateLastPlayed.getTime() > record) {
					chosen = entity.characters[i];
					record = chosen.dateLastPlayed.getTime();
				}
			}
		}
		return chosen;
	}
	
	public DestinyCharacterEntity[] getCharacters() {
		return entity.characters;
	}
	
	/**If the class type is there multiple times (But why?!) the first occurrence is returned.
	 * The the class type is not present, null is returned*/
	public DestinyCharacterEntity getCharacterByClassType(ClassType classType) {
		for(int i = 0; i < entity.characters.length; i++) {
			if(entity.characters[i] != null) {
				if(entity.characters[i].classType == classType) return entity.characters[i];
			}
		}
		return null;
	}
	
	public DestinyMemberEntity getEntity() {
		return entity;
	}
	
	public Member linkedMember() {
		return linkedMember;
	}
	
	/**@return A list of unloaded destiny members. If you want to use any of them as a linked account,<br>
	 * you would need to call {@link DiscordDestinyMember#link(Member, Node)}
	 * @param gamertag - The name of the player to search
	 * @param platform - Use {@link MembershipType#ALL} to search all platforms*/
	public static Response<DestinyMemberEntity[]> queryPlayers(String gamertag, MembershipType platform) {
		Response<JsonObject> response = BungieAPI.sendGet("/Destiny2/SearchDestinyPlayer/" + platform.id + "/" + gamertag + "/");
		if(!response.success()) {
			Logger.log("Failed to process player query " + gamertag + ", " + platform + ": " + response);
			return new Response<EntityData.DestinyMemberEntity[]>(new DestinyMemberEntity[] {}, response.httpStatus, response.errorStatus, response.errorMessage, response.errorCode);
		}
		
		ArrayList<DestinyMemberEntity> results = new ArrayList<DestinyMemberEntity>();
		JsonArray arr = response.getPayload().getAsJsonArray("Response");
		for(int i = 0; i < arr.size(); i++) {
			DestinyMemberEntity member = new DestinyMemberEntity();
			member.displayName = arr.get(i).getAsJsonObject().getAsJsonPrimitive("displayName").getAsString();
			member.memberUID = arr.get(i).getAsJsonObject().getAsJsonPrimitive("membershipId").getAsString();
			member.platform = MembershipType.byId(arr.get(i).getAsJsonObject().getAsJsonPrimitive("membershipType").getAsByte());
			results.add(member);
		}
		return new Response<EntityData.DestinyMemberEntity[]>(results.toArray(new DestinyMemberEntity[results.size()]));
	}
	
	public String toString() {
		return linkedMember.getId().asString();
	}
}
