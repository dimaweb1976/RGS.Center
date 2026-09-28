package com.rgs.center.dobot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpBufferDobotStreamLine {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    public static final int BUFFER_OK = 0;
    public static final int BUFFER_EXCEEDED = 1;
    
    private int m_nStatus;
    // Увеличиваем размер буфера с запасом, чтобы он гарантированно вмещал пакет в 1440 байт
    public static final int BUFFER_LEN = 4096;
    
    private final byte m_btBuffer[];
    private volatile int m_nLeftPos;
    private volatile int m_nRightPos;
    private volatile boolean m_bOverRound;
    private volatile boolean m_bFirstRound;
            
    public TcpBufferDobotStreamLine() {
        m_btBuffer = new byte[BUFFER_LEN];
        for(int i = 0; i < BUFFER_LEN; m_btBuffer[i++] = 0);
        m_nStatus = BUFFER_OK;
        m_nLeftPos = 0;
        m_nRightPos = 0;
        m_bOverRound = false;
        m_bFirstRound = true;
    }
    
    public void reset() {
        for(int i = 0; i < BUFFER_LEN; m_btBuffer[i++] = 0);
        m_nStatus = BUFFER_OK;
        m_nLeftPos = 0;
        m_nRightPos = 0;
        m_bOverRound = false;
        m_bFirstRound = true;
    }
    
    public String toString() {
        String strOut = "ST: " + m_nStatus + "  ";
        strOut += "L: " + String.format("%04d", m_nLeftPos) + "  ";
        strOut += "R: " + String.format("%04d", m_nRightPos) + "  ";
        strOut += "FR: " + (m_bFirstRound ? "T" : "F") + "  ";
        strOut += "OR: " + (m_bOverRound ? "T" : "F");
        return strOut;
    }
    
    public int getState() { return m_nStatus; }
    
    public int addBytes(byte[] bytes, int len) {
        if(m_nStatus != BUFFER_OK) return m_nStatus;
        
        for(int i = 0; i < len; m_btBuffer[(m_nRightPos++) % BUFFER_LEN] = bytes[i++]);
        
        if(m_nRightPos >= BUFFER_LEN) {
            m_nRightPos = m_nRightPos % BUFFER_LEN;
            m_bOverRound = true;
            m_bFirstRound = false;
        }
        
        if(m_bFirstRound) {
            if(m_nRightPos <= m_nLeftPos) {
                logger.error("AddBytes(): На первом кругу правая позиция меньше левой (L,R): " + m_nLeftPos + " " + m_nRightPos);
                m_nStatus = BUFFER_EXCEEDED;
            }
        } else {
            if(!m_bOverRound) {
                if(m_nLeftPos >= m_nRightPos) {
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
        if(m_nStatus != BUFFER_OK) return 1;
        if(len > getReadyIncomingDataLen()) return 2;
        
        for(int i = 0; i < len; i++) {
            bts[i] = m_btBuffer[m_nLeftPos];
            m_nLeftPos++;
            if(m_nLeftPos >= BUFFER_LEN) {
                m_nLeftPos = 0;
                m_bOverRound = false;
            }
        }
        return 0;
    }
    
    /**
     * Проверяет наличие целого пакета в буфере.
     * Ищет маркер длины 1440 байт (0xA0 0x05 в little-endian) в начале пакета
     * и проверяет, что в буфере доступно как минимум 1440 байт начиная с этой позиции.
     * 
     * @return 
     * 0 - пакет еще не готов (или не найден маркер)
     * 1440 - размер готового пакета
     */
    public synchronized int isAnswerReady() {
        int nActualLen = getReadyIncomingDataLen();
        int targetLen = 1440;
        
        // Если в буфере меньше 1440 байт, пакета точно нет
        if (nActualLen < targetLen) {
            return 0;
        }
        
        // Ищем в доступном диапазоне буфера байты 0xA0 0x05 (1440 в little-endian)
        for (int i = 0; i <= nActualLen - targetLen; i++) {
            int pos1 = (m_nLeftPos + i) % BUFFER_LEN;
            int pos2 = (m_nLeftPos + i + 1) % BUFFER_LEN;
            
            byte b1 = m_btBuffer[pos1];
            byte b2 = m_btBuffer[pos2];
            
            // Проверяем совпадение MessageSize = 1440 (0x05A0 -> little endian: 0xA0, 0x05)
            if ((b1 & 0xFF) == 0xA0 && (b2 & 0xFF) == 0x05) {
                // Если перед найденным маркером были "мусорные" байты, сдвигаем левый указатель, чтобы их отбросить
                if (i > 0) {
                    m_nLeftPos = pos1;
                }
                // Возвращаем фиксированный размер пакета для извлечения
                return targetLen;
            }
        }
        
        return 0;
    }
}