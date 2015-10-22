package PSaPP.pred;
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
import PSaPP.dbase.*;
import PSaPP.stats.*;
import PSaPP.data.*;
import java.util.*;
import java.io.*;

public class Convolver extends PSaPPThread {
    public static String[] validBwMethods     = { "BWexppenDrop", 
                                                  "BWstretchedExp", "BWstretchedHit", "BWstretchedEMult",
                                                  "BWstretchedPen", "BWcyclesDrop", "ti09" };
    public static String[] validRatioMethods  = { "avg", "max" };

    public static final String defaultRatioMethod = "avg";
    public static final String processedDirName   = "processed_trace";
    public static final String scratchExtension   = ".con";

    private static final Object convolverLock = new Object() {};

    Database    dataBase;
    TestCase    testCase;

    boolean     useSimMemoryTime;
    int         baseProfile;
    int[]       targetProfiles;

    double      baseMetasimNumber;
    double[]    targetMetasimNumbers;
   
    RatioMethod ratioCalculation;

    String      directDir;
    String      scratchDir;
    final TraceDB tracedb;

    String      username;
    String      shortname;
    int         predictionGroup;

    double[]    targetRatios;

    Set<Integer> cacheSysIdSet;
    HashMap sysId2MemoryP2BaseRToMetasim;
    StatCollection statistics;

    HashMap bbVariationsMap;
    String bbVariationsFile;

    Double  extraBWMultiplier;

    boolean dfpUsageEnabled;
    HashMap[] bbToDfpRanges;

    HashMap<Integer,HashMap<BlockID,Double>> powers;
    String pwrModel;

    public void setBlockVariations(String fName){
        Logger.inform("Setting block variations to " + fName);
        bbVariationsFile = fName;
    }
    public void useDfpIfExists(){
        dfpUsageEnabled = true;
    }
    public void setExtraBWMultiplier(Double r){
        assert (r != null);
        extraBWMultiplier = r;
        Logger.inform("Use of direct MULTIPLIER after BW calculation with factor" + 
                      extraBWMultiplier);
    }
    public String toString(){
        String retVal = "Convolver(\n";
        retVal += ("  testCase   : " + testCase + "\n");
        retVal += ("  directDir  : " + directDir + "\n");
        retVal += ("  scratchDir : " + scratchDir + "\n");
        retVal += ("  predGroup  : " + predictionGroup + "\n");
        retVal += ("  username   : " + username + "\n");
        retVal += ("  shortname  : " + shortname + "\n");
        retVal += ("  usesimmem  : " + useSimMemoryTime + "\n");
        retVal += ("  baseProfile: " + baseProfile + "\n");
        retVal += ("  variations : " + bbVariationsFile + "\n");
        retVal += ("  extraBWMult: " + extraBWMultiplier + "\n");
        assert (targetProfiles != null);
        for(int i=0;i<targetProfiles.length;i++){
            if(targetRatios != null){
                retVal += ("  profile    : " + targetProfiles[i] + "," + targetRatios[i] + "\n");
            } else {
                retVal += ("  profile    : " + targetProfiles[i] + "\n");
            }
        }

        retVal += ")";
        return retVal;
    }

    public Convolver(TestCase tcase,int bprof,int[] tprofs,double r,
                     String ddir,String sdir,
                     String user,String comment, 
                     int group,Database dbase)
    {

        this(tcase,bprof,tprofs,null,false,ddir,sdir,user,comment,group,dbase);

        assert (r > 0.0);
        targetRatios = new double[tprofs.length];
        for(int i=0;i<tprofs.length;i++){
            targetRatios[i] = r;
        }
    }

    public Convolver(TestCase tcase,int bprof,int[] tprofs,
                     String ratmeth,boolean usesimmem,
                     String ddir,String sdir,
                     String user,String comment, 
                     int group,Database dbase)
    {
        assert (dbase != null) && (tcase != null) && (group >= 0);
        dataBase = dbase;
        testCase = tcase;
        predictionGroup = group;

        Logger.debug("Test case is " + testCase);

        if(!dataBase.existsTestCase(testCase)){
            Logger.error("Test case " + testCase + " does not exist in database");
        }
        if(!dataBase.existsPredGroup(predictionGroup)){
            Logger.error("Prediction group " + predictionGroup + " does not exist in database");
        }

        assert (bprof >= 0);
        if(!dataBase.existsProfile(bprof)){
            Logger.error("Profile " + bprof + " does not exist in database");
        }
        baseProfile = bprof;
        assert (tprofs != null);
        targetProfiles = new int[tprofs.length];
        for(int i=0;i<tprofs.length;i++){
            assert (tprofs[i] >= 0);
            if(!dataBase.existsProfile(tprofs[i])){
                Logger.error("Profile " + tprofs[i] + " does not exist in database");
            }
            if(!dataBase.existsBaseResource(dataBase.getBaseResource(tprofs[i]))){
                Logger.error("Profile " + tprofs[i] + " has invalid base resource");
            }
            targetProfiles[i] = tprofs[i];
        }

        assert (ddir != null) && (sdir != null);
        if(!Util.isDirectory(ddir)){
            Logger.error("Direct data directory " + ddir + " does not exist");
        }
        if(!Util.isDirectory(ddir + "/" + processedDirName)){
            Logger.error(processedDirName + " in direct data dir " + ddir + " does not exist");
        }

        if(!Util.isDirectory(sdir)){
            Logger.error("Scratch data dir " + sdir + " does not exist");
        }

        directDir = ddir;
        scratchDir = sdir;

        Logger.debug(directDir + "/" + processedDirName + " exists");
        Logger.debug(scratchDir + " is the scratch directory");

        this.tracedb = new TraceDB(directDir + "/" + processedDirName, testCase, null);

        assert (user != null) && (comment != null);
        username = user;
        shortname = comment;

        useSimMemoryTime = usesimmem;

        ratioCalculation = null;
        if(ratmeth == null){
            ratmeth = defaultRatioMethod;
            Logger.debug("Using the default method for ratio " + defaultRatioMethod);
        }
        if(ratmeth.equals("max")){
            ratioCalculation = new RatioMethodMax();
        } else if(ratmeth.equals("avg")){
            ratioCalculation = new RatioMethodAvg();
        } else {
            Logger.error("Unknown ratio method is passed to predictions");
        }
        assert (ratioCalculation != null);

        for(int i=0;i<targetProfiles.length;i++){
            int mprofIdx = dataBase.getMemoryPIdx(targetProfiles[i]);
            Object[] memoryProfile = dataBase.getMemoryProfile(mprofIdx);
            if((memoryProfile == null) || !Util.isValidBWMethod((String)memoryProfile[0])){
                Logger.error("Profile " + targetProfiles[i] + " has invalid bw method");
            }
        }

        targetRatios = null;

        baseMetasimNumber = -1.0;
        targetMetasimNumbers = null;

        statistics = null;

        cacheSysIdSet = Util.newHashSet();
        sysId2MemoryP2BaseRToMetasim = new HashMap();

        bbVariationsMap = new HashMap();
        bbVariationsFile = null;

        extraBWMultiplier = null;

        dfpUsageEnabled = false;
        bbToDfpRanges = null;

        thread = new Thread(this);
    }

