package PSaPP.data;

import PSaPP.util.*;

public class BlockID implements Comparable<BlockID> {
    public final Long imgHash;
    public final Long blockHash;

    public BlockID(Long imgHash, Long blockHash) {
        this.imgHash = imgHash;
        this.blockHash = blockHash;
    }

    @Override
    public int hashCode() {
        return imgHash.hashCode()^blockHash.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if(this == other) {
            return true;
        }
        if( !(other instanceof BlockID) ) {
            return false;
        }
        BlockID other2 = (BlockID)other;
        return imgHash.equals(other2.imgHash) && blockHash.equals(other2.blockHash);
    }

    public int compareTo(BlockID other) {
        int res = imgHash.compareTo(other.imgHash);
        if(res == 0) {
            return blockHash.compareTo(other.blockHash);
        } else {
            return res;
        }
    }

    @Override
    public String toString() {
        return "0x" + Long.toHexString(imgHash) + ":0x" + Long.toHexString(blockHash);
    }

    public String shortString(){
        return "0x" + Long.toHexString(blockHash);
    }

    public FunctionID functionID() {
        return new FunctionID(imgHash, blockHash);
    }

    public BlockID blockID() {
        return new BlockID(imgHash, block(blockHash));
    }

    /*
     * See PEBIL/include/Base.h
     *
     * BlockHash {
     *     16 : instruction
     *     16 : block
     *     16 : function
     *     8  : section
     *     8  : res
     */
    public static long instruction(long blockHash) {
        return blockHash & 0x00FFFFFFFFFFFFFFL;
    }

    public static long block(long blockHash) {
        return blockHash & 0x00FFFFFFFFFF0000L;
    }

    public static long function(long blockHash) {
        return blockHash & 0x00FFFFFF00000000L;
    }

    public static long section(long blockHash) {
        return blockHash & 0x00FF000000000000L;
    }


}

