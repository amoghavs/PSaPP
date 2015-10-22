package PSaPP.cfg;

import java.util.*;

import PSaPP.util.*;
import PSaPP.data.BlockID;
import PSaPP.data.FunctionID;

public class FlowGraph {

    public FlowGraph(PSaPP.data.TraceDB tracedb, int sysid, int rank) {
        this.sysid = sysid;
        this.rank = rank;

        blockInfo = tracedb.getAllBlocks();
        tracedb.getStaticStats(blockInfo, funcInfo, loopInfo);
        tracedb.getDynamicStats(blockInfo, funcInfo, loopInfo, sysid, rank);

        // vaddr -> function: used to connect call-graph using jump/call targets
        Map<Long, FunctionID> vaddrToFunction = Util.newHashMap();

        // Add a node for each function
        Map<FunctionID, Set<BlockID>> funcsToBlocks = tracedb.getFuncToBbids();
        for( Iterator<Map.Entry<FunctionID, Set<BlockID>>> it = funcsToBlocks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<FunctionID, Set<BlockID>> ent = it.next();
            FunctionID fid = ent.getKey();
            //String funcname = ent.getKey();
            Set<BlockID> bbids = ent.getValue();

            Function f = new Function(funcInfo.get(fid));
            this.functions.put(fid, f);

            // and a node for each block in that function
            for( Iterator<BlockID> bit = bbids.iterator(); bit.hasNext(); ) {
                BlockID bbid = bit.next();
                PSaPP.data.BasicBlock bb = blockInfo.get(bbid);
                Block b = new Block(bb);
                b.setFunction(f);
                f.addBlock(b);
                this.blocks.put(bbid, b);

                // add vaddr to map
                vaddrToFunction.put(bb.vaddr, fid);
            }
        }

        // Create a loop node for each loop
        for( Iterator<PSaPP.data.Loop> it = loopInfo.values().iterator(); it.hasNext(); ) {
            Loop loop = new Loop(it.next());
            loops.put(loop.loopInfo.headBlock, loop);
        }

        // link blocks to functions and loops to blocks
        for( Iterator<Block> it = this.blocks.values().iterator(); it.hasNext(); ) {
            Block b = it.next();

            FunctionID fid = vaddrToFunction.get(b.blockInfo.callTargetAddress);
            if(fid != null) {
                Function f = this.functions.get(fid);
                b.setCallTarget(f);
                assert(f != null );
                f.addCaller(b);
            }

            if( !b.blockInfo.loopHead.blockHash.equals(0L) ) {
                Loop loop = loops.get(b.blockInfo.loopHead);
                loop.addBlock(b);
                b.setLoop(loop);
            }
        }

        // Now build loop hierarchies for each function
        Map<FunctionID, Set<Loop>> funcBins = binLoopsByFunction(loops.values());
        for( Iterator<Map.Entry<FunctionID, Set<Loop>>> it = funcBins.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<FunctionID, Set<Loop>> ent = it.next();
            FunctionID fid = ent.getKey();
            //String funcname = ent.getKey();
            Set<Loop> loops = ent.getValue();
            Function f = this.functions.get(fid);
            if( f != null ) {
                Collection<Loop> topLoops = buildLoopTree(loops).values();
                f.addLoops(topLoops);
            }
            for( Iterator<Loop> lit = loops.iterator(); lit.hasNext(); ) {
                Loop l = lit.next();
                l.setFunction(f);
            }
		//				if(f != null)
		//				{
		//					Logger.inform("Function " + f.funcInfo.functionName + " has the following loops/owners");
		//					Set<Loop> allFsLoops = f.getAllLoops();
		//					for(Iterator<Loop> acIt = allFsLoops.iterator(); acIt.hasNext(); )
		//					{
		//						Loop acLoop = acIt.next();
		//						Logger.inform("\t" + acLoop.loopInfo.headBlock + " - " + acLoop.getFunction().funcInfo.functionName);
		//					}
		//				}
        }

    }

    public Collection<Function> getFunctions() {
        return this.functions.values();
    }

    public void pruneUnsimulated() {
        for( Iterator<Function> it = this.functions.values().iterator(); it.hasNext(); ) {
            Function f = it.next();

            // Remove the whole function if nothing was simulated
            PSaPP.data.CacheStats stats = f.funcInfo.perSysCaches.get(sysid);
            if( stats == null || !stats.hasLevel(1) ) {
                it.remove();
                continue;
            } else {
                f.pruneUnsimulated(sysid);
            }
        }
    }

