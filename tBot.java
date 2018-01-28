package ru.amNemox;
//Заготовка - бот нужен для добавления телеграм-пользователей и првязки их к кошелькам и фермам. Рассылка осуществляется через
// веб апи, отдельным классом.

import org.json.JSONObject;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


public class tBot extends TelegramLongPollingBot{
    private String botName="";
    private String botToken="";
    private static boolean init=false;
    private static HashMap<Long,String> clients = new HashMap<>();
    private static String mysqlUser="";
    private static String mysqlPass="";
    private static String mysqlDbName="";
    private static String mysqlHost="";
    private static int mysqlPort=0;
    private static tMysql mySql;
    tBot(String configFileName) {
        jConfig cfg = new jConfig(configFileName);
        JSONObject config = new JSONObject(cfg.getjString());
        System.out.println(cfg.getjString() + "\n" + config);
        this.botName = config.getString("botName");
        this.botToken = config.getString("botToken");
        mysqlDbName = config.getString("mysqlDbName");
        mysqlHost = config.getString("mysqlHost");
        mysqlPass = config.getString("mysqlPass");
        mysqlPort = config.getInt("mysqlPort");
        mysqlUser = config.getString("mysqlUser");
        this.init = true;
// tMysql(String mysqlUser, String mysqlPass, String mysqlDbName, String mysqlHost, int mysqlPort){
        mySql = new tMysql(mysqlUser, mysqlPass, mysqlDbName, mysqlHost, mysqlPort);
        try {
            loadClients();
        } catch (SQLException eS) {
            System.out.println("Ошибка восстановления чатов из SQL " + eS.getMessage());
        }
    }
    public static void main(String[] args) {
        ApiContextInitializer.init();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            telegramBotsApi.registerBot(new tBot("tBot.txt"));
        } catch (TelegramApiException e) {
            if(init) e.printStackTrace();
             else System.out.println("Ошбика запуска - бот не инициализирован, "+e.getLocalizedMessage());
        }

    }
        @Override
        public String getBotUsername() {
            return this.botName;
        }

    @Override
    public String getBotToken() {
        return this.botToken;
    }
        @Override
        public void onUpdateReceived(Update e) {
            Message msg = e.getMessage();
            String txt = msg.getText();
            Long chatId = msg.getChatId();
            if (txt.equals("/start")) {
                sendMsg(chatId, "Hi, i'm jmmBot. auth pls");
            } else if( txt.startsWith("/auth")){
                String[] auth =txt.split(" ");
                if(auth[1].equals("amNemox")){
                    clients.put(chatId,auth[1]);
                    sendMsg(chatId, "Hello, "+auth[1]+" auth ok");
                    storeClients();
                }
            } else if( txt.startsWith("/farms")) {
                String[] auth = txt.split(" ");
                String owner=null;
                if(clients.containsKey(chatId)){
                    owner = clients.get(chatId);
                    }
                else sendMsg(chatId," У вас нет ферм, возможно требуется повторная авторизация ");
                if(owner != null) sendMsg(chatId,listFarms(owner));

            }else if(txt.startsWith("/farm")){
                //get farm stat: lastSee,

                //последние 10 строк farm_stat - вычислить среднее значение

                //последние 10 строк gpu_stat по каждому gpu - вычислить средние значения
            } else {
                sendMsg(chatId,  txt);
            }
        }


    @SuppressWarnings("deprecation")
    private void sendMsg(long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        try {
            sendMessage(msg);
            System.out.println("Send msg to "+chatId+"; MSG: \n"+text);
        } catch (TelegramApiException eT){
            System.out.println("Error sending telegram msg" + eT.getLocalizedMessage());
        }
    }

    private String listFarms(String owner){
        return " ";
    }

    private static void storeClients(){
        for( Map.Entry<Long,String> entry : clients.entrySet()){
            mySql.uQuery("DELETE FROM jmm_tChats WHERE `client`=\""+entry.getValue()+"\" OR `chatId`=\""+entry.getKey()+"\";");
            mySql.uQuery("INSERT INTO jmm_tChats VALUES('"+entry.getValue()+"','"+entry.getKey()+"');");
        }
    }
    private static void loadClients() throws SQLException{

        ResultSet rs = mySql.sQuery("SELECT * FROM `jmm_tChats`");
            while(rs.next()){
                clients.put(rs.getLong("chatId"),rs.getString("client"));
            }
        }

}

class tSender{
    //https://api.telegram.org/<id бота>/sendMessage?chat_id=<id чата>&text=мой текст
    private long chatId =0l;
    private String botToken="";
    private boolean init=false;
    private String url = "";
    private StringBuilder result=new StringBuilder("");
    tSender(String configFileName, long chatId) {
// null;
//        try {
//            config = new JSONObject(Files.readAllLines(Paths.get(configFileName), StandardCharsets.UTF_8).toString());
//        } catch (IOException eI) {
//            System.out.println("Ошибка чтения файла конфигурации " + configFileName + ": " + eI.getMessage());
//            return;
//        }
        jConfig cfg = new jConfig(configFileName);
        JSONObject config = new JSONObject(cfg.getjString());
        this.botToken = config.getString("botToken");
        this.init = true;
        this.url = "https://api.telegram.org/bot" + this.botToken + "/sendMessage?chat_id="+chatId+"&text=";
        this.chatId = chatId;
    }

    public void sendMsg(String msg){

        try{
            URL obj = new URL(url + msg);
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            connection.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            result.setLength(0);
            while ((inputLine = in.readLine()) != null) {
                result.append(inputLine);
            }
            in.close();

        } catch (Exception ex){
            ex.printStackTrace();
        }

    }
}
