package com.sn1pe2win.BGBot;

import java.util.ArrayList;
import java.util.List;

import com.devkev.devscript.raw.ApplicationBuilder;
import com.sn1pe2win.DataFlow.Node;
import com.sn1pe2win.DataFlow.Variable;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.rest.util.Color;

/**Manages all the required roles on the server to make the bot working.
 * If the certain, given roles in the database don't have a role id (variable name:role-id), or the given id's are invalid,
 * this manager will create new roles to make everything work
 * This could lead to role duplicates, but only if you delete or change these roles*/
public class RoleManager {
	
	public class BotRole {
		
		public final Role serverRole;
		public final int requirement;
		public final String progressId;
		public boolean forceRole;
		public final Node roleNode;
		
		private BotRole(Role serverRole, int requirement, String progressId, boolean forceRole, Node roleNode) {
			this.serverRole = serverRole;
			this.requirement = requirement;
			this.progressId = progressId;
			this.forceRole = forceRole;
			this.roleNode = roleNode;
		}
		
		public String toString() {
			return roleNode.getName() + " [" + progressId + " -> " + requirement + "]";
		}
	}
	
	public interface TriumphListener {
		public void onTriumphAquired(Member member, BotRole triumph, int progress);
	}
	
	public static final String ROLE_NODE_NAME = "roles";
	public static final String ROLE_ID_NAME = "role-id";
	/**A better name would be: progress... Too late now*/
	public static final String ROLE_REQUIREMENT_NAME = "requirement";
	/**For example: progressId: raid-rank would contain all raid triumphs (and the discord raid-rank)*/
	public static final String PROGRESS_ID_NAME = "progressId";
	public static final String ROLE_COLOR_NAME = "color";
	public static final String DEFAULT_PROGRESS_ID = "raid-completions";
	public static final String TRIUMPH_VARIABLE_NAME = "triumphs";
	public static final String ROLE_FORCEROLE_NAME = "force-role";
	
	/**All, unfiltered triumph roles*/
	public ArrayList<BotRole> botRoles = new ArrayList<>();
	private BotClient client;
	private Node database;
	private Node roleNode;
	
	public RoleManager(BotClient client) {
		this.client = client;
		this.database = client.database;
	}
	
