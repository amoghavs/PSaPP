package PSaPP.greenqueue;

import java.util.*;
import java.io.*;

import PSaPP.util.*;
import PSaPP.data.TraceDB;
import PSaPP.data.PSiNSCount;
import PSaPP.data.BlockID;

public class GreenQueue {

    public GreenQueue(EnergyProfileDB edb, TraceDB tracedb, int sysid, PSiNSCount psc) {
        this.edb = edb;
        this.tracedb = tracedb;
        this.sysid = sysid;
        this.psc = psc;
    }

    public GreenQueue(EnergyProfileDB edb, TraceDB tracedb, int sysid) {
        this(edb, tracedb, sysid, null);
    }

    public GreenQueue(EnergyProfileDB edb, PSiNSCount psc) {
        this(edb, null, INVALID_SYSID, psc);
    }

    public boolean selectFrequencies(long minPhase, long minInline) {
        
        if( this.psc == null || this.edb == null ) {
            Logger.error("psc or edb is null");
            return false;
        }

        // find the rank that used the greatest amount of cpu
        Vector<Double> cpuTimes = psc.getTimes(PSiNSCount.Event.CPUTIME);
        Double maxTime = Collections.max(cpuTimes);
        Double minTime = Collections.min(cpuTimes);

        // Make a guess at whether all ranks are doing the same work
        // Could do better by looking at some block counts on loops
        boolean ranksAreSame = true;
        if( minTime < 0.75 * maxTime ) {
            ranksAreSame = false;
        }

        if( !ranksAreSame ) {
            Logger.inform("Ranks are hetrogeneous; scaling each rank by relative cputime");
            return doUnbalancedScaling(cpuTimes, maxTime);
        } else {
            Logger.inform("Ranks are same; scaling all ranks by phases");
            return doBalancedScaling(minPhase, minInline);
        }
    }

    private TimeModel tmodel = new TimeModel(new File("/home/jperaza/psapp_interaction/get_predictions_pcubed_vec.sh"),
                                         "model_gbm_300_TIME_traditional_vector_kernel_pcubed_combo.model");

    public boolean modelLoopTimes(long minPhase, long minInline) {
         if( this.tracedb == null || this.sysid == INVALID_SYSID ) {
            Logger.error("Tracedb null or sysid is invalid in doLoopTimeHeads");
            return false;
        }

        flowGraph = new PSaPP.cfg.FlowGraph(tracedb, sysid, -1);
        flowGraph.inlineAcyclic(minInline);
        flowGraph.pruneUnsimulated();
        Logger.inform("FlowGraph:\n" + flowGraph);

        // for each function
        //   funcPhases = findPhases(f.loops)
        //       
        for( Iterator<PSaPP.cfg.Function> fit = flowGraph.getFunctions().iterator(); fit.hasNext(); ) {
            PSaPP.cfg.Function f = fit.next();
            Collection<LoopProfile> loopPhases = findPhases(f.getLoops());
            Logger.inform("Phases Found:");
            printPhases(loopPhases);

            // eliminate phases that are too short to throttle
            eliminateShortPhases(loopPhases, minPhase);
            Logger.inform("Short Phases Removed:");
            printPhases(loopPhases);

            timeModel(loopPhases);
            writeLoopTimes();

        }
        return true;

   
    }

    public boolean doLoopTimeHeads(long minPhase, long minInline) {
        if( this.tracedb == null || this.sysid == INVALID_SYSID ) {
            Logger.error("Tracedb null or sysid is invalid in doLoopTimeHeads");
            return false;
        }

        flowGraph = new PSaPP.cfg.FlowGraph(tracedb, sysid, -1);
        flowGraph.inlineAcyclic(minInline);
        flowGraph.pruneUnsimulated();
        Logger.inform("FlowGraph:\n" + flowGraph);

        // for each function
        //   funcPhases = findPhases(f.loops)
        //       
        try {
            BufferedWriter file = new BufferedWriter(new FileWriter("loopProfiles"));

            try {

                for( Iterator<PSaPP.cfg.Function> fit = flowGraph.getFunctions().iterator(); fit.hasNext(); ) {
                    PSaPP.cfg.Function f = fit.next();
                    Collection<LoopProfile> loopPhases = findPhases(f.getLoops());
                    Logger.inform("Phases Found:");
                    printPhases(loopPhases);
        
                    // eliminate phases that are too short to throttle
                    eliminateShortPhases(loopPhases, minPhase);
                    Logger.inform("Short Phases Removed:");
                    printPhases(loopPhases);
        
                    writeLoopHeads(loopPhases);
                    writeLoopProfiles(loopPhases, file);
        
                }
            } finally {
                file.close();
            }
        } catch( IOException e) {
            Logger.error(e, "Unable to write loop profiles");
            return false;
        }
        return true;

    }

