package com.rgs.center.gammapro;

import com.rgs.center.warehouse.RGSCenterWarehouse;
import com.rgs.center.shp.TransportLayerProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.GregorianCalendar;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

@Service
public class ServiceGammaProTcpRespondsProcessor {
    /**
     * logger variable
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterWarehouse warehouse;
    
    @Autowired
    GammaProIoLogger ioLogger;
    
    @Autowired
    @Lazy
    ServiceTcpGammaProConnection serviceGammaPro;
    
    // Thread-safe blocking queue between threads
    private final BlockingQueue<GammaProTcpRespondedUnit> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    public ServiceGammaProTcpRespondsProcessor() {
        this.workerThread = new Thread(this::processQueue, "consumer-worker");
    }

    @PostConstruct
    public void start() {
        workerThread.start();
    }

    // Producer service calls this method to submit objects
    public void submit(GammaProTcpRespondedUnit item) {
        queue.offer(item);
    }

    private void processQueue() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Thread sleeps here until an item is available
                GammaProTcpRespondedUnit item = queue.take();
                handleItem(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleItem( GammaProTcpRespondedUnit item) {
        int nResult = 0;

        if( item == null) return;        
        if( item.getSent() == null) return;
        if( item.getRespond() == null) return;

        byte [] abtSent = item.getSent();
        byte [] abtRespond = item.getRespond();
        
        // Логируем полученный ответ
        ioLogger.logRespond(abtRespond);
        
        // Преобразуем отправленный запрос и ответ в строки (с учетом кодировки, например, US-ASCII или UTF-8)
        String strSent = new String( abtSent).trim();
        String strRespond = new String( abtRespond).trim();
        
        if( strSent == null) return;
        if( strRespond == null) return;
        
        // Определяем, что мы запрашивали, и парсим ответ
        if( strSent.startsWith( "SET_TRACT(")) {
            if( strRespond.startsWith( "SET_TRACT=OK")) {
                //success
            }
            else {
                //fail
            }
        }
        
        // Определяем, что мы запрашивали, и парсим ответ
        if( strSent.startsWith( "SET_HV(")) {
            if( strRespond.startsWith( "SET_HV=OK")) {
                //success
            }
            else {
                //fail
            }
        }
        
        if( strSent.startsWith( "WAIT_MEAS_END(")) {
            if( strRespond.startsWith( "WAIT_MEAS_END=OK")) {
                //success
            }
            else {
                //maybe it is the goal =DO ? then - do nothing?
                //repeat this command
                //serviceGammaPro.AddCommandToSendFirst( abtSent);
            }
        }
        
    }

    @PreDestroy
    public void stop() {
        running = false;
        workerThread.interrupt();
    }
}