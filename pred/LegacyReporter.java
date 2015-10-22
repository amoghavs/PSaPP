package PSaPP.pred;

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

import java.util.*;
import java.util.regex.*;
import java.io.*;

public class LegacyReporter implements CommandLineInterface {

    TestCase testCase;
    String processedDir;
    OptionParser optionParser;
    Reports reports;

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

        "summary:?"
    };

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
        "    --processed_dir    </path/to/processed_trace> : path to directory containing processed trace files [REQ]\n" +
        "[Script Params]:\n" +
        "    --summary                           : generate a summary report from rank.sysid files\n";

    public boolean verifyValues(HashMap values) {
        if (values.get("processed_dir") == null) {
            Logger.error("--processed_dir is a required argument");
            return false;
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
        System.out.println(helpString);
        String all = "usage :\n";
        for (int i = 0; i < ALL_OPTIONS.length; ++i) {
            all += ("\t--" + ALL_OPTIONS[i] + "\n");
        }
        all += ("\n" + str);
    }

    public TestCase getTestCase(HashMap values) {
        return this.testCase;
    }

    public static void main(String argv[]) {
        LegacyReporter lr = new LegacyReporter();
        boolean status = lr.run(argv);
        if( status ) {
            Logger.inform("Success");
        } else {
            Logger.error("Failure");
        }
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

        this.processedDir = (String) optionParser.getValue("processed_dir");

        String app = (String) optionParser.getValue("application");
        String dataset = (String) optionParser.getValue("dataset");
        Integer coreCount = (Integer)optionParser.getValue("cpu_count");
        String agency = (String) optionParser.getValue("funding_agency");
        String project = (String) optionParser.getValue("project");
        Integer round = (Integer) optionParser.getValue("round");

        this.testCase = new TestCase(app, dataset, coreCount, agency, project, round);
        this.reports = new Reports(this.testCase, this.processedDir);

        boolean status = true;
        if( optionParser.getValue("summary") != null ) {
            if( !writeSummaries() ) {
                Logger.warn("Unable to write summary files");
                status = false;
            }
        }
        return status;
    }

    private Map<Integer, Set<File>> getSysidRankFiles() {
        Map<Integer, Set<File>> sysidToFiles = Util.newHashMap();
        File traceDir = new File(this.processedDir);
        File traces[] = traceDir.listFiles();

        Pattern p = Pattern.compile(this.testCase.shortName() + "_\\d\\d\\d\\d.sysid(\\d+)");
        for( int i = 0; i < traces.length; ++i ) {
            String fname = traces[i].getName();

            Matcher m = p.matcher(fname);
            if( !m.matches() ) {
                continue;
            }
            Integer sysid = Integer.parseInt(m.group(1));

            Set<File> fs = sysidToFiles.get(sysid);
            if( fs == null ) {
                fs = Util.newHashSet();
                sysidToFiles.put(sysid, fs);
            }
            fs.add(traces[i]);
        }
        return sysidToFiles;
    }

    private File getStaticFile() {
        return new File(this.processedDir + "/" + this.testCase.shortName() + ".static");
    }

    private File getBbbytesFile() {
        return new File(this.processedDir + "/" + this.testCase.shortName() + ".bbbytes");
    }

    private File getBb2FuncFile() {
        return new File(this.processedDir + "/" + this.testCase.shortName() + ".bb2func");
    }

    private boolean writeSummaries() {

        // Find all sysid files
        Map<Integer, Set<File>> sysidToFiles = getSysidRankFiles();
        Set<Integer> sysids = sysidToFiles.keySet();
        if( sysids.size() == 0 ) {
            return false;
        }

        // Parse static files
        StaticParser sp = new StaticParser(getStaticFile());
        Map<BlockID, BasicBlock> blocks = sp.getBlocks();

        BbbytesParser bp = new BbbytesParser(getBbbytesFile());
        Map<BlockID, Double> bbidToBytesPerMemop = bp.getBbidToBytesPerMemop();

        Bb2FuncParser bb2fp = new Bb2FuncParser(getBb2FuncFile());
        Map<BlockID, Function> bbidToFunc = Util.newHashMap();
        Collection<Function> funcs = Util.newHashSet();
        {
            Map<String, Set<BlockID>> fnameToBbids = bb2fp.getFuncToBbids();
            for( Iterator<Map.Entry<String, Set<BlockID>>> eit = fnameToBbids.entrySet().iterator();
                 eit.hasNext(); ) {

                Map.Entry<String, Set<BlockID>> e = eit.next();
                String fname = e.getKey();
                Set<BlockID> bbids = e.getValue();

                Function f = new Function();
                funcs.add(f);
                f.functionName = fname;
                f.dInsns = 0L;
                f.dMemOps = 0L;
                f.dFpOps = 0L;
                f.dMembytes = 0L;
                f.perSysCaches = Util.newHashMap();
                for( Iterator<BlockID> bit = bbids.iterator(); bit.hasNext(); ) {
                    BlockID bbid = bit.next();
                    bbidToFunc.put(bbid, f);
                }
            }
        }

        // Use first sysid to compute visitcount info which is the same for all systems
        Map<BlockID, Long> bbidToVisits = Util.newHashMap();
        {
            Iterator<Set<File>> sit = sysidToFiles.values().iterator();
            assert( sit.hasNext() );
            Set<File> rankSet = sit.next();

            for( Iterator<File> rit = rankSet.iterator(); rit.hasNext(); ) {
                File rFile = rit.next();
                RankSysidParser rsp = new RankSysidParser(rFile);
                Set<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> dBlocks = rsp.getBlocks();

                for( Iterator<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> bit = dBlocks.iterator(); bit.hasNext(); ) {
                    Tuple.Tuple4<BlockID, Long, Long, CacheStats> binfo = bit.next();
    
                    BlockID bbid = binfo.get1();
                    Long dynFlops = binfo.get2();
                    Long dynMemops = binfo.get3();
                    BasicBlock b = blocks.get(bbid);
                    Integer statFlops = b.fpOps;
                    Integer statMemops = b.memOps;

                    Long newVisits;
                    if( statMemops == 0 ) {
                        if( statFlops == 0 ) {
                            Logger.warn("Unable to determine a visit count for block " + bbid);
                            continue;
                        }
                        newVisits = dynFlops / statFlops;
                    } else {
                        newVisits = dynMemops / statMemops;
                    }
    
                    Long oldVisits = bbidToVisits.get(bbid);
                    if( oldVisits == null ) {
                        bbidToVisits.put(bbid, newVisits);
                    } else {
                        bbidToVisits.put(bbid, oldVisits + newVisits);
                    }
                    
                }
            }
        }

        // accumulate basic block counts into function
        for( Iterator<Map.Entry<BlockID, Long>> eit = bbidToVisits.entrySet().iterator(); eit.hasNext(); ) {
            Map.Entry<BlockID, Long> e = eit.next();
            BlockID bbid = e.getKey();
            Long visits = e.getValue();

            BasicBlock b = blocks.get(bbid);
            Double bytesPerMemop = bbidToBytesPerMemop.get(bbid);
            Function f = bbidToFunc.get(bbid);

            f.dInsns += visits * b.insns;
            f.dMemOps += visits * b.memOps;
            f.dFpOps += visits * b.fpOps;
            f.dMembytes += visits * ((Double)(b.memOps * bytesPerMemop)).longValue();
        }

        // add cache rates for each sysid
        for( Iterator<Map.Entry<Integer, Set<File>>> eit = sysidToFiles.entrySet().iterator(); eit.hasNext(); ) {
            Map.Entry<Integer, Set<File>> e = eit.next();
            Integer sysid = e.getKey();
            Set<File> rankSet = e.getValue();

            for( Iterator<File> rit = rankSet.iterator(); rit.hasNext(); ) {
                 RankSysidParser rsp = new RankSysidParser(rit.next());

                 Set<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> dBlocks = rsp.getBlocks();
                 for( Iterator<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> bit = dBlocks.iterator(); bit.hasNext(); ) {
                     Tuple.Tuple4<BlockID, Long, Long, CacheStats> binfo = bit.next();
    
                     BlockID bbid = binfo.get1();
                     Function f = bbidToFunc.get(bbid);
                     CacheStats c = f.perSysCaches.get(sysid);
                     if( c == null ) {
                         c = new CacheStats();
                         f.perSysCaches.put(sysid, c);
                     }
                     c.addCounts(binfo.get4());
                 }
            }
        }

        Vector<ReportWriter> writers = reports.summaries(sysids, funcs);
        return Reports.executeWriters(writers);
    }
}

