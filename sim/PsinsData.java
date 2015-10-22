package PSaPP.sim;
/*
Copyright (c) 2010, PMaC Laboratories, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

 *  Redistributions of source code must retain the above copyright notice, this list of conditions
and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  Neither the name of PMaC Laboratories, Inc. nor the names of its contributors may be
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

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import PSaPP.dbase.*;
import PSaPP.util.*;
import PSaPP.pred.*;
import PSaPP.data.*;

/**
 * This class encapsulates the data parsed from a psins simulation run
 */
public class PsinsData {

    public static final int INVALID_TASKID = -1;

    private String simulationDir;
    private Database database;

    private String application;
    private String dataset;
    private String simulatedSystem;
    private int sysid;
    private int machineProfile;
    private int baseResource;
    private int memoryProfile;
    private int cpuCount;

    private String statsName = null;
    
    // from .psinsout
    private TaskTimes taskEventTimes;
    private Histogram sizeCounts;
    private Histogram p2pSizeCounts;
    private Histogram p2pOnNodeSizeCounts;
    private Histogram p2pOffNodeSizeCounts;

    // from .bins
    private BinnedHitRates binnedHitRates;

    // from .func
    private Map<String, FunctionInfo> functions;

    public String fileBaseName(){
        return application + "_" + dataset + "_" + Format.cpuToString(cpuCount) + "_" + Format.PR(machineProfile);
    }

    private void setStatsBaseName(){
        LinkedList res = LinuxCommand.ls(statsDir());

        if (res != null && res.size() > 0){
            for (Iterator it = res.iterator(); it.hasNext(); ){
                String n = (String)it.next();
                if (n.matches("sysid" + sysid + "_.*_" + Format.BR(baseResource) + "_" + Format.MP(memoryProfile) + "\\..{4}+")){
                    statsName = n.substring(0, n.length() - 5);
                    return;
                }
            }
        }
        Logger.warn("Cannot find any details files for psins output. Did you run it with --stats?");
        statsName = "missingfilename";
    }

    public String statsBaseName(){
        if (statsName == null){
            setStatsBaseName();
        }
        return statsName;
    }

    public String statsDir(){
        return simulationDir + "/stats";
    }

    public String psinsoutName(){
        return simulationDir + "/" + fileBaseName() + ".psinsout";
    }

    public String binsName(){
        return statsDir() + "/" + statsBaseName() + ".bins";
    }

    public String funcName(){
        return statsDir() + "/" + statsBaseName() + ".func";
    }

    public String taskName(){
        return statsDir() + "/" + statsBaseName() + ".task";
    }

    public PsinsData(Database db, String dir, String app, String ds, Integer cpus, Integer machProf){
        simulationDir = dir;
        application = app;
        dataset = ds;
        cpuCount = cpus;
        machineProfile = machProf;

        database = db;
        assert(database != null);

        baseResource = database.getBaseResource(machineProfile);
        sysid = database.getCacheSysId(machineProfile);
        memoryProfile = database.getMemoryPIdx(machineProfile);
        simulatedSystem = database.getBaseResourceName(machineProfile);
    }

    public boolean parse(){

        if (Util.isFile(psinsoutName())){
            parseDotOut(psinsoutName());
        } else {
            Logger.error("Cannot find .psinsout file: " + psinsoutName());
        }

        if (Util.isFile(binsName())){
            parseDotBins(binsName());
        } else {
            Logger.warn("Cannot find .bins file: " + binsName());
        }

        if (Util.isFile(funcName())){
            parseDotFunc(funcName());
        } else {
            Logger.warn("Cannot find .func file: " + funcName());
        }

        if (Util.isFile(taskName())){
            parseDotTask(taskName());
        } else {
            Logger.warn("Cannot find .task file: " + taskName());
        }

        return true;
    }

    public boolean addTraceDBFunctions(Map<FunctionID, Function> funcStats){
        int functionsFound = 0;
        for (Iterator<FunctionID> it = funcStats.keySet().iterator(); it.hasNext(); ){
            FunctionID fid = it.next();
            Function f = funcStats.get(fid);
            String name = f.functionName;
            FunctionInfo fsim = functions.get(name);
            if (f != null){
                fsim.setFunctionDynamic(f);
                functionsFound++;
            }
        }

        assert(functionsFound == functions.size());
        return true;
    }

    public static void addXmlChildren(Element root, Set<Element> children){
        for (Iterator it = children.iterator(); it.hasNext(); ){
            Element child = (Element)it.next();
            root.appendChild(child);
        }
    }

