package PSaPP.data;
/*
Copyright (c) 2010, The Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

*  Redistributions of source code must retain the above copyright notice, this list of conditions
    and the following disclaimer.
*  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
    and the following disclaimer in the documentation and/or other materials provided with the distribution.
*  Neither the name of the Regents of the University of California nor the names of its contributors may be
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


import PSaPP.util.*;
import PSaPP.dbase.*;
import PSaPP.recv.*;

public class MpiComm extends Trace {
    
    double  execTime;
    String baseResource;

    public MpiComm(String path,double eTime,String resource,TestCase tcase,Database dbase){
        super(path,tcase,dbase);
        execTime = eTime;
        baseResource = resource;
    }

    public boolean send(String email,String resource,boolean noverify,boolean report) { assert false: "Should not arrive here";return true; }
    public String  toString() { assert false: "Should not arrive here"; return ""; }
    public String  ftpName(String sizeFile) { assert false: "Should not arrive here"; return ""; }
    public String  localName() { assert false: "Should not arrive here"; return ""; }
    public boolean receive(ReceiveRecord record,String primDir,String secDir,String scrDir){
        return true;
    }
}
