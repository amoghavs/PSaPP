package PSaPP.util;

/*
Copyright (c) 2011, PMaC Laboratories, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

 *  Redistributions of source code must retain the above copyright notice, this list of conditions
and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  Neither the name of PMaC Laboratories, Inc. nor the names of its contributors may be
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
import PSaPP.util.*;
import PSaPP.greenqueue.*;

import java.util.*;
import java.util.regex.*;
import java.io.*;

public class RunTool implements CommandLineInterface {

    TestCase testCase;
    String processedDir;
    String rawDir;
    String scratchDir;
    String pmactraceDir;
    Set<Integer> sysids;

    OptionParser optionParser;

    private static final String[] validReportSwitches = { "all", "block", "dud", "gpu", "trace", "range", "summary", "instruction", "functionvector", "blockvector", "vectorization", "loopview", "addressranges", "functioncalls", "scattergather", "sourcelines"};

    static final String[] ALL_OPTIONS = {
        "help:?",
        "version:?",

        "funding_agency:s",
        "project:s",
        "round:i",
        "application:s",
        "cpu_count:i",
        "dataset:s",

        "processed_dir:s",
        "scratch_dir:s",
        "trace_dir:s",
        "raw_pmactrace:s",

        "process:?",
        "greenqueue:?",
        "phase_heads:?",
        "loop_times:?",
        "gq_balanced:?",
        "gq_unbalanced:?",
        "check_energydb:?",
        "create_energydb_profiles:?",
        "write_best_energy_freqs:s",
        "report:s",
        "sysid:s",
        "predict:?",

        "loops:s",
        "select:s",
        "selectOuter:?",
        "functions:s",
        "merge:s",
        "minPhaseSize:s",
        "rank:s",
        "pruneUnsimulated:?",
        "noInline:?",
        "full:?"

    };

    private final String helpString(){
        return 
            "[Basic Params]:\n" +
            "    --help                              : print a brief help message\n" +
            "    --version                           : print the version and exit\n" +
            "[Test Case Params]:\n" +
            "    --funding_agency   <funding_agency> : funding agency. listed in PSaPP config file.\n" +
            "    --project          <project>        : project. listed in PSaPP config file.\n" +
            "    --round            <n>              : round number. listed in PSaPP config file.\n" +
            "    --application      <application>    : application. listed in PSaPP config file.\n" +
            "    --dataset          <dataset>        : dataset name. listed in PSaPP config file.\n" +
            "    --cpu_count        <cpu_count>      : number of cpus. listed in PSaPP config file.\n" +
            "[Script Modules]:\n" +
            "    --process                           : process raw traces into a trace database for a test case\n" +
            "    --greenqueue                        : create a frequency configuration for the test case\n" +
            "    --phase_heads                       : create a file phase_heads.tp with loop heads of possible phases\n" +
            "    --loop_times                        : write a loopTimes file\n" +
            "    --gq_unbalanced                     : do unbalanced scaling in the greenque module\n" +
            "    --gq_balanced                       : do balanced scaling in the greenqueue modulel\n" +
            "    --check_energydb                    : perform checks on energy db\n" +
            "    --create_energydb_profiles          : create code profiles for energydb\n" +
            "    --write_best_energy_freqs <infile>  : writes best frequencies for testcases in infile to infile.freqs\n" +
            "    --report           <type>           : generate reports from a trace database\n" +
            "                                          " + validReportString() + "\n" +
            "    --sysid            <sysidlist>      : perform actions on only a subset of sysids\n" +
            "    --predict (TODO)                    : generate convolver predictions from a trace database\n" +
            
            "    --scratch_dir      </path/to/scratch>   : scratch directory\n" +
            "    --trace_dir        </path/to/raw>   : directory containing raw traces\n" +
            "    --raw_pmactrace    </path/to/pmacTRACE> : traces are raw/unsent\n" +
            "    --processed_dir    </path/to/processed> : directory where reports and trace database will be put\n";
    }

    private boolean needTestCase = false;
    private boolean needTraceDB = false;
    private boolean needEnergyDB = false;
    private boolean needPmacTrace = false;
    private boolean needPSiNSDB = false;
    private boolean needSysids = false;

    private boolean doProcessPmacTrace = false;
    private boolean doProcessRaw = false;
    private boolean doFrequencies = false;
    private boolean doCreateEnergyDBCodeProfiles = false;
    private boolean doCheckEnergyDB = false;
    private boolean doWriteBestEnergyFreqs = false;
    private boolean doBalancedScaling = false;
    private boolean doUnbalancedScaling = false;
    private boolean doLoopTimeHeads = false;
    private boolean doLoopTimes = false;
    private boolean doPhases = false;
    private Map<String, String> phaseOptions = null;

    public boolean verifyValues(HashMap values) {

        if( optionParser.getValue("process") != null ) {
            needTestCase = true;
            needTraceDB = true;

            if( optionParser.getValue("raw_pmactrace") != null ) {
                doProcessPmacTrace = true;
                needPmacTrace = true;
            } else if( optionParser.getValue("trace_dir") != null ) {
                doProcessRaw = true;
            } else {
                Logger.error("Action process requires either raw_pmactrace or trace_dir to be set");
                return false;
            }
        }

        if( optionParser.getValue("loops") != null || optionParser.getValue("functions") != null || optionParser.getValue("blocks") != null ) {
            needTestCase = true;
            needTraceDB = true;
            needSysids = true;
            doPhases = true;
            phaseOptions = Util.newHashMap();
            phaseOptions.put("loops", (String)optionParser.getValue("loops"));
            phaseOptions.put("merge", (String)optionParser.getValue("merge"));
            phaseOptions.put("functions", (String)optionParser.getValue("functions"));
            phaseOptions.put("blocks", (String)optionParser.getValue("blocks"));
            phaseOptions.put("rank", (String)optionParser.getValue("rank"));
            phaseOptions.put("select", (String)optionParser.getValue("select"));
            phaseOptions.put("minPhaseSize", (String)optionParser.getValue("minPhaseSize"));
            if(optionParser.getValue("selectOuter") != null) {
                phaseOptions.put("selectOuter", "true");
            }
            if(optionParser.getValue("pruneUnsimulated") != null) {
                phaseOptions.put("pruneUnsimulated", "true");
            }
            if(optionParser.getValue("noInline") != null) {
                phaseOptions.put("noInline", "true");
            }
            if(optionParser.getValue("full") != null) {
                phaseOptions.put("full", "true");
            }
        }

        if( optionParser.getValue("greenqueue") != null ) {
            needTestCase = true;
            needTraceDB = true;
            needEnergyDB = true;
            needPmacTrace = true;
            needPSiNSDB = true;
            doFrequencies = true;
        }

        if( optionParser.getValue("phase_heads") != null ) {
            needTestCase = true;
            needTraceDB = true;
            needPmacTrace = true;
            doLoopTimeHeads = true;
        }

        if( optionParser.getValue("loop_times") != null ) {
            needTestCase = true;
            needTraceDB = true;
            needPmacTrace = true;
            doLoopTimes = true;
        }

        if( optionParser.getValue("gq_balanced") != null ) {
            needTestCase = true;
            needTraceDB = true;
            needEnergyDB = true;
            needPmacTrace = true;
            doBalancedScaling = true;
        }

        if( optionParser.getValue("gq_unbalanced") != null ) {
            needTestCase = true;
            needPmacTrace = true;
            needEnergyDB = true;
            needPSiNSDB = true;
            doUnbalancedScaling = true;
        }

        if( optionParser.getValue("create_energydb_profiles") != null ) {
            doCreateEnergyDBCodeProfiles = true;
        }

        if( optionParser.getValue("check_energydb") != null ) {
            needEnergyDB = true;
            doCheckEnergyDB = true;
        }

        if( optionParser.getValue("write_best_energy_freqs") != null ) {
            needEnergyDB = true;
            doWriteBestEnergyFreqs = true;
        }

        if( optionParser.getValue("report") != null ) {
            needTestCase = true;
            needTraceDB = true;
        }

        return true;
    }

    public boolean isHelp (HashMap values) {
        return values.get("help") != null;
    }

    public boolean isVersion(HashMap values) {
        return values.get("version") != null;
    }

    public void printUsage(String str) {
        System.out.println("\n" + str + "\n");
        System.out.println(helpString());
        String all = "usage :\n";
        for (int i = 0; i < ALL_OPTIONS.length; ++i) {
            all += ("\t--" + ALL_OPTIONS[i] + "\n");
        }
        all += ("\n" + str);
    }

    public TestCase getTestCase(HashMap values) {
        return this.testCase;
    }

    private String validReportString(){
        String s = "[" + validReportSwitches[0];
        for (int i = 1; i < validReportSwitches.length; i++){
            s += "," + validReportSwitches[i];
        }
        return s + "]";
    }

    private boolean isValidReportSwitch(String f){
        for (String s : validReportSwitches){
            if (f.toLowerCase().equals(s)){
                return true;
            }
        }
        return false;
    }

    private TraceDB setupTraceDB() {
        TraceDB tracedb = null;

        if( doProcessPmacTrace ) {
            tracedb = new RawTraceProcessor(testCase, scratchDir, processedDir, rawDir, pmactraceDir, sysids);
        } else if ( doProcessRaw ) {
            tracedb = new LegacyTraceProcessor(testCase, scratchDir, processedDir, rawDir, sysids);
        } else {
            tracedb = new TraceDB();
            tracedb.initialize(this.processedDir, this.testCase, this.sysids);
        }

        return tracedb;
    }

    private boolean run(String argv[]) {

        if( !ConfigSettings.readConfigFile() ) {
            return false;
        }

        this.optionParser = new OptionParser(ALL_OPTIONS, this);
        if( argv.length < 1 ) {
            optionParser.printUsage("");
        }

        optionParser.parse(argv);
        if( optionParser.isHelp() ) {
            optionParser.printUsage("");
        }

        if( !optionParser.verify() ) {
            Logger.error("Error in command line options");
        }

        this.rawDir = (String) optionParser.getValue("trace_dir");
        this.processedDir = (String) optionParser.getValue("processed_dir");
        this.scratchDir = (String) optionParser.getValue("scratch_dir");
        this.pmactraceDir = (String)optionParser.getValue("raw_pmactrace");

        if (this.rawDir != null && this.pmactraceDir != null){
            Logger.error("--trace_dir and --raw_pmactrace cannot be used together");
        }


        // Set Test case
        if( needTestCase ) {
            String app = (String) optionParser.getValue("application");
            String dataset = (String) optionParser.getValue("dataset");
            Integer coreCount = (Integer)optionParser.getValue("cpu_count");
            String agency = (String) optionParser.getValue("funding_agency");
            if(agency == null || agency.equals("")) agency = "none";
            String project = (String) optionParser.getValue("project");
            if(project == null || project.equals("")) project = "test";
            Integer round = (Integer) optionParser.getValue("round");
            if(round == null) round = 1;
            this.testCase = new TestCase(app, dataset, coreCount, agency, project, round);
        }

        // Set sysids
        String sysid = (String)optionParser.getValue("sysid");
        if(needSysids && (sysid == null || sysid.equals(""))) {
            Logger.error("Sysids required for this action");
            return false;
        }
        Integer[] syslist = Util.machineList(sysid);
        if( syslist == null ) {
            sysids = null;
        } else {
            sysids = Util.newHashSet();
            for (int i : syslist){
                sysids.add(i);
            }
        }

        // Set pmactrace dir
        PmacTrace pmactrace = null;
        if( needPmacTrace ) {
            pmactrace = new PmacTrace(pmactraceDir, testCase);

            if( processedDir == null ) {
                File f = pmactrace.getProcessed();
                f.mkdirs();
                processedDir = f.getAbsolutePath();
            }

            if( scratchDir == null ) {
                File f = pmactrace.getScratch();
                f.mkdirs();
                scratchDir = f.getAbsolutePath();
            }
        }

        // Setup a tracedb 
        TraceDB tracedb = null;
        if( needTraceDB ) {
            tracedb = setupTraceDB();
        }

        // Energy database
        if( doCreateEnergyDBCodeProfiles ) {
            File pcubedData = new File(ConfigSettings.getSetting("PCUBED_DATA"));
            File p3profiles = new File(ConfigSettings.getSetting("PCUBED_DATA") + ".profiles");

            /*
            try {
                EnergyProfileDB.createCodeProfiles(new File(this.pmactraceDir), pcubedData, sysids.iterator().next(), p3profiles);
            } catch (IOException e) {
                Logger.error(e, "Unable to create energydb code profiles");
                return false;
            }
            */
        }

        EnergyProfileDB edb = null;
        if( needEnergyDB ) {

            File modelscript = new File(ConfigSettings.getSetting("POWER_MODELER"));
            String modeldata = ConfigSettings.getSetting("POWER_MODELER_DATA");

            LoopTimeDB loopTimes = null;
            try {
                loopTimes = new LoopTimeDB(new File(ConfigSettings.getSetting("LOOP_TIMES"), testCase.getApplication() + ".loopTimes"));
            } catch (IOException e ) {
                Logger.error(e, "Unable to read loop times file ");
                return false;
            }
            edb = new PowerModelerDB(modelscript, modeldata, loopTimes);

            //File pcubedData = new File(ConfigSettings.getSetting("PCUBED_DATA"));
            //File p3profiles = new File(ConfigSettings.getSetting("PCUBED_DATA") + ".profiles");

            /*
            try {
                edb = new EnergyProfileDB(p3profiles, pcubedData);
            } catch (IOException e) {
                Logger.error(e, "Unable to create pcubed database");
                return false;
            }
            */
        }

        /*
        if( doCheckEnergyDB ) {
            try {
                edb.verify(new File("suspiciousProfiles"), new File("energyDBDiffs.dat"));
                edb.writeRanges(null);
                edb.writeFrequencyProfiles(new File("frequencyProfiles.dat"));
            } catch (IOException e) {
                Logger.error(e, "Unable to verify energy database");
                return false;
            }
        }
        */

        /*
        if( doWriteBestEnergyFreqs ) {
            try {
                String infilename = (String)optionParser.getValue("write_best_energy_freqs");
                File infile = new File(infilename);
                File outfile = new File(infilename + ".freqs");
                edb.writeBestFrequencies(infile, outfile);
            } catch (IOException e) {
                Logger.error(e, "Unable to write energydb freqs");
                return false;
            }
        }
        */

        // PSiNSDB
        PSiNSDB psinsdb = null;
        PSiNSCount psinsc = null;
        if( needPSiNSDB ) {
            File psinsFile = pmactrace.getRankPidExtended();
            psinsdb = new PSiNSDB();
            if( psinsFile.exists() ) {
                psinsc = psinsdb.addPSiNSCount(psinsFile, testCase.getApplication(), testCase.getDataset(), testCase.getCpu(), "any");
            } else {
                Logger.warn("RankPid.extended file: " + psinsFile + " does not exist");
                psinsc = null;
            }
        }

        // Greenqueue
        // run1/loopTimers
        long minPhaseSize = 50000L;

        // run2
        //long minPhaseSize = 5000000L;

        // run3
        //long minPhaseSize = 4000000L;

        // run 4
        //long minPhaseSize = 3000000L;

        // run 5
        //long minPhaseSize = 2000000L;

        // run 6
        //long minPhaseSize = 1000000L;

        // run 7
        //long minPhaseSize = 500000L;

        // run 8
        //long minPhaseSize = 250000L;

        // run 9
        //long minPhaseSize = 125000L;

        // run 10
        //long minPhaseSize = 10000000L;

        long minInlineSize = 10000L;
        if( doLoopTimeHeads ) {
            int sid = sysids.iterator().next();
            Logger.inform("Doing loop time heads");
            GreenQueue greenqueue = new GreenQueue(edb, tracedb, sid, psinsc);
            greenqueue.doLoopTimeHeads(minPhaseSize, minInlineSize);
        }

        if( doLoopTimes ) {
            int sid = sysids.iterator().next();
            Logger.inform("Doing loopTimes");
            GreenQueue greenqueue = new GreenQueue(edb, tracedb, sid, psinsc);
            greenqueue.modelLoopTimes(minPhaseSize, minInlineSize);
        }

        if( doFrequencies ) {
            int sid = sysids.iterator().next();
            Logger.inform("Doing greenqueue");
            GreenQueue greenqueue = new GreenQueue(edb, tracedb, sid, psinsc);
            greenqueue.selectFrequencies(minPhaseSize, minInlineSize);
            greenqueue.writeThrottlePoints(this.processedDir + "/" + this.testCase.shortName() + ".tp");
            greenqueue.writeFrequencyConfiguration(this.processedDir + "/" + this.testCase.shortName() + ".fc");
        }

        if( doBalancedScaling ) {
            int sid = sysids.iterator().next();
            GreenQueue greenqueue = new GreenQueue(edb, tracedb, sid);
            greenqueue.doBalancedScaling(minPhaseSize, minInlineSize);
            greenqueue.writeThrottlePoints(this.processedDir + "/" + this.testCase.shortName() + ".tp");
            greenqueue.writeFrequencyConfiguration(this.processedDir + "/" + this.testCase.shortName() + ".fc");
        }

        if( doUnbalancedScaling ) {
            GreenQueue greenqueue = new GreenQueue(edb, psinsc);
            greenqueue.doUnbalancedScaling();
            greenqueue.writeThrottlePoints(this.processedDir + "/" + this.testCase.shortName() + ".tp");
            greenqueue.writeFrequencyConfiguration(this.processedDir + "/" + this.testCase.shortName() + ".fc");
        }


        if( doPhases ) {
            Phases phases  = new Phases(tracedb, sysids.iterator().next());
            phases.run(phaseOptions, this.testCase.getCpu());
        }

        // Generate other reports
        String report = (String)optionParser.getValue("report");
        if (report != null){
            assert( this.processedDir != null );

            String[] keys = report.split(",");
            Set<String> flags = Util.newHashSet();
            for (String s : keys){
                if (!isValidReportSwitch(s)){
                    Logger.error("invalid option sent to --report. valid options are: " + validReportString());
                }
                flags.add(s.toLowerCase());
            }

            TraceReporter reporter = new TraceReporter(this.testCase, this.processedDir, tracedb, sysids);

            reporter.writeReports(flags);
        }

        if( optionParser.getValue("predict") != null ) {
            Logger.warn("--predict not yet implemented. Ignoring.");
        }

        if( tracedb != null ) {
            tracedb.close();
        }

        return true;
    }

    public static void main(String argv[]) {
        RunTool pt = new RunTool();
        if( pt.run(argv) ) {
            Logger.inform("Success");
        } else {
            Logger.error("Failure");
        }
    }
}

