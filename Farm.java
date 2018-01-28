package ru.amNemox;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.TreeMap;

public class Farm {
    private String uid;
    private TreeMap<Integer,String> SPEED_UNITS = new TreeMap<>();
    private String speedUnit;
    private String vallet;
    private String reIndex;
    private boolean needReindex=false;
    private int type;
    private String host="";
    private int minerPort;
    private int ohwPort;
    private String name ;
    private String cpu_model;
    private int cpu_cores;
    private float cpu_p_power;
    private float cpu_p_temp;
    private TreeMap<Integer, Float> cpu_temp = new TreeMap<>();
    private int gpu_num=0;
    private TreeMap<Integer,String> gpu = new TreeMap<>();
    private TreeMap<Integer, Float> gpu_temp = new TreeMap<>();
    private TreeMap<Integer, Integer> gpu_core_clk = new TreeMap<>();
    private TreeMap<Integer, Integer> gpu_mem_clk = new TreeMap<>();
    private TreeMap<Integer, Integer> gpu_fan = new TreeMap<>();
    private TreeMap<Integer, Float> gpu_temp_m = new TreeMap<>();
    private TreeMap<Integer, Integer> gpu_fan_m = new TreeMap<>();
    private TreeMap<Integer, Integer> gpu_sharesA_m = new TreeMap<>();
    private TreeMap<Integer, Integer> gpu_sharesR_m = new TreeMap<>();
    private TreeMap<Integer, Integer> gpu_power_m = new TreeMap<>();
    private TreeMap<Integer, Float> gpu_speed_m = new TreeMap<>();
    private TreeMap<Integer, GPU> cGpu = new TreeMap<>();
    private float mem_total;
    private float mem_used;
    private float mem_free;
    private TreeMap<Integer,Float> hhd_space = new TreeMap<>();

