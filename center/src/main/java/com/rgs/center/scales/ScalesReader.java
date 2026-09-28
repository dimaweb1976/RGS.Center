package com.rgs.center.scales;

import org.springframework.stereotype.Service;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ScalesReader {

    /**
     * logger variable
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    //serial port itself
    private SerialPort m_serialPort;
    public SerialPort GetSerialPort() {
        return m_serialPort;
    }
    
    ScalesPortReader m_evListener;
    
    //buffer to receive incoming bytes (one-by-one from Listener)
    private ScalesCircleBuffer m_pCircleBuffer;
    public String GetCbState() { return m_pCircleBuffer.toString(); }
    public ScalesCircleBuffer GetCb() { return m_pCircleBuffer; }
    
    public ScalesReader() {
        m_pCircleBuffer = new ScalesCircleBuffer();
    }

    public synchronized void open(String strPort, int baudRate) {
        m_pCircleBuffer = new ScalesCircleBuffer();
        if (m_serialPort != null && m_serialPort.isOpened()) return;
        
        m_serialPort = new SerialPort(strPort);
        try {
            m_serialPort.openPort();
            m_serialPort.setParams(baudRate,
                                 SerialPort.DATABITS_8,
                                 SerialPort.STOPBITS_1,
                                 SerialPort.PARITY_NONE);

            m_evListener = new ScalesPortReader(m_serialPort, m_pCircleBuffer);
            m_serialPort.addEventListener(m_evListener, SerialPort.MASK_RXCHAR);
            logger.info("Scales port {} opened successfully at baudrate {}", strPort, baudRate);
        } catch (SerialPortException ex) {
            logger.error("Scales COM-Communication exception", ex);
            m_serialPort = null;
        }
    }
    
    public synchronized void close() {
        if (m_serialPort == null) return;
        if (!m_serialPort.isOpened()) { m_serialPort = null; return; }
        
        try {
            m_serialPort.closePort();
            logger.info("Scales port closed.");
        } catch (SerialPortException ex) {
            logger.error("Scales COM-Communication exception on close", ex);
        } finally {
            m_serialPort = null;
        }
    }
    
    public synchronized boolean isAnswerReady() {
        int nMarker = m_pCircleBuffer.seekForSequence((byte) 0x0D, (byte) 0x0A);
        return (nMarker != 0);
    }
    
    public synchronized void resetCb() {
        m_pCircleBuffer = new ScalesCircleBuffer();
    }
}