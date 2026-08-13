package com.rgs.center.camera;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.filter.blur.BlurImageOps;
import boofcv.struct.ConfigLength;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_I32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Алгоритм распознавания концентрических окружностей, перенесённый из
 * проекта SilouttesDetection без его экспериментального legacy-кода.
 */
@Component
class BoofCvBottleRecognitionEngine implements BottleRecognitionEngine {

    private static final int BOTTLE_NUMBER_FONT_SIZE = 54;
    private static final double ROW_CENTER_TOLERANCE_RATIO = 0.75;

    private final DetectorConfig config;

    BoofCvBottleRecognitionEngine(
            @Value("${rgs.camera.detection.initial-threshold:40}") int initialThreshold,
            @Value("${rgs.camera.detection.blur-radius:5}") int blurRadius,
            @Value("${rgs.camera.detection.sauvola-radius:20}") int sauvolaRadius,
            @Value("${rgs.camera.detection.sauvola-k:0.005}") double sauvolaK,
            @Value("${rgs.camera.detection.morphology-radius:2}") int morphologyRadius,
            @Value("${rgs.camera.detection.contour-points-min:40}") int contourPointsMin,
            @Value("${rgs.camera.detection.diameter-min:20}") double diameterMin,
            @Value("${rgs.camera.detection.diameter-max:180}") double diameterMax,
            @Value("${rgs.camera.detection.aspect-ratio-min:0.78}") double aspectRatioMin,
            @Value("${rgs.camera.detection.aspect-ratio-max:1.22}") double aspectRatioMax,
            @Value("${rgs.camera.detection.circularity-min:0.55}") double circularityMin,
            @Value("${rgs.camera.detection.concentric-center-distance-ratio-max:0.18}")
            double centerDistanceRatioMax,
            @Value("${rgs.camera.detection.inner-to-outer-ratio-min:0.30}")
            double innerToOuterRatioMin,
            @Value("${rgs.camera.detection.inner-to-outer-ratio-max:0.90}")
            double innerToOuterRatioMax,
            @Value("${rgs.camera.detection.duplicate-center-distance-ratio-max:0.35}")
            double duplicateDistanceRatioMax
    ) {
        config = new DetectorConfig(
                initialThreshold,
                blurRadius,
                sauvolaRadius,
                sauvolaK,
                morphologyRadius,
                contourPointsMin,
                diameterMin,
                diameterMax,
                aspectRatioMin,
                aspectRatioMax,
                circularityMin,
                centerDistanceRatioMax,
                innerToOuterRatioMin,
                innerToOuterRatioMax,
                duplicateDistanceRatioMax
        );
        config.validate();
    }

    @Override
    public BottleRecognitionResult recognize(BufferedImage source) {
        GrayU8 input = grayscale(source);
        GrayU8 filtered = input.createNew(input.width, input.height);
        filtered.setTo(input);
        GrayU8 binary = input.createNew(input.width, input.height);

        ThresholdImageOps.threshold(
                filtered,
                binary,
                config.initialThreshold,
                false
        );
        for (int index = 0; index < filtered.data.length; index++) {
            filtered.data[index] = (byte) (
                    (filtered.data[index] & 0xFF) * binary.data[index]
            );
        }

        GrayU8 blurred = input.createNew(input.width, input.height);
        BlurImageOps.gaussian(
                filtered,
                blurred,
                -1,
                config.blurRadius,
                null
        );
        GThresholdImageOps.localSauvola(
                blurred,
                binary,
                ConfigLength.fixed(config.sauvolaRadius),
                (float) config.sauvolaK,
                true
        );

        GrayU8 work = binary.createNew(binary.width, binary.height);
        BinaryImageOps.dilate8(binary, config.morphologyRadius, work);
        BinaryImageOps.erode8(work, config.morphologyRadius, binary);

        List<Contour> contours = BinaryImageOps.contour(
                binary,
                ConnectRule.EIGHT,
                null
        );
        List<Candidate> candidates = candidates(contours);
        List<Candidate> rings = concentricRings(candidates);
        List<DetectedBottle> detected = new ArrayList<>(rings.size());
        for (Candidate value : rings) {
            detected.add(new DetectedBottle(
                    0,
                    value.centerX,
                    value.centerY,
                    value.width,
                    value.height,
                    value.diameter,
                    value.circularity
            ));
        }
        List<DetectedBottle> bottles = numberByRows(detected);
        return new BottleRecognitionResult(
                annotate(source, bottles),
                List.copyOf(bottles)
        );
    }