	public void checkRoles() {
		Variable roleVar = database.get(ROLE_NODE_NAME);
		if(roleVar.isUnknown() || !roleVar.isNode()) {
			Logger.warn("No roles to create because no variable with name " + ROLE_NODE_NAME + " was found or has the wrong format. Pretty boring");
			database.addNode(ROLE_NODE_NAME);
		}
		
		ArrayList<BotRole> finalServerRoles = new ArrayList<BotRole>();
		List<Role> serverRoles = client.getServer().getRoles().collectList().block();
		roleNode = database.get(ROLE_NODE_NAME).getAsNode();
		
		//Now try to search for serverRoles, that are either not in the roles list o
		for(Variable databaseRole : roleNode.getVariables()) {
			
			Logger.log("Checking role '" + databaseRole.getName() + "'");
			final Role finalServerRole;
			//If, for whatever reason the role info variable is not a node, overwrite this variable and make a node out of it
			if(!databaseRole.isNode()) {
				Logger.warn("Role " + databaseRole.getName() + " has to be a node. Creating default");
				roleNode.addNode(databaseRole.getName());
			}
			
			String id = databaseRole.getAsNode().get(ROLE_ID_NAME).getAsString();
			Role found = null;
			if(id != null) {
				for(Role serverRole : serverRoles) {
					if(serverRole.getId().asString().equals(id)) {
						found = serverRole;
						break;
					}
				}
			}
			//If the ID could not be found, try to check for the role name and change the id in the database
			if(found == null) {
				for(Role serverRole : serverRoles) {
					if(serverRole.getName().equals(databaseRole.getName())) {
						Logger.log("Updated role ID in database: " + id + " -> " + serverRole.getId().asString());
						found = serverRole;
						break;
					}
				}
			}
			
			if(id == null && found == null) finalServerRole = createRole(client.getServer(), databaseRole.getAsNode());
			else if(found != null) {
				finalServerRole = found;
				//Keep the role data in the database updated by just synchronizing the serverRole values
				databaseRole.getAsNode().addString(ROLE_ID_NAME, found.getId().asString());
				databaseRole.getAsNode().addArray(ROLE_COLOR_NAME, found.getColor().getRed()+"", found.getColor().getGreen()+"", found.getColor().getBlue()+"");
			} else finalServerRole = createRole(client.getServer(), databaseRole.getAsNode());
			
			Variable requirementVar = databaseRole.getAsNode().get(ROLE_REQUIREMENT_NAME);
			if(requirementVar.isUnknown() || !requirementVar.isNumber()) {
				Logger.warn("Role requirement not set. Setting " + ROLE_REQUIREMENT_NAME + " to 0");
				requirementVar = databaseRole.getAsNode().addNumber(ROLE_REQUIREMENT_NAME, 0).get(ROLE_REQUIREMENT_NAME);
			}
			int requirement = requirementVar.getAsInt();
			if(requirement < 0) {
				databaseRole.getAsNode().addNumber(ROLE_REQUIREMENT_NAME, 0);
				requirement = 0;
				Logger.err("Variable " + ROLE_COLOR_NAME + " not found. Creating default (0)");
			}
			Variable progressIdVar = databaseRole.getAsNode().get(PROGRESS_ID_NAME);
			if(progressIdVar.isUnknown() || !progressIdVar.isString()) {
				Logger.warn("Role misses a progress-id. Setting id as: " + DEFAULT_PROGRESS_ID);
				progressIdVar = databaseRole.getAsNode().addString(PROGRESS_ID_NAME, DEFAULT_PROGRESS_ID).get(PROGRESS_ID_NAME);
			}
			if(progressIdVar.getAsString().isEmpty()) {
				Logger.err("Progress-id contains an illegal name: " + progressIdVar.getAsString() + ". Changing to default: " + DEFAULT_PROGRESS_ID);
				progressIdVar = databaseRole.getAsNode().addString(PROGRESS_ID_NAME, DEFAULT_PROGRESS_ID).get(PROGRESS_ID_NAME);
			}
			boolean force = true;
			Variable forceRole = databaseRole.getAsNode().get(ROLE_FORCEROLE_NAME);
			if(!forceRole.isUnknown() && forceRole.isString()) {
				force = !forceRole.getAsString().equals("false");
			} else databaseRole.getAsNode().addString(ROLE_FORCEROLE_NAME, "true");
			
			finalServerRoles.add(new BotRole(finalServerRole, requirement, progressIdVar.getAsString(), force, databaseRole.getAsNode()));
		}
		
		botRoles = finalServerRoles;
		
		syncMemberRoles();
		database.save();
	}
	
	public void syncMemberRoles() {
		for(Member serverMembers : client.getServer().getMembers().collectList().block()) {
			List<Role> has = serverMembers.getRoles().collectList().block();
			BotRole[] legal = getAquiredTriumphsFromMember(serverMembers.getId().asString());
			
			for(Role role : has) {
				boolean remove = false;
				if(getById(role.getId().asString()) != null) {
					remove = true;
					for(BotRole canHave : legal) {
						if(canHave.serverRole.getId().asString().equals(role.getId().asString())) {
							remove = false;
							break;
						}
					}
				}
				if(remove) {
					Logger.log("Removing illegal role for member " + serverMembers.getDisplayName());
					serverMembers.removeRole(role.getId()).block();
				}
			}
			
			for(BotRole n : legal) {
				setProgressForMember(serverMembers.getId().asString(), n.progressId, getProgressByIdForMember(serverMembers.getId().asString(), n.progressId));
			}
		}
	}
	
	public BotRole[] getRolesByProgressId(String progressId) {
		ArrayList<BotRole> roles = new ArrayList<RoleManager.BotRole>(botRoles.size());
		for(BotRole role : botRoles) {
			if(role.progressId.equals(progressId)) roles.add(role);
		}
		return roles.toArray(new BotRole[roles.size()]);
	}
	
