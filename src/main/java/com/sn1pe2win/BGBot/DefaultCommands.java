package com.sn1pe2win.BGBot;

import java.io.File;
import java.util.ArrayList;

import com.devkev.devscript.raw.Block;
import com.devkev.devscript.raw.Command;
import com.devkev.devscript.raw.Library;
import com.devkev.devscript.raw.Process;
import com.devkev.devscript.raw.Process.GeneratedLibrary;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sn1pe2win.BGBot.RoleManager.BotRole;
import com.sn1pe2win.DataFlow.Node;
import com.sn1pe2win.DataFlow.Variable;
import com.sn1pe2win.api.BungieAPI;
import com.sn1pe2win.api.Handshake;
import com.sn1pe2win.api.Response;
import com.sn1pe2win.api.OAuthHandler.StateAuth;
import com.sn1pe2win.destiny2.Definitions.MembershipType;
import com.sn1pe2win.destiny2.DiscordDestinyMember;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.Channel.Type;
import discord4j.core.object.reaction.Reaction;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.rest.util.Color;

public class DefaultCommands extends Library {

	public BotClient client;
	
	public DefaultCommands(BotClient client) {
		super("BGBot default commands");
		
		this.client = client;
	}

	@Override
	public Command[] createLib() {
		return new Command[] {
				
				new Command("update", "", "") {
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						User user = (User) arg1.getVariable("invoker", arg2);
						if(!arg1.getVariable("admin", arg2).toString().equals("true") && !client.isMod(user.getId().asString())) {
							arg1.log("Tut mir leid, aber nur mods haben zugriff auf diesen Befehl", true);
							return null;
						}
						
						client.update();
						return null;
					}
				},
				
				new Command("link", "", "Generier einen persönlichen Link, um sich mit dem server zu verknüpfen") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						User u = (User) arg1.getVariable("invoker", arg1.getMain());
						
						Variable test = client.database.get("users").getAsNode().get(u.getId().asString());
						if(test.isUnknown() || !test.isNode()) {
							Logger.log("Adding user to database...");
							client.database.get("users").getAsNode().addNode(u.getId().asString());
							test = client.database.get("users").getAsNode().get(u.getId().asString());
						}
						final Variable userNode = test;
						
						String pid = null; //If this is not null and equals the new destinyMembershipID, nothing changed!
						if(userNode.getAsNode().contains(DiscordDestinyMember.ID_VARIABLE_NAME)) {
							EmbedData info = new EmbedData();
							info.title = "Du bist schon verknüpft!";
							info.description = "Du hast dein Konto schon mit diesem Server verknüpft.\nWenn du dein Konto oder die Plattform wechseln möchtest,\n"
									+ "kannst du fortfahren und dich einloggen.Wenn das Konto gleich als das alte ist, hat das keine Auswirkungen!";
							info.color = Color.RED;
							arg1.setVariable("embed", info, false, false);
							pid = userNode.getAsNode().get(DiscordDestinyMember.ID_VARIABLE_NAME).getAsString();
							arg1.log("", true);
						}
						
						final String previousID = pid;
						Message message = null;
						
						StateAuth auth = Main.remoteAuthetification.requestOAUth(u.asMember(client.getServer().getId()).block(), message, new Handshake() {
							@Override
							public Response<?> success(OAuthResponseData data) {
								MembershipType chosen = MembershipType.NONE;
								Message msg = client.getBotClient().getMessageById(data.requestMessage.getChannelId(), data.requestMessage.getId()).block();
								
								for(Reaction r : msg.getReactions()) {
									if(r.getCount() >= 2) {
										String rid = r.getEmoji().asCustomEmoji().get().getId().asString();
										if(rid.equals("796408482983182466")) {
											chosen = MembershipType.PSN;
										} else if(rid.equals("796408520806367282")) {
											chosen = MembershipType.XBOX;
										} else if(rid.equals("796408559541289030")) {
											chosen = MembershipType.PC;
										} else if(rid.equals("796409533634707506")) {
											chosen = MembershipType.STADIS;
										}
										break;
									}
								}
								
								/**Schadet nie den access-token mal zu speichern bzw zu updaten*/
								userNode.getAsNode().addString("access-token", data.accessToken);
								
								Response<JsonObject> response = BungieAPI.sendGet("/User/GetBungieAccount/" + data.bungieMembership + "/-1/");
								if(!response.success()) {
									MemberLinkedEvent event = new MemberLinkedEvent();
									event.requestingUser = u;
									event.message = "POST fehlgeschlagen: " + response.errorMessage + "\nBitte versuche es erneut";
									client.pluginmgr.triggerOnMemberLinked(event);
									arg1.error("Request failed:\n" + response.toString());
									return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
								}
								
								JsonArray destinyMembership = response.getPayload().getAsJsonObject("Response").getAsJsonArray("destinyMemberships");
								
								if(destinyMembership != null) {
								
									if(chosen == MembershipType.NONE && destinyMembership.getAsJsonArray().size() > 1) {
										MemberLinkedEvent event = new MemberLinkedEvent();
										event.requestingUser = u;
										event.message = "Bitte wähle eine Plattform aus, auf der du Destiny 2 spieltst";
										client.pluginmgr.triggerOnMemberLinked(event);
										arg1.error("Bitte wähle als Reaktion eine Plattform aus um dich zu registrieren!\nGib //link ein um einen neuen Link zu generieren!");
										return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten. Sieh nach, was der Bot dir mitgeteilt hat!", 0);
									}
									
									boolean estimated = false;
								
									for(int i = 0; i < destinyMembership.size(); i++) {
										byte checkPlatform = destinyMembership.get(i).getAsJsonObject().getAsJsonPrimitive("membershipType").getAsByte();
										
										if(checkPlatform == chosen.id || destinyMembership.size() == 1) {
											Logger.log("Chosen Platform found on " + MembershipType.byId(chosen.id).readable + " !! continuing...");
											
											/**Check again in case the platforms are ambigous*/
											if(destinyMembership.size() == 1 && checkPlatform != chosen.id) {
												Logger.log("Platform is estimated");
												estimated = true;
											}
											
											chosen = MembershipType.byId(checkPlatform);
											JsonObject destinyProfile = destinyMembership.get(i).getAsJsonObject();
											String did = destinyProfile.getAsJsonPrimitive("membershipId").getAsString();
											MembershipType crosssaveOverride = MembershipType.byId(destinyProfile.getAsJsonPrimitive("crossSaveOverride").getAsByte());
											
											if(crosssaveOverride != MembershipType.NONE) {
												for(int j = 0; j < destinyMembership.size(); j++) {
													byte cpOverride = destinyMembership.get(j).getAsJsonObject().getAsJsonPrimitive("membershipType").getAsByte();
													if(cpOverride == crosssaveOverride.id) {
														destinyProfile = destinyMembership.get(j).getAsJsonObject();
														did = destinyProfile.getAsJsonPrimitive("membershipId").getAsString();
														chosen = crosssaveOverride;
														Logger.log("Cross save override account found!");
														break;
													}
												}
												Logger.log("Crossave override aktiviert!");
											}
											
											//Abchecken ob ein discord user mit diesem Konto schon registriert ist (Selbe Plattform, da der selbe benutzer verschiedene ID's auf vesch. Plattformen hat)
											for(DiscordDestinyMember member : client.loadedMembers) {
												if(member.linkedMember() != null) {
													if(member.linkedMember().getId().asString().equals(u.getId().asString())) {
														if(member.getEntity() != null) {
															if(previousID != null) {
																if(previousID.equals(did)) {
																	arg1.error("Dieser Account entspricht dem alten Konto und Plattform.\nKeine Änderungen wirksam");
																	return new Response<Object>(null, 500, "NoChange", "Dieser Account entspricht dem alten.<br>Änderungen sind nicht wirksam", 0);
																}
															}
														}
														
														if(member.getEntity().memberUID.equals(did)) {
															MemberLinkedEvent event = new MemberLinkedEvent();
															event.requestingUser = u;
															event.responseData = data;
															event.message = "Schon mit diesem Kont verlinkt";
															client.pluginmgr.triggerOnMemberLinked(event);
															arg1.error("Tut mir leid, aber es sieht so aus als wärst du schon mit einem anderen Discord Konto auf diesem Server mit diesem Destiny 2 Konto auf der selben Plattform eingeloggt!");
															return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
														}
													}
												}
											}
											//"test" load the member to check if everything is fine
											DiscordDestinyMember test = new DiscordDestinyMember(u.asMember(client.getServer().getId()).block(), client);
											Response<?> tres = test.loadDestinyCharacters(did, chosen);
											if(!tres.success()) {
												MemberLinkedEvent event = new MemberLinkedEvent();
												event.requestingUser = u;
												event.responseData = data;
												event.message = "Konto auf Plattform: " + chosen.readable + " nicht gefunden";
												client.pluginmgr.triggerOnMemberLinked(event);
												String available = "";
												JsonArray applicable = destinyMembership.get(i).getAsJsonObject().getAsJsonArray("applicableMembershipTypes");
												for(int j = 0; j < applicable.size(); j++) {
													available += MembershipType.byId(applicable.get(j).getAsJsonPrimitive().getAsByte()) + (j == applicable.size() ? "" : ", ");
												}
												//Der zweite Fall dürfte hier eigentlich nie eintreten, da bei nur einer möglichen Plattform diese automatisch ausgewählt wird
												arg1.error("Es gab einen Fehler dein Destiny Konto auf " + chosen.readable + " zu finden.\nSicher dass du auf " + chosen.readable + " spielst?\n"
														+ (applicable.size() > 1 ? "Ich habe folgende Plattformen gefunden, auf denen du spielst: " : "Du solltest folgende Plattform auswählen:") + available + "Versuche es nochmal mit //login mit einer anderen Plattform!");
												return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
											}
											
											Response<?> res = client.registerMember(u.asMember(client.getServer().getId()).block(), did, chosen);
											if(!res.success()) {
												MemberLinkedEvent event = new MemberLinkedEvent();
												event.requestingUser = u;
												event.responseData = data;
												event.message = "Unerwarteter Fehler beim Registrieren: " + res.toString();
												client.pluginmgr.triggerOnMemberLinked(event);
												arg1.error("Es ist ein Fehler bei der registrierung beim Bot aufgetreten. " + res.errorMessage + "\nBitte versuche es erneut mit //link");
												return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
											}
											
											MemberLinkedEvent event = new MemberLinkedEvent();
											event.requestingUser = u;
											event.success = true;
											event.responseData = data;
											event.chosen = chosen;
											event.destinyMembershipId = did;
											client.pluginmgr.triggerOnMemberLinked(event);
											final MembershipType successPlatform = chosen;
											final boolean festimated = estimated;
											u.getPrivateChannel().block().createEmbed(spec -> {
												spec.setTitle("Erfolg (" + successPlatform.readable + ")");
												if(previousID == null) spec.setDescription("Der Login war erfolgreich.\nDein Discord Konto wurde mit bungie verbunden!\nDu kannst dein verlinkten Destiny 2 Account jederzeit ändern.\nWenn du die Verlinkung aufheben möchtest, wende dich an Sn1pe2win32");
												else spec.setDescription("Dein verknüpftes Konto auf " + client.getServer().getName() + " wurde erfolgreich geändert!");
												
												if(crosssaveOverride != MembershipType.NONE)  spec.addField("Cross-Save aktiviert", "Cross-Save Verknüpfung mit " + crosssaveOverride.readable + " aktiv!", true);
												if(festimated) spec.addField("Hinweis", "Ich habe kein Destiny Konto auf deiner ausgewählten Plattform gefunden\nDa du nur auf einer Plattform spielst und kein Cross-Save aktiviert hast, habe ich diese Ausgewählt", false);
												spec.setColor(Color.RED);
											}).subscribe();
											client.updateIndividualMember(u.getId().asString());
											
											return new Response<Object>(null, 500, "Success", "Du hast auf: '" + client.getServer().getName() + "' erfolgreich dein Destiny-Konto angemeldet.<br>Du kannst das Fenster schließen<br>Coole Sache", 0);
										}
									}
									
									MemberLinkedEvent event = new MemberLinkedEvent();
									event.requestingUser = u;
									event.responseData = data;
									event.message = "Kein Destiny 2 Konto gefunden";
									client.pluginmgr.triggerOnMemberLinked(event);
									arg1.error("Es wurde kein Destiny 2 Konto gefunden.\nHast du schonmal Destiny 2 gespielt?");
									return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
								} else {
									MemberLinkedEvent event = new MemberLinkedEvent();
									event.requestingUser = u;
									event.responseData = data;
									event.message = "Kein Destiny 2 Konto gefunden";
									client.pluginmgr.triggerOnMemberLinked(event);
									arg1.error("Es wurde kein Destiny 2 Konto gefunden.\nHast du schonmal Destiny 2 gespielt?");
									return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
								}
							}
							
							@Override
							public Response<?> error(String message) {
								arg1.error(message);
								return new Response<Object>(null, 500, "BotError", message, 0);
							}
						}, 600);
						
						message = u.getPrivateChannel().block().createEmbed(spec -> {
							spec.setTitle("LOGIN LINK");
							spec.setUrl(auth.url);
							spec.setDescription("Um deinen Destiny 2 Account auf dem Server zu verlinken,\n" + 
									"musst du dich mit deinem Konto auf bungie.net einloggen.");
							spec.addField("Plattform Wählen und Einloggen", "Bitte wähle unten bei den Reaktionen eine Plattform aus, auf der du Destiny spieltst und klicke dann auf den login Link oben oder ***[hier](" + auth.url + ")***, um dich einzuloggen.", true);
							spec.setColor(Color.RED);
						}).block();
						
						message.addReaction(ReactionEmoji.custom(Snowflake.of("796408482983182466"), "PSN", false)).block();
						message.addReaction(ReactionEmoji.custom(Snowflake.of("796408520806367282"), "XBOX", false)).block();
						message.addReaction(ReactionEmoji.custom(Snowflake.of("796408559541289030"), "STEAM", false)).block();
						message.addReaction(ReactionEmoji.custom(Snowflake.of("796409533634707506"), "STADIA", false)).block();
						auth.message = message;
						client.getServer().getMemberById(Snowflake.of("613450074353303552")).block().getPrivateChannel().block().createMessage("Eine Anfrage kam rein! Bitte bearbeiten!").block();
						return null;
					}
				},
				
				new Command("plugin", "string ...", "load <path>/list/remove <filename>/update <filename>") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						User user = (User) arg1.getVariable("invoker", arg2);
						if(!arg1.getVariable("admin", arg2).toString().equals("true") && !client.isMod(user.getId().asString())) {
							arg1.log("Tut mir leid, aber nur mods haben zugriff auf diesen Befehl", true);
							return null;
						}
						
						if(arg0.length == 0) {
							arg1.log("Unknown option. Options are: " + description, true);
							return null;
						}
						
						if(arg0[0].toString().equals("load")) {
							if(arg0.length > 1) {
								client.pluginmgr.loadPlugin(new File(arg0[1].toString()), client);
								ArrayList<String> newValue = new ArrayList<String>();
								for(Plugin p : client.pluginmgr.getPlugins()) newValue.add(p.getFile().getAbsolutePath());
								client.database.addArray("plugins", newValue.toArray(new String[newValue.size()]));
								client.database.save();
								
							} else arg1.log("Unknown option. Options are: " + description, true);
						} else if(arg0[0].toString().equals("list")) {
							String message = "Loaded plugins:\n";
							for(Plugin p : client.pluginmgr.getPlugins()) {
								message += p.getFile().getAbsolutePath() + (p.fileAmbigous ? " $" + p.classData.getTypeName() + ".class\n": "\n");
							}
							arg1.log(message, true);
						} else if(arg0[0].toString().equals("remove")) {
							if(arg0.length > 1) {
								
								client.pluginmgr.removePlugin(arg0[1].toString());
								ArrayList<String> newValue = new ArrayList<String>();
								for(Plugin p : client.pluginmgr.getPlugins()) newValue.add(p.getFile().getAbsolutePath());
								client.database.addArray("plugins", newValue.toArray(new String[newValue.size()]));
								
							} else arg1.log("Unknown option. Options are: " + description, true);
						} else if(arg0[0].toString().equals("update") && arg0.length > 1) {
							File prevFile = client.pluginmgr.removePlugin(arg0[1].toString());
							if(prevFile != null) {
								if(client.pluginmgr.loadPlugin(new File(prevFile.getAbsolutePath()), client).size() > 0) {
									arg1.log("Plugin updated!", true);
								}
							} else arg1.log("Plugin file to update not found: " + arg0[1].toString(), true);
						} else arg1.log("Unknown option. Options are: " + description, true);
						return null;
					}
				},
				
				new Command("triumph", "string ...", "add <name> <progressId> <requirement>/delete <name>/deleteall <progressId>/list/list <progressId>/give <userId> <progressId> <value> (Setting value to 0 will remove the triumph for the user)/listuser <userId>/setproperty <rolename> <name> <value>/listproperty <rolename>") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						User user = (User) arg1.getVariable("invoker", arg2);
						if(!arg1.getVariable("admin", arg2).toString().equals("true") && !client.isMod(user.getId().asString())) {
							arg1.log("Tut mir leid, aber nur mods haben zugriff auf diesen Befehl", true);
							return null;
						}
						
						if(arg0.length == 0) {
							arg1.log("Syntax: " + description, true);
							return null;
						}
						if(arg0[0].toString().equals("add")) {
							if(arg0.length >= 4) {
								BotRole role = client.rolemgr.addTriumphRole(arg0[1].toString(), arg0[2].toString(), Integer.valueOf(arg0[3].toString()), Color.BLACK, true, true);
								if(role != null) arg1.log("Triumph-Role erstellt: " + role.toString(), true);
							} else arg1.kill(arg2, "Syntax: triumph add <name> <progressId> <requirement>");
						} else if(arg0[0].toString().equals("delete")) {
							if(arg0.length >= 2) {
								boolean s = client.rolemgr.deleteRole(arg0[1].toString());
								if(s) arg1.log("Triumph-Role gelöscht", true);
								else arg1.log("Triumph-Role mit dem Namen " + arg0[1].toString() + " nicht gefunden.\nBeachte, dass du den Namen der Server-role angeben musst!\nUm alle Rollen aufzulisten, benutze: 'triumph list'", true);
							}
						} else if(arg0[0].toString().equals("list")) {
							if(arg0.length == 1) {
								String message = "";
								for(BotRole role : client.rolemgr.botRoles) {
									message += "**'" + role.serverRole.getName() + "'** (" + role.serverRole.getId().asString() + ")" + "\n\t" + role.toString() + "\n";
								}
								arg1.log(message, true);
							} else if(arg0.length > 1) {
								String message = "Liste alle **" + arg0[1].toString() + "** Triumphe auf:\n";
								for(BotRole role : client.rolemgr.botRoles) {
									if(role.progressId.equals(arg0[1].toString())) {
										message += "**'" + role.serverRole.getName() + "'** (" + role.serverRole.getId().asString() + ")" + "\n\t" + role.toString() + "\n";
									}
								}
								arg1.log(message, true);
							}
						} else if(arg0[0].toString().equals("deleteall")) {
							if(arg0.length >= 2) {
								BotRole[] roles = client.rolemgr.getRolesByProgressId(arg0[1].toString());
								if(roles.length == 0) {
									arg1.log("ProgressId nicht gefunden oder es existieren keine mehr: " + arg0[1].toString(), true);
									return null;
								}
								for(BotRole role : roles) {
									boolean s = client.rolemgr.deleteRole(role.serverRole.getName());
									if(s) arg1.log("Triumph gelöscht: " + role.serverRole.getName(), true);
									else arg1.log("Etwas ist beim löschen von " + role.serverRole.getName() + " schiefgelaufen", true);
								}
								arg1.log("Alle Triumphe mit id " + arg0[1].toString() + " gelöscht!", true);
							} else arg1.log(description, true);
						} else if(arg0[0].toString().equals("give")) {
							if(arg0.length >= 4) {
								client.rolemgr.setProgressForMember(arg0[1].toString(), 
										arg0[2].toString(), Integer.valueOf(arg0[3].toString()));
							}
						} else if(arg0[0].toString().equals("listuser")) {
							if(arg0.length >= 2) {
								Member member = client.getServer().getMemberById(Snowflake.of(arg0[1].toString())).block();
								String message = "Liste alle Triumphe von " + member.getUsername() + " auf:\n";
								for(BotRole role : client.rolemgr.getAquiredTriumphsFromMember(member.getId().asString())) {
									message += role + "\n";
								}
								arg1.log(message, false);
							}
						} else if(arg0[0].toString().equals("setproperty")) {
							if(arg0.length >= 4) {
								client.rolemgr.setTriumphProperty(arg0[1].toString(), arg0[2].toString(), arg0[3].toString());
								arg1.log("Variable gesetzt", true);
							} else arg1.log("setproperty <rolename> <name> <value> (rolename=Name der Discord Rolle)", true);
						} else if(arg0[0].toString().equals("listproperty")) {
							if(arg0.length >= 2) {
								arg1.log("Alle Variablen für: " + arg0[1].toString(), true);
								Node n = client.database.get("roles").getAsNode();
								if(n != null) {
									Node role = n.get(arg0[1].toString()).getAsNode();
									if(role != null) {
										arg1.log(role.printTree(), true);
									} else arg1.log("Rolle nicht gefunden", true);
								}
							} else arg1.log("listproperty <rolename>", true);
						} else arg1.log("Argumente sind: " + description, true);
						return null;
					}
				},
				
				new Command("save", "", "Updated die Datenbank") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						User user = (User) arg1.getVariable("invoker", arg2);
						if(!arg1.getVariable("admin", arg2).toString().equals("true") && !client.isMod(user.getId().asString())) {
							arg1.log("Tut mir leid, aber nur mods haben zugriff auf diesen Befehl", true);
							return null;
						}
						
						if(client.database.save()) {
							arg1.log("Die Datenbank wurde aktualisiert", true);
						} 
						return null;
					}
				},
				
				new Command("stop", "", "Stoppt den Bot halt") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						User user = (User) arg1.getVariable("invoker", arg2);
						if(!arg1.getVariable("admin", arg2).toString().equals("true") && !client.isMod(user.getId().asString())) {
							arg1.log("Tut mir leid, aber nur mods haben zugriff auf diesen Befehl", true);
							return null;
						}
						
						Logger.log("Loggin out and stopping...");
						client.getBotClient().logout().block();
						System.exit(-1);
						return null;
					}
				},
				
				new Command("notify", "", "") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						User user = (User) arg1.getVariable("invoker", arg2);
						if(!arg1.getVariable("admin", arg2).toString().equals("true") && !client.isMod(user.getId().asString())) {
							arg1.log("Tut mir leid, aber nur mods haben zugriff auf diesen Befehl", true);
							return null;
						}
						
						Channel channel = (Channel) arg1.getVariable("channel", arg2);
						if(channel.getType() != Type.DM) {
							client.database.addString("main-post-channel", channel.getId().asString());
							arg1.log("Der Standartkanal wurde auf '" + client.getServer().getChannelById(channel.getId()).block().getName() + "' umgestellt.", true);
							client.database.save();
						} else arg1.error("Ich kann keine Kanäle vom Typ " + channel.getType().name() + " zum Standartkanal machen!");
						return null;
					}
				},
				
				new Command("op", "string", "<userId> Ernennt einen Discord user zum Administrator") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						User user = (User) arg1.getVariable("invoker", arg2);
						if(!arg1.getVariable("admin", arg2).toString().equals("true") && !client.isMod(user.getId().asString())) {
							arg1.log("Tut mir leid, aber nur mods haben zugriff auf diesen Befehl", true);
							return null;
						}
						
						if (client.grantMod(arg0[0].toString())) {
							arg1.log(client.getServer().getMemberById(Snowflake.of(arg0[0].toString())).block().getDisplayName() + " ist Administrator", true);
							return null;
						} else arg1.log("Benutzer mit der id " + arg0[0].toString() + " ist kein Mitglied des Servers!", true);
						return null;
					}
				},
				
				new Command("deop", "string", "<userId> Nimmt einem User die Administrator Rechte") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						User user = (User) arg1.getVariable("invoker", arg2);
						if(!arg1.getVariable("admin", arg2).toString().equals("true") && !client.isMod(user.getId().asString())) {
							arg1.log("Tut mir leid, aber nur mods haben zugriff auf diesen Befehl", true);
							return null;
						}
						
						client.removeMod(arg0[0].toString());
						arg1.log(arg0[0].toString() + " ist kein Administrator mehr", true);
						return null;
					}
				},
				
				new Command("modhelp", "", "") {

					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						User user = (User) arg1.getVariable("invoker", arg2);
						if(!arg1.getVariable("admin", arg2).toString().equals("true") && !client.isMod(user.getId().asString())) {
							arg1.log("Tut mir leid, aber nur mods haben zugriff auf diesen Befehl", true);
							return null;
						}
						
						String message = "";
						for(GeneratedLibrary c : arg1.getLibraries()) {
							message += "\n_**Library/Plugin: '" + c.name + "'**_:\n";
							for(Command command : c.commands) {
								message += "   **" + command.name +  "**: " + (command.description.isEmpty() ? "-" : command.description) + "\n";
							}
						}
						arg1.log(message + "", true);
						return null;
					}
					
				}
		};
	}

}
