package PSaPP.data;

public class FunctionID extends BlockID {
    public static FunctionID INVALID_FUNCTION = new FunctionID(0L, function(Long.MAX_VALUE) | section(Long.MAX_VALUE));

    public FunctionID(Long imgHash, Long blockHash) {
        super(imgHash, function(blockHash) | section(blockHash));
    }

}

