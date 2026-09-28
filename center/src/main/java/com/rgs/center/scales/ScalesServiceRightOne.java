package com.rgs.center.scales;

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
public class ScalesServiceRightOne {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private final ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledQueue;
    private ScheduledFuture<?> scheduledPoller;

    @Autowired
    ScalesReader scalesReader;
    
    @Autowired
    ScalesProcessor scalesProcessor;
            
    private final ConcurrentLinkedQueue<byte[]> queue;
    
    public void AddCommandToSend(byte[] abt) {
        queue.add(abt);
    }
    
    ScalesSentUnit sent;
    
    public ScalesServiceRightOne() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("scales-connection-");
        taskScheduler.initialize();
        
        queue = new ConcurrentLinkedQueue<>(); 
        sent = null;
    }

    @PostConstruct
    public void autoRunOnStart() {
        // Период обработки очереди и поллинга (можно вынести в настройки)
        long lQueuePeriodMsec = 100;
        long lPollPeriodMsec = 1000;
        
        start(lQueuePeriodMsec, lPollPeriodMsec); 
    }

    public synchronized void start(long intervalQueueMs, long intervalPollerMs) {
        if (scheduledQueue != null && !scheduledQueue.isCancelled()) {
            return;
        }

        scheduledQueue = taskScheduler.scheduleWithFixedDelay(
                this::queue,
                Duration.ofMillis(intervalQueueMs)
        );
        
        if (scheduledPoller != null && !scheduledPoller.isCancelled()) {
            return;
        }

        scheduledPoller = taskScheduler.scheduleWithFixedDelay(
                this::poll,
                Duration.ofMillis(intervalPollerMs)
        );
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
            if (scalesReader.GetSerialPort() == null) {
                scalesReader.open("COM10", 9600); // Порт и скорость при необходимости скорректируете
                return;
            }

            if (sent != null) {
                if (scalesReader.isAnswerReady()) {
                    int nLen = scalesReader.GetCb().getReadyIncomingDataLen();
                    byte[] abtRespond = new byte[nLen];
                    scalesReader.GetCb().getAnswer(nLen, abtRespond);

                    ScalesRespondedUnit unit2 = new ScalesRespondedUnit(sent, abtRespond);
                    scalesProcessor.submit(unit2);
                    sent = null;
                } else {
                    if (System.currentTimeMillis() - sent.getSentTime() > 5000) {
                        sent = null;
                        scalesReader.resetCb();
                    }
                }
                return;
            }

            if (!queue.isEmpty()) {
                byte[] abtPack = queue.peek();
                SerialPort port = scalesReader.GetSerialPort();
                if (port != null) {
                    try {
                        port.writeBytes(abtPack);
                        queue.remove();
                        sent = new ScalesSentUnit(abtPack);
                    } catch (Exception ex) {
                        logger.error("Failed to write to scales port", ex);
                    }
                }
            }
    }
    
    private void poll() {
        if (scalesReader.GetSerialPort() == null) {
            return;
        }
        
        if (!queue.isEmpty()) return;
        
        // Команда запроса массы для весов RADWAG ("SI\r\n")
        byte[] abtCommand = {'S', 'I', 0x0D, 0x0A};
        queue.add(abtCommand);
    }

    @PreDestroy
    public void cleanup() {
        stop();
        taskScheduler.shutdown();
    }
    
    public boolean isRunning() {
        return (scheduledQueue != null && !scheduledQueue.isCancelled()) || 
               (scheduledPoller != null && !scheduledPoller.isCancelled());
    }
}