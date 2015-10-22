package PSaPP.greenqueue;

import java.util.*;
import java.text.DecimalFormat;

import PSaPP.data.*;
import PSaPP.util.*;

public class LoopProfile extends AggregatorProfile {

    // Loop Profile Methods
    public BlockID getEntryPoint() {
        return this.cfgLoop.loopInfo.headBlock;
    }

    public Long getEntryCount() {
        return this.cfgLoop.getEntryCount();
    }

    public Long getDynamicInsns() {
        return this.dInsns;
    }

    public Collection<LoopProfile> getSubProfiles() {
        return this.subprofiles;
    }

    public void setSubProfiles(Collection<LoopProfile> subs) {
        this.subprofiles = subs;
    }

    public LoopProfile(PSaPP.cfg.Loop cfgLoop, Integer sysid) {
        super(cfgLoop.getScaledAggregator(), sysid);
        this.cfgLoop = cfgLoop;
				this.aggLoop = cfgLoop.getScaledAggregator();
        this.loopHead = getEntryPoint();
    }

    public static List<LoopProfile> sorted(Collection<LoopProfile> loops) {
        List<LoopProfile> sortedLoops = new ArrayList<LoopProfile>(loops);
        Collections.sort(sortedLoops, new Comparator<LoopProfile>() {
            public int compare(LoopProfile l1, LoopProfile l2) {
                return l1.getDynamicInsns().compareTo(l2.getDynamicInsns());
            }
        });
        return sortedLoops;
    }

		public String getOwner()
		{
			return this.cfgLoop.getFunction().funcInfo.describe();
		}
		
		public String getFunction()
		{
			return this.cfgLoop.loopInfo.functionName;
		}

    public String toString() {
        DecimalFormat fmt = new DecimalFormat("#.###E0");
        String ret =  getEntryPoint().toString() + ":" + cfgLoop.loopInfo.functionID + ":" + fmt.format(getEntryCount()) + ":" + super.toString();
        if( subprofiles == null ) {
            return ret;
        }

        List<LoopProfile> sortedSubs = LoopProfile.sorted(subprofiles);
        Collections.reverse(sortedSubs);
        for( Iterator<LoopProfile> it = sortedSubs.iterator(); it.hasNext(); ) {
            LoopProfile sub = it.next();
            ret += "\n" + sub.toString();
        }
        return ret.replaceAll("\n", "\n\t");
    }

    public String getSubLoopHeads() {
        String heads = "";
        if( subprofiles == null ) {
            return heads;
        }
        for( Iterator<LoopProfile> it = subprofiles.iterator(); it.hasNext(); ) {
            LoopProfile l = it.next();
            heads += l.getEntryPoint() + "\n";
            heads += l.getSubLoopHeads();
        }
        return heads;
    }



    // -- Private -- //
    private final PSaPP.cfg.Loop cfgLoop;
    private final PSaPP.data.Loop aggLoop;

    private Collection<LoopProfile> subprofiles = null;
}

