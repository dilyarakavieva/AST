package practica2.Protocol;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import practica1.CircularQ.CircularQueue;
import util.Const;
import util.TCPSegment;
import util.SimNet;

public class SimNet_Monitor implements SimNet {

    protected CircularQueue<TCPSegment> queue;
    protected Lock l;
    protected Condition r,s;

    public SimNet_Monitor() {
        queue = new CircularQueue<>(Const.SIMNET_QUEUE_SIZE);
        l = new ReentrantLock();
        r = l.newCondition();
        s = l.newCondition();
    
    }

    @Override
    public void send(TCPSegment seg) {
        l.lock();
        try {
            while (queue.full()) {
                s.awaitUninterruptibly();
            }
            queue.put(seg);
            r.signal();
        } finally {
            l.unlock();
        }
    }

    @Override
    public TCPSegment receive() {
        l.lock();
        TCPSegment seg= new TCPSegment();
        try {
            while (queue.empty()) {
                r.awaitUninterruptibly();
            }
            seg = queue.get();
            s.signal();
        } finally {
            l.unlock();
        }
        return seg;
    }

    @Override
    public int getMTU() {
        throw new UnsupportedOperationException("Not supported yet. NO cal completar fins a la pràctica 3...");
    }

}
