package PSaPP.data;
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

import PSaPP.util.*;
import PSaPP.dbase.*;

import java.util.*;
import java.util.regex.*;
import java.io.*;


public class LegacyTraceProcessor extends TraceDB {

    final String scratchDir;
    final String traceDir;
    final String processedDir;

    boolean tracesRaw = false;
    int phaseCount;

    String jbbInstPath;
    String jbbCollPath;
    String[] simInstPaths;
    String[] simCollPaths;

    public LegacyTraceProcessor(TestCase tc, String sd, String pd, Database db, Set<Integer> sysidList){

        this.testCase = tc;
        this.scratchDir = sd;
        this.processedDir = pd;
        this.traceDir = this.scratchDir + "/" + this.testCase.shortName();
        this.validSysid = sysidList;

        if (!locateRawTraces(db)){
            throw new java.lang.IllegalStateException("Raw trace paths in database are missing or invalid");
        }

        initialize(sysidList);
    }

    public LegacyTraceProcessor(TestCase tc, String sd, String pd, String rd, Set<Integer> sysidList) {

        this.testCase = tc;
        this.scratchDir = sd;
        this.processedDir = pd;
        this.traceDir = this.scratchDir + "/" + this.testCase.shortName();
        this.validSysid = sysidList;

        this.tracesRaw = false;

        if (!locateRawTraces(rd)){
            throw new java.lang.IllegalArgumentException("Unable to locate raw traces in " + rd);
        }

        initialize(sysidList);
    }

    public LegacyTraceProcessor(TestCase tc, String sd, String pd, String rd, String pt, Set<Integer> sysidList) {

        this.testCase = tc;
        this.scratchDir = sd;
        this.processedDir = pd;
        this.traceDir = this.scratchDir + "/" + this.testCase.shortName();
        this.validSysid = sysidList;

        this.tracesRaw = true;

        // assume a single phase
        phaseCount = 1;

        if (!setupRawTraces(pt)){
            Logger.warn("Unable to copy raw traces for " + this.testCase.shortName());
            throw new java.lang.IllegalArgumentException("unable to set up raw traces from " + pt);
        }

        initialize(sysidList);
    }

    private void initialize(Set<Integer> sysidList){
        super.destroy(processedDir);
        super.initialize(processedDir, testCase, sysidList);

        if( !this.process() ) {
            throw new java.lang.RuntimeException();
        }
    }

    private LegacyTraceProcessor(TestCase testCase) {
        this.testCase = testCase;
        this.scratchDir = null;
        this.traceDir = null;
        this.processedDir = null;
    }

    private String rawTraceSubdir(String pmactraceDir, String type){
        return pmactraceDir + "/" + type + "/" + testCase.shortName();
    }

    // copy raw trace files to scratch dir so that they are structured exactly like they would be
    // if we processed/untarred them in unpackTraces
    boolean setupRawTraces(String pmactraceDir) {

        // create target dir for all
        if( !Util.mkdir(this.traceDir) ) {
            Logger.warn("Unable to create target directory: " + this.traceDir);
            return false;
        }

        // jbbinst
        if (LinuxCommand.copy(rawTraceSubdir(pmactraceDir, "jbbinst") + "/" + jbbInstStaticFile(), traceDir + "/" + jbbInstStaticFile()) == null){
            Logger.warn("Cannot copy raw jbb static file: " + rawTraceSubdir(pmactraceDir, "jbbinst") + "/" + jbbInstStaticFile() + " -> " + traceDir + "/" + jbbInstStaticFile());
            return false;
        }

        // jbbcoll
        for (int rank = 0; rank < testCase.getCpu(); rank++){
            String src = rawTraceSubdir(pmactraceDir, "jbbcoll") + "/" + jbbInstFile(rank);
            String tgt = traceDir + "/" + jbbInstFile(rank);
            if (LinuxCommand.copy(src, tgt) == null){
                Logger.warn("Cannot copy raw jbbcoll file: " + src + " -> " + tgt);
                return false;
            }
        }

        // loopcnt: we will required it even though it could not exist in principle
        for (int rank = 0; rank < testCase.getCpu(); rank++){
            String src = rawTraceSubdir(pmactraceDir, "jbbcoll") + "/" + loopCntFile(rank);
            String tgt = traceDir + "/" + loopCntFile(rank);
            if (LinuxCommand.copy(src, tgt) == null){
                Logger.warn("Cannot copy raw loopcnt file: " + src + " -> " + tgt);
                return false;
            }
        }

        for (int phase = 1; phase <= phaseCount; phase++){

            // create target dir for phase
            if( !Util.mkdir(this.traceDir + "/p" + Format.phaseToString(phase)) ) {
                Logger.warn("Unable to create target directory: " + this.traceDir);
                return false;
            }

            // siminst
            if (LinuxCommand.copy(rawTraceSubdir(pmactraceDir, "siminst") + "/" + simInstStaticFile(phase), traceDir + "/" + simInstStaticFile(phase)) == null){
                Logger.warn("Cannot copy raw sim static file: " + rawTraceSubdir(pmactraceDir, "siminst") + "/" + simInstStaticFile(phase) + " -> " + traceDir + "/" + simInstStaticFile(phase));
                return false;
            }
        
            // simcoll
            for (int rank = 0; rank < testCase.getCpu(); rank++){
                String src = rawTraceSubdir(pmactraceDir, "simcoll") + "/" + simInstFile(phase, rank);
                String tgt = traceDir + "/" + simInstFile(phase, rank);
                if (LinuxCommand.copy(src, tgt) == null){
                    Logger.warn("Cannot copy raw simcoll file: " + src + " -> " + tgt);
                    return false;
                }
            }

            // dfp (not required)
            for (int rank = 0; rank < testCase.getCpu(); rank++){
                String src = rawTraceSubdir(pmactraceDir, "simcoll") + "/" + dfpFile(phase, rank);
                String tgt = traceDir + "/" + dfpFile(phase, rank);
                if (LinuxCommand.copy(src, tgt) == null){
                    Logger.warn("Cannot copy raw dfp file: " + src + " -> " + tgt);
                }
            }
        }

        return true;
    }

