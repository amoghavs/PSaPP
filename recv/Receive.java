package PSaPP.recv;
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


import java.util.*;
import java.io.*;
import PSaPP.util.*;
import PSaPP.dbase.*;
import PSaPP.data.*;

public class Receive implements CommandLineInterface {

    OptionParser optionParser;

    String primaryDir;
    String secondaryDir;
    String scratchDir;
    String inboxDir;

    LinkedList listOfValidDones;
    HashMap listOfValidTraces;

    Database dataBase;

    static final String defaultInboxDirectory = "/pmaclabs/ftp/incoming/";
    static final String defaultPrimaryDirectory = "/pmaclabs/collected_data/user_modeling";
    static final String defaultScratchDirectory = "/pmaclabs/tmp";
    static final String defaultResourceForReceive = "diamond";

    public static final int inforStringTokenCount = 5;

    static final String[] ALL_OPTIONS = {
        "help:?",
        "version:?",

        "inbox:s",
        "primary:s",
        "secondary:s",
        "scratch_dir:s",

        "database:s"
    };
    public Receive(){
        optionParser = new OptionParser(ALL_OPTIONS,this);
        primaryDir = null;
        secondaryDir = null;
        scratchDir = null;
        inboxDir = null;
        dataBase = null;
        listOfValidDones = null;
        listOfValidTraces = null;
    }

    public boolean verifyValues(HashMap values){
        String inbox = (String)values.get("inbox");
        if(inbox != null){
            if(!Util.isDirectory(inbox)){
                Logger.error("The inbox directory " + inbox + " does not exist");
            }
        }
        String primary = (String)values.get("primary");
        if(primary != null){
            if(!Util.isDirectory(primary)){
                Logger.error("The primary directory " + primary + " does not exist");
            }
        }
        String scratch = (String)values.get("scratch_dir");
        if(scratch != null){
            if(!Util.isDirectory(scratch)){
                Logger.error("The scratch directory " + scratch + " does not exist");
            }
        }
        String dbasefile = (String)values.get("database");
        if((dbasefile != null) && !Util.isFile(dbasefile)){
            Logger.error("Database file " + dbasefile + " does not exist");
        }

        String secondary = (String)values.get("secondary");
        if(secondary != null){
            /*** CHECK WHICH HSI ***/
            if(!Util.isDirectory(secondary)){
                Logger.error("The secondary directory " + secondary + " does not exist");
            }
        }

        return true;
    }

