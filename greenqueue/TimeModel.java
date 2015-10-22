package PSaPP.greenqueue;

import PSaPP.util.*;
import java.io.*;
import java.util.*;

public class TimeModel {
    public TimeModel(File script, String model) {
        this.script = script;
        this.model = model;
    }

    public SortedSet<Long> getFrequencies() {
        SortedSet<Long> res = Util.newTreeSet();
        res.add(1200000L);
        res.add(1300000L);
        res.add(1400000L);
        res.add(1500000L);
        res.add(1600000L);
        res.add(1700000L);
        res.add(1800000L);
        res.add(1900000L);
        res.add(2000000L);
        res.add(2100000L);
        res.add(2200000L);
        res.add(2300000L);
        res.add(2400000L);
        res.add(2500000L);
        res.add(2600000L);
        //res.add(2601000L);
        return res;
    }
    public Map<Long, Double> getTimes(LoopProfile lp) {

        String command = this.script.getAbsolutePath();
        command += " " + lp.l1MissesPerInst() + " " + lp.l2MissesPerInst() + " " + lp.l3MissesPerInst() + " " +
                         lp.fpRatio() + " " + lp.memToIns() + " " + lp.fpToIns() + " " + lp.fmr() + " " +
                         lp.iDud() + " " + lp.fDud() + " " + this.model + " 42";

        LinkedList<String> res = LinuxCommand.execute(command);
        if( res == null ) {
            throw new IllegalStateException("TimeModelerDB: error executing modeler script");

        }

        File pred = new File("R_pred.42.dat");
        if( !pred.exists() ) {
            throw new IllegalStateException("TimeModelerDB: unable to locate output file " + pred);
        }

        Map<Long, Double> times = Util.newHashMap();
        try {
            BufferedReader file = new BufferedReader(new FileReader(pred));
            try {
               for( String line = file.readLine(); line != null; line = file.readLine() ) {
                   String[] fields = line.split("\\s+");
                   Long freq = Long.parseLong(fields[0]);
                   Double time = Double.parseDouble(fields[1]);
                   times.put(freq, time);
               }
            } finally {
                file.close();
            }
        } catch (IOException e) {
            Logger.error(e, "Error reading output file " + pred);
        }

        return times;
    }

    private final File script;
    private final String model;
}


