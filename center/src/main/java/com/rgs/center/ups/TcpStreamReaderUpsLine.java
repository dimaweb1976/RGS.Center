package com.rgs.center.ups;

import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpStreamReaderUpsLine implements Runnable {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Socket m_socket;
    private final UpsCircleBuffer m_bf;
    private volatile boolean m_running = true;
    
    public TcpStreamReaderUpsLine(Socket socket, UpsCircleBuffer bf) {
        m_socket = socket;
        m_bf = bf;
    }
    
    @Override
    public void run() {
        try {
            InputStream inputStream = m_socket.getInputStream();
            byte[] buffer = new byte[1024];
            while (m_running && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1) {
                        break;
                    }
                    if (bytesRead > 0) {
                        byte[] bts = new byte[bytesRead];
                        System.arraycopy(buffer, 0, bts, 0, bytesRead);

                        if (m_bf != null) {
                            m_bf.addBytes(bts, bytesRead);
                        } else {
                            logger.error("UPS Loosen bytes!");
                        }
                    }
                }
                catch(SocketTimeoutException ex2) {
                    // Штатный таймаут сокета
                }
            }
        } catch (Exception ex) {
            logger.error("UPS reader exception", ex);
        }
    }
    
    public void stop() {
        m_running = false;
    }
}