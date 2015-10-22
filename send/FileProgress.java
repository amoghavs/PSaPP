package PSaPP.send;
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

import java.io.*;
import java.net.*;
import java.util.Date;

public class FileProgress {

    static final long granularity = 100;

    static final long MILLISECONDS_PER_SECOND = 1000;

    long totalSize;
    long currentSize;
    String lastWritten = "";
    long percOnScreen;
    long secOnScreen;

    Date startTime;

    public FileProgress(long total){
        startTime = new Date();
        totalSize = total;
        percOnScreen = 0;
        secOnScreen = 0;
        Logger.exact("Transferring " + totalSize + " bytes... ");
        printCurrentToScreen();
    }

    public void addProgress(long n){
        if (n < 0){
            Logger.error("Cannot update progress with negative number " + n);
        }
        updateProgress(currentSize + n);
    }

    private void updateProgress(long n){
        currentSize = n;
        if (getPercentage() != percOnScreen || getElapsedTime() != secOnScreen){
            deleteLastFromScreen();
            printCurrentToScreen();
        }
    }

    public void finishedProgress(){
        updateProgress(totalSize);
        Logger.exact("\n");
    }

    private void deleteLastFromScreen(){
        for (int i = 0; i < lastWritten.length(); i++){
            Logger.exact("\b");
        }
    }

    private void printCurrentToScreen(){
        percOnScreen = getPercentage();
        secOnScreen = getElapsedTime();
        String msg = "[" + Long.toString(percOnScreen) + "%] " + Long.toString(secOnScreen) + "s";
        Logger.exact(msg);
        lastWritten = msg;
    }

    private long getPercentage(){
        return (currentSize * granularity) / totalSize;
    }

    public long getElapsedTime(){
        Date now = new Date();
        return (now.getTime() - startTime.getTime()) / MILLISECONDS_PER_SECOND;
    }
}
