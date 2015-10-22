package PSaPP.greenqueue;

import java.util.*;
import java.lang.Math;
import java.text.*;

import PSaPP.data.*;
import PSaPP.util.*;


public class CodeProfile {
    public static double MAX_FP_RATIO = 16.0;
    public static double MIN_FP_RATIO = 1.0 / 16.0;

    public BlockID loopHead;
    public Long l1Misses;
    public Long l2Misses;
    public Long l3Misses;
    public Long dFpOps;
    public Long dMemOps;
    public Long dInsns;
    public Double idu;
    public Double fdu;

    public Double l3MPI;
    public Double l2MPI;
    public Double l1MPI;

    public CodeProfile() {
        l1Misses = null;
        l2Misses = null;
        l3Misses = null;
        dFpOps = null;
        dMemOps = null;
        dInsns = null;
        idu = null;
        fdu = null;
        l3MPI = null;
        l2MPI = null;
        l1MPI = null;
    }


    public <T extends CodeProfile> Collection<T> getSubProfiles() {
        return null;
    }

    public String toString() {

        DecimalFormat fmt = new DecimalFormat("0.00");
        DecimalFormat fmt2 = new DecimalFormat("#.###E0");

        return Util.join(new String[] {
            "DINS=" + fmt2.format(dInsns),
            "L1M=" + fmt.format(l1MissesPerInst()),
            "L2M=" + fmt.format(l2MissesPerInst()),
            "L3M=" + fmt.format(l3MissesPerInst()),
            //"IDU=" + fmt.format(iDud()),
            //"FDU=" + fmt.format(fDud()),
            //"FPI=" + fmt.format(fpToIns()),
            //"MPI=" + fmt.format(memToIns()),
            "FMR=" + fmt.format(fmr())},
            " ");
    }

    public Double l1MissesPerInst() {
        return l1Misses.doubleValue() / dInsns.doubleValue();
    }

    public Double l2MissesPerInst() {
        return l2Misses.doubleValue() / dInsns.doubleValue();
    }

    public Double l3MissesPerInst() {
        return l3Misses.doubleValue() / dInsns.doubleValue();
    }

    public Double fpRatio() {
        if( this.dFpOps == null || this.dFpOps.equals(0L) ) {
            return MIN_FP_RATIO;
        }
        if( this.dMemOps == null || this.dMemOps.equals(0L) ) {
            return MAX_FP_RATIO;
        }
        return this.dFpOps.doubleValue() / this.dMemOps.doubleValue();
    }

    public Double fpToIns() {
        return dFpOps.doubleValue() / dInsns.doubleValue();
    }

    public Double memToIns() {
        return dMemOps.doubleValue() / dInsns.doubleValue();
    }

    public Double fmr() {
        return 0.5 + 0.5 * ((dFpOps.doubleValue() - dMemOps.doubleValue()) / (dFpOps.doubleValue() + dMemOps.doubleValue()));
    }

    public Double iDud() {
        return idu;
    }

    public Double fDud() {
        return fdu;
    }

    public static Double difference(CodeProfile c1, CodeProfile c2) {
        return null;
    }

}
