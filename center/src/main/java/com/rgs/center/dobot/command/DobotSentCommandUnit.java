package com.rgs.center.dobot.command;

public class DobotSentCommandUnit {
    private final String command;
    private final long timestamp;

    public DobotSentCommandUnit(String command) {
        this.command = command;
        this.timestamp = System.currentTimeMillis();
    }

    public String getCommand() {
        return command;
    }

    public long getTimestamp() {
        return timestamp;
    }
}