package com.sn1pe2win.destiny2;

/**Static Definitions von Destiny 2. Man könnte sich
 * auch das Manifest herunterladen. Aber ich komme noch nicht so mit Datenbanken klar*/
public interface Definitions {
	
	public enum Languages {
		ENG, GER;
		
		public static void languagePair(String eng, String ger) {
			
		}
	}
	
	/**http://destinydevs.github.io/BungieNetPlatform/docs/schemas/Destiny-DestinyClass*/
	public enum ClassType {

		TITAN((byte) 0, 3655393761L, "Titan"), 
		WARLOCK((byte) 2, 2271682572L, "Warlock"), 
		HUNTER((byte) 1, 671679327, "Jäger"), 
		UNKNOWN((byte) 3, 0, "unknown");
		
		public final byte id;
		public final String readable;
		public final long hash;
		
		ClassType(byte id, long hash, String readable) {
			this.id = id;
			this.readable = readable;
			this.hash = hash;
		}
		
		public static ClassType byId(byte id) {
			switch(id) {
				case 0: return TITAN;
				case 2: return WARLOCK;
				case 1: return HUNTER;
				case 3: return UNKNOWN;
				default: return UNKNOWN;
			}
		}
		
		public static ClassType byHash(long hash) {
			for(ClassType type : ClassType.values()) {
				if(type.hash == hash) return type;
			}
			return UNKNOWN;
		}
	}
	
	/**http://destinydevs.github.io/BungieNetPlatform/docs/schemas/Destiny-DestinyRace*/
	public enum RaceType {
		
		HUMAN((byte) 0, "Mensch"), 
		AWOKEN((byte) 1, "Erwachter"), 
		EXO((byte) 2, "Exo"), 
		UNKNOWN((byte) 3, "unknown");
		
		public final byte id;
		public final String readable;
		
		private RaceType(byte id, String readable) {
			this.id = id;
			this.readable = readable;
		}
		
		public static RaceType byId(short id) {
			switch(id) {
				case 0: return HUMAN;
				case 2: return EXO;
				case 1: return AWOKEN;
				case 3: return UNKNOWN;
				default: return UNKNOWN;
			}
		}
	}
	
	/**http://destinydevs.github.io/BungieNetPlatform/docs/schemas/BungieMembershipType*/
	public enum MembershipType {
		
		ALL((short) -1, "All", ""), 
		PSN((short) 2, "PSN", "ps"), 
		XBOX((short) 1, "XBox", "xb"), 
		PC((short) 3, "PC", "pc"),
		STADIS((short) 5, "Stadia", "ps"),
		BUNGIE_NEXT((short) 254, "unknown", "unknown"),
		NONE((short) 0, "none", "none");
		
		public final short id;
		public final String readable;
		public final String officalName;
		
		MembershipType(short id, String readable, String officalName) {
			this.id = id;
			this.readable = readable;
			this.officalName = officalName;
		}
		
		public static MembershipType byId(short id) {
			for(MembershipType membership : MembershipType.values()) {
				if(membership.id == id) return membership;
			}
			return NONE;
		}
	}
	
	//Destiny.Definitions.DestinyActivityModeDefinition
	/**https://destinydevs.github.io/BungieNetPlatform/docs/schemas/Destiny-HistoricalStats-Definitions-DestinyActivityModeType*/
	public enum ActivityType {
		
		NONE((short) 0, "none"),
		STORY((short) 2, "Story"),
		STRIKE((short) 3, "Strike"),
		RAID((short) 4, "Raid"),
		PATROL((short) 6, "Patroullie"),
		CONTROL((short) 10, "Kontrolle"),
		CLASH((short) 12, "Team Deathmatch"),
		NIGHTFALL((short) 16, "Dämmerung"),
		ALL_STRIKES((short) 18, "Strikes"),
		HEROIC_NIGHTFALL((short) 17, "Heroische Dämmerung"),  //?
		IRON_BANNER((short) 19, "Eisenbanner"),
		SUPREMACY((short) 31, "Supremacy"),
		SURVIVAL((short) 37, "Überleben"),
		COUNTDOWN((short) 38, "Countdown"),
		TRIAL((short) 39, "Prüfungen der Neun"),
		SOCIAL((short) 40, "Turm"),
		TRIALS_COUNTDOWN((short) 41, "Prüfungen von Oriris: Countdown"),
		TRIALS_SURVIVAL((short) 42, "Prüfungen von Osiris: Survival"),
		IRON_BANNER_CONTROL((short) 43, "Eisenbanner Kontrolle"),
		IRON_BANNER_CLASH((short) 44, "Eisenbanner Team Deathmatch"),
		IRON_BANNER_SUPREMACY((short) 45, "Einenbanner Supremacy"),
		ALL_PVE((short) 7, "PvE"),
		ALLSTRIKES((short) 18, "Strikes"),
		ALL_PVP((short) 5, "PvP"),
		TRIALS_OF_OSIRIS((short) 84, "Prüfungen von Osiris");
		
		public final short id;
		public final String readable;
		
		ActivityType(short id, String readable) {
			this.id = id;
			this.readable = readable;
		}
		
		public static ActivityType byId(short id) {
			for(ActivityType a : ActivityType.values()) {
				if(a.id == id) return a;
			}
			return NONE;
		}
	}
}
