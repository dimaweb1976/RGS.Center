package com.rgs.center.nicole;

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
public class ServiceTcpNicoleConnection {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterSettings settings;
    
    @Autowired
    NicoleIoLogger ioLogger;
    
    private final ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledQueue;
    private ScheduledFuture<?> scheduledPoller;

    @Autowired
    ServiceTcpNicoleReader nicoleReader;
    
    @Autowired
    ServiceNicoleProcessor nicoleProcessor;
            
    private final ConcurrentLinkedDeque<byte[]> queue;
    
    public void AddCommandToSend(byte[] abt) {
        queue.addLast(abt);
    }
    
    public void AddCommandToSendFirst(byte[] abt) {
        queue.addFirst(abt);
    }
    
    NicoleTcpSentUnit sent;
    
    public ServiceTcpNicoleConnection() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("tcp-nicole-connection-");
        taskScheduler.initialize();
        
        queue = new ConcurrentLinkedDeque<>();
        sent = null;
    }

    @PostConstruct
    public void autoRunOnStart() {
        long lQueueServicePeriodMsec = 200;
        long lPollServicePeriodMsec = 1000;
        
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
        if (nicoleReader.getSocket() == null || !nicoleReader.getSocket().isConnected()) {
            nicoleReader.connect("localhost", 5000); // Порт контроллера Nicole
            return;
        }
        
        if (sent != null) {
            if (nicoleReader.isAnswerReady()) {
                NicoleCircleBuffer cb = nicoleReader.GetCb();
                int nStart = cb.SeekForFirstByteOccurence((byte) 0xC0);
                int nEnd = cb.SeekForFirstByteOccurence((byte) 0xC1);
                
                if (nStart > 0 && nEnd >= nStart) {
                    int nLen = nEnd;
                    byte[] abtRespond = new byte[nLen];
                    cb.getAnswer(nLen, abtRespond);
                    
                    // Логируем ответ от Nicole
                    ioLogger.logRespond(abtRespond);
                    
                    NicoleTcpRespondedUnit unit2 = new NicoleTcpRespondedUnit(sent, abtRespond);
                    nicoleProcessor.submit(unit2);
                    sent = null;
                }
            } else {
                if (System.currentTimeMillis() - sent.getSentTime() > 10000) {
                    sent = null;
                    nicoleReader.resetCb();
                }
            }
            return;
        }
        
        if (!queue.isEmpty()) {
            byte[] abtPack = queue.peek();
            try {
                OutputStream out = nicoleReader.getSocket().getOutputStream();
                out.write(abtPack);
                out.flush();
                queue.remove();
                sent = new NicoleTcpSentUnit(abtPack);
                
                // Логируем отправку пакета Nicole
                ioLogger.logSent(abtPack);
            } catch (Exception ex) {
                logger.error("Nicole TCP write exception", ex);
            }
        }
    }
    
    private void poll() {
        if (nicoleReader.getSocket() == null || !nicoleReader.getSocket().isConnected()) return;
        if (!queue.isEmpty()) return;
        
        // Пример отправки Ping (dataType = 0x00) через транспортный процессор
        NicoleTransportLayerProcessor packer = new NicoleTransportLayerProcessor();
        byte[] pingPack = packer.pack(0x00, new byte[] { 0x00 });
        queue.add(pingPack);
    }

    @PreDestroy
    public void cleanup() {
        stop();
        taskScheduler.shutdown();
        nicoleReader.close();
    }
}