package com.rgs.center.measurement;

import org.springframework.stereotype.Service;

/** Atomically prevents workflows and service commands from sharing devices. */
@Service
public class MeasurementExecutionCoordinator {

    public enum Owner { NONE, AUTOMATIC, MANUAL, SERVICE }

    private Owner owner = Owner.NONE;

    public synchronized void acquire(Owner requested) {
        if (requested == null || requested == Owner.NONE) {
            throw new IllegalArgumentException("Measurement owner is required");
        }
        if (owner != Owner.NONE) {
            throw new IllegalStateException(
                    "Measurement equipment is already reserved by " + owner
            );
        }
        owner = requested;
    }

    public synchronized void release(Owner expected) {
        if (owner == expected) owner = Owner.NONE;
    }

    public synchronized Owner owner() {
        return owner;
    }
}
