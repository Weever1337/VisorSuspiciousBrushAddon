package dev.wvr.visor.susbrush.core.common;

import org.joml.Vector3f;

public class AddonUtils {
    public static final Vector3f BRUSH_TIP_OFFSET = new Vector3f(0.0F, 0.33F, -0.18F);
    public static final double MAX_TIP_TO_BLOCK_DISTANCE = 0.185D;
    public static final double MIN_SURFACE_MOVEMENT = 0.01D;
    public static final double REQUIRED_SURFACE_MOVEMENT = 0.065D;
    public static final int BRUSH_INTERVAL_TICKS = 10;
    public static final double MAX_TIP_TO_BLOCK_DISTANCE_SQR = MAX_TIP_TO_BLOCK_DISTANCE * MAX_TIP_TO_BLOCK_DISTANCE;
    public static final double MAX_BRUSH_DISTANCE_SQR = 25.0D;
}