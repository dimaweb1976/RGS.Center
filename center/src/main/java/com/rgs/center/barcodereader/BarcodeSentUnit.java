package com.rgs.center.barcodereader;

public class BarcodeSentUnit {
    private final byte[] abtCommand;
    private final long sentTimestamp;

    public BarcodeSentUnit( byte[] abtCommand) {
        this.abtCommand = abtCommand;
        this.sentTimestamp = System.currentTimeMillis();
    }

    public byte[] getCommand() {
        return abtCommand;
    }

    public long getSentTimestamp() {
        return sentTimestamp;
    }
}