package PSaPP.data;

import PSaPP.util.*;
import PSaPP.dbase.*;
import PSaPP.recv.*;
import java.util.*;
import java.io.*;

public final class Ipm  extends Trace {
    double  execTime;
    String baseResource;


    public Ipm(String path,double eTime,String resource,TestCase tcase,Database dbase){
        super(path,tcase,dbase);
        execTime = eTime;
        baseResource = resource;

    }
    public boolean send(String email,String resource,boolean noverify,boolean report){
        String localFile = pathToFiles;
        if(!Util.isFile(localFile)){
            Logger.warn("Invalid file path for ipm data " + localFile);
            return false;
        }
        return ftpFile(localFile,"",email,resource,noverify,report);
    }
    public String  toString() { 
        return "IpmData(" + pathToFiles + "," + execTime + "," + baseResource + ")";
    }
    public String  ftpName(String sizeFile) {
        assert (sizeFile != null) : "Ftp source file has to be assigned before this function";
        assert (Util.isFile(sizeFile)) : "Ftp source can be only file";

        return (LinuxCommand.username() + ".." + LinuxCommand.hostname() + ".." +
            testCase.ftpName() + ".." + LinuxCommand.getFileSize(sizeFile) + 
            ".." + "ipmdata" + (int)execTime );
    }
    public String localName() { return ""; }
    public boolean receive(ReceiveRecord record,String primDir,String secDir,String scrDir){

        Receive.printReceiveRecord(pathToFiles,record,primDir,secDir,scrDir);
	int i=0;
	String Base_URL = "http://www.sdsc.edu/pmac/ipm_results/data/";
	String ipmLogFile = "/projects/ipm/ftp/pub/incoming/ipmdata/log.txt";
	String ListFile = "/misc/www/server/SciComp/PMaC/ipm_results/list.txt";
	String infoFile = "/projects/ipm/ftp/pub/incoming/ipmdata/temp.info";	
	String dataFile = "/projects/ipm/ftp/pub/incoming/ipmdata/data.out";
	String AppPerfomanceFile = "/misc/www/server/SciComp/PMaC/ipm_results/" + testCase.getApplication() + ".txt";
	String scriptPath = "/projects/pmac/mitesh/projects/PSaPP/scripts/";  /** This needs to point to script directory in PSaPP installation, TBD read from config**/
	String parseScript = scriptPath + "parse.pl";
	String infoScript = scriptPath + "info.sh";
        if(!Util.isFile(ipmLogFile)){
            Logger.warn("Invalid file path for ipm log file " + ipmLogFile);
            return false;
        }

        if(!Util.isFile(ListFile)){
            Logger.warn("Invalid file path for list.txt file " + ListFile);
            return false;
        }
        if(!Util.isFile(AppPerfomanceFile)){
            Logger.warn("Invalid file path for file " + AppPerfomanceFile);
            return false;
        }
        if(!Util.isFile(parseScript)){
            Logger.warn("Invalid file path for script " + parseScript);
            return false;
        }
        if(!Util.isFile(infoScript)){
            Logger.warn("Invalid file path for script " + infoScript);
            return false;
        }

        if(!record.verifySizes(pathToFiles)){
            Logger.warn("ipmdata file is not transmitted fully " + pathToFiles);
            return false;
        }

	
        String targetDir = primDir;
        ArrayList filearr = new ArrayList();
	String date;
	String wallclock;
	String mpi, gflops, gbytes, percent_comm;
	boolean status;

	status=LinuxCommand.infoscript(infoScript, pathToFiles, infoFile);
	if(status){
            Logger.warn("Can not run info script on " + pathToFiles + "with infofile " + infoFile);
            return false;
        }

	status=LinuxCommand.parsescript(parseScript, infoFile, dataFile);
	if(status){
            Logger.warn("Can not run parse script on " + infoFile + "with dataFile " + dataFile);
            return false;
        }
	

        try {
            BufferedReader inFile = new BufferedReader(new FileReader(dataFile));
	
            String line=null;
            while( (line = inFile.readLine()) != null)
            {
	 	filearr.add(line);	

	    }
	
	
            inFile.close();
        }
        catch (Exception e){
            Logger.warn("Can not read " + dataFile + "\n" + e);
        }

	if(filearr.size()<6)
	{
            Logger.warn("The" + parseScript + " script did not extract all the data from" + infoFile + "to dataFile " + dataFile);
            return false;
	}
	wallclock = (String) filearr.get(0);
	date = (String) filearr.get(1);
	mpi  = (String) filearr.get(2);
	gflops = (String) filearr.get(3);
	gbytes = (String) filearr.get(4);
	percent_comm = (String) filearr.get(5);
	
	
        String targetFile = targetDir + "/" + testCase.getApplication() + "_" + baseResource + "_" + date + ".xml";
	String ipmhtmldir = targetDir + "/" + testCase.getApplication() + "_" + baseResource + "_" + date;
        
	status = LinuxCommand.move(pathToFiles,targetFile);
	if(!status){
            Logger.warn("Can not move " + pathToFiles + " to " + targetFile);
            return false;
        }
	

	status = LinuxCommand.ipmparse(targetFile, ipmhtmldir);
	if(status){
            Logger.warn("Can not run ipm_parse on " + targetFile + "with htmldir path " + ipmhtmldir);
            return false;
        }
	
        try {
            BufferedWriter outputFile = new BufferedWriter(new FileWriter(ipmLogFile, true));

            String log = date + "\t" + testCase.getApplication() + "\t" + baseResource + "\t" + testCase.ftpName() + "\t" + wallclock +  "\n";
            outputFile.write(log);

            outputFile.close();
        }
        catch (Exception e){
            Logger.warn("Can not append " + ipmLogFile + "\n" + e);
        }

        try {
            BufferedWriter outputFile = new BufferedWriter(new FileWriter(ListFile, true));

            String log = testCase.getApplication() + " " + baseResource + " " + date + " " + Base_URL + testCase.getApplication() + "_" + baseResource + "_" + date + "/index.html" +  "\n";
            outputFile.write(log);

            outputFile.close();
        }
        catch (Exception e){
            Logger.warn("Can not append " + ListFile + "\n" + e);
        }

      try {
            BufferedWriter outputFile = new BufferedWriter(new FileWriter(AppPerfomanceFile, true));
            
	    String MPI_URL = Base_URL + testCase.getApplication() + "_" + baseResource + "_" + date + "/img/mpi_pie.png";
	    String IPM_URL = Base_URL + testCase.getApplication() + "_" + baseResource + "_" + date + "/index.html";

            String log = baseResource + " " + date + " " + wallclock + " " + mpi + " " + gflops + " " + gbytes + " " + percent_comm + " " + MPI_URL + " " + IPM_URL;
	   

	    log = log + "\n";
		
            outputFile.write(log);

            outputFile.close();
        }
        catch (Exception e){
            Logger.warn("Can not append " + AppPerfomanceFile + "\n" + e);
        }


        return true;
    }
}

