package PSaPP.data;

import java.io.*;

import PSaPP.util.*;

/*
 * PmacTrace
 *
 * This class is a map of the pmacTRACE directory
 *
 */
public class PmacTrace {
    public PmacTrace(String dir, TestCase testCase) {
        this.root = new File(dir);
        this.testCase = testCase;
    }

    public PmacTrace(File dir, TestCase testCase) {
        this.root = dir;
        this.testCase = testCase;
    }

    public File getRankPidExtended() {
        return new File(root, "pscinst/" + testCase.shortName() + "/RankPid.extended");
    }

    public File getProcessed() {
        return new File(root, "processed/" + testCase.shortName());
    }

    public File getScratch() {
        return new File(root, "scratch/" + testCase.shortName());
    }

    // -- Private -- //
    private final File root;
    private final TestCase testCase;
}


