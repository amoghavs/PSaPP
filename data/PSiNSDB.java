package PSaPP.data;

import java.util.*;
import java.util.regex.*;
import java.io.*;

import PSaPP.util.*;


public class PSiNSDB {

    // This map is the database
    final private Map<String, PSiNSCount> psinscounts = Util.newHashMap();

    // Create a new PSiNSCount using data from file and add it to our database
    public PSiNSCount addPSiNSCount(File filename, String app, String dataset, int ncpu, String system) {
        // Check if already exists in database?

        PSiNSCount psins = new PSiNSCount(app, dataset, ncpu, system);
        
        try {
            BufferedReader file = new BufferedReader(new FileReader(filename));
            try {
                Matcher m;
                Pattern rankEventLine = Pattern.compile("MPI rank=(\\d+) name=(\\S+) count=(\\d+) time=(\\S+)");

                for( String line = file.readLine(); line != null; line = file.readLine() ) {
                    line = line.trim();

                    m = rankEventLine.matcher(line);
                    if( m.matches() ) {
                        int rank = Integer.parseInt(m.group(1));
                        String eventS = m.group(2);
                        Long count = Long.parseLong(m.group(3));
                        Double time = Double.parseDouble(m.group(4));

                        PSiNSCount.Event event = Enum.valueOf(PSiNSCount.Event.class, eventS.toUpperCase());

                        psins.addRankEventData(rank, event, count, time);
                        continue;
                    }

                    Logger.warn("Could not parse line:" + line);

                }
            } finally {
                file.close();
            }
        } catch (Exception e) {
            Logger.error(e, "Unable to create new PSiNSCount from file " + filename);
            return null;
        }

        String s = PSiNSCount.makeKey(app, dataset, ncpu, system);
        this.psinscounts.put(s, psins);

        return psins;
    }

    // Retreive a PSiNSCount from the database
    public PSiNSCount getPSiNSCount(String app, String dataset, int ncpu, String system) {
        String s = PSiNSCount.makeKey(app, dataset, ncpu, system);
        return this.psinscounts.get(s);
    }

}


