package PSaPP.chck;

import java.util.*;
import java.io.*;
import PSaPP.util.*;
import PSaPP.data.*;

class TraceRecord {

    static final String[] validTraceSubdirs = { "jbbinst","jbbcoll","siminst","simcoll" };
    static final int TotalMemoryHiers = 33; // Note that this is total number of memory hiers for TI-10.
                                            // You need to change this if the number for upcoming projs
                                            // are smaller

    String  caseString;
    HashMap typeToDir;

    String glblAppName;
    int    glblAppSize;

    boolean isValidType(String t) {
        boolean retValue = false;
        for(int i=0;i<validTraceSubdirs.length;i++){
            if(validTraceSubdirs[i].equals(t)){
                return true;
            }
        }
        return retValue;
    }

    TraceRecord(String str) { typeToDir = new HashMap(); caseString = str; glblAppName = null; glblAppSize = 0; }
    void setTraceDir(String dir,String typ){
        if(!isValidType(typ)){
            Logger.warn("The trace directory type " + typ + " is not a valid sub directory for traces");
            return;
        }
        typeToDir.put(typ,dir);
    }

    int getCpuCount(){
        String[] tokens = caseString.split("_");
        assert (tokens.length == 3);
        Integer val = Util.toInteger(tokens[2]);
        if(val == null){
            return 0;
        }
        return val.intValue();
    }

    boolean readVerifyStaticFile(String path,String execName){
        if((path == null) || !Util.isFile(path)){
            Logger.warn("Invalid path for static file " + path + " indicating the .static file does not exist");
            return false;
        }
        Logger.inform("Reading static file " + path);

        String appName = null;
        int totalBlockInfo = 0;
        int totalBlocks = 0;
        int appSize = 0;

        try {
            BufferedReader inputFile = new BufferedReader(new FileReader(path));
            String line = null;
            int lineNumber = 0;
            while ((line = inputFile.readLine()) != null) {
                lineNumber++;
                String cleanedLine = Util.cleanComment(line);
                cleanedLine = Util.cleanWhiteSpace(cleanedLine,false);
                if(cleanedLine.length() > 0){
                    if(cleanedLine.matches("^\\d+.*")){
                        Object[] fields = Util.stringsToRecord(cleanedLine,"iliiiss");
                        if(fields == null){
                            Logger.warn("Line " + lineNumber + " in " + path + " is not valid");
                            return false;
                        }
                        totalBlockInfo++;
                    }
                } else if(line.matches("^#.*=.*")){
                    String[] tokens = line.split("=");
                    tokens[1] =  Util.cleanWhiteSpace(tokens[1]);
                    if(line.matches("^#.*appname.*=.*")){
                        Logger.debug(line);
                        appName = tokens[1].replaceAll(".*\\/","");
                    } else if(line.matches("^#.*appsize.*=.*")){
                        Logger.debug(line);
                        assert(Util.toInteger(tokens[1]) != null);
                        appSize = Util.toInteger(tokens[1]).intValue();
                    } else if(line.matches("^#.*blocks.*=.*")){
                        Logger.debug(line);
                        assert(Util.toInteger(tokens[1]) != null);
                        totalBlocks = Util.toInteger(tokens[1]).intValue();
                    }
                }
            }
            inputFile.close();
        } catch (Exception e){
            Logger.error(e, "Can not read from " + path );
            return false;
        }
        Logger.debug("[" + appName + "," + appSize + "," + totalBlocks + "," + totalBlockInfo + "]");

        if(glblAppName == null){
            glblAppName = appName;
        }
        if(glblAppSize == 0){
            glblAppSize = appSize;
        }

        boolean retValue = true;
        if((glblAppName != null) && !glblAppName.equals(appName)){
            Logger.warn(appName + " in trace file " + path + " does not match the global name " + glblAppName + " for " + caseString);
            retValue = false;
        }
        if(glblAppSize != appSize){
            Logger.warn(appSize + " in trace file " + path + " does not match the global size " + glblAppSize + " for " + caseString);
            retValue = false;
        }
        if(totalBlocks != totalBlockInfo){
            Logger.warn(totalBlocks + " blocks are reported for trace file " + path + 
                        " but does not match the info count presented inside " + totalBlockInfo + " for " + caseString);
            retValue = false;
        }
        if((totalBlocks == 0) || (totalBlockInfo == 0)){
            Logger.warn(" 0 blocks are reported or included for trace file " + path + " for " + caseString);
            retValue = false;
        }
        if((glblAppName != null) && !glblAppName.equals(execName)){
            Logger.warn(execName + " in name of trace " + path + " does not match the global name " + glblAppName + " for " + caseString);
            retValue = false;
        }

        return retValue;
    }

