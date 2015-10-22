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
import java.io.*;
import java.util.*;



public class TraceReporter extends Reports {

    private final TestCase testCase;
    private final TraceDB tracedb;
    private final Set<Integer> sysids;

    public TraceReporter(TestCase testCase, String processedDir, TraceDB tracedb, Set<Integer> sysids) {
        super(testCase, processedDir);
        this.testCase = testCase;
        this.tracedb = tracedb;
        this.sysids = sysids;
    }

    /* Options are a sequence of report names
     *  block sysid rank
     *
     */
    public boolean writeReports(Set<String> flags) {

        Vector<ReportWriter> writers = Util.newVector(5);

        if (flags.contains("block") || flags.contains("all")){
            if( !blockReports(writers) || !Reports.executeWriters(writers) ) {
                Logger.warn("Unable to write block reports\n");
                return false;
            }
            writers.clear();
        }

        if (flags.contains("dud") || flags.contains("all")){
            if( !dudReports(writers) || !Reports.executeWriters(writers) ) {
                Logger.warn("Unable to write dud reports\n");
                return false;
            }
            writers.clear();
        }

        if (flags.contains("gpu") || flags.contains("all")){
            if( !gpuReports(writers) || !Reports.executeWriters(writers) ) {
                Logger.warn("Unable to write gpu reports\n");
                return false;
            }
            writers.clear();
        }

        if (flags.contains("trace") || flags.contains("all")){
            if( !systemReports(writers) ) {
                Logger.warn("Unable to write system reports\n");
                return false;
            }
        }

        if (flags.contains("summary") || flags.contains("all")){
            if( !systemSummaries(writers) || !Reports.executeWriters(writers) ) {
                Logger.warn("Unable to write system summaries\n");
                return false;
            }
        }

        if (flags.contains("instruction") || flags.contains("all")){
            if( !instructionReports(writers) || !Reports.executeWriters(writers) ) {
                Logger.warn("Unable to write instruction reports\n");
                return false;
            }
        }

        if (flags.contains("blockvector") || flags.contains("all")){
            if( !blockVectorReports(writers) || !Reports.executeWriters(writers) ) {
                Logger.warn("Unable to write block vector reports\n");
                return false;
            }
        }

        if (flags.contains("functionvector") || flags.contains("all")){
            if( !functionVectorReports(writers) || !Reports.executeWriters(writers) ) {
                Logger.warn("Unable to write function vector reports\n");
                return false;
            }
        }

        if (flags.contains("vectorization") || flags.contains("all")){
            if(!vectorizationReports(writers) || !Reports.executeWriters(writers)) {
                Logger.warn("Unable to write vectorization reports\n");
                return false;
            }
        }

        if (flags.contains("loopview")){
            if(!loopViewReports(writers) || !Reports.executeWriters(writers)) {
                Logger.warn("Unable to write loop view reports\n");
                return false;
            }
        }
        if (flags.contains("scattergather")){
            if(!scatterGatherReports(writers) || !Reports.executeWriters(writers)) {
                Logger.warn("Unable to write scatter gather reports\n");
                return false;
            }
        }
        if (flags.contains("sourcelines")){
            if(!sourceLinesReports(writers) || !Reports.executeWriters(writers)) {
                Logger.warn("Unable to write source lines reports\n");
                return false;
            }
        }

        if (flags.contains("functioncalls")){
            if(!functionCallsReports(writers) || !Reports.executeWriters(writers)) {
                Logger.warn("Unable to write function call reports\n");
                return false;
            }
        }

        if (flags.contains("addressranges")) {
            if(!addressRangeReports(writers) || !Reports.executeWriters(writers)) {
                Logger.warn("Unable to write address range reports\n");
                return false;
            }
        }

        return true;
    }
    
    private boolean functionCallsReports(Vector<ReportWriter> writers) {
        for(int rank = 0; rank < testCase.getCpu(); ++rank) {
            PSaPP.cfg.FlowGraph flowGraph = new PSaPP.cfg.FlowGraph(tracedb, -1, rank);
            writers.add(functionCalls(flowGraph, rank));
        }
        return true;
    }

