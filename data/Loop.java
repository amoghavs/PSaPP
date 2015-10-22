package PSaPP.data;

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

public class Loop extends DynamicAggregator {
    public BlockID parentHead = null;
    public int depth = 0;
    
    public static final String LOOP_UNAFFILIATED = "__not_loop_member__";

    public void setHead(BasicBlock bb){
        assert(bb.functionName != null);
        functionName = bb.functionName;

        functionID = bb.functionID;
        file = bb.file;
        line = bb.line;

        headBlock = bb.loopHead;
        parentHead = bb.parentLoopHead;
        depth = bb.loopDepth;

        blocks.add(bb.bbid);
    }

    public void setUnknown(){
        functionID = FunctionID.INVALID_FUNCTION;
        functionName = Loop.LOOP_UNAFFILIATED;
        file = Loop.LOOP_UNAFFILIATED;
        line = 0;
        assert(depth == 0);        
    }
    
    public String toString(){
        String s = "Loop " + headBlock + " in function " + functionName + " @" + file  + ":" + line + ": ";
        s += "STATIC<" + loopCount + "," + getBlockCount() + "," + insns + "," + memOps + "," + fpOps + ">; ";
        s += "DYNAMIC<" + visitCount + "," + dInsns + "," + dMemOps + "," + dFpOps + "," + dMembytes + ">;";
        s += "LOOP<" + headBlock + "," + parentHead + "," + depth + "," + entryCount + ">;";
        return s;
    }

    public String describe() { return headBlock.toString(); }
}
