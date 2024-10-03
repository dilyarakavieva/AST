package practica2.P0CZ.Monitor;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MonitorCZ {

    private int x = 0;
    private Lock l = new ReentrantLock();
    private boolean dins = false;
    
    public void inc() {
        l.lock();
        x++;
        l.unlock();
        
    }

    public int getX() {
        return x;
    }

}
