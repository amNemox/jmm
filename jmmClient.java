package ru.amNemox;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;

public class jmmClient {
    public static String SERVER_ADDR = "127.0.0.1";
    public static int SERVER_PORT = 8086;
    public static worker fTh = null;
    public static pingCheck pTh = null;
    public static void sendPing(){
        if(fTh != null){
            fTh.sendPing();
        }
    }
    public static void main(String[] args) {
	// write your code here
//Каждую минуту надо считывать следующие статы:
        Socket socket = null;
        PrintWriter writer = null;
        BufferedReader reader = null;
        try {


            socket = new Socket(SERVER_ADDR, SERVER_PORT);
            writer = new PrintWriter(socket.getOutputStream());
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

        } catch (IOException eio){
            System.out.println("Cant connect to server: "+eio.getMessage());
        }
        fTh = new worker("farm2.txt",writer);
        pTh = new pingCheck(reader);

        System.out.println("--------------------------------------------------------\n\n\n");
        fTh.getFarm().getMinerStat();
        fTh.getFarm().loadOhw();
        if(fTh.getFarm().needreIndex()){
            fTh.getFarm().reIndex();
        }
        System.out.println(fTh.getFarm().getData());
        System.out.println(fTh.getFarm().getJString());
        System.out.println("--------------------------------------------------------\n\n\n");
        int thCnt=0;
        ArrayList<Thread> threads = new ArrayList<>();
        Thread stat = new Thread(fTh);
        Thread ping = new Thread(pTh);
        ping.setDaemon(true);
        stat.setDaemon(true);
        while(true){
            Date date = new Date();

            System.out.println("Проверка нитей "+date.toString());
            try{
                if(!stat.isAlive()) {
                    System.out.println("Нить контроля не запущена, запускаем ");
                    stat.interrupt();
                    stat = new Thread(fTh);
                    stat.start();
                } else{
                    System.out.println("Нить контроля в порядке");
                }
                if(!ping.isAlive()){
                    System.out.println("Нить ответа PING не запущена, запускаем ");
                    ping.interrupt();
                    ping = new Thread(pTh);
                    ping.start();
                } else {
                    System.out.println("Нить ping в порядке");
                }

                Thread.sleep(30000);
                } catch (InterruptedException ex){
                    ex.printStackTrace();
                }
        }
    }

}

class worker implements Runnable{
    private Farm farm;
    private int curTime;
    private PrintWriter writer;
    private boolean doWork=true;
    worker(String name, String host, int ohwPort, int minerPort, int type, String vallet,String uid){
        farm = new Farm(name, 1, host,minerPort,ohwPort,vallet,uid);

    }
    worker(Farm farm){
        this.farm = farm;

    }
    worker( String configFile, PrintWriter writer){
        File config = new File(configFile);
        JSONObject cfg = null;
        try{
            BufferedReader rd = new BufferedReader(new FileReader(config));
            cfg = new JSONObject(rd.readLine());
        } catch (IOException ex){
            System.out.println("Config File "+configFile+" not found\"");
        } catch (JSONException ej){
            System.out.println("Error parsing JSON data from farm.cfg\n"+ej.getLocalizedMessage());
        }
        farm = new Farm(cfg.getString("Name"),
                        cfg.getInt("type"),
                        cfg.getString("host"),
                        cfg.getInt("minerPort"),
                        cfg.getInt("ohwPort"),
                        cfg.getString("vallet"),
                        cfg.getString("uid"));
        if(cfg.has("gpuIndexes")){
            farm.setIndex(cfg.getString("gpuIndexes"));
        }
        this.writer=writer;
        this.doWork=true;

    }
    @Override
    public void run() {
        System.out.println("Запущена нить контроля и опроса фермы");
        writer.println(farm.getUid());
        writer.flush();
        while(doWork){
            curTime = (int)( System.currentTimeMillis() / 1000);
            //System.out.println("_____________________________________________________\n\nTimestamp: "+curTime+"\n");
            farm.loadOhw();
            farm.getMinerStat();
          //  System.out.println(farm.getData());
            try{
                writer.println(farm.getJString());
                writer.flush();
                Thread.sleep(60000);
            } catch (InterruptedException ex){
                System.out.println("Поток прерван");
            }
        }
        System.out.println("Нить контроля и опроса остановлена");
    }
    public void sendPing(){
            writer.println("PING_OK");
            writer.flush();

    }
    public Farm getFarm(){
        return this.farm;
    }
}

class pingCheck implements Runnable{
    private  BufferedReader reader;
    private boolean doWork = true;
    pingCheck(BufferedReader reader){
        this.reader=reader;
    }
    @Override
    public void run(){
        System.out.println("Запущена нить проврки связи ");
        String message="";
        while(doWork){
           try{
               message = reader.readLine();
           } catch (IOException ex){
               this.doWork=false;
               System.out.println("Ping recive error, need restart "+ ex.getMessage());
           }
            if(message.equals("PING")){
                jmmClient.sendPing();
                System.out.println("PING from server OK");
            } else {
                System.out.println(message);
            }
        }
        System.out.println("Завершена нить контроля связи ");
    }
}