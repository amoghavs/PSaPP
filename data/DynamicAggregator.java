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
import java.util.*;

public abstract class DynamicAggregator {
    // -- static info -- //
    public BlockID headBlock = null;
    public String functionName = null;
    public FunctionID functionID = null;
    public String file = null;
    public int line = 0;
    public int loopCount = 0;
    public int insns = 0;
    public int memOps = 0;
    public int fpOps = 0;
    public int intOps = 0;

    // -- dynamic info -- //
    public long visitCount = 0L; // Does this make sense?
    public long dInsns = 0L;
    public long dMemOps = 0L;
    public long dFpOps = 0L;
    public long dIntOps = 0L;
    public long dMembytes = 0L;
    public long entryCount = 0L;
    public long dLoads = 0L;
    public long dStores = 0L;
    public long dBranchOps = 0L;
    public long dScatterGatherOps = 0L;
    public long dVectorMaskOps = 0L;

    // record all blocks that have been put in here already
    public Set<BlockID> blocks = null;
    public Map<Integer, Set<BlockID>> cacheBlocks = null;

    // dist -> dyn exec count at that dist
    public Map<Integer, Long> intDuDistances = null;
    public Map<Integer, Long> fltDuDistances = null;
    public Map<Integer, Long> memDuDistances = null;

    public List<BinnedCounter> spatialStats = null;

    // sysid -> cache rates
    public Map<Integer, CacheStats> perSysCaches = null;

    // nElements -> elemBits -> VecOps
    public Map<Integer, Map<Integer, VecOps>> vecops = null;

    public DynamicAggregator(){
        clearDynamicStats();
    }

    public void clearDynamicStats() {
        visitCount = 0L;
        dInsns = 0L;
        dMemOps = 0L;
        dFpOps = 0L;
        dIntOps = 0L;
        dMembytes = 0L;
        dLoads = 0L;
        dStores = 0L;
        dBranchOps = 0L;
        dScatterGatherOps = 0L;
        dVectorMaskOps = 0L;
        entryCount = 0L;

        blocks = Util.newHashSet();
        cacheBlocks = Util.newHashMap();
        perSysCaches = Util.newHashMap();
        intDuDistances = Util.newHashMap();
        fltDuDistances = Util.newHashMap();
        memDuDistances = Util.newHashMap();
        vecops = Util.newHashMap();
        spatialStats = Util.newLinkedList();
    }

    public abstract String describe();

    
    static private Map<Integer, Long> scaleDuds(Map<Integer, Long> duds, Double scaleRatio) {
        Map<Integer, Long> retval = Util.newHashMap();
        for( Iterator<Map.Entry<Integer, Long>> it = duds.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> ent = it.next();
            Integer dist = ent.getKey();
            Long count = ent.getValue();
            retval.put(dist, ((Double)(count * scaleRatio)).longValue());
        }
        return retval;
    }

