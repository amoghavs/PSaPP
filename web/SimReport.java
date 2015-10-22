package PSaPP.web;
/*
Copyright (c) 2010, PMaC Laboratories, Inc.
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

import PSaPP.util.*;
import PSaPP.dbase.*;
import PSaPP.pred.*;
import PSaPP.sim.*;
import PSaPP.data.*;

import java.util.*;
import java.io.*;
import java.math.*;

import javax.mail.internet.MimeUtility;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;

public class SimReport {

    private static final String subject = "pmac-tools results";
    private static final String DATABASE_CONTENTS_FILE = "report_cases.txt";

    Database database;

    String simulationDir;

    String application;
    String dataset;
    Integer cpuCount;
    Integer[] machineProfiles;
    Integer defaultProfile = Database.InvalidDBID;

    double actualRuntime = 0.0;
    Vector allAttachments = null;
    String reportDatabase = null;
    boolean saveOutput = false;
    boolean privateReport = false;
    String[] recipients = null;
    String[] cc = null;

    TraceDB traceDB = null;

    private Map<Integer, PsinsData> psinsData = null;
    private String xmlName = null;

    public static byte[] aesEncode(byte[] b){
        try {
            String passphrase = (new BigInteger(256, new Random())).toString(16);
            Logger.inform("passphrase for encode: " + passphrase);
            byte[] salt = (new BigInteger(1024, new Random())).toByteArray();
            
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, 1024, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secret);
            AlgorithmParameters params = cipher.getParameters();
            byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
            byte[] ciphertext = cipher.doFinal(b);
            return ciphertext;
        } catch (Exception e){
            Logger.error(e, "Cannot aes-encode xml output");
        }
        return null;
    }

    public static byte[] base64Encode(byte[] b){
        Logger.inform(b.length);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStream b64os = MimeUtility.encode(baos, "base64");
            b64os.write(b);
            b64os.close();
            Logger.inform(baos.toByteArray().length);
            return baos.toByteArray();
        } catch (Exception e){
            Logger.error(e, "Cannot base64 encode reporter output");
        }
        return null;
    }

    public SimReport(String dir, String app, String ds, Integer cpu, int dProf, String email, boolean save, TraceDB td, boolean prv){
        simulationDir = dir;

        application = app;
        dataset = ds;
        cpuCount = cpu;

        database = new Postgres();
        if (!database.initialize()) {
            Logger.warn("Cannot initialize the database");
        }
        traceDB = td;

        actualRuntime = 0.0;
        saveOutput = save;

        recipients = email.split(",");
        assert(recipients != null);
        for (int i = 0; i < recipients.length; i++){
            if (!Util.isValidEmail(recipients[i])){
                Logger.error("Invalid email address in reporter: " + recipients[i]);
            }
        }
        defaultProfile = dProf;

        reportDatabase = ConfigSettings.getSetting("REPORT_DATABASE");
        psinsData = Util.newHashMap();
        privateReport = prv;
    }

    public SimReport(String dir, TestCase testcase, Database db, int dProf, double realRuntime, TraceDB td, boolean prv) {
        simulationDir = dir; 
        application = testcase.getApplication();
        dataset = testcase.getDataset();
        cpuCount = testcase.getCpu();

        database = db;
        traceDB = td;

        actualRuntime = realRuntime;
        saveOutput = false;

        recipients = database.getEmailsFromTestCase(testcase);
        assert(recipients != null);
        for (int i = 0; i < recipients.length; i++){
            if (!Util.isValidEmail(recipients[i])){
                Logger.error("Invalid email address in reporter: " + recipients[i]);
            }
        }

        cc = null;
        if (ConfigSettings.hasSettingsList("EMAIL_CC")){
            cc = ConfigSettings.getSettingsList("EMAIL_CC");
        }

        defaultProfile = dProf;

        reportDatabase = ConfigSettings.getSetting("REPORT_DATABASE");
        psinsData = Util.newHashMap();
        privateReport = prv;
    }

    protected static Tuple.Tuple4<String, String, Integer, Integer> parseFileName(String file){
        String woExt = file.replace(".psinsout", "");
        String[] fields = woExt.split("_");
        if (fields.length != 4){
            Logger.error("File name has invalid format: " + file);
        }
        Integer mp = new Integer(fields[3].replace("pr", ""));
        return Tuple.newTuple4(fields[0], fields[1], new Integer(fields[2]), mp);
    }

    private String formXmlName(){
        String machStr = "d" + defaultProfile;
        for (int i = 0; i < machineProfiles.length; i++){
            machStr += "_" + machineProfiles[i];
        }
        return application + "_" + dataset + "_" + Format.cpuToString(cpuCount) + "_" + machStr + ".xml";
    }

    private void setXmlOutputName(){
        xmlName = simulationDir + "/" + formXmlName();
    }

    private String databaseContents(){
        return formXmlName() + ' ' + application + ' ' + dataset + ' ' + cpuCount + ' ' + 
            defaultProfile + ' ' + database.getBaseResourceName(defaultProfile);
    }

    public String xmlOutputName(){
        if (xmlName == null){
            setXmlOutputName();
        }
        return xmlName;
    }

    private String xmlString(){

        // create an xml document object
        Document doc = null;
        try {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException pce){
            Logger.error(pce, "Cannot create xml document");
        }
        assert(doc != null);        

        // build the main document structure
        Element docRoot = doc.createElement("simcollection");
        doc.appendChild(docRoot);
        docRoot.setAttribute("application", application);
        docRoot.setAttribute("dataset", dataset);
        docRoot.setAttribute("cpu_count", Integer.toString(cpuCount));
        docRoot.setAttribute("default_profile", Integer.toString(defaultProfile));
        docRoot.setAttribute("default_system", database.getBaseResourceName(defaultProfile));
        if ((new Double(0.0)).compareTo(actualRuntime) != 0){
            docRoot.setAttribute("actual_runtime", Double.toString(actualRuntime));
        }

        // add all psins data
        for (Iterator it = psinsData.keySet().iterator(); it.hasNext(); ){
            Integer mProf = (Integer)it.next();
            PsinsData p = (PsinsData)psinsData.get(mProf);
            docRoot.appendChild(p.toXMLElement(doc));
        }

        // write the xml document to a string
        DOMSource source = new DOMSource(doc);
        StreamResult result =  new StreamResult(new StringWriter());
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(source, result);
        } catch (TransformerException te){
            Logger.error(te, "Cannot write xml document to string");
        }
        return result.getWriter().toString();
    }

    public boolean run(int[] profiles){
        Integer[] prof = new Integer[profiles.length];
        for (int i = 0; i < profiles.length; i++){
            prof[i] = profiles[i];
        }

        return run(prof);
    }

    public boolean run(Integer[] profiles) {
        String skipReport = ConfigSettings.getSetting("SKIP_REPORT");
        if (skipReport.matches("true")){
            Logger.warn("Skipping reports due to config file");
            return true;
        }

        machineProfiles = profiles;
        if (machineProfiles.length == 0) {
            Logger.error("At least 1 machine profile is required for reporting");
        }
        Arrays.sort(machineProfiles);

        Map<FunctionID, Function> funcStats = Util.newHashMap();
        traceDB.getDynamicAggregateStats(funcStats, null);
        
        for (int i = 0; i < machineProfiles.length; i++){
            PsinsData p = new PsinsData(database, simulationDir, application, dataset, cpuCount, machineProfiles[i]);
            psinsData.put(machineProfiles[i], p);
            p.parse();
            p.addTraceDBFunctions(funcStats);
        }

        try {
            String filename = xmlOutputName();
            if (privateReport){
                filename += ".enc.base64";
            }

            Logger.inform("Writing xml-formatted simulation results to " + filename);
            BufferedWriter out = new BufferedWriter(new FileWriter(filename, false));

            if (privateReport){
                Logger.error("Private reporting not fully implemented yet");
            } else {
                out.write(xmlString());
            }

            out.close();

        } catch (IOException ioe){
            Logger.error(ioe, "Cannot write xml results to " + xmlOutputName());
            return false;
        }

        // prepare/send email
        String subj = subject + " - " + application + " " + dataset + " " + cpuCount;
        String body = "This application's full performance report will be available for viewing at http://apps.ccac.hpc.mil/PMAC/report.html?simreport=" + 
            formXmlName() + "\n\nThank You,\nThe PMaC Laboratories Team\n";
        String[] attach = new String[1];
        attach[0] = xmlOutputName();

        try {
            Util.sendEmail(recipients, cc, subj, body, attach);
        } catch (Exception e){
            Logger.error(e, "Cannot send report email");
            return false;
        }

        // update report database
        if (!addToReportDatabase()){
            return false;
        }

        return true;
    }

    public boolean addToReportDatabase(){
        if (LinuxCommand.copy(xmlOutputName(), reportDatabase + "/data/") == null){
            return false;
        }

        try{
            String reportList = reportDatabase + "/" + DATABASE_CONTENTS_FILE;
            String reportListBak = reportList + ".bak";

            if (LinuxCommand.copy(reportList, reportListBak) == null){
                return false;
            }

            BufferedWriter out = new BufferedWriter(new FileWriter(reportListBak, true));
            out.write(databaseContents() + "\n");
            out.close();

            if(!LinuxCommand.move(reportListBak, reportList)){
                return false;
            }

        } catch (Exception e){
            Logger.error(e, "Cannot update report database");
            return false;
        }
        return true;
    }

    public static void main(String argv[]) {
        ConfigSettings.readConfigFile();
        CommandLineParser c = new CommandLineParser(argv);
        TraceDB traceDB = null;
        if (c.testCase != null && c.tracedbPath != null){
            traceDB = new TraceDB(c.tracedbPath, c.testCase, null);
        }

        SimReport simReport = new SimReport(c.simulationDir, c.application, c.dataset, c.cpuCount, c.defaultProfile, c.email, c.save, traceDB, c.privateReport);

        if (simReport.run(c.machineProfiles)) {
            Logger.inform(Util.DONE_SUCCESS);
        }
    }

}

class CommandLineParser implements CommandLineInterface {

    public String fundingAgency;
    public String project;
    public Integer round;
    public String application;
    public String dataset;
    public Integer cpuCount;

    public TestCase testCase;

    public String simulationDir;
    public Integer[] machineProfiles;
    public String tracedbPath;
    public String email;
    public boolean save;
    public boolean privateReport;

    public Integer defaultProfile;

    static final String[] ALL_OPTIONS = {
        "help:?",

        "funding_agency:s",
        "project:s",
        "round:i",
        "application:s",
        "dataset:s",
        "cpu_count:i",

        "dir:s",
        "file:s",
        "tracedb:s",
        "profiles:s",
        "email:s",
        "save_output:?",
        "private:?",
    };

    private final String helpString(){
        return
            "[Basic Params]:\n" +
            "    --help                              : print a brief help message\n" +
            "[Test Case Params]:\n" +
            "    --funding_agency <funding_agency>   : funding agency. listed in PSaPP config file.\n" +
            "    --project        <project>          : project. listed in PSaPP config file.\n" +
            "    --round          <n>                : round number\n" +
            "    --application      <application>    : application. listed in PSaPP config file. [REQ]\n" +
            "    --dataset          <dataset>        : dataset name. listed in PSaPP config file. [REQ]\n" +
            "    --cpu_count        <cpu_count>      : number of cpus. listed in PSaPP config file. [REQ]\n" +

            "[Script Params]:\n" +
            "    --dir            /path/to/psinsout  : path to directory containing .psinsout files [REQ]\n" +
            "                                          default is to process all\n" +
            "    --file           <filename>         : process a specific file\n" +
            "    --profiles       <profiles>         : comma delimited machine profiles to process\n" +
            "    --tracedb        <path/to/tracedb>  : path to trace database to include trace data in report\n" +
            "                                          (requires all test case parameters)\n" +
            "    --email          <email addresses>  : comma delimited email recipients for report [REQ]\n" +
            "    --save_output                       : output files are never deleted\n" +
            "    --private (TODO)                    : report is encrypted and not added to report database\n" +
            "";
        
    }

    public CommandLineParser(String[] argv){
        OptionParser optionParser = new OptionParser(ALL_OPTIONS, this);

        if (argv.length < 1) {
            optionParser.printUsage("");
        }
        optionParser.parse(argv);
        if (optionParser.isHelp()) {
            optionParser.printUsage("");
        }
        if (!optionParser.verify()) {
            Logger.error("Error in command line options");
        }

        fundingAgency = (String)optionParser.getValue("funding_agency");
        project = (String)optionParser.getValue("project");
        round = (Integer)optionParser.getValue("round");
        application = (String)optionParser.getValue("application");
        dataset = (String)optionParser.getValue("dataset");
        cpuCount = (Integer)optionParser.getValue("cpu_count");

        testCase = null;
        if (fundingAgency != null &&
            project != null &&
            round != null){
            testCase = new TestCase(application, dataset, cpuCount, fundingAgency, project, round);
        }

        simulationDir = (String)optionParser.getValue("dir");
        tracedbPath = (String)optionParser.getValue("tracedb");
        email = (String)optionParser.getValue("email");
        save = (optionParser.getValue("save_output") != null);
        privateReport = (optionParser.getValue("private") != null);

        String filename = (String)optionParser.getValue("file");
        machineProfiles = Util.machineList((String)optionParser.getValue("profiles"));

        if (filename == null){
            defaultProfile = machineProfiles[0];
        } else {
            Tuple.Tuple4<String, String, Integer, Integer> nameParts = SimReport.parseFileName(filename);
            defaultProfile = nameParts.get3();            
            machineProfiles = new Integer[1];
            machineProfiles[0] = defaultProfile;
        }
    }

    public boolean verifyValues(HashMap values) {
        if (values.get("dir") == null) {
            Logger.error("--dir is a required argument");
            return false;
        }
        if (values.get("application") == null){
            Logger.error("--application is a required argument");
            return false;
        }
        if (values.get("dataset") == null){
            Logger.error("--dataset is a required argument");
            return false;
        }
        if (values.get("cpu_count") == null){
            Logger.error("--cpu_count is a required argument");
            return false;
        }
        if (values.get("email") == null){
            Logger.error("--email is a required argument");
            return false;
        }
        if (values.get("file") != null && values.get("profiles") != null){
            Logger.error("Can only use one of --file and --profiles");
            return false;
        }
        if (values.get("profiles") != null){
            Integer[] profs = Util.machineList((String)values.get("profiles"));
            if (profs.length < 1){
                Logger.error("option to --profiles should be lists/ranges of integers");
                return false;
            }
        } else {
            if (values.get("file") == null){
                Logger.error("requires --file or --profiles");
                return false;
            }
        }
        if (values.get("tracedb") != null){
            if (values.get("funding_agency") == null ||
                values.get("project") == null ||
                values.get("round") == null){
                Logger.error("--tracedb requires all test case params to be used");
                return false;
            }
        }
        return true;
    }

    public boolean isHelp(HashMap values) {
        return (values.get("help") != null);
    }

    public void printUsage(String str) {
        System.out.println("\n" + str + "\n");
        System.out.println(helpString());
        String all = "usage :\n";
        for (int i = 0; i < ALL_OPTIONS.length; ++i) {
            all += ("\t--" + ALL_OPTIONS[i] + "\n");
        }
        all += ("\n" + str);
    }

    public boolean isVersion(HashMap values){
        return false;
    }

    public TestCase getTestCase(HashMap value){
        return null;
    }
}
