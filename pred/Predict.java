package PSaPP.pred;
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
import PSaPP.stats.*;
import PSaPP.data.*;
import PSaPP.web.*;
import PSaPP.sim.*;

public final class Predict implements CommandLineInterface {

    OptionParser optionParser;
    TestCase     testCase;
    Database     dataBase;
    Convolver    convolver;
    NetworkSim   networkSim;
    Statistics   statistics;
    private static final Object predictLock = new Object() {};

    static final String[] ALL_OPTIONS = {
        "help:?",
        "version:?",

        "direct_dir:s",

        "funding_agency:s",
        "project:s",
        "round:i",
        "application:s",
        "cpu_count:i",
        "dataset:s",

        "ratio:d",
        "ratio_method:s",
        "use_sim_memtime:?",
        "base_profile:i",
        "base_system:i",
        "machine_list:s",
        "profile_list:s",

        "noDim:?",
        "pwr:s",
        "network_simulator:s",
        "netsim_dir:s",
        "psins_model:s",

        "scratch:s",

        "user:s",
        "shortname:s",
        "prediction_group:i",

        "database:s",

        "sensitivity_study:s",

        "sblocks:s",

        "stats:?",
        "bwhist:?",
        "pwstats:?",
        "bb_list:s",
        "gpu_report:?",

        "netsim_suffix:s",

        "bw_multiplier:d",

        "use_dfp:?",
        "no_reports:?"
    };

    public Predict(){
        optionParser = new OptionParser(ALL_OPTIONS,this);
        testCase     = null;
        dataBase     = null;
        convolver    = null;
        networkSim   = null;
        statistics   = null;
    }

    String getNetworkSimExec(String simtype,String simdir){
        if(simtype == null){
            simtype = NetworkSim.defaultNetworkSim;
        }
        String defsimdir = null;
        String simexec = null;

        if(simtype.equals("psins")){
            defsimdir = ConfigSettings.getSetting("PSINS_PATH");
            simexec = "psins";
        } else if(simtype.equals("dimemas")){
            defsimdir = ConfigSettings.getSetting("DIMEMAS_PATH");
            simexec = "Dimemas";
        }
        if(simdir == null){
            simdir = defsimdir;
        }
        return (simdir + "/bin/" + simexec);
    }

