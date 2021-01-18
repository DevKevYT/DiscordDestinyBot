package com.sn1pe2win.DataFlow;

import com.sn1pe2win.DataFlow.Values.DataVariable;

public final class Variable {
	
	public static final Variable UNKNOWN = new Variable();
	
	DataVariable data;
	Node parent;
	
	private Variable() {
		data = new DataVariable();
	}
	
	Variable(Node parent, DataVariable data) {
		this.data = data;
		this.parent = parent;
	}
	
	public boolean isUnknown() {
		return this.equals(UNKNOWN);
	}
	
	public boolean isNode() {
		return data.inherit != null;
	}
	
	public boolean isNumber() {
		return data.number != null;
	}
	
	public boolean isString() {
		return data.string != null;
	}
	
	public boolean isArray() {
		return data.array != null;
	}
	
	/**Wenn die Variable vom Typ: INHERIT ist*/
	public Node getAsNode() {
		if(!isNode()) return null;
		
		return new Node(parent, data.name, data.inherit);
	}
	
	/**Nur m�glich, wenn diese Variable ein Node ist, d.h. noch weitere Variablen enth�lt.
	 * Eigentlich nur eine Abk�rzung f�r die Funktion:<br><br><code> Variable.getAsNode().get(String name)<br>->Variable.getAsVariable(String name)</code>*/
	public Variable getAsVariable(String name) {
		if(isUnknown()) return null;
		
		Node n = new Node(parent, data.name, data.inherit);
		return n.get(name);
	}
	
	public float getAsFloat() {
		return data.number.floatValue();
	}
	
	public int getAsInt() {
		return data.number.intValue();
	}
	
	public long getAsLong() {
		return data.number.longValue();
	}
	
	public Float getAsNumber() {
		return data.number;
	}
	
	public String[] getAsArray() {
		if(isArray()) return data.array;
		else return null;
	}
	
	public String getAsString() {
		if(isString()) return data.string;
		else return null;
	}
	
	public Node getParent() {
		return parent;
	}
	
	public String getName() {
		return data.name;
	}
	
	public void delete() {
		getParent().remove(getName());
	}
	
	public void setString(String stringValue) {
		erase();
		data.string = stringValue;
	}
	
	public void setNumber(float number) {
		erase();
		data.number = number;
	}
	
	public void setArray(String...stringValues) {
		erase();
		data.array = stringValues;
	}
	
	private void erase() {
		data.array = null;
		data.inherit = null;
		data.number = null;
		data.string = null;
	}
	
	public String toString() {
		if(isUnknown()) return "unknown";
		else {
			String path = getName();
			Node parent = this.parent;
			while(parent != null) {
				path =  parent.getName() + "." + path;
				parent = parent.getParent();
			}
			return path;
		}
	}
}
