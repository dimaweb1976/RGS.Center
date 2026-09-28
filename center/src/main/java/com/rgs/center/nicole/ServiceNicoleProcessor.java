package com.rgs.center.nicole;

import com.rgs.center.warehouse.RGSCenterWarehouse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ServiceNicoleProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterWarehouse warehouse;
    
    private final BlockingQueue<NicoleTcpRespondedUnit> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    public ServiceNicoleProcessor() {
        this.workerThread = new Thread(this::processQueue, "nicole-consumer-worker");
    }

    @PostConstruct
    public void start() {
        workerThread.start();
    }

    public void submit(NicoleTcpRespondedUnit item) {
        queue.offer(item);
    }

    private void processQueue() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                NicoleTcpRespondedUnit item = queue.take();
                handleItem(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleItem(NicoleTcpRespondedUnit item) {
        if (item == null) return;        
        if (item.getSent() == null) return;
        if (item.getRespond() == null) return;

        byte[] abtRespond = item.getRespond();
        
        NicoleTransportLayerProcessor unpacker = new NicoleTransportLayerProcessor();
        int nRes = unpacker.unpack(abtRespond);
        
        if (nRes == 0) {
            byte[] abtUnpacked = unpacker.getUnPacked();
            if (abtUnpacked == null || abtUnpacked.length < 2) {
                logger.error("Nicole unpacker error: data too short");
                return;
            }
            
            int dataSize = abtUnpacked[0] & 0xFF;
            int dataType = abtUnpacked[1] & 0xFF;
            
            // Данные начинаются с индекса 2 (после Data Size и Data Type)
            byte[] dataPayload = new byte[dataSize];
            if (abtUnpacked.length >= 2 + dataSize) {
                System.arraycopy(abtUnpacked, 2, dataPayload, 0, dataSize);
            }
            
            switch (dataType) {
                case 0x10: // ACK Ping (Pong)
                {
                    if (dataPayload.length >= 26) { // 13 слов по 2 байта = 26 байт
                        // offset 0: NICOLE Status
                        int nicoleStatus = ByteBuffer.wrap(dataPayload, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        int resetCause = nicoleStatus & 0xFF; // 0..7 биты
                        boolean ethUp = (nicoleStatus & (1 << 8)) != 0; // 8 бит
                        boolean dhcpLeased = (nicoleStatus & (1 << 9)) != 0; // 9 бит
                        boolean cryoComm = (nicoleStatus & (1 << 10)) != 0; // 10 бит

                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_STATUS, nicoleStatus);
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_RESET_CAUSE, resetCause);
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_ETH_UP, ethUp);
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_DHCP_LEASED, dhcpLeased);
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_CRYO_COM, cryoComm);

                        // offset 1: Cooler status
                        int coolerStatus = ByteBuffer.wrap(dataPayload, 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        boolean coolerStarted = (coolerStatus & 1) != 0; // 0 бит
                        boolean coolerAdvanced = (coolerStatus & 2) != 0; // 1 бит

                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_COOLER_STATUS, coolerStatus);
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_COOLER_STARTED, coolerStarted);
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_COOLER_ADVANCED, coolerAdvanced);

                        // offset 2 & 3: Current N level & pressure
                        int nLevel = ByteBuffer.wrap(dataPayload, 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        int nPressure = ByteBuffer.wrap(dataPayload, 6, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_N2_LEVEL, nLevel);
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_N2_PRESSURE, nPressure);

                        // offset 4 & 5: voltages
                        int voltageLow = ByteBuffer.wrap(dataPayload, 8, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        int voltageHigh = ByteBuffer.wrap(dataPayload, 10, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_VOLTAGE_LOW, voltageLow);
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_VOLTAGE_HIGH, voltageHigh);

                        // offset 6 & 7: Frequency (32 bits, Little Endian)
                        long freqLow = ByteBuffer.wrap(dataPayload, 12, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        long freqHigh = ByteBuffer.wrap(dataPayload, 14, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        long frequency = (freqHigh << 16) | freqLow;
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_FREQ, frequency);

                        // offset 8: Часы, минуты (младший байт = часы, старший байт = минуты)
                        int timeWord = ByteBuffer.wrap(dataPayload, 16, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        int hours = timeWord & 0xFF;
                        int minutes = (timeWord >> 8) & 0xFF;
                        warehouse.putItem("device.nicole.time.hours", hours);
                        warehouse.putItem("device.nicole.time.minutes", minutes);

                        // offset 9: Секунды, день (младший байт = секунды, старший байт = день)
                        int secDayWord = ByteBuffer.wrap(dataPayload, 18, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        int seconds = secDayWord & 0xFF;
                        int day = (secDayWord >> 8) & 0xFF;
                        warehouse.putItem("device.nicole.time.seconds", seconds);
                        warehouse.putItem("device.nicole.date.day", day);

                        // offset 10: Месяц, год (младший байт = месяц, старший байт = год)
                        int monthYearWord = ByteBuffer.wrap(dataPayload, 20, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        int month = monthYearWord & 0xFF;
                        int year = (monthYearWord >> 8) & 0xFF;
                        warehouse.putItem("device.nicole.date.month", month);
                        warehouse.putItem("device.nicole.date.year", year);

                        // offset 11: Cooler error code
                        int coolerErrorCode = ByteBuffer.wrap(dataPayload, 22, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_COOLER_ERR_CODE, coolerErrorCode);

                        // offset 12: Cooler Error, Warning messages (16 bits)
                        int coolerWarnings = ByteBuffer.wrap(dataPayload, 24, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                        warehouse.putItem( RGSCenterWarehouse.DEVICES_NICOLE_COOLER_WARNINGS, coolerWarnings);
                    }
                    break;
                }
                    
                case 0x11: // ACK Request Display Codes
                    break;
                    
                case 0xEE: // ACK Request Error (NACK)
                    logger.error("Nicole device returned NACK error response");
                    break;
                    
                default:
                    logger.debug(String.format("Unhandled Nicole data type: 0x%02X", dataType));
                    break;
            }
        } else {
            logger.debug("Nicole transport layer unpacking and CRC-check fails. Code=" + nRes);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        workerThread.interrupt();
    }
}