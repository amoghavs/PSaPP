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

public class BWMethodFpgaGraph500 extends BWMethodStretchedExp {

    BWMethodFpgaGraph500() { super(); }

    public BWMethodFpgaGraph500(int lvl) { super(lvl); }

    public double calculateBW(Object[] blockFields,Object[] memoryProfile,Object[] userData){

        //TEST something then call :
        double predictedBW = 0.0;
        //if((userData != null) && (userData.length == 1)){
        //    double factor = ((Double)userData[0]).doubleValue();
        //    predictedBW *= factor;
       // }
        //return predictedBW;

        //PREDICT TIME graph500 on FPGA
        //LAURA assert (range != null);
        double rangeVal=0.0; //LAURA NEED TO SET THIS VALUE
        if((userData != null) && (userData.length == 2)){
            rangeVal = ((Double)userData[0]).doubleValue();
        }

        double newValue = 0.0;
             

        //LAURA long rangeVal = range.longValue();
	if(rangeVal >= 192 && rangeVal < 8352)
        {
            newValue = (2.0e-5*Math.pow(rangeVal,1.0212))*1.0e9;
        	Logger.debug("MITESH IN FPGA Range 1 : bytes = " + rangeVal + " BW =  " + newValue );
	}
        else if(rangeVal >= 8352 && rangeVal < 91568)
	{
	    newValue = (9.0e-5*Math.pow(rangeVal, 0.851))*1.0e9;
        	Logger.debug("MITESH IN FPGA Range 2 : bytes = " + rangeVal + " BW =  " + newValue );
	}
	else if(rangeVal >= 91568 && rangeVal < 883568)
	{
	    newValue = (0.0029*Math.pow(rangeVal,0.5607))*1.0e9;
        	Logger.debug("MITESH IN FPGA Range 3 : bytes = " + rangeVal + " BW =  " + newValue );
	}
	else if(rangeVal >= 883568 && rangeVal < 32727024)
	{
	    newValue = (0.0688*Math.pow(rangeVal,0.3285))*1.0e9;
        	Logger.debug("MITESH IN FPGA Range 4 : bytes = " + rangeVal + " BW =  " + newValue );
	}
	else if(rangeVal >= 32727024)
	{
	    newValue = (3.2575*Math.pow(rangeVal, 0.1147))*1.0e9;
        	Logger.debug("MITESH IN FPGA Range 5 : bytes = " + rangeVal + " BW =  " + newValue );
	}
        else 
        {
            Logger.warn("LAURA calling original value size too small \n");
            double origValue = super.calculateBW(blockFields,memoryProfile,userData);
            newValue = origValue;
        }
       
        if(newValue != 0.0){
             //Logger.debug("Orig " + origValue + " new " + newValue + " for " + blockFields[0]);
        }

        return newValue;


        //if((userData != null) && (userData.length == 1)){
        //    double factor = ((Double)userData[0]).doubleValue();
        //    predictedBW *= factor;
       // }
        //return predictedBW;
    }
}

