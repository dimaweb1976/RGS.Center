package com.rgs.center.scales;

import com.rgs.center.warehouse.RGSCenterWarehouse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ScalesProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterWarehouse warehouse;
    
    // Thread-safe blocking queue between threads
    private final BlockingQueue<ScalesRespondedUnit> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    public ScalesProcessor() {
        this.workerThread = new Thread(this::processQueue, "scales-consumer-worker");
    }

    @PostConstruct
    public void start() {
        workerThread.start();
    }

    // Producer service calls this method to submit objects
    public void submit( ScalesRespondedUnit item) {
        queue.offer( item);
    }

    private void processQueue() {
        while( running && !Thread.currentThread().isInterrupted()) {
            try {
                // Thread sleeps here until an item is available
                ScalesRespondedUnit item = queue.take();
                handleItem( item);
            } catch( InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleItem(ScalesRespondedUnit item) {
        if (item == null) return;
        if (item.getRespond() == null) return;

        byte[] abtRespond = item.getRespond();
        
        try {
            String responseStr = new String(abtRespond, StandardCharsets.US_ASCII).trim();
            
            // Пример кадра от весов согласно спецификации:
            // S I _ ? _ _ _ _ _ _ _ 1 8 . 5 _ k g _ \r\n
            // Или если ответ содержит эхо команды SI, парсим правую часть с массой
            
            if (responseStr.length() >= 16) {
                // Извлекаем символы массы (позиции с 6 по 14 в соответствии с маской протокола)
                String massText = responseStr.substring(6, 15).trim();
                double weight = Double.parseDouble(massText);
                
                // Проверяем знак (если на 5 позиции стоит минус)
                if (responseStr.charAt(5) == '-') {
                    weight = -weight;
                }
                
                // Сохраняем разобранный вес в витрину данных
                warehouse.putItem(RGSCenterWarehouse.DEVICES_SCALES_MASS, weight);
                //warehouse.putItem(RGSCenterWarehouse.DEVICES_SCALES_RAW_RESPONSE, responseStr);
                
                logger.debug("Scales parsed weight: {}", weight);
            }
        } catch (Exception ex) {
            logger.error("Failed to parse scales response: {}", new String(abtRespond, StandardCharsets.US_ASCII), ex);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        workerThread.interrupt();
    }
}