    // set fields in sdag to be partial counts from this dag
    public <T extends DynamicAggregator> void scaleAggregator(Double scaleRatio, T sdag) {

        // Copy static fields directly
        sdag.headBlock = headBlock;
        sdag.functionName = functionName;
        sdag.functionID = functionID;
        sdag.file = file;
        sdag.line = line;
        sdag.loopCount = loopCount;
        sdag.insns = insns;
        sdag.memOps = memOps;
        sdag.fpOps = fpOps;
        sdag.intOps = intOps;

        sdag.dInsns = ((Double)(dInsns * scaleRatio)).longValue();
        assert( sdag.dInsns >= 0 );

        sdag.dMemOps = ((Double)(dMemOps * scaleRatio)).longValue();
        sdag.dFpOps = ((Double)(dFpOps * scaleRatio)).longValue();
        sdag.dIntOps = ((Double)(dIntOps * scaleRatio)).longValue();
        sdag.dMembytes = ((Double)(dMembytes * scaleRatio)).longValue();
        sdag.dLoads = ((Double)(dLoads * scaleRatio)).longValue();
        sdag.dStores = ((Double)(dStores * scaleRatio)).longValue();
        sdag.dBranchOps = ((Double)(dBranchOps * scaleRatio)).longValue();
        sdag.dScatterGatherOps = ((Double)(dScatterGatherOps * scaleRatio)).longValue();
        sdag.dVectorMaskOps = ((Double)(dVectorMaskOps * scaleRatio)).longValue();

        // scale du distances
        sdag.intDuDistances = scaleDuds(intDuDistances, scaleRatio);
        sdag.fltDuDistances = scaleDuds(fltDuDistances, scaleRatio);
        sdag.memDuDistances = scaleDuds(memDuDistances, scaleRatio);

        // scale cache stats
        Map<Integer, CacheStats> caches = Util.newHashMap();
        for( Iterator<Map.Entry<Integer, CacheStats>> it = perSysCaches.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, CacheStats> ent = it.next();
            Integer sysid = ent.getKey();
            CacheStats cs = ent.getValue();

            if( cs.getLevels() == 0 ) {
                caches.put(sysid, new CacheStats());
                continue;
            }

            CacheStats scaledStats = new CacheStats(cs.getLevels());
            caches.put(sysid, scaledStats);
            for( int level = 1; level <= cs.getLevels(); ++level ) {
                if( !cs.hasLevel(level) ) {
                    continue;
                }
                long hitCount, missCount;
                if(cs.getHits(level) == CacheStats.INVALID_COUNT) {
                    hitCount = 0;
                } else {
                    hitCount = ((Double)(cs.getHits(level) * scaleRatio)).longValue();
                }
                if(cs.getMisses(level) == CacheStats.INVALID_COUNT) {
                    missCount = 0;
                } else {
                    missCount = ((Double)(cs.getMisses(level) * scaleRatio)).longValue();
                }
                scaledStats.addLevelCounts(level, hitCount, missCount);
            }
        }
        sdag.perSysCaches = caches;

        // scale vecops
        sdag.vecops = Util.newHashMap();
        for(Iterator<Map.Entry<Integer, Map<Integer, VecOps>>> nit = vecops.entrySet().iterator(); nit.hasNext(); ) {
            Map.Entry<Integer, Map<Integer, VecOps>> ent1 = nit.next();
            Integer nElem = ent1.getKey();
            Map<Integer, VecOps> vecops2 = ent1.getValue();
            Map<Integer, VecOps> scaledVecops2 = Util.newHashMap();
            sdag.vecops.put(nElem, scaledVecops2);
            for(Iterator<Map.Entry<Integer, VecOps>> sit = vecops2.entrySet().iterator(); sit.hasNext(); ) {
                Map.Entry<Integer, VecOps> ent2 = sit.next();
                Integer size = ent2.getKey();
                VecOps v = ent2.getValue();
                VecOps sv = new VecOps(nElem, size, ((Double)(v.fpcnt*scaleRatio)).longValue(), ((Double)(v.intcnt*scaleRatio)).longValue());
                scaledVecops2.put(size, sv);
            }
        }

        // scale spatial stats
        sdag.spatialStats = Util.newLinkedList();
        for(Iterator<BinnedCounter> it = spatialStats.iterator(); it.hasNext(); ) {
            BinnedCounter bc = it.next();
            sdag.spatialStats.add(new BinnedCounter(bc.lowerBound, bc.upperBound, ((Double)(bc.counter*scaleRatio)).longValue()));
        }
    }

    // Aggregate duds into oldduds
    private static void aggregateDuds(Map<Integer, Long> duds, Map<Integer, Long> oldduds) {
        for( Iterator<Map.Entry<Integer, Long>> it = duds.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> ent = it.next();
            Integer dist = ent.getKey();
            Long count = ent.getValue();

            Long oldcount = oldduds.get(dist);
            if( oldcount == null ) {
                oldduds.put(dist, count);
            } else {
                oldduds.put(dist, count + oldcount);
            }
        }
    }

