package com.rgs.center.barcodereader;

import com.rgs.center.scales.ScalesRespondedUnit;
import com.rgs.center.scales.ScalesSentUnit;
import com.rgs.center.warehouse.RGSCenterWarehouse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import jssc.SerialPort;

@Service
public class BarcodeService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private final ThreadPoolTaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledChecker;

    @Autowired
    BarcodeReader barcodeReader;
    
    @Autowired
    BarcodeProcessor barcodeProcessor;

    @Autowired
    private RGSCenterWarehouse warehouse;
    
    private final ConcurrentLinkedQueue<byte[]> queue;
    
    public void AddCommandToSend(byte[] abt) {
        queue.add(abt);
    }
    
    private volatile boolean scanningActive = false;
    public boolean isScanningActive() { return scanningActive; }
    
    private long scanningStartTime = 0;
    private static final long SCAN_TIMEOUT_MS = 5000;

    BarcodeSentUnit sent;
    
    public BarcodeService() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("barcode-connection-");
        taskScheduler.initialize();
        
        queue = new ConcurrentLinkedQueue<>(); 
        sent = null;
    }

    public boolean isActive() {
        return( scheduledChecker != null && !scheduledChecker.isCancelled());
    }
    
    @PostConstruct
    public void autoRunOnStart() {
        long lCheckPeriodMsec = 50;
        start(lCheckPeriodMsec); 
    }

    public synchronized void start(long intervalCheckMs) {
        if (scheduledChecker != null && !scheduledChecker.isCancelled()) {
            return;
        }

        scheduledChecker = taskScheduler.scheduleWithFixedDelay(
                this::queue,
                Duration.ofMillis(intervalCheckMs)
        );
    }

    public synchronized void stop() {
        if (scheduledChecker != null && !scheduledChecker.isCancelled()) {
            scheduledChecker.cancel(false);
            scheduledChecker = null;            
        }
        barcodeReader.close();
        scanningActive = false;
    }

    public synchronized void triggerRead() {
        if( barcodeReader.GetSerialPort() == null) {
            logger.warn("Cannot trigger read: port is not open.");
            return;
        }
        try {
            warehouse.putItem(RGSCenterWarehouse.DEVICES_BARCODE_READER_LAST_CODE, "");

            byte[] triggerCmd = new byte[]{0x16, 'T', 0x0D};
            queue.add(triggerCmd);
            
            scanningActive = true;
            scanningStartTime = System.currentTimeMillis();
            logger.info("Barcode reader triggered into reading mode, warehouse reset.");
        } catch (Exception ex) {
            logger.error("Failed to send trigger command to barcode reader", ex);
        }
    }

    private void queue() {
        if ( barcodeReader.GetSerialPort() == null) {
            barcodeReader.open( "COM7", 115200); // Порт и скорость при необходимости скорректируете
            byte[] initCommand1 = new byte[] { 0x16, 'M', 0x0D, 'P', 'A', 'P', 'H', 'H', 'F', '!'};
            queue.add( initCommand1);
            byte[] initCommand2 = new byte[] { 0x16, 'M', 0x0D, 'P', 'W', 'R', 'N', 'O', 'L', '1', '5', '0', '!'};
            queue.add( initCommand2);
            return;
        }

        if( scanningActive) {
            if( System.currentTimeMillis() - scanningStartTime > SCAN_TIMEOUT_MS) {
                //таймаут
                scanningActive = false;
                
                if (barcodeReader.GetSerialPort() == null) {
                    return;
                }
                try {
                    byte[] deactivateCmd = new byte[] { 0x16, 'U', 0x0D};
                    barcodeReader.GetSerialPort().writeBytes( deactivateCmd);
                    logger.info("Barcode reader deactivated.");
                } catch( Exception ex) {
                    logger.error("Failed to send deactivate command to barcode reader", ex);
                }
            }
            else {
                int nData = barcodeReader.GetCb().haveScannedData();
                if( nData > 0) {
                    byte[] scannedCode = new byte[ nData];
                    barcodeReader.GetCb().getAnswer( nData, scannedCode);
                    String strScannedCode = new String( scannedCode, StandardCharsets.US_ASCII).trim();
                    warehouse.putItem(RGSCenterWarehouse.DEVICES_BARCODE_READER_LAST_CODE, strScannedCode);
                    logger.info("Успешно обработан штрих-код: {}", strScannedCode);
                    scanningActive = false;
                }
            }
        }
            
        if( sent != null) {
            int nReplyLen = barcodeReader.GetCb().haveCommandReply();
            if( nReplyLen > 0) {
                byte[] abtRespond = new byte[ nReplyLen];
                barcodeReader.GetCb().getAnswer( nReplyLen, abtRespond);

                BarCodeRespondedUnit unit2 = new BarCodeRespondedUnit( sent, abtRespond);
                barcodeProcessor.submit(unit2);
                sent = null;
            } else {
                if (System.currentTimeMillis() - sent.getSentTimestamp() > 5000) {
                    sent = null;
                    barcodeReader.GetCb().reset();
                }
            }
            return;
        }

        if (!queue.isEmpty()) {
            byte[] abtPack = queue.peek();
            SerialPort port = barcodeReader.GetSerialPort();
            if (port != null) {
                try {
                    port.writeBytes(abtPack);
                    queue.remove();
                    sent = new BarcodeSentUnit(abtPack);
                } catch( Exception ex) {
                    logger.error("Failed to write to scales port", ex);
                }
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        stop();
        taskScheduler.shutdown();
    }
    
    public boolean isRunning() {
        return (scheduledChecker != null && !scheduledChecker.isCancelled());
    }
}