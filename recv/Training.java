package PSaPP.recv;
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

import java.io.*;
import java.util.*;
import PSaPP.dbase.*;
import PSaPP.util.*;

/**
 * This class uses the trace files for a specific application, data set, CPU count, and SYSID to
 * generate a machine profile.
 * @author bsheely
 */
public class Training extends PSaPPThread {
    final double EPSILON = 0.01;
    final int NUM_ZONES = 21;
    final int PADDED_LENGTH = 4;
    boolean forceNewWeights;
    String traceDir;
    String gaInputDir;
    String gaConfig;
    String gaTraining;
    String bwMethod;
    String trainingOptions;
    String gaTrainingPath;
    String multiMAPS;
    String prefix;
    String weightFile;
    int numWeightFiles;
    int sysId;
    int baseResource;
    int cacheLevels;
    int zoneWeightsDbid[];
    TestCase testCase;
    Database database;
    long totalReferences[];
    long zoneTotals[][];
    double zoneWeights[][];
    double l1stride2 = 0;
    double l1stride4 = 0;
    double l1stride8 = 0;
    double l2stride2 = 0;
    double l2stride4 = 0;
    double l2stride8 = 0;
    double l3stride2 = 0;
    double l3stride4 = 0;
    double l3stride8 = 0;
    int trainedProfile = Database.InvalidDBID;
    private static final Object trainingLock = new Object() {};
    GaInput gaInput = null;
    int cores_per_node=-1;
    Double[] ratios = null;

    /**
     * Constructor
     * @param separateWeights Compute separate zone totals and create weight file for each CPU
     * @param tracePath Path to directory which contains the .sysid<xx> trace files
     * @param scratchPath Path to directory where temp file will be written
     * @param training Name of GATraining executable to use
     * @param method Name of bw method to use
     * @param trainingOptions Default options, if any, to be passed to GA Training
     * @param multiMaps Absolute path to multiMaps file
     * @param gaPrefix Prefix to be used with GA output filename
     * @param sysid Cache sys ID
     * @param resource Base resource ID
     * @param testCase TestCase
     * @param db Database which contains the zone_weights table
     */
    public Training(boolean forceNew, boolean separateWeights, String tracePath, String scratchPath,
                    String training, String method, String options, String multiMaps,
                    String gaPrefix, int resource, TestCase testcase, Integer sysid, Database db) {
        database = db;
        forceNewWeights = forceNew;
        numWeightFiles = 1;
        if (separateWeights == true){
            Logger.error("Separate weights being used... it may not be implemented correctly");
            numWeightFiles = testcase.getCpu();
        }
        traceDir = tracePath;
        gaInputDir = scratchPath;
        if (!gaInputDir.endsWith("/")) {
            gaInputDir += "/";
        }
        gaConfig = gaInputDir + gaPrefix + ".ga.config";
        gaTraining = training;
        bwMethod = method;
        trainingOptions = options;
        baseResource = resource;

        if (multiMaps != null){
            multiMAPS = multiMaps;
        } else {
            String mmapsData = ConfigSettings.getSetting("MULTIMAPS_DATA_PATH");
            if (mmapsData == null){
                Logger.error("MULTIMAPS_DATA_PATH config key needs to be set in $PSAPP_ROOT/etc/config");
            }
            multiMAPS = mmapsData + "/br" + baseResource + "_multimaps.gain";
        }
        Logger.inform("Multimaps data being read from: " + multiMAPS);
        if (!(new File(multiMAPS)).exists()) {
            Logger.error("Cannot open multimaps file " + multiMAPS);
        }

        prefix = gaPrefix + "_br" + baseResource;
        testCase = testcase;
        if (!traceDir.endsWith("/")) {
            traceDir += "/";
        }
        gaTrainingPath = ConfigSettings.getSetting("GATRAINING_PATH");
        if (gaTrainingPath == null){
            Logger.error("GATRAINING_PATH config key needs to be set in $PSAPP_ROOT/etc/config");
        }
        zoneWeightsDbid = new int[numWeightFiles];
        totalReferences = new long[numWeightFiles];
        for (int i = 0; i < numWeightFiles; ++i) {
            totalReferences[i] = 0;
            zoneWeightsDbid[i] = Database.InvalidDBID;
        }
        zoneTotals = new long[numWeightFiles][NUM_ZONES+1];
        zoneWeights = new double[numWeightFiles][NUM_ZONES];
        for (int i = 0; i < numWeightFiles; ++i) {
            for (int j = 0; j < NUM_ZONES; ++j) {
                zoneTotals[i][j] = 0;
                zoneWeights[i][j] = 0.0;
            }
            zoneTotals[i][21] = 0;
        }

        if (database == null) {
            database = new Postgres();
            if (!database.initialize()) {
                runStatus = PSaPPThread.THREAD_FAILED;
                Logger.error("Cannot initialize the database");
            }
        }

        if (!database.existsBaseResource(baseResource)){
            Logger.error("Base resource is invalid: " + baseResource);
        }


        if (sysid == null){
            sysId = database.getSysidFromBaseResource(baseResource);
        } else {
            sysId = sysid.intValue();
            Logger.inform("Setting sysid manually to " + sysId);
        }

        thread = new Thread(this);
    }

