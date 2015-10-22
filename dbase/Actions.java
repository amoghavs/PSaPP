package PSaPP.dbase;
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

import PSaPP.util.*;
import java.util.*;
import java.io.*;

public final class Actions implements CommandLineInterface {

    static final String fieldSeperator = ",";
    static final String[] actionList = {"add", "list"};
    static final int InvalidType = -1;
    public static final String[] typeList = {"TestCaseData", "BaseResource", "MemoryBenchmarkData", "NetworkBenchmarkData",
        "MachineProfile", "DefaultProfile", "PredictionGroup", "ActualRuntime",
        "PredictionRun", "MachWMemoryProfile", "TraceStatus", "PhaseStatus",
        "PredictionResult", "CacheStructures"};
    static final int[] typeTokenCount = {6, 8, 0, 8, 5, 2, 2, 3, 14, 0, 27, 12, 14, 0};
    public static final String[] typeTokenTitles = {"agency,project,round,application,dataset,cpucount",
        "center,vendor,cpu,core,corepernode,frequency,FPpercycle,name",
        "BwCalcType,level_count,(cycs,bws,exps,pens) or (bws,taus,betas)",
        "netbw,netlat,nodebw,nodelat,corepernode,bus,inplink,outlink",
        "resource_dbid,memorybench_dbid,networkbench_dbid,sysidx,comment",
        "resource_dbid,profile_dbid",
        "name,comment",
        "testcase_dbid,resource_dbid,time",
        "pred_group,base_profile,machine_profile,testcase_dbid,memory_time,"
        + "ratio,predicted_time,simulation_comp_time,sim_type,sim_mod,"
        + "ratio_meth,use_sim_memtime,short_name,date",
        "resource_dbid,sysidx,memorybench details",
        "testcase_dbid,"
        + "exdate,exmach,exuser,exfile,extime,"
        + "psdate,psmach,psuser,psfile,pstime,"
        + "dsdate,dsmach,dsuser,dsfile,dstime,"
        + "jidate,jimach,jiuser,jifile,jitime,"
        + "jcdate,jcmach,jcuser,jcfile,jctime,"
        + "num_phases",
        "testcase_dbid,phaseno,sidate,simach,siuser,sifile,sitime,"
        + "scdate,scmach,scuser,scfile,sctime",
        "shortname,project,application,dataset,cpu_count,machine_profile,"
        + "base_resource,official_name,date_run,memory_time,ratio,"
        + "predicted_runtime,actual_runtime,relative_error",
        "l1_size,l2_size,l3_size,l1_associativity,l2_associativity,l3_associativity,"
        + "l1_linesize,l2_linesize,l3_linesize,l1_replacement_policy,l2_replacement_policy,"
        + "l3_replacement_policy,comments"};
    OptionParser optionParser;
    Database dataBase;
    BufferedReader inputFile;
    static boolean readConfig = false;
    static final String[] ALL_OPTIONS = {
        "help:?",
        "version:?",
        "database:s",
        "action:s",
        "type:s",
        "input:s"
    };

    public Actions() {
        optionParser = new OptionParser(ALL_OPTIONS, this);
        dataBase = null;
        inputFile = null;
    }

    public Actions(Database dbase, BufferedReader iFile) {
        dataBase = dbase;
        inputFile = iFile;
    }

    int stringToType(String str) {
        int retValue = InvalidType;
        if (str.startsWith("PredictionResult")) {
            retValue = 12;
        } else {
            for (int i = 0; i < typeList.length; i++) {
                if (str.equals(typeList[i])) {
                    retValue = i;
                }
            }
        }
        return retValue;
    }

