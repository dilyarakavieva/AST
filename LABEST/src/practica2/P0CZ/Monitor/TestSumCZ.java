package practica2.P0CZ.Monitor;

public class TestSumCZ {

    public static void main(String[] args) throws InterruptedException {
        MonitorCZ mon = new MonitorCZ();
        CounterThreadCZ f1 = new CounterThreadCZ(mon);
        CounterThreadCZ f2 = new CounterThreadCZ(mon);
       f1.run();
       f2.run();
        System.out.println(mon.getX());
       
        
    }
}
