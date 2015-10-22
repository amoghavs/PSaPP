package PSaPP.send;
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
import PSaPP.data.*;
import PSaPP.util.*;
import PSaPP.dbase.*;
import PSaPP.chck.*;

public final class Send implements CommandLineInterface {

    OptionParser optionParser;
    TestCase     testCase;
    LinkedList   actionList;

    static final String[] ALL_OPTIONS = {
        "help:?",
        "version:?",

        "resource:s",

        "funding_agency:s",
        "project:s",
        "round:i",
        "application:s",
        "cpu_count:i",
        "dataset:s",

        "notify:s",

        "exec:s",
        "mpidtrace:s",
        "psinstrace:s",
        "jbbinst:?",
        "jbbcoll:d",
        "siminst:s",
        "simcoll:s",
        "ipmdata:s",
        "syshealth:s",

        "pmacinst_dir:s",
        "num_phases:i",

        "noverify:?",
        "report:?",

        "tau:d",

        // trace check and verification options for computation traces
        "trc_nochck:?",
        "chck_nostop:?"
    };

    public Send(){
        optionParser = new OptionParser(ALL_OPTIONS,this);
        testCase = null;
        actionList = new LinkedList();
    }

    public boolean verifyValues(HashMap values){

        assert (testCase != null) : "testcase is not allocated yet";
        if(!testCase.verify()){
            Logger.error("Invalid test case " + testCase);
        }

        if(values.get("notify") == null){
            Logger.error("--notify is required");
        }

        String resource = null;
        Object value = values.get("resource");
        if(value != null){
            resource = (String)values.get("resource");
            if(!ConfigSettings.isValid("BASE_RESOURCES", resource)){
                Logger.error("Base resource " + resource + " is not valid");
            }
        }

        String pmacinstDir = null;
        value = values.get("pmacinst_dir");
        if(value != null){
            pmacinstDir = (String)value;
            if(!Util.pathExists(pmacinstDir)){
                Logger.error("Invalid directory for --pmacinst_dir " + pmacinstDir);
            }
        }

        value = values.get("exec");
        if(value != null){
            String strVal = (String)value;
            if(Util.fileTimeTuple(strVal) == null){
                Logger.error("Invalid value for --exec " + strVal);
            }
            if(resource == null){
                Logger.error("--resource is required for executable");
            }
        }
        /*** Mitesh added stuff for syshealth monitoring **/
        value = values.get("syshealth");
        if(value != null){
            String strVal = (String)value;
            Object[] fileAndTime = Util.fileTimeTuple(strVal);
            if(fileAndTime == null){
                Logger.error("Invalid value for --syshealth " + strVal);
            }
            strVal = (String)fileAndTime[0];
            
            if(resource == null){
                Logger.error("--resource is required for syshealth");
            }
        }

        value = values.get("mpidtrace");
        if(value != null){
            String strVal = (String)value;
            Object[] fileAndTime = Util.fileTimeTuple(strVal);
            if(fileAndTime == null){
                Logger.error("Invalid value for --mpidtrace " + strVal);
            }
            strVal = (String)fileAndTime[0];
            if(!strVal.matches(".*\\.trf\\.gz$")){
                Logger.error("Mpidtrace trace " + strVal + " does not have right extension or not zipped");
            }

            if(resource == null){
                Logger.error("--resource is required for mpidtrace");
            }
        }

        value = values.get("psinstrace");
        if(value != null){
            String strVal = (String)value;
            Object[] fileAndTime = Util.fileTimeTuple(strVal);
            if(fileAndTime == null){
                Logger.error("Invalid value for --psinstrace " + strVal);
            }
            strVal = (String)fileAndTime[0];
            if(!strVal.matches(".*\\.psins\\.gz$")){
                Logger.error("Psins trace " + strVal + " does not have right extension or not zipped");
            }
            if(resource == null){
                Logger.error("--resource is required for psinstrace");
            }
        }

/** Mitesh edits ***/
        value = values.get("ipmdata");
        if(value != null){
            String strVal = (String)value;
            Object[] fileAndTime = Util.fileTimeTuple(strVal);
            if(fileAndTime == null){
                Logger.error("Invalid value for --ipmdata " + strVal);
            }
            strVal = (String)fileAndTime[0];

	    if(resource == null){
                Logger.error("--resource is required for ipmdata");
            }
        }

        if((values.get("jbbinst") != null) ||
           (values.get("jbbcoll") != null) ||
           (values.get("siminst") != null) ||
           (values.get("simcoll") != null)){
            if(pmacinstDir == null){
                Logger.error("--pmacinst_dir is required for raw traces");
            }
        }

        int phaseCount = 0;
        value = values.get("num_phases");
        if(value != null){
            phaseCount = ((Integer)value).intValue();
        } 

        if((values.get("siminst") != null) ||
           (values.get("simcoll") != null)){
            if(value == null){
                Logger.error("--num_phases is reuired for this action");
            }
            if(phaseCount <= 0){
                Logger.error("Invalid phase count " + phaseCount);
            }
        }

        value = values.get("siminst");
        if(value != null){
            if(phaseCount == 0){
                Logger.error("--num_phases is required for siminst");
            }
            String strVal = (String)value;
            Object[] phases = Util.phaseListValue(strVal);
            if(phases == null){
                Logger.error("Phase information is required for siminst");
            }
            for(int i=0;i<phases.length;i++){
                int phaseId = ((Integer)phases[i]).intValue();
                if((phaseId < 1) || (phaseId > phaseCount)){
                    Logger.error("Phase " + phaseId + " is not valid for " + phaseCount + " phases");
                }
            }
        }
        value = values.get("simcoll");
        if(value != null){
            if(phaseCount == 0){
                Logger.error("--num_phases is required for simcoll");
            }
            String strVal = (String)value;
            Object[] phases = Util.phaseListTimeValue(strVal);
            if(phases == null){
                Logger.error("--simcoll value is not in right format " + strVal);
            }
            for(int i=0;i<phases.length;i++){
                int phaseId = ((Integer)phases[i++]).intValue();
                if((phaseId < 1) || (phaseId > phaseCount)){
                    Logger.error("Phase " + phaseId + " is not valid for " + phaseCount + " phases");
                }
            }
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
        System.err.println(helpString);
        String allStr = "usage :\n";
        for(int i=0;i<ALL_OPTIONS.length;i++){
            allStr += ("\t--" + ALL_OPTIONS[i] + "\n");
        }
        allStr += ("\n" + str);
        //System.err.println(allStr);
    }

    boolean traceVerification(boolean noStop){
        String pmacinstDir  = (String)optionParser.getValue("pmacinst_dir");
        
        // if trace checking is disbaled before submission, skip the following step
        // note that trace verification is enabled if any of the traces are submitted for
        // memory computation and for no network traces or others
        
        if((optionParser.getValue("jbbinst") != null) ||
           (optionParser.getValue("jbbcoll") != null) ||
           (optionParser.getValue("siminst") != null) ||
           (optionParser.getValue("simcoll") != null)){
            
            assert (pmacinstDir != null);
            assert (testCase != null);
            
            TraceCheck traceChecker = new TraceCheck();
            if(noStop) {
                traceChecker.setNoStop();
            }
            
            if(!traceChecker.setPmacinstDir(pmacinstDir)){
                Logger.warn("There are ERROR(s) in test case identifications under " + pmacinstDir + " for trace checking");
                return false;
            }
            
            if(!traceChecker.consistencyCheck(testCase.getApplication(),testCase.getDataset(),testCase.getCpu())){
                Logger.warn("There are ERROR(s) in consistency check for test case, " + testCase + " please manually check!!!!");
                return false;
            }
        }
        
        return true;
    }

    public void prepareActionList(){

        String pmacinstDir  = (String)optionParser.getValue("pmacinst_dir");
        String baseResource = (String)optionParser.getValue("resource");

        Database dbase = null; /** send does not use dbase **/

        Object value = optionParser.getValue("exec");
        if(value != null){
            String strVal = (String)value;
            Object[] fileAndTime = Util.fileTimeTuple(strVal);
            assert (fileAndTime != null);
            actionList.add(new Executable((String)fileAndTime[0],
                                          ((Double)fileAndTime[1]).doubleValue(),baseResource,
                                          testCase,dbase));
        }

        value = optionParser.getValue("mpidtrace");
        if(value != null){
            String strVal = (String)value;
            Object[] fileAndTime = Util.fileTimeTuple(strVal);
            assert (fileAndTime != null);
            actionList.add(new Mpidtrace((String)fileAndTime[0],
                                         ((Double)fileAndTime[1]).doubleValue(),
                                         baseResource,
                                         testCase,dbase));
        }

        /*** Mitesh Edits **/
        value = optionParser.getValue("ipmdata");
        if(value != null){
            String strVal = (String)value;
            Object[] fileAndTime = Util.fileTimeTuple(strVal);
            assert (fileAndTime != null);
            actionList.add(new Ipm((String)fileAndTime[0],
                                     ((Double)fileAndTime[1]).doubleValue(),
                                     baseResource,
                                     testCase,dbase));
        }

        /*** Mitesh added stuff for syshealth data **/
        value = optionParser.getValue("syshealth");
        if(value != null){
            String strVal = (String)value;
            Object[] fileAndTime = Util.fileTimeTuple(strVal);
            assert (fileAndTime != null);
            actionList.add(new SysHealth((String)fileAndTime[0],
                                         ((Double)fileAndTime[1]).doubleValue(),
                                         baseResource,
                                         testCase,dbase));
        }

        value = optionParser.getValue("psinstrace");
        if(value != null){
            String strVal = (String)value;
            Object[] fileAndTime = Util.fileTimeTuple(strVal);
            assert (fileAndTime != null);
            actionList.add(new Psins((String)fileAndTime[0],
                                     ((Double)fileAndTime[1]).doubleValue(),
                                     baseResource,
                                     testCase,dbase));
        }

        value = optionParser.getValue("jbbinst");
        if(value != null){
            assert (pmacinstDir != null);
            actionList.add(new JbbInst(pmacinstDir,testCase,dbase));
        }

        value = optionParser.getValue("jbbcoll");
        if(value != null){
            assert (pmacinstDir != null);
            Double time = (Double)value;
            actionList.add(new JbbColl(pmacinstDir,time.doubleValue(),
                                       testCase,dbase));
        }

        int phaseCount = 0;
        value = optionParser.getValue("num_phases");
        if(value != null){
            phaseCount = ((Integer)value).intValue();
        }

        value = optionParser.getValue("siminst");
        if(value != null){
            assert (pmacinstDir != null);
            assert (phaseCount > 0);
            String strVal = (String)value;
            Object[] phases = Util.phaseListValue(strVal);
            for(int i=0;i<phases.length;i++){
                int phaseId = ((Integer)phases[i]).intValue();
                Logger.debug("siminst pidx : " + phaseId);
                actionList.add(new SimInst(pmacinstDir,phaseId,phaseCount,
                                           testCase,dbase));
            }
        }

        value = optionParser.getValue("simcoll");
        if(value != null){
            assert (pmacinstDir != null);
            assert (phaseCount > 0);
            String strVal = (String)value;
            Object[] phases = Util.phaseListTimeValue(strVal);
            for(int i=0;i<phases.length;i++){
                int phaseId = ((Integer)phases[i++]).intValue();
                double phaseTime = ((Double)phases[i]).doubleValue();
                Logger.debug("simcoll pidx : " + phaseId + " : " + phaseTime);
                actionList.add(new SimColl(pmacinstDir,phaseId,phaseTime,phaseCount,
                                           testCase,dbase));
            }
        }

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
        Logger.inform("Test case naming segment " + testCase.ftpName());

        Logger.inform("Verifying the options");
        optionParser.verify();

        String notifyEmail  = (String)optionParser.getValue("notify");
        assert (notifyEmail != null);

        String baseResource = (String)optionParser.getValue("resource");

        Logger.inform("Constructing the list of objects for submission");

        if(optionParser.getValue("trc_nochck") != null){
            Logger.warn("Trace check is disabled expliclity by the user, so no trace checking or verification for computation");
        } else {
            if(!traceVerification(optionParser.getValue("chck_nostop") != null)){
                Logger.warn("Could not pass trace verifications, manually check and run send again, or use --trc_nochck option if you are SURE");
                return false;
            }
        }
        
        prepareActionList();

        if(actionList.size() == 0){
            Logger.inform("There is NO data to be submitted, check your options",true);
            return true;
        }

        Logger.inform("Sending the data one object at a time");

        boolean overallStatus = true;
        Iterator it = actionList.iterator();
        while(it.hasNext()){
            Trace currentData = (Trace)it.next();

            Logger.inform("");
            Logger.inform("Sending for " + currentData);

            boolean status = currentData.send(notifyEmail,baseResource,
                                              optionParser.getValue("noverify") != null, optionParser.getValue("report") != null);
            if(status){
                Logger.inform("SUCCESS");
            } else {
                Logger.inform("FAIL");
            } 
            overallStatus = overallStatus && status;
        }

        return overallStatus;
    }

    public static void main(String argv[]) {
        try {
            Send send = new Send();
            boolean status = send.run(argv);
            if(status){
                Logger.inform("\n*** DONE *** SUCCESS *** SUCCESS *** SUCCESS *****************\n");
            } 
        } catch (Exception e) {
            Logger.error(e, "*** FAIL *** FAIL *** FAIL Exception *****************\n");
        }
    }
    static final String helpString = 
     "[Basic Params]:\n" +
     "    --help                               : Print a brief help message\n" +
     "    --version                            : Print the version and exit\n" +
     "[Test Case Params]:\n" +
     "    --funding_agency <funding_agency>    : funding agency. listed in PSaPP config file. [REQ]\n" +
     "    --project        <project>           : project. listed in PSaPP config file. [REQ]\n" +
     "    --round          <n>                 : round number [REQ]\n" +
     "    --application    <application>       : application. listed in PSaPP config file. [REQ]\n" +
     "    --dataset        <dataset>           : dataset name. listed in PSaPP config file. [REQ]\n" +
     "    --cpu_count      <cpu_count>         : number of cpus [REQ]\n" +
     "[Script Params]:\n" +
     "    --notify         <email_address>     : receive a confirmation [REQ]\n" +
     "    --noverify                           : for psinstrace and mpidtrace disable verification of post processing,\n" +
     "                                         : otherwise does nothing\n" +
     "    --report                             : receive a performance synopsis\n" +
     "[Collection Choices]:\n" +
     "    --resource     datastar              : nickname of collection resource\n" +
     "    --exec         to/myexecutable,8070  : submit executable with runtime\n" +
     "    --mpidtrace    to/file.trf.gz,8624   : zipped trf file and trace time\n" +
     "    --psinstrace   to/file.psins.gz,4321 : zipped psins trace file and trace time\n" +
     "    --jbbinst                            : submit jbbinst\n" +
     "    --jbbcoll      7000                  : submit jbbcoll <tracetime>\n" +
     "    --siminst      p01,p02               : submit siminst <phase list where each is pNN format>\n" +
     "    --simcoll      p01=9000,p02=8100     : submit simcoll <phase=tracetime list where phase is pNN format>\n" +
     "    --num_phases   2                     : total number of phases\n" +
     "    --pmacinst_dir path/with/pmacTRACE   : directory where traces are stored during tracing\n" +
     "    --tau          9000                  : submit tau <tracetime>. !!!Not Implemented Yet.\n" +
     "    --ipmdata      to/IPM_XML_file,1202  : submit IPMdata XML file with application runtime\n" +
     "    --syshealth    to/datafile,1202      : submit healthdata file with application runtime\n" +
     "[Trace check/verification Params]:\n" +
     "    --trc_nochck                         : disasbles trace check/verification before submission of compute traces\n" +
     "    --chck_nostop                        : trace check/verification continues even if an error is seen, but submission is not\n";
}
