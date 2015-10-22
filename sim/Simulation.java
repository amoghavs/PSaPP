package PSaPP.sim;
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
import PSaPP.pred.*;

import java.util.*;

abstract class Simulation {

    String executableFile;
    String configFile;
    String outputFile;
    Object[] networkProfile;
    TestCase testCase;
    double ratio;
    String commModel;
    String directDir;
    String outputDir;
    int targetProfile;

    double overallTime;
    double[] computationTimes;
    double[] communicationTimes;

    HashMap mpiEventTimes;

    String traceFileSuffix;

    Simulation(String exec,String model,String config,String outfile,Object[] profile,
               TestCase tcase,double r,String ddir,String dir,int pr){

        executableFile = exec;
        configFile = config;
        outputFile = outfile;
        networkProfile = profile;
        testCase = tcase;
        ratio = r;
        commModel = model;
        directDir = ddir;
	outputDir = dir;
	targetProfile = pr;
        overallTime = -1.0;
        computationTimes = new double[testCase.getCpu()];
        communicationTimes = new double[testCase.getCpu()];
        for(int i=0;i<testCase.getCpu();i++){
            computationTimes[i] = -1.0;
            communicationTimes[i] = -1.0;
        }
        mpiEventTimes = new HashMap();
        traceFileSuffix = null;
    }

    abstract boolean generateConfigFile();
    abstract boolean run();

    double getPredictedTime() { 
        return overallTime; 
    }
    double getTotalCompTime() { 
        assert areTimesValid();
        double sum = 0.0;
        for(int i=0;i<testCase.getCpu();i++){
            sum += computationTimes[i];
        }
        return sum;
    }
    double getTotalCommTime() { 
        assert areTimesValid();
        double sum = 0.0;
        for(int i=0;i<testCase.getCpu();i++){
            sum += communicationTimes[i];
        }
        return sum;
    }
    double getRepresentCompTime(String rMeth){
        assert areTimesValid();
        double retValue = -1.0;
        if(rMeth.equals("max")){
            for(int i=0;i<testCase.getCpu();i++){
                if(computationTimes[i] > retValue){
                    retValue = computationTimes[i];
                }
            }
        } else if(rMeth.equals("avg")){
            retValue = getTotalCompTime() / testCase.getCpu();
        }

        return retValue;
    }
    boolean areTimesValid() { 
        boolean retValue = true;
        retValue = retValue && (overallTime > 0.0);
        for(int i=0;i<testCase.getCpu();i++){
            retValue = retValue && (computationTimes[i] > 0.0);
            retValue = retValue && (communicationTimes[i] >= 0.0);
        }
        return retValue;
    }

    double[] getCompTimes() { return computationTimes; }
    double[] getCommTimes() { return communicationTimes; }

    HashMap getMpiEventTimes() { return mpiEventTimes; }

    public void setTraceFileSuffix(String suffix){
        assert (suffix != null);
        traceFileSuffix = suffix;
    }
}
