package com.sn1pe2win.BGBot;

import java.io.File;
import java.util.ArrayList;

import com.devkev.devscript.raw.Library;
import com.devkev.devscript.raw.Process;
import com.sn1pe2win.api.Handshake;
import com.sn1pe2win.destiny2.DiscordDestinyMember;

import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;

/**Um ein Plugin zu implementieren, sollte eine beliebige Klasse der jar datei von dieser Klasse erben*/
public abstract class Plugin {
	
	public final String[] version = new String[] {"1", "5", "2"};
	public BotClient client;
	File file;
	Class<?> classData;
	
	/**True, if multiple plugin classes are in the same file*/
	boolean fileAmbigous = false;
	
	/**This can be used to distinguish between different plugin versions and to check, if the most recent plugin version is running!*/
	public short VERSION = 0;
	
	public Plugin(BotClient client) {
		this.client = client;
	}
	
	public File getFile() {
		return file;
	}
	
	public String toString() {
		return file.getName() + " $" + classData.getName() + ".class" + " (Ver: " + VERSION + ")";
	}
	
	public abstract Library addLibrary();
	
	public abstract void onPluginLoad();
	
	public abstract void onPluginRemove();
	
	public abstract void onDestinyMemberUpdate(DiscordDestinyMember member);
	
	public abstract void onUpdate(ArrayList<DiscordDestinyMember> members);
	
	public abstract void onCommandRecieved(MessageCreateEvent event, Process process);
	
	public abstract void onCommandExecuted(MessageCreateEvent event, Process process, int exitCode);
	
	public abstract void onMessageRecieved(MessageCreateEvent event);
	
	public abstract void onMemberJoin(MemberJoinEvent event);
	
	public abstract void onMemberLeave(MemberLeaveEvent event);
	
	public abstract void onTriumphAquired(MemberTriumphEvent event);
	/**Pretty unique function. Executed, when the {@link Handshake#success(String, discord4j.core.object.entity.Message)} or {@link Handshake#error(String)}
	 * is called in the //link command in {@link DefaultCommands}*/
	public abstract void onMemberLinked(MemberLinkedEvent event);
}
