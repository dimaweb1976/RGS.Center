/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.rgs.center.shp;

import java.util.zip.Checksum;

/**
 *
 * @author designer5
 */
public class CRC8 implements Checksum
{
    
    
    private final short init;
    private final short[]   crcTable = new short[256];
    private short   value;

    /**
     * Construct a CRC8 specifying the polynomial and initial value.
     * @param polynomial Polynomial, typically one of the POLYNOMIAL_* constants.
     * @param init Initial value, typically either 0xff or zero.
     */
    public CRC8(int polynomial, short init)
    {
        this.value = this.init = init;
        for (int dividend = 0; dividend < 256; dividend++)
        {
            int remainder = dividend ;//<< 8;
            for (int bit = 0; bit < 8; ++bit)
                if ((remainder & 0x01) != 0)
                    remainder = (remainder >>> 1) ^ polynomial;
                else
                    remainder >>>= 1;
            crcTable[dividend] = (short)remainder;
        }
    }

    @Override
    public void update(byte[] buffer, int offset, int len)
    {
        for (int i = 0; i < len; i++)
        {
            int data = buffer[offset+i] ^ value;
            value = (short)(crcTable[data & 0xff] ^ (value << 8));
        }
    }

    /**
     * Updates the current checksum with the specified array of bytes.
     * Equivalent to calling <code>update(buffer, 0, buffer.length)</code>.
     * @param buffer the byte array to update the checksum with
     */
    public void update(byte[] buffer)
    {
        update(buffer, 0, buffer.length);
    }

    @Override
    public void update(int b)
    {
        update(new byte[]{(byte)b}, 0, 1);
    }

    @Override
    public long getValue()
    {
        return value & 0xff;
    }

    @Override
    public void reset()
    {
        value = init;
    }

    /*
    public static void  main(String[] args)
    {
        int nPos = 0x3FFFFFFF;
        System.out.println( String.format( "%X %d", nPos, nPos));
        
        nPos += 10;
        nPos = (nPos & 0xFFFF);
        System.out.println( String.format( "%X %d", nPos, nPos));
    }*/    
    
    public static void  main(String[] args)
    {
        final int   CRC_POLYNOM = 0x8C;
        final byte  CRC_INITIAL = (byte)0x00;
        
        //SHP запрос тока
        //71 05 00 00 00 00 00
        final byte[] data1 = {(0x71&0xFF), (0x05&0xFF), (0x00&0xFF),(0x00&0xFF),(0x00&0xFF),(0x00&0xFF),(0x00&0xFF)};
        CRC8 crc8 = new CRC8( CRC_POLYNOM, CRC_INITIAL);
        crc8.update( data1, 0, data1.length);
        System.out.println( String.format( "SHP.I RES: calc=0x%02X  check=0x%02X", crc8.getValue(), 0x05));
        
        //SHP запрос температуры
        //71 03 00 00 00 00 00
        final byte[] data2 = {(0x71&0xFF), (0x03&0xFF), (0x00&0xFF),(0x00&0xFF),(0x00&0xFF),(0x00&0xFF),(0x00&0xFF)};
        crc8 = new CRC8( CRC_POLYNOM, CRC_INITIAL);
        crc8.update( data2, 0, data2.length);
        System.out.println( String.format( "SHP.T RES: calc=0x%02X  check=0x%02X", crc8.getValue(), 0xB7));
        
        //SHP запрос Uoutput_charge_circuit
        //71 04 00 00 00 00 00
        final byte[] data21 = {(0x71&0xFF), (0x04&0xFF), (0x00&0xFF),(0x00&0xFF),(0x00&0xFF),(0x00&0xFF),(0x00&0xFF)};
        crc8 = new CRC8( CRC_POLYNOM, CRC_INITIAL);
        crc8.update( data21, 0, data21.length);
        System.out.println( String.format( "SHP.U RES: calc=0x%02X  check=0x%02X", crc8.getValue(), 0x32));
                            
        //SHP ответ на запрос тока
        //91 05 40 4D E3 7A 33 2E 32 31 37 00 00 00 00 00 00         B6
        final byte[] data3 = {( byte) (0x91&0xFF), ( byte) (0x05&0xFF), ( byte) (0x40&0xFF), ( byte) (0x4D&0xFF),
                              ( byte) (0xE3&0xFF), ( byte) (0x7A&0xFF), ( byte) (0x33&0xFF), ( byte) (0x2E&0xFF),
                              ( byte) (0x32&0xFF), ( byte) (0x31&0xFF), ( byte) (0x37&0xFF), ( byte) (0x00&0xFF),
                              ( byte) (0x00&0xFF), ( byte) (0x00&0xFF), ( byte) (0x00&0xFF), ( byte) (0x00&0xFF),
                              ( byte) (0x00&0xFF)};
        crc8 = new CRC8( CRC_POLYNOM, CRC_INITIAL);
        crc8.update( data3, 0, data3.length);
        System.out.println( String.format( "RES: calc=0x%02X  check=0x%02X", crc8.getValue(), 0xB6));
        
        
        //SHP команда запроса сопротивления
        //75 07 00 00 00 00 00
        final byte[] data4 = {( byte) 0x75, ( byte) 0x07, 0x00, 0x00, 0x00, 0x00, 0x00 };
        crc8 = new CRC8( CRC_POLYNOM, CRC_INITIAL);
        crc8.update( data4, 0, data4.length);
        System.out.println( String.format( "SHP.R RES: calc=0x%02X  check=0x%02X", crc8.getValue(), 0x9F));
    }
    