    public <T extends DynamicAggregator> void aggregateAggregator(T dag) {

        // Aggregate counts
        assert( dInsns >= 0 );
        assert( dag.dInsns >= 0 );
        dInsns += dag.dInsns;
        assert( dInsns >= 0 );

        dMemOps += dag.dMemOps;
        dFpOps += dag.dFpOps;
        dIntOps += dag.dIntOps;
        dMembytes += dag.dMembytes;
        dLoads += dag.dLoads;
        dStores += dag.dStores;
        dBranchOps += dag.dBranchOps;
        dScatterGatherOps += dag.dScatterGatherOps;
        dVectorMaskOps += dag.dVectorMaskOps;

        // Aggregate block listing
        for (Iterator<BlockID> it = dag.blocks.iterator(); it.hasNext(); ){
            BlockID b = it.next();
            assert(blocks.contains(b) == false);
            blocks.add(b);
        }

        // Aggregate du distances
        aggregateDuds(dag.intDuDistances, this.intDuDistances);
        aggregateDuds(dag.fltDuDistances, this.fltDuDistances);
        aggregateDuds(dag.memDuDistances, this.memDuDistances);

        // Aggregate cache hits/misses
        for( Iterator<Map.Entry<Integer, CacheStats>> it = dag.perSysCaches.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, CacheStats> ent = it.next();
            Integer sysid = ent.getKey();
            CacheStats cs = ent.getValue();
            
            CacheStats myCs = perSysCaches.get(sysid);
            if( myCs == null ) {
                myCs = new CacheStats(cs.getLevels());
                perSysCaches.put(sysid, myCs);
            }
            for( int level = 1; level <= cs.getLevels(); ++level ) {
                if( !cs.hasLevel(level) ) {
                    continue;
                }
                long hits, misses;
                hits = cs.getHits(level);
                misses = cs.getMisses(level);
                if(hits == CacheStats.INVALID_COUNT) {
                    hits = 0;
                }
                if(misses == CacheStats.INVALID_COUNT) {
                    misses = 0;
                }
                myCs.addLevelCounts(level, hits, misses);
            }
        }

        // Aggregate vecops
        for(Iterator<Integer> nit = dag.vecops.keySet().iterator(); nit.hasNext(); ) {
            Integer numElem = nit.next();
            Map<Integer, VecOps> dv2 = dag.vecops.get(numElem);
            Map<Integer, VecOps> vecops2 = vecops.get(numElem);
            if(vecops2 == null) {
                vecops2 = Util.newHashMap();
                vecops.put(numElem, vecops2);
            }
            for(Iterator<Integer> sit = dv2.keySet().iterator(); sit.hasNext(); ) {
                Integer size = sit.next();
                VecOps dvec = dv2.get(size);
                VecOps vec = vecops2.get(size);
                if(vec == null) {
                    vec = new VecOps(dvec.vectorLength, dvec.elementSize, 0, 0);
                    vecops2.put(size, vec);
                }
                vec.fpcnt += dvec.fpcnt;
                vec.intcnt += dvec.intcnt;
            }
        }
        
        // aggregate spatial stats
        aggregateSpatialStats(dag.spatialStats);

    }

    public void aggregateBasicBlock(long visitCount, BasicBlock bb){
        this.blocks.add(bb.bbid);

        this.visitCount += visitCount;
        this.dInsns += (bb.insns * visitCount);
        this.dMemOps += (bb.memOps * visitCount);
        this.dFpOps += (bb.fpOps * visitCount);
        this.dIntOps += (bb.intOps * visitCount);
        this.dMembytes += (bb.memBytes * visitCount);
        this.dLoads += (bb.loadOps * visitCount);
        this.dStores += (bb.storeOps * visitCount);
        this.dBranchOps += (bb.branchOps * visitCount);
        this.dScatterGatherOps += (bb.scatterGatherOps * visitCount);
        this.dVectorMaskOps += (bb.vectorMaskOps * visitCount);

        if (bb.line < this.line){
            this.line = bb.line;
        }

        this.insns += bb.insns;
        this.memOps += bb.memOps;
        this.fpOps += bb.fpOps;
    }

