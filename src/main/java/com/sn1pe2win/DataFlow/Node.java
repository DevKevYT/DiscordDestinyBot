package com.sn1pe2win.DataFlow;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.sn1pe2win.BGBot.Logger;
import com.sn1pe2win.DataFlow.Values.Data;
import com.sn1pe2win.DataFlow.Values.DataVariable;

public class Node {
	
	private Data mainNode;
	private File file;
	private static Json handler = new Json();
	
	private ArrayList<DataVariable> variables;
	private Node parent;
	private String name;
	
	Node(Node parent, String name, ArrayList<DataVariable> variables) {
		this.variables = variables;
		this.parent = parent;
		this.name = name;
	}
	
	/**Die Daten bleiben immer in Referenz. Das heißt die Werte im Objekt "mainNode" sind
	 * immer aktuell und müssen z.B. vor dem Speichern nicht nochmal aktualisiert werden.
	 * */
	public Node(Data mainNode) {
		this.mainNode = mainNode;
		this.variables = mainNode.variables;
	}
	
	/**Creates an empty node, not declared as main node*/
	public Node() {
		this.mainNode = null;
		this.variables = new ArrayList<Values.DataVariable>();
	}
	
	/**Lädt die Daten direkt aus der Datenbank im JSON format*/
	public Node(File file) {
		if(!file.exists()) throw new IllegalArgumentException("Database file not found");
		this.file = file;
		FileHandle fh = new FileHandle(file);
		
		try {
			this.mainNode = handler.fromJson(Data.class, fh);
			this.variables = mainNode.variables;
			return;
		} catch(Exception e) {
			Logger.warn("This file is not readable. Trying to load using the new format...");
			
			try {
				this.mainNode = FileReader.parse(file).getData();
				this.variables = mainNode.variables;
			} catch(Exception ex) {
				Logger.err("This file is still unreadable!");
				ex.printStackTrace();
			}
			if(this.mainNode == null) {
				this.mainNode = new Data();
				this.variables = new ArrayList<Values.DataVariable>();
			}
			return;
		}
	}
	
	public boolean save() {
		if(file == null) throw new IllegalAccessError("Use the function save(File file), if you loaded this database without a file");
		return save(file);
	}
	
