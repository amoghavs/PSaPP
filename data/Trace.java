package PSaPP.data;
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
import PSaPP.send.*;
import PSaPP.recv.*;
import PSaPP.stats.*;
import PSaPP.pred.*;
import PSaPP.web.*;
import PSaPP.sim.*;

import java.io.*;
import java.util.*;


public abstract class Trace {

    String    pathToFiles;
    TestCase  testCase;
    Database  dataBase;
    TraceDB   tracedb;

    public Trace(){
        pathToFiles = null;
        testCase = null;
        dataBase = null;
        tracedb = null;
    }

    public Trace(String path,TestCase test,Database dbase){
        pathToFiles = path;
        testCase    = test;
        dataBase    = dbase;
        tracedb     = null;
    }

    boolean ftpFile(String dataToSend,String addExtension,String email,String resource,boolean noverify, boolean report){
        FileTransfer ftp = new FileTransfer(dataToSend,ftpName(dataToSend) + addExtension);
        boolean check = ftp.transfer();
        if(!check){
            Logger.warn("Can not transfer " + dataToSend + " due to ftp problems");
            return false;
        }
        FileTransfer done = new FileTransfer(ftpName(dataToSend) + ".done");
        OutputStream desc = done.getRemoteDesc();
        if(desc == null){
            Logger.warn("Can not transfer .done file due to ftp problems");
            return false;
        }

        assert (email != null);

        try {
            desc.write(("agency=" + testCase.getAgency() + "\n").getBytes());
            desc.write(("notify=" + email + "\n").getBytes());
            if(noverify){
                desc.write("noverify=1\n".getBytes());
            }
            if (report){
                String reportResources = ConfigSettings.getSetting("REPORT_RESOURCES");
                if (reportResources == null){
                    Logger.error("REPORT_RESOURCES config key needs to be set in $PSAPP_ROOT/etc/config");
                }
                desc.write(("report=" + reportResources + "\n").getBytes());
            }
            if(resource != null){
                desc.write(("resource=" + resource + "\n").getBytes());
            }
            desc.close();
        } catch (Exception e){
            Logger.error(e, "Can not close remote file in ftp\n" + e);
            return false;
        }

        return true;
    }
    public TestCase getTestCase() { return testCase; }

    public boolean processTrace(String scrDir,String primDir){

        Logger.inform("Process Trace is called for " + testCase);

        Logger.debug("Scratch is " + scrDir);
        Logger.debug("Primary is " + primDir);

        if(!Util.isDirectory(scrDir) || !Util.isDirectory(primDir)){
            Logger.warn("The scratch and/or primary dir(s) do not exist\n" + scrDir + "\n" + primDir);
            return false;
        }

        // If we don't have all the raw traces, we've done all the processing we can do for now
        if( !LegacyTraceProcessor.allTracesExist(testCase, dataBase) ) {
            return true;
        }

        // Create database and reports in scratch dir
        String processedDir = scrDir + "/" + Convolver.processedDirName;
        if( !LinuxCommand.mkdir(processedDir) ) {
            Logger.warn("Unable to create directory: " + processedDir);
            return false; 
        }

        tracedb = new LegacyTraceProcessor(testCase, scrDir, processedDir, dataBase, null);
        if( tracedb == null ) {
            return false;
        }

        //TraceReporter reporter = new TraceReporter(testCase, processedDir, tracedb);
        //Set<String> flags = Util.newHashSet();
        //flags.add("all");
        //if( !reporter.writeReports(flags) ) {
        //    return false;
        //}

        tracedb.close();

        // Move processed traces to primary dir
        String ptTarfile = testCase.ftpName() + "_pt.tar";
        String pathToPtTarfile = scrDir + "/" + ptTarfile;
        if( LinuxCommand.tar(Convolver.processedDirName, pathToPtTarfile, scrDir) == null ) {
            Logger.warn("Cannot tar processed trace: " + pathToPtTarfile);
            return false;
        }
 
        if( LinuxCommand.gzip(pathToPtTarfile) == null ) {
            Logger.warn("Cannot gzip processed trace " + pathToPtTarfile);
            return false;
        }
        if( LinuxCommand.move(pathToPtTarfile + ".gz", primDir + "/" + ptTarfile + ".gz") == false ) {
            Logger.warn("Cannot move processed trace to primary dir " + primDir);
            return false;
        }
    
        Logger.inform("Trace processing complete\n");
        return true;
    }