	/**@return null, if the progressId does not exist.*/
	public BotRole getLowestRequirement(String progessId) {
		BotRole recordRole = null;
		for(BotRole role : getRolesByProgressId(progessId)) {
			if(recordRole == null) recordRole = role;
			else {
				if(role.requirement < recordRole.requirement) recordRole = role;
			}
		}
		return recordRole;
	}
	
	public BotRole getHighestRequirement(String progressId) {
		BotRole recordRole = null;
		for(BotRole role : getRolesByProgressId(progressId)) {
			if(recordRole == null) recordRole = role;
			else {
				if(role.requirement > recordRole.requirement) recordRole = role;
			}
		}
		return recordRole;
	}
	
	/**@return null, if the botRole list is empty or the progress-id does not exist*/
	public BotRole getRoleByRequirement(String progressId, int progressValue) {
		BotRole r = getLowestRequirement(progressId);
		if(r == null || r.requirement > progressValue) return null;
		
		for(BotRole role : botRoles) {
			if(progressValue >= role.requirement && role.progressId.equals(progressId)) r = role;
		}
		return r;
	}
	
	/**@return May return a negative value, if the progressValue exceeds the highest progress role.*/
	public int getUntilNextLevel(String progressId, int progressValue) {
		BotRole current = getRoleByRequirement(progressId, progressValue);
		if(current == null) current = getLowestRequirement(progressId);
		if(current == null) return 0;
		//Find the next highest
		BotRole record = getHighestRequirement(progressId);
		for(BotRole role : botRoles) {
			if(role.requirement >= current.requirement && role.requirement < record.requirement && !role.equals(current)) {
				record = role;
			}
		}
		return record.requirement - progressValue;
	}
	
	/**@deprecated
	 * @return The lowest requirement of the default progress-id: raid-completions*/
	public BotRole getLowestRequirement() {
		return getLowestRequirement(DEFAULT_PROGRESS_ID);
	}
	
	/**@deprecated
	 * @return The highest requirement of the default progress-id: raid-completions*/
	public BotRole getHighestRequirement() {
		return getHighestRequirement(DEFAULT_PROGRESS_ID);
	}
	
	/**@deprecated*/
	public BotRole getRoleByRequirement(int clears) {
		return getRoleByRequirement(DEFAULT_PROGRESS_ID, clears);
	}
	
	/**@deprecated
	 * @return If the botRole list is empty or clears already is the highest role, 0 is returned*/
	public int getUntilNextLevel(int clears) {
		return getUntilNextLevel(DEFAULT_PROGRESS_ID, clears);
	}
	
	/**If the botRole list is empty or the id does not exist, null is returned
	 * @returns The BotRole with the corresponding discord role id.*/
	public BotRole getById(String id) {
		for(BotRole role : botRoles) {
			if(role.serverRole.getId().asString().equals(id)) return role;
		}
		return null;
	}
	
	public BotRole editTriumphRole(String triumphName, String progressId, int requirement, Color color, boolean forceRole) {
		return addTriumphRole( triumphName,  progressId,  requirement,  color,  forceRole, true);
	}
	
	public BotRole addTriumphRole(String triumphName, String progressId, int requirement, Color color, boolean forceRole) {
		return addTriumphRole( triumphName,  progressId,  requirement,  color,  forceRole, false);
	}
	
	public BotRole addTriumphRole(String triumphName, String progressId, int requirement, Color color, boolean forceRole, boolean edit) {
		
		for(BotRole role : botRoles) {
			if((role.progressId.equals(progressId) && role.requirement == requirement) || role.serverRole.getName().equals(triumphName)) {
				if(!edit) {
					Logger.warn("Conflict found but aborting. Since the role should not get modified!");
					return role;
				}
				Logger.warn("Conflict with existing role found. " + role +" Deleting/overwriting old role");
				if(roleExists(role.serverRole.getId())) role.serverRole.delete().doOnError(error -> {}).block();
				botRoles.remove(role);
				break;
			}
		}
		
		Node roleAsNode = new Node();
		roleAsNode.setName(triumphName);
		roleAsNode.getName();
		roleAsNode.addNumber(ROLE_REQUIREMENT_NAME, requirement);
		roleAsNode.addString(PROGRESS_ID_NAME, progressId);
		roleAsNode.addArray(ROLE_COLOR_NAME, color.getRed(), color.getGreen(), color.getBlue());
		roleAsNode.addString(ROLE_FORCEROLE_NAME, forceRole ? "true" : "false");
		Role serverRole = createRole(client.getServer(), roleAsNode);
		roleNode.addNode(triumphName, roleAsNode);
		
		BotRole newRole = new BotRole(serverRole, requirement, progressId, forceRole, roleAsNode);
		
		botRoles.add(newRole);
		database.save();
		Logger.log("Triumph role created and saved inside the database");
		return newRole;
	}
	
