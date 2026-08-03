package com.rgs.center.measurement.emulator;

/** Состояние верхнего уровня эмулятора алгоритма измерения. */
public enum MeasurementEmulatorPhase {
    PRECHECK("Проверка готовности"),
    PARALLEL_PREPARATION("Подготовка высокого напряжения и камеры"),
    WAITING_FOR_OBJECTS("Ожидание списка объектов"),
    READY_TO_START("Готов к запуску"),
    RUNNING("Выполнение цикла"),
    COMPLETED("Цикл завершён"),
    ALERT("Оповещение");

    private final String displayName;

    MeasurementEmulatorPhase(String displayName) {
        this.displayName = displayName;
    }

    /** @return подпись состояния для web-интерфейса */
    public String getDisplayName() {
        return displayName;
    }
}