    public boolean runReports(String scrDir, String primDir, int resourceDbid, ReceiveRecord record){

        Logger.inform("trace run reports is called for " + testCase);

        Integer[] reportList = record.getReportList();

        if (reportList == null){
            Logger.error("Trying to report but no report resource list was given");
            return false;
        }


        int defaultProfIdx = dataBase.getDefaultPIdx(resourceDbid);
        if(defaultProfIdx == Database.InvalidDBID){
            Logger.warn("Resource " + resourceDbid + " has no default profile");
            return false;
        }

        String pTraceFileName = testCase.ftpName() + "_pt.tar.gz";
        String psinsFileName  = testCase.ftpName() + ".psins.gz";

        /* train, run psins, then report instead of a straight verify */
        int[] profileList = new int[reportList.length];
        int[] defProfList = new int[reportList.length];
        
        Logger.inform("Report will be generated on " + reportList.length + " systems");
        for (int i = 0; i < reportList.length; i++){
            defProfList[i] = dataBase.getDefaultPIdx(reportList[i].intValue());
            Logger.inform("br -> profile map: " + reportList[i].intValue() + " -> " + defProfList[i]);
            if(defProfList[i] == Database.InvalidDBID){
                Logger.error("Resource " + reportList[i].intValue() + " has no default profile");
                return false;
            }
        }
        
        Logger.inform("Starting training");
        
        String traceDir = scrDir + "/processed_trace";
        String trainingScratch = scrDir;
        String gaPrefix = testCase.toString();
        String gaTrainingType = ConfigSettings.getSetting("GATRAINING_TYPE");
        String bwMethod = ConfigSettings.getSetting("GATRAINING_BWMETHOD");
        String trainingOptions = ConfigSettings.getSetting("GATRAINING_OPTIONS");
        String forceSetting = ConfigSettings.getSetting("FORCE_NEW_TRAINING");
        boolean forceNew = false;
        if (forceSetting.compareTo("yes") == 0){
            Logger.inform("Forcing new GA training to be done even if trained weights/zones/profiles are already present");
            forceNew = true;
        }
        
        if (gaTrainingType == null){
            Logger.error("GATRAINING_TYPE config key needs to be set in $PSAPP_ROOT/etc/config");
        }
        if (bwMethod == null){
            Logger.error("GATRAINING_BWMETHOD config key needs to be set in $PSAPP_ROOT/etc/config");
        }
        if (trainingOptions == null){
            Logger.error("GATRAINING_OPTIONS config key needs to be set in $PSAPP_ROOT/etc/config");
        }
        
        /* training */
        Training[] trainings = new Training[reportList.length];
        for (int i = 0; i < reportList.length; i++){
            trainings[i] = new Training(forceNew, false, traceDir, trainingScratch, gaTrainingType, bwMethod,
                                        trainingOptions, null, gaPrefix, reportList[i].intValue(), testCase, null, null);
        }
        
        int[] trainedProfiles = new int[reportList.length];
        try {
            for (int i = 0; i < reportList.length; i++){
                trainings[i].thread.start();
            }
            for (int i = 0; i < reportList.length; i++){
                trainings[i].thread.join();
                trainedProfiles[i] = trainings[i].getTrainedProfile();
                Logger.inform("Trained profile is  " + trainedProfiles[i]);
            }
        } catch (InterruptedException e){
            Logger.error(e, "Training thread run interrupted");
        }
        
        boolean check = true;
        for (int i = 0; i < reportList.length; i++){
            if (trainings[i].getRunStatus() != PSaPPThread.THREAD_SUCCESS){
                check = false;
            }
        }
        if(!check){
            Logger.error("A training run failed");
            return false;
        }


        /* convolver */
        Statistics statistics = new Statistics(testCase);
        int[] profiles = new int[1];
        profiles[0] = defaultProfIdx;

        String successStr = "";
        double actualExecTime = dataBase.getActualRuntime(testCase,defaultProfIdx);

        double[] metasimNumbers = new double[reportList.length];
        for (int i = 0; i < reportList.length; i++){
            metasimNumbers[i] = 0.0;
        }
        double[] ratios = new double[reportList.length];

        Logger.warn("starting convolution");
        Convolver convolver = new Convolver(testCase,defaultProfIdx,trainedProfiles,
                                          Convolver.defaultRatioMethod,true,
                                          scrDir,scrDir,"pmacdata","synopsis run ",
                                          dataBase.getSimMemoryTimeGroup(),dataBase);
        convolver.setStatistics(statistics);
        
        try {
            convolver.thread.start();
            convolver.thread.join();
        } catch (InterruptedException e){
            Logger.error(e, "Convolver thread run interrupted");
        }
        
        if(convolver.getRunStatus() != PSaPPThread.THREAD_SUCCESS){
            Logger.warn("Verification error due to error in the convolver");
            return false;
        }
        
        for (int i = 0; i < reportList.length; i++){
            metasimNumbers[i] = convolver.getTargetMetasimNumbers(i);
            ratios[i] = convolver.getTargetRatios()[i];
            assert(trainedProfiles[i] == convolver.getTargetProfiles()[i]);
            
            Logger.inform("Metasim number for verification is: " + metasimNumbers[i] + ", ratio for verification is: " + ratios[i]);
        }
        statistics.print(scrDir);
            
        String commModel = ConfigSettings.getSetting("PSINS_COMM_MODEL");
        if (commModel == null){
            Logger.error("PSINS_COMM_MODEL config key needs to be set in $PSAPP_ROOT/etc/config");
        }
        
        /* run network simulations */
        NetworkSim[] networkSims = new NetworkSim[reportList.length];
        for (int i = 0; i < reportList.length; i++){
            int[] myprof = new int[1];
            myprof[0] = trainedProfiles[i];
            double[] myratios = new double[1];
            myratios[0] = ratios[i];
            
            networkSims[i] = new NetworkSim(testCase,trainedProfiles[i],myratios,myprof,
                                            "psins",commModel,null,
                                            scrDir,scrDir,"pmacdata","synopsis run pr" + trainedProfiles[i],
                                            dataBase.getSimReportGroup(),dataBase);
            networkSims[i].setStatistics(statistics);
        }
        
        try {
            for (int i = 0; i < reportList.length; i++){
                networkSims[i].thread.start();
            }
            for (int i = 0; i < reportList.length; i++){
                networkSims[i].thread.join();
            }
            statistics.print(scrDir);
        } catch (InterruptedException e){
            Logger.error(e, "NetworkSim thread run interrupted");
        }
        
        check = true;
        for (int i = 0; i < reportList.length; i++){
            if (networkSims[i].getRunStatus() != PSaPPThread.THREAD_SUCCESS){
                check = false;
            }
        }
        if(!check){
            Logger.warn("Verification error due to error network simulation (psins)");
            return false;
        }                
        
        for (int i = 0; i < reportList.length; i++){
            Logger.inform("synopsis directory: " + networkSims[i].getSynopsisDirectory());
            
            double overall = networkSims[i].getPredictedTimes(0);
            double comptime = networkSims[i].getRepresentCompTimes(0,Convolver.defaultRatioMethod);
            Logger.inform("Overall and Comp times for verification of psins are " + overall + " " + comptime);
            
            int testDbid = dataBase.getDbid(testCase);
            assert (testDbid != Database.InvalidDBID);
            
            String[] runFields = Database.generateRunRecord(dataBase.getSimReportGroup(),
                                                            trainedProfiles[i],trainedProfiles[i],testDbid,
                                                            metasimNumbers[i],ratios[i],
                                                            overall,comptime,
                                                            "psins","",
                                                            Convolver.defaultRatioMethod,false,
                                                            "synopsis run pr" + trainedProfiles[i],new Date());
            
            int dbid = dataBase.addPredictionRun(runFields);
            assert (dbid != Database.InvalidDBID);
            
            String errorStr = "(Exec time is not available)";
            if(actualExecTime > 0.0){
                errorStr = Format.format2d(100.0*(overall-actualExecTime)/actualExecTime);
            }
            successStr += ("\nPSINS\n" +
                           "exectim  : " + Format.format2d(actualExecTime) + " : " + errorStr + "\n" +
                           "predict  : " + Format.format2d(overall) + "\n" +
                           "comptim  : " + Format.format2d(comptime) + "\n" +
                           "ratioMt  : " + Convolver.defaultRatioMethod + "\n" +
                           "profile  : " + trainedProfiles[i] + "\n" + 
                           "baseRes  : " + reportList[i].intValue() + "(" + dataBase.getBaseResourceName(trainedProfiles[i]) + ")\n" +
                           "ratio    : " + ratios[i] + "\n" + 
                           "metaSim  : " + metasimNumbers[i] + "\n");
        }

        Logger.inform(successStr);
        String psinsOutDir = networkSims[0].getSynopsisDirectory();
            
        /* run the SimReport */
        SimReport simReport;
        if(psinsOutDir == null){
            Logger.error("Can not make directory " + psinsOutDir + " for runs, so no report is done");
            return false;
        }
        Logger.inform("Sending output from " + psinsOutDir + " to SimReport");
        if (tracedb != null){
            tracedb.open();
        }

        assert(testCase != null);
        if (tracedb == null){
            tracedb = new TraceDB(scrDir + "/" + convolver.processedDirName, testCase, null);
        }
        assert(tracedb != null);

        assert(dataBase != null);
        simReport = new SimReport(psinsOutDir, testCase, dataBase, defaultProfIdx, actualExecTime, tracedb, record.getPrivateReport());
        try{
            if (!simReport.run(trainedProfiles)){
                Logger.error("SimReport failed");
                return false;
            }
        }
        catch(Exception e) {
            Logger.error(e, "Exception caught while running SimReport");
            return false;
        }
        if (tracedb != null){
            tracedb.close();
        }
                
        return true;
    }


