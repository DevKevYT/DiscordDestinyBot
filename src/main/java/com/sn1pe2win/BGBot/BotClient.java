package com.sn1pe2win.BGBot;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

import com.devkev.devscript.raw.ApplicationListener;
import com.devkev.devscript.raw.Library;
import com.devkev.devscript.raw.Output;
import com.devkev.devscript.raw.Process;
import com.sn1pe2win.DataFlow.Node;
import com.sn1pe2win.DataFlow.Variable;
import com.sn1pe2win.api.BungieAPI;
import com.sn1pe2win.api.Response;
import com.sn1pe2win.destiny2.Definitions.MembershipType;
import com.sn1pe2win.destiny2.DiscordDestinyMember;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import discord4j.core.object.entity.channel.Channel.Type;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;

/**Each instance of the BGBot should represent a bot on a different server*/
public class BotClient {
	
	public ArrayList<DiscordDestinyMember> loadedMembers = new ArrayList<DiscordDestinyMember>();
	public static final long ACCOUNT_EXPIRE_TIME  = 432000L;
	public Node database;
	private Process commandHandler;
	private Process adminCommandHandler;
	
	private GatewayDiscordClient botClient;
	private Guild server;
	private static Guild rootServer; //this is the main server for the bot for various recources like emojiis etc. Initialized once, when the first constructor of "this" is called
	private static final Snowflake rootServerId = Snowflake.of("774003470889123840");
	
	public RoleManager rolemgr;
	public PluginManager pluginmgr;
	private volatile boolean ready = false;
	
