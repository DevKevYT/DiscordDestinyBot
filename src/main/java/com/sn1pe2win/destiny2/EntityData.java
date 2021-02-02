package com.sn1pe2win.destiny2;

import java.text.ParseException;
import java.util.Date;

import com.google.gson.JsonObject;
import com.sn1pe2win.BGBot.Logger;
import com.sn1pe2win.destiny2.Definitions.ActivityType;
import com.sn1pe2win.destiny2.Definitions.ClassType;
import com.sn1pe2win.destiny2.Definitions.MembershipType;
import com.sn1pe2win.destiny2.Definitions.RaceType;

/**These classes contain the most basic and important data
 * for various destiny stats and informations.
 * You can collect additional information by the API that would construct them*/
public interface EntityData {
	
	public class DestinyMemberEntity {
		
		public JsonObject jsonData;
		public boolean isPrivate = false;
		public String memberUID;
		public MembershipType platform;
		public MembershipType[] applicablePlatforms;
		public String displayName;
		public DestinyCharacterEntity[] characters;
		public int raidClears = 0;
		
		public DestinyClanEntity clan;
		public boolean isOnline = false;
	}
	
	public class DestinyCharacterEntity {
		
		public JsonObject jsonData;
		public MembershipType platform;
		public String memberUID;
		public String characterID;
		public int lightLevel;
		public RaceType raceType;
		public ClassType classType;
		public String emblemURL;
		public String emblemBackgroundURL;
		public Date dateLastPlayed;
		
		public void parse(JsonObject rawData) {
			characterID = rawData.getAsJsonPrimitive("characterId").getAsString();
			lightLevel = rawData.getAsJsonPrimitive("light").getAsInt();
			raceType = RaceType.byId(rawData.getAsJsonPrimitive("raceType").getAsShort());
			classType = ClassType.byId(rawData.getAsJsonPrimitive("classType").getAsByte());
			emblemURL = "https://www.bungie.net" + rawData.getAsJsonPrimitive("emblemPath").getAsString();
			emblemBackgroundURL = "https://www.bungie.net" + rawData.getAsJsonPrimitive("emblemBackgroundPath").getAsString();
			
			String date = rawData.getAsJsonPrimitive("dateLastPlayed").getAsString();
			try {
				dateLastPlayed = DestinyDateFormat.toDate(date);
			} catch (ParseException e) {
				Logger.err("Unable to convert " + date + " to " + DestinyDateFormat.class.getName());
				dateLastPlayed = new Date();
			}
		}
	}
	
	public class DestinyClanEntity {
		
		public String clanID;
		public DestinyMemberEntity[] members;
	}
	
	public class DestinyActivityDefinitionEntity {
		
		public JsonObject jsonData;
		public long activityHash;
		public String description = "";
		public String name = "";
		
		//Not the best solution, but since the ID's vary for a strance reason, I found the best way to check for activities via name
		public static final String[] CLASSIFIED_RAID = new String[] {
				"Scourge of the Past",
				"Leviathan, Eater of Worlds: Normal",
				"Leviathan, Spire of Stars: Normal",
				"Leviathan: Normal",
				"Crown of Sorrow: Normal",
				"Leviathan: Prestige",
				"Leviathan, Spire of Stars: Prestige",
				"Leviathan, Eater of Worlds: Prestige"
		};
	}
	
	public class DestinyActivityEntity {
		public JsonObject jsonData;
		
		//Only set if it was pvp
		public boolean victory = false;
		
		public boolean completed;
		public boolean objectiveCompleted;
		public long instanceId;
		public long activityHash;
		public DestinyActivityDefinitionEntity definition;
		public int deaths, kills, durationInSeconds;
		public String timeDisplay;
		public boolean checkpoint;
		public Date timestamp;
		public ActivityType mode;
		public ActivityType[] modes;
		public MembershipType platform;
		public boolean isPrivate;
	}
	
	public class PGCREntity {
		public JsonObject jsonData;
		
		public DestinyActivityEntity activity;
		public PGCRPlayerEntity[] players;
	}
	
	public class PGCRPlayerEntity {
		//Not related to any loaded characters
		public DestinyCharacterEntity character;
		public long fireteamId;
		public boolean completed; //KD = kills / deaths
		public int kills;
		public int deaths;
		public float kda;
		public int playerCount;
		public int teamScore;
		public int timePlayedSeconds;
		public int precisionKills = -1;
		public int superKills = -1;
		public int meleeKills = -1;
		public int abillityKills = -1;
	}
	
	public class StatValue {
		
		final float value;
		final String displayValue;
		
		public StatValue(float value, String displayValue) {
			this.value = value;
			this.displayValue = displayValue;
		}
	}
}
