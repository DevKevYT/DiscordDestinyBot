package com.sn1pe2win.DataFlow;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

	private Connection connection;
	private Statement statement;
	
	public Database(Connection connection) throws SQLException {
		this.connection = connection;
		statement = this.connection.createStatement();
	}
	
	public ResultSet query(String SQLQuery) {
		try {
			return statement.executeQuery(SQLQuery);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
}
