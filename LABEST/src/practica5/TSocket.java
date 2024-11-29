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
    protected int snd_sndNxt;//el seg q enviamos
    protected int snd_rcvWnd;
    protected int snd_rcvNxt;//llegada ACK
    protected boolean zero_wnd_probe_ON;

    // Receiver variables:
    protected CircularQueue<TCPSegment> rcv_Queue;
    protected int rcv_SegConsumedBytes;
    protected int rcv_rcvNxt;// llegada de PSH

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
        //rcv_Queue = new CircularQueue<>(Const.RCV_QUEUE_SIZE);
        rcv_Queue = new CircularQueue<>(2);

        envia = lock.newCondition();// lo he añadido
        flag = true;

    }

    // -------------  SENDER PART  ---------------
    @Override
    public void sendData(byte[] data, int offset, int length) {
        lock.lock();
        try {
            TCPSegment seg = new TCPSegment();
            int enviado = 0;
            int enviar;
            while (length > enviado) {
                while (!flag) {
                    appCV.awaitUninterruptibly();
                }

                if (zero_wnd_probe_ON) {

                    enviar = 1;

                } else {
                    enviar = Math.min(length - enviado, MSS);
                }
                seg = this.segmentize(data, offset + enviado, enviar);
                if (!zero_wnd_probe_ON){
                    network.send(seg);    
                }
                
                enviado += enviar;
                this.snd_sndNxt++;
                startRTO(seg);
                flag = false;

            }

        } finally {
            lock.unlock();
        }

    }

    protected TCPSegment segmentize(byte[] data, int offset, int length) {

        TCPSegment seg = new TCPSegment();
        seg.setData(data, offset, length);
        seg.setPsh(true);
        seg.setSourcePort(localPort);
        seg.setDestinationPort(remotePort);
        seg.setSeqNum(this.snd_sndNxt);
        return seg;

    }

    @Override
    protected void timeout(TCPSegment seg) // no se xd
    {
        lock.lock();
        try {
            if (seg.getSeqNum() >= snd_rcvNxt) {
                if (this.zero_wnd_probe_ON) {
                    log.printGREEN("zero-window probe ON: " + seg.toString());
                } else {
                    log.printPURPLE("retrans: " + seg.toString());
                }
                this.network.send(seg);
                startRTO(seg);
            }
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

        seg.setWnd(this.rcv_Queue.free());

        seg.setAckNum(this.rcv_rcvNxt);
        network.send(seg);

    }

    // -------------  SEGMENT ARRIVAL  -------------
    @Override

    public void processReceivedSegment(TCPSegment rseg) {

        lock.lock();
        try {

            if (!this.rcv_Queue.full() && rseg.isPsh()) {
                printRcvSeg(rseg);
                if (rcv_rcvNxt <= rseg.getSeqNum()) 
                {

                    this.rcv_Queue.put(rseg);
                    this.rcv_rcvNxt++;// siguiente seg q estamos planeando recibir
                }

                this.appCV.signal();
                this.sendAck();
            }

            if (rseg.isAck()) {// si hemos recibido un ACK
                printRcvSeg(rseg);
                if (rseg.getWnd() > 0 && zero_wnd_probe_ON) 
                {
                    this.zero_wnd_probe_ON = false;
                    log.printGREEN("----- zero-window probe OFF -----");

                }
                if (rseg.getWnd() == 0) 
                {
                    this.zero_wnd_probe_ON = true;
                    log.printGREEN("----- zero-window probe ON -----");
                }

                if (rseg.getAckNum() > this.snd_rcvNxt) 
                {
                    this.snd_rcvNxt++;// el siguiente seg q estamos planeando enviar
                    flag = true;
                    this.appCV.signal();
                }

                snd_rcvWnd = rseg.getWnd();

            }

        } finally {
            lock.unlock();
        }
    }

}