    public TestCase getTestCase(HashMap values) {
        return null;
    }
    public boolean isHelp(HashMap values) {
        return (values.get("help") != null);
    }
    public boolean isVersion(HashMap values) {
        return (values.get("version") != null);
    }
    public void printUsage(String str){
        System.err.println(helpString);
        String allStr = "usage :\n";
        for(int i=0;i<ALL_OPTIONS.length;i++){
            allStr += ("\t--" + ALL_OPTIONS[i] + "\n");
        }
        allStr += ("\n" + str);
        //System.err.println(allStr);
    }
    public boolean donotBackup(){
        return (secondaryDir == null);
    }
    public boolean makeAllDirectories(TestCase tCase){
        String[] allDirs = {  primaryDir,
                              primaryDir + "/processed",
                              primaryDir + "/processed/" + tCase.getProject(),
                              primaryDir + "/raw" ,
                              primaryDir + "/raw/" + tCase.getProject(),
                              scratchDir,
                              scratchDir + "/" + tCase.ftpName() };

        return Util.mkdirs(allDirs);
    }
    public boolean run(String argv[]) {
        if (argv.length < 1) {
            optionParser.printUsage("");
            return false;
        }
        optionParser.parse(argv);
        if(optionParser.isHelp()){
            optionParser.printUsage("");
            return true;
        }
        if(optionParser.isVersion()){
            Logger.inform("The version is <this>",true);
            return true;
        }

        ConfigSettings.readConfigFile();

        Logger.inform("Verifying the options");
        optionParser.verify();

        String dbasefile = (String)optionParser.getValue("database");
        if(dbasefile != null){
            assert Util.isFile(dbasefile);
            dataBase = new BinaryFiles(dbasefile);
        } else {
            dataBase = new Postgres();
        }

        inboxDir = (String)optionParser.getValue("inbox");
        if(inboxDir == null){
            Logger.inform("Using the default inbox directory " + defaultInboxDirectory);
            inboxDir = defaultInboxDirectory;
        }
        primaryDir = (String)optionParser.getValue("primary");
        if(primaryDir == null){
            Logger.inform("Using the default primary directory " + defaultPrimaryDirectory);
            primaryDir = defaultPrimaryDirectory;
        }
        scratchDir = (String)optionParser.getValue("scratch_dir");
        if(scratchDir == null){
            Logger.inform("Using the default scratch directory " + defaultScratchDirectory);
            scratchDir = defaultScratchDirectory;
        }
        assert (inboxDir != null) && (primaryDir != null) && (scratchDir != null);

        secondaryDir = (String)optionParser.getValue("secondary");

        Logger.inform("Inbox directory is " + inboxDir);
        Logger.inform("Primary directory is " + primaryDir);
        Logger.inform("Scratch directory is " + scratchDir);
        if(donotBackup()){
            Logger.inform("No backup is asked, so no use of secondary directory");
        } else {
            Logger.inform("Secondary directory is " + secondaryDir);
        }

        boolean status = dataBase.initialize();
        if(!status){
            Logger.warn("Error in starting the database");
            return false;
        }

        TestCase testCase = optionParser.getTestCase();
        assert (testCase == null);

        listOfValidDones = getDoneFileList();
        if((listOfValidDones == null) || (listOfValidDones.size() == 0)){
            Logger.inform("No valid done files to process so exiting");
            return false;
        }

        listOfValidTraces = generateReceiveObjects();
        if((listOfValidTraces == null) || (listOfValidTraces.size() == 0)){
            Logger.inform("No done files are filetered to process so exiting");
            return false;
        }
        Logger.inform("There are " + listOfValidTraces.size() + " valid traces for receive");

        int failedCount = 0;
        Iterator it = listOfValidTraces.keySet().iterator();
        while(it.hasNext()){
            String infoString = (String)it.next();

            ReceiveRecord receiveRecord = (ReceiveRecord)listOfValidTraces.get(infoString);
            Trace traceObject = receiveRecord.getTraceObject();
            assert (traceObject != null);

            status = makeAllDirectories(traceObject.getTestCase());
            if(!status){
                Logger.warn("Error in making the receive directory(ies)");
                return false;
            }

            Logger.inform("");
            String emailFile = scratchDir + "/" + traceObject.getTestCase().ftpName() + ".email";
            Logger.tee(emailFile);

            Logger.inform("Receive for " + infoString + " is starting");

            boolean recv_status = traceObject.receive(receiveRecord,primaryDir,secondaryDir,scratchDir);

            Logger.tee();

            String subject = "PMaC Receive (";
            if(recv_status) {
                subject += "SUCCESS";
            } else {
                subject += "  FAIL ";
            }
            subject += ") " + infoString;

            /****** Mitesh edits ***/
            TreeMap traceInfo=null;
            if( (traceObject instanceof Ipm) || (traceObject instanceof SysHealth))
            {
                traceInfo = new TreeMap();
            }
            else 
            {
                traceInfo= dataBase.getTraceStatuses(traceObject.getTestCase());
            }

            traceInfo.put("0.Verify",receiveRecord.getSuccessStr());

            String[] to = {receiveRecord.getNotify()};
            String[] cc = ConfigSettings.getSettingsList("EMAIL_CC");
            StringWriter writer = new StringWriter();
            
            try {
                FileReader reader = new FileReader(emailFile);
                char buffer[] = new char[2048];
                int n = 0;
                int t = 0;
                int emailLimit = 0;
                if (ConfigSettings.hasSetting("EMAIL_LIMIT")){
                    emailLimit = Integer.parseInt(ConfigSettings.getSetting("EMAIL_LIMIT"));
                }
                while((n = reader.read(buffer)) != -1){
                    writer.write(buffer, 0, n);
                    t += n;
                    if (emailLimit > 0 && t > emailLimit){
                        break;
                    }
                }
                writer.flush();
                reader.close();
            }
            catch (Exception e) {
                Logger.error("Exception reading file " + emailFile + " " + e);
            }
            String body = writer.toString();
            try {
                Util.sendEmail(to, cc, subject, body, null);
            }
            catch (Exception e) {
                Logger.error("Exception sending email from Receive " + e);
            }
            
            body = "";
            body += ("\n" + subject + "\n");
            Iterator iter = traceInfo.keySet().iterator();
            while(iter.hasNext()){
                String key = (String)iter.next();
                String val = (String)traceInfo.get(key);
                body += ("\n" + key + " : " + val + "\n");
            }
            try {
                Util.sendEmail(to, cc, "Verify : " + subject, body, null);
            }
            catch (Exception e) {
                Logger.error("Exception sending email from Receive " + e);
            }
            
            if(!status){
                Logger.warn("Receive for " + infoString + " has failed");
                failedCount++;
            }
            
            if (recv_status){
                status = LinuxCommand.deleteFile(inboxDir + "/" + infoString + ".done");
                if(!status){
                    Logger.warn("Can not delete the .done file for " + inboxDir + "/" + infoString);
                }
                status = true;
                String keepScratch = ConfigSettings.getSetting("KEEP_SCRATCH");
                if (keepScratch.matches("true")){
                    Logger.warn("Removal of scratch files for receive is disabled in config file");
                } else {
                    status = LinuxCommand.rmdir(scratchDir + "/" + traceObject.getTestCase().ftpName());
                }
                if(!status){
                    Logger.warn("Can not remove test dir " + scratchDir + "/" + traceObject.getTestCase().ftpName());
                }
                status = LinuxCommand.deleteFile(emailFile);
                if(!status){
                    Logger.warn("Can not delete emailing file " + emailFile);
                }
            }

        }
        
        status = dataBase.commit(); 
        if(!status){
            Logger.warn("Error in terminating the database");
            return false;
        }
        
        if(failedCount != 0){
            Logger.warn(failedCount + " receives failed. Re-submit please");
            return false;
        }
        
        Logger.inform("All receives succeeded");
        
        return true;
    }
    
