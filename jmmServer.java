package ru.amNemox;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

public class jmmServer {
    static JSONObject CONFIG;
    private static tMysql mySrv;

    public static tMysql getMySrv(){
        return mySrv;
    }
    private static HashMap<Integer,srvFarm> clients = new HashMap<>();


    public static void close(int id, srvFarm Farm){
        remove(id);
        Farm.close();
    }
    public static void close(int id){
        if(clients.containsKey(id)) clients.get(id).close();
        remove(id);
    }
    public static void remove(int id){
        if(clients.containsKey(id)) clients.remove(id);
    }
    public static void main(String[] args) {

        new jmmServer();

    }

    jmmServer(){
        int clientCount = 0;
        jConfig cfg = new jConfig("server.txt");
        System.out.println(cfg.getjString());
        CONFIG = new JSONObject(cfg.getjString());
//    tMysql(String mysqlUser, String mysqlPass, String mysqlDbName, String mysqlHost, int mysqlPort){
        mySrv = new tMysql(
                CONFIG.getString("mysqlUser"),
                CONFIG.getString("mysqlPass"),
                CONFIG.getString("mysqlDbName"),
                CONFIG.getString("mysqlHost"),
                CONFIG.getInt("mysqlPort")
                );
        System.out.println("Server start");

        try (ServerSocket server = new ServerSocket(CONFIG.getInt("serverPort"))) {
            while (true) {
                Socket socket = server.accept();
                System.out.println("#" + (++clientCount) + " Connected");
                PrintWriter writer = new PrintWriter(socket.getOutputStream());
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String helloMsg = reader.readLine();
                clients.put(clientCount,new srvFarm(reader,writer, clientCount,helloMsg));
                clients.get(clientCount).prepare();
                new Thread(clients.get(clientCount).getCheck()).start();
                new Thread(clients.get(clientCount).getRecive()).start();
            }
        } catch (Exception ex) {
            System.out.println("Some server error in main class try "+ex.getMessage());
            ex.printStackTrace();
        }
        System.out.println("Server stop ");
    }
}

class checkThread extends  srvThread implements Runnable{
    PrintWriter writer;
    checkThread(PrintWriter writer, int clientCount) {
        System.out.println("#WT: Created new ping check thread for client "+clientCount);
        try {
            this.writer = writer;
            name = "Client #" + clientCount;
            id = clientCount;
        } catch(Exception ex) {
            System.out.println("#WT: Error creating ping check for client "+id+" : "+ex.getMessage());
        }
        doWork=true;
    }
    @Override
    public void run() {
        System.out.println("#WT: Started new ping check thread for client "+id);
        owner.setLastSeen(System.currentTimeMillis());
        do {
            writer.println("PING");
            writer.flush();
            System.out.println("#WT: Send ping to client "+id);
            try{
                Thread.sleep(10000);
            } catch (InterruptedException eInt){
                System.out.println("#WT: Sleep thread interrupted"+eInt.getLocalizedMessage());
            }
            if((System.currentTimeMillis() - owner.getLastSeen())>30000){
                owner.sendAlert("#WT: Farm ping check fail");
                jmmServer.close(id);
            }
        } while(doWork);
        jmmServer.close(id);
        // System.out.println(id + ":"+name + " close Check Thread;" );
    }

}
class reciveThread extends srvThread implements Runnable{

    BufferedReader reader;

    reciveThread(BufferedReader reader, int clientCount) {
        System.out.println("#RT: New revice thread created for client"+clientCount);
        try {
            this.reader = reader;
            name = "Client #" + clientCount;
            id = clientCount;
        } catch(Exception ex) {
            System.out.println("#RT: Error creating new revice thread for client "+id+" : "+ ex.getMessage());
        }
        lastPing = System.currentTimeMillis();
        doWork=true;
    }



    @Override
    public void run() {
        String message;
        System.out.println("#RT: Recive thread for client "+id+" started ");
        do {
            try{
                message = reader.readLine();
                if (message.equals("PING_OK")) {
                    lastPing = System.currentTimeMillis();
                    owner.setLastSeen(lastPing);
                    System.out.println("#RT: Farm " + id + " ping ok, time updated");
                } else {
                    System.out.println("#RT: Recived farm data ");
                    owner.parse(message);
                }
            } catch (Exception ex) {
                System.out.println("#RT: Error in recive thread for client " + id + " : " + ex.getMessage());
                ex.printStackTrace();
                jmmServer.close(id);

            }
        }
        while(doWork);
        jmmServer.close(id);
        //  System.out.println(id + ":"+name + "Disconnected from revice thread" );
    }

}
abstract class srvThread{
    protected int id;
    protected String name;
    protected boolean doWork=true;
    protected long lastPing;
    protected srvFarm owner;
    srvThread(){
        id=0;
    }

