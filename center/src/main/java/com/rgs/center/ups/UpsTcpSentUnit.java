package com.rgs.center.ups;

public class UpsTcpSentUnit {
    private final long lSentTime;
    public long getSentTime() { return lSentTime; }
    
    private final byte[] abtSent;
    public byte[] getSent() { return abtSent; }
    
    public UpsTcpSentUnit(byte[] abtSent) {
        lSentTime = System.currentTimeMillis();
        this.abtSent = abtSent;
    }
}