    private Set<BlockID> profilesWritten = Util.newHashSet();
    public void writeLoopProfiles(Collection<LoopProfile> loops, BufferedWriter file) throws IOException {
        for( Iterator<LoopProfile> it = loops.iterator(); it.hasNext(); ) {
            LoopProfile l = it.next();
            BlockID head = l.getEntryPoint();

            if( profilesWritten.contains(head) ) {
                continue;
            }
            profilesWritten.add(head);

            Collection<LoopProfile> subprofiles = l.getSubProfiles();
            if( subprofiles != null ) {
                writeLoopProfiles(subprofiles, file);
            }

            file.write(head + "\t" + l.l1MissesPerInst() + "\t" + l.l2MissesPerInst() + "\t" + l.l3MissesPerInst() + "\t" +
                       l.fpRatio() + "\t" + l.memToIns() + "\t" + l.fpToIns() + "\t" + l.fmr() + "\t" +
                       l.iDud() + "\t" + l.fDud() + "\n");
            
        }
    }

    // Perform scaling as if all ranks are the same
    public boolean doBalancedScaling(long minPhase, long minInline) {
        if( this.tracedb == null || this.sysid == INVALID_SYSID || this.edb == null ) {
            return false;
        }

        flowGraph = new PSaPP.cfg.FlowGraph(tracedb, sysid, -1);
        flowGraph.inlineAcyclic(minInline);
        flowGraph.pruneUnsimulated();
        Logger.inform("FlowGraph:\n" + flowGraph);

        // for each function
        //   funcPhases = findPhases(f.loops)
        //       
        for( Iterator<PSaPP.cfg.Function> fit = flowGraph.getFunctions().iterator(); fit.hasNext(); ) {
            PSaPP.cfg.Function f = fit.next();
            Collection<LoopProfile> loopPhases = findPhases(f.getLoops());
            Logger.inform("Phases Found:");
            printPhases(loopPhases);

            // eliminate phases that are too short to throttle
            eliminateShortPhases(loopPhases, minPhase);
            Logger.inform("Short Phases Removed:");
            printPhases(loopPhases);

            // do power modeling
            Logger.inform("MODELING ********************************************************");
            powerModel(loopPhases);
            Logger.inform("DONE MODELING ***************************************************");
            Logger.inform("Phases Modeled:");
            printPhases(loopPhases);


            // eliminate sub-loops that all have same optimal frequency
            combineSameFreq(loopPhases);
            Logger.inform("Phases Combined:");
            printPhases(loopPhases);

            // select optimal frequency settings
            setPhaseFrequencies(loopPhases);
        }
        return true;
    }

    // Scale each rank according to its cputime
    public boolean doUnbalancedScaling() {
        if( this.psc == null || this.edb == null ) {
            return false;
        }
        Vector<Double> cpuTimes = psc.getTimes(PSiNSCount.Event.CPUTIME);
        Double maxTime = Collections.max(cpuTimes);
        return doUnbalancedScaling(cpuTimes, maxTime);
    }

    private boolean doUnbalancedScaling(Vector<Double> cpuTimes, Double maxTime) {

        SortedSet<Long> freqs = edb.getFrequencies();
        Long maxFreq = freqs.last();
        Long minFreq = freqs.first();

        // scale down all other ranks by how much less cpu they used
        for( int rank = 0; rank < cpuTimes.size(); ++rank ) {
            Double time = cpuTimes.elementAt(rank);
            Double tgtFreq = (time / maxTime) * maxFreq;
            
            Long bestFreq = null;
            Double bestDiff = Double.MAX_VALUE;
            for( Iterator<Long> it = freqs.iterator(); it.hasNext(); ) {
                Long freq = it.next();
                Double diff = Math.abs(tgtFreq - freq.doubleValue());
                if( diff < bestDiff ) {
                    bestFreq = freq;
                    bestDiff = diff;
                }
            }
            this.freqConfig.setMaxFrequency(rank, bestFreq);
        }

        return true;
    }

    public boolean writeFrequencyConfiguration(String filename) {
        return this.freqConfig != null && this.freqConfig.writeFreqConfig(filename);
    }

    public boolean writeThrottlePoints(String filename) {
        return this.freqConfig != null && this.freqConfig.writeThrottlePoints(filename);
    }

    // -- private -- //
 
    private static final int INVALID_SYSID = -1;

    // Important pieces of state
    private final TraceDB tracedb;
    private final PSiNSCount psc;
    private final int sysid;
    private final EnergyProfileDB edb;

    private FrequencyConfiguration freqConfig = new FrequencyConfiguration();
    private PSaPP.cfg.FlowGraph flowGraph;


