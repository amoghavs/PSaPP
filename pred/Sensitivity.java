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


import java.util.*;
import PSaPP.util.*;
import PSaPP.dbase.*;

public class Sensitivity {
    public static String[] validSensCases = { "L1Lat","L2Lat","L3Lat","MMLat","L1Bw","L2Bw","L3Bw","MMBw", 
                                       "NetBw","NetLat","NodeBw","NodeLat" };
    Database dataBase;
    String sensitivityString;
    Object[] sensitivityCases;

    void generateSensitivityCases(){
        if(sensitivityString == null){
            return;
        }
        sensitivityString = Util.cleanWhiteSpace(sensitivityString,false);
        String[] tokens = sensitivityString.split(",");
        if(tokens == null){
            Logger.warn("There are no sensitiviy cases at " + sensitivityString);
            return;
        }
        LinkedList tupleList = new LinkedList();
        for(int i=0;i<tokens.length;i++){
            String[] caseAndRatio = tokens[i].split("(X|x)");
            if(caseAndRatio.length != 2){
                Logger.warn("Sensitivity case [" + tokens[i] + "] is invalid. case and factor is needed");
                return;
            }
            String senscase = Util.cleanWhiteSpace(caseAndRatio[0]);
            Double ratio = Util.toDouble(Util.cleanWhiteSpace(caseAndRatio[1]));
            if(ratio == null){
                Logger.warn("Sensitivity case [" + tokens[i] + "] is invalid as factor is not double");
                return;
            }
            boolean isValid = false;
            Integer caseIdx = null;
            for(int j=0;j<validSensCases.length;j++){
                if(senscase.equals(validSensCases[j])){
                    isValid = true;
                    caseIdx = new Integer(j);
                }
            }
            if(!isValid){
                Logger.warn("Sensitivity case [" + senscase + "] is invalid as the case string is invalid");
                Logger.warn("Possible values are " + Util.listToString(validSensCases));
                return;
            }
            assert (caseIdx != null) && (ratio != null);

            tupleList.add(caseIdx);
            tupleList.add(ratio);
        }
        if(tupleList.size() > 0){
            sensitivityCases = tupleList.toArray();
        }
    }
    
    public Sensitivity(String str){
        dataBase = null;
        sensitivityString = str;
        sensitivityCases = null;
        generateSensitivityCases();
    }

    public boolean hasValidCases(){
        return ((sensitivityString == null) || (sensitivityCases != null));
    }

    public static String sensTypeToLabel(int typ){
        assert ((typ <= 11) && (typ >= 0));
        return validSensCases[typ];
    }
    public static boolean isNetworkSensitivity(int typ){
        return ((typ >= 8) && (typ <= 11));
    }
    public static boolean isMemorySensitivity(int typ){
        return ((typ <= 7) && (typ >= 0));
    }
    public static boolean isLatencySensitivity(int typ){
        return (((typ >= 0) && (typ <= 3)) || (typ == 9) || (typ == 11));
    }
    public static boolean isL3Sensitivity(int typ){
        return ((typ == 2) || (typ == 6));
    }
    public static boolean isMainMemorySensitivity(int typ){
        return ((typ == 3) || (typ == 7));
    }

