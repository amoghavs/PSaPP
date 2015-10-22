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


//mtikir..maryland..ti08_round1_testapp_standard_0064..56..exec100.done

import PSaPP.util.*;
import PSaPP.dbase.*;
import PSaPP.recv.*;
import java.util.*;

public final class Executable extends Trace {

    double  execTime;
    String baseResource;

    public Executable(String path,double eTime,String resource,TestCase tcase,Database dbase){
        super(path,tcase,dbase);
        execTime = eTime;
        baseResource = resource;
    }
    public boolean send(String email,String resource,boolean noverify,boolean report){
        String localFile = pathToFiles;
        if(!Util.isFile(localFile)){
            Logger.warn("Invalid path for executable " + pathToFiles);
            return false;
        }
        return ftpFile(localFile,"",email,resource,noverify,report);
    }
    public String  toString() { 
        return "ExecutableTrace(" + pathToFiles + "," + execTime + "," + baseResource + ")"; 
    }
    public String ftpName(String sizeFile) { 
        assert (sizeFile != null) : "Ftp source file has to be assigned before this function";
        assert (Util.isFile(sizeFile)) : "Ftp source can be only file";

        return (LinuxCommand.username() + ".." + LinuxCommand.hostname() + ".." +
                testCase.ftpName() + ".." + LinuxCommand.getFileSize(sizeFile) + 
                ".." + "exec" + (int)execTime);
    }
    public String localName() { return ""; }

    public boolean receive(ReceiveRecord record,String primDir,String secDir,String scrDir){

        Receive.printReceiveRecord(pathToFiles,record,primDir,secDir,scrDir);

        if(!record.verifySizes(pathToFiles)){
            Logger.warn("Trace file was not transfered sucessfully " + pathToFiles);
            return false;
        }

        int[] dbids = record.getDbids(dataBase,testCase);
        if(dbids == null){
            Logger.warn("Error in querying test case and resource id " + testCase);
            return false;
        }
        int testDbid = dbids[0];
        int resourceDbid = dbids[1];

        assert (testDbid != Database.InvalidDBID) && (resourceDbid != Database.InvalidDBID);
        String[] fields = new String[3];
        fields[0] = String.valueOf(testDbid);
        fields[1] = String.valueOf(resourceDbid);
        fields[2] = String.valueOf(execTime);

        int check = dataBase.addActualRuntime(fields);
        if(check == Database.InvalidDBID){
            Logger.warn("Error in inserting the actual runtime for fields\n" + Util.listToString(fields));
            return false;
        }

        Date date = new Date();
        String targetFile = primDir + "/raw/" + testCase.getProject() + "/" + testCase.ftpName() + ".exe";

        boolean status = LinuxCommand.move(pathToFiles,targetFile);
        if(!status){
            Logger.warn("Can not move " + pathToFiles + " to " + targetFile);
            return false;
        }

        check = dataBase.updateTraceStatus(testDbid,"exec",
                                Util.dateToString(date),
                                record.getMachine(),record.getNotify(),
                                targetFile,execTime);
        assert (check != Database.InvalidDBID);

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
