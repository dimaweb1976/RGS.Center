package com.rgs.center.objects;

/**
 * Поддерживаемые типы настроек и их числовые коды для таблицы БД.
 */
public enum SettingType {
    BOOLEAN(1, Boolean.class),
    LONG(2, Long.class),
    DOUBLE(3, Double.class),
    STRING(4, String.class);

    private final int code;
    private final Class<?> valueClass;

    /**
     * @param code числовой код для хранения
     * @param valueClass допустимый Java-класс
     */
    SettingType(int code, Class<?> valueClass) {
        this.code = code;
        this.valueClass = valueClass;
    }

    /** @return числовой код типа */
    public int getCode() {
        return code;
    }

    /**
     * @param value проверяемое значение
     * @return соответствует ли значение этому типу
     */
    public boolean accepts(Object value) {
        return value != null && valueClass.isInstance(value);
    }

    /**
     * @param code числовой код
     * @return соответствующий тип настройки
     */
    public static SettingType fromCode(int code) {
        for (SettingType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown setting type code: " + code);
    }
}
