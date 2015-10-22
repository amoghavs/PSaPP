package PSaPP.vis;

import java.util.*;
import java.io.*;
import java.sql.*;
import java.awt.*;
import java.awt.event.*;
import PSaPP.util.*;
import PSaPP.dbase.*;
import PSaPP.stats.*;
import PSaPP.pred.*;

class  MultipleChoice extends Panel {

    static Color checkBoxBackground  = new Color(135,206,250);
    final static int NO_SELECTION = -1;

    Checkbox[] boxes;
    CheckboxGroup group;

    MultipleChoice(String[] choices,boolean vertical){
        boxes = new Checkbox[choices.length];
        if(vertical){
            setLayout(new GridLayout(choices.length,1));
        } else {
            setLayout(new GridLayout(1,choices.length));
        }
        group = new CheckboxGroup();
        for(int i=0;i<choices.length;i++){
            boxes[i] = new Checkbox(choices[i],group,false);
            add(boxes[i]);
            boxes[i].setBackground(checkBoxBackground);
        }
    }
    int getSelectedIndex(){
        for(int i=0;i<boxes.length;i++){
            if(boxes[i].getState()){
                assert (boxes[i] == group.getSelectedCheckbox());
                return i;
            }
        }
        return NO_SELECTION;
    }
    void clearBoxes(){
        group.setSelectedCheckbox(null);
    }
}

class BWCurveCanvas extends Canvas {
    
    final static double markers[] = { 100.0, 99.0, 98.0, 96.0, 92.0, 84.0, 68.0, 36.0 };
    final static String[] freeParamNames = { "L2 Tau", "L3 Tau", "MM Tau", "L2 Beta", "L3 Beta", "MM Beta" };
    final static String[] directionNames = { "Increment" , "Decrement" };
    final static double factorInc = 1.0;
    final static int direcIterationCount = 5;

    Object[] profile;
    BWMethodStretchedExp bwMethod;
    int levelCount;
    int paramIdx;
    int direcIdx;
    Label actionStatus;
    LinkedList allPoints;

    public BWCurveCanvas (Label status) {
        profile = null;
        bwMethod = null;
        levelCount = 0;
        paramIdx = MultipleChoice.NO_SELECTION;
        direcIdx = MultipleChoice.NO_SELECTION;
        actionStatus = status;
        allPoints = new LinkedList();
        setBackground(Color.white);
    }

    Object[] newUpdatedProfile(Object[] inpProf,int idx,double r){
        boolean isBetaRel = false;
        if(idx >= 3){
            isBetaRel = true;
        }
        Object[] retValue = new Object[inpProf.length];
        for(int i=0;i<inpProf.length;i++){
            retValue[i] = inpProf[i];
        }
        if((levelCount == 2) && (idx > 1)){
            idx--;
        }
        if((levelCount == 2) && (idx > 4)){
            idx--;
        }
        double newValue = ((Double)retValue[2+levelCount+idx]).doubleValue() * r;
        if(isBetaRel && (newValue > 1.0)){
            newValue = 1.0;
        }
        retValue[2+levelCount+idx] = new Double(newValue);
        return retValue;
    }

    public void setTypeAndDir(int pIdx,int dIdx){
        paramIdx = pIdx;
        direcIdx = dIdx;
    }

    public void setBWParams(int lvl,Object[] prof,BWMethodStretchedExp meth){
        profile = prof;
        bwMethod = meth;
        levelCount = lvl;

        allPoints.clear();

        if((profile == null) || (bwMethod == null) || (levelCount == 0)){
            return;
        }

        int[] digitValue = new int[levelCount];
        for(int i=0;i<levelCount;i++){
            digitValue[i] = 0;
        }

        int currDigit = levelCount-1;
        while(true){
            if(currDigit == levelCount-1){
                Object[] hits = new Object[levelCount];
                for(int i=0;i<levelCount;i++){
                    hits[levelCount-i-1] = new Double(markers[digitValue[i]]);
                }
                allPoints.add(hits);
            }
            digitValue[currDigit] = (digitValue[currDigit] + 1) % markers.length;
            if(digitValue[currDigit] == 0){
                currDigit = currDigit-1;
                if(currDigit < 0){
                    break;
                }
            } else if(currDigit < levelCount-1){
                for(int i=currDigit+1;i<levelCount;i++){
                    digitValue[i] = digitValue[currDigit];
                }
                currDigit = levelCount-1;
            }
        }
    }
    public void paint(Graphics g) {
        Dimension canvasSize = getSize();
        g.clearRect(0,0,canvasSize.width,canvasSize.height);

        if((profile == null) || 
           (bwMethod == null) || 
           (levelCount == 0) ||
           (allPoints.size() == 0)){
            return;
        }

        drawBWCurve(g,profile,Color.blue,true);

        if((paramIdx != MultipleChoice.NO_SELECTION) && (direcIdx != MultipleChoice.NO_SELECTION)){
            if((levelCount == 2) && ((paramIdx == 1) || (paramIdx == 4))){
                actionStatus.setBackground(Color.red);
                Logger.warn(freeParamNames[paramIdx] + " is not valid for " + levelCount + " levels");
                return;
            }

            double factor = 1.0;
            for(int i=0;i<direcIterationCount;i++){
                if(direcIdx == 0){
                    factor *= (1.0+factorInc);
                } else if(direcIdx == 1){
                    factor /= (1.0+factorInc);
                }
                Object[] newProfile = newUpdatedProfile(profile,paramIdx,factor);
                drawBWCurve(g,newProfile,Color.red,false);
            }
        }
    }