    public boolean verifyTrace(String scrDir,String primDir,int resourceDbid,ReceiveRecord record){
        
        Logger.inform("Verify trace is called for " + testCase);

        Logger.debug("Scratch is " + scrDir);
        Logger.debug("Primary is " + primDir);

        if(!Util.isDirectory(scrDir)){ 
            Logger.warn(scrDir + " does not exist for trace verification");
            return false;
        }

        scrDir += "/" + testCase.ftpName();
        if(!Util.mkdir(scrDir)){
            Logger.warn(scrDir + " can not be made for trace verification");
            return false;
        }

        int defaultProfIdx = dataBase.getDefaultPIdx(resourceDbid);
        if(defaultProfIdx == Database.InvalidDBID){
            Logger.warn("Resource " + resourceDbid + " has no default profile");
            return false;
        }

        String pTraceFileName = testCase.ftpName() + "_pt.tar.gz";
        String psinsFileName  = testCase.ftpName() + ".psins.gz";
        String trfFileName    = testCase.ftpName() + ".trf.gz";

        boolean skipConvolver = false;
        boolean skipPsinsSim = false;
        boolean skipTrfSim = false;

        if(!Util.isFile(primDir + "/" + pTraceFileName)){
            skipConvolver = true;
        }
        if(!Util.isFile(primDir + "/" + psinsFileName)){
            skipPsinsSim = true;
        }
        if(!Util.isFile(primDir + "/" + trfFileName)){
            skipTrfSim = true;
        }

        if(skipConvolver){
            Logger.inform("Not performing verification -- memory trace data missing");
            return true;
        }
        if(skipPsinsSim && skipTrfSim){
            Logger.inform("Not performing verification -- network trace data missing");
            return true;            
        }
        Logger.inform("All data present, performing verification");

        if(!skipConvolver){
            if(LinuxCommand.unZipTar(primDir + "/" + pTraceFileName,scrDir) == null){
                Logger.warn("Can not open package of processed trace " + primDir + "/" + pTraceFileName);
                return false;
            }
        }
        if(!skipPsinsSim){
            if(LinuxCommand.copy(primDir + "/" + psinsFileName,scrDir + "/" + psinsFileName) == null){
                Logger.warn("Can not copy " + psinsFileName + " to " + scrDir);
                return false;
            }
            if(LinuxCommand.gunzip(scrDir + "/" + psinsFileName) == null){
                Logger.warn("Can not unzip psins trace for " + psinsFileName);
                return false;
            }
        }
        if(!skipTrfSim){
            if(LinuxCommand.copy(primDir + "/" + trfFileName,scrDir + "/" + trfFileName) == null){
                Logger.warn("Can not copy trf file " + trfFileName + " to " + scrDir);
                return false;
            }
            if(LinuxCommand.gunzip(scrDir + "/" + trfFileName) == null){
                Logger.warn("Can not unzip trf file " + trfFileName);
                return false;
            }
        }

        Statistics statistics = new Statistics(testCase);
        int[] profiles = new int[1];
        profiles[0] = defaultProfIdx;

        String successStr = "";
        double actualExecTime = dataBase.getActualRuntime(testCase,defaultProfIdx);

        double metasimNumber = 0.0;
        if(!skipConvolver){
            Logger.warn("starting convolution");

            Convolver convolver = new Convolver(testCase,defaultProfIdx,profiles,
                                                Convolver.defaultRatioMethod,false,
                                                scrDir,scrDir,"pmacdata","verification run",
                                                dataBase.getSimMemoryTimeGroup(),dataBase);
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
                Logger.warn("Verification error due to error in the convolver");
                return false;
            }
            
            metasimNumber = convolver.getTargetMetasimNumbers(0);
            Logger.inform("Metasim number for verification is " + metasimNumber);
            statistics.print(scrDir);
            
            String errorStr = "(Exec time is not available)";
            if(actualExecTime > 0.0){
                errorStr = Format.format2d(100.0*(metasimNumber-actualExecTime)/actualExecTime);
            }
            successStr += ("\nCONVOLVER\n" +
                           "exectim  : " + Format.format2d(actualExecTime) + " : " + errorStr + "\n" +
                           "basePro  : " + defaultProfIdx + "\n" + 
                           "targetP  : " + defaultProfIdx + "\n" +
                           "metasim  : " + Format.format2d(metasimNumber) + "\n" +
                           "ratio    : " + "1.0\n");
        } 