    public void setLastPing(long lastPing) {
        this.lastPing = lastPing;
    }
    public long getLastPing() {
        return lastPing;
    }
    public srvFarm getOwner() {
        return owner;
    }
    public void setOwner(srvFarm owner) {
        this.owner = owner;
    }
    public void close(){
        this.doWork=false;
    }
    public int getId(){
        return id;
    }

}

class srvFarm {
    private tSender sender;
    private reciveThread recive;
    private checkThread  check;
    private long LastSeen;
    private tMysql mySrv;
    private farmStat farm= null;
    private ArrayList<fAlert> alerts = new ArrayList<>();
    private String owner="";
    private long ownerTChat=0l;
    srvFarm(BufferedReader reader, PrintWriter writer, int id, String uid){
        recive = new reciveThread(reader,id);
        check = new checkThread(writer,id);
        LastSeen = System.currentTimeMillis();
        this.connect_sql();
        try {
        ResultSet rs = mySrv.sQuery("SELECT `chatId` FROM jmm_tChats where `client`=(SELECT `client` FROM `jmm_client_farms` WHERE `farmId`='"+uid+"');");
            if(rs.next()) ownerTChat = rs.getLong("chatId");
        } catch (SQLException eSql){
            System.out.println("Ошибка инициализации sender'а - ошибка получения chatId "+eSql.getMessage());
        }
        this.sender = new tSender("tBot.txt",ownerTChat);
    }
    private boolean connect_sql(){
        mySrv = jmmServer.getMySrv();
        if(mySrv.getConnection() != null) return true;
        else return false;
    }
    public void prepare(){
        recive.setOwner(this);
        check.setOwner(this);
    }
    public void close(){
        System.out.println("#FARM: Close threads from owner Farm");
        sender.sendMsg("SRV: Ферма "+farm.name+":"+farm.id);
        check.close();
        recive.close();

    }
    public checkThread getCheck() {
        return check;
    }
    public reciveThread getRecive() {
        return recive;
    }
    public void sendAlert(String Alert){
        sender.sendMsg("ALERT: "+Alert);
    }
    public void parse(String inData){
        JSONObject inJSON = new JSONObject(inData);
        //System.out.println(inJSON.toString());
        int rTime = (int) System.currentTimeMillis()/1000;
        JSONArray aGpuJ = inJSON.getJSONArray("gpu_array");
        checkFarm(inJSON.getString("uid"),rTime);
        updateFarm(inJSON.getString("uid"),rTime,inJSON.getString("cpu_model"),inJSON.getString("name"));
        float farmSpeed=0;
        for(int i=0;i<aGpuJ.length();i++){
            //System.out.println(aGpuJ.getJSONObject(i));
            farmSpeed+=aGpuJ.getJSONObject(i).getDouble("speed");
            updateGpuStat(
                    inJSON.getString("uid"),
                    i,
                    rTime,
                    aGpuJ.getJSONObject(i).getString("name"),
                    (float) aGpuJ.getJSONObject(i).getDouble("temp_o"),
                    (float) aGpuJ.getJSONObject(i).getDouble("temp_m"),
                    (float) aGpuJ.getJSONObject(i).getDouble("fan_o"),
                    //aGpuJ.getJSONObject(i).getFloat("fan_o"),
                    (float) aGpuJ.getJSONObject(i).getDouble("fan_m"),
                    aGpuJ.getJSONObject(i).getInt("coreClk"),
                    aGpuJ.getJSONObject(i).getInt("memClk"),
                    (float) aGpuJ.getJSONObject(i).getDouble("power"),
                    (float) aGpuJ.getJSONObject(i).getDouble("speed"),
                    aGpuJ.getJSONObject(i).getInt("a_share"),
                    aGpuJ.getJSONObject(i).getInt("r_share")
            );
        }
        updateFramStat(
                inJSON.getString("uid"),
                rTime,
                inJSON.getInt("cpu_temp"),
                (float) inJSON.getDouble("cpu_power"),
                inJSON.getInt("gpu_count"),
                (double) inJSON.getDouble("mem_used"),
                (double) inJSON.getDouble("mem_free"),
                farmSpeed);

        if(farm == null) {
            farm = new farmStat(inJSON);
            alerts.add(0,new fAlert(false));
        }
        else alerts.addAll(farm.update(inJSON));
        if(alerts.get(0).isAlert()) for(fAlert alert : alerts){
            sender.sendMsg(alert.toString());
        }
        alerts.clear();
        //Вставить данные в jmm_farm_stat - INSERT INTO `jmm_farm_stat` VALUES ('uid','rTime','cpu_temp','cpu_power','gpu_count','mem_used','mem_free','speed');

        //Вставить данные в jmm_gpu_stat - INSERT INTO `jmm_gpu_stat` VALUES ('uid','gpu_id','tTime','name','temp_o','temp_m','fan_o','fan_m','coreClk','memClk','power','speed','a_share','r_share');
    }
    public ArrayList<fAlert> getAlerts(){
        return alerts;
    }
    private void checkFarm(String uid, int cTime ){
        String checkQuery = "SELECT * FROM `jmm_farms` WHERE `uid`=\""+uid+"\";";
        ResultSet rs = mySrv.sQuery(checkQuery);
        int numRows=0;
        try{
            rs.last();
            numRows =rs.getRow();
        } catch (SQLException eS){
            eS.printStackTrace();
        }
    }
    private void updateFarm(String uid, int cTime, String cpuModel, String name){
        String updateQuery = "UPDATE `jmm_farms` SET `lastSeen`="+cTime+",`cpu`=\""+cpuModel+"\",`name`=\""+name+"\";";
        mySrv.uQuery(updateQuery);
    }
    private void updateFramStat(String uid, int cTime, int cpuTemp, double cpuPower, int gpuCount, double memUsed, double memFree, double tSpeed){
        String insertQuery = "INSERT INTO `jmm_farm_stat` VALUES('"+uid+"','"+cTime+"','"+cpuTemp+"','"+cpuPower+"','"+gpuCount+"','"+memUsed+"','"+memFree+"','"+tSpeed+"');";
        mySrv.uQuery(insertQuery);
    }
    private void updateGpuStat(String farmUid, int gpuId, int cTime, String gpuName, double oTemp, double mTemp, double oFan, double mFan, int coreClk, int memClk, double power, double speed, int aShare, int rShare){
        String insertQuery = "INSERT INTO `jmm_gpu_stat` VALUES('"+farmUid+"','"+gpuId+"','"+cTime+"','"+gpuName+"','"+oTemp+"','"+mTemp+"','"+oFan+"','"+mFan+"','"+coreClk+"','"+memClk+"','"+power+"','"+speed+"','"+aShare+"','"+rShare+"');";
        mySrv.uQuery(insertQuery);

    }
    public long getLastSeen() {
        return LastSeen;
    }
    public void setLastSeen(long lastSeen) {
        LastSeen = lastSeen;
    }
}

