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

import java.io.*;
import java.util.*;


class Psins extends Simulation {
    Psins(String exec,String model,String config,String outfile,Object[] profile,
          TestCase tcase,double r,String ddir,String dir,int pr){
        super(exec,model,config,outfile,profile,tcase,r,ddir,dir,pr);
    }
    boolean generateConfigFile(){
        Double netBw   = (Double)networkProfile[0];
        Double netLat  = (Double)networkProfile[1];
        Double nodeBw  = (Double)networkProfile[2];
        Double nodeLat = (Double)networkProfile[3];
        Integer coresPerNode = (Integer)networkProfile[4];
        Integer busCount = (Integer)networkProfile[5];
        Integer inpLinkCount = (Integer)networkProfile[6];
        Integer outLinkCount = (Integer)networkProfile[7];
	Integer profile = new Integer(targetProfile);

        try {
            BufferedWriter outputFile = new BufferedWriter(new FileWriter(configFile));
            outputFile.write("#machine_profile " + profile + "\n\n");
            outputFile.write("#machine\tnodes\tnetwork_bw\tnetwork_lt\tbuses\n");
            outputFile.write("Machine\t" + (-1*coresPerNode.intValue()) + "\t" +
                              Util.multMillion(netBw.doubleValue()) + "\t" + netLat + "\t" + busCount + "\n\n\n");
            outputFile.write("#node\tid\tcores\tinp\tout\tlocal_bw\tlocal_lt\tcpu_ratio\n");
            outputFile.write("Node\t-1\t" + coresPerNode + "\t" + inpLinkCount + "\t" + outLinkCount + "\t" +
                              Util.multMillion(nodeBw.doubleValue()) + "\t" + nodeLat + "\t" + ratio + "\n\n\n");
            outputFile.write("#map\ttask_id\tnode_id\n");
            outputFile.write("Map\t-1\t\t0\n\n\n");
            outputFile.write("#io\tio_bw\tio_lat\tblock_size\tmax_simul\n");
            outputFile.write("IO\t0.0\t0.0\t8192\t0\n\n\n");
            outputFile.write("#collective\ttype\tMultiplier(lin(linear),con(constant),log(logarithmic),pen(penalty,value))\n");
            outputFile.write("#Collective\tAllgather\tlin\n");
            outputFile.write("#Collective Allgather\tpen\t0.00774 49.31400 0.68300 4.63500\n");
            outputFile.close();
        } catch (Exception e){
            Logger.warn("Can not construct the config file " + configFile + "\n" + e);
            return false;
        }
        return true;
    }
    boolean run(){
        if(!Util.isFile(configFile)){
            Logger.warn("Configuration file " + configFile + " does not exist");
            return false;
        }
        String suffix = "";
        if(traceFileSuffix != null){
            suffix = ("_" + traceFileSuffix);
        }
        String traceFile = directDir + "/" + testCase.ftpName() + suffix + ".psins";
        if(!Util.isFile(traceFile)){
            Logger.warn("Trace file " + traceFile + " does not exist");
            return false;
        }

        try {
            String command = executableFile + " --trace_typ psins --version 1" +
                             " --comm_model " + commModel + " --config_file " + configFile  + 
                             " --application " + testCase.getApplication() + 
                             " --dataset " + testCase.getDataset() + " --task_count " + testCase.getCpu() + 
                             " --trace_path " + traceFile + " --dir " + outputDir;

            Logger.debug("Running <=== " + command);
            Logger.debug("Output <=== " + outputFile);

            LinkedList resultLines = LinuxCommand.execute(command,outputFile);
            if(resultLines == null){
                Logger.warn("Simulation failed for during execution, check " + outputFile);
                return false;
            }
        } catch (Exception e){
            Logger.warn("Simulation failed, check the output " + outputFile + "\n" + e);
            return false;
        }

        try {

            BufferedReader inputFile = new BufferedReader(new FileReader(outputFile));
            boolean success = false;
            String line = null;
            while ((line = inputFile.readLine()) != null) {
                line = Util.cleanWhiteSpace(line,false);
                if(line.matches(".*SUCCESS.*SUCCESS.*SUCCESS.*")){
                    Logger.debug(line);
                    success = true;
                } else if(line.matches("^TotalPredictionTime.*")){
                    Logger.debug(line);
                    line = line.replaceAll(".*TotalPredictionTime\\s+","");
                    Double t = Util.toDouble(line);
                    if(t == null){
                        Logger.warn("Overall time in " + outputFile + " does not exist");
                        return false;
                    }
                    overallTime = t.doubleValue();
                } else if(line.matches("^TaskTimes.*")){
                    Logger.debug(line);
                    line = line.replaceAll("^TaskTimes\\s+","");
                    String[] tokens = line.split("\\s+");
                    assert (tokens.length == 3);
                    Object[] fields = Util.stringsToRecord(tokens,"idd");
                    if(fields == null){
                        Logger.warn("Per task time at line " + line + " is invalid in " + outputFile);
                        return false;
                    }
                    computationTimes[((Integer)fields[0]).intValue()] = ((Double)fields[1]).doubleValue();
                    communicationTimes[((Integer)fields[0]).intValue()] = ((Double)fields[2]).doubleValue();
                } else if(line.matches("^ETime\\s+.*")){
                    Logger.inform(line);
                    line = line.replaceAll(".*ETime\\s+","");
                    line = line.replaceAll("\\(.*","");
                    String[] tokens = line.split("\\s+");
                    assert (tokens.length == 2);
                    Object[] fields = Util.stringsToRecord(tokens,"sd");
                    mpiEventTimes.put((String)fields[0],(Double)fields[1]);
                }
            }

            inputFile.close();

            if(!success || !areTimesValid()){
                Logger.warn("Lines for times needed in " + outputFile + " do not exist");
                return false;
            }

        } catch (Exception e){
            Logger.warn("Can not read simulation output " + outputFile + "\n" + e);
            return false;
        }

        assert areTimesValid();

        return true;
    }
}