    void drawBWCurve(Graphics g,Object[] profTokens,Color foreColor,boolean drawFixture){

        Dimension canvasSize = getSize();

        Object[] blockFields = new Object[levelCount+3];
        for(int i=0;i<3;i++){
            blockFields[i] = null;
        }
        for(int i=0;i<levelCount;i++){
            blockFields[i+3] = new Double(100.0);
        }

        int xinc = canvasSize.width / allPoints.size();

        double maxBW = bwMethod.calculateBW(blockFields,profTokens,null);

        if(drawFixture){
            g.setColor(Color.black);
            g.drawString("Max BW = " + Format.format2d(maxBW / 1024 / 1024) + " MB/sec",2*canvasSize.width/3,canvasSize.height/3);
        }

        int x = 0;
        int y = 0;

        for(Iterator it=allPoints.iterator();it.hasNext();){
            Object[] hits = (Object[])it.next();

            for(int i=0;i<levelCount;i++){
                blockFields[i+3] = hits[i];
            }

            double bw = bwMethod.calculateBW(blockFields,profTokens,null);
            int xp = x+xinc;
            int yp = (int)(canvasSize.height - (bw*canvasSize.height/maxBW));

            double l0h = ((Double)hits[0]).doubleValue();
            double l1h = ((Double)hits[1]).doubleValue();

            g.setColor(foreColor);
            if(l0h != l1h){
                g.drawLine(x,y,xp,yp);
            }

            g.drawRect(xp,yp,2,2);

            if(drawFixture){
                g.setColor(Color.black);
                g.drawLine(x,canvasSize.height-2,x,canvasSize.height);
            }

            y = yp;
            x = xp;

            if(((Double)hits[levelCount-2]).doubleValue() == markers[markers.length-1]){
                g.setColor(Color.black);
                if(drawFixture){
                    g.drawLine(x,20,x,canvasSize.height);
                    g.drawString("h[" + levelCount + "]=" + 
                        Format.formatNMd(2,0,((Double)hits[levelCount-1]).doubleValue()),x-20,20);
                }
                //if(drawFixture){
                //    g.drawString("<<",x+5,y+5);
                //}
            }
        }
    }
    void printProfile(Object[] prof){
        String str = "( ";
        for(int i=2;i<prof.length;i++){
            if(prof[i] instanceof Double){
                str += " " + prof[i];
            }
        }
        str += " )";
        Logger.inform(str);
    }
}


public class VisWindow {

    final static int XWIDTH = 1280;
    final static int YHEIGHT = 1024;
    static Color mainBackground      = Color.lightGray; //new Color(164,211,238); 
    static Color displayBackground   = Color.black;
    static Color displayForeground   = Color.white; //Color.yellow;
    static Color buttonBackground    = new Color(100,149,237); //new Color(255,218,185);
    static Color inputBackground     = Color.white; //new Color(238,213,210);
    static Font  mainFont            = new Font("monospaced", Font.BOLD, 12);

    Database dataBase;
    String dataBaseFile;

    Frame main;
    MultipleChoice paramChoice;
    MultipleChoice direcChoice;
    BWCurveCanvas canvas;

    TextField memoryBenchData;
    TextField memoryBenchProf;
    Button actionClear;
    Button actionRun;
    Button quitButton;

    Label actionStatus;
    TextArea display;


    public VisWindow(String dbFile) {

        dataBaseFile = dbFile;
        dataBase = null;
    }

