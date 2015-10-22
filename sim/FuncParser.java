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
import PSaPP.pred.*;

/**
 * This class parses a .func file and uses the data contained
 * within to populate a FuncData object.
 * @author bsheely
 */
public class FuncParser {

    static final int THRESHOLD = 10; //percent of total computation time

    /**
     * Parse the specified file and populate the data object
     * @param file The .func file to be parsed
     * @param data The object to be populated with the parse results
     * @return boolean True if successful
     */
    public boolean parse(String file, FuncData data) throws Exception {
        try {
            FileInputStream fstream = new FileInputStream(file);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("#")) {
                    String tokens[] = line.split("\t");
                    String name = tokens[0].trim();
                    if (name.startsWith(".")) {
                        name = name.substring(1);
                    }
                    if (name.indexOf("__") > 0) {
                        name = name.substring(0, name.indexOf("__"));
                    }
                    double time = Double.valueOf(tokens[1].trim()).doubleValue();
                    List hitrates = new ArrayList();
                    for (int i = 2; i < tokens.length; ++i) {
                        hitrates.add(Double.valueOf(tokens[i].trim()));
                    }
                    data.funcTimes.add(new FuncTime(name, time, hitrates));
                    data.totalTime += time;
                    if (data.cachelevels == 0) {
                        data.cachelevels = hitrates.size();
                    }
                }
            }
            in.close();
        } catch (Exception e) {
            throw e;
        }
        for (int i = 0; i < data.funcTimes.size(); ++i) {
            FuncTime func = (FuncTime) data.funcTimes.get(i);
            func.percent_runtime = func.time / data.totalTime * 100;
            data.funcTimes.set(i, func);
        }
        Collections.sort(data.funcTimes);
        double percentTotal = data.totalTime * ((double) THRESHOLD / 100);
        Iterator iterator = data.funcTimes.iterator();
        while (iterator.hasNext()) {
            FuncTime func = (FuncTime) iterator.next();
            if (func.time > percentTotal) {
                data.funcTimeComments.add(func);
            }
        }
        return true;
    }
}
