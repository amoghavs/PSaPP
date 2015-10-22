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

//### field 0 blockid
//### field 1 fpcount
//### field 2 memcount
//### field 3 l1hit
//### field 4 l2hit
//### field 5 l3hit

public class BWMethodStretchedExp extends BWMethod {

    BWMethodStretchedExp() { super(); }

    public BWMethodStretchedExp(int lvl) { super(lvl); }

    //MITESH added for graph500
    public double[] calculateArrayOfBW(Object[] blockFields,Object[] memoryProfile,Object[] userData){
        double[] BW = new double[2];
        return BW;
    }

    public double calculateBW(Object[] blockFields,Object[] memoryProfile,Object[] userData){

        assert ((levelCount + 3) == blockFields.length);

        boolean allZero = true;
        double[] hits = new double[levelCount];
        for(int i=0;i<levelCount;i++){
            hits[i] = ((Double)blockFields[3+i]).doubleValue();
            allZero = allZero && (hits[i] == 0.0);
        }

        Long blockId = (Long)blockFields[0];

        if(allZero){
            Logger.warn("Block " + blockId + " has all 0 cache hits with exec freq of " + blockFields[2]);
            for(int i=0;i<levelCount;i++){
                hits[i] = 3.0;
            }
            //return 0.0;
        }

        double[] bws = new double[levelCount];
        double[] taus = new double[levelCount];
        double[] betas = new double[levelCount];

        for(int i=0;i<levelCount;i++){
            bws[i] = Util.multMillion(((Double)memoryProfile[2+i]).doubleValue());
            taus[i] = ((Double)memoryProfile[2+levelCount+i]).doubleValue();
            betas[i] = ((Double)memoryProfile[2+2*levelCount+i]).doubleValue();
            if(taus[i] == 0.0){
                taus[i] = 0.000001;
            }
            assert (taus[i] != 0.0);
        }
        
        double predictedBW = 0.0;
        double[] ltimes = new double[levelCount];
        for(int i=0;i<levelCount;i++){
            ltimes[i] = bws[i]*Math.exp(-1.0*Math.pow((100.0-hits[i])/taus[i],betas[i])); 
            predictedBW += ltimes[i];
        }

        assert (predictedBW > 0.0);
        //Logger.debug("LAURA here ");

        if((userData != null) && (userData.length == 1)){
            double factor = ((Double)userData[0]).doubleValue();
            predictedBW *= factor;
        }
        return predictedBW;
    }
}

