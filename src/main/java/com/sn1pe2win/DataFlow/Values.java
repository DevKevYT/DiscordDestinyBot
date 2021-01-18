package com.sn1pe2win.DataFlow;

import java.util.ArrayList;

public interface Values {
	
	public class DataVariable {
		public String name = "";
		
		public String string = null;
		public Float number = null;
		public String[] array = null;
		public ArrayList<DataVariable> inherit = null;
		
		public String toString() {
			return name;
		}
	}
	
	public class Data {
		public ArrayList<DataVariable> variables = new ArrayList<>();
	}
}
