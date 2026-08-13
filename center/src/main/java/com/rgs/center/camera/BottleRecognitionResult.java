package com.rgs.center.camera;

import java.awt.image.BufferedImage;
import java.util.List;

/** Размеченное изображение и список найденных бутылок. */
record BottleRecognitionResult(
        BufferedImage annotatedImage,
        List<DetectedBottle> bottles
) {
}
