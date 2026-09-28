package com.rgs.center.scales;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScalesPortReader implements SerialPortEventListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private final SerialPort m_serialPort;
    private final ScalesCircleBuffer m_bf;
    
    public ScalesPortReader(SerialPort port, ScalesCircleBuffer bf) {
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
                    logger.debug("SCALES READ {} BYTE. FIRST ONE=0x%02X", nReadyBytes, bts[0]);
                    if (m_bf != null) {
                        m_bf.addBytes(bts, nReadyBytes);
                    } else {
                        logger.error("Scales buffer is null, bytes lost!");
                    }
                }
            } catch (SerialPortException ex) {
                logger.error("SerialPortException caught in ScalesPortReader", ex);
            }
        }
    }
}