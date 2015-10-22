package PSaPP.pred;
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


import java.io.*;
import PSaPP.util.*;

class PWRMethod {

    String model;

    PWRMethod(String m) { model = m; }

    //<CPU_FREQ, L1M_P_INS, L2M_P_INS, L3M_P_INS, FPRAT, MOPS_INS, FOPS_INS, FOPS_MOPS, dintDUD, dfpDUD>
    double calculatePWR(int freq, double[] vec) {
        //int freq=2600000;
        //double l1MPI=0.1247427474879446;
        //double l2MPI=0.12474204850734597;
        //double l3MPI=0.12474204850734597;
        //double fpRatio =0.4999999995343387;
        //double memToIns=0.4992680551950163;
        //double fpToIns =0.24963402736501836;
        //double fmr     =0.33333333312637276;
        //double idud    =3.50;
        //double fdud    =1.00;
        String model = new String("power.model");

        String Rcommand = new String("load(\""+model+"\");p=list(");
        Rcommand += "FREQ="+freq+", ";
        Rcommand += "L1M_P_INS="+vec[0]+", ";//l1MPI;
        Rcommand += "L2M_P_INS="+vec[1]+", ";//l2MPI;
        Rcommand += "L3M_P_INS="+vec[2]+", ";//l3MPI;
        Rcommand += "FPRAT="+vec[3]+", ";//fpRatio;
        Rcommand += "MOPS_INS="+vec[4]+", ";//memToIns;
        Rcommand += "FOPS_INS="+vec[5]+", ";//fpToIns;
        Rcommand += "FOPS_MOPS="+vec[6]+", ";//fmr;
        Rcommand += "dintDUD="+vec[7]+", ";//idud;
        Rcommand += "dfpDUD="+vec[8];//fdud;
        //command += ");\nexp(predict(model, newdata=p))\nEOT\n | grep -v -e \">\" | awk '{print $2}'\n";
        Rcommand += ");exp(predict(model, newdata=p))";

        double pwr = 0;
        try {
            //Process r = Runtime.getRuntime().exec(command);
            Process r = new ProcessBuilder("R", "--no-save", "--quiet", "-e", Rcommand).start();
            InputStreamReader isr = new InputStreamReader(r.getInputStream());
            BufferedReader result = new BufferedReader(isr);
            String line = result.readLine();
            line = result.readLine();
            String[] res = line.split(" ");
            pwr = Double.parseDouble(res[1]);
        }
        catch(IOException e) {
            System.err.println("Power modeling failed");
        }

        return pwr;
    }
}