    static boolean allTracesExist(TestCase testCase, Database dataBase) {
        LegacyTraceProcessor p = new LegacyTraceProcessor(testCase);

        return p.locateRawTraces(dataBase);
    }

    boolean locateRawTraces(String rawTraceDir) {

        String prefix = rawTraceDir + "/" + testCase.ftpName();

        File jbbInst;
        File jbbColl;
        File[] simInst;
        File[] simColl;

        jbbInst = new File(prefix + "_jbbinst.tar.gz");
        if( !jbbInst.exists() ) {
            return false;
        }
        jbbInstPath = jbbInst.getAbsolutePath();
        Logger.debug("JbbInst Path : " + jbbInstPath);

        jbbColl = new File(prefix + "_jbbcoll.tar.gz");
        if( !jbbColl.exists() ) {
            return false;
        }
        jbbCollPath = jbbColl.getAbsolutePath();
        Logger.debug("JbbColl Path : " + jbbCollPath);

        File dir = new File(rawTraceDir);
        assert( dir.exists() );

        final Pattern siminstPat = Pattern.compile(testCase.ftpName() + "_siminst.*\\.tar\\.gz");
        simInst = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return siminstPat.matcher(name).matches();
            }
        });

        if( simInst.length == 0 ) {
            return false;
        }
        simInstPaths = new String[simInst.length];
        for( int i = 0; i < simInst.length; ++i ) {
            simInstPaths[i] = simInst[i].getAbsolutePath();
            Logger.debug("SimInst Path " + (i+1) + " : " + simInstPaths[i]);
        }

        final Pattern simcollPat = Pattern.compile(testCase.ftpName() + "_simcoll.*\\.tar\\.gz");
        simColl = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return simcollPat.matcher(name).matches();
            }
        });

        if( simColl.length == 0 ) {
            return false;
        }
        simCollPaths = new String[simColl.length];
        for( int i = 0; i < simColl.length; ++i ) {
            simCollPaths[i] = simColl[i].getAbsolutePath();
            Logger.debug("SimColl Path " + (i+1) + " : " + simCollPaths[i]);
        }

        if( simInst.length != simColl.length ){
            Logger.warn("Phase count for inst and coll does not match " + 
                        simInst.length + " != " + simColl.length);
            return false;
        }

        phaseCount = simInst.length;

        return true;
    }

    boolean locateRawTraces(Database dataBase) {
        String[] paths;

        paths = dataBase.getTraceFilePaths(testCase, "jbbinst");
        if( paths == null ) {
            return false;
        }
        jbbInstPath = paths[0];
        assert( jbbInstPath != null );
        Logger.debug("JbbInst path: " + jbbInstPath);

        paths = dataBase.getTraceFilePaths(testCase, "jbbcoll");
        if( paths == null ) {
            return false;
        }
        jbbCollPath = paths[0];
        assert( jbbCollPath != null );
        Logger.debug("JbbColl path: " + jbbCollPath);

        simInstPaths = dataBase.getTraceFilePaths(testCase, "siminst");
        if( simInstPaths == null ) {
            return false;
        }
        for( int i = 0; i < simInstPaths.length; ++i ) {
            if( simInstPaths[i] == null ) {
                return false;
            }
            Logger.debug("SimInst path " + (i+1) + ": " + simInstPaths[i]);
        }

        simCollPaths = dataBase.getTraceFilePaths(testCase, "simcoll");
        if( simCollPaths == null ) {
            return false;
        }
        for( int i = 0; i < simCollPaths.length; ++i ) {
            if( simCollPaths[i] == null ) {
                return false;
            }
            Logger.debug("SimColl path " + (i+1) + ": " + simCollPaths[i]);
        }

        if(simInstPaths.length != simCollPaths.length){
            Logger.warn("Phase count for inst and coll does not match " + 
                        simInstPaths.length + " != " + simCollPaths.length);
            return false;
        }
        phaseCount = simInstPaths.length;

        return true;
    }

    boolean unpackTraces() {

        if( LinuxCommand.unZipTar(jbbInstPath, scratchDir) == null ) {
            Logger.warn("Cannot un-zip-tar " + jbbInstPath);
            return false;
        }

        if( LinuxCommand.unZipTar(jbbCollPath, scratchDir) == null ) {
            Logger.warn("Cannot un-zip-tar " + jbbCollPath);
            return false;
        }

        for( int i = 0; i < phaseCount; ++i ) {
            if( LinuxCommand.unZipTar(simInstPaths[i], scratchDir) == null ) {
                Logger.warn("Cannot un-zip-tar " + simInstPaths[i]);
                return false;
            }
            if( LinuxCommand.unZipTar(simCollPaths[i], scratchDir) == null ) {
                Logger.warn("Cannot un-zip-tar " + simCollPaths[i]);
                return false;
            }
        }

        return true;
    }

    // Trace file locations
    String jbbInstStaticFile() {
        return testCase.getApplication() + ".jbbinst.static";
    }

    String jbbInstFile(int rank) {
        return testCase.getApplication() + ".meta_" + Format.cpuToString(rank) + ".jbbinst";
    }

    String loopCntFile(int rank) { 
        return testCase.getApplication() + ".meta_" + Format.cpuToString(rank) + ".loopcnt";
    }

    String simInstFile(int phase, int rank) {
        return "p" + Format.phaseToString(phase) + "/" +
               testCase.getApplication() + ".phase." + phase + ".meta_" +
               Format.cpuToString(rank) + "." + Format.cpuToString(testCase.getCpu()) +
               ".siminst";
    }

    String simInstSysidPath(int phase){
        return "p" + Format.phaseToString(phase) + "/sysid/";
    }

    String simInstSysidFile(int phase, int sysid) {
        return simInstSysidPath(phase) +
               testCase.getApplication() + ".phase." + phase + "." + Format.cpuToString(testCase.getCpu()) +
               ".siminst" + ".sysid" + sysid;
    }

    String dfpFile(int phase, int rank){
        return "p" + Format.phaseToString(phase) + "/dfp/" + 
               testCase.getApplication() + ".phase." + phase + ".meta_" +
               Format.cpuToString(rank) + ".dfp";
    }

    String simInstStaticFile(int phase) {
        return "p" + Format.phaseToString(phase) + "/" +
               testCase.getApplication() + ".phase." + phase + "." + Format.cpuToString(testCase.getCpu()) +
               ".siminst.static";
    }

    // Method to process raw traces into a TraceDB
    public boolean process() {

        // -- Prepare -- //
        Logger.inform("Preparing to process trace data");

        // Create scratch directory
        if( !Util.mkdir(this.scratchDir) ) {
            Logger.warn("Unable to create scratch directory: " + this.scratchDir);
            return false;
        }

        // Make processed_trace directory
        if( !Util.mkdir(this.processedDir) ) {
            Logger.warn("Cannot make processed directory" + this.processedDir);
            return false;
        }

        // Unpack raw traces to scratch directory
        if (!tracesRaw){
            if( !unpackTraces() ) {
                Logger.warn("Unable to unpack traces for " + this.testCase);
                return false;
            }
        } 

        // -- Read raw trace files -- //
        Logger.inform("Processing trace data");

        if( !this.initializeTables() ) {
            return false;
        }

        // Records static information 
        if( !readJbbInstStatic(traceDir + "/" + jbbInstStaticFile()) ) {
            return false;
        }

        // Records basic block visit counts
        for( int rank = 0; rank < testCase.getCpu(); ++rank ) {
            if( !readJbbInst(traceDir + "/" + jbbInstFile(rank), rank) ) {
                return false;
            }
        }

        // Records loop visit counts
        for( int rank = 0; rank < testCase.getCpu(); ++rank ) {
            readLoopCnt(traceDir + "/" + loopCntFile(rank), rank);
        }

        // Records basic block cache hit rates
        Map<Integer, Integer> sysidLevels = null;
        for( int phase = 1; phase <= phaseCount; ++phase ) {
            Map<Integer, BlockID> seqToBbid = readSimInstStatic(traceDir + "/" + simInstStaticFile(phase));
            if( seqToBbid == null ) {
                return false;
            }

            sysidLevels = readSysids(seqToBbid);
            assert(sysidLevels.size() > 0);

            if (!LinuxCommand.mkdir(traceDir + "/" + simInstSysidPath(phase))){
                Logger.error("Cannot make temp sysid directory: " + traceDir + "/" + simInstSysidPath(phase));
                return false;
            }

            // transforms per-rank files to per-sysid, since cache trace data is stored per sysid
            writeSiminstPerSysid(phase, seqToBbid, sysidLevels);

            for (Iterator it = sysidLevels.keySet().iterator(); it.hasNext(); ){
                Integer sysid = (Integer)it.next();
                initializeSysid(sysid);
                if (!readSimInstFromSysid(traceDir + "/" + simInstSysidFile(phase, sysid), sysid, phase, seqToBbid, sysidLevels)){
                    return false;
                }
                closeSysid(sysid);
            }

            if (!LinuxCommand.rmdir(traceDir + "/" + simInstSysidPath(phase))){
                Logger.error("Cannot remove temp sysid directory: " + traceDir + "/" + simInstSysidPath(phase));
                return false;
            }

            boolean haveDfp = false;
            for( int rank = 0; rank < testCase.getCpu(); ++rank ) {
                if (readDfp(traceDir + "/" + dfpFile(phase, rank), rank, phase, seqToBbid)){
                    haveDfp = true;
                }
            }

            // Make processed_trace/pXX/dfp directory
            if (haveDfp){
                if( !Util.mkdir(this.processedDir + "/dfp") ) {
                    Logger.warn("Cannot make dfp directory" + this.processedDir + "/dfp");
                    return false;
                } 
            }
        }
        return this.commit();
    }

    // -- Methods to read trace files -- //

    boolean readJbbInstStatic(String fileName) {

        // Open file for reading
        if( fileName == null || !Util.isFile(fileName) ) {
            Logger.warn("Cannot find static file " + fileName);
            return false;
        }

        Logger.inform("Processing file " + fileName);

        boolean status = true;
        Set<BlockID> multiLoopHeads = Util.newHashSet();

        try {
            BufferedReader file = new BufferedReader(new FileReader(fileName));
            try {
                // Prepare regular expressions we will need
                Matcher m;
                Pattern bbLine = Pattern.compile("^\\d+.*");
                Pattern location = Pattern.compile("(.*):(-?\\d+)");
                Pattern dud = Pattern.compile("(\\d+):(\\d+):(\\d+)");
    
                Set<BasicBlock> blocks = Util.newHashSet();
    
                // Read lines
                int lineno = 0;
                BasicBlock b = null;
                for( String line = file.readLine(); line != null; line = file.readLine() ) {
                    ++lineno;
        
                    line = line.trim();
                    if( line.equals("") ) {
                        continue;
                    }
                    if( line.startsWith("#") ) {
                        continue;
                    }
                    String[] fields = line.split("\\s+");
        
                    // bb
                    m = bbLine.matcher(line);
                    if( m.matches() ) {
                        b = new BasicBlock();
                        b.bbid = new BlockID(0L, Long.parseLong(fields[1])); // imgid = 0
                        b.memOps = Integer.parseInt(fields[2]);
                        b.fpOps = Integer.parseInt(fields[3]);
                        b.insns = Integer.parseInt(fields[4]);

                        m = location.matcher(fields[5]);
                        if( !m.matches() ) {
                            Logger.warn("Unable to read location " + fields[5] + " at line " + lineno + " of " + fileName);
                            status = false;
                            break;
                        }

                        b.file = m.group(1);
                        b.line = Integer.parseInt(m.group(2));
                        if (b.line < 0){
                            Logger.warn("negative line number " + b.line + " found at line " + lineno + " of " + fileName);
                        }
        
                        b.functionName = fields[6];
                        b.functionID = b.bbid.functionID();

                        blocks.add(b);

                        continue;
                    }

                    if( b == null ) {
                        Logger.warn("No basic block line seen; can't interpret line " + lineno + ":" + fileName + ": " + line + "\n");
                        status = false;
                        break;
                    }

                    // lpi
                    if( line.startsWith("+lpi") ) {
                        b.loopCount = Integer.parseInt(fields[1]); // property of function
                        b.loopDepth = Integer.parseInt(fields[3]); // of loop
                        b.loopLoc = Integer.parseInt(fields[4]); // of block

                    // cnt
                    } else if( line.startsWith("+cnt") ) {
                        b.branchOps = Integer.parseInt(fields[1]);
                        b.intOps = Integer.parseInt(fields[2]);
                        b.logicOps = Integer.parseInt(fields[3]);
                        b.shiftRotateOps = Integer.parseInt(fields[4]);
                        b.trapSyscallOps = Integer.parseInt(fields[5]);
                        b.specialRegOps = Integer.parseInt(fields[6]);
                        b.otherOps = Integer.parseInt(fields[7]);
                        b.loadOps = Integer.parseInt(fields[8]);
                        b.storeOps = Integer.parseInt(fields[9]);
                        b.memOps = Integer.parseInt(fields[10]);

                    // mem
                    } else if( line.startsWith("+mem") ) {
                        b.memOps = Integer.parseInt(fields[1]);
                        b.memBytes = Integer.parseInt(fields[2]);

                    // lpc
                    } else if( line.startsWith("+lpc") ) {
                        b.loopHead = new BlockID(0L, Long.parseLong(fields[1]));
                        b.parentLoopHead = new BlockID(0L, Long.parseLong(fields[2]));

                        if (b.loopDepth > 1 && b.loopHead.equals(b.parentLoopHead) && !multiLoopHeads.contains(b.loopHead)){
                            Logger.warn("Block found that heads multiple loops (will result in unreliable stats): " + b.loopHead + " (" + b.functionName + " @ " + b.file + ":" + b.line + ")");
                            multiLoopHeads.add(b.loopHead);
                        }

                    // dud
                    } else if( line.startsWith("+dud") ) {
                        for( int i = 1; i < fields.length; ++i ) {
                            m = dud.matcher(fields[i]);
                            if( !m.matches() ) {
                                continue;
                            }
                            Integer dist = Integer.parseInt(m.group(1));
                            Integer intcnt = Integer.parseInt(m.group(2));
                            Integer fpcnt = Integer.parseInt(m.group(3));
                            b.duds.add(new Dud(dist, intcnt, fpcnt, 0));
                        }

                    // dxi
                    } else if( line.startsWith("+dxi") ) {
                        b.defUseCrossCount = Integer.parseInt(fields[1]);
                        b.callCount = Integer.parseInt(fields[2]);

                    // ipa
                    } else if( line.startsWith("+ipa") ) {
                        b.callTargetAddress = Long.decode(fields[1]);
                        b.callTargetName = fields[2];
                    }
                }

                if( !this.insertBlocks(blocks) ) {
                    return false;
                }

                Map<BlockID, Loop> loops = Util.newHashMap();
                Map<FunctionID, Function> functions = Util.newHashMap();
                for( Iterator<BasicBlock> bit = blocks.iterator(); bit.hasNext(); ) {
                    BasicBlock bb = bit.next();
                    if (bb.loopLoc == BasicBlock.LOOPLOC_HEAD){
                        Loop l = new Loop();
                        assert(bb.loopHead.equals(bb.bbid));
                        l.setHead(bb);

                        assert(!loops.containsKey(bb.bbid));
                        loops.put(bb.bbid, l);
                    }
                    if( !functions.containsKey(bb.functionID) ) {
                        Function f = new Function();
                        f.functionID = bb.functionID;
                        f.functionName = bb.functionName;
                        f.file = bb.file;
                        f.loopCount = bb.loopCount;

                        functions.put(bb.functionID, f);
                    } 
                }

                if( !this.insertLoops(loops.values()) ) {
                    return false;
                }

                if( !this.insertFunctions(functions.values()) ) {
                    return false;
                }

            } finally {
                file.close();
            }

        } catch (Exception e) {
            Logger.error(e, "Error reading file " + fileName);
            return false;
        }

        return status;
    }

    // Build a mapping from seqIDs to bbids from a siminst.static file
    Map<Integer, BlockID> readSimInstStatic(String fileName) {

        // Open file for reading
        if( fileName == null || !Util.isFile(fileName) ) {
            Logger.warn("Cannot find static file " + fileName);
            return null;
        }
        Logger.inform("Processing file " + fileName);

        Map<Integer, BlockID> seqToBbid = Util.newHashMap();
        try {
            BufferedReader file = new BufferedReader(new FileReader(fileName));
            try {
                Pattern bbLine = Pattern.compile("^\\d+.*");
    
                // Read lines
                for( String line = file.readLine(); line != null; line = file.readLine() ) {
                    line = line.trim();
                    if( !bbLine.matcher(line).matches() ) {
                        continue;
                    }

                    String[] fields = line.split("\\s+");
                    Integer seqID = Integer.parseInt(fields[0]);
                    BlockID bbid = new BlockID(0L, Long.parseLong(fields[1]));
                    seqToBbid.put(seqID, bbid);
                }

            } finally {
                file.close();
            }

        } catch (Exception e) {
            Logger.error(e, "Error reading file " + fileName);
            return null;
        }

        return seqToBbid;
    }

    // .jbbinst files contain visit counts for basicblocks per rank
    boolean readJbbInst(String fileName, int rank) {
        if( fileName == null || !Util.isFile(fileName) ) {
            Logger.warn("Cannot find jbbinst file " + fileName);
            return false;
        }

        Logger.inform("Processing file " + fileName);

        Map<BlockID, Long> bbidToCounts = Util.newHashMap();
        try {
            BufferedReader file = new BufferedReader(new FileReader(fileName));
            try {

                Pattern digits = Pattern.compile("\\d+");
                Matcher m;

                for( String line = file.readLine(); line != null; line = file.readLine() ) {
                    line = line.trim();
                    if( line.equals("") ) {
                        continue;
                    }
                    String[] fields = line.split("\\s+");

                    m = digits.matcher(fields[0]);
                    if( !m.matches() ) {
                        continue;
                    }

                    BlockID bbid = new BlockID(0L, Long.parseLong(fields[4]));
                    Long visitCount = Long.parseLong(fields[1]);

                    bbidToCounts.put(bbid, visitCount);
                    //this.insertBlockVisitCounts(bbid, rank, visitCount);
                }
            } finally {
                file.close();
            }
        } catch (Exception e) {
            Logger.error(e, "Error reading file " + fileName);
            return false;
        }
        this.insertBlockVisitCounts(rank, bbidToCounts);

        return true;
    }

    boolean readLoopCnt(String fileName, int rank) {
        if( fileName == null || !Util.isFile(fileName)) {
            Logger.warn("Cannot find loopcnt file " + fileName);
            return false;
        }

        Logger.inform("Processing file " + fileName);

        boolean status = true;
        try {
            BufferedReader file = new BufferedReader(new FileReader(fileName));
            try {

                Pattern digits = Pattern.compile("\\d+");
                Matcher m;

                Set<BlockID> heads = Util.newHashSet();
                int lineno = 0;
                for( String line = file.readLine(); line != null; line = file.readLine() ) {
                    lineno++;
                    line = line.trim();
                    if( line.equals("") ) {
                        continue;
                    }
                    String[] fields = line.split("\\s+");

                    m = digits.matcher(fields[0]);
                    if( !m.matches() ) {
                        continue;
                    }

                    // NOTE: if loopHead is conflicting as a foreign key in the trace DB, you may have
                    // one of the few versions of the tracer which uses the wrong type of id in the
                    // loopcnt file... consider using PEBIL/scripts/legacy_loopcnt_convert.sh to fix
                    BlockID loopHead = new BlockID(0L, Long.parseLong(fields[0]));
                    Long visitCount = Long.parseLong(fields[1]);
                    heads.add(loopHead);
                    this.insertLoopVisitCounts(loopHead, rank, visitCount);
                }
            } finally {
                file.close();
            }
        } catch (Exception e) {
            Logger.error(e, "Error reading loopcnt file " + fileName);
            return false;
        }
        return true;
    }

    // .siminst files contain cache simulation information
    Map<Integer, Integer> readSysids(Map<Integer, BlockID> seqToBbid){

        Map<Integer, Integer> sysidLevels = Util.newHashMap();

        try {
            for (int phase = 1; phase <= phaseCount; ++phase){
                for( int rank = 0; rank < testCase.getCpu(); ++rank ) {

                    String fileName = traceDir + "/" + simInstFile(phase, rank);
                    if( !Util.isFile(fileName) ) {
                        Logger.error("Cannot find siminst file " + fileName);
                        return null;
                    }

                    Logger.inform("Processing file for sysids " + fileName);
                    BufferedReader file = new BufferedReader(new FileReader(fileName));

                    try {
                        BlockID bbid = null;
                        for( String line = file.readLine(); line != null; line = file.readLine() ) {
                            line = line.trim();
                            String[] fields = line.split("\\s+");
                            
                            if( line.startsWith("sys") ) {
                                assert( bbid != null );
                                
                                Integer sysid = Integer.parseInt(fields[1]);
                                Integer level = Integer.parseInt(fields[3]);
                                
                                if (sysidLevels.get(sysid) == null || sysidLevels.get(sysid) < level){
                                    sysidLevels.put(sysid, level);
                                }
                                continue;
                            }
                            
                            if( line.startsWith("block") ) {
                                if (bbid != null){
                                    break;
                                }
                                Integer seqId = Integer.parseInt(fields[1]);
                                bbid = seqToBbid.get(seqId);
                                if( bbid == null ) {
                                    new Exception().printStackTrace();
                                    Logger.error("Unable to find bbid for sequence number " + seqId);
                                    return null;
                                }
                            }
                        }
                        
                    } finally {
                        file.close();
                    }
                }
            }
        } catch (Exception e) {
            Logger.error(e, "Error reading sysids from siminst files");
            return null;
        }

        this.insertSysids(sysidLevels);
        return sysidLevels;
    }

    boolean writeSiminstPerSysid (int phase, Map <Integer, BlockID> seqToBbid, Map<Integer, Integer> sysidLevels){
        Map<Integer, BufferedWriter> sysidFiles = Util.newHashMap();

        try {
            // rank -> <bbid, simVisit>
            Map<Integer, Map<BlockID, Long>> blockVisits = Util.newHashMap();

            for (Iterator it = sysidLevels.keySet().iterator(); it.hasNext(); ){
                Integer sysid = (Integer)it.next();
                sysidFiles.put(sysid, new BufferedWriter(new FileWriter(traceDir + "/" + simInstSysidFile(phase, sysid))));
            }

            for( int rank = 0; rank < testCase.getCpu(); ++rank ) {
                String metaFile = traceDir + "/" + simInstFile(phase, rank);
                BufferedReader file = new BufferedReader(new FileReader(metaFile));
                Logger.inform("reading file " + metaFile);

                Map<BlockID, Long> r = Util.newHashMap();
                blockVisits.put(rank, r);

                 // the innards of this loop MUST be kept as fast as possible
                BlockID bbid = null;
                for (String line = file.readLine(); line != null; line = file.readLine()){
                     line = line.trim();
                     String[] fields = line.split("\\s+");

                     if (line.startsWith("sys")){
                         int sysid = Integer.parseInt(fields[1]);
                         int level = Integer.parseInt(fields[3]);
                         long hitCount = Long.parseLong(fields[4]);
                         long missCount = Long.parseLong(fields[5]);
                         
                         BufferedWriter b = sysidFiles.get(sysid);
                         if (b != null){
                             b.write(bbid.blockHash + "\t" + rank + "\t" + sysid + "\t" + level + "\t" + hitCount + "\t" + missCount + "\n");
                         }
                     } else if (line.startsWith("block")){
                         Integer seqId = Integer.parseInt(fields[1]);
                         bbid = seqToBbid.get(seqId);
                         //assert(bbid != null);
                         Long simVisits = Long.parseLong(fields[2]);
                         r.put(bbid, simVisits);
                     }
                 }
                 file.close();
            }
            for (Iterator it = sysidFiles.keySet().iterator(); it.hasNext(); ){
                Integer sysid = (Integer)it.next();
                BufferedWriter w = sysidFiles.get(sysid);
                w.close();
            }
            
            insertSimBlockVisits(blockVisits);

        } catch (IOException e){
            Logger.error(e, "Unable to process sysid meta file");
            return false;
        }

        return true;
    }
    
    boolean readSimInstFromSysid(String fileName, int sysid, int phase, Map<Integer, BlockID> seqToBbid, Map<Integer, Integer> sysidLevels) {
        if( fileName == null || !Util.isFile(fileName) ) {
            Logger.warn("Cannot find siminst file " + fileName);
            return false;
        }

        Logger.inform("Processing file " + fileName + " for sysid " + sysid);

        try {
            BufferedReader file = new BufferedReader(new FileReader(fileName));
            try {

                // rank -> <bbid -> cache hit rates>
                Map<Integer, Map<BlockID, CacheStats>> blocks = Util.newHashMap();
                for( int rank = 0; rank < testCase.getCpu(); ++rank ) {
                    Map<BlockID, CacheStats> r = Util.newHashMap();
                    blocks.put(rank, r);
                }

                tracedbTimerBegin();                
                // the innards of this loop MUST be kept as fast as possible. this is a bottleneck for generating .sysid files
                for( String line = file.readLine(); line != null; line = file.readLine() ) {
                    line = line.trim();
                    String[] fields = line.split("\\s+");

                    BlockID bbid = new BlockID(0L, Long.parseLong(fields[0]));
                    Integer rank = Integer.parseInt(fields[1]);
                    //Integer bbsysid = Integer.parseInt(fields[2]);
                    //assert(bbsysid == sysid);
                    Integer level = Integer.parseInt(fields[3]);
                    Long hitCount = Long.parseLong(fields[4]);
                    Long missCount = Long.parseLong(fields[5]);

                    Map<BlockID, CacheStats> r = blocks.get(rank);
                    //assert(r != null);
                    CacheStats c = r.get(bbid);
                    if( c == null ) {
                        c = new CacheStats(sysidLevels.get(sysid));
                        r.put(bbid, c);
                    }
                    CacheLevel l = c.levels.elementAt(level-1);
                    l.hitCount += hitCount;
                    l.missCount += missCount;
                }
                tracedbTimerEnd("parsing siminst for sysid " + sysid);
                this.insertBlockCacheHitRates(sysid, blocks);

            } finally {
                file.close();
            }
        } catch (Exception e) {
            Logger.error(e, "Error reading siminst file " + fileName);
            return false;
        }
        return true;
    }

    // .dfp files contain address range information
    boolean readDfp(String fileName, int rank, int phase, Map<Integer, BlockID> seqToBbid) {
        if( fileName == null || !Util.isFile(fileName) ) {
            Logger.warn("Cannot find dfp file " + fileName);
            return false;
        }

        Logger.inform("Processing file " + fileName);

        try {
            BufferedReader file = new BufferedReader(new FileReader(fileName));
            try {

                BlockID bbid = null;
                AddressRanges addressRanges = null;

                // bbid -> address ranges
                Map<BlockID, AddressRanges> allRanges = Util.newHashMap();

                for( String line = file.readLine(); line != null; line = file.readLine() ) {
                    line = line.trim();
                    if( line.equals("") ) {
                        continue;
                    }
                    String[] fields = line.split("\\s+");

                    if( line.startsWith("block") ) {
                        if (fields.length != 4){
                            Logger.error("badly formatted line in " + fileName + ": " + line);
                            return false;
                        }
                        Integer seqId = Integer.parseInt(fields[1]);

                        bbid = seqToBbid.get(seqId);
                        if( bbid == null ) {
                            new Exception().printStackTrace();
                            Logger.error("Unable to find bbid for sequence number " + seqId);
                            return false;
                        }

                        if (!AddressRanges.isValidPattern(fields[2])){
                            Logger.error("invalid dfpattern found in " + fileName + ": " + fields[2]);
                            return false;
                        }
                        addressRanges = new AddressRanges(fields[2]);
                        allRanges.put(bbid, addressRanges);
                        continue;
                    }
                    if( line.startsWith("range") ) {
                        assert( bbid != null );
                        assert(addressRanges != null);
                        if (fields.length != 3){
                            Logger.error("badly formatted line in " + fileName + ": " + line);
                            return false;
                        }

                        String minh = fields[1].replaceFirst("0x", "");
                        String maxh = fields[2].replaceFirst("0x", "");
                        long min = Long.parseLong(minh, 16);
                        long max = Long.parseLong(maxh, 16);
                        
                        addressRanges.addRange(min, max);
                    }
                }
                this.insertAddressRanges(rank, allRanges);

            } finally {
                file.close();
            }
        } catch (Exception e) {
            Logger.error(e, "Error reading siminst file " + fileName);
            return false;
        }
        return true;
    }
}