    public HashMap generateReceiveObjects(){
        HashMap retValue = new HashMap();
        
        Iterator it = listOfValidDones.iterator();
        while(it.hasNext()){
            String infoString = (String)it.next();
            Logger.inform("Verifying " + infoString + " for receive inclusion");
            String doneFile = inboxDir + "/" + infoString + ".done";
            
            String agency = null;
            Boolean verify = new Boolean(true);
            String notify = null;
            String resource = null;
            String report = null;
            Boolean privateReport = new Boolean(false);
            
            try {
                BufferedReader inputFile = new BufferedReader(new FileReader(doneFile));
                String line = null;
                while ((line = inputFile.readLine()) != null) {
                    line = Util.cleanWhiteSpace(line);
                    if(line.matches("^agency=.*")){
                        agency = line.replaceAll(".*=","");
                    } else if(line.matches("^resource=.*")){
                        resource = line.replaceAll(".*=","");
                    } else if(line.matches("^noverify=.*")){
                        verify = new Boolean(false);
                    } else if(line.matches("^notify=.*")){
                        notify = line.replaceAll(".*=","");
                    } else if(line.matches("^report=.*")){
                        report = line.replaceAll(".*=","");
                    } else if(line.matches("^private=.*")){
                        privateReport = new Boolean(true);
                    }
                }
                inputFile.close();
            } catch (Exception e){
                Logger.warn("Error reading the .done file " + doneFile + "\n" + e);
                continue;
            }
            
            if((agency == null) || (notify == null)){
                Logger.warn(doneFile + " should have agency and notify lines in it, so skipping");
                continue;
            }
            
            if(resource == null){
                resource = defaultResourceForReceive;
            }
            
            if(!ConfigSettings.isValid("AGENCIES", agency)){
                Logger.warn(agency + " is not a valid agency");
                continue;
            }
            if(!ConfigSettings.isValid("BASE_RESOURCES", resource)){
                Logger.warn(resource + " is not a valid base resource");
                continue;
            }
            
            String[] tokens = infoString.split("\\.\\.");
            assert (tokens.length == inforStringTokenCount);
            String user = tokens[0];
            String machine = tokens[1];
            String testCaseString = tokens[2];
            Long   size = Util.toLong(tokens[3]);
            assert (size != null);
            String type = tokens[4];
            
            TestCase testCase = new TestCase(agency + "_" + testCaseString);
            assert (testCase != null);
            
            Trace traceObject = null;
            
            // decrypt any files that are encrypted with two-sided aes
            String dataFile = inboxDir + "/" + infoString;
            String encryptedFile = null;
            Logger.inform("Looking for " + dataFile);
            if (Util.isFile(dataFile + ".tar.gz.enc")){
                encryptedFile = dataFile + ".tar.gz";
            } else if (Util.isFile(dataFile + ".enc")){
                encryptedFile = dataFile;
            } else if (Util.isFile(dataFile + ".psins.gz.enc")){
                encryptedFile = dataFile + ".psins.gz";
            }

            if (encryptedFile != null){
                LinuxCommand.decryptAndRemoveClear(encryptedFile + ".enc", encryptedFile);
            } else {
                Logger.inform("openssl... " + dataFile);
            }
            
            //            Logger.error("wall" + encryptedFile);

            if(type.matches("^exec.*")){
                
                String timeStr = type.replaceAll("^exec","");
                Integer time = Util.toInteger(timeStr);
                assert (time != null);
                dataFile += "";
                if(resource == null){
                    Logger.warn("Receive needs resource in " + doneFile);
                    continue;
                }
                traceObject = new Executable(dataFile,(double)time.intValue(),resource,testCase,dataBase);
                
            } else if(type.matches("^psinstrace.*")){
                
                String timeStr = type.replaceAll("^psinstrace","");
                Integer time = Util.toInteger(timeStr);
                assert (time != null);
                dataFile += ".psins.gz";
                if(resource == null){
                    Logger.warn("Receive needs resource in " + doneFile);
                    continue;
                }
                traceObject = new Psins(dataFile,(double)time.intValue(),resource,testCase,dataBase);
                
            } else if(type.matches("^ipmdata.*")){	/*** Mitesh edits**/
                
                String timeStr = type.replaceAll("^ipmdata","");
                Integer time = Util.toInteger(timeStr);
                assert (time != null);
                dataFile += "";
                if(resource == null){
                    Logger.warn("Receive needs resource in " + doneFile);
                    continue;
                }   
                traceObject = new Ipm(dataFile,(double)time.intValue(),resource,testCase,dataBase);
                
            } else if(type.matches("^syshealth.*")){      /*** Mitesh added stuff for syshealth**/
                String timeStr = type.replaceAll("^syshealth","");
                Integer time = Util.toInteger(timeStr);
                assert (time != null);
                dataFile += "";
                if(resource == null){
                    Logger.warn("Receive needs resource in " + doneFile);
                    continue;
                }
                traceObject = new SysHealth(dataFile,(double)time.intValue(),resource,testCase,dataBase);
            } else if(type.matches("^mpidtrace.*")){
                
                String timeStr = type.replaceAll("^mpidtrace","");
                Integer time = Util.toInteger(timeStr);
                assert (time != null);
                dataFile += ".trf.gz";
                if(resource == null){
                    Logger.warn("Receive needs resource in " + doneFile);
                    continue;
                }
                traceObject = new Mpidtrace(dataFile,(double)time.intValue(),resource,testCase,dataBase);
                
            } else if(type.matches("^jbbinst.*")){
                
                dataFile += ".tar.gz";
                traceObject = new JbbInst(dataFile,testCase,dataBase);
                
            } else if(type.matches("^jbbcoll.*")){
                
                String timeStr = type.replaceAll("^jbbcoll","");
                Integer time = Util.toInteger(timeStr);
                assert (time != null);
                dataFile += ".tar.gz";
                
                traceObject = new JbbColl(dataFile,(double)time.intValue(),testCase,dataBase);
                
            } else if(type.matches("^siminst.*")){
                
                String phaseStr = type.replaceAll("^siminst","");
                phaseStr = phaseStr.replaceAll("[a-z]"," ");
                String[] phaseTokens = phaseStr.split("\\s+");
                if(phaseTokens.length != 2){
                    Logger.warn(phaseStr + " should have 2 tokens for phase");
                    continue;
                }
                Integer phaseCnt = Util.toInteger(phaseTokens[0]);
                Integer phaseNo = Util.toInteger(phaseTokens[1]);
                if((phaseCnt == null) || (phaseNo == null)){
                    Logger.warn("phase count and no are needed in " + phaseStr);
                    continue;
                }
                dataFile += ".tar.gz";
                traceObject = new SimInst(dataFile,phaseNo.intValue(),phaseCnt.intValue(),testCase,dataBase);
                
            } else if(type.matches("^simcoll.*")){
                
                String phaseStr = type.replaceAll("^simcoll","");
                phaseStr = phaseStr.replaceAll("[a-z]"," ");
                String[] phaseTokens = phaseStr.split("\\s+");
                if(phaseTokens.length != 3){
                    Logger.warn(phaseStr + " should have 3 tokens for phase");
                    continue;
                }
                Integer phaseCnt = Util.toInteger(phaseTokens[0]);
                Integer phaseNo = Util.toInteger(phaseTokens[1]);
                Integer time = Util.toInteger(phaseTokens[2]);
                if((phaseCnt == null) || (phaseNo == null) || (time == null)){
                    Logger.warn("phase count and no and time are needed in " + phaseStr);
                    continue;
                }
                dataFile += ".tar.gz";
                traceObject = new SimColl(dataFile,phaseNo.intValue(),
                                          (double)time.intValue(),phaseCnt.intValue(),testCase,dataBase);
                
            }
            if(traceObject != null){
                ReceiveRecord receiveRecord = new ReceiveRecord(traceObject,verify.booleanValue(),
                                                                notify,resource,user,machine,size.longValue(),report, privateReport);
                Logger.inform("Trace file for " + infoString + " is included for final receive list");
                retValue.put(infoString,receiveRecord);
            }
        }
        return retValue;
    }
    LinkedList getDoneFileList(){
        
        Logger.inform("Listing the " + inboxDir + " for candidate receives");
        
        LinkedList allFiles = LinuxCommand.ls(inboxDir);
        if((allFiles == null) || (allFiles.size() == 0)){
            Logger.inform("There is no file at inbox " + inboxDir);
            return null;
        }
        
        LinkedList restFiles = new LinkedList();
        LinkedList doneList = new LinkedList();
        Iterator it = allFiles.iterator();
        while(it.hasNext()){
            String currentFile = (String)it.next();
            if(currentFile.matches(".*\\.done")){
                Logger.inform("Checking validity of " + currentFile);
                String pathToDone = inboxDir + "/" + currentFile;
                if(!Util.isFile(pathToDone) || (LinuxCommand.getFileSize(pathToDone) == 0)){
                    Logger.warn(pathToDone + " either does not exist or has size 0, so skipping.");
                    continue;
                }
                
                String infoString = currentFile.replaceAll("\\.done","");
                String[] tokens = infoString.split("\\.\\.");
                if(tokens.length != inforStringTokenCount){
                    Logger.warn(pathToDone + " should have " + inforStringTokenCount + 
                                " tokens sep by .., so skipping");
                    continue;
                }
                doneList.add(infoString);
            } else {
                restFiles.add(currentFile);
            }
        }
        
        if(doneList.size() == 0){
            Logger.warn("Found no valid .done file to process at " + inboxDir);
            return null;
        }
        
        LinkedList retValue = new LinkedList();
        
        it = doneList.iterator();
        while(it.hasNext()){
            String infoString = (String)it.next();
            boolean foundMate = false;
            Iterator restit = restFiles.iterator();
            while(restit.hasNext()){
                String currentFile = (String)restit.next();
                int idx = currentFile.indexOf(infoString);
                if((idx >= 0) && (LinuxCommand.getFileSize(currentFile) != 0)){
                    foundMate = true;
                    break;
                }
            }
            if(!foundMate){
                Logger.warn("There is no mate file for " + infoString + ",so skipping");
                continue;
            }
            
            Logger.inform("Inbox has trace for receiving " + infoString);
            
            retValue.add(infoString);
        }
        
        return retValue;
    }
    public static void main(String argv[]) {
        try {
            Receive receive = new Receive();
            boolean check = receive.run(argv);
            if(check){
                Logger.inform("\n*** DONE *** SUCCESS *** SUCCESS *** SUCCESS *****************\n");
            }
        } catch (Exception e) {
            Logger.error(e, "*** FAIL *** FAIL *** FAIL Exception *****************\n");
        }
    }
    public static void printReceiveRecord(String pathToFile,ReceiveRecord record,String primDir,String secDir,String scrDir){
        Logger.debug("Receive record{\n" +
                     "\tfile     = " + pathToFile + "\n" + 
                     "\tuser     = " + record.getUser() + "\n" +
                     "\tmach     = " + record.getMachine() + "\n" +
                     "\tverify   = " + record.getVerify() + "\n" +  
                     "\tnotify   = " + record.getNotify() + "\n" +
                     "\tresource = " + record.getResource() + "\n" +
                     "\tsize     = " + record.getSize() + "\n" +
                     "\tpridir   = " + primDir + "\n" +
                     "\tsecdir   = " + secDir + "\n" +
                     "\tscrdir   = " + scrDir + "\n}\n");
    }
    static final String helpString =
        "[Basic Params]:\n" +
        "    --help                                 : Print a brief help message\n" +
        "    --version                              : Print the version and exit\n" +
        "[Script Params]:\n" +
        "    --inbox       /location/of/inbox       : The inbox directory for ftp transfer [REQ]\n" +
        "    --primary     /location/on/mounteddisk : The primary directory for storing all traces [REQ]\n" +
        "    --scratch_dir /scratch/username        : The scratch directory to work on temp files  [REQ]\n" +
        "    --secondary   /location/on/hpss        : The directory used for secondary backup [REQ]\n" +
        "                                              !!! Not Implemented Yet\n" +
        "[Other Params]:\n" +
        "    --database    /path/file.bin           : The path to the database file if binary files are used.\n" +
        "                                             Default is to use Postgres.\n";
}
