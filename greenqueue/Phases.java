package PSaPP.greenqueue;

import java.util.*;
import java.io.*;

import PSaPP.util.*;
import PSaPP.data.TraceDB;
import PSaPP.data.BlockID;
import PSaPP.util.Util.Predicate;

/*
 * This module supports options used Via RunTool
 * These options support creating data columns for logical structures
 * This module does inlining of functions such that a report for any structure
 * includes statistics from sub-structures.
 * e.g.
 * loop 1:
 *   f()
 * A report for loop 1 will include its estimated share of statistics for its calls to f
 *
 * Options:
 * --functions [vectorItem*]
 *   Writes to file each function in the program
 *
 * --loops [vectorItem*]
 *   Writes to file each phase in the program, no merging
 *   This option supports the use of additional options for selection, below
 *
 * --merge <vectorItem> <tolerance>
 *   Performs merging of phases based on the vectorItem
 *
 * --select <vectorItem> <target>+
 *
 *  <target>: greaterThan <value>
 *            lessThan <value>
 *            within <tolerance> of <value>
 *
 * --pruneUnsimulated
 *
 * --noInline
 *
 * --full // Print full loop vector report with all loops
 */
public class Phases {

    private Collection<LoopProfile> loops = null;
    private Collection<FunctionProfile> functions = null;
    private PSaPP.cfg.FlowGraph flowGraph = null;
    final private TraceDB tracedb;
    final private int sysid;

    public Phases(TraceDB tracedb, int sysid) {
        this.tracedb = tracedb;
        this.sysid = sysid;
    }

    // run this module with options
    // TODO:
    //   optionally add power model/data
    //   optionally add time model/data
    public boolean run(Map<String, String> options, int numRanks) {
        Integer rank;
        boolean processAllRanks = false;
        if(options.get("rank") != null) {
            String rankString = options.get("rank");
            // Test if we are producing files for all ranks, or just one
            if(rankString.toLowerCase().equals("all")) {
                processAllRanks = true;
                rank = 0;
            } else {
                rank = Integer.parseInt(rankString);
            }
        } else {
            rank = TraceDB.INVALID_RANK;
        }
        boolean pruneUnsim = false;
        boolean noInlining = false;
				boolean fullPrint  = false;
        if(options.get("pruneUnsimulated") != null) {
            pruneUnsim = true;
        }
        if(options.get("noInline") != null) {
            noInlining = true;
        }
        if(options.get("full") != null) {
            fullPrint = true;
        }
        if(options.get("loops") != null) {
            String vectorItems = options.get("loops");
            if(vectorItems.length() == 0 || vectorItems.equals("default")) {
                vectorItems = AggregatorProfile.ALL_PROPERTIES;
            }

            if(processAllRanks)
            {
                for(rank = 0; rank < numRanks; rank++)
                {
                    findPhases(rank, pruneUnsim, noInlining);
                    if(options.get("minPhaseSize") != null) {
                        Long minsize = Long.parseLong(options.get("minPhaseSize"));
                        eliminateShortPhases(minsize);
                    }
                    if(options.get("merge") != null) {
                        String[] mergeOpts = (options.get("merge")).split(" ");
                        String mergeItem = mergeOpts[0];
                        Double tolerance = Double.parseDouble(mergeOpts[1]);
                        mergePhases(mergeItem, tolerance);
                    }
                    if(options.get("select") != null) {
                        String[] selectOpts = options.get("select").split(" ");
                        boolean selectOuter = false;
                        if(options.get("selectOuter") != null) selectOuter = true;
                        selectPhases(selectOpts, selectOuter);
                    }
                    
                    writeLoopsToFile(rank, vectorItems.split(" "), fullPrint);
                }
            } else {
                findPhases(rank, pruneUnsim, noInlining);

                if(options.get("minPhaseSize") != null) {
                    Long minsize = Long.parseLong(options.get("minPhaseSize"));
                    eliminateShortPhases(minsize);
                }

                if(options.get("merge") != null) {
                    String[] mergeOpts = (options.get("merge")).split(" ");
                    String mergeItem = mergeOpts[0];
                    Double tolerance = Double.parseDouble(mergeOpts[1]);
                    mergePhases(mergeItem, tolerance);
                }
                if(options.get("select") != null) {
                    String[] selectOpts = options.get("select").split(" ");
                                    boolean selectOuter = false;
                    if(options.get("selectOuter") != null) selectOuter = true;
                    selectPhases(selectOpts, selectOuter);
                }
                    
                writeLoopsToFile(rank, vectorItems.split(" "), fullPrint);
            }
        }

        if(options.get("functions") != null) {
            String vectorItems = options.get("functions");
            if(vectorItems.length() == 0 || vectorItems.equals("default")) {
                vectorItems = AggregatorProfile.ALL_PROPERTIES;
            }
    
            if(processAllRanks) {
                for(rank = 0; rank < numRanks; rank++) {
                    accumulateFunctions(rank, pruneUnsim, noInlining);
                    writeFunctionsToFile(rank, vectorItems.split(" "));
                }
            } else {
                accumulateFunctions(rank, pruneUnsim, noInlining);
                writeFunctionsToFile(rank, vectorItems.split(" "));
            }
        }

        return true;
    }

