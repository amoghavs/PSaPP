package PSaPP.cfg;

import java.util.*;

import PSaPP.util.*;

public class Block implements FlowElement, Comparable<Block> {
    final public PSaPP.data.BasicBlock blockInfo;

    public Block(PSaPP.data.BasicBlock blockInfo) {
        this.blockInfo = blockInfo;
    }

    public Block clone() {
        Block b = new Block(this.blockInfo);
        b.setCallTarget(callTarget);
        b.setFunction(function);
        b.setLoop(loop);
        return b;
    }

    public Long countFunctionCalls() {
        if(callTarget != null) {
            return blockInfo.visitCount;
        } else {
            return 0L;
        }
    }

    public List<Function> getFunctions() {
        List<Function> l = Util.newLinkedList();
        if( callTarget != null ) {
            l.add(callTarget);
        }
        return l;
    }

    public Set<Loop> getLoops() {
        return null;
    }

    public List<Block> getBlocks() {
        return null;
    }

    public int compareTo(Block other) {
        return blockInfo.bbid.compareTo(other.blockInfo.bbid);
    }

    public Function getFunction() {
        return this.function;
    }

    public Long getEntryCount() {
        return null;
    }

    public PSaPP.data.DynamicAggregator getAggregator() {
        throw new UnsupportedOperationException();
    }

    public Loop getLoop() {
        return this.loop;
    }

    public Function getCallTarget() {
        return this.callTarget;
    }

    // -- Package Private -- //
    void setFunction(Function f) {
        this.function = f;
    }

    void setLoop(Loop l) {
        this.loop = l;
    }

    void setCallTarget(Function f) {
        this.callTarget = f;
    }

    // -- Private -- //
    private Function callTarget = null;
    private Function function = null;
    private Loop loop = null;
}

