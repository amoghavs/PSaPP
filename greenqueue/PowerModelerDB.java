package PSaPP.greenqueue;

import java.util.*;
import java.io.*;

import PSaPP.util.*;

public class PowerModelerDB implements EnergyProfileDB {
    public PowerModelerDB(File script, String model, LoopTimeDB loopTimes) {
        this.script = script;
        if( !this.script.exists() ) {
            throw new java.lang.IllegalArgumentException("PowerModelerDB: script " + script + " does not exist");
        }

        this.model = model;
        this.loopTimes = loopTimes;
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

    public Set<EnergyProfile> allNearest(CodeProfile cp) {
        String command = this.script.getAbsolutePath();
        command += " " + cp.l1MissesPerInst() + " " + cp.l2MissesPerInst() + " " + cp.l3MissesPerInst() + " " +
                   cp.fpRatio() + " " + cp.memToIns() + " " + cp.fpToIns() + " " + cp.fmr() + " " +
                   cp.iDud() + " " + cp.fDud() + " " + this.model + " 42";
        LinkedList<String> res = LinuxCommand.execute(command);
        if( res == null ) {
            throw new IllegalStateException("PowerModelerDB: error executing modeler script");
        }
        
        File pred = new File("R_pred.42.dat");
        if( !pred.exists() ) {
            throw new IllegalStateException("PowerModelerDB: unable to locate output file " + pred);
        }

        Map<Long, Double> sysPower = Util.newHashMap();
        try {
            BufferedReader file = new BufferedReader(new FileReader(pred));
            try {
                for( String line = file.readLine(); line != null; line = file.readLine() ) {
                    String[] fields = line.split("\\s+");
                    Long freq = Long.parseLong(fields[0]);
                    Double power = Double.parseDouble(fields[1]);
                    sysPower.put(freq, power);
                }
            } finally {
                file.close();
            }
        } catch (IOException e) {
            Logger.error(e, "Error reading output file " + pred);
        }

        EnergyProfile ep = new EnergyProfile();
        ep.setSysPower(sysPower);
        ep.setTime(loopTimes.getLoopTime(cp.loopHead));
        Logger.inform("Loop Times for loop: " + cp.loopHead);
        Logger.inform("" + loopTimes.getLoopTime(cp.loopHead));
        Set<EnergyProfile> retval = Util.newHashSet();
        retval.add(ep);
        return retval;

    }

    private final File script;
    private final String model;
    private final LoopTimeDB loopTimes;
}