/* Parsers for static appliction files */

class Bb2FuncParser {
    final File traceFile;

    Map<String, String> funcsToFiles = null;
    Map<String, Set<BlockID>> funcsToBbids = null;

    public Bb2FuncParser(File traceFile) {
        this.traceFile = traceFile;
    }

    public boolean parse() {
        try {
            BufferedReader rdr = new BufferedReader(new FileReader(this.traceFile));

            try {
                funcsToFiles = Util.newHashMap();
                funcsToBbids = Util.newHashMap();

                Pattern p = Pattern.compile("(\\d+)\\s+([^\\s]+)\\s+#\\s+(\\S+):(\\d+)");
                for( String line = rdr.readLine(); line != null; line = rdr.readLine()) {

                    if( line.startsWith("#") ) {
                        continue;
                    }

                    Matcher m = p.matcher(line);
                    if( !m.matches() ) {
                        Logger.warn("Unable to parse line " + line);
                        continue;
                    }
                    BlockID bbid = new BlockID(0L, Long.parseLong(m.group(1)));
                    String function = m.group(2);
                    String file = m.group(3);
                    Integer lineno = Integer.parseInt(m.group(4));

                    funcsToFiles.put(function, file);
                    Set<BlockID> bbids = funcsToBbids.get(function);
                    if( bbids == null ) {
                        bbids = Util.newHashSet();
                        funcsToBbids.put(function, bbids);
                    }
                    bbids.add(bbid);
                }
            } finally {
                rdr.close();
            }

        } catch (IOException e) {
            this.funcsToBbids = null;
            this.funcsToFiles = null;
            Logger.error(e, "Unable to parse file " + traceFile);
            return false;
        }

        return true;
    }

