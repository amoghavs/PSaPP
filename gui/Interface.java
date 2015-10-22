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


import java.awt.*;
import java.util.*;
import java.io.*;
import java.sql.*;

import PSaPP.util.*;
import PSaPP.dbase.*;
import PSaPP.stats.*;
import PSaPP.pred.*;
import PSaPP.sim.*;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


class TestCaseListener implements ItemListener {

    int        arrayIndex;
    Choice[]   testCaseChoices;
    String[][] allTestCaseData;
    TreeSet[]  allTestCaseValues;

    TestCaseListener(int idx,Choice[] choices,String[][] allCases,TreeSet[] values){
        arrayIndex = idx;
        testCaseChoices = choices;
        allTestCaseData = allCases;
    }
    public void itemStateChanged(ItemEvent e) {
        //Logger.inform("At this moment we do not narrow choices");
        String[] alreadySelected = new String[testCaseChoices.length];
        for(int j=0;j<testCaseChoices.length;j++){
            if(testCaseChoices[j].getSelectedIndex() == 0){
                alreadySelected[j] = null;
            } else {
                alreadySelected[j] = testCaseChoices[j].getSelectedItem();
            }
        }
    }
}

public class Interface {

    final static int XWIDTH = 1024;
    final static int YHEIGHT = 1024; // 700;
    final static String noSelectionString = "<none>";
    static Color mainBackground      = Color.lightGray; //new Color(164,211,238); 
    static Color seperatorBackground = mainBackground;
    static Color titleBackground     = Color.blue; //new Color(72,61,139);
    static Color titleForeground     = Color.white; //mainBackground;
    static Color displayBackground   = Color.black;
    static Color displayForeground   = Color.white; //Color.yellow;
    static Color buttonBackground    = new Color(100,149,237); //new Color(255,218,185);
    static Color inputBackground     = Color.white; //new Color(238,213,210);
    static Color choiceBackground    = buttonBackground; //new Color(255,228,196);
    static Color checkBoxBackground  = new Color(135,206,250);
    static Font  mainFont            = new Font("monospaced", Font.BOLD, 12);

    Database dataBase;
    String dataBaseFile;

    Frame main;

    TextArea display;

    Choice actionChoice;
    Label actionStatus;
    TextField actionSingleInput;
    Button actionFileBrowse;
    TextField actionFileDisplay;
    Label actionSingleLabels;
    Button actionClear;
    Button actionSubmit;

    static final String[] testCaseFields = { "funding_agency","project","round","application","dataset","cpu_count" };
    Choice[] testCaseChoices;
    String[][] allTestCaseData;
    TreeSet[] allTestCaseValues;

    TextField directDirText;
    TextField scratchText;
    TextField simulatorText;
    TextField shortNameText;

    Button directDirBrowse;
    Button scratchBrowse;
    Button simulatorBrowse; 

    Choice groupChoice;
    Choice baseSystemChoice;
    Choice targetSystemChoice;

    TextField groupText;
    TextField baseProfileText;
    TextField targetProfileText;

    Choice netsimType;
    Choice netsimModel;
    Choice ratioMethod;
    TextField ratioInput;

    Checkbox netsimUseMemtime;
    Checkbox netsimNoUse;
    Checkbox printStats;
    Checkbox printBWStats;

    Label predictionStatus;

    Button predictionSubmit;
    Button predictionClear;

    Choice sensitivityCases;
    TextField sensitivityFactor;

    public Interface(String dbFile) {

        dataBaseFile = dbFile;
        dataBase = null;
    }

