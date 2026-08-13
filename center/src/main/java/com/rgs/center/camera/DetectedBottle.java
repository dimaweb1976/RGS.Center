package com.rgs.center.camera;

/**
 * Найденная бутылка, представленная круглым контуром крышки на изображении.
 * Координаты и размеры указаны в пикселях исходного кадра.
 */
public record DetectedBottle(
        int number,
        double centerX,
        double centerY,
        int width,
        int height,
        double diameter,
        double circularity
) {
}
