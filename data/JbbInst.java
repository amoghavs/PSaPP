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
import PSaPP.recv.*;
import java.util.*;


public final class JbbInst extends Trace {
    public JbbInst(String path,TestCase tcase,Database dbase){
        super(path,tcase,dbase);
    }
    public boolean send(String email,String resource,boolean noverify, boolean report) { 
        String tmpdir = pathToFiles;

        if(!Util.isDirectory(tmpdir)){
            Logger.warn("Invalid path for directory " + tmpdir);
            return false;
        }
        tmpdir = tmpdir + "/jbbinst";
        if(!Util.isDirectory(tmpdir)){
            Logger.warn("Invalid path for directory " + tmpdir);
            return false;
        }
        String tracedir = tmpdir + "/" + testCase.getApplication();
        if(!Util.isDirectory(tracedir)){
            tracedir = null;
        }
        if(tracedir == null){
            tracedir = tmpdir + "/" + testCase.getApplication() + "_" + testCase.getDataset();
            if(!Util.isDirectory(tracedir)){
                tracedir = null;
            }
        }
        if(tracedir == null){
            tracedir = tmpdir + "/" + testCase.getApplication() + "_" + testCase.getDataset() + 
                     "_" + Format.cpuToString(testCase.getCpu());
            if(!Util.isDirectory(tracedir)){
                tracedir = null;
            }
        }
        if(tracedir == null){
            Logger.warn("Invalid path for directory " + tracedir);
            return false;
        }

        Logger.inform("The actual trace files are found under " + tracedir);
        LinkedList result = LinuxCommand.ls(tracedir);

        String executableName = null;

        Iterator it = result.iterator();
        while(it.hasNext()){
            String file = (String)it.next();
            file = Util.cleanWhiteSpace(file);
            if(file.endsWith(".jbbinst") && Util.isFile(tracedir + "/" + file)){
                executableName = file.replaceAll("\\.jbbinst$","");
                Logger.inform("Executable name is " + executableName);
                break;
            }
        }

        if(executableName == null){
            Logger.warn("Can not extract executable name from " + tracedir);
            return false;
        }

        String jbbstatic = tracedir + "/" + executableName + ".jbbinst.static";
        if(!Util.isFile(jbbstatic)){
            Logger.warn("Static file is missing " + jbbstatic);
            return false;
        }

        String localFile = localName() + ".tar";
        String[] dirs = LinuxCommand.splitDirAndFile(tracedir);
        result = LinuxCommand.tar(dirs[1],localFile,dirs[0]);
        if(!Util.isFile(localFile)){
            Logger.warn("Tar failed for " + tracedir);
            return false;
        }

        result = LinuxCommand.gzip(localFile);
        localFile += ".gz";
        if(!Util.isFile(localFile)){
            Logger.warn("Zip failed for " + localFile);
            return false;
        }

        boolean retValue = ftpFile(localFile,".tar.gz",email,resource,noverify,report);
        LinuxCommand.deleteFile(localFile);
        return retValue;
    }

    public String  toString() { 
        return "JbbinstTrace(" + pathToFiles + ")";
    }
    public String  ftpName(String sizeFile) { 
        assert (sizeFile != null) : "Ftp source file has to be assigned before this function";
        assert (Util.isFile(sizeFile)) : "Ftp source can be only file";

        return (LinuxCommand.username() + ".." + LinuxCommand.hostname() + ".." +
                testCase.ftpName() + ".." + LinuxCommand.getFileSize(sizeFile) +
                ".." + "jbbinst");
    }
    public String localName() {
        return (LinuxCommand.username() + ".." + testCase.ftpName() + ".." + "jbbinst");
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
        String targetFile = primDir + "/raw/" + testCase.getProject() + "/" + testCase.ftpName() + "_jbbinst.tar.gz";

        boolean status = LinuxCommand.move(pathToFiles,targetFile);
        if(!status){
            Logger.warn("Can not move " + pathToFiles + " to " + targetFile);
            return false;
        }
        int check = dataBase.updateTraceStatus(testDbid,"jbbinst",
                                Util.dateToString(date),
                                record.getMachine(),record.getNotify(),
                                targetFile,0.0);
        assert (check != Database.InvalidDBID);

        boolean statusProcess = processTrace(scrDir + "/" + testCase.ftpName(),
                              primDir + "/processed/" + testCase.getProject());
        if(!statusProcess){
            Logger.warn("Process trace failed for " + testCase);
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
