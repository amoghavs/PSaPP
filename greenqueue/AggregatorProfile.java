package PSaPP.greenqueue;

import java.util.*;

import PSaPP.data.*;
import PSaPP.util.*;


public class AggregatorProfile extends EnergyProfile {

    // Flow Element Profile Methods
    static final String ALL_PROPERTIES =
        "file line " +
        "dinsns dintops dmemops dloads dstores dfpops dbranchops dscattergatherops dvectormaskops " +
        "pintops pmemops ploads pstores pfpops pbranchops pscattergatherops pvectormaskops " +
        "l1hr l2hr l3hr l1m l2m l3m l1mpi l2mpi l3mpi " +
        "idu fdu idu2 fdu2 mdu2 fprat bytespermop " +
        "pvec512 pvec256 pvec128 pvec64 pvec32 pvecother pvecunknown pvecunknown32 pvecunknown64 vecintensity vecutilization " +
        "dvec512 dvec256 dvec128 dvec64 dvec32 dvecother dvecunknown dvecunknown32 dvecunknown64 velementsactive " +
        "spatial spat0 spat2 spat4 spat8 spat16 spat32 spat64 spat128 spatOther";

    private Map<String, Object> properties = Util.newHashMap();
    public Object getProperty(String id) {
        Object result = properties.get(id);
        return properties.get(id);
    }

    public void setProperty(String id, Object prop) {
        properties.put(id, prop);
    }

