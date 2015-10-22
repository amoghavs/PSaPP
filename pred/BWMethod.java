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

abstract class BWMethod {
    int levelCount;

    BWMethod() { levelCount = 0; }

    BWMethod(int lvl) { levelCount = lvl; }

    //MITESH added for graph500
    double[] calculateArrayOfBW(Object[] blockFields,Object[] memoryProfile){
        return calculateArrayOfBW(blockFields,memoryProfile,null);
    }

    double calculateBW(Object[] blockFields,Object[] memoryProfile){
        return calculateBW(blockFields,memoryProfile,null);
    }
    double calculateBWWithDfp(Object[] blockFields,Object[] memoryProfile,Object[] userData,Long range){

        assert (range != null);

        double origValue = calculateBW(blockFields,memoryProfile,userData);
        double newValue = 0.0;

        long rangeVal = range.longValue();
        if(rangeVal >= 3145728){
            newValue = 48.0e9;
        } else if(rangeVal > 196608){
            newValue = (1.0e-5*rangeVal+12.6)*1.0e9;
        } else {
            newValue = origValue;
        }
       
        if(newValue != 0.0){
            Logger.debug("Orig " + origValue + " new " + newValue + " for " + blockFields[0]);
        }

        return newValue;

    }
    abstract double calculateBW(Object[] blockFields,Object[] memoryProfile,Object[] userData);
    //MITESH added for graph500
    public double[] calculateArrayOfBW(Object[] blockFields,Object[] memoryProfile,Object[] userData){
        double[] BW = new double[2];
        return BW;
    }

    void setLevel(int lvl) { levelCount = lvl; }
    int  getLevel() { return levelCount; }
}
