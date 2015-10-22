package PSaPP.sim;
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
import PSaPP.dbase.*;
import PSaPP.stats.*;
import PSaPP.pred.*;

import java.io.*;
import java.util.*;

public class NetworkSim extends PSaPPThread {
    public static String[] validNetworkSims   = { "psins", "dimemas" };
    public static String[] validNetworkMods   = { "simple", "pmac", "busonly", "inponly", "outonly", "cont" };

    public static final String defaultNetworkSim  = "psins";
    public static final String defaultNetworkMod  = "pmac";
    static final String scratchExtension   = ".net";
    private static final Object networkSimLock = new Object() {};
    
    Database    dataBase;
    TestCase    testCase;

    int         baseProfile;
    double[]    targetRatios;
    int[]       targetProfiles;

    String      directDir;
    String      scratchDir;

    String      username;
    String      shortname;
    int         predictionGroup;

    String      simulatorType;
    String      simulationModel;
    String      simulationExecutable;

    StatCollection statistics;

    double[]    overallTime;
    double[]    totalComputationTime;
    double[]    totalCommunicationTime;
    double[]    representCompTimeMax;
    double[]    representCompTimeAvg;

    String traceFileSuffix;
    String synopsisDir;

    public String getSynopsisDirectory() { return synopsisDir; }

    public String toString(){
        String retVal = "NetworkSimulation(\n";
        retVal += ("  testCase   : " + testCase + "\n");
        retVal += ("  directDir  : " + directDir + "\n");
        retVal += ("  scratchDir : " + scratchDir + "\n");
        retVal += ("  predGroup  : " + predictionGroup + "\n");
        retVal += ("  username   : " + username + "\n");
        retVal += ("  shortname  : " + shortname + "\n");
        retVal += ("  baseProfile: " + baseProfile + "\n");
        retVal += ("  simType    : " + simulatorType + "\n");
        retVal += ("  simModel   : " + simulationModel + "\n");
        retVal += ("  simExec    : " + simulationExecutable + "\n");
        assert ((targetProfiles != null) && (targetRatios != null));
        for(int i=0;i<targetProfiles.length;i++){
            retVal += ("  profile    : " + targetProfiles[i] + "," + targetRatios[i] + "\n");
        }
        retVal += ")";
        return retVal;
    }

    public NetworkSim(TestCase tcase,int bprof,double[] ratios,int[] tprofs,
                      String simtyp,String simmod,String simexec,
                      String ddir,String sdir,
                      String user,String comment,
                      int group,Database dbase)
    {
        assert (dbase != null) && (tcase != null) && (group >= 0);
        dataBase = dbase;
        testCase = tcase;
        predictionGroup = group;

        if(!dataBase.existsTestCase(testCase)){
            Logger.error("Test case " + testCase + " does not exist in database");
        }
        if(!dataBase.existsPredGroup(predictionGroup)){
            Logger.error("Prediction group " + predictionGroup + " does not exist in database");
        }

        Logger.debug("Test case is " + testCase);

        assert (bprof >= 0);
        if(!dataBase.existsProfile(bprof)){
            Logger.error("Profile idx " + bprof + " does not exist in database");
        }
        baseProfile = bprof;
        assert (tprofs != null);
        for(int i=0;i<tprofs.length;i++){
            assert (tprofs[i] >= 0);
            if(!dataBase.existsProfile(tprofs[i])){
                Logger.error("Profile idx " + tprofs[i] + " does not exist in database");
            }
        }
        targetProfiles = tprofs;

        if(ratios.length != tprofs.length){
            Logger.error("Profile count does not match the ratio count " + 
                         tprofs.length + " != " + ratios.length);
        }
        assert (ratios.length == tprofs.length);
        for(int i=0;i<ratios.length;i++){
            if(ratios[i] <= 0.0){
                Logger.error("Ratio " + ratios[i] + " is invalid, needs to be > 0.0");
            }
        }
        targetRatios = ratios;

        assert (ddir != null) && (sdir != null);
        if(!Util.isDirectory(ddir)){
            Logger.error("Direct data dir " + ddir + " does not exist");
        }
        if(!Util.isDirectory(sdir)){
            Logger.error("Scratch data dir " + sdir + " does not exist");
        }

        directDir = ddir;
        scratchDir = sdir;
        Logger.debug(directDir + " is the data directory");
        Logger.debug(scratchDir + " is the scratch directory");

        assert (user != null) && (comment != null);
        username = user;
        shortname = comment;

        if(simtyp == null){
            simtyp = defaultNetworkSim;
            Logger.debug("Using the default simulator " + defaultNetworkSim);
        }
        simulatorType = simtyp;

        if(simtyp.equals("psins") && (simmod == null)){
            simmod = defaultNetworkMod;
            Logger.debug("Using the default simulation model " + defaultNetworkMod);
        }
        if(!simulatorType.equals("dimemas")){
            simulationModel = simmod;
        }

        simulationExecutable = simexec;

        if(simexec == null){
            if(simtyp.equals("psins"))
                simexec = ConfigSettings.getSetting("PSINS_PATH");
            else if(simtyp.equals("dimemas"))
                simexec = ConfigSettings.getSetting("DIMEMAS_PATH");

            Logger.debug("Using the default simulation executable at " + simexec);
            if(simtyp.equals("psins")){
                simulationExecutable = simexec + "/bin/psins";
            } else if(simtyp.equals("dimemas")){
                simulationExecutable = simexec + "/bin/Dimemas";
            }
        }

        Logger.debug("The simulation executable is " + simulationExecutable);

        assert ((simulatorType != null) && (simulationExecutable != null));
        assert (simulatorType.equals("dimemas") || (simulationModel != null));

        if(!Util.isFile(simulationExecutable)){
            Logger.error("Simulation executable " + simulationExecutable + " does not exist");
        }

        statistics = null;

        overallTime = new double[targetProfiles.length];
        totalComputationTime = new double[targetProfiles.length];
        totalCommunicationTime = new double[targetProfiles.length];
        representCompTimeMax = new double[targetProfiles.length];
        representCompTimeAvg = new double[targetProfiles.length];
        for(int i=0;i<targetProfiles.length;i++){
            overallTime[i] = 0.0;
            totalComputationTime[i] = 0.0;
            totalCommunicationTime[i] = 0.0;
            representCompTimeMax[i] = 0.0;
            representCompTimeAvg[i] = 0.0;
        }

        traceFileSuffix = null;

        thread = new Thread(this);
    }

