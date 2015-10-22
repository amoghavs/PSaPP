package PSaPP.greenqueue;


public class FunctionProfile extends AggregatorProfile {

    final public String name;

    // Function Profile Methods
    public FunctionProfile(PSaPP.cfg.Function f, Integer sysid) {
        super(f.funcInfo, sysid);
        this.name = f.funcInfo.functionName;
    }

    public FunctionProfile(PSaPP.data.Function f, Integer sysid) {
        super(f, sysid);
        this.name = f.functionName;
    }

    // -- Private -- //
}

