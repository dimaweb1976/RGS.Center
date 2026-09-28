package com.rgs.center.barcodereader;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BarcodePortReader implements SerialPortEventListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private final SerialPort m_serialPort;
    private final BarcodeCircleBuffer m_bf;
    
    public BarcodePortReader(SerialPort port, BarcodeCircleBuffer bf) {
        m_serialPort = port;
        m_bf = bf;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.isRXCHAR() && event.getEventValue() > 0) {
            try {
                int nReadyBytes = event.getEventValue();
                byte[] bts = m_serialPort.readBytes(nReadyBytes);
                
                if (bts != null && bts.length > 0) {
                    logger.debug("BARCODE READ {} BYTES", nReadyBytes);
                    if (m_bf != null) {
                        m_bf.addBytes(bts, nReadyBytes);
                    } else {
                        logger.error("Barcode buffer is null, bytes lost!");
                    }
                }
            } catch (SerialPortException ex) {
                logger.error("SerialPortException caught in BarcodePortReader", ex);
            }
        }
    }
}