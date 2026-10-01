package com.rgs.center.dobot.command;

import com.rgs.center.settings.RGSCenterSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServiceTcpDobotCommandConnection {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RGSCenterSettings settings;

    @Autowired
    private ServiceDobotCommandProcessor commandProcessor;

    @Autowired
    private DobotCommandIoLogger ioLogger;
    
    private final ConcurrentLinkedQueue<DobotSentCommandUnit> commandQueue = new ConcurrentLinkedQueue<>();
    private final TcpBufferDobotCommandLine tcpBuffer = new TcpBufferDobotCommandLine(4096);

    private ScheduledExecutorService scheduler;
    private Thread readerThread;
    private ServiceTcpDobotCommandSocketReader streamReader;

    private Socket m_socket;
    private java.io.OutputStream m_out;
    private java.io.InputStream m_in;

    private DobotSentCommandUnit activeUnit = null;
    private volatile boolean running = false;

    public boolean isRunning() {
        return running;
    }
    
    @PostConstruct
    public void start() {
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "dobot-command-worker"));
        
        long pollIntervalMs = 50;
        Object intervalObj = settings != null ? settings.getSetting("dobot.command.interval_ms") : null;
        if (intervalObj instanceof Number) {
            pollIntervalMs = ((Number) intervalObj).longValue();
        }

        scheduler.scheduleAtFixedRate(this::processLoop, 1000, pollIntervalMs, TimeUnit.MILLISECONDS);
        logger.info("ServiceTcpDobotConnection started.");
    }

    public void AddCommandToSend(String command) {
        if (command != null && !command.isEmpty()) {
            commandQueue.add(new DobotSentCommandUnit(command));
        }
    }

    private void processLoop() {
        if (!running) return;

        if (!ensureConnection()) {
            return;
        }

        // 1. Проверяем наличие ответа на активную отправленную команду
        if (activeUnit != null) {
            if (tcpBuffer.hasCompletePacket()) {
                String response = tcpBuffer.extractPacket();
                logger.debug("Received response for [{}]: {}", activeUnit.getCommand(), response);
                
                // Логируем ответ от Dobot
                if (ioLogger != null) {
                    ioLogger.logRespond(response);
                }
                
                if (commandProcessor != null) {
                    commandProcessor.processResponse(activeUnit.getCommand(), response);
                }
                
                activeUnit = null; // Освобождаем слот для следующей команды
            } else {
                // Проверяем таймаут
                Object timeoutObj = settings != null ? settings.getSetting("dobot.command.timeout_ms") : null;
                int timeoutMs = (timeoutObj instanceof Number) ? ((Number) timeoutObj).intValue() : 2000;
                if (timeoutMs <= 0) timeoutMs = 2000;

                if (System.currentTimeMillis() - activeUnit.getTimestamp() > timeoutMs) {
                    logger.error("Command timeout expired for: {}", activeUnit.getCommand());
                    activeUnit = null; // Сбрасываем зависшую команду по таймауту
                }
            }
            return;
        }

        // 2. Берем следующую команду из очереди
        DobotSentCommandUnit nextUnit = commandQueue.poll();
        if (nextUnit == null) {
            return;
        }

        try {
            String payload = nextUnit.getCommand() + "\n";
            m_out.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            m_out.flush();

            activeUnit = nextUnit;
            logger.debug("Sent command to Dobot: {}", nextUnit.getCommand());
            
            // Логируем отправку команды Dobot
            if (ioLogger != null) {
                ioLogger.logSent(nextUnit.getCommand());
            }
            
        } catch (Exception ex) {
            logger.error("Failed to send command: " + nextUnit.getCommand(), ex);
            closeConnection();
        }
    }

    private synchronized boolean ensureConnection() {
        if (m_socket != null && m_socket.isConnected() && !m_socket.isClosed()) {
            return true;
        }

        try {
            closeConnection();

            String host = "localhost";
            int port = 29999;
            
            Object hostObj = settings != null ? settings.getSetting("dobot.host") : null;
            if (hostObj instanceof String && !((String) hostObj).isEmpty()) {
                host = (String) hostObj;
            }

            m_socket = new Socket(host, port);
            m_out = m_socket.getOutputStream();
            m_in = m_socket.getInputStream();

            tcpBuffer.clear();
            streamReader = new ServiceTcpDobotCommandSocketReader(m_in, tcpBuffer);
            readerThread = new Thread(streamReader, "dobot-command-reader");
            readerThread.start();

            logger.info("Connected to Dobot command port {}:{} successfully.", host, port);
            return true;
        } catch (Exception ex) {
            //logger.error("Failed to connect to Dobot command port 29999", ex.getMessage());
            return false;
        }
    }

    private synchronized void closeConnection() {
        if (streamReader != null) {
            streamReader.stop();
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
        try {
            if (m_in != null) m_in.close();
            if (m_out != null) m_out.close();
            if (m_socket != null && !m_socket.isClosed()) m_socket.close();
        } catch (Exception ex) {
            logger.error("Error closing connection", ex);
        } finally {
            m_socket = null;
            m_out = null;
            m_in = null;
            activeUnit = null;
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        
        // 1. Останавливаем ридер сокета
        if (streamReader != null) {
            streamReader.stop();
        }
        
        // 2. Останавливаем планировщик отправки команд
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        
        // 3. Прерываем поток чтения
        if (readerThread != null) {
            readerThread.interrupt();
        }
        
        // 4. Закрываем сокет
        try {
            if (m_socket != null && !m_socket.isClosed()) {
                m_socket.close();
            }
        } catch (Exception ex) {
            logger.error("Error closing socket during stop", ex);
        } finally {
            m_socket = null;
            m_out = null;
            m_in = null;
            activeUnit = null;
        }
        logger.info("ServiceTcpDobotCommandConnection stopped.");
    }
}