    /**
     * Generate machine profile using trace files
     * @return boolean True if successful else false
     */
    public void run() {
        runStatus = PSaPPThread.THREAD_RUNNING;
        try {
            if (!init()) {
                runStatus = PSaPPThread.THREAD_FAILED;
                Logger.error("Unable to initialize Training Object");
            }
            readTraceFiles();
            computeZoneWeights();
            boolean needsGA = checkZoneWeights();

            if (needsGA){                
                runGA();
            }

            /* look up the correct memory profile */
            trainedProfile = database.getTrainedMachineProfile(zoneWeightsDbid[0], baseResource);
            if (trainedProfile == Database.InvalidDBID){
                Logger.error("training for base_resource " + baseResource + " failed to yield a machine_profile");
            }
            Logger.inform("br" + baseResource + " is trained to machine_profile " + trainedProfile);


        } catch (Exception e) {
            runStatus = PSaPPThread.THREAD_FAILED;
            Logger.error(e, "Training failed");
        }
        runStatus = PSaPPThread.THREAD_SUCCESS;
    }

    public int getTrainedProfile() { return trainedProfile; }

    boolean init() {
        int l1lookup = database.existsCacheStructureL1Line(sysId);
        int l2lookup = database.existsCacheStructureL2Line(sysId);
        int l3lookup = database.existsCacheStructureL3Line(sysId);

        cacheLevels = 2;
        if (l3lookup > 0){
            cacheLevels = 3;
        }
        assert(cacheLevels >= 2 && cacheLevels <= 3);

        l1stride2 = GaInput.computeMissingHitrate(l1lookup, 2);
        l1stride4 = GaInput.computeMissingHitrate(l1lookup, 4);
        l1stride8 = GaInput.computeMissingHitrate(l1lookup, 8);
        if (l2lookup != 0) {
            l2stride2 = GaInput.computeMissingHitrate(l2lookup, 2);
            l2stride4 = GaInput.computeMissingHitrate(l2lookup, 4);
            l2stride8 = GaInput.computeMissingHitrate(l2lookup, 8);
        }
        if (l3lookup != 0) {
            l3stride2 = GaInput.computeMissingHitrate(l3lookup, 2);
            l3stride4 = GaInput.computeMissingHitrate(l3lookup, 4);
            l3stride8 = GaInput.computeMissingHitrate(l3lookup, 8);
        }

        gaInput = new GaInput(multiMAPS, l1lookup, l2lookup, l3lookup);
        assert(gaInput.levelCount() == cacheLevels);

        ratios = gaInput.getPlateauBWRatios();
        for (int i = 0; i < GaInput.PLATEAU_COUNT; i++){
            Logger.inform("plateau ratio: " + ratios[i].doubleValue());
        }
        
        Logger.warn("In init L1 stride 2,4,8 = " + String.valueOf(l1stride2) + "," + String.valueOf(l1stride4) + "," + String.valueOf(l1stride8)); 
        Logger.warn("In init L1 stride 2,4,8 = " + String.valueOf(l2stride2) + "," + String.valueOf(l2stride4) + "," + String.valueOf(l2stride8));
        Logger.warn("In init L1 stride 2,4,8 = " + String.valueOf(l3stride2) + "," + String.valueOf(l3stride4) + "," + String.valueOf(l3stride8));
        cores_per_node  = database.getBaseResourceCoresPerNode(baseResource);
        Logger.inform("LAURA cores per node =" + cores_per_node );
        return true;
    }

