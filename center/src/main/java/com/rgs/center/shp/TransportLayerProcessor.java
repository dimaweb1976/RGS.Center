package com.rgs.center.shp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransportLayerProcessor {

    /**
     * logger variable
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private byte[] abPacked;
    public byte[] getPacked() { return abPacked;}
    
    /**
     * Pack the MB pack (wrap with START/STOP BYTES, SERVER_ADDRESS, CRC-8)
     * @param
     *  abIn input array bytes 
     * @return 
     *  abOut output array bytes
     *  could be null in case of !12b length of incoming pack
     */
    synchronized public int PackThePack( byte [] abIn) {
        if( abIn.length != 12) return 1;
            
        try {
            int nCountStuffedPack = 0;

            //count byte stuffed data pack length
            for( int i = 0; i < abIn.length; i++) {
                nCountStuffedPack++;
                if( abIn[i] == ( byte) 0xC0 || abIn[i] == ( byte) 0x0C || abIn[i] == ( byte) 0xAA)
                    nCountStuffedPack++;
            }

            //NEW TRANSPORT PACK        
            int nPutIndx = 0;
            byte[] abOut = new byte[ 4 + nCountStuffedPack + 1];
            int nFilledLength = 4 + nCountStuffedPack;

            //START BYTE
            abOut[ nPutIndx++] = ( byte) 0xC0;

            //ADDRESS BYTE      (ALWAYS 0x00)
            abOut[ nPutIndx++] = ( byte) 0x00;

            //byte stuffing
            for( int i = 0; i < abIn.length; i++) {
                switch( abIn[i]) {
                    case (( byte) 0xC0): { abOut[ nPutIndx++] = ( byte) 0xAA; abOut[ nPutIndx++] = ( byte) 0x3F; break;}
                    case (( byte) 0x0C): { abOut[ nPutIndx++] = ( byte) 0xAA; abOut[ nPutIndx++] = ( byte) 0xF3; break;}
                    case (( byte) 0xAA): { abOut[ nPutIndx++] = ( byte) 0xAA; abOut[ nPutIndx++] = ( byte) 0x55; break;}
                    default            :   abOut[ nPutIndx++] = ( byte) abIn[i];
                }
            }

            //CRC-8
            CRC8 crc8 = new CRC8( 0x8C, ( byte) 0x00);
            crc8.update( abOut, 1, nCountStuffedPack + 1);
            //System.out.println( String.format( "RES: calc=0x%02X  check=0x%02X", crc8.getValue(), 0x83));
            byte bCrc8 = ( byte) crc8.getValue();
            if( bCrc8 == 0xC0) {        abOut[ nPutIndx++] = ( byte) 0xAA; abOut[ nPutIndx++] = ( byte) 0x3F; nFilledLength++; }
            else if( bCrc8 == 0x0C) {   abOut[ nPutIndx++] = ( byte) 0xAA; abOut[ nPutIndx++] = ( byte) 0xF3; nFilledLength++; }
            else if( bCrc8 == 0xAA) {   abOut[ nPutIndx++] = ( byte) 0xAA; abOut[ nPutIndx++] = ( byte) 0x55; nFilledLength++; }
            else {                      abOut[ nPutIndx++] = bCrc8; }
            
            

            //STOP BYTE
            abOut[ nPutIndx++] = 0x0C;

            //put to output
            abPacked = new byte[ nFilledLength];
            for( int i=0; i<nFilledLength; abPacked[i] = abOut[i++]);
        }
        catch( java.lang.ArrayIndexOutOfBoundsException ex) {
            return 2;
        }
    
        return 0;
    }
    
    private byte[] abUnpacked;
    public byte[] getUnPacked() { return abUnpacked;}

    /**
     * 
     * @param in - bytes pack (including start(0xC0),data (12x xx), CRC(xx), STOP(0x0C)
     * @return 
     * 1    incoming pack does not start with 0xC0
     * 2    incoming pack does not end with 0xC0
     * 3    byte-unstuffing fails (the wrong byte follows after 0xAA)
     * 4    unstuffed pack is not 16 bytes length
     * 5    crc check fail
     */
    synchronized public int UnPackThePack( byte [] abIn) {
        if( abIn == null)                               return 1;
        if( abIn[ 0] != ( byte) 0xC0)                   return 2;
        if( abIn[ abIn.length - 1] != ( byte) 0x0C)     return 3;
        
        byte abTemp[] = null;
        /*try {*/
            abTemp = new byte[ 20];
            int nUnPackedLength = 0;

            //byte unstuffing
            for( int i=0; i<abIn.length; i++) {
                if( abIn[i] == ( byte) 0xAA) {
                    i++;
                         if( abIn[i] == ( byte) 0x3F) abTemp[ nUnPackedLength++] = ( byte) 0xC0;
                    else if( abIn[i] == ( byte) 0xF3) abTemp[ nUnPackedLength++] = ( byte) 0x0C;
                    else if( abIn[i] == ( byte) 0x55) abTemp[ nUnPackedLength++] = ( byte) 0xAA;
                    else
                        return 4;
                }
                else {
                    try {
                        abTemp[ nUnPackedLength++] = abIn[i];
                    }
                    catch( ArrayIndexOutOfBoundsException ex) {                        
                        logger.error( "", ex);
                        if( logger != null && logger.isDebugEnabled())
                            logger.debug( "TPB: " + bytesToHex( abIn));
                        else
                            System.out.println("TPB: " + bytesToHex( abIn));

                        if( logger != null && logger.isDebugEnabled())
                            logger.debug( "TPA: " + bytesToHex( abTemp));
                        else
                           System.out.println( "TPA: " + bytesToHex( abTemp));
                        
                        return 5;
                    }
                }
            }

            /*if( logger != null && logger.isDebugEnabled())
                logger.debug( "TPB: " + bytesToHex( abIn));
            else
                System.out.println("TPB: " + bytesToHex( abIn));

            if( logger != null && logger.isDebugEnabled())
                logger.debug( "TPA: " + bytesToHex( abTemp));
            else
               System.out.println( "TPA: " + bytesToHex( abTemp));*/
        
            if( nUnPackedLength != 20)
                return 6;

            //CHECK ADDRESS??


            //CHECK CRC-8
            abUnpacked = new byte[17];

            //ADDR and DATA PACK
            for( int i = 1; i < nUnPackedLength - 2; abUnpacked[ i - 1] = abTemp[ i++]);
        
        
            CRC8 crc8 = new CRC8( 0x8C, ( byte) 0x00);
            crc8.update( abUnpacked, 0, abUnpacked.length);
            int n1 = ( abTemp[ 18]&0xFF);
            int n2 = (( int) crc8.getValue())&0xFF;
            
            if( n1 != n2) {
                logger.error( String.format( "SHP checksumm comparison fail: calc=0x%02X  check=0x%02X", n2, n1));
                return 7;
            }


            //DATA_PACK_ONLY
            abUnpacked = new byte[17];
            for( int i = 1; i < 17; abUnpacked[ i - 1] = abTemp[ i++]);
        
        /*} catch( ArrayIndexOutOfBoundsException ex) {
            logger.debug( ex);
            
            String strMsg = "abIn={";
            for( byte q : abIn) strMsg += String.format( "%02X ", ( int) ( q &0xFF));
            strMsg += "}";
            logger.debug( strMsg);
            
            strMsg = "abTemp={";
            if( abTemp != null)
                for( byte q : abTemp) strMsg += String.format( "%02X ", ( int) ( q & 0xFF));
            else
                strMsg += "null";
            strMsg += "}";
            logger.debug( strMsg);
            
            strMsg = "abUnpacked={";
            if( abUnpacked != null)
                for( byte q : abUnpacked) strMsg += String.format( "%02X ", ( int) ( q & 0xFF));
            else
                strMsg += "null";
            strMsg += "}";
            logger.debug( strMsg);
        }*/
        
        return 0;
    }
    
    public byte calcCRC( byte[] abtBuff) {
        //CRC-8
        CRC8 crc8 = new CRC8( 0x8C, ( byte) 0x00);
        crc8.update( abtBuff, 0, abtBuff.length);
        //System.out.println( String.format( "RES: calc=0x%02X  check=0x%02X", crc8.getValue(), 0x83));
        return (( byte) ( crc8.getValue() & 0xFF));
    }
    
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = HEX_ARRAY[v >>> 4];
            hexChars[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }
    
    /*public static void main(String[] args) {
        final int   CRC_POLYNOM = 0x8C;
        final byte  CRC_INITIAL = (byte)0x00;
        
        final byte bts[] = { ( byte) 0x00, ( byte) 0x8A, ( byte) 0x08, ( byte) 0x00,
                       ( byte) 0x00, ( byte) 0x40, ( byte) 0x00, ( byte) 0x15,
                       ( byte) 0xD9, ( byte) 0x51, ( byte) 0x52, ( byte) 0x53, ( byte) 0x54};
        
        CRC8 crc8 = new CRC8( CRC_POLYNOM, CRC_INITIAL);
        crc8.update( bts, 0, bts.length);
        System.out.println( String.format( "RES: calc=0x%02X  check=0x%02X", crc8.getValue(), 0x83));
        
        
        
        //TESTS FOR ATRPOSITIONTRANSPORTLAYERPROCSSOR
        TransportLayerProcessor prc = new TransportLayerProcessor();
        
        //SITUATION 1
        final byte abtResp1[] = { ( byte) 0xC0, ( byte) 0x00,
                        ( byte) 0x8B,
                        ( byte) 0x07,
                        ( byte) 0x00,
                        ( byte) 0x00, ( byte) 0x00, ( byte) 0x28, ( byte) 0xAA, ( byte) 0x55,
                        ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00, ( byte) 0x00,
                        ( byte) 0xA1, ( byte) 0x0C};
        
        System.out.println( "Check unstuffing for: " + bytesToHex( abtResp1));
        
        
        int nRes = prc.UnPackThePack( abtResp1);
        System.out.println( String.format( "RES=0x%02X", nRes));
        byte [] btProcessed = prc.getUnPacked();
        System.out.println( String.format( "   LEN=0x%02X", btProcessed.length));
        System.out.println( "   " + bytesToHex( btProcessed));
        System.out.println( "");
        
        
        //C0 00 80 BF 34 43 82 BC 8A 13 AA F3 00 00 00 2F 0C
        final byte abtResp2[] = { ( byte) 0xC0, ( byte) 0x00,
                        ( byte) 0x80,
                        ( byte) 0xBF, ( byte) 0x34, ( byte) 0x00, ( byte) 0x00,
                        ( byte) 0x28, ( byte) 0xAA, ( byte) 0x55, ( byte) 0x00, ( byte) 0x00,
                        ( byte) 0x00,
                        ( byte) 0x00,
                        ( byte) 0x00,
                        ( byte) 0xA1, ( byte) 0x0C};
        
        //SITUATION 2
        System.out.println( "Check unstuffing for: " + bytesToHex( abtResp2));
        
        nRes = prc.UnPackThePack( abtResp2);
        System.out.println( String.format( "RES=0x%02X", nRes));
        btProcessed = prc.getUnPacked();
        System.out.println( String.format( "   LEN=0x%02X", btProcessed.length));
        System.out.println( "   " + bytesToHex( btProcessed));
        
    }*/
}