    public Element toXMLElement(Document doc) {

        // set root element
        Element e = doc.createElement("psinsrun");
        e.setAttribute("machine_profile", Integer.toString(machineProfile));
        e.setAttribute("simulated_system", simulatedSystem);

        // add sub-structures
        if (taskEventTimes != null){
            addXmlChildren(e, taskEventTimes.toXML(doc));
        }
        if (sizeCounts != null){
            addXmlChildren(e, sizeCounts.toXML(doc));
        }
        if (p2pSizeCounts != null){
            addXmlChildren(e, p2pSizeCounts.toXML(doc));
        }
        if (p2pOnNodeSizeCounts != null){
            addXmlChildren(e, p2pOnNodeSizeCounts.toXML(doc));
        }
        if (p2pOffNodeSizeCounts != null){
            addXmlChildren(e, p2pOffNodeSizeCounts.toXML(doc));
        }
        if (binnedHitRates != null){
            addXmlChildren(e, binnedHitRates.toXML(doc));
        }

        if (functions != null){
            Element eltf = doc.createElement("function_details");
            e.appendChild(eltf);
            for (Iterator it = functions.keySet().iterator(); it.hasNext(); ){
                String name = (String)it.next();
                FunctionInfo f = (FunctionInfo)functions.get(name);
                if (f.func.perSysCaches.get(sysid) == null){
                    Logger.warn("Cannot find cache information for sysid " + sysid);
                }
                addXmlChildren(eltf, f.toXML(doc, sysid));
            }
        }

        return e;
    }

    private String[] splitLineCheck(String line, String delim, int num){
        String[] fields = line.split(delim);
        if (fields.length != num){
            Logger.error("Field length of line should be " + num + " but is " + fields.length + ": " + line);
            return null;
        }
        return fields;
    }

    private String washLine(String line){
        String[] washThese = { "\\(", "\\)", "---", ":", "\\[", "\\]" };
        line = line.trim();
        for (String s : washThese){
            line = line.replaceAll(s, "");
        }
        return line;
    }

    private void addHistogramLine(Histogram h, String line){
        assert(h != null);
        String[] fields = splitLineCheck(washLine(line), "\\s+", 3);
        long bin = new Long(fields[1]);
        long value = new Long(fields[2]);
        h.addBin(bin, value);
    }

    private void parseDotBins(String file){
        Logger.inform("initializing psinsdata for... " + file);
        binnedHitRates = new BinnedHitRates();
        try {
            FileInputStream fstream = new FileInputStream(file);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line, token = "";        
            Map<Integer, Tuple.Tuple3<Integer, Long, Double>> blockBins = Util.newHashMap();
            Map<Integer, Tuple.Tuple3<Double, Double, Double>> binIdents = Util.newHashMap();

            Integer binIdx = null;
            while ((line = br.readLine()) != null) {
                StringTokenizer tokens = new StringTokenizer(line);
                String[] fields = null;
                if (line.startsWith("BlockBin")){
                    fields = splitLineCheck(line, "\\s+", 7);
                    binIdx = new Integer(fields[1]);
                    Integer bbCnt = new Integer(fields[2]);
                    Long dynMemop = new Long(fields[3]);
                    Double binTime = new Double(fields[4]);

                    blockBins.put(binIdx, Tuple.newTuple3(bbCnt, dynMemop, binTime));
                } else if (line.startsWith("BinIdent")){
                    fields = line.split("\\s+");
                    int levels = fields.length - 2;
                    assert(levels >= 1 && levels <= 3);
                    binIdx = new Integer(fields[1]);
                    Double l1hr = new Double(fields[2]);
                    Double l2hr = new Double(CacheStats.INVALID_HIT_RATE);
                    Double l3hr = new Double(CacheStats.INVALID_HIT_RATE);
                    if (levels > 1){
                        l2hr = new Double(fields[3]);
                    }
                    if (levels > 2){
                        l3hr = new Double(fields[4]);
                    }

                    binIdents.put(binIdx, Tuple.newTuple3(l1hr, l2hr, l3hr));
                } 

                if (binIdx != null){
                    if (binIdents.containsKey(binIdx) &&
                        blockBins.containsKey(binIdx)){
                        
                        Tuple.Tuple3<Integer, Long, Double> bbin = blockBins.remove(binIdx);
                        Tuple.Tuple3<Double, Double, Double> bide = binIdents.remove(binIdx);
                        
                        Integer bbCnt = bbin.get1();
                        Long dynMemop = bbin.get2();
                        Double binTime = bbin.get3();
                        Double l1hr = bide.get1();
                        Double l2hr = bide.get2();
                        Double l3hr = bide.get3();

                        binnedHitRates.addBin(binIdx, bbCnt, dynMemop, binTime, l1hr, l2hr, l3hr);
                    }
                }

                binIdx = null;
            }
        } catch (Exception e) {
            Logger.error(e, "Cannot parse stats/.bins file " + file);
            return;
        }
    }