    public AggregatorProfile(DynamicAggregator agg, Integer sysid) {

        setProperty("file", agg.file);
        setProperty("line", agg.line);

        Long l1hit, l2hit, l3hit;
        CacheStats cs = agg.perSysCaches.get(sysid);
        if( cs != null ) {
            long l1m = cs.getMisses(1);
            long l2m = cs.getMisses(2);
            long l3m = cs.getMisses(3);
            if(l1m == CacheStats.INVALID_COUNT) {
                l1m = 0;
            }
            if(l2m == CacheStats.INVALID_COUNT) {
                l2m = 0;
            }
            if(l3m == CacheStats.INVALID_COUNT) {
                l3m = 0;
            }
            setProperty("l1m", l1m);
            setProperty("l2m", l2m);
            setProperty("l3m", l3m);
            this.l1Misses = l1m;
            this.l2Misses = l2m;
            this.l3Misses = l3m;

            l1hit = cs.getHits(1);
            l2hit = cs.getHits(2);
            l3hit = cs.getHits(3);
            if(l1hit == CacheStats.INVALID_COUNT) {
                l1hit = 0L;
            }
            if(l2hit == CacheStats.INVALID_COUNT) {
                l2hit = 0L;
            }
            if(l3hit == CacheStats.INVALID_COUNT) {
                l3hit = 0L;
            }

        } else {
            setProperty("l1m", 0L);
            setProperty("l2m", 0L);
            setProperty("l3m", 0L);
            this.l1Misses = 0L;
            this.l2Misses = 0L;
            this.l3Misses = 0L;

            l1hit = 0L;
            l2hit = 0L;
            l3hit = 0L;
        }


        Double l1hr = l1hit.doubleValue() / ((Long)(l1hit+l1Misses)).doubleValue() * 100.0;
        Double l2hr = ((Long)(l1hit+l2hit)).doubleValue() / ((Long)(l1hit+l1Misses)).doubleValue() * 100.0;
        Double l3hr = ((Long)(l1hit+l2hit+l3hit)).doubleValue() / ((Long)(l1hit+l1Misses)).doubleValue() * 100.0;

        if(l1hr.isNaN()) {
            l1hr = 0.0;
        }
        if(l2hr.isNaN()) {
            l2hr = 0.0;
        }
        if(l3hr.isNaN()) {
            l3hr = 0.0;
        }
        setProperty("l1hr", l1hr);
        setProperty("l2hr", l2hr);
        setProperty("l3hr", l3hr);

        setProperty("dinsns", agg.dInsns);
        setProperty("dintops", agg.dIntOps);
        setProperty("dmemops", agg.dMemOps);
        setProperty("dloads", agg.dLoads);
        setProperty("dstores", agg.dStores);
        setProperty("dfpops", agg.dFpOps);
        setProperty("dbranchops", agg.dBranchOps);
        setProperty("dscattergatherops", agg.dScatterGatherOps);
        setProperty("dvectormaskops", agg.dVectorMaskOps);

        setProperty("pintops", 100.0 * agg.dIntOps / agg.dInsns);
        setProperty("pmemops", 100.0 * agg.dMemOps / agg.dInsns);
        setProperty("ploads",  100.0 * agg.dLoads  / agg.dInsns);
        setProperty("pstores", 100.0 * agg.dStores / agg.dInsns);
        setProperty("pfpops",  100.0 * agg.dFpOps  / agg.dInsns);
        setProperty("pbranchops", 100.0 * agg.dBranchOps / agg.dInsns);
        setProperty("pscattergatherops", 100.0 * agg.dScatterGatherOps / agg.dInsns);
        setProperty("pvectormaskops", 100.0 * agg.dVectorMaskOps / agg.dInsns);

        this.dFpOps = agg.dFpOps;
        this.dMemOps = agg.dMemOps;
        this.dInsns = agg.dInsns;

        this.l1MPI = this.l1MissesPerInst();
        this.l2MPI = this.l2MissesPerInst();
        this.l3MPI = this.l3MissesPerInst();

        setProperty("l1mpi", this.l1MPI);
        setProperty("l2mpi", this.l2MPI);
        setProperty("l3mpi", this.l3MPI);

        this.idu = sumDuds(agg.intDuDistances).doubleValue() / (double)this.dInsns;
        this.fdu = sumDuds(agg.fltDuDistances).doubleValue() / (double)this.dInsns;

        setProperty("idu", this.idu);
        setProperty("fdu", this.fdu);

        Double idu2 = computeDuds(agg.intDuDistances, this.dInsns);
        Double fdu2 = computeDuds(agg.fltDuDistances, this.dInsns);
        Double mdu2 = computeDuds(agg.memDuDistances, this.dInsns);
        setProperty("idu2", idu2);
        setProperty("fdu2", fdu2);
        setProperty("mdu2", mdu2);

        Double fprat = 0.0;
        if(agg.dFpOps == 0L) {
            fprat = 1.0 / 16.0;
        } else if(agg.dMemOps == 0L) {
            fprat = 16.0;
        } else {
            fprat = ((Long)agg.dFpOps).doubleValue() / ((Long)agg.dMemOps).doubleValue();
        }
        setProperty("fprat", fprat);

        // filename, line, function
        // bytesPerMemop, Reuse, Spatial, LowAddr, HighAddr, Range

        Double bytesPerMop = ((Long)agg.dMembytes).doubleValue() / ((Long)agg.dMemOps).doubleValue();
        setProperty("bytespermop", bytesPerMop);

        // Compute Vectorization
        Double dInsns = ((Long)(agg.dInsns)).doubleValue();
        Long n512 = 0L;
        Long n256 = 0L;
        Long n128 = 0L;
        Long n64  = 0L;
        Long n32  = 0L;
        Long nother = 0L;
        Long nUnknown32 = 0L;
        Long nUnknown64 = 0L;
        Long nUnknownX = 0L;
        Long vecElements = 0L;
        Long vecInstructions = 0L;
        Long bitsActive = 0L;
        for(Iterator<Map.Entry<Integer, Map<Integer, VecOps>>> nit = agg.vecops.entrySet().iterator(); nit.hasNext(); ) {
            Map.Entry<Integer, Map<Integer, VecOps>> ent = nit.next();
            Integer nElem = ent.getKey();
            Map<Integer, VecOps> vecops2 = ent.getValue();
            for(Iterator<Map.Entry<Integer, VecOps>> sit = vecops2.entrySet().iterator(); sit.hasNext(); ) {
                Map.Entry<Integer, VecOps> ent2 = sit.next();
                Integer size = ent2.getKey();
                VecOps v = ent2.getValue();

                if(nElem.equals(VecOps.UNKNOWN)) {
                    if(size.equals(32)) {
                        nUnknown32 += v.intcnt + v.fpcnt;
                    } else if(size.equals(64)) {
                        nUnknown64 += v.intcnt + v.fpcnt;
                    } else {
                        nUnknownX += v.intcnt + v.fpcnt;
                    }
                } else {
                    vecElements += (v.intcnt + v.fpcnt) * nElem;
                    bitsActive += size*nElem * (v.intcnt + v.fpcnt);
                    vecInstructions += v.intcnt + v.fpcnt;
                    switch(size*nElem) {
                        case 512: n512 += v.intcnt + v.fpcnt; break;
                        case 256: n256 += v.intcnt + v.fpcnt; break;
                        case 128: n128 += v.intcnt + v.fpcnt; break;
                        case 64:  n64  += v.intcnt + v.fpcnt; break;
                        case 32:  n32  += v.intcnt + v.fpcnt; break;
                        default: nother += v.intcnt + v.fpcnt; break;
                    }
                }
            }

        }

        Double vecutilization = bitsActive.doubleValue() / vecInstructions.doubleValue();
        Double vecintensity = vecElements.doubleValue() / vecInstructions.doubleValue();
        if(vecintensity.isNaN()) {
            vecintensity = 1.0;
        }
        Double pvec512 = 100.0 * n512 / dInsns;
        Double pvec256 = 100.0 * n256 / dInsns;
        Double pvec128 = 100.0 * n128 / dInsns;
        Double pvec64  = 100.0 * n64  / dInsns;
        Double pvec32  = 100.0 * n32  / dInsns;
        Double pvecother = 100.0 * nother / dInsns;
        Double pvecUnknown = 100.0 * nUnknownX / dInsns;
        Double pvecUnknown32 = 100.0 * nUnknown32 / dInsns;
        Double pvecUnknown64 = 100.0 * nUnknown64 / dInsns;

        setProperty("vecutilization", vecutilization);
        setProperty("vecintensity", vecintensity);
        setProperty("pvec512", pvec512);
        setProperty("pvec256", pvec256);
        setProperty("pvec128", pvec128);
        setProperty("pvec64",  pvec64);
        setProperty("pvec32",  pvec32);
        setProperty("pvecother", pvecother);
        setProperty("pvecunknown", pvecUnknown);
        setProperty("pvecunknown32", pvecUnknown32);
        setProperty("pvecunknown64", pvecUnknown64);
        setProperty("velementsactive", vecElements);

        setProperty("dvec512", n512);
        setProperty("dvec256", n256);
        setProperty("dvec128", n128);
        setProperty("dvec64",  n64);
        setProperty("dvec32",  n32);
        setProperty("dvecother", nother);
        setProperty("dvecunknown", nUnknownX);
        setProperty("dvecunknown32", nUnknown32);
        setProperty("dvecunknown64", nUnknown64);


        // Compute Spatial locality

        Double bin0 = 0.0;
        Double bin2 = 0.0;
        Double bin4 = 0.0;
        Double bin8 = 0.0;
        Double bin16 = 0.0;
        Double bin32 = 0.0;
        Double bin64 = 0.0;
        Double bin128 = 0.0;
        Double binRem = 0.0;
        Double spatialScore = 0.0;
        Long totalRefs = 0L;

        for(Iterator<BinnedCounter> it = agg.spatialStats.iterator(); it.hasNext(); ) {
            BinnedCounter bc = it.next();
            totalRefs += bc.counter;
            int val = bc.upperBound.intValue();
            if(val == 0) {
                bin0 += bc.counter;
            } else if(val <= 2) {
                bin2 += bc.counter;
            } else if(val <= 4) {
                bin4 += bc.counter;
            } else if(val <= 8) {
                bin8 += bc.counter;
            } else if(val <= 16) {
                bin16 += bc.counter;
            } else if(val <= 32) {
                bin32 += bc.counter;
            } else if(val <= 64) {
                bin64 += bc.counter;
            } else if(val <= 128) {
                bin128 += bc.counter;
            } else {
                binRem += bc.counter;
            }
        }

        if (totalRefs > 0 ){  //checking that had spatial trace
           // DOUBLES with vectorization
           Double vecPerc = pvec512 + pvec256 + pvec128;
           if ( (vecPerc > 0.05) && (bytesPerMop >= 6 ) ){
               if (bin16/totalRefs < 9.23/100.0 ) {
                   spatialScore = 1.95;
               }else if ( (bin16/totalRefs >= 9.23/100.0 ) && (bin32/totalRefs < 12.68/100.0 ) ) {
                   spatialScore = 2.02;
               }else if ( (bin16/totalRefs >= 9.23/100.0 ) && (bin32/totalRefs >= 12.68/100.0  )
               && (bin128/totalRefs >= 4.92/100.0  )  ){
                   spatialScore = 2.15;
               }else if ( (bin16/totalRefs >= 9.23/100.0 ) && (bin32/totalRefs >= 12.68/100.0  )
               && (bin128/totalRefs < 4.92/100.0  )  ) {
                   spatialScore = 2.30;

               }
           // DOUBLES with NO vectorization
           }else if ( (vecPerc <= 0.05) && (bytesPerMop >= 6 ) ){
               if ( (bin64/totalRefs > 12.21/100.0 ) && (bin64/totalRefs < 49.5/100.0  ) ) {
                   spatialScore = 1.96;
               }else if ( bin64/totalRefs >= 49.5/100.0  ) {
                   spatialScore = 2.11;
               }else if ( (bin64/totalRefs <= 12.21/100.0 ) && (bin32/totalRefs >= 29.15/100.0  ) ){
                   spatialScore = 2.14;
               }else if ( (bin64/totalRefs <= 12.21/100.0 ) && (bin32/totalRefs < 29.15/100.0  ) ){
                   spatialScore = 2.39;

               }
           // FLOATS & INT with vectorization
           }else if( (vecPerc > 0.05) &&  (bytesPerMop < 6 ) ){ //FLOATS or INTS with vectorization
               if ( (bin32/totalRefs >= 24.48/100.0 ) && (bin16/totalRefs < 2.68/100.0 ) ){
                   spatialScore = 1.98;
               }else if ( (bin32/totalRefs >= 24.48/100.0 ) && (bin16/totalRefs >= 2.68/100.0 )
               && (bin128/totalRefs >= 12.48/100.0 ) ){
                   spatialScore = 2.00;
               }else if ( (bin32/totalRefs >= 24.48/100.0 ) && (bin16/totalRefs >= 2.68/100.0 )
               && (bin128/totalRefs < 12.48/100.0 ) ){
                   spatialScore = 2.14;
               }else if ( (bin32/totalRefs < 24.48/100.0 ) && (bin16/totalRefs >= 55.77/100.0 )){
                   spatialScore = 2.19;
               }else if ( (bin32/totalRefs < 24.48/100.0 ) && (bin16/totalRefs < 55.77/100.0 )
               && (bin64/totalRefs >= 5.70/100.0 ) ){
                   spatialScore = 2.33;
               }else if ( (bin32/totalRefs < 24.48/100.0 ) && (bin16/totalRefs < 55.77/100.0 )
               && (bin64/totalRefs < 5.70/100.0 ) ){
                   spatialScore = 2.49;

               }
           // FLOATS & INT with NO vectorization
           }else if( (vecPerc <= 0.05) &&  (bytesPerMop < 6 ) ){ //FLOATS or INTS
               if ( (bin32/totalRefs >= 24.61/100.0 ) && (bin32/totalRefs < 49.87/100.0 ) ){
                   spatialScore = 2.16;
               }else if (bin32/totalRefs >= 49.87/100.0 ) {
                   spatialScore = 2.01;
               }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs < 49.39/100.0 )
               && ( bin16/totalRefs >= 49.68/100.0 )){
                   spatialScore = 2.24;
               }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs < 49.39/100.0 )
               && ( bin16/totalRefs < 49.68/100.0 )
                  && ( bin16/totalRefs < 12.21/100.0 ) && ( bin8/totalRefs < 24.98/100.0 ) ){
                   spatialScore = 2.28;
               }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs < 49.39/100.0 )
               && ( bin16/totalRefs < 49.68/100.0 )
                  && ( bin16/totalRefs < 12.21/100.0 ) && ( bin8/totalRefs >= 24.98/100.0 ) ){
                   spatialScore = 2.37;
               }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs < 49.39/100.0 )
               && ( bin16/totalRefs < 49.68/100.0 )
                  && ( bin16/totalRefs >= 12.21/100.0 ) && ( bin8/totalRefs < 12.05/100.0 ) ){
                   spatialScore = 2.39;
               }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs < 49.39/100.0 )
               && ( bin16/totalRefs < 49.68/100.0 )
                  && ( bin16/totalRefs >= 12.21/100.0 ) && ( bin8/totalRefs >= 12.05/100.0 ) ){
                   spatialScore = 2.47;
               }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs >= 49.39/100.0 ) ){
                   spatialScore = 2.49;
               }else {
                   Logger.warn("Unknown spatial score\n");
               }
           }//end if then for double, floats, ints & vectorization
        } else { //NO SPATIAL TRACE
           spatialScore = 0.0;
        } //end if then for totalRef > 0


        setProperty("spatial", spatialScore);
        setProperty("spat0", bin0);
        setProperty("spat2", bin2);
        setProperty("spat4", bin4);
        setProperty("spat8", bin8);
        setProperty("spat16", bin16);
        setProperty("spat32", bin32);
        setProperty("spat64", bin64);
        setProperty("spat128", bin128);
        setProperty("spatOther", binRem);

       
    }

    // -- Private -- //
    private Long sumDuds(Map<Integer, Long> duDistances) {
        Long totalDist = 0L;
        for( Iterator<Map.Entry<Integer, Long>> it = duDistances.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> ent = it.next();
            Integer dist = ent.getKey();
            Long count = ent.getValue();

            totalDist += count * dist;
        }
        return totalDist;
    }

    // 0: high effect, short distance
    // >0: low effect, large distance
    // score = SUM(i.visits * i.distance) *  dInsns
    //         --------------------------    ------
    //              SUM(i.visits)          SUM(i.visits)
    private Double computeDuds(Map<Integer, Long> duDistances, Long allInsns) {
        Long opTypeDist = 0L;
        Long opTypeCount = 0L;
        for(Iterator<Map.Entry<Integer, Long>> it = duDistances.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> ent = it.next();
            Integer dist = ent.getKey();
            Long count = ent.getValue();
            opTypeDist += count * dist;
            opTypeCount += count;
        }
        // add 1 to denominator to prevent zeros
        return (opTypeDist.doubleValue()*dInsns.doubleValue()) / (opTypeCount.doubleValue()*opTypeCount.doubleValue() + 1);
    }
}