    boolean processJbbInst(String dir){
        if(dir == null){
            Logger.warn("No jbbinst directory for trace verification for " + caseString);
            return true;
        }
        Logger.inform("Verifying jbbinst directory for " + dir);
        String executableName = null;
        String staticFilePath = null;
        LinkedList result = LinuxCommand.ls(dir);
        Iterator it = result.iterator();
        while(it.hasNext()){
            String file = (String)it.next();
            file = Util.cleanWhiteSpace(file);
            if(file.endsWith(".jbbinst.static")){
                executableName = file.replaceAll("\\.jbbinst\\.static","");
                staticFilePath = dir + "/" + file;
                break;
            }
        }
        return readVerifyStaticFile(staticFilePath,executableName);
    }

    boolean processSimInst(String dir){
        if(dir == null){
            Logger.warn("No siminst directory for trace verification for " + caseString);
            return true;
        }
        Logger.inform("Verifying siminst directory for " + dir);
        String executableName = null;
        String staticFilePath = null;
        LinkedList result = LinuxCommand.ls(dir);
        Iterator it = result.iterator();
        while(it.hasNext()){
            String file = (String)it.next();
            file = Util.cleanWhiteSpace(file);
            if(file.endsWith(".siminst.static")){
                executableName = file.replaceAll("\\.phase\\.\\d+\\.\\d+\\.siminst\\.static","");
                staticFilePath = dir + "/" + file;
                break;
            }
        }
        return readVerifyStaticFile(staticFilePath,executableName);
    }

