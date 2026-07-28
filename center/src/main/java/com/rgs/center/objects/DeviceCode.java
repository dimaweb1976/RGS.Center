package com.rgs.center.objects;

public enum DeviceCode {
    CAMERA("camera", "Camera", "device.camera.ip"),
    ROBOPLC("roboplc", "RoboPLC", "device.roboplc.ip"),
    NICOLE("nicole", "Nicole", "device.nicole.ip"),
    BOSON("boson", "BOSON", "device.boson.ip"),
    BARCODE_SCANNER(
            "barcode-scanner",
            "Barcode scanner",
            "device.barcode-scanner.port"
    );

    private final String code;
    private final String displayName;
    private final String ipSettingCode;

    DeviceCode(String code, String displayName, String ipSettingCode) {
        this.code = code;
        this.displayName = displayName;
        this.ipSettingCode = ipSettingCode;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIpSettingCode() {
        return ipSettingCode;
    }
}
