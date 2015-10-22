package PSaPP.cfg;

import java.util.*;

import PSaPP.util.*;

public class Loop implements FlowElement, Comparable<Loop> {
    public final PSaPP.data.Loop loopInfo;

    public Loop(PSaPP.data.Loop loopInfo) {
        this.loopInfo = loopInfo;
        setEntryCount(loopInfo.entryCount);
    }

    public Loop clone() {
        Loop l = new Loop(loopInfo);
				l.setEntryCount(this.getEntryCount());	
				l.setFunction(this.getFunction());	
			
        // clone each immediate child block
        for( Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next().clone();
            l.addBlock(b);
            b.setLoop(l);
        }

        // clone each immediate child loop
        for( Iterator<Loop> it = loops.iterator(); it.hasNext(); ) {
            Loop sub = it.next().clone();
            sub.parent = l;
            l.addLoop(sub);
        }
        
        return l;
    }

    public Long countFunctionCalls() {
        Long total = 0L;
        for(Iterator<Block> it = blocks.iterator(); it.hasNext();) {
            Block b = it.next();
            total += b.countFunctionCalls();
        }
        return total;
    }

    public List<Function> getFunctions() {
        List<Function> funcs = Util.newLinkedList();
        for( Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            funcs.addAll(it.next().getFunctions());
        }
        return funcs;
    }

    public Set<Loop> getLoops() {
        return loops;
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

    public Collection<Block> getBlocks() {
        return this.blocks;
    }

    public Collection<Block> getAllBlocks() {
        List<Block> blocks = Util.newLinkedList();
        blocks.addAll(this.blocks);
        for( Iterator<Loop> it = this.loops.iterator(); it.hasNext(); ) {
            Loop l = it.next();
            blocks.addAll(l.getAllBlocks());
        }
        return blocks;
    }

    public Function getFunction() {
        return this.owner;
    }

    public int compareTo(Loop loop2) {
        return (this.loopInfo.headBlock.compareTo(loop2.loopInfo.headBlock));
    }

    public Loop getParent() {
        return this.parent;
    }

    public String toString() {
        String ret =  loopInfo.headBlock + ":" + loopInfo.functionID;
        for( Iterator<Loop> it = loops.iterator(); it.hasNext(); ) {
            ret += "\n" + it.next();
        }
        ret = ret.replaceAll("\n", "\n\t");
        return ret;
        
    }

    public Long getEntryCount() {
        return this.entryCount;
    }

    public PSaPP.data.DynamicAggregator getAggregator() {
        return this.loopInfo;
    }

    public PSaPP.data.Loop getScaledAggregator() {
        PSaPP.data.Loop l = new PSaPP.data.Loop();
        double scale = (double)this.entryCount / (double)loopInfo.entryCount;
        this.loopInfo.scaleAggregator(scale, l);
        l.headBlock = loopInfo.headBlock;
        l.parentHead = loopInfo.parentHead;
        l.depth = loopInfo.depth;
        l.functionID = loopInfo.functionID;
        return l;
    }

    public Long getDynamicInsns() {
        if( this.loopInfo.entryCount == 0 ) {
            return 0L;
        }
        Double ratio = this.entryCount.doubleValue() / ((Long)this.loopInfo.entryCount).doubleValue();
        return ((Double)(this.loopInfo.dInsns * ratio)).longValue();
    }

    // -- Package Private -- //

    Loop parent = null;

    void addLoop(Loop loop) {
        this.loops.add(loop);
    }

    void addLoops(Collection<Loop> loops) {
        for( Iterator<Loop> it = loops.iterator(); it.hasNext(); ) {
            Loop l = it.next();
            this.loops.add(l);
        }
    }

    void addBlock(Block b) {
        this.blocks.add(b);
    }

    void setFunction(Function f) {
        this.owner = f;
    }

    void setEntryCount(Long entryCount) {
        this.entryCount = entryCount;
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
    private Function owner = null;
    private Long entryCount;

    // direct sub-loops
    private final Set<Loop> loops = Util.newHashSet();

    // directly contained blocks. i.e. each block has loop head here
    private final List<Block> blocks = Util.newArrayList(1);
}

