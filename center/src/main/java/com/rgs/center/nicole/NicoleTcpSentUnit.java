package com.rgs.center.nicole;

public class NicoleTcpSentUnit {
    private final long lSentTime;
    public long getSentTime() { return lSentTime; }
    
    private final byte[] abtSent;
    public byte[] getSent() { return abtSent; }
    
    public NicoleTcpSentUnit(byte[] abtSent) {
        lSentTime = System.currentTimeMillis();
        this.abtSent = abtSent;
    }
}