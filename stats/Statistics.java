package PSaPP.stats;
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
import PSaPP.data.*;
import java.util.*;
import java.io.*;

class HitRateBins {
    
    static final double binBeginInterval = 0.25;
    static final double binEndInterval   = 64.0;

    int      levelCount;
    int      binCount;
    long[]   bbfrequency;
    long[]   memOpFrequency;
    double[] memOpTime;
    double[] binMarkers;
    
    double[] currentHits;

    HitRateBins(int lvl){
        int cnt = 0;
        for(double d=binBeginInterval;d<binEndInterval;d = d * 2.0){
            ++cnt;
        }
        binMarkers = new double[++cnt];
        int idx = 0;
        for(double d=binBeginInterval;d<binEndInterval;d = d * 2.0){
            binMarkers[idx++] = 100.0-d;
        }
        binMarkers[idx] = CacheStats.INVALID_HIT_RATE;
        binCount = (int)Math.pow(cnt,lvl);
        levelCount = lvl;

        bbfrequency = new long[binCount];
        memOpFrequency = new long[binCount]; 
        memOpTime = new double[binCount];

        for(int i=0;i<binCount;i++){
            bbfrequency[i] = 0;
            memOpFrequency[i] = 0;
            memOpTime[i] = 0.0;
        }
        currentHits = new double[levelCount];

    }

    int findBinIdx(){
        int retVal = 0;
        for(int i=0;i<levelCount;i++){
            int whichMarker = 0;
            for(int j=0;j<binMarkers.length;j++){
                if(currentHits[i] > binMarkers[j]){
                    whichMarker = j;
                    break;
                }
            }
            retVal = retVal * binMarkers.length + whichMarker;
        }
        return retVal;
    }
    String getMarkerIdxString(int idx){
        String retValue = "";
        for(int i=0;i<levelCount;i++){
            int markerIdx = idx % binMarkers.length;
            retValue = (markerIdx + ".") + retValue;
            idx = idx / binMarkers.length;
        }
        return retValue;
    }
    String getBinHitsString(int idx){
        String retValue = "";
        for(int i=0;i<levelCount;i++){
            int markerIdx = idx % binMarkers.length;
            retValue = (Format.format4d(binMarkers[markerIdx]) + "\t") + retValue;
            idx = idx / binMarkers.length;
        }
        return retValue;
    }

    void addMemoryTime(Object[] bbfields,double t){
        for(int i=0;i<levelCount;i++){
            currentHits[i] = ((Double)bbfields[3+i]).doubleValue();
        }
        int binIdx = findBinIdx();
        assert (binIdx >= 0) && (binIdx < binCount);

        bbfrequency[binIdx]++;
        memOpFrequency[binIdx] += ((Long)bbfields[2]).longValue();
        memOpTime[binIdx] += t;
    }

    void print(BufferedWriter outputFile){
        try {
            outputFile.write("#\t");
            for(int i=0;i<binMarkers.length;i++){
                outputFile.write(" " + Format.format2d(binMarkers[i]));
            }
            outputFile.write("\n# BlockBin\tbinidx\tbbcnt\tmemop\ttime #\tmarker\n");
            for(int i=0;i<binCount;i++){
                if(bbfrequency[i] != 0){
                    outputFile.write("BlockBin\t" + i + "\t" + bbfrequency[i] + "\t" + memOpFrequency[i] + "\t" +
                                     Format.format4d(memOpTime[i]) + " #\t" + getMarkerIdxString(i) + "\n");
                }
            }
            outputFile.write("\n# BinIdent\tbinidx\tbb\tL1hr\tL2hr\tL3hr\n");
            for(int i=0;i<binCount;i++){
                outputFile.write("BinIdent\t" + i + "\t" + getBinHitsString(i) + "\n");
            }
        } catch (Exception e){
            Logger.warn("Can not write the .bins file\n" + e);
        }
    }
}


class PerFunctionTimes {

    String[] functionNames;
    HashMap<BlockID, Integer>  blockIdToFuncId;
    double[] computationTimes;
    double[][] averageHitrates; /* LAURA store running total in average then compute avg with count */
    double[] countHitrates; /* LAURA used to compute the avg */
    int levelCount; /* LAURA */
    double[] currentHits; /* LAURA */
    double   otherBBTimes;
    /*LAURA need to pass in lvl */
    PerFunctionTimes(String[] names,HashMap<BlockID, Integer> bb2fid, int lvl){
    /* LAURA changed from PerFunctionTimes(String[] names,HashMap bb2fid){  */
        assert (names != null) && (bb2fid != null);

        functionNames = names;
        blockIdToFuncId = bb2fid;
        computationTimes = new double[names.length];
        averageHitrates = new double[names.length][lvl]; /*LAURA */
        countHitrates = new double[names.length]; /* LAURA */
        for(int i=0;i<names.length;i++){
            computationTimes[i] = 0.0;
            countHitrates[i] = 0.0; 		/* LAURA */
            for (int j=0; j<lvl;j++){		/*LAURA*/
               averageHitrates[i][j]=0.0;	/*LAURA*/
            }		 			/*LAURA*/
            //if (i == 0 ) { Logger.inform("LAURA zeroing perFunc times"); }
        }
        otherBBTimes = 0.0;
        levelCount = lvl; /*LAURA */
        currentHits = new double[levelCount]; /*LAURA */
    }

