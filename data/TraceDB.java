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

/*
  BasicBlocks:

    bbid
    insns
    intOps
    branchOps
    logicOps
    shiftRotateOps
    trapSyscallOps
    memOps
    loadOps
    storeOps
    fpOps
    specialRegOps
    otherOps
    memBytes
    defUseCrossCount
    callCount
    file
    function
    line
    loopHead
    loopLoc

  Duds:
    bbid
    dist
    fpcnt
    intcnt
    memcnt

  VectorOps:
    bbid
    vectorLength
    elementSize
    fpCnt
    intCnt

  IPAs:
    callTargetAddress
    callTargetName

  BlockVisitCounts:
    bbid
    rank
    jbbVisitcount
    simVisitCount

  Functions:
    fid
    imgHash
    name
    file
    loopCount

  Loops:
    loopHead
    parentLoopHead
    depth

  LoopVisitCounts:
    loopHead
    rank
    loopVisitCount

  Sysids:
    sysid
    levels

  // EVERY SYSID GETS ITS OWN DATABASE!
  BlockCacheHitRates:
    bbid 
    rank 
    L1Hits 
    L1Misses 
    L2Hits 
    L3Hits
*/

import PSaPP.util.*;
import PSaPP.dbase.*;
import PSaPP.pred.*;

import java.util.*;
import java.io.*;
import java.util.regex.*;
import java.sql.*;

/**
 * An interface to processed trace data.
 *
 */
public class TraceDB {
    private Connection dbConnection;
    private Map<Integer, Connection> sysidConnections;
    private Set<BlockID> blockids;
    private Properties properties;
    private String dbDir;
    protected TestCase testCase;
    protected Set<Integer> validSysid = null;

    static {
        try {
            Class.forName("org.hsqldb.jdbcDriver");
        } catch (ClassNotFoundException e){
            Logger.error(e, "Cannot load hsqldb class");
        }
    }

    public static int INVALID_RANK = -1;
    public static int NO_SYSID = Integer.MAX_VALUE;
    public static int ALL_SYSIDS = -1;

    private static final boolean timeDatabaseOps = true;
    private static double dbtimerbegin;
    private static double dbtimerend;
    public static final int INTERNAL_VARCHAR_LENGTH = 1024;

    public boolean tracedbTimerBegin(){
        if (timeDatabaseOps){
            dbtimerbegin = System.currentTimeMillis();
        }
        return timeDatabaseOps;
    }

    public boolean tracedbTimerEnd(String desc){
        if (timeDatabaseOps){
            dbtimerend = System.currentTimeMillis();
            Logger.inform("database operation timer for " + desc + ": " + (dbtimerend - dbtimerbegin));
        }
        return timeDatabaseOps;
    }

    private String sysidDBName(int sysid){
        return dbDir + testCase.shortName() + "_mem_sysid" + sysid;
    }

    private void setValidSysids(Set<Integer> sysidList){
        validSysid = sysidList;

        // if not given on command line, read from config file
        if (validSysid != null){
           return;
        }

        // config file doesn't have it either
        if (!ConfigSettings.hasSetting("SYSID_PROCESS")){
            return;
        }

        // config file has it
        validSysid = Util.newHashSet();
        String s = ConfigSettings.getSetting("SYSID_PROCESS");
        Logger.inform("TraceDB sysid list from config file: " + s);
        Integer[] p = Util.machineList(s);
        for (Integer i : p){
            validSysid.add(i);
        }
    }

    public TraceDB(){
    }

    public TraceDB(String processedDir, TestCase tcase, Set<Integer> sysidList) {
        initialize(processedDir, tcase, sysidList);
    }

    public static long getVisitCount(long jbb, long sim){
        if (jbb == 0L){
            return sim;
        }
        return jbb;
    }

    public void initialize(File processedDir, TestCase tcase, Set<Integer> sysidList) {
        initialize(processedDir.getAbsolutePath(), tcase, sysidList);
    }

    public void initialize(String processedDir, TestCase tcase, Set<Integer> sysidList){
        setValidSysids(sysidList);

        testCase = tcase;
        dbDir = processedDir + "/db/";

        sysidConnections = Util.newHashMap();


        properties = new Properties();
        properties.setProperty("check_props", "true"); // check the validity of connection properties
        //properties.setProperty("hsqldb.tx", "mvcc"); // set the transaction control mode to mvcc
        //properties.setProperty("hsqldb.cache_file_scale", "32"); // allows for .data file to grow larger -- (2N)GB -- see http://hsqldb.org/doc/2.0/guide/dbproperties-chapt.html#N151FA
        properties.setProperty("hsqldb.cache_rows", "1000000"); // store this many CACHED rows in memory
        properties.setProperty("hsqldb.cache_size", "1000000"); // cache this many KBs of CACHED tables
        properties.setProperty("hsqldb.inc_backup", "false"); // don't do incremental backup of DB
        properties.setProperty("hsqldb.lock_file", "false"); // don't use a lock file (for speed)
        properties.setProperty("hsqldb.log_data", "false"); // don't log db ops
        properties.setProperty("hsqldb.log_size", "0"); // same as log_data=false?

        if (!open()){
            throw new java.lang.IllegalArgumentException("Unable to connect to database in " + processedDir);
        }
    }

    public boolean destroy(File pd) {
        return destroy(pd.getAbsolutePath());
    }

    public boolean destroy(String pd){
        return LinuxCommand.rmdir(pd + "/db/");
    }

    private Connection openTracedb(String dir, Properties p){
        Connection c = null;
        try {
            tracedbTimerBegin();
            c = DriverManager.getConnection("jdbc:hsqldb:file:" + dir, p);
            tracedbTimerEnd("open database connection " + dir);
        } catch (Exception e) {
            return null;
        }
        assert(c != null);
        return c;
    }

    public boolean open(){
        String prefix = dbDir + testCase.shortName();
        Logger.inform("Opening trace database at " + prefix);

        dbConnection = openTracedb(prefix, properties);
        if (dbConnection == null){
            return false;
        }
        return true;
    }

    private boolean commitTracedb(Connection c){
        try {
            assert(!c.isClosed());
            Statement stmt = c.createStatement();
            tracedbTimerBegin();
            stmt.execute("CHECKPOINT;");
            tracedbTimerEnd("checkpoint");
            stmt.close();
        } catch (SQLException e) {
            Logger.error(e, "Unable to checkpoint trace database");
            return false;
        }        
        return true;
    }

    public boolean commit() {
        if (!commitTracedb(dbConnection)){
            return false;
        }
        for (Iterator it = sysidConnections.keySet().iterator(); it.hasNext(); ){
            Integer sysid = (Integer)it.next();
            if (!commitTracedb(sysidConnections.get(sysid))){
                return false;
            }
        }
        return true;
    }

    /**
     * Closes the database connection.
     */
    private void closeTracedb(Connection c){
        try {
            if (c.isClosed()){
                return;
            }
            Statement stmt = c.createStatement();
            stmt.execute("SHUTDOWN;");
            stmt.close();
            c.close();
        } catch (SQLException e) {
            Logger.error(e, "Unable to shutdown trace database");
        }
    }

    public void close() {
        closeTracedb(dbConnection);
        for (Iterator it = sysidConnections.keySet().iterator(); it.hasNext(); ){
            Integer sysid = (Integer)it.next();
            closeTracedb(sysidConnections.get(sysid));
            sysidConnections.remove(sysid);
        }
    }

    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    boolean initializeTables() {

        String sql;
        Statement stmt;
        try {
            stmt = dbConnection.createStatement();
        } catch (SQLException e) {
            Logger.error(e, "Unable to create statement");
            return false;
        }

        sql = "CREATE CACHED TABLE BasicBlocks (" +
              "bbid BIGINT" +
              ", imgHash BIGINT" +
              ", insns INTEGER" +
              ", intOps INTEGER" +
              ", branchOps INTEGER" +
              ", logicOps INTEGER" +
              ", shiftRotateOps INTEGER" +
              ", trapSyscallOps INTEGER" +
              ", memOps INTEGER" +
              ", loadOps INTEGER" +
              ", storeOps INTEGER" +
              ", fpOps INTEGER" +
              ", specialRegOps INTEGER" +
              ", otherOps INTEGER" +
              ", memBytes INTEGER" +
              ", defUseCrossCount INTEGER" +
              ", callCount INTEGER" +
              ", file VARCHAR(" + INTERNAL_VARCHAR_LENGTH + ")" +
              ", function BIGINT" +
              ", line INTEGER" +
              ", loopHead BIGINT" +
              ", loopLoc INTEGER" +
              ", callTargetAddress INTEGER" + 
              ", callTargetName VARCHAR(" + INTERNAL_VARCHAR_LENGTH + ")" +
              ", vaddr BIGINT" +
              ", scatterGatherOps INTEGER" +
              ", vectorMaskOps INTEGER" + 
              ", CONSTRAINT pk_basicblocks PRIMARY KEY (bbid, imgHash)" +
              ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create basic block table in trace database");
            return false;
        }

