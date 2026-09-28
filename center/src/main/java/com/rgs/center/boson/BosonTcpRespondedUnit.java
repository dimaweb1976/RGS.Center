package com.rgs.center.boson;

public class BosonTcpRespondedUnit {
    private final long lSentTime;
    public long getSentTime() { return lSentTime; }
    
    private final byte[] abtSent;
    public byte[] getSent() { return abtSent; }
    
    private final long lRespondTime;
    public long getRespondTime() { return lRespondTime; }
    
    private final byte[] abtRespond;
    public byte[] getRespond() { return abtRespond; }
    
    public BosonTcpRespondedUnit(BosonTcpSentUnit unit, byte[] abtRespond) {
        this.lSentTime = unit.getSentTime();
        this.abtSent = unit.getSent();
        this.lRespondTime = System.currentTimeMillis();
        this.abtRespond = abtRespond;
    }
}