    public boolean verifyValues(HashMap values){

        assert (testCase != null) : "testcase is not allocated yet";
        if(!testCase.verify()){
            Logger.error("Test case " + testCase + " is not valid");
            return false;
        }

        String dbasefile = (String)values.get("database");
        if((dbasefile != null) && !Util.isFile(dbasefile)){
            Logger.error("Database file " + dbasefile + " does not exist");
            return false;
        }
        String blockBWConf = (String)values.get("sblocks");
        if((blockBWConf != null) && !Util.isFile(blockBWConf)){
            Logger.error("Blocks BW config file " + blockBWConf + " does not exist");
            return false;
        }

        if(values.get("user") == null){
            values.put("user",LinuxCommand.username());
            Logger.inform("--user is not specified so using " + values.get("user"));
        }

        Integer group = (Integer)values.get("prediction_group");
        if(group == null){
            Logger.error("--prediction_group is a required argument");
            return false;
        }

        if(values.get("shortname") == null){
            Logger.error("--shortname is a required argument");
            return false;
        }

        String datadir = (String)values.get("direct_dir");
        if(datadir == null){
            Logger.error("--direct_dir is a required argument");
            return false;
        }
        if(!Util.isDirectory(datadir)){
            Logger.error("--direct_dir " + datadir + " does not exist");
            return false;
        }

        String scrdir = (String)values.get("scratch");
        if(scrdir == null){
            Logger.error("--scratch is a required argument");
            return false;
        }
        if(!Util.isDirectory(scrdir)){
            Logger.error("Scratch directory " + scrdir + " does not exist");
            return false;
        }

        Double ratio = (Double)values.get("ratio");
        if((ratio != null) && (ratio.doubleValue() <= 0.0)){
            Logger.error("Value for --ratio  " + ratio + " is invalid, needs to be > 0.0");
            return false;
        }

        String ratiomth = (String)values.get("ratio_method");
        if((ratiomth != null) && !Util.isValidRatioMethod(ratiomth)){
            Logger.error("Value for --ratio_method " + ratiomth + " is invalid");
            return false;
        }

        String netsim = (String)values.get("network_simulator");
        if(netsim == null){
            netsim = NetworkSim.defaultNetworkSim;
        }
        if(!Util.isValidNetworkSim(netsim)){
            Logger.error("Value for --network_simulator " + netsim + " is invalid");
            return false;
        }

        String netsimdir = (String)values.get("netsim_dir");
        String simexec = getNetworkSimExec(netsim,netsimdir);
        if(!Util.isFile(simexec)){
            Logger.error("Simulator executable does not exist " + simexec);
            return false;
        }

        String netmod = (String)values.get("psins_model");
        if((netmod != null) && !Util.isValidNetworkMod(netsim,netmod)){
            Logger.error("Value for --psins_model " + netmod + " is invalid");
            return false;
        }

        if((values.get("base_profile") == null) && (values.get("base_system") == null)){
            Logger.error("One of --base_profile and --base_system is required");
            return false;
        }

        String machinelist = (String)values.get("machine_list");
        String profilelist = (String)values.get("profile_list");

        if((machinelist == null) && (profilelist == null)){
            Logger.error("One of --machine_list and --profile_list is required");
            return false;
        }

        if(machinelist != null){
            Integer[] checklist = Util.machineList(machinelist);
            if(checklist == null){
                Logger.error("--machine_list does not have valid value");
                return false;
            }
        }

        if(profilelist != null){
            Integer[] checklist = Util.machineList(profilelist);
            if(checklist == null){
                Logger.error("--profile_list does not have valid value");
                return false;
            }
        }

        String sensString = (String)values.get("sensitivity_study");
        Sensitivity sensitivity = new Sensitivity(sensString);
        if((sensString != null) && !sensitivity.hasValidCases()){
            Logger.error("Sensitivity string [" + sensString + "] is invalid");
            return false;
        }
        
        Double bwMultiplier = (Double)values.get("bw_multiplier");
        if((bwMultiplier != null) && (bwMultiplier.doubleValue() <= 0.0)){
            Logger.error("Value for --bw_multiplier  " + bwMultiplier + " is invalid, needs to be > 0.0");
            return false;
        }
        return true;
    }