    boolean traceFileExists(String traceDirectory,int cachesysid){
        for(int i=0;i<testCase.getCpu();i++){
            String fileName = traceDirectory + "/" + testCase.shortName() + "_" + 
                              Format.cpuToString(i) + ".sysid" + cachesysid;
            if(!Util.isFile(fileName)){
                Logger.warn("Trace file " + fileName + " does not exist");
                return false;
            }
        }
        return true;
    }

    boolean readProcessedTraceAndCalculate(int cachesysid, HashMap mmemoryProfIdxs, Map<BlockID, Double> bbid2MemopBytes){
        if(mmemoryProfIdxs.size() == 0){
            Logger.warn("Memory profile for sysid " + cachesysid + " does not exist");
            return false;
        }

        String traceDirectory = directDir + "/" + processedDirName;
/*
        if(!traceFileExists(traceDirectory,cachesysid)){
            Logger.warn("Missin trace file(s) for sysid " + cachesysid + " at " + traceDirectory);
            return false;
        }
*/
        int profileCount = 0;
        Iterator it = mmemoryProfIdxs.keySet().iterator();
        while(it.hasNext()){
            Integer memprofkey = (Integer)it.next();
            HashMap bResourceToRest = (HashMap)mmemoryProfIdxs.get(memprofkey);
            profileCount += bResourceToRest.size();
        }

        Integer[]  memoryProfileKeys = new Integer[profileCount];
        Integer[]  baseResourceKeys = new Integer[profileCount];
        double[]   flops = new double[profileCount];
        BWMethod[] bwMethods = new BWMethod[profileCount];
        PWRMethod[] pwrMethods = new PWRMethod[profileCount];
        Object[]   memoryProfiles = new Object[profileCount];
        int[]      levelCounts = new int[profileCount];
        double[][] perTaskTimers = new double[profileCount][testCase.getCpu()];
        double[][] perTaskEnergy = new double[profileCount][testCase.getCpu()];

        int idx = 0;
        it = mmemoryProfIdxs.keySet().iterator();
        while(it.hasNext()){
            Integer memprofkey = (Integer)it.next();
            HashMap bResourceToRest = (HashMap)mmemoryProfIdxs.get(memprofkey);
            Iterator bit = bResourceToRest.keySet().iterator();
            while(bit.hasNext()){
                Integer resourcekey = (Integer)bit.next();

                Object[] fields = dataBase.getMemoryProfile(memprofkey.intValue());
                assert (fields != null);

                memoryProfileKeys[idx] = memprofkey;
                baseResourceKeys[idx] = resourcekey;
                memoryProfiles[idx] = fields;
                flops[idx] = dataBase.getBaseResourceFLOPS(resourcekey.intValue());
                assert (flops[idx] > 0.0);

                String bwmeth = (String)fields[0];
                int lvl = ((Integer)fields[1]).intValue();
                
                if(!Util.isValidLevelCount(lvl)){
                    Logger.warn("Level count " + lvl + " is not valid for memory prof " + memprofkey);
                    return false;
                }
                if(!Util.isValidBWMethod(bwmeth)){
                    Logger.warn("Bw method " + bwmeth + " is not valid for memory prof " + memprofkey);
                    return false;
                }

                bwMethods[idx] = null;
                pwrMethods[idx] = new PWRMethod(pwrModel);
                levelCounts[idx] = lvl;
                if(bwmeth.equals("BWstretchedExp") || bwmeth.equals("ti09")){
                    Logger.debug("memory profile " + memprofkey + " uses BWstretchedExp");
                    bwMethods[idx] = new BWMethodStretchedExp(lvl);
                } else if(bwmeth.equals("BWstretchedHit")){
                    Logger.debug("memory profile " + memprofkey + " uses BWstretchedHit");
                    bwMethods[idx] = new BWMethodStretchedHit(lvl);
                } else if(bwmeth.equals("BWstretchedEMult")){
                    Logger.debug("memory profile " + memprofkey + " uses BWstretchedEMult");
                    bwMethods[idx] = new BWMethodStretchedEMult(lvl);
                } else if(bwmeth.equals("BWstretchedPen")){
                    Logger.debug("memory profile " + memprofkey + " uses BWstretchedPen");
                    bwMethods[idx] = new BWMethodStretchedPen(lvl);
                } else if(bwmeth.equals("BWcyclesDrop")){
                    Logger.debug("memory profile " + memprofkey + " uses BWcyclesDrop");
                    bwMethods[idx] = new BWMethodCyclesDrop(lvl);
                } else if(bwmeth.equals("BWexppenDrop")){
                    Logger.debug("memory profile " + memprofkey + " uses BWexppenDrop");
                    bwMethods[idx] = new BWMethodExppenDrop(lvl);
                } else {
                    Logger.warn("Bw method " + bwmeth + " is invalid for memory prof " + memprofkey);
                    return false;
                }
                for(int j=0;j<testCase.getCpu();j++){
                    perTaskTimers[idx][j] = 0.0;
                    perTaskEnergy[idx][j] = 0.0;
                }
                idx++;
            }
        }

        int check = levelCounts[0];
        for(int i=1;i<profileCount;i++){
            if(check != levelCounts[i]){
                Logger.warn("Memory profiles for sysid " + cachesysid + " needs same number of levels\n" +
                            memoryProfileKeys[i] + " has a different one");
                return false;
            }
        }

        String signature = "lll";
        for(int i=0;i<levelCounts[0];i++){
            signature += "d";
        }

        if(statistics != null){
            int[] memoryPs = new int[profileCount];
            int[] baseRs = new int[profileCount];
            for(int i=0;i<profileCount;i++){
                memoryPs[i] = memoryProfileKeys[i].intValue();
                baseRs[i] = baseResourceKeys[i].intValue();
            }
            statistics.addMemoryProfiles(cachesysid,memoryPs,baseRs,levelCounts);
        }

        for(int cpuId=0;cpuId<testCase.getCpu();cpuId++){

            String fileName = traceDirectory + "/" + testCase.shortName() + "_" + 
                              Format.cpuToString(cpuId) + ".sysid" + cachesysid;
            if((cpuId % 16) == 0){
                Logger.inform("Processing for task " + cpuId + " for sysid " + cachesysid);
            }

            Set<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> blocks = this.tracedb.getSimulatedBlocks(cachesysid, cpuId, testCase.getCpu(), this.tracedb.getDynamicBlockCounts(cpuId));

            for( Iterator<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> bit = blocks.iterator(); bit.hasNext(); ) {
                Tuple.Tuple4<BlockID, Long, Long, CacheStats> binfo = bit.next();

                BlockID bbid = binfo.get1();
                Long dynFpops = binfo.get2();
                Long dynMemops = binfo.get3();

                Object[] blockFields = new Object[3 + levelCounts[0]];
                blockFields[0] = bbid.blockHash; // FIXME ignoring imgid
                blockFields[1] = dynFpops;
                blockFields[2] = dynMemops;

                CacheStats c = binfo.get4();
                if( c == null ) {
                    for( int l = 1; l <= levelCounts[0]; ++l ) {
                        blockFields[2 + l] = 100.0;
                    }
                } else {
                    Double hits = 0.0;
                    Double access = ((Long)(c.getHits(1) + c.getMisses(1))).doubleValue();
                    for( int l = 1; l <= levelCounts[0]; ++l ) {
                        hits += c.getHits(l);
                        blockFields[2 + l] = hits / access * 100.0;
                    }
                }

                for( int pidx = 0; pidx < profileCount; ++pidx ) {


                    BWMethod bwObject = bwMethods[pidx];
                    PWRMethod pwrObject = pwrMethods[pidx];
                    Double[] bwObjectArgs = null;
                    //MITESH to cost data Xfer cost to accelerator devices
                    // for each BB the ratio determines fraction of bytes that incur data Xfer costs
                    // This ratio can be any value >0 and can exceed 1.0 in extreme cases
                    //  Conversely, the ratio also can be almost 0 if we overlap Xfer to accelerate with CPU compute
                    double Xfer_Ratio = 0.0; //MITESH to cost data Xfer cost to accelerator devices


                    Integer baseR = baseResourceKeys[pidx];

                    HashMap variationsHash = (HashMap)bbVariationsMap.get(bbid);
                    if(variationsHash != null) {
                        Object[] variationsSpec = (Object[])variationsHash.get(baseR);
                        if(variationsSpec != null){
			    //MITESH reading in variations file 
                            //setting BW method
                            if(variationsSpec[0] != null){
                                bwObject = (BWMethod)variationsSpec[0];
                                bwObject.setLevel(bwMethods[pidx].getLevel());
                            }
                            
                            //LAURA change to allow null specs
			    // MITESH not sure what this will be used to pass in, but for dfp I use it get Xfer ratio?
                            if (variationsSpec[1] !=null){
                                 bwObjectArgs = (Double[])variationsSpec[1];
                            }


                            //MITESH you need to add to bwObjectArgs the number of 
                            // times you will transfer  don't assume starting with empty
                            if( ( bwObject instanceof BWMethodFpgaGather) ||
                                ( bwObject instanceof BWMethodGpuGather ) ||
                                ( bwObject instanceof BWMethodGpuStreams) ||
                                ( bwObject instanceof BWMethodFpgaStreams) ||
                                ( bwObject instanceof BWMethodFpgaGather_bad) ||
                                ( bwObject instanceof BWMethodGpuGather_bad ) ||
                                ( bwObject instanceof BWMethodGpuStreams_bad) ||
                                ( bwObject instanceof BWMethodFpgaStreams_bad) ||
                                ( bwObject instanceof BWMethodFpgaGatherStreams) ||
                                ( bwObject instanceof BWMethodGpuGatherStreams) ||
                                ( bwObject instanceof BWMethodFpgaGraph500)) {
                                Logger.inform("LAURA HERE add range to args \n");
                                // LAURA HERE need to add range to bwOjectArgs
                                if(dfpUsageEnabled){
                                     assert ((bbToDfpRanges != null) && (bbToDfpRanges[cpuId] != null));
                                     if(bbToDfpRanges[cpuId].get(bbid) != null){
                                            Long range  = (Long)bbToDfpRanges[cpuId].get(bbid);
					    //MITESH Changes to allow adding of Xfer cost
                                            Double[] rr = new Double[2];
                                            rr[0] = range.doubleValue();
					    rr[1] = 0.0;
					    if (variationsSpec[1]!=null)
                                            {
                                                 Double[] temp = (Double[])variationsSpec[1];
                                                 rr[1] = temp[0];
                                                 Xfer_Ratio = temp[0];
                                             }


                                            bwObjectArgs = rr;
                                     }
                                }
                            }//end if instance of accelerator
                        }
                    }

                    if((bwObject != bwMethods[pidx]) || (bwObjectArgs != null)){
                        //Logger.debug(bbid + " on " + baseR + "(mp" + memoryProfileKeys[pidx] + ")" + 
                        //   bwObject + " --- " + bwObjectArgs[0] );
                        //LAURA right now bwObjectArgs is garbage
                    }
                    //LAURA what you want to do is read in range and add to bwObjectArgs and t
                    // bw method defined in bbvariations file for given block and now read range above
                    //LAURA now calling object will have range in bwObjectArgs??

                    double[] bbBw_arr=null;
                    double bbBw=0.0;
                    double bbPwr=0.0;
                    if(powers != null) {
                        HashMap<BlockID,Double> sysVectors = powers.get(cachesysid);
                        if(sysVectors == null) {
                            sysVectors = new HashMap<BlockID,Double>();
                            powers.put(cachesysid, sysVectors);
                        }
                        Double bbPwrD = sysVectors.get(bbid);
                        if(bbPwrD == null) {
                            bbPwrD = pwrObject.calculatePWR(2600000, this.tracedb.getBlockPowerVector(cachesysid,bbid,cpuId));
                            sysVectors.put(bbid, bbPwrD);
                        }
                        
                        bbPwr=bbPwrD;
                    }
                    if( ( bwObject instanceof BWMethodFpgaGatherStreams) || ( bwObject instanceof BWMethodGpuGatherStreams) ) {
                          bbBw_arr = (double []) bwObject.calculateArrayOfBW(blockFields,(Object[])memoryProfiles[pidx],bwObjectArgs);
                          if(extraBWMultiplier != null){
                                bbBw_arr[0] = bbBw_arr[0] * extraBWMultiplier.doubleValue();
                                bbBw_arr[1] = bbBw_arr[1] * extraBWMultiplier.doubleValue();
                          }
                          Logger.debug(" GatherScatter BW_gather = " + bbBw_arr[0] + " BW_stream = " + bbBw_arr[1]);
                    }
                    else {
                         bbBw = bwObject.calculateBW(blockFields,(Object[])memoryProfiles[pidx],bwObjectArgs);
                         if(extraBWMultiplier != null){
                              bbBw = bbBw * extraBWMultiplier.doubleValue();
                         }
                    }


                    double bbStatBytes = 0.0;
                    if(bbid2MemopBytes.get(bbid) != null){
                        bbStatBytes = bbid2MemopBytes.get(bbid);
                    } else {
                        bbStatBytes = 8.0;
                    }

                    double fpTime = dynFpops / flops[pidx];
                    double memoryTime = 0.0;
                    double memoryTime_gather = 0.0;
                    double memoryTime_stream = 0.0;
                    if( (bwObject instanceof BWMethodFpgaGatherStreams) || (bwObject instanceof BWMethodGpuGatherStreams) ) {
	                    if(bbBw_arr[0] > 0.0){
                                     memoryTime_gather = (bbStatBytes*dynMemops)/2 / bbBw_arr[0];
                            }
                            if(bbBw_arr[1] > 0.0){
                                     memoryTime_stream = (bbStatBytes*dynMemops)/2 / bbBw_arr[1];
                            }
                            memoryTime = memoryTime_gather + memoryTime_stream;
                            Logger.debug(" GatherScatter  memTime_gather = " + memoryTime_gather  + " memTime_stream = " + memoryTime_stream + " tot_memTime = " + memoryTime);
                    }
                    else {
                            if(bbBw > 0.0){
				    	memoryTime = (bbStatBytes*dynMemops) / bbBw;
                            }
                    }

                    //MITESH need to check that bwObjectsArg has a second number and instance of FPGA or GPU
                    // then calculate transfer time
                    // MITESH if using dfp and specified Xfer ratio in variation file
                    // We add to memory time the Data Xfer cost to accelerator for each basic block

                    if(Xfer_Ratio > 0.0) {
                          double XferTime = 0.0;
                          double Bytes_Xfered = (bbStatBytes*((Long)blockFields[2]).longValue()) * Xfer_Ratio;
                          double Xfer_bbBw = 1.0e9;

                          //MITESH: Check if instance of GPU or FPGA and use model to calculate Xfer cost
                          // In future , for flexibility the Xfer cost should allow for different models 
                          if( ( bwObject instanceof BWMethodFpgaGather) ||
                              ( bwObject instanceof BWMethodFpgaStreams) ||
                              ( bwObject instanceof BWMethodFpgaGather_bad) ||
                              ( bwObject instanceof BWMethodFpgaStreams_bad) ||
                              ( bwObject instanceof BWMethodFpgaGatherStreams) ||
                              ( bwObject instanceof BWMethodFpgaGraph500)){

	                              // Here goes the model for FPGA
                                        if(Bytes_Xfered < 4096)
                                                Xfer_bbBw = (-1.44e-04 + 7.977e-06*Bytes_Xfered  ) * 1.0e9;
                                        else if(Bytes_Xfered >= 4096 && Bytes_Xfered < 65536)
                                                Xfer_bbBw = (1.73e-02 + 4.202e-06*Bytes_Xfered  ) * 1.0e9;
                                        else if(Bytes_Xfered >= 65536 && Bytes_Xfered < 524288)
                                                Xfer_bbBw = (1.85e-01 + 1.05e-06*Bytes_Xfered  ) * 1.0e9;
                                        else if(Bytes_Xfered >= 524288 && Bytes_Xfered < 16777216)
                                                Xfer_bbBw = (5.642e-01 + 2.071e-08*Bytes_Xfered  ) * 1.0e9;
                                        else if(Bytes_Xfered >= 16777216)
                                                Xfer_bbBw = (7.625e-01) * 1.0e9;
                                        else {
                                                Logger.warn("MITESH data to transfer too small, assuming BW of 0.7625 * 1.0e9 \n");
                                                Xfer_bbBw = 0.7625*1.0e9;
                                        }

                                        XferTime = Bytes_Xfered / Xfer_bbBw;
                         }
                         if( ( bwObject instanceof BWMethodGpuGather) ||
                             ( bwObject instanceof BWMethodGpuStreams) ||
                             ( bwObject instanceof BWMethodGpuGather_bad ) ||
                             ( bwObject instanceof BWMethodGpuStreams_bad)) {

                         	     // Here goes the model for GPU 
                                        if(Bytes_Xfered < 8192)
                                                Xfer_bbBw = (4.334e-04 + 7.883e-05*Bytes_Xfered + -9.096e-09*Bytes_Xfered*Bytes_Xfered ) * 1.0e9;
                                        else if(Bytes_Xfered >= 8192 && Bytes_Xfered < 131072)
                                                Xfer_bbBw = (1.959e-01 + 7.204e-06*Bytes_Xfered + -6.505e-11*Bytes_Xfered*Bytes_Xfered ) * 1.0e9;
                                        else if(Bytes_Xfered >= 131072 && Bytes_Xfered < 524288)
                                                Xfer_bbBw = (3.227e-01 + 5.771e-07*Bytes_Xfered ) * 1.0e9;
                                        else if(Bytes_Xfered >= 524288)
                                                Xfer_bbBw = (5.621502e-01) * 1.0e9;
                                        else {
                                                Logger.warn("MITESH data to transfer too small, assuming BW of 0.5621 * 1.0e9 \n");
                                                Xfer_bbBw = (5.621502e-01) * 1.0e9;
                                        }

                                        XferTime = Bytes_Xfered / Xfer_bbBw;

                         }
                         Logger.debug(" Data XferBW = " + Xfer_bbBw + " XferTime = "+ XferTime + " for Xfer Ratio = "+Xfer_Ratio + " and bytes Xfered = "+ Bytes_Xfered);

                         memoryTime = memoryTime + XferTime;
                    }//End of adding data Xfer Time



		     //LAURA
                    /*LAURA checking L1? */
                    double l1hit = (Double)blockFields[3];
                    if ( l1hit > 99.9){
                        fpTime = fpTime * .40;
                    }else{
                        fpTime = fpTime * .80;
                    }

//		    if (fpTime>memoryTime){
//                      Logger.warn("FPTIME is bigger than memory time fp=" + fpTime + " m=" + memoryTime);
//                    }
                    double basicBlockTime = memoryTime + fpTime;
                    assert (basicBlockTime >= 0.0);
                    perTaskTimers[pidx][cpuId] += basicBlockTime;
                    perTaskEnergy[pidx][cpuId] += basicBlockTime*bbPwr;

                    if(statistics != null){
                        statistics.addBWData(cachesysid,pidx,cpuId,basicBlockTime,bbBw);
                        statistics.addComputationTime(cachesysid,pidx,cpuId,
                                                      blockFields,memoryTime,fpTime);
                        statistics.addPWRData(cachesysid,pidx,cpuId,basicBlockTime,bbPwr);
                        statistics.addComputationEnergy(cachesysid,pidx,cpuId,blockFields,(memoryTime+fpTime)*bbPwr);
                    }
                }
            }
        }

        for(int midx=0;midx<profileCount;midx++){
            double metasimNumber = ratioCalculation.getMetasimNumber(perTaskTimers[midx]);
            HashMap bResourceToRest = (HashMap)mmemoryProfIdxs.get(memoryProfileKeys[midx]);
            bResourceToRest.put(baseResourceKeys[midx],new Double(metasimNumber));
            Logger.debug("Metasim number for memory profile " + memoryProfileKeys[midx] + 
                         " with cache sysid " + cachesysid + " and base resource " + baseResourceKeys[midx] + 
                         " ----> " + metasimNumber);
            if(powers != null) {
                metasimNumber = ratioCalculation.getMetasimNumber(perTaskEnergy[midx]);
                Logger.debug("Metasim number for energy profile " + memoryProfileKeys[midx] +
                             " with cache sysid " + cachesysid + " and base resource " + baseResourceKeys[midx] +
                             " ----> " + metasimNumber);
            }
        }

        return true;
    }

