package com.rgs.center.peripheral;

/** Определяет маршрут кадра с учётом физического источника. */
@FunctionalInterface
public interface IncomingFrameClassifier {

    /**
     * @param sourceId постоянное имя входящего соединения
     * @param frame полный кадр протокола
     * @return маршрут, который должен получить кадр
     */
    IncomingFrameRoute classify(String sourceId, byte[] frame);
}