	/**This node should at least contain the following variables:<br><code>
	 * token: "Discord-Bot-Token"<br>
	 * serverID: "Discord-Guild-ID-to-connect-to"</code>
	 * @throws IOException 
	 * @throws MalformedURLException 
	 * @throws IllegalArgumentException If one of the above named variables is missing*/
	public BotClient(Node database) throws MalformedURLException, IOException {
		Variable serverID = database.get("serverID");
		if(serverID.isUnknown() || !serverID.isString()) throw new IllegalArgumentException("Variable serverID missing or wrong format. Unable to connect to an unknown guild");
		Variable token = database.get("token");
		if(token.isUnknown() || !token.isString()) throw new IllegalArgumentException("Variable token missing or wrong format. Unable to operate without API token");
		Variable bungieToken = database.get("destiny-api-key");
		if(bungieToken.isUnknown() || !bungieToken.isString()) 
			Logger.warn("Variable destiny-api-key not found in the database. Bot wont be able to handle destiny API requests");
		else BungieAPI.X_API_KEY = bungieToken.getAsString();
		
		this.database = database;
		botClient = DiscordClientBuilder.create(token.getAsString()).build().login().block();
		
		if(rootServer == null) rootServer = botClient.getGuildById(rootServerId).block();
		
		server = botClient.getGuildById(Snowflake.of(serverID.getAsString())).block();
		Logger.log("Connected to Server '" + server.getName() + "' (" + serverID.getAsString() + ")");
		//server.getMemberById(Snowflake.of("613450074353303552")).block().addRole(Snowflake.of("774228313664782338")).block();
		
		botClient.updatePresence(Presence.online(Activity.listening("//help für Commands"))).subscribe();
		
		commandHandler = new Process(true);
		commandHandler.clearLibraries();
		commandHandler.maxRuntime = 3000;
		
		adminCommandHandler = new Process(true);
		adminCommandHandler.addSystemOutput();
		adminCommandHandler.setInput(System.in);
	}
	
	
	/**If the initialisation was successfull and the bot instance connected, you can now build the bot,
	 * which includes plugin and role managers and -of course- the destiny stuff.
	 * Because of lots of separate threading, this function blocks the execution at the end
	 * and finished if the bot disconnected on that specific server. If you want to run several bots on the same program, execute this function in
	 * a separate thread*/
	public void build() {
		rolemgr = new RoleManager(this);
		pluginmgr = new PluginManager();
		
		//rolemgr.addTriumphRole("Raid Meister", "raid-completions", 100, Color.YELLOW);
		rolemgr.checkRoles();
		
		botClient.on(MessageCreateEvent.class).subscribe(event -> {
			try {
				if(event.getGuildId().isPresent()) {
					if(!event.getGuildId().get().asString().equals(server.getId().asString())) return;
				}
				
				Message message = event.getMessage();
				
				//members = event.getClient().getGuildMembers(Snowflake.of(node.getData().serverID)).buffer().blockLast();
				pluginmgr.triggerOnMessageRecieved(event);
				
				if(!message.getAuthor().get().equals(botClient.getSelf().block()) && message.getContent().startsWith("//")) {
					MessageChannel channel = message.getChannel().block();
					
					if(!isReady()) {
						channel.createEmbed(spec -> {
							spec.setTitle("Nicht so schnell!");
							spec.setDescription("Ich wurde gerade erst neu gestartet.\nBitte gedulde dich einen Moment.");
							spec.setColor(Color.RED);
						}).block();
						return;
					}
					
					Process p = new Process(true);
					p.clearLibraries();
					p.includeLibrary(new DefaultCommands(this));
					for(Plugin plugin : pluginmgr.getPlugins()) {
						com.devkev.devscript.raw.Library lib = plugin.addLibrary();
						if(lib != null) p.includeLibrary(lib);
					}
					
					p.maxRuntime = 3000;
					String content = message.getContent().substring(2);
					Logger.log("Recieved script to execute from " + message.getAuthor().get().getId().asString() + ": " + content);
					
					bindProcessToChannel(p, channel);
					
					p.getVariables().clear();
					p.setVariable("channel", message.getChannel().block(), true, true);
					p.setVariable("invoker", message.getAuthor().get(), true, true);
					p.setVariable("invokerMember", message.getAuthorAsMember().block(), true, true);
					p.setVariable("admin", "false", true, true);
					p.setVariable("privateChannel", String.valueOf(channel.getType() == Type.DM), true, true);
					p.execute(content, true);
					p.setApplicationListener(new ApplicationListener() {
						public void done(int arg0) {
							pluginmgr.triggerOnCommandExecuted(event, p, arg0);
						}
					});
					pluginmgr.triggerOnCommandRecieved(event, p);
				}
			} catch(Exception e) {
				Logger.log("Fehler das MessageEvent zu verarbeiten " + e.getMessage());
			}
		});
		
		Variable plugins = database.get("plugins");
		if(plugins.isUnknown() || !plugins.isArray()) database.addArray("plugins", "");
		else {
			for(String path : plugins.getAsArray()) {
				try {
					pluginmgr.loadPlugin(new File(path), this);
				} catch (Exception e) {
					Logger.log("Failed to load plugin " + e.getMessage());
				}
			}
		}
		
		loadLinkedMembers();
		Logger.log("Done");
		
		botClient.on(MemberLeaveEvent.class).subscribe(event -> {
			if(event.getGuildId().asString().equals(server.getId().asString())) {
				if(event.getMember().isPresent()) leaveServer(event.getMember().get().getId().asString());
				pluginmgr.triggerOnMemberLeaveEvent(event);
			}
		});
		
		botClient.on(MemberJoinEvent.class).subscribe(event -> {
			if(event.getGuildId().asString().equals(server.getId().asString())) {
				pluginmgr.triggerOnMemberJoinEvent(event);
				if(database.get("on-leave").isUnknown()) database.addNode("on-leave");
				database.get("on-leave").getAsNode().remove(event.getMember().getId().asString());
				database.save();
			}
		});
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				@SuppressWarnings("resource")
				Scanner s = new Scanner(System.in);
				while(true) {
					try {
						System.out.print(">>");
						String script = s.nextLine();
						Logger.consoleLog =  false;
						Logger.log("Recieved script to execute from admin: " + script);
						adminCommandHandler.clearLibraries();
						adminCommandHandler.includeLibrary(new DefaultCommands(BotClient.this));
						for(Plugin plugin : pluginmgr.getPlugins()) {
							Library lib = plugin.addLibrary();
							if(lib != null) adminCommandHandler.includeLibrary(lib);
						}
						adminCommandHandler.getVariables().clear();
						adminCommandHandler.setVariable("invoker", botClient.getSelf().block(), true, true);
						adminCommandHandler.setVariable("invokerMember", botClient.getSelf().block().asMember(server.getId()), true, true);
						adminCommandHandler.setVariable("admin", "true", true, true);
						adminCommandHandler.execute(script, false);
						Logger.consoleLog = true;
					} catch(Exception e) {
						Logger.warn("Error in admin console: " + e.getMessage());
						e.printStackTrace();
						adminCommandHandler.kill(null, "Java Exception");
					}
				}
			}
		}, "Admin-Console").start();
		
		ready = true;
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				while(true) {
					synchronized (this) {
						try {
							update();
							System.gc();
						} catch(Exception e) {
							Logger.err("Error in thread Profile Scheduler " + e.getLocalizedMessage());
							e.printStackTrace();
						}
						try {
							wait((int) 5 * 60000);
						} catch (InterruptedException e) {
							Logger.err("FATAL Scheduler failed to pause the thread. Aborting\nPlease restart the Bot for proper functioning!");
							System.exit(-1);
						}
					}
				}
			}
		}, "Profile Updater").start();
		
		
		//Logger.log("Debug: create role from code");
		//Node role = new Node();
		//rolemgr.createRole(server, "test", role);
		botClient.onDisconnect().block();
	}
	
	private volatile boolean locked = false;
	public void loadLinkedMembers() {
		if(locked) return;
		locked = true;
		loadedMembers.clear();
		
		Node usersToLoad = database.getCreateNode("users");
		Logger.log("Loading " + usersToLoad.size() + " destiny member(s) from database (May take a while)");
		for(Variable user : usersToLoad.getVariables()) {
			if(user.isNode()) {
				Member discordMember = null;
				try {
					discordMember = server.getMemberById(Snowflake.of(user.getName())).block();
				} catch(Exception e) {}
				if(discordMember != null) {
					DiscordDestinyMember member = new DiscordDestinyMember(discordMember, this);
					Response<?> response = member.loadDestinyEntity();
					if(!response.success()) {
						Logger.log(response.toString());
						continue;
					} 
					
					Response<?> charresponse = member.loadDestinyCharacters(member.getEntity().memberUID, member.getEntity().platform);
					if(!charresponse.success()) {
						Logger.log(response.toString());
						continue;
					}
					
					loadedMembers.add(member);
				} else {
					leaveServer(user.getName());
					Logger.err("User with id " + user.getName() + " is not a member of this server! Put on on-leave list!");
				}
			} else user.delete();
		}
		locked = false;
	}
	
	public void updateIndividualMember(String discordUserId) {
		DiscordDestinyMember loaded = null;
		for(DiscordDestinyMember linked : loadedMembers) {
			if(linked.linkedMember().getId().asString().equals(discordUserId)) {
				loaded = linked;
			}
		}
		
		if(loaded == null) {
			Logger.warn("User with ID " + discordUserId + " not found or is not registered yet!");
			return;
		}
		Response<?> response = loaded.loadDestinyEntity();
		if(!response.success()) {
			Logger.log(response.toString());
		}

		Response<?> charresponse = loaded.loadDestinyCharacters(loaded.getEntity().memberUID, loaded.getEntity().platform);
		if(!charresponse.success()) {
			Logger.log(response.toString());
		}
		loaded.updateRegistration();
		pluginmgr.triggerOnDestinyMemberUpdate(loaded);
	}
	
	private volatile boolean updateLocked = false;
	public void update() {
		if(updateLocked) {
			Logger.warn("Function already running!");
			updateLocked = false;
		}
		updateLocked = true;
		long currentSeconds = System.currentTimeMillis() / 1000l;
		for(Variable user : database.getCreateNode("on-leave").getVariables()) {
			if(!updateLocked) return;
			if(currentSeconds >= user.getAsLong()) {
				Logger.log("USER EXPIRED: " + user.getName() + ". DELETING ENTRIES!");
				
				for(DiscordDestinyMember member : loadedMembers) {
					if(member.linkedMember().getId().asString().equals(user.getName())) {
						loadedMembers.remove(member);
						continue;
					}
				}
				Variable del = database.get("users").getAsNode().get(user.getName());
				if(!del.isUnknown()) del.delete();
				
				user.delete();
				Logger.log("User deleted");
			}
		}
		if(!updateLocked) return;
		
		pluginmgr.triggerOnUpdate(loadedMembers);
		for(int i = 0; i < loadedMembers.size(); i++) {
			DiscordDestinyMember loaded = loadedMembers.get(i);
			if(!updateLocked) return;
			Response<?> response = loaded.loadDestinyEntity();
			if(!response.success()) {
				Logger.log(response.toString());
				continue;
			}
			if(!updateLocked) return;
			Response<?> charresponse = loaded.loadDestinyCharacters(loaded.getEntity().memberUID, loaded.getEntity().platform);
			if(!charresponse.success()) {
				Logger.log(response.toString());
				continue;
			}
			loaded.updateRegistration();
			pluginmgr.triggerOnDestinyMemberUpdate(loaded);
		}
		if(!updateLocked) return;
		
		rolemgr.syncMemberRoles();
		
		database.save();
		updateLocked = false;
	}
	
	public void cancelUpdating() {
		updateLocked = false;
	}
	
	public void bindProcessToChannel(Process process, MessageChannel channel) {
		process.getOutput().clear();
		process.addOutput(new Output() {
			@Override
			public void warning(String arg0) {}
			@Override
			public void log(String arg0, boolean arg1) {
				Message botmessage;
				
				if(arg0.isEmpty()) {
					Object embed = process.getVariable("embed", process.getMain());
					if(embed == null) {
						Logger.warn("A embed post was requested (empty message), but no embed was specified as process Variable \"embed\"");
						return;
					}
					if(!(embed instanceof EmbedData)) {
						Logger.warn("Unable to convert " + embed.getClass().getTypeName() + " to " + EmbedCreateSpec.class.getTypeName());
						return;
					}
					
					botmessage = channel.createEmbed(spec -> {
						EmbedData data = (EmbedData) embed;
						if(data.url != null) spec.setUrl(data.url);
						if(data.author != null) spec.setAuthor(data.author, data.authorURL, data.authorIconURL);
						if(data.color != null) spec.setColor(data.color);
						if(data.description != null) spec.setDescription(data.description);
						if(data.imageURL != null) spec.setImage(data.imageURL);
						if(data.thumbnailURL != null) spec.setThumbnail(data.thumbnailURL);
						if(data.title != null) spec.setTitle(data.title);
						if(data.footer != null) spec.setFooter(data.footer, data.footerURL);
						
						for(com.sn1pe2win.BGBot.EmbedData.Field f : data.fields) spec.addField(f.name, f.text, f.inline);
					}).block();
				} else botmessage = channel.createMessage(arg0).block();
				process.setVariable("last-bot-message", botmessage, false, false);
				process.removeVariable("embed");
			}
			
			@Override
			public void error(String arg0) {
				if(arg0.isEmpty()) return;
				Logger.err(arg0);
				if(!arg0.contains("No such command")) {
					channel.createEmbed(spec -> {
						spec.setTitle("Huch :(");
						spec.setDescription("Ein Fehler ist aufgetreten");
						spec.setFooter("Mehr Details:\n" + arg0, "http://bohrmaschinengang.de/errorcode.png");
						spec.setColor(Color.RED);
					}).block();
				} else {
					channel.createEmbed(spec -> {
						spec.setTitle("Diesen Befehl kenne ich nicht");
						spec.setDescription("Für eine Liste aller Befehle, gib //help ein.");
						spec.setFooter("Weitere Details:\n" + arg0, "http://bohrmaschinengang.de/errorcode.png");
						spec.setColor(Color.RED);
					}).block();
				}
			}
		});
	}
	
	/**If already linked, values in the database will get updated etc.*/
	public Response<DiscordDestinyMember> registerMember(Member discordMember, String memberId, MembershipType platform) {
		for(DiscordDestinyMember linked : loadedMembers) {
			if(linked.linkedMember().getId().asString().equals(discordMember.getId().asString())) {
				Logger.log("This member is already linked. Removing him from the list and creating new instance");
				loadedMembers.remove(linked);
				break;
			}
		}
		updateLocked = false;
		
		DiscordDestinyMember object = new DiscordDestinyMember(discordMember, this);
		object.updateRegistration();
		object.userNode.addString(DiscordDestinyMember.ID_VARIABLE_NAME, memberId);
		object.userNode.addNumber(DiscordDestinyMember.PLATFORM_VARIABLE_NAME, platform.id);
		loadedMembers.add(object);
		database.save();
		updateIndividualMember(discordMember.getId().asString());
		return new Response<DiscordDestinyMember>(object);
	}
	
	public void leaveServer(String userId) {
		for(DiscordDestinyMember linked : loadedMembers) {
			if(linked.linkedMember() != null) {
				if(linked.linkedMember().getId().asString().equals(userId)) {
					loadedMembers.remove(linked);
					break;
				}
			}
		}
		
		if(database.get("on-leave").isUnknown()) database.addNode("on-leave");
		database.get("on-leave").getAsNode().addNumber(userId, (long) ((System.currentTimeMillis() / 1000l) + ACCOUNT_EXPIRE_TIME));
		SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss dd-MM-yyyy");
		Logger.log("User " + userId + " left the Server. Put on expire list. Data expires at " + format.format(new Date(((long) (System.currentTimeMillis() / 1000l) + ACCOUNT_EXPIRE_TIME) * 1000)) + "+-5 min");
		database.save();
	}
	
	/**Looks for a Variable called: main-post-channel in the database containing the id.
	 * If the id is invalid or does not exist, the system-channel as fallback is returned.
	 * If even the system channel does not exist, an error in the Logs is thrown and null is returned.
	 * You can set the main-channel by executing //notify in the needed channel.*/
	public TextChannel getNotificationChannel() {
		Variable mainPost = database.get("main-post-channel");
		if(!mainPost.isUnknown() && mainPost.isString()) {
			TextChannel ch = server.getChannelById(Snowflake.of(mainPost.getAsString())).cast(TextChannel.class).onErrorReturn(null).block();
			if(ch != null) return ch;
		} 
		//Fallback 1
		return server.getSystemChannel().block();
	}
	
	/**The main server. You can be sure to find all recources you need here. Like reaction emojiis and roles
	 * This is mainly needed for the bot program. Since you can easily grief this server (ban random members etc.), this function is only 
	 * available in the local program flow and the DefaultCommands library*/
	static Guild getRootServer() {
		return rootServer;
	}
	
	/**Returns a list of ID's of all mods set in the database or in {@link BotClient#grantMod()}*/
	public String[] getMods() {
		return database.getCreateArray("mods").getAsArray();
	}
	
	/**To grant a mod to someone, he needs to be a member of this server
	 * @return True, if success*/
	public boolean grantMod(String discordUserId) {
		if(!isServerMember(discordUserId)) return false;
		
		database.getCreateArray("mods").addArrayEntry(discordUserId);
		return true;
	}
	
	public boolean isMod(String discordUserId) {
		for(String mod : database.getCreateArray("mods").getAsArray()) {
			if(mod.equals(discordUserId)) return true;
		}
		return false;
	}
	
	public void removeMod(String discordUserId) {
		database.getCreateArray("mods").removeArrayEntry(discordUserId);
	}
	
	public synchronized boolean isReady() {
		return ready;
	}
	
	public Node getServerNode() {
		return database;
	}
	
	public GatewayDiscordClient getBotClient() {
		return botClient;
	}
	
	public Guild getServer() {
		return server;
	}
	
	/**Illegal properties to set:
	 * triumphs, access-token, destiny-member-id, platform*/
	public void setMemberProperty(String memberId, String propertyName, Object propertyValue) {
		if(!isServerMember(memberId)) {
			Logger.err("Member with id " + memberId + " is not a member of this server!");
			return;
		}
		if(propertyName.equals("triumphs") || propertyName.equals("access-token") || propertyName.equals("destiny-member-id") || propertyName.equals("platform")) {
			Logger.err("Illegal property name " + propertyName + " to modify");
			return;
		}
		Node memberNode = database.getCreateNode("users").getCreateNode(memberId);
		if(propertyValue instanceof String || propertyValue instanceof Float || propertyValue instanceof String[])
			memberNode.addVariable(propertyName, propertyValue);
		else if(propertyValue instanceof Long || propertyValue instanceof Integer || propertyValue instanceof Byte 
				|| propertyValue instanceof Short) memberNode.addVariable(propertyName, Float.valueOf(propertyValue.toString())); 
		else if(propertyValue instanceof Node) memberNode.addNode(propertyName, (Node) propertyValue);
		else Logger.err("Unsupported property value for name " + propertyName + ": " + propertyValue.getClass().getTypeName());
	}
	
	public Variable getMemberProperty(String memberId, String propertyName) {
		if(!isServerMember(memberId)) {
			Logger.err("Member with id " + memberId + " is not a member of this server!");
			return Variable.UNKNOWN;
		}
		
		Node memberNode = database.getCreateNode("users").getCreateNode(memberId);
		return memberNode.get(propertyName);
	}
	
	public boolean isServerMember(String id) {
		Member member = null;
		try {
			member = server.getMemberById(Snowflake.of(id)).block();
		} catch(Exception e) {
			member = null;
		}
		return member != null;
	}
}