    void addTimes(Object[] bbfields,double memTime,double fpTime){ 
        BlockID bbid = new BlockID(0L, (Long)bbfields[0]);
        Integer funcId = blockIdToFuncId.get(bbid);

        /* LAURA computing the average hitrate per function need sumHitrates and countHitrates to compute avg */
        /* first get hitrates */
        for(int i=0;i<levelCount;i++){
            currentHits[i] = ((Double)bbfields[3+i]).doubleValue();
            // Logger.inform("LAURA hits are " + i + "  " + currentHits[i]);
           //LAURA HERE 
        }

        if(funcId != null){
            computationTimes[funcId.intValue()] += (memTime + fpTime);
            /* place sum in average then at print time compute average*/
            //Logger.inform("LAURA time is  " + memTime + "  " + fpTime);
            for(int j=0;j<levelCount;j++){
               averageHitrates[funcId.intValue()][j] += currentHits[j]*(memTime + fpTime); 
            }
            //if (funcId.intValue() == 0 ) { Logger.inform("LAURA time is  " + memTime + "  " + fpTime + "hit=" + currentHits[0]  + "tot=" + averageHitrates[funcId.intValue()][0]); }
            countHitrates[funcId.intValue()] += 1;

        } else {
            otherBBTimes += (memTime + fpTime);
        }
    }

    // print function info by name
    void print(BufferedWriter outputFile){

        try {
            outputFile.write("#\t\t\tHit rates are weighted average by time\n");
            
            outputFile.write("# name\tcomptime\tl1hr\tl2hr\tl3hr\n");

            for(int i = 0; i < functionNames.length; i++){
                for (int j = 0; j < levelCount; j++){
                    if (computationTimes[i] > 0.0 ){
                        averageHitrates[i][j] = averageHitrates[i][j]/computationTimes[i];
                    } else {
                        averageHitrates[i][j] = 0.0;
                    }
                }

                Double l1hr = averageHitrates[i][0];
                Double l2hr = CacheStats.INVALID_HIT_RATE;
                Double l3hr = CacheStats.INVALID_HIT_RATE;

                if (levelCount > 1){
                    l2hr = averageHitrates[i][1];
                }
                if (levelCount > 2){
                    l3hr = averageHitrates[i][2];
                }

                outputFile.write(functionNames[i] + "\t" + Format.format4d(computationTimes[i])                 
                                 + "\t" + Format.format4d(l1hr)
                                 + "\t" + Format.format4d(l2hr)
                                 + "\t" + Format.format4d(l3hr) + "\n");
            }
            outputFile.write("<others>\t" + otherBBTimes);
        } catch (Exception e){
            Logger.warn("Can not write per function times\n" + e);
        }
    }
}

class PerTaskCompEnergy {

    double[] computationEnergy;

    PerTaskCompEnergy(int tskCnt){
        computationEnergy = new double[tskCnt];
        for(int i=0;i<tskCnt;i++){
            computationEnergy[i] = 0.0;
        }
    }

    void addEnergy(int tskId,double e){
        assert (tskId >= 0) && (tskId < computationEnergy.length);
        computationEnergy[tskId] += e;
    }

    void print(BufferedWriter outputFile){
        try {
            double min = 1.0e9;
            double max = -1.0e9;
            double sum = 0.0;
            outputFile.write("#\ttask\tcompenrgy\n");
            for(int i=0;i<computationEnergy.length;i++){
                outputFile.write(i + "\t" + Format.format4d(computationEnergy[i]) + "\n");
                sum += computationEnergy[i];
                if(min > computationEnergy[i]){
                    min = computationEnergy[i];
                }
                if(max < computationEnergy[i]){
                    max = computationEnergy[i];
                }
            }
            outputFile.write("sum\t" + Format.format4d(sum) + "\n");
            outputFile.write("avg\t" + Format.format4d(sum/computationEnergy.length) + "\n");
            outputFile.write("min\t" + Format.format4d(min) + "\n");
            outputFile.write("max\t" + Format.format4d(max) + "\n");

        } catch (Exception e){
            Logger.warn("Can not write per per task energy\n" + e);
        }
    }
}

class PerTaskCompTimes {

    double[] computationTimes;

    PerTaskCompTimes(int tskCnt){
        computationTimes = new double[tskCnt];
        for(int i=0;i<tskCnt;i++){
            computationTimes[i] = 0.0;
        }
    }

    void addTimes(int tskId,double memTime,double fpTime){
        assert (tskId >= 0) && (tskId < computationTimes.length);
        computationTimes[tskId] += (memTime + fpTime);
    }
    void print(BufferedWriter outputFile){
        try {
            double min = 1.0e9;
            double max = -1.0e9;
            double sum = 0.0;
            outputFile.write("#\ttask\tcomptime\n");
            for(int i=0;i<computationTimes.length;i++){
                outputFile.write(i + "\t" + Format.format4d(computationTimes[i]) + "\n");
                sum += computationTimes[i];
                if(min > computationTimes[i]){
                    min = computationTimes[i];
                }
                if(max < computationTimes[i]){
                    max = computationTimes[i];
                }
            }
            outputFile.write("sum\t" + Format.format4d(sum) + "\n");
            outputFile.write("avg\t" + Format.format4d(sum/computationTimes.length) + "\n");
            outputFile.write("min\t" + Format.format4d(min) + "\n");
            outputFile.write("max\t" + Format.format4d(max) + "\n");

        } catch (Exception e){
            Logger.warn("Can not write per per task times\n" + e);
        }
    }
}

class PerBlockTimes {
    HashMap[] taskToBBToTimes;

    PerBlockTimes(int tskCnt){
        taskToBBToTimes = new HashMap[tskCnt];
        for(int i=0;i<tskCnt;i++){
            taskToBBToTimes[i] = new HashMap();
        }
    }

