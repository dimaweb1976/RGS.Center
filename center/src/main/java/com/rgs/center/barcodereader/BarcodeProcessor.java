package com.rgs.center.barcodereader;

import com.rgs.center.scales.ScalesRespondedUnit;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class BarcodeProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final BlockingQueue< BarCodeRespondedUnit> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;
    
    public BarcodeProcessor() {
        this.workerThread = new Thread(this::processQueue, "barcode-consumer-worker");
    }
    
    @PostConstruct
    public void start() {
        workerThread.start();
    }
    
    // Producer service calls this method to submit objects
    public void submit( BarCodeRespondedUnit item) {
        queue.offer( item);
    }
    
    private void processQueue() {
        while( running && !Thread.currentThread().isInterrupted()) {
            try {
                // Thread sleeps here until an item is available
                BarCodeRespondedUnit item = queue.take();
                handleItem( item);
            } catch( InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    // Helper method to format byte array
    private String printHexBinary(byte[] data) {
        if (data == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format("%02X", data[i]));
            if (i < data.length - 1) {
                sb.append(" ");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private void handleItem( BarCodeRespondedUnit item) {
        if (item == null) return;
        if (item.getAbtResponse() == null) return;

        byte[] abtSent = item.getAbtSent();
        byte[] abtRespond = item.getAbtResponse();
        
        //STUB!
        logger.debug("Barcode command {} response {} stubbed!", 
                printHexBinary( abtSent), 
                printHexBinary( abtRespond));
    }

    @PreDestroy
    public void stop() {
        running = false;
        workerThread.interrupt();
    }
}