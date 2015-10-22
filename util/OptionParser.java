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


import java.util.*;

public class OptionParser {
    
    HashMap  option2Type;
    HashMap  option2Value;
    CommandLineInterface commandLineObj;

    public OptionParser(String[] opts,CommandLineInterface cli){
        for(int i=0;i<opts.length;i++){
            if(opts[i].indexOf("-") >= 0){ 
                Logger.error("Option " + opts[i] + " in list has hypen, please remove.");
            } 
        }

        option2Type  = new HashMap();

        for(int i=0;i<opts.length;i++){
            assert (opts[i].indexOf(':') == opts[i].lastIndexOf(':'));
            int idx = opts[i].indexOf(':');
            if(idx < 0){
                Logger.error(opts[i] + " is not a valid option specification");
            }
            assert (idx == (opts[i].length() - 2));

            String optionName = new String(opts[i].toCharArray(),0,idx);
            String optionType = new String(opts[i].toCharArray(),idx+1,1);

            if((optionType.charAt(0) == '?') ||
               (optionType.charAt(0) == 'i') ||
               (optionType.charAt(0) == 's') ||
               (optionType.charAt(0) == 'f') ||
               (optionType.charAt(0) == 'd'))
            {
                option2Type.put(optionName,optionType);
            } else {
                Logger.error(optionName + " does not have a valid type " + optionType);
            }

        }

        commandLineObj = cli;
    }
    public void parse(String[] args){
        option2Value = new HashMap();
        for(int i=0;i<args.length;i++){
            if(args[i].startsWith("-")){
                int idx = args[i].lastIndexOf('-');
                String option = args[i].substring(idx+1);
                String optionType = (String)option2Type.get(option);
                if(optionType == null){
                    printUsage("Invalid option " + args[i]);
                }
                try {
                    Object value = null;
                    if(optionType.charAt(0) == '?'){
                        value = new Boolean(true);
                    } else {
                        if((i+1) >= args.length){
                            printUsage("Invalid option value for " + args[i]);
                        }
                        if(optionType.charAt(0) == 'i'){
                            value = new Integer(args[++i]);
                        } else if(optionType.charAt(0) == 's'){
                            String vals = "";
                            while(i+1 < args.length && !args[i+1].startsWith("-")) {
                                vals += " " + args[++i];
                            }
                            value = vals.substring(1);
                        } else if(optionType.charAt(0) == 'f'){
                            value = new Float(args[++i]);
                        } else if(optionType.charAt(0) == 'd'){
                            value = new Double(args[++i]);
                        }
                    }
                    assert (value != null);
                    option2Value.put(option,value);
                } catch (Exception e){
                    Logger.error("Invalid option value at " + args[i]);
                }
            } else {
                Logger.error("Invalid option or you forgot the value for the option " + args[i]);
            }
        }
    }

    public String toString(){
        String ret = "";
        if(option2Value != null){
            ret += "Options X Values :";
            Set keys = option2Value.keySet();
            Iterator it = keys.iterator();
            while(it.hasNext()){
                Object key = it.next();
                ret += ("\n\t" + key + " --> " + option2Value.get(key));
            }
        } else {
            ret += "Options:";
            Set keys = option2Type.keySet();
            Iterator it = keys.iterator();
            while(it.hasNext()){
                Object key = it.next();
                ret += ("\n\t" + key + " --> " + option2Type.get(key));
            }
        }
        return ret;
    }

    public boolean verify(){
        return commandLineObj.verifyValues(option2Value);
    }
    public TestCase getTestCase(){
        return commandLineObj.getTestCase(option2Value);
    }
    public void printUsage(String str){
        commandLineObj.printUsage(str);
        System.exit(-1);
    }
    public boolean isHelp() {
        return commandLineObj.isHelp(option2Value);
    }
    public boolean isVersion() {
        return commandLineObj.isVersion(option2Value);
    }
    public Object getValue(String key){
        return option2Value.get(key);
    }
}