    void addTimes(Long bbid,int tskId,double memTime,double fpTime){
        assert (tskId >= 0) && (tskId < taskToBBToTimes.length);
        HashMap currMap = taskToBBToTimes[tskId];
        assert (currMap != null); 
        Double currValue = (Double)currMap.get(bbid);
        if(currMap.get(bbid) == null){
            currValue = new Double(0.0);
        }
        currValue = new Double(currValue.doubleValue() + memTime + fpTime);
        currMap.put(bbid,currValue);
    }
    void print(BufferedWriter outputFile){
        try {
            double[] perTaskTotals = new double[taskToBBToTimes.length];
            for(int i=0;i<taskToBBToTimes.length;i++){
                perTaskTotals[i] = 0;
                HashMap bbMap = taskToBBToTimes[i];
                Iterator it = bbMap.keySet().iterator();
                while(it.hasNext()){
                    Long bbid = (Long)it.next();
                    Double val = (Double)bbMap.get(bbid);
                    perTaskTotals[i] += val.doubleValue();
                }
            }
            HashMap bbTimeTotals = new HashMap();
            for(int i=0;i<taskToBBToTimes.length;i++){
                HashMap bbMap = taskToBBToTimes[i];
                Iterator it = bbMap.keySet().iterator();
                while(it.hasNext()){
                    Long bbid = (Long)it.next();
                    Double val = (Double)bbMap.get(bbid);
                    if(bbTimeTotals.get(bbid) == null){
                        bbTimeTotals.put(bbid,new Double(val.doubleValue()));
                        //bbTimeTotals.put(bbid,Double(val));
                    } else {
                        bbTimeTotals.put(bbid,new Double(((Double)bbTimeTotals.get(bbid)).doubleValue() + val.doubleValue()));
                    }
                }
            }
            double dfpSum = 0;
            outputFile.write("#\ttask\tbb_comptime\n");
            for(int i=0;i<taskToBBToTimes.length;i++){
                outputFile.write("bbtimes_ptask\t" + i + "\t" + perTaskTotals[i] + "\n");
                dfpSum += perTaskTotals[i];
            }
            outputFile.write("#\ttotal_task " + dfpSum + "\n");
            outputFile.write("#\ttask\tbb_comptime\n");
            Iterator it = bbTimeTotals.keySet().iterator();
            while(it.hasNext()){
                Long bbid = (Long)it.next();
                Double val = (Double)bbTimeTotals.get(bbid);
                outputFile.write("bbtimes_pbb\t" + bbid + "\t" + val + "\n");
            }
        } catch (Exception e){
            Logger.warn("Can not write per basic block times\n" + e);
        }
    }
}

class PerBlockEnergy {
    HashMap[] taskToBBToEnergy;

    PerBlockEnergy(int tskCnt){
        taskToBBToEnergy = new HashMap[tskCnt];
        for(int i=0;i<tskCnt;i++){
            taskToBBToEnergy[i] = new HashMap();
        }
    }

    void addEnergy(Long bbid,int tskId,double e){
        assert (tskId >= 0) && (tskId < taskToBBToEnergy.length);
        HashMap currMap = taskToBBToEnergy[tskId];
        assert (currMap != null); 
        Double currValue = (Double)currMap.get(bbid);
        if(currMap.get(bbid) == null){
            currValue = new Double(0.0);
        }
        currValue = new Double(currValue.doubleValue() + e);
        currMap.put(bbid,currValue);
    }
    void print(BufferedWriter outputFile){
        try {
            double[] perTaskTotals = new double[taskToBBToEnergy.length];
            for(int i=0;i<taskToBBToEnergy.length;i++){
                perTaskTotals[i] = 0;
                HashMap bbMap = taskToBBToEnergy[i];
                Iterator it = bbMap.keySet().iterator();
                while(it.hasNext()){
                    Long bbid = (Long)it.next();
                    Double val = (Double)bbMap.get(bbid);
                    perTaskTotals[i] += val.doubleValue();
                }
            }
            HashMap bbTimeTotals = new HashMap();
            for(int i=0;i<taskToBBToEnergy.length;i++){
                HashMap bbMap = taskToBBToEnergy[i];
                Iterator it = bbMap.keySet().iterator();
                while(it.hasNext()){
                    Long bbid = (Long)it.next();
                    Double val = (Double)bbMap.get(bbid);
                    if(bbTimeTotals.get(bbid) == null){
                        bbTimeTotals.put(bbid,new Double(val.doubleValue()));
                        //bbTimeTotals.put(bbid,Double(val));
                    } else {
                        bbTimeTotals.put(bbid,new Double(((Double)bbTimeTotals.get(bbid)).doubleValue() + val.doubleValue()));
                    }
                }
            }
            double dfpSum = 0;
            outputFile.write("#\ttask\tbb_comptime\n");
            for(int i=0;i<taskToBBToEnergy.length;i++){
                outputFile.write("bbtimes_ptask\t" + i + "\t" + perTaskTotals[i] + "\n");
                dfpSum += perTaskTotals[i];
            }
            outputFile.write("#\ttotal_task " + dfpSum + "\n");
            outputFile.write("#\ttask\tbb_comptime\n");
            Iterator it = bbTimeTotals.keySet().iterator();
            while(it.hasNext()){
                Long bbid = (Long)it.next();
                Double val = (Double)bbTimeTotals.get(bbid);
                outputFile.write("bbtimes_pbb\t" + bbid + "\t" + val + "\n");
            }
        } catch (Exception e){
            Logger.warn("Can not write per basic block times\n" + e);
        }
    }
}

class BWData{
  double[] computationTimes;
  double[] bandwidths;
  int arraySize;
  int dataCount;
  double[] mins  ;
  double[] cumTime ;
  double totalTime;

  BWData(){
    arraySize = 100;
    dataCount = 0;
    computationTimes = new double[arraySize];
    bandwidths = new double[arraySize];
    mins = new double[20];
    cumTime = new double[20];
  }

  void addBWTime(double time,double bw){
    if(dataCount >= arraySize){
      expandArrays();
    }
    computationTimes[dataCount] = time;
    bandwidths[dataCount] = bw;
    totalTime += time;
    dataCount++;
  }
  
