package com.rgs.center.shp;

import com.rgs.center.warehouse.RGSCenterWarehouse;
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

@Service
public class ServiceShpProcessor {
    /**
     * logger variable
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterWarehouse warehouse;
    
    // Thread-safe blocking queue between threads
    private final BlockingQueue<ShpRespondedUnit> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    public ServiceShpProcessor() {
        this.workerThread = new Thread(this::processQueue, "consumer-worker");
    }

    @PostConstruct
    public void start() {
        workerThread.start();
    }

    // Producer service calls this method to submit objects
    public void submit(ShpRespondedUnit item) {
        queue.offer(item);
    }

    private void processQueue() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Thread sleeps here until an item is available
                ShpRespondedUnit item = queue.take();
                handleItem(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleItem( ShpRespondedUnit item) {
        int nResult = 0;

        if( item == null) return;        
        if( item.getSent() == null) return;
        if( item.getRespond() == null) return;

        byte [] abtSent = item.getSent();
        byte [] abtRespond = item.getRespond();
        
        TransportLayerProcessor unpacker = new TransportLayerProcessor();
        int nRes = unpacker.UnPackThePack( abtRespond);
        if( nRes == 0) {
            byte [] abtUnpacked = unpacker.getUnPacked();
            if( abtUnpacked.length != 17)
                logger.error( "BDYSCH1 "  + abtUnpacked.length);
                            
            //2.check if you have req
            switch( ( abtUnpacked[ 0] & 0xFF)) {
                case 0x91:
                    //SENSORS_SHP - now we should understand what have we requested?
                    switch( ( abtUnpacked[ 1] & 0xFF)) {
                        case 0x03:
                        {
                            //requesting of temperature
                            byte[] bytes = { ( byte) ( abtUnpacked[ 2] & 0xFF),
                                             ( byte) ( abtUnpacked[ 3] & 0xFF),
                                             ( byte) ( abtUnpacked[ 4] & 0xFF),
                                             ( byte) ( abtUnpacked[ 5] & 0xFF)
                                           };
                            float f = ByteBuffer.wrap(bytes).order( ByteOrder.BIG_ENDIAN).getFloat();

                            warehouse.putItem( RGSCenterWarehouse.DEVICES_SHP_DETECTOR_TEMPERATURE, f);
                        }
                        break;

                        case 0x04:
                        {
                            //requesting of "voltage at the output of the charging circuit"
                            byte[] bytes = { ( byte) ( abtUnpacked[ 2] & 0xFF),
                                             ( byte) ( abtUnpacked[ 3] & 0xFF),
                                             ( byte) ( abtUnpacked[ 4] & 0xFF),
                                             ( byte) ( abtUnpacked[ 5] & 0xFF)
                                            };
                            float f1 = ByteBuffer.wrap(bytes).order( ByteOrder.BIG_ENDIAN).getFloat();


                            //theApp.GetGetEqStand().putParameter( StandGetEqConstants.MU_CHARGE_OUTPUT_VOLTAGE, f);

                            byte [] abtCopy = { 0,0,0,0,0,0,0,0,0,0,0,0};
                            System.arraycopy( abtUnpacked, 6, abtCopy, 0, 8);
                            String str = new String( abtCopy);
                            try {
                                double d = Double.parseDouble( str);
                                float f2 = ( float) d;
                                //if( f1 != f2) {
                                //    logger.fatal( String.format( "BDYSCH2   %.5f %.5f", f1, f2));
                                //}
                                warehouse.putItem( RGSCenterWarehouse.DEVICES_SHP_CHARGE_OUTPUT_VOLTAGE, f2);
                            }
                            catch( NumberFormatException ex) {
                                logger.error( "", ex);
                                warehouse.putItem( RGSCenterWarehouse.DEVICES_SHP_CHARGE_OUTPUT_VOLTAGE, f1);
                            }


                        }
                        break;

                        case 0x05:
                        {
                            //requesting of current
                            byte [] bytes = { ( byte) ( abtUnpacked[ 2] & 0xFF),
                                             ( byte) ( abtUnpacked[ 3] & 0xFF),
                                             ( byte) ( abtUnpacked[ 4] & 0xFF),
                                              ( byte) ( abtUnpacked[ 5] & 0xFF)
                                            };
                            float f = ByteBuffer.wrap(bytes).order( ByteOrder.BIG_ENDIAN).getFloat();
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_SHP_CURRENT, f);

                        }
                        break;

                        default:
                            nResult = 710;
                    }
                break;

                case 0x92:
                    //PROPERTIES_SHP
                    switch( ( abtUnpacked[1] & 0xFF)) {
                        case 0x06:
                        {
                            int nHwVer = ( abtUnpacked[ 3] & 0xFF) << 8 + ( abtUnpacked[ 4] & 0xFF);
                            int nFwVer = ( abtUnpacked[ 5] & 0xFF) << 8 + ( abtUnpacked[ 6] & 0xFF);

                            warehouse.putItem( RGSCenterWarehouse.DEVICES_SHP_HW_VERSION, nHwVer);
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_SHP_FW_VERSION, nFwVer);
                        }
                        break;
                        case 0x0D:
                            GregorianCalendar cld;
                            cld = new GregorianCalendar(
                                    ( abtUnpacked[ 5] & 0xFF) * 100 + ( abtUnpacked[ 6] & 0xFF),       //year 
                                    ( abtUnpacked[ 4] & 0xFF) - 1,
                                    ( abtUnpacked[ 3] & 0xFF));
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_SHP_DATE_OF_MANUFACTURE, cld);
                        break;
                        default:
                            nResult = 720;
                    }
                break;

                case 0x95:
                    //SETUP_SHP?
                    switch( ( abtUnpacked[1] & 0xFF)) {

                        case 0x07:
                        {
                            //requesting of resistance
                            int nR = ( int) ( abtUnpacked[3] & 0xFF);
                            warehouse.putItem( RGSCenterWarehouse.DEVICES_SHP_RESISTANCE, nR);
                        }
                        break;

                        default:
                            nResult = 750;
                    }
                break;

                default:
                    nResult = 5;
            }
        }
        else {
            //TRANSPORT LEVEL UNPACKING AND CRC-CHECK FAILS
            logger.debug( "TRANSPORT LEVEL UNPACKING AND CRC-CHECK FAILS. Code=" + nRes);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        workerThread.interrupt();
    }
}