    boolean initializeWidgets(){

        main = new Frame("pmacgui");
        main.setLayout(new BorderLayout(20,20));
        main.setFont(mainFont);
        main.setBackground(seperatorBackground);

        Panel displayPanel = new Panel();
        displayPanel.setLayout(new BorderLayout(1,1));

        display = new TextArea("Display is used for informational purposes\n",80,80);
        display.setEditable(false);
        display.setBackground(displayBackground);
        display.setForeground(displayForeground);

        Label tmpLabel = new Label("v OUTPUT v WARNINGS v ERRORS v",Label.CENTER);
        tmpLabel.setBackground(titleBackground);
        tmpLabel.setForeground(titleForeground);
        tmpLabel.setFont(new Font("monospaced", Font.BOLD, 15));

        displayPanel.add(tmpLabel,BorderLayout.NORTH);
        displayPanel.add(display,BorderLayout.CENTER);

        Logger.setGuiDisplay(display);

        // choices for action types and left most panel
        Panel dbPanelL2Left = new Panel();
        dbPanelL2Left.setLayout(new GridLayout(3,1,1,1));
        tmpLabel  = new Label("Data Record Type",Label.CENTER);
        actionChoice = new Choice(); 
        actionChoice.setBackground(choiceBackground);
        String[] actionTypes = Actions.typeList;
        actionChoice.add(noSelectionString);
        for(int i=0;i<actionTypes.length;i++) {
            actionChoice.add(actionTypes[i]);
        }
        dbPanelL2Left.add(tmpLabel);
        dbPanelL2Left.add(actionChoice);

        //choices for single line input, file input
        Panel dbPanelL2Middle = new Panel();
        dbPanelL2Middle.setLayout(new GridLayout(3,1,1,1));
        Panel dbPanelL2MiddleTop = new Panel();
        Panel dbPanelL2MiddleMid = new Panel();
        Panel dbPanelL2MiddleBot = new Panel();
        dbPanelL2MiddleTop.setLayout(new BorderLayout());
        dbPanelL2MiddleMid.setLayout(new BorderLayout());
        dbPanelL2MiddleBot.setLayout(new BorderLayout());
        actionSingleLabels = new Label("",Label.LEFT);
        actionSingleInput = new TextField();
        actionSingleInput.setBackground(inputBackground);
        actionSingleInput.setEditable(false);
        actionFileDisplay = new TextField();
        actionFileDisplay.setEditable(false);
        actionFileDisplay.setBackground(inputBackground);
        actionFileBrowse = new Button("Browse");
        actionFileBrowse.setBackground(buttonBackground);
        actionSingleInput.setBackground(inputBackground);
        actionFileDisplay.setBackground(inputBackground);

        Label tmpLabel1 = new Label("FIELDS",Label.LEFT);
        Label tmpLabel2 = new Label("VALUES",Label.LEFT);
        Label tmpLabel3 = new Label("INFILE",Label.LEFT);

        dbPanelL2MiddleTop.add(tmpLabel1,BorderLayout.WEST);
        dbPanelL2MiddleTop.add(actionSingleLabels,BorderLayout.CENTER);
        dbPanelL2MiddleMid.add(tmpLabel2,BorderLayout.WEST);
        dbPanelL2MiddleMid.add(actionSingleInput,BorderLayout.CENTER);
        dbPanelL2MiddleBot.add(tmpLabel3,BorderLayout.WEST);
        dbPanelL2MiddleBot.add(actionFileDisplay,BorderLayout.CENTER);
        dbPanelL2MiddleBot.add(actionFileBrowse,BorderLayout.EAST);
        dbPanelL2Middle.add(dbPanelL2MiddleTop);
        dbPanelL2Middle.add(dbPanelL2MiddleMid);
        dbPanelL2Middle.add(dbPanelL2MiddleBot);

        // clear submit status for jDB
        Panel dbPanelL2Right = new Panel();
        dbPanelL2Right.setLayout(new GridLayout(3,1,0,3));
        actionClear  =  new Button("Clear"); 
        actionClear.setBackground(buttonBackground);
        actionSubmit =  new Button("Submit"); 
        actionSubmit.setBackground(buttonBackground);
        actionStatus = new Label("Status",Label.CENTER); 
        actionStatus.setBackground(Color.black);
        actionStatus.setForeground(inputBackground);
        dbPanelL2Right.add(actionClear);
        dbPanelL2Right.add(actionSubmit);
        dbPanelL2Right.add(actionStatus);

        tmpLabel = new Label("jDB INTERFACE",Label.CENTER);
        tmpLabel.setBackground(titleBackground);
        tmpLabel.setForeground(titleForeground);
        tmpLabel.setFont(new Font("monospaced", Font.BOLD, 15));

        Panel dbActions = new Panel();
        dbActions.setLayout(new BorderLayout(5,5));
        dbActions.add(tmpLabel,BorderLayout.NORTH);
        dbActions.add(dbPanelL2Left,BorderLayout.WEST);
        dbActions.add(dbPanelL2Middle,BorderLayout.CENTER);
        dbActions.add(dbPanelL2Right,BorderLayout.EAST);
        dbActions.setBackground(mainBackground);

        Panel predictions = new Panel();
        predictions.setLayout(new BorderLayout());
        predictions.setBackground(mainBackground);

        Panel predictionsCore = new Panel();
        predictionsCore.setLayout(new BorderLayout(10,10));

        Panel testCasePanel = new Panel();
        testCasePanel.setLayout(new GridLayout(2,testCaseFields.length,1,1));
        testCaseChoices = new Choice[testCaseFields.length];
        for(int i=0;i<testCaseFields.length;i++){
            tmpLabel = new Label(testCaseFields[i],Label.CENTER);
            testCasePanel.add(tmpLabel);
        }
        for(int i=0;i<testCaseFields.length;i++){
            testCaseChoices[i] = new Choice();
            testCaseChoices[i].setBackground(choiceBackground);
            testCasePanel.add(testCaseChoices[i]);
        }

        Panel directDirPanel = new Panel();
        Panel scratchPanel = new Panel();
        Panel simulatorPanel = new Panel();
        Panel shortNamePanel = new Panel();
        directDirPanel.setLayout(new BorderLayout(1,1));
        scratchPanel.setLayout(new BorderLayout(1,1));
        simulatorPanel.setLayout(new BorderLayout(1,1));
        shortNamePanel.setLayout(new BorderLayout(1,1));

        tmpLabel = new Label("DirectPath",Label.LEFT);
        directDirText = new TextField();
        directDirText.setBackground(inputBackground);
        directDirBrowse = new Button("Browse");
        directDirText.setEditable(true);
        directDirBrowse.setBackground(buttonBackground);
        directDirPanel.add(tmpLabel,BorderLayout.WEST);
        directDirPanel.add(directDirText,BorderLayout.CENTER);
        directDirPanel.add(directDirBrowse,BorderLayout.EAST);

        tmpLabel = new Label("ScratchDir",Label.LEFT);
        scratchText = new TextField();
        scratchText.setBackground(inputBackground);
        scratchBrowse = new Button("Browse");
        scratchText.setEditable(true);
        scratchPanel.add(tmpLabel,BorderLayout.WEST);
        scratchPanel.add(scratchText,BorderLayout.CENTER);
        scratchPanel.add(scratchBrowse,BorderLayout.EAST);
        scratchBrowse.setBackground(buttonBackground);

        tmpLabel = new Label("NetworkSim",Label.LEFT);
        simulatorText = new TextField();
        simulatorText.setBackground(inputBackground);
        simulatorBrowse = new Button("Browse");
        simulatorText.setEditable(true);
        simulatorPanel.add(tmpLabel,BorderLayout.WEST);
        simulatorPanel.add(simulatorText,BorderLayout.CENTER);
        simulatorPanel.add(simulatorBrowse,BorderLayout.EAST);
        simulatorBrowse.setBackground(buttonBackground);

        tmpLabel = new Label("Short Name",Label.LEFT);
        shortNameText = new TextField();
        shortNameText.setBackground(inputBackground);
        shortNameText.setEditable(true);
        shortNamePanel.add(tmpLabel,BorderLayout.WEST);
        shortNamePanel.add(shortNameText,BorderLayout.CENTER);

        predictionStatus = new Label("Status",Label.CENTER); 
        predictionStatus.setBackground(Color.black);
        predictionStatus.setForeground(inputBackground);

        predictionSubmit = new Button("Submit");
        predictionSubmit.setBackground(buttonBackground);
        predictionClear = new Button("Clear");
        predictionClear.setBackground(buttonBackground);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayout(new GridLayout(1,3,5,5));
        buttonPanel.add(predictionClear);
        buttonPanel.add(predictionSubmit);
        buttonPanel.add(predictionStatus);

        Panel predGroupPanel = new Panel();
        Panel baseSystemPanel = new Panel();
        Panel targetSystemPanel = new Panel();
        Panel netsimOptions = new Panel();
        Panel checkboxOptions = new Panel();
        Panel sensitivityOptions = new Panel();
        predGroupPanel.setLayout(new FlowLayout(FlowLayout.LEFT,10,2));
        baseSystemPanel.setLayout(new FlowLayout(FlowLayout.LEFT,10,2));
        targetSystemPanel.setLayout(new FlowLayout(FlowLayout.LEFT,10,2));
        netsimOptions.setLayout(new FlowLayout(FlowLayout.LEFT,20,2));
        checkboxOptions.setLayout(new FlowLayout(FlowLayout.LEFT,20,5));
        sensitivityOptions.setLayout(new FlowLayout(FlowLayout.LEFT,20,5));

        tmpLabel = new Label("Prediction Group      ",Label.LEFT);
        groupChoice = new Choice();
        groupChoice.add(noSelectionString);
        groupChoice.setBackground(choiceBackground);
        groupText = new TextField(20);
        groupText.setBackground(inputBackground);
        predGroupPanel.add(tmpLabel);
        predGroupPanel.add(groupText);
        predGroupPanel.add(groupChoice);

        tmpLabel = new Label("BASE Profile Idx      ",Label.LEFT);
        baseProfileText = new TextField(20);
        baseProfileText.setBackground(inputBackground);
        baseSystemPanel.add(tmpLabel);
        baseSystemPanel.add(baseProfileText);
        tmpLabel = new Label("Resource Idx",Label.LEFT);
        baseSystemChoice = new Choice();
        baseSystemChoice.setBackground(choiceBackground);
        baseSystemPanel.add(new Label("OR"));
        baseSystemPanel.add(tmpLabel);
        baseSystemPanel.add(baseSystemChoice);

        tmpLabel = new Label("TARGET Profile Idx(s) ",Label.LEFT);
        targetProfileText = new TextField(20);
        targetProfileText.setBackground(inputBackground);
        targetSystemPanel.add(tmpLabel);
        targetSystemPanel.add(targetProfileText);
        tmpLabel = new Label("Resource Idx",Label.LEFT);
        targetSystemChoice = new Choice();
        targetSystemChoice.setBackground(choiceBackground);
        targetSystemPanel.add(new Label("OR"));
        targetSystemPanel.add(tmpLabel);
        targetSystemPanel.add(targetSystemChoice);

        baseSystemChoice.add(noSelectionString);
        targetSystemChoice.add(noSelectionString);

        netsimType = new Choice();
        netsimModel = new Choice();
        ratioMethod = new Choice();
        ratioInput = new TextField(20);
        ratioInput.setBackground(inputBackground);
        netsimType.setBackground(choiceBackground);
        netsimModel.setBackground(choiceBackground);
        ratioMethod.setBackground(choiceBackground);

        for(int i=0;i<NetworkSim.validNetworkSims.length;i++){
            netsimType.add(NetworkSim.validNetworkSims[i]);
        }
        netsimType.select(NetworkSim.defaultNetworkSim);
        for(int i=0;i<NetworkSim.validNetworkMods.length;i++){
            netsimModel.add(NetworkSim.validNetworkMods[i]);
        }
        netsimModel.select(NetworkSim.defaultNetworkMod);
        for(int i=0;i<Convolver.validRatioMethods.length;i++){
            ratioMethod.add(Convolver.validRatioMethods[i]);
        }
        ratioMethod.select(Convolver.defaultRatioMethod);

        netsimOptions.add(new Label("Simulation Type ",Label.LEFT));
        netsimOptions.add(netsimType);
        netsimOptions.add(new Label("Simulation Model ",Label.LEFT));
        netsimOptions.add(netsimModel);
        netsimOptions.add(new Label("Ratio Type",Label.LEFT));
        netsimOptions.add(ratioMethod);
        netsimOptions.add(new Label("Input Ratio",Label.LEFT));
        netsimOptions.add(ratioInput);

        netsimUseMemtime = new Checkbox("Use simulated memory time");
        netsimNoUse = new Checkbox("Skip Network Simulation");
        printStats = new Checkbox("Generate Statistics");
        printBWStats = new Checkbox("Generate BWHistograms (Power)");
        netsimUseMemtime.setBackground(checkBoxBackground);
        netsimNoUse.setBackground(checkBoxBackground);
        printStats.setBackground(checkBoxBackground);
        printBWStats.setBackground(checkBoxBackground);
        checkboxOptions.add(new Label("Optional selections"));
        checkboxOptions.add(netsimUseMemtime);
        checkboxOptions.add(netsimNoUse);
        checkboxOptions.add(printStats);
        checkboxOptions.add(printBWStats);

        sensitivityCases = new Choice();
        sensitivityCases.add(noSelectionString);
        String[] validSensCases = Sensitivity.validSensCases;
        for(int i=0;i<validSensCases.length;i++){
            sensitivityCases.add(validSensCases[i]);
        }
        sensitivityFactor = new TextField(20);
        sensitivityFactor.setText(null);

        sensitivityOptions.add(new Label("Sensitivity Study"));
        sensitivityOptions.add(new Label("Case"));
        sensitivityOptions.add(sensitivityCases);
        sensitivityOptions.add(new Label("Factor"));
        sensitivityOptions.add(sensitivityFactor);

        Panel predOtherOptionsPanel = new Panel();
        predOtherOptionsPanel.setLayout(new GridLayout(10,1,0,0));

        predOtherOptionsPanel.add(directDirPanel);
        predOtherOptionsPanel.add(scratchPanel);
        predOtherOptionsPanel.add(predGroupPanel);
        predOtherOptionsPanel.add(baseSystemPanel);
        predOtherOptionsPanel.add(targetSystemPanel);
        predOtherOptionsPanel.add(shortNamePanel);
        predOtherOptionsPanel.add(netsimOptions);
        predOtherOptionsPanel.add(simulatorPanel);
        predOtherOptionsPanel.add(checkboxOptions);
        predOtherOptionsPanel.add(sensitivityOptions);

        predictionsCore.add(testCasePanel,BorderLayout.NORTH);
        predictionsCore.add(predOtherOptionsPanel,BorderLayout.CENTER);
        predictionsCore.add(buttonPanel,BorderLayout.SOUTH);
        
        tmpLabel = new Label("jPredict INTERFACE",Label.CENTER);
        tmpLabel.setBackground(titleBackground);
        tmpLabel.setForeground(titleForeground);
        tmpLabel.setFont(new Font("monospaced", Font.BOLD, 15));

        predictions.add(tmpLabel,BorderLayout.NORTH);
        predictions.add(predictionsCore,BorderLayout.CENTER);

        main.add(dbActions, BorderLayout.NORTH);
        main.add(displayPanel,BorderLayout.CENTER);
        main.add(predictions, BorderLayout.SOUTH);

        if(openDatabase()){
            String[] rows = dataBase.getGUIResourceEntries();
            for(int i=0;i<rows.length;i++){
                baseSystemChoice.add(rows[i]);
                targetSystemChoice.add(rows[i]);
            }
            rows = dataBase.getGUIPredGroupEntries();
            for(int i=0;i<rows.length;i++){
                groupChoice.add(rows[i]);
            }

            allTestCaseData = null;
            allTestCaseValues = null;
            rows = dataBase.getGUITestCases();
            if((rows != null) && (rows.length > 0)){
                allTestCaseData = new String[rows.length][testCaseFields.length];
                for(int i=0;i<rows.length;i++){
                    String str = rows[i];
                    String[] fields = Util.cleanWhiteSpace(str,false).split("\\|");
                    if(fields.length != testCaseFields.length){
                        return false;
                    }
                    for(int j=0;j<fields.length;j++){
                        allTestCaseData[i][j] = fields[j];
                    }
                }
                allTestCaseValues = new TreeSet[testCaseFields.length];
                for(int j=0;j<testCaseFields.length;j++){
                    allTestCaseValues[j] = new TreeSet();
                }

                for(int i=0;i<allTestCaseData.length;i++){
                    for(int j=0;j<testCaseFields.length;j++){
                        allTestCaseValues[j].add(allTestCaseData[i][j]);
                    }
                }
            }
            addTestCaseValues();
            closeDatabase();
        } else {
            return false;
        }
        return true;
    }

