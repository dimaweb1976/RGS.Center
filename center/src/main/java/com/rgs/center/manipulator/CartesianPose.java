package com.rgs.center.manipulator;

/** Cartesian robot pose reported by the Dobot real-time feedback channel. */
public record CartesianPose(
        double x,
        double y,
        double z,
        double rx,
        double ry,
        double rz
) {
}
