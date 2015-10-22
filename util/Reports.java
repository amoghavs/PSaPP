package PSaPP.util;

/*
Copyright (c) 2011, PMaC Laboratories, Inc.
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
import PSaPP.data.*;
import java.io.*;
import java.util.*;
import java.text.*;

public class Reports {

    final TestCase testCase;
    final String processedDir;
    final String gpuDir;

    public Reports(TestCase testCase, String processedDir) {
        this.testCase = testCase;
        this.processedDir = processedDir;
        this.gpuDir = processedDir + "/gpu_report";
    }

    public static boolean executeWriters(Collection<ReportWriter> writers) {
        for(Iterator<ReportWriter> it = writers.iterator(); it.hasNext(); ) {
            ReportWriter rw = it.next();
            Logger.inform("Writing file " + rw.getFilename());

            try {
                BufferedWriter file = new BufferedWriter(new FileWriter(rw.getFilename()));
                try {
                    rw.writeToFile(file);
                } finally {
                    file.close();
                }
            } catch (IOException e) {
                Logger.error(e, "Unable to write file " + rw.getFilename());
                return false;
            }
        }
        return true;
    }

    public ReportWriter sourceLines(final PSaPP.cfg.FlowGraph flowGraph, final int rank) {
        return new ReportWriter() {
            public String getType(){
                return "SourceLines";
            }
            public String getFilename(){
                return processedDir + "/" + testCase.shortName() + "." + "r" + rank + "." + this.getType();
            }
            public void writeToFile(BufferedWriter file) throws IOException {

                // Order functions by dInsns
                ArrayList<PSaPP.cfg.Function> functions = new ArrayList(flowGraph.getFunctions());
                Collections.sort(functions, Collections.reverseOrder(new Comparator<PSaPP.cfg.Function>() {
                    public int compare(PSaPP.cfg.Function f1, PSaPP.cfg.Function f2) {
                        return ((Integer)((Long)f1.funcInfo.dInsns).compareTo(f2.funcInfo.dInsns));
                    }
                }));

                file.write("# FunctionID\tName\tfile\tline\tdinsns\tdMemBytes\n");
                // For each function
                for(Iterator<PSaPP.cfg.Function> fit = functions.iterator(); fit.hasNext(); ) {
                    PSaPP.cfg.Function f = fit.next();

                    Map<Integer, Tuple.Tuple2<Long, Long>> sourceLines = Util.newHashMap();

                    List<PSaPP.cfg.Block> blocks = f.getBlocks();
                    for(Iterator<PSaPP.cfg.Block> bit = blocks.iterator(); bit.hasNext(); ) {
                        PSaPP.cfg.Block b = bit.next();

                        Long visits = b.blockInfo.visitCount*b.blockInfo.insns;
                        Long dMemBytes = b.blockInfo.visitCount*b.blockInfo.memBytes;

                        Tuple.Tuple2<Long, Long> oldData = sourceLines.get(b.blockInfo.line);
                        if(oldData == null) {
                            sourceLines.put(b.blockInfo.line, Tuple.newTuple2(visits, dMemBytes));
                        } else {
                            Long oldVisits = oldData.get1();
                            Long oldMemBytes = oldData.get2();
                            sourceLines.put(b.blockInfo.line, Tuple.newTuple2(visits+oldVisits, dMemBytes+oldMemBytes));
                        }
                    }

                    for(Iterator<Map.Entry<Integer, Tuple.Tuple2<Long, Long>>> mit = sourceLines.entrySet().iterator(); mit.hasNext(); ) {
                        Map.Entry<Integer, Tuple.Tuple2<Long, Long>> ment = mit.next();
                        Integer line = ment.getKey();
                        Tuple.Tuple2<Long, Long> tup = ment.getValue();
                        Long visits = tup.get1();
                        Long dMemBytes = tup.get2();

                        file.write(f.funcInfo.functionID + "\t" + f.funcInfo.functionName + "\t" + f.funcInfo.file + "\t" + line + "\t" + visits + "\t" + dMemBytes + "\n");
                    }
                }

            }
        };
    }



    private void lvWriteLoops(BufferedWriter file, Collection<PSaPP.cfg.Loop> iloops, String prefix, Long appInsns) throws IOException {
        // order loops
        ArrayList<PSaPP.cfg.Loop> loops = new ArrayList(iloops);
        Collections.sort(loops, Collections.reverseOrder(new Comparator<PSaPP.cfg.Loop>() {
            public int compare(PSaPP.cfg.Loop l1, PSaPP.cfg.Loop l2) {
                return ((Integer)((Long)l1.loopInfo.dInsns).compareTo(l2.loopInfo.dInsns));
            }
        }));

        // write loops
        for(Iterator<PSaPP.cfg.Loop> lit = loops.iterator(); lit.hasNext(); ) {
            PSaPP.cfg.Loop l = lit.next();

            double percInsns = 100.0 * ((Long)l.loopInfo.dInsns).doubleValue() / appInsns.doubleValue();
            file.write(prefix + l.loopInfo.headBlock + "\t" + percInsns + "\t" + l.loopInfo.dInsns + "\t" + l.loopInfo.entryCount + "\t" + l.countFunctionCalls() +"\n");
            // get Called functions
            List<PSaPP.cfg.Function> functions = l.getFunctions();

            lvWriteLoops(file, l.getLoops(), prefix + "\t", appInsns);
        }
    }

    public ReportWriter loopView(final PSaPP.cfg.FlowGraph flowGraph, final int rank) {
        return new ReportWriter() {
            public String getType(){
                return "LoopView";
            }
            public String getFilename(){
                return processedDir + "/" + testCase.shortName() + "." + "r" + rank + "." + this.getType();
            }
            public void writeToFile(BufferedWriter file) throws IOException {

                // Order functions by dInsns
                ArrayList<PSaPP.cfg.Function> functions = new ArrayList(flowGraph.getFunctions());
                Collections.sort(functions, Collections.reverseOrder(new Comparator<PSaPP.cfg.Function>() {
                    public int compare(PSaPP.cfg.Function f1, PSaPP.cfg.Function f2) {
                        return ((Integer)((Long)f1.funcInfo.dInsns).compareTo(f2.funcInfo.dInsns));
                    }
                }));

                // Count dInsns
                Long appInsns = 0L;
                for(Iterator<PSaPP.cfg.Function> fit = functions.iterator(); fit.hasNext(); ) {
                    PSaPP.cfg.Function f = fit.next();
                    appInsns += f.funcInfo.dInsns;
                }

                file.write("# FunctionID\tName\t%dInsns\tdInsns\tentryCount\tfCalls\n");
                file.write("# \tLoopID\t%dInsns\tdInsns\tentryCount\tfCalls\n");
                // For each function
                for(Iterator<PSaPP.cfg.Function> fit = functions.iterator(); fit.hasNext(); ) {
                    PSaPP.cfg.Function f = fit.next();

                    double percInsns = 100.0 * ((Long)f.funcInfo.dInsns).doubleValue() / appInsns.doubleValue();
                    // write function line
                    file.write(f.funcInfo.functionID + "\t" + f.funcInfo.functionName +"\t"+ percInsns +"\t"+ f.funcInfo.dInsns + "\t" + f.funcInfo.entryCount + "\t" + f.countFunctionCalls() +"\n");

                    // get top-level function calls
                    lvWriteLoops(file, f.getLoops(), "\t", appInsns);

                }

            }
        };
    }

    public ReportWriter scattergather(final PSaPP.cfg.FlowGraph flowGraph, final int rank) {
        return new ReportWriter() {
            public String getType(){
                return "ScatterGather";
            }
            public String getFilename(){
                return processedDir + "/" + testCase.shortName() + "." + "r" + rank + "." + this.getType();
            }
            public void writeToFile(BufferedWriter file) throws IOException {

                // Get all functions
                ArrayList<PSaPP.cfg.Function> functions = new ArrayList(flowGraph.getFunctions());

                // Count dInsns and collect inner loops
                Long appInsns = 0L;
                LinkedList<PSaPP.cfg.Loop> loops = Util.newLinkedList();
                for(Iterator<PSaPP.cfg.Function> fit = functions.iterator(); fit.hasNext(); ) {
                    PSaPP.cfg.Function f = fit.next();
                    appInsns += f.funcInfo.dInsns;
                    loops.addAll(f.getInnerLoops());
 
                }
                // Check for scatter-gathers
                for(Iterator<PSaPP.cfg.Loop> it = loops.iterator(); it.hasNext(); ) {
                    PSaPP.cfg.Loop l = it.next();
                    if(l.loopInfo.dScatterGatherOps == 0) {
                        it.remove();
                    }
                }
                // Sort remaining loops by dinsns
                Collections.sort(loops, Collections.reverseOrder(new Comparator<PSaPP.cfg.Loop>() {
                    public int compare(PSaPP.cfg.Loop l1, PSaPP.cfg.Loop l2) {
                        return ((Integer)((Long)l1.loopInfo.dInsns).compareTo(l2.loopInfo.dInsns));
                    }
                }));

                file.write("LoopId\tpIns\tUnrollAmount\tpOverhead\tpAppOverhead\tfunction\tfile\n");
                // Print to file
                for(Iterator<PSaPP.cfg.Loop> it = loops.iterator(); it.hasNext(); ) {
                    PSaPP.cfg.Loop l = it.next();

                    //int scattergathers = 0;
                    //int instructions = 0;
                    //int vectormaskops = 0;
                    //Collection<PSaPP.cfg.Block> blocks = l.getBlocks();
                    //for(Iterator<PSaPP.cfg.Block> bit = blocks.iterator(); bit.hasNext() ; ) {
                    //    PSaPP.cfg.Block b = bit.next();

                    //    scattergathers += b.scatterGatherOps;
                    //    instructions += b.insns;
                    //    vectormaskops += b.vectorMaskOps;
                    //}

                    double percInsns = 100.0*((Long)l.loopInfo.dInsns).doubleValue() / appInsns.doubleValue();
                    //double pscattergather = ((Long)l.loopInfo.dScatterGatherOps).doubleValue() / ((Long)l.loopInfo.dInsns).doubleValue();
                    //double IPE = ((Long)l.loopInfo.dInsns).doubleValue() / ((Long)l.loopInfo.entryCount).doubleValue();
                    double unrollAmount = ((Long)l.loopInfo.dScatterGatherOps).doubleValue() / ((Long)l.loopInfo.entryCount).doubleValue();
                    double overhead = ((Long)(l.loopInfo.dInsns - l.loopInfo.dScatterGatherOps)).doubleValue() / ((Long)l.loopInfo.dInsns).doubleValue();
                    double appOverhead = percInsns * overhead;

                    file.write(l.loopInfo.headBlock + "\t" + percInsns + "\t" + unrollAmount + "\t" +overhead+ "\t" + appOverhead + "\t" + l.loopInfo.functionName + "\t" + l.loopInfo.file + "\t" + l.loopInfo.line +"\n");
                }
            }
        };
    }
    public ReportWriter functionCalls(final PSaPP.cfg.FlowGraph flowGraph, final int rank) {
        return new ReportWriter() {
            public String getType(){
                return "FunctionCalls";
            }
            public String getFilename(){
                return processedDir + "/" + testCase.shortName() + "." + "r" + rank + "." + this.getType();
            }
            public void writeToFile(BufferedWriter file) throws IOException {

                // Order functions by dInsns
                ArrayList<PSaPP.cfg.Function> functions = new ArrayList(flowGraph.getFunctions());
                Collections.sort(functions, Collections.reverseOrder(new Comparator<PSaPP.cfg.Function>() {
                    public int compare(PSaPP.cfg.Function f1, PSaPP.cfg.Function f2) {
                        return ((Integer)((Long)f1.funcInfo.dInsns).compareTo(f2.funcInfo.dInsns));
                    }
                }));

                // Count dInsns
                Long appInsns = 0L;
                for(Iterator<PSaPP.cfg.Function> fit = functions.iterator(); fit.hasNext(); ) {
                    PSaPP.cfg.Function f = fit.next();
                    appInsns += f.funcInfo.dInsns;
                }

                file.write("# FunctionID\tName\tentryCount\tfCalls\tnumSubFuncs\n");
                file.write("# \tFunctionID\tfCallsForThisFunction\tTotalfCalls\n");
                // For each function
                for(Iterator<PSaPP.cfg.Function> fit = functions.iterator(); fit.hasNext(); ) {
                    PSaPP.cfg.Function f = fit.next();

										Set<PSaPP.cfg.Function> subFunctions = f.getUniqueFunctions();

                    // write function line
                    file.write(f.funcInfo.functionID + "\t" + f.funcInfo.functionName + "\t" + f.funcInfo.entryCount + "\t" + f.countFunctionCalls() + "\t" + subFunctions.size() +"\n");

										for(Iterator<PSaPP.cfg.Function> subFit = subFunctions.iterator(); subFit.hasNext(); ) {
											PSaPP.cfg.Function subF = subFit.next();
											int totalVisits = 0;

											Set<PSaPP.cfg.Block> callerBlocks = subF.getCallerBlocks();
											for(Iterator<PSaPP.cfg.Block> cbit = callerBlocks.iterator(); cbit.hasNext(); )
											{
												PSaPP.cfg.Block cb = cbit.next();
												if( cb.getFunction().funcInfo.functionID.equals(f.funcInfo.functionID) )
												{
													totalVisits += cb.blockInfo.visitCount;
												}
											}
											file.write("\t" + subF.funcInfo.functionName + "\t" + totalVisits + "\t" + subF.funcInfo.entryCount + "\n");
										}
							}
					}
			};	
    }

    public class AddressRangeData {
        public Long minAddr;
        public Long maxAddr;
        public Long l1hits;
        public Long l1misses;
        public Long l2misses;
        public Long l3misses;
        public Long visits;
        public int nInstructions;
    };
    public ReportWriter addressRanges(final int rank,
        final Map<BlockID, Map<Integer, AddressRanges>> ranges,
        final Map<BlockID, Map<Integer, Long>> rankVisits,
        final Map<BlockID, Map<Integer, CacheStats>> cacheStats) {
        return new ReportWriter() {
            public String getType() {
                return "addressRanges";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".r" + rank + ".addressRanges";
            }
            public void writeToFile(BufferedWriter file) throws IOException {

                file.write("Min\tMax\tRange\tVisits\tl1hits\tl1misses\tl1hr\tl2hr\tl3hr\n");
                // Collect all address range data into a list of address ranges
                List<AddressRangeData> rangeData = Util.newLinkedList();

                //   for each memop/block
                for(Iterator<BlockID> bit = ranges.keySet().iterator(); bit.hasNext(); ) {
                    BlockID bbid = bit.next();
                    Map<Integer, AddressRanges> blockRanges = ranges.get(bbid);
                    if(blockRanges == null) {
                        continue;
                    }
                    Map<Integer, Long> blockVisits = rankVisits.get(bbid);
                    Map<Integer, CacheStats> blockStats = cacheStats.get(bbid);

                    // for each thread
                    for(Iterator<Integer> tit = blockRanges.keySet().iterator(); tit.hasNext(); ) {
                        Integer thread = tit.next();
                        AddressRanges ranges = blockRanges.get(thread);
                        Long visits = blockVisits.get(thread);
                        CacheStats threadStats = blockStats.get(thread);

                        // for each instructions range
                        for(Iterator<AddressRanges.AddressRange> ait = ranges.iterator(); ait.hasNext(); ) {
                            AddressRanges.AddressRange r = ait.next();
                            AddressRangeData newRange = new AddressRangeData();
                            rangeData.add(newRange);

                            // fill in data
                            newRange.minAddr = r.min;
                            newRange.maxAddr = r.max;
                            newRange.visits = visits;

                            // This will be wrong if per-instruction tracing is not used
                            newRange.l1hits = threadStats.getHits(1);
                            newRange.l1misses = threadStats.getMisses(1);
                            newRange.l2misses = threadStats.getMisses(2);
                            newRange.l3misses = threadStats.getMisses(3);

                            newRange.nInstructions = 1;
        
                        }
                    }   
                }

                // sort by address range
                Collections.sort(rangeData, new Comparator<AddressRangeData>() {
                    public int compare(AddressRangeData a1, AddressRangeData a2) {
                        int lowRes = a1.minAddr.compareTo(a2.minAddr);
                        if(lowRes != 0) {
                            return lowRes;
                        }
                        return a1.maxAddr.compareTo(a2.maxAddr);
                    }
                });

                // merge identical address ranges
                Long prevMinAddr = null;
                Long prevMaxAddr = null;
                for(ListIterator<AddressRangeData> ait = rangeData.listIterator(); ait.hasNext(); ) {
                    AddressRangeData a = ait.next();

                    // merge if address range is same as previous
                    if(a.minAddr.equals(prevMinAddr) && a.maxAddr.equals(prevMaxAddr)) {
                        AddressRangeData prev = rangeData.get(ait.previousIndex() - 1);
                        prev.visits += a.visits;
                        prev.l1hits += a.l1hits;
                        prev.l1misses += a.l1misses;
                        prev.l2misses += a.l2misses;
                        prev.l3misses += a.l3misses;
                        prev.nInstructions = prev.nInstructions + a.nInstructions;
                        ait.remove();
                    }

                    prevMinAddr = a.minAddr;
                    prevMaxAddr = a.maxAddr;
                }
                // print address ranges
                for(Iterator<AddressRangeData> ait = rangeData.iterator(); ait.hasNext(); ) {
                    AddressRangeData a = ait.next();
                    Double l1hr = (100.0*a.l1hits) / ((Long)(a.l1hits + a.l1misses)).doubleValue();
                    Double l2hits = ((Long)(a.l1misses - a.l2misses)).doubleValue();
                    Double l2hr = (100.0*l2hits) / a.l1misses.doubleValue();
                    Double l3hits = ((Long)(a.l2misses - a.l3misses)).doubleValue();
                    Double l3hr = (100.0*l3hits) / a.l2misses.doubleValue();
                    file.write(a.minAddr + "\t" + a.maxAddr + "\t" + (a.maxAddr - a.minAddr) + "\t" + a.visits + "\t" + a.l1hits + "\t" + a.l1misses +
                        "\t" + l1hr + "\t" + l2hr + "\t" + l3hr + "\n");
                }
            }
        };
    }

    public ReportWriter bb2func(final Collection<BasicBlock> blocks) {
        return new ReportWriter() {
            public String getType(){
                return "bb2func";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".bb2func";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID\tFunction\t#\tFile:Line\n");
                for( Iterator<BasicBlock> it = blocks.iterator(); it.hasNext(); ) {
                    BasicBlock bb = it.next();
                    file.write(bb.bbid + "\t" + bb.functionName + "\t#\t" + bb.file + ":" + bb.line + "\n");
                }
            }
        };
    }

    public ReportWriter bb2Loop(final Collection<BasicBlock> blocks) {
        return new ReportWriter() {
            public String getType() {
                return "bb2loop";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".bb2loop";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID\tBlock\tLoop\tParentLoop\n");
                for(Iterator<BasicBlock> it = blocks.iterator(); it.hasNext(); ) {
                    BasicBlock bb = it.next();
                    file.write(bb.bbid + "\t" + bb.loopHead + "\t" + bb.parentLoopHead + "\n");
                }
            }
        };
    }

    public ReportWriter bbbytes(final Collection<BasicBlock> blocks) {
        return new ReportWriter() {
            public String getType(){
                return "bbbytes";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".bbbytes";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID\tAvgBytesPerMemOp\n");
                for( Iterator<BasicBlock> it = blocks.iterator(); it.hasNext(); ) {
                    BasicBlock bb = it.next();
                    file.write(bb.bbid + "\t" + (((Integer)bb.memBytes).floatValue() / bb.memOps) + "\n");
                }
            }
        };
    }

    public ReportWriter cnt(final Collection<BasicBlock> blocks) {
        return new ReportWriter() {
            public String getType(){
                return "cnt";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".cnt";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID\tInsns\tInt\tBranch\tLogic\tShiftRotate\t" +
                           "TrapSyscall\tMem\tLoad\tStore\tFp\tSpecialReg\tOther\n");
                for( Iterator<BasicBlock> it = blocks.iterator(); it.hasNext(); ) {
                    BasicBlock bb = it.next();
                    file.write(bb.bbid + "\t" + bb.insns + "\t" + bb.intOps + "\t" + bb.branchOps +
                               "\t" + bb.logicOps + "\t" + bb.shiftRotateOps + "\t" +
                               bb.trapSyscallOps + "\t" + bb.memOps + "\t" + bb.loadOps + "\t" +
                               bb.storeOps + "\t" + bb.fpOps + "\t" + bb.specialRegOps + "\t" +
                               bb.otherOps + "\n");
                }
            }
        };
    }

    public ReportWriter dxi(final Collection<BasicBlock> blocks) {
        return new ReportWriter() {
            public String getType(){
                return "dxi";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".dxi";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID DefUseCrossCount CallCount\n");
                for( Iterator<BasicBlock> it = blocks.iterator(); it.hasNext(); ) {
                    BasicBlock bb = it.next();
                    file.write(bb.bbid + "\t" + bb.defUseCrossCount + "\t" + bb.callCount + "\n");
                }
            }
        };
    }

    public ReportWriter static_(final Collection<BasicBlock> blocks) {
        return new ReportWriter() {
            public String getType(){
                return "static";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".static";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID\tMemOps\tFpOps\tInsns\tPercNonMemFp\n");
                for( Iterator<BasicBlock> it = blocks.iterator(); it.hasNext(); ) {
                    BasicBlock bb = it.next();
                    float perc = 100.0F * ((Integer)(bb.insns - bb.memOps - bb.fpOps)).floatValue() / bb.insns;
                    file.write(bb.bbid + "\t" + bb.memOps + "\t" + bb.fpOps + "\t" + bb.insns + "\t" + perc + "\n");
                }
            }
        };
    }

    public ReportWriter lp(final Collection<BasicBlock> blocks) {
        return new ReportWriter() {
            public String getType(){
                return "lp";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".lp";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID\tLoopCount\tLoopDepth\tLoopLoc\tLoopHead\tParentLoopHead\n");
                for( Iterator<BasicBlock> it = blocks.iterator(); it.hasNext(); ) {
                    BasicBlock bb = it.next();
                    file.write(bb.bbid + "\t" + bb.loopCount + "\t" + bb.loopDepth + "\t" + bb.loopLoc + 
                               "\t" + bb.loopHead + "\t" + bb.parentLoopHead + "\n");
                }
            }
        };
    }

    public ReportWriter dud(final Map<BlockID, Set<Dud>> bbidToDuds) {
        return new ReportWriter() {
            public String getType(){
                return "dud";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".dud";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID\t(dist:intCnt:fpCnt)*\n");
                List<BlockID> blockList = new ArrayList(bbidToDuds.keySet());
                Collections.sort(blockList);
                for( Iterator<BlockID> it = blockList.iterator(); it.hasNext(); ) {
                    BlockID bbid = it.next();
                    Set<Dud> duds = bbidToDuds.get(bbid);
                    List<Dud> dudList = new ArrayList(duds);
                    Collections.sort(dudList, new Comparator<Dud>() {
                        public int compare(Dud d1, Dud d2) {
                            return ((Integer)d1.dist).compareTo(d2.dist);
                        }
                    });
                    String line = bbid.toString();
                    for( Iterator<Dud> dit = dudList.iterator(); dit.hasNext(); ) {
                        Dud d = dit.next();
                        line += "\t" + d.dist + ":" + d.intcnt + ":" + d.fpcnt;
                    }
                    file.write(line + "\n");
                }
            }
        };
    }

    public ReportWriter ipa(final Collection<BasicBlock> blocks) {
        return new ReportWriter() {
            public String getType(){
                return "ipa";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".ipa";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID\tCallTargetAddress\tCallTargetName\n");
                for( Iterator<BasicBlock> it = blocks.iterator(); it.hasNext(); ) {
                    BasicBlock bb = it.next();
                    file.write(bb.bbid + "\t" + bb.callTargetAddress + "\t" + bb.callTargetName + "\n");
                }
            }
        };
    }

    public ReportWriter gpu(final Set<Function> funcs) {
        if( !Util.mkdir(gpuDir) ) {
            Logger.warn("Unable to create directory: " + gpuDir);
            return null;
        }

        return new ReportWriter() {
            public String getType(){
                return "gpu";
            }
            public String getFilename() {
                return gpuDir + "/" + testCase.shortName() + ".gpu";
            }
            public void writeToFile(BufferedWriter file) throws IOException {

                List<Function> funcList = new ArrayList<Function>(funcs);
                Collections.sort(funcList, Collections.reverseOrder(new Comparator<Function>() {
                    public int compare(Function f1, Function f2) {
                        int res = ((Long)f1.visitCount).compareTo(f2.visitCount);
                        if(res == 0) {
                            return f1.functionID.compareTo(f2.functionID);
                        } else {
                            return res;
                        }
                    }

                }));
                file.write("# Function\tFile\tLine\tNumBBs\tInsns\tMemops\tFpops\tvisitCount\n");
                for( Iterator<Function> it = funcList.iterator(); it.hasNext(); ) {
                    Function f = it.next();
                    file.write(f.functionID + "\t" + f.functionName + "\t" + f.file + "\t" + f.line + "\t" + f.numBlocks + "\t" +
                               f.insns + "\t" + f.memOps + "\t" + f.fpOps + "\t" + f.visitCount + "\n");
                }
            }
        };
    }

    // FIXME rank/sysid hardcoded in TraceReporter.java:missesPerLine
    public ReportWriter micReport(final Map<BlockID, BasicBlock> blockInfo,
                                  final Map<BlockID, CacheStats> cacheInfo,
                                  final Map<BlockID, Loop>       loopInfo,
                                  final Map<BlockID, Map<Integer, AddressRanges>> rangeInfo) {
        return new ReportWriter() {
            public String getType() {
                return "lineMisses";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".lineMisses";
            }

            public void writeToFile(BufferedWriter file) throws IOException {

                file.write("# LoopID\tLoopIns\tLpL3M\tBlockID\tL3M\tFile\tLine\tRange\tMinAddr\tMaxAddr\n");

                // LoopID  LoopIns  LpL3M  BlockID L3M File Line Range MinAdd MaxAdd
                for(Iterator<BlockID> bit = cacheInfo.keySet().iterator(); bit.hasNext(); ) {
                    BlockID bbid = bit.next();

                    BasicBlock bb = blockInfo.get(bbid);
                    Loop loop = loopInfo.get(bb.loopHead);
                    CacheStats cs = cacheInfo.get(bbid);
                    Map<Integer, AddressRanges> blockRanges = rangeInfo.get(bbid);
                    CacheStats lpcache = loop.perSysCaches.get(77);

                    if(blockRanges == null || loop == null || lpcache == null) {
                        Logger.warn("Incomplete data for block " + bbid);
                        continue;
                    }
                    long misses = cs.getMisses(3);
                    long lpmisses = lpcache.getMisses(3);
                    long lpinsns = loop.dInsns;
                    double missPI = (float)misses / (float)lpinsns;
                    double lpmissPI = (float)lpmisses / (float)lpinsns;

                    Long minAddr = null;
                    Long maxAddr = null;
                    for(Iterator<Integer> tit = blockRanges.keySet().iterator(); tit.hasNext(); ) {
                        Integer thread = tit.next();
                        AddressRanges rng = blockRanges.get(thread);
                        Long min = rng.getMin(0);
                        Long max = rng.getMax(0);
                        if(minAddr == null || min < minAddr) {
                            minAddr = min;
                        }
                        if(maxAddr == null || max > maxAddr) {
                            maxAddr = max;
                        }
                    }

                    file.write(loop.headBlock + "\t" + loop.dInsns + "\t" + lpmisses + "\t" +
                               bbid + "\t" + misses + "\t" + bb.file + "\t" + bb.line + "\t" + (maxAddr - minAddr) + "\t" + minAddr + "\t" + maxAddr + "\n");
                }

            }
        };
    }

    public Vector<ReportWriter> summaries(final Set<Integer> sysids, final Collection<Function> funcs) {
        Vector<ReportWriter> writers = Util.newVector(sysids.size());

        long dinsns = 0;
        long dmemops = 0;
        long dfpops = 0;

        for (Iterator<Function> fit = funcs.iterator(); fit.hasNext(); ){
            Function f = fit.next();
            dinsns += f.dInsns;
            dmemops += f.dMemOps;
            dfpops += f.dFpOps;
        }

        final long dynInsns = dinsns;
        final long dynMemops = dmemops;
        final long dynFpops = dfpops;

        for( Iterator<Integer> sit = sysids.iterator(); sit.hasNext(); ) {
            final int sysid = sit.next();

            writers.add(new ReportWriter() {
                public String getType(){
                    return "summary";
                }
                public String getFilename() {
                    return processedDir + "/" + testCase.shortName() + "_summary.sysid" + sysid;
                }
                public void writeToFile(BufferedWriter file) throws IOException {
                    file.write("# Application: " + testCase.getApplication() + "\n");
                    file.write("# Dataset: " + testCase.getDataset() + "\n");
                    file.write("# Core Count: " + testCase.getCpu() + "\n");
                    file.write("# Sysid: " + sysid + "\n");
                    file.write("# Dynamic Insns: " + dynInsns + "\n");
                    file.write("# Dynamic Memops: " + dynMemops + "\n");
                    file.write("# Dynamic Fpops: " + dynFpops + "\n");

                    long totL1Hits = 0L;
                    long totAccess = 0L;
                    long totL2Hits = 0L;
                    long totL3Hits = 0L;
                    for(Iterator<Function> fit = funcs.iterator(); fit.hasNext(); ) {
                        Function f = fit.next();
                        if(f.perSysCaches == null) {
                            continue;
                        }
                        CacheStats c = f.perSysCaches.get(sysid);
                        if(c == null) {
                            continue;
                        }
                        long l1hits = c.getHits(1);
                        long access = c.getMisses(1) + l1hits;
                        long l2hits = c.getHits(2);
                        long l3hits = c.getHits(3);

                        if(l1hits != CacheStats.INVALID_COUNT) {
                            totL1Hits += l1hits;
                            assert(access != CacheStats.INVALID_COUNT);
                            totAccess += access;
                        }
                        if(l2hits != CacheStats.INVALID_COUNT) {
                            totL2Hits += l2hits + l1hits;
                        }
                        if(l3hits != CacheStats.INVALID_COUNT) {
                            totL3Hits += l3hits + l2hits + l1hits;
                        }
                    }
                    double totAccessf = totAccess;
                    file.write("# Overall L1 Hit Rate: " + Format.format4d(totL1Hits / totAccessf * 100.0F) + "\n");
                    file.write("# Overall L2 Hit Rate: " + Format.format4d(totL2Hits / totAccessf * 100.0F) + "\n");
                    file.write("# Overall L3 Hit Rate: " + Format.format4d(totL3Hits / totAccessf * 100.0F) + "\n");

                    file.write("# " + Format.padRight("Function", 24) + "\tEntryCount\tBlockCount\t%ofInsns\t%Memops\t%Fpops\tDynBytes/MemOp\tintDUD\tfpDUD\tL1hr\tL2hr\tL3hr\tCacheMissPerInsns\n");

                    List<Function> funcList = new ArrayList(funcs);
                    Collections.sort(funcList, Collections.reverseOrder(new Comparator<Function>() {
                        public int compare(Function f1, Function f2) {
                            return ((Long)f1.dInsns).compareTo(f2.dInsns);
                        }
                    }));
                    for (Iterator<Function> fit = funcList.iterator(); fit.hasNext(); ){
                        Function f = fit.next();
                        if (f.dInsns == 0L){
                            continue;
                        }

                        double pins = (f.dInsns * 100.0) / (double)dynInsns;
                        double pmem = (f.dMemOps * 100.0) / (double)f.dInsns;
                        double pfp  = (f.dFpOps * 100.0) / (double)f.dInsns;
                        double bpm = 0.0;
                        if (f.dMemOps != 0L){
                            bpm = (f.dMembytes * 1.0) / (double)f.dMemOps;
                        }
                        file.write(Format.padRight(f.describe(), 24) + "\t" +
                                   Format.padRight(Long.toString(f.entryCount), 12) + "\t" + 
                                   Format.padRight(Long.toString(f.getBlockCount()), 10) + "\t" +
                                   Format.format10d(pins) + "\t" + 
                                   Format.format6d(pmem) + "\t" + 
                                   Format.format6d(pfp) + "\t" + 
                                   Format.format2d(bpm) + "\t" +
                                   Format.format2d(DynamicAggregator.averageDuDist(f.intDuDistances)) + "\t" +
                                   Format.format2d(DynamicAggregator.averageDuDist(f.fltDuDistances)));
                                   
                        if (f.perSysCaches == null){
                            file.write("\n");
                            continue;
                        }

                        CacheStats c = f.perSysCaches.get(sysid);
                        if( c == null ) {
                            file.write("\n");
                            continue;
                        }

                        long lhits = c.getHits(1);
                        float access = c.getMisses(1) + lhits;
                        long hits = 0;
                        int level = 1;
                        file.write("\t");
                        while( level <= 3 && lhits != CacheStats.INVALID_COUNT ) {

                            hits += lhits;
                            file.write("\t" + Format.format4d(hits / access * 100.0F) + "  ");

                            ++level;
                            lhits = c.getHits(level);
                        }
                        double lmisses = c.getMisses(level - 1);
                        file.write("\t" + Format.format4d(lmisses / f.dInsns));
                        file.write("\t" + lmisses * 8);
                        file.write("\n");

                    }
                }
            });
        }
        return writers;
    }

    public ReportWriter vectorization(final int rank, final Map<BlockID, BasicBlock> blocks, final Collection<Loop> loops, final Map<BlockID, Collection<VecOps>> vecops) {
        return new ReportWriter() {
            public String getType() {
                return "vectorization";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + "_" + Format.cpuToString(rank) + ".loops.vectorization";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# Loop\t%dInsns\tdInsns\tnSubLoops\tdFuncCalls\t%512bit\t%256bit\t%128bit\tvectorizations\n");

                // Count total number of dynamic instructions
                Long dynInsns = 0L;
                for(Iterator<Loop> lit = loops.iterator(); lit.hasNext(); ) {
                    Loop l = lit.next();
                    // only add outermost loops since they already contain stats for inner loops
                    if (l.headBlock.equals(l.parentHead)){
                        dynInsns += l.dInsns;
                    }
                }
                Long all512ops = 0L;
                Long all256ops = 0L;
                Long all128ops = 0L;

                // Sort loops by dynamic instructions
                List<Loop> loopList = new ArrayList(loops);
                Collections.sort(loopList, Collections.reverseOrder(new Comparator<Loop>() {
                    public int compare(Loop l1, Loop l2) {
                        return ((Long)l1.dInsns).compareTo(l2.dInsns);
                    }
                }));

                for(Iterator<Loop> lit = loopList.iterator(); lit.hasNext(); ) {
                    Loop l = lit.next();
                    if(l.dInsns == 0L) {
                        break;
                    }

                    Double percDInsns = (l.dInsns * 100.0) / (double)dynInsns;

                    long dFuncCalls = 0;
                    Map<Integer, Map<Integer, VecOps>> loopVecops = Util.newHashMap();
                    Long bitops512 = 0L;
                    Long bitops256 = 0L;
                    Long bitops128 = 0L;

                    // Collect stats for each block in loop
                    Integer nSubLoops = -1;
                    for(Iterator<BlockID> bit = l.blocks.iterator(); bit.hasNext(); ) {
                        BlockID bbid = bit.next();
                        BasicBlock bb = blocks.get(bbid);

                        if(bb.loopLoc == BasicBlock.LOOPLOC_HEAD) {
                            ++nSubLoops;
                        }

                        // accumulate function calls
                        if(bb.callTargetAddress != null && bb.callTargetAddress > 0L) {
                            dFuncCalls += bb.visitCount;
                        }

                        // accumulate vector ops
                        Collection<VecOps> bbvecops = vecops.get(bbid);
                        if(bbvecops != null) {
                            for(Iterator<VecOps> vit = bbvecops.iterator(); vit.hasNext(); ) {
                                VecOps v = vit.next();
                                Integer veclen = v.vectorLength;
                                Integer elemSize = v.elementSize;
                                long fpcnt = v.fpcnt;
                                long intcnt = v.intcnt;

                                Map<Integer, VecOps> tmp = loopVecops.get(veclen);
                                if(tmp == null) {
                                    tmp = Util.newHashMap();
                                    loopVecops.put(veclen, tmp);
                                }

                                VecOps vsum = tmp.get(elemSize);
                                if(vsum == null) {
                                    vsum = new VecOps();
                                    tmp.put(elemSize, vsum);
                                }

                                vsum.fpcnt += v.fpcnt * bb.visitCount;
                                vsum.intcnt += v.intcnt * bb.visitCount;

                                if(((Integer)(veclen*elemSize)).equals(512)) {
                                    bitops512 += v.fpcnt * bb.visitCount;
                                    bitops512 += v.intcnt * bb.visitCount;
                                } else if(((Integer)(veclen*elemSize)).equals(256)) {
                                    bitops256 += v.fpcnt * bb.visitCount;
                                    bitops256 += v.intcnt * bb.visitCount;
                                } else if(((Integer)(veclen*elemSize)).equals(128)) {
                                    bitops128 += v.fpcnt * bb.visitCount;
                                    bitops128 += v.intcnt * bb.visitCount;
                                }
                            }
                        }
                    }
                    if(l.headBlock.equals(l.parentHead)) {
                        all512ops += bitops512;
                        all256ops += bitops256;
                        all128ops += bitops128;
                    }
                    double perc128 = bitops128 * 100.0 / ((Long)l.dInsns).doubleValue();
                    double perc256 = bitops256 * 100.0 / ((Long)l.dInsns).doubleValue();
                    double perc512 = bitops512 * 100.0 / ((Long)l.dInsns).doubleValue();
                    file.write(l.headBlock + "\t" + percDInsns + "\t" + l.dInsns + "\t" + nSubLoops + "\t" + dFuncCalls + "\t" + perc512 + "\t" + perc256 + "\t" + perc128);
                    // for each vector op config

                    for(Iterator<Integer> vlit = loopVecops.keySet().iterator(); vlit.hasNext(); ) {
                        Integer vectorLength = vlit.next();
                        Map<Integer, VecOps> tmp = loopVecops.get(vectorLength);

                        for(Iterator<Integer> esit = tmp.keySet().iterator(); esit.hasNext(); ) {
                            Integer elementSize = esit.next();
                            VecOps v = tmp.get(elementSize);

                            file.write("\t" + vectorLength + "x" + elementSize + ":" + v.fpcnt + ":" + v.intcnt);
                        }
                    }

                    file.write("\n");
                }
                double perc128 = all128ops * 100.0 / dynInsns.doubleValue();
                double perc256 = all256ops * 100.0 / dynInsns.doubleValue();
                double perc512 = all512ops * 100.0 / dynInsns.doubleValue();
                file.write("App %512 bit vectors: " + perc512 + "\n");
                file.write("App %256 bit vectors: " + perc256 + "\n");
                file.write("App %128 bit vectors: " + perc128 + "\n");
                file.write("App dInsns: " + dynInsns + "\n");
            }
        };
    }

    public Vector<ReportWriter> loops(final Set<Integer> sysids, final Collection<Loop> loops) {
        Vector<ReportWriter> writers = Util.newVector(sysids.size());

        long dinsns = 0;
        long dmemops = 0;
        long dfpops = 0;

        for (Iterator lit = loops.iterator(); lit.hasNext(); ){
            Loop l = (Loop)lit.next();

            // only add outermost loops since these already contain stats for their inner loops
            if (l.headBlock.equals(l.parentHead)){
                dinsns += l.dInsns;
                dmemops += l.dMemOps;
                dfpops += l.dFpOps;
            }
        }

        final long dynInsns = dinsns;
        final long dynMemops = dmemops;
        final long dynFpops = dfpops;

        for( Iterator<Integer> sit = sysids.iterator(); sit.hasNext(); ) {
            final int sysid = (Integer)sit.next();

            writers.add(new ReportWriter() {
                public String getType(){
                    return "loops";
                }
                public String getFilename() {
                    return processedDir + "/" + testCase.shortName() + "_loops.sysid" + sysid;
                }
                public void writeToFile(BufferedWriter file) throws IOException {
                    file.write("# Application: " + testCase.getApplication() + "\n");
                    file.write("# Dataset: " + testCase.getDataset() + "\n");
                    file.write("# Core Count: " + testCase.getCpu() + "\n");
                    file.write("# Sysid: " + sysid + "\n");
                    file.write("# Dynamic Insns: " + dynInsns + "\n");
                    file.write("# Dynamic Memops: " + dynMemops + "\n");
                    file.write("# Dynamic Fpops: " + dynFpops + "\n");

                    long totL1Hits = 0L;
                    long totAccess = 0L;
                    long totL2Hits = 0L;
                    long totL3Hits = 0L;
                    for(Iterator<Loop> lit = loops.iterator(); lit.hasNext(); ) {
                        Loop l = lit.next();
                        if(l.perSysCaches == null) {
                            continue;
                        }
                        CacheStats c = l.perSysCaches.get(sysid);
                        if(c == null) {
                            continue;
                        }
                        long l1hits = c.getHits(1);
                        long access = c.getMisses(1) + l1hits;
                        long l2hits = c.getHits(2);
                        long l3hits = c.getHits(3);

                        if(l1hits != CacheStats.INVALID_COUNT) {
                            totL1Hits += l1hits;
                            assert(access != CacheStats.INVALID_COUNT);
                            totAccess += access;
                        }
                        if(l2hits != CacheStats.INVALID_COUNT) {
                            totL2Hits += l1hits + l2hits;
                        }
                        if(l3hits != CacheStats.INVALID_COUNT) {
                            totL3Hits += l1hits + l2hits + l3hits;
                        }
                        
                    }
                    double totAccessf = totAccess;
                    file.write("# Overall L1 Hit Rate: " + Format.format4d(totL1Hits / totAccessf * 100.0F) + "\n");
                    file.write("# Overall L2 Hit Rate: " + Format.format4d(totL2Hits / totAccessf * 100.0F) + "\n");
                    file.write("# Overall L3 Hit Rate: " + Format.format4d(totL3Hits / totAccessf * 100.0F) + "\n");

                    file.write("# " + Format.padRight("Loop", 16) + "\t" + Format.padRight("ParentLoop", 16) + "\t" + Format.padRight("Function", 16) + "\t" + Format.padRight("File", 16) + "\tLine \tEntryCount  \tBlockCount\tDepth\t%ofInsns\t%Memops \t%Fpops  \tDynBytes/MemOp\tintDUD\tfpDUD\tL1hr\t\tL2hr\t\tL3hr\n");

                    List<Loop> loopList = new ArrayList(loops);
                    Collections.sort(loopList, Collections.reverseOrder(new Comparator<Loop>() {
                        public int compare(Loop l1, Loop l2) {
                            return ((Long)l1.dInsns).compareTo(l2.dInsns);
                        }
                    }));
                    for (Iterator<Loop> lit = loopList.iterator(); lit.hasNext(); ){
                        Loop l = lit.next();

                        // only report on loops that are executed
                        if (l.dInsns == 0L){
                            continue;
                        }

                        double pins = (l.dInsns * 100.0) / (double)dynInsns;
                        double pmem = (l.dMemOps * 100.0) / (double)l.dInsns;
                        double pfp  = (l.dFpOps * 100.0) / (double)l.dInsns;
                        double bpm = 0.0;
                        if (l.dMemOps != 0L){
                            bpm = (l.dMembytes * 1.0) / (double)l.dMemOps;
                        }

                        // probably want some more info here: block count, loop depth, file/line
                        assert(l.functionName != null);
                        file.write(Format.padRight(l.describe(), 16) + "\t" + 
                                   Format.padRight(l.parentHead.shortString(), 16) + "\t" + 
                                   Format.padRight(l.functionName, 16) + "\t" + 
                                   Format.padRight(l.file, 16) + "\t" + 
                                   Format.padRight(Integer.toString(l.line), 5) + "\t" + 
                                   Format.padRight(Long.toString(l.entryCount), 12) + "\t" + 
                                   Format.padRight(Long.toString(l.getBlockCount()), 10) + "\t" +
                                   l.depth + "\t" + 
                                   Format.format10d(pins) + "\t" + 
                                   Format.format6d(pmem) + "\t" + 
                                   Format.format6d(pfp) + "\t" + 
                                   Format.format2d(bpm) + "\t" +
                                   Format.format2d(DynamicAggregator.averageDuDist(l.intDuDistances)) + "\t" +
                                   Format.format2d(DynamicAggregator.averageDuDist(l.fltDuDistances)));

                        if (l.perSysCaches == null){
                            file.write("\n");
                            continue;
                        }

                        CacheStats c = l.perSysCaches.get(sysid);
                        if( c == null ) {
                            file.write("\n");
                            continue;
                        }

                        long lhits = c.getHits(1);
                        float access = c.getMisses(1) + lhits;
                        long hits = 0;
                        int level = 1;
                        file.write("\t");
                        while( level <= 3 && lhits != CacheStats.INVALID_COUNT ) {

                            hits += lhits;
                            file.write("\t" + Format.format4d(hits / access * 100.0F) + "  ");

                            ++level;
                            lhits = c.getHits(level);
                        }
                        file.write("\n");

                    }
                }
            });
        }
        return writers;
    }

    public ReportWriter rangeSizeByRank(final int rank, final Map<BlockID, AddressRanges> blockAddressRanges){
        return new ReportWriter(){
            public String getType(){
                return "rangeSizeByRank";
            }
            public String getFilename(){
                return processedDir + "/dfp/" + testCase.shortName() + "_" + 
                       Format.cpuToString(rank) + ".rngsize";
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID\tRangeSize\n");

                Iterator<BlockID> it = blockAddressRanges.keySet().iterator();
                while (it.hasNext()){
                    BlockID bbid = it.next();
                    AddressRanges ranges = blockAddressRanges.get(bbid);

                    if (ranges == null || ranges.getNumberOfRanges() == 0){
                        continue;
                    }
                    file.write(bbid + "\t" + ranges.overallRangeSize() + "\n");
                }
            }
        };
    }


    public ReportWriter sysidByRank(final int sysid, final int rank, final Set<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> blocks) {
        return new ReportWriter() {
            public String getType(){
                return "sysidByRank";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + "_" +
                       Format.cpuToString(rank) + ".sysid" + sysid;
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# BBID\tDynFpops\tDynMemops\tL1\tL2\tL3\n");

                List<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> blockList = new ArrayList<Tuple.Tuple4<BlockID, Long, Long, CacheStats>>(blocks);
                Collections.sort(blockList, new Comparator<Tuple.Tuple4<BlockID, Long, Long, CacheStats>>() {
                    public int compare(Tuple.Tuple4<BlockID, Long, Long, CacheStats> b1, Tuple.Tuple4<BlockID, Long, Long, CacheStats> b2) {
                        BlockID bid1 = b1.get1();
                        BlockID bid2 = b2.get1();
                        return bid1.compareTo(bid2);
                    }
                });
                for( Iterator<Tuple.Tuple4<BlockID, Long, Long, CacheStats>> bit = blockList.iterator();
                     bit.hasNext(); ) {
                    Tuple.Tuple4<BlockID, Long, Long, CacheStats> binfo = bit.next();

                    BlockID bbid = binfo.get1();
                    Long fpops = binfo.get2();
                    Long memops = binfo.get3();
                    CacheStats c = binfo.get4();

                    if( c == null ) {
                        continue;
                    }

                    file.write(bbid + "\t" + fpops + "\t" + memops);
                    long lhits = c.getHits(1);
                    float access = c.getMisses(1) + lhits;
                    long hits = 0;
                    int l = 1;
                    while( l <= 3 && lhits != CacheStats.INVALID_COUNT ) {

                        hits += lhits;
                        file.write("\t" + (hits / access * 100.0F));

                        ++l;
                        lhits = c.getHits(l);
                    }
                    file.write("\n");
                }
            }
        };
    }

   public ReportWriter vector(final int rank,
                              final int sysid,
                              final Map<BlockID, BasicBlock> blockStatic,
                              final Map<BlockID, Set<Dud>> duds,
                              final Map<BlockID, Long> blockVisits,
                              final Map<BlockID, Long> loopVisits,
                              final Map<BlockID, Map<Integer, AddressRanges>> addressRanges,
                              final Map<BlockID, List<BinnedCounter>> reuseStats,
                              final Map<BlockID, List<BinnedCounter>> spatialStats,
                              final Map<BlockID, CacheStats> cacheStats,
                              final Map<BlockID, Set<BlockID>> aggregation,
                              final String agType,
                              final Map<BlockID, Collection<VecOps>> vecops) {
        return new ReportWriter() {
            public String getType(){
                return agType + ".vector";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + "_" + Format.cpuToString(rank) + "." + agType + ".vector." + sysid;
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# HashCode\tdInsns\tIntOps\tMemOps\tLoadOps\tStoreOps\tFpOps\tBranchOps\t" +
                           "pIntOps\tpMemOps\tpLoadOps\tpStoreOps\tpFpOps\tpBranchOps\t" +
                           "L1HR\tL2HR\tL3HR\tL1Misses\tL2Misses\tL3Misses\tIntDud\tFpDud\tidu2\tfdu2\tmdu2\tFpRat\tL1MPI\tL2MPI\tL3MPI\t" +
                           "Filename\tLine\tFunction\t" +
                           "BytesPerMemOp\tReuse\tSpatial\tLowAddress\tHighAddress\tRange\t" +
                           "pvec64\tbitops64\tpvec128\tbitops128\tpvec256\tbitops256\tpvec512\tbitops512\tbin0\tbin2\tbin4\tbin8\tbin16\tbin32\tbin64\tbin128\n");

                for(Iterator<BlockID> ait = aggregation.keySet().iterator(); ait.hasNext(); ) {
                    BlockID headBlock = ait.next();
                    Set<BlockID> aggset = aggregation.get(headBlock);

                    // Report vector for aggregation of in this set
                    Long dInsns = 0L;
                    Long intOps = 0L;
                    Long memOps = 0L;
                    Long loadOps = 0L;
                    Long storeOps = 0L;
                    Long fpOps = 0L;
                    Long branchOps = 0L;
                    Long l1hits = 0L;
                    Long l1miss = 0L;
                    Long l2hits = 0L;
                    Long l2miss = 0L;
                    Long l3hits = 0L;
                    Long l3miss = 0L;
                    Long membytes = 0L;
                    Double idud = 0.0;
                    Double fdud = 0.0;
                    Double mdud = 0.0;
                    Double idud_cnt = 0.0;
                    Double fdud_cnt = 0.0;
                    Double mdud_cnt = 0.0;

                    Long lowAddr = null;
                    Long highAddr = null;
                    
                    String funcname = null;
                    String filename = null;
                    Integer lineno = null;

                    // *** Spatial locality and vectorization 
                    Long totalRefs = 0L; // total all inst
                    double bin0=0.0;  //V2
                    double bin2=0.0;  //V3
                    double bin4=0.0;  //V4
                    double bin8=0.0;  //V5
                    double bin16=0.0; //V6
                    double bin32=0.0; //V7
                    double bin64=0.0; //V8
                    double bin128=0.0; //V9
                    long[] dudFP={0,0,0,0,0,0,0,0,0};
                    long[] dudINT={0,0,0,0,0,0,0,0,0};
                    Double spatialScore = 0.0;
                    Long bitops512 = 0L;
                    Long bitops256 = 0L;
                    Long bitops128 = 0L;
                    Long bitops64  = 0L;

                    for(Iterator<BlockID> bit = aggset.iterator(); bit.hasNext(); ) {
                        BlockID bbid = bit.next();

                        BasicBlock bb = blockStatic.get(bbid);
                        assert(bb!=null);
                        if(funcname == null) {
                            funcname = bb.functionName;
                            filename = bb.file;
                            lineno = bb.line;
                        }
                        Set<Dud> blockDuds = duds.get(bbid);
                        Long visits = 1L; // FIXME turn this into a flag, this makes it static stats
                        //Long visits = blockVisits.get(bbid);
                        if(visits == null)
                            visits = 0L;

                        dInsns += bb.insns * visits;
                        intOps += bb.intOps * visits;
                        memOps += bb.memOps * visits;
                        loadOps += bb.loadOps * visits;
                        storeOps += bb.storeOps * visits;
                        fpOps += bb.fpOps * visits;
                        branchOps += bb.branchOps * visits;
                        membytes += bb.memBytes * visits;

                        Map<Integer, AddressRanges> perThreadRanges = addressRanges.get(bbid);
                        //List<BinnedCounter> blockReuse = reuseStats.get(bbid);
                        List<BinnedCounter> blockSpatial;
                        if(spatialStats == null) {
                            blockSpatial = null;
                        } else {
                            blockSpatial = spatialStats.get(bbid);
                        }
                        
                        CacheStats cs;
                        if(cacheStats == null) {
                            cs = null;
                        } else {
                            cs = cacheStats.get(bbid);
                        }

                        if(cs != null) {
                            if(cs.getMisses(1) != CacheStats.INVALID_COUNT)
                                l1miss += cs.getMisses(1);
                            if(cs.getMisses(2) != CacheStats.INVALID_COUNT)
                                l2miss += cs.getMisses(2);
                            if(cs.getMisses(3) != CacheStats.INVALID_COUNT)
                                l3miss += cs.getMisses(3);

                            if(cs.getHits(1) != CacheStats.INVALID_COUNT)
                                l1hits += cs.getHits(1);
                            if(cs.getHits(2) != CacheStats.INVALID_COUNT)
                                l2hits += cs.getHits(2);
                            if(cs.getHits(3) != CacheStats.INVALID_COUNT)
                                l3hits += cs.getHits(3);
                        }

                        if(blockDuds != null) {
                            for(Iterator<Dud> dit = blockDuds.iterator(); dit.hasNext(); ) {
                                Dud dud = dit.next();

                                //Logger.inform ("what is the dud" + dud.dist); 
                                //LAURA OLD VERSION   idud += dud.dist*dud.intcnt;
                                //LAURA OLD VERSION   fdud += dud.dist*dud.fpcnt;
                                idud += dud.dist*dud.intcnt;  idud_cnt += dud.intcnt;
                                fdud += dud.dist*dud.fpcnt;   fdud_cnt += dud.fpcnt;
                                mdud += dud.dist*dud.memcnt;  mdud_cnt += dud.memcnt;


                                // FP duds
                                  if (dud.dist <= 1){ dudFP[0] += dud.fpcnt;}
                                  if (dud.dist == 2){ dudFP[1] += dud.fpcnt;}
                                  if (dud.dist == 3){ dudFP[2] += dud.fpcnt;}
                                  if (dud.dist == 4){ dudFP[3] += dud.fpcnt;}
                                  if (dud.dist == 5){ dudFP[4] += dud.fpcnt;}
                                  if (dud.dist == 6){ dudFP[5] += dud.fpcnt;}
                                  if (dud.dist == 7){ dudFP[6] += dud.fpcnt;}
                                  if (dud.dist == 8){ dudFP[7] += dud.fpcnt;}
                                  if (dud.dist >= 9){ dudFP[8] += dud.fpcnt;}

                                // INT duds
                                  if (dud.dist <= 1){ dudINT[0] += dud.intcnt;}
                                  if (dud.dist == 2){ dudINT[1] += dud.intcnt;}
                                  if (dud.dist == 3){ dudINT[2] += dud.intcnt;}
                                  if (dud.dist == 4){ dudINT[3] += dud.intcnt;}
                                  if (dud.dist == 5){ dudINT[4] += dud.intcnt;}
                                  if (dud.dist == 6){ dudINT[5] += dud.intcnt;}
                                  if (dud.dist == 7){ dudINT[6] += dud.intcnt;}
                                  if (dud.dist == 8){ dudINT[7] += dud.intcnt;}
                                  if (dud.dist >= 9){ dudINT[8] += dud.intcnt;}

                            }
                        }

                        // ******VECTORIZATION SCORE ******
                        Map<Integer, Map<Integer, VecOps>> loopVecops = Util.newHashMap();

                        Collection<VecOps> bbvecops = vecops.get(bbid);

                        // accumulate vector ops
                        if(bbvecops != null) {
                             for(Iterator<VecOps> vit = bbvecops.iterator(); vit.hasNext(); ) {
                                   VecOps v = vit.next();
                                    Integer veclen = v.vectorLength;
                                    Integer elemSize = v.elementSize;
                                    long fpcnt = v.fpcnt;
                                    long intcnt = v.intcnt;

                                    if(((Integer)(veclen*elemSize)).equals(256)) {
                                         bitops256 += fpcnt * visits;
                                         bitops256 += intcnt * visits;
                                    } else if(((Integer)(veclen*elemSize)).equals(128)) {
                                         bitops128 += fpcnt * visits;
                                         bitops128 += intcnt * visits;
                                    } else if(((Integer)(veclen*elemSize)).equals(512)) {
                                         bitops512 += fpcnt * visits;
                                         bitops512 += intcnt * visits;
                                    } else if(veclen > 1 && ((Integer)(veclen*elemSize)).equals(64)) {
                                         bitops64 += fpcnt * visits;
                                         bitops64 += intcnt * visits;
                                    }

                               }
                         }

                        // ******SPATIAL SCORE  *******
                        if(blockSpatial != null) {
                            //Sum all ref for instruction to get total number of refs for this instruction
                            for(Iterator<BinnedCounter> bcit = blockSpatial.iterator(); bcit.hasNext(); ) {
                                BinnedCounter bc = bcit.next();
                                totalRefs += bc.counter;
                                if (bc.upperBound.doubleValue() == 0){
                                     bin0 +=bc.counter.doubleValue();
                                }else if(bc.upperBound.doubleValue() == 2){
                                     bin2 +=bc.counter.doubleValue();
                                }else if(bc.upperBound.doubleValue() == 4){
                                     bin4 +=bc.counter.doubleValue();
                                }else if(bc.upperBound.doubleValue() == 8){
                                     bin8 +=bc.counter.doubleValue();
                                }else if(bc.upperBound.doubleValue() == 16){
                                     bin16 +=bc.counter.doubleValue();
                                }else if(bc.upperBound.doubleValue() == 32){
                                     bin32 +=bc.counter.doubleValue();
                                }else if(bc.upperBound.doubleValue() == 64){
                                     bin64 +=bc.counter.doubleValue();
                                }else if(bc.upperBound.doubleValue() == 128){
                                     bin128 +=bc.counter.doubleValue();
                                }
                            } //END for-loop all bins

                        }//if spatial != null


                        // ************* DATA RANGES **************
                        if(perThreadRanges != null) {
                            for(Iterator<Integer> tit = perThreadRanges.keySet().iterator(); tit.hasNext(); ) {
                                Integer thread = tit.next();
                                AddressRanges ranges = perThreadRanges.get(thread);
                                Long min = ranges.overallMin();
                                Long max = ranges.overallMax();

                                if(lowAddr == null || min < lowAddr) {
                                    lowAddr = min;
                                }
                                if(highAddr == null || max > highAddr) {
                                    highAddr = max;
                                }
                            }
                        }
                    } //END looping through all BBids/Instructions/aggregators

                    // ******* Calc all Aggregation values ****************
                    //

                    // **************** CACHE hitrates Aggregation values ******************
                    Double l1hr, l2hr, l3hr, l1mpi, l2mpi, l3mpi;
                    if(l1miss + l1hits != 0)
                        l1hr = l1hits.doubleValue() / ((Long)(l1hits+l1miss)).doubleValue() * 100.0;
                    else
                        l1hr = 100.0;
                    if(l2miss + l2hits != 0)
                        l2hr = ((Long)(l1hits+l2hits)).doubleValue() / ((Long)(l1hits+l1miss)).doubleValue() * 100.0;
                    else
                        l2hr = 100.0;
                    if(l3miss + l3hits != 0)
                        l3hr = ((Long)(l1hits+l2hits+l3hits)).doubleValue() / ((Long)(l1hits+l1miss)).doubleValue() * 100.0;
                    else
                        l3hr = 100.0;

                    l1mpi = l1miss.doubleValue() / dInsns.doubleValue();
                    l2mpi = l2miss.doubleValue() / dInsns.doubleValue();
                    l3mpi = l3miss.doubleValue() / dInsns.doubleValue();

                    Double fprat = 0.0;
                    if(fpOps.equals(0L)) {
                        fprat = 1.0 / 16.0;
                    } else if(memOps.equals(0L)) {
                        fprat = 16.0;
                    } else {
                        fprat = fpOps.doubleValue() / memOps.doubleValue();
                    }

                    Double bytesPerMop = membytes.doubleValue() / memOps.doubleValue();

                    // ******** VECTORIZATION SCORE ***************
                    double perc512 = 0.0;
                    double perc256 = 0.0;
                    double perc128 = 0.0;
                    double perc64  = 0.0;
                    if(dInsns > 0L) {
                        perc64  = 100.0*bitops64  / dInsns.doubleValue();
                        perc128 = 100.0*bitops128 / dInsns.doubleValue();
                        perc256 = 100.0*bitops256 / dInsns.doubleValue();
                        perc512 = 100.0*bitops512 / dInsns.doubleValue();
                    } else {
                       perc64  = 0.0;
                       perc128 = 0.0;
                       perc256 = 0.0;
                       perc512 = 0.0;
                    }


                    // ******** SPATIAL SCORE **************
                    //   Calculated from Amogha's kernels set 2
                       //spatialScore = 0.0; 
                       if (totalRefs > 0 ){  //checking that had spatial trace
                          // DOUBLES with vectorization
                          Double vecPerc = perc128 + perc256 + perc512;
                          if ( (vecPerc > 0.05) && (bytesPerMop >= 6 ) ){
                              if (bin16/totalRefs < 9.23/100.0 ) {
                                  spatialScore = 1.95;
                              }else if ( (bin16/totalRefs >= 9.23/100.0 ) && (bin32/totalRefs < 12.68/100.0 ) ) {
                                  spatialScore = 2.02;
                              }else if ( (bin16/totalRefs >= 9.23/100.0 ) && (bin32/totalRefs >= 12.68/100.0  )
                              && (bin128/totalRefs >= 4.92/100.0  )  ){
                                  spatialScore = 2.15;
                              }else if ( (bin16/totalRefs >= 9.23/100.0 ) && (bin32/totalRefs >= 12.68/100.0  )
                              && (bin128/totalRefs < 4.92/100.0  )  ) {
                                  spatialScore = 2.30;

                              }
                          // DOUBLES with NO vectorization
                          }else if ( (vecPerc <= 0.05) && (bytesPerMop >= 6 ) ){
                              if ( (bin64/totalRefs > 12.21/100.0 ) && (bin64/totalRefs < 49.5/100.0  ) ) {
                                  spatialScore = 1.96;
                              }else if ( bin64/totalRefs >= 49.5/100.0  ) {
                                  spatialScore = 2.11;
                              }else if ( (bin64/totalRefs <= 12.21/100.0 ) && (bin32/totalRefs >= 29.15/100.0  ) ){
                                  spatialScore = 2.14;
                              }else if ( (bin64/totalRefs <= 12.21/100.0 ) && (bin32/totalRefs < 29.15/100.0  ) ){
                                  spatialScore = 2.39;

                              }
                          // FLOATS & INT with vectorization
                          }else if( (vecPerc > 0.05) &&  (bytesPerMop < 6 ) ){ //FLOATS or INTS with vectorization
                              if ( (bin32/totalRefs >= 24.48/100.0 ) && (bin16/totalRefs < 2.68/100.0 ) ){
                                  spatialScore = 1.98;
                              }else if ( (bin32/totalRefs >= 24.48/100.0 ) && (bin16/totalRefs >= 2.68/100.0 )
                              && (bin128/totalRefs >= 12.48/100.0 ) ){
                                  spatialScore = 2.00;
                              }else if ( (bin32/totalRefs >= 24.48/100.0 ) && (bin16/totalRefs >= 2.68/100.0 )
                              && (bin128/totalRefs < 12.48/100.0 ) ){
                                  spatialScore = 2.14;
                              }else if ( (bin32/totalRefs < 24.48/100.0 ) && (bin16/totalRefs >= 55.77/100.0 )){
                                  spatialScore = 2.19;
                              }else if ( (bin32/totalRefs < 24.48/100.0 ) && (bin16/totalRefs < 55.77/100.0 )
                              && (bin64/totalRefs >= 5.70/100.0 ) ){
                                  spatialScore = 2.33;
                              }else if ( (bin32/totalRefs < 24.48/100.0 ) && (bin16/totalRefs < 55.77/100.0 )
                              && (bin64/totalRefs < 5.70/100.0 ) ){
                                  spatialScore = 2.49;

                              }
                          // FLOATS & INT with NO vectorization
                          }else if( (vecPerc <= 0.05) &&  (bytesPerMop < 6 ) ){ //FLOATS or INTS
                              if ( (bin32/totalRefs >= 24.61/100.0 ) && (bin32/totalRefs < 49.87/100.0 ) ){
                                  spatialScore = 2.16;
                              }else if (bin32/totalRefs >= 49.87/100.0 ) {
                                  spatialScore = 2.01;
                              }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs < 49.39/100.0 )
                              && ( bin16/totalRefs >= 49.68/100.0 )){
                                  spatialScore = 2.24;
                              }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs < 49.39/100.0 )
                              && ( bin16/totalRefs < 49.68/100.0 )
                                 && ( bin16/totalRefs < 12.21/100.0 ) && ( bin8/totalRefs < 24.98/100.0 ) ){
                                  spatialScore = 2.28;
                              }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs < 49.39/100.0 )
                              && ( bin16/totalRefs < 49.68/100.0 )
                                 && ( bin16/totalRefs < 12.21/100.0 ) && ( bin8/totalRefs >= 24.98/100.0 ) ){
                                  spatialScore = 2.37;
                              }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs < 49.39/100.0 )
                              && ( bin16/totalRefs < 49.68/100.0 )
                                 && ( bin16/totalRefs >= 12.21/100.0 ) && ( bin8/totalRefs < 12.05/100.0 ) ){
                                  spatialScore = 2.39;
                              }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs < 49.39/100.0 )
                              && ( bin16/totalRefs < 49.68/100.0 )
                                 && ( bin16/totalRefs >= 12.21/100.0 ) && ( bin8/totalRefs >= 12.05/100.0 ) ){
                                  spatialScore = 2.47;
                              }else if ( (bin32/totalRefs < 24.61/100.0 ) && ( bin8/totalRefs >= 49.39/100.0 ) ){
                                  spatialScore = 2.49;
                              }else {
                                  Logger.warn("MISSED A SPATIAL SCORE =" + headBlock );
                              }
                          }//end if then for double, floats, ints & vectorization
                       } else { //NO SPATIAL TRACE
                          spatialScore = 0.0;
                       } //end if then for totalRef > 0

                    // ************* DUDs *****************

                    //LAURA OLD VERSION  idud = idud / dInsns.doubleValue();
                    //LAURA OLD VERSION  fdud = fdud / dInsns.doubleValue();

                    // score = SUM(i.visits * i.distance) *  dInsns
                    //         --------------------------    ------
                    //              SUM(i.visits)          SUM(i.visits)
                    Double idud2 = (idud * dInsns) / (idud_cnt*idud_cnt + 1);
                    Double fdud2 = (fdud * dInsns) / (fdud_cnt*fdud_cnt + 1);
                    Double mdud2 = (mdud * dInsns) / (mdud_cnt*mdud_cnt + 1);

                    idud = idud*idud_cnt;
                    fdud = fdud/fdud_cnt;


                    Long addRange = null;
                    if(highAddr != null && lowAddr != null)
                        addRange = highAddr - lowAddr;

                    Double reuseScore = 0.0;

                    Double pIntOps = 100.0 * storeOps / dInsns;
                    Double pMemOps = 100.0 * memOps / dInsns;
                    Double pLoadOps = 100.0 * loadOps / dInsns;
                    Double pStoreOps = 100.0 * storeOps / dInsns;
                    Double pFpOps = 100.0 * fpOps / dInsns;
                    Double pBranchOps = 100.0 * branchOps / dInsns;

                    DecimalFormat f = new DecimalFormat("0.0000000000000");

                    file.write(headBlock + "\t" + dInsns + "\t" + intOps + "\t" + memOps + "\t" + loadOps + "\t" +
                               storeOps + "\t" + fpOps + "\t" + branchOps + "\t" +
                               pIntOps + "\t" + pMemOps + "\t" + pLoadOps + "\t" + pStoreOps + "\t" + pFpOps + "\t" + pBranchOps + "\t" +
                               l1hr + "\t" + l2hr + "\t" + l3hr + "\t" + l1miss + "\t" + l2miss + "\t" + l3miss + "\t" +
                               idud + "\t" + fdud + "\t" + idud2 + "\t" + fdud2 + "\t" + mdud2 + "\t" + fprat + "\t" + f.format(l1mpi) + "\t" + f.format(l2mpi) + "\t" + f.format(l3mpi) + "\t" +
                               filename + "\t" + lineno + "\t" + funcname + "\t" +
                               bytesPerMop + "\t" + reuseScore + "\t" + spatialScore + "\t" + lowAddr + "\t" + highAddr + "\t" +
                               addRange + "\t" + perc64 + "\t" + bitops64 + "\t" + perc128 + "\t" + bitops128 + "\t" + perc256 + "\t" + bitops256 + "\t" + perc512 + "\t" + bitops512 +
                               "\t" + bin0 + "\t" + bin2 + "\t" + bin4 + "\t" + bin8 + "\t" + bin16 +
                               "\t" + bin32 + "\t" + bin64 +  "\t" + bin128 + "\n");

                }
            }
        };
    }

   /* BasicBlocks
    * Duds
    * BlockVisitCounts
    * AddressRanges
    * Functions
    * ReuseStats
    * SpatialStats
    */
    public ReportWriter instructions(final int rank,
                                     final int sysid,
                                     final Map<BlockID, BasicBlock> blockStatic,
                                     final Map<BlockID, Set<Dud>> duds,
                                     final Map<BlockID, Long> blockVisits,
                                     final Map<BlockID, Long> loopVisits,
                                     final Map<BlockID, Map<Integer, AddressRanges>> addressRanges,
                                     final Map<BlockID, List<BinnedCounter>> reuseStats,
                                     final Map<BlockID, List<BinnedCounter>> spatialStats,
                                     final Map<BlockID, CacheStats> cacheStats) {
        return new ReportWriter() {
            public String getType(){
                return "instructions";
            }
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + "_" + Format.cpuToString(rank) + ".instructions.vector." + sysid;
            }
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("# HashCode\tdInsns\tIntOps\tMemOps\tLoadOps\tStoreOps\tFpOps\t" +
                           "L1HR\tL2HR\tL3HR\tL1Misses\tL2Misses\tL3Misses\tIntDud\tFpDud\tFpRat\tL1MPI\tL2MPI\tL3MPI\t" +
                           "Filename\tLine\tFunction\t" +
                           "BytesPerMemOp\tReuse\tSpatial\tLowAddress\tHighAddress\tRange\n");

                //assert(spatialStats.size() > 0);
                //assert(reuseStats.size() > 0);
                for(Iterator<BlockID> bit = blockStatic.keySet().iterator(); bit.hasNext(); ) {
                    BlockID bbid = bit.next();
                    BasicBlock bb = blockStatic.get(bbid);
                    Set<Dud> blockDuds = duds.get(bbid);
                    Long visits = blockVisits.get(bbid);
                    if(visits == null)
                        visits = 0L;
                    Map<Integer, AddressRanges> perThreadRanges = addressRanges.get(bbid);
                    //List<BinnedCounter> blockReuse = reuseStats.get(bbid);
                    //List<BinnedCounter> blockSpatial = spatialStats.get(bbid);

                    Long dInsns = bb.insns*visits;
                    Long intOps = bb.intOps*visits;
                    Long memOps = bb.memOps*visits;
                    Long loadOps = bb.loadOps*visits;
                    Long storeOps = bb.storeOps*visits;
                    Long fpOps = bb.fpOps*visits;

                    CacheStats cs;
                    if(cacheStats == null) {
                        cs = null;
                    } else {
                        cs = cacheStats.get(bbid);
                    }
                    Long l1miss, l2miss, l3miss, l1hit, l2hit, l3hit;
                    Double l1hr, l2hr, l3hr, l1mpi, l2mpi, l3mpi;
                    if(cs != null) {
                        l1miss = cs.getMisses(1);
                        l2miss = cs.getMisses(2);
                        l3miss = cs.getMisses(3);
                        l1hit = cs.getHits(1);
                        l2hit = cs.getHits(2);
                        l3hit = cs.getHits(3);

                        l1hr = l1hit.doubleValue() / ((Long)(l1hit+l1miss)).doubleValue() * 100.0;
                        l2hr = ((Long)(l1hit+l2hit)).doubleValue() / ((Long)(l1hit+l1miss)).doubleValue() * 100.0;
                        l3hr = ((Long)(l1hit+l2hit+l3hit)).doubleValue() / ((Long)(l1hit+l1miss)).doubleValue() * 100.0;

                        l1mpi = l1miss.doubleValue() / dInsns.doubleValue();
                        l2mpi = l2miss.doubleValue() / dInsns.doubleValue();
                        l3mpi = l3miss.doubleValue() / dInsns.doubleValue();
                        
                    } else {
                        l1miss = l2miss = l3miss = l1hit = l2hit = l3hit = null;
                        l1hr = l2hr = l3hr = l1mpi = l2mpi = l3mpi = null;
                    }

                    Double idud = 0.0;
                    Double fdud = 0.0;
                    if(dInsns == 0 || blockDuds == null) {
                        idud = fdud = 0.0;
                    } else {
                        for(Iterator<Dud> dit = blockDuds.iterator(); dit.hasNext(); ) {
                            Dud dud = dit.next();
                            idud += dud.dist*dud.intcnt;
                            fdud += dud.dist*dud.fpcnt;
                        }
                        idud = idud / dInsns;
                        fdud = fdud / dInsns;
                    }

                    Double fprat = 0.0;
                    if(fpOps.equals(0L)) {
                        fprat = 1.0 / 16.0;
                    } else if(memOps.equals(0L)) {
                        fprat = 16.0;
                    } else {
                        fprat = fpOps.doubleValue() / memOps.doubleValue();
                    }

                    Double bytesPerMop = ((Integer)bb.memBytes).doubleValue() / ((Integer)bb.memOps).doubleValue();

                    // Temporal
                    // FIXME
                    Double reuseScore = 0.0;
                    
                    // Spatial
                    // stride_i = fraction of mem refs with stride i
                    // spatialScore = Sum (stride_i / i)
                    Double spatialScore = 0.0;
                    /*
                    Long totalRefs = 0L;
                    if(blockSpatial != null) {
                        for(Iterator<BinnedCounter> bcit = blockSpatial.iterator(); bcit.hasNext(); ) {
                            BinnedCounter bc = bcit.next();
                            totalRefs += bc.counter;
                        }
                        for(Iterator<BinnedCounter> bcit = blockSpatial.iterator(); bcit.hasNext(); ) {
                            BinnedCounter bc = bcit.next();
                            spatialScore += (bc.counter.doubleValue() / totalRefs.doubleValue()) / bc.upperBound.doubleValue();
                        }
                    }
                    */

                    Long lowAddr = null;
                    Long highAddr = null;
                    Long addRange = null;
                    if(perThreadRanges != null) {
                        for(Iterator<Integer> tit = perThreadRanges.keySet().iterator(); tit.hasNext(); ) {
                            Integer thread = tit.next();
                            AddressRanges ranges = perThreadRanges.get(thread);
                            Long min = ranges.overallMin();
                            Long max = ranges.overallMax();

                            if(lowAddr == null || min < lowAddr) {
                                lowAddr = min;
                            }
                            if(highAddr == null || max > highAddr) {
                                highAddr = max;
                            }
                        }
                        if(lowAddr != null && highAddr != null) {
                            addRange = highAddr - lowAddr;
                        }
                    }

                    file.write(bbid + "\t" + dInsns + "\t" + intOps + "\t" + memOps + "\t" + loadOps + "\t" + storeOps + "\t" + fpOps + "\t" +
                               l1hr + "\t" + l2hr + "\t" + l3hr + "\t" + l1miss + "\t" + l2miss + "\t" + l3miss + "\t" +
                               idud + "\t" + fdud + "\t" + fprat + "\t" + l1mpi + "\t" + l2mpi + "\t" + l3mpi + "\t" +
                               bb.file + "\t" + bb.line + "\t" + bb.functionName + "\t" +
                               bytesPerMop + "\t" + reuseScore + "\t" + spatialScore + "\t" + lowAddr + "\t" + highAddr + "\t" + addRange +
                              "\n");
                }
                
            }
        };
    }

    // parameters to this function are the data structures you will need to write the report
    // they will usually be data structures returned by a TraceDB. i.e. grep public TraceDB.java and search for the get methods
    // Parameters must be marked "final"
    public ReportWriter example() {
        return new ReportWriter() {
            // report name -- not sure if this is actually used anywhere anymore
            public String getType(){
                return "report_name";
            }
            // This is the filename that the report gets written to
            public String getFilename() {
                return processedDir + "/" + testCase.shortName() + ".example_report";
            }
            // This function does the work of processing data structures into a format for the report
            public void writeToFile(BufferedWriter file) throws IOException {
                file.write("Header");

                // for each blah, file.write('blah blah blah');
            }
        };
    }

}