    void enablePWR(String model) {
        powers = new HashMap();
        pwrModel = model;
    }

    boolean convolveEachCacheSysid(){

        Map<BlockID, Double> bbid2MemopBytes = this.tracedb.getBbidToBytesPerMemop();

        if(bbid2MemopBytes == null){
            Logger.warn("There is error in reading the bbid 2 memop byte mapping");
            return false;
        }

        if(bbid2MemopBytes.size() == 0){
            Logger.inform("There is no basic block avg memop bytes info so assuming each memop will be 8 bytes");
        }

        Iterator<Integer> it = cacheSysIdSet.iterator();
        while(it.hasNext()){
            Integer sysid = it.next();
            Logger.inform("Convolving for sysid " + sysid);

            HashMap mProfileToRest = (HashMap)sysId2MemoryP2BaseRToMetasim.get(sysid);
            assert (mProfileToRest != null);

            boolean success = readProcessedTraceAndCalculate(sysid, mProfileToRest, bbid2MemopBytes);
            if(!success){
                Logger.warn("Colvolving the trace files for sysid " + sysid + " has failed");
                return false;
            }
        }
        return true;
    }

    void addVoidMetasimNumber(int profIdx){
        assert (profIdx >= 0);
        int machineIdx = dataBase.getBaseResource(profIdx);
        int cachesysid = dataBase.getCacheSysId(profIdx);
        int mprofIdx = dataBase.getMemoryPIdx(profIdx);
        assert ((cachesysid != Database.InvalidDBID) && (mprofIdx != Database.InvalidDBID) &&
                (machineIdx != Database.InvalidDBID));

        Integer machkey = new Integer(machineIdx);
        Integer sysidkey = new Integer(cachesysid);
        Integer memprofkey = new Integer(mprofIdx);

        cacheSysIdSet.add(sysidkey);
        HashMap mProfileToRest = (HashMap)sysId2MemoryP2BaseRToMetasim.get(sysidkey);
        if(mProfileToRest == null){
            mProfileToRest = new HashMap();
            sysId2MemoryP2BaseRToMetasim.put(sysidkey,mProfileToRest);
        }
        HashMap bResourceToRest = (HashMap)mProfileToRest.get(memprofkey);
        if(bResourceToRest == null){
            bResourceToRest = new HashMap();
            mProfileToRest.put(memprofkey,bResourceToRest);
        }
        
        bResourceToRest.put(machkey,new Double(-1.0));
    }

