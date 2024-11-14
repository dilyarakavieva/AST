package practica5;

import java.util.TimerTask;
import java.util.concurrent.locks.Condition;
import practica1.CircularQ.CircularQueue;
import practica4.Protocol;
import util.Const;
import util.TSocket_base;
import util.TCPSegment;

// en esta practica he implementado el protocolo Stop and Wait
// he modificado cosas en segmentize y en processSegment
//
public class TSocket extends TSocket_base {

    // Sender variables:
    protected int MSS;
    protected int snd_sndNxt;
    protected int snd_rcvWnd;
    protected int snd_rcvNxt;
    protected boolean zero_wnd_probe_ON;

    // Receiver variables:
    protected CircularQueue<TCPSegment> rcv_Queue;
    protected int rcv_SegConsumedBytes;
    protected int rcv_rcvNxt;

    Condition envia; // he añadido condicion para stop and wait
    boolean flag;

    protected TSocket(Protocol p, int localPort, int remotePort) {
        super(p.getNetwork());
        this.localPort = localPort;
        this.remotePort = remotePort;
        p.addActiveTSocket(this);

        // init sender variables
        MSS = p.getNetwork().getMTU() - Const.IP_HEADER - Const.TCP_HEADER;
        snd_rcvWnd = Const.RCV_QUEUE_SIZE;

        // init receiver variables
        rcv_Queue = new CircularQueue<>(Const.RCV_QUEUE_SIZE);
        //rcv_Queue = new CircularQueue<>(2);

        envia = lock.newCondition();// lo he añadido
        flag = true;

    }

    // -------------  SENDER PART  ---------------
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
            seg = this.segmentize(data, offset + num, length);
        }

    }

    protected TCPSegment segmentize(byte[] data, int offset, int length) {
        lock.lock();
        try {
            while (!flag) {
                envia.awaitUninterruptibly();
            }
            TCPSegment seg = new TCPSegment();
            seg.setData(data, offset, length);
            seg.setPsh(true);
            seg.setSourcePort(localPort);
            seg.setDestinationPort(remotePort);

            seg.setSeqNum(this.snd_sndNxt);
            this.snd_sndNxt++;// no estoy segura que es snd_snd

            //System.out.println("\t\t\t\t\t\t\t\trecived:"+seg.toString());
            network.send(seg);
            
            flag = false;
            return seg;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void timeout(TCPSegment seg) // no se xd
    {
        lock.lock();
        try {
            //throw new RuntimeException("//Completar...");
            // 
            
            log.printPURPLE(seg.toString());
        } finally {

            lock.unlock();
        }

    }

    /**
     *
     * @param seg
     */
    @Override
    protected void startRTO(TCPSegment seg) {
        TimerTask sndRtTimer = new TimerTask() {
            @Override
            public void run() {
                timeout(seg);
                

            }
        };
        timerService.schedule(sndRtTimer, Const.SND_RTO);
    }

    // -------------  RECEIVER PART  ---------------
    @Override
    public int receiveData(byte[] buf, int offset, int length) {
        lock.lock();
        try {
            int num = 0;
            while (this.rcv_Queue.empty()) {
                this.appCV.awaitUninterruptibly();
            }

            while (num < length && !rcv_Queue.empty()) {
                num += this.consumeSegment(buf, offset + num, length - num);
            }
            return num;
        } finally {
            lock.unlock();
        }
    }

    protected int consumeSegment(byte[] buf, int offset, int length) {
        TCPSegment seg = rcv_Queue.peekFirst();
        int a_agafar = Math.min(length, seg.getDataLength() - rcv_SegConsumedBytes);
        System.arraycopy(seg.getData(), rcv_SegConsumedBytes, buf, offset, a_agafar);
        rcv_SegConsumedBytes += a_agafar;
        if (rcv_SegConsumedBytes == seg.getDataLength()) {
            rcv_Queue.get();
            rcv_SegConsumedBytes = 0;
        }
        return a_agafar;
    }

    protected void sendAck() {
        TCPSegment seg = new TCPSegment();
        seg.setAck(true);
        seg.setDestinationPort(remotePort);
        seg.setSourcePort(localPort);

        this.snd_rcvNxt++;// he añadido, es lo que espera a recibir
        this.snd_rcvWnd--;
        seg.setWnd(this.snd_rcvWnd);//he añadido
        seg.setAckNum(this.snd_rcvNxt);// no estoy segura

        //log.printBLACK(seg.toString());
        //System.out.println("\t\t\t\t\t\t\t\trecived:"+seg.toString());
        network.send(seg);

    }

    // -------------  SEGMENT ARRIVAL  -------------
    @Override

    public void processReceivedSegment(TCPSegment rseg) {

        lock.lock();
        try {
            //printRcvSeg(rseg);

            if (!this.rcv_Queue.full() && rseg.isPsh()) {
                this.rcv_Queue.put(rseg);
                printRcvSeg(rseg);
                this.sendAck();
                startRTO(rseg);

                this.appCV.signal();

            }
            if (rseg.isAck()) {// if no recibe ack durnamte rto envia ootra vez rseg
                printRcvSeg(rseg);
                if (rseg.getAckNum() == snd_sndNxt) {
                    flag = true;
                    this.envia.signal();
                }
                
            }
            startRTO(rseg);
        } finally {
            lock.unlock();
        }
    }

}
