package PSaPP.data;

/*
Copyright (c) 2011, PMaC Laboratories, Inc.
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

import PSaPP.util.Util;
import java.util.*;
public class AddressRanges {
    public static final long INVALID_ADDRESS = -1;
    public static final String VALID_DFPATTERN_PREFIX = "dfTypePattern_";

    private Vector<AddressRange> ranges = new Vector<AddressRange>();
    private String pattern = null;

    public class AddressRange {
        public long min = 0;
        public long max = 0;
    }

    public Iterator<AddressRange> iterator() {
        return ranges.iterator();
    }

    public AddressRanges(String p){
        assert(isValidPattern(p));
        pattern = p;
    }

    public AddressRanges(){
        this("dfTypePattern_none");
    }

    private boolean isValidRange(AddressRange a){
        return isValidRange(a.min, a.max);
    }

    public static boolean isValidRange(long min, long max){
        if (max < min){
            return false;
        }
        return true;
    }

    public void addRange(long min, long max){
        AddressRange a = new AddressRange();
        a.min = min;
        a.max = max;

        assert(isValidRange(a));
        ranges.add(a);
    }

    public int getNumberOfRanges(){
        return ranges.size();
    }
    
    public AddressRange getRange(int idx){
        return ranges.get(idx);
    }

    public long getRangeSize(int idx){
        return (getMax(idx) - getMin(idx));
    }

    public long getMin(int idx){
        return getRange(idx).min;
    }

    public long getMax(int idx){
        return getRange(idx).max;
    }

    public long overallMin(){
        long min = INVALID_ADDRESS;
        for (int i = 0; i < getNumberOfRanges(); i++){
            if (min == INVALID_ADDRESS || getMin(i) < min){
                min = getMin(i);
            }
        }
        return min;
    }

    public long overallMax(){
        long max = INVALID_ADDRESS;
        for (int i = 0; i < getNumberOfRanges(); i++){
            if (max == INVALID_ADDRESS || getMax(i) > max){
                max = getMax(i);
            }
        }
        return max;
    }

    public long overallRangeSize(){
        return (overallMax() - overallMin());
    }

    public static boolean isValidPattern(String p){
        return p.startsWith(VALID_DFPATTERN_PREFIX);
    }

    public void setPattern(String p){
        pattern = p;
    }

    public String getPattern(){
        return pattern;
    }

}