    public TestCase getTestCase(HashMap values) {
        String  app = (String)values.get("application");
        String  siz = (String)values.get("dataset");
        Integer cpu = (Integer)values.get("cpu_count");
        String  agy = (String)values.get("funding_agency");
        String  prj = (String)values.get("project");
        Integer rnd = (Integer)values.get("round");

        return new TestCase(app,siz,cpu,agy,prj,rnd);
    }
    public boolean isHelp(HashMap values) {
        return (values.get("help") != null);
    }
    public boolean isVersion(HashMap values) {
        return (values.get("version") != null);
    }
    public void printUsage(String str){
        System.err.println("\n" + str + "\n");
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
       
        ConfigSettings.readConfigFile();

        testCase = optionParser.getTestCase();
        assert (testCase != null);

        Logger.inform("Test case is " + testCase);

        Logger.inform("Verifying the options");
        if(!optionParser.verify()){
            Logger.error("Error in command line options");
            return false;
        }

        String dbasefile = (String)optionParser.getValue("database");
        if(dbasefile != null){
            assert Util.isFile(dbasefile);
            dataBase = new BinaryFiles(dbasefile);
        } else {
            dataBase = new Postgres();
        }
        boolean status = dataBase.initialize();
        if(!status){
            Logger.warn("Can not initialize the database");
            return false;
        }

        String user = (String)optionParser.getValue("user");
        String shortname = (String)optionParser.getValue("shortname");
        Integer group = (Integer)optionParser.getValue("prediction_group");

        assert (user != null) && (shortname != null) && (group != null);

        int predictionGroup = group.intValue();
        assert (predictionGroup >= 0);

        String scratchDir = (String)optionParser.getValue("scratch");
        assert ((scratchDir != null) && Util.isDirectory(scratchDir));

        String directDir = (String)optionParser.getValue("direct_dir");
        assert ((directDir != null) && Util.isDirectory(directDir));

        if(!dataBase.existsTestCase(testCase)){
            Logger.warn("Test case " + testCase + " does not exist in database");
            return false;
        }
        if(!dataBase.existsPredGroup(predictionGroup)){
            Logger.warn("Prediction group " + predictionGroup + " does not exist in database");
            return false;
        }

        List targetProfiles = new ArrayList();

        String targetMachStr = (String)optionParser.getValue("machine_list");
        String targetProfStr = (String)optionParser.getValue("profile_list");
        if(targetMachStr != null){
            Integer[] machList = Util.machineList(targetMachStr);
            assert (machList != null);
            for(int i=0;i<machList.length;i++){
                int machIdx = machList[i].intValue();
                if(!dataBase.existsBaseResource(machIdx)){
                    Logger.warn("Base resource " + machIdx + " does not exist in database");
                    return false;
                }
                int profIdx = dataBase.getDefaultPIdx(machIdx);
                if(!dataBase.existsProfile(profIdx)){
                    Logger.warn("Profile " + profIdx + " does not exist in database");
                    return false;
                }
                targetProfiles.add(new Integer(profIdx));
            }
        }
        if(targetProfStr != null){
            Integer[] profList = Util.machineList(targetProfStr);
            assert (profList != null);
            for(int i=0;i<profList.length;i++){
                int profIdx = profList[i].intValue();
                if(!dataBase.existsProfile(profIdx)){
                    Logger.warn("Profile " + profIdx + " does not exist in database");
                    return false;
                }
                targetProfiles.add(new Integer(profIdx));
            }
        }

        if(targetProfiles.size() == 0){
            Logger.inform("There is no target profile to simulate, exiting");
            return true;
        }

        String sensString = (String)optionParser.getValue("sensitivity_study");
        Sensitivity sensitivity = new Sensitivity(sensString);
        if(sensString != null){
            Logger.inform("Sensitivity study is aksed for");
            assert (sensitivity.hasValidCases());
            status = sensitivity.addProfiles(targetProfiles,optionParser.getValue("ratio") != null,dataBase);
            if(!status){
                Logger.warn("Some of the sensitivity data can not inserted in to the database");
            }
        } else {
            Logger.inform("NO sensitivity study is aksed for");
        }

        Logger.inform("There are " + targetProfiles.size() + " profiles for simulation");
        int[] targetProfileArray = new int[targetProfiles.size()];
        Iterator profIt = targetProfiles.iterator();
        int idx = 0;
        while(profIt.hasNext()){
            targetProfileArray[idx++] = ((Integer)profIt.next()).intValue();
        }

        Integer baseSystem  = (Integer)optionParser.getValue("base_system");
        Integer baseProfile = (Integer)optionParser.getValue("base_profile");
        assert ((baseSystem == null) || (baseProfile == null));

        int baseProfileIdx = -1;
        if(baseSystem != null){
            int baseSysIdx = baseSystem.intValue();
            if(!dataBase.existsBaseResource(baseSysIdx)){
                Logger.warn("Base resource " + baseSysIdx + " does not exist in database");
                return false;
            }
            baseProfileIdx =  dataBase.getDefaultPIdx(baseSysIdx);
        }
        if(baseProfile != null){
            baseProfileIdx = baseProfile.intValue();
        }
        assert (baseProfileIdx >= 0);

        if(!dataBase.existsProfile(baseProfileIdx)){
            Logger.warn("Profile " + baseProfileIdx + " does not exist in database");
            return false;
        }

        if(optionParser.getValue("stats") != null){
            statistics = new Statistics(testCase);
            if(optionParser.getValue("bwhist") != null){
                statistics.enableBWHistograms();
            }
            if(optionParser.getValue("pwr") != null){
                statistics.enablePWRHistograms();
            }
            if(optionParser.getValue("bb_list") != null){
                String blockListFile = (String)optionParser.getValue("bb_list");
                if((blockListFile != null) && !Util.isFile(blockListFile)){
                    Logger.error("Blocks list for times config file " + blockListFile + " does not exist");
                    return false;
                }
                statistics.enableBBTimeHistograms(blockListFile);
            }
            if(optionParser.getValue("bb_list") != null){
                String blockListFile = (String)optionParser.getValue("bb_list");
                if((blockListFile != null) && !Util.isFile(blockListFile)){
                    Logger.error("Blocks list for times config file " + blockListFile + " does not exist");
                    return false;
                }
                statistics.enableBBEnergyHistograms(blockListFile);
            }
        } else {
            if(optionParser.getValue("bwhist") != null){
                Logger.warn("--bwhist can not be used without --stats option");
                return false;
            }
            if(optionParser.getValue("bb_list") != null){
                Logger.warn("--bb_list can not be used without --stats option");
                return false;
            }
            if(optionParser.getValue("gpu_report") != null) {
                Logger.warn("--gpu_report can not be used without --stats option");
                return false;
            }
        }

        String ratioMethod = (String)optionParser.getValue("ratio_method");
        if(ratioMethod == null){
            ratioMethod = Convolver.defaultRatioMethod;
        }

        boolean skipConvolver = false;
        boolean useSimMemtime = (optionParser.getValue("use_sim_memtime") != null);

        if(optionParser.getValue("ratio") != null){
            Logger.inform("Ratio is given so skipping the convolver step.");
            Logger.inform("Ignoring the ratio_method,use_sim_memtime options.");
            skipConvolver = true;
            Double ratio = (Double)optionParser.getValue("ratio");
            convolver = new Convolver(testCase,baseProfileIdx,targetProfileArray,ratio.doubleValue(),
                                      directDir,scratchDir,user,shortname,predictionGroup,dataBase);
        } else {

            convolver = new Convolver(testCase,baseProfileIdx,targetProfileArray,
                                      ratioMethod,useSimMemtime,
                                      directDir,scratchDir,user,shortname,predictionGroup,dataBase);
        }
        if(optionParser.getValue("pwr") != null)
            convolver.enablePWR((String)optionParser.getValue("pwr"));

        convolver.setBlockVariations((String)optionParser.getValue("sblocks"));
        if(optionParser.getValue("use_dfp") != null){
            Logger.inform("--use_dfp is set. Will use dfPattern BW model if dfp block information is available under direct directory");
            convolver.useDfpIfExists();
        }

        Double bwMultiplier = (Double)optionParser.getValue("bw_multiplier");
        if(bwMultiplier != null){
            Logger.inform("An additional multiplier (" + bwMultiplier + ") for BW is given, IMPORTANT");
            convolver.setExtraBWMultiplier(bwMultiplier);
        }

        if(!skipConvolver){
            convolver.setStatistics(statistics);

            try {
                convolver.thread.start();
                convolver.thread.join();
            } catch (InterruptedException e){
                Logger.error(e, "Convolver thread run interrupted");
            }

            boolean check = false;
            if (convolver.getRunStatus() == PSaPPThread.THREAD_SUCCESS){
                check = true;
            }
            if(!check){
                Logger.warn("Convolver failed");
                return false;
            }

            if( optionParser.getValue("gpu_report") != null ) {
                writeGPUReports(targetProfileArray, directDir);
           }
        }

        boolean skipNetworkSim = false;

        String simtype = (String)optionParser.getValue("network_simulator");
        if(simtype == null){
            simtype = NetworkSim.defaultNetworkSim;
        }
        String simmod = (String)optionParser.getValue("psins_model");
        if(simmod == null){
            simmod = ConfigSettings.getSetting("PSINS_COMM_MODEL");
            if (simmod == null){
                simmod = NetworkSim.defaultNetworkMod;
            }
        }

        if(optionParser.getValue("noDim") != null){
            Logger.inform("No network simulation is requested so skipping the network simulation");
            Logger.inform("Ignoring the ratio,network_simulator,netsim_dir,psins_model options.");
            networkSim = null;
            skipNetworkSim = true;
        } else {
            String simexec = getNetworkSimExec(simtype,(String)optionParser.getValue("netsim_dir"));
            assert Util.isFile(simexec) : "The executable for network simulation does not exist";

            int[] convolverProfiles = convolver.getTargetProfiles(); 
            double[] convolverRatios = convolver.getTargetRatios();
            assert (convolverRatios != null);
            assert ((convolverRatios.length == targetProfileArray.length) && 
                    (convolverRatios.length == convolverProfiles.length));
            networkSim = new NetworkSim(testCase,baseProfileIdx,convolverRatios,convolverProfiles,
                                        simtype,simmod,simexec,
                                        directDir,scratchDir,user,shortname,predictionGroup,dataBase);

            String netsimSuffix = (String)optionParser.getValue("netsim_suffix");
            if(netsimSuffix != null){
                networkSim.setTraceFileSuffix(netsimSuffix);
            }
        }

        if(!skipNetworkSim){
            networkSim.setStatistics(statistics);

            try {
                networkSim.thread.start();
                networkSim.thread.join();
            } catch (InterruptedException e){
                Logger.error(e, "NetworkSim thread run interrupted");
            }

            boolean check = false;
            if (networkSim.getRunStatus() == PSaPPThread.THREAD_SUCCESS){
                check = true;
            }
            if(!check){
                Logger.warn("Network simulation run failed");
                return false;
            }
        }

        if(!skipNetworkSim || !skipConvolver){

            assert (convolver != null);

            double[] convolverRatios = convolver.getTargetRatios();
            assert (convolverRatios != null);

            double[] targetCompTimes = null;
            if(!skipConvolver){
                targetCompTimes = convolver.getTargetMetasimNumbers();
                assert (targetCompTimes.length == convolverRatios.length);
            }

            double[] targetPredTimes = null;
            double[] targetRepresentCompTimes = null;
            if(!skipNetworkSim){
                targetPredTimes = networkSim.getPredictedTimes();
                targetRepresentCompTimes = networkSim.getRepresentCompTimes(ratioMethod);
                assert (targetPredTimes.length == convolverRatios.length) && 
                       (targetRepresentCompTimes.length == convolverRatios.length);
            }else{ //LAURA ADDED if skipping network then print stats
                if(statistics != null){
                    String outputDir = Predict.makeScratchDirs(scratchDir,predictionGroup,testCase);
        
                    if(outputDir == null){
                        Logger.warn("Can not make " + outputDir + " for runs, so no statistics is written");
                    } else {
                        statistics.print(outputDir);
                    }
                }
            }

            Date date = new Date();
            int testDbid = dataBase.getDbid(testCase);
            assert (testDbid != Database.InvalidDBID);

            for(int i=0;i<targetProfileArray.length;i++){

                double tmpCompTime = 0.0;
                double tmpPredTime = 0.0;
                double tmpRepsTime = 0.0;

                if(!skipConvolver){
                    assert (targetCompTimes != null);
                    tmpCompTime = targetCompTimes[i];
                }


                if(!skipNetworkSim){
                    assert (targetPredTimes != null);
                    tmpPredTime = targetPredTimes[i];
                    tmpRepsTime = targetRepresentCompTimes[i];
                }

                String[] runFields = Database.generateRunRecord(predictionGroup,
                                                    baseProfileIdx,targetProfileArray[i],testDbid,
                                                    tmpCompTime,convolverRatios[i],
                                                    tmpPredTime,tmpRepsTime,
                                                    simtype,simmod,
                                                    ratioMethod,useSimMemtime,
                                                    shortname,date);

                int dbid = dataBase.addPredictionRun(runFields);
                if(dbid == Database.InvalidDBID){
                    Logger.warn("Prediction run can not be inserted into the database");
                }
            }
        } else {
            Logger.inform("No entry in prediction runs as there is no runs");
        }

        boolean reports = optionParser.getValue("no_reports") != null ? false : true;
        if (reports){
            if (skipNetworkSim){
                Logger.warn("Skipped network simulation, but asking for report!?! Can't do it");
            } else {
                /* run the web synopsis tool */
                String psinsOutDir = networkSim.getSynopsisDirectory();
                
                SimReport simReport;
                if(psinsOutDir == null){
                    Logger.error("Can not make directory " + psinsOutDir + " for runs, so no report is done");
                    return false;
                }
                Logger.inform("Sending output from " + psinsOutDir + " to SimReport");
                TraceDB tracedb = new TraceDB(directDir + "/" + Convolver.processedDirName, this.testCase, null);
                simReport = new SimReport(psinsOutDir, testCase, dataBase, baseProfileIdx, 0.0, tracedb, false);
                try{
                    if (!simReport.run(targetProfileArray)){
                        Logger.error("SimReport return false");
                        return false;
                    }
                }
                catch(Exception e) {
                    Logger.error(e, "Exception caught while running simReport");
                return false;
                }
            }
        }


        status = dataBase.commit();
        if(!status){
            Logger.warn("Can not terminate the database");
            return false;
        }
        //LAURA added
        //if(statistics != null){
            //String outputDir = Predict.makeScratchDirs(scratchDir,predictionGroup,testCase);
            //
            //if(outputDir == null){
                //Logger.warn("Can not make " + outputDir + " for runs, so no statistics is written");
            //} else {
                //statistics.print(outputDir);
            //}
        //}

        return true;
    }