  void expandArrays(){
    int newArraySize = arraySize*2;
    int idx=0;
    double[] timeTmp = new double[newArraySize];
    double[] bwTmp = new double[newArraySize];
  
    for(idx=0;idx<arraySize;idx++){
      timeTmp[idx] = computationTimes[idx];
      bwTmp[idx] = bandwidths[idx];
    }
    computationTimes = timeTmp;
    bandwidths = bwTmp;
    arraySize = newArraySize;
  }

  void summarizeHistogram(){
    int idx;
    double min = bandwidths[0];
    double max = bandwidths[0];
    // first find the min and max bandwidths
    for(idx=1;idx<dataCount;idx++){
      if(bandwidths[idx] < min){
        min = bandwidths[idx];
       }
      if(bandwidths[idx] > max){
        max = bandwidths[idx];
      }
    }

    // now create the bins dividing the difference by 20
    double binSize = (max-min)/20;
    idx = 0;
    do{
      mins[idx] = min;
      min += binSize;
      idx++;
     }while(idx<20);

    // last tabulate the bins into a single histogram
    for(idx=0;idx<dataCount;idx++){
      int bin = findBin(bandwidths[idx]);
      cumTime[bin] += computationTimes[idx];
    }
  }

  int findBin(double bw){
    int idx = 0;
    for(;idx<20;idx++){
      if(mins[idx] > bw){
        return idx-1;
      }
    }
    return idx-1;
  }

  void print(BufferedWriter outputFile, int sysid, int rank){
     summarizeHistogram();
     try {
       outputFile.write("#\ttask\tsysid\tbwMin\tcumTime\n");
       for(int i=0;i<20;i++){
          outputFile.write(rank + "\t" + sysid + "\t" + Format.format4d(mins[i]) + "\t" + Format.format4d(cumTime[i]) + "\n");
       }
       outputFile.write("totalTime\t" + Format.format4d(totalTime) + "\n");

        } catch (Exception e){
            Logger.warn("Can not write per per task times\n" + e);
        }
    }
}

class PWRData{
  double[] computationTimes;
  double[] powers;
  int arraySize;
  int dataCount;
  double[] mins  ;
  double[] cumTime ;
  double totalTime;

  PWRData(){
    arraySize = 100;
    dataCount = 0;
    computationTimes = new double[arraySize];
    powers = new double[arraySize];
    mins = new double[20];
    cumTime = new double[20];
  }

  void addPWRTime(double time,double bw){
    if(dataCount >= arraySize){
      expandArrays();
    }
    computationTimes[dataCount] = time;
    powers[dataCount] = bw;
    totalTime += time;
    dataCount++;
  }
  
  void expandArrays(){
    int newArraySize = arraySize*2;
    int idx=0;
    double[] timeTmp = new double[newArraySize];
    double[] bwTmp = new double[newArraySize];
  
    for(idx=0;idx<arraySize;idx++){
      timeTmp[idx] = computationTimes[idx];
      bwTmp[idx] = powers[idx];
    }
    computationTimes = timeTmp;
    powers = bwTmp;
    arraySize = newArraySize;
  }

  void summarizeHistogram(){
    int idx;
    double min = powers[0];
    double max = powers[0];
    // first find the min and max powers
    for(idx=1;idx<dataCount;idx++){
      if(powers[idx] < min){
        min = powers[idx];
       }
      if(powers[idx] > max){
        max = powers[idx];
      }
    }

    // now create the bins dividing the difference by 20
    double binSize = (max-min)/20;
    idx = 0;
    do{
      mins[idx] = min;
      min += binSize;
      idx++;
     }while(idx<20);

    // last tabulate the bins into a single histogram
    for(idx=0;idx<dataCount;idx++){
      int bin = findBin(powers[idx]);
      cumTime[bin] += computationTimes[idx];
    }
  }

  int findBin(double pw){
    int idx = 0;
    for(;idx<20;idx++){
      if(mins[idx] > pw){
        return idx-1;
      }
    }
    return idx-1;
  }

  void print(BufferedWriter outputFile, int sysid, int rank){
     summarizeHistogram();
     try {
       outputFile.write("#\ttask\tsysid\tpwMin\tcumTime\n");
       for(int i=0;i<20;i++){
          outputFile.write(rank + "\t" + sysid + "\t" + Format.format4d(mins[i]) + "\t" + Format.format4d(cumTime[i]) + "\n");
       }
       outputFile.write("totalTime\t" + Format.format4d(totalTime) + "\n");

        } catch (Exception e){
            Logger.warn("Can not write per per task times\n" + e);
        }
    }
}

public class Statistics implements StatCollection {

    public static Object statsLock = new Object() {};

    TestCase testCase;

    boolean  bwHistEnabled;
    boolean  pwrHistEnabled;
    boolean  bbTimeHistEnabled;
    boolean  bbEnergyHistEnabled;

    Map<Integer, int[]> sysIdToMemoryPList;
    HashMap sysIdToBaseRList;
    HashMap sysIdToBinsList;
    Map<Integer, PerFunctionTimes[]> sysIdToPerFuncList;
    HashMap sysIdToPerTaskList;
    HashMap sysIdToPerTaskEnergyList;
    HashMap sysIdToBWDataList;
    HashMap sysIdToPWRDataList;
    HashMap sysIdToBBTimes;
    HashMap sysIdToBBEnergy;
    HashMap includedBBIds;
    HashMap includedBBEIds;

    int[]      networkProfilesList;
    int[]      networkProfilesBaseRList;
    double[]   networkProfilesRatios;
    double[][] networkProfilesCompTimes;
    double[][] networkProfilesCommTimes;
    HashMap[]  networkProfilesMpiTimes;
    
    String[] functionNames;
    HashMap  blockIdToFuncId;

