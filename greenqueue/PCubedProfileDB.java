package PSaPP.greenqueue;

import java.io.*;
import java.util.*;

import PSaPP.data.*;
import PSaPP.data.TraceDB;
import PSaPP.data.PmacTrace;
import PSaPP.util.*;
import PSaPP.greenqueue.EnergyProfile;

/*
 * A colleciton of energy profiles
 */
public class PCubedProfileDB implements EnergyProfileDB {

    // For each energy profile in energy profiles
    //   process trace data to a code profile
    //   write record to outfile
    public static void createCodeProfiles(File pmacTraceDir, File energyProfiles, Integer sysid, File outfile)
        throws IOException {

        BufferedReader ifile = new BufferedReader(new FileReader(energyProfiles));
        BufferedWriter ofile = new BufferedWriter(new FileWriter(outfile));
        Set<String> processed = Util.newHashSet();
        Set<Integer> sysids = Util.newHashSet();
        sysids.add(sysid);

        try {
            for( String line = ifile.readLine(); line != null; line = ifile.readLine() ) {
                line = line.trim();
                if( line.startsWith("#") || line.equals("") ) {
                    continue;
                }

                // Determine the test case for this line
                String[] fields = line.split("\\s+");
                String app = fields[0];
                String dataset = fields[1];
                Integer cpus = Integer.parseInt(fields[2]);
                String agency = "none";
                String project = "greenq";
                Integer round = 1;
                TestCase testCase = new TestCase(app, dataset, cpus, agency, project, round);
                if( processed.contains(testCase.shortName()) ) {
                    continue;
                }
                processed.add(testCase.shortName());

                // Get a profile of functions in code
                PmacTrace pmactrace = new PmacTrace(pmacTraceDir, testCase);
                TraceDB tdb = new TraceDB(pmactrace.getProcessed().getAbsolutePath(), testCase, sysids);

                Map<FunctionID, Function> functions = Util.newHashMap();
                tdb.getDynamicAggregateStats(functions, null, sysid);

                Function summary = new Function();
                summary.functionName = "Summary";

                for( Iterator<Function> it = functions.values().iterator(); it.hasNext(); ) {
                    Function f = it.next();
                    summary.aggregateAggregator(f);
                }

                EnergyProfile ep = new FunctionProfile(summary, sysid);
                Logger.inform("Created Energy Profile:\n" + ep);

                String oline = testCase.shortName();
                oline += " " + ep.l1Misses + " " + ep.l2Misses + " " + ep.l3Misses;
                oline += " " + ep.dFpOps + " " + ep.dMemOps + " " + ep.dInsns;
                oline += " " + ep.idu + " " + ep.fdu;
                oline += "\n";
                ofile.write(oline);

            }
        } finally {
            ifile.close();
            ofile.close();
        }
    }

    // Create energy profiles from code profiles that we previously wrote and energy profiles
    public PCubedProfileDB(File profiles, File energyProfiles) throws IOException {
        readProfiles(profiles);
        readEnergyData(energyProfiles);
    }

    // All frequencies seen
    public SortedSet<Long> getFrequencies() {
        return this.frequencies;
    }

    // Searching for nearest profiles
    public EnergyProfile nearestProfile(CodeProfile cp) {
        return nearestEntry(cp).get2();
    }