    // Turns cfg loops into LoopProfiles
    private Collection<LoopProfile> findPhases(Collection<PSaPP.cfg.Loop> loops) {

        Collection<LoopProfile> loopPhases = Util.newLinkedList();

        for( Iterator<PSaPP.cfg.Loop> it = loops.iterator(); it.hasNext(); ) {
            PSaPP.cfg.Loop l = it.next();

            Collection<LoopProfile> subphases;
            Collection<PSaPP.cfg.Loop> subloops = l.getLoops();
            if( subloops.size() == 0 ) {
                subphases = null;
            } else {
                subphases = findPhases(subloops);
            }

            LoopProfile lprof = new LoopProfile(l, sysid);
            lprof.setSubProfiles(subphases);
            loopPhases.add(lprof);

        }
        return loopPhases;
    }

    // Selects or creates a profile to represent the input set of profiles
    private EnergyProfile selectBestProfile(Set<EnergyProfile> eps) {
        // If all profiles have same best frequency; return any of them
        Long lastFreq = null;
        boolean same = true;
        for( Iterator<EnergyProfile> it = eps.iterator(); it.hasNext(); ) {
            EnergyProfile ep = it.next();
            if( lastFreq != null && !lastFreq.equals(ep.getBestFreq()) ) {
                same = false;
                break;
            }
            lastFreq = ep.getBestFreq();
        }
        if( same ) {
            Logger.inform("All profiles have same frequency");
            return eps.iterator().next();
        }

        // Otherwise...
        Logger.inform("Nearest profiles disagree on best frequency");
        for( Iterator<EnergyProfile> it = eps.iterator(); it.hasNext(); ) {
            Logger.inform("\t" + it.next());
        }
        return eps.iterator().next();

    }

    private void writeLoopTimes() {
        SortedSet<Long> frequencies = tmodel.getFrequencies();
        try {
            BufferedWriter file = new BufferedWriter(new FileWriter("loopTimes"));
            try {
                // getblocks
                List<BlockID> loopheads = new ArrayList(timeModels.keySet());

                // write header
                file.write("Frequency");
                for( Iterator<BlockID> it = loopheads.iterator(); it.hasNext(); ) {
                    file.write("\t" + it.next());
                }
                file.write("\n");

                // for each frequency
                for( Iterator<Long> fit = frequencies.iterator(); fit.hasNext(); ) {
                    Long freq = fit.next();
                    file.write(freq.toString());
                    for( Iterator<BlockID> lit = loopheads.iterator(); lit.hasNext(); ) {
                        file.write("\t" + timeModels.get(lit.next()).getTime().get(freq));
                    }
                    file.write("\n");
                }

            } finally {
                file.close();
            }
        } catch(IOException e) {
            Logger.error(e, "Unable to write loopTimes file");
        }
    }

    private Map<BlockID, EnergyProfile> timeModels = Util.newHashMap();
    private void timeModel(Collection<LoopProfile> loopProfiles) {
        for( Iterator<LoopProfile> lit = loopProfiles.iterator(); lit.hasNext(); ) {
            LoopProfile lprof = lit.next();
            
            Collection<LoopProfile> subprofs = lprof.getSubProfiles();

            if( subprofs != null && subprofs.size() > 0 ) {
                timeModel(subprofs);
            }

            EnergyProfile ep;

            Logger.inform("Time Modeling Loop:\n" + lprof);
            ep = timeModels.get(lprof.getEntryPoint());
            if( ep == null ) {
                Map<Long, Double> times = tmodel.getTimes(lprof);

                ep = new EnergyProfile();
                ep.setTime(times);
                timeModels.put(lprof.getEntryPoint(), ep);
            }
            lprof.setTime(ep.getTime());

            Logger.inform("Loop Profile:\n" + lprof);


        }

    }


    // Fills loop profiles with power modeling data by comparing against pcubed
    // A cache for already modeled loops
    private Map<BlockID, EnergyProfile> powerModels = Util.newHashMap();
    private void powerModel(Collection<LoopProfile> loopProfiles) {
        for( Iterator<LoopProfile> lit = loopProfiles.iterator(); lit.hasNext(); ) {
            LoopProfile lprof = lit.next();
            
            Collection<LoopProfile> subprofs = lprof.getSubProfiles();

            if( subprofs != null && subprofs.size() > 0 ) {
                powerModel(subprofs);
            }

            EnergyProfile ep;

            Logger.inform("Power Modeling Loop:\n" + lprof);
            ep = powerModels.get(lprof.getEntryPoint());
            if( ep == null ) {
                Set<EnergyProfile> eps = edb.allNearest(lprof);
                if( eps.size() > 1 ) {
                    Logger.inform("Selecting from " + eps.size() + " profiles with same distance");
                    ep = selectBestProfile(eps);
                } else if ( eps.size() == 0 ) {
                    Logger.error("Unable to find profile for " + lprof);
                    return;
                } else {
                    Logger.inform("Single best profile found");
                    ep = eps.iterator().next();
                }

                powerModels.put(lprof.getEntryPoint(), ep);
            }

            //Logger.inform("PCubedProfile:\n" + ep.key + " " + ep + "\n");

            // FIXME time is taken directly from pcubed test case; may want to scale it to estimate actual performance
            lprof.setTime(ep.getTime());
            lprof.setSysPower(ep.getSysPower());

            Logger.inform("Loop Profile:\n" + lprof);


        }
    }