    Double getCurrentMetasimNumber(int profIdx){
        assert (profIdx >= 0);
        int machineIdx = dataBase.getBaseResource(profIdx);
        int cachesysid = dataBase.getCacheSysId(profIdx);
        int mprofIdx = dataBase.getMemoryPIdx(profIdx);
        assert ((cachesysid != Database.InvalidDBID) && (mprofIdx != Database.InvalidDBID) &&
                (machineIdx != Database.InvalidDBID));

        Integer machkey = new Integer(machineIdx);
        Integer sysidkey = new Integer(cachesysid);
        Integer memprofkey = new Integer(mprofIdx);

        HashMap mProfileToRest = (HashMap)sysId2MemoryP2BaseRToMetasim.get(sysidkey);
        assert (mProfileToRest != null);

        HashMap bResourceToRest = (HashMap)mProfileToRest.get(memprofkey);
        assert (bResourceToRest != null);

        Double mnumber = (Double)bResourceToRest.get(machkey);
        return mnumber;
    }

    public void setStatistics(StatCollection stats){
        Logger.debug("LAURA in convolver set statistics ");
        statistics = stats;
    }

    public void run(){
        runStatus = THREAD_RUNNING;
        Logger.inform(this);

        if(targetRatios != null){
            Logger.inform("The ratios are already calculated for the target machines");
            runStatus = THREAD_SUCCESS;
            return;
        }

        if(useSimMemoryTime){
            baseMetasimNumber = dataBase.getSimMemoryTime(testCase,baseProfile);
            if(baseMetasimNumber <= 0.0){
                Logger.inform("The special group does not have memory time for " + testCase + 
                            " for resource in profile " + baseProfile);
                Logger.inform("Will run convolver using the base profile " + baseProfile);
            }
        }

        if(!useSimMemoryTime || (baseMetasimNumber <= 0.0)){
            addVoidMetasimNumber(baseProfile);
        }

        for(int i=0;i<targetProfiles.length;i++){
            addVoidMetasimNumber(targetProfiles[i]);
        }

        Logger.inform("There are " + cacheSysIdSet.size() + " cache systems for convolver");
        Iterator it = cacheSysIdSet.iterator();
        String freqlist = "";
        String memproflist = "";
        String syslist = "";
        while(it.hasNext()){
            Integer sysidkey = ((Integer)it.next());
            syslist += sysidkey + " ";
            memproflist += "{ ";
            freqlist += "{ ";
            HashMap mProfileToRest = (HashMap)sysId2MemoryP2BaseRToMetasim.get(sysidkey);
            Iterator mit = mProfileToRest.keySet().iterator();
            while(mit.hasNext()){
                Integer memprofkey = (Integer)mit.next();
                memproflist += memprofkey + " ";
                HashMap bResourceToRest = (HashMap)mProfileToRest.get(memprofkey);
                Iterator bit = bResourceToRest.keySet().iterator();
                freqlist += "{ ";
                while(bit.hasNext()){
                    freqlist += (Integer)bit.next() + " ";
                }
                freqlist += "}";
            }
            freqlist += " }  ";
            memproflist += "}";
        }
        Logger.inform("\t" + syslist);
        Logger.inform("\t" + memproflist);
        Logger.inform("\t" + freqlist);

        Map<FunctionID, Set<BlockID>> func2bbset = this.tracedb.getFuncToBbids();
        Map<FunctionID, String> functionNames = this.tracedb.getFunctionNames();

        if(statistics != null){
            statistics.generateBlocksToFunctions(func2bbset, functionNames);
        }

        boolean success;

        if( bbVariationsFile != null ) {

            Map<String, Set<BlockID>> funcNamesToBlocks = Util.newHashMap();
            for(Iterator<Map.Entry<FunctionID, String>> fit = functionNames.entrySet().iterator(); fit.hasNext(); ) {
                Map.Entry<FunctionID, String> ent = fit.next();
                FunctionID fid = ent.getKey();
                String fname = ent.getValue();
                Set<BlockID> newbbs = func2bbset.get(fid);
                Set<BlockID> bbs = funcNamesToBlocks.get(fname);
                if(bbs == null) {
                    funcNamesToBlocks.put(fname, new HashSet(newbbs));
                } else {
                    bbs.addAll(newbbs);
                }
            }

            success = parseBlockVariations(funcNamesToBlocks);
            if(!success){
                 Logger.warn("Error in reading blocks variations in " + bbVariationsFile);
                 runStatus = THREAD_FAILED;
                 return;
            }
        }

        if(dfpUsageEnabled){
            if(!readDfpRanges()){
                Logger.warn("Even though dfPattern usage is enabled, the data can not be read, so NO DFPATTERN");
                dfpUsageEnabled = false;
            }
        }

        success = convolveEachCacheSysid();
        if(!success){
             Logger.warn("Error in convolution for sysidds");
             runStatus = THREAD_FAILED;
             return;
        }

        targetRatios = new double[targetProfiles.length];
        targetMetasimNumbers = new double[targetProfiles.length];
        for(int i=0;i<targetProfiles.length;i++){
            targetRatios[i] = 0.0;
            targetMetasimNumbers[i] = -1.0;
        }

        if(!useSimMemoryTime || (baseMetasimNumber <= 0.0)){
            Double mnumber = getCurrentMetasimNumber(baseProfile);
            if(mnumber == null){
                Logger.warn("Can not calculate metasim number for " + baseProfile); 
                runStatus = THREAD_FAILED;
                return;
            }
            assert (mnumber != null);
            baseMetasimNumber = mnumber.doubleValue();

        }
        assert (baseMetasimNumber > 0.0);

        for(int i=0;i<targetProfiles.length;i++){
            int cachesysid = dataBase.getCacheSysId(targetProfiles[i]);
            Double mnumber = getCurrentMetasimNumber(targetProfiles[i]);
            if(mnumber == null){
                Logger.warn("Can not calculate metasim number for " + targetProfiles[i]); 
                runStatus = THREAD_FAILED;
                return;
            }
            assert (mnumber != null);

            targetMetasimNumbers[i] = mnumber.doubleValue();
            assert (targetMetasimNumbers[i] > 0.0);
            targetRatios[i] = (baseMetasimNumber / targetMetasimNumbers[i]);

            Logger.inform("Profile " + targetProfiles[i] + " sysid " + cachesysid + " ratio " + 
                          Format.format4d(targetRatios[i]) + 
                          " base metasim number " + Format.format4d(baseMetasimNumber) + 
                          " target metasim number " + Format.format4d(targetMetasimNumbers[i]));
        }

        success = generateScratchOutputs();
        if(!success){
            Logger.warn("Can not generate scratch files");
            runStatus = THREAD_FAILED;
            return;
        }
        runStatus = THREAD_SUCCESS;

        this.tracedb.close();
    }