	/**The node object should contain the following valiables:<br>
	 * <code>- requirement: number<br>- </code>*/
	private Role createRole(Guild server, Node role) {
		Role newRole = server.createRole(spec -> {
			spec.setName(role.getName());
			if(role.get(ROLE_COLOR_NAME).isArray()) {
				String[] array = role.get(ROLE_COLOR_NAME).getAsArray();
				if(array.length >= 3) {
					if(ApplicationBuilder.testForWholeNumber(array[0]) && 
							ApplicationBuilder.testForWholeNumber(array[1]) &&
								ApplicationBuilder.testForWholeNumber(array[2])) {
						spec.setColor(Color.of(Integer.valueOf(array[0]), Integer.valueOf(array[1]), Integer.valueOf(array[2])));
					} else Logger.err("Wrong color format " + ROLE_COLOR_NAME + " Needs to be an array with at least 3 rgb numbers");
				} else Logger.err("Wrong color format " + ROLE_COLOR_NAME + " Needs to be an array with at least 3 rgb numbers");
			} else {
				Logger.warn("Variable " + ROLE_COLOR_NAME + " not found. Creating default r:0, g:0, b:0");
				role.addArray(ROLE_COLOR_NAME, "0", "0", "0");
			}
			
			spec.setHoist(true);
			spec.setMentionable(true);
		}).doOnError(onError -> {
			Logger.err("Unable to create Role: " + onError.getLocalizedMessage());
		}).block();
		
		role.addString(ROLE_ID_NAME, newRole.getId().asString());
		
		Variable triumphId = role.get(PROGRESS_ID_NAME);
		if(triumphId.isUnknown() || !triumphId.isString()) {
			role.addString(PROGRESS_ID_NAME, "");
			Logger.warn("Role has an unknown triumph id");
		} 
		
		role.addString(PROGRESS_ID_NAME, triumphId.getAsString());
		Logger.log("Role " + role.getName() + " created on the server and assigned to the id: " + role.get(ROLE_ID_NAME).getAsString());
		return newRole;
	}

	public boolean deleteRole(String string) {
		if(roleNode.get(string).isUnknown()) return false;
		
		for(BotRole role : botRoles) {
			if(role.serverRole.getName().equals(string)) {
				roleNode.get(string).delete();
				botRoles.remove(role);
				role.serverRole.delete().onErrorStop().block();
				database.save();
				return true;
			}
		}
		return false;
	}
	