    boolean generateScratchOutputs(){
        synchronized(networkSimLock){
            String outputDir = Predict.makeScratchDirs(scratchDir,predictionGroup,testCase);
            if(outputDir == null){
                Logger.warn("Can not make " + outputDir + " for prediction runs");
                return false;
            }
            for(int i=0;i<targetProfiles.length;i++){
                int machineIdx = dataBase.getBaseResource(targetProfiles[i]);
                int mprofIdx = dataBase.getMemoryPIdx(targetProfiles[i]);
                int nprofIdx = dataBase.getNetworkPIdx(targetProfiles[i]);
                assert ((nprofIdx != Database.InvalidDBID) && (machineIdx != Database.InvalidDBID));
                
                String filePath = outputDir + "/" + Format.BR(machineIdx) + "_" + Format.PR(targetProfiles[i]) + "_" +
                    Format.NP(nprofIdx) + scratchExtension;
                try {
                    BufferedWriter outputFile = new BufferedWriter(new FileWriter(filePath));
                    outputFile.write("profile    = " + targetProfiles[i]+ "\n");
                    outputFile.write("nprofile   = " + nprofIdx + "\n");
                    outputFile.write("mprofile   = " + mprofIdx + "\n");
                    outputFile.write("testcase   = " + testCase + "\n");
                    outputFile.write("resource   = " + dataBase.getBaseResourceString(machineIdx) + "\n");
                    outputFile.write("type       = " + simulatorType + "\n");
                    outputFile.write("model      = " + simulationModel + "\n");
                    outputFile.write("ratio      = " + targetRatios[i] + "\n");
                    outputFile.write("comp(tot)  = " + totalComputationTime[i] + "\n");
                    outputFile.write("comm(tot)  = " + totalCommunicationTime[i] + "\n");
                    outputFile.write("comp(repM) = " + representCompTimeMax[i] + "\n");
                    outputFile.write("comp(repA) = " + representCompTimeAvg[i] + "\n");
                    outputFile.write("overall    = " + overallTime[i] + "\n");
                    outputFile.close();
                } catch (Exception e){ 
                    Logger.warn("Can not write the network simulation summary at " + filePath + "\n" + e);
                    return false;
                }
            }
        }

        return true;
    }

    public void setStatistics(StatCollection stats){
        statistics = stats;
    }

