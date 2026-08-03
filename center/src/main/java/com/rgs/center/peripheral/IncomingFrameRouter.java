package com.rgs.center.peripheral;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Разделяет ответы команд, телеметрию и аварийные сообщения по независимым
 * ограниченным очередям.
 *
 * <p>Медленный потребитель одного маршрута не блокирует остальные. При
 * переполнении удаляется только самый старый кадр того же маршрута, а потеря
 * отражается в диагностическом снимке.</p>
 */
public final class IncomingFrameRouter {

    private final IncomingFrameClassifier classifier;
    private final Map<IncomingFrameRoute, LinkedBlockingDeque<RoutedFrame>> queues =
            new EnumMap<>(IncomingFrameRoute.class);
    private final Map<IncomingFrameRoute, AtomicLong> routed =
            new EnumMap<>(IncomingFrameRoute.class);
    private final Map<IncomingFrameRoute, AtomicLong> dropped =
            new EnumMap<>(IncomingFrameRoute.class);

    /**
     * @param classifier протокольная классификация входящих кадров
     * @param queueCapacity ёмкость каждой независимой очереди
     */
    public IncomingFrameRouter(
            IncomingFrameClassifier classifier,
            int queueCapacity
    ) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        for (IncomingFrameRoute route : IncomingFrameRoute.values()) {
            queues.put(route, new LinkedBlockingDeque<>(queueCapacity));
            routed.put(route, new AtomicLong());
            dropped.put(route, new AtomicLong());
        }
    }

    /** Классифицирует и публикует защищённую копию кадра. */
    public IncomingFrameRoute route(String sourceId, byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        IncomingFrameRoute destination = Objects.requireNonNullElse(
                classifier.classify(sourceId, frame),
                IncomingFrameRoute.UNKNOWN
        );
        RoutedFrame routedFrame = new RoutedFrame(
                sourceId,
                frame,
                System.currentTimeMillis()
        );
        LinkedBlockingDeque<RoutedFrame> queue = queues.get(destination);
        if (!queue.offerLast(routedFrame)) {
            queue.pollFirst();
            queue.offerLast(routedFrame);
            dropped.get(destination).incrementAndGet();
        }
        routed.get(destination).incrementAndGet();
        return destination;
    }

    /**
     * Ожидает кадр только выбранного маршрута, не затрагивая остальные.
     *
     * @param route требуемый маршрут
     * @param timeout максимальное время ожидания
     */
    public Optional<RoutedFrame> await(
            IncomingFrameRoute route,
            Duration timeout
    ) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        try {
            return Optional.ofNullable(queues.get(route).pollFirst(
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Удаляет устаревшие кадры одного маршрута перед новым запросом. */
    public void clear(IncomingFrameRoute route) {
        queues.get(route).clear();
    }

    /** Удаляет все ещё не обработанные кадры. */
    public void clear() {
        queues.values().forEach(LinkedBlockingDeque::clear);
    }

    /** @return согласованный снимок размеров очередей и счётчиков */
    public IncomingFrameRouterSnapshot snapshot() {
        Map<IncomingFrameRoute, Integer> depths =
                new EnumMap<>(IncomingFrameRoute.class);
        Map<IncomingFrameRoute, Long> routedCounts =
                new EnumMap<>(IncomingFrameRoute.class);
        Map<IncomingFrameRoute, Long> droppedCounts =
                new EnumMap<>(IncomingFrameRoute.class);
        for (IncomingFrameRoute route : IncomingFrameRoute.values()) {
            depths.put(route, queues.get(route).size());
            routedCounts.put(route, routed.get(route).get());
            droppedCounts.put(route, dropped.get(route).get());
        }
        return new IncomingFrameRouterSnapshot(
                Collections.unmodifiableMap(depths),
                Collections.unmodifiableMap(routedCounts),
                Collections.unmodifiableMap(droppedCounts)
        );
    }
}