    public Statistics(TestCase tCase){
        testCase = tCase;

        sysIdToMemoryPList = Util.newHashMap();
        sysIdToBaseRList = new HashMap();
        sysIdToBinsList = new HashMap();
        sysIdToPerFuncList = Util.newHashMap();
        sysIdToPerTaskList = new HashMap();

        bwHistEnabled = false;
        pwrHistEnabled = false;
        sysIdToBWDataList = null;
        sysIdToPWRDataList = null;
        bbTimeHistEnabled = false;
        bbEnergyHistEnabled = false;
        includedBBIds = null;
        includedBBEIds = null;
        sysIdToBBTimes = null;
        sysIdToBBEnergy = null;

        functionNames = null;
        blockIdToFuncId = null;
    }

    public void enableBWHistograms(){
        synchronized(statsLock){
        bwHistEnabled = true;
        sysIdToBWDataList = new HashMap();
        }
    }

    public void enablePWRHistograms(){
        synchronized(statsLock){
        pwrHistEnabled = true;
        sysIdToPWRDataList = new HashMap();
        }
    }

    public void enableBBTimeHistograms(String blockListFile){
        synchronized(statsLock){
        if(blockListFile == null){
            return;
        }
        Logger.inform("Reading BB list for time histogram from " + blockListFile);
        bbTimeHistEnabled = true;
        sysIdToBBTimes =  new HashMap();
        try {
            includedBBIds = new HashMap();
            BufferedReader inputFile = new BufferedReader(new FileReader(blockListFile));
            String line = null;
            int lineNo = 0;
            int functionIdx = 0;
            while ((line = inputFile.readLine()) != null) {
                lineNo++;
                line = Util.cleanComment(line);
                line = Util.cleanWhiteSpace(line,false);
                if(line.length() > 0){
                    String[] tokens = line.split("\\s+");
                    if(tokens.length != 1){
                        Logger.warn("Line " + lineNo + " in " + blockListFile + " needs 1 fields");
                        continue;
                    }

                    Long bbId = Util.toLong(line);
                    if(bbId == null){
                        Logger.warn("Line " + lineNo + " in " + blockListFile + " is an invalid unique block id,skipping");
                        continue;

                    }
                    //Logger.debug("Will use basic block in time histogram " + bbId);
                    includedBBIds.put(bbId,bbId);
                }
            }
        } catch (Exception e){
            Logger.warn("Error in reading " + blockListFile + "\n" + e);
            bbTimeHistEnabled = false;
            includedBBIds = null;
            sysIdToBBTimes = null;
        }
        }
    }

    public void enableBBEnergyHistograms(String blockListFile){
        synchronized(statsLock){
        if(blockListFile == null){
            return;
        }
        bbEnergyHistEnabled = true;
        sysIdToBBEnergy =  new HashMap();
        enableBBTimeHistograms(blockListFile);
        }
    }

    public void print(){
        print(null);
    }

    public void addMemoryProfiles(int cachesysid,int[] memoryProfiles,int[] baseResources,int[] levelCounts){
        synchronized(statsLock){

        assert (memoryProfiles != null) && (baseResources != null) && (levelCounts != null);
        assert (memoryProfiles.length == baseResources.length) && (baseResources.length == levelCounts.length);
        Integer sysidkey = new Integer(cachesysid);

        if(sysIdToMemoryPList.containsKey(sysidkey)){
            Logger.warn(cachesysid + " already exists so overwriting");
        }

        HitRateBins[] allbins = new HitRateBins[memoryProfiles.length];
        PerFunctionTimes[] allfunctimes = new PerFunctionTimes[memoryProfiles.length];
        PerTaskCompTimes[] alltasktimes = new PerTaskCompTimes[memoryProfiles.length];
        for(int i=0;i<memoryProfiles.length;i++){
            allbins[i] = new HitRateBins(levelCounts[i]);
            if((functionNames != null) && (blockIdToFuncId != null)){
                allfunctimes[i] = new PerFunctionTimes(functionNames,blockIdToFuncId,levelCounts[i]);
                /* LAURA changed from allfunctimes[i] = new PerFunctionTimes(functionNames,blockIdToFuncId); */
            } else {
                allfunctimes[i] = null;
            }
            alltasktimes[i] = new PerTaskCompTimes(testCase.getCpu());
        }
        sysIdToMemoryPList.put(sysidkey,memoryProfiles);
        sysIdToBaseRList.put(sysidkey,baseResources);
        sysIdToBinsList.put(sysidkey,allbins);
        sysIdToPerFuncList.put(sysidkey,allfunctimes);
        sysIdToPerTaskList.put(sysidkey,alltasktimes);

        if(bbTimeHistEnabled){
            PerBlockTimes[] allblocktimes = new PerBlockTimes[memoryProfiles.length];
            for(int i=0;i<memoryProfiles.length;i++){
                allblocktimes[i] = new PerBlockTimes(testCase.getCpu());
            }
            sysIdToBBTimes.put(sysidkey,allblocktimes);
        }
        }
    }
    /**
 * Method: addBWData
 * Builds the data structures to tabulate all of the bw data into a histogram.
 * The high level hashmap is indexed by the sysid. The array inside this is created using the
 * ranks. This is because ranks will be continuous, whearas sysids may not be.
 * The object that actually holds the data is the BWData object, it is super simple.
 */
    public void addBWData(int cachesysid,int pidx,int cpuId, double time, double bw){
        synchronized(statsLock){

        if(!bwHistEnabled){
            return;
        }
        // first attempt to look the cachesysid up in the hashmap, if it isn't found then we need
        // to create an entry
        Integer sysidkey = new Integer(cachesysid);
        HashMap pidxBWmap  = (HashMap)sysIdToBWDataList.get(sysidkey);
        if(pidxBWmap == null){
          pidxBWmap = new HashMap();
          sysIdToBWDataList.put(sysidkey,pidxBWmap);
        }
        // next look up the profile idx in the map found at the last step
        BWData[] bwDataList = (BWData[])pidxBWmap.get(new Integer(pidx));
        if(bwDataList == null){
          // since we are creating this entry we also need to add it to the hash map so that we
          // find it next time.
          bwDataList = new BWData[testCase.getCpu()];  
          pidxBWmap.put(new Integer(pidx),bwDataList);
         } 
        // now look up the bwData for this cache sys id, profile id, rank combination
        BWData bwData = bwDataList[cpuId];
        if(bwData == null){
          bwData = new BWData();
          bwDataList[cpuId] = bwData;
        }
        bwData.addBWTime(bw,time);
        }
    }

