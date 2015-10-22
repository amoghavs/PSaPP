package PSaPP.dbase;
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
import java.util.*;
import java.io.*;

public final class BinaryFiles extends Database {

    static final int fileHeaderId = 111000111;
    static final int defaultBRStartIdx = 1;
    static final int defaultTCStartIdx = 1;
    static final int defaultMPStartIdx = 101;
    static final int defaultNPStartIdx = 101;
    static final int defaultPRStartIdx = 1001;
    static final int defaultPGStartIdx = 10001;
    String dataPath;
    HashMap baseResources;
    HashMap baseResourcesSet;
    int baseResourceCIdx;
    HashMap testCases;
    HashMap testCasesSet;
    int testCaseCIdx;
    HashMap predictionGroups;
    int predictionGroupCIdx;
    HashMap actualRuntimes;
    HashMap actualRuntimesSet;
    int actualRuntimesCIdx;
    HashMap memoryProfiles;
    int memoryProfileCIdx;
    HashMap networkProfiles;
    int networkProfileCIdx;
    HashMap machineProfiles;
    int machineProfileCIdx;
    HashMap defaultProfiles;
    HashMap predictionRuns;
    int predictionRunsCIdx;
    HashMap traceStatuses;
    int phaseStatusesCIdx;
    HashMap phaseStatusesSet;
    HashMap phaseStatuses;
    boolean modifiedFlag;

    public BinaryFiles(String dbasefile) {

        if (dbasefile == null) {
            Logger.error("File name is required for binary files");
        }

        baseResources = new HashMap();
        baseResourcesSet = new HashMap();
        testCases = new HashMap();
        testCasesSet = new HashMap();
        predictionGroups = new HashMap();
        actualRuntimes = new HashMap();
        actualRuntimesSet = new HashMap();

        memoryProfiles = new HashMap();
        networkProfiles = new HashMap();
        machineProfiles = new HashMap();
        defaultProfiles = new HashMap();
        predictionRuns = new HashMap();
        traceStatuses = new HashMap();

        phaseStatusesSet = new HashMap();
        phaseStatuses = new HashMap();

        baseResourceCIdx = defaultBRStartIdx;
        testCaseCIdx = defaultTCStartIdx;
        memoryProfileCIdx = defaultMPStartIdx;
        networkProfileCIdx = defaultNPStartIdx;
        machineProfileCIdx = defaultPRStartIdx;
        predictionGroupCIdx = defaultPGStartIdx;
        actualRuntimesCIdx = 0;
        predictionRunsCIdx = 0;
        phaseStatusesCIdx = 0;

        dataPath = dbasefile;

        modifiedFlag = false;
    }

    public int getSimMemoryTimeGroup() {
        return 10000;
    }

    public int getSimReportGroup() {
        return 10000;
    }

    public String[] getEmailsFromTestCase(TestCase testCase) {
        return null;
    }

    boolean dumpHeaders(DataOutputStream outStream) {
        try {
            outStream.writeInt(fileHeaderId);

            outStream.writeInt(baseResourceCIdx);
            outStream.writeInt(testCaseCIdx);
            outStream.writeInt(memoryProfileCIdx);
            outStream.writeInt(networkProfileCIdx);
            outStream.writeInt(machineProfileCIdx);
            outStream.writeInt(predictionGroupCIdx);
            outStream.writeInt(actualRuntimesCIdx);
            outStream.writeInt(predictionRunsCIdx);
            outStream.writeInt(phaseStatusesCIdx);
        } catch (Exception e) {
            Logger.warn("Can not write back to the database");
            return false;
        }
        return true;
    }

    static boolean dumpRecords(DataOutputStream outStream, HashMap records, String signature, String info) {
        int size = records.size();
        Iterator it = records.keySet().iterator();
        Logger.debug("Dumping " + size + " entries for " + info);
        try {
            outStream.writeInt(size);
            while (it.hasNext()) {
                Object key = it.next();
                Object[] tokens = (Object[]) records.get(key);
                assert (tokens != null);
                if (!Util.recordToFile(tokens, signature, outStream)) {
                    Logger.warn("Record can not be written for " + info);
                    return false;
                }
            }
        } catch (Exception e) {
            Logger.warn("Can not write the database back(" + info + ")\n" + e);
            return false;
        }
        return true;
    }

    public int getSysidFromBaseResource(int baseResource){
        assert(false);
        return 0;
    }

    boolean dumpBaseResources(DataOutputStream outStream) {
        return dumpRecords(outStream, baseResources, baseResourcesSignature, "base resources");
    }

    boolean dumpTestCases(DataOutputStream outStream) {
        return dumpRecords(outStream, testCases, testCasesSignature, "test cases");
    }

    boolean dumpPredictionGroups(DataOutputStream outStream) {
        return dumpRecords(outStream, predictionGroups, predictionGroupsSignature, "prediction groups");
    }

    boolean dumpActualRuntimes(DataOutputStream outStream) {
        return dumpRecords(outStream, actualRuntimes, actualRuntimesSignature, "actual runtimes");
    }

    boolean dumpMemoryProfiles(DataOutputStream outStream) {
        HashMap records = memoryProfiles;
        int size = records.size();
        Iterator it = records.keySet().iterator();
        Logger.debug("Dumping " + size + " entries for memory profiles");
        try {
            outStream.writeInt(size);
            while (it.hasNext()) {
                Object key = it.next();
                Object[] tokens = (Object[]) records.get(key);
                assert (tokens != null);
                String restOfSig = Util.getMemoryProfileSignature((String) tokens[0],
                        (Integer) tokens[1]);
                assert (restOfSig != null);
                String signature = memoryProfilesSignature + restOfSig;
                if (!Util.recordToFile(tokens, signature, outStream)) {
                    Logger.warn("Can not write memory profile record");
                    return false;
                }
            }
        } catch (Exception e) {
            Logger.warn("Error writing the database back( memory profile )\n" + e);
            return false;
        }
        return true;
    }

