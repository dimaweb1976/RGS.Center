package com.rgs.center.camera;

import java.awt.image.BufferedImage;

/** Выделяет бутылки на одном неподвижном кадре камеры. */
interface BottleRecognitionEngine {

    BottleRecognitionResult recognize(BufferedImage source);
}