    public void addPWRData(int cachesysid, int pidx, int cpuId, double time, double pwr){
        synchronized(statsLock){

        if(!pwrHistEnabled){
            return;
        }
        // first attempt to look the cachesysid up in the hashmap, if it isn't found then we need
        // to create an entry
        Integer sysidkey = new Integer(cachesysid);
        HashMap pidxPWRmap  = (HashMap)sysIdToPWRDataList.get(sysidkey);
        if(pidxPWRmap == null){
          pidxPWRmap = new HashMap();
          sysIdToPWRDataList.put(sysidkey,pidxPWRmap);
        }
        // next look up the profile idx in the map found at the last step
        PWRData[] pwrDataList = (PWRData[])pidxPWRmap.get(new Integer(pidx));
        if(pwrDataList == null){
          // since we are creating this entry we also need to add it to the hash map so that we
          // find it next time.
          pwrDataList = new PWRData[testCase.getCpu()];  
          pidxPWRmap.put(new Integer(pidx),pwrDataList);
        } 
        // now look up the pwrData for this cache sys id, profile id, rank combination
        PWRData pwrData = pwrDataList[cpuId];
        if(pwrData == null){
          pwrData = new PWRData();
          pwrDataList[cpuId] = pwrData;
        }
        pwrData.addPWRTime(pwr,time);
        }
    }

    public void addComputationTime(int cachesysid,int idx,int cpuId,Object[] bbFields,double memoryT,double fpT){
        synchronized(statsLock){
        Integer sysidkey = new Integer(cachesysid);
        int[] profiles = sysIdToMemoryPList.get(sysidkey);
        assert (profiles != null) && ((idx >= 0) && (idx < profiles.length));

        HitRateBins[] allbins = (HitRateBins[])sysIdToBinsList.get(sysidkey);
        allbins[idx].addMemoryTime(bbFields,memoryT);

        PerFunctionTimes[] allfunctimes = sysIdToPerFuncList.get(sysidkey);
        /* LAURA might break here fix */
        if(allfunctimes[idx] != null){
            allfunctimes[idx].addTimes(bbFields,memoryT,fpT);
        }

        PerTaskCompTimes[] alltasktimes = (PerTaskCompTimes[])sysIdToPerTaskList.get(sysidkey);
        if(alltasktimes[idx] != null){
            alltasktimes[idx].addTimes(cpuId,memoryT,fpT);
        }

        if(bbTimeHistEnabled){
            Long bbid = (Long)bbFields[0];
            PerBlockTimes[] allblocktimes = (PerBlockTimes[])sysIdToBBTimes.get(sysidkey);
            if(includedBBIds.get(bbid) != null){
                allblocktimes[idx].addTimes(bbid,cpuId,memoryT,fpT);
            }
        }
        }
    }

    public void addComputationEnergy(int cachesysid,int idx,int cpuId,Object[] bbFields,double e){
        synchronized(statsLock){
        Integer sysidkey = new Integer(cachesysid);
        int[] profiles = sysIdToMemoryPList.get(sysidkey);
        assert (profiles != null) && ((idx >= 0) && (idx < profiles.length));

        PerTaskCompEnergy[] alltaske = (PerTaskCompEnergy[])sysIdToPerTaskEnergyList.get(sysidkey);
        if(alltaske[idx] != null){
            alltaske[idx].addEnergy(cpuId,e);
        }

        if(bbEnergyHistEnabled){
            Long bbid = (Long)bbFields[0];
            PerBlockEnergy[] allblocke = (PerBlockEnergy[])sysIdToBBEnergy.get(sysidkey);
            if(includedBBEIds.get(bbid) != null){
                allblocke[idx].addEnergy(bbid,cpuId,e);
            }
        }
        }
    }