    Farm(String _name,int _type ,String valet){
        SPEED_UNITS.put(1,"Sol/s");
        SPEED_UNITS.put(2,"MH/s");
        SPEED_UNITS.put(3,"Kh/s");
        this.name=_name;
        this.type = _type;
        this.speedUnit = this.SPEED_UNITS.get(_type);
        switch (_type){
            case 1:
                this.minerPort=42000;
                break;
            case 2:
                this.minerPort=3333;
                break;
            default:
                this.minerPort=0;
                break;

        }
        this.ohwPort=8085;
        this.host="127.0.0.1";
        this.vallet = valet;
    }
    Farm(String _name,int _type,String host, int minerPort,int ohwPort,String vallet,String uid ){
        SPEED_UNITS.put(1,"Sol/s");
        SPEED_UNITS.put(2,"MH/s");
        SPEED_UNITS.put(3,"Kh/s");
        this.name=_name;
        this.type = _type;
        this.speedUnit = this.SPEED_UNITS.get(_type);
        this.minerPort=minerPort;
        this.ohwPort=ohwPort;
        this.host=host;
        this.vallet = vallet;
        this.uid = uid;
    }
    public void reIndex(){
        reIndex(this.reIndex);
    }
    public void reIndex(String index){
        if(this.gpu_num > 0){
            String[] indArr_s = index.split(",");
            int[] indArr_i = new int[gpu_num];
            for(int i=0;i<gpu_num;i++){
                indArr_i[i]=Integer.parseInt(indArr_s[i]);
            }
            for(int i=0;i<gpu_num;i++){
//        GPU(String name, int CoreClc, int memClk,float temp_o, float temp_m,int fan_0,int fan_m, float spped_m, int accepted_shares, int rejected_shares, int gpu_power ) {
                cGpu.put(i,
                        new GPU(   gpu.get(indArr_i[i]),
                                (gpu_core_clk.containsKey(i))?gpu_core_clk.get(indArr_i[i]):0,
                                (gpu_mem_clk.containsKey(i))?gpu_mem_clk.get(indArr_i[i]):0,
                                (gpu_temp.containsKey(i))?gpu_temp.get(indArr_i[i]):0,
                                (gpu_temp_m.containsKey(i))?gpu_temp_m.get(i):0,
                                (gpu_fan.containsKey(i))?gpu_fan.get(indArr_i[i]):0,
                                (gpu_fan_m.containsKey(i))?gpu_fan_m.get(i):0,
                                (gpu_speed_m.containsKey(i))?gpu_speed_m.get(i):0,
                                gpu_sharesA_m.getOrDefault(i,0),
                                gpu_sharesR_m.getOrDefault(i,0),
                                gpu_power_m.getOrDefault(i,0)));
            }
        }
    }
    public void setIndex(String index){
        this.reIndex = index;
        this.needReindex=true;
    }
    public boolean needreIndex(){return this.needReindex;}
    //Miner statictic functions
    public void getMinerStat(){
        getMinerStat(this.host,this.minerPort);
    }
    public void getMinerStat(String host, int port){
        JSONObject jMine = new JSONObject();
        JSONArray stat;// = new JSONArray();
        switch(type){
            case 1:
                //ZEC EWB
                try {
                    jMine =  JsonReader.readJsonFromUrl("http://"+host+":"+String.valueOf(port)+"/getstat");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                stat = jMine.getJSONArray("result");
                //String poolname = jMine.getString("current_server");
                //int starttime = jMine.getInt("start_time");
                for(int i=0;i<stat.length();i++){
                    try {
                        JSONObject jGpu = stat.getJSONObject(i);
//                        System.out.println(jGpu.toString());
                        gpu_power_m.put(i,jGpu.getInt("gpu_power_usage"));
                        gpu_speed_m.put(i,(float) jGpu.getInt("speed_sps"));
                        gpu_sharesA_m.put(i,jGpu.getInt("accepted_shares"));
                        gpu_sharesR_m.put(i,jGpu.getInt("rejected_shares"));
                        gpu_temp_m.put(i,(float) jGpu.getInt("temperature"));

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                //          System.out.println(1);
                break;
            case 2:
                try {
                    System.out.println(2);
                    String request = "{\"id\":0,\"jsonrpc\":\"2.0\",\"method\":\"miner_getstat1\"}\n";
                    Socket socket = new Socket(host, port);
                    PrintWriter writer = new PrintWriter(socket.getOutputStream());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    writer.println(request);
                    writer.flush();
                    String out = reader.readLine();
                    jMine = new JSONObject(out);
                } catch (IOException ex){
                    ex.printStackTrace();
                }
                stat = jMine.getJSONArray("result");
                String result = stat.toString();
                String[] sRes = result.split(",");

                for(int i=0; i < sRes.length;i++){

                    String filtred = sRes[i].replaceAll("\"",";");
                    String[] s2Res = filtred.split(";");
                    System.out.print(i+":::: ");
                    for(int k=0;k<s2Res.length;k++){
                        System.out.print(k+":"+s2Res[k].toString() + "; ");
                    }
                    switch (i){
                        case 0:
                            //miner version and mode
                            break;
                        case 1:
                            //uptime in minutes
                            break;
                        case 2:
                            //Total share stat. 1: Accepted 2:
                            break;
                        case 3:
                            //Speed in Kh. /1000 for float in MH/s, lenth=GPU Count
                            break;
                        case 6:
                            //Temp-Fan pars. n=n GPU Temp, n+1=n GPU Fan
                            break;
                        case 7:
                            //pool address
                            break;
                    }
                    //  System.out.println();
                }
                break;
        }
    }

    //OpenHardwareMonitor
    public void loadOhw(){
        loadOhw(this.host,this.ohwPort);
    }
    public void loadOhw(String host, int port){
        JSONObject ohwStat = new JSONObject();
        TreeMap<String,String> parsed = new TreeMap<>();
        try{
            ohwStat = JsonReader.readJsonFromUrl("http://"+host+":"+port+"/data.json");
        } catch (IOException ex){
            System.out.println("Exeption in load OHW stat:\n"+ex.getMessage());
        }
        tryParse(ohwStat,"",parsed);
        this.load_ohw(parsed);
    }
    public void tryParse(JSONObject json, String curWork,TreeMap<String,String> parsed){
        JSONObject intJson;
        JSONArray   intArray;
        int atiCnt=0,nvCnt=0,gpuCnt=0,hddCnt=0;
        if(json.length()>0){
            try{
                intArray = json.getJSONArray("Children");
                for(int k=0;k<intArray.length();k++){
                    intJson = intArray.getJSONObject(k);
                    if(intJson.get("ImageURL").equals("images_icon/computer.png")){
                        parsed.put("cpu_name",intJson.get("Text").toString());
                    } else if(intJson.get("ImageURL").equals("images_icon/mainboard.png")){
                        parsed.put("motherboard",intJson.get("Text").toString());
                        curWork = "mb_";
                    } else if(intJson.get("ImageURL").equals("images_icon/cpu.png")){
                        parsed.put("cpu_model",intJson.get("Text").toString());
                        curWork = "cpu_";
                    } else if(intJson.get("ImageURL").equals("images_icon/ati.png")){
                        parsed.put("gpu"+gpuCnt+"_model",intJson.get("Text").toString());
                        parsed.put("ati"+atiCnt+"_model",intJson.get("Text").toString());
                        parsed.put("gpu"+gpuCnt+"_id",intJson.get("id").toString());
                        curWork = "gpu_"+gpuCnt;
                        atiCnt++;gpuCnt++;
                    } else if(intJson.get("ImageURL").equals("images_icon/nvidia.png")){
                        parsed.put("gpu"+gpuCnt+"_model",intJson.get("Text").toString());
                        parsed.put("nv"+nvCnt+"_model",intJson.get("Text").toString());
                        parsed.put("gpu"+gpuCnt+"_id",intJson.get("id").toString());
                        curWork = "gpu_"+gpuCnt;
                        nvCnt++;gpuCnt++;
                    }else if(intJson.get("ImageURL").equals("images_icon/hdd.png")){
                        parsed.put("hdd"+hddCnt+"_model",intJson.get("Text").toString());
                        parsed.put("hdd"+hddCnt+"_id",intJson.get("id").toString());
                        curWork = "hdd_"+hddCnt;
                        hddCnt++;
                    }else{
                        String key = curWork + ":"+intJson.get("Text").toString();
                        int keymod=0;
                        while(parsed.containsKey(key)){
                            keymod++;
                            key = curWork + ":"+intJson.get("Text").toString() + "_"+keymod;
                        }
                        parsed.put(key,intJson.get("Value").toString().replaceAll(",","."));
                    }
                    tryParse(intJson, curWork,parsed);
                }
            } catch (Exception ex){
                ex.printStackTrace();
            }
        }
        if(hddCnt > 0 )parsed.put("total_hhd",String.valueOf(hddCnt));
        if(gpuCnt > 0 )parsed.put("total_gpu",String.valueOf(gpuCnt));
        if(atiCnt > 0 )parsed.put("ati_gpu",String.valueOf(atiCnt));
        if(nvCnt > 0 )parsed.put("nv_gpu",String.valueOf(nvCnt));
    }
    public void load_ohw(TreeMap<String,String> ohw){
        if(ohw.containsKey("total_gpu")){
            gpu_num = Integer.parseInt(ohw.get("total_gpu"));
            for(int i=0;i<gpu_num;i++){
                //GPU work = new GPU();
                try {
                    gpu.put(i, ohw.get("gpu" + i + "_model"));


                    if(ohw.containsKey("ati_gpu")) {                                          //gpu_1:GPU Fan
                        gpu_fan.put(i, Integer.parseInt(ohw.get("gpu_" + i + ":GPU Fan_1").replaceAll("[^0-9\\,]", ""))/10);
                        gpu_core_clk.put(i, Integer.parseInt(ohw.get("gpu_" + i + ":GPU Core_1").replaceAll("[^0-9\\,]", "")));
                        gpu_temp.put(i, Float.parseFloat(ohw.get("gpu_" + i + ":GPU Core_2").replaceAll("[^0-9\\,]", "")) / 10);
                    }
                    if(ohw.containsKey("nv_gpu")){
                        gpu_fan.put(i, Integer.parseInt(ohw.get("gpu_" + i + ":GPU Fan").replaceAll("[^0-9\\,]", ""))/10);
                        gpu_core_clk.put(i, Integer.parseInt(ohw.get("gpu_" + i + ":GPU Core").replaceAll("[^0-9\\,]", "")));
                        gpu_temp.put(i, Float.parseFloat(ohw.get("gpu_" + i + ":GPU Core_1").replaceAll("[^0-9\\,]", "")) / 10);
                    }
                    gpu_mem_clk.put(i, Integer.parseInt(ohw.get("gpu_" + i + ":GPU Memory").replaceAll("[^0-9\\,]", "")));
                } catch (Exception ex){
                    ex.printStackTrace();
                }

            }
        }
        if(ohw.containsKey("cpu_model")){
            this.cpu_model = ohw.get("cpu_model");
        }
        //cpu_p_power;cpu_:CPU Package_1
        if(ohw.containsKey("cpu_:CPU Package_1")){
            this.cpu_p_power = Float.parseFloat(ohw.get("cpu_:CPU Package_1").replaceAll("[^0-9\\,]", ""))/10;
        }
        //cpu_p_temp;cpu_:CPU Package
        if(ohw.containsKey("cpu_:CPU Package")){
            this.cpu_p_temp = Float.parseFloat(ohw.get("cpu_:CPU Package").replaceAll("[^0-9\\,]", ""))/10;
        }
        //mem_total;cpu_:Available Memory
        if(ohw.containsKey("cpu_:Available Memory")){
            this.mem_total = Float.parseFloat(ohw.get("cpu_:Available Memory").replaceAll("[^0-9\\,]", ""))/10;
        }
        //mem_used;cpu_:Used Memory
        if(ohw.containsKey("cpu_:Used Memory")){
            this.mem_used = Float.parseFloat(ohw.get("cpu_:Used Memory").replaceAll("[^0-9\\,]", ""))/10;
        }
        //TreeMap<Integer,Float> hhd_space; hdd_0:Used Space
        if(ohw.containsKey("total_hdd")){
            for(int i=0;i<Integer.parseInt(ohw.get("total_hdd"));i++){
                hhd_space.put(i,Float.parseFloat(ohw.get("hdd_"+i+":Used Space").replaceAll("[^0-9\\,]", "")));
            }
        }
    }
    //Get Miner stat functions
    public void getPoolWorker(){
        // /miner/:miner/worker/:worker/currentStats
        JSONObject poolJson = new JSONObject();
        try{
            poolJson = JsonReader.readJsonFromUrl("https://api-zcash.flypool.org/miner/"+this.vallet+"/workers/");
            //poolJson = JsonReader.readJsonFromUrl("https://api-zcash.flypool.org/miner/"+this.vallet+"/worker/"+name+"/currentStats");
        } catch (IOException ex){
            System.out.println("Error reading JSON miner current status from pool\n"+ex.getMessage());
        } finally {
            System.out.println(poolJson);
        }
    }

    //OUT DATA Functions
    public String getData() {
        //return String data of farm
        StringBuilder tRet = new StringBuilder(name + "\n"+"CPU: "+cpu_model+"( "+cpu_cores+" Cores)" + "Temp: " + cpu_p_temp + "C; CPU Power: " + cpu_p_power + "W" +
                "\n RAM: Used: " + mem_used + " / Free " + mem_total + "; HDD: " + hhd_space.toString());
        for(int i=0;i<gpu_num;i++){
            tRet.append("\n"+cGpu.get(i).toString());
        }
        return tRet.toString();
        /*return name + "\n"+"CPU: "+cpu_model+"( "+cpu_cores+" Cores)" + "Temp: " + cpu_p_temp + "C; CPU Power: " + cpu_p_power + "W" +
            "\n RAM: Used: " + mem_used + " / Free " + mem_total + "; HDD: " + hhd_space.toString() +
            "\nGPU: "+gpu_num+":"+gpu.toString()+
            "\nGPU Core CLK " + gpu_core_clk.toString() +
            "\nGPU MEM CLK "+gpu_mem_clk.toString()+
            "\nGPU Core TEMP" + gpu_temp.toString()+
            "\nGPU FAN "+gpu_fan.toString()+
            "\nMine Speed:"+gpu_speed_m.toString()+
            "\n Accepted Shares: "+gpu_sharesA_m.toString()+
            "\n Rejected Shares: "+gpu_sharesR_m.toString()+
            "\n GPU Power Data: "+gpu_power_m.toString();*/
    }
    public double getCpuTemp(int cpu_core){
        if(cpu_core >0 && cpu_core <= this.cpu_cores)
            return cpu_temp.get(cpu_core);
        else return -1f;
    }
    public double getGpuTemp(int gpu_num){
        if(gpu_num >0 && gpu_num <= this.gpu_num)
            return gpu_temp.get(gpu_num);
        else return -1f;
    }
    public int getGpuFan(int gpu_num){
        if(gpu_num >0 && gpu_num <= this.gpu_num)
            return gpu_fan.get(gpu_num);
        else return -1;
    }
    public String getJString(){
        StringBuilder tRet = new StringBuilder("{\"level\":0,\"uid\":\""+uid+"\",\"name\":\""+name+"\",\"type\":"+type+",\"cpu_model\":\""+cpu_model+"\",\"cpu_temp\":"+cpu_p_temp+",\"cpu_power\":"+cpu_p_power+",\"mem_used\":"+  mem_used +",\"mem_free\":"+mem_total+",\"gpu_count\":"+gpu_num+",\"gpu_array\":[");
        for(int i=0;i<gpu_num;i++){
            tRet.append("{\"gpu_id\":"+i+",");
            tRet.append(cGpu.get(i).getjString());
            tRet.append("}");
            if(i!=gpu_num-1) tRet.append(",");
        }
        tRet.append("]}");


        return tRet.toString();
    }

    public String getUid() {
        return uid;
    }
}

class GPU {
    String name;
    int coreClk;
    int memClk;
    double temp_o;
    double temp_m;
    int fan_o;
    int fan_m;
    double speed_m;
    int ok_shares;
    int bad_shares;
    int gpu_power;
    GPU(String name, int CoreClc, int memClk,double temp_o, double temp_m,int fan_0,int fan_m, double spped_m, int accepted_shares, int rejected_shares, int gpu_power ) {
        this.name = name;
        this.coreClk = CoreClc;
        this.memClk = memClk;
        this.temp_o = temp_o;
        this.temp_m = temp_m;
        this.fan_o = fan_0;
        this.fan_m = fan_m;
        this.speed_m = spped_m;
        this.ok_shares = accepted_shares;
        this.bad_shares = rejected_shares;
        this.gpu_power = gpu_power;
    }
    @Override
    public String toString(){
        return "GPU: "+name+"; Core temp (OHW/Miner): "+temp_o+"/"+temp_m+"; FAN(OHW/Miner) "+fan_o+"/"+fan_m+"; Clocks Core/Mem: "+coreClk+"/"+memClk+"\n"+
                "Mine speed: "+speed_m+" at power "+gpu_power + "; Shares accepted/rejected: "+ok_shares+"/"+bad_shares;

    }
    public JSONObject getJson(){
        return new JSONObject("{\"test\":\"ok\"}");
    }
    public String getJsonS(){
        return getJson().toString();
    }
    public String getjString(){
        return "\"name\":\""+name+"\",\"temp_o\":"+temp_o+",\"temp_m\":"+temp_m+",\"fan_o\":"+fan_o+",\"fan_m\":"+fan_m+",\"coreClk\":"+coreClk+",\"memClk\":"+memClk+",\"speed\":"+speed_m+",\"power\":"+gpu_power + ",\"a_share\":"+ok_shares+",\"r_share\":"+bad_shares+"";

    }
}

class JsonReader {

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public static JSONObject  readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            JSONObject json = new JSONObject(jsonText);
            return json;
        } finally {
            is.close();
        }
    }

}

class jConfig{
    private StringBuilder jStr= new StringBuilder();
    jConfig(String configFileName){
        try{
            List<String> lines =
                    Files.readAllLines(Paths.get(configFileName), StandardCharsets.UTF_8);
            String[] strings = new String[lines.size()];
            strings = lines.toArray(strings);
            for(String line : strings){
                jStr.append(line);
            }
        }  catch (IOException eI){
            System.out.println("Ошибка чтения файла конфигурации "+configFileName+": "+eI.getMessage());
            return;
        } catch (JSONException eJ){
            System.out.println("Ошибка синтаксиса файла конфигурации "+configFileName+": "+eJ.getMessage()+"\n Содержимое: "+jStr.toString());
        }

    }
    public JSONObject getJson(){
        return new JSONObject(jStr);
    }

    public String getjString() {
        return jStr.toString();
    }
}