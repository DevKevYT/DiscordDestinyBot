package com.sn1pe2win.BGBot;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

import org.reflections.Reflections;

import com.devkev.devscript.raw.Process;
import com.sn1pe2win.destiny2.DiscordDestinyMember;

import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;

public class PluginManager {
	
	private ArrayList<Plugin> loadedPlugins = new ArrayList<Plugin>();
	
	public ArrayList<Plugin> loadPlugin(File file, BotClient client) throws Exception {
		if(pluginLoaded(file)) return new ArrayList<>(0);
		if(!file.exists()) System.err.println("Plugin file: " + file.getAbsolutePath() + " not found or does not exist!");
		
		URI uri = file.getAbsoluteFile().toURI();
		URL url = uri.toURL();
	
		ArrayList<Plugin> loaded = new ArrayList<Plugin>();
		Reflections reflections = null;
		try {
			reflections = new Reflections(new URLClassLoader(new URL[] {url}));
		} catch(Exception e) {
			Logger.err("Failed to load plugin classes: " + e.getLocalizedMessage());
			return new ArrayList<Plugin>();
		}
		
		for(Class<? extends Plugin> objects : reflections.getSubTypesOf(Plugin.class)) {
			Plugin pluginObject = objects.getConstructor(BotClient.class).newInstance(client);
			//if(versionMatch(pluginObject.version, Main.version)) System.err.println("Versions do not match. Plugin may cause errors");
			pluginObject.file = file;
			loadedPlugins.add(pluginObject);
			loaded.add(pluginObject);
			Logger.log("Plugin class loaded: " + pluginObject.getClass().getCanonicalName());
			Logger.log("Plugin Version: " + pluginObject.VERSION);
			try {
				pluginObject.onPluginLoad();
			} catch(Exception e) {
				System.err.println("Error in plugin function onPluginLoad() " + e.getMessage());
				e.printStackTrace();
			}
		}
		Logger.log("Plugins loaded successfully");
		return loaded; 
	}
	
	/**@return The removed file, if the name was found. null otherwise*/
	public File removePlugin(String fileName) {
		for(Plugin p : loadedPlugins) {
			if(p.getFile().getName().equals(fileName)) {
				Logger.log("Plugin removed " + p.getFile().getAbsolutePath());
				
				try {
					p.onPluginRemove();
				} catch(Exception e) {
					Logger.err("Error in plugin function onPluginRemove() " + e.getMessage());
					e.printStackTrace();
				}
				
				loadedPlugins.remove(p);
				return p.file;
			}
		}
		return null;
	}
	
	public void triggerOnDestinyMemberUpdate(DiscordDestinyMember member) {
		for(Plugin p : loadedPlugins) {
			try {
				p.onDestinyMemberUpdate(member);
			}catch(Exception e) {
				Logger.err("Catched Plugin error! Continuing at next update cycle " + p.file.getAbsolutePath());
				e.printStackTrace();
			}
		}
	}
	
	public void triggerOnUpdate(ArrayList<DiscordDestinyMember> loadedMembers) {
		for(Plugin p : loadedPlugins) {
			try {
				p.onUpdate(loadedMembers);
			}catch(Exception e) {
				Logger.err("Catched Plugin error! Continuing at next update cycle" + p.file.getAbsolutePath());
				e.printStackTrace();
			}
		}
	}
	
	public void triggerOnCommandRecieved(MessageCreateEvent event, Process process) {
		for(Plugin p : loadedPlugins) {
			try {
				p.onCommandRecieved(event, process);
			}catch(Exception e) {
				Logger.err("Catched Plugin error!" + p.file.getAbsolutePath());
				e.printStackTrace();
			}
		}
	}
	
	public void triggerOnCommandExecuted(MessageCreateEvent event, Process process, int exitCode) {
		for(Plugin p : loadedPlugins) {
			try {
				p.onCommandExecuted(event, process, exitCode);
			}catch(Exception e) {
				Logger.err("Catched Plugin error!" + p.file.getAbsolutePath());
				e.printStackTrace();
			}
		}
	}
	
	public void triggerOnMessageRecieved(MessageCreateEvent event) {
		for(Plugin p : loadedPlugins) {
			try {
				p.onMessageRecieved(event);
			}catch(Exception e) {
				Logger.err("Catched Plugin error!" + p.file.getAbsolutePath());
				e.printStackTrace();
			}
		}
	}
	
	public void triggerOnMemberJoinEvent(MemberJoinEvent event) {
		for(Plugin p : loadedPlugins) {
			try {
				p.onMemberJoin(event);
			}catch(Exception e) {
				Logger.err("Catched Plugin error!" + p.file.getAbsolutePath());
				e.printStackTrace();
			}
		}
	}
	
	public void triggerOnMemberLeaveEvent(MemberLeaveEvent event) {
		for(Plugin p : loadedPlugins) {
			try {
				p.onMemberLeave(event);
			}catch(Exception e) {
				Logger.err("Catched Plugin error!" + p.file.getAbsolutePath());
				e.printStackTrace();
			}
		}
	}
	
	public void triggerOnMemberLinked(boolean success, String message, String membershipID, User member) {
		for(Plugin p : loadedPlugins) {
			try {
				p.onMemberLinked(success, message, membershipID, member);
			}catch(Exception e) {
				Logger.err("Catched Plugin error!" + p.file.getAbsolutePath());
				e.printStackTrace();
			}
		}
	}
	
	public ArrayList<Plugin> getPlugins() {
		return loadedPlugins;
	}
	
	private boolean pluginLoaded(File file) {
		for(Plugin p : loadedPlugins) {
			if(p.file.getAbsolutePath().equals(file.getAbsolutePath())) return true;
		}
		return false;
	}
}
