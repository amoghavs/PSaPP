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

class Dimemas extends Simulation {

    static final String dimF4String = "Policy: FIFO\n" + 
            "0 LIN MAX  LIN MAX 1.0\n" + 
            "1 0 MIN LOG MAX 1.0\n" + 
            "2 LOG MEAN 0 MAX 1.0\n" + 
            "3 LOG MEAN  0 MAX 1.0\n" + 
            "4 LOG MEAN 0 MAX 1.0\n" + 
            "5 LOG MEAN  0 MAX 1.0\n" + 
            "6 LOG MEAN  LOG MEAN 1.0\n" + 
            "7 LOG MEAN  LOG MEAN 1.0\n" + 
            "8 LOG MEAN  LOG MAX 1.0\n" + 
            "9 LOG MEAN  LOG MAX 1.0\n" + 
            "10 LOG 2MAX  0 MAX 1.0\n" + 
            "11 LOG 2MAX  LOG MAX 1.0\n" + 
            "12 LOG 2MAX  LOG MIN 1.0\n" + 
            "13 LOG MAX  LOG MAX 1.0";

    Dimemas(String exec,String model,String config,String outfile,Object[] profile,
            TestCase tcase,double r,String ddir,String dir,int pr){
        super(exec,model,config,outfile,profile,tcase,r,ddir,dir,pr);
    }

