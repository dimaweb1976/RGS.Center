package com.rgs.center.barcodereader;

import org.springframework.stereotype.Service;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BarcodeReader {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private SerialPort m_serialPort;
    public SerialPort GetSerialPort() {
        return m_serialPort;
    }
    
    BarcodePortReader m_evListener;
    private BarcodeCircleBuffer m_pCircleBuffer;
    
    public BarcodeReader() {
        m_pCircleBuffer = new BarcodeCircleBuffer();
    }

    public synchronized void open(String strPort, int baudRate) {
        m_pCircleBuffer = new BarcodeCircleBuffer();
        if (m_serialPort != null && m_serialPort.isOpened()) return;
        
        m_serialPort = new SerialPort(strPort);
        try {
            m_serialPort.openPort();
            m_serialPort.setParams(baudRate,
                                 SerialPort.DATABITS_8,
                                 SerialPort.STOPBITS_1,
                                 SerialPort.PARITY_NONE);

            m_evListener = new BarcodePortReader(m_serialPort, m_pCircleBuffer);
            m_serialPort.addEventListener(m_evListener, SerialPort.MASK_RXCHAR);
            
            logger.info("Barcode port {} opened successfully at baudrate {}", strPort, baudRate);
        } catch (Exception ex) {
            logger.error("Barcode COM-Communication initialization exception", ex);
            m_serialPort = null;
        }
    }
    
    public synchronized void close() {
        if (m_serialPort == null) return;
        if (!m_serialPort.isOpened()) { m_serialPort = null; return; }
        
        try {
            m_serialPort.closePort();
            logger.info("Barcode port closed.");
        } catch (SerialPortException ex) {
            logger.error("Barcode COM-Communication exception on close", ex);
        } finally {
            m_serialPort = null;
        }
    }
    
    public synchronized BarcodeCircleBuffer GetCb() { 
        return m_pCircleBuffer; 
    }
    
    public synchronized void resetCb() {
        m_pCircleBuffer.reset();
    }
}