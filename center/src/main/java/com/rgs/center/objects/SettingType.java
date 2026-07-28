package com.rgs.center.objects;

public enum SettingType {
    BOOLEAN(1, Boolean.class),
    LONG(2, Long.class),
    DOUBLE(3, Double.class),
    STRING(4, String.class);

    private final int code;
    private final Class<?> valueClass;

    SettingType(int code, Class<?> valueClass) {
        this.code = code;
        this.valueClass = valueClass;
    }

    public int getCode() {
        return code;
    }

    public boolean accepts(Object value) {
        return value != null && valueClass.isInstance(value);
    }

    public static SettingType fromCode(int code) {
        for (SettingType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown setting type code: " + code);
    }
}
