package com.rgs.center.shp;

import com.rgs.center.settings.RGSCenterSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import jssc.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;



@Service
public class ServiceShpConnection {

    /**
     * logger variable
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    /*
    //CONNECTION states enum
    public final static int STATE_DISCONNECTED_INITIAL      = 0;    // INITIAL
    public final static int STATE_REQUESTED_TO_CONNECT      = 1;    // TRANSIT
    public final static int STATE_CONNECTED1                = 2;    // CONNECTION ESTABLISHED, BUT NO DATA
    public final static int STATE_CONNECTED2                = 3;    // CONNECTION ESTABLISHED, FIRST PACK IS REPSONDED
    public final static int STATE_REQUESTED_TO_DISCONNECT   = 5;    // TRANSIT
    public final static int STATE_DISCONNECTED              = 6;    // DISCONNECTED
    
    //connecton state
    protected int m_nConnectionState;    
    public int GetConnectionState() { return m_nConnectionState; }
    */
    public int isActive() {
        if( taskScheduler == null) return 8;        
        if( scheduledQueue == null) return 7;
        if( scheduledPoller == null) return 6;
        if( scheduledQueue.isCancelled()) return 5;
        if( scheduledPoller.isCancelled()) return 4;
        if( shpReader == null) return 3;
        if( shpReader.GetSerialPort() == null) return 2;
        if( !shpReader.GetSerialPort().isOpened()) return 1;
        return 0;
    }
    
    @Autowired
    private RGSCenterSettings settings;
    
    private final ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledQueue;
    private ScheduledFuture<?> scheduledPoller;

    @Autowired
    ServiceShpReader shpReader;
    
    @Autowired
    ServiceShpProcessor shpProcessor;
            
    private final ConcurrentLinkedQueue<byte[]> queue;
    public void AddCommandToSend( byte[] abt) {
        queue.add(abt);
    }
    
    ShpSentUnit sent;
    
    
    public ServiceShpConnection() {
        logger.info("ServiceShpConnection constructor called!");
        
        //m_nConnectionState = STATE_DISCONNECTED_INITIAL;
                
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize( 2);
        taskScheduler.setThreadNamePrefix( "shp-connection-");
        taskScheduler.initialize();
        
        queue = new ConcurrentLinkedQueue(); 
        
        sent = null;
    }

    @PostConstruct
    public void autoRunOnStart() {
        // Start with default intervals on bean initialization
        long lShpQueueServicePeriodMsec = ( long) settings.getSetting("shp.queue_service.period_ms");
        if( lShpQueueServicePeriodMsec == 0) lShpQueueServicePeriodMsec = 100;
        
        
        long lShpPollServicePeriodMsec = ( long) settings.getSetting("shp.poller_service.period_ms");
        if( lShpPollServicePeriodMsec == 0) lShpPollServicePeriodMsec = 1000;
        
        start( lShpQueueServicePeriodMsec, lShpPollServicePeriodMsec); 
    }

    public synchronized void start( long intervalQueueMs, long intervalPollerMs) {
        // Prevent duplicate tasks if already running
        if( scheduledQueue != null && !scheduledQueue.isCancelled()) {
            return;
        }

        scheduledQueue = taskScheduler.scheduleWithFixedDelay(
                this::queue,
                Duration.ofMillis( intervalQueueMs)
        );
        
        // Prevent duplicate tasks if already running
        if( scheduledPoller != null && !scheduledPoller.isCancelled()) {
            return;
        }

        scheduledPoller = taskScheduler.scheduleWithFixedDelay(
                this::poll,
                Duration.ofMillis( intervalPollerMs)
        );
    }

    public synchronized void stop() {
        if( scheduledQueue != null && !scheduledQueue.isCancelled()) {
            // false allows the current iteration to finish safely
            scheduledQueue.cancel(false);
            scheduledQueue = null;
        }
        
        if( scheduledPoller != null && !scheduledPoller.isCancelled()) {
            // false allows the current iteration to finish safely
            scheduledPoller.cancel(false);
            scheduledPoller = null;            
        }
        
        queue.clear();
    }

    public synchronized void restartTask( long newIntervalQueueMs, long newIntervalPollMs) {
        stop();
        start( newIntervalQueueMs, newIntervalPollMs);
    }

