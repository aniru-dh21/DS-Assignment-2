package Weather.util;

public class LamportClock {
    private int time;

    public LamportClock() {
        this.time = 0;
    }

    public synchronized int tick() {
        return ++time; // This is a local event
    }

    public synchronized int update(int receivedTime) {
        time = Math.max(time, receivedTime) + 1;
        return time;
    }

    public synchronized int getTime() {
        return time;
    }
}
