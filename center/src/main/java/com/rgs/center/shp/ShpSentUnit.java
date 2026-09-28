package com.rgs.center.shp;

public class ShpSentUnit {
    private final long lSentTime;
    public long getSentTime() { return lSentTime; }
    
    private final byte [] abtSent;
    public byte[] getSent() { return abtSent; }
    
    public ShpSentUnit( byte [] abtSent) {
        lSentTime = System.currentTimeMillis();
        this.abtSent = abtSent;
    }
}