    // Remove phases with fewer than minsize dynamic instructions
    private void eliminateShortPhases(Long minsize) {
        Logger.inform("Eliminating phases smaller than " + minsize);
        eliminateShortPhases(loops, minsize);
    }

    private void eliminateShortPhases(Collection<LoopProfile> loops, Long minsize) {
        for( Iterator<LoopProfile> it = loops.iterator(); it.hasNext(); ) {
            LoopProfile lprof = it.next();

            Long insns = lprof.getDynamicInsns();
            Long entries = lprof.getEntryCount();
            if( entries.equals(0L) && insns > 0L ) {
                Logger.warn(insns + " dynamic insns with " + entries + " entries for loop " + lprof);
            }

            if( entries.equals(0L) || insns / entries < minsize ) {
                it.remove();
            } else {
                Collection<LoopProfile> subprofs = lprof.getSubProfiles();
                if( subprofs != null ) {
                    eliminateShortPhases(lprof.getSubProfiles(), minsize);
                    if( subprofs.size() == 0 ) {
                        lprof.setSubProfiles(null);
                    }
                }
            }
        }
    }

    // Merge similar phases
    // If all subloops have the same property value within a tolerance, eliminate them
    //   and move phase to outer loop.
    private void mergePhases(String vectorItem, Double tolerance) {
        Logger.inform("merging phases!");
        mergePhases(vectorItem, tolerance, loops);
    }
    private void mergePhases(String vectorItem, Double tolerance, Collection<LoopProfile> profiles) {

        // Outermost profiles won't be eliminated
        for(Iterator<LoopProfile> it = profiles.iterator(); it.hasNext(); ) {
            LoopProfile lprof = it.next();

            // Possibly elminate the subprofiles
            Collection<LoopProfile> subprofiles = lprof.getSubProfiles();

            // first recurse
            if(subprofiles != null && subprofiles.size() > 0 ) {
                mergePhases(vectorItem, tolerance, subprofiles);

                // examine each subprofile to determine if they are homogeneous
                // keep track of the range of values seen
                Number minVal = null;
                Number maxVal = null;
                boolean same = true;
                for(Iterator<LoopProfile> sit = subprofiles.iterator(); sit.hasNext(); ) {
                    LoopProfile sub = sit.next();

                    // can't be homogeneous if a subprofile wasn't homogeneous
                    if(sub.getSubProfiles() != null && sub.getSubProfiles().size() > 0){
                        same = false;
                        break;
                    }

                    // get property for this subprofile and it to range
                    Object ob = sub.getProperty(vectorItem);
                    Number subVal;
                    if(!(ob instanceof Number)){
                        Logger.warn("WARNING: Invalid vector item being used for merging, not a number");
                        subVal = 1;
                    } else {
                        subVal = (Number)ob;
                    }

                    if(subVal == null)
                        continue;
                    if(minVal == null || subVal.doubleValue() < minVal.doubleValue())
                        minVal = subVal;
                    if(maxVal == null || subVal.doubleValue() > maxVal.doubleValue())
                        maxVal = subVal;

                }
                // eliminate the subprofiles if the property values are similar
                if(same && (minVal == null || maxVal.doubleValue() - minVal.doubleValue() < tolerance)) {
                    lprof.setSubProfiles(null);
                }
            }
        }
    }

