package PSaPP.data;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.math.BigInteger;

import PSaPP.util.*;
import PSaPP.dbase.*;

/*
 * Reads new trace format into new tracedb
 */
public class RawTraceProcessor extends TraceDB {

    final File scratchDir;
    final File traceDir;
    final File processedDir;

    Set<String> staticFiles = Util.newHashSet();
    Map<FunctionID, Function> functions = Util.newHashMap();

    /*
     * Constructors
     */
    public RawTraceProcessor(TestCase testCase, String scratchDir, String processedDir, String unused, String traceDir, Set<Integer> sysidList) {
        this.testCase = testCase;
        this.scratchDir = new File(scratchDir);
        this.traceDir = new File(traceDir);
        this.processedDir = new File(processedDir);
        this.validSysid = sysidList;

        initialize(sysidList);
    }

    private void initialize(Set<Integer> sysidList){
        super.destroy(processedDir);
        super.initialize(processedDir, testCase, sysidList);

        if( !this.process() ) {
            throw new java.lang.RuntimeException();
        }
    }

    static boolean allTracesExist(TestCase testCase, Database dataBase) {
        return true; // FIXME
    }

    File dfpFile(int rank){
        return new File(this.traceDir, testCase.getApplication() + String.format(".r%08d", rank) + String.format(".t%08d", testCase.getCpu()) + ".dfp");
    }

    File jbbInstFile(int rank) {
        return new File(this.traceDir, testCase.getApplication() + String.format(".r%08d", rank) + String.format(".t%08d", testCase.getCpu()) + ".jbbinst");
    }

    File jbbStatFile(String imgName) {
        return new File(this.traceDir, imgName + ".jbbinst.static");
    }

    File simInstFile(int rank) {
        return new File(this.traceDir, testCase.getApplication() + String.format(".r%08d", rank) + String.format(".t%08d", testCase.getCpu()) + ".siminst");
    }

    File simInstSysidPath(){
        return new File(this.scratchDir, "sysid");
    }

    File simInstSysidFile(int sysid) {
        return new File(simInstSysidPath(),
               testCase.getApplication() + "." + Format.cpuToString(testCase.getCpu()) +
               ".siminst.sysid" + sysid);
    }
    File reuseDistFile(int rank) {
        return new File(this.traceDir, testCase.getApplication() + String.format(".r%08d", rank) + String.format(".t%08d", testCase.getCpu()) + ".dist");
    }
    File spatialLocalityFile(int rank) {
        return new File(this.traceDir, testCase.getApplication() + String.format(".r%08d", rank) + String.format(".t%08d", testCase.getCpu()) + ".spatial");
    }

    boolean process() {
        Logger.inform("Preparing to process trace data");

        // Create scratch directory
        if(!this.scratchDir.exists() && !this.scratchDir.mkdir()) {
            Logger.warn("Unable to create scratch directory: " + this.scratchDir);
            return false;
        }

        // Make processed_trace directory
        if(!this.processedDir.exists() && !this.processedDir.mkdir()) {
            Logger.warn("Cannot make processed directory" + this.processedDir);
            return false;
        }

        // Setup tables
        if( !this.initializeTables() ) {
            return false;
        }

        // Read all static files present in trace directory
        String[] sfiles = traceDir.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".jbbinst.static");
            }
        });
        for(int i = 0; i < sfiles.length; ++i) {
            File f = new File(this.traceDir, sfiles[i]);
            String name = sfiles[i].substring(0, sfiles[i].length() - 15);
            readJbbInstStatic(f, null);
        }

        // Read .jbbinst files -- optional
        // Records basic block visit counts and loop counts
        for( int rank = 0; rank < testCase.getCpu(); ++rank ) {
            if( !readJbbInst(jbbInstFile(rank), rank) ) {
                return false;
            }
        }

        if(!insertFunctions(functions.values())){
            return false;
        }

        // Read .siminst files -- optional
        // Records basic block cache hit rates
        Map<Integer, Integer> sysidLevels = readSysids();
        if(sysidLevels != null && sysidLevels.size() > 0) {
            File tmpSysidDir = simInstSysidPath();
            if (!tmpSysidDir.exists() && !tmpSysidDir.mkdir()){
                Logger.error("Cannot make temp sysid directory: " + tmpSysidDir);
                return false;
            }

            // transforms per-rank files to per-sysid, since cache trace data is stored per sysid
            if(!writeSiminstPerSysid(sysidLevels)) {
                return false;
            }

            for (Iterator<Integer> it = sysidLevels.keySet().iterator(); it.hasNext(); ){
                Integer sysid = it.next();
                initializeSysid(sysid);
                if (!readSimInstFromSysid(simInstSysidFile(sysid), sysid, sysidLevels)){
                    return false;
                }
                closeSysid(sysid);
            }

            File[] sysfiles = tmpSysidDir.listFiles();
            for(int i = 0; i < sysfiles.length; ++i) {
                if(!sysfiles[i].delete()) {
                    Logger.warn("Cannot delete sysid tmp file " + sysfiles[i]);
                }
            }
            if(!tmpSysidDir.delete()) {
                Logger.warn("Cannot remove temp sysid directory: " + tmpSysidDir);
            }
        }

        // optionally process dist and spatial files
        for(int rank = 0; rank < testCase.getCpu(); ++rank) {
            File distFile = reuseDistFile(rank);
            if(distFile.exists()) {
                readReuseDistFile(distFile, rank);
            }
        }

        for(int rank = 0; rank < testCase.getCpu(); ++rank) {
            File spatialFile = spatialLocalityFile(rank);
            if(spatialFile.exists()) {
                readSpatialFile(spatialFile, rank);
            }
        }

