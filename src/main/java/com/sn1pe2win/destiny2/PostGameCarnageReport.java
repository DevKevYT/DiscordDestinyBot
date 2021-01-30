package com.sn1pe2win.destiny2;

import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sn1pe2win.destiny2.Definitions.ClassType;
import com.sn1pe2win.destiny2.Definitions.MembershipType;
import com.sn1pe2win.destiny2.EntityData.DestinyCharacterEntity;
import com.sn1pe2win.destiny2.EntityData.PGCREntity;
import com.sn1pe2win.destiny2.EntityData.PGCRPlayerEntity;

public class PostGameCarnageReport {

	private final PGCREntity entity;
	
	public PostGameCarnageReport(DestinyActivity activity, JsonObject data) {
		entity = new PGCREntity();
		entity.activity = activity.getEntity();
		JsonArray entries = data.getAsJsonObject("Response").getAsJsonArray("entries");
		ArrayList<PGCRPlayerEntity> playerEntries = new ArrayList<EntityData.PGCRPlayerEntity>();
		
		for(int i = 0; i < entries.size(); i++) {
			JsonObject playerjson = entries.get(i).getAsJsonObject().getAsJsonObject("player");
			PGCRPlayerEntity player = new PGCRPlayerEntity();
			
			if(playerjson != null) {
				DestinyCharacterEntity character = new DestinyCharacterEntity();
				JsonObject userInfo = playerjson.getAsJsonObject("destinyUserInfo");
				
				character.characterID = entries.get(i).getAsJsonObject().getAsJsonPrimitive("characterId").getAsString();
				character.classType = ClassType.byHash(playerjson.getAsJsonPrimitive("classHash").getAsLong());
				character.memberUID = userInfo.getAsJsonPrimitive("membershipId").getAsString();
				character.lightLevel = playerjson.getAsJsonPrimitive("lightLevel").getAsInt();
				character.platform = MembershipType.byId(userInfo.getAsJsonPrimitive("membershipType").getAsShort());
				character.emblemURL = "https://www.bungie.net" + userInfo.getAsJsonPrimitive("iconPath");
				player.character = character;
			}
			
			JsonObject values = entries.get(i).getAsJsonObject().getAsJsonObject("values");
			player.completed = values.getAsJsonObject("completionReason").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt() == 0;
			player.deaths = values.getAsJsonObject("deaths").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
			player.kills = values.getAsJsonObject("kills").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
			player.kda = values.getAsJsonObject("killsDeathsRatio").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsFloat();
			player.fireteamId = values.getAsJsonObject("fireteamId").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsLong();
			player.playerCount = values.getAsJsonObject("playerCount").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
			player.teamScore = values.getAsJsonObject("teamScore").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
			
			//player.superKills = values.getAsJsonObject("extended").getAsJsonObject("values").getAsJsonObject("weaponKillsSuper").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
			//player.precisionKills = values.getAsJsonObject("extended").getAsJsonObject("values").getAsJsonObject("precisionKills").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
			//player.meleeKills = values.getAsJsonObject("extended").getAsJsonObject("values").getAsJsonObject("weaponKillsMelee").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
			//player.abillityKills = values.getAsJsonObject("extended").getAsJsonObject("values").getAsJsonObject("weaponKillsAbility").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
			
			playerEntries.add(player);
		}
		entity.players = playerEntries.toArray(new PGCRPlayerEntity[playerEntries.size()]);
	}

	public PGCREntity getEntity() {
		return entity;
	}
}