    boolean generateScratchOutputForProfile(int profIdx,double tNumber,double bNumber,double tRatio,String outputDir,String ext){
        synchronized(convolverLock){
            int machineIdx = dataBase.getBaseResource(profIdx);
            int cachesysid = dataBase.getCacheSysId(profIdx);
            int mprofIdx = dataBase.getMemoryPIdx(profIdx);
            assert ((cachesysid != Database.InvalidDBID) && (mprofIdx != Database.InvalidDBID));
            String filePath = outputDir + "/" + Format.BR(machineIdx) + "_" + Format.PR(profIdx) + "_" + 
                Format.MP(mprofIdx) + "_sysid" + cachesysid + ext;
            try {
                BufferedWriter outputFile = new BufferedWriter(new FileWriter(filePath));
                outputFile.write("profile  = " + profIdx + "\n");
                outputFile.write("mprofile = " + mprofIdx + "\n");
                outputFile.write("cache id = " + cachesysid + "\n");
                if(profIdx == baseProfile){
                    outputFile.write("baseprof = " + baseProfile + " (the same)\n");
                } else {
                    outputFile.write("baseprof = " + baseProfile + "\n");
                }
                outputFile.write("testcase = " + testCase + "\n");
                outputFile.write("resource = " + dataBase.getBaseResourceString(machineIdx) + " " + machineIdx + "\n");
                outputFile.write("bmetasim = " + bNumber + "\n");
                outputFile.write("metasim  = " + tNumber + "\n");
                outputFile.write("ratio    = " + tRatio + "\n");
                outputFile.write("ratiometh= " + ratioCalculation + "\n");
                if(useSimMemoryTime){
                    outputFile.write("usesimem = yes\n");
                } else {
                    outputFile.write("usesimem = no\n");
                }
                if(bbVariationsFile != null){
                    outputFile.write("variation= " + bbVariationsFile + "\n");
                }
                if(extraBWMultiplier != null){
                    outputFile.write("BWmultip = " + extraBWMultiplier + "\n");
                }
                outputFile.close();
            } catch (Exception e){
                Logger.error(e, "Can not write the target metasim number summary at " + filePath);
                return false;
            }
        }
        return true;
    }