    boolean dumpNetworkProfiles(DataOutputStream outStream) {
        return dumpRecords(outStream, networkProfiles, networkProfilesSignature, "network profiles");
    }

    boolean dumpMachineProfiles(DataOutputStream outStream) {
        return dumpRecords(outStream, machineProfiles, machineProfilesSignature, "machine profiles");
    }

    boolean dumpDefaultProfiles(DataOutputStream outStream) {
        return dumpRecords(outStream, defaultProfiles, defaultProfilesSignature, "default profiles");
    }

    boolean dumpPredictionRuns(DataOutputStream outStream) {
        return dumpRecords(outStream, predictionRuns, predictionRunsSignature, "prediction runs");
    }

    boolean dumpTraceStatuses(DataOutputStream outStream) {
        return dumpRecords(outStream, traceStatuses, traceStatusesSignature, "trace statuses");
    }

    boolean dumpPhaseStatuses(DataOutputStream outStream) {
        return dumpRecords(outStream, phaseStatuses, phaseStatusesSignature, "phase statuses");
    }

    boolean readHeaders(DataInputStream inpStream) {
        try {
            int format = inpStream.readInt();
            if (format == fileHeaderId) {
                baseResourceCIdx = inpStream.readInt();
                testCaseCIdx = inpStream.readInt();
                memoryProfileCIdx = inpStream.readInt();
                networkProfileCIdx = inpStream.readInt();
                machineProfileCIdx = inpStream.readInt();
                predictionGroupCIdx = inpStream.readInt();
                actualRuntimesCIdx = inpStream.readInt();
                predictionRunsCIdx = inpStream.readInt();
                phaseStatusesCIdx = inpStream.readInt();
            } else {
                Logger.warn("Invalid database file format " + format);
                return false;
            }
        } catch (Exception e) {
            Logger.warn("Can not read the database headers\n" + e);
            return false;
        }
        return true;
    }

    static boolean readRecords(DataInputStream inpStream, HashMap records,
            HashMap keys, String signature,
            int keySidx, int ketCnt, String info) {
        try {
            int recordCount = inpStream.readInt();
            Logger.debug("Reading " + recordCount + " " + info);
            for (int i = 0; i < recordCount; i++) {
                Object[] tokens = Util.fileToRecord(signature, inpStream);
                assert (tokens != null);
                records.put(tokens[tokens.length - 1], tokens);
                if (keys != null) {
                    keys.put(Util.genKeyString(tokens, keySidx, ketCnt), tokens[tokens.length - 1]);
                }
            }
            if (keys != null) {
                assert (records.size() == keys.size());
            }
            assert (records.size() == recordCount);
        } catch (Exception e) {
            Logger.warn("Can not read the database for " + info + "\n" + e);
            return false;
        }
        return true;
    }

    boolean readBaseResources(DataInputStream inpStream) {
        return readRecords(inpStream, baseResources, baseResourcesSet, baseResourcesSignature, 7, 1, "base resources");
    }

    boolean readTestCases(DataInputStream inpStream) {
        return readRecords(inpStream, testCases, testCasesSet, testCasesSignature, 0, 6, "test cases");
    }

    boolean readPredictionGroups(DataInputStream inpStream) {
        return readRecords(inpStream, predictionGroups, null, predictionGroupsSignature, 0, 0, "prediction groups");
    }

    boolean readActualRuntimes(DataInputStream inpStream) {
        return readRecords(inpStream, actualRuntimes, actualRuntimesSet, actualRuntimesSignature, 0, 2, "actual runtimes");
    }

    boolean readMemoryProfiles(DataInputStream inpStream) {
        HashMap records = memoryProfiles;
        try {
            int recordCount = inpStream.readInt();
            Logger.debug("Reading " + recordCount + " memory profile");
            for (int i = 0; i < recordCount; i++) {
                Object[] tokens = Util.fileToRecord(memoryProfilesSignature, inpStream, false);
                assert (tokens != null);
                String restOfSig = Util.getMemoryProfileSignature((String) tokens[0],
                        (Integer) tokens[1]);
                assert (restOfSig != null);

                Object[] restOfTokens = Util.fileToRecord(restOfSig, inpStream);
                assert (restOfTokens != null);

                tokens = Util.concatArray(tokens, restOfTokens);

                records.put(tokens[tokens.length - 1], tokens);
            }
            assert (records.size() == recordCount);
        } catch (Exception e) {
            Logger.warn("Can not read the memory profile " + e);
            return false;
        }
        return true;
    }

    boolean readNetworkProfiles(DataInputStream inpStream) {
        return readRecords(inpStream, networkProfiles, null, networkProfilesSignature, 0, 0, "network profiles");
    }

    boolean readMachineProfiles(DataInputStream inpStream) {
        return readRecords(inpStream, machineProfiles, null, machineProfilesSignature, 0, 0, "machine profiles");
    }

    boolean readDefaultProfiles(DataInputStream inpStream) {
        return readRecords(inpStream, defaultProfiles, null, defaultProfilesSignature, 0, 0, "default profiles");
    }

    boolean readPredictionRuns(DataInputStream inpStream) {
        return readRecords(inpStream, predictionRuns, null, predictionRunsSignature, 0, 0, "prediction runs");
    }

    boolean readTraceStatuses(DataInputStream inpStream) {
        return readRecords(inpStream, traceStatuses, null, traceStatusesSignature, 0, 0, "trace statuses");
    }

    boolean readPhaseStatuses(DataInputStream inpStream) {
        return readRecords(inpStream, phaseStatuses, phaseStatusesSet, phaseStatusesSignature, 0, 2, "phase statuses");
    }

