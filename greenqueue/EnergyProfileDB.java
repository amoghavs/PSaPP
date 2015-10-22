package PSaPP.greenqueue;

import java.util.*;

public interface EnergyProfileDB {
    public Set<EnergyProfile> allNearest(CodeProfile cp);
    public SortedSet<Long> getFrequencies();
}