	public boolean save(File file) {
		if(!isMainNode()) return false;
		
		try {
			OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
			BufferedWriter writer = new BufferedWriter(osw);
			writer.write(FileReader.print(this));
			writer.flush();
			writer.close();
			return true;
		} catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**Tries to find a node with this name.
	 * If none exist, an empty node is created*/
	public Node getCreateNode(String name) {
		Node exist = get(name).getAsNode();
		if(exist != null) return exist;
		else return addNode(name);
	}
	
	/**Tries to find an array with this name.
	 * If none exist, an empty array is created and returned*/
	public Variable getCreateArray(String name) {
		Variable exist = get(name);
		if(exist.isUnknown() || !exist.isArray()) return addArray(name, new String[] {}).get(name);
		else return exist;
	}
	
	public Variable getCreateString(String name, String fallbackValue) {
		Variable exist = get(name);
		if(exist.isUnknown() || !exist.isString()) return addString(name, fallbackValue).get(name);
		else return exist;
	}
	
	public Variable get(String name) {
		for(DataVariable var : variables) {
			if(var.name.equals(name)) return new Variable(this, var);
		}
		return Variable.UNKNOWN;
	}
	
	/**@return True, if the removal was successfull / variable with this name was found*/
	public boolean remove(String name) {
		for(DataVariable var : variables) {
			if(var.name.equals(name)) {
				variables.remove(var);
				return true;
			}
		}
		return false;
	}
	
	public boolean contains(String name) {
		return !get(name).isUnknown();
	}
	
	/**Returns the variable with the raw index.
	 * Useful if you want to check all variables from this node with loops*/
	public Variable get(int index) {
		if(index < 0 || index > size()) throw new ArrayIndexOutOfBoundsException("Index < 0 or > " + size());
		return new Variable(this, variables.get(index));
	}
	
	public Variable[] getVariables() {
		Variable[] var = new Variable[variables.size()];
		for(int i = 0; i < var.length; i++) {
			var[i] = new Variable(this, variables.get(i));
		}
		return var;
	}
	
	public Node getParent() {
		return parent;
	}
	
	public boolean isMainNode() {
		return mainNode != null;
	}
	
	public void setName(String name) {
		this.name = name;
		if(getParent() != null) {
			if(getParent().contains(name)) {
				getParent().get(name).data.inherit = this.variables;
			}
		}
	}
	
	public String getName() {
		return name == null ? "BASE" : name;
	}
	
	public String getPath() {
		return toString();
	}
	
	/**Fügt einen Node hinzu, an dem weitere Variablen gesetzt werden können.
	 * VORSICHT!!! Wenn eine Variable mit diesem Namen bereits existiert, wird der
	 * gesamte - darunterliegende Pfad gelöscht!*/
	public Node addNode(String name) {
		verifyName(name);
		
		Variable found = get(name);
		if(found.equals(Variable.UNKNOWN)) {
			DataVariable var = new DataVariable();
			var.name = name;
			var.inherit = new ArrayList<Values.DataVariable>();
			variables.add(var);
			return new Node(this.parent, name, var.inherit);
		} else {
			for(DataVariable var : variables) {
				if(var.name.equals(name)) {
					var.array = null;
					var.number = null;
					var.string = null;
					var.inherit = new ArrayList<Values.DataVariable>();
					var.name = name;
					return found.getAsNode();
				}
			}
			return null;
		}
	}
	
	/**Adds a node with all its contents with the given name. The possible name of the node itself
	 * is getting overwritten*/
	public Node addNode(String name, Node node) {
		Node created = this.addNode(name);
		created.variables.addAll(node.variables);
		return created;
	}
	
	/**Fügt eine String Variable hinzu.
	 * Wenn eine Variable mit diesem Namen existiert, wird der Wert überschrieben*/
	public Node addString(String name, Object stringValue) {
		if(stringValue == null) return this;
		verifyName(name);
		return addVariable(name, stringValue.toString());
	}
	
	public Node addNumber(String name, float numberValue) {
		verifyName(name);
		return addVariable(name, numberValue);
	}
	
	public Node addArray(String name, String...arrayValue) {
		if(arrayValue == null) return this;
		verifyName(name);
		return addVariable(name, arrayValue);
	}
	
	public Node addArray(String name, Object... arrayValue) {
		if(arrayValue == null) return this;
		verifyName(name);
		String[] stringArray = new String[arrayValue.length];
		for(int i = 0; i < stringArray.length; i++) stringArray[i] = arrayValue[i].toString();
		return addVariable(name, stringArray);
	}
	
	public Node addVariable(String name, Object value) {
		if(value == null) return this;
		verifyName(name);
		Variable found = get(name);
		if(found.equals(Variable.UNKNOWN)) {
			DataVariable dataVar = new DataVariable();
			dataVar.name = name;
			if(value instanceof String) dataVar.string = value.toString();
			else if(value instanceof Float) dataVar.number = (Float) value;
			else if(value instanceof String[]) dataVar.array = (String[]) value;
			variables.add(dataVar);
			return this;
		} else {
			if(value instanceof String) found.setString(value.toString());
			else if(value instanceof Float) found.setNumber(((Float) value).floatValue());
			else if(value instanceof String[]) found.setArray((String[]) value);
			return this;
		}
	}
	
	/**Läuft den gegebenen Pfad ab. Das kann Dinge deutlich übersichtlicher zu machen. Ein Beispiel:<br>
	 * <code>mainNode.get("shame-table").getAsVariable("ID").getAsVariable("points")</code><br>Wird zu:<br>
	 * <code>mainNode.walkPath("shame-table/ID/points")</code>
	 * Als pfad separator sollte man "/" benutzen<br>
	 * Als letzter punkt sollte immer ein Node sein. Man kann sich das so vorstellen, als würde man den Pfad eines
	 * Ordners anwählen*/
	public Node walkNodePath(String path) {
		String[] parts = path.split("/");
		if(parts.length == 0) return this;
		
		Node current = get(parts[0]).getAsNode();
		for(int i = 1; i < parts.length; i++) {
			if(parts[i].isEmpty()) continue;
			if(current == null) throw new IllegalAccessError("Variable node " + parts[i-1] + " from path " + path + " not found!");
			
			Variable var = current.get(parts[i]);
			current = var.getAsNode();
		}
		return current;
	}
	
	public Variable walkVariablePath(String path) {
		String[] parts = path.split("/");
		if(parts.length == 0) return null;
		
		Node current = get(parts[0]).getAsNode();
		for(int i = 1; i < parts.length-1; i++) {
			if(parts[i].isEmpty()) continue;
			if(current == null) throw new IllegalAccessError("Variable " + parts[i-1] + " from path " + path + " not found!");
			
			Variable var = current.get(parts[i]);
			current = var.getAsNode();
		}
		return current.get(parts[parts.length-1]);
	}
	
	public static void verifyName(String name) {
		if(name.length() == 0 || name.contains("/")) 
			throw new IllegalArgumentException("\"" + name + "\": Variables should not contain / characters or be empty!");
	}
	
	public int size() {
		return variables.size();
	}
	
	public Data getData() {
		return mainNode;
	}
	
	/**@return Eine möglichst visuelle representation des Baumes zurück*/
	public String printTree() {
		String m = "";
		for(DataVariable v : variables) m = printTree(v, m, 0);
		return m;
	}
	
	private String printTree(DataVariable variable, String message, int depth) {
		for(int i = 0; i < depth; i++) message += "\t";
		if(variable.string != null) message += "[" + variable.name + ":'" + variable.string + "']\n";
		else if(variable.number != null) message += "[" + variable.name + ":'" + variable.number + "']\n";
		else if(variable.array != null) {
			message += "[" + variable.name + ":{";
			for(int i = 0; i < variable.array.length; i++) {
				message += variable.array[i] + (i == variable.array.length-1 ? "" : ", ");
			}
			message += "}]\n";
		} else if(variable.inherit != null) {
			message += "[" + variable.name + ":\n";
			for(DataVariable v : variable.inherit) {
				message = printTree(v, message, depth+1);
			}
		} else message += "[???]\n";
		return message;
	}
	
	public String toString() {
		if(isMainNode()) return "main-node";
		else {
			String path = getName() + ".";
			Node parent = this.parent;
			while(parent != null) {
				path =  parent.getName() + "." + path;
				parent = parent.getParent();
			}
			return path;
		}
	}
}
