package PSaPP.dbase;
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

/**
 * This class parses an input file and returns an array
 * in the correct format for the Actions class.
 * Input is assumed to be in the following format:
 * Levels L1size L1assoc L1line L1repl L2size L2assoc L2line L2repl L3size L3assoc L3line L3repl #Comment
 * For example:
 * 1 65536 8192 8 lru #For temporal locality score fully associative 64KB cache line size is word
 * 2 32KB  64  32    lru    2048KB   8  128    lru      # Theoretical BGL/P and real BGL is same
 * 3 64KB   8   128    lru    4096KB   8  128    lru  16384KB   16  128  lru   # Theoretical IBM P6 
 * @author bsheely
 */
public class CacheStructuresParser {

    static int[] cacheLevels;

    /**
     * Parse the specified file and return an array of the contents
     * @param file The file to be parsed
     * @return String[] Array of the file contents
     */
    public static String[] parse(BufferedReader file) {
        String[] retValue = null;
        LinkedList lines = new LinkedList();
        try {
            String line;
            while ((line = file.readLine()) != null) {
                if (!line.startsWith("#") && !line.isEmpty()) {
                    lines.add(line);
                }
            }
            file.close();
        } catch (Exception e) {
            Logger.error("Exception while reading file\n" + e.getMessage());
        }
        if (lines.size() > 0) {
            int i = 0;
            cacheLevels = new int[lines.size()];
            retValue = new String[lines.size()];
            Iterator itr = lines.iterator();
            while (itr.hasNext()) {
                String formattedLine = "";
                String line = (String) itr.next();
                int j = -1;
                int levels = 0;
                String comment = line.substring(line.indexOf("#") + 1).trim();
                StringTokenizer tokens = new StringTokenizer(line.substring(0, line.indexOf("#")));
                String[] data = new String[tokens.countTokens() - 1];
                while (tokens.hasMoreTokens()) {
                    String token = tokens.nextToken().trim();
                    if (j == -1) {
                        levels = Integer.parseInt(token);
                    } else {
                        data[j] = token;
                    }
                    ++j;
                }
                String l1_size = data[0].endsWith("KB") ? Util.convertKbToBytes(data[0]) : data[0];
                if (levels == 1) {
                    formattedLine = l1_size + "," + data[1] + "," + data[2] + "," + data[3] + "," + comment;
                } else if (levels == 2) {
                    String l2_size = data[4].endsWith("KB") ? Util.convertKbToBytes(data[4]) : data[4];
                    formattedLine = l1_size + "," + l2_size + "," + data[1] + ","
                            + data[5] + "," + data[2] + "," + data[6] + "," + data[3] + "," + data[7] + "," + comment;
                } else if (levels == 3) {
                    String l2_size = data[4].endsWith("KB") ? Util.convertKbToBytes(data[4]) : data[4];
                    String l3_size = data[8].endsWith("KB") ? Util.convertKbToBytes(data[8]) : data[8];
                    formattedLine = l1_size + "," + l2_size + "," + l3_size + "," + data[1] + "," + data[5] + ","
                            + data[9] + "," + data[2] + "," + data[6] + "," + data[10] + "," + data[3] + ","
                            + data[7] + "," + data[11] + "," + comment;
                }
                retValue[i] = formattedLine;
                cacheLevels[i] = levels;
                ++i;
            }
        }
        return retValue;
    }

    /**
     * Return the number of cache levels for a specified line of input data
     * @param lineNum The line in the input file being processed
     * @return int Number of cache levels
     */
    public static int getCacheLevels(int lineNum) {
        if (lineNum < cacheLevels.length) {
            return cacheLevels[lineNum];
        }
        return 0;
    }
}