    public Collection<Function> inlineFunction(Function f) {
			
        Collection<Function> affected = Util.newHashSet();

        Collection<Block> callers = f.getCallerBlocks();
        if( callers.size() == 0 ) {
            return affected;
        }

        // inline at each caller
        for( Iterator<Block> bit = callers.iterator(); bit.hasNext(); ) {
            Block b = bit.next();

            // This can happen if the function has already been removed
            if( b.getCallTarget() == null ) {
                continue;
            }
            assert( b.getCallTarget() == f );
            b.setCallTarget(null);

            Function g = b.getFunction();
         //   Logger.inform("Inlining " + f.funcInfo.functionName + " at " + g.funcInfo.functionName);
           // Logger.inform("\tStarting with  "  + g.funcInfo.dInsns);

            if( b.blockInfo.visitCount == null ) {
                continue;
            }

            // If we only inline at one location, don't need to clone
						// We're no longer deleting from the graph, so we need to 
						// save this information...
            Function clone;
            //if( callers.size() == 1 ) {
            //    clone = f;
						//} else {
                clone = f.clone();
            //}

            Collection<Loop> allFLoops = clone.getAllLoops();
            Collection<Loop> topFLoops = clone.getLoops();

            List<Block> fBlocks = clone.getBlocks();
            //Logger.inform("\tBlock " + b.blockInfo.bbid + " visited " + b.blockInfo.visitCount + " times");
            //Logger.inform("\tFunction " + f.funcInfo.functionName + " entered " + f.funcInfo.entryCount + " times");
            if( f.funcInfo.entryCount < b.blockInfo.visitCount ) {
                Logger.warn("Discrepancy between function entry count and caller block visit count; not inlining here");
                continue;
            }


            // Create a pseudo function that will hold scaled versions of f's dynamic stats
            PSaPP.data.Function f2 = new PSaPP.data.Function();
            Double scaleRatio = (b.blockInfo.visitCount).doubleValue() / ((Long)f.funcInfo.entryCount).doubleValue();
            f.funcInfo.scaleAggregator(scaleRatio, f2);

            // Then merge those partial stats into g
            g.funcInfo.aggregateAggregator(f2);

            for( Iterator<Loop> it = g.getAllLoops().iterator(); it.hasNext(); ) {
                assert( it.next().getFunction() == g );
            }

            // set new function on each block
            for( Iterator<Block> it = fBlocks.iterator(); it.hasNext(); ) {
                Block fb = it.next();
                fb.setFunction(g);
            }

            // set new function on each loop and scale down entry count for each loop
            for( Iterator<Loop> it = allFLoops.iterator(); it.hasNext(); ) {
                Loop l = it.next();
                l.setFunction(g);
                l.setEntryCount(((Double)(l.getEntryCount() * scaleRatio)).longValue());
            }

            // If b is in a loop
            Loop bl = b.getLoop();
            if( bl != null ) {

                //Logger.inform("\tFunction " + f.funcInfo.functionName + " called in loop " + bl.loopInfo.headBlock + " in func " + g.funcInfo.functionName);
                // change parent of top loops of f to b's loop
                for( Iterator<Loop> it = topFLoops.iterator(); it.hasNext(); ) {
                    Loop l = it.next();
                    l.parent = bl;
                    assert( l.loopInfo != null );
                    assert( bl.loopInfo != null );
                    l.loopInfo.parentHead = bl.loopInfo.headBlock;
                }
                bl.addLoops(topFLoops);

                for( Iterator<Loop> it = g.getAllLoops().iterator(); it.hasNext(); ) {
                    assert( it.next().getFunction() == g );
                }

                // and aggregate f's stats into each of b's loops
                bl.loopInfo.aggregateAggregator(f2);
                while( bl.parent != null ) {
                    bl = bl.parent;
                    bl.loopInfo.aggregateAggregator(f2);
                }
            } else {
                g.addLoops(topFLoops);

                for( Iterator<Loop> it = g.getAllLoops().iterator(); it.hasNext(); ) {
                    assert( it.next().getFunction() == g );
                }
            }

            g.addBlocks(fBlocks);
						
            //Logger.inform("\t" + g.funcInfo.functionName + " has accumulated " + g.getLoops().size() + " top loops");
            //Logger.inform("\t" + g.funcInfo.functionName + " has accumulated " + g.funcInfo.dInsns + " dInsns");
            
						affected.add(g);
					
        }

        // Remove function from flow graph
				// Don't want to remove function from flow graph because we want 
				// the reports to show all functions
        //Logger.inform("Not removing " + f.funcInfo.functionName);
        //this.remove(f);

        return affected;
    }

    public Set<Function> remove(Function f) {
        Set<Function> affected = Util.newHashSet();

        Collection<Block> callers = f.getCallerBlocks();
        for( Iterator<Block> it = callers.iterator(); it.hasNext(); ) {
            Block b = it.next();
            assert( b.getCallTarget() == f || b.getCallTarget() == null );
            b.setCallTarget(null);
            affected.add(b.getFunction());
        }
        this.functions.remove(f.funcInfo.functionID);
        return affected;
    }