    boolean generateScratchOutputs(){
        String outputDir = Predict.makeScratchDirs(scratchDir,predictionGroup,testCase);
        if(outputDir == null){
            Logger.warn("Can not make " + outputDir);
            return false;
        }
        if(!generateScratchOutputForProfile(baseProfile,baseMetasimNumber,baseMetasimNumber,1.0,outputDir,".bas")){
            return false;
        }
        for(int i=0;i<targetProfiles.length;i++){
            if(!generateScratchOutputForProfile(targetProfiles[i],targetMetasimNumbers[i],
                                                baseMetasimNumber,targetRatios[i],outputDir,scratchExtension)){
                return false;
            }
        }
        return true;
    }

    public double[] getTargetRatios(){
        double[] retVal = null;
        if(targetRatios != null){
            retVal = new double[targetRatios.length];
            for(int i=0;i<targetRatios.length;i++){
                retVal[i] = targetRatios[i];
            }
        }
        return retVal;
    }
    public double getTargetMetasimNumbers(int i){
        assert ((i >= 0) && (targetMetasimNumbers != null) && (i < targetMetasimNumbers.length));
        return targetMetasimNumbers[i];
    }
    public double[] getTargetMetasimNumbers(){
        double[] retVal = null;
        if(targetMetasimNumbers != null){
            retVal = new double[targetMetasimNumbers.length];
            for(int i=0;i<targetMetasimNumbers.length;i++){
                retVal[i] = targetMetasimNumbers[i];
            }
        }
        return retVal;
    }
    public int[] getTargetProfiles(){
        int[] retVal = null;
        if(targetProfiles != null){
            retVal = new int[targetProfiles.length];
            for(int i=0;i<targetProfiles.length;i++){
                retVal[i] = targetProfiles[i];
            }
        }
        return retVal;
    }

