package PSaPP.sim;
/*
Copyright (c) 2010, PMaC Laboratories, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

 *  Redistributions of source code must retain the above copyright notice, this list of conditions
and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  Neither the name of PMaC Laboratories, Inc. nor the names of its contributors may be
used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import PSaPP.pred.*;
import java.util.*;

/**
 * This class encapsulates the data parsed from a .bins file
 * @author bsheely
 */
public class BinsData {

    public List hitRates;
    public List comments;
    public double timeL1;
    public double timeL2;
    public double timeL3;
    public double timeMM;
    public double totalTime;

    public BinsData() {
        hitRates = new ArrayList();
        comments = new ArrayList();
    }
}

class MemoryStats {

    public int cacheLevel; // 0 indicates main memory
    public double time;
    public double percent_total_time;
    public double hitrate_threshold;

    public MemoryStats(int l, double t, double p, double h) {
        cacheLevel = l;
        time = t;
        percent_total_time = p;
        hitrate_threshold = h;
    }
}

class HitRate implements Comparable {

    public int idx;
    public int numBB;
    public long numMemOps;
    public double time;
    public List hitRates;

    public HitRate(int i, int bb, long mem, double t) {
        idx = i;
        numBB = bb;
        numMemOps = mem;
        time = t;
        hitRates = new ArrayList();
    }

    public int compareTo(Object obj) {
        final HitRate that = (HitRate) obj;

        if (this.time < that.time) {
            return 1;
        } else if (this.time > that.time) {
            return -1;
        } else {
            return 0;
        }
    }
}
