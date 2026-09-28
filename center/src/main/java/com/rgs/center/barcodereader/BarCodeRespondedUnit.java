package com.rgs.center.barcodereader;

public class BarCodeRespondedUnit {

    private final byte[] abtSent;
    private final long sentTimestamp;
    
    private final byte[] abtResponse;
    private final long receivedTimestamp;

    
    public byte[] getAbtSent() { return abtSent;}
    public long getSentTimestamp() { return sentTimestamp; }
    
    public byte[] getAbtResponse() { return abtResponse; }
    public long getReceivedTimestamp() { return receivedTimestamp; }

    
    public BarCodeRespondedUnit( BarcodeSentUnit unit, byte[] abtResponse) {
        this.abtSent = unit.getCommand();
        this.sentTimestamp = unit.getSentTimestamp();

        this.abtResponse = abtResponse;
        this.receivedTimestamp = System.currentTimeMillis();
    }
}