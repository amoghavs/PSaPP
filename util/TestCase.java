package PSaPP.util;
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
import java.io.*;

public class TestCase {

    String  agency;
    String  project;
    Integer round;
    String  application;
    String  dataset;
    Integer taskCount;

    public boolean verify(){
        if((agency == null) || (project == null) || (round == null) ||
           (application == null) || (dataset == null) || (taskCount == null)){
            Logger.warn("Required argument(s) for test case specification is/are missing");
            Logger.warn("It needs agency,project,round,application,dataset,taskCount");
            return false;
        }

        if(!Util.isValidRound(round)){
            Logger.warn("Round needs to be > 0, and it is " + round);
            return false;
        }
        if(!ConfigSettings.isValid("APPLICATIONS", application)){
            Logger.warn("Invalid application " + application);
            return false;
        }
        if(!ConfigSettings.isValid("SIZES", dataset)){
            Logger.warn("Invalid dataset " + dataset);
            return false;
        }
        if(!ConfigSettings.isValid("PROJECTS", project)){
            Logger.warn("Invalid project " + project);
            return false;
        }
        if(!ConfigSettings.isValid("AGENCIES", agency)){
            Logger.warn("Invalid funding_agency " + agency);
            return false;
        }
        if(!Util.isValidTaskCount(taskCount)){
            Logger.warn("Cpu count needs to be > 0, it is " + taskCount);
            return false;
        }

        return true;
    }

    public TestCase(String app,String siz,Integer cpu,String agy,String prj,Integer rnd){
        agency = agy;
        project = prj;
        round = rnd;
        application = app;
        dataset = siz;
        taskCount = cpu;
        
        if(!verify()){
            System.exit(-1);
        }
    }

    public TestCase(String spec){
        assert (spec != null);
        String[] tokens = spec.split("_");
        assert (tokens.length == 6);
        
        tokens[2] = tokens[2].replaceAll("round","");
        tokens[5] = tokens[5].replaceAll("^0+","");

        agency = tokens[0];
        project = tokens[1];
        round = Util.toInteger(tokens[2]);
        application = tokens[3];
        dataset = tokens[4];
        taskCount = Util.toInteger(tokens[5]);

        assert (round != null) && (taskCount != null);
    }

    public String getAgency()       { return agency; }
    public String getProject()      { return project; }
    public int getRound()           { return round.intValue(); }
    public String getApplication()  { return application; }
    public String getDataset()      { return dataset; }
    public int getCpu()             { return taskCount.intValue(); }

    public String toString(){
        return (agency + "_" + project + "_round" + round + "." + application + "_" + dataset + "_" + taskCount); 
    }

    public String ftpName(){
        return (project + "_round" + round + "_" + application + "_" + dataset + "_" + Format.cpuToString(taskCount));
    }
    public String shortName(){
        return (application + "_" + dataset + "_" + Format.cpuToString(taskCount));
    }

    public String[] toStringFields(){
        String[] retValue = new String[6];
        retValue[0] = agency;
        retValue[1] = project;
        retValue[2] = String.valueOf(round.intValue());
        retValue[3] = application;
        retValue[4] = dataset;
        retValue[5] = String.valueOf(taskCount.intValue());
        return retValue;
    }
}