    public void print(String outputDir){
        synchronized(statsLock){
        if(outputDir != null){
            outputDir += "/stats";
            if(!Util.mkdir(outputDir)){
                Logger.warn("Can not make scratch directory " + outputDir);
                outputDir = null;
            }
            Logger.inform("Printing the statistics for convolver and simulator in " + outputDir);
        } else {
            Logger.inform("Printing the statistics for convolver and simulator to std output");
        }

        if(outputDir != null){
            if(sysIdToMemoryPList != null){
                Iterator it = sysIdToMemoryPList.keySet().iterator();
                while(it.hasNext()){
                    Integer sysidkey = (Integer)it.next();
                    int[] profiles = sysIdToMemoryPList.get(sysidkey);
                    int[] resources = (int[])sysIdToBaseRList.get(sysidkey);
 
                    if(bwHistEnabled){
                        // this is the printing for bw histograms
                        // step one: get the hash map of profile indexes to
                        // BWDatas out of the sysIdtoBWDataList Mapping
                        HashMap pidxBWmap  = (HashMap)sysIdToBWDataList.get(sysidkey);
                        Iterator itProf = pidxBWmap.keySet().iterator();
                        // this is essentially foreach profile
                        for(int j=0;j<profiles.length;j++){
                          Integer pidxkey = new Integer(j);
                          // each profile then has a list of bwData objects based
                          // on rank
                          BWData[] bwDataList = (BWData[])pidxBWmap.get(pidxkey);
                          for(int i=0;i<bwDataList.length;i++){
                            int mpIdx = profiles[j];
                            int brIdx = resources[j];
                            String fileName = outputDir + "/sysid" + sysidkey + "_" + Format.cpuToString(i) + 
                                              "_" + Format.BR(brIdx) + "_" +
                                              Format.MP(mpIdx) + ".bwHist";
                            try {
                                BufferedWriter outputFile = new BufferedWriter(new FileWriter(fileName));
                                bwDataList[i].print(outputFile,sysidkey.intValue(),i);
                                outputFile.close();
                            } catch (Exception e){
                                Logger.warn("Can not write histogram information to " + fileName + "\n" + e);
                            }
                          }
                        }
                    }

                    if(pwrHistEnabled){
                        // this is the printing for pwr histograms
                        // step one: get the hash map of profile indexes to
                        // PWRDatas out of the sysIdtoPWRDataList Mapping
                        HashMap pidxPWRmap  = (HashMap)sysIdToPWRDataList.get(sysidkey);
                        Iterator itProf = pidxPWRmap.keySet().iterator();
                        // this is essentially foreach profile
                        for(int j=0;j<profiles.length;j++){
                          Integer pidxkey = new Integer(j);
                          // each profile then has a list of pwrData objects based
                          // on rank
                          PWRData[] pwrDataList = (PWRData[])pidxPWRmap.get(pidxkey);
                          for(int i=0;i<pwrDataList.length;i++){
                            int mpIdx = profiles[j];
                            int brIdx = resources[j];
                            String fileName = outputDir + "/sysid" + sysidkey + "_" + Format.cpuToString(i) + 
                                              "_" + Format.BR(brIdx) + "_" +
                                              Format.MP(mpIdx) + ".pwrHist";
                            try {
                                BufferedWriter outputFile = new BufferedWriter(new FileWriter(fileName));
                                pwrDataList[i].print(outputFile,sysidkey.intValue(),i);
                                outputFile.close();
                            } catch (Exception e){
                                Logger.warn("Can not write histogram information to " + fileName + "\n" + e);
                            }
                          }
                        }
                    }

                    HitRateBins[] allbins = (HitRateBins[])sysIdToBinsList.get(sysidkey);
                    for(int i=0;i<profiles.length;i++){
                        int mpIdx = profiles[i];
                        int brIdx = resources[i];
                        HitRateBins bins = allbins[i];
                        String fileName = outputDir + "/sysid" + sysidkey + "_" + Format.cpuToString(i) + 
                                          "_" + Format.BR(brIdx) + "_" +
                                          Format.MP(mpIdx) + ".bins";
                        try {
                            BufferedWriter outputFile = new BufferedWriter(new FileWriter(fileName));
                            bins.print(outputFile);
                            outputFile.close();
                        } catch (Exception e){
                            Logger.warn("Can not write binning information to " + fileName + "\n" + e);
                        }
                    }

                    PerFunctionTimes[] allfunctimes = sysIdToPerFuncList.get(sysidkey);
                    for(int i=0;i<profiles.length;i++){
                        PerFunctionTimes ftimes = allfunctimes[i];
                        if(ftimes == null){
                            continue;
                        }
                        int mpIdx = profiles[i];
                        int brIdx = resources[i];
                        String fileName = outputDir + "/sysid" + sysidkey + "_" + Format.cpuToString(i) + 
                                          "_" + Format.BR(brIdx) + "_" +
                                          Format.MP(mpIdx) + ".func";
                        try {
                            BufferedWriter outputFile = new BufferedWriter(new FileWriter(fileName));
                            ftimes.print(outputFile);
                            outputFile.close();
                        } catch (Exception e){
                            Logger.warn("Can not write per function times in " + fileName + "\n" + e);
                        }
                    }

                    PerTaskCompTimes[] alltasks = (PerTaskCompTimes[])sysIdToPerTaskList.get(sysidkey);
                    for(int i=0;i<profiles.length;i++){
                        PerTaskCompTimes ttimes = alltasks[i];
                        if(ttimes == null){
                            continue;
                        }
                        int mpIdx = profiles[i];
                        int brIdx = resources[i];
                        String fileName = outputDir + "/sysid" + sysidkey + "_" + Format.cpuToString(i) + 
                                          "_" + Format.BR(brIdx) + "_" +
                                          Format.MP(mpIdx) + ".task";
                        try {
                            BufferedWriter outputFile = new BufferedWriter(new FileWriter(fileName));
                            ttimes.print(outputFile);
                            outputFile.close();
                        } catch (Exception e){
                            Logger.warn("Can not write per function times in " + fileName + "\n" + e);
                        }
                    }
                    if(bbTimeHistEnabled){
                        PerBlockTimes[] allblocks = (PerBlockTimes[])sysIdToBBTimes.get(sysidkey);
                        for(int i=0;i<profiles.length;i++){
                            PerBlockTimes btimes = allblocks[i];
                            if(btimes == null){
                                continue;
                            }
                            int mpIdx = profiles[i];
                            int brIdx = resources[i];
                            String fileName = outputDir + "/sysid" + sysidkey + "_" + Format.cpuToString(i) +
                                                "_" + Format.BR(brIdx) + "_" +
                                                Format.MP(mpIdx) + ".perbb";
                            try {
                                BufferedWriter outputFile = new BufferedWriter(new FileWriter(fileName));
                                    btimes.print(outputFile);
                                outputFile.close();
                            } catch (Exception e){
                                Logger.warn("Can not write per block times in " + fileName + "\n" + e);
                            }
                        }
                    }
                    if(bbEnergyHistEnabled){
                        PerBlockTimes[] allblocks = (PerBlockTimes[])sysIdToBBTimes.get(sysidkey);
                        for(int i=0;i<profiles.length;i++){
                            PerBlockTimes btimes = allblocks[i];
                            if(btimes == null){
                                continue;
                            }
                            int mpIdx = profiles[i];
                            int brIdx = resources[i];
                            String fileName = outputDir + "/sysid" + sysidkey + "_" + Format.cpuToString(i) +
                                                "_" + Format.BR(brIdx) + "_" +
                                                Format.MP(mpIdx) + ".perbbe";
                            try {
                                BufferedWriter outputFile = new BufferedWriter(new FileWriter(fileName));
                                    btimes.print(outputFile);
                                outputFile.close();
                            } catch (Exception e){
                                Logger.warn("Can not write per block times in " + fileName + "\n" + e);
                            }
                        }
                    }

                }
            }
            if(networkProfilesList != null){
                for(int i=0;i<networkProfilesList.length;i++){
                    int npIdx = networkProfilesList[i];
                    int brIdx = networkProfilesBaseRList[i];

                    double[] compTimes = networkProfilesCompTimes[i];
                    double[] commTimes = networkProfilesCommTimes[i];
                    HashMap  mpiTimes = networkProfilesMpiTimes[i];

                    if((compTimes == null) || (commTimes == null) || (mpiTimes == null)){
                        Logger.warn("No information for statistics for base resource " + brIdx + 
                                    " and network profile " + npIdx);
                        continue;
                    }

                    String fileName = outputDir + "/" + Format.cpuToString(i) + "_" + Format.BR(brIdx) + "_" + 
                                      Format.NP(npIdx) + ".comm";
                    try {
                        BufferedWriter outputFile = new BufferedWriter(new FileWriter(fileName));

                        outputFile.write("#\tratio\t" + networkProfilesRatios[i] + "\n\n");
                        for(int j=0;j<testCase.getCpu();j++){
                            outputFile.write(j + "\t" + Format.format4d(compTimes[j]) + "\t" + 
                                             Format.format4d(commTimes[j]) + "\t" + 
                                             Format.format4d(compTimes[j] + commTimes[j]) + "\n");
                        }

                        Iterator it = mpiTimes.keySet().iterator();
                        if(it.hasNext()){
                            outputFile.write("\n\n");
                        }
                        while(it.hasNext()){
                            String mpiFunction = (String)it.next();
                            Double t = (Double)mpiTimes.get(mpiFunction);

                            outputFile.write("#\t" + mpiFunction + "\t\t" + Format.format4d(t) + "\n");
                        }
                        outputFile.close();

                    } catch (Exception e){
                        Logger.warn("Can not write communication information to " + fileName + "\n" + e);
                    }
                }
            }
        }
        }
    }


