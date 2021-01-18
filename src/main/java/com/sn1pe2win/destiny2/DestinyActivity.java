package com.sn1pe2win.destiny2;

import java.text.ParseException;
import java.util.Date;

import com.google.gson.JsonObject;
import com.sn1pe2win.BGBot.Logger;
import com.sn1pe2win.destiny2.EntityData.DestinyActivityEntity;

/**Enthält alle reports von einer bestimmten, gespielten Aktivität*/
public class DestinyActivity {
	
	private DestinyActivityEntity entity;
	
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
		
		try {
			entity.timestamp = DestinyDateFormat.toDate(stringDate);
		} catch (ParseException e) {
			Logger.err("Unable to parse timestamp " + stringDate);
			entity.timestamp = new Date();
		}
	}
	
	public DestinyActivityEntity getEntity() {
		return entity;
	}
	
	@Override
	public String toString() {
		return entity.definition.toString() + "\nKills: " + entity.kills + "\nDeaths: " + entity.deaths + "\nKD: " +  (float) entity.kills/  (float) entity.deaths + "\nDuration: " + entity.timeDisplay;
	}
}
