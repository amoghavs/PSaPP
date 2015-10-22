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

import java.util.*;
import PSaPP.util.*;

public abstract class Database {

    public static final int InvalidDBID = -1;
    static final String testCasesSignature = "ssissi";
    static final String baseResourcesSignature = "sssiidis";
    static final String predictionGroupsSignature = "ss";
    static final String actualRuntimesSignature = "iid";
    static final String memoryProfilesSignature = "isi";
    static final String networkProfilesSignature = "ddddiiii";
    static final String machineProfilesSignature = "iiiis";
    static final String defaultProfilesSignature = "ii";
    static final String predictionRunsSignature = "iiiiddddssssss";
    static final String traceStatusesSignature = "issssdssssdssssdssssdssssdi";
    static final String phaseStatusesSignature = "iissssdssssd";
    static final String zoneWeightsSignature = "ddddddddddddddddddddds";
    static final String applicationZoneWeightSignature = "iii";

    public abstract int getSimMemoryTimeGroup();
    public abstract int getSimReportGroup();

    public abstract boolean initialize();

    public abstract boolean commit();

    public abstract boolean existsTestCase(int testCaseId);

    public abstract boolean existsTestCase(TestCase testCase);

    public abstract boolean existsPredGroup(int group);

    public abstract boolean existsBaseResource(int machIdx);

    public abstract boolean existsProfile(int profIdx);

    public abstract int getSysidFromBaseResource(int baseResource);
//LAURA
    public abstract int existsCacheStructureL1Line(int cacheStructure);
    public abstract int existsCacheStructureL2Line(int cacheStructure);
    public abstract int existsCacheStructureL3Line(int cacheStructure);
//LAURA

    public abstract String[] getEmailsFromTestCase(TestCase tc);
 
    public abstract int getTrainedMachineProfile(int zoneWeightIdx, int baseResource);
    public abstract boolean existsMemoryBenchmarkData(int zoneWeightIdx, int baseResource);

    public abstract boolean existsApplicationZoneWeight(int cacheSysId, int testCaseDataIdx, int zoneWeightIdx);

    public abstract int existsZoneWeights(double[] weights, double epsilon);

    public abstract int existsCacheStructures(int cacheLevels, String[] fields);

    public abstract int getDbid(TestCase testCase);

    public abstract int getDbid(String resourceName);

    public abstract int getDefaultPIdx(int machIdx);

    public abstract int getCacheSysId(int profIdx);

    public abstract double getSimMemoryTime(TestCase testCase, int basePIdx);

    public abstract int getMemoryPIdx(int profIdx);

    public abstract int getNetworkPIdx(int profIdx);

    public abstract int getBaseResource(int profIdx);

    public abstract double getBaseResourceFLOPS(int machIdx);


    public abstract int getBaseResourceCoresPerNode(int machIdx); //LAURA

    public abstract String getMachineLabel(int profIdx);

    public abstract String getBaseResourceName(int profIdx);

    public abstract Object[] getMachineProfile(int profIdx);

    public abstract Object[] getMemoryProfile(int mprofIdx);

    public abstract Object[] getNetworkProfile(int nprofIdx);

    public abstract String getBaseResourceString(int machIdx);

    public abstract double getActualRuntime(TestCase tCase, int profIdx);

    public abstract int addTestCase(String[] fields);

    public abstract int addBaseResource(String[] fields);

    public abstract int addPredictionGroup(String[] fields);

    public abstract int addActualRuntime(String[] fields);

    public abstract int addDefaultProfile(String[] fields);

    public abstract int addMemoryBenchmarkData(String[] fields);

    public abstract int addNetworkBenchmarkData(String[] fields);

    public abstract int addMachineProfile(String[] fields);

    public abstract int addPredictionRun(String[] fields);

    public abstract int addZoneWeights(String[] fields);

    public abstract int addApplicationZoneWeight(String[] fields);

    public abstract int addCacheStructures(int levelCount, String[] fields);

    public abstract int updateTraceStatus(int testDbid, String typ, String date, String mach, String user, String file, double eTime);

    public abstract int updateTraceStatus(int testDbid, int phsCnt);

    public abstract int updatePhaseStatus(int testDbid, String typ, int phsNo, String date, String mach, String user, String file, double eTime);

    public abstract String[] getTraceFilePaths(TestCase tCase, String type);

    public abstract TreeMap getTraceStatuses(TestCase tCase);

    public abstract TreeMap getTestCaseUsers(TestCase tCase);

    public abstract boolean listTestCase(String header, int tokenCnt);

    public abstract boolean listBaseResource(String header, int tokenCnt);

    public abstract boolean listActualRuntime(String header, int tokenCnt);

    public abstract boolean listPredictionGroup(String header, int tokenCnt);

    public abstract boolean listDefaultProfile(String header, int tokenCnt);

    public abstract boolean listMemoryBenchmarkData(String header, int tokenCnt);

    public abstract boolean listNetworkBenchmarkData(String header, int tokenCnt);

