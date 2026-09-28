package com.rgs.center.dobot;

import com.rgs.center.settings.RGSCenterSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class ServiceTcpDobotStreamLineConnection {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterSettings settings;
    
    private final ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledPoller;

    @Autowired
    ServiceTcpDobotReader dobotReader;
    
    @Autowired
    ServiceDobotStreamLineProcessor dobotProcessor;
            
    public ServiceTcpDobotStreamLineConnection() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("tcp-dobot-connection-");
        taskScheduler.initialize();
    }

    public boolean isActive() {
        return( scheduledPoller != null && !scheduledPoller.isCancelled());
    }
    
    @PostConstruct
    public void autoRunOnStart() {
        // Период опроса буфера (например, каждые 50 или 100 мс)
        Object settingVal = settings.getSetting("dobot.poller_service.period_ms");
        long lPollServicePeriodMsec = (settingVal instanceof Number) ? ((Number) settingVal).longValue() : 100;
        if (lPollServicePeriodMsec == 0) lPollServicePeriodMsec = 100;

        start(lPollServicePeriodMsec);
    }

    public synchronized void start(long intervalPollerMs) {
        if (scheduledPoller != null && !scheduledPoller.isCancelled()) return;

        scheduledPoller = taskScheduler.scheduleWithFixedDelay(
                this::poll,
                Duration.ofMillis(intervalPollerMs)
        );
    }

    public boolean isRunning() {
        return (scheduledPoller != null);
    }
    
    public synchronized void stop() {
        if (scheduledPoller != null && !scheduledPoller.isCancelled()) {
            scheduledPoller.cancel(false);
            scheduledPoller = null;            
        }
        dobotReader.close();
    }

    
    private void poll() {
        // 1. Проверяем состояние соединения, если сокет не подключен — пытаемся подключиться (порт 30006)
        if (dobotReader.getSocket() == null || !dobotReader.getSocket().isConnected() || dobotReader.getSocket().isClosed()) {
            dobotReader.connect("localhost", 30006);
            return;
        }
        
        // 2. Периодически смотрим в кольцевой буфер через isAnswerReady()
        int nReadyLen = dobotReader.isAnswerReady();
        if (nReadyLen > 0) {
            byte[] abtRespond = new byte[nReadyLen];
            // Извлекаем готовую пачку байтов (1440 байт) из буфера
            if (dobotReader.GetCb().getAnswer(nReadyLen, abtRespond) == 0) {
                // Передаем пачку в процессор без объекта-инициатора
                dobotProcessor.submit(abtRespond);
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        stop();
        taskScheduler.shutdown();
        dobotReader.close();
    }
}