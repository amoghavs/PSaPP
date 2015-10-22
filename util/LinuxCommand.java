package PSaPP.util;
/*
Copyright (c) 2010, The Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

*  Redistributions of source code must retain the above copyright notice, this list of conditions
    and the following disclaimer.
*  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
    and the following disclaimer in the documentation and/or other materials provided with the distribution.
*  Neither the name of the Regents of the University of California nor the names of its contributors may be
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


import java.io.*;
import java.net.*;
import java.util.*;


public class LinuxCommand {

    public static LinkedList execute(String command){
        return execute(command,null);
    }
    public static LinkedList execute(String command,String outFile){
        String[] tokens = command.split("\\s+");
        return execute(tokens,outFile);
    }
    public static LinkedList execute(String[] command){
        return execute(command,null);
    }

    public static LinkedList execute(String[] command,String outFile){
        return execute(command, outFile, true);
    }

    public static LinkedList execute(String[] command, String outFile, boolean printDebug){
        LinkedList retValue = new LinkedList();

        BufferedReader reader = null;
        String cmdString = "";
        for(int i=0;i<command.length;i++){
            cmdString += (command[i] + " ");
        }
        String[] xtendedCommand = null;
        if(outFile != null){
            cmdString += " >& " + outFile;
            xtendedCommand = new String[3];
            xtendedCommand[0] = "/bin/tcsh";
            xtendedCommand[1] = "-c";
            xtendedCommand[2] = cmdString;
        }
        if (printDebug){
            Logger.inform("running ==> " + cmdString);
        }

        try {
            Process process = null;
            if(outFile == null){
                process = Runtime.getRuntime().exec(command);
            } else {
                process = Runtime.getRuntime().exec(xtendedCommand);
            }

            DataInputStream process_in = new DataInputStream(process.getInputStream());
            String process_str = process_in.readLine();
            while (process_str != null) {
                retValue.add(process_str);
                if (printDebug){
                    System.out.println(process_str);
                }
                process_str = process_in.readLine();
            }

            int exitCode = process.waitFor();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (printDebug){
                Logger.debug(command[0] + " exit status is " + exitCode );
            }
            if(exitCode != 0){
                return null;
            }
        } catch (Exception e){
            Logger.error("Can not execute linux command\n" + 
                         Util.listToString(command) + "\n" + e);
            return null;
        }
        return retValue;
    }

    public static LinkedList tar(String source,String target){
        return tar(source,target,null);
    }

    public static LinkedList tar(String source,String target,String tgtdir){
        String[] command = new String[6];
        command[0] = "tar";
        command[1] = "-cf";
        command[2] = target;
        command[3] = "-C";
        if(tgtdir == null){
            command[4] = ".";
        } else {
            command[4] = tgtdir;
        }
        command[5] = source;

        LinkedList result = execute(command);

        if((result == null) || !Util.isFile(target)){
            Logger.warn("Target " + target + " for tar failed, check also the source " + source);
        }
        return result;
    }

    public static LinkedList unZipTar(String source){
        return unZipTar(source,null);
    }

    public static LinkedList unZipTar(String source,String tgtdir){
        String[] command = new String[5];
        command[0] = "tar";
        command[1] = "-C";
        if(tgtdir == null){
            command[2] = ".";
        } else {
            command[2] = tgtdir;
        }
        command[3] = "-xzf";
        command[4] = source;

        LinkedList result = execute(command);
        return result;
    }

    public static LinkedList gunzip(String source){
        String[] command = new String[3];
        command[0] = "gunzip";
        command[1] = "-f";
        command[2] = source;

        LinkedList result = execute(command);
        source = source.replaceAll("\\.gz$","");
        if((result == null) || !Util.isFile(source)){
            Logger.error("Gunzip failed for the source " + source);
        }
        return result;
    }

    public static LinkedList gzip(String source){
        String[] command = new String[2];
        command[0] = "gzip";
        command[1] = source;

        LinkedList result = execute(command);
        if((result == null) || !Util.isFile(source + ".gz")){
            Logger.error("Zip failed for " + source);
        }
        return result;
    }

    public static LinkedList ls(String path){
        System.out.println("running ==> ls " + path);
        File file = new File(path);
        if(!file.exists()){
            return null;
        }
        LinkedList retValue = new LinkedList();
        if(file.isFile()){
            Logger.debug(path);
            retValue.add(path);
        } else if(file.isDirectory()){
            String[] list = file.list();
            if(list != null){
                for(int i=0;i<list.length;i++){
                    Logger.debug(list[i]);
                    retValue.add(list[i]);
                }
            }
        }

        return retValue;
    }
    public static LinkedList lsWP(String path){
        String[] command = new String[3];
        command[0] = "ls";
        command[1] = "-1";
        command[2] = path;
        return execute(command);
    }
    public static LinkedList copy(String from,String to){
        String[] command = new String[4];
        command[0] = "cp";
        command[1] = "-f";
        command[2] = from;
        command[3] = to;

        return execute(command);
    }
    public static LinkedList deepCopy(String from,String to){
        String[] command = new String[4];
        command[0] = "cp";
        command[1] = "-rf";
        command[2] = from;
        command[3] = to;

        return execute(command);
    }

    public static String hostname(){ 
        String retValue = "";
        try {
            InetAddress localMachine = InetAddress.getLocalHost();    
            retValue = localMachine.getCanonicalHostName();
        } catch(UnknownHostException uhe){
            Logger.warn("Hostname of local machine can not be queried, taking the default");
            retValue = "localhost";
        }
        return retValue;
    }

    public static String username(){
        String retValue = System.getProperty("user.name");
        return retValue;
    }

    public static String pathExists(String path){
        File file = new File(path);
        boolean exists = file.exists();
        if(exists){
            return path;
        }
        return null;
    }
    public static boolean mkdir(String path){
        File file = new File(path);
        if(file.exists() && file.isDirectory()){
            return true;
        }
        if(file.exists() && file.isFile()){
            return false;
        }
        return file.mkdir();
    }

    private static boolean opensslSymmetric(String infile, String outfile, String key, String type, boolean keyIsFile){
        String[] command = new String[10];
        command[0] = "openssl";
        command[1] = "enc";
        command[2] = type;
        command[3] = "-aes-256-cbc";
        command[4] = "-in";
        command[5] = infile;
        command[6] = "-out";
        command[7] = outfile;
        if (keyIsFile){
            command[8] = "-kfile";
        } else {
            command[8] = "-k";
        }
        command[9] = key;

        return (execute(command) != null);
    }

    public static boolean opensslEncryptSymmetricPhrase(String infile, String outfile, String key){
        return opensslSymmetric(infile, outfile, key, "-e", false);
    }

    public static boolean opensslEncryptSymmetric(String infile, String outfile, String key){
        return opensslSymmetric(infile, outfile, key, "-e", true);
    }

    public static boolean opensslDecryptSymmetric(String infile, String outfile, String key){
        return opensslSymmetric(infile, outfile, key, "-d", true);
    }

    public static boolean decryptAndRemoveClear(String encryptedFile, String clearFile){
        String key = ConfigSettings.getSetting("OPENSSL_KEYFILE");
        if (key == null){
            Logger.error("OPENSSL_KEYFILE config key needs to be set in $PSAPP_ROOT/etc/config");
        }
        if (!opensslDecryptSymmetric(encryptedFile, clearFile, key)){
            Logger.error("Problem decrypting file " + encryptedFile);
            return false;
        }
        if (!deleteFile(encryptedFile)){
            Logger.error("Problem removing file " + clearFile);
            return false;
        }
        return true;
    }

    public static boolean fileContains(String file, String searchStr){
        String[] command = new String[3];
        command[0] = "grep";
        command[1] = searchStr;
        command[2] = file;

        return (execute(command) != null);
    }

    public static String cppFilt(String mangled){
        String[] command = new String[2];
        command[0] = "c++filt";
        command[1] = mangled;

        LinkedList result = execute(command, null, false);
        assert(result != null);

        String unmangled = null;

        for (Iterator it = result.iterator(); it.hasNext(); ){
            String res = (String)it.next();
            unmangled = res;
        }
        assert(unmangled != null);
        return unmangled;
    }

    public static String cppName(String mangled){

        String unmangled = cppFilt(mangled);
        int argLoc = unmangled.indexOf('(');

        if (argLoc > 0){
            return unmangled.substring(0, argLoc);
        }
        return unmangled;
    }

    public static boolean rmdir(String path){
        path = Util.cleanWhiteSpace(path);
        if(path.equals("") || path.equals(".") || 
           path.equals("/") || path.equals("*")){
            return false;
        }
        String[] command = new String[3];
        command[0] = "rm";
        command[1] = "-rf";
        command[2] = path;
        return (execute(command) != null);
    }

    public static String pwd(){
        String retValue = System.getProperty("user.dir");
	return retValue;
    }


    public static boolean isFile(String path){
        File file = new File(path);
        return (file.exists() && file.isFile());
    }
    public static boolean isDirectory(String path){
        File file = new File(path);
        return (file.exists() && file.isDirectory());
    }
    public static long getFileSize(String path){
        if(pathExists(path) != null){
            File file = new File(path);
            return file.length();
        }
        return -1;
    }
    public static boolean deleteFile(String path){
        if(pathExists(path) != null){
            if(isDirectory(path)){
                Logger.warn("Can not delete " + path + ", it is a directory");
                return false;
            }
            File file = new File(path);
            Logger.inform("Deleting " + path);
            return file.delete();
        }
        return true;
    }

    public static boolean touchFile(String path){
        String[] command = new String[2];
        command[0] = "touch";
        command[1] = path;
        return (execute(command) != null);
    }

    public static String[] splitDirAndFile(String path){
        String[] dirs = new String[2];
        dirs[0] = null;
        dirs[1] = null;

        if(path != null){
            path = Util.cleanWhiteSpace(path);
            path = path.replaceAll("\\/+$","");
            int idx = path.lastIndexOf('/');
            if(idx < 0){
                dirs[0] = ".";
                dirs[1] = path;
            } else {
                dirs[0] = new String(path.toCharArray(),0,idx);
                dirs[1] = path.substring(idx+1);
            }
        }

        assert (dirs[1] != null) : "There has to be at least one dir";
        return dirs;
    }

    static boolean backupForMove(String to){
        int toIdx = 1;
        for(;;toIdx++){
            if(pathExists(to + "." + toIdx) == null){
                break;
            }
        }
        return move(to,to + "." + toIdx);
    }
    public static boolean ipmparse(String xmlFile, String htmldir){ /*** Mitesh edits ***/
        boolean status = true;
        if(pathExists(xmlFile) == null){
            Logger.warn("ipmparse fails since " + xmlFile + " does not exist");
            return false;
        }

        try {
            File xml = new File(xmlFile);
            if((xml == null) ){
                Logger.warn("Can not run ipm_parse on " + xmlFile + "with htmldir " + htmldir);
                return false;
            }
            String[] command = new String[6];
            command[0] = "ipm_parse";
            command[1] = "-full";
            command[2] = "-html";
            command[3] = "-htmldir";
	    command[4] = htmldir;
            command[5] = xmlFile;

	    status = (execute(command) != null);
            
        } catch (Exception e){
            Logger.warn("ipm_parse failed on " + xmlFile + "with htmldir " + htmldir + "\n" + e);
            return false;
        }
        return status;
    }

    public static boolean infoscript(String script, String xmlFile, String infoFile ){ /*** Mitesh edits ***/
        boolean status = true;
        if(pathExists(xmlFile) == null){
            Logger.warn("infoScript fails since " + xmlFile + " does not exist");
            return false;
        }
        
        try {
            File IN = new File(xmlFile);
            if((IN == null) ){
                Logger.warn("Can not run info script  on " + xmlFile + "with infoFile " + infoFile);
                return false;
            }
            String[] command = new String[3];
            command[0] = script;
            command[1] = xmlFile;
            command[2] = infoFile;
            
            status = (execute(command) != null);
            
        } catch (Exception e){
            Logger.warn("info script failed on " + xmlFile + "with infoFile " + infoFile + "\n" + e);
            return false;
        }
        return status;
    }
    
    public static boolean parsescript(String script, String inFile, String outFile ){ /*** Mitesh edits ***/
        boolean status = true;
        if(pathExists(inFile) == null){
            Logger.warn("parsescript fails since " + inFile + " does not exist");
            return false;
        }
        
        try {
            File IN = new File(inFile);
            if((IN == null) ){
                Logger.warn("Can not run parse script  on " + inFile + "with outFile " + outFile);
                return false;
            }
            String[] command = new String[5];
            command[0] = script;
            command[1] = "--infile";
            command[2] = inFile;
            command[3] = "--outfile";
            command[4] = outFile;
            
            status = (execute(command) == null);
            
        } catch (Exception e){
            Logger.warn("ipm_parse failed on " + inFile + "with outFile " + outFile + "\n" + e);
            return false;
        }
        return status;
    }
    
    public static boolean move(String from, String to){
        boolean status = true;
        if(pathExists(from) == null){
            Logger.warn("Move fails since " + from + " does not exist");
            return false;
        }
        if(pathExists(to) != null){
            Logger.inform("Moving " + to + " for backing up");
            backupForMove(to);
        }

        try {
            File fromFile = new File(from);
            File toFile = new File(to);
            if((fromFile == null) || (toFile == null)){
                Logger.warn("Can not move " + from + " to " + to);
                return false;
            }
            String[] command = new String[4];
            command[0] = "mv";
            command[1] = "-f";
            command[2] = from;
            command[3] = to;

	    status = (execute(command) != null);
            
        } catch (Exception e){
            Logger.warn("Move failed " + from + " to " + to + "\n" + e);
            return false;
        }
        return status;
    }
    public static boolean email(String to,String subject,String file){
        return email(to,subject,file,null);
    }

    public static boolean email(String to,String subject,String file,TreeMap tuples){

        String statusFileName = file + ".extra";

        String mailCmd = "/usr/local/app/PSaPP/scripts/pmacmail.py -s " + "'" + subject + "' -t " + to + " -i " + file;
        String[] command = new String[3];
        command[0] = "/bin/tcsh";
        command[1] = "-c";
        command[2] = mailCmd;

        if(tuples != null){
            try {
                BufferedWriter statusFile = new BufferedWriter(new FileWriter(statusFileName));
                statusFile.write("\n" + subject + "\n");
                Iterator it = tuples.keySet().iterator();
                while(it.hasNext()){
                    String key = (String)it.next();
                    String val = (String)tuples.get(key);
                    statusFile.write("\n" + key + " : " + val + "\n");
                }
                statusFile.close();
            } catch (Exception e){
                Logger.warn("Can not write to " + statusFileName + "\n" + e);
                tuples = null;
            }
        }

        boolean status = (execute(command) != null);

        if(tuples != null){
            mailCmd = "/usr/local/app/PSaPP/scripts/pmacmail.py -s " + "'" + "Verify : " + subject + "' -t " + to + " -i " + statusFileName;
            command[2] = mailCmd;
            status = status && (execute(command) != null);
        }

        return status;
    }
}