/* FIXME
        boolean haveDfp = false;
        for( int rank = 0; rank < testCase.getCpu(); ++rank ) {
            if (readDfp(dfpFile(rank), rank, seqToBbid)){
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
*/
        return this.commit();

    }


    // Returns seqids -> BBID
    Map<Integer, BlockID> readSimInstStatic(File statFile) {
        assert(statFile != null);

        if(!statFile.exists()) {
            Logger.warn("Cannot find static file " + statFile);
            return null;
        }
        Logger.inform("Processing file " + statFile);

        Map<Integer, BlockID> seqToBbid = Util.newHashMap();
        try {
            BufferedReader file = new BufferedReader(new FileReader(statFile));
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
            Logger.error(e, "Error reading file " + statFile);
            return null;
        }

        return seqToBbid;
    }

    boolean readJbbInstStatic(File statFile, Long imgHash) {
        assert(statFile != null);
        if(!statFile.exists()) {
            Logger.warn("Cannot find jbb static file:" + statFile);
            return false;
        }

        if(staticFiles.contains(statFile.getName())) {
            return true;
        }
        staticFiles.add(statFile.getName());

        Logger.inform("Processing file " + statFile);

        boolean status = true;
        try {
            BufferedReader file = new BufferedReader(new FileReader(statFile));
            try {
                // Prepare regular expressions we will need
                Matcher m;
                Pattern bbLine = Pattern.compile("^\\d+.*");
                Pattern location = Pattern.compile("(.*):(-?\\d+)");
                Pattern dud = Pattern.compile("(\\d+):(\\d+):(\\d+):(\\d+)");
                Pattern vec = Pattern.compile("(\\d+)x(\\d+):(\\d+):(\\d+)");
                Pattern unknownVec = Pattern.compile("(\\?\\?\\?)x(\\d+):(\\d+):(\\d+)");
    
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
                    if( line.startsWith("# sha1sum") ) {
                        String[] fields = line.split("\\s+");
                        Long imgid = Util.toLong("0x" + fields[3].substring(0, 16));
                        if(imgHash == null) {
                            imgHash = imgid;
                        } else {
                            assert(imgHash.equals(imgid));
                        }
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
                        b.bbid = new BlockID(imgHash, Long.parseLong(fields[1]));
                        b.memOps = Integer.parseInt(fields[2]);
                        b.fpOps = Integer.parseInt(fields[3]);
                        b.insns = Integer.parseInt(fields[4]);

                        m = location.matcher(fields[5]);
                        if( !m.matches() ) {
                            Logger.warn("Unable to read location " + fields[5] + " at line " + lineno + " of " + statFile);
                            status = false;
                            break;
                        }

                        b.file = m.group(1);
                        b.line = Integer.parseInt(m.group(2));
                        if (b.line < 0){
                            Logger.warn("negative line number " + b.line + " found at line " + lineno + " of " + statFile);
                        }
        
                        b.functionName = fields[6];
                        b.functionID = b.bbid.functionID();

                        // hashcode = fields[7]

                        b.vaddr = Util.toLong(fields[9]);

                        blocks.add(b);

                        continue;
                    }

                    if( b == null ) {
                        Logger.warn("No basic block line seen; can't interpret line " + lineno + ":" + statFile + ": " + line + "\n");
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
                        if(!fields[11].equals("#")) {
                          b.scatterGatherOps = Integer.parseInt(fields[11]);
                          b.vectorMaskOps = Integer.parseInt(fields[12]);
                        }

                    // mem
                    } else if( line.startsWith("+mem") ) {
                        b.memOps = Integer.parseInt(fields[1]);
                        b.memBytes = Integer.parseInt(fields[2]);

                    // lpc
                    } else if( line.startsWith("+lpc") ) {
                        b.loopHead = new BlockID(imgHash, Long.parseLong(fields[1]));
                        b.parentLoopHead = new BlockID(imgHash, Long.parseLong(fields[2]));

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
                            Integer memcnt = Integer.parseInt(m.group(4));
                            b.duds.add(new Dud(dist, intcnt, fpcnt, memcnt));
                        }

                    // dxi
                    } else if( line.startsWith("+dxi") ) {
                        b.defUseCrossCount = Integer.parseInt(fields[1]);
                        b.callCount = Integer.parseInt(fields[2]);

                    // ipa
                    } else if( line.startsWith("+ipa") ) {
                        b.callTargetAddress = Long.decode(fields[1]);
                        b.callTargetName = fields[2];

                    // vec
                    } else if( line.startsWith("+vec") ) {
                        for( int i = 1; i < fields.length; ++i ) {
                            m = vec.matcher(fields[i]);
                            if( !m.matches() ) {
                                m = unknownVec.matcher(fields[i]);
                                if(!m.matches()) {
                                    continue;
                                }
                                Integer elmSiz = Integer.parseInt(m.group(2));
                                Integer fpcnt = Integer.parseInt(m.group(3));
                                Integer intcnt = Integer.parseInt(m.group(4));
                                b.vecs.add(new VecOps(VecOps.UNKNOWN, elmSiz, fpcnt, intcnt));
                                continue;
                            }
                            Integer vecLen = Integer.parseInt(m.group(1));
                            Integer elmSiz = Integer.parseInt(m.group(2));
                            Integer fpcnt = Integer.parseInt(m.group(3));
                            Integer intcnt = Integer.parseInt(m.group(4));

                            b.vecs.add(new VecOps(vecLen, elmSiz, fpcnt, intcnt));
                        }
                    }
                    
                }

                if( !this.insertBlocks(blocks) ) {
                    return false;
                }

                Map<BlockID, Loop> loops = Util.newHashMap();
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

            } finally {
                file.close();
            }

        } catch (Exception e) {
            Logger.error(e, "Error reading file " + statFile);
            return false;
        }

        return status;

    }

    Map<Integer, Integer> readSysids(){
        
        Map<Integer, Integer> sysidLevels = Util.newHashMap();

        try {
            for( int rank = 0; rank < testCase.getCpu(); ++rank ) {

                File simFile = simInstFile(rank);
                if(!simFile.exists()) {
                    Logger.warn("Cannot find siminst file " + simFile);
                    return null;
                }

                Logger.inform("Processing file for sysids " + simFile);
                BufferedReader file = new BufferedReader(new FileReader(simFile));

                try {
                    int START = 0;
                    int READING_SYSIDS = 1;
                    int readState = START;

                    for( String line = file.readLine(); line != null; line = file.readLine() ) {
   
                        line = line.trim();
                        if(line.startsWith("#") || line.equals("") || line.startsWith("IMG")) {
                            continue;
                        }

                        if(line.startsWith("BLK")) {
                            readState = READING_SYSIDS;
                            continue;
                        }

                        if(readState != READING_SYSIDS) {
                            Logger.warn("Skipping unrecognized line in file " + file + ": " + line);
                            continue;
                        }

                        String[] fields = line.split("\\s+");

                        Integer sysid = Integer.parseInt(fields[0]);
                        Integer level = Integer.parseInt(fields[1]);
                        
                        if (sysidLevels.get(sysid) == null || sysidLevels.get(sysid) < level){
                            sysidLevels.put(sysid, level);
                        }
                    }
                   
                } finally {
                    file.close();
                }
            }
        } catch (Exception e) {
            Logger.error(e, "Error reading sysids from siminst files");
            return null;
        }

        this.insertSysids(sysidLevels);
        return sysidLevels;
    }

    boolean readSimInstFromSysid(File fileName, int sysid, Map<Integer, Integer> sysidLevels) {
        if(!fileName.exists()) {
            Logger.warn("Cannot find siminst file " + fileName);
            return false;
        }

        Logger.inform("Processing file " + fileName + " for sysid " + sysid);

        try {
            BufferedReader file = new BufferedReader(new FileReader(fileName));
            try {

                // rank -> thread -> bbid -> cache hit rates
                Map<Integer, Map<Integer, Map<BlockID, CacheStats>>> rankBlocks = Util.newHashMap();
                for( int rank = 0; rank < testCase.getCpu(); ++rank ) {
                    Map<Integer, Map<BlockID, CacheStats>> threadBlocks = Util.newHashMap();
                    rankBlocks.put(rank, threadBlocks);
                }

                tracedbTimerBegin();                
                // the innards of this loop MUST be kept as fast as possible. this is a bottleneck for generating .sysid files
                for( String line = file.readLine(); line != null; line = file.readLine() ) {
                    line = line.trim();
                    String[] fields = line.split("\\s+");

                    Long blockHash = Long.parseLong(fields[0]);
                    Long imgHash = Long.parseLong(fields[1]);
                    BlockID bbid = new BlockID(imgHash, blockHash);
                    Integer rank = Integer.parseInt(fields[2]);
                    Integer thread = Integer.parseInt(fields[3]);
                    Integer level = Integer.parseInt(fields[4]);
                    Long hitCount = Long.parseLong(fields[5]);
                    Long missCount = Long.parseLong(fields[6]);
                    if( hitCount < 0L || missCount < 0L ) {
                        Logger.warn("Found negative hit/miss count in file " + fileName + " for block " + bbid);
                    }
                    Map<Integer, Map<BlockID, CacheStats>> threadBlocks = rankBlocks.get(rank);
                    Map<BlockID, CacheStats> blocks = threadBlocks.get(thread);
                    if(blocks == null){
                        blocks = Util.newHashMap();
                        threadBlocks.put(thread, blocks);
                    }

                    CacheStats c = blocks.get(bbid);
                    if( c == null ) {
                        c = new CacheStats(sysidLevels.get(sysid));
                        blocks.put(bbid, c);
                    }
                    CacheLevel l = c.levels.elementAt(level-1);
                    l.hitCount += hitCount;
                    l.missCount += missCount;
                }
                tracedbTimerEnd("parsing siminst for sysid " + sysid);
                this.insertBlockCacheHitRatesPerThread(sysid, rankBlocks);

            } finally {
                file.close();
            }
        } catch (Exception e) {
            Logger.error(e, "Error reading siminst file " + fileName);
            return false;
        }
        return true;
    }


    boolean writeSiminstPerSysid (Map<Integer, Integer> sysidLevels){
        Map<Integer, BufferedWriter> sysidFiles = Util.newHashMap();

        try {
            // rank -> thread -> bbid -> simVisit
            Map<Integer, Map<Integer, Map<BlockID, Long>>> ranksToVisits = Util.newHashMap();

            for (Iterator<Integer> it = sysidLevels.keySet().iterator(); it.hasNext(); ){
                Integer sysid = it.next();
                sysidFiles.put(sysid, new BufferedWriter(new FileWriter(simInstSysidFile(sysid))));
            }

            // multiplex data from each rank file to appropriate sysid file
            for( int rank = 0; rank < testCase.getCpu(); ++rank ) {
                File simFile = simInstFile(rank);
                BufferedReader file = new BufferedReader(new FileReader(simFile));
                Logger.inform("reading file " + simFile);

                Map<Integer, Map<BlockID, Long>> threadsToVisits = Util.newHashMap();
                ranksToVisits.put(rank, threadsToVisits);

                Map<Integer, Long> images = Util.newHashMap();

                // the innards of this loop MUST be kept as fast as possible
                Integer thread = null;
                Long imgHash = null;
                Long blockHash = null;
                for(String line = file.readLine(); line != null; line = file.readLine()) {
                    line = line.trim();
                    if(line.startsWith("#") || line.equals("")) {
                        continue;
                    }

                    if(line.startsWith("IMG")) {
                        String[] fields = line.split("\\s+");
                        imgHash = Util.toLong(fields[1]);
                        Integer imgSeq = Integer.parseInt(fields[2]);
                        images.put(imgSeq, imgHash);
                        continue;
                    }

                    if(line.startsWith("BLK")) {
                        String[] fields = line.split("\\s+");
                        //Integer seqId = Integer.parseInt(fields[1]);
                        blockHash = Util.toLong(fields[2]);
                        Integer imgSeq = Integer.parseInt(fields[3]);
                        imgHash = images.get(imgSeq);
                        thread = Integer.parseInt(fields[4]);
                        BlockID bbid = new BlockID(imgHash, blockHash);

                        if(fields.length > 5) {
                            Map<BlockID, Long> visits = threadsToVisits.get(thread);
                            if(visits == null) {
                                visits = Util.newHashMap();
                                threadsToVisits.put(thread, visits);
                            }
                            Long simVisits = Long.parseLong(fields[5]);
                            Long minAddr = Util.toLong(fields[7]);
                            Long maxAddr = Util.toLong(fields[8]);

                            visits.put(bbid, simVisits);

                            AddressRanges ranges = new AddressRanges();
                            ranges.addRange(minAddr, maxAddr);
                            insertAddressRanges(rank, thread, bbid, ranges);
                        }
                        continue;
                    }

                    String[] fields = line.split("\\s+");
                    Integer sysid = Integer.parseInt(fields[0]);
                    Integer level = Integer.parseInt(fields[1]);
                    Long hitCount = Long.parseLong(fields[2]);
                    Long missCount = Long.parseLong(fields[3]);

                   BufferedWriter b = sysidFiles.get(sysid);
                   if(b != null) {
                       b.write(blockHash + "\t" + imgHash + "\t" + rank + "\t" + thread + "\t" + level + "\t" + hitCount + "\t" + missCount + "\n");
                   }
                }
                file.close();
            }
            for (Iterator it = sysidFiles.keySet().iterator(); it.hasNext(); ){
                Integer sysid = (Integer)it.next();
                BufferedWriter w = sysidFiles.get(sysid);
                w.close();
            }
            
            insertSimBlockVisitsPerThread(ranksToVisits);

        } catch (IOException e){
            Logger.error(e, "Unable to process sysid meta file");
            return false;
        }

        return true;
    }


    boolean readJbbInst(File jbbfile, int rank) {
        // check for file's existence
        if(jbbfile == null || !jbbfile.exists()) {
            Logger.warn("Cannot find jbbinst file " + jbbfile);
            return true;
        }

        Map<BlockID, Map<Integer, Long>> blockVisitCounts = Util.newHashMap();
        Map<BlockID, Map<Integer, Long>> loopVisitCounts = Util.newHashMap();
        Map<Integer, Long> imgSeqToHash = Util.newHashMap();
        Map<Integer, Long> threadVisits = null;
        try {
            BufferedReader file = new BufferedReader(new FileReader(jbbfile));
            try {

                int NONE = 0;
                int BLOCK = 1;
                int LOOP = 2;
                int reading = NONE;

                Pattern locPat = Pattern.compile("(.*):(-?\\d+)");

                for(String line = file.readLine(); line != null; line = file.readLine()) {
                    line = line.trim();
                    if(line.startsWith("#") || line.equals("")) {
                        continue;
                    }
                    String[] fields = line.split("\\s+");

                    if(line.startsWith("IMG")) {
                        Long imgHash = Util.toLong(fields[1]);
                        Integer imgSeq = Integer.parseInt(fields[2]);
                        //String imgType = fields[3];
                        String name = fields[4];
                        //Integer blkCnt = Integer.parseInt(fields[5]);
                        //Integer lpCnt = Integer.parseInt(fields[6]);
                        readJbbInstStatic(jbbStatFile(name), imgHash);
                        imgSeqToHash.put(imgSeq, imgHash);
                        reading = NONE;
                    }

                    else if(line.startsWith("BLK")) {
                        Integer seq = Integer.parseInt(fields[1]);
                        Long blockHash = Util.toLong(fields[2]);
                        Integer imgSeq = Integer.parseInt(fields[3]);
                        //Long visits = Long.parseLong(fields[4]);
                        // 5 = #
                        //Matcher m = locPat.matcher(fields[6]);
                        //String fileName = m.group(1);
                        //Integer lineNo = Integer.parseInt(m.group(2));
                        //String func = fields[7];
                        //String addr = fields[8];
                        Long imgHash = imgSeqToHash.get(imgSeq);
                        BlockID bbid = new BlockID(imgHash, blockHash);
                        threadVisits = Util.newHashMap();
                        blockVisitCounts.put(bbid, threadVisits);
                        reading = BLOCK;
                    }

                    else if(line.startsWith("LPP")) {
                        Long loopHash = Util.toLong(fields[1]);
                        Integer imgSeq = Integer.parseInt(fields[2]);
                        Long visits = Long.parseLong(fields[3]);
                        // 4 = #
                        //Matcher m = locPat.matcher(fields[5]);
                        //String fileName = m.group(1);
                        //Integer lineNo = Integer.parseInt(m.group(2));
                        //String func = fields[6];
                        //String addr = fields[7];
                        Long imgHash = imgSeqToHash.get(imgSeq);
                        BlockID loopHead = new BlockID(imgHash, loopHash);
                        threadVisits = Util.newHashMap();
                        loopVisitCounts.put(loopHead, threadVisits);
                        reading = LOOP;
                    }

                    else if(reading == BLOCK || reading == LOOP) {
                        Integer thread = Integer.parseInt(fields[0]);
                        Long visits;
                        try {
                            visits = Long.parseLong(fields[1]);
                        } catch (NumberFormatException e) {
                            Logger.warn("Unable to parse visit count " + fields[1]);
                            visits = 0L;
                        }
                        if(threadVisits == null) {
                            Logger.error("Error reading file " + file);
                            return false;
                        }
                        threadVisits.put(thread, visits);
                    }

                    else {
                        Logger.error("Misplaced line: " + line);
                        return false;
                    }
                }
            } finally {
                file.close();
            }
        } catch (Exception e) {
            Logger.error(e, "Error reading file " + jbbfile);
            return false;
        }

        insertBlockCountsPerThread(rank, blockVisitCounts);
        insertLoopVisitCounts(rank, loopVisitCounts);
        
        return true;    
    }

    // FIXME
    // .dfp files contain address range information
    /*
    boolean readDfp(File fileName, int rank, Map<Integer, BlockID> seqToBbid) {
        if(!fileName.exists()) {
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
    */

    boolean readReuseDistFile(File distFile, int rank) {
        return readDistFile(distFile, rank, "REUSESTATS", "BB", "ReuseStats");
    }

    boolean readSpatialFile(File spatialFile, int rank) {
        return readDistFile(spatialFile, rank, "SPATIALSTATS", "SPATIALID", "SpatialStats");
    }

    boolean readDistFile(File distFile, int rank, String STATS_TOK, String ID_TOK, String table) {
        if(!distFile.exists()) {
            Logger.warn("Cannot find resuse dist file " + distFile);
            return false;
        }
   
        Logger.inform("Processing file " + distFile);
        try {
            BufferedReader file = new BufferedReader(new FileReader(distFile));
            try {

                Long imgHash = null;
                Integer thread = null;
                Map<Long, List<Tuple.Tuple3<Long, Long, Long>>> blocks = null;

                Long blockHash = null;
                List<Tuple.Tuple3<Long, Long, Long>> bins = null;

                for(String line = file.readLine(); line != null; line = file.readLine()) {
                    line = line.trim();
                    String[] fields = line.split("\\s+");

                    // IMAGE img THREAD thread
                    if(fields[0].startsWith("IMAGE")) {
                        if(blocks != null) {
                            insertDistances(table, rank, imgHash, thread, blocks);
                        }
                        blocks = Util.newHashMap();
                        imgHash = Util.toLong(fields[1]);
                        thread = Integer.parseInt(fields[3]);


                    // REUSESTATS window_size bin_start max_value num_ids accesses misses
                    } else if(fields[0].startsWith(STATS_TOK)) {
                        Integer windowSize = Integer.parseInt(fields[1]);
                        Integer binStart = Integer.parseInt(fields[2]);
                        Integer maxValue = Integer.parseInt(fields[3]);
                        

                    // REUSEID id accesses misses
                    } else if(fields[0].startsWith(ID_TOK)) {
                        blockHash = Long.parseLong(fields[1]);
                        bins = blocks.get(blockHash);
                        if(bins == null) {
                            bins = Util.newLinkedList();
                            blocks.put(blockHash, bins);
                        }

                    // lower_bound upper_bound accesses
                    } else {
                        Long lowerBound = Long.parseLong(fields[0]);
                        Long upperBound = Long.parseLong(fields[1]);
                        Long accesses = Long.parseLong(fields[2]);
                        Tuple.Tuple3<Long, Long, Long> bin = Tuple.newTuple3(lowerBound, upperBound, accesses);
                        bins.add(bin);
                    }
                }
                if(blocks != null) {
                    insertDistances(table, rank, imgHash, thread, blocks);
                }

            } finally {
                file.close();
            }
        } catch (Exception e) {
            Logger.error(e, "Error reading file " + distFile);
            return false;
        }

        return true;
    }
}

