package com.rgs.center.ups;

import com.rgs.center.warehouse.RGSCenterWarehouse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServiceUpsProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterWarehouse warehouse;
    
    private final BlockingQueue<UpsTcpRespondedUnit> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    public ServiceUpsProcessor() {
        this.workerThread = new Thread(this::processQueue, "ups-consumer-worker");
    }

    @PostConstruct
    public void start() {
        workerThread.start();
    }

    public void submit(UpsTcpRespondedUnit item) {
        queue.offer(item);
    }

    private void processQueue() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                UpsTcpRespondedUnit item = queue.take();
                handleItem(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleItem(UpsTcpRespondedUnit item) {
        if (item == null) return;        
        if (item.getSent() == null) return;
        if (item.getRespond() == null) return;

        byte[] abtRespond = item.getRespond();
        
        UpsTransportLayerProcessor unpacker = new UpsTransportLayerProcessor();
        int nRes = unpacker.unpack(abtRespond);
        
        if (nRes == 0) {
            byte[] abtUnpacked = unpacker.getUnPacked();
            int byteCount = abtUnpacked[2] & 0xFF;
            if (abtUnpacked.length < 3 + byteCount) {
                logger.error("UPS unpacker error: data payload too short");
                return;
            }
            
            // Читаем значение регистра 40023 (State of Charge, SoC), scale factor 0.1
            int socRaw = ByteBuffer.wrap(abtUnpacked, 3, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
            double stateOfCharge = socRaw * 0.1;

            warehouse.putItem("device.ups.battery.soc", stateOfCharge);
            logger.debug("UPS Battery State of Charge (SoC): " + stateOfCharge + "%");

        } else {
            logger.debug("UPS transport layer unpacking and CRC-check fails. Code=" + nRes);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        workerThread.interrupt();
    }
}