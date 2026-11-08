package dev.rentaldisplay.probe;

/** Connection lifecycle is independent of any activity or screen. */
final class StreamLifecycle {
    enum Phase { IDLE, STARTING, RUNNING, STOPPING }
    private Phase phase = Phase.IDLE;
    synchronized boolean requestStart() {
        if (phase != Phase.IDLE) return false;
        phase = Phase.STARTING; return true;
    }
    synchronized boolean markRunning() {
        if (phase != Phase.STARTING) return false;
        phase = Phase.RUNNING; return true;
    }
    synchronized void requestStop() { if (phase != Phase.IDLE) phase = Phase.STOPPING; }
    synchronized void finished() { phase = Phase.IDLE; }
    synchronized boolean active() { return phase != Phase.IDLE; }
    synchronized Phase phase() { return phase; }
}