    void readTraceFiles() throws Exception {
        synchronized(trainingLock) {

            int cpuCount = testCase.getCpu();
            //Requires Java 1.5 String numCPU = String.format("%04d", cpuCount);
            StringBuffer numCPU = new StringBuffer(String.valueOf(cpuCount));
            while (numCPU.length() < PADDED_LENGTH) {
                numCPU.insert(0, '0');
            }
            String filePrefix = testCase.getApplication();
            filePrefix += "_";
            filePrefix += testCase.getDataset();
            filePrefix += "_";
            filePrefix += numCPU.toString();
            filePrefix += "_";
            
            for (int i = 0; i < cpuCount; ++i) {
                StringBuffer taskNum = new StringBuffer(String.valueOf(i));
                while (taskNum.length() < PADDED_LENGTH) {
                    taskNum.insert(0, '0');
                }
                String filename = filePrefix + taskNum.toString() + ".sysid" + String.valueOf(sysId);
                Logger.inform("reading trace file " + filename);
                try {
                    FileReader fileReader = new FileReader(new File(traceDir + filename));
                    LineNumberReader reader = new LineNumberReader(fileReader);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        StringTokenizer tokenizer = new StringTokenizer(Util.cleanComment(line));
                        if (tokenizer.countTokens() > 0){
                            int count = 1;
                            long references = 0;
                            double hitRates[] = new double[cacheLevels];
                            
                            while (tokenizer.hasMoreTokens()) {
                                String token = tokenizer.nextToken();
                                if (count == 3) {
                                    references = Long.parseLong(token);
                                } else if (count > 3 && count <= cacheLevels + 3) {
                                    hitRates[count - 4] = Double.parseDouble(token);
                                } 
                                ++count;
                            }
                        
                            calcZoneTotals(i, references, hitRates);
                        }
                    }
                } catch (Exception e) {
                    Logger.error(e, "Exception while trying to read file " + filename);
                    throw e;
                }
            }
        }
    }

    void calcZoneTotals(int taskId, long references, double[] hitRates) {
        /*
         * |  zone 0
         * |__________
         * |          |
         * |          | zone 2
         * |          |__________
         * |          |          |
         * |          | zone 9   | zone 4
         * |          |_________ |_________
         * |          |         |          |
         * |          | zone 16 | zone 11  | zone 6
         * |          |________ |_________ |_________  stride 2
         * |                   |          |
         * |                   | zone 18  | zone 13
         * |                   |_________ |_________   stride 4
         * |                             |
         * |                             | zone 20
         * |                             |__________   stride 8
         * |________________________________________
         */
        double l1 = hitRates[0];
        double l2 = hitRates.length > 1 ? hitRates[1] : 0;
        double l3 = hitRates.length > 2 ? hitRates[2] : 0;
        int zone;
        /* LAURA  setting break points to decide where to bin the data */
        /*    L1 break point     L2 break point        L3 break point  */
        double l1BreakPoint = 99.95;
        double l2S2BreakFactor = .015, l3S2BreakFactor = .1;
        double l2S4BreakFactor = .2, l3S4BreakFactor = .25;
        double l2S8BreakFactor = .3, l3S8BreakFactor = .35;

        /* some data resides between the concrete plateaus, for that data
           we need to split the mem ref and give some to each zone
           the split number says what percent to go to the lower level */
        /* split goes to L3 over L2 */
        double splitToL3 = 0.75;
        /* split goes to MM over L3 or L2 depending on structure */
        double splitToMM = .95; /* study 2 has 90% */
        /* weird S2 S8 factors used for really low hitrates */
        /* for stride-8 have different split goes to MM over L3/L2 */
        double splitToMMS8 = 0.75;

        int upperZone;
        long newRef;
        double limit=100.0;
          /* Logger.warn("DEBUG LAURA check if define cacheLevels =  " + Integer.toString(cacheLevels)); */
        int index = numWeightFiles > 1 ? taskId : 0;
        /* DEFINE zoneFactor */
        double[] zoneFactor = new double[NUM_ZONES];

           /* STRIDE-2 */
        if( ratios[0] != null ){
           if (ratios[0].doubleValue() > 1.6 ) { 
               zoneFactor[0] = 0.25;
           }else{
               zoneFactor[0] = 1.0;
           }

        }else{
          Logger.error("null ratio\n");
        }
           zoneFactor[1] = 0.0; /* no drop zones */
           zoneFactor[2] = 0.75; //ratio[0]
           zoneFactor[3] = 0.0; /* no drop zones */ 
           zoneFactor[4] = (cores_per_node/4)*ratios[1].doubleValue(); 
           zoneFactor[5] = 0.0; /* no drop zones */ 
           zoneFactor[6] = (cores_per_node/4)*ratios[2].doubleValue(); 
       
           /* STRIDE - 4 */
           zoneFactor[7] = 0.0;
           zoneFactor[8] = 0.0; /* no drop zones */ 
           zoneFactor[9] = (cores_per_node/2)*ratios[3].doubleValue(); 
           zoneFactor[10] = 0.0; /* no drop zones */ 
           zoneFactor[11] = (cores_per_node/2)*ratios[4].doubleValue(); 
           zoneFactor[12] = 0.0; /* no drop zones */
           zoneFactor[13] = (cores_per_node/2)*ratios[5].doubleValue(); 
       
           /* STRIDE - 8 */
           zoneFactor[14] = 0.0;
           zoneFactor[15] = 0.0; /* no drop zones */
           zoneFactor[16] = (cores_per_node/1.333)*ratios[6].doubleValue();
           zoneFactor[17] = 0.0; /* no drop zones */
           zoneFactor[18] = (cores_per_node/1.333)*ratios[7].doubleValue();
           zoneFactor[19] = 0.0; /* no drop zones */
           zoneFactor[20] = 2.0*(cores_per_node)*ratios[8].doubleValue();


        zone = -1;


        if (l1 > limit){
          Logger.warn("PROBLEM1 hitrates too big " + Double.toString(l1) 
		+ "," + Double.toString(l2) + "," + Double.toString(l3) + "\n" );
        }


        if (l1 >= l1BreakPoint) {
            zone = 0;
            zoneTotals[index][zone] += zoneFactor[zone]*(double)references;
            totalReferences[index] += zoneFactor[zone]*references;

        /* STRIDE-2 L2 plateau */
        } else if (l1 >= l1stride2 && 
		   l2 >= (100.0 - (100-l2stride2)*l2S2BreakFactor) ) {
            zone = 2;
            zoneTotals[index][zone] += zoneFactor[zone]*references;
            totalReferences[index] += zoneFactor[zone]*references;

        /* STRIDE-2 L3 plateau */
        } else if (l1 >= l1stride2 && l2 >= l2stride2 &&
                   l3 >= (100.0 - (100-l3stride2)*l3S2BreakFactor) &&
                   cacheLevels == 3 ) {
            zone = 4;
            upperZone = 2;
            newRef= (long)(splitToL3*references*zoneFactor[zone]) + 
                      (long)((1.0 - splitToL3)*references*zoneFactor[upperZone]);
            zoneTotals[index][zone] += splitToL3*newRef;
            zoneTotals[index][upperZone] += (1.0-splitToL3)*newRef;
            totalReferences[index] += newRef;

        /* STRIDE-2 MM plateau */   /*METHOD 04 new traces push out of stride2 */
        } else if (l1 >= l1stride2 && l2 >= l2stride2 &&
                  ( ( l3 >= 90.0 && cacheLevels==3 ) || cacheLevels==2)) {
                   /// for turbo first run was 93.0 history was that davinci you can't add 15 because it isn't 75%
                  //( ( l3 >= l3stride2+15.0 && cacheLevels==3 ) || cacheLevels==2)) {
            zone = 6;
            
            if (cacheLevels==2){
               /*split between level 2 and 6 */
               upperZone = 2;
               newRef= (long)(splitToL3*references*zoneFactor[zone]) + 
                          (long)((1.0 - splitToMM)*references*zoneFactor[upperZone]);
            }else {
               /* split between zone 4 and 6 */
               upperZone = 4;
               newRef= (long)(splitToMM*references*zoneFactor[zone]) + 
                          (long)((1.0 - splitToMM)*references*zoneFactor[upperZone]);
            }

            zoneTotals[index][zone] += splitToMM*newRef;
            zoneTotals[index][upperZone] += (1.0-splitToMM)*newRef;
            totalReferences[index] += newRef;

        /* STRIDE-4 L2 plateau */
        } else if (l1 >= l1stride4 && 
                   l2 >= (100.0 - (100-l2stride4)*l2S4BreakFactor) ) {
            zone = 9; 
            zoneTotals[index][zone] += zoneFactor[zone]*references;
            totalReferences[index] += zoneFactor[zone]*references;

        /* STRIDE-4 L3 plateau */
        } else if (l1 >= l1stride4 && l2 >= l2stride4 && 
                   l3 >= (100.0 - (100-l3stride4)*l3S4BreakFactor) && cacheLevels==3) {
            zone = 11;
            upperZone=9;
            newRef= (long)(splitToL3*references*zoneFactor[upperZone]) + 
                      (long)((1.0 - splitToL3) * references *zoneFactor[zone]);
            zoneTotals[index][zone] += splitToL3*newRef;
            zoneTotals[index][upperZone] += (1.0-splitToL3)*newRef;
            totalReferences[index] += newRef;

        /* STRIDE-4 MM plateau */
        } else if (l1 >= l1stride4 && l2 >= l2stride4 && 
                  ( ( l3 >= l3stride4 && cacheLevels==3 ) || cacheLevels==2)) {
            zone = 13;
            if (cacheLevels==2){
               /*split between level 9 and 13 */
               upperZone = 9;
               newRef= (long)(splitToL3*references*zoneFactor[zone]) + 
                        (long)((1.0 - splitToMM) * references *zoneFactor[upperZone]);
            }else {
               /* split between zone 11 and 13 */
               upperZone = 11;
               newRef= (long)(splitToMM*references*zoneFactor[zone]) + 
                         (long)((1.0 - splitToMM) * references *zoneFactor[upperZone]);
            }

            zoneTotals[index][zone] += splitToMM*newRef;
            zoneTotals[index][upperZone] += (1.0-splitToMM)*newRef;
            totalReferences[index] += newRef;

        /* STRIDE-8 L2 plateau */
        } else if (l1 >= l1stride8 && 
                   l2 >= (100.0 - (100-l2stride8)*l2S8BreakFactor) ) {
            zone = 16;
            zoneTotals[index][zone] += zoneFactor[zone]*references;
            totalReferences[index] += zoneFactor[zone]*references;

        /* STRIDE-8 L3 plateau */
        } else if (l1 >= l1stride8 && l2 >= l2stride8 && 
                   l3 >= (100.0 - (100-l3stride8)*l3S8BreakFactor) && cacheLevels==3 ) {
            zone = 18;
            upperZone=16;
            newRef= (long)(splitToL3*references*zoneFactor[upperZone]) + 
                     (long)((1.0 - splitToL3) * references *zoneFactor[zone]);
            zoneTotals[index][zone] += splitToL3*newRef;
            zoneTotals[index][upperZone] += (1.0-splitToL3)*newRef;
            totalReferences[index] += newRef;

            /* STRIDE-8 MM plateau */
        } else if (l1 >= 0.0 && l2 >= 0.0 &&
                   ( ( l3 >= 0.0 && cacheLevels==3 ) || cacheLevels==2)){
            zone = 20;
            if (cacheLevels==2){
               /*split between level 16 and 20 */
               upperZone = 16;
               newRef= (long)(splitToMMS8*references*zoneFactor[zone]) + 
                         (long)((1.0 - splitToMMS8) * references *zoneFactor[upperZone]);
            }else {
               /* split between zone 18 and 20 */
               upperZone = 18;
               newRef= (long)(splitToMMS8*references*zoneFactor[zone]) + 
                         (long)((1.0 - splitToMMS8) * references *zoneFactor[upperZone]);
            }
            //method 12 checking if hits reach 0
            if ( references>10000 && (l1<0.03 && l2<0.03 && ( ( l3 >= 0.0 && cacheLevels==3 ) || cacheLevels==2)) ){
               zoneTotals[index][21] = 9999;
            }

            zoneTotals[index][zone] += splitToMMS8*newRef;
            zoneTotals[index][upperZone] += (1.0-splitToMM)*newRef;
            totalReferences[index] += newRef;


        } else {
           /* problem because point did NOT get zoned */
           Logger.warn("PROBLEM no zone found L1,L2,L3 =  " + String.valueOf(l1) + 
			"," + String.valueOf(l2)+","+String.valueOf(l3));
        }
         //Logger.warn("in zoneWeight and l1,l2,l3 = " + String.valueOf(l1) + "," + 
         //	String.valueOf(l2)+","+String.valueOf(l3) + " zone= " + Integer.toString(zone)); 
    }

    void computeZoneWeights() {

        for (int i = 0; i < numWeightFiles; ++i) {
            // need to make first recalc on zone0 and zone6
            double weight0 = (double) zoneTotals[i][0] / totalReferences[i];
            double weight2 = (double) zoneTotals[i][2] / totalReferences[i];
            double weight4 = (double) zoneTotals[i][4] / totalReferences[i];

            double maxL1 = 0.20;
            double minL1 = 0.05;//LAURA was set to 0.04
            double minS4mm = 0.11; //LAURA changed from .15 method27
            //method32 if ( (weight0>maxL1) ) {
            //method32      Logger.warn("in Training your adjusting zone 0 from " + weight0 + " to " + maxL1 + "\n");
            //method32      totalReferences[i] = (long)( (double) totalReferences[i] - (double) zoneTotals[i][0] );
            //method32      totalReferences[i] = (long)( maxL1*(double) totalReferences[i]  + (double) totalReferences[i] ) ;
            //method32      zoneTotals[i][0] = (long) ( maxL1*totalReferences[i] ) ;
            //method32 }
            if ( (double) zoneTotals[i][21] > 0 ){
               //method 12 set s4 and s8 mm at 10%
                 double weight13 =  (double) zoneTotals[i][13] / totalReferences[i] ;
                 double weight20 =  (double) zoneTotals[i][20] / totalReferences[i] ;
                 double weight9 =  (double) zoneTotals[i][9] / totalReferences[i] ;
                 //first reset total
                 if (weight13 < minS4mm ){
                     totalReferences[i] = (long)( (double) totalReferences[i] - (double) zoneTotals[i][13] );
                 }
                 if (weight20 < minS4mm ){
                     totalReferences[i] = (long)( (double) totalReferences[i] - (double) zoneTotals[i][20] );
                 }
                 if (weight9 < minS4mm ){
                     totalReferences[i] = (long)( (double) totalReferences[i] - (double) zoneTotals[i][9] );
                 }

                 if( (weight20 < minS4mm ) && (weight13 < minS4mm ) && (weight9 < minS4mm ) ){
                     totalReferences[i] = (long)( 3.0*minS4mm*(double) totalReferences[i]  + (double) totalReferences[i] ) ;
                     zoneTotals[i][13] = (long) ( minS4mm*totalReferences[i] ) ;
                     zoneTotals[i][20] = (long) ( minS4mm*totalReferences[i] ) ;
                     zoneTotals[i][9] = (long) ( minS4mm*totalReferences[i] ) ;
                 }else if ( (weight20 < minS4mm ) && (weight13 < minS4mm )) {
                     totalReferences[i] = (long)( 2.0*minS4mm*(double) totalReferences[i]  + (double) totalReferences[i] ) ;
                     zoneTotals[i][13] = (long) ( minS4mm*totalReferences[i] ) ;
                     zoneTotals[i][20] = (long) ( minS4mm*totalReferences[i] ) ;
                 }else if ( (weight20 < minS4mm ) && (weight9 < minS4mm ) ){
                     totalReferences[i] = (long)( 2.0*minS4mm*(double) totalReferences[i]  + (double) totalReferences[i] ) ;
                     zoneTotals[i][9] = (long) ( minS4mm*totalReferences[i] ) ;
                     zoneTotals[i][20] = (long) ( minS4mm*totalReferences[i] ) ;
                 }else if ( (weight13 < minS4mm ) && (weight9 < minS4mm ) ){
                     totalReferences[i] = (long)( 2.0*minS4mm*(double) totalReferences[i]  + (double) totalReferences[i] ) ;
                     zoneTotals[i][13] = (long) ( minS4mm*totalReferences[i] ) ;
                     zoneTotals[i][9] = (long) ( minS4mm*totalReferences[i] ) ;
                 }else if (weight13 < minS4mm ){
                     totalReferences[i] = (long)( minS4mm*(double) totalReferences[i]  + (double) totalReferences[i] ) ;
                     zoneTotals[i][13] = (long) ( minS4mm*totalReferences[i] ) ;
                 }else if (weight20 < minS4mm ){
                     totalReferences[i] = (long)( minS4mm*(double) totalReferences[i]  + (double) totalReferences[i] ) ;
                     zoneTotals[i][20] = (long) ( minS4mm*totalReferences[i] ) ;
                 }else if (weight9 < minS4mm ){
                     totalReferences[i] = (long)( minS4mm*(double) totalReferences[i]  + (double) totalReferences[i] ) ;
                     zoneTotals[i][9] = (long) ( minS4mm*totalReferences[i] ) ;
                 }


            }
            // Method 8 where if zone0 isn't > .04 then make it so
            if (weight0 < minL1){
                 totalReferences[i] = (long)( (double) totalReferences[i] - (double) zoneTotals[i][0] );
                 totalReferences[i] = (long)( minL1*(double) totalReferences[i]  + (double) totalReferences[i] ) ;
                 zoneTotals[i][0] = (long) ( minL1*totalReferences[i] ) ;
            }


            for (int j = 0; j < NUM_ZONES; ++j) {
                //First must recalculate zone0 and zone6
                double weight = (double) zoneTotals[i][j] / totalReferences[i];
                zoneWeights[i][j] = weight < EPSILON ? 0.0 : weight;
                //Logger.warn("in computeZoneWeights weight for zone " + String.valueOf(j) + " file " + String.valueOf(i) + " weight " + zoneWeights[i][j]);
            }
        }
    }

    boolean checkZoneWeights() {
        boolean runGA = true;
        boolean zoneWeightsExist = true;
        int zonedbid=0;
        synchronized(trainingLock) {
            for (int i = 0; i < numWeightFiles; ++i) {
                /* LAURA calling a method to compute the difference between one element of the 
                   current zoneWeight and the same element from a weight in the DB 
                   The method is in dbase/Postgres.java and computes the number of matches where
                   a match is when the difference between a given weight element is <= epsilon.
                   To be considered the same weight all weight elements must be matches.*/
                zoneWeightsDbid[i] = database.existsZoneWeights(zoneWeights[i], EPSILON);
                if (zoneWeightsDbid[i] == Database.InvalidDBID) {
                    zoneWeightsExist = false;
                    String fields[] = new String[NUM_ZONES + 1];
                    /* zoneWeight[FILES][NUM_ZONES] */
                    Logger.warn(" zoneWeights.length = " + String.valueOf(zoneWeights.length) );
                    for (int j = 0; j < NUM_ZONES; ++j) {
                        fields[j] = Double.toString(zoneWeights[i][j]);
                        Logger.warn("adding fields j=" + String.valueOf(j) + " weight = " + fields[j] );
                    }
                    fields[NUM_ZONES] = "Basic zoning model 1";
                    
                    zoneWeightsDbid[i] = database.addZoneWeights(fields);
                    if (zoneWeightsDbid[i] == Database.InvalidDBID){
                        Logger.error("Cannot add zone weight to database");
                    }
                }else{
                    zonedbid=i;
                }
            }
            if (zoneWeightsExist) {
                /*LAURA CHANGED FROM:
                  int dbid = database.existsMemoryBenchmarkData(zoneWeightsDbid[0], baseResource);
                  if (dbid != Database.InvalidDBID) {
                  Logger.inform("Zone weights and memory benchmark data (" + String.valueOf(dbid) +
                  ") already exist for base resource " + Integer.toString(baseResource));
                */
                if (database.getTrainedMachineProfile(zoneWeightsDbid[0], baseResource) != Database.InvalidDBID){
                    //if (database.existsMemoryBenchmarkData(zoneWeightsDbid[0], baseResource)) {
                    Logger.inform("Zone weights and memory benchmark data already exist for base resource "
                                  + Integer.toString(baseResource) + " Zone weight= " + Integer.toString(zoneWeightsDbid[zonedbid]));
                    if (!forceNewWeights){
                        Logger.inform("Opting out of GA re-run for base_resource " + baseResource);
                        runGA = false;
                    }
                } else {
                    Logger.inform("Zone weights already exist for base resource "
                                  + Integer.toString(baseResource));
                }
            }
            int testCaseDbid = database.getDbid(testCase);
            if (testCaseDbid ==-1){Logger.error("ERROR jTraining: can not find test case dbid in database\n");}
            if (!database.existsApplicationZoneWeight(sysId, testCaseDbid, zoneWeightsDbid[0])) {
                Logger.inform("Inserting zone weight " + zoneWeightsDbid[0] + " for base_resource " + baseResource);
                String fields[] = {Integer.toString(sysId), Integer.toString(testCaseDbid),
                                   Integer.toString(zoneWeightsDbid[0])};
                database.addApplicationZoneWeight(fields);
            } else {
                Logger.inform("Not inserting zone weight " + zoneWeightsDbid[0] + " for base_resource " + baseResource);
            }
        }
        return runGA;
    }

    boolean createGAInputFiles() {
        final int DECIMAL_PLACES = 5;
        String filename = null;
        PrintWriter config = null;
        try {
            if (numWeightFiles > 1) {
                File configFile = new File(gaConfig);
                configFile.createNewFile();
                FileWriter fileWriter = new FileWriter(configFile);
                config = new PrintWriter(fileWriter);
            }
            for (int i = 0; i < numWeightFiles; ++i) {
                if (numWeightFiles > 1) {
                    StringBuffer cpu = new StringBuffer(String.valueOf(i));
                    while (cpu.length() < PADDED_LENGTH) {
                        cpu.insert(0, '0');
                    }
                    filename = prefix + "." + cpu.toString() + ".weig";
                    config.println(Integer.toString(i) + ": --weight " + filename);
                } else {
                    int cpuCount = testCase.getCpu();
                    filename = prefix + ".0_" + Integer.toString(cpuCount) + ".weig";
                    weightFile = filename;
                }
                File file = new File(gaInputDir + filename);
                file.createNewFile();
                FileWriter fstream = new FileWriter(file);
                PrintWriter out = new PrintWriter(fstream);
                for (int j = 0; j < NUM_ZONES; ++j) {
                    String weight = Double.toString(zoneWeights[i][j]);
                    if (weight.substring(weight.indexOf('.') + 1).length() < DECIMAL_PLACES) {
                        while (weight.substring(weight.indexOf('.') + 1).length() < DECIMAL_PLACES) {
                            weight += "0";
                        }
                    } else {
                        weight = weight.substring(0, 2 + DECIMAL_PLACES);
                    }
                    out.println("? " + weight + " zone" + Integer.toString(j));
                }
                out.close();
            }
            if (numWeightFiles > 1) {
                config.close();
            }
        } catch (Exception e) {
            Logger.error(e, "Exception while creating GATraining input files");
            return false;
        }
        return true;
    }

    boolean runGA() {
        if (!createGAInputFiles()) {
            return false;
        }
        String executable = gaTrainingPath + "/bin/" + gaTraining;
        String command = null;
        try {
            if (numWeightFiles > 1) {
                command = executable + " --input " + multiMAPS + " --config " + gaConfig
                        + " --dirwei " + gaInputDir + " --prefix " + gaInputDir + prefix + " " + trainingOptions;
            } else {
                command = executable + " --input " + multiMAPS + " --weight " + weightFile
                        + " --dirwei " + gaInputDir + " --prefix " + gaInputDir + prefix + " " + trainingOptions;
            }
            if (LinuxCommand.execute(command) == null) {
                Logger.error(gaTraining + " failed during execution for br" + baseResource);
                return false;
            }

            Logger.inform("Ga training output file??? " + gaInputDir + prefix + ".r000.out");

            BufferedReader gaout = new BufferedReader(new FileReader(gaInputDir + prefix + ".r000.out"));
            String line = gaout.readLine();
            while (line != null){
                Logger.inform(line);
                line = gaout.readLine();
            }
            gaout.close();
            
        } catch (Exception e) {
            Logger.error(e, "Exception executing " + gaTraining);
            return false;
        }
        return createMachineProfile(gaInputDir + prefix);
    }

    boolean createMachineProfile(String prefix) {
        boolean success = false;
        synchronized(trainingLock){
            try {
                String executable = gaTrainingPath + "/scripts/genMemProfList.pl";
                String command = executable + " --pre " + prefix + " --cnt " + Integer.toString(numWeightFiles)
                    + " --lvl " + Integer.toString(cacheLevels) + " --dbx " + Integer.toString(baseResource)
                    + " --sys " + Integer.toString(sysId) + " --bwm " + bwMethod + " --wei " + Integer.toString(zoneWeightsDbid[0])
                    + " --ext " + "auto-trained-profile-" + gaTraining + "-" + trainingOptions.replace(" ", "-");
                Logger.warn("genMemProfile command =" + command );
                if (LinuxCommand.execute(command) == null) {
                    Logger.error("genMemProfList.pl failed during execution for br" + baseResource);
                    return false;
                }
            } catch (Exception e) {
                Logger.error(e, "Exception executing genMemProfList.pl");
                return false;
            }
            success = recordTrainingData(prefix);
        }
        return success;
    }

    boolean recordTrainingData(String prefix) {
        boolean success = false;
        String input = prefix + ".0_" + Integer.toString(numWeightFiles) + ".profiles";
        Actions actions = new Actions();
        String[] args = {"--action", "add", "--type", "MachWMemoryProfile", "--input", input};
        synchronized(trainingLock){
            success = actions.run(args);
        }
        return success;
    }

    public static void main(String args[]) {
        try {
            ConfigSettings.readConfigFile();
            CommandLineParser commandLineParser = new CommandLineParser(args);
            TestCase testCase = new TestCase(commandLineParser.application, commandLineParser.dataset,
                    commandLineParser.cpuCount, commandLineParser.agency, commandLineParser.project, commandLineParser.round);
            Integer[] baseResourceList = commandLineParser.baseResourceList;
            Training[] trainingList = new Training[baseResourceList.length];
            for (int i = 0; i < baseResourceList.length; i++){
                trainingList[i] = new Training(commandLineParser.forceNewWeights, commandLineParser.separateWeights, commandLineParser.traceDir,
                                               commandLineParser.scratchDir, commandLineParser.training, commandLineParser.method,
                                               commandLineParser.trainingOptions, commandLineParser.multiMaps, commandLineParser.prefix,
                                               baseResourceList[i].intValue(), testCase, commandLineParser.sysId, null);
                trainingList[i].thread.start();
            }

            boolean trainingFailure = false;
            try {
                Logger.inform("Waiting for " + baseResourceList.length + " Training runs");
                for (int i = 0; i < baseResourceList.length; i++){
                    trainingList[i].thread.join();
                }
            } catch (InterruptedException e){
                e.printStackTrace();
                trainingFailure = true;
                Logger.error("Training thread run interrupted");
            }

            for (int i = 0; i < baseResourceList.length; i++){
                if (trainingList[i].getRunStatus() != PSaPPThread.THREAD_SUCCESS){
                    trainingFailure = true;
                }
            }
            if (!trainingFailure){
                Logger.inform("\n*** DONE *** SUCCESS *** SUCCESS *** SUCCESS *****************\n");
            } else {
                Logger.inform("\n*** FAIL *** FAIL *** FAIL *****************\n");
            }
        } catch (Exception e) {
            Logger.error(e, "*** FAIL *** FAIL *** FAIL Exception *****************\n");
        }
    }
}

