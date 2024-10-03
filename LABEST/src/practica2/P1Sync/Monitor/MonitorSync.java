package practica2.P1Sync.Monitor;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MonitorSync {

    private final int N;
    private final Lock l;
    boolean pasat;
    private final Condition entra;
    private int ultim;

    public MonitorSync(int N) {
        this.N = N;
        l = new ReentrantLock();
        pasat = false;
        entra = l.newCondition();

    }

    public void waitForTurn(int id) {
        l.lock();
        try {
            while (ultim == id) {
                entra.awaitUninterruptibly();
            }
            ultim = id;
            pasat = true;
        } finally {
            l.unlock();
        }

    }

    public void transferTurn() {
        l.lock();
        try {
            if (pasat) {
                entra.signal();
            }
        } finally {
            l.unlock();
        }

    }
}
