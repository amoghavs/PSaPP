package PSaPP.greenqueue;

import java.io.*;
import java.util.*;
import PSaPP.util.*;
import PSaPP.data.BlockID;

public class LoopTimeDB {
    public LoopTimeDB(File input) throws IOException {
        if( !input.exists() ) {
            throw new IOException("loop time file does not exist: " + input);
        }

        List<BlockID> loopHeads = Util.newArrayList(1);
        BufferedReader file = new BufferedReader(new FileReader(input));
        try {
            String line = file.readLine();
            String[] fields = line.split("\\s+");
            int i;
            for( i = 1; i < fields.length; ++i ) {
                BlockID id = new BlockID(0L, Long.parseLong(fields[i]));
                loopHeads.add(id);
                loopTimes.put(id, new HashMap<Long, Double>());
            }

            for( line = file.readLine(); line != null; line = file.readLine() ) {
                fields = line.split("\\s+");
                Long freq = Long.parseLong(fields[0]);
                for( i = 1; i < fields.length; ++i ) {
                    Double time = Double.parseDouble(fields[i]);
                    loopTimes.get(loopHeads.get(i-1)).put(freq, time);
                }
            }
        } finally {
            file.close();
        }
    }

    public Map<Long, Double> getLoopTime(BlockID loopHead) {
        return loopTimes.get(loopHead);
    }

    // Private
    Map<BlockID, Map<Long, Double>> loopTimes = Util.newHashMap();
}