    private void parseDotFunc(String file){
        Logger.inform("initializing psinsdata for... " + file);
        functions = Util.newHashMap();

        Double zero = new Double(0.0);

        try {
            FileInputStream fstream = new FileInputStream(file);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;

            while ((line = br.readLine()) != null) {
                line = Util.cleanComment(line);
                StringTokenizer tokens = new StringTokenizer(line);
                String[] fields = line.split("\\s+");

                if (fields.length == 5){
                    try {
                        String name = fields[0];
                        Double funcTime = new Double(fields[1]);
                        Double l1hr = new Double(fields[2]);
                        Double l2hr = new Double(fields[3]);
                        Double l3hr = new Double(fields[4]);

                        if (zero.compareTo(l1hr + l2hr + l3hr) == 0 &&
                            zero.compareTo(funcTime) == 0){
                            continue;
                        }
                        functions.put(name, new FunctionInfo(name, funcTime, l1hr, l2hr, l3hr));
                    } catch (NumberFormatException nfe){
                        // do nothing, this means that a number isn't the 1st token
                    }
                }
            }
        } catch (Exception e){
            Logger.error(e, "Cannot parse stats/.bins file " + file);
            return;
        }
    }

    private void parseDotTask(String file){
        Logger.inform("initializing psinsdata for... " + file);
        assert(taskEventTimes != null);
        try {
            FileInputStream fstream = new FileInputStream(file);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;

            while ((line = br.readLine()) != null) {
                StringTokenizer tokens = new StringTokenizer(line);
                String[] fields = line.split("\\s+");

                if (fields.length == 2){
                    try {
                        Integer task = new Integer(fields[0]);
                        Double compTime = new Double(fields[1]);

                        taskEventTimes.addCompTime(task, compTime);
                    } catch (NumberFormatException nfe){
                        // do nothing, this means that a number isn't the 1st token
                    }
                }
            }
        } catch (Exception e){
            Logger.error(e, "Cannot parse stats/.bins file " + file);
            return;
        }
    }

