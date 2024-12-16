package practica6;

import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.locks.Condition;
import practica1.CircularQ.CircularQueue;
import practica4.Protocol;
import util.Const;
import util.TSocket_base;
import util.TCPSegment;

// en esta practica he implementado el protocolo Stop and Wait
// he modificado cosas en segmentize y en processSegment
// 1 error:(SIN PERDIDA DEL SEGMENTO) Si el segmento llega fuera del orden empieza a retransmitir todos los segmentos que han llegado fuera del orden
// 2 error: cuando ventana = 0 => aparecen errores
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
    protected Map<Integer, TCPSegment> out_of_order_segs;

    Condition envia; // he añadido condicion para stop and wait
    boolean flag;

    protected int stopNum = 1;
    protected int sndWnd = 5;
    protected int congWnd = 3;

    protected TSocket(Protocol p, int localPort, int remotePort) {
        super(p.getNetwork());
        this.localPort = localPort;
        this.remotePort = remotePort;
        p.addActiveTSocket(this);

        // init sender variables
        MSS = p.getNetwork().getMTU() - Const.IP_HEADER - Const.TCP_HEADER;
        MSS = 10;
        snd_rcvWnd = Const.RCV_QUEUE_SIZE;

        // init receiver variables
        rcv_Queue = new CircularQueue<>(Const.RCV_QUEUE_SIZE);
        // rcv_Queue = new CircularQueue<>(2);

        envia = lock.newCondition();// lo he añadido
        flag = true;
        out_of_order_segs = new HashMap<>();

    }

    // -------------  SENDER PART  ---------------
    @Override
    public void sendData(byte[] data, int offset, int length) {
        lock.lock();
        try {
            TCPSegment seg = new TCPSegment();
            int enviado = 0;
            while (length > enviado) {
                while (snd_sndNxt == stopNum) {
                    appCV.awaitUninterruptibly();
                }

                int enviar = Math.min(length - enviado, MSS);
                if (snd_rcvWnd == 0) {
                    enviar = 1;
                }

                seg = this.segmentize(data, offset + enviado, enviar);

                if (snd_rcvWnd != 0) {
                    network.send(seg);
                }

                enviado += enviar;
                this.snd_sndNxt++;
                startRTO(seg);

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
    protected void timeout(TCPSegment seg) {
        lock.lock();
        try {
            if (seg.getSeqNum() >= snd_rcvNxt)// if num_seq >= Ack 
            {
                log.printPURPLE("retrans: " + seg);
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
            printRcvSeg(rseg);
            if (rseg.isPsh()) {

                if (this.rcv_Queue.full()) {
                    log.printRED("Paquete perdido en la recepción: " + rseg.toString());
                    return;// ignorar el segmento
                }

                if (rseg.getSeqNum() == rcv_rcvNxt) {
                    log.printBLACK("\t\t\t\t\t\t\t\tintroducido: " + rseg.getSeqNum() + " rcv_rcvNxt: " + rcv_rcvNxt);
                    this.rcv_Queue.put(rseg);
                    this.rcv_rcvNxt++;// siguiente seg q estamos planeando recibir
                    this.appCV.signal();
                } else {
                    this.out_of_order_segs.put(rseg.getSeqNum(), rseg);
                    log.printBLACK("\t\t\t\t\t\t\t\tintroducido fuera del orden: " + rseg.getSeqNum() + " rcv_rcvNxt: " + rcv_rcvNxt);
                }
                
                while (this.out_of_order_segs.containsKey(rcv_rcvNxt)) {
                    TCPSegment seg = this.out_of_order_segs.remove(rcv_rcvNxt);
                    this.rcv_Queue.put(seg);
                    rcv_rcvNxt = rcv_rcvNxt + 1;
                    log.printGREEN("sacamos un segmento de out_of_order_segs: " + seg.toString());
                }

                this.sendAck();

                this.appCV.signal();

            }
            if (rseg.isAck()) {

                if (rseg.getAckNum() > this.snd_rcvNxt)// if seq_num > ack
                {
                    this.snd_rcvNxt = rseg.getAckNum();
                    snd_rcvWnd = rseg.getWnd();
                    sndWnd = Math.max(1, Math.min(snd_rcvWnd, congWnd));
                    stopNum = rseg.getAckNum() + sndWnd;
                    this.appCV.signal();
                }

            }

        } finally {
            lock.unlock();
        }
    }

}
