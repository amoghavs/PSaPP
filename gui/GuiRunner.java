package PSaPP.gui;
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

import java.util.*;
import java.io.*;

public class GuiRunner implements CommandLineInterface {

    OptionParser   optionParser;
    Database       dataBase;

    static final String[] ALL_OPTIONS = {
        "help:?",
        "version:?",
        "database:s"
    };

    GuiRunner() {
        optionParser = new OptionParser(ALL_OPTIONS,this);
        dataBase = null;
    }

    public boolean verifyValues(HashMap values){
        return true;
    }
    public TestCase getTestCase(HashMap values) {
        return null;
    }
    public boolean isHelp(HashMap values) {
        return (values.get("help") != null);
    }
    public boolean isVersion(HashMap values) {
        return (values.get("version") != null);
    }
    public void printUsage(String str){
        System.err.println(helpString);
        String allStr = "usage :\n";
        for(int i=0;i<ALL_OPTIONS.length;i++){
            allStr += ("\t--" + ALL_OPTIONS[i] + "\n");
        }
        allStr += ("\n" + str);
    }
    public boolean run(String argv[]) {

        boolean retValue  = false;

        optionParser.parse(argv);
        if(optionParser.isHelp()){
            optionParser.printUsage("");
            return false;
        }
        if(optionParser.isVersion()){
            Logger.inform("The version is <this>",true);
            return true;
        }
    
		ConfigSettings.readConfigFile();

		String dbasefile = (String)optionParser.getValue("database");
	Interface gui = new Interface(dbasefile);
	gui.run();
        return retValue;
    }

    public static void main(String[] args) {
        try {
            GuiRunner guiRunner = new GuiRunner();
            boolean check = guiRunner.run(args);
            if(check){
                Logger.inform("\n*** DONE *** SUCCESS *** SUCCESS *** SUCCESS *****************\n");
            }
        } catch (Exception e) {
            Logger.error(e, "*** FAIL *** FAIL *** FAIL Exception *****************\n");
        }
    }

    static final String helpString =
      "[Basic Params]:\n" +
      "    --help                   : Print a brief help message\n" +
      "    --version                : Print the version and exit\n" +
      "[DB Params]:\n" +
      "    --database /path/db.bin  : The path to the database file if binary files are used.\n" +
      "                               Default is to use Postgres.\n";
}
