package PSaPP.util;
/*
Copyright (c) 2011, PMaC Laboratories, Inc.
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


import java.io.*;
import java.net.*;
import java.util.*;


public class GaInput {
    public static final int PLATEAU_COUNT = 9;

    Vector inputs;
    int levels = 0;
    int lineSizes[];
    
    public static double computeMissingHitrate(int lineSize, int stride){
        double line = (double)lineSize;
        if (stride != 2 && stride != 4 && stride != 8){
            Logger.error("Stride should be a power of two");
        }
        double hr = (1 - (1 / ((line / 8) / stride))) * 100;
        return hr;
    }

    public GaInput(String fileName, int l1LineSize, int l2LineSize, int l3LineSize){
        inputs = new Vector();
        lineSizes = new int[3];
        lineSizes[0] = l1LineSize;
        lineSizes[1] = l2LineSize;
        lineSizes[2] = l3LineSize;
        try {
            FileReader fileReader = new FileReader(new File(fileName));
            LineNumberReader reader = new LineNumberReader(fileReader);
            String line;
            while ((line = reader.readLine()) != null) {
                String cleanLine = Util.cleanComment(line);
                StringTokenizer tokenizer = new StringTokenizer(cleanLine);

                if (tokenizer.countTokens() > 0){
                    String token = tokenizer.nextToken();
                    if (token.matches("\\+")){
                        token = tokenizer.nextToken();
                        levels = (new Integer(token)).intValue();
                    } else if (token.matches("<")){
                        GaInputLine gaLine = new GaInputLine(cleanLine);
                        inputs.add(gaLine);
                    } else {
                        Logger.warn("Ignoring GA input file line: " + line);
                    }
                }
            }
        } catch (Exception e) {
            Logger.error(e, "Exception while trying to read GA Input file " + fileName);
        }
    }

    public int levelCount() { 
        return levels; 
    }

    public GaInputLine getLine(int idx) {
        return (GaInputLine)inputs.get(idx);
    }

    public String toString() {
        String stringified = "";
        for (int i = 0; i < inputs.size(); i++){
            stringified += getLine(i).toString() + "\n";            
        }
        return stringified;
    }

    public Double getBandwidthFromHitrate(double l1, double l2, double l3){
        for (int i = 0; i < inputs.size(); i++){
            GaInputLine inp = getLine(i);
            if (l1 == inp.getL1() && l2 == inp.getL2() && l3 == inp.getL3()){
                return inp.getBandwidth();
            }
        }
        return null;
    }

    public Double[] getPlateauBandwidths(){
        Double[] bws = new Double[PLATEAU_COUNT];

        // Stride 2
        bws[0] = getBandwidthFromHitrate(computeMissingHitrate(lineSizes[0], 2),
                                         100.0,
                                         100.0);
        bws[1] = getBandwidthFromHitrate(computeMissingHitrate(lineSizes[0], 2),
                                         computeMissingHitrate(lineSizes[1], 2),
                                         100.0);
        bws[2] = getBandwidthFromHitrate(computeMissingHitrate(lineSizes[0], 2),
                                         computeMissingHitrate(lineSizes[1], 2),
                                         computeMissingHitrate(lineSizes[2], 2));

        // Stride 4
        bws[3] = getBandwidthFromHitrate(computeMissingHitrate(lineSizes[0], 4),
                                         100.0,
                                         100.0);
        bws[4] = getBandwidthFromHitrate(computeMissingHitrate(lineSizes[0], 4),
                                         computeMissingHitrate(lineSizes[1], 4),
                                         100.0);
        bws[5] = getBandwidthFromHitrate(computeMissingHitrate(lineSizes[0], 4),
                                         computeMissingHitrate(lineSizes[1], 4),
                                         computeMissingHitrate(lineSizes[2], 4));

        // Stride 8
        bws[6] = getBandwidthFromHitrate(computeMissingHitrate(lineSizes[0], 8),
                                         100.0,
                                         100.0);
        bws[7] = getBandwidthFromHitrate(computeMissingHitrate(lineSizes[0], 8),
                                         computeMissingHitrate(lineSizes[1], 8),
                                         100.0);
        bws[8] = getBandwidthFromHitrate(computeMissingHitrate(lineSizes[0], 8),
                                         computeMissingHitrate(lineSizes[1], 8),
                                         computeMissingHitrate(lineSizes[2], 8));

        return bws;
    }

    public Double[] getPlateauBWRatios(){
        Double l1bw = getBandwidthFromHitrate(100.0, 100.0, 100.0);
        assert(l1bw != null);
        Double[] platBWs = getPlateauBandwidths();
        Double[] platRatios = new Double[PLATEAU_COUNT];
        for (int i = 0; i < PLATEAU_COUNT; i++){
            if (platBWs[i] != null){
                platRatios[i] = new Double(l1bw.doubleValue() / platBWs[i].doubleValue());
            } else {
                platRatios[i] = null;
            }
        }
        return platRatios;
    }
}

class GaInputLine {
    int levels;
    Integer zone;
    Double[] hitRates;
    Double bandwidth; 

    public GaInputLine(String line){
        StringTokenizer tokenizer = new StringTokenizer(line);
        levels = tokenizer.countTokens() - 3;
        assert(levels > 1 && levels < 4);

        hitRates = new Double[levels];
        zone = null;
        bandwidth = null;

        int count = 0;
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (count == 0){
            } else if (count <= levels) {
                hitRates[count-1] = new Double(token);
            } else if (count == levels + 1) {
                bandwidth = new Double(token);
            } else if (count == levels + 2) {
                zone = new Integer(token.replaceAll("zone", ""));
            } else {
                Logger.error("Malformed ga input line: " + line);
            }
            count++;
        }
        assert(verify());
    }

    public String toString(){
        String stringified = "<\t";
        for (int i = 0; i < levels; i++){
            stringified += hitRates[i].toString() + "\t";
        }
        stringified += bandwidth.toString() + "\tzone" + zone.toString();
        return stringified;
    }

    public boolean verify(){
        if (bandwidth == null) return false;
        if (zone == null) return false;
        for (int i = 0; i < levels; i++){
            if (hitRates[i] == null){
                return false;
            }
        }
        return true;
    }

    public int levelCount() { return levels; }
    public Integer getZone() { return zone; }
    public Double getBandwidth() { return bandwidth; }

    public double getL1() { if (hitRates[0] != null) { return hitRates[0].doubleValue(); } return 0.0; }
    public double getL2() { if (hitRates[1] != null) { return hitRates[1].doubleValue(); } return 0.0; }
    public double getL3() { if (hitRates[2] != null) { return hitRates[2].doubleValue(); } return 0.0; }
}
