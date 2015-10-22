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

import PSaPP.util.*;
import java.util.*;

class CacheLevel {
    long hitCount = 0;
    long missCount = 0;

    public double getHitRate(){
        if (hitCount + missCount == 0L){
            return CacheStats.INVALID_HIT_RATE;
        }
        double x = (double)hitCount;
        x = x / (double)(x + missCount);
        return x;
    }
}

public class CacheStats {
    public static final long INVALID_COUNT = -999;
    public static final double INVALID_HIT_RATE = -1.0;

    public final Vector<CacheLevel> levels = Util.newVector(3);
    int maxLevel = 0;

    public CacheStats(){
    }

    public String toString(){
        String s = "CacheStats: ";
        for( int lvl = 1; lvl <= levels.size(); ++lvl ) {
            long hits = getHits(lvl);
            long misses = getMisses(lvl);
            if( hits == INVALID_COUNT || misses == INVALID_COUNT ) {
                continue;
            }
            s += "\tL" + lvl + ": [h" + hits + ", m" + misses + "]";
        }
        return s;
    }

    public CacheStats(int level){
        assert(level > 0 && level <= 3);
        levels.setSize(level);
        for (int i = 0; i < level; i++){
            levels.setElementAt(new CacheLevel(), i);
        }
    }

    public void addCounts(CacheStats c) {
        for( int lvl = 1; lvl <= c.levels.size(); ++lvl ) {
            long hits = c.getHits(lvl);
            long misses = c.getMisses(lvl);
            addLevelCounts(lvl, hits, misses);
        }
    }

    public int getLevels() {
        return levels.size();
    }

    public void addLevelCounts(int level, long hitCount, long missCount) {

        if(hitCount == INVALID_COUNT) {
            hitCount = 0;
        }
        if(missCount == INVALID_COUNT) {
            missCount = 0;
        }
        assert(hitCount >= 0L && missCount >= 0L);

        if( levels.size() < level ) {
            levels.setSize(level);
        }
        CacheLevel l = levels.elementAt(level-1);
        if( l == null ) {
            l = new CacheLevel();
            levels.setElementAt(l, level-1);
        }

        l.hitCount += hitCount;
        l.missCount += missCount;
    }

    public long getHits(int level) {

        if( levels.size() < level ) {
            return INVALID_COUNT;
        }

        CacheLevel l = levels.elementAt(level-1);
        if( l == null ) {
            return INVALID_COUNT;
        }
        return l.hitCount;
    }

    public long getMisses(int level) {

        if( levels.size() < level ) {
            return INVALID_COUNT;
        }

        CacheLevel l = levels.elementAt(level-1);
        if( l == null ) {
            return INVALID_COUNT;
        }
        return l.missCount;
    }

    public double getHitRate(int level){
        if( levels.size() < level ) {
            return INVALID_HIT_RATE;
        }
        CacheLevel l = levels.elementAt(level-1);
        if (l == null){
            return INVALID_HIT_RATE;
        }
        return l.getHitRate();
    }

    public CacheLevel getCacheLevel(int level) {
        return this.levels.get(level-1);
    }

    public boolean hasLevel(int level){
        return (getHits(level) != INVALID_COUNT && getMisses(level) != INVALID_COUNT);
    }
}


