package PSaPP.cfg;

import java.util.*;

public interface FlowElement {
    public Collection<Function> getFunctions();
    public Collection<Loop> getLoops();
    public Collection<Block> getBlocks();
    public Long getEntryCount();
    public PSaPP.data.DynamicAggregator getAggregator();
}

