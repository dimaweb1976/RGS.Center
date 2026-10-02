package com.rgs.center.ups;

import com.rgs.center.settings.RGSCenterSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class ServiceTcpUpsConnection {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterSettings settings;
    
    @Autowired
    UpsIoLogger ioLogger;
    
    private final ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledQueue;
    private ScheduledFuture<?> scheduledPoller;

    @Autowired
    ServiceTcpUpsReader upsReader;
    
    @Autowired
    ServiceUpsProcessor upsProcessor;
            
    private final ConcurrentLinkedDeque<byte[]> queue;
    
    public void AddCommandToSend(byte[] abt) {
        queue.addLast(abt);
    }
    
    UpsTcpSentUnit sent;
    
    public ServiceTcpUpsConnection() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("tcp-ups-connection-");
        taskScheduler.initialize();
        
        queue = new ConcurrentLinkedDeque<>();
        sent = null;
    }

    @PostConstruct
    public void autoRunOnStart() {
        long lQueueServicePeriodMsec = 200;
        long lPollServicePeriodMsec = 2000;
        
        start(lQueueServicePeriodMsec, lPollServicePeriodMsec);
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
        return (scheduledQueue != null && !scheduledQueue.isCancelled() &&
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
        if (upsReader.getSocket() == null || !upsReader.getSocket().isConnected()) {
            upsReader.connect("localhost", 502); 
            return;
        }
        
        if (sent != null) {
            int expectedBytes = 7; 
            if (upsReader.isAnswerReady(expectedBytes)) {
                UpsCircleBuffer cb = upsReader.GetCb();
                byte[] abtRespond = new byte[expectedBytes];
                cb.getAnswer(expectedBytes, abtRespond);
                
                ioLogger.logRespond(abtRespond);
                
                UpsTcpRespondedUnit unit2 = new UpsTcpRespondedUnit(sent, abtRespond);
                upsProcessor.submit(unit2);
                sent = null;
            } else {
                if (System.currentTimeMillis() - sent.getSentTime() > 5000) {
                    sent = null;
                    upsReader.resetCb();
                }
            }
            return;
        }
        
        if (!queue.isEmpty()) {
            byte[] abtPack = queue.peek();
            try {
                OutputStream out = upsReader.getSocket().getOutputStream();
                out.write(abtPack);
                out.flush();
                queue.remove();
                sent = new UpsTcpSentUnit(abtPack);
                
                ioLogger.logSent(abtPack);
            } catch (Exception ex) {
                logger.error("UPS TCP write exception", ex);
            }
        }
    }
    
    private void poll() {
        if (upsReader.getSocket() == null || !upsReader.getSocket().isConnected()) return;
        if (!queue.isEmpty()) return;
        
        UpsTransportLayerProcessor packer = new UpsTransportLayerProcessor();
        byte[] readReq = packer.packReadHoldingRegisters(1, 22, 1);
        queue.add(readReq);
    }

    @PreDestroy
    public void cleanup() {
        stop();
        taskScheduler.shutdown();
        upsReader.close();
    }
}