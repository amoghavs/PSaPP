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
import java.util.*;
import PSaPP.recv.*;

public final class SimColl extends Trace {

    int phaseCount;
    int phaseNo;
    double execTime;

    public SimColl(String path,int pidx,double eTime,int pcnt,TestCase tcase,Database dbase){
        super(path,tcase,dbase);
        phaseNo = pidx;
        phaseCount = pcnt;
        execTime = eTime;

    }

    public boolean send(String email,String resource,boolean noverify, boolean report) {
        String tmpdir = pathToFiles;

        if(!Util.isDirectory(tmpdir)){
            Logger.warn("Path does not exist as directory " + tmpdir);
            return false;
        }
        tmpdir = tmpdir + "/simcoll";
        if(!Util.isDirectory(tmpdir)){
            Logger.warn("Path does not exist as directory " + tmpdir);
            return false;
        }
        String tracedir = tmpdir + "/" + testCase.shortName();
        if(!Util.isDirectory(tracedir)){
            Logger.warn("Path does not exist as directory " + tracedir);
            return false;
        }
        
        String phasedir = tracedir + "/p" + Format.phaseToString(phaseNo);
        if(!Util.isDirectory(phasedir)){
            Logger.warn("Path does not exist as directory " + phasedir);
            return false;
        }

        Logger.inform("The actual trace files are found under " + phasedir);

        LinkedList result = LinuxCommand.ls(phasedir);

        String executableName = null;

        Iterator it = result.iterator();
        while(it.hasNext()){
            String file = (String)it.next();
            file = Util.cleanWhiteSpace(file);
            if(file.matches(".*\\.phase\\.\\d+\\.meta_\\d+\\.\\d+\\.siminst$") && 
               Util.isFile(phasedir + "/" + file)){
                executableName = file.replaceAll("\\.phase\\.\\d+\\.meta_\\d+\\.\\d+\\.siminst$","");
                Logger.inform("Executable name is " + executableName);
                break;
            }
        }

        if(executableName == null){
            Logger.warn("Can not extract the executable name at " + phasedir);
            return false;
        }
        
        for(int i=0;i<testCase.getCpu();i++){ 
            String checkfile = phasedir + "/" + executableName + ".phase." + phaseNo +
                               ".meta_" + Format.cpuToString(i) + "." + 
                               Format.cpuToString(testCase.getCpu()) + ".siminst";
            if(!Util.isFile(checkfile)){
                Logger.warn("Missing the trace file " + checkfile);
                return false;
            }
        }

        String dfpatterndir = null;
        if(Util.isDirectory(phasedir + "/dfp")){
            dfpatterndir = phasedir + "/dfp";
            Logger.inform("The trace files for DFPattern are found under " + dfpatterndir);

            for(int i=0;i<testCase.getCpu();i++){
                String checkfile = dfpatterndir + "/" + executableName + ".phase." + phaseNo +
                                   ".meta_" + Format.cpuToString(i) + ".dfp";
                if(!Util.isFile(checkfile)){
                    Logger.warn("Missing the dfp trace file " + checkfile);
                    return false;
                }
            }
        }

        String localFile = localName() + ".tar";
        String[] dirs = LinuxCommand.splitDirAndFile(tracedir);
        result = LinuxCommand.tar(dirs[1],localFile,dirs[0]);
        if(!Util.isFile(localFile)){
            Logger.warn("Can not tar the directory " + tracedir);
            return false;
        }

        result = LinuxCommand.gzip(localFile);
        localFile += ".gz";
        if(!Util.isFile(localFile)){
            Logger.warn("Can not zip for " + localFile);
            return false;
        }

        boolean retValue = ftpFile(localFile,".tar.gz",email,resource,noverify,report);
        LinuxCommand.deleteFile(localFile);
        return retValue;
    }
    public String  toString() { 
        return "Simcoll(" + pathToFiles + "," + phaseCount + "," + phaseNo + "," + execTime + ")";
    }
    public String  ftpName(String sizeFile) {
        assert (sizeFile != null) : "Ftp source file has to be assigned before this function";
        assert (Util.isFile(sizeFile)) : "Ftp source can be only file";

        return (LinuxCommand.username() + ".." + LinuxCommand.hostname() + ".." +
                testCase.ftpName() + ".." + LinuxCommand.getFileSize(sizeFile) +
                ".." + "simcoll" + phaseCount + "p" + phaseNo + "t" + (int)execTime);
    }
    public String localName() {
        return (LinuxCommand.username() + ".." + testCase.ftpName() + ".." + "simcoll" +
                "..p" + Format.phaseToString(phaseNo));
    }
    public boolean receive(ReceiveRecord record,String primDir,String secDir,String scrDir){

        Receive.printReceiveRecord(pathToFiles,record,primDir,secDir,scrDir);

        if(!record.verifySizes(pathToFiles)){
            Logger.warn("Trace file is not transmitted fully " + pathToFiles);
            return false;
        }

        int[] dbids = record.getDbids(dataBase,testCase);
        if(dbids == null){
            return false;
        }
        int testDbid = dbids[0];
        int resourceDbid = dbids[1];

        assert (testDbid != Database.InvalidDBID) && (resourceDbid != Database.InvalidDBID);

        Date date = new Date();
        String targetFile = primDir + "/raw/" + testCase.getProject() + "/" + testCase.ftpName() + 
                            "_simcoll" + "p" + Format.phaseToString(phaseNo) + ".tar.gz";

        boolean status = LinuxCommand.move(pathToFiles,targetFile);
        if(!status){
            Logger.warn("Can not move " + pathToFiles + " to " + targetFile);
            return false;
        }
        int check = dataBase.updateTraceStatus(testDbid,phaseCount); 
        assert (check != Database.InvalidDBID);

        check = dataBase.updatePhaseStatus(testDbid,"simcoll",phaseNo,
                                Util.dateToString(date),
                                record.getMachine(),record.getNotify(),
                                targetFile,execTime);
        assert (check != Database.InvalidDBID);

        boolean statusProcess = processTrace(scrDir + "/" + testCase.ftpName(),
                              primDir + "/processed/" + testCase.getProject());
        if(!statusProcess){
            Logger.warn("Process trace has failed for " + testCase);
            return false;
        } 

        if(!record.getVerify()){
            Logger.inform("No verification is asked for " + testCase + " at the end of receive");
        } else {
            boolean statusVerify = verifyTrace(scrDir + "/" + testCase.ftpName(),
                                          primDir + "/processed/" + testCase.getProject(),
                                          resourceDbid,record);
            if(!statusVerify){
                Logger.warn("Verification error for " + testCase);
                return false;
            }
        }

        return true;
    }
}
