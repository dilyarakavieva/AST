package practica4;

import practica1.CircularQ.CircularQueue;
import util.Const;
import util.TCPSegment;
import util.TSocket_base;


public class TSocket extends TSocket_base {

  //sender variable:
  protected int MSS;

  //receiver variables:
  protected CircularQueue<TCPSegment> rcvQueue;
  protected int rcvSegConsumedBytes;

  protected TSocket(Protocol p, int localPort, int remotePort) {
    super(p.getNetwork());
    this.localPort  = localPort;
    this.remotePort = remotePort;
    p.addActiveTSocket(this);
    MSS = network.getMTU() - Const.IP_HEADER - Const.TCP_HEADER;
    rcvQueue = new CircularQueue<>(Const.RCV_QUEUE_SIZE);
    rcvSegConsumedBytes = 0;
  }

  @Override
  public void sendData(byte[] data, int offset, int length) {
     TCPSegment seg;
        int num = 0;
        while (MSS < length) {
            seg = this.segmentize(data, offset + num, MSS);
            num += MSS;
            length -= MSS;
        }
        if (MSS > length) {
            seg = this.segmentize(data, offset+num, length);
        }
  }

  protected TCPSegment segmentize(byte[] data, int offset, int length) {
    TCPSegment seg = new TCPSegment();
        seg.setData(data, offset, length);
        seg.setPsh(true);
        
        seg.setSourcePort(localPort);
        seg.setDestinationPort(remotePort);
       
        //System.out.println("\t\t\t\t\t\t\t\trecived:"+seg.toString());
        network.send(seg);
        return seg;
  }

  @Override
  public int receiveData(byte[] buf, int offset, int length) {
     lock.lock();
        try {
            int num = 0;
            while (this.rcvQueue.empty()) {
                this.appCV.awaitUninterruptibly();
            }
            
            while (num < length && !rcvQueue.empty()) {
                num += this.consumeSegment(buf, offset + num, length - num);
            }
            return num;
        } finally {
            lock.unlock();
        }
  }

  protected int consumeSegment(byte[] buf, int offset, int length) {
    TCPSegment seg = rcvQueue.peekFirst();
    int a_agafar = Math.min(length, seg.getDataLength() - rcvSegConsumedBytes);
    System.arraycopy(seg.getData(), rcvSegConsumedBytes, buf, offset, a_agafar);
    rcvSegConsumedBytes += a_agafar;
    if (rcvSegConsumedBytes == seg.getDataLength()) {
      rcvQueue.get();
      rcvSegConsumedBytes = 0;
    }
    return a_agafar;
  }

  protected void sendAck() {
        TCPSegment seg = new TCPSegment();
        seg.setAck(true);
        seg.setDestinationPort(remotePort);
        seg.setSourcePort(localPort);
        
        //log.printBLACK(seg.toString());
        //System.out.println("\t\t\t\t\t\t\t\trecived:"+seg.toString());
        network.send(seg);
        
  }

  @Override
  public void processReceivedSegment(TCPSegment rseg) {

   lock.lock();
        try {
            printRcvSeg(rseg);
            if (!this.rcvQueue.full() && rseg.isPsh()) {
                this.rcvQueue.put(rseg);
                
                this.sendAck();
                this.appCV.signal();
                
            }
        } finally {
            lock.unlock();
        }
  }

}
