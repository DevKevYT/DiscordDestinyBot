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
import com.sn1pe2win.DataFlow.Variable;
import com.sn1pe2win.api.BungieAPI;
import com.sn1pe2win.api.Handshake;
import com.sn1pe2win.api.Response;
import com.sn1pe2win.api.OAuthHandler.StateAuth;
import com.sn1pe2win.destiny2.Definitions.MembershipType;
import com.sn1pe2win.destiny2.DiscordDestinyMember;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
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
				
				new Command("help", "", "") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						if(arg1.getVariable("admin", arg2).toString().equals("true")) {
							String message = "";
							for(GeneratedLibrary c : arg1.getLibraries()) {
								message += "Library: " + c.name + "\n";
								for(Command command : c.commands) {
									message += command.name + "\t\t" + command.description + "\n";
								}
							}
							arg1.log(message, true);
							return null;
						}
						
						EmbedData helpPage = new EmbedData();
						helpPage.title = "Help Page (2.0)";
						helpPage.description = "Befehle, markiert mit einem * Stern sind nur\nverfügbar, wenn du dein Destiny Konto mit\n//link verlinkt hast";
						helpPage.addField("//help", "Ruft diese Liste auf", false);
						helpPage.addField("//link", "Generiert einen Link, mit dem du dein Discord Konto verknüpfen kannst", false);
						helpPage.addField("//stats   *", "Zeigt einen kleinen Report von deinen Raid-Stats.\nOptional kann auch ein Name angegeben werden, um die raid-stats anderer Spieler zu sehen!", false);
//								"Optional kann zusätzlich auch der Nickname eingegeben werden,\n" + 
//								"um die stats anderer Discord-Mitglieder zu sehen\nZ.B.:'//stats Sn1pe2win32'", false);
//						helpPage.addField("//showpowerlevel [on/off] *", "Bei [off] wird der Nickname nicht mehr\nvon mir beeinflusst", false);
//						helpPage.addField("//blame [Nickname] \"[Grund]\"", "Du kannst jemanden hier Anzeigen, der im Einsatztrupp\n" + 
//								"Mist gebaut hat. Den Grund bitte in Gänsefüschen \" einbetten", false);
//						helpPage.addField("//mypoints", "Zeigt alle deine Punkte an, sowie die Gründe", false);
						helpPage.color = Color.RED;
						arg1.setVariable("embed", helpPage, false, false);
						arg1.log("", true);
						return null;
					}
				},
				
				new Command("update", "", "") {
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						if(!arg1.getVariable("admin", arg2).toString().equals("true")) {
							arg1.log("You don't have the permission to execute this command", true);
							return null;
						}
						client.update();
						return null;
					}
				},
				
				new Command("link", "", "") {
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
							public Response<?> success(ResponsePayload data) {
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
									client.pluginmgr.triggerOnMemberLinked(false, "POST fehlgeschlagen: " + response.errorMessage + "\nBitte versuche es erneut", "", u);
									arg1.error("Request failed:\n" + response.toString());
									return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
								}
								
								JsonArray destinyMembership = response.getPayload().getAsJsonObject("Response").getAsJsonArray("destinyMemberships");
								
								if(destinyMembership != null) {
								
									if(chosen == MembershipType.NONE && destinyMembership.getAsJsonArray().size() > 1) {
										client.pluginmgr.triggerOnMemberLinked(false, "Plattform auswählen", "", u);
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
															
															if(member.getEntity().memberUID.equals(did)) {
																client.pluginmgr.triggerOnMemberLinked(false, "Schon mit diesem Konto verlinkt", did, u);
																arg1.error("Tut mir leid, aber es sieht so aus als wärst du schon mit einem anderen Discord Konto auf diesem Server mit diesem Destiny 2 Konto auf der selben Plattform eingeloggt!");
																return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
															}
														}
													}
												}
											}
											//"test" load the member to check if everything is fine
											DiscordDestinyMember test = new DiscordDestinyMember(u.asMember(client.getServer().getId()).block(), client.database);
											Response<?> tres = test.loadDestinyCharacters(did, chosen);
											if(!tres.success()) {
												client.pluginmgr.triggerOnMemberLinked(false, "Konto auf Plattform nicht gefunden", did, u);
												String available = "";
												JsonArray applicable = destinyMembership.get(i).getAsJsonObject().getAsJsonArray("applicableMembershipTypes");
												for(int j = 0; j < applicable.size(); j++) {
													available += MembershipType.byId(applicable.get(j).getAsJsonPrimitive().getAsByte()) + (j == applicable.size() ? "" : ", ");
												}
												//Der zweite Fall dürfte hier eigentlich nie auftreten, da bei einer möglichen Plattform diese automatisch ausgewählt wird
												arg1.error("Es gab einen Fehler dein Destiny Konto auf " + chosen.readable + " zu finden.\nSicher dass du auf " + chosen.readable + " spielst?\n"
														+ (applicable.size() > 1 ? "Ich habe folgende Plattformen gefunden, auf denen du spielst: " : "Du solltest folgende Plattform auswählen:") + available + "Versuche es nochmal mit //login mit einer anderen Plattform!");
												return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
											}
											
											Response<?> res = client.registerMember(u.asMember(client.getServer().getId()).block(), did, chosen);
											if(!res.success()) {
												client.pluginmgr.triggerOnMemberLinked(false, "Fehler beim Registrieren " + res.toString(), did, u);
												arg1.error("Es ist ein Fehler bei der registrierung beim Bot aufgetreten. " + res.errorMessage + "\nBitte versuche es erneut mit //link");
												return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
											}
											
											client.pluginmgr.triggerOnMemberLinked(true, "Konten wurden erfolgreich verlinkt", did, u);
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
										
									client.pluginmgr.triggerOnMemberLinked(false, "Du spielst noch kein Destiny", "", u);
									arg1.error("Es wurde kein Destiny 2 Konto gefunden.\nHast du schonmal Destiny 2 gespielt?");
									return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
								} else {
									client.pluginmgr.triggerOnMemberLinked(false, "Du spielst noch kein Destiny", "", u);
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
						return null;
					}
				},
				
				new Command("plugin", "string ...", "load [path]/list/remove [filename]/update [filename]") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						if(!arg1.getVariable("admin", arg2).toString().equals("true")) {
							arg1.log("You don't have the permission to execute this command", true);
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
							for(Plugin p : client.pluginmgr.getPlugins()) message += p.getFile().getAbsolutePath() + "\n";
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
				
				new Command("stop", "", "Logs the client out from this server") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						if(!arg1.getVariable("admin", arg2).toString().equals("true")) {
							arg1.log("You don't have the permission to execute this command", true);
							return null;
						}
						Logger.log("Loggin out and stopping...");
						client.getBotClient().logout().block();
						System.exit(-1);
						return null;
					}
				}
		};
	}

}
