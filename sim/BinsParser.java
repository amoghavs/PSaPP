package PSaPP.sim;
/*
Copyright (c) 2010, The Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

 *  Redistributions of source code must retain the above copyright notice, this list of conditions
and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  Neither the name of the Regents of the University of California nor the names of its contributors may be
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

import PSaPP.util.*;
import PSaPP.pred.*;

import java.util.*;
import java.io.*;

/**
 * This class parses a .bins file and uses the data contained
 * within to populate a BinsData object.
 * @author bsheely
 */
public class BinsParser {

    static final double L1_THRESHOLD = 99.5;
    static final double L2_THRESHOLD = 99.5;
    static final double L3_THRESHOLD = 98.0;

    /**
     * Parse the specified file and populate the data object
     * @param file The .bins file to be parsed
     * @param data The object to be populated with the parse results
     * @return boolean True if successful
     */
    public boolean parse(String file, BinsData data) throws Exception {
        List hitRates = new ArrayList();
        try {
            FileInputStream fstream = new FileInputStream(file);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            boolean pastComment = false;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("#")) {
                    pastComment = true;
                    String tokens[] = line.split("\t");
                    if (tokens.length < 4) {
                        Logger.warn("Parsing error while reading hit rates in " + file);
                        continue;
                    }
                    int idx = Integer.valueOf(tokens[0].trim()).intValue();
                    int numBB = Integer.valueOf(tokens[1].trim()).intValue();
                    long numMemOps = Long.valueOf(tokens[2].trim()).longValue();
                    double time = Double.valueOf(tokens[3].trim()).doubleValue();
                    data.hitRates.add(new HitRate(idx, numBB, numMemOps, time));
                } else if (pastComment) {
                    StringTokenizer tokens = new StringTokenizer(line);
                    List rates = new ArrayList();
                    int tokenCount = 1;
                    while (tokens.hasMoreTokens()) {
                        String token = tokens.nextToken();
                        if (tokenCount > 2) {
                            rates.add(new Double(Double.valueOf(token.trim()).doubleValue()));
                        }
                        ++tokenCount;
                    }
                    hitRates.add(rates);
                }
            }
            in.close();
        } catch (Exception e) {
            throw e;
        }
        Collections.sort(data.hitRates);
        Iterator iter = data.hitRates.iterator();
        int cacheLevels = 1;
        while (iter.hasNext()) {
            HitRate hitRate = (HitRate) iter.next();
            hitRate.hitRates = (List) hitRates.get(hitRate.idx);
            if (hitRate.hitRates.size() > cacheLevels) {
                cacheLevels = hitRate.hitRates.size();
            }
            if (((Double) hitRate.hitRates.get(0)).doubleValue() >= L1_THRESHOLD) {
                data.timeL1 += hitRate.time;
            } else if (cacheLevels > 1 && ((Double) hitRate.hitRates.get(1)).doubleValue() >= L2_THRESHOLD) {
                data.timeL2 += hitRate.time;
            } else if (cacheLevels > 2 && ((Double) hitRate.hitRates.get(2)).doubleValue() >= L3_THRESHOLD) {
                data.timeL3 += hitRate.time;
            } else {
                data.timeMM += hitRate.time;
            }
            data.totalTime += hitRate.time;
        }
        data.comments.add(new MemoryStats(1, data.timeL1, data.timeL1 / data.totalTime * 100, L1_THRESHOLD));
        if (cacheLevels == 2) {
            data.comments.add(new MemoryStats(2, data.timeL2, data.timeL2 / data.totalTime * 100, L2_THRESHOLD));
        }
        if (cacheLevels == 3) {
            data.comments.add(new MemoryStats(2, data.timeL2, data.timeL2 / data.totalTime * 100, L2_THRESHOLD));
            data.comments.add(new MemoryStats(3, data.timeL3, data.timeL3 / data.totalTime * 100, L3_THRESHOLD));
        }
        data.comments.add(new MemoryStats(0, data.timeMM, data.timeMM / data.totalTime * 100, 0));
        return true;
    }
}