class CommandLineParser implements CommandLineInterface {

    OptionParser optionParser;
    public String traceDir;
    public String scratchDir;
    public String training;
    public String method;
    public String trainingOptions = "";
    public String multiMaps;
    public String prefix;
    public String agency;
    public String project;
    public String application;
    public String dataset;
    public Integer round;
    public Integer cpuCount;
    public Integer sysId;
    public Integer[] baseResourceList;
    public boolean separateWeights;
    public boolean forceNewWeights;
    static final String[] ALL_OPTIONS = {
        "help:?",
        "trace:s",
        "scratch:s",
        "training:s",
        "method:s",
        "training_options:s",
        "separate_weights:?",
        "force_new:?",
        "multiMaps:s",
        "prefix:s",
        "agency:s",
        "project:s",
        "round:i",
        "application:s",
        "dataset:s",
        "cpu_count:i",
        "sysid:i",
        "base_resource:s"
    };
    static final String helpString =
            "[Basic Params]:\n"
            + "    --help                                 : print a brief help message\n"
            + "[Script Params]:\n"
            + "    --trace            /path/to/files      : path to directory containing .sysid<x> trace files [REQ]\n"
            + "    --scratch          /path/to/dir        : path to directory where temp files will be created [REQ]\n"
            + "    --training         <training method>   : specify which GA Training executable to use (Default = ga_stretched)\n"
            + "    --method           <bw method>         : specify which bw method to use (Default = BWstretchedExp)\n"
            + "    --training_options <options>           : specify any default options for GA Training\n"
            + "    --use_separate_weights                 : compute separate zone weight totals and create seperate weight file per task\n"
            + "    --force_new                            : always run ga training and always perform DB inserts\n"
            + "    --multiMaps        /path/to/file       : path to MultiMAPS file\n"
            + "    --prefix           <prefix>            : prefix of GA output filename (See GATraining usage) [REQ]\n"
            + "    --agency           <agency>            : funding agency (listed in PSaPP config file) [REQ]\n"
            + "    --project          <project>           : project name (listed in PSaPP config file) [REQ]\n"
            + "    --round            <round>             : round number [REQ]\n"
            + "    --application      <application>       : application name (listed in PSaPP config file) [REQ]\n"
            + "    --dataset          <dataset>           : dataset name (listed in PSaPP config file) [REQ]\n"
            + "    --cpu_count        <CPU_count>         : number of CPUs [REQ]\n"
            + "    --sysid            <cache_id>          : system index in cache_structures, overrides the default found via base_resource\n"
            + "    --base_resource    <base resource>     : base resource IDs [REQ]\n";