    public void run(){

        Logger.inform(this);

        boolean success = false;

        String outputDir = Predict.makeScratchDirs(scratchDir,predictionGroup,testCase);
        synopsisDir = outputDir;
        if(outputDir == null){
            Logger.warn("Can not make scratch directory " + outputDir);
            runStatus = PSaPPThread.THREAD_FAILED;
            return;
        }

        if(statistics != null){
            int[] networkPs = new int[targetProfiles.length];
            int[] baseRs = new int[targetProfiles.length];
            for(int i=0;i<targetProfiles.length;i++){
                assert (targetProfiles[i] >= 0);
                networkPs[i] = dataBase.getNetworkPIdx(targetProfiles[i]);
                baseRs[i] = dataBase.getBaseResource(targetProfiles[i]);
            }
            statistics.addNetworkProfiles(targetRatios,networkPs,baseRs);
        }
        for(int i=0;i<targetProfiles.length;i++){
            assert (targetProfiles[i] >= 0);
            int machineIdx = dataBase.getBaseResource(targetProfiles[i]);
            int nprofIdx = dataBase.getNetworkPIdx(targetProfiles[i]);
            assert ((nprofIdx != Database.InvalidDBID) && (machineIdx != Database.InvalidDBID));

            Object[] networkProfile = dataBase.getNetworkProfile(nprofIdx);
            assert (networkProfile != null);

            String configFile = outputDir + "/" + Format.BR(machineIdx) + "_" + Format.PR(targetProfiles[i]) + "_" +
                                Format.NP(nprofIdx) + "_" + simulatorType + ".config";
            String outputFile = outputDir + "/" + Format.BR(machineIdx) + "_" + Format.PR(targetProfiles[i]) + "_" +
                                Format.NP(nprofIdx) + "_" + simulatorType + ".output";

            Simulation simulation = null;
            if(simulatorType.equals("dimemas")){
                simulation = new Dimemas(simulationExecutable,simulationModel,
                                         configFile,outputFile,
                                         networkProfile,testCase,
                                         targetRatios[i],directDir,
                                         outputDir,targetProfiles[i]);
            } else if(simulatorType.equals("psins")){
                simulation = new Psins(simulationExecutable,simulationModel,
				       configFile,outputFile,
				       networkProfile,testCase,
				       targetRatios[i],directDir,
                                       outputDir,targetProfiles[i]);
            } else {
                Logger.warn("Invalid simulation type " + simulatorType);
                runStatus = PSaPPThread.THREAD_FAILED;
                return;
            }
            assert (simulation != null);
            if(traceFileSuffix != null){
                simulation.setTraceFileSuffix(traceFileSuffix);
            }

            success = simulation.generateConfigFile();
            if(!success){
                Logger.warn("Can not construct the config file for profile " + targetProfiles[i]);
                runStatus = PSaPPThread.THREAD_FAILED;
                return;
            }

            success = simulation.run();
            if(!success){
                Logger.warn("Simulation error for profile " + targetProfiles[i]);
                runStatus = PSaPPThread.THREAD_FAILED;
                return;
            }

            overallTime[i] = simulation.getPredictedTime();
            totalComputationTime[i] = simulation.getTotalCompTime();
            totalCommunicationTime[i] = simulation.getTotalCommTime();
            representCompTimeMax[i] = simulation.getRepresentCompTime("max");
            representCompTimeAvg[i] = simulation.getRepresentCompTime("avg");

            assert ((overallTime[i] > 0.0) && 
                    (totalComputationTime[i] > 0.0) && (totalCommunicationTime[i] >= 0.0) && 
                    (representCompTimeMax[i] > 0.0) && (representCompTimeAvg[i] > 0.0));

            if(statistics != null){
                double[] compTimes = simulation.getCompTimes();
                double[] commTimes = simulation.getCommTimes();
                HashMap mpiETimes = simulation.getMpiEventTimes();
                statistics.addNetworkTimes(i,compTimes,commTimes,mpiETimes);
            }

            Logger.inform("Results for simulation of " + targetProfiles[i] + " with network benchmark " + nprofIdx +
                          " for base resource " + machineIdx + " is at " + outputFile);
            Logger.inform("Overall time " + overallTime[i] + " compXcomp totals = " + 
                          totalComputationTime[i] + " x " + totalCommunicationTime[i] + "(" + 
                          Format.format4d(Util.percentage(totalCommunicationTime[i],totalComputationTime[i])) + "% comm)");

        }

        success = generateScratchOutputs();
        if(!success){
            Logger.warn("Can not generate scratch dirs and files");
        }

        if(statistics != null){
            if(outputDir == null){
                Logger.warn("Can not make " + outputDir + ", so no statistics are written");
            } else {
                statistics.print(outputDir);
            }
        }

        runStatus = PSaPPThread.THREAD_SUCCESS;
    }

    public double getPredictedTimes(int i){
        assert ((i >= 0) && (overallTime != null) && (i < overallTime.length));
        return overallTime[i];
    }
    public double getRepresentCompTimes(int i,String rMeth){
        if(rMeth.equals("max")){
            assert ((i >= 0) && (representCompTimeMax != null) && (i < representCompTimeMax.length));
            return representCompTimeMax[i];
        } else if(rMeth.equals("avg")){
            assert ((i >= 0) && (representCompTimeAvg != null) && (i < representCompTimeAvg.length));
            return representCompTimeAvg[i];
        }
        return 0.0;
    }
    public double[] getPredictedTimes(){
        double[] retVal = null;
        if(overallTime != null){
            retVal = new double[overallTime.length];
            for(int i=0;i<overallTime.length;i++){
                retVal[i] = overallTime[i];
            }
        }
        return retVal;
    }
    public double[] getRepresentCompTimes(String rMeth){
        double[] retVal = null;
        if(rMeth.equals("max")){
            if(representCompTimeMax != null){
                retVal = new double[representCompTimeMax.length];
                for(int i=0;i<representCompTimeMax.length;i++){
                    retVal[i] = representCompTimeMax[i];
                }
            }
        } else if(rMeth.equals("avg")){
            if(representCompTimeAvg != null){
                retVal = new double[representCompTimeAvg.length];
                for(int i=0;i<representCompTimeAvg.length;i++){
                    retVal[i] = representCompTimeAvg[i];
                }
            }
        }
        return retVal;
    }
    public void setTraceFileSuffix(String suffix){
        assert (suffix != null);    
        traceFileSuffix = suffix;
    }
}


