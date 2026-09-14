package com.rgs.center.measurement.parameters;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Транзакционная граница чтения, проверки и сохранения параметров. */
@Service
public class MeasurementParametersService {

    private final MeasurementParametersRepository repository;

    public MeasurementParametersService(MeasurementParametersRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public MeasurementParameters current() {
        return repository.findCurrent();
    }

    /**
     * Проверяет единицы и допустимые диапазоны перед записью.
     *
     * @throws IllegalArgumentException если значение нельзя применять
     * @throws IllegalStateException если форму сохранили из устаревшей вкладки
     */
    @Transactional
    public MeasurementParameters save(
            long measuringTimeMinutes,
            double volumeMl,
            SampleGeometry geometry,
            double tareMassGrams,
            long expectedVersion
    ) {
        validate(measuringTimeMinutes, volumeMl, geometry, tareMassGrams);
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Version must not be negative");
        }
        MeasurementParameters candidate = new MeasurementParameters(
                measuringTimeMinutes,
                volumeMl,
                geometry,
                tareMassGrams,
                expectedVersion,
                Instant.EPOCH
        );
        if (repository.update(candidate, expectedVersion) != 1) {
            throw new IllegalStateException(
                    "Parameters were changed in another browser. Reload the page."
            );
        }
        return repository.findCurrent();
    }

    private static void validate(
            long measuringTimeMinutes,
            double volumeMl,
            SampleGeometry geometry,
            double tareMassGrams
    ) {
        if (measuringTimeMinutes < 1 || measuringTimeMinutes > 1440) {
            throw new IllegalArgumentException(
                    "Measuring time must be between 1 and 1440 minutes"
            );
        }
        if (!Double.isFinite(volumeMl) || volumeMl <= 0 || volumeMl > 10_000) {
            throw new IllegalArgumentException(
                    "Volume must be greater than 0 and not exceed 10000 ml"
            );
        }
        if (geometry == null) {
            throw new IllegalArgumentException("Geometry is required");
        }
        if (!Double.isFinite(tareMassGrams)
                || tareMassGrams < 0
                || tareMassGrams > 100_000) {
            throw new IllegalArgumentException(
                    "Tare mass must be between 0 and 100000 g"
            );
        }
    }
}
