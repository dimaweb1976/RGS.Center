package com.rgs.center.shp;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortReader implements SerialPortEventListener {
    /**
     * logger variable
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
        
    private final SerialPort m_serialPort;
    private final CircleBuffer m_bf;
    
    public PortReader( SerialPort port, CircleBuffer bf) {
        m_serialPort = port;
        m_bf = bf;
    }
    @Override
    public void serialEvent(SerialPortEvent event) {            
        

        if( event.isRXCHAR() && event.getEventValue() > 0){
            
            //theApp.GetConnection().RegisterReceiveInteraction();
            
            try {
                //Получаем ответ от устройства, обрабатываем данные и т.д.
                int nReadyBytes = event.getEventValue();
                byte bts[] = new byte[ nReadyBytes];
                bts = m_serialPort.readBytes( nReadyBytes);
                    
                String strLogMessage;
                strLogMessage = String.format( "READ %d BYTE. FIRST ONE=0x%02X", nReadyBytes, bts[0]);
                logger.debug( strLogMessage);
                

                if( m_bf != null)
                    m_bf.addBytes( bts, nReadyBytes);
                else
                    logger.error( "Loosen bytes!");
            }
            catch (SerialPortException ex) {
                logger.error( "SerialPortException caught", ex);
            }
        }
    }
}
