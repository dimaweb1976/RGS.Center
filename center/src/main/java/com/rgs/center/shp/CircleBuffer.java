package com.rgs.center.shp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircleBuffer {
    
    /**
     * logger variable
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    /**
     * Return code for getState(), describing that buffer is OK
     */
    public static final int BUFFER_OK = 0;
    /**
     * Return code for getState(), describing that buffer is exceeded (too much input, not enough output)
     */
    public static final int BUFFER_EXCEEDED = 1;
    
    /**
     * internal variable that holds the buffer state (see constants here a little bit above)
     */        
    private int m_nStatus;
    
    /**
     * A constant that holds maximum buffer size
     */
    public static final int BUFFER_LEN = 1025;
    
    /**
     * A constant that holds minimal response pack length
     */
    private final int RESP_LEN_MIN = 16;
    
    /**
     * A constant that holds maximum response pack length
     * (i.e. there should be a warning if you wouldn't find start-stop markers in the range[0;RESP_LEN_MAX] of captured bytes)
     */
    private final int RESP_LEN_MAX = 500;

    /**
     * A buffer itself.
     * A byte array that holds captured bytes.
     */
    private final byte m_btBuffer[];
    
    /**
     * A left circle buffer index
     */
    private volatile int m_nLeftPos;
    
    /**
     * A right circle buffer index
     */
    private volatile int m_nRightPos;
    
    /**
     * A lock flag that L is overround, and R is also overround
     */
    private volatile boolean m_bOverRound;
    
    /**
     * A lock flag that we are running first round over the circle buffer
     * the state should be: (overround=false; L > R)
     */
    private volatile boolean m_bFirstRound;
            
    
    public CircleBuffer() {
        m_btBuffer = new byte[ BUFFER_LEN];
        for( int i = 0; i < BUFFER_LEN; m_btBuffer[ i++] = 0);
        
        m_nStatus = BUFFER_OK;
        
        m_nLeftPos = 0;
        m_nRightPos = 0;
        
        m_bOverRound = false;
        m_bFirstRound = true;
    }
    
    public void reset() {
        for( int i = 0; i < BUFFER_LEN; m_btBuffer[ i++] = 0);
        
        m_nStatus = BUFFER_OK;
        
        m_nLeftPos = 0;
        m_nRightPos = 0;
        
        m_bOverRound = false;
        m_bFirstRound = true;
    }
    
    public String toString() {
        String strOut = "ST: " + m_nStatus + "  ";
        strOut +=  "L: " + String.format("%04d", m_nLeftPos) + "  ";
        strOut += "R: " + String.format("%04d", m_nRightPos) + "  ";
        strOut += "FR: " + ( m_bFirstRound?"T":"F") + "  ";
        strOut += "OR: " + ( m_bOverRound?"T":"F");

        return strOut;
    }
    /**
     * Print circle buffer state: left_marker, right_marker, bytes
     * 
     */
    public void printState() {
        String strOut =  "PrintState(): L: " + String.format("%04d", m_nLeftPos) + "  ";
               strOut += "R: " + String.format("%04d", m_nRightPos) + "  ";
               strOut += "FR: " + ( m_bFirstRound?"T":"F") + "  ";
               strOut += "OR: " + ( m_bOverRound?"T":"F");

        logger.debug( strOut);
        
        int l = ( BUFFER_LEN + m_nLeftPos - 5) % BUFFER_LEN;
        int r = ( BUFFER_LEN + m_nRightPos + 5) % BUFFER_LEN;
        
        strOut = "[";
        
        int indx = l;
        do {
            strOut += "" + m_btBuffer[ indx] + ", ";
            indx = ( indx++) % BUFFER_LEN;
            logger.debug( "" + indx);
        } while( indx == r);
        
        strOut += "]";
        logger.debug( strOut);
    }
    
    /**
     * Get circle buffer validation status
     * 
     * @return 
     * 0 = BUFFER_OK
     * 1 = BUFFER_EXCEEDED
     * rest - look in CircleBuffer constants at the top
     * 
     */
    public int getState() { return m_nStatus; }
    
    /**
     * Add bytes to buffer
     * @param bytes
     * bytes to add
     * @return 
     * returns validation status of the object (here we can overround circle buffer)
     */
    public int addBytes( byte[] bytes, int len) {
        if( m_nStatus != BUFFER_OK) return m_nStatus;
        
        //copy bytes
        for( int i=0; i < len; m_btBuffer[ ( m_nRightPos++) % BUFFER_LEN] = bytes[i++]);
        
        //check overround
        if( m_nRightPos >= BUFFER_LEN) {
            m_nRightPos = m_nRightPos % BUFFER_LEN;
            
            m_bOverRound = true;
            m_bFirstRound = false;
        }
        
        //check exceeds
        if( m_bFirstRound) {
            if( m_nRightPos <= m_nLeftPos) {
                logger.error( "AddBytes(): На первом кругу правая позиция меньше левой (L,R): " + m_nLeftPos + " " + m_nRightPos);
                m_nStatus = BUFFER_EXCEEDED;
            }
        }
        else {
            if( m_bOverRound) {
                /*if( m_nRightPos >= m_nLeftPos) {
                    logger.fatal( "AddBytes(): С переходом круга, правая позиция больше левой (L,R): " + m_nLeftPos + " " + m_nRightPos);
                    logger.fatal( toString());
                    m_nStatus = BUFFER_EXCEEDED;
                }*/
            }
            else {
                if( m_nLeftPos >= m_nRightPos) {
                    logger.error( "AddBytes(): Без перехода круга, левая позиция больше правой (L,R): " + m_nLeftPos + " " + m_nRightPos);
                    m_nStatus = BUFFER_EXCEEDED;
                }
            }        
        }
        
        //logger.trace( "AddBytes(): result: l:" + m_nLeftPos + "  r:" + m_nRightPos + "  FR:" + m_bFirstRound + "  OR:" + m_bOverRound);
        return m_nStatus;
    }
    
    /**
     * Checks if (CR) is present within MAX_RESP_LEN
     * @return 
     * 0 - problems, or nothing
     * rest - length of the answer
     */
    /*public synchronized int isAnswerReady() {
        if( m_nStatus != BUFFER_OK) return 0;
        
        int len = min( MAX_RESP_LEN, ( BUFFER_LEN + m_nRightPos - m_nLeftPos) % BUFFER_LEN);
        int i;
        for( i = 0; i < len; i++) {
            if( m_btBuffer[ ( i + m_nLeftPos) % BUFFER_LEN] == 0x0A) {
                return (i + 1);
            }
        }
        
        if( len == MAX_RESP_LEN)
            m_nStatus = BUFFER_NO_CR;
        
        return 0;
    }*/
    
    /**
     * Gets ready data length
     * @return 
     * returns ready data len
     */
    public int getReadyIncomingDataLen() {
        int len = ( BUFFER_LEN + m_nRightPos - m_nLeftPos) % BUFFER_LEN;
        //logger.trace( "getReadyIncomingDataLen(): L=" + m_nLeftPos + " R=" + m_nRightPos + " len=" + len);
        return len;
    }
    
    /**
     * Gets len length answer from buffer
     * @param len requested data length (in bytes)
     * @param bts user buffer for receiving bytes
     * @return
     * 0 - ok<br>
     * 1 - CircleBuffer instance is not in valid state<br>
     * 2 - user asks for more data bytes than we have in our buffer
     */
    public synchronized int getAnswer( int len, byte [] bts) {
        if( m_nStatus != BUFFER_OK) return 1;
        
        //check if user asks for more data than we have
        if( len > getReadyIncomingDataLen())
            return 2; 
        
        for( int i=0; i<len; i++) {
            bts[i] = m_btBuffer[ m_nLeftPos];
            m_nLeftPos++;
            if( m_nLeftPos >= BUFFER_LEN) {
                m_nLeftPos = 0;
                m_bOverRound = false;
            }
        }
        
        return 0;
    }
    
    public synchronized int SeekForFirstByteOccurence( byte btSeek) {
        int nActualLen = getReadyIncomingDataLen();
        for( int i=0; i<nActualLen; i++) {
            //logger.debug( "1)" + String.format( "%02X", m_btBuffer[ ( m_nLeftPos + i) % BUFFER_LEN]) + "  2)" + String.format( "%02X", btSeek));
            if( m_btBuffer[ ( m_nLeftPos + i) % BUFFER_LEN] == btSeek) {
                //System.err.print(i);
                return (i+1);
            }
        }
        return 0;
    }
}
