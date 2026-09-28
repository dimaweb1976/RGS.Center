package com.rgs.center.shp;

public class ShpRespondedUnit {
    private final long lSentTime;
    public long getSentTime() { return lSentTime; }
    
    private final byte [] abtSent;
    public byte[] getSent() { return abtSent; }
    
    private final long lRespondTime;
    public long getRespondTime() { return lRespondTime; }
    
    private final byte [] abtRespond;
    public byte[] getRespond() { return abtRespond; }
    
    public ShpRespondedUnit( ShpSentUnit unit, byte [] abtRespond) {
        lSentTime = unit.getSentTime();
        abtSent = unit.getSent();
        lRespondTime = System.currentTimeMillis();
        this.abtRespond = abtRespond;
    }
}
