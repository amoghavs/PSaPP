package PSaPP.send;
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

import java.io.*;
import java.net.*;

public class FileTransfer {

    //static final String FTP_TARGET = "ftp://anonymous:pmacdata@ftppmac.sdsc.edu/pub/incoming/pmacdata/test";

    String directory;
    String srcFileName;
    String tgtFileName;

    public FileTransfer(String source,String tgtName){
        int idx = source.lastIndexOf('/');
        if(idx < 0){
            directory = ".";
            srcFileName = source;
        } else {
            directory = new String(source.toCharArray(),0,idx);
            srcFileName = source.substring(idx+1);
        }
        tgtFileName = srcFileName;
        if(tgtName != null){
            assert (tgtName.lastIndexOf('/') < 0);
            tgtFileName = tgtName;
        }
    }
    public FileTransfer(String target){
        directory = null;
        srcFileName = null;
        assert ((target != null) && (target.lastIndexOf('/') < 0));
        tgtFileName = target;
    }

    public OutputStream getRemoteDesc(){
        String destination = "";
        try {
            String ftpTarget = ConfigSettings.getSetting("FTP_URL");
            destination = ftpTarget + "/" + tgtFileName;
            URL url = new URL(destination);
            URLConnection urlCon = url.openConnection();
            OutputStream destFile = urlCon.getOutputStream();
            return destFile;
        } catch (Exception e){
            Logger.warn("check the permissions of " + destination + "\n" + e);
            return null;
        }
    }

    public boolean transfer(){

        if((directory == null) || (tgtFileName == null)){
            Logger.warn("src path is missing for ftp");
            return false;
        }

        Logger.inform("Transfering file");
        String source = directory + "/" + srcFileName;
        Logger.inform("\tfrom : " + source);
        String destination = "";

        try {
            String ftpTarget = ConfigSettings.getSetting("FTP_URL");
            destination = ftpTarget + "/" + tgtFileName;
            Logger.inform("\tto   : " + destination);

            URL url = new URL(destination);
            URLConnection urlCon = url.openConnection();
            urlCon.setDoOutput(true);
            OutputStream destFile = urlCon.getOutputStream();

            FileInputStream inputFile = new FileInputStream(source);
            byte[] buffer = new byte[4096];

            long totalBytes = LinuxCommand.getFileSize(source);
            FileProgress p = new FileProgress(totalBytes);
            while(true){
                int n = inputFile.read(buffer);
                if(n < 0){
                    break;
                }
                destFile.write(buffer,0,n);
                p.addProgress((long)n);
            }
            p.finishedProgress();

            inputFile.close();

            int ftplimit = Integer.MAX_VALUE;
            try {
                ftplimit = Integer.parseInt(ConfigSettings.getSetting("FTP_TIMEOUT_LIMIT"));
            } catch (NumberFormatException nfe){
                //Logger.inform("FTP timeout value from config file is not a valid int, using default value of " + ftplimit);
            }
            if (p.getElapsedTime() < ftplimit){
                destFile.close();
            } else {
                Logger.warn("Not closing output file since it is probable that an ftp timeout occurred");
                Logger.warn("Flushing stream and waiting since file can't be closed...");
                // for OutputStream flush apparently does nothing, but why not try anyway
                destFile.flush();
                Thread.sleep(10000);
                destFile.flush();
            }
            return true;

        } catch (Exception e){
            Logger.warn("Check the permissions of " + source + "," + destination + " for ftp");
            return false;
        }
    }
}