    boolean initializeWidgets(){

        paramChoice = new MultipleChoice(BWCurveCanvas.freeParamNames,true);
        direcChoice = new MultipleChoice(BWCurveCanvas.directionNames,true);

        Panel paramTypePanel = new Panel();
        paramTypePanel.setLayout(new GridLayout(1,2,5,5));
        paramTypePanel.add(paramChoice);
        paramTypePanel.add(direcChoice);

        actionClear  =  new Button("Clear"); 
        actionClear.setBackground(buttonBackground);
        actionStatus = new Label("Status",Label.CENTER);
        actionStatus.setBackground(Color.black);
        actionStatus.setForeground(inputBackground);
        actionRun =  new Button("Draw BW Curve"); 
        actionRun.setBackground(buttonBackground);
        quitButton =  new Button("Quit"); 
        quitButton.setBackground(buttonBackground);


        Panel buttonPanel = new Panel();
        buttonPanel.setLayout(new GridLayout(1,4,20,20));
        buttonPanel.add(actionRun); 
        buttonPanel.add(actionClear);
        buttonPanel.add(quitButton);
        buttonPanel.add(actionStatus);

        memoryBenchData = new TextField();
        memoryBenchData.setEditable(true);
        memoryBenchData.setBackground(inputBackground);
        memoryBenchProf = new TextField();
        memoryBenchProf.setEditable(true);
        memoryBenchProf.setBackground(inputBackground);

        Panel paramInputPanelTop = new Panel();
        paramInputPanelTop.setLayout(new BorderLayout());
        paramInputPanelTop.add(new Label("Memory Benchmark Data",Label.LEFT),BorderLayout.WEST);
        paramInputPanelTop.add(memoryBenchData,BorderLayout.CENTER);

        Panel paramInputPanelMid = new Panel();
        paramInputPanelMid.setLayout(new BorderLayout());
        paramInputPanelMid.add(new Label("OR Memory Profile Id ",Label.LEFT),BorderLayout.WEST);
        paramInputPanelMid.add(memoryBenchProf,BorderLayout.CENTER);

        Panel paramInputPanel = new Panel();
        paramInputPanel.setLayout(new GridLayout(4,1,5,5));
        paramInputPanel.add(new Label("Enter Memory Profile Info, Choose Param & Direction and Run"));
        paramInputPanel.add(paramInputPanelTop);
        paramInputPanel.add(paramInputPanelMid);
        paramInputPanel.add(buttonPanel);

        main = new Frame("viswin");
        main.setLayout(new BorderLayout(10,100));
        main.setFont(mainFont);
        main.setBackground(mainBackground);

        Panel actionsPanel = new Panel();
        actionsPanel.setLayout(new BorderLayout(5,5));
        actionsPanel.setBackground(mainBackground);
        actionsPanel.add(paramTypePanel,BorderLayout.WEST);
        actionsPanel.add(paramInputPanel,BorderLayout.CENTER);

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        canvas = new BWCurveCanvas(actionStatus);

        display = new TextArea("Display is used for informational purposes\n",5,80);
        display.setEditable(false);
        display.setBackground(displayBackground);
        display.setForeground(displayForeground);

        Logger.setGuiDisplay(display);

        main.setLayout(new BorderLayout(10,10));
        main.add(actionsPanel,BorderLayout.NORTH);
        main.add(canvas,BorderLayout.CENTER);
        main.add(display,BorderLayout.SOUTH);

        return true;
    }

    Object[] parseMemoryBench(String line){
        int tokenCountCheck = 0;
        String[] fields = line.split(",");
        for(int j=0;j<fields.length;j++){
            fields[j] = Util.cleanWhiteSpace(fields[j],false);
        }
        Integer levelCount = Util.toInteger(fields[1]);
        String restOfSig = Util.getMemoryProfileSignature(fields[0],levelCount);
        if(restOfSig == null){
            Logger.warn("[" + line + "] has to have a known BW bwMethod and valid level count");
            return null;
        } else {
            Logger.inform("Signature for " + fields[0] + "," + levelCount + " is " + restOfSig);
            tokenCountCheck = 2 + restOfSig.length();
        }
        if(fields.length != tokenCountCheck){
            Logger.warn("[" + line + "] has to be " + tokenCountCheck + " fields, not " + fields.length);
            return null;
        }
        String signature = "si" + restOfSig;
        Object[] tokens = Util.stringsToRecord(fields,signature);
        if(tokens == null){
            Logger.warn("One or more entry in memory benchmark data is invalid " + Util.listToString(fields));
            return null;
        }

        return tokens;
    }

