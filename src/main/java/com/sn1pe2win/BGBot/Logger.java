package com.sn1pe2win.BGBot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
	
	/**Die Log Datei. Standartmäßig als bot.log erstellt*/
	private static File logFile;
	
	/**Der output stream um in die Text-Datei zu schreiben*/
	private static BufferedWriter writer;
	
	/**Das Format für die Protokollierte Zeit. Default ist: dd-MM-yyyy HH:mm:ss im {@link SimpleDateFormat}*/
	private static DateFormat format = new SimpleDateFormat("dd.MM.yyyy-HH:mm:ss.SSS");
	
	public static boolean consoleLog = true;
	
	public static void configure(String path) throws IOException {
		DateFormat fileFormat = new SimpleDateFormat("ddMMyyyy-HHmmss");
		if(path == null) return;
		
		logFile = new File(path + fileFormat.format(new Date(System.currentTimeMillis())) + ".log");
		if(logFile == null) throw new IOException("Please specify a valid path");
		
		writer = new BufferedWriter(new FileWriter(logFile));
	}
	
	public static void setDateFormat(DateFormat format) {
		Logger.format = format;
	}
	
	/**Protokolliert die basic informations über das derzeit laufende Programm, wann es gestartet wurde etc.*/
	public static void logStart() {
		write("#####\n[ INFO] ", "Programm gestartet:");
	}
	
	public static void log(String content) {
		write("[ INFO] ", content.replace("\n", ""));
	}
	
	public static void err(String content) {
		write("[ERROR] ", content.replace("\n", ""));
	}
	
	public static void warn(String content) {
		write("[ WARN] ", content.replace("\n", ""));
	}
	
	private static void write(String beforeDate, String afterDate) {
		try {
			String content = beforeDate + Logger.format.format(new Date(System.currentTimeMillis())) + " >> " + afterDate + "\n";
			if(logFile != null) {
				writer.write(content);
				writer.flush();
			}
			if(consoleLog || logFile == null) {
				if(beforeDate.equals("[ERROR] ")) System.err.print(content);
				else System.out.print(content);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public File getLogFile() {
		return logFile;
	}
}