    boolean parseBlockVariations(Map<String, Set<BlockID>> func2bbset){
        try {
            BufferedReader inputFile = new BufferedReader(new FileReader(bbVariationsFile));
            String line = null;
            int lineNo = 0;
// sample lines:
//   function  | .__chemkin_m_NMOD_reaction_rate_vec  |  57,BWMethodStretchedExp,2.0:21,BWMethodStretchedExp,3.0,5.0,7.0
//   block     | 281822870175744                      |  21,BWMethodTestExp,9.0,-5.0
//   block     | 281822871289856                      |  21,*,9.0:57,*,-1.0

            while ((line = inputFile.readLine()) != null) {
                lineNo++;
                line = Util.cleanComment(line);
                line = Util.cleanWhiteSpace(line,true);
                if(line.length() == 0){
                    continue;
                }

                String[] tokens = line.split("\\|");
                //first split by "|" should have 3 values :
                //  first value can be : block or function
                //  second value is either : function name, bbid, or *<---match all only for 1st value function
                //  third value series of specs kept in:bwObjAndArgs first number base_resource, second is BWMethod,??
                if(tokens.length != 3){
                    Logger.warn("Line " + lineNo + " in " + bbVariationsFile  + " needs 3 fields");
                    return false;
                }
                Set<BlockID> bbset = null;
                if(tokens[0].equals("function")){
                    if(tokens[1].equals("*")){
                        bbset = Util.newHashSet();
                        Iterator<Set<BlockID>> sit = func2bbset.values().iterator();
                        while( sit.hasNext() ) {
                            Set<BlockID> fbbs = sit.next();
                            Iterator<BlockID> bit = fbbs.iterator();
                            while( bit.hasNext() ) {
                                bbset.add(bit.next());
                            }
                        }

                    } else {
                        bbset = func2bbset.get(tokens[1]);
                        if(bbset == null){
                            Logger.warn("Line " + lineNo + " in " + bbVariationsFile + 
                                        " has a function with no bb info");
                            continue;
                        }
                    }
                } else if(tokens[0].equals("block")){
                    BlockID bbid = new BlockID(0L, Util.toLong(tokens[1]));
                    if(bbid == null){
                        Logger.warn("Line " + lineNo + " in " + bbVariationsFile + 
                                    " has an invalid bbid format ");
                        return false;
                    }
                    bbset = Util.newHashSet();
                    bbset.add(bbid);
                } else {
                    Logger.warn("Line " + lineNo + " in " + bbVariationsFile  + " starts with unknown token");
                    return false;
                }
                assert (bbset != null);

                String[] specs = tokens[2].split(":");
                if(specs == null){
                    Logger.warn("Line " + lineNo + " in " + bbVariationsFile +
                        "has invalid spec " + tokens[2]);
                    return false;
                }

                HashMap specsBaseRHash = new HashMap();
                Logger.inform("Line " + lineNo + " in " + bbVariationsFile + " has " + specs.length + " specs");
                for(int i=0;i<specs.length;i++){
                    String[] entries = specs[i].split(",");
                    //LAURA changing if(entries.length < 3){
                    if(entries.length < 2){
                        Logger.warn("Line " + lineNo + " in " + bbVariationsFile +
                                " has invalid spec at " + specs[i]);
                        return false;
                    }
                    Integer baseR = Util.toInteger(entries[0]);
                    if(baseR == null){
                        Logger.warn("Line " + lineNo + " in " + bbVariationsFile +
                                " has invalid base resource at " + entries[0]);
                        return false;
                    }
                    String method = entries[1];
                    Object bwObj = null;
                    Long range=null;
                    if(!method.equals("*")){
                        try {
                            ClassLoader classLoader = ClassLoader.getSystemClassLoader();
                            Class bwClass = classLoader.loadClass("PSaPP.pred." + method);
                            assert (bwClass != null);
                            bwObj = bwClass.newInstance();
                            assert (bwObj != null);
                            if( bwObj instanceof BWMethodFpgaGather){
                               //check if BW method part of accelerators that need dfp data
                               Logger.inform("LAURA caught the BW method ");
                            }//end if then for accelerator object that needs range
                        } catch (Exception e){
                            Logger.warn("Line " + lineNo + " in " + bbVariationsFile +
                                    " has invalid bw method " + method + " at " + i);
                            return false;
                        }
                    }
                    //LAURA changing so only need 2 args not 3
                    Double[] args=null;
                    if (entries.length>2){ 
                        args = new Double[entries.length-2];
                        for(int j=2;j<entries.length;j++){
                            args[j-2] = Util.toDouble(entries[j]);
                            if(args[j-2] == null){
                                Logger.warn("Line " + lineNo + " in " + bbVariationsFile +
                                    " has invalid double arg at " + i + " and " + entries[j]);
                                return false;
                            }
                        }
                    }
                    Object[] bwObjAndArgs = new Object[2];
                    bwObjAndArgs[0] = bwObj;
                    bwObjAndArgs[1] = args;
                    specsBaseRHash.put(baseR,bwObjAndArgs);
                }
                if(specsBaseRHash.size() > 0){
                    Iterator it = bbset.iterator();
                    while(it.hasNext()){
                        BlockID bbid = (BlockID)it.next();
                        bbVariationsMap.put(bbid,specsBaseRHash);
                    }
                }
            }
            inputFile.close();
        } catch (Exception e){
            Logger.warn("Error in reading " + bbVariationsFile + ", no block to func mapping\n" + e);
            return false;
        }
        return true;
    }