    void addTestCaseValues(){
        for(int j=0;j<testCaseFields.length;j++){
            testCaseChoices[j].removeAll();
            testCaseChoices[j].add(noSelectionString);
        }

        for(int j=0;j<testCaseFields.length;j++){
            Iterator it = allTestCaseValues[j].iterator();
            while(it.hasNext()){
                String member = (String)it.next();
                testCaseChoices[j].add(member);
            }
        }
    }

    boolean initializeListeners(){

        //Item Listener for the drop-down box
        actionChoice.addItemListener(new ItemListener(){
            public void itemStateChanged(ItemEvent e) {

                String current = actionChoice.getSelectedItem();
                int index = actionChoice.getSelectedIndex();

                if((index == 0) ||
                   current.equals("TraceStatus") ||
                   current.equals("PhaseStatus") ||
                   current.equals("PredictionResult")){
                    actionSingleInput.setEditable(false);
                    actionSingleInput.setText(null);
                    actionFileDisplay.setEditable(false);
                    actionFileDisplay.setText(null);
                } else {
                    actionSingleLabels.setText(Actions.typeTokenTitles[index-1]);
                    actionSingleInput.setEditable(true);
                    actionSingleInput.setText(null);
                    actionFileDisplay.setEditable(true);
                    actionFileDisplay.setText(null);
                }
            }
        });
        actionFileBrowse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {

                FileDialog fileChoice = new FileDialog(main);
                fileChoice.setVisible(true);

                try{
                    String dir = fileChoice.getDirectory();
                    String fname = fileChoice.getFile();
                    if((dir != null) || (fname != null)) {
                        if(fname == null){
                            fname = "";
                        }
                        String file = dir + fname;
                        if((file != null) && !Util.isFile(file)){
                            Logger.warn(file + " wrong selection, needs to be a file");
                            actionFileDisplay.setText(null);
                        } else {
                            actionFileDisplay.setText(file);
                        }
                    }
                } catch(Exception e) {
                    Logger.warn("While selecting file the problem occurred " + e);
                }
            }
        });



        actionClear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                actionChoice.select(0);
                actionStatus.setBackground(Color.black);
                actionSingleInput.setEditable(false);
                actionSingleInput.setText(null);
                actionFileDisplay.setEditable(false);
                actionFileDisplay.setText(null);
                actionSingleLabels.setText(null);
            }
        });
        actionSubmit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {

                boolean status = true;

                String current = actionChoice.getSelectedItem();
                int index = actionChoice.getSelectedIndex();

                if((index == 0) ||
                   current.equals("TraceStatus") ||
                   current.equals("PhaseStatus") ||
                   current.equals("PredictionResult")){
                    Logger.warn("Select Valid Record Type please");
                    status = false;
                } else {
                    BufferedReader inputFile = null;
                    String file = actionFileDisplay.getText();
                    if((file != null) && !Util.isFile(file)){
                        file = null;
                    }
                    String data = actionSingleInput.getText();
                    if(data != null){
                        data = Util.cleanWhiteSpace(data,false);
                    }
                    if((file == null) && isInvalidTextInput(data)){
                        Logger.warn("Error in input data, select either file or enter data");
                        status = false;
                    }
                    if(status){
                        try {
                            if(file != null){
                                inputFile = new BufferedReader(new FileReader(file));
                            } else {
                                inputFile = new BufferedReader(new StringReader(data));
                            }
                            if(openDatabase()){
                                Actions actions = new Actions(dataBase,inputFile);
                                status = actions.addRecordsFromGUI(actionChoice.getSelectedItem());
                                closeDatabase();
                            } else {
                                Logger.warn("Error in staring the database");
                                status = false;
                            }
                        } catch(Exception e){
                            Logger.warn("Error in opening the source of data " + e);
                            status = false;
                        }
                    }
                }
                if(!status){
                    reportActionFailure();
                } else {
                    reportActionSuccess();
                }
            }
        });

        directDirBrowse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {

                FileDialog fileChoice = new FileDialog(main);
                fileChoice.setVisible(true);

                try{
                    String dir = fileChoice.getDirectory();
                    String fname = fileChoice.getFile();
                    if((dir != null) || (fname != null)) {
                        if(fname == null){
                            fname = "";
                        }
                        String file = dir + fname;
                        if((file != null) && !Util.isDirectory(file)){
                            Logger.warn(file + " wrong selection for direct dir, needs to be a directory");
                            directDirText.setText(null);
                        } else {
                            directDirText.setText(file);
                        }
                    }
                } catch(Exception e) {
                    Logger.warn("While selecting file the problem occurred " + e);
                }
            }
        });

        scratchBrowse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {

                FileDialog fileChoice = new FileDialog(main);
                fileChoice.setVisible(true);

                try{
                    String dir = fileChoice.getDirectory();
                    String fname = fileChoice.getFile();
                    if((dir != null) || (fname != null)) {
                        if(fname == null){
                            fname = "";
                        }
                        String file = dir + fname;
                        if((file != null) && !Util.isDirectory(file)){
                            Logger.warn(file + " wrong selection for scracth, needs to be a directory");
                            scratchText.setText(null);
                        } else {
                            scratchText.setText(file);
                        }
                    }
                } catch(Exception e) {
                    Logger.warn("While selecting file the problem occurred " + e);
                }
            }
        });

        simulatorBrowse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {

                FileDialog fileChoice = new FileDialog(main);
                fileChoice.setVisible(true);

                try{
                    String dir = fileChoice.getDirectory();
                    String fname = fileChoice.getFile();
                    if((dir != null) || (fname != null)) {
                        if(fname == null){
                            fname = "";
                        }
                        String file = dir + fname;
                        if((file != null) && !Util.isDirectory(file)){
                            Logger.warn(file + " wrong selection for scracth, needs to be a directory");
                            simulatorText.setText(null);
                        } else {
                            simulatorText.setText(file);
                        }
                    }
                } catch(Exception e) {
                    Logger.warn("While selecting file the problem occurred " + e);
                }
            }
        });
        predictionClear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                addTestCaseValues();
                directDirText.setText(null);
                scratchText.setText(null);
                simulatorText.setText(null);
                shortNameText.setText(null);

                groupChoice.select(0);
                baseSystemChoice.select(0);
                targetSystemChoice.select(0);

                groupText.setText(null);
                baseProfileText.setText(null);
                targetProfileText.setText(null);

                netsimType.select(NetworkSim.defaultNetworkSim);
                netsimModel.select(NetworkSim.defaultNetworkMod);
                ratioMethod.select(Convolver.defaultRatioMethod);
                ratioInput.setText(null);

                netsimUseMemtime.setState(false);
                netsimNoUse.setState(false);
                printStats.setState(false);
                printBWStats.setState(false);

                predictionStatus.setBackground(Color.black);

                sensitivityFactor.setText(null);
                sensitivityCases.select(0);
            }
        });

        for(int j=0;j<testCaseFields.length;j++){
            testCaseChoices[j].addItemListener(new TestCaseListener(j,testCaseChoices,
                                                    allTestCaseData,allTestCaseValues));
        }
            
        predictionSubmit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {

                LinkedList argsList = new LinkedList();


                for(int j=0;j<testCaseFields.length;j++){
                    if(testCaseChoices[j].getSelectedIndex() == 0){
                        reportPredFailure("All test case choices must be made");
                        return;
                    }
                    argsList.add("--" + testCaseFields[j]);
                    argsList.add(Util.cleanWhiteSpace(testCaseChoices[j].getSelectedItem()));
                }

                if(isInvalidTextInput(directDirText.getText())){
                    reportPredFailure("Direct directory to the trace data needs to be given");
                    return;
                }
                argsList.add("--direct_dir");
                argsList.add(Util.cleanWhiteSpace(directDirText.getText()));

                if(isInvalidTextInput(scratchText.getText())){
                    reportPredFailure("Scratch directory for predictions needs to be given");
                    return;
                }
                argsList.add("--scratch");
                argsList.add(Util.cleanWhiteSpace(scratchText.getText()));

                String groupIdx = checkFormatAndReturn(groupChoice,groupText);
                if(groupIdx == null){
                    reportPredFailure("Prediction group is invalid, needs to be a number and choice has priority");
                    return;
                }
                argsList.add("--prediction_group");
                argsList.add(groupIdx);

                String baseSystemIdx = checkFormatAndReturn(baseSystemChoice,baseProfileText);
                if(baseSystemIdx == null){
                    reportPredFailure("Invalid base resource info. Single value is required. Choice has priority");
                    return;
                }
                if(baseSystemChoice.getSelectedIndex() != 0){
                    argsList.add("--base_system");
                } else {
                    argsList.add("--base_profile");
                }
                argsList.add(baseSystemIdx);

                String targetSystemIdx = checkFormatAndReturn(targetSystemChoice,targetProfileText);
                if(targetSystemIdx == null){
                    reportPredFailure("Invalid target resource(s) info. Either choose 1 system or input profiles");
                    return;
                }
                if(targetSystemChoice.getSelectedIndex() != 0){
                    argsList.add("--machine_list");
                } else {
                    argsList.add("--profile_list");
                }
                argsList.add(targetSystemIdx);

                if(isInvalidTextInput(shortNameText.getText())){
                    reportPredFailure("Short name for predictions needs to be given");
                    return;
                }
                argsList.add("--shortname");
                argsList.add(Util.cleanWhiteSpace(shortNameText.getText(),false));

                argsList.add("--network_simulator");
                argsList.add(netsimType.getSelectedItem());

                argsList.add("--psins_model");
                argsList.add(netsimModel.getSelectedItem());

                argsList.add("--ratio_method");
                argsList.add(ratioMethod.getSelectedItem());

                if(!isInvalidTextInput(ratioInput.getText())){
                    argsList.add("--ratio");
                    String str = Util.cleanWhiteSpace(ratioInput.getText());
                    if(Util.toDouble(str) == null){
                        reportPredFailure("Ratio " + str + " needs to be double");
                        return;
                    }
                    argsList.add(str);
                }

                if(!isInvalidTextInput(simulatorText.getText())){
                    argsList.add("--netsim_dir");
                    argsList.add(Util.cleanWhiteSpace(simulatorText.getText()));
                }

                if(netsimUseMemtime.getState()){
                    argsList.add("--use_sim_memtime");
                }
                if(netsimNoUse.getState()){
                    argsList.add("--noDim");
                }
                if(printStats.getState()){
                    argsList.add("--stats");
                }
                if(printBWStats.getState()){
                    argsList.add("--bwhist");
                }

                if(sensitivityCases.getSelectedIndex() != 0){
                    if(!isInvalidTextInput(sensitivityFactor.getText())){
                        String str = Util.cleanWhiteSpace(sensitivityFactor.getText());
                        if(Util.toDouble(str) == null){
                            reportPredFailure("Ratio " + str + " needs to be double");
                            return;
                        }
                        str = sensitivityCases.getSelectedItem() + "x" + str;
                        Logger.inform("The sensitivity study is " + str);

                        argsList.add("--sensitivity_study");
                        argsList.add(str);
                    } else {
                        reportPredFailure("Ratio \"" + Util.cleanWhiteSpace(sensitivityFactor.getText()) + 
                                          "\" needs to be double. It is empty or not valid");
                        return;
                    }
                } else {
                    Logger.inform("No sensitivity study is asked");
                }

                try {
                    Predict predict = new Predict();
                    String[] args = new String[argsList.size()];
                    int idx = 0;
                    Iterator it = argsList.iterator();
                    while(it.hasNext()){
                        args[idx++] = it.next().toString();
                        //Logger.inform(args[idx-1]);
                    }
                    boolean check = predict.run(args);
                    if(check){
                        reportPredSuccess();
                    } else {
                        reportPredFailure("Prediction run failed due to an error in predict.run");
                    }
                } catch (Exception e) {
                    reportPredFailure("Prediction run failed due to exception " + e);
                }
            }
        });

        return true;
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

    void reportPredFailure(String message){
        Logger.warn(message);
        predictionStatus.setBackground(Color.red);
    }
    void reportPredSuccess(){
        predictionStatus.setBackground(Color.green);
    }
    void reportActionFailure(){
        actionStatus.setBackground(Color.red);
    }
    void reportActionSuccess(){
        actionStatus.setBackground(Color.green);
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