    private List<Candidate> candidates(List<Contour> contours) {
        List<Candidate> result = new ArrayList<>();
        for (Contour contour : contours) {
            addCandidate(result, contour.external);
            for (List<Point2D_I32> internal : contour.internal) {
                addCandidate(result, internal);
            }
        }
        return result;
    }

    private void addCandidate(
            List<Candidate> destination,
            List<Point2D_I32> points
    ) {
        Candidate candidate = createCandidate(points);
        if (candidate != null) {
            destination.add(candidate);
        }
    }

    private Candidate createCandidate(List<Point2D_I32> points) {
        if (points == null || points.size() < config.contourPointsMin) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        double twiceArea = 0;
        double perimeter = 0;
        for (int index = 0; index < points.size(); index++) {
            Point2D_I32 first = points.get(index);
            Point2D_I32 second = points.get((index + 1) % points.size());
            minX = Math.min(minX, first.x);
            maxX = Math.max(maxX, first.x);
            minY = Math.min(minY, first.y);
            maxY = Math.max(maxY, first.y);
            twiceArea += (double) first.x * second.y
                    - (double) second.x * first.y;
            perimeter += Math.hypot(second.x - first.x, second.y - first.y);
        }
        int width = maxX - minX;
        int height = maxY - minY;
        if (width <= 0 || height <= 0 || perimeter <= 0) {
            return null;
        }
        double diameter = (width + height) / 2.0;
        double aspectRatio = (double) width / height;
        double area = Math.abs(twiceArea) / 2.0;
        double circularity = 4.0 * Math.PI * area / (perimeter * perimeter);
        if (diameter < config.diameterMin || diameter > config.diameterMax
                || aspectRatio < config.aspectRatioMin
                || aspectRatio > config.aspectRatioMax
                || circularity < config.circularityMin) {
            return null;
        }
        return new Candidate(
                minX + width / 2.0,
                minY + height / 2.0,
                width,
                height,
                diameter,
                circularity
        );
    }

    private List<Candidate> concentricRings(List<Candidate> candidates) {
        List<Candidate> rings = new ArrayList<>();
        for (Candidate outer : candidates) {
            for (Candidate inner : candidates) {
                if (outer == inner || inner.diameter >= outer.diameter) {
                    continue;
                }
                double ratio = inner.diameter / outer.diameter;
                double distanceRatio = Math.hypot(
                        inner.centerX - outer.centerX,
                        inner.centerY - outer.centerY
                ) / outer.diameter;
                if (ratio >= config.innerToOuterRatioMin
                        && ratio <= config.innerToOuterRatioMax
                        && distanceRatio <= config.centerDistanceRatioMax) {
                    rings.add(outer);
                    break;
                }
            }
        }
        rings.sort(Comparator.comparingDouble(value -> value.diameter));
        List<Candidate> unique = new ArrayList<>();
        for (Candidate candidate : rings) {
            boolean duplicate = unique.stream().anyMatch(accepted ->
                    Math.hypot(
                            candidate.centerX - accepted.centerX,
                            candidate.centerY - accepted.centerY
                    ) / Math.max(candidate.diameter, accepted.diameter)
                            <= config.duplicateDistanceRatioMax
            );
            if (!duplicate) {
                unique.add(candidate);
            }
        }
        return unique;
    }

    /**
     * Groups detections into horizontal rows and numbers them from top to
     * bottom, then from left to right inside each row.
     */
    static List<DetectedBottle> numberByRows(List<DetectedBottle> detected) {
        List<DetectedBottle> orderedByY = new ArrayList<>(detected);
        orderedByY.sort(
                Comparator.comparingDouble(DetectedBottle::centerY)
                        .thenComparingDouble(DetectedBottle::centerX)
        );

        List<BottleRow> rows = new ArrayList<>();
        for (DetectedBottle bottle : orderedByY) {
            BottleRow row = rows.stream()
                    .filter(value -> value.accepts(bottle))
                    .min(Comparator.comparingDouble(
                            value -> value.distanceTo(bottle)
                    ))
                    .orElseGet(() -> {
                        BottleRow value = new BottleRow();
                        rows.add(value);
                        return value;
                    });
            row.add(bottle);
        }

        rows.sort(Comparator.comparingDouble(BottleRow::centerY));
        List<DetectedBottle> numbered = new ArrayList<>(detected.size());
        int number = 1;
        for (BottleRow row : rows) {
            row.bottles.sort(Comparator.comparingDouble(DetectedBottle::centerX));
            for (DetectedBottle bottle : row.bottles) {
                numbered.add(new DetectedBottle(
                        number++,
                        bottle.centerX(),
                        bottle.centerY(),
                        bottle.width(),
                        bottle.height(),
                        bottle.diameter(),
                        bottle.circularity()
                ));
            }
        }
        return List.copyOf(numbered);
    }