    // Eliminate subphases if they all have the same frequency
    private void combineSameFreq(Collection<LoopProfile> profiles) {

        for( Iterator<LoopProfile> it = profiles.iterator(); it.hasNext(); ) {
            LoopProfile lprof = it.next();

            Collection<LoopProfile> subprofiles = lprof.getSubProfiles();
            if( subprofiles != null && subprofiles.size() > 0 ) {
                combineSameFreq(subprofiles);

                Long lastFreq = null;
                boolean same = true;
                for( Iterator<LoopProfile> sit = subprofiles.iterator(); sit.hasNext(); ) {
                    LoopProfile sub = sit.next();

                    if( sub.getSubProfiles() != null && sub.getSubProfiles().size() > 0 ) {
                        same = false;
                        break;
                    }

                    Long bestFreq = sub.getBestFreq();
                    if( bestFreq == null ) {
                        continue;
                    }
                    if( lastFreq != null && !lastFreq.equals(bestFreq) ) {
                        same = false;
                        break;
                    }
                    lastFreq = bestFreq;
                }

                if( same && lastFreq == null || same && lprof.getBestFreq() != null ) {
                    lprof.setSubProfiles(null);
                }
            }
        }
    }

    // Eliminate subphases that are too small to instrument
    private void eliminateShortPhases(Collection<LoopProfile> profiles, Long minIPE) {
        for( Iterator<LoopProfile> it = profiles.iterator(); it.hasNext(); ) {
            LoopProfile lprof = it.next();

            Long insns = lprof.getDynamicInsns();
            Long entries = lprof.getEntryCount();
            if( entries.equals(0L) && insns > 0L ) {
                Logger.warn(insns + " dynamic insns with " + entries + " entries for loop " + lprof);
            }

            if( entries.equals(0L) || insns / entries < minIPE ) {
                it.remove();
            } else {
                Collection<LoopProfile> subprofs = lprof.getSubProfiles();
                if( subprofs != null ) {
                    eliminateShortPhases(lprof.getSubProfiles(), minIPE);
                    if( subprofs.size() == 0 ) {
                        lprof.setSubProfiles(null);
                    }
                }
            }
        }
    }

    // set frequency configuration from power data in loop profiles
    private void setPhaseFrequencies(Collection<LoopProfile> loops) {
        for( Iterator<LoopProfile> lit = loops.iterator(); lit.hasNext(); ) {
            LoopProfile loop = lit.next();
            Collection<LoopProfile> subloops = loop.getSubProfiles();

            if( subloops == null ) {
                Long bestFreq = loop.getBestFreq();
                BlockID bbid = loop.getEntryPoint();
                if( bestFreq == null ) {
                    Logger.warn("Best frequency could not be determined for loop " + bbid);
                } else {
                    this.freqConfig.setMaxFrequency(bbid, bestFreq);
                }
            } else {
                setPhaseFrequencies(subloops);
            }
        }
    }

    private void printPhases(Collection<LoopProfile> loops) {

        List<LoopProfile> sortedLoops = LoopProfile.sorted(loops);
        Collections.reverse(sortedLoops);

        String phases = "";
        for( Iterator<LoopProfile> it = sortedLoops.iterator(); it.hasNext(); ) {
            phases += "\n" + it.next();
        }
        Logger.inform(phases + "\n");
    }

    private void writeLoopHeads(Collection<LoopProfile> loops) {
        String heads = "";
        for( Iterator<LoopProfile> it = loops.iterator(); it.hasNext(); ) {
            LoopProfile l = it.next();
            heads += l.getEntryPoint() + "\n";
            heads += l.getSubLoopHeads();
        }
        try {
            BufferedWriter file = new BufferedWriter(new FileWriter("phase_heads.tp"));
            try {
                file.write(heads);
            } finally {
                file.close();
            }
        } catch (IOException e) {
            Logger.error(e, "Unable to write loop heads");
        }
    }
}
