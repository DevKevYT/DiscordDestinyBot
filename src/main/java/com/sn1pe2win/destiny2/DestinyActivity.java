package com.sn1pe2win.destiny2;

import java.text.ParseException;
import java.util.Date;

import com.google.gson.JsonObject;
import com.sn1pe2win.BGBot.Logger;
import com.sn1pe2win.api.BungieAPI;
import com.sn1pe2win.api.Response;
import com.sn1pe2win.destiny2.Definitions.ActivityType;
import com.sn1pe2win.destiny2.EntityData.DestinyActivityEntity;
import com.sn1pe2win.destiny2.EntityData.DestinyCharacterEntity;

/**Enth�lt alle reports von einer bestimmten, gespielten Aktivit�t*/
public class DestinyActivity {
	
	private DestinyActivityEntity entity;
	DestinyCharacterEntity character;
	
	public DestinyActivity(ActivityDefinitions activityType, long instanceId, JsonObject statDetails) {
		entity = new DestinyActivityEntity();
		entity.instanceId = instanceId;
		entity.definition = activityType.getEntity();
		
		entity.deaths = statDetails.getAsJsonObject("values").getAsJsonObject("deaths").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
		entity.kills = statDetails.getAsJsonObject("values").getAsJsonObject("kills").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
		entity.durationInSeconds = statDetails.getAsJsonObject("values").getAsJsonObject("activityDurationSeconds").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt();
		entity.timeDisplay = statDetails.getAsJsonObject("values").getAsJsonObject("activityDurationSeconds").getAsJsonObject("basic").getAsJsonPrimitive("displayValue").getAsString();
		String stringDate = statDetails.getAsJsonPrimitive("period").getAsString();
		entity.checkpoint = statDetails.getAsJsonObject("values").getAsJsonObject("completionReason").getAsJsonObject("basic").getAsJsonPrimitive("value").getAsInt() != 0;
		entity.activityHash = statDetails.getAsJsonObject("activityDetails").getAsJsonPrimitive("directorActivityHash").getAsLong();
		entity.mode = ActivityType.byId(statDetails.getAsJsonObject("activityDetails").getAsJsonPrimitive("mode").getAsShort());
		entity.isPrivate = statDetails.getAsJsonObject("activityDetails").getAsJsonPrimitive("isPrivate").getAsBoolean();
		
		try {
			entity.timestamp = DestinyDateFormat.toDate(stringDate);
		} catch (ParseException e) {
			Logger.err("Unable to parse timestamp " + stringDate);
			entity.timestamp = new Date();
		}
	}
	
	/**If you just need the character that played this activity without really loading the pgcr
	 * May return null*/
	public DestinyCharacterEntity getPlayedByCharacter() {
		return character;
	}
	
	public Response<PostGameCarnageReport> loadPostGameCarnageReport() {
		Response<JsonObject> response = BungieAPI.sendFullGet("https://stats.bungie.net/Platform/Destiny2/Stats/PostGameCarnageReport/"  + entity.instanceId + "/");
		if(!response.success()) return new Response<PostGameCarnageReport>(null, response.httpStatus, response.errorStatus, response.errorMessage, response.errorCode);
		
		return new Response<PostGameCarnageReport>(new PostGameCarnageReport(this, response.getPayload()));
	}
	
	public DestinyActivityEntity getEntity() {
		return entity;
	}
	
	@Override
	public String toString() {
		return entity.definition.toString() + "\nKills: " + entity.kills + "\nDeaths: " + entity.deaths + "\nKD: " +  (float) entity.kills/  (float) entity.deaths + "\nDuration: " + entity.timeDisplay;
	}
}
