package PSaPP.cfg;

import java.util.*;

import PSaPP.util.*;

public class Function implements FlowElement {
    final public PSaPP.data.Function funcInfo;

    public Function(PSaPP.data.Function funcInfo) {
        this.funcInfo = funcInfo;
    }

    public Function clone() {

        Integer id;
        synchronized(cloneid) {
            id = cloneid++;
        }
        Function f = new Function(funcInfo);

        // clone each loop
        for( Iterator<Loop> it = loops.iterator(); it.hasNext(); ) {
            Loop l = it.next().clone();
            l.setFunction(f);
            f.addLoop(l);
            f.addBlocks(l.getAllBlocks());
        }

        // clone each block that wasn't in a loop
        for( Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            if( b.getLoop() == null ) {
                b = b.clone();
                f.addBlock(b);
            }
        }

        // set function for all blocks
        for( Iterator<Block> it = f.blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            b.setFunction(f);
        }

        return f;
    }

    public Long getEntryCount() {
        return funcInfo.entryCount;
    }

    public PSaPP.data.DynamicAggregator getAggregator() {
        return this.funcInfo;
    }

    public String toString() {
        String ret = funcInfo.functionName.toString();
        for( Iterator<Loop> it = loops.iterator(); it.hasNext(); ) {
            ret += "\n" +  it.next().toString();
        }
        ret = ret.replaceAll("\n", "\n\t");
        return ret;
    }

    public Long countFunctionCalls() {
        Long total = 0L;
        for( Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            total += b.countFunctionCalls();
        }
        return total;
    }

    public List<Function> getFunctions() {
        // Collection the functions that each block calls
        List<Function> l = Util.newLinkedList();
        for( Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            List<Function> bl = b.getFunctions();
            l.addAll(bl);
        }
        return l;
    }

		public Set<Function> getUniqueFunctions() {
				// Collection the functions that each block calls, but return a
				// set not a list.
        Set<Function> allFunctions = Util.newHashSet();
        for( Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            List<Function> bl = b.getFunctions();
            allFunctions.addAll(bl);
        }
        return allFunctions;
    }


    public Set<Loop> getLoops() {
        return this.loops;
    }

    public Set<Loop> getAllLoops() {
        Set<Loop> allLoops = Util.newHashSet();
        for( Iterator<Loop> it = this.loops.iterator(); it.hasNext(); ) {
            Loop sub = it.next();
            allLoops.add(sub);
            allLoops.addAll(sub.getAllLoops());
        }
        return allLoops;
    }

    public Set<Loop> getInnerLoops() {
        Set<Loop> innerLoops = Util.newHashSet();
        for(Iterator<Loop> it = this.loops.iterator(); it.hasNext(); ) {
            Loop sub = it.next();
            Set<Loop> subInner = sub.getInnerLoops();
            if(subInner.size() == 0) {
                innerLoops.add(sub);
            } else {
                innerLoops.addAll(subInner);
            }
        }
        return innerLoops;
    }

    public List<Block> getBlocks() {
        Collections.sort(blocks);
        return this.blocks;
    }

    public Set<Block> getCallerBlocks() {
        return this.blockCallers;
    }

    // -- Package Private -- //
    void addBlock(Block b) {
        this.blocks.add(b);
    }

    void addBlocks(Collection<Block> blocks) {
			this.blocks.addAll(blocks);
    }

    void addLoop(Loop loop) {
        this.loops.add(loop);
    }

    void addLoops(Collection<Loop> loops) {
        this.loops.addAll(loops);
    }

    void addCaller(Block b) {
        blockCallers.add(b);
    }

    void pruneUnsimulated(Integer sysid) {
        for( Iterator<Loop> it = loops.iterator(); it.hasNext(); ) {
            Loop l = it.next();
            PSaPP.data.CacheStats stats = l.loopInfo.perSysCaches.get(sysid);
            if( stats == null || !stats.hasLevel(1) ) {
                it.remove();
            } else {
                l.pruneUnsimulated(sysid);
            }
        }
    }

    // -- Private -- //

		// the intent for loops is to only hold top loops
    private Set<Loop> loops = Util.newHashSet();
    private List<Block> blocks = Util.newArrayList(1);
    private Set<Block> blockCallers = Util.newHashSet();

    private static Integer cloneid = 0;
}

