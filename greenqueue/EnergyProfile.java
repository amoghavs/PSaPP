package PSaPP.greenqueue;

import java.util.*;

import PSaPP.util.*;

public class EnergyProfile extends CodeProfile {
    String key = "none";

    public Map<Long, Double> getTime() {
        return this.time;
    }
    public Map<Long, Double> getSysPower() {
        return this.sysPower;
    }
    public Map<Long, Double> getCpuPower(int socket) {
        switch(socket) {
            case 1:
                return this.cpu1Power;
            case 2:
                return this.cpu2Power;
            default:
                return null;
        }
    }
    public Map<Long, Double> getMemPower(int dimm) {
        switch(dimm) {
            case 1:
                return this.mem1Power;
            case 2:
                return this.mem2Power;
            default:
                return null;
        }
    }
    public Map<Long, Double> getSysEnergy() {
        if( this.sysEnergy == null ) {
            computeEnergy();
        }
        return this.sysEnergy;
    }

    public void setTime(Map<Long, Double> time) {
        this.sysEnergy = null;
        this.time = time;
    }

    public void setSysPower(Map<Long, Double> power) {
        this.sysEnergy = null;
        this.sysPower = power;
    }

    public void setCpuPower(int socket, Map<Long, Double> power) {
        switch(socket) {
            case 1: this.cpu1Power = power;
                return;
            case 2: this.cpu2Power = power;
                return;
            default:
                throw new IllegalArgumentException();
        }
    }
    public void setMemPower(int dimm, Map<Long, Double> power) {
        switch(dimm) {
            case 1: this.mem1Power = power;
                return;
            case 2: this.mem2Power = power;
                return;
            default:
                throw new IllegalArgumentException();
        }
    }

    public void addStats(Long freq, Double time, Double sysPower, Double cpu1Power, Double cpu2Power,
                         Double mem1Power, Double mem2Power) {
        this.time.put(freq, time);
        this.sysPower.put(freq, sysPower);
        this.cpu1Power.put(freq, cpu1Power);
        this.cpu2Power.put(freq, cpu2Power);
        this.mem1Power.put(freq, mem1Power);
        this.mem2Power.put(freq, mem2Power);
    }

    public Long getBestFreq() {
        if( this.time == null || this.sysPower == null ) {
            return null;
        }
        if( this.sysEnergy == null ) {
            computeEnergy();
        }
        return bestFreq;
    }

    public String toString() {
        return getBestFreq() + ":" + super.toString();
    }

    public boolean checkProfile() {
        List<Long> freqs = new ArrayList(sysPower.keySet());
        Collections.sort(freqs);
        Double lastPower1 = null;
        Double lastPower2 = null;
        Double lastSysPower = null;
        for( Iterator<Long> it = freqs.iterator(); it.hasNext(); ) {
            Long freq = it.next();
            Double cpu1Power = this.cpu1Power.get(freq);
            Double cpu2Power = this.cpu2Power.get(freq);
            Double sysPower = this.sysPower.get(freq);

            if( lastPower1 != null && lastPower1 > cpu1Power ) {
                return false;
            }
            if( lastPower2 != null && lastPower2 > cpu2Power ) {
                return false;
            }
            if( lastSysPower != null && lastSysPower > sysPower ) {
                return false;
            }
            lastPower1 = cpu1Power;
            lastPower2 = cpu2Power;
            lastSysPower = sysPower;
        }

        return true;
    }

    public boolean checkSysPower() {
        return increasingMonotonic(this.sysPower);
    }

    public boolean checkCpuPower() {
        return increasingMonotonic(this.cpu1Power) && increasingMonotonic(this.cpu2Power);
    }


    // -- Private -- //
    private Map<Long, Double> time = Util.newHashMap();
    private Map<Long, Double> sysPower = Util.newHashMap();
    private Map<Long, Double> cpu1Power = Util.newHashMap();
    private Map<Long, Double> cpu2Power = Util.newHashMap();
    private Map<Long, Double> mem1Power = Util.newHashMap();
    private Map<Long, Double> mem2Power = Util.newHashMap();
    private Map<Long, Double> sysEnergy = null;
    private Long bestFreq = null;
    private Double bestEnergy = null;

    private void computeEnergy() {
        Map<Long, Double> energies = Util.newHashMap();
        for( Iterator<Map.Entry<Long, Double>> it = this.time.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Double> ent = it.next();
            Long freq = ent.getKey();
            Double t = ent.getValue();

            Double power = this.sysPower.get(freq);
            if( power == null ) {
                Logger.warn("Missing power data for frequency " + freq);
                continue;
            }

            Double energy = power * t;
            if( bestEnergy == null || energy < bestEnergy ) {
                bestEnergy = energy;
                bestFreq = freq;
            }
            energies.put(freq, energy);
        }
        this.sysEnergy = energies;
        Logger.inform("Energies: " + energies);
    }

    private boolean increasingMonotonic(Map<Long, Double> data) {
        List<Long> freqs = new ArrayList(data.keySet());
        Collections.sort(freqs);
        Double lastdata = null;
        for( Iterator<Long> it = freqs.iterator(); it.hasNext(); ) {
            Long freq = it.next();
            Double curdata = data.get(freq);

            if( lastdata != null && lastdata > curdata ) {
                return false;
            }
            lastdata = curdata;
        }

        return true;
    }

    
}

