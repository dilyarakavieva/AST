package practica7;

import practica1.CircularQ.CircularQueue;
import util.Const;
import util.TCPSegment;
import util.TSocket_base;

/**
 * Connection oriented Protocol Control Block.
 *
 * Each instance of TSocket maintains all the status of an endpoint.
 * 
 * Interface for application layer defines methods for passive/active opening and for closing the connection.
 * Interface lower layer defines methods for processing of received segments and for sending of segments.
 * We assume an ideal lower layer with no losses and no errors in packets.
 *
 * State diagram:<pre>
                              +---------+
                              |  CLOSED |-------------
                              +---------+             \
                           LISTEN  |                   \
                           ------  |                    | CONNECT
                                   V                    | -------
                              +---------+               | snd SYN
                              |  LISTEN |               |
                              +---------+          +----------+
                                   |               | SYN_SENT |
                                   |               +----------+
                         rcv SYN   |                    |
                         -------   |                    | rcv SYN
                         snd SYN   |                    | -------
                                   |                    |
                                   V                   /
                              +---------+             /
                              |  ESTAB  |<------------
                              +---------+
                       CLOSE    |     |    rcv FIN
                      -------   |     |    -------
 +---------+          snd FIN  /       \                    +---------+
 |  FIN    |<-----------------           ------------------>|  CLOSE  |
 |  WAIT   |------------------           -------------------|  WAIT   |
 +---------+          rcv FIN  \       /   CLOSE            +---------+
                      -------   |      |  -------
                                |      |  snd FIN 
                                V      V
                              +----------+
                              |  CLOSED  |
                              +----------+
 * </pre>
 *
 * @author AST's teachers
 */

public class TServerSocket extends TSocket_base {

  protected Protocol proto;

  protected int state;
  protected CircularQueue<TSocket> acceptQueue;

  // States of FSM:
  protected final static int  CLOSED      = 0,
                              LISTEN      = 1,
                              SYN_SENT    = 2,
                              ESTABLISHED = 3,
                              FIN_WAIT    = 4,
                              CLOSE_WAIT  = 5;

  protected TServerSocket(Protocol p, int localPort) {
    super(p.getNetwork());
    proto = p;
    this.localPort = localPort;
    state = CLOSED;
    p.addListenTSocket(this);
    
    listen();
  }

  @Override
  public void listen() {
    lock.lock();
    try {
      acceptQueue = new CircularQueue<>(Const.LISTEN_QUEUE_SIZE);
      state = LISTEN;
      //proto.addListenTSocket(this); // porque no podemos añadir dos veces
    } finally {
      lock.unlock();
    }
  }

  @Override
  public TSocket accept() {
      // hay un await tmb
    TSocket sc;
    lock.lock();
    try {
      while(this.acceptQueue.empty()){
          appCV.awaitUninterruptibly();
      }
      sc=this.acceptQueue.get();
    } finally {
      lock.unlock();
    }
    return sc;
  }


  /**
   * Segment arrival.
   *
   */
  public void processReceivedSegment(TCPSegment rseg) {
    lock.lock();
    try {

      printRcvSeg(rseg);

      switch (state) {
        case LISTEN: {
          if (rseg.isSyn()) {
            TSocket new_sc = new TSocket(proto, localPort,rseg.getSourcePort()); 
            //proto.addActiveTSocket(new_sc);
            new_sc.state = ESTABLISHED;
            //sendSYN
            TCPSegment seg = new TCPSegment();
            seg.setSyn(true);
            seg.setSourcePort(new_sc.localPort);
            seg.setDestinationPort(new_sc.remotePort);
            new_sc.network.send(seg);
            
            this.acceptQueue.put(new_sc);
            appCV.signalAll();
            
            
            
          }
          break;
        }
      }
    } finally {
      lock.unlock();
    }
  }

  protected void printRcvSeg(TCPSegment rseg) {
    log.printBLACK("\t\t\t\t\t\t\t    rcvd: " + rseg);
  }

  protected void printSndSeg(TCPSegment rseg) {
    log.printBLACK("\t\t\t\t\t\t\t    sent: " + rseg);
  }

}
