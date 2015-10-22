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

class BWMethodCyclesDrop extends BWMethod {

    BWMethodCyclesDrop() { super(); }

    BWMethodCyclesDrop(int lvl) { super(lvl); }

    double calculateBW(Object[] blockFields,Object[] memoryProfile,Object[] userData){

        assert ((levelCount + 3) == blockFields.length);

        boolean allZero = true;

        double[] hits = new double[levelCount+1];
        for(int i=0;i<=levelCount;i++){
            hits[i] = 100.0;
            if(i < levelCount){
                hits[i] = ((Double)blockFields[3+i]).doubleValue();
                allZero = allZero && (hits[i] == 0.0);
            }
        }

        Long blockId = (Long)blockFields[0];

        if(allZero){
            Logger.warn("Block " + blockId + " has all 0 cache hits");
            return 0.0;
        }

        double factor = 0.0;
        double remain = 1.0;
        for(int i=0;i<=levelCount;i++){ 
            double hitRate = hits[i];
            if(i > 0){
                if(hits[i-1] < 100.0){
                    hitRate = 100.0*(hits[i]-hits[i-1])/(100.0-hits[i-1]);
                } else {
                    hitRate = 0.0;
                }
            }
            double tmpBw = (remain * (hitRate/100.0));
            remain = (100-hits[i])/100.0;
            if(i > 0){
                factor += tmpBw*((Double)memoryProfile[2+i]).doubleValue();
            } else {
                factor += tmpBw;
            }
        }
        double predictedBW = (1.0/factor)*Util.multMillion(((Double)memoryProfile[2]).doubleValue());

        assert (predictedBW > 0.0);

        if((userData != null) && (userData.length == 1)){
            factor = ((Double)userData[0]).doubleValue();
            predictedBW *= factor;
            Logger.debug(blockId + " -- " + factor);
        }

        return predictedBW;
    }
}
