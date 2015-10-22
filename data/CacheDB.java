package PSaPP.data;

import java.util.*;
import java.util.regex.*;
import java.io.*;

import PSaPP.util.*;

/* This class knows the format of CacheDescriptions.txt files */

public class CacheDB extends HashMap<Integer, CacheDescription> {

    public boolean addFromFile(String filename) {
        try {
            BufferedReader file = new BufferedReader(new FileReader(filename));
            try {

                Pattern size_pat = Pattern.compile("(\\d+)(.*)");

                for( String line = file.readLine(); line != null; line = file.readLine() ) {
                    line = Util.cleanComment(line);
                    line = line.trim();
                    if( line.equals("") ) {
                        continue;
                    }

                    String fields[] = line.split("\\s+");

                    Integer sysid = Integer.parseInt(fields[0]);
                    int levels = Integer.parseInt(fields[1]);

                    if( fields.length != (2 + 4*levels) ) {
                        throw new IOException("Number of fields inconsistent with number of levels on line:" + line);
                    }

                    CacheDescription cd = new CacheDescription(levels);

                    for( int level = 1; level <= levels; ++level ) {
                        int offset = (level-1) * 4;

                        Matcher m = size_pat.matcher(fields[2 + offset]);
                        if( !m.matches() ) {
                            throw new IOException("Unable to parse cache size " + fields[2 + offset]);
                        }
                        int size = Integer.parseInt(m.group(1));
                        String multiplier = m.group(2);

                        if( multiplier.equals("KB") ) {
                            size = size << 10;
                        } else if( multiplier.equals("") ) {

                        } else if( multiplier.equals("MB") ) {
                            size = size << 20;
                        }
                        
                        int assoc = Integer.parseInt(fields[3 + offset]);
                        int bytesPerLine = Integer.parseInt(fields[4 + offset]);
                        String repl = fields[5 + offset];
                        cd.setLevelInfo(level, size, assoc, bytesPerLine, repl);
                    }
                    this.put(sysid, cd);
                }
            } finally {
                file.close();
            }
        } catch (IOException e) {
            Logger.error(e, "Unable to read file " + filename);
            return false;
        }
        return true;
    }
}