    String[] updateNetworkBenchmark(Object[] inpfields,int sensTypeIdx,double factor){

        int updateIdx = sensTypeIdx - 8;
        assert (updateIdx >= 0);

        Object[] fields = Util.duplicate(inpfields);
        Double oldValue = (Double)fields[updateIdx];
        Double newValue = new Double(oldValue.doubleValue() * factor);
        fields[updateIdx] = newValue;

        int lenWoDbid = fields.length-1;
        String[] retValue = new String[lenWoDbid];
        for(int i=0;i<lenWoDbid;i++){
            retValue[i] = fields[i].toString();
        }


        return retValue;
    }
    String[] updateMemoryBenchExppenDrop(Object[] inpfields,int sensTypeIdx,double factor){

        int[] sensTypeTo3LevelProfIdx = { 0, 1, 2, 3, 4, 5, 6, 7 };
        int[] sensTypeTo2LevelProfIdx = { 0, 1,-1, 2, 3, 4,-1, 5 };

        assert(isMemorySensitivity(sensTypeIdx));

        int incDirection = -1;
        if(factor < 1.0){
            incDirection = 1;
        }
        if(isLatencySensitivity(sensTypeIdx)){
            incDirection = -1 * incDirection;
        }
        Integer levelCount = ((Integer)inpfields[1]);
        assert((levelCount.intValue() == 3) || !isL3Sensitivity(sensTypeIdx));

        int startIdx = sensTypeIdx;
        int endIdx = sensTypeIdx;

        if(isLatencySensitivity(sensTypeIdx)){
            if(incDirection == 1){
                endIdx = 4;
            } else {
                endIdx = -1;
            }
        } else {
            if(incDirection == 1){
                endIdx = 8;
            } else {
                endIdx = 3;
            }
        }

        Object[] fields = Util.duplicate(inpfields);
        for(;startIdx!=endIdx;startIdx+=incDirection){
            int updateIdx = -1;
            if(levelCount.intValue() == 2){
                updateIdx = sensTypeTo2LevelProfIdx[startIdx];
            } else if(levelCount.intValue() == 3){
                updateIdx = sensTypeTo3LevelProfIdx[startIdx];
            } else {
                assert(false);
            }
            if(updateIdx == -1){
                continue;
            }
            updateIdx += 2;
            Double oldValue = (Double)fields[updateIdx];
            Double newValue = new Double(oldValue.doubleValue() * factor);
            fields[updateIdx] = newValue;
        }

        int lenWoDbid = fields.length-1;
        String[] retValue = new String[lenWoDbid];
        for(int i=0;i<lenWoDbid;i++){
            retValue[i] = fields[i].toString();
        }
        retValue[lenWoDbid-1] += " " + sensTypeToLabel(sensTypeIdx) + " x " + factor;
        return retValue;
    }

    double calculateOneLevel(double hit,double bw,double tau,double beta){
        return bw*Math.exp(-1.0*Math.pow(((100.0-hit)/tau),beta));
    }

    String[] updateMemoryBenchStretchedExp(Object[] inpfields,int sensTypeIdx,double factor){

        double[][] markerHits = {{ 100.0, 100.0, 100.0 }, 
                                 {   0.0, 100.0, 100.0 }, 
                                 {   0.0,   0.0, 100.0 }, 
                                 {   0.0,   0.0,   0.0 }};

        assert(isMemorySensitivity(sensTypeIdx));
        assert(!isLatencySensitivity(sensTypeIdx));

        int levelCount = ((Integer)inpfields[1]).intValue();
        assert((levelCount == 3) || !isL3Sensitivity(sensTypeIdx));

        double[] bws = new double[levelCount];
        double[] taus = new double[levelCount];
        double[] betas = new double[levelCount];

        for(int i=0;i<levelCount;i++){
            bws[i] = ((Double)inpfields[2+i]).doubleValue();
            taus[i] = ((Double)inpfields[2+levelCount+i]).doubleValue();
            betas[i] = ((Double)inpfields[2+2*levelCount+i]).doubleValue();
            if(taus[i] == 0.0){
                taus[i] = 0.000001;
            }
            assert (taus[i] != 0.0);
        }

        Object[] fields = Util.duplicate(inpfields);

        if(isMainMemorySensitivity(sensTypeIdx)){
            for(int j=0;j<levelCount;j++){
                double expFactor = 0.0;
                double maxfactor =  Math.exp(Math.pow(100.0/taus[j],betas[j]));
                assert (maxfactor >= 0);

                if(factor >= maxfactor){
                    Logger.warn("Even though factor is " + factor + ", it does not make valid sens study for " +
                                (j+1) + "th level. The max factor can be  < " + maxfactor);
                    expFactor =  Math.pow(1.0-(Math.log(maxfactor-0.1)/Math.pow(100.0/taus[j],betas[j])),1.0/betas[j]);
                } else {
                    expFactor =  Math.pow(1.0-(Math.log(factor)/Math.pow(100.0/taus[j],betas[j])),1.0/betas[j]);
                }
                assert (expFactor > 0.0);

                int updateIdx = 2+levelCount+j;
                Double oldValue = (Double)fields[updateIdx];
                Double newValue = new Double(oldValue.doubleValue() / expFactor);
                fields[updateIdx] = newValue;
            }
        } else {
            double[] lbws = new double[levelCount];
            int updateLevel = sensTypeIdx - 4;
            double sum = 0.0;
            for(int j=0;j<levelCount;j++){
                lbws[j] = calculateOneLevel(markerHits[updateLevel][j],
                                            bws[j],taus[j],betas[j]);
                sum += lbws[j];
            }
            double bwFactor = ((sum * factor) - (sum - lbws[updateLevel])) / lbws[updateLevel];
            double expFactor = Math.pow((Math.log(bwFactor)/Math.pow(100.0/taus[updateLevel],betas[updateLevel]))+1.0, 1.0/betas[updateLevel]);

            int updateIdx = updateLevel;
            updateIdx += 2;
            Double oldValue = (Double)fields[updateIdx];
            Double newValue = new Double(oldValue.doubleValue() * bwFactor);
            fields[updateIdx] = newValue;

            updateIdx = updateLevel + levelCount;
            updateIdx += 2;
            oldValue = (Double)fields[updateIdx];
            newValue = new Double(oldValue.doubleValue() / expFactor);
            fields[updateIdx] = newValue;
        }

        printEachMarker(inpfields,markerHits,levelCount,"orig");
        printEachMarker(fields,markerHits,levelCount,sensTypeToLabel(sensTypeIdx));

        int lenWoDbid = fields.length-1;
        String[] retValue = new String[lenWoDbid];
        for(int i=0;i<lenWoDbid;i++){
            retValue[i] = fields[i].toString();
        }
        retValue[lenWoDbid-1] += " " + sensTypeToLabel(sensTypeIdx) + " x " + factor;
        return retValue;
    }

