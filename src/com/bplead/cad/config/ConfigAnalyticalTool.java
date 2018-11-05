package com.bplead.cad.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import priv.lee.cad.util.StringUtils;
import wt.log4j.LogR;


public class ConfigAnalyticalTool {
    
    private static final String fileName =  "RealtimeConfig.properties";
    private static final Properties prop = new Properties();
    private static long modify;
    private static Map<String, Long> map = new HashMap<String, Long>();
    private static Map<String, Properties> mapP = new HashMap<String, Properties>();
    private static Logger logger = LogR.getLogger(ConfigAnalyticalTool.class.getName());
    
    private static void initProperties() {
        try {
            File configFile = new File(ConfigAnalyticalTool.class.getResource(fileName).getPath());
            long lastModified = configFile.lastModified();
//            logger.debug(modify + " \t " + lastModified);
            if(modify == 0 || modify != lastModified)
            {
                modify = lastModified;
                String path = ConfigAnalyticalTool.class.getResource(fileName).getPath();
                InputStream is = new FileInputStream(path);
                prop.load(new InputStreamReader(is, "UTF-8"));
                is.close();
                logger.debug(">>>>>>>>>>>加载配置文件["+fileName+"]>>>>>>>>>"+prop);
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.debug("解析properties配置文件出错");
        }
    }
    
    /**
     * 得到指定配置文件
     * @param filename 文件名路径
     * @param flag true:取与当前类同目录下的配置文件只传文件名即可,如：RealtimeConfig.properties,false:要传相对于src下的配置文件路径,如：ext/csr/config/doctypes.properties
     */
    private static void initProperties(String filename,boolean flag) {
        try {
            String path = "";
            if (flag) {
                path = ConfigAnalyticalTool.class.getResource(filename).getPath();
            } else {
                path = ConfigAnalyticalTool.class.getClassLoader().getResource(filename).getPath();
            }
            File configFile = new File(path);
            long lastModified = configFile.lastModified();
            boolean loadFlag=false;
//            logger.debug(modify + " \t " + lastModified + " map is -> " + map);
            if(map.containsKey(filename))
            {
                long modify = map.get(filename);
                if(modify!=lastModified)
                {
                    loadFlag=true;
                    map.put(filename, lastModified);
                }
            }
            else{
                loadFlag=true;
                map.put(filename, lastModified);
            }
//            logger.debug(modify + " \t " + lastModified + " loadFlag is -> " + loadFlag  + " map is -> " + map);
            if (loadFlag) {
                    Properties prop = new Properties();
                    InputStream is = new FileInputStream(path);
                    prop.load(new InputStreamReader(is, "UTF-8"));
                    is.close();
                    mapP.remove(filename);
                    mapP.put(filename, prop);
                    logger.debug(">>>>>>>>>>>加载配置文件["+filename+"]>>>>>>>>> " + map);
                }
        } catch (IOException e) {
            e.printStackTrace();
            logger.debug("解析properties配置文件出错");
        }
    }

    public static Properties getProperties() {
        initProperties();
        return prop;
    }
    
    public static Properties getProperties(String filename,boolean flag)  {
        initProperties(filename,flag);
        return mapP.get(filename);
    }
    
    public static String getPropertiesValue (String name) {
        initProperties();
        return prop.getProperty(name);
    }
    
    public static String getPropertiesValue (String name,String defaultValue) {
        initProperties();
        return StringUtils.isEmpty(prop.getProperty(name)) ? defaultValue : prop.getProperty(name);
    }
    
    public static String getPropertiesValue (String filename,String name,boolean flag) {
        initProperties(filename,flag);
        return mapP.get(filename).getProperty(name);
    }
    
    public static String getPropertiesValue (String filename,String name,boolean flag,String defaultValue) {
        initProperties(filename,flag);
        return StringUtils.isEmpty(mapP.get(filename).getProperty(name)) ? defaultValue : mapP.get(filename).getProperty(name);
    }
    

    public static void main(String[] args) {
        logger.debug(getPropertiesValue("config.earlyBOM.unit"));
    }
}
