package com.rgs.center.nicole;

import com.rgs.center.settings.RGSCenterSettings;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServiceTcpNicoleReader {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterSettings settings;
    
    private Socket m_socket;
    private TcpStreamReaderNicoleLine m_streamReader;
    private Thread m_readerThread;
    
    private NicoleCircleBuffer m_pCircleBuffer;
    public String GetCbState() { return m_pCircleBuffer.toString(); }
    public NicoleCircleBuffer GetCb() { return m_pCircleBuffer; }
    public Socket getSocket() { return m_socket; }
    
    public ServiceTcpNicoleReader() {
        m_pCircleBuffer = new NicoleCircleBuffer();
    }

    public synchronized void connect(String host, int port) {
        m_pCircleBuffer = new NicoleCircleBuffer();
        if (m_socket != null && m_socket.isConnected() && !m_socket.isClosed()) return;
        
        try {
            m_socket = new Socket(host, port);
            m_socket.setSoTimeout(1000);
            m_streamReader = new TcpStreamReaderNicoleLine(m_socket, m_pCircleBuffer);
            m_readerThread = new Thread(m_streamReader, "tcp-nicole-reader-thread");
            m_readerThread.start();
        } catch (Exception ex) {
            logger.error("Nicole TCP Connection exception", ex);
            m_socket = null;
        }
    }
    
    public synchronized void close() {
        if (m_streamReader != null) {
            m_streamReader.stop();
        }
        if (m_readerThread != null) {
            m_readerThread.interrupt();
        }
        try {
            if (m_socket != null && !m_socket.isClosed()) {
                m_socket.close();
            }
        } catch (Exception ex) {
            logger.error("Nicole TCP Close exception", ex);
        } finally {
            m_socket = null;
            m_streamReader = null;
            m_readerThread = null;
        }
    }
    
    public synchronized boolean isAnswerReady() {
        int nStartMarker = m_pCircleBuffer.SeekForFirstByteOccurence((byte) 0xC0);
        int nEndMarker = m_pCircleBuffer.SeekForFirstByteOccurence((byte) 0xC1);
        return (nStartMarker != 0 && nEndMarker != 0);
    }
    
    public synchronized void resetCb() {
        m_pCircleBuffer = new NicoleCircleBuffer();
    }
}