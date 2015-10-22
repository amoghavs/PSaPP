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


import java.io.*;
import java.awt.TextArea;

public final class Logger {

    static BufferedWriter duplicateFile = null;
    static TextArea       guiDisplay = null;

    static void printDuplicate(String str){
        if(duplicateFile != null){
            try {
                duplicateFile.write("\n" + str + "\n");
            } catch (Exception e) {
                Logger.warn("Duplicate file in Loggger can not be written\n" + e);
                duplicateFile = null;
            }
        }
    }

    public static void error(Exception e, Object str){
        e.printStackTrace();
        warn(e);
        error(str, true);
    }
    
    static void error(Object str, boolean x){
        System.err.println("!!! Error !: " + str);
        printDuplicate("ERROR: " + str);
        if(guiDisplay != null){
            guiDisplay.append("!!! Error !: " + str + "\n");
        }
        if(guiDisplay == null){
            if(x){
                tee();
                System.exit(-1);
            }
        }
    }

    public static void warn(Object str){
        System.out.println("??? Check ?: " + str);
        printDuplicate("CHECK: " + str);
        if(guiDisplay != null){
            guiDisplay.append("??? Check ?: " + str + "\n");
        }
    }
    public static void error(Object str){
        error(str,true);
    }
    public static void inform(Object str,boolean x){
        System.out.println("Information: " + str);
        printDuplicate("INFOR: " + str);
        if(guiDisplay != null){
            guiDisplay.append("Information: " + str + "\n");
        }
        if(x){
            tee();
            System.exit(-1);
        }
    }
    public static void inform(Object str){
        inform(str,false);
    }
    public static void debug(Object str){
        System.out.println("+++ Debug +: " + str);
    }
    public static void plain(Object str){
        System.out.println(str);
    }
    public static void exact(Object str){
        System.out.print(str);
    }
    public static void tee(){
        if(duplicateFile != null){
            try {
                duplicateFile.close();
            } catch (Exception e) {
                Logger.warn("Duplicate file in Loggger can not be closed\n" + e);
            }
        }
        duplicateFile = null;
    }
    public static void tee(String dupFile){
        try {
            tee();
            duplicateFile = new BufferedWriter(new FileWriter(dupFile));
        } catch (Exception e) {
            Logger.warn("Duplicate file " + dupFile + " in Loggger can not be opened\n" + e);
            duplicateFile = null;
        }
    }
    public static void setGuiDisplay(TextArea d){
        guiDisplay = d;
    }

}
