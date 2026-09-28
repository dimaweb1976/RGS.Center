package com.rgs.center.gammapro;

public class GammaProTcpRespondedUnit {
    private final long lSentTime;
    public long getSentTime() { return lSentTime; }
    
    private final byte [] abtSent;
    public byte[] getSent() { return abtSent; }
    
    private final long lRespondTime;
    public long getRespondTime() { return lRespondTime; }
    
    private final byte [] abtRespond;
    public byte[] getRespond() { return abtRespond; }
    
    public GammaProTcpRespondedUnit( GammaProTcpSentUnit unit, byte [] abtRespond) {
        lSentTime = unit.getSentTime();
        abtSent = unit.getSent();
        lRespondTime = System.currentTimeMillis();
        this.abtRespond = abtRespond;
    }
}