        sql = "CREATE CACHED TABLE Duds (" +
              "bbid BIGINT" +
              ", imgHash BIGINT" +
              ", dist INTEGER" +
              ", fpcnt INTEGER" +
              ", intcnt INTEGER " +
              ", memcnt INTEGER " +
              ", CONSTRAINT dudsToBasicBlocks FOREIGN KEY (bbid, imgHash) REFERENCES BasicBlocks(bbid, imgHash)" +
              ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create Duds table in trace database");
            return false;
        }

        sql = "CREATE CACHED TABLE VectorOps (" +
              "bbid BIGINT" +
              ", imgHash BIGINT" +
              ", vectorLength INTEGER" +
              ", elementSize INTEGER" +
              ", fpcnt INTEGER " +
              ", intcnt INTEGER " +
              ", CONSTRAINT vecsToBasicBlocks FOREIGN KEY (bbid, imgHash) REFERENCES BasicBlocks(bbid, imgHash)" +
              ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create VectorOps table in trace database");
            return false;
        }

        sql = "CREATE CACHED TABLE BlockVisitCounts (" +
              "bbid BIGINT" +
              ", imgHash BIGINT" +
              ", rank INTEGER" +
              ", thread INTEGER" +
              ", jbbVisitCount BIGINT " +
              ", simVisitCount BIGINT " +
              ", CONSTRAINT visitCountsToBasicBlocks FOREIGN KEY (bbid, imgHash) REFERENCES BasicBlocks(bbid, imgHash)" +
              ", UNIQUE (bbid, imgHash, rank, thread)" +
              ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create BlockVisitCounts table in trace database");
            return false;
        }

        sql = "CREATE MEMORY TABLE Sysids (" +
              "sysid INTEGER PRIMARY KEY" +
              ", levels INTEGER" +
              ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create Sysids table in trace database");
            return false;
        }

        sql = "CREATE CACHED TABLE BlockDfpDescriptors (" + // dfp entry
            "bbid BIGINT" +
            ", imgHash BIGINT" +
            ", rank INTEGER" +
            ", thread INTEGER" +
            ", pattern VARCHAR(" + INTERNAL_VARCHAR_LENGTH + ")" + // gather, scatter, transpose, etc.
            ", CONSTRAINT BlockDfpDescriptorsToBasicBlocks FOREIGN KEY (bbid, imgHash) REFERENCES BasicBlocks(bbid, imgHash)" + 
            ", UNIQUE (bbid, imgHash, rank, thread)" +
            ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create BlockDfpDescriptors table in trace database");
            return false;
        }

        sql = "CREATE CACHED TABLE AddressRanges (" + // individual address range (for dfp)
            "bbid BIGINT" +
            ", imgHash BIGINT" +
            ", rank INTEGER" +
            ", thread INTEGER" +
            ", minAddress BIGINT" +
            ", maxAddress BIGINT" +
            //", CONSTRAINT AddressRangesToBlockDfpDescriptors FOREIGN KEY (bbid, imgHash, rank, thread) REFERENCES BlockDfpDescriptors(bbid, imgHash, rank, thread)" +
            ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create AddressRanges table in trace database");
            return false;
        }

        sql = "CREATE CACHED TABLE Loops (" +
              "loopHead BIGINT, " + // no foreign keys here because blocks might not be added yet
              "imgHash BIGINT, " +
              "parentLoopHead BIGINT, " +
              "depth INTEGER, " +
              "CONSTRAINT pk_loops PRIMARY KEY (loopHead, imgHash)" +
              ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create Loops table in trace database");
            return false;
        }

        sql = "CREATE CACHED TABLE LoopVisitCounts (" +
              "loopHead BIGINT" +
              ", imgHash BIGINT" +
              ", rank INTEGER" +
              ", thread INTEGER" +
              ", loopVisitCount BIGINT" +
              ", CONSTRAINT visitCountsToLoops FOREIGN KEY (loopHead, imgHash) REFERENCES Loops(loopHead, imgHash)" +
            //", UNIQUE (loopHead, rank)" +
              ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create LoopVisitCounts table in trace database");
            return false;
        }

        sql = "CREATE CACHED TABLE Functions (" +
              "fid BIGINT, " +
              "imgHash BIGINT, " +
              "name VARCHAR(" + INTERNAL_VARCHAR_LENGTH + "), " +
              "file VARCHAR(" + INTERNAL_VARCHAR_LENGTH + "), " +
              "loopCount INTEGER, " +
              "CONSTRAINT pk_functions PRIMARY KEY (fid, imgHash)" +
              ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create Functions table in trace database");
            return false;
        }

        if(!initializeReuseStats() || !initializeSpatialStats()) {
            return false;
        }

        return true;
    }

    public boolean initializeReuseStats() {
        String sql;
        Statement stmt;
        try {
            stmt = dbConnection.createStatement();
        } catch (SQLException e) {
            Logger.error(e, "Unable to create statement");
            return false;
        }
        sql = "CREATE CACHED TABLE ReuseStats (" +
              "bbid BIGINT, " +
              "imgHash BIGINT, " +
              "rank INTEGER, " +
              "thread INTEGER, " +
              "lowerBound INTEGER, " +
              "upperBound INTEGER, " +
              "accesses BIGINT, " +
              "CONSTRAINT ReuseToBasicBlocks FOREIGN KEY (bbid, imgHash) REFERENCES BasicBlocks(bbid, imgHash)" +
              ");";

        try {
            stmt.execute(sql);
        } catch(SQLException e) {
            Logger.error(e, "Unable to create ReuseStats table in trace database");
            return false;
        }
        return true;
    }

    public boolean initializeSpatialStats() {
        String sql;
        Statement stmt;
        try {
            stmt = dbConnection.createStatement();
        } catch (SQLException e) {
            Logger.error(e, "Unable to create statement");
            return false;
        }
        sql = "CREATE CACHED TABLE SpatialStats (" +
              "bbid BIGINT, " +
              "imgHash BIGINT, " +
              "rank INTEGER, " +
              "thread INTEGER, " +
              "lowerBound BIGINT, " +
              "upperBound BIGINT, " +
              "accesses BIGINT, " +
              "CONSTRAINT SpatialToBasicBlocks FOREIGN KEY (bbid, imgHash) REFERENCES BasicBlocks(bbid, imgHash)" +
              ");";

        try {
            stmt.execute(sql);
        } catch(SQLException e) {
            Logger.error(e, "Unable to create ReuseStats table in trace database");
            return false;
        }
        return true;
 
    }

    public boolean closeSysid(int sysid){
        Logger.inform("Closing database for sysid " + sysid);

        assert(sysidConnections.containsKey(sysid));

        commitTracedb(sysidConnections.get(sysid));
        closeTracedb(sysidConnections.get(sysid));

        sysidConnections.remove(sysid);

        return true;
    }

    public Connection openSysid(int sysid){
        Logger.inform("Opening database for sysid " + sysid);

        Connection c = openTracedb(sysidDBName(sysid), properties);
        sysidConnections.put(sysid, c);

        return c;
    }

    public boolean initializeSysid(int sysid){

        //Logger.inform("Initializing database for sysid " + sysid);

        Connection c = openTracedb(sysidDBName(sysid), properties);
        sysidConnections.put(sysid, c);

        // create tables for sysid databases
        String sql;
        Statement stmt;
        try {
            stmt = c.createStatement();
        } catch (SQLException e) {
            Logger.error(e, "Unable to create statement");
            return false;
        }

        sql = "CREATE CACHED TABLE BlockCacheHitRates (" +
              "bbid BIGINT" +
              ", imgHash BIGINT" +
              ", rank INTEGER" +
              ", thread INTEGER" +
              ", l1hits BIGINT" +
              ", l1misses BIGINT" +
              ", l2hits BIGINT" +
              ", l3hits BIGINT" +
              ", UNIQUE (bbid, imgHash, rank, thread)" +
              ");";
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            Logger.error(e, "Unable to create BlockCacheHitRates table in trace database");
            return false;
        }

        return true;
    }

    // -- Data getters/setters --//

    /**
     * @return a map from function ids to a set of member basic block ids
     */
    public Map<FunctionID, Set<BlockID>> getFuncToBbids() {
        Map<FunctionID, Set<BlockID>> funcs = Util.newHashMap();

        try {
            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT bbid, imgHash, function from BasicBlocks;"
            );
            tracedbTimerEnd("select bbids/functions");

            while( res.next() ) {
                Long bbid = res.getLong(1);
                Long imgHash = res.getLong(2);
                FunctionID func = new FunctionID(imgHash, res.getLong(3));
                assert(func != null);

                Set<BlockID> bbs = funcs.get(func);
                if( bbs == null ) {
                    bbs = Util.newHashSet();
                    funcs.put(func, bbs);
                }
                bbs.add(new BlockID(imgHash, bbid));
            }

        } catch (SQLException e) {
            Logger.error(e, "Unable to retrieve func to bbid mapping from trace database");
            return null;
        }
        return funcs;
    }

    public Map<FunctionID, String> getFunctionNames() {
        Map<FunctionID, String> funcs = Util.newHashMap();

        try {
            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT fid, imgHash, name from Functions;"
            );
            tracedbTimerEnd("select function names");

            while(res.next()) {
                Long funcHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                String name = res.getString(3);

                FunctionID fid = new FunctionID(imgHash, funcHash);
                funcs.put(fid, name);
            }
        } catch(SQLException e) {
            Logger.error(e, "Unable to retrieve function names from trace database");
            return null;
        }
        return funcs;
    }

    /**
     * @return a set of all sysids in this trace database
     */
    public Map<Integer, Integer> getSysidLevels() {
        Map<Integer, Integer> sysidLevels = Util.newHashMap();

        try {
            Statement stmt = dbConnection.createStatement();
            ResultSet res = stmt.executeQuery(
                "SELECT * FROM Sysids;");

            while( res.next() ) {
                Integer sysid = res.getInt(1);
                Integer level = res.getInt(2);
                if (validSysid == null || validSysid.contains(sysid)){
                    sysidLevels.put(sysid, level);
                }
            }
        } catch (SQLException e) {
            Logger.error(e, "Unable to select sysids from trace database");
            return null;
        }
        return sysidLevels;
    }

    /**
     * @return a map from basic block ids to average bytes per memory operation
     */
    public Map<BlockID, Double> getBbidToBytesPerMemop() {
        Map<BlockID, Double> blocks = Util.newHashMap();

        try {
            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT bbid, imgHash, case when memOps = 0 then 0 else CAST(memBytes as DOUBLE) / CAST(memOps as DOUBLE) end from BasicBlocks;"
            );
            tracedbTimerEnd("select bbid->bytes/memop");

            while( res.next() ) {
                Long bbid = res.getLong(1);
                Long imgHash = res.getLong(2);
                Double bytesPerMemop = res.getDouble(3);
                blocks.put(new BlockID(imgHash, bbid), bytesPerMemop);
            }

        } catch (SQLException e) {
            Logger.error(e, "Unable to query memop bytes from trace database");
            return null;
        }
        return blocks;
    }

    // set this to zero to only cache *this* rank's blocks
    public static final int ADDRESS_RANGE_RANK_LOOKAHEAD = 31;
    private Map<Integer, Map<BlockID, AddressRanges>> cachedRanges = Util.newHashMap();


    // Returns a bbid -> thread -> address range
    public Map<BlockID, Map<Integer, AddressRanges>> getAllAddressRanges(int rank){
        try {
            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT * from AddressRanges " +
                "WHERE rank = " + rank);
            tracedbTimerEnd("select all address ranges");

            Map<BlockID, Map<Integer, AddressRanges>> addressRanges = Util.newHashMap();
            while(res.next()) {
                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                int bbrank = res.getInt(3);
                int thread = res.getInt(4);
                Long minAddr = res.getLong(5);
                Long maxAddr = res.getLong(6);
                BlockID bbid = new BlockID(imgHash, blockHash);

                Map<Integer, AddressRanges> brngs = addressRanges.get(bbid);
                if(brngs == null) {
                    brngs = Util.newHashMap();
                    addressRanges.put(bbid, brngs);
                }
                AddressRanges rngs = new AddressRanges();
                rngs.addRange(minAddr, maxAddr);
                brngs.put(thread, rngs);
            }

            return addressRanges;
        } catch (SQLException e) {
            Logger.error(e, "Unable to query address ranges from trace database");
        }
        return null;
    }

    /**
     * @return a set of all basic blocks with all static data members filled except Duds
     */
    // bbid -> BasicBlock
    public Map<BlockID, BasicBlock> getAllBlocks() {

        Map<BlockID, BasicBlock> blocks = Util.newHashMap();

        try {
            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT * from BasicBlocks " +
                "LEFT OUTER JOIN Functions " +
                "ON Functions.fid = BasicBlocks.function " +
                "AND Functions.imgHash = BasicBlocks.imgHash " +
                "LEFT OUTER JOIN Loops " +
                "ON Loops.loopHead = BasicBlocks.bbid " +
                "AND Loops.imgHash = BasicBlocks.imgHash;"
            );
            tracedbTimerEnd("select all blocks");
            while( res.next() ) {
                BasicBlock bb = new BasicBlock();

                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                bb.bbid = new BlockID(imgHash, blockHash);
                bb.insns = res.getInt(3);
                bb.intOps = res.getInt(4);
                bb.branchOps = res.getInt(5);
                bb.logicOps = res.getInt(6);
                bb.shiftRotateOps = res.getInt(7);
                bb.trapSyscallOps = res.getInt(8);
                bb.memOps = res.getInt(9);
                bb.loadOps = res.getInt(10);
                bb.storeOps = res.getInt(11);
                bb.fpOps = res.getInt(12);
                bb.specialRegOps = res.getInt(13);
                bb.otherOps = res.getInt(14);
                bb.memBytes = res.getInt(15);
                bb.defUseCrossCount = res.getInt(16);
                bb.callCount = res.getInt(17);
                bb.file = res.getString(18);
                assert(bb.file != null);
                bb.functionID = new FunctionID(imgHash, res.getLong(19));
                bb.line = res.getInt(20);
                bb.loopHead = new BlockID(imgHash, res.getLong(21));
                bb.loopLoc = res.getInt(22);
                bb.callTargetAddress = res.getLong(23);
                bb.callTargetName = res.getString(24);
                bb.vaddr = res.getLong(25);
                bb.scatterGatherOps = res.getInt(26);
                bb.vectorMaskOps = res.getInt(27);
                assert(bb.callTargetName != null);

                // Join Funtions at col 26
                bb.functionName = res.getString(30);
                bb.loopCount = res.getInt(32);

                // Join Loops at col 31
                bb.parentLoopHead = new BlockID(imgHash, res.getLong(35));
                bb.loopDepth = res.getInt(36);

                blocks.put(bb.bbid, bb);
            }

        } catch (SQLException e) {
            Logger.error(e, "Unable to query basic blocks from trace database");
            return null;
        }

        return blocks;
    }

    /**
     * Fills static info for funcStats and/or loopStats if not null
     * blockStatic must not be null
     */ 
    public void getStaticStats(Map<BlockID, BasicBlock> blockStatic,
                               Map<FunctionID, Function> funcStats,
                               Map<BlockID, Loop> loopStats) {

        assert( blockStatic != null );

        // make a pass to pick off loop heads and create Loop objects
        for (Iterator<BasicBlock> it = blockStatic.values().iterator(); it.hasNext(); ){
            BasicBlock bb = it.next();
            BlockID bbid = bb.bbid;
            FunctionID fid = bb.functionID;
            BlockID loopHead = bb.loopHead;

            if( loopStats != null ) {
                if(loopHead.equals(bbid) ||
                   loopHead.blockHash.equals(0L) && !loopStats.containsKey(loopHead)){
                    assert(!loopStats.containsKey(loopHead));
                    Loop loop = new Loop();
                    loop.setHead(bb);
                    
                    // this loop is a catch-all for all blocks that aren't in a loop
                    if (loop.headBlock.blockHash.longValue() == 0){
                        loop.setUnknown();
                    }
                    loop.loopCount = bb.loopCount;//1;
                    loop.loopDepth = bb.loopDepth;
                    loopStats.put(loop.headBlock, loop);
                }
            }

            if( funcStats != null ) {
                Function f = funcStats.get(fid);
                if( f == null ) {
                    f = new Function();

                    f.functionID = fid;
                    f.functionName = bb.functionName;
                    f.file = bb.file;
                    f.line = bb.line;
                    f.loopCount = bb.loopCount;
                    f.loopDepth = bb.loopDepth;
                    f.headBlock = bbid;
                    funcStats.put(fid, f);
                }

            }
        }

        for (Iterator<BasicBlock> it = blockStatic.values().iterator(); it.hasNext(); ){
            BasicBlock bb = it.next();
            BlockID bbid = bb.bbid;
            FunctionID fid = bb.functionID;
            BlockID loopHead = bb.loopHead;
            if( loopStats != null ) {

                    Loop loop = loopStats.get(bb.loopHead);
                    assert(loop != null);
                    loop.loopCount = 0;
                    while (true){
                      if (loop.headBlock.equals(loop.parentHead)){
                            break;
                      }
                      int currLoopDepth = loop.loopDepth;                      
                      loop = loopStats.get(loop.parentHead);
                      assert(loop != null);
                      loop.loopCount +=1;
                    }
            }
        } 

    }

    /**
     * Get dynamic stats for a particular rank/system
     * fills in dynamic stats for funcStats and/or loopStats if not null
     * blockStatic must not be null
     * if not null, funcStats and loopStats must already contain all static info
     * Use -1 for sysid/rank to collect data for all sysids/ranks
     */
    public void getDynamicStats(Map<BlockID, BasicBlock> blockStatic,
                                Map<FunctionID, Function> funcStats,
                                Map<BlockID, Loop> loopStats,
                                int sysid_in, int rank) {

        assert( blockStatic != null );

        // Static info
        Map<BlockID, Set<Dud>> allStaticDuds = getAllDuds();
        Map<BlockID, Collection<VecOps>> allStaticVecOps = getAllVecOps();

        // Get system info
        Map<Integer, Integer> allSysidLevels = getSysidLevels();
        Map<Integer, Integer> sysidLevels;
        if( sysid_in == NO_SYSID ) {
            sysidLevels = Util.newHashMap();
        } else if( sysid_in == ALL_SYSIDS ) {
            sysidLevels = allSysidLevels;
        } else {
            sysidLevels = Util.newHashMap();
            sysidLevels.put(sysid_in, allSysidLevels.get(sysid_in));
        }

        // Get Block/Loop visits for rank
        Map<Integer, Map<BlockID, Long>> blockVisits = getBlockVisits(rank);
        Map<Integer, Map<BlockID, Long>> loopVisits = getLoopVisits(rank);

        Map<Integer, Map<BlockID, List<BinnedCounter>>> spatialStats = getSpatialStats(rank, blockVisits, blockStatic);

        // Set loop entry counts
        if( loopStats != null ) {
            for( Iterator<Loop> it = loopStats.values().iterator(); it.hasNext(); ) {
                Loop loop = it.next();

                for (Iterator<Integer> rit = loopVisits.keySet().iterator(); rit.hasNext(); ){
                    Integer myrank = rit.next();
                    Map<BlockID, Long> rankVisits = loopVisits.get(myrank);

                    Long entries = rankVisits.get(loop.headBlock);
                    if (entries != null){
                        loop.entryCount += entries;
                    }
                }
            }
        }

        // aggregate non-sysid-related stats
        for (Iterator<BasicBlock> it = blockStatic.values().iterator(); it.hasNext(); ){
            BasicBlock bb = it.next();
            BlockID bbid = bb.bbid;
            FunctionID fid = bb.functionID;
            BlockID loopHead = bb.loopHead;
            bb.visitCount = 0L;

            // sum visit counts for each rank
            for (Iterator<Integer> rit = blockVisits.keySet().iterator(); rit.hasNext(); ){
                Integer myrank = rit.next();
                Map<BlockID, Long> rankVisits = blockVisits.get(myrank);

                long visitCount = 0L;

                if (rankVisits.containsKey(bbid)){
                    visitCount = rankVisits.get(bbid);
                }
                bb.visitCount += visitCount;
            }

            // collect dynamic loop stats
            if(loopStats != null) {
                Loop loop = loopStats.get(loopHead);
                do {
                    // aggregate
                    loop.aggregateBasicBlock(bb.visitCount, bb);
                    loop.aggregateDuds(bb.visitCount, allStaticDuds.get(bbid));
                    loop.aggregateVecOps(bb.visitCount, allStaticVecOps.get(bbid));
                    for(Iterator<Integer> rit = spatialStats.keySet().iterator(); rit.hasNext(); ) {
                        Integer myrank = rit.next();
                        Map<BlockID, List<BinnedCounter>> blockSpatial = spatialStats.get(myrank);
                        loop.aggregateSpatialStats(blockSpatial.get(bbid));
                    }

                    // break if outer
                    if(loop.headBlock.equals(loop.parentHead)) {
                        break;
                    }

                    // move out
                    loop = loopStats.get(loop.parentHead);
                } while(true);
            }

            // collect dynamic function stats
            if (funcStats != null){
                Function f = funcStats.get(fid);

                f.aggregateBasicBlock(bb.visitCount, bb);
                f.aggregateDuds(bb.visitCount, allStaticDuds.get(bbid));
                f.aggregateVecOps(bb.visitCount, allStaticVecOps.get(bbid));
                for(Iterator<Integer> rit = spatialStats.keySet().iterator(); rit.hasNext(); ) {
                    Integer myrank = rit.next();
                    Map<BlockID, List<BinnedCounter>> blockSpatial = spatialStats.get(myrank);
                    f.aggregateSpatialStats(blockSpatial.get(bbid));
                }

                if( bbid.compareTo(f.headBlock) <= 0 ) {
                    f.entryCount = bb.visitCount;
                    f.headBlock = bbid;
                }

                //if( bb.callTargetName != null ) {
                //    Function target = funcStats.get(bb.callTargetName);
                //    if( target != null ) {
                //        target.entryCount += bb.visitCount;
                //    }
                //}
            }

        }

        // check function entry counts
        if(funcStats != null) {
            for(Iterator<BasicBlock> it = blockStatic.values().iterator(); it.hasNext(); ) {
                BasicBlock bb = it.next();
                FunctionID fid = bb.functionID;
                BlockID bbid = bb.bbid;

                Function f = funcStats.get(fid);
                if( f.entryCount == 0 && bb.visitCount > 0 ) {
                    Logger.warn("Function " + fid + " has 0 entries with entry block " + f.headBlock + " but member block " + bbid + " has " + bb.visitCount + " visits");
/*
                    Logger.inform("Collecting member blocks");
                    List<BasicBlock> memberBlocks = Util.newLinkedList();
                    for(Iterator<BasicBlock> bit = blockStatic.values().iterator(); bit.hasNext(); ) {
                        BasicBlock bb2 = bit.next();
                        if(bb2.function.equals(funcName)){
                            memberBlocks.add(bb2);
                        } else if(bb2.bbid.equals(bbid)) {
                            Logger.warn("Function " + funcName + " isn't equal to " + bb2.function);
                        }
                    }
                    Collections.sort(memberBlocks);
                    for(Iterator<BasicBlock> bit = memberBlocks.iterator(); bit.hasNext(); ) {
                        BasicBlock bb2 = bit.next();
                        Logger.inform("Member block " + bb2.bbid + " has " + bb2.visitCount + " visits");
                    }
                    //assert(false);
*/
                }
            }
        }

        // Set dynamic block memops and visits in blocks
        Map<Integer, Map<BlockID, Long>> blockMemCounts = Util.newHashMap();

        // for each rank
        for (Iterator<Integer> it = blockVisits.keySet().iterator(); it.hasNext(); ){
            Integer myrank = it.next();
            Map<BlockID, Long> rankVisits = blockVisits.get(myrank);

            // initialize rankMems
            Map<BlockID, Long> rankMems = Util.newHashMap();
            blockMemCounts.put(myrank, rankMems);

            // for each block
            for (Iterator<BlockID> bit = rankVisits.keySet().iterator(); bit.hasNext(); ){
                BlockID bbid = bit.next();
                Long visits = rankVisits.get(bbid);

                BasicBlock bb = blockStatic.get(bbid);
                Long dynMemOps = visits * bb.memOps;

                rankMems.put(bbid, dynMemOps);
            }
        }

        // pass over every sysid to get cache stats
        for (Iterator<Integer> sit = sysidLevels.keySet().iterator(); sit.hasNext(); ){
            Integer sysid = sit.next();
            Integer levels = sysidLevels.get(sysid);

            Map<Integer, Map<BlockID, CacheStats>> blockRates = getAverageHitRates(sysid, rank, blockMemCounts);

            for (Iterator<BasicBlock> it = blockStatic.values().iterator(); it.hasNext(); ){
                BasicBlock bb = it.next();
                BlockID bbid = bb.bbid;
                FunctionID fid = bb.functionID;
                BlockID loopHead = bb.loopHead;

                for (Iterator<Integer> rit = blockVisits.keySet().iterator(); rit.hasNext(); ){
                    Integer myrank = rit.next();
                    Map<BlockID, Long> rankVisits = blockVisits.get(myrank);

                    Long visitCount = rankVisits.get(bbid);

                    if (visitCount != null){
                        Map<BlockID, CacheStats> rankRates = blockRates.get(myrank);
                        if(rankRates == null) {
                            continue;
                        }

                        CacheStats c = rankRates.get(bbid);
                        if (c != null){
                            if (loopStats != null){
                                Loop loop = loopStats.get(bb.loopHead);
                                assert(loop != null);
                                loop.aggregateCacheStats(sysid, c, bbid);
                                
                                while (true){
                                    if (loop.headBlock.equals(loop.parentHead)){
                                        break;
                                    }
                                    loop = loopStats.get(loop.parentHead);
                                    assert(loop != null);
                                    loop.aggregateCacheStats(sysid, c, bbid);
                                }
                            }
                            
                            if (funcStats != null){
                                Function f = funcStats.get(fid);
                                f.aggregateCacheStats(sysid, c, bbid);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Aggregate stats across all ranks for all systems
     * fills in funcStats and/or loopStats if not null
     */
    public void getDynamicAggregateStats(Map<FunctionID, Function> funcStats, Map<BlockID, Loop> loopStats){
        getDynamicAggregateStats(funcStats, loopStats, ALL_SYSIDS);
    }

    public void getDynamicAggregateStats(Map<FunctionID, Function> funcStats, Map<BlockID, Loop> loopStats, int sysid) {

        if (funcStats == null && loopStats == null){
            return;
        }

        Map<BlockID, BasicBlock> blockStatic = getAllBlocks();
        getStaticStats(blockStatic, funcStats, loopStats);
        getDynamicStats(blockStatic, funcStats, loopStats, sysid, -1);
    }

    // rank -> [bbid -> count]
    // rank == INVALID_RANK means get all ranks
    public Map<Integer, Map<BlockID, Long>> getBlockVisits(int rank){
        Map<Integer, Map<BlockID, Long>> blockPerRank = Util.newHashMap();

        try {
            String rankFilt = makeRankFilt(rank);

            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT bbid, " +
                "imgHash, " +
                "rank, " +
                "SUM(jbbVisitCount), " +
                "SUM(simVisitCount) " +
                "FROM BlockVisitCounts" +
                rankFilt +
                " GROUP BY bbid, imgHash, rank;");

            tracedbTimerEnd("selecting dynamic block counts per rank");

            while (res.next()){
                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                BlockID bbid = new BlockID(imgHash, blockHash);
                Integer myrank = res.getInt(3);
                Long visitCount = getVisitCount(res.getLong(4), res.getLong(5));

                Map<BlockID, Long> blockStats = blockPerRank.get(myrank);
                if (blockStats == null){
                    blockStats = Util.newHashMap();
                    blockPerRank.put(myrank, blockStats);
                }

                blockStats.put(bbid, visitCount);
            }
        } catch (SQLException e) {
            Logger.error(e, "Unable to query dynamic function summary from trace database");
            return null;
        }
        if(blockPerRank.size() == 0) {
            Map<BlockID, Long> nullMap = Util.newHashMap();
            blockPerRank.put(0, nullMap);
        }
        return blockPerRank;
    }

    // rank -> bbid -> thread -> count
    // rank == INVALID_RANK means get all ranks
    public Map<Integer, Map<BlockID, Map<Integer, Long>>> getBlockVisitsPerThread(int rank){
        Map<Integer, Map<BlockID, Map<Integer, Long>>> blockPerRank = Util.newHashMap();

        try {
            String rankFilt = makeRankFilt(rank);

            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT bbid, " +
                "imgHash, " +
                "rank, " +
                "thread, " +
                "SUM(jbbVisitCount), " +
                "SUM(simVisitCount) " +
                "FROM BlockVisitCounts" +
                rankFilt +
                " GROUP BY bbid, imgHash, rank, thread;");

            tracedbTimerEnd("selecting dynamic block counts per rank");

            while (res.next()){
                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                BlockID bbid = new BlockID(imgHash, blockHash);
                Integer myrank = res.getInt(3);
                Integer thread = res.getInt(4);
                Long visitCount = getVisitCount(res.getLong(5), res.getLong(6));

                Map<BlockID, Map<Integer, Long>> rankStats = blockPerRank.get(myrank);
                if (rankStats == null){
                    rankStats = Util.newHashMap();
                    blockPerRank.put(myrank, rankStats);
                }
                Map<Integer, Long> blockStats = rankStats.get(bbid);
                if(blockStats == null) {
                    blockStats = Util.newHashMap();
                    rankStats.put(bbid, blockStats);
                }

                blockStats.put(thread, visitCount);
            }
        } catch (SQLException e) {
            Logger.error(e, "Unable to query dynamic function summary from trace database");
            return null;
        }
        if(blockPerRank.size() == 0) {
            Map<BlockID, Map<Integer,  Long>> nullMap = Util.newHashMap();
            blockPerRank.put(0, nullMap);
        }
        return blockPerRank;
    }

    // rank -> {head -> entries}
    public Map<Integer, Map<BlockID, Long>> getLoopVisits(int rank){
        Map<Integer, Map<BlockID, Long>> entries = Util.newHashMap();
        try {
            Connection conn = dbConnection;
            Statement stmt = conn.createStatement();

            String rankFilt = makeRankFilt(rank);

            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(                   
                "SELECT loopHead, " +
                "imgHash, " +
                "rank, " +
                "SUM(loopVisitCount) " +
                "FROM LoopVisitCounts" +
                rankFilt +
                " GROUP BY loopHead, imgHash, rank;");

            tracedbTimerEnd("select loop visits");

            int loopCount = 0;
            while (res.next()){
                Long loopHeadHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                BlockID loopHead = new BlockID(imgHash, loopHeadHash);
                Integer myrank = res.getInt(3);
                Long loopVisitCount = res.getLong(4);

                Map<BlockID, Long> e = entries.get(myrank);
                if (e == null){
                    e = Util.newHashMap();
                    entries.put(myrank, e);
                }
                e.put(loopHead, loopVisitCount);

                loopCount++;
            }
            if (loopCount == 0){
                Logger.warn("Nothing found when querying for loop visit counts, you probably did not submit .loopcnt data");
            }
        } catch (SQLException e) {
            Logger.error(e, "Unable to query loop visit counts");
            return null;
        }
        return entries;
    }

    // return bbid -> cacheStats
    public Map<BlockID, CacheStats> getAverageHitRates(int sysid, int rank, Map<BlockID, Long> rankVisits, Map<BlockID, BasicBlock> blockStatic) {
        assert(rank != INVALID_RANK);
        Map<Integer, Map<BlockID, Long>> allMemCounts = Util.newHashMap();
        allMemCounts.put(rank, getBlockMemOps(rankVisits, blockStatic));
        return getAverageHitRates(sysid, rank, allMemCounts).get(rank);
    }

    public static Map<BlockID, Long> getBlockMemOps(Map<BlockID, Long> rankVisits, Map<BlockID, BasicBlock> blockStatic) {
        Map<BlockID, Long> rankMems = Util.newHashMap();

        for(Iterator<BlockID> bit = rankVisits.keySet().iterator(); bit.hasNext(); ) {
            BlockID bbid = bit.next();
            BasicBlock bb = blockStatic.get(bbid);
            Long visits = rankVisits.get(bbid);
            Long dynMemOps = visits*bb.memOps;
            rankMems.put(bbid, dynMemOps);
        }
        return rankMems;
    }

    public Map<BlockID, Map<Integer, Long>> getBlockMemsPerThread(Map<BlockID, Map<Integer, Long>> rankVisits, Map<BlockID, BasicBlock> blockStatic) {
        Map<BlockID, Map<Integer, Long>> rankMems = Util.newHashMap();

        for(Iterator<BlockID> bit = rankVisits.keySet().iterator(); bit.hasNext(); ) {
            BlockID bbid = bit.next();
            BasicBlock bb = blockStatic.get(bbid);
            Map<Integer, Long> blockVisits = rankVisits.get(bbid);
            Map<Integer, Long> blockMems = Util.newHashMap();
            rankMems.put(bbid, blockMems);

            for(Iterator<Integer> tit = blockVisits.keySet().iterator(); tit.hasNext(); ) {
                Integer thread = tit.next();
                Long visits = blockVisits.get(thread);
                Long dynMemOps = visits*bb.memOps;
                blockMems.put(thread, dynMemOps);
            }
        }
        return rankMems;
    }

    private String makeRankFilt(int rank) {
        String rankFilt = "";
        if (rank != INVALID_RANK){
            rankFilt = " WHERE rank = " + rank;
        }
        return rankFilt;
    }

    public void correctForSampling(int levels, long memCounts, long l1hits, long l1misses, long l2hits, long l3hits, CacheStats c) {
        double multiplier = ((double)memCounts) / ((double)(l1hits + l1misses));

        assert(memCounts >= l1misses + l1hits);
        assert(l1hits >= 0L);
        assert(l1misses >= 0L);
        assert(l2hits >= 0L);
        assert(l3hits >= 0L);
          
        // If there was only one miss, assume it won't be repeated
        if(l1misses == 1) {
            c.addLevelCounts(1, memCounts - 1, l1misses);
        } else {
            c.addLevelCounts(1, (long)(l1hits * multiplier), (long)(l1misses * multiplier));
        }

        if (levels > 1){
            long l2misses = l1misses - l2hits;
            if( l2hits < 0L || l2misses < 0L ) {
                Logger.warn("Invalid cache hit/miss counts for block with " + l1hits + "," + l1misses + "," + l2hits);
            }
            assert(l2hits >= 0L);
            assert(l2misses >= 0L);
            if(l2misses == 1) {
                c.addLevelCounts(2, c.getMisses(1)-1, l2misses);
            } else {
                c.addLevelCounts(2, (long)(l2hits * multiplier), (long)(l2misses * multiplier));
            }

            if (levels > 2){
                long l3misses = l1misses - l2hits - l3hits;
                if(l3hits < 0L || l3misses < 0L) {
                    Logger.warn("Invalid cache hit/miss counts for block with l1hits,l1misses,l2hits,l3hits=" + l1hits +
                        "," + l1misses + "," + l2hits + "," + l3hits);
                }

                if(l3misses == 1) {
                    c.addLevelCounts(3, c.getMisses(2)-1, l3misses);
                } else {
                    c.addLevelCounts(3, (long)(l3hits * multiplier), (long)(l3misses * multiplier));
                }
            }
        }

    }
    // return rank -> [bbid -> CacheStats]
    public Map<Integer, Map<BlockID, CacheStats>> getAverageHitRates(int sysid, int rank, Map<Integer, Map<BlockID, Long>> blockMemCounts){

        Map<Integer, Map<BlockID, CacheStats>> blockStats = Util.newHashMap();
        Map<Integer, Integer> sysidLevels = getSysidLevels();
	if(sysidLevels.get(sysid) == null) {
            return blockStats;
        }
        
        int levels = getSysidLevels().get(sysid);

        boolean closeIt = false;
        try {
            Connection conn = sysidConnections.get(sysid);
            if (conn == null){
                conn = openSysid(sysid);
                closeIt = true;
            }
            assert(conn != null);

            String rankFilt = "";
            if (rank != INVALID_RANK){
                rankFilt = " WHERE rank = " + rank;
            }

            Statement stmt = conn.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT bbid, " +
                "imgHash, " +
                "rank, " +
                "SUM(l1hits), " +
                "SUM(l1misses), " +
                "SUM(l2hits), " +
                "SUM(l3hits) " +
                "FROM BlockCacheHitRates " +
                rankFilt + 
                " GROUP BY bbid, imgHash, rank;");

            tracedbTimerEnd("select hit rates for sysid " + sysid);
            if (closeIt){
                closeSysid(sysid);
            }

            while (res.next()){
                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                BlockID bbid = new BlockID(imgHash, blockHash);
                Integer myrank = res.getInt(3);

                Map<BlockID, CacheStats> rankStats = blockStats.get(myrank);
                if (rankStats == null){
                    rankStats = Util.newHashMap();
                    blockStats.put(myrank, rankStats);
                }

                CacheStats c = rankStats.get(bbid);
                if (c == null){
                    c = new CacheStats(levels);
                    rankStats.put(bbid, c);
                }

                long l1hits = res.getLong(4);
                long l1misses = res.getLong(5);
                assert(!res.wasNull());

                long l2hits = res.getLong(6);
                long l3hits = res.getLong(7);

                // Correct for sampling using actual block visit counts
                Map<BlockID, Long> rankMemCounts = blockMemCounts.get(myrank);
                assert(rankMemCounts != null);

                Long mem = rankMemCounts.get(bbid);
                if(mem == null) {
                    Logger.warn("Unable to find visit counts for block " + bbid);
                    continue;
                }
                correctForSampling(levels, mem, l1hits, l1misses, l2hits, l3hits, c);
            }
        } catch (SQLException e) {
            Logger.error(e, "Unable to query dynamic function summary from trace database");
            return null;
        }

        return blockStats;
    }

    public Map<BlockID, Map<Integer, CacheStats>> getCacheStatsPerThread(int sysid, int rank, Map<BlockID, Map<Integer, Long>> allMems) {
        assert(rank != INVALID_RANK);
        Map<Integer, Map<BlockID, Map<Integer, Long>>> allRankMems = Util.newHashMap();
        allRankMems.put(rank, allMems);
        return getAllRanksCacheStatsPerThread(sysid, rank, allRankMems).get(rank);
    }

    // rank -> block -> thread -> CacheStats
    public Map<Integer, Map<BlockID, Map<Integer, CacheStats>>> getAllRanksCacheStatsPerThread(int sysid, int rank, Map<Integer, Map<BlockID, Map<Integer, Long>>> allMems) {
        Map<Integer, Map<BlockID, Map<Integer, CacheStats>>> allStats = Util.newHashMap();

        int levels = getSysidLevels().get(sysid);
        try {
            boolean closeIt = false;
            Connection conn = sysidConnections.get(sysid);
            if(conn == null) {
                conn = openSysid(sysid);
                closeIt = true;
            }
            assert(conn != null);

            String rankFilt = makeRankFilt(rank);

            Statement stmt = conn.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT bbid, " +
                "imgHash, " +
                "rank, " +
                "thread, " +
                "SUM(l1hits), " +
                "SUM(l1misses), " +
                "SUM(l2hits), " +
                "SUM(l3hits) " +
                "FROM BlockCacheHitRates " +
                rankFilt +
                " GROUP BY bbid, imgHash, rank, thread;"
            );

            tracedbTimerEnd("select cache stats for sysid " + sysid);
            if(closeIt) {
                closeSysid(sysid);
            }

            while(res.next()) {
                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                BlockID bbid = new BlockID(imgHash, blockHash);
                Integer myrank = res.getInt(3);
                Integer thread = res.getInt(4);
                long l1hits = res.getLong(5);
                long l1misses = res.getLong(6);
                long l2hits = res.getLong(7);
                long l3hits = res.getLong(8);

                Map<BlockID, Map<Integer, CacheStats>> rankStats = allStats.get(myrank);
                if(rankStats == null) {
                    rankStats = Util.newHashMap();
                    allStats.put(myrank, rankStats);
                }

                Map<Integer, CacheStats> blockStats = rankStats.get(bbid);
                if(blockStats == null) {
                    blockStats = Util.newHashMap();
                    rankStats.put(bbid, blockStats);
                }

                CacheStats c = new CacheStats(levels);
                blockStats.put(thread, c);

                Map<BlockID, Map<Integer, Long>> rankMems = allMems.get(myrank);
                Map<Integer, Long> blockMems = rankMems.get(bbid);
                Long threadMems = blockMems.get(thread);
                if(threadMems == null) {
                    threadMems = 0L;
                    Logger.inform("0 mems for block/rank/thread " + bbid + "\t" + rank + "\t" + thread + "\n");
                }

                correctForSampling(levels, threadMems, l1hits, l1misses, l2hits, l3hits, c);

               
            }
        } catch (SQLException e) {
            Logger.error(e, "Unable to query dynamic function summary from trace database");
            return null;
        }
        return allStats;
    }

    public Map<BlockID, Collection<VecOps>> getAllVecOps() {
        Map<BlockID, Collection<VecOps>> vecops = Util.newHashMap();

        try {
            Statement stmt = dbConnection.createStatement();
            ResultSet res = stmt.executeQuery("SELECT * from VectorOps ORDER BY imgHash, bbid;");

            Collection<VecOps> bbvecops = null;
            BlockID lastBbid = null;
            BlockID bbid = null;
            while(res.next()) {
                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                bbid = new BlockID(imgHash, blockHash);
                if(lastBbid == null || !bbid.equals(lastBbid)) {
                    bbvecops = Util.newHashSet();
                    vecops.put(bbid, bbvecops);
                    lastBbid = bbid;
                }

                bbvecops.add(new VecOps(res.getInt(3), res.getInt(4), ((Integer)res.getInt(5)).longValue(), ((Integer)res.getInt(6)).longValue()));
            }
        } catch (SQLException e) {
            Logger.error("Unable to query vecops from trace database");
            return null;
        }
        return vecops;
    }

    /**
     * @return a map from basic block ids to its set of duds
     */
    public Map<BlockID, Set<Dud>> getAllDuds() {
        Map<BlockID, Set<Dud>> duds = Util.newHashMap();

        try {
            Statement stmt = dbConnection.createStatement();
            ResultSet res = stmt.executeQuery("SELECT * from Duds ORDER BY imgHash, bbid;");

            Set<Dud> bbduds = null;
            BlockID lastBbid = null;
            BlockID bbid = null;
            while( res.next() ) {
                
                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                
                bbid = new BlockID(imgHash, blockHash);
                if( lastBbid == null || !bbid.equals(lastBbid) ) {
                    bbduds = Util.newHashSet();
                    duds.put(bbid, bbduds);
                    lastBbid = bbid;
                } else {
                }
                bbduds.add(new Dud(res.getInt(3), res.getInt(5), res.getInt(4), res.getInt(6)));
            }

        } catch (SQLException e) {
            Logger.error("Unable to query duds from trace database");
            return null;
        }
        return duds;

    }

    /**
     * @return a set of functions with the follwing members set:
     *  name, file, line, numBlocks, insns, memOps, fpOps, jbbVisitCount.
     */
    public Set<Function> getFunctionSummaryGPU() {

        Set<Function> funcs = Util.newHashSet();

        try {
            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT Functions.name, " +
                       "Functions.file, " +
                       "MIN(line), " +
                       "COUNT(*), " +
                       "SUM(insns), " +
                       "SUM(memops), " +
                       "SUM(fpops), " +
                       "MAX(case when loopHead = -1 then BlockVisits.cnt else 0 end), " +
                       "imgHash, " +
                       "fid " +
                "FROM BasicBlocks " +
                "LEFT JOIN (" +
                    "SELECT bbid, " +
                           "imgHash, " +
                           "SUM(jbbVisitCount) AS cnt " +
                    "FROM BlockVisitCounts " +
                    "GROUP BY bbid, imgHash" +
                ") AS BlockVisits " +
                "ON BasicBlocks.bbid=BlockVisits.bbid " +
                "AND BasicBlocks.imgHash=BlockVisits.imgHash " +
                "LEFT JOIN Functions " +
                "ON BasicBlocks.function=Functions.fid " +
                "AND BasicBlocks.imgHash=Functions.imgHash " +
                "GROUP BY imgHash, fid, Functions.name, Functions.file;");
            tracedbTimerEnd("select GPU summary");

            while( res.next() ) {
                Function f = new Function();
                funcs.add(f);
                f.functionName = res.getString(1);
                assert(f.functionName != null);
                f.file = res.getString(2);
                assert(f.file != null);
                f.line = res.getInt(3);
                f.numBlocks = res.getInt(4);
                f.insns = res.getInt(5);
                f.memOps = res.getInt(6);
                f.fpOps = res.getInt(7);
                f.visitCount = res.getLong(8);
                f.functionID = new FunctionID(res.getLong(9), res.getLong(10));
            }

        } catch (SQLException e) {
            Logger.error(e, "Unable to query function summary from trace database");
            return null;
        }
        return funcs;
    }

    // bbid,rank -> PowerModel stats vector
    public double[] getBlockPowerVector(int sysid, BlockID bbid, int rank) {
        double[] resv = new double[12];
        try {
            Statement stmt = dbConnection.createStatement();
            ResultSet res = stmt.executeQuery(
                "SELECT insns, memOps, fpOps, intOps, fpcnt, intcnt," +
                       "jbbVisitcount, simVisitCount " +
                "FROM BasicBlocks NATURAL JOIN Duds NATURAL JOIN BlockVisitCounts " +
                "WHERE BlockVisitCounts.rank = " + rank + " " +
                "AND bbid = " + bbid.blockHash + " " +
                "AND imgHash = " + bbid.imgHash + ";");

            Connection conn = sysidConnections.get(sysid);
            if (conn == null)
                conn = openSysid(sysid);
            assert(conn != null);
            stmt = conn.createStatement();
            ResultSet reshr = stmt.executeQuery(
                "SELECT l1hits, l1misses, l2hits, l3hits " +
                "FROM BlockCacheHitRates " +
                "WHERE rank = " + rank + " " +
                "AND bbid = " + bbid.blockHash + ";");

            if(res.next() && reshr.next()) {
                resv[0] = res.getInt(1);
                resv[1] = res.getInt(2);
                resv[2] = res.getInt(3);
                resv[3] = res.getInt(4);
                resv[4] = res.getInt(5);
                resv[5] = res.getInt(6);
                resv[6] = res.getLong(7);
                resv[7] = res.getLong(8);
                resv[8] = reshr.getLong(1);
                resv[9] = reshr.getLong(2);
                resv[10] = reshr.getLong(3);
                resv[11] = reshr.getLong(4);

                double insns = resv[0];
                double dinsns = resv[0]*getVisitCount((new Double(resv[6])).longValue(),(new Double(resv[7])).longValue());
                double memop = resv[1];
                double fpops = resv[2];
                double intop = resv[3];
                double fpcnt = resv[4];
                double intcn = resv[5];

                resv[0] = resv[9]/dinsns;
                resv[1] = (resv[9]-resv[10])/dinsns;
                resv[2] = (resv[9]-resv[10]-resv[11])/dinsns;
                resv[3] = fpops/memop;
                resv[4] = memop/insns;
                resv[5] = fpops/insns;
                resv[6] = 0.5+0.5*(fpops-memop)/(fpops+memop);
                resv[7] = intcn;
                resv[8] = fpcnt;
            } else for(int i = 0; i<9; ++i)
                 resv[i] = 0;

        } catch (SQLException e) {
            Logger.error(e, "Unable to query cache stats from trace database");
            return null;
        }
        return resv;
    }

    // set this to 0 to cache only this rank's blocks
    public static final int BLOCK_COUNT_RANK_LOOKAHEAD = 63;
    private Map<Integer, Map<BlockID, Tuple.Tuple2<Long, Long>>> cachedBlockCounts = Util.newHashMap();

    // bbid -> [dFpOps, dMemOps]
    public Map<BlockID, Tuple.Tuple2<Long, Long>> getDynamicBlockCounts(int rank){
        if (!cachedBlockCounts.containsKey(rank)){
            cachedBlockCounts = Util.newHashMap();
            try {
                Connection conn = dbConnection;
                Statement stmt = conn.createStatement();
                
                tracedbTimerBegin();
                ResultSet primaryRes = stmt.executeQuery(                   
                    "SELECT bbid, " +
                    "imgHash, " +
                    "SUM(jbbVisitCount), " +
                    "SUM(simVisitCount), " +
                    "fpops, " +
                    "memops, " +
                    "rank " +
                    "FROM BasicBlocks, BlockVisitCounts " +
                    "WHERE BasicBlocks.bbid = BlockVisitCounts.bbid " + 
                    "AND BasicBlocks.imgHash = BlockVisitCounts.imgHash " +
                    "AND BlockVisitCounts.rank >= " + rank + 
                    "AND BlockVisitCounts.rank <= " + (rank + BLOCK_COUNT_RANK_LOOKAHEAD) +
                    " GROUP BY bbid, imgHash, rank;");
                tracedbTimerEnd("select block dynamic counts ranks [" + rank + "," + (rank + BLOCK_COUNT_RANK_LOOKAHEAD) + "]");

                while (primaryRes.next()){
                    Long blockHash = primaryRes.getLong(1);
                    Long imgHash = primaryRes.getLong(2);
                    BlockID bbid = new BlockID(imgHash, blockHash);
                    Long visitCount = getVisitCount(primaryRes.getLong(3), primaryRes.getLong(4));
                    Long fpops = primaryRes.getLong(5) * visitCount;
                    Long memops = primaryRes.getLong(6) * visitCount;

                    Integer bbrank = primaryRes.getInt(7);
                    
                    Map<BlockID, Tuple.Tuple2<Long, Long>> blockCounts = cachedBlockCounts.get(bbrank);
                    if (blockCounts == null){
                        blockCounts = Util.newHashMap();
                        cachedBlockCounts.put(bbrank, blockCounts);
                    }
                    
                    blockCounts.put(bbid, Tuple.newTuple2(fpops, memops));
                }
                
            } catch (SQLException e) {
                Logger.error(e, "Unable to query simulated blocks");
                return null;
            }
        }
        return cachedBlockCounts.get(rank);
    }

    /**
     * @return a set of tuples (bbid, dynFpops, dynMemops, CacheStats).
     *   The set of blocks returned includes all blocks that were visisted.
     *   If the blocks were not cache simulated or had no memory operations,
     *   the cacheStats will be null.
     */
    private Map<Integer, Map<BlockID, CacheStats>> savedStats = null;
    public Set<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> getSimulatedBlocks(int sysid, int rank, int cpuCount, Map<BlockID, Tuple.Tuple2<Long, Long>> blockStats) {
        Set<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> blocks = Util.newHashSet();

        boolean closeIt = false;
        try {
            if (savedStats == null){
                savedStats = Util.newHashMap();
            }
            
            Map<BlockID, CacheStats> cacheBlocks = savedStats.get(rank);
            if (cacheBlocks == null){
                //Logger.warn("Need to query tracedb for cache results of sysid " + sysid);

                Connection conn = sysidConnections.get(sysid);
                if (conn == null){
                    conn = openSysid(sysid);
                    closeIt = true;
                }
                assert(conn != null);
                Statement stmt = conn.createStatement();
                
                tracedbTimerBegin();
                ResultSet sysidRes = stmt.executeQuery(
                                                       "SELECT bbid, " +
                                                       "imgHash, " +
                                                       "rank, " +
                                                       "SUM(l1hits), " +
                                                       "SUM(l1misses), " +
                                                       "SUM(l2hits), " +
                                                       "SUM(l3hits) " +
                                                       "FROM BlockCacheHitRates " +
                                                       "GROUP BY bbid, imgHash, rank;");
                tracedbTimerEnd("select simulated blocks");
                
                if (closeIt){
                    closeSysid(sysid);
                }

                savedStats = Util.newHashMap();

                int cacheCount = 0;
                while (sysidRes.next()){
                    Long blockHash = sysidRes.getLong(1);
                    Long imgHash = sysidRes.getLong(2);
                    BlockID bbid = new BlockID(imgHash, blockHash);
                    Integer bbrank = sysidRes.getInt(3);
                    Long l1hits = sysidRes.getLong(4);
                    
                    CacheStats c = new CacheStats();                
                    assert(!sysidRes.wasNull());
                    Long l1misses = sysidRes.getLong(5);
                    c.addLevelCounts(1, l1hits, l1misses);
                    
                    Long l2hits = sysidRes.getLong(6);

		    if (!sysidRes.wasNull() && l2hits >= 0){
			c.addLevelCounts(2, l2hits, l1misses - l2hits);

			Long l3hits = sysidRes.getLong(7);
                        if (!sysidRes.wasNull() && l3hits >= 0){
			    c.addLevelCounts(3, l3hits, l1misses - l2hits - l3hits);
			}
		    }

                    Map<BlockID, CacheStats> stats = savedStats.get(bbrank);
                    if (stats == null){
                        stats = Util.newHashMap();
                        savedStats.put(bbrank, stats);
                    }
                    stats.put(bbid, c);
                    cacheCount++;
                }

                // for any rank with no cache activity, insert an empty map so we don't have to keep querying for it
                for (int bbrank = 0; bbrank < cpuCount; bbrank++){
                    Map<BlockID, CacheStats> stats = savedStats.get(bbrank);
                    if (stats == null){
                        stats = Util.newHashMap();
                        savedStats.put(bbrank, stats);
                    }
                }

                cacheBlocks = savedStats.get(rank);
            }

            assert(cacheBlocks != null);
            for (Iterator<BlockID> it = cacheBlocks.keySet().iterator(); it.hasNext(); ){
                BlockID bbid = it.next();
                
                Tuple.Tuple2<Long, Long> b = blockStats.get(bbid);
                if(b == null){
                    Logger.warn("Looking for block " + bbid + " and not found in blockStats. has CacheStats: " + cacheBlocks.get(bbid));
                    continue;
                }
                
                Long fpops = b.get1();
                Long memops = b.get2();
                
                CacheStats c = cacheBlocks.get(bbid);
                assert(c != null);
                
                blocks.add(Tuple.newTuple4(bbid, fpops, memops, c));
            }

        } catch (SQLException e) {
            Logger.error(e, "Unable to query simulated blocks");
            return null;
        }
        return blocks;
    }

    private PreparedStatement insertSysidStmt = null;
    public boolean insertSysids(Map<Integer, Integer> numLevels) {
        if (numLevels.size() == 0){
            return true;
        }

        try {
            if (insertSysidStmt == null){
                String sql = "INSERT INTO Sysids VALUES (" +
                             "?, ?);";
                insertSysidStmt = dbConnection.prepareStatement(sql);
            }
            PreparedStatement stmt = insertSysidStmt;
   
            for (Iterator<Integer> it = numLevels.keySet().iterator(); it.hasNext(); ){
                Integer sysid = it.next();
                Integer levels = numLevels.get(sysid);

                //initializeSysid(sysid);

                stmt.setInt(1, sysid.intValue());
                stmt.setInt(2, levels.intValue());

                stmt.addBatch();
            }

            tracedbTimerBegin();
            stmt.executeBatch();
            tracedbTimerEnd("sysid insert");

            stmt.clearParameters();
            stmt.clearBatch();

        } catch (SQLException e) {
            Logger.error(e, "Unable to insert sysid into trace database");
            return false;
        }

        return true;
    }

    private PreparedStatement insertBasicBlockStmt = null;
    public boolean insertBlocks(Collection<BasicBlock> blocks) {

        if( blocks.size() == 0 ) {
            return true;
        }

        try {
            if( insertBasicBlockStmt == null ) {
                String sql = "INSERT INTO BasicBlocks VALUES (" +
                             "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                             "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                             "?, ?, ?, ?, ?, ?, ?);";
    
                insertBasicBlockStmt = dbConnection.prepareStatement(sql);
            }
            PreparedStatement stmt = insertBasicBlockStmt;
   
            for( Iterator<BasicBlock> bit = blocks.iterator(); bit.hasNext(); ) { 
                BasicBlock bb = bit.next();

                stmt.setLong(1, bb.bbid.blockHash);
                stmt.setLong(2, bb.bbid.imgHash);
                stmt.setInt(3, bb.insns);
                stmt.setInt(4, bb.intOps);
                stmt.setInt(5, bb.branchOps);
                stmt.setInt(6, bb.logicOps);
                stmt.setInt(7, bb.shiftRotateOps);
                stmt.setInt(8, bb.trapSyscallOps);
                stmt.setInt(9, bb.memOps);
                stmt.setInt(10, bb.loadOps);
                stmt.setInt(11, bb.storeOps);
                stmt.setInt(12, bb.fpOps);
                stmt.setInt(13, bb.specialRegOps);
                stmt.setInt(14, bb.otherOps);
                stmt.setInt(15, bb.memBytes);
                stmt.setInt(16, bb.defUseCrossCount);
                stmt.setInt(17, bb.callCount);
                stmt.setString(18, bb.file);
                if (bb.file.length() > INTERNAL_VARCHAR_LENGTH){
                    Logger.error("Database string length too short for file: " + bb.file);
                }
                stmt.setLong(19, bb.functionID.blockHash);
                stmt.setInt(20, bb.line);
                stmt.setLong(21, bb.loopHead.blockHash);
                stmt.setInt(22, bb.loopLoc);
                if (bb.callTargetAddress == null || bb.callTargetName == null){
                    bb.callTargetAddress = new Long(0);
                    bb.callTargetName = BasicBlock.INFO_UNKNOWN;
                }
                stmt.setLong(23, bb.callTargetAddress);
                stmt.setString(24, bb.callTargetName);
                if (bb.callTargetName.length() > INTERNAL_VARCHAR_LENGTH){
                    Logger.error("Database string length too short for function: " + bb.callTargetName);
                }
                stmt.setLong(25, bb.vaddr);
                stmt.setLong(26, bb.scatterGatherOps);
                stmt.setLong(27, bb.vectorMaskOps);
                stmt.addBatch();
            }

            tracedbTimerBegin();
            stmt.executeBatch();
            tracedbTimerEnd("block static insert");
            stmt.clearParameters();
            stmt.clearBatch();

            for( Iterator<BasicBlock> bit = blocks.iterator(); bit.hasNext(); ) {
                BasicBlock bb = bit.next();

                for( Iterator<Dud> it = bb.duds.iterator(); it.hasNext() ; ) {
                    Dud d = it.next();

                    if( !insertDud(bb.bbid, d) ) {
                        return false;
                    }
                }

                if(!insertVecs(bb.bbid, bb.vecs)) {
                    return false;
                }
            }

        

        } catch (SQLException e) {
            Logger.error(e, "Unable to insert basic block into trace database");
            return false;
        }

        return true;
    }

    private PreparedStatement insertDudStmt = null;
    public boolean insertDud(BlockID bbid, Dud d) {

        try {
            if( insertDudStmt == null ) {
                insertDudStmt = dbConnection.prepareStatement(
                    "INSERT INTO Duds VALUES (" +
                    "?, ?, ?, ?, ?, ?);");
            }
            PreparedStatement stmt = insertDudStmt;

            stmt.setLong(1, bbid.blockHash);
            stmt.setLong(2, bbid.imgHash);
            stmt.setInt(3, d.dist);
            stmt.setInt(4, d.fpcnt);
            stmt.setInt(5, d.intcnt);
            stmt.setInt(6, d.memcnt);

            stmt.executeUpdate();
            

        } catch (SQLException e) {
            Logger.error(e, "Unable to insert dud into trace database");
            return false;
        }
        return true;
    }

    private PreparedStatement insertVecsStmt = null;
    public boolean insertVecs(BlockID bbid, Collection<VecOps> v) {
        if(v.size() == 0) {
            return true;
        }
        try {
            if( insertVecsStmt == null ) {
                insertVecsStmt = dbConnection.prepareStatement(
                    "INSERT INTO VectorOps VALUES (" +
                    "?, ?, ?, ?, ?, ?);");
            }
            PreparedStatement stmt = insertVecsStmt;

            stmt.setLong(1, bbid.blockHash);
            stmt.setLong(2, bbid.imgHash);

            for(Iterator<VecOps> it = v.iterator(); it.hasNext(); ) {
                VecOps vecops = it.next();

                stmt.setInt(3, vecops.vectorLength);
                stmt.setInt(4, vecops.elementSize);
                stmt.setInt(5, ((Long)vecops.fpcnt).intValue());
                stmt.setInt(6, ((Long)vecops.intcnt).intValue());
                stmt.addBatch();
            }

            //tracedbTimerBegin();
            stmt.executeBatch();
            //tracedbTimerEnd("vecops insert");
            stmt.clearParameters();
            stmt.clearBatch();
        } catch (SQLException e) {
            Logger.error(e, "Unable to insert vecs into trace database");
            return false;
        }
        return true;
    }


    private PreparedStatement insertBlockVisitCountsStmt = null;
    public boolean insertBlockVisitCounts(int rank, Map<BlockID, Long> bbidToCount) {
        try {
            if( insertBlockVisitCountsStmt == null ) {
                insertBlockVisitCountsStmt = dbConnection.prepareStatement(
                    "INSERT INTO BlockVisitCounts VALUES (" +
                    "?, ?, ?, ?, ?, ?);");
            }
            PreparedStatement stmt = insertBlockVisitCountsStmt;

            for( Iterator<Map.Entry<BlockID, Long>> eit = bbidToCount.entrySet().iterator(); eit.hasNext(); ) {
                Map.Entry<BlockID, Long> e = eit.next();
                BlockID bbid = e.getKey();
                Long count = e.getValue();

                stmt.setLong(1, bbid.blockHash);
                stmt.setLong(2, bbid.imgHash);
                stmt.setInt(3, rank);
                stmt.setInt(4, 0);
                stmt.setLong(5, count);
                stmt.setLong(6, 0);

                stmt.addBatch();
            }

            tracedbTimerBegin();        
            stmt.executeBatch();
            tracedbTimerEnd("block visit counts rank " + rank);
            stmt.clearParameters();
            stmt.clearBatch();

        } catch (SQLException e) {
            Logger.error(e, "Unable to insert block visit count batch into trace database");
            return false;
        }
        return true;
    }

    public boolean insertBlockCountsPerThread(int rank, Map<BlockID, Map<Integer, Long>> bbidToCount) {
        try {
            if( insertBlockVisitCountsStmt == null ) {
                insertBlockVisitCountsStmt = dbConnection.prepareStatement(
                    "INSERT INTO BlockVisitCounts VALUES (" +
                    "?, ?, ?, ?, ?, ?);");
            }
            PreparedStatement stmt = insertBlockVisitCountsStmt;

            for( Iterator<Map.Entry<BlockID, Map<Integer, Long>>> eit = bbidToCount.entrySet().iterator(); eit.hasNext(); ) {
                Map.Entry<BlockID, Map<Integer, Long>> e = eit.next();
                BlockID bbid = e.getKey();
                Map<Integer, Long> threadToCounts = e.getValue();
                for(Iterator<Map.Entry<Integer, Long>> eit2 = threadToCounts.entrySet().iterator(); eit2.hasNext(); ) {
                    Map.Entry<Integer, Long> e2 = eit2.next();
                    Integer thread = e2.getKey();
                    Long count = e2.getValue();

                    stmt.setLong(1, bbid.blockHash);
                    stmt.setLong(2, bbid.imgHash);
                    stmt.setInt(3, rank);
                    stmt.setInt(4, thread);
                    stmt.setLong(5, count);
                    stmt.setLong(6, 0);

                    stmt.addBatch();
                }
            }

            tracedbTimerBegin();        
            stmt.executeBatch();
            tracedbTimerEnd("block visit counts rank " + rank);
            stmt.clearParameters();
            stmt.clearBatch();

        } catch (SQLException e) {
            Logger.error(e, "Unable to insert block visit count batch into trace database");
            return false;
        }
        return true;
    }

    private PreparedStatement insertLoopVisitCountsStmt = null;
    public boolean insertLoopVisitCounts(BlockID loopHead, int rank, Long visitCount) {

        try {
            if( insertLoopVisitCountsStmt == null ) {
                insertLoopVisitCountsStmt = dbConnection.prepareStatement(
                    "INSERT INTO LoopVisitCounts (loopHead, imgHash, rank, thread, loopVisitCount) Values (" +
                    "?, ?, ?, ?, ?);");
            }
            PreparedStatement stmt = insertLoopVisitCountsStmt;

            stmt.setLong(1, loopHead.blockHash);
            stmt.setLong(2, loopHead.imgHash);
            stmt.setInt(3, rank);
            stmt.setInt(4, 0);
            stmt.setLong(5, visitCount);

            stmt.executeUpdate();
        } catch (SQLException e) {
            Logger.error(e, "Unable to insert loop visit count into trace database");
            return false;
        }
        return true;
    }

    public boolean insertLoopVisitCounts(int rank, Map<BlockID, Map<Integer, Long>> visitCounts) {

        try {
            if( insertLoopVisitCountsStmt == null ) {
                insertLoopVisitCountsStmt = dbConnection.prepareStatement(
                    "INSERT INTO LoopVisitCounts (loopHead, imgHash, rank, thread, loopVisitCount) Values (" +
                    "?, ?, ?, ?, ?);");
            }
            PreparedStatement stmt = insertLoopVisitCountsStmt;

            for(Iterator<Map.Entry<BlockID, Map<Integer, Long>>> it = visitCounts.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<BlockID, Map<Integer, Long>> e = it.next();
                BlockID loopHead = e.getKey();
                Map<Integer, Long> threadCounts = e.getValue();

                for(Iterator<Map.Entry<Integer, Long>> tit = threadCounts.entrySet().iterator(); tit.hasNext(); ) {
                    Map.Entry<Integer, Long> te = tit.next();
                    Integer thread = te.getKey();
                    Long count = te.getValue();

                    stmt.setLong(1, loopHead.blockHash);
                    stmt.setLong(2, loopHead.imgHash);
                    stmt.setInt(3, rank);
                    stmt.setInt(4, thread);
                    stmt.setLong(5, count);
                    stmt.addBatch();
                }
            }

            stmt.executeBatch();
            stmt.clearParameters();
            stmt.clearBatch();
        } catch (SQLException e) {
            Logger.error(e, "Unable to insert loop visit count into trace database");
            return false;
        }
        return true;
    }

    private PreparedStatement insertAddressRangesStmt = null;
    public boolean insertAddressRanges(int rank, int thread, BlockID bbid, AddressRanges ranges) {

        try {
            if(insertAddressRangesStmt == null) {
                     insertAddressRangesStmt = dbConnection.prepareStatement(
                        "INSERT INTO AddressRanges VALUES (" +
                        "?, ?, ?, ?, ?, ?);");
            }

            PreparedStatement stmt = insertAddressRangesStmt;

            stmt.setLong(1, bbid.blockHash);
            stmt.setLong(2, bbid.imgHash);
            stmt.setInt(3, rank);
            stmt.setInt(4, thread);
            for(int i = 0; i < ranges.getNumberOfRanges(); ++i) {
                Long min = ranges.getMin(i);
                Long max = ranges.getMax(i);
                stmt.setLong(5, min);
                stmt.setLong(6, max);
                stmt.addBatch();
            }
            stmt.executeBatch();
            stmt.clearParameters();
            stmt.clearBatch();
        } catch (SQLException e) {
            Logger.error(e, "Unable to insert address ranges batch into trace database");
            return false;
        }
        return true;
    }

    // FIXME to handle threads
    private PreparedStatement insertBlockDfpDescriptorsStmt = null;
    public boolean insertAddressRanges(int rank, Map<BlockID, AddressRanges> blocks) {
        if( blocks.size() == 0 ) {
            return true;
        }

        try {
            if (insertBlockDfpDescriptorsStmt == null){
                insertBlockDfpDescriptorsStmt = dbConnection.prepareStatement(
                    "INSERT INTO BlockDfpDescriptors VALUES (" + 
                    "?, ?, ?, ?, ?);");
            }
            PreparedStatement bstmt = insertBlockDfpDescriptorsStmt;

            if (insertAddressRangesStmt == null){
                insertAddressRangesStmt = dbConnection.prepareStatement(
                    "INSERT INTO AddressRanges VALUES (" +
                    "?, ?, ?, ?, ?, ?);");
            }
            PreparedStatement astmt = insertAddressRangesStmt;

            for( Iterator<Map.Entry<BlockID, AddressRanges>> bit = blocks.entrySet().iterator(); bit.hasNext(); ) {
                Map.Entry<BlockID, AddressRanges> be = bit.next();
                BlockID bbid = be.getKey();
                AddressRanges ranges = be.getValue();

                String pattern = ranges.getPattern();
                bstmt.setLong(1, bbid.blockHash);
                bstmt.setLong(2, bbid.imgHash);
                bstmt.setInt(3, rank);
                bstmt.setInt(4, 0);
                bstmt.setString(5, pattern);
                if (pattern.length() > INTERNAL_VARCHAR_LENGTH){
                    Logger.error("Database string length too short for pattern: " + pattern);
                }

                bstmt.addBatch();

                for (int i = 0; i < ranges.getNumberOfRanges(); i++){
                    Long min = new Long(ranges.getMin(i));
                    Long max = new Long(ranges.getMax(i));

                    astmt.setLong(1, bbid.blockHash);
                    astmt.setLong(2, bbid.imgHash);
                    astmt.setInt(3, rank);
                    astmt.setInt(4, 0);
                    astmt.setLong(5, min);
                    astmt.setLong(6, max);

                    astmt.addBatch();
                }
            }

            tracedbTimerBegin();
            bstmt.executeBatch();
            tracedbTimerEnd("dfp descriptors of rank " + rank);
            bstmt.clearParameters();
            bstmt.clearBatch();

            tracedbTimerBegin();
            astmt.executeBatch();
            tracedbTimerEnd("address ranges of rank " + rank);
            astmt.clearParameters();
            astmt.clearBatch();
        } catch (SQLException e) {
            Logger.error(e, "Unable to insert address ranges batch into trace database");
            return false;
        }
        return true;
    }

    // FIXME copy/pasted from below -- eliminate duplicated code
    public boolean insertSimBlockVisitsPerThread(Map<Integer, Map<Integer, Map<BlockID, Long>>> ranksToVisits){
        if (ranksToVisits.size() == 0){
            return true;
        }
        try {

            // Retrieve exisint jbb visits
            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT * from BlockVisitCounts;"
            );
            tracedbTimerEnd("select block visits");

            // a map of visits that will be updated instead of inserted
            // initialize with a hashmap per rank
            Map<Integer, Map<Integer, Map<BlockID, Long>>> updateRanksToVisits = Util.newHashMap();
            for(Iterator<Integer> it = ranksToVisits.keySet().iterator(); it.hasNext(); ) {
                Integer rank = it.next();
                Map<Integer, Map<BlockID, Long>> threadVisits = Util.newHashMap();
                updateRanksToVisits.put(rank,  threadVisits);
            }

            // for each exising entry in BlockVisitCounts (from jbb)
            while( res.next() ) {
                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                BlockID bbid = new BlockID(imgHash, blockHash);
                Integer rank = res.getInt(3);
                Integer thread = res.getInt(4);
                Long jbbVisitCount = res.getLong(5);
                Long simVisitCount = res.getLong(6);


                // null if rank isn't being updated
                Map<Integer, Map<BlockID, Long>> threadsToVisits = ranksToVisits.get(rank);
                if(threadsToVisits == null) {
                    continue;
                }

                // null if thread isn't being updated
                Map<BlockID, Long> visits = threadsToVisits.get(thread);
                if(visits == null) {
                    continue;
                }

                // if this block is being updated, move it to updateVisits
                if(visits.containsKey(bbid)) {
                    Map<Integer, Map<BlockID, Long>> updateThreadsToVisits = updateRanksToVisits.get(rank);
                    Map<BlockID, Long> updateVisits = updateThreadsToVisits.get(thread);
                    if(updateVisits == null) {
                        updateVisits = Util.newHashMap();
                        updateThreadsToVisits.put(thread, updateVisits);
                    }
                    updateVisits.put(bbid, visits.get(bbid));
                    visits.remove(bbid);
                }
            }

            // now there should be no entries in visits
            // if there are insert them and print a warning
            PreparedStatement istmt = dbConnection.prepareStatement(
                    "INSERT INTO BlockVisitCounts VALUES (" +
                    "?, ?, ?, ?, ?, ?);");

            int entryCount = 0;
            for (Iterator<Integer> it = ranksToVisits.keySet().iterator(); it.hasNext(); ){
                Integer rank = it.next();
                Map<Integer, Map<BlockID, Long>> threadsToVisits = ranksToVisits.get(rank);

                for(Iterator<Integer> tit = threadsToVisits.keySet().iterator(); tit.hasNext(); ) {
                    Integer thread = tit.next();
                    Map<BlockID, Long> visits = threadsToVisits.get(thread);
                    for (Iterator<BlockID> bit = visits.keySet().iterator(); bit.hasNext(); ){
                        BlockID bbid = bit.next();
                        Long simVisitCount = visits.get(bbid);
    
                        istmt.setLong(1, bbid.blockHash);
                        istmt.setLong(2, bbid.imgHash);
                        istmt.setInt(3, rank);
                        istmt.setInt(4, thread);
                        istmt.setLong(5, 0);
                        istmt.setLong(6, simVisitCount);
                        Logger.warn("Block " + bbid + " in rank " + rank + " appears in siminst (" + simVisitCount + " executions) but not jbb");
    
                        istmt.addBatch();
                        entryCount++;
                    }
                }
            }

            if (entryCount > 0){
                tracedbTimerBegin();
                istmt.executeBatch();
                tracedbTimerEnd(entryCount + " insert with simulation visit counts");
                
                istmt.clearParameters();
                istmt.clearBatch();
            }

            // the remainder are updates instead of inserts
            PreparedStatement ustmt = dbConnection.prepareStatement(
                    "UPDATE BlockVisitCounts SET simVisitCount = ?" +
                    "WHERE bbid = ? " +
                    "AND imgHash = ? " +
                    "AND rank = ? " + 
                    "AND thread = ? " +";");

            entryCount = 0;
            for (Iterator<Integer> rit = updateRanksToVisits.keySet().iterator(); rit.hasNext(); ){
                Integer rank = rit.next();
                Map<Integer, Map<BlockID, Long>> updateThreadsToVisits = updateRanksToVisits.get(rank);

                for(Iterator<Integer> tit = updateThreadsToVisits.keySet().iterator(); tit.hasNext(); ) {
                    Integer thread = tit.next();
                    Map<BlockID, Long> updateVisits = updateThreadsToVisits.get(thread);

                    for (Iterator<BlockID> bit = updateVisits.keySet().iterator(); bit.hasNext(); ){
                        BlockID bbid = bit.next();
                        Long simVisitCount = updateVisits.get(bbid);
    
                        ustmt.setLong(1, simVisitCount);
                        ustmt.setLong(2, bbid.blockHash);
                        ustmt.setLong(3, bbid.imgHash);
                        ustmt.setInt(4, rank);
                        ustmt.setInt(5, thread);
                        ustmt.addBatch();
                        entryCount++;
                    }
                }
            }

            if (entryCount > 0){
                tracedbTimerBegin();
                ustmt.executeBatch();
                tracedbTimerEnd(entryCount + " update with simulation visit counts");
                
                ustmt.clearParameters();
                ustmt.clearBatch();
            }

        } catch (SQLException e) {
            Logger.error(e, "Unable to update sim visit counts batch into trace database");
            return false;
        }
        return true;
    }


    public boolean insertSimBlockVisits(Map<Integer, Map<BlockID, Long>> blockVisits){
        if (blockVisits.size() == 0){
            return true;
        }
        try {
            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT * from BlockVisitCounts;"
            );
            tracedbTimerEnd("select block visits");

            // initialize updateVisits for each rank
            Map<Integer, Map<BlockID, Long>> updateVisits = Util.newHashMap();
            for (Iterator<Integer> it = blockVisits.keySet().iterator(); it.hasNext(); ){
                Integer rank = it.next();
                Map<BlockID, Long> r = Util.newHashMap();
                updateVisits.put(rank, r);
            }

            // for each exising entry in BlockVisitCounts (from jbb)
            while( res.next() ) {
                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                BlockID bbid = new BlockID(imgHash, blockHash);
                Integer rank = res.getInt(3);
                Long jbbVisitCount = res.getLong(4);
                Long simVisitCount = res.getLong(5);

                Map<BlockID, Long> r = blockVisits.get(rank);
                Map<BlockID, Long> u = updateVisits.get(rank);

                // if the entry is being updated, move it to updateVisits
                if (r.containsKey(bbid)){
                    u.put(bbid, r.get(bbid));
                    r.remove(bbid);
                }
            }

            PreparedStatement istmt = dbConnection.prepareStatement(
                    "INSERT INTO BlockVisitCounts VALUES (" +
                    "?, ?, ?, ?, ?, ?);");

            int entryCount = 0;
            for (Iterator<Integer> it = blockVisits.keySet().iterator(); it.hasNext(); ){
                Integer rank = it.next();
                Map<BlockID, Long> r = blockVisits.get(rank);
                for (Iterator<BlockID> bit = r.keySet().iterator(); bit.hasNext(); ){
                    BlockID bbid = bit.next();
                    Long simVisitCount = r.get(bbid);

                    istmt.setLong(1, bbid.blockHash);
                    istmt.setLong(2, bbid.imgHash);
                    istmt.setInt(3, rank);
                    istmt.setInt(4, 0);
                    istmt.setLong(5, 0);
                    istmt.setLong(6, simVisitCount);
                    Logger.warn("Block " + bbid + " in rank " + rank + " appears in siminst (" + simVisitCount + " executions) but not jbb");

                    istmt.addBatch();
                    entryCount++;
                }
            }

            if (entryCount > 0){
                tracedbTimerBegin();
                istmt.executeBatch();
                tracedbTimerEnd(entryCount + " insert with simulation visit counts");
                
                istmt.clearParameters();
                istmt.clearBatch();
            }

            PreparedStatement ustmt = dbConnection.prepareStatement(
                    "UPDATE BlockVisitCounts SET simVisitCount = ?" +
                    "WHERE bbid = ? " +
                    "AND imgHash = ? " +
                    "AND rank = ? " + ";");

            entryCount = 0;
            for (Iterator it = updateVisits.keySet().iterator(); it.hasNext(); ){
                Integer rank = (Integer)it.next();
                Map<BlockID, Long> r = updateVisits.get(rank);
                for (Iterator bit = r.keySet().iterator(); bit.hasNext(); ){
                    BlockID bbid = (BlockID)bit.next();
                    Long simVisitCount = r.get(bbid);

                    ustmt.setLong(1, simVisitCount);
                    ustmt.setLong(2, bbid.blockHash);
                    ustmt.setLong(3, bbid.imgHash);
                    ustmt.setInt(4, rank);
                    ustmt.addBatch();
                    entryCount++;
                }
            }

            if (entryCount > 0){
                tracedbTimerBegin();
                ustmt.executeBatch();
                tracedbTimerEnd(entryCount + " update with simulation visit counts");
                
                ustmt.clearParameters();
                ustmt.clearBatch();
            }

        } catch (SQLException e) {
            Logger.error(e, "Unable to update sim visit counts batch into trace database");
            return false;
        }
        return true;
    }

    public boolean insertBlockCacheHitRates(int sysid, Map<Integer, Map<BlockID, CacheStats>> blocks){
        if( blocks.size() == 0 ) {
            return true;
        }
        try {
            Connection conn = sysidConnections.get(sysid);
            assert(conn != null);
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO BlockCacheHitRates VALUES (" +
                    "?, ?, ?, ?, ?, ?, ?, ?);");

            int entryCount = 0;
            for (Iterator<Integer> it = blocks.keySet().iterator(); it.hasNext(); ){
                Integer rank = it.next();
                Map<BlockID, CacheStats> r = blocks.get(rank);
                for (Iterator<BlockID> bit = r.keySet().iterator(); bit.hasNext(); ){
                    BlockID bbid = bit.next();
                    CacheStats c = r.get(bbid);

                    stmt.setLong(1, bbid.blockHash);
                    stmt.setLong(2, bbid.imgHash);
                    stmt.setInt(3, rank);
                    stmt.setInt(4, 0);
                    stmt.setLong(5, c.getHits(1));
                    stmt.setLong(6, c.getMisses(1));
                    stmt.setLong(7, c.getHits(2));
                    stmt.setLong(8, c.getHits(3));
                
                    stmt.addBatch();
                    entryCount++;
                }
            }

            tracedbTimerBegin();
            stmt.executeBatch();
            tracedbTimerEnd(entryCount + " cache hit rates for sysid " + sysid);

            stmt.clearParameters();
            stmt.clearBatch();

        } catch (SQLException e) {
            Logger.error(e, "Unable to insert block cache hit rates batch into trace database");
            return false;
        }
        return true;
    }

    public boolean insertBlockCacheHitRatesPerThread(int sysid, Map<Integer,  Map<Integer, Map<BlockID, CacheStats>>> rankBlocks){
        if( rankBlocks.size() == 0 ) {
            return true;
        }
        try {
            Connection conn = sysidConnections.get(sysid);
            assert(conn != null);
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO BlockCacheHitRates VALUES (" +
                    "?, ?, ?, ?, ?, ?, ?, ?);");

            int entryCount = 0;
            for (Iterator<Map.Entry<Integer, Map<Integer, Map<BlockID, CacheStats>>>> rit = rankBlocks.entrySet().iterator(); rit.hasNext(); ){
                Map.Entry<Integer, Map<Integer, Map<BlockID, CacheStats>>> re = rit.next();
                Integer rank = re.getKey();
                Map<Integer, Map<BlockID, CacheStats>> threadBlocks = re.getValue();

                for(Iterator<Map.Entry<Integer, Map<BlockID, CacheStats>>> tit = threadBlocks.entrySet().iterator(); tit.hasNext(); ){
                    Map.Entry<Integer, Map<BlockID, CacheStats>> te = tit.next();
                    Integer thread = te.getKey();
                    Map<BlockID, CacheStats> blocks = te.getValue();

                    for(Iterator<Map.Entry<BlockID, CacheStats>> bit = blocks.entrySet().iterator(); bit.hasNext(); ) {
                        Map.Entry<BlockID, CacheStats> be = bit.next();
                        BlockID bbid = be.getKey();
                        CacheStats c = be.getValue();

                        stmt.setLong(1, bbid.blockHash);
                        stmt.setLong(2, bbid.imgHash);
                        stmt.setInt(3, rank);
                        stmt.setInt(4, thread);
                        stmt.setLong(5, c.getHits(1));
                        stmt.setLong(6, c.getMisses(1));
                        if(c.getHits(2) == CacheStats.INVALID_COUNT) {
                            stmt.setNull(7, Types.BIGINT);
                        } else {
                            stmt.setLong(7, c.getHits(2));
                        }
                        if(c.getHits(3) == CacheStats.INVALID_COUNT) {
                            stmt.setNull(8, Types.BIGINT);
                        } else {
                            stmt.setLong(8, c.getHits(3));
                        }

                        stmt.addBatch();
                        entryCount++;
                    }
                }
            }

            tracedbTimerBegin();
            stmt.executeBatch();
            tracedbTimerEnd(entryCount + " cache hit rates for sysid " + sysid);

            stmt.clearParameters();
            stmt.clearBatch();

        } catch (SQLException e) {
            Logger.error(e, "Unable to insert block cache hit rates batch into trace database");
            return false;
        }
        return true;
    }

    private PreparedStatement insertFunctionStmt = null;
    public boolean insertFunctions(Collection<Function> functions) {
        if( functions.size() == 0 ) {
            return true;
        }

        try {
            if( insertFunctionStmt == null ) {
                insertFunctionStmt = dbConnection.prepareStatement(
                    "INSERT INTO Functions VALUES (" +
                    "?, ?, ?, ?, ?);");
            }
            PreparedStatement stmt = insertFunctionStmt;

            for( Iterator<Function> fit = functions.iterator(); fit.hasNext(); ) {
                Function f = fit.next();

                stmt.setLong(1, f.functionID.blockHash);
                stmt.setLong(2, f.functionID.imgHash);
                stmt.setString(3, f.functionName);
                if (f.functionName.length() > INTERNAL_VARCHAR_LENGTH){
                    Logger.error("Database string length too short for function: " + f.functionName);
                }
                stmt.setString(4, f.file);
                if (f.file.length() > INTERNAL_VARCHAR_LENGTH){
                    Logger.error("Database string length too short for file: " + f.file);
                }
                stmt.setInt(5, f.loopCount);

                stmt.addBatch();
            }

            tracedbTimerBegin();
            stmt.executeBatch();
            tracedbTimerEnd("function insert");
            stmt.clearParameters();
            stmt.clearBatch();
        } catch (SQLException e) {
            Logger.error(e, "Unable to insert function into trace database");
            return false;
        }
        return true;
    }

    private PreparedStatement insertLooistmt = null;
    public boolean insertLoops(Collection<Loop> loops) {
        if( loops.size() == 0 ) {
            return true;
        }

        try {
            if( insertLooistmt == null ) {
                insertLooistmt = dbConnection.prepareStatement(
                    "INSERT INTO Loops VALUES (" +
                    "?, ?, ?, ?);");
            }
            PreparedStatement stmt = insertLooistmt;

            for( Iterator<Loop> lit = loops.iterator(); lit.hasNext(); ) {
                Loop l = lit.next();

                stmt.setLong(1, l.headBlock.blockHash);
                stmt.setLong(2, l.headBlock.imgHash);
                stmt.setLong(3, l.parentHead.blockHash);
                stmt.setLong(4, l.depth);
                stmt.addBatch();
            }

            tracedbTimerBegin();
            stmt.executeBatch();
            tracedbTimerEnd("loop insert");
            stmt.clearParameters();
            stmt.clearBatch();
        } catch (SQLException e) {
            Logger.error(e, "Unable to insert loop into trace database");
            return false;
        }
        return true;
    }

    public boolean insertDistances(String table, int rank, long imgHash, int thread, Map<Long, List<Tuple.Tuple3<Long, Long, Long>>> blocks) {
        if(!table.equals("ReuseStats") && !table.equals("SpatialStats")) {
            Logger.error("insertDistances called for inappropriate table " + table);
            assert(false);
            return false;
        }

        if(blocks == null || blocks.size() == 0) {
            return true;
        }

        try {
            PreparedStatement stmt = dbConnection.prepareStatement(
                "INSERT INTO " + table + " VALUES (" +
                "?, ?, ?, ?, ?, ?, ?);");

            stmt.setLong(2, imgHash);
            stmt.setInt(3, rank);
            stmt.setInt(4, thread);

            for(Iterator<Long> it = blocks.keySet().iterator(); it.hasNext(); ) {
                Long blockHash = it.next();
                stmt.setLong(1, blockHash);
                List<Tuple.Tuple3<Long, Long, Long>> bins = blocks.get(blockHash);
                for(Iterator<Tuple.Tuple3<Long, Long, Long>> bit = bins.iterator(); bit.hasNext(); ) {
                    Tuple.Tuple3<Long, Long, Long> bin = bit.next();
                    Long lowerBound = bin.get1();
                    Long upperBound = bin.get2();
                    Long accesses = bin.get3();
                    stmt.setLong(5, lowerBound);
                    stmt.setLong(6, upperBound);
                    stmt.setLong(7, accesses);
                    stmt.addBatch();
                }
            }

            tracedbTimerBegin();
            stmt.executeBatch();
            tracedbTimerEnd("dist insert");
            stmt.clearParameters();
            stmt.clearBatch();

        } catch (SQLException e) {
            Logger.error(e, "Unable to insert reuse distances into trace database");
            return false;
        }
        return true;
    }

    public Map<Integer, Map<BlockID, List<BinnedCounter>>> getDistances(int rank, String table, Map<Integer, Map<BlockID, Long>> blockVisits, Map<BlockID, BasicBlock> blockStatic) {
        Map<Integer, Map<BlockID, Long>> memCounts = Util.newHashMap();
        for(Iterator<Integer> rit = blockVisits.keySet().iterator(); rit.hasNext(); ) {
            Integer r = rit.next();
            memCounts.put(r, getBlockMemOps(blockVisits.get(r), blockStatic));
        }
        return getDistances(rank, table, memCounts);
    }

    // Returns rank -> bbid -> BinnedCounter List
    public Map<Integer, Map<BlockID, List<BinnedCounter>>> getDistances(int rank, String table, Map<Integer, Map<BlockID, Long>> memCounts) {
        if(!table.equals("ReuseStats") && !table.equals("SpatialStats")) {
            Logger.error("getDistances called for inappropriate table " + table);
            assert(false);
            return null;
        }
        
        // BinnedCounters are in sorted order
        Map<Integer, Map<BlockID, List<BinnedCounter>>> allBins = Util.newHashMap();
        try {
            String rankFilt = makeRankFilt(rank);

            Statement stmt = dbConnection.createStatement();
            tracedbTimerBegin();
            ResultSet res = stmt.executeQuery(
                "SELECT bbid, " +
                "imgHash, " +
                "rank, " + 
                "lowerBound, " +
                "upperBound, " +
                "SUM(accesses) " +
                "FROM " + table +
                rankFilt +
                " GROUP BY bbid, imgHash, rank, lowerBound, upperBound" +
                " ORDER BY lowerBound, upperBound;"
            );
            tracedbTimerEnd("selecting distance stats per rank");

            while(res.next()) {
                Long blockHash = res.getLong(1);
                Long imgHash = res.getLong(2);
                BlockID bbid = new BlockID(imgHash, blockHash);
                Integer r = res.getInt(3);
                Long lowerBound = res.getLong(4);
                Long upperBound = res.getLong(5);
                Long accesses = res.getLong(6);
               
                Map<BlockID, List<BinnedCounter>> rankBins = allBins.get(r);
                if(rankBins == null) {
                    //Logger.inform("Adding dist bin for rank " + r + " in table " + table);
                    rankBins = Util.newHashMap();
                    allBins.put(r, rankBins);
                }
                List<BinnedCounter> blockBins = rankBins.get(bbid);
                if(blockBins == null) {
                    //Logger.inform("Adding bin for block " + bbid + " in table " + table);
                    blockBins = Util.newLinkedList();
                    rankBins.put(bbid, blockBins);
                }

                BinnedCounter counter = new BinnedCounter(lowerBound, upperBound, accesses);
                blockBins.add(counter);

            }

            // Correct for sampling
            // allBins : rank -> block -> counter list
            // for each rank
            for(Iterator<Map.Entry<Integer, Map<BlockID, List<BinnedCounter>>>> eit1 = allBins.entrySet().iterator(); eit1.hasNext(); ) {
                Map.Entry<Integer, Map<BlockID, List<BinnedCounter>>> ent = eit1.next();
                Integer r = ent.getKey();
                Map<BlockID, List<BinnedCounter>> rankBins = ent.getValue();
                Map<BlockID, Long> rankMemOps = memCounts.get(r);

                // for each block
                for(Iterator<Map.Entry<BlockID, List<BinnedCounter>>> eit2 = rankBins.entrySet().iterator(); eit2.hasNext(); ) {
                    Map.Entry<BlockID, List<BinnedCounter>> ent2 = eit2.next();
                    BlockID bbid = ent2.getKey();
                    List<BinnedCounter> blockBins = ent2.getValue();
                    // for each counter bin
                    Long totalBlockCounts = 0L;
                    for(Iterator<BinnedCounter> bit = blockBins.iterator(); bit.hasNext(); ) {
                        BinnedCounter bin = bit.next();
                        totalBlockCounts += bin.counter;
                    }
                    if(rankMemOps.containsKey(bbid) == false) {
                    Logger.error("siminst block id " + bbid + " was not found in jbbinst file. This is likely caused by non-deterministic behavior.");
                    assert(false);
                    return null;
						        }
                    Long blockMemOps = rankMemOps.get(bbid);
                    Double multiplier = blockMemOps.doubleValue() / totalBlockCounts.doubleValue();
 
                    // scale counts to actual memory counts
                    for(Iterator<BinnedCounter> bit = blockBins.iterator(); bit.hasNext(); ) {
                        BinnedCounter bin = bit.next();
                        bin.counter = ((Double)(bin.counter * multiplier)).longValue();
                    }
                }
            }

        } catch (SQLException e) {
            Logger.error(e, "Unable to query reuse stats from trace database");
            return null;
        }
        return allBins;

    }

    // rank -> bbid -> list of counters
    // rank == INVALID_RANK to return all ranks, otherwise, just one
    // SUMs across threads
    // List of BinnedCounters is ordered by lowerBound
    public Map<Integer, Map<BlockID, List<BinnedCounter>>> getRankReuseStats(int rank, Map<BlockID, Long> blockVisits, Map<BlockID, BasicBlock> blockStatic) {
        assert(rank != INVALID_RANK);
        Map<Integer, Map<BlockID, Long>> rankVisits = Util.newHashMap();
        rankVisits.put(rank, blockVisits);
        return getReuseStats(rank, rankVisits, blockStatic);
    }
    public Map<Integer, Map<BlockID, List<BinnedCounter>>> getReuseStats(int rank, Map<Integer, Map<BlockID, Long>> rankVisits, Map<BlockID, BasicBlock> blockStatic) {
        return getDistances(rank, "ReuseStats", rankVisits, blockStatic);
    }
    public Map<Integer, Map<BlockID, List<BinnedCounter>>> getRankSpatialStats(int rank, Map<BlockID, Long> blockVisits, Map<BlockID, BasicBlock> blockStatic) {
        assert(rank != INVALID_RANK);
        Map<Integer, Map<BlockID, Long>> rankVisits = Util.newHashMap();
        rankVisits.put(rank, blockVisits);
        return getSpatialStats(rank, rankVisits, blockStatic);
    }
    public Map<Integer, Map<BlockID, List<BinnedCounter>>> getSpatialStats(int rank, Map<Integer, Map<BlockID, Long>> rankVisits, Map<BlockID, BasicBlock> blockStatic) {
        return getDistances(rank, "SpatialStats", rankVisits, blockStatic);
    }
}