    boolean processJbbColl(String dir){
        if(dir == null){
            Logger.warn("No jbbcoll directory for trace verification for " + caseString);
            return true; 
        }
        LinkedList allFiles = new LinkedList();
        Logger.inform("Verifying jbbcoll directory for " + dir);
        int cpuCnt = getCpuCount();
        String executableName = null;
        String staticFilePath = null;
        LinkedList result = LinuxCommand.ls(dir);
        Iterator it = result.iterator();
        int foundFiles = 0;
        while(it.hasNext()){
            String file = (String)it.next();
            file = Util.cleanWhiteSpace(file);
            if(file.matches(".*\\.meta_\\d+.jbbinst")){
                executableName = file.replaceAll("\\.meta_\\d+.jbbinst","");
                if((glblAppName != null) && !glblAppName.equals(executableName)){
                    Logger.warn("Executable name " + executableName + " differs from global name " + glblAppName + " in " + file);
                    return false;
                }
                foundFiles++;
                allFiles.add(dir+"/"+file);
            }
        }
        assert(foundFiles == allFiles.size());
        if(foundFiles != cpuCnt){
            Logger.warn("Number of jbbcoll files " + foundFiles + " do not match with " + caseString);
            return false;
        }

        boolean retValue = true;
        it = allFiles.iterator();
        while(it.hasNext()){
            String path = (String)it.next();
            String appName = null;
            boolean isEmpty = true;
            Logger.inform("Reading trace " + path);
            try {
                BufferedReader inputFile = new BufferedReader(new FileReader(path));
                String line = null;
                int lineNumber = 0;
                while ((line = inputFile.readLine()) != null) {
                    lineNumber++;
                    String cleanedLine = Util.cleanComment(line);
                    cleanedLine = Util.cleanWhiteSpace(cleanedLine,false);
                    if(cleanedLine.length() > 0){
                        if(cleanedLine.matches("^\\d+.*")){
                            isEmpty = false;
                            break;
                        }
                    } else if(line.matches("^#.*=.*")){
                        String[] tokens = line.split("=");
                        tokens[1] =  Util.cleanWhiteSpace(tokens[1]);
                        if(line.matches("^#.*appname.*=.*")){
                            Logger.debug(line);
                            appName = tokens[1].replaceAll(".*\\/","");
                        }
                    }
                }
                inputFile.close();
            } catch (Exception e){
                Logger.error(e, "Can not read from " + path);
                return false;
            }

            if((glblAppName != null) && !glblAppName.equals(appName)){
                Logger.warn(appName + " in trace file " + path + " does not match the global name " + glblAppName + " for " + caseString);
                retValue = false;
            }
            if((appName != null) && !appName.equals(executableName)){
                Logger.warn(executableName + " in name of trace " + path + " does not match the global name " + appName + " for " + caseString);
                retValue = false;
            }
            if(isEmpty){
                Logger.warn("The trace " + path + " does not have any valid BB execution information for " + caseString);
                retValue = false;
            }

            if(!retValue){
                break;
            }
        }
        return retValue;
    }
    boolean processSimColl(String dir){
        if(dir == null){
            Logger.warn("No jbbcoll directory for trace verification for " + caseString);
            return true; 
        }

        LinkedList allFiles = new LinkedList();
        Logger.inform("Verifying jbbcoll directory for " + dir);
        int cpuCnt = getCpuCount();
        String executableName = null;
        String staticFilePath = null;
        LinkedList result = LinuxCommand.ls(dir);
        Iterator it = result.iterator();
        int foundFiles = 0;
        while(it.hasNext()){
            String file = (String)it.next();
            file = Util.cleanWhiteSpace(file);
            if(file.matches(".*\\.phase\\.\\d+\\.meta_\\d+\\.\\d+\\.siminst")){
                executableName = file.replaceAll("\\.phase\\.\\d+\\.meta_\\d+\\.\\d+\\.siminst","");
                if((glblAppName != null) && !glblAppName.equals(executableName)){
                    Logger.warn("Executable name " + executableName + " differs from global name " + glblAppName + " in " + file);
                    return false;
                }
                foundFiles++;
                allFiles.add(dir+"/"+file);
            }
        }
        assert(foundFiles == allFiles.size());
        if(foundFiles != cpuCnt){
            Logger.warn("Number of simcoll files " + foundFiles + " do not match with " + caseString);
            return false;
        }

        boolean retValue = true;
        it = allFiles.iterator();
        while(it.hasNext()){
            String path = (String)it.next();
            String appName = null;
            boolean isEmpty = true;
            int totalHiers = 0;
            Logger.inform("Reading trace " + path);
            try {
                BufferedReader inputFile = new BufferedReader(new FileReader(path));
                String line = null;
                int lineNumber = 0;
                while ((line = inputFile.readLine()) != null) {
                    lineNumber++;
                    String cleanedLine = Util.cleanComment(line);
                    cleanedLine = Util.cleanWhiteSpace(cleanedLine,false);
                    if(cleanedLine.length() > 0){
                        if(cleanedLine.matches("^block\\s+\\d+.*")){
                            isEmpty = false;
                            break;
                        }
                    } else if(line.matches("^#.*=.*")){
                        String[] tokens = line.split("=");
                        tokens[1] =  Util.cleanWhiteSpace(tokens[1]);
                        if(line.matches("^#.*appname.*=.*")){
                            Logger.debug(line);
                            appName = tokens[1].replaceAll(".*\\/","");
                        } else if(line.matches("^#.*sysid\\d+.*=.*")){
                            Logger.debug(line);
                            totalHiers++;
                        }
                        // Note that if you want to add more tests about sampling on/off, intervals
                        // of sampling or any additional information given in the comments,
                        // you need to add here
                    }
                }
                inputFile.close();
            } catch (Exception e){
                Logger.error(e, "Can not read from " + path);
                return false;
            }

            if((glblAppName != null) && !glblAppName.equals(appName)){
                Logger.warn(appName + " in trace file " + path + " does not match the global name " + glblAppName + " for " + caseString);
                retValue = false;
            }
            if((appName != null) && !appName.equals(executableName)){
                Logger.warn(executableName + " in name of trace " + path + " does not match the global name " + appName + " for " + caseString);
                retValue = false;
            }
            if(isEmpty){
                Logger.warn("The trace " + path + " does not have any valid cache simulation information for " + caseString);
                retValue = false;
            }
            if(totalHiers < TotalMemoryHiers){
                Logger.warn("The trace " + path + " does not have enough memory hierarchy info" + caseString + 
                            ". It is " + totalHiers + " and should be > " + TotalMemoryHiers);
                retValue = false;
            }

            if(!retValue){
                break;
            }
        }
        return retValue;
    }

