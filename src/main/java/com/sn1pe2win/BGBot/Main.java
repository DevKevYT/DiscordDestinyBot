package com.sn1pe2win.BGBot;

import java.io.File;
import java.io.IOException;

import com.sn1pe2win.DataFlow.Node;
import com.sn1pe2win.api.OAuthHandler;

public class Main {
	
	public static OAuthHandler remoteAuthetification;
	//Now supporting PGCR's
	public static final String VERSION = "2.1.7";
	private static String[] args;
	
	public static void main(String [] args) throws Exception {
		if(args.length == 0) throw new IllegalArgumentException("Missing config file path");
		Main.args = args;
		
		try {
			Logger.configure(args.length >= 2 ? args[1] : null);
			Logger.logStart();
			if(args.length >= 2) Logger.log("File Created");
		} catch (IOException e) {
			System.err.println("Unable to create log file: " + e.getMessage());
		}
		
//		if(System.getProperty("bot-running") != null) {
//			Logger.err("A bot instance is already running on this machine. Exiting.");
//			System.exit(-1);
//		}
		
		System.setProperty("bot-running", "true");
		
		Logger.log("Version: " + VERSION);
		Logger.log("Initializing OAuth2.0");

		Node database = new Node(new File(args[0]));
		
		remoteAuthetification = new OAuthHandler(34630);
		remoteAuthetification.listen();
		
		BotClient bot = new BotClient(database);
		bot.build();

		//System.setProperty("bot-running", null);
	}
	
	public static String[] getArguments() {
		return args;
	}
}