    private static GrayU8 grayscale(BufferedImage source) {
        GrayU8 result = new GrayU8(source.getWidth(), source.getHeight());
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                int red = (rgb >>> 16) & 0xFF;
                int green = (rgb >>> 8) & 0xFF;
                int blue = rgb & 0xFF;
                int gray = (299 * red + 587 * green + 114 * blue) / 1000;
                result.set(x, y, gray);
            }
        }
        return result;
    }

    private static BufferedImage annotate(
            BufferedImage source,
            List<DetectedBottle> bottles
    ) {
        BufferedImage result = copyRgb(source);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );
            graphics.setStroke(new BasicStroke(4));
            graphics.setFont(new Font(
                    Font.SANS_SERIF,
                    Font.BOLD,
                    BOTTLE_NUMBER_FONT_SIZE
            ));
            for (DetectedBottle bottle : bottles) {
                int padding = 5;
                int x = (int) Math.round(bottle.centerX() - bottle.width() / 2.0)
                        - padding;
                int y = (int) Math.round(bottle.centerY() - bottle.height() / 2.0)
                        - padding;
                graphics.setColor(Color.GREEN);
                graphics.drawOval(
                        x,
                        y,
                        bottle.width() + padding * 2,
                        bottle.height() + padding * 2
                );
                drawLabel(graphics, Integer.toString(bottle.number()), x, y);
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            drawLabel(
                    graphics,
                    "Найдено бутылок: " + bottles.size(),
                    10,
                    10
            );
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static void drawLabel(
            Graphics2D graphics,
            String text,
            int x,
            int y
    ) {
        int width = graphics.getFontMetrics().stringWidth(text) + 10;
        int height = graphics.getFontMetrics().getHeight() + 4;
        graphics.setColor(new Color(0, 0, 0, 190));
        graphics.fillRect(x, y, width, height);
        graphics.setColor(Color.GREEN);
        graphics.drawString(
                text,
                x + 5,
                y + graphics.getFontMetrics().getAscent() + 2
        );
    }

    private static BufferedImage copyRgb(BufferedImage source) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private record Candidate(
            double centerX,
            double centerY,
            int width,
            int height,
            double diameter,
            double circularity
    ) {
    }

    private static final class BottleRow {

        private final List<DetectedBottle> bottles = new ArrayList<>();

        private void add(DetectedBottle bottle) {
            bottles.add(bottle);
        }

        private boolean accepts(DetectedBottle bottle) {
            if (bottles.isEmpty()) {
                return true;
            }
            double averageHeight = bottles.stream()
                    .mapToInt(DetectedBottle::height)
                    .average()
                    .orElse(bottle.height());
            double tolerance = (averageHeight + bottle.height()) / 2.0
                    * ROW_CENTER_TOLERANCE_RATIO;
            return distanceTo(bottle) <= tolerance;
        }

        private double distanceTo(DetectedBottle bottle) {
            return Math.abs(centerY() - bottle.centerY());
        }

        private double centerY() {
            return bottles.stream()
                    .mapToDouble(DetectedBottle::centerY)
                    .average()
                    .orElse(0.0);
        }
    }

    private record DetectorConfig(
            int initialThreshold,
            int blurRadius,
            int sauvolaRadius,
            double sauvolaK,
            int morphologyRadius,
            int contourPointsMin,
            double diameterMin,
            double diameterMax,
            double aspectRatioMin,
            double aspectRatioMax,
            double circularityMin,
            double centerDistanceRatioMax,
            double innerToOuterRatioMin,
            double innerToOuterRatioMax,
            double duplicateDistanceRatioMax
    ) {
        private void validate() {
            if (initialThreshold < 0 || initialThreshold > 255
                    || blurRadius < 0 || sauvolaRadius <= 0
                    || morphologyRadius < 0 || contourPointsMin <= 0
                    || diameterMin <= 0 || diameterMax < diameterMin
                    || aspectRatioMin <= 0 || aspectRatioMax < aspectRatioMin
                    || circularityMin < 0 || circularityMin > 1
                    || innerToOuterRatioMin <= 0
                    || innerToOuterRatioMax < innerToOuterRatioMin) {
                throw new IllegalArgumentException(
                        "Invalid bottle detector configuration"
                );
            }
        }
    }
}