    boolean readDfpRanges(){
        if(!dfpUsageEnabled){
            return false;
        }

        Logger.inform("DfPattern range information will be read as dfp is enabled with an option");

        bbToDfpRanges = new HashMap[testCase.getCpu()];
        String dfpDirectory = directDir + "/dfp";
        for(int i=0;i<testCase.getCpu();i++){
            bbToDfpRanges[i] = new HashMap();
            String dfpFileName = dfpDirectory + "/processed_" + Format.cpuToString(i) + ".dfp";
            if(!Util.isFile(dfpFileName)){
                return false;
            }
            try {
                Logger.debug("Reading " + dfpFileName);
                BufferedReader inputFile = new BufferedReader(new FileReader(dfpFileName));
                String line = null;
                int lineNo = 0;
                while ((line = inputFile.readLine()) != null) {
                    lineNo++;
                    line = Util.cleanComment(line);
                    line = Util.cleanWhiteSpace(line,false);
                    if(line.length() > 0){
                        String[] tokens = line.split("\\s+");
                        assert (tokens.length == 2);
                        BlockID bbid = new BlockID(0L, Util.toLong(tokens[0]));
                        Long mrange = Util.toLong(tokens[1]);
                        assert ((bbid != null) && (mrange != null));

                        bbToDfpRanges[i].put(bbid,mrange);
                    }
                }
                inputFile.close();
            } catch (Exception e) {
                Logger.warn("Can not read " + dfpFileName + "\n" + e);
                e.printStackTrace();
                bbToDfpRanges = null;
                return false;
            }
        }
        return true;
    }
}
