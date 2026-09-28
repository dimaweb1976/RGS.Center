package com.rgs.center.boson;

public class BosonTcpSentUnit {
    private final long lSentTime;
    public long getSentTime() { return lSentTime; }
    
    private final byte[] abtSent;
    public byte[] getSent() { return abtSent; }
    
    public BosonTcpSentUnit(byte[] abtSent) {
        this.lSentTime = System.currentTimeMillis();
        this.abtSent = abtSent;
    }
}