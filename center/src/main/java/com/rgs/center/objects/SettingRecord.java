package com.rgs.center.objects;

/**
 * Представление одной настройки в форме строки будущей таблицы БД.
 *
 * @param stringId строковый идентификатор
 * @param type числовой тип
 * @param booleanValue значение BOOLEAN
 * @param longValue значение LONG
 * @param doubleValue значение DOUBLE
 * @param stringValue значение STRING
 */
public record SettingRecord(
        String stringId,
        int type,
        Boolean booleanValue,
        Long longValue,
        Double doubleValue,
        String stringValue
) {

    /**
     * @return значение из колонки, соответствующей полю {@link #type()}
     */
    public Object value() {
        return switch (SettingType.fromCode(type)) {
            case BOOLEAN -> booleanValue;
            case LONG -> longValue;
            case DOUBLE -> doubleValue;
            case STRING -> stringValue;
        };
    }

    /**
     * Создаёт запись, заполняя только колонку объявленного типа.
     *
     * @param stringId идентификатор настройки
     * @param type тип настройки
     * @param value значение
     * @return запись для сохранения
     */
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
