package PSaPP.data;

import PSaPP.util.*;
import PSaPP.dbase.*;
import PSaPP.recv.*;
import java.util.*;
import java.io.*;

public final class SysHealth  extends Trace {
    double  execTime;
    String baseResource;


    public SysHealth(String path,double eTime,String resource,TestCase tcase,Database dbase){
        super(path,tcase,dbase);
        execTime = eTime;
        baseResource = resource;

    }
    public boolean send(String email,String resource,boolean noverify,boolean report){
        String localFile = pathToFiles;
        if(!Util.isFile(localFile)){
            Logger.warn("Invalid file path for syshealth data " + localFile);
            return false;
        }
        return ftpFile(localFile,"",email,resource,noverify,report);
    }
    public String  toString() { 
        return "SysHealth(" + pathToFiles + "," + execTime + "," + baseResource + ")";
    }
    public String  ftpName(String sizeFile) {
        assert (sizeFile != null) : "Ftp source file has to be assigned before this function";
        assert (Util.isFile(sizeFile)) : "Ftp source can be only file";

        return (LinuxCommand.username() + ".." + LinuxCommand.hostname() + ".." +
            testCase.ftpName() + ".." + LinuxCommand.getFileSize(sizeFile) + 
            ".." + "syshealth" + (int)execTime );
    }
    public String localName() { return ""; }
    public boolean receive(ReceiveRecord record,String primDir,String secDir,String scrDir){

        Receive.printReceiveRecord(pathToFiles,record,primDir,secDir,scrDir);

	String syshealthLogFile = "/projects/ipm/ftp/pub/incoming/ipmdata/syshealth/log.txt";
	String ListFile = "/misc/www/server/SciComp/PMaC/syshealth_results/list.txt";

        if(!Util.isFile(syshealthLogFile)){
            Logger.warn("Invalid file path for syshealth log file " + syshealthLogFile);
            return false;
        }

        if(!Util.isFile(ListFile)){
            Logger.warn("Invalid file path for list.txt file " + ListFile);
            return false;
        }

        if(!record.verifySizes(pathToFiles)){
            Logger.warn("syshealth file is not transmitted fully " + pathToFiles);
            return false;
        }

        Date date = new Date();
	

        String targetDir = primDir;
        String targetFile = targetDir + "/" + testCase.getApplication() + "_" + testCase.getDataset() +  "_" + baseResource + "_" + Util.SimpleDateString(date) + ".dat";
	String syshealthhtmldir = targetDir + "/" + testCase.getApplication() + "_" + testCase.getDataset() + "_" + baseResource + "_" + Util.SimpleDateString(date);

	
        boolean status = LinuxCommand.move(pathToFiles,targetFile);
	if(!status){
            Logger.warn("Can not move " + pathToFiles + " to " + targetFile);
            return false;
        }
	

		        
        try {
            BufferedWriter outputFile = new BufferedWriter(new FileWriter(syshealthLogFile, true));

            String log = date + "\t" + testCase.getApplication() + "\t" + testCase.getDataset() + "\t" + baseResource + "\t" + testCase.ftpName() + "\n";
            outputFile.write(log);

            outputFile.close();
        }
        catch (Exception e){
            Logger.warn("Can not append " + syshealthLogFile + "\n" + e);
        }

        try {
            BufferedWriter outputFile = new BufferedWriter(new FileWriter(ListFile, true));

            String log = testCase.getApplication() + " " + testCase.getDataset() + " " + baseResource + " " + Util.SimpleDateString(date) + " " + execTime + "\n";
            outputFile.write(log);

            outputFile.close();
        }
        catch (Exception e){
            Logger.warn("Can not append " + ListFile + "\n" + e);
        }



        return true;
    }
}
