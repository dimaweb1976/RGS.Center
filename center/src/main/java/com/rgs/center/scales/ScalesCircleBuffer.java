package com.rgs.center.scales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScalesCircleBuffer {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    public static final int BUFFER_OK = 0;
    public static final int BUFFER_EXCEEDED = 1;
    
    private int m_nStatus;
    public static final int BUFFER_LEN = 1025;
    
    private final byte m_btBuffer[];
    private volatile int m_nLeftPos;
    private volatile int m_nRightPos;
    private volatile boolean m_bOverRound;
    private volatile boolean m_bFirstRound;
    
    public ScalesCircleBuffer() {
        m_btBuffer = new byte[BUFFER_LEN];
        for (int i = 0; i < BUFFER_LEN; m_btBuffer[i++] = 0);
        
        m_nStatus = BUFFER_OK;
        m_nLeftPos = 0;
        m_nRightPos = 0;
        m_bOverRound = false;
        m_bFirstRound = true;
    }
    
    public void reset() {
        for (int i = 0; i < BUFFER_LEN; m_btBuffer[i++] = 0);
        m_nStatus = BUFFER_OK;
        m_nLeftPos = 0;
        m_nRightPos = 0;
        m_bOverRound = false;
        m_bFirstRound = true;
    }
    
    public int getState() {
        return m_nStatus;
    }
    
    public int addBytes(byte[] bytes, int len) {
        if (m_nStatus != BUFFER_OK) return m_nStatus;
        
        for (int i = 0; i < len; m_btBuffer[(m_nRightPos++) % BUFFER_LEN] = bytes[i++]);
        
        if (m_nRightPos >= BUFFER_LEN) {
            m_nRightPos = m_nRightPos % BUFFER_LEN;
            m_bOverRound = true;
            m_bFirstRound = false;
        }
        
        if (m_bFirstRound) {
            if (m_nRightPos <= m_nLeftPos) {
                logger.error("AddBytes(): На первом кругу правая позиция меньше левой (L,R): " + m_nLeftPos + " " + m_nRightPos);
                m_nStatus = BUFFER_EXCEEDED;
            }
        } else {
            if (!m_bOverRound) {
                if (m_nLeftPos >= m_nRightPos) {
                    logger.error("AddBytes(): Без перехода круга, левая позиция больше правой (L,R): " + m_nLeftPos + " " + m_nRightPos);
                    m_nStatus = BUFFER_EXCEEDED;
                }
            }
        }
        return m_nStatus;
    }
    
    public int getReadyIncomingDataLen() {
        return (BUFFER_LEN + m_nRightPos - m_nLeftPos) % BUFFER_LEN;
    }
    
    public synchronized int getAnswer(int len, byte[] bts) {
        if (m_nStatus != BUFFER_OK) return 1;
        if (len > getReadyIncomingDataLen()) return 2;
        
        for (int i = 0; i < len; i++) {
            bts[i] = m_btBuffer[m_nLeftPos];
            m_nLeftPos++;
            if (m_nLeftPos >= BUFFER_LEN) {
                m_nLeftPos = 0;
                m_bOverRound = false;
            }
        }
        return 0;
    }
    
    public synchronized int seekForSequence(byte b1, byte b2) {
        int nActualLen = getReadyIncomingDataLen();
        for (int i = 0; i < nActualLen - 1; i++) {
            int idx1 = (m_nLeftPos + i) % BUFFER_LEN;
            int idx2 = (m_nLeftPos + i + 1) % BUFFER_LEN;
            if (m_btBuffer[idx1] == b1 && m_btBuffer[idx2] == b2) {
                return i + 2; // возвращаем длину включая последовательность
            }
        }
        return 0;
    }
}