package com.sn1pe2win.destiny2;

import java.util.ArrayList;

import com.google.gson.JsonObject;
import com.sn1pe2win.api.BungieAPI;
import com.sn1pe2win.api.Response;
import com.sn1pe2win.destiny2.EntityData.DestinyActivityDefinitionEntity;

public class ActivityDefinitions {
	//Um zu verhindern, dass bereits mit der API gefetchte definitions neu geladen werden müssen
	public static final ArrayList<Response<ActivityDefinitions>> KNOWN_DEFINITIONS = new ArrayList<>();
	public static final ArrayList<String> IGNORE_DEFINITIONS = new ArrayList<String>();
	
	private final DestinyActivityDefinitionEntity entity;
	
	/**Alle Namen klassifizierter Raids. Es wird sich bewusst nicht auf die Id's bezogen, da verschieden gewertete Aktivitäten gen gleichen
	 * namen tragen und sie somit verschieden gewertet werden*/
	public static final String[] CLASSIFIED = new String[] {
			"Scourge of the Past",
			"Leviathan, Eater of Worlds: Normal",
			"Leviathan, Spire of Stars: Normal",
			"Leviathan: Normal",
			"Crown of Sorrow: Normal",
			"Leviathan: Prestige",
			"Leviathan, Spire of Stars: Prestige",
			"Leviathan, Eater of Worlds: Prestige",
			"Leviathan, Eater of Worlds"
	};
	
	public static Response<ActivityDefinitions> IGNORED_DEFINITION = new Response<>(null, 500, "UnknownDefinitionID", "Ignored definition id", 0);
	
	private ActivityDefinitions(JsonObject object, long activityHash) {
		entity = new DestinyActivityDefinitionEntity();
		entity.jsonData = object;
		entity.description = object.getAsJsonObject("displayProperties").getAsJsonPrimitive("description").getAsString();
		entity.name = object.getAsJsonObject("displayProperties").getAsJsonPrimitive("name").getAsString();
		entity.activityHash = activityHash;
	}
	
	public DestinyActivityDefinitionEntity getEntity() {
		return entity;
	}
	
	
	/**Schaut durch alle "known definitions". Wenn die Aktivität mit dieser ReferenceId noch nicht gefunden wurde,
	 * wird sie mittels der API geladen
	 * @return null, wenn nichts mit der activityHash gefunden wurde*/
	public static Response<ActivityDefinitions> getByReferenceId(long activityHash) {
		if(partOfIgnoreList(activityHash)) return IGNORED_DEFINITION;
		
		for(Response<ActivityDefinitions> definition : KNOWN_DEFINITIONS) {
			if(definition.getPayload().getEntity().activityHash == activityHash) return definition;
		}
		
		//Versuche die Definition per API zu laden
		Response<JsonObject> definition = BungieAPI.sendGet("/Destiny2/Manifest/DestinyActivityDefinition/" + activityHash);
		if(!definition.success() || !definition.containsPayload()) {
			addToIgnoreList(activityHash);
			return new Response<ActivityDefinitions>(null, definition.httpStatus, definition.errorStatus, definition.errorMessage, definition.errorCode);
		}
	
		Response<ActivityDefinitions> def = new Response<ActivityDefinitions>(new ActivityDefinitions(definition.getPayload().getAsJsonObject("Response"), activityHash));
		KNOWN_DEFINITIONS.add(def);
		return def;
	}
	
	public static boolean isClassified(String activityName) {
		for(String s : CLASSIFIED) {
			if(s.equals(activityName)) return true;
		}
		return false;
	}
	
	private static boolean partOfIgnoreList(long activityHash) {
		for(String s : IGNORE_DEFINITIONS) {
			if(s.equals(String.valueOf(activityHash))) return true;
		}
		return false;
	}
	
	private static void addToIgnoreList(long activityHash) {
		if(!partOfIgnoreList(activityHash)) IGNORE_DEFINITIONS.add(String.valueOf(activityHash));
	}
	
	public String toString() {
		return entity.name + "(" + entity.activityHash + ")";
	}
}