    // IMPORTANT : The order of file processing should be kep as
    // processJbbInst processSimInst processJbbColl processSimColl
    boolean readAndCompare(String jbbInstdir,String simInstdir,String jbbColldir,String simColldir){
        Logger.debug("Dirs : [\n\t" + jbbInstdir + ",\n\t" + simInstdir + ",\n\t" + jbbColldir + ",\n\t" + simColldir + "\n]");

        glblAppName = null; glblAppSize = 0;

        boolean check = processJbbInst(jbbInstdir);
        if(!check) { Logger.warn("Error in jbbinst files checking " + caseString); return check; }
        check = processSimInst(simInstdir);
        if(!check) { Logger.warn("Error in siminst files checking " + caseString); return check; }

        check = processJbbColl(jbbColldir);
        if(!check) { Logger.warn("Error in jbbcoll files checking " + caseString); return check; }
        check = processSimColl(simColldir);
        if(!check) { Logger.warn("Error in simcoll files checking " + caseString); return check; }

        return true;

    }
        // jbbinst.static empty??
        // jbbcoll.meta*jbbinst empty??
        // siminst.static empty??
        // simcoll.meta*siminst empty? cache structures? 

    boolean consistencyCheck(){

        String jbbInstdir = (String)typeToDir.get("jbbinst");
        String simInstdir = (String)typeToDir.get("siminst");
        String jbbColldir = (String)typeToDir.get("jbbcoll");
        String simColldir = (String)typeToDir.get("simcoll");
        if(simInstdir != null){
            simInstdir += "/p01";
        }
        if(simColldir != null){
            simColldir += "/p01";
        }
        return readAndCompare(jbbInstdir,simInstdir,jbbColldir,simColldir);
    }
}

public class TraceCheck implements CommandLineInterface {

    OptionParser optionParser;
    String       pmacinstDirectory;
    HashMap      testCaseStrs;
    boolean      noStopSet;

    static final String[] ALL_OPTIONS = {
        "help:?",
        "version:?",
        "nostop:?",
        "pmacinst_dir:s",
        "config:s"
    };
    static final String SEPERATOR = "----------------------- VERIFICATION & CHECK --------------------------------";

    static LinkedList selectDirsUnder(String dir){
        LinkedList retValue = null;
        LinkedList result = LinuxCommand.ls(dir);
        if(result != null){
            retValue = new LinkedList();
            Iterator it = result.iterator();
            while(it.hasNext()){
                String file = (String)it.next();
                file = Util.cleanWhiteSpace(file);
                if(Util.isDirectory(dir + "/" + file) && 
                   file.matches(".*_.*_\\d+$")){
                    retValue.add(file);
                } else {
                    Logger.warn("Ignoring " + file + " under " + dir + " as NOT a trace directory");
                }
            }
        }
        if((retValue != null) && (retValue.size() == 0)){
            retValue = null;
        }
        return retValue;
    }

    boolean identifyAllCases(){
        assert(pmacinstDirectory != null);
        Logger.inform("BEGIN >> " + SEPERATOR);
        Logger.inform("Analyzing under " + pmacinstDirectory + " to find possible test cases with traces");
        for(int i=0;i<TraceRecord.validTraceSubdirs.length;i++){
            String pathToDir = pmacinstDirectory + "/" + TraceRecord.validTraceSubdirs[i];
            LinkedList dirList = selectDirsUnder(pathToDir);
            if(dirList == null){
                continue;
            }
            Iterator it = dirList.iterator();
            while(it.hasNext()){
                String tcDir = (String)it.next();
                tcDir = Util.cleanWhiteSpace(tcDir);
                Logger.debug("trace dir to process : " + pathToDir + "/" + tcDir);
                if(testCaseStrs.get(tcDir) == null){
                    testCaseStrs.put(tcDir,new TraceRecord(tcDir));
                }
                TraceRecord entry = (TraceRecord)testCaseStrs.get(tcDir);
                assert (entry != null);
                entry.setTraceDir(pathToDir + "/" + tcDir,TraceRecord.validTraceSubdirs[i]);
            }
        }
        return true;
    }

