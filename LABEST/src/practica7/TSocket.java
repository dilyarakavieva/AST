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

public class TSocket extends TSocket_base {

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

  protected TSocket(Protocol p, int localPort, int remotePort) {
    super(p.getNetwork());
    proto = p;
    this.localPort = localPort;
    this.remotePort = remotePort;
    state = CLOSED;
    p.addActiveTSocket(this);
  }

  @Override
  public void connect() {
    lock.lock();
    try {
       state = SYN_SENT;
       //proto.addActiveTSocket(this); lo añadimos en active socket a la hora de conectar
       //sendSYN
       TCPSegment seg = new TCPSegment();
       seg.setSyn(true);
       seg.setSourcePort(localPort);
       seg.setDestinationPort(remotePort);
       network.send(seg);
       
       while(state !=  ESTABLISHED){
          appCV.awaitUninterruptibly();
       }
      
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close() {
    lock.lock();
    try {
      switch (state) {
          
        case ESTABLISHED:
        {
            state = FIN_WAIT;
            //sendFIN
            TCPSegment seg = new TCPSegment();
            seg.setFin(true);
            seg.setSourcePort(localPort);
            seg.setDestinationPort(remotePort);
            network.send(seg);
            
            while(state != CLOSED)
            {
                appCV.awaitUninterruptibly();
            }
            break;// saldra de este case y no pasaremos al otro
        }
        case CLOSE_WAIT: {
            state = CLOSED;
            
            //sendFIN
            TCPSegment seg = new TCPSegment();
            seg.setFin(true);
            seg.setSourcePort(localPort);
            seg.setDestinationPort(remotePort);
            network.send(seg);
            proto.removeActiveTSocket(this);// eliminamos este socket de la lista
          break;
        }
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Segment arrival.
   *
   */
  @Override
  public void processReceivedSegment(TCPSegment rseg) {
    lock.lock();
    try {

      printRcvSeg(rseg);

      switch (state) {

        case SYN_SENT: {
          if (rseg.isSyn())
          {
            state = ESTABLISHED;
            appCV.signal();
          }
          break;
        }
        
        case ESTABLISHED:{
            if(rseg.isFin())
            {
                state = CLOSE_WAIT;    
            }
            break;
        }
        case FIN_WAIT:{
            if(rseg.isFin()) 
            {
                state = CLOSED;
                proto.removeActiveTSocket(this);
                appCV.signal();
            }
            break;
        }
        case CLOSE_WAIT: {
         /* if (rseg.isPsh()) {
            if (state == ESTABLISHED || state == FIN_WAIT) {
              // Here should go the segment's data processing.
            } else {
              // This should not occur, since a FIN has been 
              // received from the remote side. 
              // Ignore the data segment.
            }
          }*/// NUNCA VAMOS A RECIBIR MAS INFO
          /*if (rseg.isFin()) {
            throw new RuntimeException("//Completar...");
          }*/
          break;
        }
      }
    } finally {
      lock.unlock();
    }
  }

  protected void printRcvSeg(TCPSegment rseg) {
    log.printBLACK("    rcvd: " + rseg);
  }

  protected void printSndSeg(TCPSegment rseg) {
    log.printBLACK("    sent: " + rseg);
  }

}