    /* --select (<vectorItem> <target>+)+
     *
     *  <target>: greaterThan <value>
     *            lessThan <value>
     *            within <tolerance> of <value>
     */
    private Predicate<LoopProfile> parseRules(String[] aopts) {
        List<String> opts = Util.newLinkedList(Arrays.asList(aopts));

        List<String> vectorItems = Util.newLinkedList(Arrays.asList(AggregatorProfile.ALL_PROPERTIES.split(" ")));

        final List<Predicate<LoopProfile>> rules = Util.newLinkedList();

        String vectorItem = null;
        while(opts.size() > 0) {
            String next = opts.get(0).toLowerCase(); opts.remove(0);
            if(next.equals("greaterthan")) {
                final Double limit = Double.parseDouble(opts.get(0)); opts.remove(0);
                final String vi = vectorItem;
                if(vi == null) {Logger.error("no vector item specified in rule at: " + next); return null;}
                rules.add(new Predicate<LoopProfile>() {
                    public boolean accept(LoopProfile lp) {return ((Number)lp.getProperty(vi)).doubleValue() > limit;}
                });
            } else if(next.equals("lessthan")) {
                final Double limit = Double.parseDouble(opts.get(0)); opts.remove(0);
                final String vi = vectorItem;
                if(vi == null) {Logger.error("no vector item specified in rule at: " + next); return null;}
                rules.add(new Predicate<LoopProfile>() {
                    public boolean accept(LoopProfile lp) {return ((Number)lp.getProperty(vi)).doubleValue() < limit;}
                });
            } else if(next.equals("within")) {
                final Double target = Double.parseDouble(opts.get(0));opts.remove(0);
                assert(opts.get(0).equals("of")); opts.remove(0); // "of"
                final Double tolerance = Double.parseDouble(opts.get(0)); opts.remove(0);
                final String vi = vectorItem;
                if(vi == null) {Logger.error("no vector item specified in rule at: " + next); return null;}
                rules.add(new Predicate<LoopProfile>() {
                    public boolean accept(LoopProfile lp) {
                        return ((Number)lp.getProperty(vi)).doubleValue() < (target + tolerance) && ((Number)lp.getProperty(vi)).doubleValue() > (target - tolerance);
                    }
                });
            } else if(vectorItems.contains(next)) {
                vectorItem = next;
            } else {
                Logger.error("unknown option in phase select: " + next);
                return null;
            }
        }

        Predicate<LoopProfile> retval = new Predicate<LoopProfile>() {
            public boolean accept(LoopProfile lp) {
                for(Iterator<Predicate<LoopProfile>> it = rules.iterator(); it.hasNext(); ) {
                    Predicate<LoopProfile> p = it.next();
                    if(!p.accept(lp)) {
                        return false;
                    }
                }
                return true;
            }
        };
        return retval;
    }

    private boolean selectPhases(String[] aopts, boolean selectOuter) {
        Predicate<LoopProfile> p = parseRules(aopts);
        if(p == null) return false;
        selectPhases(p, loops, selectOuter);
        return true;
    }
    private void selectPhases(Predicate<LoopProfile> p, Collection<LoopProfile> profiles, boolean selectOuter) {

        for(Iterator<LoopProfile> it = profiles.iterator(); it.hasNext(); ) {
            LoopProfile lp = it.next();
            
            Collection<LoopProfile> subprofiles = lp.getSubProfiles();
            if(subprofiles != null && subprofiles.size() > 0) {
                selectPhases(p, subprofiles, selectOuter);

                // This loop is eliminated if children were eliminated and
                // we don't want to selectOuter
                if(subprofiles.size() == 0 && !selectOuter) {
                    it.remove();
                    continue;
                } else if(subprofiles.size() > 0) {
                    continue;
                }
            }

            // either children were eliminated and selectOuter or
            // there were no children
            // eliminate loop if it doesn't pass the predicate
            if(!p.accept(lp)) {
                it.remove();
            }
        }
    }

    // Accumulation functions
    // accumulateFunctions
    // findPhases
    private void accumulateFunctions(int rank, boolean prune, boolean noInlining) {
        Logger.inform("accumulating functions");

        flowGraph = new PSaPP.cfg.FlowGraph(tracedb, sysid, rank);
        if(!noInlining)
        {
          flowGraph.inlineAcyclic(10000L);
        }
        if(prune) {
            flowGraph.pruneUnsimulated();
        }

        this.functions = Util.newLinkedList();
        for(Iterator<PSaPP.cfg.Function> fit = flowGraph.getFunctions().iterator(); fit.hasNext(); ) {
            PSaPP.cfg.Function f = fit.next();
            this.functions.add(new FunctionProfile(f, sysid));
        }
    }

