package com.rgs.center.boson;

import com.rgs.center.warehouse.RGSCenterWarehouse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class BosonTcpRespondsProcessor {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterWarehouse warehouse;
    
    @Autowired
    BosonIoLogger ioLogger;
    
    @Autowired
    @Lazy
    ServiceTcpBosonConnection serviceBoson;
    
    private boolean m_bInitiated = false;
    public boolean isInitiated() { return m_bInitiated; }
    
    private final BlockingQueue<BosonTcpRespondedUnit> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    public BosonTcpRespondsProcessor() {
        this.workerThread = new Thread(this::processQueue, "boson-consumer-worker");
    }

    @PostConstruct
    public void start() {
        m_bInitiated = false;
        workerThread.start();
    }

    public void submit(BosonTcpRespondedUnit item) {
        queue.offer(item);
    }

    private void processQueue() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                BosonTcpRespondedUnit item = queue.take();
                handleItem(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleItem(BosonTcpRespondedUnit item) {
        if (item == null) return;        
        if (item.getSent() == null) return;
        if (item.getRespond() == null) return;

        byte[] abtSent = item.getSent();
        byte[] abtRespond = item.getRespond();
        
        ioLogger.logRespond(abtRespond);
        
        if (abtSent.length > 3) {
            switch (abtSent[2]) {
                case 2: {
                    boolean bOk = true;
                    for (int i = 0; i < 12 && i < abtRespond.length && i < abtSent.length; i++) {
                        if (abtRespond[i] != abtSent[i]) bOk = false;
                    }
                    logger.debug("BOSON HV cmd {}", bOk ? "ok" : "fail");
                    break;
                }
                case 3: {
                    switch (abtSent[3]) {
                        case 5: { // GetData response (126 bytes)
                            int nHV_State = (abtRespond[13] & 0xFF) + ((abtRespond[12] & 0xFF) << 8);
                            int nHV2_State = (nHV_State & 0xE0) >> 5;

                            if (nHV2_State == 6) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        byte[] btOff = {
                                            (byte) 0xA5, 0x00, 0x02, 0x0D,
                                            0x03, 0x00, 0x00, 0x00, 0x02, 0x00,
                                            (byte) 0xEB, (byte) 0x5A
                                        };
                                        serviceBoson.addCommandToSend(btOff);

                                        try { Thread.sleep(2000); } catch (InterruptedException ex) { logger.warn("", ex); }

                                        byte[] btOn = {
                                            (byte) 0xA5, 0x00, 0x02, 0x0D,
                                            0x03, 0x00, 0x01, 0x00, 0x02, 0x00,
                                            (byte) 0xEA, (byte) 0x5A
                                        };
                                        serviceBoson.addCommandToSend(btOn);
                                    }
                                }).start();
                            }

                            warehouse.putItem(RGSCenterWarehouse.DEVICES_BOSON_HV_IS_APPLIED, nHV2_State);

                            int nHV1Actual = (abtRespond[96] & 0xFF) + ((abtRespond[97] & 0xFF) << 8);
                            int nHV2Actual = (abtRespond[98] & 0xFF) + ((abtRespond[99] & 0xFF) << 8);
                            
                            warehouse.putItem(RGSCenterWarehouse.DEVICES_BOSON_HV_ACTUAL, nHV2Actual);

                            m_bInitiated = true;
                            break;
                        }
                        case 6: { // GetSettings response (126 bytes)
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_BOSON_MODIFICATION, ( abtRespond[ 12] & 0xFF));
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_BOSON_VERSION, ( abtRespond[ 13] & 0xFF));
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_BOSON_PHYS_MANUFACTURE, ( abtRespond[ 14] & 0xFF));
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_BOSON_DEVICE_CODE, ( abtRespond[ 15] & 0xFF));

                            int serial = ( abtRespond[ 16] & 0xFF) + ( ( abtRespond[ 17] & 0xFF) << 8);
                            int id = ( abtRespond[ 18] & 0xFF) + ( ( abtRespond[ 19] & 0xFF) << 8);
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_BOSON_SERIAL, serial);
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_BOSON_ID, id);

                            int nHV_Preset = ( abtRespond[ 56] & 0xFF) + ( ( abtRespond[ 57] & 0xFF) << 8);
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_BOSON_HV_PRESET, nHV_Preset);

                            m_bInitiated = true;
                            break;
                        }
                        default:
                            logger.warn("Unknown BOSON 3- subcommand: {}", abtSent[3]);
                    }
                    break;
                }
                default:
                    logger.warn("Unknown BOSON command: {}", abtSent[2]);
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        workerThread.interrupt();
    }
}