package com.rgs.center.peripheral;

import java.util.Map;

/** Диагностический снимок маршрутизатора входящих кадров. */
public record IncomingFrameRouterSnapshot(
        Map<IncomingFrameRoute, Integer> queueDepths,
        Map<IncomingFrameRoute, Long> routedFrames,
        Map<IncomingFrameRoute, Long> droppedFrames
) {
}