    String generateDimF4(String prefix){
        String dimF4File = prefix + ".dim.F4";
        try {
            BufferedWriter outputFile = new BufferedWriter(new FileWriter(dimF4File));
            outputFile.write(dimF4String);
            outputFile.close();
        } catch (Exception e){
            Logger.warn("Can not write dimF4 file " + dimF4File + "\n" + e);
            return null;
        }
        return dimF4File;
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
        
        int taskCount = testCase.getCpu();
        int nodeCount = taskCount / coresPerNode.intValue();
        if((taskCount % coresPerNode.intValue()) != 0){
            nodeCount++;
        }

        String dimF4File = generateDimF4(configFile);
        if(dimF4File == null){
            Logger.warn("Can not write dimf4 file for " + configFile);
            return false;
        }
        try {
            BufferedWriter outputFile = new BufferedWriter(new FileWriter(configFile));
            outputFile.write(commentHeaderString);
            outputFile.write("\"environment information\" { \"\", " + nodeCount + ", " + 
                             Format.format8d(netBw.doubleValue()) + ", " + busCount + ", " + 
                             "3};;\n");
            for(int i=0;i<nodeCount;i++){
                outputFile.write("\"node information\" { " + i + ", " + "\"\", " + 
                                 coresPerNode + ", " + inpLinkCount + ", " + outLinkCount + ", " +
                                 Format.format8d(netLat) + ", " + 
                                 Format.format8d(nodeLat) + ", " + ratio + ", " + 
                                 Format.format8d(nodeBw) + "};;\n");
            }
            String suffix = "";
            if(traceFileSuffix != null){
                suffix = ("_" + traceFileSuffix);
            }
            outputFile.write("\"mapping information\" { \"" + (directDir + "/" + testCase.ftpName() + suffix + ".trf") + "\", " +
                             taskCount + ", [" + taskCount + "] {\n");
            for(int i=0;i<taskCount;i++){
                outputFile.write("\t" + (i / coresPerNode.intValue()));
                if(i != (taskCount-1)){
                    outputFile.write(",\n");
                } else {
                    outputFile.write("\t} };;\n");
                }
            }
            outputFile.write("\"modules information\" { 129, 0.661813};;\n");
            outputFile.write("\"modules information\" { 130, 0.661813};;\n");
            outputFile.write("\"modules information\" { 131, 0.661813};;\n");
            
            outputFile.write("\"file system parameters\" { 0.000000, 0.000000, 8.000000, 0, 1.000000};;\n");

            outputFile.write("\"configuration files\" { \"\", \"\",\"" + 
                             dimF4File + "\",\"\"};;\n");
            outputFile.close();
        } catch (Exception e){
            Logger.warn("Can not generate the config file " + configFile + "\n" + e);
            return false;
        }
        return true;
    }
    boolean run(){
        if(!Util.isFile(configFile)){
            Logger.warn("The configuration file " + configFile + " does not exist");
            return false;
        }
        String suffix = "";
        if(traceFileSuffix != null){
            suffix = ("_" + traceFileSuffix);
        }
        String traceFile = directDir + "/" + testCase.ftpName() + suffix + ".trf";
        if(!Util.isFile(traceFile)){
            Logger.warn("Trace file " + traceFile + " does not exist");
            return false;
        }
        try {
            String command = executableFile + " -l " + configFile;

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
                if(line.matches("^Execution time:\\s+.*")){
                    Logger.debug(line);
                    success = true;
                    line  = line.replaceAll("^Execution time:\\s+","");
                    Double t = Util.toDouble(line);
                    if(t == null){
                        Logger.warn("No overall time is found in " + outputFile);
                        return false;
                    }
                    overallTime = t.doubleValue();
                } else if(line.matches("^\\d+\\s+\\d+\\.\\d+\\s+\\d+\\.\\d+\\s+\\d+\\.\\d+")){
                    Logger.debug(line);
                    String[] tokens = line.split("\\s+");
                    assert (tokens.length == 4);
                    Object[] fields = Util.stringsToRecord(tokens,"iddd");
                    if(fields == null){
                        Logger.warn("Per task time at line " + line + " is invalid at " + outputFile);
                        return false;
                    }
                    computationTimes[((Integer)fields[0]).intValue()-1] = ((Double)fields[1]).doubleValue();
                    communicationTimes[((Integer)fields[0]).intValue()-1] = ((Double)fields[3]).doubleValue();
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

    static final String commentHeaderString = "SDDFA\n" +
	    "    /*\n" +
	    "    * \"Dimemas Configuration Format:\" \"Version 2.5\"\n" +
	    "    * \"Last update\"  \"03/31/04 at 10:01\"\n" +
	    "    */ ;;\n" +
	    "\n" +
	    "    #1:\n" +
	    "    \"environment information\" {\n" +
	    "        // \"instrumented_architecture\" \"Architecture used to instrument\"\n" +
	    "        char    \"instrumented_architecture\"[];\n" +
	    "        // \"number_of_nodes\" \"Number of nodes on virtual machine\"\n" +
	    "        int     \"number_of_nodes\";\n" +
	    "        // \"network_bandwidth\" \"Data tranfer rate between nodes in Mbytes/s\"\n" +
	    "        // \"0 means instantaneous communication\"\n" +
	    "        double  \"network_bandwidth\";\n" +
	    "        // \"number_of_buses_on_network\" \"Maximun number of messages on network\"\n" +
	    "        // \"0 means no limit\"\n" +
	    "        // \"1 means bus contention\"\n" +
	    "        int     \"number_of_buses_on_network\";\n" +
	    "        // \"1 Constant, 2 Lineal, 3 Logarithmic\"\n" +
	    "        int     \"communication_group_model\";\n" +
	    "    };;\n" +
	    "\n" +
	    "\n" +
	    "    #2:\n" +
	    "    \"node information\" {\n" +
	    "        // \"node_id\" \"Node number\"\n" +
	    "        int     \"node_id\";\n" +
	    "        // \"simulated_architecture\" \"Architecture node name\"\n" +
	    "        char    \"simulated_architecture\"[];\n" +
	    "        // \"number_of_processors\" \"Number of processors within node\"\n" +
	    "        int     \"number_of_processors\";\n" +
	    "        // \"number_of_input_links\" \"Number of input links in node\"\n" +
	    "        int     \"number_of_input_links\";\n" +
	    "        // \"number_of_output_links\" \"Number of output links in node\"\n" +
	    "        int     \"number_of_output_links\";\n" +
	    "        // \"startup_on_local_communication\" \"Communication startup\"\n" +
	    "        double  \"startup_on_local_communication\";\n" +
	    "        // \"startup_on_remote_communication\" \"Communication startup\"\n" +
	    "        double  \"startup_on_remote_communication\";\n" +
	    "        // \"speed_ratio_instrumented_vs_simulated\" \"Relative processor speed\"\n" +
	    "        double  \"speed_ratio_instrumented_vs_simulated\";\n" +
	    "        // \"memory_bandwidth\" \"Data tranfer rate into node in Mbytes/s\"\n" +
	    "        // \"0 means instantaneous communication\"\n" +
	    "        double  \"memory_bandwidth\";\n" +
	    "    };;\n" +
	    "\n" +
	    "\n" +
	    "    #3:\n" +
	    "    \"mapping information\" {\n" +
	    "        // \"tracefile\" \"Tracefile name of application\"\n" +
	    "        char    \"tracefile\"[];\n" +
	    "        // \"number_of_tasks\" \"Number of tasks in application\"\n" +
	    "        int     \"number_of_tasks\";\n" +
	    "        // \"mapping_tasks_to_nodes\" \"List of nodes in application\"\n" +
	    "        int     \"mapping_tasks_to_nodes\"[];\n" +
	    "    };;\n" +
	    "\n" +
	    "\n" +
	    "    #4:\n" +
	    "    \"configuration files\" {\n" +
	    "        char       \"scheduler\"[];\n" +
	    "        char       \"file_system\"[];\n" +
	    "        char       \"communication\"[];\n" +
	    "        char       \"sensitivity\"[];\n" +
	    "    };;\n" +
	    "\n" +
	    "\n" +
	    "    #5:\n" +
	    "    \"modules information\" {\n" +
	    "        // Module identificator number\n" +
	    "        int     \"identificator\";\n" +
	    "        // Speed ratio for this module, 0 means instantaneous execution\n" +
	    "        double  \"execution_ratio\";\n" +
	    "    };;\n" +
	    "\n" +
	    "\n" +
	    "    #6:\n" +
	    "    \"file system parameters\" {\n" +
	    "        double     \"disk latency\";\n" +
	    "        double     \"disk bandwidth\";\n" +
	    "        double     \"block size\";\n" +
	    "        int        \"concurrent requests\";\n" +
	    "        double     \"hit ratio\";\n" +
	    "    };;\n" +
	    "\n";
}