    public boolean initialize() {
        boolean retValue = true;

        if (Util.isFile(dataPath)) {
            try {
                DataInputStream inpStream = new DataInputStream(new FileInputStream(dataPath));

                boolean status = readHeaders(inpStream);
                status = status && readBaseResources(inpStream);
                status = status && readTestCases(inpStream);
                status = status && readPredictionGroups(inpStream);
                status = status && readActualRuntimes(inpStream);
                status = status && readMemoryProfiles(inpStream);
                status = status && readNetworkProfiles(inpStream);
                status = status && readMachineProfiles(inpStream);
                status = status && readDefaultProfiles(inpStream);
                status = status && readPredictionRuns(inpStream);
                status = status && readTraceStatuses(inpStream);
                status = status && readPhaseStatuses(inpStream);

                assert status;

                inpStream.close();

            } catch (Exception e) {
                Logger.warn("Read error from the database\n" + e);
                return false;
            }
        }
        boolean status = addSpecialGroup();
        assert status;

        return retValue;
    }

    public boolean commit() {
        boolean retValue = true;
        if (!modifiedFlag) {
            return true;
        }
        try {
            assert (dataPath != null);
            DataOutputStream outStream = new DataOutputStream(new FileOutputStream(dataPath));

            boolean status = dumpHeaders(outStream);
            status = status && dumpBaseResources(outStream);
            status = status && dumpTestCases(outStream);
            status = status && dumpPredictionGroups(outStream);
            status = status && dumpActualRuntimes(outStream);
            status = status && dumpMemoryProfiles(outStream);
            status = status && dumpNetworkProfiles(outStream);
            status = status && dumpMachineProfiles(outStream);
            status = status && dumpDefaultProfiles(outStream);
            status = status && dumpPredictionRuns(outStream);
            status = status && dumpTraceStatuses(outStream);
            status = status && dumpPhaseStatuses(outStream);

            assert status;

            outStream.close();

        } catch (Exception e) {
            Logger.warn("Can not close the database file\n" + e);
            return false;
        }
        return retValue;
    }

    public int addBaseResource(String[] fields) {
        int dbid = InvalidDBID;
        Object[] tokens = Util.stringsToRecord(fields, baseResourceCIdx, baseResourcesSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in base resource is invalid " + Util.listToString(fields));
            return dbid;
        }
        String key = Util.genKeyString(fields, 7, 1);
        if (baseResourcesSet.get(key) == null) {
            dbid = baseResourceCIdx++;
            assert (tokens != null);
            baseResources.put(tokens[tokens.length - 1], tokens);
            baseResourcesSet.put(key, tokens[tokens.length - 1]);
            modifiedFlag = true;
        } else {
            Logger.warn("Base resource already exists in database for " + key);
        }
        return dbid;
    }

    public int addTestCase(String[] fields) {
        int dbid = InvalidDBID;
        boolean status = ConfigSettings.isValid("AGENCIES", fields[0])
                && ConfigSettings.isValid("PROJECTS", fields[1])
                && Util.isValidRound(Util.toInteger(fields[2]))
                && ConfigSettings.isValid("APPLICATIONS", fields[3])
                && ConfigSettings.isValid("SIZES", fields[4])
                && Util.isValidTaskCount(Util.toInteger(fields[5]));

        if (status) {
            Object[] tokens = Util.stringsToRecord(fields, testCaseCIdx, testCasesSignature);
            if (tokens == null) {
                Logger.warn("One or more entry in base resource is invalid " + Util.listToString(fields));
                return dbid;
            }

            String key = Util.genKeyString(fields, 0, 6);
            if (testCasesSet.get(key) == null) {
                dbid = testCaseCIdx++;
                assert (tokens != null);
                testCases.put(tokens[tokens.length - 1], tokens);
                testCasesSet.put(key, tokens[tokens.length - 1]);
                modifiedFlag = true;
            } else {
                Logger.warn("Test case already exists in database for " + key);
            }
        } else {
            Logger.warn("One or more entry in test case is invalid");
        }
        return dbid;
    }

    public boolean addSpecialGroup() {
        Integer specialGroupKey = new Integer(getSimMemoryTimeGroup());
        Object[] tokens = (Object[]) predictionGroups.get(specialGroupKey);
        if (tokens == null) {
            tokens = new Object[3];
            tokens[0] = "SimMemoryTimeGroup";
            tokens[1] = "Prediction runs for receive verification";
            tokens[2] = specialGroupKey;

            predictionGroups.put(tokens[tokens.length - 1], tokens);
        }
        return true;
    }

