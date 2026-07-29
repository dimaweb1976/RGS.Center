package com.rgs.center.objects;

/**
 * Постоянные коды физических устройств из структурной схемы RGS.
 *
 * <p>Сетевые устройства используют настройку IP, локальная периферия —
 * настройку имени COM-порта.</p>
 */
public enum DeviceCode {
    CAMERA("camera", "Camera", "device.camera.ip"),
    ROBOPLC("roboplc", "RoboPLC", "device.roboplc.ip"),
    NICOLE("nicole", "Nicole", "device.nicole.ip"),
    BOSON("boson", "BOSON", "device.boson.ip"),
    BARCODE_SCANNER(
            "barcode-scanner",
            "Barcode scanner",
            "device.barcode-scanner.port"
    ),
    SCALES(
            "scales",
            "Scales",
            "device.scales.port"
    ),
    SHP(
            "shp",
            "SHP",
            "device.shp.port"
    );

    private final String code;
    private final String displayName;
    private final String ipSettingCode;

    /**
     * @param code машинный идентификатор для витрины
     * @param displayName отображаемое имя
     * @param ipSettingCode код настройки адреса или COM-порта
     */
    DeviceCode(String code, String displayName, String ipSettingCode) {
        this.code = code;
        this.displayName = displayName;
        this.ipSettingCode = ipSettingCode;
    }

    /** @return машинный идентификатор */
    public String getCode() {
        return code;
    }

    /** @return отображаемое имя */
    public String getDisplayName() {
        return displayName;
    }

    /** @return код настройки подключения */
    public String getIpSettingCode() {
        return ipSettingCode;
    }
}