    public CommandLineParser(String argv[]) {
        optionParser = new OptionParser(ALL_OPTIONS, this);
        if (argv.length < 1) {
            optionParser.printUsage("");
        }
        optionParser.parse(argv);
        if (optionParser.isHelp()) {
            optionParser.printUsage("");
        }
        if (!optionParser.verify()) {
            Logger.error("Error in command line options");
        }
        traceDir = (String) optionParser.getValue("trace");
        scratchDir = (String) optionParser.getValue("scratch");
        training = optionParser.getValue("training") != null ? (String) optionParser.getValue("training") : "ga_stretched";
        method = optionParser.getValue("method") != null ? (String) optionParser.getValue("method") : "BWstretchedExp";
        trainingOptions = optionParser.getValue("training_options") != null ? (String) optionParser.getValue("training_options") : "";
        separateWeights = optionParser.getValue("use_separate_weights") != null ? true : false;
        forceNewWeights = optionParser.getValue("force_new") != null ? true : false;
        multiMaps = (String) optionParser.getValue("multiMaps");
        prefix = (String) optionParser.getValue("prefix");
        agency = (String) optionParser.getValue("agency");
        project = (String) optionParser.getValue("project");
        round = (Integer) optionParser.getValue("round");
        application = (String) optionParser.getValue("application");
        dataset = (String) optionParser.getValue("dataset");
        cpuCount = (Integer) optionParser.getValue("cpu_count");
        sysId = (Integer)optionParser.getValue("sysid");
        String baseResourceString = (String)optionParser.getValue("base_resource");
        baseResourceList = Util.machineList(baseResourceString);
    }

    public boolean verifyValues(HashMap values) {
        if (values.get("trace") == null) {
            return false;
        } else if (values.get("scratch") == null) {
            return false;
        } else if (values.get("prefix") == null) {
            return false;
        } else if (values.get("agency") == null) {
            return false;
        } else if (values.get("project") == null) {
            return false;
        } else if (values.get("round") == null) {
            return false;
        } else if (values.get("application") == null) {
            return false;
        } else if (values.get("dataset") == null) {
            return false;
        } else if (values.get("cpu_count") == null) {
            return false;
        } else if (values.get("base_resource") == null) {
            return false;
        }
        return true;
    }

    public TestCase getTestCase(HashMap values) {
        return null;
    }

    public boolean isHelp(HashMap values) {
        return (values.get("help") != null);
    }

    public boolean isVersion(HashMap values) {
        return false;
    }

    public void printUsage(String str) {
        System.out.println("\n" + str + "\n");
        System.out.println(helpString);
        String all = "usage :\n";
        for (int i = 0; i < ALL_OPTIONS.length; ++i) {
            all += ("\t--" + ALL_OPTIONS[i] + "\n");
        }
        all += ("\n" + str);
    }
}