    private void parseDotOut(String file){
        Logger.inform("initializing psinsdata for... " + file);
        taskEventTimes = new TaskTimes(cpuCount);
        try {
            FileInputStream fstream = new FileInputStream(file);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line, token = "", event = "", eventValue = "";
            boolean readingETaskTimeEvent = false;
            List taskEvents = null;
            int taskCount = 0, tokenCount;
            HashMap eTaskTimeData = new HashMap();
            int currentTaskEvent = INVALID_TASKID;

            while ((line = br.readLine()) != null) {
                StringTokenizer tokens = new StringTokenizer(line);
                String[] fields = null;
                if (line.startsWith("application")){
                    fields = splitLineCheck(line, "\\s+", 2);
                    if (fields[1].compareTo(application) != 0){
                        Logger.error("application name (" + fields[1] + ") found inside file doesn't match file name: " + application);
                    }
                } else if (line.startsWith("dataset")){
                    fields = splitLineCheck(line, "\\s+", 2);
                    if (fields[1].compareTo(dataset) != 0){
                        Logger.error("dataset (" + fields[1] + ") found inside file doesn't match file name: " + dataset);
                    }
                } else if (line.startsWith("task count")){
                    fields = splitLineCheck(line, "\\s+", 3);
                    Integer cpus = new Integer(fields[2]);
                    if (cpus.intValue() != cpuCount){
                        Logger.error("cpuCount (" + cpus + ") found inside file doesn't match file name: " + cpuCount);
                    }
                } else if (line.startsWith("machine profile")){
                    fields = splitLineCheck(line, "\\s+", 3);
                    Integer mProf = new Integer(fields[2]);
                    if (mProf.intValue() != machineProfile){
                        Logger.error("machine profile (" + mProf + ") found inside file doesn't match file name: " + machineProfile);
                    }
                } else if (line.startsWith("Task")){
                    fields = washLine(line).split("\\s+");

                    if (fields.length == 11){
                        int cput = new Integer(fields[1]);
                        double comm = new Double(fields[3]);
                        double comp = new Double(fields[5]);
                        double wait = new Double(fields[7]);
                        
                        taskEventTimes.addTaskSummary(cput, comp, comm, wait);
                    } else if (fields.length == 10 && !fields[1].equals("++++")){
                        int cput = new Integer(fields[1]);
                        long count = new Long(fields[2]);
                        long inserts = new Long(fields[6]);

                        taskEventTimes.addEventCounts(cput, count, inserts);
                    }
                } else if (line.startsWith("BaseSystemClock")){
                    fields = splitLineCheck(washLine(line), "\\s+", 4);
                    int cput = new Integer(fields[1]);
                    double time = new Double(fields[3]);

                    taskEventTimes.addBaseClock(cput, time);
                } else if (line.startsWith("CommSizeToECount")){
                    if (sizeCounts == null){
                        sizeCounts = new Histogram("Size Counts");
                    } else {
                        addHistogramLine(sizeCounts, line);
                    }
                } else if (line.startsWith("CommSizeToP2PECount")){
                    if (p2pSizeCounts == null){
                        p2pSizeCounts = new Histogram("P2P Size Counts");
                    } else {
                        addHistogramLine(p2pSizeCounts, line);
                    }
                } else if (line.startsWith("CommSizeToP2POnNodeECount")){
                    if (p2pOnNodeSizeCounts == null){
                        p2pOnNodeSizeCounts = new Histogram("P2P On Node Size Counts");
                    } else {
                        addHistogramLine(p2pOnNodeSizeCounts, line);
                    }
                } else if (line.startsWith("CommSizeToP2POffNodeECount")){
                    if (p2pOffNodeSizeCounts == null){
                        p2pOffNodeSizeCounts = new Histogram("P2P Off Node Size Counts");
                    } else {
                        addHistogramLine(p2pOffNodeSizeCounts, line);
                    }
                } else if (line.startsWith("ETaskTime")){
                    fields = splitLineCheck(line, "\\s+", 2);
                    int cput = new Integer(fields[1]);
                    currentTaskEvent = cput;
                } else if (line.startsWith("-----------------------------------")){
                    currentTaskEvent = INVALID_TASKID;
                } else {
                    if (currentTaskEvent != INVALID_TASKID){
                        fields = splitLineCheck(washLine(line), "\\s+", 4);
                        String type = fields[0];
                        double time = new Double(fields[2]);
                        taskEventTimes.addDetailedTime(currentTaskEvent, type, time);
                    }
                }
            }
            in.close();
        } catch (Exception e) {
            Logger.error(e, "Cannot parse psinsout file " + file);
            return;
        }
    }

}

class TaskTimes {

    private int cpuCount;

    private double[] cpuTimes;
    private double[] waitTimes;
    private double[] commTimes;
    private double[] compTimes;

    private double[] baseClocks;

    private long[] eventCounts;
    private long[] eventInserts;

    private Vector<Map<String, Double>> eTaskTimes;

    public Set<Element> toXML(Document doc){
        Set<Element> elts = Util.newHashSet();
        Element root = doc.createElement("task_details");
        elts.add(root);
        for (int i = 0; i < cpuCount; i++){
            Element e = doc.createElement("task_time");
            root.appendChild(e);

            e.setAttribute("rank", Integer.toString(i));
            e.setAttribute("events", Long.toString(eventCounts[i]));
            e.setAttribute("event_inserts", Long.toString(eventInserts[i]));
            e.setAttribute("total_timer", Format.format4d(baseClocks[i]));
            e.setAttribute("cpu_timer", Format.format4d(cpuTimes[i]));
            e.setAttribute("comm_timer", Format.format4d(commTimes[i]));
            e.setAttribute("wait_timer", Format.format4d(waitTimes[i]));
            e.setAttribute("comp_timer", Format.format4d(compTimes[i]));

            Map<String, Double> m = eTaskTimes.get(i);
            for (Iterator it = m.keySet().iterator(); it.hasNext(); ){
                String type = (String)it.next();
                double time = m.get(type);

                Element dtl = doc.createElement("task_event");
                e.appendChild(dtl);

                dtl.setAttribute("type", type);
                dtl.setAttribute("timer", Format.format4d(time));
            }
        }
        return elts;
    }

