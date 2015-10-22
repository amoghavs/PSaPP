package PSaPP.util;
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


import java.text.*;

public class Format {
    static String dec2StrRightJust(long number,int width,char blank){
        StringBuffer nbuffer = new StringBuffer();
        nbuffer.append(number);
        if(nbuffer.length() >= width){
            return nbuffer.toString();
        }
        int diff = width - nbuffer.length();
        for(int i=0;i<diff;i++){
            nbuffer.insert(0,blank);
        }
        return nbuffer.toString();
    }
    public static String padRight(String s, int n){
        return String.format("%1$-" + n + "s", s);  
    }
    public static String cpuToString(int n){
        return dec2StrRightJust((long)n,4,'0');
    }
    public static String cpuToString(Integer n){
        return dec2StrRightJust(n.longValue(),4,'0');
    }
    public static String phaseToString(int n){
        return dec2StrRightJust((long)n,2,'0');
    }
    public static String formatNd(Object v,char filler){
        if(v instanceof Integer){
            return dec2StrRightJust(((Integer)v).longValue(),9,filler);
        } else if(v instanceof Long){
            return dec2StrRightJust(((Long)v).longValue(),12,filler);
        }
        String str = v + "";
        if(str.length() > 40){
            return str.substring(0,39);
        }
        return str;
    }
    public static String format2d(double x){
        NumberFormat formatter = new DecimalFormat("0.00");
        return formatter.format(x);
    }
    public static String format4d(double x){
        NumberFormat formatter = new DecimalFormat("0.0000");
        return formatter.format(x);
    }
    public static String format6d(double x){
        NumberFormat formatter = new DecimalFormat("0.000000");
        return formatter.format(x);
    }
    public static String format8d(double x){
        NumberFormat formatter = new DecimalFormat("0.00000000");
        return formatter.format(x);
    }
    public static String format10d(double x){
        NumberFormat formatter = new DecimalFormat("0.0000000000");
        return formatter.format(x);
    }
    public static String formatNMd(int n,int m,double x){
        String formatStr = "";
        for(int i=0;i<n;i++){
            formatStr += "0";
        }
        if (m != 0){
            formatStr += ".";
            for(int i=0;i<m;i++){
                formatStr += "0";
            }
        }
        NumberFormat formatter = new DecimalFormat(formatStr);
        return formatter.format(x);
    }
    public static String format4d(Double x){
        return format4d(x.doubleValue());
    }
    public static String format8d(Double x){
        return format8d(x.doubleValue());
    }
    public static String formatNMd(int n,int m,Double x){
        return formatNMd(n,m,x.doubleValue());
    }
    public static String BR(int x){
        return ("br" + x);
    }
    public static String PR(int x){
        return ("pr" + x);
    }
    public static String MP(int x){
        return ("mp" + x);
    }
    public static String NP(int x){
        return ("np" + x);
    }
}