    private boolean scatterGatherReports(Vector<ReportWriter> writers) {
        for(int rank = 0; rank < testCase.getCpu(); ++rank) {
            PSaPP.cfg.FlowGraph flowGraph = new PSaPP.cfg.FlowGraph(tracedb, -1, rank);
            writers.add(scattergather(flowGraph, rank));
        }
        return true;
    }
    private boolean sourceLinesReports(Vector<ReportWriter> writers) {
            PSaPP.cfg.FlowGraph flowGraph = new PSaPP.cfg.FlowGraph(tracedb, -1, TraceDB.INVALID_RANK);
            writers.add(sourceLines(flowGraph, TraceDB.INVALID_RANK));
        return true;
    }

    private boolean loopViewReports(Vector<ReportWriter> writers) {
        for(int rank = 0; rank < testCase.getCpu(); ++rank) {
            PSaPP.cfg.FlowGraph flowGraph = new PSaPP.cfg.FlowGraph(tracedb, -1, rank);
            writers.add(loopView(flowGraph, rank));
        }
        return true;
    }
    private boolean addressRangeReports(Vector<ReportWriter> writers) {

        for(Iterator<Integer> sit = sysids.iterator(); sit.hasNext(); ) {
            Integer sysid = sit.next();
            for(int rank = 0; rank < testCase.getCpu(); ++rank) {
                Map<BlockID, BasicBlock> blockStatic = tracedb.getAllBlocks();
                Map<BlockID, Map<Integer, AddressRanges>> addressRanges = tracedb.getAllAddressRanges(rank);
                Map<BlockID, Map<Integer, Long>> rankVisits = tracedb.getBlockVisitsPerThread(rank).get(rank);
                Map<BlockID, Map<Integer, Long>> mems = tracedb.getBlockMemsPerThread(rankVisits, blockStatic);
                Map<BlockID, Map<Integer, CacheStats>> cacheStats = tracedb.getCacheStatsPerThread(sysid, rank, mems);
                writers.add(addressRanges(rank, addressRanges, rankVisits, cacheStats));
            }
        }
        return true;
    }

    private boolean vectorizationReports(Vector<ReportWriter> writers) {
        Map<BlockID, BasicBlock> blocks = tracedb.getAllBlocks();
        Map<BlockID, Collection<VecOps>> vecops = tracedb.getAllVecOps();
        for(int rank = 0; rank < testCase.getCpu(); ++rank) {
            Map<BlockID, Loop> loops = Util.newHashMap();
            tracedb.getStaticStats(blocks, null, loops);
            tracedb.getDynamicStats(blocks, null, loops, TraceDB.NO_SYSID, rank);
            writers.add(vectorization(rank, blocks, loops.values(), vecops));
        }
        return true;
    }

    private boolean functionVectorReports(Vector<ReportWriter> writers) {
        Map<BlockID, BasicBlock> blockStatic = tracedb.getAllBlocks();
        Map<BlockID, Set<Dud>> duds = tracedb.getAllDuds();
       
        for(Iterator<Integer> sit = sysids.iterator(); sit.hasNext(); ) {
            Integer sysid = sit.next();

            for(int rank = 0; rank < testCase.getCpu(); ++rank) {
                Map<BlockID, Long> blockVisits = tracedb.getBlockVisits(rank).get(rank);
                Map<BlockID, Long> loopVisits = tracedb.getLoopVisits(rank).get(rank);
                Map<BlockID, Map<Integer, AddressRanges>> addressRanges = tracedb.getAllAddressRanges(rank);
                Map<BlockID, List<BinnedCounter>> reuseStats = tracedb.getRankReuseStats(rank, blockVisits, blockStatic).get(rank);
                Map<BlockID, List<BinnedCounter>> spatialStats = tracedb.getRankSpatialStats(rank, blockVisits, blockStatic).get(rank);
                Map<BlockID, CacheStats> cacheStats = tracedb.getAverageHitRates(sysid, rank, blockVisits, blockStatic);

                // create bins of blocks by function
                Map<BlockID, Set<BlockID>> agg = Util.newHashMap();
                Map<BlockID, Collection<VecOps>> vecops = tracedb.getAllVecOps();

                for(Iterator<BlockID> bit = blockStatic.keySet().iterator(); bit.hasNext(); ) {
                    BlockID instruction = bit.next();
                    BlockID function = instruction.functionID();

                    Set<BlockID> instructions = agg.get(function);
                    if(instructions == null) {
                        instructions = Util.newHashSet();
                        agg.put(function, instructions);
                    }

                    instructions.add(instruction);
                }
                Logger.inform("Reporting on " + agg.size() + " agg groups");

                writers.add(vector(rank, sysid, blockStatic, duds, blockVisits, loopVisits, addressRanges, reuseStats, spatialStats, cacheStats, agg, "function", vecops));
            }
        }
        return true;
    }

