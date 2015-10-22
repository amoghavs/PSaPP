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


import PSaPP.data.*;
import PSaPP.dbase.*;
import PSaPP.util.*;

public class ReceiveRecord {

    Trace   traceObject;
    boolean verify;
    String  notify;
    String  resource;
    String  user;
    String  machine;
    long    size;
    Integer[] reportList;
    boolean privateReport;

    String  successStr;

    public ReceiveRecord(Trace obj,boolean verFlg,String email,String basesys,String usr,String subsys,long sz,String report, boolean isPriv){

        assert (obj != null) && (email != null) && 
               (usr != null) && (subsys != null);

        traceObject = obj;
        verify = verFlg;
        notify = email;
        resource = basesys;
        user = usr;
        machine = subsys;
        size = sz;
        successStr = "";
        reportList = Util.machineList(report);
        privateReport = isPriv;
    }

    public Trace getTraceObject() { return traceObject; }
    public String getUser() { return user; }
    public String getMachine() { return machine; }
    public boolean getVerify() { return verify; }
    public String getNotify() { return notify; }
    public long getSize() { return size; }
    public String getResource() { return resource; }
    public String getSuccessStr() { return successStr; }
    public void setSuccessStr(String str) { successStr = str; }
    public Integer[] getReportList() { return reportList; }
    public boolean getPrivateReport() { return privateReport; }

    public boolean verifySizes(String pathToFile){
        long embeddedSize = getSize();
        long realSize = LinuxCommand.getFileSize(pathToFile);
        return (embeddedSize == realSize);
    }

    public int[] getDbids(Database dataBase,TestCase testCase){
        int[] retValue = new int[2];

        int testDbid = dataBase.getDbid(testCase);
        if(testDbid == Database.InvalidDBID){
            Logger.debug("Adding " + testCase + " to the database as it does not exist");
            testDbid = dataBase.addTestCase(testCase);
        }
        if(testDbid == Database.InvalidDBID){
            Logger.warn("Test case " + testCase + " can not be inserted into database");
            return null;
        }
        int resourceDbid = Database.InvalidDBID;
        if(getResource() != null){
            resourceDbid = dataBase.getDbid(getResource());
            if(resourceDbid == Database.InvalidDBID){
                Logger.warn("Base resource " + getResource() + " does not exist in database");
                return null;
            }
        }

        retValue[0] = testDbid;
        retValue[1] = resourceDbid;

        return retValue;
    }
}
