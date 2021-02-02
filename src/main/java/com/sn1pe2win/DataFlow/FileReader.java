package com.sn1pe2win.DataFlow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.sn1pe2win.DataFlow.Values.Data;

public final class FileReader {

	/**Alphabet*/
	public static final char VAR_END = ';';
	public static final char VAR_VALUE = '=';
	public static final char VAR_ARRAY = ',';
	public static final char VAR_OBJECT_START = '{';
	public static final char VAR_OBJECT_END = '}';
	
	public static Node parse(File file) {
		if(!file.exists()) return new Node(new Data());
		try {
			InputStreamReader isr = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
			BufferedReader reader = new BufferedReader(isr);
			String line = reader.readLine();
			String content = "";
			
			while(line != null) {
				content += line;
				line = reader.readLine();
			}
			reader.close();
			return parse(content);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new Node(new Data());
	}
	
	public static Node parse(String stringData) {
		if(stringData == null) return new Node(new Data());
		if(stringData.isEmpty()) return new Node(new Data());
		stringData = stringData.replaceAll("\t", "");
		stringData = stringData.replaceAll("\r", "");
		stringData = stringData.replaceAll("\n", "");
		
		Node main = new Node(new Data());
		handleBlock(stringData, main);
		return main;
	}
	
	private static void handleBlock(String block, Node data) {
		String line = "";
		
		int bracketWrap = 0;
		for(int i = 0; i < block.length(); i++) {
			char current = block.charAt(i);
			//char prev = i > 0 ? stringData.charAt(i-1) : 0x00;
			if(current == VAR_OBJECT_START) bracketWrap++;
			if(current == VAR_OBJECT_END) bracketWrap--;
			
			line += current;
			if((current == VAR_END || current == VAR_OBJECT_END) && bracketWrap == 0) {
				handleLine(line, data);
				line = "";
				continue;
			}
		}
	}
	
	/**A "line" is a string till the next ;*/
	private static void handleLine(String line, Node data) {
		String variableName;
		int varNameEnd = line.indexOf(VAR_VALUE);
		
		if(varNameEnd != -1) {
			variableName = line.substring(0, line.indexOf(VAR_VALUE));
		} else return;
		
		String variableContent = line.substring(varNameEnd+1, line.length());
		if(!variableContent.startsWith(String.valueOf(VAR_OBJECT_START))) {
			ArrayList<String> arrayEntries = new ArrayList<>();
			String varcSoFar = "";
			for(int i = 0; i < variableContent.length(); i++) {
				char c = variableContent.charAt(i);
				
				if(c == VAR_END) {
					arrayEntries.add(varcSoFar);
					varcSoFar = "";
					i++;
					break;
				} else if(c == VAR_ARRAY) {
					arrayEntries.add(varcSoFar);
					varcSoFar = "";
					continue;
				}
				
				varcSoFar += c;
			}
			
			if(arrayEntries.isEmpty()) arrayEntries.add("null");
			
			if(arrayEntries.size() == 1) {
				if(!testForFloat(arrayEntries.get(0))) data.addVariable(variableName, arrayEntries.get(0));
				else data.addVariable(variableName, Float.valueOf(arrayEntries.get(0)));
			} else {
				boolean forceArray = arrayEntries.get(arrayEntries.size()-1).isEmpty();
				//Remove all empty entries
				for(int i = 0; i < arrayEntries.size(); i++) {
					if(arrayEntries.get(i).isEmpty()) {
						arrayEntries.remove(i);
						i = 0;
					}
				}
				if(arrayEntries.size() == 1 && !forceArray) data.addVariable(variableName, arrayEntries.get(0));
				else data.addVariable(variableName, arrayEntries.toArray(new String[arrayEntries.size()]));
			}
		} else {
			Node inherit = new Node();
			handleBlock(variableContent.substring(1, variableContent.length()), inherit);
			data.addNode(variableName, inherit);
		}
	}
	
	public static String print(Node node) {
		String m = "";
		for(Variable v : node.getVariables()) m = printTree(v, m, 0);
		return m;
	}
	
	private static String printTree(Variable variable, String message, int depth) {
		for(int i = 0; i < depth; i++) message += "\t";
		if(variable.isString()) message += variable.getName() + "=" + (testForFloat(variable.getAsString()) ? "," : "") + variable.getAsString() + ";\n";
		else if(variable.isNumber()) message += variable.getName() + "=" + variable.getAsString() + ";\n";
		else if(variable.isArray()) {
			message += variable.getName() + "=";
			for(int i = 0; i < variable.getAsArray().length; i++) {
				message += variable.getAsArray()[i] +  ",";
			}
			message += ";\n";
		} else if(variable.isNode()) {
			message += variable.getName() + "={\n";
			for(Variable v : variable.getAsNode().getVariables()) {
				message = printTree(v, message, depth+1);
			}
			for(int i = 0; i < depth; i++) message += "\t";
			message += "}\n";
		} else message += "=;";
		return message;
	}
	
	/**Valid, if the string is separated either with a dot, or a comma:<br>
	 * <code>0.01<br>0,2<br>12,000.00</code> Is not allowed, because of a separator.<br><code>12000.00 or 12000,00</code>would be accepted.*/
	private static boolean testForFloat(String string) {
		if(string == null) return false;
		if(string.isEmpty()) return false;
		if(string.startsWith("-")) string = string.substring(1);
		
		string = string.replaceAll(",", ".");
		boolean point = false;
		for(int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if(!Character.isDigit(c) && c != '.') return false;
			else if(c == '.' && point) return false;
			if(c == '.') point = true;
		}
		return true;
	}
}