    // Analyze profiles in database and check for errors
    public void verify(File suspiciousProfiles, File energyDBDiffs) throws IOException {

        BufferedWriter failedProfiles = new BufferedWriter(new FileWriter(suspiciousProfiles));
        BufferedWriter outfile = new BufferedWriter(new FileWriter(energyDBDiffs));

        TreeMap<String, EnergyProfile> sortedProfiles = new TreeMap<String, EnergyProfile>(this.profiles);

        EnergyProfile[] profiles = sortedProfiles.values().toArray(new EnergyProfile[0]);
        String[] profileNames = sortedProfiles.keySet().toArray(new String[0]);

        // null profiles that seem to have measurement errors
        int nSysPowerError = 0;
        int nCpuPowerError = 0;
        int nAllPowerError = 0;
        for( int i = 0; i < profiles.length; ++i ) {
            EnergyProfile p = profiles[i];

            boolean failedCpu = false;
            boolean failedSys = false;

            if( !p.checkCpuPower() ) {
                ++nCpuPowerError;
                failedCpu = true;
                profiles[i] = null;
                failedProfiles.write(profileNames[i] + " failed cpu check\n");
            }

            if( !p.checkSysPower() ) {
                ++nSysPowerError;
                failedSys = true;
                profiles[i] = null;
                failedProfiles.write(profileNames[i] + " failed sys check\n");
            }

            if( failedCpu && failedSys ) {
                ++nAllPowerError;
            }
        }
        failedProfiles.close();

        List<Tuple.Tuple2<Double, Long>> diffs = Util.newLinkedList();

        double minDistDiffer = Double.MAX_VALUE;
        double maxDistDiffer = 0.0;
        double minDistSame = Double.MAX_VALUE;
        double maxDistSame = 0.0;
        long countSameProfileAndFreq = 0;
        long countSameProfileDiffFreq = 0;

        for( int i = 0; i < profiles.length; ++i ) {
            EnergyProfile p1 = profiles[i];
            if( p1 == null ) {
                continue;
            }
            for( int j = i + 1; j < profiles.length; ++j ) {
                EnergyProfile p2 = profiles[j];
                if( p2 == null ) {
                    continue;
                }

                double dist = CodeProfile.difference(p1, p2);
                Long bestFreq1 = p1.getBestFreq();
                Long bestFreq2 = p2.getBestFreq();

                diffs.add(Tuple.newTuple2(dist, Math.abs(bestFreq1-bestFreq2)));

                if( bestFreq1.equals(bestFreq2) ) {
                    if( dist == 0.0 ) {
                        ++countSameProfileAndFreq;
                    }
                    if( dist < minDistSame ) {
                        minDistSame = dist;
                    }
                    if( dist > maxDistSame ) {
                        maxDistSame = dist;
                    }
                } else {
                    if( dist == 0.0 ) {
                        ++countSameProfileDiffFreq;
                        Logger.inform("DIFFERENT FREQUENCIES AT PCUBED POINT:\n" +
                        profileNames[i] + " has " + dist + " difference from " + profileNames[j] + "\n" +
                        profileNames[i] + ":" + p1 + "\n" +
                        profileNames[j] + ":" + p2 + "\n");
                        
                    }

                    if( dist < minDistDiffer ) {
                        minDistDiffer = dist;
                    }
                    if( dist > maxDistDiffer ) {
                        maxDistDiffer = dist;
                    }
                }

            }
        }

        String output = "\nENERGY DATABASE CHECK RESULTS\n";
            output += "CPU Power Errors: " + nCpuPowerError + "\n";
            output += "Sys Power Errors: " + nSysPowerError + "\n";
            output += "Overlap Power Error: " + nAllPowerError + "\n";
            output += "MinDistDiffer:" + minDistDiffer + "\n";
            output += "MaxDistDiffer:" + maxDistDiffer + "\n";
            output += "MinDistSame:" + minDistSame + "\n";
            output += "MaxDistSame:" + maxDistSame + "\n";
            output += "ZeroDistSameFreq:" + countSameProfileAndFreq + "\n";
            output += "ZeroDistDiffFreq:" + countSameProfileDiffFreq + "\n";

        Logger.inform(output);

        try {
            for( Iterator<Tuple.Tuple2<Double, Long>> it = diffs.iterator(); it.hasNext(); ) {
                Tuple.Tuple2<Double, Long> tup = it.next();
                Double dist = tup.get1();
                Long freqdiff = tup.get2();
                outfile.write(dist + "\t" + freqdiff + "\n");
            }
        } finally {
            outfile.close();
        }
    }

    public void writeFrequencyProfiles(File outfile) throws IOException {
        BufferedWriter file = new BufferedWriter(new FileWriter(outfile));

        try {
            file.write("Frequency\tL1MPI\tL2MPI\tL3MPI\tFMR\tIDU\tFDU\n");
            for( Iterator<EnergyProfile> it = this.profiles.values().iterator(); it.hasNext(); ) {
                EnergyProfile ep = it.next();
                file.write(ep.getBestFreq() + "\t" + ep.l1MissesPerInst() + "\t" + ep.l2MissesPerInst() + "\t" +
                           ep.l3MissesPerInst() + "\t" + ep.fmr() + "\t" + ep.idu + "\t" + ep.fdu + "\n");
                           
            }
        } finally {
            file.close();
        }
    }

    public void writeBestFrequencies(File infile, File outfile) throws IOException {
        BufferedReader file = new BufferedReader(new FileReader(infile));
        try {
            List<String> testcases = Util.newLinkedList();
            for( String line = file.readLine(); line != null; line = file.readLine() ) {
                line = line.trim();
                if( line.startsWith("#") ) {
                    continue;
                }
                String[] fields = line.split("\\s+");
                testcases.add(fields[0]);
            }
            writeBestFrequencies(testcases, outfile);
        } finally {
            file.close();
        }
    }

    public void writeBestFrequencies(List<String> testcases, File outfile) throws IOException {
        BufferedWriter file = new BufferedWriter(new FileWriter(outfile));
        try {
            for( Iterator<String> it = testcases.iterator(); it.hasNext(); ) {
                String testcase = it.next();
                EnergyProfile ep = this.profiles.get(testcase);
                file.write(testcase + "\t" + ep.getBestFreq() + "\n");
            }
        } finally {
            file.close();
        }
    }

    // -- Private -- //
    private Map<String, EnergyProfile> profiles = Util.newHashMap();
    private SortedSet<Long> frequencies = Util.newTreeSet();
    private File pmacTraceDir = null;

    private Long parseFreq(String gHz) {
        Double fgHz = Double.parseDouble(gHz);
        Long fkHz = ((Double)(fgHz * 1000000)).longValue();
        return fkHz;
    }

