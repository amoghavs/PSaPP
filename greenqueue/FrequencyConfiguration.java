package PSaPP.greenqueue;

import PSaPP.util.*;
import PSaPP.data.BlockID;

import java.util.*;
import java.io.*;

public class FrequencyConfiguration {

    private Map<Integer, Long> rankMaxFreqs = Util.newHashMap();
    private Map<BlockID, Long> bbidMaxFreqs = Util.newHashMap();
    private Set<BlockID> bbids = Util.newHashSet();

    // set max frequency for a rank
    public void setMaxFrequency(int rank, long freq) {
        rankMaxFreqs.put(rank, freq);
    }

    // set max frequency for a bbid
    public void setMaxFrequency(BlockID bbid, long freq) {
        bbidMaxFreqs.put(bbid, freq);
        bbids.add(bbid);
    }

    public Long getRankMaxFrequency(int rank) {
        return rankMaxFreqs.get(rank);
    }

    public boolean writeThrottlePoints(String filename) {
        try {
            BufferedWriter file = new BufferedWriter(new FileWriter(filename));
            try {
                for( Iterator<BlockID> it = this.bbids.iterator(); it.hasNext(); ) {
                    BlockID bbid = it.next();
                    file.write(bbid.blockHash + "\n"); // FIXME ignores imgid
                }

            } finally {
                file.close();
            }
        } catch (IOException e) {
            Logger.error(e, "Unable to write throttle point file " + filename);
            return false;
        }
        return true;
    }

    public boolean writeFreqConfig(String filename) {

        try {
            BufferedWriter file = new BufferedWriter(new FileWriter(filename));
            try {

                for( Iterator<Map.Entry<Integer, Long>> rit = rankMaxFreqs.entrySet().iterator(); rit.hasNext(); ) {
                    Map.Entry<Integer, Long> ent = rit.next();
                    Integer rank = ent.getKey();
                    Long freq = ent.getValue();
                    file.write(rank + " " + freq + "\n");
                }

                for( Iterator<BlockID> it = bbids.iterator(); it.hasNext(); ) {
                    BlockID bbid = it.next();
                    Long bbMaxFreq = bbidMaxFreqs.get(bbid);

                    if( bbMaxFreq != null ) {
                        file.write(bbid + " * " + bbMaxFreq + "\n");
                    }
                }
            } finally {
                file.close();
            }
        } catch (IOException e) {
            Logger.error(e, "Unable to write frequency configuration file " + filename);
            return false;
        }
        return true;
    }
}