    // inline all leaf functions until we reach a fixed point
		// Previous versions of PSaPP removed duplicate information as 
		// leaves were inlined into parents (by removing the leaf). The 
		// current version does NOT remove this information so that the 
		// user can look at the aggregated nodes.
    public void inlineAcyclic(Long minDInsns) {
        Logger.inform("Inlining acyclic");

        // we reach a fixed point iff the queue becomes empty
        Queue<Function> toInline = new LinkedList<Function>(this.functions.values());

        while( toInline.size() > 0 ) {
            Function f = toInline.poll();

            if( !this.functions.containsKey(f.funcInfo.functionID) ) {
                continue;
            }

            // if not a leaf, skip
            List<Function> subcalls = f.getFunctions();
            if( subcalls.size() > 0 ) {
                continue;
            }

            Collection<Function> affected;

            // if the function is cold, just remove it
						// --> For now, adding these back in.
           // if( f.funcInfo.dInsns < minDInsns ) {
        	 // 		Logger.inform("Removing " + f.funcInfo.functionName);
           //     affected = remove(f);

           // // otherwise, inline it
           // } else {
                affected = inlineFunction(f);
           // }

            toInline.addAll(affected);
        }
    }

    public String toString() {
        String retval = "";
        for( Iterator<Function> fit = this.functions.values().iterator(); fit.hasNext(); ) {
            Function f = fit.next();
            retval += f.toString() + "\n";
        }

        return retval;
    }

    // -- Private -- //

    // FlowGraph nodes
    private final Map<FunctionID, Function> functions = Util.newHashMap();

    // invalid as soon as we transform the graph
    private final Map<BlockID, Block> blocks = Util.newHashMap();
    private final Map<BlockID, Loop> loops = Util.newHashMap();

    // Info objects
    private final Map<BlockID, PSaPP.data.Loop> loopInfo = Util.newHashMap();
    private final Map<FunctionID, PSaPP.data.Function> funcInfo = Util.newHashMap();
    private final Map<BlockID, PSaPP.data.BasicBlock> blockInfo;

    private final Integer sysid;
    private final Integer rank;

    // build a tree of loops for a function
    private Map<BlockID, Loop> buildLoopTree(Collection<Loop> loops) {

        // top level loops in function
        Map<BlockID, Loop> topLoops = Util.newHashMap();

        // all loops in function
        Map<BlockID, Loop> allLoops = Util.newHashMap();

        // parentHead -> sub-loop nodes
        Map<BlockID, Collection<Loop>> families = Util.newHashMap();


        // Create new loop node
        // Place in bin with siblings
        for( Iterator<Loop> it = loops.iterator(); it.hasNext(); ) {
            Loop loop = it.next();
            PSaPP.data.Loop loopInfo = loop.loopInfo;

            BlockID parentHead = loopInfo.parentHead;

            // Place top level loops in topLoops
            if( parentHead.equals(loopInfo.headBlock) ) {
                topLoops.put(loopInfo.headBlock, loop);

            // and all others in a bin with their siblings
            } else {
                Collection<Loop> siblings = families.get(parentHead);
		if( siblings == null ) {
		    siblings = Util.newLinkedList();
		    families.put(parentHead, siblings);
		}
		siblings.add(loop);
            }

            allLoops.put(loopInfo.headBlock, loop);
        }


        // for each node, collect children
        for( Iterator<Loop> it = allLoops.values().iterator(); it.hasNext(); ) {
            Loop loop = it.next();

            Collection<Loop> children = families.get(loop.loopInfo.headBlock);
            if( children != null ) {
                loop.addLoops(children);
            }
            if( loop.loopInfo.parentHead.equals(loop.loopInfo.headBlock) ) {
                loop.parent = null;
            } else {
                loop.parent = allLoops.get(loop.loopInfo.parentHead);
            }
        }

        return topLoops;
    }

    private Map<FunctionID, Set<Loop>> binLoopsByFunction(Collection<Loop> loops) {
        Map<FunctionID, Set<Loop>> bins = Util.newHashMap();

        for( Iterator<Loop> it = loops.iterator(); it.hasNext(); ) {
            Loop loop = it.next();
            FunctionID fid = loop.loopInfo.functionID;
            Set<Loop> bin = bins.get(fid);
            if( bin == null ) {
                bin = Util.newHashSet();
                bins.put(fid, bin);
            }
            bin.add(loop);
        }
        return bins;
    }

    // for each function f
    //   for each block b in f
    //       b.setFunction(f)
    private void setBlockBackCalls() {
        for( Iterator<Function> fit = this.functions.values().iterator(); fit.hasNext(); ) {
            Function f = fit.next();
            for( Iterator<Block> bit = f.getBlocks().iterator(); bit.hasNext(); ) {
                Block b = bit.next();
                b.setFunction(f);
            }
        }
    }
}

