package com.rgs.center.measurement;

/**
 * Состояние автоматического сценария с точки зрения возможности продолжения.
 *
 * <p>Сейчас рабочий алгоритм устанавливает {@link #NORMAL} или
 * {@link #EMERGENCY}. Остальные значения зарезервированы для последующего
 * развития оркестратора и уже поддерживаются web-представлением.</p>
 */
public enum ScenarioStatus {
    NORMAL("scenario-status--normal"),
    EMERGENCY("scenario-status--emergency"),
    PAUSED("scenario-status--paused"),
    ABORTING("scenario-status--aborting"),
    RECOVERY_REQUIRED("scenario-status--recovery-required");

    private final String cssClass;

    ScenarioStatus(String cssClass) {
        this.cssClass = cssClass;
    }

    /** @return неизменяемое имя состояния для оператора и внешних клиентов */
    public String getLabel() {
        return name();
    }

    /** @return CSS-класс цветового индикатора состояния */
    public String getCssClass() {
        return cssClass;
    }
}