    public void aggregateCacheStats(int sysid, CacheStats c, BlockID bbid){
        CacheStats s = this.perSysCaches.get(sysid);
        Set<BlockID> cb = this.cacheBlocks.get(sysid);
        if (s == null){
            s = new CacheStats();
            this.perSysCaches.put(sysid, s);

            assert(cb == null);
            cb = Util.newHashSet();
            this.cacheBlocks.put(sysid, cb);
        }

        assert(cb != null);
        cb.add(bbid);

        s.addCounts(c);
 
    }

    public void aggregateSpatialStats(List<BinnedCounter> counters) {
        if(counters == null) {
            return;
        }
        ListIterator<BinnedCounter> bins = spatialStats.listIterator();
        for(Iterator<BinnedCounter> it = counters.iterator(); it.hasNext(); ) {
            BinnedCounter bc = it.next();
            while(true) {
                if(!bins.hasNext()) {
                    bins.add(new BinnedCounter(bc.lowerBound, bc.upperBound, bc.counter));
                    break;
                }
                BinnedCounter here = bins.next();
                int res = bc.compareTo(here);
                if(res < 0) {
                    continue;
                } else if (res > 0) {
                    bins.set(new BinnedCounter(bc.lowerBound, bc.upperBound, bc.counter));
                    bins.add(here);
                    break;
                } else {
                    here.counter += bc.counter;
                    break;
                }
            }
        }
    }

    public void aggregateVecOps(long visitCount, Collection<VecOps> blockVecOps) {
        if(blockVecOps == null) {
            return;
        }

        for(Iterator<VecOps> it = blockVecOps.iterator(); it.hasNext(); ) {
            VecOps v = it.next();
            Integer nElem = v.vectorLength;
            Integer size = v.elementSize;
            Map<Integer, VecOps> vecops2 = vecops.get(nElem);
            if(vecops2 == null) {
                vecops2 = Util.newHashMap();
                vecops.put(nElem, vecops2);
            }
            VecOps curvec = vecops2.get(size);
            if(curvec == null) {
                curvec = new VecOps(nElem, size, 0, 0);
                vecops2.put(size, curvec);
            }
            curvec.intcnt += v.intcnt*visitCount;
            curvec.fpcnt += v.fpcnt*visitCount;
        }
    }

    public void aggregateDuds(long visitCount, Set<Dud> duds){
        if (duds == null){
            return;
        }

        for (Iterator it = duds.iterator(); it.hasNext(); ){
            Dud dud = (Dud)it.next();

            Integer d = dud.dist;
            if (intDuDistances.get(d) == null){
                intDuDistances.put(d, 0L);
            }
            intDuDistances.put(d, intDuDistances.get(d) + (dud.intcnt * visitCount));

            if (fltDuDistances.get(d) == null){
                fltDuDistances.put(d, 0L);
            }
            fltDuDistances.put(d, fltDuDistances.get(d) + (dud.fpcnt * visitCount));

            if (memDuDistances.get(d) == null){
                memDuDistances.put(d, 0L);
            }
            memDuDistances.put(d, memDuDistances.get(d) + (dud.memcnt * visitCount));
        }
    }

    public int getBlockCount(){
        if (blocks == null){
            return 0;
        }
        return blocks.size();
    }

    // deprecate
    public static double averageDuDist(Map<Integer, Long> dudist){
        long dtotal = 0L;
        long etotal = 0L;
        for (Iterator it = dudist.keySet().iterator(); it.hasNext(); ){
            Integer d = (Integer)it.next();
            Long e = dudist.get(d);
            dtotal += (d * e);
            etotal += e;
        }
        if (etotal == 0L){
            return 0.0;
        }
        return (double)dtotal / (double)etotal;
    }

}
