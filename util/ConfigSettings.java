package PSaPP.util;
/*
Copyright (c) 2010, PMaC Laboratories, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

*  Redistributions of source code must retain the above copyright notice, this list of conditions
    and the following disclaimer.
*  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
    and the following disclaimer in the documentation and/or other materials provided with the distribution.
*  Neither the name of PMaC Laboratories, Inc. nor the names of its contributors may be
    used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

import PSaPP.util.*;

import java.util.*;
import java.io.*;

public final class ConfigSettings {
    private static String configFile = null;

    private static Map<String, Set<String>> validCases = null;
    private static final String[] allowedValidCases = 
        {"AGENCIES",
         "PROJECTS",
         "APPLICATIONS",
         "SIZES",
         "BASE_RESOURCES"
        };

    private static Map<String, String> settings = Util.newHashMap();
    private static final String[] allowedSettings = 
        {"SMTP_SERVER",
         "SMTP_PORT",
         "SMTP_LOGIN",
         "SMTP_PASSWORD",
         "SMTP_USE_TLS",
         "EMAIL_SENDER",
         "EMAIL_SUBJECT",
         "EMAIL_DISABLE",
         "KEEP_SCRATCH",
         "CACHE_DESCRIPTIONS",
         "PCUBED_DATA",
         "POWER_MODELER",
         "POWER_MODELER_DATA",
         "LOOP_TIMES",
         "PCUBED_TEST",
         "PSINS_PATH",
         "DIMEMAS_PATH",
         "GATRAINING_PATH",
         "MULTIMAPS_DATA_PATH",
         "DATABASE",
         "FTP_URL",
         "FTP_TIMEOUT",
         "REPORT_RESOURCES",
         "GATRAINING_OPTIONS",
         "GATRAINING_BWMETHOD",
         "GATRAINING_TYPE",
         "PSINS_COMM_MODEL",
         "FORCE_NEW_TRAINING",
         "SKIP_REPORT",
         "REPORT_DATABASE",
         "OPENSSL_KEYFILE",
         "SYSID_PROCESS"
        };

    private static Map<String, Set<String>> settingsList = Util.newHashMap();
    private static final String[] allowedSettingsList = 
        {"EMAIL_CC",
         "REPORT_ATTACHMENTS"
        };


    private static boolean checkConfigFile(){
        if (configFile == null) {
            Logger.error("Config file has not been read");
            return false;
        }
        return true;
    }

    private static String washKey(String key){
        String w = new String(key);
        w = w.trim();
        w = w.toUpperCase();
        w = w.replaceAll("\\s+", "_");
        return w;
    }

    private static boolean isValidKey(String[] array, String elt){
        for (String x : array){
            if (elt.startsWith(x)){
                return true;
            }
        }
        return false;
    }

    private static String extractKey(String line){
        String[] fields = line.split("=");
        if (fields.length != 2){
            Logger.error("invalid line found when parsing config file (requires exactly one = char): " + line);
        }
        return ConfigSettings.washKey(fields[0]);
    }

    private static String extractValue(String line){
        String[] fields = line.split("=");
        if (fields.length != 2){
            Logger.error("invalid line found when parsing config file (requires exactly one = char): " + line);
        }
        return Util.expandEnvVariables(fields[1]).trim();
    }

    private static void insertSetting(String key, String value){
        settings.put(key, value);
    }

    private static void insertSettingList(String key, String value){
        String[] values = value.split(",");
        Set<String> v = Util.newHashSet();
        for (String s : values){
            v.add(s);
        }
        settingsList.put(key, v);
    }

    private static void insertValidCase(String key, String value){
        if (validCases == null){
            validCases = Util.newHashMap();
        }
        String[] values = value.split(" ");
        Set<String> v = Util.newHashSet();
        for (String s : values){
            v.add(s);
        }
        validCases.put(key, v);
    }

    /**
     * Read the configuration settings from the default config file
     * @return boolean True if successful else false 
     */
    public static boolean readConfigFile() { 
        String c = System.getenv("PSAPP_ROOT");			
        if (c == null) {
            Logger.error("PSAPP_ROOT environment variable not set");       
        }
        if (!c.endsWith("/")) {
            c += "/"; 
        }
        c += "etc/config.txt";
        if(Util.isFile(c)) {
            return readConfigFile(c);
        } else{
            Logger.error("Config file " + c + " not found");
	}
        return false;
    }

    /**
     * Read the configuration settings from the specified config file
     * @param configFile The fully qualified path to the configuration file 
     * @return boolean True if successful else false 
     */
    public static boolean readConfigFile(String c) {  
        configFile = c;
        try {
            FileInputStream fstream = new FileInputStream(configFile);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) {
                String[] values;
                line = Util.cleanComment(line);

                if (line.equals("")){
                    continue;
                }

                String key = ConfigSettings.extractKey(line);
                String value = ConfigSettings.extractValue(line);
                assert(key != null);
                assert(value != null);

                if (ConfigSettings.isValidKey(allowedSettings, key)){
                    insertSetting(key, value);
                } else if (ConfigSettings.isValidKey(allowedSettingsList, key)){
                    insertSettingList(key, value);
                } else if (ConfigSettings.isValidKey(allowedValidCases, key)){
                    insertValidCase(key, value);
                }

            }	
            in.close();  
        }
        catch (Exception e){
            Logger.error(e, "Unable to open config file " + configFile);
            return false;
        } 
        //ConfigSettings.print();
        return true;
    }

    /**
     * Indicates whether or not the specified value is a valid case
     * @param key Identifies the setting to be checked for validity
     * @param value The value is to be checked 
     * @return boolean True if valid else false 
     */
    public static boolean isValid(String key, String value) {
        checkConfigFile();

        Set<String> v = validCases.get(key);
        if (v == null){
            Logger.error("Cannot find key in validCases: " + key);
            return false;
        }

        for (Iterator it = v.iterator(); it.hasNext(); ){
            String s = (String)it.next();
            if (value.equals(s) || value.matches(s)){
                return true;
            }
        }
        return false;
    }

    public static String getSetting(String key){
        String res = settings.get(key);
        if (res == null){
            print();
            Logger.error("Cannot find key in settings: " + key);
            return null;
        }
        return res;
    }

    public static boolean hasSetting(String key){
        String res = settings.get(key);
        if (res == null){
            return false;
        }
        return true;
    }

    public static boolean hasSettingsList(String key){
        Set<String> res = settingsList.get(key);
        if (res == null){
            return false;
        }
        return true;
    }

    public static boolean hasValidCase(String key){
        Set<String> res = validCases.get(key);
        if (res == null){
            return false;
        }
        return true;
    }

    public static void print(){
        Logger.inform("Configuration Settings read from " + configFile);

        Logger.inform("\tSettings");
        for (Iterator it = settings.keySet().iterator(); it.hasNext(); ){
            String key = (String)it.next();
            String value = settings.get(key);
            assert(value != null);
            Logger.inform("\t\t" + Format.padRight(key, 24) + " --> " + value);
        }

        Logger.inform("\tSettingsList");
        for (Iterator it = settingsList.keySet().iterator(); it.hasNext(); ){
            String key = (String)it.next();
            Set<String> value = settingsList.get(key);
            assert(value != null);
            String valueString = "";
            for (Iterator vit = value.iterator(); vit.hasNext(); ){
                String v = (String)vit.next();
                valueString += v + " ";
            }
            Logger.inform("\t\t" + Format.padRight(key, 24) + " --> " + valueString);
        }

        Logger.inform("\tValidCases");
        for (Iterator it = validCases.keySet().iterator(); it.hasNext(); ){
            String key = (String)it.next();
            Set<String> value = validCases.get(key);
            assert(value != null);
            String valueString = "";
            for (Iterator vit = value.iterator(); vit.hasNext(); ){
                String v = (String)vit.next();
                valueString += v + " ";
            }
            Logger.inform("\t\t" + Format.padRight(key, 24) + " --> " + valueString);
        }
    }

    public static String[] getSettingsList(String key){
        Set<String> v = settingsList.get(key);
        if (v == null){
            print();
            Logger.error("Cannot find key in settings list: " + key);
            return null;
        }
        String[] res = new String[v.size()];
        int i = 0;
        for (Iterator it = v.iterator(); it.hasNext(); i++){            
            res[i] = (String)it.next();
        }
        return res;
    }
}