    int updateAndInsertProfile(boolean isMemory,Object[] inpfields,int sensTypeIdx,double factor){

        int retValue = Database.InvalidDBID;
        int fieldIdx = -1;

        Logger.debug("\nSENSITIVITY " + sensTypeToLabel(sensTypeIdx) + " X " + factor);
        Logger.debug("\tINP " + Util.listToString(inpfields));

        String[] newFields = null;
        if(!isMemory){
            newFields = updateNetworkBenchmark(inpfields,sensTypeIdx,factor);
            retValue = dataBase.addNetworkBenchmarkData(newFields);
        } else { 
            String bwmeth = (String)inpfields[0];
            assert (bwmeth != null);
            if(bwmeth.equals("BWexppenDrop")){
                newFields = updateMemoryBenchExppenDrop(inpfields,sensTypeIdx,factor);
            } else if(bwmeth.equals("BWstretchedExp")){
                newFields = updateMemoryBenchStretchedExp(inpfields,sensTypeIdx,factor);
            } else {
                assert(false);
            }
            retValue = dataBase.addMemoryBenchmarkData(newFields);
        }
        Logger.debug("\tOUT " + Util.listToString(newFields) + "\n");

        return retValue;
    }

    public boolean addProfiles(List profileSet,boolean isNetworkOnly,Database dbase){
        dataBase = dbase;
        assert (dataBase != null);
        boolean status = true;

        if(sensitivityCases == null){
            return false;
        }
        int[] profs = new int[profileSet.size()];
        int idx = 0;
        for(Iterator it = profileSet.iterator();it.hasNext();){
            profs[idx++] = ((Integer)it.next()).intValue();
        }

        for(int i=0;i<profs.length;i++){
            int profIdx = profs[i];
            if(!dataBase.existsProfile(profIdx)){
                Logger.warn("Profile " + profIdx + " does not exist, no sensitivity for it");
                continue;
            }

            int mprofIdx = dataBase.getMemoryPIdx(profIdx);
            int nprofIdx = dataBase.getNetworkPIdx(profIdx);
            assert (mprofIdx != Database.InvalidDBID) && (nprofIdx != Database.InvalidDBID);
            Object[] memoryProfile = dataBase.getMemoryProfile(mprofIdx);
            Object[] networkProfile = dataBase.getNetworkProfile(nprofIdx);
            assert (memoryProfile != null) && (networkProfile != null);

            String bwmeth = (String)memoryProfile[0];
            assert (bwmeth != null);
            if(!bwmeth.equals("BWexppenDrop") && !bwmeth.equals("BWstretchedExp")){
                Logger.warn("The method of sensitivity is not implemented for " + bwmeth);
                continue;
            }

            for(int j=0;j<sensitivityCases.length;){
                Integer sensType = (Integer)sensitivityCases[j++];
                Double sensFact = (Double)sensitivityCases[j++];
                String casestr = sensTypeToLabel(sensType.intValue()) + " X " + sensFact;

                if(isMemorySensitivity(sensType.intValue())){
                    if(isNetworkOnly){
                        Logger.warn("Only network sensitivity cases are valid for this run. Not " + casestr);
                        continue;
                    } 
                    if(bwmeth.equals("BWstretchedExp") && isLatencySensitivity(sensType.intValue())){
                        Logger.warn(casestr + " sensitivity is not valid for " + bwmeth + " BW calculation method");
                        continue;
                    }
                    Integer levelCount = (Integer)memoryProfile[1];
                    assert (levelCount != null);
                    if((levelCount.intValue() == 2) && isL3Sensitivity(sensType.intValue())){
                        Logger.warn(casestr + " sensitivity is not valid for 2 level system");
                        continue;
                    }
                    if(bwmeth.equals("BWstretchedExp") && (sensFact.doubleValue() < 1.0)){
                        Logger.warn(casestr + " sensitivity is not valid for BWstretchedExp with under 1.0 multiplication factor");
                        continue;
                    }
                }

                int sysIdx = dataBase.getBaseResource(profIdx);
                int cachesysid = dataBase.getCacheSysId(profIdx);
                String label = dataBase.getMachineLabel(profIdx);

                Logger.debug("Adding new profile from " + profIdx + " using " + casestr);

                int newMProfIdx = mprofIdx;
                int newNProfIdx = nprofIdx;

                if(isMemorySensitivity(sensType.intValue())){
                    newMProfIdx = updateAndInsertProfile(true,memoryProfile,sensType.intValue(),sensFact.doubleValue());
                    Logger.inform("memory profile for " + profIdx + " for " + casestr + " is " + newMProfIdx + " based " + mprofIdx);
                } else if(isNetworkSensitivity(sensType.intValue())){
                    newNProfIdx = updateAndInsertProfile(false,networkProfile,sensType.intValue(),sensFact.doubleValue());
                    Logger.inform("network profile for " + profIdx + " for " + casestr + " is " + newNProfIdx + " based " + nprofIdx);
                }

                assert ((newMProfIdx != Database.InvalidDBID) && (newNProfIdx != Database.InvalidDBID));

                if((newMProfIdx == mprofIdx) && (newNProfIdx == nprofIdx)){
                    Logger.warn("No additional profiles for sensitivity study");
                } else {
                    int dbid = dataBase.addMachineProfile(sysIdx, newMProfIdx,newNProfIdx,cachesysid, label + "(" + casestr + ")");

                    if(dbid != Database.InvalidDBID){
                        Logger.inform("Profile " + dbid + " for " + casestr + " of " + profIdx + " used for predictions");
                        profileSet.add(new Integer(dbid));
                    } else {
                        Logger.warn("Sensitivity case profile " + casestr + " did not succeed");
                        status = false;
                    }
                }
            }
        }
        return status;
    }

    void printEachMarker(Object[] inpfields,double[][] markerHits,int levelCount,String caseStr){
        String tmpStr = caseStr + "\n";
        for(int i=0;i<(levelCount+1);i++){
            for(int j=0;j<levelCount;j++){
                tmpStr += Format.formatNMd(3,2,markerHits[i][j]) + " ";
            }
            tmpStr += " === ";
            double sum = 0.0;
            double[] lbws = new double[levelCount];
            for(int j=0;j<levelCount;j++){
                lbws[j] = calculateOneLevel(markerHits[i][j],
                                            ((Double)inpfields[2+j]).doubleValue(),
                                            ((Double)inpfields[2+levelCount+j]).doubleValue(),
                                            ((Double)inpfields[2+2*levelCount+j]).doubleValue());
                sum += lbws[j];
                tmpStr += Format.formatNMd(6,2,lbws[j]) + " ";
            }
            tmpStr += Format.formatNMd(6,2,sum) + "\n";
        }
        Logger.debug(tmpStr);
    }

}