    public boolean writeGPUReports(int[] targetProfileArray, String directDir) {

        TraceDB tracedb = new TraceDB(directDir + "/" + Convolver.processedDirName, this.testCase, null);
        if( tracedb == null ) {
            return false;
        }

        Set<Function> funcs = tracedb.getFunctionSummaryGPU();
        if( funcs == null ) {
            return false;
        }

        String gpuDir = directDir + "/" + Convolver.processedDirName + "/gpu_report/";
        if( !Util.mkdir(gpuDir) ) {
            return false;
        }

        for( int i = 0; i < targetProfileArray.length; ++i ) {
            int machineProfile = targetProfileArray[i];
            int baseResource = this.dataBase.getBaseResource(machineProfile);
            int sysid = this.dataBase.getCacheSysId(machineProfile);
            int memProfIdx = this.dataBase.getMemoryPIdx(machineProfile);

            Map<String, Double> funcTimes = this.statistics.getFunctionTimes(sysid, memProfIdx);
            assert( funcTimes != null );

            String filename = gpuDir + "/" + this.testCase.shortName() + "_br" +
                baseResource + "_pr" + machineProfile + ".gpu";

            try {
                BufferedWriter file = new BufferedWriter(new FileWriter(filename));
                try {
                    file.write("# Function\tFile\tLine\tNumBBs\tInsns\tMemops\tFpops\tvisitCount\tAvgTimePerVisit\n");

                    for( Iterator<Function> fit = funcs.iterator(); fit.hasNext(); ) {
                        Function f = fit.next();
                        Double time = funcTimes.get(f.functionName);
                        Double timePerVisit;
                        if( f.visitCount > 0 ) {
                            timePerVisit = time / f.visitCount;
                        } else {
                            timePerVisit = -1 * time;
                        }

                        file.write(f.functionName + "\t" + f.file + "\t" + f.line + "\t" + f.numBlocks + "\t" + f.insns + "\t" +
                                   f.memOps + "\t" + f.fpOps + "\t" + f.visitCount + "\t" + timePerVisit + "\n");
                    }

                } finally {
                    file.close();
                }
            } catch (Exception e) {
                Logger.error(e, "Unable to write gpu report " + filename);
                return false;
            }
        }
        return true;
    }