    public Map<String, Set<BlockID>> getFuncToBbids() {
        if( this.funcsToBbids == null ) {
            this.parse();
        }

        return this.funcsToBbids;
    }

    public Map<BlockID, String> getBbidToFunc() {
        return null;
    }

    public Map<String, String> getFuncsToFiles() {
        if( this.funcsToFiles == null ) {
            this.parse();
        }
        return this.funcsToFiles;
    }
}

class BbbytesParser {
    final File traceFile;

    Map<BlockID, Double> bbidToBytesPerMemop = null;

    public BbbytesParser(File traceFile) {
        this.traceFile = traceFile;
    }

    public boolean parse() {
        try {
            BufferedReader rdr = new BufferedReader(new FileReader(this.traceFile));
            try {
                this.bbidToBytesPerMemop = Util.newHashMap();
                Pattern p = Pattern.compile("(\\d+)\\s+(\\d*\\.\\d*)");
                for( String line = rdr.readLine(); line != null; line = rdr.readLine() ) {

                    if( line.startsWith("#") ) {
                        continue;
                    }

                    Matcher m = p.matcher(line);
                    if (!m.matches()) {
                        Logger.warn("Unable to parse line " + line);
                        continue;
                    }
                    BlockID bbid = new BlockID(0L, Long.parseLong(m.group(1)));
                    Double avgBytesPerMemOp = Double.parseDouble(m.group(2));

                    this.bbidToBytesPerMemop.put(bbid, avgBytesPerMemOp);
        
                }
            } finally {
                rdr.close();
            }
        } catch (IOException e) {
            this.bbidToBytesPerMemop = null;
            Logger.error(e, "Unable to parse file " + this.traceFile);
            return false;
        }
        return true;
    }

    public Map<BlockID, Double> getBbidToBytesPerMemop() {
        if( this.bbidToBytesPerMemop == null ) {
            this.parse();
        }
        return this.bbidToBytesPerMemop;
    }
}

class StaticParser {
    final File traceFile;

