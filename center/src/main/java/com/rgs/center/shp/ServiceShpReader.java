package com.rgs.center.shp;

import com.rgs.center.settings.RGSCenterSettings;
import org.springframework.stereotype.Service;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ServiceShpReader {

    /**
     * logger variable
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterSettings settings;
    
    //serial port itself
    private SerialPort m_serialPort;
    public SerialPort GetSerialPort() {
        return m_serialPort;
    }
    
    PortReader m_evListener;
    
    //buffer to receive incoming bytes (one-by-one from Listener)
    private CircleBuffer m_pCircleBuffer;
    public String GetCbState() { return m_pCircleBuffer.toString();}
    public CircleBuffer GetCb() { return m_pCircleBuffer;}
    
    public ServiceShpReader() {
        m_pCircleBuffer = new CircleBuffer();
        
        String strPort = "COM11";
        m_serialPort = new SerialPort( strPort);
        try {
            //Открываем порт
            m_serialPort.openPort();

            //Выставляем параметры
            m_serialPort.setParams( 9600,
                                 SerialPort.DATABITS_8,
                                 SerialPort.STOPBITS_1,
                                 SerialPort.PARITY_NONE);

            //Включаем аппаратное управление потоком
            //serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | 
            //                              SerialPort.FLOWCONTROL_RTSCTS_OUT);


            //RECEIVER
            //Устанавливаем ивент лисенер и маску
            m_evListener = new PortReader( m_serialPort, m_pCircleBuffer);
            m_serialPort.addEventListener( m_evListener, SerialPort.MASK_RXCHAR);
        }
        catch( SerialPortException ex) {
            logger.error( "COM-Communication exception", ex);
            m_serialPort = null;
        }
    }

    public synchronized void open( String strPort) {
        logger.info("Attempting to open port: {}", strPort);
        
        m_pCircleBuffer = new CircleBuffer();
        if( m_serialPort != null && m_serialPort.isOpened()) return;
        
        m_serialPort = new SerialPort( strPort);
        try {
            //Открываем порт
            m_serialPort.openPort();

            //Выставляем параметры
            m_serialPort.setParams( 9600,
                                 SerialPort.DATABITS_8,
                                 SerialPort.STOPBITS_1,
                                 SerialPort.PARITY_NONE);

            //Включаем аппаратное управление потоком
            //serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | 
            //                              SerialPort.FLOWCONTROL_RTSCTS_OUT);


            //RECEIVER
            //Устанавливаем ивент лисенер и маску
            m_evListener = new PortReader( m_serialPort, m_pCircleBuffer);
            m_serialPort.addEventListener( m_evListener, SerialPort.MASK_RXCHAR);
        }
        catch( SerialPortException ex) {
            logger.error( "COM-Communication exception", ex);
            m_serialPort = null;
        }
    }
    
    public synchronized void close() {
        if( m_serialPort == null) return;
        if( !m_serialPort.isOpened()) { m_serialPort = null; return; }
        
        try {
            //Открываем порт
            m_serialPort.closePort();
        }
        catch( SerialPortException ex) {
            logger.error( "COM-Communication exception", ex);
        }
        finally {
            m_serialPort = null;
        }
    }
    
    public synchronized boolean isAnswerReady() {
        int nStartMarker = m_pCircleBuffer.SeekForFirstByteOccurence( ( byte) 0xC0);
        int nEndMarker = m_pCircleBuffer.SeekForFirstByteOccurence( ( byte) 0x0C);
        
        return( nStartMarker != 0 && nEndMarker != 0);
    }
    
    public synchronized void resetCb() {
        m_pCircleBuffer = new CircleBuffer();
    }
}