        if(!skipTrfSim){
            double[] ratios = new double[1];
            ratios[0] = 1.0;
            NetworkSim networkSim = new NetworkSim(testCase,defaultProfIdx,ratios,profiles,
                                                   "dimemas",null,null,
                                                   scrDir,scrDir,"pmacdata","verification run",
                                                   dataBase.getSimMemoryTimeGroup(),dataBase);
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
                Logger.warn("Verification error due to error network simulation (trf)");
                return false;
            }
            double overall = networkSim.getPredictedTimes(0);
            double comptime = networkSim.getRepresentCompTimes(0,Convolver.defaultRatioMethod);
            Logger.inform("Overall and Comp times for verification of trf are " + overall + " " + comptime);
            statistics.print(scrDir);

            int testDbid = dataBase.getDbid(testCase);
            assert (testDbid != Database.InvalidDBID);


            String[] runFields = Database.generateRunRecord(dataBase.getSimMemoryTimeGroup(),
                                                defaultProfIdx,defaultProfIdx,testDbid,
                                                metasimNumber,1.0,
                                                overall,comptime,
                                                "dimemas","",
                                                Convolver.defaultRatioMethod,false,
                                                "verification run",new Date());

            int dbid = dataBase.addPredictionRun(runFields);
            assert (dbid != Database.InvalidDBID);

