package PSaPP.data;

import java.util.*;

import PSaPP.util.*;

public class CacheDescription {

    private int levelCount;

    private class Level {
        int size;
        int assoc;
        int bytesPerLine;
        String repl;
    }
    final private Vector<Level> levels;

    public CacheDescription(int nlevels) {
        this.levels = Util.newVector(nlevels);
        this.levelCount = nlevels;
    }

    void setLevelInfo(int level, int size, int assoc, int bytesPerLine, String repl) {
        Level l = new Level();
        l.size = size;
        l.assoc = assoc;
        l.bytesPerLine = bytesPerLine;
        l.repl = repl;
        this.levels.add(level-1, l);
    }

    public int getLevelCount() {
        return this.levelCount;
    }

    public int getLevelSize(int level) {
        return this.levels.get(level-1).size;
    }

    public int getLevelAssociativity(int level) {
        return this.levels.get(level-1).assoc;
    }

    public int getLevelLineSize(int level) {
        return this.levels.get(level-1).bytesPerLine;
    }

    public String getLevelReplacementPolicy(int level) {
        return this.levels.get(level-1).repl;
    }
}