    private boolean blockVectorReports(Vector<ReportWriter> writers) {


        Map<BlockID, BasicBlock> blockStatic = tracedb.getAllBlocks();
        Map<BlockID, Set<Dud>> duds = tracedb.getAllDuds();

        for(Iterator<Integer> sit = sysids.iterator(); sit.hasNext(); ) {
            Integer sysid = sit.next();

            for(int rank = 0; rank < testCase.getCpu(); ++rank) {
                Map<BlockID, Long> blockVisits = tracedb.getBlockVisits(rank).get(rank);
                Map<BlockID, Long> loopVisits = tracedb.getLoopVisits(rank).get(rank);
                Map<BlockID, Map<Integer, AddressRanges>> addressRanges = tracedb.getAllAddressRanges(rank);
                Map<BlockID, List<BinnedCounter>> reuseStats = tracedb.getRankReuseStats(rank, blockVisits, blockStatic).get(rank);
                Map<BlockID, List<BinnedCounter>> spatialStats = tracedb.getRankSpatialStats(rank, blockVisits, blockStatic).get(rank);
                Map<BlockID, CacheStats> cacheStats = tracedb.getAverageHitRates(sysid, rank, blockVisits, blockStatic);

                Map<BlockID, Set<BlockID>> agg = Util.newHashMap();
                Map<BlockID, Collection<VecOps>> vecops = tracedb.getAllVecOps();

                for(Iterator<BlockID> bit = blockStatic.keySet().iterator(); bit.hasNext(); ) {
                    BlockID instruction = bit.next();
                    BlockID block = instruction.blockID();

                    Set<BlockID> instructions = agg.get(block);
                    if(instructions == null) {
                        instructions = Util.newHashSet();
                        agg.put(block, instructions);
                    }

                    instructions.add(instruction);
                }
                Logger.inform("Reporting on " + agg.size() + " agg groups");
                

                writers.add(vector(rank, sysid, blockStatic, duds, blockVisits, loopVisits, addressRanges, reuseStats, spatialStats, cacheStats, agg, "block", vecops));
            }
        }
        return true;

    }

    private boolean instructionReports(Vector<ReportWriter> writers) {

        Map<BlockID, BasicBlock> blockStatic = tracedb.getAllBlocks();
        Map<BlockID, Set<Dud>> duds = tracedb.getAllDuds();

        for(Iterator<Integer> sit = sysids.iterator(); sit.hasNext(); ) {
            Integer sysid = sit.next();

            for(int rank = 0; rank < testCase.getCpu(); ++rank) {
                Map<BlockID, Long> blockVisits = tracedb.getBlockVisits(rank).get(rank);
                Map<BlockID, Long> loopVisits = tracedb.getLoopVisits(rank).get(rank);
                Map<BlockID, Map<Integer, AddressRanges>> addressRanges = tracedb.getAllAddressRanges(rank);
                Map<BlockID, List<BinnedCounter>> reuseStats = tracedb.getRankReuseStats(rank, blockVisits, blockStatic).get(rank);
                Map<BlockID, List<BinnedCounter>> spatialStats = tracedb.getRankSpatialStats(rank, blockVisits, blockStatic).get(rank);
                Map<BlockID, CacheStats> cacheStats = tracedb.getAverageHitRates(sysid, rank, blockVisits, blockStatic);
                writers.add(instructions(rank, sysid, blockStatic, duds, blockVisits, loopVisits, addressRanges, reuseStats, spatialStats, cacheStats));
            }
        }
        return true;
    }

