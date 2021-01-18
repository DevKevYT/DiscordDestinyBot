package com.sn1pe2win.BGBot;

import java.util.ArrayList;
import java.util.List;

import com.devkev.devscript.raw.ApplicationBuilder;
import com.sn1pe2win.DataFlow.Node;
import com.sn1pe2win.DataFlow.Variable;

import discord4j.core.object.entity.Guild;
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
		
		private BotRole(Role serverRole, int requirement) {
			this.serverRole = serverRole;
			this.requirement = requirement;
		}
		
		public String toString() {
			return serverRole.getName() + ": " + requirement;
		}
	}
	
	public static final String ROLE_NODE_NAME = "roles";
	public static final String ROLE_ID_NAME = "role-id";
	public static final String ROLE_REQUIREMENT_NAME = "requirement";
	public static final String ROLE_COLOR_NAME = "color";
	
	public BotRole[] botRoles = new BotRole[] {};
	private Node database;
	
	public RoleManager(Node database) {
		this.database = database;
	}
	
	public void checkRoles(Guild server) {
		Variable roleVar = database.get(ROLE_NODE_NAME);
		if(roleVar.isUnknown() || !roleVar.isNode()) {
			Logger.warn("No roles to create because no variable with name " + ROLE_NODE_NAME + " was found or has the wrong format. Pretty boring");
			database.addNode(ROLE_NODE_NAME);
		}
		
		ArrayList<BotRole> finalServerRoles = new ArrayList<BotRole>();
		List<Role> serverRoles = server.getRoles().collectList().block();
		Node roles = database.get(ROLE_NODE_NAME).getAsNode();
		
		//Now try to search for serverRoles, that are either not in the roles list o
		for(Variable databaseRole : roles.getVariables()) {
			
			Logger.log("Checking role '" + databaseRole.getName() + "'");
			final Role finalServerRole;
			//If, for whatever reason the role info variable is not a node, overwrite this variable and make a node out of it
			if(!databaseRole.isNode()) {
				Logger.warn("Role " + databaseRole.getName() + " has to be a node. Creating default");
				roles.addNode(databaseRole.getName());
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
			
			if(id == null && found == null) finalServerRole = createRole(server, databaseRole.getAsNode());
			else if(found != null) {
				finalServerRole = found;
				//Keep the role data in the database updated by just synchronizing the serverRole values
				databaseRole.getAsNode().addString(ROLE_ID_NAME, found.getId().asString());
				databaseRole.getAsNode().addArray(ROLE_COLOR_NAME, found.getColor().getRed()+"", found.getColor().getGreen()+"", found.getColor().getBlue()+"");
			} else finalServerRole = createRole(server, databaseRole.getAsNode());
			
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
			finalServerRoles.add(new BotRole(finalServerRole, requirement));
		}
		
		botRoles = finalServerRoles.toArray(new BotRole[finalServerRoles.size()]);
		database.save();
	}
	
	/**If the botRole list is empty, null is returned*/
	public BotRole getLowestRequirement() {
		BotRole recordRole = null;
		for(BotRole role : botRoles) {
			if(recordRole == null) recordRole = role;
			else {
				if(role.requirement < recordRole.requirement) recordRole = role;
			}
		}
		return recordRole;
	}
	
	public BotRole getHighestRequirement() {
		BotRole recordRole = null;
		for(BotRole role : botRoles) {
			if(recordRole == null) recordRole = role;
			else {
				if(role.requirement > recordRole.requirement) recordRole = role;
			}
		}
		return recordRole;
	}
	
	/**If the botRole list is empty, null is returned*/
	public BotRole getRoleByRequirement(int clears) {
		BotRole r = getLowestRequirement();
		if(r == null) return null;
		
		for(BotRole role : botRoles) {
			if(clears >= role.requirement) r = role;
		}
		return r;
	}
	
	/**If the botRole list is empty or clears already is the highest role, 0 is returned*/
	public int getUntilNextLevel(int clears) {
		BotRole current = getRoleByRequirement(clears);
		
		if(current == null) return 0;
		//Find the next highest
		BotRole record = getHighestRequirement();
		for(BotRole role : botRoles) {
			if(role.requirement >= current.requirement && role.requirement < record.requirement && !role.equals(current)) {
				record = role;
			}
		}
		return record.requirement - clears;
	}
	
	/**If the botRole list is empty or the id does not exist, null is returned*/
	public BotRole getById(String id) {
		for(BotRole role : botRoles) {
			if(role.serverRole.getId().asString().equals(id)) return role;
		}
		return null;
	}
	
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
		}).block();
		role.addString(ROLE_ID_NAME, newRole.getId().asString());
		 Logger.log("Role " + role.getName() + " created on the server and assigned to the id: " + role.get(ROLE_ID_NAME).getAsString());
		return newRole;
	}
}