    public TaskTimes(int cpu){
        cpuCount = cpu;

        compTimes = new double[cpuCount];
        cpuTimes = new double[cpuCount];
        commTimes = new double[cpuCount];
        waitTimes = new double[cpuCount];
        baseClocks = new double[cpuCount];
        eventCounts = new long[cpuCount];
        eventInserts = new long[cpuCount];

        eTaskTimes = new Vector<Map<String, Double>>();
        eTaskTimes.setSize(cpuCount);
        for (int i = 0; i < cpuCount; i++){
            Map<String, Double> m = Util.newHashMap();
            eTaskTimes.set(i, m);
        }
    }

    public void addTaskSummary(int cpu, double comp, double comm, double wait){
        assert(cpu >= 0 && cpu < cpuCount);
        cpuTimes[cpu] = comp;
        commTimes[cpu] = comm;
        waitTimes[cpu] = wait;
    }

    public void addDetailedTime(int cpu, String type, double t){
        assert(cpu >= 0 && cpu < cpuCount);
        Map<String, Double> m = eTaskTimes.get(cpu);
        m.put(type, t);
    }

    public void addCompTime(int cpu, double t){
        assert(cpu >= 0 && cpu < cpuCount);
        compTimes[cpu] = t;
    }

    public void addBaseClock(int cpu, double clock){
        assert(cpu >= 0 && cpu < cpuCount);
        baseClocks[cpu] = clock;
    }

    public void addEventCounts(int cpu, long events, long inserts){
        assert(cpu >= 0 && cpu < cpuCount);
        eventCounts[cpu] = events;
        eventInserts[cpu] = inserts;
    }
}

class HitRateBin {
    public Integer blockCount;
    public Long memopCount;
    public Double timer;
    public Double l1hr;
    public Double l2hr;
    public Double l3hr;

    public HitRateBin(Integer bc, Long mem, Double tmr, Double l1, Double l2, Double l3){
        blockCount = bc;
        memopCount = mem;
        timer = tmr;
        l1hr = l1;
        l2hr = l2;
        l3hr = l3;
    }

    public boolean hitRateMatches(Double l1, Double l2, Double l3){
        if (l1.compareTo(l1hr) == 0 &&
            l2.compareTo(l2hr) == 0 &&
            l3.compareTo(l3hr) == 0){
            return true;
        }
        return false;
    }

    public int levelCount(){
        if (l2hr.compareTo(CacheStats.INVALID_HIT_RATE) == 0){
            return 1;
        }
        if (l3hr.compareTo(CacheStats.INVALID_HIT_RATE) == 0){
            return 2;
        }
        return 3;
    }

    public Element toXMLElement(Document doc){
        Element e = doc.createElement("bin");
        e.setAttribute("block_count", Integer.toString(blockCount));
        e.setAttribute("memop_dyn_count", Long.toString(memopCount));
        e.setAttribute("timer", Format.format4d(timer));
        e.setAttribute("l1hr", Format.format4d(l1hr));
        if (levelCount() > 1){
            e.setAttribute("l2hr", Format.format4d(l2hr));
        }
        if (levelCount() > 2){
            e.setAttribute("l3hr", Format.format4d(l3hr));
        }
        return e;
    }
}

class BinnedHitRates {

    private Map<Integer, HitRateBin> bins;

    public HitRateBin getBin(Double l1, Double l2, Double l3){
        for (Iterator it = bins.keySet().iterator(); it.hasNext(); ){
            Integer binIdx = (Integer)it.next();
            HitRateBin hrb = bins.get(binIdx);
            if (hrb.hitRateMatches(l1, l2, l3)){
                return hrb;
            }
        }
        return null;
    }

    public void addBin(Integer binIdx, Integer bbCnt, Long dynMemops, Double binTime, Double l1hr, Double l2hr, Double l3hr){
        bins.put(binIdx, new HitRateBin(bbCnt, dynMemops, binTime, l1hr, l2hr, l3hr));
    }

    public BinnedHitRates(){
        bins = Util.newHashMap();
    }

    public Set<Element> toXML(Document doc){
        Set<Element> elts = Util.newHashSet();
        Element e = doc.createElement("hit_rate_bins");
        elts.add(e);

        for (Iterator it = bins.keySet().iterator(); it.hasNext(); ){
            Integer i = (Integer)it.next();
            HitRateBin b = bins.get(i);
            e.appendChild(b.toXMLElement(doc));
        }
        return elts;
    }
}