    public TraceCheck(){
        optionParser = new OptionParser(ALL_OPTIONS,this);
        pmacinstDirectory = null;
        testCaseStrs = new HashMap();
        noStopSet = false;
    }

    public void setNoStop(){ noStopSet = true; }

    public boolean setPmacinstDir(String dir){
        pmacinstDirectory = dir;
        if(pmacinstDirectory == null){
            Logger.warn("The pmacinst_dir directory argument is null");
            return false;
        }
        if(!Util.isDirectory(pmacinstDirectory)){
            Logger.warn("The pmacinst_dir directory " + pmacinstDirectory + " does not exist or not a directory");
            return false;
        }
        return identifyAllCases();
    }

    public boolean consistencyCheck(){
        return consistencyCheck(null,null,0);
    }

    public boolean consistencyCheck(String app,String dset,int cpu){
        Logger.inform(SEPERATOR);
        Logger.inform("Consistency check under " + pmacinstDirectory + " for trace check/verification");
        boolean retValue = true;
        Iterator it = testCaseStrs.keySet().iterator();
        while(it.hasNext()){
            String tcDir = (String)it.next();
            TraceRecord entry = (TraceRecord)testCaseStrs.get(tcDir);
            String[] tokens = tcDir.split("_");
            assert (tokens.length == 3);
            int c = 0;
            if(Util.toInteger(tokens[2]) != null){
                c = Util.toInteger(tokens[2]).intValue();
            }
            if(((app == null) && (dset == null) && (cpu == 0)) ||
               (tokens[0].equals(app) && tokens[1].equals(dset) && (cpu == c))){
                Logger.inform("");
                Logger.inform("Verifing traces for " + tcDir);
                if(!entry.consistencyCheck()){
                    Logger.warn("Verification/Consistency for the case " + tcDir + " failed");
                    retValue = false;
                    if(!noStopSet){
                        break;
                    } else {
                        Logger.warn("**** IMPORTANT: Even though errors found, continue for --nostop use *******");
                    }

                }
            }
        }

        Logger.inform("END << " + SEPERATOR);
        return retValue;
    }

    public boolean verifyValues(HashMap values){
        String checkdir = (String)values.get("pmacinst_dir");
        if(checkdir != null){
            if(!Util.isDirectory(checkdir)){
                Logger.error("The pmacinst_dir directory " + checkdir + " does not exist");
            }
        } else {
            Logger.error("The pmacinst_dir directory option is required");
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

        Logger.inform("Verifying the options");
        optionParser.verify();

        Logger.inform("Pmacinst tracers will be looked under directory is " +
                      (String)optionParser.getValue("pmacinst_dir"));
        if(!setPmacinstDir((String)optionParser.getValue("pmacinst_dir"))){
            Logger.warn("There is an error in case identifications");
            return false;
        }
        assert (pmacinstDirectory != null);

        Logger.inform("All checks succeeded for identification");

        noStopSet = optionParser.getValue("nostop") != null;

        if(!consistencyCheck()){
            Logger.warn("!!!!!!!!FATAL: There are ERROR(s) in consistency check");
            return false;
        }
        return true;
    }

    public static void main(String argv[]) {
        try {
            TraceCheck traceCheck = new TraceCheck();
            boolean check = traceCheck.run(argv);
            if(check){
                Logger.inform("\n*** DONE *** SUCCESS *** SUCCESS *** SUCCESS *****************\n");
            }
        } catch (Exception e) {
            Logger.error(e, "*** FAIL *** FAIL *** FAIL Exception *****************\n");
        }
    }

    static final String helpString =
     "[Basic Params]:\n" +
     "    --help                                 : Print a brief help message\n" +
     "    --version                              : Print the version and exit\n" +
     "[Script Params]:\n" +
     "    --pmacinst_dir path/with/pmacTRACE     : directory where traces are stored during tracing\n" +
     "    --nostop                               : continue printing error messages rather than fail at first\n" +
     "[Other Params]:\n" +
     "    --config      /path/etc/con.txt        : Path to the PSaPP config file.\n" +
     "                                             Not needed for j scripts under bin directory.\n";
}
