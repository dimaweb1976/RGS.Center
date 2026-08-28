package com.rgs.center.objects;

/**
 * Постоянные коды физических устройств из структурной схемы RGS.
 *
 * <p>Сетевые устройства используют настройку IP, локальная периферия —
 * настройку имени COM-порта.</p>
 */
public enum DeviceCode {
    CAMERA("camera", "Camera", "device.camera.ip"),
    LENOVO_PC("lenovo-pc", "LenovoPC", "device.lenovo-pc.ip"),
    NICOLE(
            "nicole",
            "Nicole",
            "device.nicole.ip",
            "device.nicole.port"
    ),
    BOSON("boson", "BOSON", "device.boson.ip"),
    ROBOTIC_MANIPULATOR(
            "RoboticManipulator",
            "Robotic manipulator",
            "device.manipulator.host",
            "device.manipulator.command-port"
    ),
    BARCODE_SCANNER(
            "barcode-scanner",
            "Barcode scanner",
            "device.barcode-scanner.port"
    ),
    GDRM(
            "gdrm",
            "GDRM",
            "device.gdrm.port"
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
    ),
    GAMMA_PRO(
            "gamma-pro",
            "Gamma-PRO",
            "device.gamma-pro.host",
            "device.gamma-pro.port"
    );

    private final String code;
    private final String displayName;
    private final String ipSettingCode;
    private final String portSettingCode;

    /**
     * @param code машинный идентификатор для витрины
     * @param displayName отображаемое имя
     * @param ipSettingCode код настройки адреса или COM-порта
     */
    DeviceCode(String code, String displayName, String ipSettingCode) {
        this(code, displayName, ipSettingCode, null);
    }

    /**
     * @param code машинный идентификатор
     * @param displayName отображаемое имя
     * @param ipSettingCode настройка адреса
     * @param portSettingCode отдельная настройка TCP-порта или {@code null}
     */
    DeviceCode(
            String code,
            String displayName,
            String ipSettingCode,
            String portSettingCode
    ) {
        this.code = code;
        this.displayName = displayName;
        this.ipSettingCode = ipSettingCode;
        this.portSettingCode = portSettingCode;
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

    /** @return код отдельной настройки TCP-порта или {@code null} */
    public String getPortSettingCode() {
        return portSettingCode;
    }

    /** @return Spring property controlling this device connection */
    public String getEnabledProperty() {
        if (this == ROBOTIC_MANIPULATOR) {
            return "rgs.device.manipulator.enabled";
        }
        return "rgs.device." + code + ".enabled";
    }
}
