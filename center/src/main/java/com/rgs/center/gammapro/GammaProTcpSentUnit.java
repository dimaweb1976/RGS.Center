package com.rgs.center.gammapro;

public class GammaProTcpSentUnit {
    private final long lSentTime;
    public long getSentTime() { return lSentTime; }
    
    private final byte [] abtSent;
    public byte[] getSent() { return abtSent; }
    
    public GammaProTcpSentUnit( byte [] abtSent) {
        lSentTime = System.currentTimeMillis();
        this.abtSent = abtSent;
    }
}
