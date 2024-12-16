package practica7;

import util.Protocol_base;
import util.TCPSegment;
import util.SimNet;
import util.TSocket_base;

public class Protocol extends Protocol_base {

  protected Protocol(SimNet network) {
    super(network);
  }

  public void ipInput(TCPSegment segment) {
    TSocket_base nou = this.getMatchingTSocket(segment.getSourcePort(), segment.getDestinationPort());
        if (nou != null) {
            
            //seg.setDestinationPort(nou.remotePort);// se puede cambiar puerta de seg q llega?
            //seg.setSourcePort(nou.localPort);
            nou.processReceivedSegment(segment);
        } else {
            log.printPURPLE("no tenemos TSocket_base para este segmento!");
        }
  }

  protected TSocket_base getMatchingTSocket(int localPort, int remotePort) {
    lk.lock();
    try {
            TSocket_base base = new TSocket_base(network);
            for (TSocket_base nou : this.activeSockets) {
                if (nou.localPort == remotePort && nou.remotePort == localPort) {
                    base = nou;
                    
                }
            }
            for (TSocket_base nou : this.listenSockets) {
                if (nou.localPort == remotePort) {
                    base = nou;
                    
                }
            }
            return base;
    }finally {
      lk.unlock();
    }
  }

}
