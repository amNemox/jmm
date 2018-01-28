package ru.amNemox;

import java.sql.*;

public class tMysql {
    private String URL;
    private Connection connection = null;
    private Statement stmt;
    private ResultSet rs;

    public Connection getConnection() {
        return connection;
    }

    public void connect(String cUrl, String cUser, String cPass){

        Driver driver;

        try   {
            driver = new com.mysql.jdbc.Driver();
            DriverManager.registerDriver(driver);

            try {
                connection = DriverManager.getConnection(cUrl, cUser, cPass);
                if (!connection.isClosed())
                    System.out.println("Соединение установлено");
            }catch (SQLException ex){
                System.err.println("Соединение не установлено");
                ex.printStackTrace(); // Понадобился, чтобы отловить исключения, скрытые выводом на экран предупреждения
                return;
            }

        }
        catch (SQLException e1) {
            System.out.println("Драйвер не зарегистрировался");
            return;
        }
    }

    tMysql(String mysqlUser, String mysqlPass, String mysqlDbName, String mysqlHost, int mysqlPort){
        this.URL = "jdbc:mysql://"+mysqlHost+":"+mysqlPort+"/"+mysqlDbName+"?autoReconnect=true&useSSL=false";
        this.connect(this.URL,mysqlUser,mysqlPass);
    }
    public ResultSet sQuery(String query){
        try {
            stmt = connection.createStatement();
            rs = stmt.executeQuery(query);
        } catch (SQLException eSq){
            System.out.println("Error in SQL query: "+query+"\n");
            eSq.printStackTrace();
        } finally {
            if(rs != null) return rs;
              else return null;
        }

    }
    public boolean uQuery(String query){
        try {
            stmt = connection.createStatement();
            stmt.execute(query);
        } catch (SQLException eSq){
            System.out.println("Error in SQL query: "+query+"\n");
            eSq.printStackTrace();
        }
        return true;
    }
}
