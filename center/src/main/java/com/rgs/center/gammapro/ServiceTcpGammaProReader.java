package com.rgs.center.gammapro;

import com.rgs.center.settings.RGSCenterSettings;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServiceTcpGammaProReader {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterSettings settings;
    
    private Socket m_socket;
    private TcpStreamReaderGammaProLine m_streamReader;
    private Thread m_readerThread;
    
    private TcpBufferGammaProLine m_pCircleBuffer;
    public String GetCbState() { return m_pCircleBuffer.toString(); }
    public TcpBufferGammaProLine GetCb() { return m_pCircleBuffer; }
    public Socket getSocket() { return m_socket; }
    
    public ServiceTcpGammaProReader() {
        m_pCircleBuffer = new TcpBufferGammaProLine();
    }

    public synchronized void connect(String host, int port) {
        m_pCircleBuffer = new TcpBufferGammaProLine();
        if (m_socket != null && m_socket.isConnected() && !m_socket.isClosed()) return;
        
        try {
            m_socket = new Socket(host, port);
            m_socket.setSoTimeout( 1000);
            m_streamReader = new TcpStreamReaderGammaProLine(m_socket, m_pCircleBuffer);
            m_readerThread = new Thread(m_streamReader, "tcp-reader-thread");
            m_readerThread.start();
        } catch (Exception ex) {
            logger.error("TCP Connection exception", ex);
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
            logger.error("TCP Close exception", ex);
        } finally {
            m_socket = null;
            m_streamReader = null;
            m_readerThread = null;
        }
    }
    
    public synchronized boolean isAnswerReady() {
        return m_pCircleBuffer.isAnswerReady() > 0;
    }
    
    public synchronized void resetCb() {
        m_pCircleBuffer = new TcpBufferGammaProLine();
    }
}