    private void queue() {
        if( shpReader.GetSerialPort() == null) {
            shpReader.open( "COM11");
            return;
        }
        
        if( sent != null) {
            //мы отправили что-то...
            
            if( shpReader.isAnswerReady()) {
                int nLen = shpReader.GetCb().SeekForFirstByteOccurence( ( byte) 0x0C);
                byte [] abtRespond = new byte[ nLen];
                shpReader.GetCb().getAnswer( nLen, abtRespond);
                
                ShpRespondedUnit unit2 = new ShpRespondedUnit(sent, abtRespond);
                shpProcessor.submit(unit2);
                sent = null;
            }
            else {
                if( System.currentTimeMillis() - sent.getSentTime() > 10000) {
                    sent = null;
                    shpReader.resetCb();
                }
            }
            return;
        }
        
        if( !queue.isEmpty()) {
            
            byte[] abtPack = queue.peek();
            sent = new ShpSentUnit(abtPack);
                    
            SerialPort port = shpReader.GetSerialPort();
            if( port != null) {
                try {
                    port.writeBytes( abtPack);
                    queue.remove();
                    sent = new ShpSentUnit( abtPack);
                }
                catch( Exception ex) {
                    logger.error( "", ex);
                }
            }
                
            
        }
    }
    
    private void poll() {
        if( shpReader.GetSerialPort() == null) {
            return;
        }
        
        if( !queue.isEmpty()) return;
        
        /*
        byte[] abtPack = {  ( byte) 0xC0,
                            ( byte) 0x72,
                            ( byte) 0x06,
                            ( byte) 0x00,
                            ( byte) 0x00,
                            ( byte) 0x00,
                            ( byte) 0x00,
                            ( byte) 0x00,
                            ( byte) 0x1B,
                            ( byte) 0x0C  };
        queue.add(abtPack);
        */
        
        //C0 71 01 00 00 00 00 00 D9 0C
        byte[] abtPack1 = {( byte) 0xC0, ( byte) 0x71, ( byte) 0x01, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0xD9, ( byte) 0x0C};
        queue.add( abtPack1);
        
        //C0 71 02 00 00 00 00 00 80 0C
        byte[] abtPack2 = {( byte) 0xC0, ( byte) 0x71, ( byte) 0x02, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x80, ( byte) 0x0C};
        queue.add( abtPack2);
        
        //C0 71 03 00 00 00 00 00 B7 0C
        byte[] abtPack3 = {( byte) 0xC0, ( byte) 0x71, ( byte) 0x03, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0xB7, ( byte) 0x0C};
        queue.add( abtPack3);
        
        //C0 71 04 00 00 00 00 00 32 0C
        byte[] abtPack4 = {( byte) 0xC0, ( byte) 0x71, ( byte) 0x04, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x32, ( byte) 0x0C};
        queue.add( abtPack4);
        
        //C0 71 05 00 00 00 00 00 05 0C
        byte[] abtPack5 = {( byte) 0xC0, ( byte) 0x71, ( byte) 0x05, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x05, ( byte) 0x0C};
        queue.add( abtPack5);
        
        //C0 71 06 00 00 00 00 00 5C 0C
        byte[] abtPack6 = {( byte) 0xC0, ( byte) 0x71, ( byte) 0x06, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x5C, ( byte) 0x0C};
        queue.add( abtPack6);
        
        //C0 73 01 00 00 00 00 00 A3 0C
        byte[] abtPack7 = {( byte) 0xC0, ( byte) 0x73, ( byte) 0x01, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0xA3, ( byte) 0x0C};
        queue.add( abtPack7);
        
        //C0 73 02 00 00 00 00 00 FA 0C
        byte[] abtPack8 = {( byte) 0xC0, ( byte) 0x73, ( byte) 0x02, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0xFA, ( byte) 0x0C};
        queue.add( abtPack8);
        
        //C0 73 03 00 00 00 00 00 CD 0C
        byte[] abtPack9 = {( byte) 0xC0, ( byte) 0x73, ( byte) 0x03, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0xCD, ( byte) 0x0C};
        queue.add( abtPack9);
    }

    @PreDestroy
    public void cleanup() {
        stop();
        taskScheduler.shutdown();
    }
}