    Map<BlockID, BasicBlock> blocks = null;

    public StaticParser(File traceFile) {
        this.traceFile = traceFile;
    }

    public boolean parse() {
        try {
            BufferedReader rdr = new BufferedReader(new FileReader(traceFile));
            try {
                this.blocks = Util.newHashMap();
                Pattern p = Pattern.compile("(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(-?\\d*\\.\\d*)");
                for( String line = rdr.readLine(); line != null; line = rdr.readLine() ) {
                    if( line.startsWith("#") ) {
                        continue;
                    }

                    Matcher m = p.matcher(line);
                    if (!m.matches()) {
                        Logger.warn("Unable to parse line " + line);
                        continue;
                    }

                    BasicBlock b = new BasicBlock();
                    b.bbid = new BlockID(0L, Long.parseLong(m.group(1)));
                    b.memOps = Integer.parseInt(m.group(2));
                    b.fpOps = Integer.parseInt(m.group(3));
                    b.insns = Integer.parseInt(m.group(4));

                    this.blocks.put(b.bbid, b);
        
                }
            } finally {
                rdr.close();
            }
        } catch (IOException e) {
            Logger.error(e, "Unable to parse file " + this.traceFile);
            this.blocks = null;
            return false;
        }
        return true;
    }

    public Map<BlockID, BasicBlock> getBlocks() {
        if( this.blocks == null ) {
            this.parse();
        }
        return this.blocks;
    }

}

class RankSysidParser {
    final File traceFile;

    Set<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> blocks = null;


    public RankSysidParser(File traceFile) {
        this.traceFile = traceFile;
    }

    public Set<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> getBlocks() {
        if( this.blocks == null ) {
            this.parse();
        }
        return this.blocks;
    }

    public boolean parse() {

        try {
            BufferedReader rdr = new BufferedReader(new FileReader(traceFile));
            try {
                this.blocks = Util.newHashSet();

                Pattern p = Pattern.compile("\\s+");
                for( String line = rdr.readLine(); line != null; line = rdr.readLine() ) {
                    if( line.startsWith("#") ) {
                        continue;
                    }
                    String[] fields = p.split(line);

                    if( fields.length < 3 ) {
                        Logger.warn("Unable to parse line " + line);
                        continue;
                    }

                    BlockID bbid = new BlockID(0L, Long.parseLong(fields[0]));
                    Long flops = Long.parseLong(fields[1]);
                    Long memops = Long.parseLong(fields[2]);

                    Tuple.Tuple4<BlockID, Long, Long, CacheStats> binfo;
                    if( fields.length < 4 ) {
                        binfo = Tuple.newTuple4(bbid, flops, memops, (CacheStats)null);
                        this.blocks.add(binfo);
                        continue;
                    }

                    CacheStats c = new CacheStats();
                    binfo = Tuple.newTuple4(bbid, flops, memops, c);
                    this.blocks.add(binfo);
                   
                    Float l1HitRate = Float.parseFloat(fields[3]);
                    Long l1hits = ((Float)(l1HitRate / 100.0F * memops)).longValue();
                    Long l1misses = memops - l1hits;

                    c.addLevelCounts(1, l1hits, l1misses);

                    if( fields.length < 5 ) {
                        continue;
                    }
                    Float l2HitRate = Float.parseFloat(fields[4]);
                    Long l2hits = ((Float)(l2HitRate / 100.0F * memops)).longValue() - l1hits;
                    Long l2misses = l1misses - l2hits;
                    c.addLevelCounts(2, l2hits, l2misses);

                    if( fields.length < 6 ) {
                        continue;
                    }
                    Float l3HitRate = Float.parseFloat(fields[5]);
                    Long l3hits = ((Float)(l3HitRate / 100.0F * memops)).longValue() - l1hits - l2hits;
                    Long l3misses = l2misses - l3hits;
                    c.addLevelCounts(3, l3hits, l3misses);
                }
            } finally {
                rdr.close();
            }
        } catch (IOException e) {
            Logger.error(e, "Unable to parse file " + this.traceFile);
            this.blocks = null;
            return false;
        }
        return true;
    }


}