            String errorStr = "(Exec time is not available)";
            if(actualExecTime > 0.0){
                errorStr = Format.format2d(100.0*(overall-actualExecTime)/actualExecTime);
            }
            successStr += ("\nDIMEMAS\n" +
                            "exectim  : " + Format.format2d(actualExecTime) + " : " + errorStr + "\n" +
                            "predict  : " + Format.format2d(overall) + "\n" +
                            "comptim  : " + Format.format2d(comptime) + "\n" +
                            "ratioMt  : " + Convolver.defaultRatioMethod);

        }

        if(!skipPsinsSim){
            double[] ratios = new double[1];
            ratios[0] = 1.0;
            NetworkSim networkSim = new NetworkSim(testCase,defaultProfIdx,ratios,profiles,
                                                   "psins",null,null,
                                                   scrDir,scrDir,"pmacdata","verification run",
                                                   dataBase.getSimMemoryTimeGroup(),dataBase);
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
                Logger.warn("Verification error due to error network simulation (psins)");
                return false;
            }
            
            double overall = networkSim.getPredictedTimes(0);
            double comptime = networkSim.getRepresentCompTimes(0,Convolver.defaultRatioMethod);
            Logger.inform("Overall and Comp times for verification of psins are " + overall + " " + comptime);
            statistics.print(scrDir);
            
            int testDbid = dataBase.getDbid(testCase);
            assert (testDbid != Database.InvalidDBID);
            
            String[] runFields = Database.generateRunRecord(dataBase.getSimMemoryTimeGroup(),
                                                            defaultProfIdx,defaultProfIdx,testDbid,
                                                            metasimNumber,1.0,
                                                            overall,comptime,
                                                            "psins","",
                                                            Convolver.defaultRatioMethod,false,
                                                            "verification run",new Date());
            
            int dbid = dataBase.addPredictionRun(runFields);
            assert (dbid != Database.InvalidDBID);
            
            String errorStr = "(Exec time is not available)";
            if(actualExecTime > 0.0){
                errorStr = Format.format2d(100.0*(overall-actualExecTime)/actualExecTime);
            }
            successStr += ("\nPSINS\n" +
                           "exectim  : " + Format.format2d(actualExecTime) + " : " + errorStr + "\n" +
                           "predict  : " + Format.format2d(overall) + "\n" +
                           "comptim  : " + Format.format2d(comptime) + "\n" +
                           "ratioMt  : " + Convolver.defaultRatioMethod + "\n");
        }
        

        if (!skipPsinsSim && !skipConvolver){
            Integer[] reportList = record.getReportList();
            if (reportList != null && Util.isFile(primDir + "/" + pTraceFileName) && Util.isFile(primDir + "/" + psinsFileName)){
                Logger.inform("calling runReports instead of verifyTrace");
                runReports(scrDir, primDir, resourceDbid, record);
            }
        }
        

        record.setSuccessStr(successStr);

        if(!successStr.equals("")){
            String acceptedPkgDir = primDir + "/acceptance";
            if(Util.mkdir(acceptedPkgDir)){
                String tarFile = acceptedPkgDir + "/" + testCase.ftpName() + ".tar";
                if(Util.isFile(tarFile)){
                    LinuxCommand.deleteFile(tarFile);
                }
                if(LinuxCommand.tar(testCase.ftpName(),tarFile,scrDir + "/..") != null){
                    if(Util.isFile(tarFile + ".gz")){
                        LinuxCommand.deleteFile(tarFile + ".gz");
                    }
                    if(LinuxCommand.gzip(tarFile) == null){
                        Logger.warn("Trace verification data can not be saved due to gzip error");
                    }
                } else {
                    Logger.warn("Trace verification data can not be saved due to tar error");
                }
            } else {
                Logger.warn(acceptedPkgDir + " can not be made for saving trace verification data");
            }
        }

        return true;
    }

    public abstract boolean send(String email,String resource,boolean noverify,boolean report);
    public abstract String  ftpName(String sizeFile);
    public abstract String  localName();
    public abstract String  toString();
    public abstract boolean receive(ReceiveRecord record,String primDir,String secDir,String scrDir);

}