    // locate all phases in the program and save them in this.phases
    //
    // TODO -- add options to:
    //   - control minimum inline
    //   - control minimum phase size
    private void findPhases(int rank, boolean prune, boolean noInlining) {
        flowGraph = new PSaPP.cfg.FlowGraph(tracedb, sysid, rank);
        if(!noInlining)
        {
          flowGraph.inlineAcyclic(10000L);
        }
        if(prune) {
            flowGraph.pruneUnsimulated();
        }

        loops = Util.newLinkedList();
        for( Iterator<PSaPP.cfg.Function> fit = flowGraph.getFunctions().iterator(); fit.hasNext(); ) {
            PSaPP.cfg.Function f = fit.next();
            Collection<LoopProfile> loopPhases = findPhases(f.getLoops());
            loops.addAll(loopPhases);
        }
    }
    private Collection<LoopProfile> findPhases(Collection<PSaPP.cfg.Loop> loops) {

        Collection<LoopProfile> loopPhases = Util.newLinkedList();
        for( Iterator<PSaPP.cfg.Loop> it = loops.iterator(); it.hasNext(); ) {
            PSaPP.cfg.Loop l = it.next();
						//Logger.inform("\t\t" + l.loopInfo.describe() + " - " + l.getEntryCount());
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

    /************** OUTPUT ******/
    private boolean writeLoopsToFile(Integer rank, String[] vectorItems, boolean fullPrint) {
        Logger.inform("writing phases");
        profilesWritten = Util.newHashSet();
        try {
            String filename = "loopVectors.r";
            if(rank.equals(TraceDB.INVALID_RANK)) {
                filename += "ALL";
            } else {
                filename += rank.toString();
            }
            BufferedWriter file = new BufferedWriter(new FileWriter(filename));
            try {
                file.write("# LoopID");
								if(fullPrint)
									file.write("\tinlinedAt");
                for(int i = 0; i < vectorItems.length; ++i) {
                    file.write("\t" + vectorItems[i]);
                }
                file.write("\n");
                writeLoopsToFile(loops, vectorItems, file, fullPrint);
            } finally {
                file.close();
            }
        } catch (IOException e) {
            Logger.error(e, "Unable to write phases");
            return false;
        }
        profilesWritten = null;
        return true;
    }


    private Set<BlockID> profilesWritten = null;
    private void writeLoopsToFile(Collection<LoopProfile> loops, String[] vectorItems, BufferedWriter file, boolean fullPrint) throws IOException {

        //ArrayList<LoopProfile> loops = new ArrayList<LoopProfile>(loops_in);
        //Collections.sort(loops, new Comparator<LoopProfile>() {
        //    public int compare(LoopProfile l1, LoopProfile l2) {
        //        return l1.getEntryPoint().compareTo(l2.getEntryPoint());
        //    }
        //});
        for(Iterator<LoopProfile> it = loops.iterator(); it.hasNext(); ) {
            LoopProfile l = it.next();
            BlockID head = l.getEntryPoint();

            //if(profilesWritten.contains(head)) {
            //    continue;
            //}
            //profilesWritten.add(head);
            // Write this loops profile
						String owner = l.getOwner();
						String actualLocation = l.getFunction();
						if(owner.equals(actualLocation) || fullPrint){
    	        file.write(head.toString());
							if(fullPrint)
								file.write("\t" + owner);
    	        for(int i = 0; i < vectorItems.length; ++i) {
    	            String item = vectorItems[i];
    	            Object value = l.getProperty(item);
    	            if(value == null) {
    	                Logger.warn("No value found for property " + item);
    	            }
    	            file.write("\t" + value);
    	        }
    	        file.write("\n");
						}

            // Write subloops' profiles
            Collection<LoopProfile> subprofiles = l.getSubProfiles();
            if( subprofiles != null ) {
                writeLoopsToFile(subprofiles, vectorItems, file, fullPrint);
            }

        }
    }

    // Output functions report to file
    private boolean writeFunctionsToFile(Integer rank, String[] vectorItems) {
        Logger.inform("writing function vectors");

        try {
            String filename = "functionVectors.r";
            if(rank.equals(TraceDB.INVALID_RANK)){
                filename += "ALL";
            } else {
                filename += rank.toString();
            }
            BufferedWriter file = new BufferedWriter(new FileWriter(filename));
            try {
                file.write("# Function");
                for(int i = 0; i < vectorItems.length; ++i) {
                    file.write("\t" + vectorItems[i]);
                }
                file.write("\n");

                writeFunctionsToFile(functions, vectorItems, file);
            } finally {
                file.close();
            }
        } catch (IOException e) {
            Logger.error(e, "Unable to write function vectors");
            return false;
        }
        return true;
    }
    private void writeFunctionsToFile(Collection<FunctionProfile> functions, String[] vectorItems, BufferedWriter file) throws IOException {
        for(Iterator<FunctionProfile> it = functions.iterator(); it.hasNext(); ) {
            FunctionProfile f = it.next();

            // Write this profile
            file.write(f.name);
            for(int i = 0; i < vectorItems.length; ++i) {
                String item = vectorItems[i];
                Object value = f.getProperty(item);
                if(value == null) {
                    Logger.warn("No value found for property " + item);
                }
                file.write("\t" + value);
            }
            file.write("\n");
        }
    }

}
