package PSaPP.data;

import java.util.*;

import PSaPP.util.*;

public class PSiNSCount {

    public enum Event {
        MPI_SEND,
        MPI_BSEND,
        MPI_RSEND,
        MPI_SSEND,
        MPI_ISEND,
        MPI_IBSEND,
        MPI_IRSEND,
        MPI_ISSEND,

        MPI_RECV,
        MPI_IRECV,
        MPI_SEND_INIT,
        MPI_RECV_INIT,
        MPI_BSEND_INIT,
        MPI_RSEND_INIT,
        MPI_SSEND_INIT,
        MPI_REQUEST_FREE,

        MPI_SENDRECV,
        MPI_SENDRECV_R,

        MPI_SCAN,
        MPI_PROBE,
        MPI_CANCEL,

        MPI_START,
        MPI_STARTALL,

        MPI_WAIT,
        MPI_WAITALL,
        MPI_WAITANY,
        MPI_WAIT_SOME,

        MPI_BCAST,
        MPI_BARRIER,

        MPI_REDUCE,
        MPI_ALLREDUCE,
        MPI_REDUCE_SCATTER,

        MPI_GATHER,
        MPI_GATHERV,
        MPI_ALLGATHER,
        MPI_ALLGATHERV,

        MPI_SCATTER,
        MPI_SCATTERV,

        MPI_ALLTOALL,
        MPI_ALLTOALLV,

        MPI_FINALIZE,
        MPI_ABORT,
        MPI_INIT,
        MPI_COMM_CREATE,
        MPI_COMM_DUP,
        MPI_COMM_SPLIT,
        MPI_CART_CREATE,
        MPI_CART_SUB,
        MPI_GRAPH_CREATE,
        MPI_INTERCOMM_CREATE,
        MPI_INTERCOMM_MERGE,
        MPI_NOCOMM,
        MPI_COMM_FREE,
        CPUTIME
    }


    // stats
    // rank -> stats

    // app, dataset, ncpu, system
    final String app;
    final String dataset;
    final int ncpu;
    final String system;

    // rank -> event -> time/count
    final private Set<Event> eventsInTrace = EnumSet.noneOf(Event.class);
    final private Vector<Map<Event, Double>> rankEventTimes;
    final private Vector<Map<Event, Long>> rankEventCounts;

    public PSiNSCount(String app, String dataset, int ncpu, String system) {
        this.app = app;
        this.dataset = dataset;
        this.ncpu = ncpu;
        this.system = system;

        this.rankEventTimes = Util.newVector(ncpu);
        this.rankEventCounts = Util.newVector(ncpu);
        this.rankEventTimes.setSize(ncpu);
        this.rankEventCounts.setSize(ncpu);
    }

    public double getTime(int rank, Event e) {
        Map<Event, Double> eventTimes = rankEventTimes.elementAt(rank);
        if( eventTimes == null ) {
            return 0.0;
        }

        Double retval = eventTimes.get(e);
        if( retval == null ) {
            return 0.0;
        }
        return retval;
    }

    public Vector<Double> getTimes(Event e) {
        Logger.inform("rank event times has " + rankEventTimes.size() + " entries");
        Vector<Double> rankTimes = Util.newVector(rankEventTimes.size());
        for( int i = 0; i < rankEventTimes.size(); ++i ) {
            Map<Event, Double> m = rankEventTimes.elementAt(i);
            if( m == null ) {
                rankTimes.add(i, 0.0);
                continue;
            }
            Double time = m.get(e);
            if( time == null ) {
                time = 0.0;
            }
            rankTimes.add(i, time);
        }
        return rankTimes;
    }

    public double getTime(int rank) {
        return 0.0;
    }

    public long getCount(int rank, Event e) {
        return 0L;
    }

    public void addRankEventData(int rank, Event e, Long count, Double time) {
        if( rank >= this.ncpu ) {
            throw new IllegalArgumentException("Rank exceeds total number of ranks. Unable to add PSiNSCount data");
        }

        Map<Event, Double> eventTimes = rankEventTimes.elementAt(rank);
        if( eventTimes == null ) {
            eventTimes = Util.newEnumMap(Event.class);
            rankEventTimes.setElementAt(eventTimes, rank);
        }
        eventTimes.put(e, time);

        Map<Event, Long> eventCounts = rankEventCounts.elementAt(rank);
        if( eventCounts == null ) {
            eventCounts = Util.newEnumMap(Event.class);
            rankEventCounts.setElementAt(eventCounts, rank);
        }
        eventCounts.put(e, count);

        eventsInTrace.add(e);
    }

    public String toString() {
        String retval;

        // trace header
        retval = app + "_" + dataset + "_" + ncpu + "_" + system + "\n";

        // event column names
        for( Iterator<Event> it = eventsInTrace.iterator(); it.hasNext(); ) {
            Event e = it.next();
            retval += "\t" + e;
        }
        retval += "\n";

        // per rank data
        for( int rank = 0; rank < ncpu; ++rank ) {
            retval += rank;

            Map<Event, Double> eventTimes = rankEventTimes.elementAt(rank);
            for( Iterator<Event> it = eventsInTrace.iterator(); it.hasNext(); ) {
                Event e = it.next();
                Double time = eventTimes.get(e);
                if( time == null ) {
                    time = 0.0;
                }
                retval += "\t" + time;
            }

            retval += "\n";
        }

        return retval;
    }

    static String makeKey(String app, String dataset, int ncpu, String system) {
        return app + dataset + ncpu + system;
    }
}

