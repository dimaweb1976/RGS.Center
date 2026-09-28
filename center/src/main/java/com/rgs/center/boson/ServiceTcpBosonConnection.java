package com.rgs.center.boson;

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
public class ServiceTcpBosonConnection {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RGSCenterSettings settings;

    @Autowired
    BosonIoLogger ioLogger;

    @Autowired
    ServiceTcpBosonReader bosonReader;

    @Autowired
    BosonTcpRespondsProcessor bosonProcessor;

    private final ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledQueue;
    private ScheduledFuture<?> scheduledPoller;

    private final ConcurrentLinkedDeque<byte[]> queue;
    private BosonTcpSentUnit sent;

    public ServiceTcpBosonConnection() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("tcp-boson-connection-");
        taskScheduler.initialize();

        queue = new ConcurrentLinkedDeque<>();
        sent = null;
    }

    public void addCommandToSend(byte[] abt) {
        queue.addLast(abt);
    }

    public void addCommandToSendFirst(byte[] abt) {
        queue.addFirst(abt);
    }

    @PostConstruct
    public void autoRunOnStart() {
        long lPeriodMs = 200;
        start(lPeriodMs, 1000);
    }

    public synchronized void start(long intervalQueueMs, long intervalPollerMs) {
        if (scheduledQueue != null && !scheduledQueue.isCancelled()) return;

        scheduledQueue = taskScheduler.scheduleWithFixedDelay(
                this::queueProcess,
                Duration.ofMillis(intervalQueueMs)
        );
        
        if (scheduledPoller != null && !scheduledPoller.isCancelled()) return;

        scheduledPoller = taskScheduler.scheduleWithFixedDelay(
                this::pollProcess,
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

    private void queueProcess() {
        if (bosonReader.getSocket() == null || !bosonReader.getSocket().isConnected()) {
            bosonReader.connect("localhost", 3000);
            return;
        }

        if (sent != null) {
            int nLen = bosonReader.GetCb().isAnswerReady();
            if (nLen > 0) {
                byte[] abtRespond = new byte[nLen];
                bosonReader.GetCb().getAnswer(nLen, abtRespond);

                BosonTcpRespondedUnit unit2 = new BosonTcpRespondedUnit(sent, abtRespond);
                bosonProcessor.submit(unit2);
                sent = null;
                bosonReader.GetCb().setExpectedLen(0);
            } else {
                if (System.currentTimeMillis() - sent.getSentTime() > 10000) {
                    sent = null;
                    bosonReader.GetCb().setExpectedLen(0);
                    bosonReader.resetCb();
                }
            }
            return;
        }

        if (!queue.isEmpty()) {
            byte[] abtPack = queue.peek();
            try {
                OutputStream out = bosonReader.getSocket().getOutputStream();
                out.write(abtPack);
                out.flush();
                queue.remove();
                sent = new BosonTcpSentUnit(abtPack);

                ioLogger.logSent(abtPack);
            } catch (Exception ex) {
                logger.error("BOSON TCP write exception", ex);
            }
        }
    }

    private void pollProcess() {
        if (bosonReader.getSocket() == null || !bosonReader.getSocket().isConnected()) return;
        if (!queue.isEmpty()) return; // Не поллим, если в очереди есть активные/внеочередные команды
        if (sent != null) return;     // Ждем завершения текущей отправки

        // Формируем команды для периодического опроса (GetSettings и GetData из вашего примера)
        byte[] btGetSettings = {
            (byte) 0xA5, 0x00, 0x03, 0x06,     // addr, cmd, subcmd[cite: 9]
            0x00, 0x00,     // param1[cite: 9]
            0x00, 0x00,     // param2[cite: 9]
            0x00, 0x00,     // param3[cite: 9]
            (byte) 0xF6, (byte) 0x5A        // CS, stop[cite: 9]
        };

        byte[] btGetData = {
            (byte) 0xA5, 0x00, 0x03, 0x05,     // addr, cmd, subcmd[cite: 9]
            0x00, 0x00,     // param1[cite: 9]
            0x00, 0x00,     // param2[cite: 9]
            0x00, 0x00,     // param3[cite: 9]
            (byte) 0xF7, (byte) 0x5A        // CS, stop[cite: 9]
        };

        // Для обеих команд ожидаемый ответ равен 126 байтам[cite: 9]
        bosonReader.GetCb().setExpectedLen(126);

        // Добавляем их в очередь на отправку
        queue.addLast(btGetSettings);
        queue.addLast(btGetData);
    }

    @PreDestroy
    public void cleanup() {
        stop();
        taskScheduler.shutdown();
        bosonReader.close();
    }
}