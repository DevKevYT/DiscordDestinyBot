package com.sn1pe2win.BGBot;

import java.util.ArrayList;

import discord4j.rest.util.Color;

/**Nerviger wrapper extra für embeds man*/
public class EmbedData {
	
	public class Field {
		public String name = "";
		public String text = "";
		public boolean inline = false;
	}
	
	public String url;
	public String author;
	public String authorURL = "";
	public String authorIconURL = "";
	public String title;
	public String description;
	public Color color;
	public String thumbnailURL;
	public String imageURL;
	public String footer;
	public String footerURL;
	public ArrayList<Field> fields = new ArrayList<>();
	
	public void addField(String name, String text, boolean inline) {
		Field f = new Field();
		f.name = name;
		f.text = text;
		f.inline = inline;
		fields.add(f);
	}
}
