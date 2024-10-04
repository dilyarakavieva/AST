package practica2.Protocol;

import practica1.Protocol.SimNet_Queue;
import practica1.Protocol.TSocketRecv;
import practica1.Protocol.TSocketSend;
import util.Receiver;
import util.Sender;
import util.TCPSegment;
import util.SimNet;

public class Test {

  public static void main(String[] args) throws InterruptedException {
    
    TCPSegment.SHOW_DATA = true;
    
    SimNet net        = new SimNet_Monitor();//tuve q cambiar a simnet queue para acceder a simnetMonitor
    Sender sender     = new Sender(new TSocketSend(net), 10, 1, 1000);
    Receiver receiver = new Receiver(new TSocketRecv(net), 1, 200);
    sender.start();
    receiver.start();
    //Completar (trobar una manera que demostri que la xarxa utilitzada no funciona bé per aquest cas)
  }
}

