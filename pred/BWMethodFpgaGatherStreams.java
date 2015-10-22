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

public class BWMethodFpgaGatherStreams extends BWMethodStretchedExp {

    BWMethodFpgaGatherStreams() { super(); }

    public BWMethodFpgaGatherStreams(int lvl) { super(lvl); }

    public double calculateBW(Object[] blockFields,Object[] memoryProfile,Object[] userData){
        double newValue = 0.0;

            Logger.warn("MITESH: Warning attempting to use GatherStreams to calculate single BW instead of multiple BW \n");
            Logger.warn("MITESH: calling original BW Method \n");
            double origValue = super.calculateBW(blockFields,memoryProfile,userData);
            newValue = origValue;
	    return newValue;	
    }
    public double[] calculateArrayOfBW(Object[] blockFields,Object[] memoryProfile,Object[] userData){

        //TEST something then call :
        double predictedBW = 0.0;
        //if((userData != null) && (userData.length == 1)){
        //    double factor = ((Double)userData[0]).doubleValue();
        //    predictedBW *= factor;
       // }
        //return predictedBW;

        //PREDICT TIME GS on FPGA
        //LAURA assert (range != null);
        double rangeVal_gather=0.0; //LAURA NEED TO SET THIS VALUE
        double rangeVal_stream=0.0; //LAURA NEED TO SET THIS VALUE
        if((userData != null) && (userData.length == 2)){
            rangeVal_gather = (((Double)userData[0]).doubleValue())/2;
            rangeVal_stream = (((Double)userData[0]).doubleValue())/2;
        }

        Logger.warn("MITESH: Uing GatherStreams to calculate multiple BWs  \n");
        double newValue_gather = 0.0;
        double newValue_stream = 0.0;

        //LAURA long rangeVal = range.longValue();
        if(rangeVal_gather >= 3145728){
            newValue_gather = 48.0e9;
        } else if(rangeVal_gather > 196608){
            newValue_gather = (1.0e-5*rangeVal_gather+12.6)*1.0e9;
        } else {
	    Double[] userData_gather = new Double[1];
	    userData_gather[0] = rangeVal_gather; 
            Logger.warn("LAURA calling original value size too small \n");
            double origValue_gather = super.calculateBW(blockFields,memoryProfile,userData_gather);
            newValue_gather = origValue_gather;
        }

        if( (rangeVal_stream >= 262144) && ( rangeVal_stream<= 4194304) ){
            newValue_stream = (13.59*Math.log(rangeVal_stream) - 159.2)*1.0e9;
        } else if ( rangeVal_stream > 4194304){
            newValue_stream = 53.0*1.0e9;
        } else {
	    Double[] userData_stream = new Double[1];
	    userData_stream[0] = rangeVal_stream; 
            Logger.warn("LAURA calling original value size too small \n");
            double origValue_stream = super.calculateBW(blockFields,memoryProfile,userData_stream);
            newValue_stream = origValue_stream;
        }
 
        if(newValue_gather != 0.0){
             Logger.debug("BW Gather " + newValue_gather + " for " + blockFields[0]);
        }
        if(newValue_stream != 0.0){
             Logger.debug("BW stream " + newValue_stream + " for " + blockFields[0]);
        }

	double BW[] = new double[2];
        BW[0] = newValue_gather;
        BW[1] = newValue_stream;
        return BW;


    }
}