    /*public static void  main(String[] args)
    {
        

	final int   CRC_POLYNOM = 0x8C;
        final byte  CRC_INITIAL = (byte)0x00;

        final byte[]    data = {1, 56, -23, 3, 0, 19, 0, 0, 2, 0, 3, 13, 8, -34, 7, 9, 42, 18, 26, -5, 54, 11, -94, -46, -128, 4, 48, 52, 0, 0, 0, 0, 0, 0, 0, 0, 4, 1, 1, -32, -80, 0, 98, -5, 71, 0, 64, 0, 0, 0, 0, -116, 1, 104, 2};
        
        //this example is from Internet- here are different POLY and INI
        CRC8    crc8 = new CRC8( 0x9C, ( byte) 0xFF);
        crc8.update( data, 0, data.length);
        System.out.println("Test successful:\t"+(crc8.getValue() == 29));
        



        //0x01 0x02 0x03 0x04 0x05 0x06 0x07 0x08           0x83
        final byte[] data1 = {(0x01&0xFF), (0x02&0xFF), (0x03&0xFF),(0x04&0xFF),(0x05&0xFF),(0x06&0xFF),(0x07&0xFF),(0x08&0xFF)};
        crc8 = new CRC8( CRC_POLYNOM, CRC_INITIAL);
        crc8.update( data1, 0, data1.length);
        System.out.println( String.format( "RES: calc=0x%02X  check=0x%02X", crc8.getValue(), 0x83));

        //0x21 0x22 0x23 0x24 0x25 0x26 0x27 0x28           0x8C        
        final byte[] data2 = {(byte)0x21,(byte)0x22,(byte)0x23,(byte)0x24,(byte)0x25,(byte)0x26,(byte)0x27,(byte)0x28};
        crc8 = new CRC8( CRC_POLYNOM, CRC_INITIAL);
        crc8.update( data2, 0, data2.length);
        System.out.println( String.format( "RES: calc=0x%02X  check=0X%02X", crc8.getValue(), 0x8C));
        

        
        
        //0x31 0x32 0x33 0x34 0x35 0x36 0x37 0x38           0x07
        final byte[] data3 = {(byte)0x31,(byte)0x32,(byte)0x33,(byte)0x34,(byte)0x35,(byte)0x36,(byte)0x37,(byte)0x38};
        crc8 = new CRC8( CRC_POLYNOM, CRC_INITIAL);
        crc8.update( data3, 0, data3.length);
        System.out.println( String.format( "RES: calc=0x%02X  check=0X%02X", crc8.getValue(), 0x07));
        
        //this pack caused crc-check fail from Dev
        //C0 00 1A 03 00 00 00 AA 55 AA 55 00 00 00 00 00 92 0C
        final byte[] data4 = {(byte)0x00,(byte)0x1A,(byte)0x03,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xAA,(byte)0x55,(byte)0xAA,(byte)0x55,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00};
        crc8 = new CRC8( CRC_POLYNOM, CRC_INITIAL);
        crc8.update( data4, 0, data4.length);
        System.out.println( String.format( "RES: calc=0x%02X  check=0X%02X", crc8.getValue(), 0x92));
    }*/
}