    public void addNetworkProfiles(double[] ratios,int[] networkProfiles,int[] baseResources){
        synchronized(statsLock){
        assert (ratios != null) && (networkProfiles != null) && (baseResources != null);
        assert (networkProfiles.length == baseResources.length) && (ratios.length == baseResources.length);

        networkProfilesList = networkProfiles;
        networkProfilesBaseRList = baseResources;
        networkProfilesRatios = ratios;
        networkProfilesCompTimes = new double[baseResources.length][];
        networkProfilesCommTimes = new double[baseResources.length][];
        networkProfilesMpiTimes  = new HashMap[baseResources.length];
        }
    }

    public void addNetworkTimes(int idx,double[] compTimes,double[] commTimes,HashMap mpiTimes){
        synchronized(statsLock){
        assert (networkProfilesList != null) && (idx >= 0) && (idx < networkProfilesList.length);
        networkProfilesCompTimes[idx] = compTimes;
        networkProfilesCommTimes[idx] = commTimes;
        networkProfilesMpiTimes[idx] = mpiTimes;
        }
    }

    public void generateBlocksToFunctions(Map<FunctionID, Set<BlockID>> funcToBbids, Map<FunctionID, String> funcNames) {
        synchronized(statsLock){
        if( funcToBbids.size() == 0 ) {
            this.blockIdToFuncId = null;
            return;
        }
        this.blockIdToFuncId = Util.newHashMap();

        this.functionNames = new String[funcToBbids.size()];
        Iterator<Map.Entry<FunctionID, Set<BlockID>>> eit = funcToBbids.entrySet().iterator();
        for( Integer fid = 0; fid < funcToBbids.size(); ++fid ) {
            assert( eit.hasNext() );

            Map.Entry<FunctionID, Set<BlockID>> e = eit.next();
            FunctionID funcID = e.getKey();
            String funcName = funcNames.get(funcID);
            Set<BlockID> bbids = e.getValue();

            this.functionNames[fid] = funcName;
            for( Iterator<BlockID> bit = bbids.iterator(); bit.hasNext(); ) {
                BlockID bbid = bit.next();
                this.blockIdToFuncId.put(bbid, fid);
            }
        }
        }
    }

    // sysid -> memProf -> function -> time
    public Map<String, Double> getFunctionTimes(int sysid, int memProfIdx) {
        synchronized(statsLock){
        Map<String, Double> funcs = Util.newHashMap();

        int[] mProfIds = sysIdToMemoryPList.get(sysid);
        int pidx;
        Logger.inform("Looking for profile id " + memProfIdx);
        for( pidx = 0; pidx < mProfIds.length; ++pidx ) {
            Logger.inform("Looking at profile id " + mProfIds[pidx]);
            if( mProfIds[pidx] == memProfIdx ) {
                break;
            }
        }
        if( pidx == mProfIds.length ) {
            return null;
        }
        PerFunctionTimes ftimes = sysIdToPerFuncList.get(sysid)[pidx];

        assert( ftimes.functionNames.length == ftimes.computationTimes.length );

        for( int fid = 0; fid < ftimes.functionNames.length; ++fid ) {
            String fname = ftimes.functionNames[fid];
            Double time = ftimes.computationTimes[fid];
            funcs.put(fname, time);
        }
        return funcs;
        }
    }
}