    public abstract boolean listMachineProfile(String header, int tokenCnt);

    public abstract boolean listPredictionRun(String header, int tokenCnt);

    public abstract boolean listTraceStatus(String header, int tokenCnt);

    public abstract boolean listPhaseStatus(String header, int tokenCnt);

    public abstract boolean listPredictionResult(String header, int tokenCnt, int group);

    public abstract boolean listCacheStructures();

    public String getCacheStructuresSignature(int cacheLevels, boolean includeCommment) {
        if (cacheLevels == 1) {
            if (includeCommment) {
                return "iiiss";
            } else {
                return "iiis";
            }
        } else if (cacheLevels == 2) {
            if (includeCommment) {
                return "iiiiiisss";
            } else {
                return "iiiiiiss";
            }
        } else if (cacheLevels == 3) {
            if (includeCommment) {
                return "iiiiiiiiissss";
            } else {
                return "iiiiiiiiisss";
            }
        } else {
            Logger.error("Invalid cacheLevels");
        }
        return null;
    }

    public int addMachWMemoryProfile(String[] fields) {
        Integer baseR = Util.toInteger(fields[0]);
        if (!existsBaseResource(baseR.intValue())) {
            Logger.warn("base resource " + baseR + " in machine profile is invalid " + Util.listToString(fields));
            return InvalidDBID;
        }
        int deafultPIdx = getDefaultPIdx(baseR.intValue());
        if (deafultPIdx == InvalidDBID) {
            Logger.warn("base resource " + baseR + " does not have default profile to extract network");
            return InvalidDBID;
        }
        int networkPIdx = getNetworkPIdx(deafultPIdx);
        assert (networkPIdx != InvalidDBID);

        Object[] memoryPTokens = Util.duplicate(fields, 2);
//        assert (memoryPTokens != null);
        String[] memoryPFields = new String[memoryPTokens.length];
        for (int i = 0; i < memoryPFields.length; i++) {
            memoryPFields[i] = (String) memoryPTokens[i];
        }
        int memoryPIdx = addMemoryBenchmarkData(memoryPFields);
        if (memoryPIdx == InvalidDBID) {
            Logger.warn("machine profile has invalid memory benchmark data " + Util.listToString(fields));
            return InvalidDBID;
        }
        String[] machinePFields = new String[machineProfilesSignature.length()];
        machinePFields[0] = fields[0];
        machinePFields[1] = String.valueOf(memoryPIdx);
        machinePFields[2] = String.valueOf(networkPIdx);
        machinePFields[3] = fields[1];
        machinePFields[4] = fields[fields.length - 1];

        int dbid = addMachineProfile(machinePFields);
        if (dbid == InvalidDBID) {
            Logger.warn("Can not insert the machine profile " + Util.listToString(fields));
            return InvalidDBID;
        }
        return dbid;
    }

    public int addMachineProfile(int bs, int mp, int np, int ci, String lb) {
        String[] fields = new String[5];
        fields[0] = String.valueOf(bs);
        fields[1] = String.valueOf(mp);
        fields[2] = String.valueOf(np);
        fields[3] = String.valueOf(ci);
        fields[4] = lb;
        return addMachineProfile(fields);
    }

    public int addTestCase(TestCase testCase) {
        String[] fields = new String[6];
        fields[0] = testCase.getAgency();
        fields[1] = testCase.getProject();
        fields[2] = testCase.getRound() + "";
        fields[3] = testCase.getApplication();
        fields[4] = testCase.getDataset();
        fields[5] = testCase.getCpu() + "";
        return addTestCase(fields);
    }

    public static String[] generateRunRecord(int grpIdx, int basePId,
            int proIdx, int testIdx,
            double compT, double ratio,
            double predT, double repsT,
            String simType, String simMod,
            String ratioMeth, boolean useSimmem,
            String comment, Date date) {
        String[] predictionRunFields = new String[14];

        predictionRunFields[0] = String.valueOf(grpIdx);
        predictionRunFields[1] = String.valueOf(basePId);
        predictionRunFields[2] = String.valueOf(proIdx);
        predictionRunFields[3] = String.valueOf(testIdx);

        predictionRunFields[4] = Format.format4d(compT);
        predictionRunFields[5] = Format.format4d(ratio);

        predictionRunFields[6] = Format.format4d(predT);
        predictionRunFields[7] = Format.format4d(repsT);

        predictionRunFields[8] = simType;

        if (simMod != null) {
            predictionRunFields[9] = simMod;
        } else {
            predictionRunFields[9] = "";
        }
        predictionRunFields[10] = ratioMeth;
        if (useSimmem) {
            predictionRunFields[11] = "true";
        } else {
            predictionRunFields[11] = "false";
        }

        predictionRunFields[12] = comment;
        predictionRunFields[13] = Util.dateToString(date);

        return predictionRunFields;
    }

    public abstract String[] getGUIResourceEntries();

    public abstract String[] getGUIPredGroupEntries();

    public abstract String[] getGUITestCases();
}