    // Read profile data from a file, rather than from processed traces
    private void readProfiles(File filepath) throws IOException {
        BufferedReader file = new BufferedReader(new FileReader(filepath));
        try {
            for( String line = file.readLine(); line != null; line = file.readLine() ) {
                if( line.startsWith("#") ) {
                    continue;
                }
                String[] fields = line.split("\\s+");

                EnergyProfile ep = new EnergyProfile();

                String testCase = fields[0];
                ep.l1Misses = Long.parseLong(fields[1]);
                ep.l2Misses = Long.parseLong(fields[2]);
                ep.l3Misses = Long.parseLong(fields[3]);
                ep.dFpOps = Long.parseLong(fields[4]);
                ep.dMemOps = Long.parseLong(fields[5]);
                ep.dInsns = Long.parseLong(fields[6]);
                ep.idu = Double.parseDouble(fields[7]);
                ep.fdu = Double.parseDouble(fields[8]);
                ep.l1MPI = ep.l1MissesPerInst();
                ep.l2MPI = ep.l2MissesPerInst();
                ep.l3MPI = ep.l3MissesPerInst();

                this.profiles.put(testCase, ep);
                ep.key = testCase;
            }
        } finally {
            file.close();
        }
    }

    // Read energy data from file, assuming all code profiles have already been read
    private void readEnergyData(File energyProfiles) throws IOException {

        BufferedReader file = new BufferedReader(new FileReader(energyProfiles));
        try {
            for( String line = file.readLine(); line != null; line = file.readLine() ) {
                if( line.startsWith("#") ) {
                    continue;
                }
                // Determine which test case this is
                String[] fields = line.split("\\s+");
                String app = fields[0];
                String dataset = fields[1];
                Integer cpus = Integer.parseInt(fields[2]);
                String agency = "none";
                String project = "greenq";
                Integer round = 1;
                TestCase testCase = new TestCase(app, dataset, cpus, agency, project, round);

                EnergyProfile ep = this.profiles.get(testCase.shortName());
                assert( ep != null );

                Long freq = parseFreq(fields[3]);
                Double time = Double.parseDouble(fields[4]);
                Double sysPower = Double.parseDouble(fields[5]);
                Double cpu1Power = Double.parseDouble(fields[6]);
                Double cpu2Power = Double.parseDouble(fields[7]);
                Double mem1Power = Double.parseDouble(fields[8]);
                Double mem2Power = Double.parseDouble(fields[9]);

                ep.addStats(freq, time, sysPower, cpu1Power, cpu2Power, mem1Power, mem2Power);
                this.frequencies.add(freq);
                
            }
        } finally {
            file.close();
        }
    }

    private Tuple.Tuple2<String, EnergyProfile> nearestEntry(CodeProfile cp) {
        Tuple.Tuple3<String, EnergyProfile, Double> all = nearestEntryAndDist(cp);
        return Tuple.newTuple2(all.get1(), all.get2());
    }

    private Tuple.Tuple3<String, EnergyProfile, Double> nearestEntryAndDist(CodeProfile cp) {
        String bestKey = null;
        EnergyProfile best = null;
        Double bestDist = Double.MAX_VALUE;
        for( Iterator<Map.Entry<String, EnergyProfile>> it = this.profiles.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, EnergyProfile> ent = it.next();
            String testName = ent.getKey();
            EnergyProfile fp = ent.getValue();
            Double dist = CodeProfile.difference(cp, fp);
            if( best == null || dist < bestDist ) {
                best = fp;
                bestDist = dist;
                bestKey = testName;
            }
        }

        return Tuple.newTuple3(bestKey, best, bestDist);
    }

    public Set<EnergyProfile> allNearest(CodeProfile cp) {
        Double bestDist = null;
        Set<EnergyProfile> best = null;

        for( Iterator<Map.Entry<String, EnergyProfile>> it = this.profiles.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, EnergyProfile> ent = it.next();
            String testname = ent.getKey();
            EnergyProfile ep = ent.getValue();

            Double dist = CodeProfile.difference(cp, ep);

            if( bestDist == null || dist < bestDist ) {
                best = Util.newHashSet();
                best.add(ep);
                bestDist = dist;
            } else if ( dist.equals(bestDist) ) {
                best.add(ep);
            }
        }

        Logger.inform("Best distance: " + bestDist);
        return best;
    }

    private EnergyProfile nearestAndCheckTestCase(CodeProfile cp, TestCase testCase) {
        Tuple.Tuple3<String, EnergyProfile, Double> res = nearestEntryAndDist(cp);
        String testName = res.get1();

        if( testName.equals(testCase.shortName()) ) {
            Logger.inform("ENERGYDB CHECK PASSED: " + testName);
        } else {
            EnergyProfile correctProfile = this.profiles.get(testCase.shortName());
            Double dist = CodeProfile.difference(cp, correctProfile);

            Logger.inform("ENERGYDB CHECK FAILED: " + testCase.shortName() + " != " + testName + "\n" +
                          testCase.shortName() + ":" + dist + ":" + correctProfile.toString() + "\n" +
                          testName + ":" + res.get3() + ":" + res.get2().toString() + "\n");

        }
        return res.get2();
    }
}