class Histogram {
    private SortedMap<Long, Long> bins;
    private String title;

    public Histogram(String t){
        bins = new TreeMap<Long, Long>();
        title = t;
    }

    public void addBin(long key, long value){
        bins.put(key, value);
    }

    public Set<Element> toXML(Document doc){
        Set<Element> elts = Util.newHashSet();
        Element e = doc.createElement("histogram");
        elts.add(e);

        e.setAttribute("title", title);
        
        for (Iterator it = bins.keySet().iterator(); it.hasNext(); ){
            Long key = (Long)it.next();
            Long value = bins.get(key);

            Element child = doc.createElement("bin");
            e.appendChild(child);

            child.setAttribute("size", Long.toString(key));
            child.setAttribute("value", Long.toString(value));
        }
        return elts;
    }
}

class Event implements Comparable {

    public String eventType;
    public double value;

    public Event(String s, double d) {
        eventType = s;
        value = d;
    }

    public int compareTo(Object obj) {
        final Event that = (Event) obj;

        if (this.value < that.value) {
            return 1;
        } else if (this.value > that.value) {
            return -1;
        } else {
            return 0;
        }
    }
}

class CommSize {

    public long totalBytes;
    public int count;
    public double avgBytes;

    public CommSize(long t, int c, double a) {
        totalBytes = t;
        count = c;
        avgBytes = a;
    }
}

class Data {

    public int count = 1;
    public double sum;
    public double avg;
    public List values;
    
    Data(double value) {
        avg = sum = value;
        values = new ArrayList();
        values.add(new Double(value));
    }
    
    Data(int c, double s, double a, List v) {
        count = c;
        sum = s;
        avg = a;
        values = v;
    }
}

class FunctionInfo {
    public Double l1hr;
    public Double l2hr;
    public Double l3hr;

    public String name;
    public Double runTime;

    public Function func;

    public FunctionInfo(String n, Double t, Double l1, Double l2, Double l3){
        name = n;
        runTime = t;

        l1hr = l1;
        l2hr = l2;
        l3hr = l3;

        func = null;
    }

    public void setFunctionDynamic(Function f){
        func = f;
    }

    public Set<Element> toXML(Document doc, Integer sysid){
        Set<Element> elts = Util.newHashSet();
        Element e = doc.createElement("function");
        elts.add(e);

        e.setAttribute("name", LinuxCommand.cppFilt(name));
        e.setAttribute("timer", Format.format4d(runTime));
        e.setAttribute("l1hr", Format.format4d(l1hr));
        if (l2hr.compareTo(CacheStats.INVALID_HIT_RATE) != 0){
            e.setAttribute("l2hr", Format.format4d(l2hr));
        }
        if (l3hr.compareTo(CacheStats.INVALID_HIT_RATE) != 0){
            e.setAttribute("l3hr", Format.format4d(l3hr));
        }

        if (func != null){
            e.setAttribute("insn_dyn_count", Long.toString(func.dInsns));
            e.setAttribute("memop_dyn_count", Long.toString(func.dMemOps));
            e.setAttribute("fp_dyn_count", Long.toString(func.dMemOps));

            double bpm = 0.0;
            if (func.dMemOps != 0L){
                bpm = (func.dMembytes * 1.0) / (double)func.dMemOps;
            }
            e.setAttribute("memop_bytes", Format.format4d(bpm));

            e.setAttribute("file", func.file);
            e.setAttribute("lineno", Integer.toString(func.line));
            e.setAttribute("block_count", Integer.toString(func.numBlocks));
            e.setAttribute("insn_count", Integer.toString(func.insns));
            e.setAttribute("memop_count", Integer.toString(func.memOps));
            e.setAttribute("fp_count", Integer.toString(func.fpOps));
            e.setAttribute("avg_dudist_int", Format.format4d(DynamicAggregator.averageDuDist(func.intDuDistances)));
            e.setAttribute("avg_dudist_fp", Format.format4d(DynamicAggregator.averageDuDist(func.fltDuDistances)));

            if (func.perSysCaches != null){
                CacheStats c = func.perSysCaches.get(sysid);
                if (c != null){
                    e.setAttribute("l1hr", Format.format4d(c.getHitRate(1)));
                    e.setAttribute("l2hr", Format.format4d(c.getHitRate(2)));
                    e.setAttribute("l3hr", Format.format4d(c.getHitRate(3)));
                }
            }
        }

        return elts;
    }
}
