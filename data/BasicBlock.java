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

import PSaPP.util.Util;
import java.util.*;

public class BasicBlock implements Comparable<BasicBlock> {

    public static final int LOOPLOC_NONE = 0;
    public static final int LOOPLOC_HEAD = 1;
    public static final int LOOPLOC_TAIL = 2;

    public static final String INFO_UNKNOWN = "__unknown__";

    // -- Static data -- //
    public BlockID bbid;

    public int insns;
    public int intOps;
    public int branchOps;
    public int logicOps;
    public int shiftRotateOps;
    public int trapSyscallOps;
    public int memOps;
    public int loadOps;
    public int storeOps;
    public int fpOps;
    public int specialRegOps;
    public int scatterGatherOps;
    public int vectorMaskOps;
    public int otherOps;

    public int memBytes;
    public int defUseCrossCount;
    public int callCount;

    public String file;
    public FunctionID functionID;
    public String functionName;
    public int line;

    public Long vaddr;

    public int loopLoc;

    public Long callTargetAddress;
    public String callTargetName;

    public LinkedList<Dud> duds = Util.newLinkedList();
    public LinkedList<VecOps> vecs = Util.newLinkedList();

    public BlockID loopHead;
    public BlockID parentLoopHead;
    public int loopDepth;

    public int loopCount;

    public Map<String, Object> info = Util.newHashMap();

    public Long visitCount;

    public int compareTo(BasicBlock other) {
        return bbid.compareTo(other.bbid);
    }
}
