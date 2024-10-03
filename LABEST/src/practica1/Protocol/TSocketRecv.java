package practica1.Protocol;

import util.TCPSegment;
import util.TSocket_base;
import util.SimNet;

public class TSocketRecv extends TSocket_base {

    public TSocketRecv(SimNet network) {
        super(network);
    }

    @Override
    public int receiveData(byte[] data, int offset, int length) {
        int num;
        TCPSegment seg = this.network.receive();
        byte[] dataS = seg.getData();

        num = Math.min(dataS.length, length);
        System.arraycopy(dataS, 0, data, offset, length);

        printRcvSeg(seg);

        return num;
    }
}
