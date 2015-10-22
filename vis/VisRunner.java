package PSaPP.vis;

import PSaPP.util.*;
import PSaPP.dbase.*;

import java.util.*;
import java.io.*;

public class VisRunner implements CommandLineInterface {

    OptionParser   optionParser;
    Database       dataBase;

    static final String[] ALL_OPTIONS = {
        "help:?",
        "version:?",
        "database:s",
        "config:s"
    };

    VisRunner() {
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

        String dbasefile = (String)optionParser.getValue("database");
		VisWindow vis = new VisWindow(dbasefile);
		vis.run();

        return retValue;
    }

    public static void main(String[] args) {
        try {
            VisRunner visRunner = new VisRunner();
            boolean check = visRunner.run(args);
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
      "                               Default is to use Postgres.\n" +
      "[Other Params]:\n" +
      "    --config   /path/con.txt : Path to the PSaPP config file.\n" +
      "                               Not needed for j scripts under bin directory\n";

}