	/**To be able to save triumphs for a discord user
	 *@return true, if the user got a triumph*/
	public boolean setProgressForMember(String memberId, String progressId, int progressValue) {
		if(!client.isServerMember(memberId)) return false;
		
		Node userNode = database.get("users").getAsNode().getCreateNode(memberId);
		Node triumphs = userNode.getCreateNode(TRIUMPH_VARIABLE_NAME);
		
		int prevProgress = getProgressByIdForMember(memberId, progressId);
		boolean forceRole = false;
		
		for(BotRole role : getRolesByProgressId(progressId)) {
			if(role.progressId.equals(progressId)) {
				
				if(!roleExists(role.serverRole.getId())) {
					Logger.warn("Role was somehow deletet. Creating a new one!");
					botRoles.remove(role);
					botRoles.add(editTriumphRole(role.serverRole.getName(), progressId, role.requirement, role.serverRole.getColor(), role.forceRole));
				}
				
				forceRole = role.forceRole;
				triumphs.addNumber(progressId, progressValue);
				break;
			}
		}
		if(!forceRole) return false;
		
		Member target = client.getServer().getMemberById(Snowflake.of(memberId)).block();
		BotRole current = getRoleByRequirement(progressId, progressValue);
		BotRole previous = getRoleByRequirement(progressId, prevProgress);
		if(current != null && previous != null) {
			if(current.requirement > previous.requirement) {
				if(forceRole) {
					target.removeRole(previous.serverRole.getId()).block();
					target.addRole(current.serverRole.getId()).subscribe();
				}
				MemberTriumphEvent event = new MemberTriumphEvent();
				event.current = current;
				event.previous = previous;
				event.progressValue = progressValue;
				event.member = target;
				client.pluginmgr.triggerOnMemberTriumph(event);
				return true;
			} else if(current.requirement == previous.requirement) {
				target.addRole(current.serverRole.getId()).subscribe();
				return false;
			}
		} else if(current != null && previous == null) {
			if(forceRole) target.addRole(current.serverRole.getId()).subscribe();
			MemberTriumphEvent event = new MemberTriumphEvent();
			event.current = current;
			event.previous = previous;
			event.progressValue = progressValue;
			event.member = target;
			client.pluginmgr.triggerOnMemberTriumph(event);
			return true;
		} else if(current == null && previous != null) {
			if(forceRole) target.removeRole(previous.serverRole.getId()).subscribe();
			return false;
		}
		return false;
	}
	
	/**If a player does not have the triumph by the id, 0 is returned as standart progress.*/
	public int getProgressByIdForMember(String memberId, String progressId) {
		if(!client.isServerMember(memberId)) return 0;
		
		Node userNode = database.get("users").getAsNode().getCreateNode(memberId);
		Node triumphs = userNode.getCreateNode(TRIUMPH_VARIABLE_NAME);
		
		for(Variable triumph : triumphs.getVariables()) {
			if(triumph.getName().equals(progressId)) {
				return triumph.getAsInt();
			}
		}
		return 0;
	}
	
	public BotRole[] getAquiredTriumphsFromMember(String memberId) {
		if(!client.isServerMember(memberId)) return new BotRole[] {};
		
		Node userNode = database.get("users").getAsNode().getCreateNode(memberId);
		Node triumphs = userNode.getCreateNode(TRIUMPH_VARIABLE_NAME);
		
		ArrayList<BotRole> aquired = new ArrayList<>();
		for(Variable triumph : triumphs.getVariables()) {
			BotRole highest = getRoleByRequirement(triumph.getName(), triumph.getAsInt());
			if(highest != null) aquired.add(highest);
		}
		return aquired.toArray(new BotRole[aquired.size()]);
	}
	
	/**You can edit any triumph properties with this. Except the default ones*/
	public void setTriumphProperty(String roleName, String propertyName, String propertyValue) {
		if(propertyName.equals(PROGRESS_ID_NAME) 
				|| propertyName.equals(ROLE_ID_NAME)
				|| propertyName.equals(ROLE_COLOR_NAME)
				|| propertyName.equals(ROLE_REQUIREMENT_NAME)) {
			Logger.log("Illegal property name " + propertyName);
			return;
		}
		
		Variable var = roleNode.get(roleName);
		if(!var.isUnknown() && var.isNode()) {
			Node n = var.getAsNode();
			n.addString(propertyName, propertyValue);
			client.database.save();
		} else Logger.log("Role name to set property not found");
	}
	
	public String getTriumphProperty(String roleName, String propertyName) {
		Variable var = roleNode.get(roleName);
		if(!var.isUnknown() && var.isNode()) {
			Variable property = var.getAsNode().get(propertyName);
			if(!property.isUnknown() && property.isString()) {
				return property.getAsString();
			} else Logger.log("Property for role " + roleName + " not found: " + propertyName);
		} else Logger.log("Role name to get property not found");
		return null;
	}
	
	private boolean roleExists(Snowflake id) {
		return client.getServer().getRoleById(id).onErrorReturn(null).block() != null;
	}
}