class farmStat{
    //элемент 0 - среднее значение, элемент 1 - позапрошлое значение, элемент 2 - прошлое значение
    public String id;
    public String name;
    private int[] cpuTemp       = new int[3];
    private double[] cpuPower    = new double[3];
    private double[] memUsed     = new double[3];
    private double[] memFree     = new double[3];
    private double[] speed       = new double[3];
    private int gpuCount=0;
    private ArrayList<GPU> gpuLast = new ArrayList<>();
    private ArrayList<GPU> gpuMid = new ArrayList<>();
    farmStat(JSONObject jFarm){
        this.id = jFarm.getString("uid");
        this.name = jFarm.getString("name");
        JSONArray jGpu = jFarm.getJSONArray("gpu_array");
        loadInt(this.cpuTemp, jFarm.getInt("cpu_temp"));
        loadDouble(this.cpuPower,jFarm.getDouble("cpu_power"));
        loadDouble(this.memUsed,jFarm.getDouble("mem_used"));
        loadDouble(this.memFree,jFarm.getDouble("mem_free"));
        for(int i=0;i<jGpu.length();i++){
// GPU(String name, int CoreClc, int memClk,double temp_o, double temp_m,int fan_0,int fan_m, double spped_m, int accepted_shares, int rejected_shares, int gpu_power ) {
            //   if(jGpu.getJSONObject(i).optInt("level",2)==2) {
            gpuLast.add(new GPU(
                    jGpu.getJSONObject(i).getString("name"),
                    jGpu.getJSONObject(i).getInt("coreClk"),
                    jGpu.getJSONObject(i).getInt("memClk"),
                    jGpu.getJSONObject(i).getDouble("temp_o"),
                    jGpu.getJSONObject(i).getDouble("temp_m"),
                    jGpu.getJSONObject(i).getInt("fan_o"),
                    jGpu.getJSONObject(i).getInt("fan_m"),
                    jGpu.getJSONObject(i).getDouble("speed"),
                    jGpu.getJSONObject(i).getInt("a_share"),
                    jGpu.getJSONObject(i).getInt("r_share"),
                    jGpu.getJSONObject(i).getInt("power")
            ));
            gpuMid.add(new GPU(
                    jGpu.getJSONObject(i).getString("name"),
                    jGpu.getJSONObject(i).getInt("coreClk"),
                    jGpu.getJSONObject(i).getInt("memClk"),
                    jGpu.getJSONObject(i).getDouble("temp_o"),
                    jGpu.getJSONObject(i).getDouble("temp_m"),
                    jGpu.getJSONObject(i).getInt("fan_o"),
                    jGpu.getJSONObject(i).getInt("fan_m"),
                    jGpu.getJSONObject(i).getDouble("speed"),
                    jGpu.getJSONObject(i).getInt("a_share"),
                    jGpu.getJSONObject(i).getInt("r_share"),
                    jGpu.getJSONObject(i).getInt("power")
            ));

            //      }
        }
    }
    private void loadInt(int[] dInt,int value){
        for(int i=0;i<dInt.length;i++){
            dInt[i]=value;
        }
    }
    private void loadDouble(double[] dFloat, double value){
        for(int i=0;i<dFloat.length;i++){
            dFloat[i]=value;
        }
    }
    public ArrayList<fAlert> update(JSONObject newData){
        //Проврка соотношения со средним значением параметра. Если сильная разница:
        // - Проверка соотношения с прошлым параметром. >= Проверка с позапрошлым значением. Если позапрошлое нормальное - генерация тревоги, иначе - тревога уже сгенерирована
        fAlert alert = new fAlert(false);
        ArrayList<fAlert> alerts = new ArrayList<>();
        GPU tGPU;
        if(this.cpuTemp[0]/newData.getInt("cpu_temp")<0.8){
            //нагрев проца
            int curTemp = newData.getInt("cpu_temp");
            if(curTemp-cpuTemp[2]<=3 && curTemp - cpuTemp[1] > 3){
                alerts.add(new fAlert("CPU: ", "Температура проца растёт"+curTemp));
                alert.setAlert(true);
            }
        }
        if(this.cpuPower[0]/newData.getDouble("cpu_power")<0.8){
            //рост загрузки проца
            double curCp = newData.getDouble("cpu_power");
            if(curCp-cpuPower[2]<=3 && curCp - cpuPower[1] > 3){
                alerts.add(new fAlert("CPU: ", "ЭП процессора увеличивается "+curCp));
                alert.setAlert(true);
            }

        }
        if(this.memUsed[0]/newData.getDouble("mem_used")<0.8){
            //возможна утечка памяти
            double curMemU = newData.getDouble("mem_used");
            if(curMemU-memUsed[2]<=1 && curMemU - memUsed[1] > 1){
                alerts.add(new fAlert("MEM: ", "Растёт загрузка памяти"));
                alert.setAlert(true);
            }

        }
        if(newData.getDouble("mem_free")<1 & memFree[2]<1 & memFree[1]>1 & memFree[0]>1){
            alerts.add(new fAlert("MEM: ", "Осталось меньше 1Гб свободной ОП"));
            alert.setAlert(true);

        }
        for(int i=0;i<newData.getJSONArray("gpu_array").length();i++){
            tGPU = new GPU(
                    newData.getJSONArray("gpu_array").getJSONObject(i).getString("name"),
                    newData.getJSONArray("gpu_array").getJSONObject(i).getInt("coreClk"),
                    newData.getJSONArray("gpu_array").getJSONObject(i).getInt("memClk"),
                    newData.getJSONArray("gpu_array").getJSONObject(i).getDouble("temp_o"),
                    newData.getJSONArray("gpu_array").getJSONObject(i).getDouble("temp_m"),
                    newData.getJSONArray("gpu_array").getJSONObject(i).getInt("fan_o"),
                    newData.getJSONArray("gpu_array").getJSONObject(i).getInt("fan_m"),
                    newData.getJSONArray("gpu_array").getJSONObject(i).getDouble("speed"),
                    newData.getJSONArray("gpu_array").getJSONObject(i).getInt("a_share"),
                    newData.getJSONArray("gpu_array").getJSONObject(i).getInt("r_share"),
                    newData.getJSONArray("gpu_array").getJSONObject(i).getInt("power")
            );
            //тут слегка иная логика проверки - просто отличие от прошлого и среднего. Кроме скорости - там бывают сбои, когда она пляшет постоянно, что приведёт к потоку алертов.
            if(gpuMid.get(i).temp_o/tGPU.temp_o < 0.8){
                //проверить с прошлым. Если прошлое примерно такое-же - тревога уже есть, если расходжение аналогично - генерить тревогу
                if(gpuLast.get(i).temp_o/tGPU.temp_o<0.90){
                    //no alert
                } else {
                    alerts.add(new fAlert("GPU: "+i,"Рост температуры по OHW, сейчас "+tGPU.temp_o));
                    alert.setAlert(true);
                }
            }
            if(gpuMid.get(i).temp_m/tGPU.temp_m < 0.8){
                //проверить с прошлым. Если прошлое примерно такое-же - тревога уже есть, если расходжение аналогично - генерить тревогу
                if(gpuLast.get(i).temp_m/tGPU.temp_m<0.90){
                    //no alert
                } else {
                    alerts.add(new fAlert("GPU: "+i,"Рост температуры по майнеру, сейчас "+tGPU.temp_m));
                    alert.setAlert(true);
                }
            }
            if((tGPU.temp_o>80 | tGPU.temp_m>80) & (gpuLast.get(i).temp_o<78 | gpuLast.get(i).temp_m<78)){
                alerts.add(new fAlert("GPU: "+i,"Критическая температура GPU "+tGPU.temp_m));
                alert.setAlert(true);

            }
            if(tGPU.bad_shares/tGPU.ok_shares>0.05 & gpuMid.get(i).bad_shares/gpuMid.get(i).ok_shares<=0.05){
                alerts.add(new fAlert("GPU: "+i,"Ошибочные шары больше 5% "+tGPU.temp_m));
                alert.setAlert(true);
            }
            gpuMid.get(i).ok_shares = (int) (gpuMid.get(i).ok_shares + gpuLast.get(i).ok_shares + tGPU.ok_shares)/3;
            gpuMid.get(i).bad_shares = (int) (gpuMid.get(i).bad_shares + gpuLast.get(i).bad_shares + tGPU.bad_shares)/3;
            gpuMid.get(i).temp_o = (int)  (gpuMid.get(i).temp_o + gpuLast.get(i).temp_o + tGPU.temp_o)/3;
            gpuMid.get(i).temp_m = (int)  (gpuMid.get(i).temp_m + gpuLast.get(i).temp_m + tGPU.temp_m)/3;

            gpuLast.set(i,tGPU);
        }

        //обновляем последние, предпоследние и средние значения
        cpuTemp[0]=(int) (cpuTemp[0]+cpuTemp[1]+cpuTemp[2]+newData.getInt("cpu_temp"))/4;
        cpuTemp[1] = cpuTemp[2];
        cpuTemp[2] = newData.getInt("cpu_temp");

        cpuPower[0] = (cpuPower[0]+cpuPower[1]+cpuPower[2]+newData.getDouble("cpu_power")/4);
        cpuPower[1] = cpuPower [2];
        cpuPower[2] = newData.getDouble("cpu_power");

        memFree[0] = (memFree[0]+memFree[1]+memFree[2]+newData.getDouble("mem_free"))/4;
        memFree[1]=memFree[2];
        memFree[2]=newData.getDouble("mem_free");

        memUsed[0] = (memUsed[0]+ memUsed[1]+memUsed[2]+newData.getDouble("mem_used"))/4;
        memUsed[1]=memUsed[2];
        memUsed[2]=newData.getDouble("mem_used");
        alerts.add(0,alert);
        return alerts;
    }


}

class fAlert{
    private boolean isAlert=false;
    private String text;
    private String source;
    fAlert(String source, String text){
        isAlert=true;
        this.text = text;
        this.source = source;
    }
    fAlert(boolean isAlert){
        this.isAlert=false;
    }
    public void makeAlert(String source,  String text){
        isAlert=true;
        this.text = text;
        this.source = source;
    }
    public boolean isAlert(){
        return this.isAlert;
    }
    public void setAlert(boolean alert) {
        isAlert = alert;
    }
    @Override
    public String toString(){
        return this.source+":"+this.text;
    }
}

