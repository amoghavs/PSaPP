package PSaPP.data;

public class BinnedCounter implements Comparable<BinnedCounter> {
    public Long lowerBound;
    public Long upperBound;
    public Long counter;

    public BinnedCounter(long lower_, long upper_, long counter_) {
        lowerBound = lower_;
        upperBound = upper_;
        counter = counter_;
    }

    public int compareTo(BinnedCounter other) {
        int res = lowerBound.compareTo(other.lowerBound);
        if(res == 0) {
            return upperBound.compareTo(other.upperBound);
        } else {
            return res;
        }
    }
};