    boolean initializeListeners(){

        actionRun.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {

                clearActionStatus();

                int paramIdx = paramChoice.getSelectedIndex();
                int direcIdx = direcChoice.getSelectedIndex();

                String membench = memoryBenchData.getText();
                String memprofid = memoryBenchProf.getText();

                if((paramIdx != MultipleChoice.NO_SELECTION) && (direcIdx == MultipleChoice.NO_SELECTION)){
                    Logger.warn("The direction of change in param needs to be selected, currently " + direcIdx);
                    reportActionFailure();
                    return;
                }

                /*** memprofid = new String("192378"); for testing it may be easier to use **/
                if(((membench == null) || membench.equals("")) && 
                   ((memprofid == null) || memprofid.equals(""))){
                        Logger.warn("Memory benchmark data is required and needs to be entered");
                        reportActionFailure();
                        return;
                }

                Object[] profile = null;
                if((membench != null) && !membench.equals("")){
                    Logger.inform("Using entered profile data " + membench);
                    profile = parseMemoryBench(membench);
                    if(profile == null){
                        Logger.warn("Memory benchmark data " + membench + " is not valid or the format is wrong");
                        reportActionFailure();
                        return;
                    }
                } else { //if((memprofid != null) && !memprofid.equals("")){
                    Logger.inform("Using entered profile idx " + memprofid);
                    Integer profIdx = Util.toInteger(memprofid);
                    if(profIdx == null){
                        Logger.warn("The profile id " + profIdx + " is not a valid integer");
                        reportActionFailure();
                        return;
                    }

                    if(openDatabase()){
                        profile = dataBase.getMemoryProfile(profIdx.intValue());
                        closeDatabase();
                        if(profile == null){
                            Logger.warn("Profile Idx given " + profIdx + " is invalid");
                            reportActionFailure();
                            return;
                        }
                    } else {
                        Logger.warn("Database can not be opened, so enter only membench data");
                        reportActionFailure();
                        return;
                    }
                }

                assert (profile != null);

                if(!((String)profile[0]).equals("BWstretchedExp") && 
                   !((String)profile[0]).equals("ti09")){
                    Logger.warn("The profile type " + ((String)profile[0]) + " is not supported for now");
                    reportActionFailure();
                    return;
                }

                int levelCount =((Integer)profile[1]).intValue();
                BWMethodStretchedExp bwMethod = new BWMethodStretchedExp(levelCount);

                printProfile(profile);

                canvas.setTypeAndDir(paramIdx,direcIdx);
                canvas.setBWParams(levelCount,profile,bwMethod);
                canvas.paint(canvas.getGraphics());

            }
        });
        
        actionClear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                paramChoice.clearBoxes();
                direcChoice.clearBoxes();
                clearActionStatus();
                memoryBenchData.setText(null);
                memoryBenchProf.setText(null);

                canvas.setTypeAndDir(0,0);
                canvas.setBWParams(0,null,null);
                canvas.paint(canvas.getGraphics());
            }
        });

        quitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                main.setVisible(false);
                main.dispose();
            }
        });

        return true;
    }

    void printProfile(Object[] prof){
        String str = "( ";
        for(int i=2;i<prof.length;i++){
            if(prof[i] instanceof Double){
                str += " " + prof[i];
            }
        }
        str += " )";
        Logger.inform(str);
    }
    void reportActionFailure(){
        actionStatus.setBackground(Color.red);
    }
    void reportActionSuccess(){
        actionStatus.setBackground(Color.green);
    }
    void clearActionStatus(){
        actionStatus.setBackground(Color.black);
    }

    String checkFormatAndReturn(Choice choice,TextField text){
        String retValue = null;
        if(choice.getSelectedIndex() != 0){
            String[] tokens = choice.getSelectedItem().split("\\|");
            assert(tokens.length == 2);
            retValue = Util.cleanWhiteSpace(tokens[0]);
        } else {
            if(!isInvalidTextInput(text.getText())){
                retValue = Util.cleanWhiteSpace(text.getText());
            }
        }
        if((retValue != null) && (Util.machineList(retValue) == null)){
            return null;
        }
        return retValue;
    }
    boolean isInvalidTextInput(String str){
        if(str == null){
            return true;
        }
        str = Util.cleanWhiteSpace(str);
        return str.equals("");
    }

    public void run() {

        boolean status = initializeWidgets();
        assert (status);

        status = initializeListeners();


        Dimension minSize = new Dimension(XWIDTH,YHEIGHT);
        main.setMinimumSize(minSize);
        main.setVisible(true);

        main.addWindowListener(new WindowAdapter() { 
            public void windowClosing(WindowEvent e) {
                main.setVisible(false);
                main.dispose();
            }
        });
    }

    boolean openDatabase(){
        if(dataBaseFile != null){
            if(Util.isFile(dataBaseFile)){
                Logger.inform("Working on already existing dbase " + dataBaseFile);
            } else {
                Logger.inform("Will work on new database " + dataBaseFile);
            }
            Logger.inform("Text based database is used");
            dataBase = new BinaryFiles(dataBaseFile);
        } else {
            dataBase = new Postgres();
            Logger.inform("Postgres database is used");
        }
        boolean status = dataBase.initialize();
        if(!status){
            Logger.warn("Database can not be initialized");
            return false;
        }
        return true;
    }
    boolean closeDatabase(){
        if(dataBase == null){
            return false;
        }
        boolean status = dataBase.commit();
        if(!status){
            Logger.warn("Can not terminate the database");
            return false;
        }
        dataBase = null;
        return true;
    }
}