    public int addPredictionGroup(String[] fields) {
        Object[] tokens = Util.stringsToRecord(fields, predictionGroupCIdx, predictionGroupsSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in prediction group is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        int dbid = predictionGroupCIdx++;
        assert (tokens != null);
        predictionGroups.put(tokens[tokens.length - 1], tokens);
        modifiedFlag = true;
        return dbid;
    }

    public int addActualRuntime(String[] fields) {
        int dbid = InvalidDBID;
        boolean status = (testCases.get(Util.toInteger(fields[0])) != null)
                && (baseResources.get(Util.toInteger(fields[1])) != null);

        if (status) {
            String key = Util.genKeyString(fields, 0, 2);
            dbid = actualRuntimesCIdx++;
            Object[] tokens = Util.stringsToRecord(fields, dbid, actualRuntimesSignature);
            if (tokens == null) {
                Logger.warn("One or more entry in actual runtime is invalid " + Util.listToString(fields));
                return InvalidDBID;
            }
            Integer existing = (Integer) actualRuntimesSet.get(key);
            if (existing != null) {
                Logger.inform("The actual runtimes already exists in database for " + key + " so overwriting");
                actualRuntimes.remove(existing);
                actualRuntimesSet.remove(key);
            }
            assert (tokens != null);
            actualRuntimes.put(tokens[tokens.length - 1], tokens);
            actualRuntimesSet.put(key, tokens[tokens.length - 1]);
            modifiedFlag = true;
        } else {
            Logger.warn("One or more entry in actual runtime is invalid");
        }

        return dbid;
    }

    public int addMemoryBenchmarkData(String[] fields) {
        int dbid = -1;
        Integer levelCount = Util.toInteger(fields[1]);
        boolean status = Util.isValidBWMethod(fields[0])
                && ((levelCount != null)
                && ((levelCount.intValue() > 0) && (levelCount.intValue() <= 3)));
        if (status) {
            String restOfSig = Util.getMemoryProfileSignature((String) fields[0], levelCount);
            if (restOfSig != null) {
                String signature = memoryProfilesSignature + restOfSig;
                Object[] tokens = Util.stringsToRecord(fields, memoryProfileCIdx, signature);
                if (tokens == null) {
                    Logger.warn("One or more entry in memory benchmark data is invalid " + Util.listToString(fields));
                    return InvalidDBID;
                }
                dbid = memoryProfileCIdx++;
                assert (tokens != null);
                memoryProfiles.put(tokens[tokens.length - 1], tokens);
                modifiedFlag = true;
            } else {
                Logger.warn("BW method and/or level count is invalid");
            }
        } else {
            Logger.warn("One or more entry in memory profile is invalid");
        }
        return dbid;
    }

    public int addNetworkBenchmarkData(String[] fields) {
        Object[] tokens = Util.stringsToRecord(fields, networkProfileCIdx, networkProfilesSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in network benchmark data is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        int dbid = networkProfileCIdx++;
        assert (tokens != null);
        networkProfiles.put(tokens[tokens.length - 1], tokens);
        modifiedFlag = true;
        return dbid;
    }

    public int addMachineProfile(String[] fields) {
        int dbid = InvalidDBID;
        boolean status = (baseResources.get(Util.toInteger(fields[0])) != null)
                && (memoryProfiles.get(Util.toInteger(fields[1])) != null)
                && (networkProfiles.get(Util.toInteger(fields[2])) != null);

        Object[] tokens = Util.stringsToRecord(fields, machineProfileCIdx, machineProfilesSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in machine profile is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        if (status) {
            dbid = machineProfileCIdx++;
            assert (tokens != null);
            machineProfiles.put(tokens[tokens.length - 1], tokens);
            modifiedFlag = true;
        } else {
            Logger.warn("One or more entry in machine profile is invalid");
        }
        return dbid;
    }

    public int addDefaultProfile(String[] fields) {
        int dbid = InvalidDBID;
        boolean status = (baseResources.get(Util.toInteger(fields[0])) != null)
                && (machineProfiles.get(Util.toInteger(fields[1])) != null);

        if (status) {
            Integer tmp = Util.toInteger(fields[0]);
            if (tmp == null) {
                Logger.warn("One or more entry in default profile is invalid " + Util.listToString(fields));
                return InvalidDBID;
            }
            dbid = tmp.intValue();
            Object[] tokens = Util.stringsToRecord(fields, dbid, defaultProfilesSignature);
            if (tokens == null) {
                Logger.warn("One or more entry in default profile is invalid " + Util.listToString(fields));
                return InvalidDBID;
            }
            if (defaultProfiles.get(tokens[tokens.length - 1]) != null) {
                Logger.inform("Overwriting the default profile for " + tokens[tokens.length - 1]);
            }
            defaultProfiles.put(tokens[tokens.length - 1], tokens);
            dbid = 0;
            modifiedFlag = true;
        } else {
            Logger.warn("One or more entry in default profiles is invalid");
        }
        return dbid;
    }

    public int addPredictionRun(String[] fields) {
        int dbid = InvalidDBID;
        boolean status = (predictionGroups.get(Util.toInteger(fields[0])) != null)
                && (machineProfiles.get(Util.toInteger(fields[1])) != null)
                && (machineProfiles.get(Util.toInteger(fields[2])) != null)
                && (testCases.get(Util.toInteger(fields[3])) != null);
        if (!status) {
            Logger.warn("Prediction runs has to have valid prediction groups and machine profiles");
            return InvalidDBID;
        }
        dbid = predictionRunsCIdx++;
        Object[] tokens = Util.stringsToRecord(fields, dbid, predictionRunsSignature);
        if (tokens == null) {
            Logger.warn("One or more entry in prediction runs is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        predictionRuns.put(tokens[tokens.length - 1], tokens);
        modifiedFlag = true;

        return dbid;
    }

    public int getTrainedMachineProfile(int zoneWeight, int baseResource){
        assert(false);
        return InvalidDBID;
    }

    public int addZoneWeights(String[] fields) {
        /*
         * HACK HACK HACK Need to implement!
         * */
        return InvalidDBID;
    }

    public int addApplicationZoneWeight(String[] fields) {
        /*
         * HACK HACK HACK Need to implement!
         * */
        return InvalidDBID;
    }

    public int addCacheStructures(int levelCount, String[] fields) {
        /*
         * HACK HACK HACK Need to implement!
         * */
        return InvalidDBID;
    }

    Object[] genEmptyRecord(Integer key, String signature) {
        if ((signature == null) || (signature.length() == 0)) {
            return null;
        }
        Object[] retValue = new Object[signature.length() + 1];
        for (int i = 0; i < signature.length(); i++) {
            Object obj = null;
            char sig = signature.charAt(i);
            switch (sig) {
                case 's':
                    obj = "";
                    break;
                case 'i':
                    obj = new Integer(0);
                    break;
                case 'f':
                    obj = new Float((float) 0.0);
                    break;
                case 'l':
                    obj = new Long(0);
                    break;
                case 'd':
                    obj = new Double(0.0);
                    break;
            }
            assert (obj != null);
            retValue[i] = obj;
        }
        retValue[signature.length()] = key;
        return retValue;
    }

    public TreeMap getTraceStatuses(TestCase tCase) {
        TreeMap retValue = new TreeMap();
        int testDbid = getDbid(tCase);
        if (testDbid == InvalidDBID) {
            Logger.warn(tCase + " is not in the database");
            return null;
        }
        Integer dbidKey = new Integer(testDbid);
        assert (testCases.get(dbidKey) != null);
        Object[] tokens = (Object[]) traceStatuses.get(dbidKey);
        if (tokens == null) {
            return null;
        }

        int recIdx = 1;

        retValue.put((recIdx++) + ".Exec", "" + tokens[1] + ":" + tokens[4]);
        retValue.put((recIdx++) + ".Psins", "" + tokens[6] + ":" + tokens[9]);
        retValue.put((recIdx++) + ".Trf", "" + tokens[11] + ":" + tokens[14]);
        retValue.put((recIdx++) + ".JbbInst", "" + tokens[16] + ":" + tokens[19]);
        retValue.put((recIdx++) + ".JbbColl", "" + tokens[21] + ":" + tokens[24]);

        Object[] fields = new Object[2];
        int phsCnt = ((Integer) tokens[26]).intValue();
        retValue.put((recIdx++) + ".Phase", "" + phsCnt);

        for (int phsNo = 1; phsNo <= phsCnt; phsNo++) {

            fields[0] = new Integer(testDbid);
            fields[1] = new Integer(phsNo);
            String statusKey = Util.genKeyString(fields, 0, 2);

            dbidKey = (Integer) phaseStatusesSet.get(statusKey);
            if (dbidKey == null) {
                continue;
            }
            tokens = (Object[]) phaseStatuses.get(dbidKey);

            retValue.put((recIdx++) + ".SimInst " + phsNo, "" + tokens[2] + ":" + tokens[5]);
            retValue.put((recIdx++) + ".SimColl " + phsNo, "" + tokens[7] + ":" + tokens[10]);
        }

        return retValue;
    }

    public TreeMap getTestCaseUsers(TestCase tCase) {
        TreeMap retValue = new TreeMap();
        int testDbid = getDbid(tCase);
        if (testDbid == InvalidDBID) {
            Logger.warn(tCase + " is not in the database");
            return null;
        }
        Integer dbidKey = new Integer(testDbid);
        assert (testCases.get(dbidKey) != null);
        Object[] tokens = (Object[]) traceStatuses.get(dbidKey);
        if (tokens == null) {
            return null;
        }

        //retValue.put("ExecUser","" + tokens[3]);
        //retValue.put("PsinsUser","" + tokens[8]);
        //retValue.put("TrfUser","" + tokens[13]);
        //retValue.put("JbbInstUser","" + tokens[18]);
        //retValue.put("JbbCollUser","" + tokens[23]);
        retValue.put("NEEDS_CORRECT_TOKEN_INDEX001", "" + "NEEDS_CORRECT_TOKEN_INDEX001");
        retValue.put("NEEDS_CORRECT_TOKEN_INDEX002", "" + "NEEDS_CORRECT_TOKEN_INDEX002");
        retValue.put("NEEDS_CORRECT_TOKEN_INDEX003", "" + "NEEDS_CORRECT_TOKEN_INDEX003");
        retValue.put("NEEDS_CORRECT_TOKEN_INDEX004", "" + "NEEDS_CORRECT_TOKEN_INDEX004");
        retValue.put("NEEDS_CORRECT_TOKEN_INDEX005", "" + "NEEDS_CORRECT_TOKEN_INDEX005");

        Object[] fields = new Object[2];
        int phsCnt = ((Integer) tokens[26]).intValue();

        for (int phsNo = 1; phsNo <= phsCnt; phsNo++) {

            fields[0] = new Integer(testDbid);
            fields[1] = new Integer(phsNo);
            String statusKey = Util.genKeyString(fields, 0, 2);

            dbidKey = (Integer) phaseStatusesSet.get(statusKey);
            if (dbidKey == null) {
                continue;
            }
            tokens = (Object[]) phaseStatuses.get(dbidKey);

            //retValue.put("PhaseUser_" + phsNo,"" + tokens[4]);
            retValue.put("NEEDS_CORRECT_TOKEN_INDEX006_" + phsNo, "" + "NEEDS_CORRECT_TOKEN_INDEX006");
        }

        return retValue;
    }

    public String[] getTraceFilePaths(TestCase tCase, String type) {
        String[] retValue = null;
        int testDbid = getDbid(tCase);
        if (testDbid == InvalidDBID) {
            Logger.warn(tCase + " is not in the database");
            return null;
        }
        Integer dbidKey = new Integer(testDbid);
        assert (testCases.get(dbidKey) != null);

        Object[] tokens = (Object[]) traceStatuses.get(dbidKey);
        if (tokens == null) {
            return null;
        }

        if (type.equals("jbbinst") || type.equals("jbbcoll")) {
            int idx = 0;
            if (type.equals("jbbinst")) {
                idx = 3;
            } else {
                idx = 4;
            }
            idx = idx * 5 + 1 + 3;
            String path = (String) tokens[idx];
            if ((path.length() == 0) || !Util.isFile(path)) {
                return null;
            }
            retValue = new String[1];
            retValue[0] = path;
        } else if (type.equals("siminst") || type.equals("simcoll")) {
            int phsCnt = ((Integer) tokens[26]).intValue();
            if (phsCnt == 0) {
                return null;
            }
            retValue = new String[phsCnt];
            Object[] fields = new Object[2];
            for (int phsNo = 1; phsNo <= phsCnt; phsNo++) {
                fields[0] = new Integer(testDbid);
                fields[1] = new Integer(phsNo);
                String statusKey = Util.genKeyString(fields, 0, 2);
                dbidKey = (Integer) phaseStatusesSet.get(statusKey);
                if (dbidKey == null) {
                    retValue[phsNo - 1] = null;
                    continue;
                }
                tokens = (Object[]) phaseStatuses.get(dbidKey);
                int idx = 0;
                if (type.equals("siminst")) {
                    idx = 0;
                } else {
                    idx = 1;
                }
                idx = idx * 5 + 2 + 3;
                String path = (String) tokens[idx];
                if ((path.length() == 0) || !Util.isFile(path)) {
                    return null;
                }
                retValue[phsNo - 1] = path;
            }
        } else {
            Logger.warn("Invalid trace type " + type);
            return null;
        }

        return retValue;
    }

    public int updateTraceStatus(int testDbid, int phsCnt) {
        int dbid = InvalidDBID;
        if (phsCnt <= 0) {
            Logger.warn("Phase count is invalid in status update " + phsCnt);
            return InvalidDBID;
        }
        Integer dbidKey = new Integer(testDbid);
        if (testCases.get(dbidKey) == null) {
            Logger.warn(testDbid + " in not a valid test case");
            return InvalidDBID;
        }
        Object[] tokens = (Object[]) traceStatuses.get(dbidKey);
        if (tokens == null) {
            tokens = genEmptyRecord(dbidKey, traceStatusesSignature);
            traceStatuses.put(dbidKey, tokens);
        }

        assert ((((Integer) tokens[0]).intValue() == 0) || (((Integer) tokens[0]).intValue() == testDbid));
        assert ((((Integer) tokens[26]).intValue() == 0) || (((Integer) tokens[26]).intValue() == phsCnt));

        tokens[0] = dbidKey;
        modifiedFlag = true;

        tokens[26] = new Integer(phsCnt);
        dbid = testDbid;
        return dbid;
    }

    public int updateTraceStatus(int testDbid, String typ, String date, String mach, String user, String file, double eTime) {

        int dbid = InvalidDBID;
        if ((typ == null) || (date == null) || (mach == null) || (user == null) || (file == null)) {
            Logger.warn("One or more field is not valid fo trace status update");
            return InvalidDBID;
        }
        Integer dbidKey = new Integer(testDbid);
        if (testCases.get(dbidKey) == null) {
            Logger.warn(testDbid + " in not a valid test case");
            return InvalidDBID;
        }
        Object[] tokens = (Object[]) traceStatuses.get(dbidKey);
        if (tokens == null) {
            tokens = genEmptyRecord(dbidKey, traceStatusesSignature);
            traceStatuses.put(dbidKey, tokens);
        }
        tokens[0] = dbidKey;
        modifiedFlag = true;

        int idx = 0;
        if (typ.equals("exec")) {
            idx = 0;
        } else if (typ.equals("psins")) {
            idx = 1;
        } else if (typ.equals("mpidtrace")) {
            idx = 2;
        } else if (typ.equals("jbbinst")) {
            idx = 3;
        } else if (typ.equals("jbbcoll")) {
            idx = 4;
        } else {
            Logger.warn("Invalid type in status update " + typ);
            return InvalidDBID;
        }
        idx *= 5;
        idx += 1;

        tokens[idx++] = date;
        tokens[idx++] = mach;
        tokens[idx++] = user;
        tokens[idx++] = file;
        tokens[idx++] = new Double(eTime);

        dbid = testDbid;
        return dbid;
    }

    public int updatePhaseStatus(int testDbid, String typ, int phsNo,
            String date, String mach, String user, String file, double eTime) {
        int dbid = InvalidDBID;
        if ((phsNo <= 0) || (typ == null) || (date == null) || (mach == null)
                || (user == null) || (file == null)) {
            Logger.warn("One or more field is not valid fo trace status update");
            return InvalidDBID;
        }
        Integer testDbidKey = new Integer(testDbid);
        if (testCases.get(testDbidKey) == null) {
            Logger.warn(testDbidKey + " in not a valid test case");
            return InvalidDBID;
        }

        Object[] fields = new Object[2];
        fields[0] = new Integer(testDbid);
        fields[1] = new Integer(phsNo);
        String statusKey = Util.genKeyString(fields, 0, 2);

        Object[] tokens = null;
        Integer dbidKey = (Integer) phaseStatusesSet.get(statusKey);
        if (dbidKey == null) {
            dbid = phaseStatusesCIdx++;
            dbidKey = new Integer(dbid);
            tokens = genEmptyRecord(dbidKey, phaseStatusesSignature);
            phaseStatuses.put(dbidKey, tokens);
            phaseStatusesSet.put(statusKey, dbidKey);
        } else {
            tokens = (Object[]) phaseStatuses.get(dbidKey);
        }
        assert (tokens != null);

        assert ((((Integer) tokens[0]).intValue() == 0) || (((Integer) tokens[0]).intValue() == testDbid));
        assert ((((Integer) tokens[1]).intValue() == 0) || (((Integer) tokens[1]).intValue() == phsNo));

        tokens[0] = testDbidKey;
        tokens[1] = new Integer(phsNo);
        modifiedFlag = true;

        int idx = 0;
        if (typ.equals("siminst")) {
            idx = 0;
        } else if (typ.equals("simcoll")) {
            idx = 1;
        } else {
            Logger.warn("Invalid type for status update " + typ);
            return InvalidDBID;
        }
        idx *= 5;
        idx += 2;

        tokens[idx++] = date;
        tokens[idx++] = mach;
        tokens[idx++] = user;
        tokens[idx++] = file;
        tokens[idx++] = new Double(eTime);

        dbid = dbidKey.intValue();
        return dbid;
    }

    static boolean listRecords(HashMap records, String signature, String info,
            String header, int tokensCnt, boolean usedbid) {
        int size = records.size();

        Logger.inform("Listing " + size + " records for " + info);
        Logger.inform("================================================================");
        if (usedbid) {
            header = "dbid," + header;
        }
        Logger.inform(header);

        Iterator it = records.keySet().iterator();

        LinkedList list = new LinkedList();
        while (it.hasNext()) {
            Integer key = (Integer) it.next();
            list.add(key);
        }
        Collections.sort(list);
        it = list.iterator();

        while (it.hasNext()) {
            Object key = it.next();
            Object[] tokens = (Object[]) records.get(key);
            assert (tokens != null);

            String line = "";
            for (int i = 0; i < (tokens.length - 1); i++) {
                line += (" " + tokens[i] + " |");
            }
            if (usedbid) {
                line = tokens[tokens.length - 1] + " |" + line;
            }
            Logger.inform(line);
        }
        return true;
    }

    public boolean listTestCase(String header, int tokenCnt) {
        return listRecords(testCases, testCasesSignature, "test cases", header, tokenCnt, true);
    }

    public boolean listBaseResource(String header, int tokenCnt) {
        return listRecords(baseResources, baseResourcesSignature, "base resources", header, tokenCnt, true);
    }

    public boolean listPredictionGroup(String header, int tokenCnt) {
        return listRecords(predictionGroups, baseResourcesSignature, "base resources", header, tokenCnt, true);
    }

    public boolean listActualRuntime(String header, int tokenCnt) {
        return listRecords(actualRuntimes, actualRuntimesSignature, "actual runtimes", header, tokenCnt, false);
    }

    public boolean listMemoryBenchmarkData(String header, int tokenCnt) {
        return listRecords(memoryProfiles, memoryProfilesSignature, "memory profiles", header, tokenCnt, true);
    }

    public boolean listNetworkBenchmarkData(String header, int tokenCnt) {
        return listRecords(networkProfiles, networkProfilesSignature, "network profiles", header, tokenCnt, true);
    }

    public boolean listMachineProfile(String header, int tokenCnt) {
        return listRecords(machineProfiles, machineProfilesSignature, "machine profiles", header, tokenCnt, true);
    }

    public boolean listDefaultProfile(String header, int tokenCnt) {
        return listRecords(defaultProfiles, defaultProfilesSignature, "default profiles", header, tokenCnt, false);
    }

    public boolean listPredictionRun(String header, int tokenCnt) {
        return listRecords(predictionRuns, predictionRunsSignature, "prediction runs", header, tokenCnt, false);
    }

    public boolean listPredictionResult(String header, int tokenCnt, int group) {
        Logger.error("The listing of prediction runs in a pretty way is not implemented yet\n");
        return true;
    }

    public boolean listTraceStatus(String header, int tokenCnt) {
        return listRecords(traceStatuses, traceStatusesSignature, "trace statuses", header, tokenCnt, false);
    }

    public boolean listPhaseStatus(String header, int tokenCnt) {
        return listRecords(phaseStatuses, phaseStatusesSignature, "phase statuses", header, tokenCnt, false);
    }

    public int getDbid(String resourceName) {
        int dbid = InvalidDBID;
        Integer dbidKey = (Integer) baseResourcesSet.get(Util.makeKey(resourceName));
        if (dbidKey == null) {
            return InvalidDBID;
        }
        dbid = dbidKey.intValue();
        return dbid;
    }

    public int getDbid(TestCase testCase) {
        int retValue = InvalidDBID;
        if (testCase != null) {
            String key = Util.genKeyString(testCase.toStringFields(), 0, 6);
            Integer keyObj = (Integer) testCasesSet.get(key);
            Object[] tokens = null;
            if (keyObj != null) {
                tokens = (Object[]) testCases.get(keyObj);
            }
            if (tokens != null) {
                retValue = ((Integer) tokens[tokens.length - 1]).intValue();
            }
        }
        return retValue;
    }

    public boolean existsTestCase(int testCaseId) {
        return (testCases.get(new Integer(testCaseId)) != null);
    }

    public boolean existsTestCase(TestCase testCase) {
        boolean retValue = false;
        if (testCase != null) {
            String key = Util.genKeyString(testCase.toStringFields(), 0, 6);
            retValue = (testCasesSet.get(key) != null);
        }
        return retValue;
    }

    public boolean existsPredGroup(int group) {
        return (predictionGroups.get(new Integer(group)) != null);
    }

    public boolean existsBaseResource(int machIdx) {
        return (baseResources.get(new Integer(machIdx)) != null);
    }

    public boolean existsProfile(int profIdx) {
        return (machineProfiles.get(new Integer(profIdx)) != null);
    }

    public boolean existsMemoryBenchmarkData(int zoneWeightIdx, int baseResource) {
        /*
         * HACK HACK HACK Need to implement!
         * */
        return false;
    }

    public boolean existsApplicationZoneWeight(int cacheSysId, int testCaseDataIdx, int zoneWeightIdx) {
        /*
         * HACK HACK HACK Need to implement!
         * */
        return false;
    }

    public int existsZoneWeights(double[] weights, double epsilon) {
        /*
         * HACK HACK HACK Need to implement!
         * */
        return InvalidDBID;
    }

    public int existsCacheStructures(int cacheLevels, String[] fields) {
        /*
         * HACK HACK HACK Need to implement!
         * */
        return InvalidDBID;
    }
//LAURA START
    public int existsCacheStructureL1Line(int cacheStructure){
        /*
         * HACK HACK HACK Need to implement!
         * */
        return InvalidDBID;
    }
    public int existsCacheStructureL2Line(int cacheStructure){
        /*
         * HACK HACK HACK Need to implement!
         * */
        return InvalidDBID;
    }
    public int existsCacheStructureL3Line(int cacheStructure){
        /*
         * HACK HACK HACK Need to implement!
         * */
        return InvalidDBID;
    }
//LAURA END

    public int getDefaultPIdx(int machIdx) {
        int retValue = InvalidDBID;
        Object[] tokens = (Object[]) defaultProfiles.get(new Integer(machIdx));
        if (tokens != null) {
            retValue = ((Integer) tokens[1]).intValue();
            assert (retValue >= 0);
        }
        return retValue;
    }

    public int getCacheSysId(int profIdx) {
        int retValue = InvalidDBID;
        Object[] tokens = (Object[]) machineProfiles.get(new Integer(profIdx));
        if (tokens != null) {
            retValue = ((Integer) tokens[3]).intValue();
            assert (retValue >= 0);
        }
        return retValue;
    }

    public double getSimMemoryTime(TestCase testCase, int basePIdx) {

        double retValue = -1.0;

        int testDbid = getDbid(testCase);
        int baseResIdx = getBaseResource(basePIdx);

        assert ((testDbid != InvalidDBID) && (baseResIdx != InvalidDBID));

        int maxDbid = -1;

        Iterator it = predictionRuns.keySet().iterator();

        while (it.hasNext()) {

            Integer dbidKey = (Integer) it.next();
            Object[] tokens = (Object[]) predictionRuns.get(dbidKey);
            int currGrpIdx = ((Integer) tokens[0]).intValue();

            if (currGrpIdx != getSimMemoryTimeGroup()) {
                continue;
            }

            int currTestId = ((Integer) tokens[3]).intValue();
            int currResIdx = getBaseResource(((Integer) tokens[2]).intValue());

            if ((testDbid == currTestId) && (baseResIdx == currResIdx)) {
                if (dbidKey.intValue() > maxDbid) {
                    retValue = ((Double) tokens[7]).doubleValue();
                    maxDbid = dbidKey.intValue();
                }
            }
        }

        return retValue;
    }

    public int getMemoryPIdx(int profIdx) {
        Object[] tokens = getMachineProfile(profIdx);
        if (tokens == null) {
            return InvalidDBID;
        }
        return ((Integer) tokens[1]).intValue();
    }

    public int getNetworkPIdx(int profIdx) {
        Object[] tokens = getMachineProfile(profIdx);
        if (tokens == null) {
            return InvalidDBID;
        }
        return ((Integer) tokens[2]).intValue();
    }

    public Object[] getMachineProfile(int profIdx) {
        return Util.duplicate((Object[]) machineProfiles.get(new Integer(profIdx)));
    }

    public Object[] getMemoryProfile(int mprofIdx) {
        return Util.duplicate((Object[]) memoryProfiles.get(new Integer(mprofIdx)));
    }

    public Object[] getNetworkProfile(int nprofIdx) {
        return Util.duplicate((Object[]) networkProfiles.get(new Integer(nprofIdx)));
    }

    public int getBaseResource(int profIdx) {
        Object[] tokens = getMachineProfile(profIdx);
        if (tokens == null) {
            return InvalidDBID;
        }
        return ((Integer) tokens[0]).intValue();
    }

    public double getBaseResourceFLOPS(int machIdx) {
        if (InvalidDBID == machIdx) {
            return -1.0;
        }
        Object[] tokens = (Object[]) baseResources.get(new Integer(machIdx));
        if (tokens == null) {
            return -1.0;
        }
        Double freq = (Double) tokens[5];
        Integer simultaneous = (Integer) tokens[6];
        return (freq.doubleValue() * simultaneous.intValue());
    }
    public int getBaseResourceCoresPerNode(int machIdx) {
        if (InvalidDBID == machIdx) {
            return -1;
        }
        Object[] tokens = (Object[]) baseResources.get(new Integer(machIdx));
        if (tokens == null) {
            return -1;
        }
        return -1; //LAURA JUNK
    }

    public String getMachineLabel(int profIdx) {
        Object[] tokens = getMachineProfile(profIdx);
        if (tokens == null) {
            return null;
        }
        return ((String) tokens[4]);
    }

    public String getBaseResourceName(int profIdx) {
        Object[] tokens = getMachineProfile(profIdx);
        if (tokens == null) {
            return null;
        }
        //return (String)tokens[2];
        return (String) "NEEDS_CORRECT_TOKEN_INDEX007";
    }

    public String getBaseResourceString(int machIdx) {
        String retVal = null;
        Object[] tokens = (Object[]) baseResources.get(new Integer(machIdx));
        if (tokens != null) {
            retVal = "";
            for (int i = 0; i < tokens.length - 2; i++) {
                if (i == 0) {
                    retVal += tokens[i];
                } else {
                    retVal += ("_" + tokens[i]);
                }
            }
        }
        return retVal;
    }

    public double getActualRuntime(TestCase tCase, int profIdx) {

        double retValue = -1.0;

        int testDbid = getDbid(tCase);
        int baseResIdx = getBaseResource(profIdx);

        assert ((testDbid != InvalidDBID) && (baseResIdx != InvalidDBID));

        String[] fields = new String[2];
        fields[0] = String.valueOf(testDbid);
        fields[1] = String.valueOf(baseResIdx);
        String key = Util.genKeyString(fields, 0, 2);

        Integer dbidKey = (Integer) actualRuntimesSet.get(key);
        if (dbidKey != null) {
            Object[] tokens = (Object[]) actualRuntimes.get(dbidKey);
            retValue = ((Double) tokens[2]).doubleValue();
        } else {
            Logger.warn("Actual runtime for " + key + " does not exist");
        }

        return retValue;
    }

    public String[] getGUIResourceEntries() {
        return null;
    }

    public String[] getGUIPredGroupEntries() {
        return null;
    }

    public String[] getGUITestCases() {
        return null;
    }

    public boolean listCacheStructures() {
        /*HACK need to implement*/
        return false;
    }
}
