package com.rgs.center.objects;

public record SettingRecord(
        String stringId,
        int type,
        Boolean booleanValue,
        Long longValue,
        Double doubleValue,
        String stringValue
) {

    public Object value() {
        return switch (SettingType.fromCode(type)) {
            case BOOLEAN -> booleanValue;
            case LONG -> longValue;
            case DOUBLE -> doubleValue;
            case STRING -> stringValue;
        };
    }

    public static SettingRecord from(String stringId, SettingType type, Object value) {
        return new SettingRecord(
                stringId,
                type.getCode(),
                type == SettingType.BOOLEAN ? (Boolean) value : null,
                type == SettingType.LONG ? (Long) value : null,
                type == SettingType.DOUBLE ? (Double) value : null,
                type == SettingType.STRING ? (String) value : null
        );
    }
}
