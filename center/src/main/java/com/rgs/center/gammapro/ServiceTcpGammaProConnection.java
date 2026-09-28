package com.rgs.center.gammapro;

import com.rgs.center.settings.RGSCenterSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class ServiceTcpGammaProConnection {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterSettings settings;
    
    @Autowired
    GammaProIoLogger ioLogger;
    
    private final ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledQueue;
    private ScheduledFuture<?> scheduledPoller;

    @Autowired
    ServiceTcpGammaProReader gammaProReader;
    
    @Autowired
    ServiceGammaProTcpRespondsProcessor gammaProProcessor;
            
    private final ConcurrentLinkedDeque<byte[]> queue;
    
    public void AddCommandToSend(byte[] abt) {
        queue.addLast(abt);
    }
    
    public void AddCommandToSendFirst(byte[] abt) {
        queue.addFirst(abt);
    }
    
    GammaProTcpSentUnit sent;
    
    public ServiceTcpGammaProConnection() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("tcp-shp-connection-");
        taskScheduler.initialize();
        
        queue = new ConcurrentLinkedDeque<>();
        sent = null;
    }

    @PostConstruct
    public void autoRunOnStart() {
        long lShpQueueServicePeriodMsec = (long) settings.getSetting("shp.queue_service.period_ms");
        if (lShpQueueServicePeriodMsec == 0) lShpQueueServicePeriodMsec = 200;
        
        long lShpPollServicePeriodMsec = (long) settings.getSetting("shp.poller_service.period_ms");
        if (lShpPollServicePeriodMsec == 0) lShpPollServicePeriodMsec = 1000;
        
        start(lShpQueueServicePeriodMsec, lShpPollServicePeriodMsec);
    }

    public synchronized void start(long intervalQueueMs, long intervalPollerMs) {
        if (scheduledQueue != null && !scheduledQueue.isCancelled()) return;

        scheduledQueue = taskScheduler.scheduleWithFixedDelay(
                this::queue,
                Duration.ofMillis(intervalQueueMs)
        );
        
        if (scheduledPoller != null && !scheduledPoller.isCancelled()) return;

        scheduledPoller = taskScheduler.scheduleWithFixedDelay(
                this::poll,
                Duration.ofMillis(intervalPollerMs)
        );
    }

    public boolean isActive() {
        return( scheduledQueue != null && !scheduledQueue.isCancelled() &&
                scheduledPoller != null && !scheduledPoller.isCancelled());
    }
    
    public synchronized void stop() {
        if (scheduledQueue != null && !scheduledQueue.isCancelled()) {
            scheduledQueue.cancel(false);
            scheduledQueue = null;
        }
        if (scheduledPoller != null && !scheduledPoller.isCancelled()) {
            scheduledPoller.cancel(false);
            scheduledPoller = null;            
        }
        queue.clear();
    }

    private void queue() {
        if (gammaProReader.getSocket() == null || !gammaProReader.getSocket().isConnected()) {
            gammaProReader.connect("localhost", 37569); // Пример хоста и порта
            return;
        }
        
        if (sent != null) {
            if( gammaProReader.isAnswerReady()) {
                int nLen = gammaProReader.GetCb().isAnswerReady();
                byte [] abtRespond = new byte[ nLen];
                gammaProReader.GetCb().getAnswer( nLen, abtRespond);
                
                GammaProTcpRespondedUnit unit2 = new GammaProTcpRespondedUnit(sent, abtRespond);
                gammaProProcessor.submit( unit2);
                sent = null;
            } else {
                if (System.currentTimeMillis() - sent.getSentTime() > 10000) {
                    sent = null;
                    gammaProReader.resetCb();
                }
            }
            return;
        }
        
        if (!queue.isEmpty()) {
            byte[] abtPack = queue.peek();
            try {
                OutputStream out = gammaProReader.getSocket().getOutputStream();
                out.write(abtPack);
                out.flush();
                queue.remove();
                sent = new GammaProTcpSentUnit(abtPack);
                
                // Логируем отправку
                ioLogger.logSent(abtPack);
                
                String str = new String( abtPack, StandardCharsets.US_ASCII);
                logger.error(String.format("TCP WRITE '%s'", str));
            } catch (Exception ex) {
                logger.error("", ex);
            }
        }
    }
    
    private void poll() {
        /*if (gammaProReader.getSocket() == null || !gammaProReader.getSocket().isConnected()) return;
        if (!queue.isEmpty()) return;
        
        byte[] abtPack1 = {(byte) 0xC0, (byte) 0x71, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xD9, (byte) 0x0C};
        queue.add(abtPack1);*/
    }

    @PreDestroy
    public void cleanup() {
        stop();
        taskScheduler.shutdown();
        gammaProReader.close();
    }
}