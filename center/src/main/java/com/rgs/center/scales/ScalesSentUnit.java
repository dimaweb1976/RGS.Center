package com.rgs.center.scales;

public class ScalesSentUnit {
    private final long lSentTime;
    public long getSentTime() { return lSentTime; }
    
    private final byte[] abtSent;
    public byte[] getSent() { return abtSent; }
    
    public ScalesSentUnit(byte[] abtSent) {
        lSentTime = System.currentTimeMillis();
        this.abtSent = abtSent;
    }
}