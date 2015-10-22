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

import java.util.*;
import java.io.*;

import PSaPP.util.*;
import PSaPP.pred.*;

/**
 * This class parses a .task file and uses the data contained
 * within to populate a TaskData object.
 * @author bsheely
 */
public class TaskParser {

    public TaskParser() {}
    
    /**
     * Parse the specified file and populate the data object
     * @param file The .task file to be parsed
     * @param data The object to be populated with the parse results
     * @return boolean True if successful
     */
    public boolean parse(String file, TaskData data) throws Exception {
        try {
            FileInputStream fstream = new FileInputStream(file);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) {
                String tokens[] = line.split("\t");
                if (Util.isNumeric(tokens[0].trim())) {
                    data.compTimes.add(new Double(Double.valueOf(tokens[1].trim()).doubleValue()));
                } else if (tokens[0].contentEquals("avg")) {
                    data.avg = Double.valueOf(tokens[1].trim()).doubleValue();
                } else if (tokens[0].contentEquals("min")) {
                    data.min = Double.valueOf(tokens[1].trim()).doubleValue();
                } else if (tokens[0].contentEquals("max")) {
                    data.max = Double.valueOf(tokens[1].trim()).doubleValue();
                }
            }
            in.close();
        } catch (Exception e) {
            throw e;
        }
        Iterator iter = data.compTimes.iterator();
        double summation = 0;
        while (iter.hasNext()) {
            double time = ((Double) iter.next()).doubleValue();
            summation += Math.pow(time - data.avg, 2);
        }
        data.standardDeviation = Math.sqrt(summation / data.compTimes.size());
        return true;
    }
}