    public boolean verifyValues(HashMap values) {

        String action = (String) values.get("action");
        String type = (String) values.get("type");

        if (action == null) {
            Logger.error("--action is a required argument");
            return false;
        }
        boolean found = false;
        for (int i = 0; i < actionList.length; i++) {
            if (action.equals(actionList[i])) {
                found = true;
                break;
            }
        }
        if (!found) {
            Logger.error("Action " + action + " is invalid");
            return false;
        }

        if (type == null) {
            Logger.error("--type is a required argument");
            return false;
        }
        found = false;
        for (int i = 0; i < typeList.length; i++) {
            if (type.equals(typeList[i])) {
                found = true;
                break;
            }
        }
        if (type.startsWith("PredictionResult")) {
            found = true;
        }
        if (!found) {
            Logger.error("Type " + type + " is invalid");
            return false;
        }

        String file = (String) values.get("input");
        if (file == null) {
            Logger.inform("Using the standard input for data");
        } else {
            Logger.inform("Using the input file " + file);
            if (!Util.isFile(file)) {
                Logger.error("Input file " + file + " does not exist");
                return false;
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

    public void printUsage(String str) {
        System.err.println(helpString);
        String allStr = "usage :\n";
        for (int i = 0; i < ALL_OPTIONS.length; i++) {
            allStr += ("\t--" + ALL_OPTIONS[i] + "\n");
        }
        allStr += ("\n" + str);
        //System.err.println(allStr);
    }

    String[] readInputLines(String type) {
        if (type.contentEquals("CacheStructures")) {
            return CacheStructuresParser.parse(inputFile);
        }
        String[] retValue = null;
        LinkedList lines = new LinkedList();
        int lineNumber = 0;
        try {
            String line = null;
            while ((line = inputFile.readLine()) != null) {
                String origLine = line;
                lineNumber++;
                line = Util.cleanComment(line);
                line = Util.cleanWhiteSpace(line, false);
                if (line.length() > 0) {
                    lines.add(line);
                } else {
                    Logger.warn("Line " + lineNumber + " has no data : [" + origLine + "]");
                }
            }
            if (lines.size() > 0) {
                int idx = 0;
                retValue = new String[lines.size()];
                Iterator it = lines.iterator();
                while (it.hasNext()) {
                    retValue[idx++] = (String) it.next();
                }
            }
        } catch (Exception e) {
            Logger.warn("Line can not be read from input at " + lineNumber + "\n" + e);
        }
        return retValue;
    }

    public boolean run(String argv[]) {

        if (argv.length < 1) {
            optionParser.printUsage("");
            return false;
        }

        optionParser.parse(argv);
        if (optionParser.isHelp()) {
            optionParser.printUsage("");
            return false;
        }
        if (optionParser.isVersion()) {
            Logger.inform("The version is <this>", true);
            return true;
        }

        if (readConfig) {
            ConfigSettings.readConfigFile();
        }

        Logger.inform("Verifying the options");
        if (!optionParser.verify()) {
            Logger.error("Error in command line options");
            return false;
        }

        String dbasefile = (String) optionParser.getValue("database");
        if (dbasefile != null) {
            if (Util.isFile(dbasefile)) {
                Logger.inform("Working on already existing dbase " + dbasefile);
            } else {
                Logger.inform("Will work on new database " + dbasefile);
            }
            Logger.inform("Text based database is used");
            dataBase = new BinaryFiles(dbasefile);
        } else {
            dataBase = new Postgres();
            Logger.inform("Postgres database is used");
        }

        boolean status = dataBase.initialize();
        if (!status) {
            Logger.error("Database can not be initialized");
        }

        String action = (String) optionParser.getValue("action");
        String type = (String) optionParser.getValue("type");
        String file = (String) optionParser.getValue("input");

        assert ((action != null) && (type != null));
        assert ((file == null) || Util.isFile(file));

        if (action.equals("list") && (file != null)) {
            Logger.inform("input file " + file + " is ignored as it is only listing");
        }
        if (action.equals("list")) {
            inputFile = null;
        } else {
            try {
                if (file != null) {
                    inputFile = new BufferedReader(new FileReader(file));
                } else {
                    inputFile = new BufferedReader(new InputStreamReader(System.in));
                }
            } catch (Exception e) {
                Logger.error("Input file " + file + " can not be opened\n" + e);
                return false;
            }
        }

        boolean retValue = true;

        int typeIdx = stringToType(type);
        if (typeIdx == InvalidType) {
            Logger.error("Unknown action type " + type);
            return false;
        }

        String fieldTitles = typeTokenTitles[typeIdx];

        if (action.equals("list")) {
            Integer inpGroup = null;
            if (typeIdx == 12) {
                type = type.replaceAll("\\s+", "");
                if (type.matches("PredictionResult:\\d+")) {
                    String[] tmpTokens = type.split(":");
                    inpGroup = Util.toInteger(tmpTokens[1]);
                    assert (inpGroup != null);
                } else {
                    Logger.error("Value needs to be --type PredictionResult:<pred_group_id>");
                    return false;
                }
            }
            int tokenCountCheck = typeTokenCount[typeIdx];
            switch (typeIdx) {
                case 0:
                    dataBase.listTestCase(fieldTitles, tokenCountCheck);
                    break;
                case 1:
                    dataBase.listBaseResource(fieldTitles, tokenCountCheck);
                    break;
                case 2:
                    dataBase.listMemoryBenchmarkData(fieldTitles, tokenCountCheck);
                    break;
                case 3:
                    dataBase.listNetworkBenchmarkData(fieldTitles, tokenCountCheck);
                    break;
                case 4:
                    dataBase.listMachineProfile(fieldTitles, tokenCountCheck);
                    break;
                case 5:
                    dataBase.listDefaultProfile(fieldTitles, tokenCountCheck);
                    break;
                case 6:
                    dataBase.listPredictionGroup(fieldTitles, tokenCountCheck);
                    break;
                case 7:
                    dataBase.listActualRuntime(fieldTitles, tokenCountCheck);
                    break;
                case 8:
                    dataBase.listPredictionRun(fieldTitles, tokenCountCheck);
                    break;
                case 9:
                    dataBase.listMachineProfile(fieldTitles, tokenCountCheck);
                    break;
                case 10:
                    dataBase.listTraceStatus(fieldTitles, tokenCountCheck);
                    break;
                case 11:
                    dataBase.listPhaseStatus(fieldTitles, tokenCountCheck);
                    break;
                case 12:
                    dataBase.listPredictionResult(fieldTitles, tokenCountCheck, inpGroup.intValue());
                    break;
                case 13:
                    dataBase.listCacheStructures();
                    break;
            }
            retValue = true;
        } else {
            Logger.plain("fields< " + fieldTitles + " >");
            switch (typeIdx) {
                case 10:
                    Logger.error("Trace statuses can not be added right now");
                    break;
                case 11:
                    Logger.error("Phase statuses can not be added right now");
                    break;
                case 12:
                    Logger.error("Prediction results can not be added right now");
                    break;
                default:
            }

            int mindbid = (int) 0x7fffffff;
            int maxdbid = -1;

            String[] allLines = readInputLines(type);
            if (allLines != null) {
                for (int i = 0; i < allLines.length; i++) {
                    String line = allLines[i];
                    String[] tokens = line.split(fieldSeperator);
                    for (int j = 0; j < tokens.length; j++) {
                        tokens[j] = Util.cleanWhiteSpace(tokens[j], false);
                    }
                    int levelCount = 0;
                    int tokenCountCheck = typeTokenCount[typeIdx];
                    if (tokenCountCheck == 0) {
                        if (typeIdx == 2) {
                            levelCount = Util.toInteger(tokens[1]).intValue();
                            String restOfSig = Util.getMemoryProfileSignature(tokens[0], new Integer(levelCount));
                            if (restOfSig == null) {
                                Logger.warn("[" + line + "] has to have a known BW method and valid level count");
                                continue;
                            } else {
                                Logger.debug("Signature for " + tokens[0] + "," + levelCount + " is " + restOfSig);
                                tokenCountCheck = 2 + restOfSig.length();
                            }
                        } else if (typeIdx == 9) {
                            levelCount = Util.toInteger(tokens[4]).intValue();
                            String restOfSig = Util.getMemoryProfileSignature(tokens[3], new Integer(levelCount));
                            if (restOfSig == null) {
                                Logger.warn("[" + line + "] has to have a known BW method and valid level count");
                                continue;
                            } else {
                                Logger.debug("Signature for " + tokens[3] + "," + levelCount + " is " + restOfSig);
                                tokenCountCheck = 3 + restOfSig.length() + 2;
                            }
                        } else if (typeIdx == 13) {
                            levelCount = CacheStructuresParser.getCacheLevels(i);
                            if (levelCount == 1) {
                                tokenCountCheck = 5;
                            } else if (levelCount == 2) {
                                tokenCountCheck = 9;
                            } else if (levelCount == 3) {
                                tokenCountCheck = 13;
                            }
                        }
                    }
                    if (tokens.length != tokenCountCheck) {
                        Logger.warn("[" + line + "] has to be " + tokenCountCheck + " tokens, not " + tokens.length);
                        continue;
                    }
                    int dbid = Database.InvalidDBID;
                    switch (typeIdx) {
                        case 0:
                            dbid = dataBase.addTestCase(tokens);
                            break;
                        case 1:
                            dbid = dataBase.addBaseResource(tokens);
                            break;
                        case 2:
                            dbid = dataBase.addMemoryBenchmarkData(tokens);
                            break;
                        case 3:
                            dbid = dataBase.addNetworkBenchmarkData(tokens);
                            break;
                        case 4:
                            dbid = dataBase.addMachineProfile(tokens);
                            break;
                        case 5:
                            dbid = dataBase.addDefaultProfile(tokens);
                            break;
                        case 6:
                            dbid = dataBase.addPredictionGroup(tokens);
                            break;
                        case 7:
                            dbid = dataBase.addActualRuntime(tokens);
                            break;
                        case 8:
                            dbid = dataBase.addPredictionRun(tokens);
                            break;
                        case 9:
                            dbid = dataBase.addMachWMemoryProfile(tokens);
                            break;
                        case 13:
                            dbid = dataBase.existsCacheStructures(levelCount, tokens);
                            if (dbid == Database.InvalidDBID) {
                                dbid = dataBase.addCacheStructures(levelCount, tokens);
                            }
                            break;
                        default:
                            Logger.error("Something wrong in use of the type");
                            return false;
                    }

                    if (dbid == Database.InvalidDBID) {
                        Logger.warn("Update for line failed : [" + line + "]");
                    } else {
                        if (mindbid > dbid) {
                            mindbid = dbid;
                        }
                        if (maxdbid < dbid) {
                            maxdbid = dbid;
                        }
                        Logger.inform("dbid : " + dbid + " <--- " + line);
                    }
                }
            } else {
                retValue = false;
            }
            if (maxdbid >= 0) {
                Logger.inform("RANGE [ " + mindbid + "-" + maxdbid + " ]");
            } else {
                Logger.inform("RANGE [ NONE ]");
                retValue = false;
            }
        }

        if ((inputFile != null) && (file == null)) {
            try {
                inputFile.close();
            } catch (Exception e) {
                Logger.warn("Can not close " + file + "\n" + e);
                retValue = false;
            }
        }

        if (action.equals("add")) {
            status = dataBase.commit();
            if (!status) {
                Logger.warn("Can not terminate the database");
                retValue = false;
            }
        }

        return retValue;
    }

    public static void main(String argv[]) {
        try {
            Actions actions = new Actions();
            readConfig = true;
            boolean check = actions.run(argv);
            if (check) {
                Logger.inform("\n*** DONE *** SUCCESS *** SUCCESS *** SUCCESS *****************\n");
            }
        } catch (Exception e) {
            Logger.error(e, "*** FAIL *** FAIL *** FAIL Exception *****************\n");
        }
    }
    static final String helpString =
            "[Basic Params]:\n"
            + "    --help                   : Print a brief help message\n"
            + "    --version                : Print the version and exit\n"
            + "[DB Params]:\n"
            + "    --action   add           : The action to perform on the database [REQ]\n"
            + "                               Allowed = list, add\n"
            + "    --type     BaseResource  : The data type to display [REQ]\n"
            + "                               Allowed = TestCaseData,BaseResource,CacheStructures,\n"
            + "                                         MemoryBenchmarkData,NetworkBenchmarkData,\n"
            + "                                         MachineProfile,DefaultProfile,PredictionGroup,ActualRuntime,\n"
            + "                                         PredictionRun,MachWMemoryProfile,\n"
            + "                                         TraceStatus,PhaseStatus,PredictionResult (for list action only)\n"
            + "    --input    /path/inp.txt : The path to the file containing input data (for \"add\" only)\n"
            + "    --database /path/db.bin  : The path to the database file if binary files are used.\n"
            + "                               Default is to use Postgres.\n";

    public boolean addRecordsFromGUI(String type) {

        boolean retValue = true;

        int typeIdx = stringToType(type);
        if (typeIdx == InvalidType) {
            Logger.warn("Unknown action type " + type);
            return false;
        }
        switch (typeIdx) {
            case 10:
                Logger.warn("Trace statuses can not be added right now");
                return false;
            case 11:
                Logger.warn("Phase statuses can not be added right now");
                return false;
            case 12:
                Logger.warn("Prediction results can not be added right now");
                return false;
            default:
        }

        int mindbid = (int) 0x7fffffff;
        int maxdbid = -1;

        String[] allLines = readInputLines(type);
        if (allLines != null) {
            for (int i = 0; i < allLines.length; i++) {
                String line = allLines[i];
                String[] tokens = line.split(fieldSeperator);
                for (int j = 0; j < tokens.length; j++) {
                    tokens[j] = Util.cleanWhiteSpace(tokens[j], false);
                }
                int tokenCountCheck = typeTokenCount[typeIdx];
                if (tokenCountCheck == 0) {
                    if (typeIdx == 2) {
                        Integer levelCount = Util.toInteger(tokens[1]);
                        String restOfSig = Util.getMemoryProfileSignature(tokens[0], levelCount);
                        if (restOfSig == null) {
                            Logger.warn("[" + line + "] has to have a known BW method and valid level count");
                            continue;
                        } else {
                            Logger.debug("Signature for " + tokens[0] + "," + levelCount + " is " + restOfSig);
                            tokenCountCheck = 2 + restOfSig.length();
                        }
                    } else if (typeIdx == 9) {
                        Integer levelCount = Util.toInteger(tokens[3]);
                        String restOfSig = Util.getMemoryProfileSignature(tokens[2], levelCount);
                        if (restOfSig == null) {
                            Logger.warn("[" + line + "] has to have a known BW method and valid level count");
                            continue;
                        } else {
                            Logger.debug("Signature for " + tokens[2] + "," + levelCount + " is " + restOfSig);
                            tokenCountCheck = 2 + restOfSig.length() + 2;
                        }
                    }
                }
                if (tokens.length != tokenCountCheck) {
                    Logger.warn("[" + line + "] has to be " + tokenCountCheck + " tokens, not " + tokens.length);
                    continue;
                }
                int dbid = Database.InvalidDBID;
                switch (typeIdx) {
                    case 0:
                        dbid = dataBase.addTestCase(tokens);
                        break;
                    case 1:
                        dbid = dataBase.addBaseResource(tokens);
                        break;
                    case 2:
                        dbid = dataBase.addMemoryBenchmarkData(tokens);
                        break;
                    case 3:
                        dbid = dataBase.addNetworkBenchmarkData(tokens);
                        break;
                    case 4:
                        dbid = dataBase.addMachineProfile(tokens);
                        break;
                    case 5:
                        dbid = dataBase.addDefaultProfile(tokens);
                        break;
                    case 6:
                        dbid = dataBase.addPredictionGroup(tokens);
                        break;
                    case 7:
                        dbid = dataBase.addActualRuntime(tokens);
                        break;
                    case 8:
                        dbid = dataBase.addPredictionRun(tokens);
                        break;
                    case 9:
                        dbid = dataBase.addMachWMemoryProfile(tokens);
                        break;
                    default:
                        Logger.warn("Something wrong in use of the type");
                        return false;
                }

                if (dbid == Database.InvalidDBID) {
                    Logger.warn("Update for line failed : [" + line + "]");
                } else {
                    if (mindbid > dbid) {
                        mindbid = dbid;
                    }
                    if (maxdbid < dbid) {
                        maxdbid = dbid;
                    }
                    Logger.inform("dbid : " + dbid + " <--- " + line);
                }
            }
        } else {
            retValue = false;
        }
        if (maxdbid >= 0) {
            Logger.inform("RANGE [ " + mindbid + "-" + maxdbid + " ]");
        } else {
            Logger.inform("RANGE [ NONE ]");
            retValue = false;
        }
        return retValue;
    }
}
