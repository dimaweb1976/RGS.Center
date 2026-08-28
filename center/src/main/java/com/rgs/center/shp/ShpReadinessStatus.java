package com.rgs.center.shp;

/** Итоговый технологический статус последних показаний SHP. */
public enum ShpReadinessStatus {
    /** Все контролируемые значения ниже заданных порогов. */
    READY("Ready", "shp-status--ready"),
    /** Хотя бы одно контролируемое значение достигло или превысило порог. */
    ALERT("Alert", "shp-status--alert"),
    /** Достоверного актуального снимка SHP пока нет. */
    NO_DATA("No data", "shp-status--no-data");

    private final String label;
    private final String cssClass;

    ShpReadinessStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    /** @return текст статуса для страницы устройств */
    public String getLabel() {
        return label;
    }

    /** @return безопасное фиксированное имя CSS-класса индикатора */
    public String getCssClass() {
        return cssClass;
    }
}