    private boolean blockReports(Vector<ReportWriter> writers) {
        // First we will write reports that just need the info for all the basic blocks
        final Map<BlockID, BasicBlock> b = tracedb.getAllBlocks();
        if( b == null ) {
            return false;
        }
        final Collection<BasicBlock> blocks = b.values();

        // Create commands to write reports
        writers.add(bb2func(blocks));
        writers.add(bbbytes(blocks));
        writers.add(cnt(blocks));
        writers.add(dxi(blocks));
        writers.add(static_(blocks));
        writers.add(lp(blocks));
        writers.add(ipa(blocks));
        return true;
    }

    private boolean dudReports(Vector<ReportWriter> writers) {
        final Map<BlockID, Set<Dud>> bbidToDuds = tracedb.getAllDuds();
        if( bbidToDuds == null ) {
            return false;
        }
        writers.add(dud(bbidToDuds));
        return true;
    }

    private boolean gpuReports(Vector<ReportWriter> writers) {
        final Set<Function> funcs = tracedb.getFunctionSummaryGPU();
        if( funcs == null ) {
            return false;
        }
        writers.add(gpu(funcs));
        return true;
    }

    private boolean systemSummaries(Vector<ReportWriter> writers) {
        final Map<Integer, Integer> sysidLevels = tracedb.getSysidLevels();
        
        final Map<FunctionID, Function> funcs = Util.newHashMap();
        final Map<BlockID, Loop> loops = Util.newHashMap();

        tracedb.getDynamicAggregateStats(funcs, loops);

        writers.addAll(summaries(sysidLevels.keySet(), funcs.values()));
        writers.addAll(loops(sysidLevels.keySet(), loops.values()));

        return true;
    }

    private boolean systemReports(Vector<ReportWriter> writers) {
        final Map<Integer, Integer> sysidLevels = tracedb.getSysidLevels();

        for( Iterator<Integer> sit = sysidLevels.keySet().iterator(); sit.hasNext(); ) {
            int sysid = sit.next();
            for( int rank = 0; rank < testCase.getCpu(); ++rank ) {
                Map<BlockID, Tuple.Tuple2<Long, Long>> blockStats = tracedb.getDynamicBlockCounts(rank);
                Set<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> blocks = tracedb.getSimulatedBlocks(sysid, rank, testCase.getCpu(), blockStats);
                writers.add(sysidByRank(sysid, rank, blocks));
                Reports.executeWriters(writers);
                writers.clear();
            }
        }

        return true;
    }

/*
    private boolean doMicReport(Vector<ReportWriter> writers) {

        int rank = 0;
        int sysid = 77;

        Map<BlockID, BasicBlock> blocks = tracedb.getAllBlocks();
        Map<BlockID, Loop> loops = Util.newHashMap();
        tracedb.getStaticStats(blocks, null, loops);
        tracedb.getDynamicStats(blocks, null, loops, sysid, rank);
        Map<BlockID, Map<Integer, AddressRanges>> rangeInfo = tracedb.getAllAddressRanges(rank);

        // Get per block hit counts
        Map<Integer, Map<BlockID, Long>> allRankBlockVisits = tracedb.getBlockVisits(rank);
        Map<BlockID, Long> blockVisits = allRankBlockVisits.get(rank);

        Map<Integer, Map<BlockID, Long>> allMemCounts = Util.newHashMap();
        Map<BlockID, Long> blockMems = Util.newHashMap();
        allMemCounts.put(rank, blockMems);

        for (Iterator<BlockID> bit = blockVisits.keySet().iterator(); bit.hasNext(); ){
            BlockID bbid = bit.next();
            Long visits = blockVisits.get(bbid);

            BasicBlock bb = blocks.get(bbid);
            Long dynMemOps = visits * bb.memOps;

            blockMems.put(bbid, dynMemOps);
        }
            Map<BlockID, Map<Integer, AddressRanges>> addressRanges = tracedb.getAllAddressRanges(rank);
            Map<BlockID, List<BinnedCounter>> reuseStats = tracedb.getRankReuseStats(rank).get(rank);
            Map<BlockID, List<BinnedCounter>> spatialStats = tracedb.getRankSpatialStats(rank).get(rank);
        Map<Integer, Map<BlockID, CacheStats>> allHitRates = tracedb.getAverageHitRates(sysid, rank, allMemCounts);
        Map<BlockID, CacheStats> hitRates = allHitRates.get(rank);

        writers.add(micReport(blocks, hitRates, loops, rangeInfo));
        return true;
    }
   */
}