    public static void main(String argv[]) {
        try {
            Predict predict = new Predict();
            boolean check = predict.run(argv);
            if(check){
                Logger.inform(Util.DONE_SUCCESS);
            }
        } catch (Exception e) {
            Logger.error(e, "*** FAIL *** FAIL *** FAIL Exception*****************\n");
        }
    }

    public static String makeScratchDirs(String scrDir,int grp,TestCase tCase){
        String outputDir = scrDir + "/" + grp;
        synchronized(predictLock){
            if(!Util.mkdir(outputDir)){
                Logger.warn("Can not make the directory " + outputDir);
                return null;
            }
            outputDir += "/" + tCase.shortName();
            if(!Util.mkdir(outputDir)){
                Logger.warn("Can not make the directory " + outputDir);
                return null;
            }
        }
        return outputDir;
    }

    static final String helpString =
        "[Basic Params]:\n" +
        "    --help                              : print a brief help message\n" +
        "    --version                           : print the version and exit\n" +
        "[Test Case Params]:\n" +
        "    --funding_agency   <funding_agency> : funding agency. listed in PSaPP config file. [REQ]\n" +
        "    --project          <project>        : project. listed in PSaPP config file. [REQ]\n" +
        "    --round            <n>              : round number. listed in PSaPP config file. [REQ]\n" +
        "    --application      <application>    : application. listed in PSaPP config file. [REQ]\n" +
        "    --dataset          <dataset>        : dataset name. listed in PSaPP config file. [REQ]\n" +
        "    --cpu_count        <cpu_count>      : number of cpus. listed in PSaPP config file. [REQ]\n" +
        "    --direct_dir       /path/to/data    : directory at which processed_trace and network trace are [REQ]\n" +
        "[Script Params]:\n" +
        "    --shortname        \"my runs 2009\"   : short name of this run to be used in database [REQ]\n" +
        "    --base_system      57               : base resource dbx id of the base system\n" +
        "    --base_profile     10017            : profile dbx id for the base system\n" +
        "    --machine_list     21,101,76        : base resource dbx id  of the target machines \n" +
        "    --profile_list     30101,20102      : dbx ids of machine profiles to predict for\n" +
        "                                          NOTE : one of --base_system and --base_profile is [REQ].\n" +
        "                                          NOTE : one of --machine_list and  --profile_list is [REQ].\n" +
        "    --scratch          /path            : scratch space for predictions [REQ]\n" +
        "    --prediction_group 30011            : dbx id of prediction group for this run [REQ]\n" +
        "    --ratio            0.5              : relative speed of target compute units\n" +
        "                                          metasim number is not calculated for any system, no convolver.\n" +
        "    --ratio_method     avg              : method used to compute the metasim ratio. default is avg \n" +
        "                                          Allowed = max, avg \n" +
        "    --use_sim_memtime                   : use simulated computation time from network sim of base system.\n" +
        "                                          metasim number of base system is not calculated, no convolver.\n" +
        "    --noDim                             : do not run network simulation. Only find metasim numbers.\n" +
        "    --network_simulator psins           : type of the network simulator to use. default is psins.\n" +
        "                                          Allowed = psins, dimemas\n" +
        "    --netsim_dir       /path/to/netsim  : path to network simulator. top directory of install.\n" +
        "                                          default is /projects/pmac/apps/PSiNS for psins\n" +
        "                                          default is /projects/pmac/dimemas for dimemas\n" +
        "    --psins_model      pmac             : communication model for network simulation. default is pmac.\n" +
        "                                          Allowed = simple,busonly,inplink,outlink,pmac,cont for psins\n" +
        "    --user             mtikir           : user requesting the predictions. if not given username is used.\n" +
        "    --sblocks          /path/vars.txt   : path to file for special BW calculations for a list of blocks.\n" +
        "                                          a sample file is at etc/bbvariations.txt under PSaPP.\n" +
        "    --stats                             : print detailed statistics under scratch directory\n" +
        "    --pwr              power model      : print metasim number for power\n" +
        "    --bwhist                            : print detailed statistics under scratch directory for power\n" +
        "                                          use it with --stats option\n" +
        "    --bb_list         <list_of_uniqbbid>: print detailed statistics under scratch directory for bbtimes\n" +
        "                                          use it with --stats option\n" +
        "    --netsim_suffix   jaws              : the suffix for netwok trace file. Originally it is .psins but\n" +
        "                                          if this option is used, it will be _jaws.psins to enable use\n" +
        "                                          of network traces collected on other machines\n" +
        "    --bw_multiplier   0.5               : additional mutliplication factor for BWs calculated during\n" +
        "                                          convolver. Calculated BW for each basic block is multiplied\n" +
        "                                          with this additional factor\n" +
        "    --use_dfp                           : if dfp is ready in processed trace use it\n" +
        "    --no_reports                        : skip reporting of results\n" +
        "[Sensitivity Params:]\n" +
        "    --sensitivity_study L1LatX2.0,L1BwX5 : sensitivity cases to predict. Each case is <caseXfactor>\n" +
        "                                           Allowed = { L1Lat, L2Lat, L3Lat, MMLat, \n" +
        "                                                       L1Bw, L2Bw, L3Bw, MMBw,\n" +
        "                                                       NetBw, NetLat, NodeBw, NodeLat } X <float>\n" +
        "[Other Params]:\n" +
        "    --database         /path/file.bin   : the path to the database file if binary files are used.\n" +
        "                                          default is to use Postgres.\n";
}
