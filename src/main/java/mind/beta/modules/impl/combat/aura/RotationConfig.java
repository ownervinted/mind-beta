/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.combat.aura;

import lombok.Getter;

@Getter
public class RotationConfig {
    private float acquireSpeed;
    private float trackSpeed;
    private float returnSpeed;
    private float maxYawSpeed;
    private float maxPitchSpeed;
    private float acceleration;
    private float deceleration;
    private float aimSmoothing;

    public RotationConfig() {
        this(80.0f, 50.0f, 120.0f, 70.0f, 55.0f, 0.56f, 0.74f, 0.8f);
    }

    public RotationConfig(float acquireSpeed, float trackSpeed, float returnSpeed,
                          float maxYawSpeed, float maxPitchSpeed, float acceleration,
                          float deceleration, float aimSmoothing) {
        this.acquireSpeed = acquireSpeed;
        this.trackSpeed = trackSpeed;
        this.returnSpeed = returnSpeed;
        this.maxYawSpeed = maxYawSpeed;
        this.maxPitchSpeed = maxPitchSpeed;
        this.acceleration = acceleration;
        this.deceleration = deceleration;
        this.aimSmoothing = aimSmoothing;
    }

    public RotationConfig acquireSpeed(float value) { this.acquireSpeed = value; return this; }
    public RotationConfig trackSpeed(float value) { this.trackSpeed = value; return this; }
    public RotationConfig returnSpeed(float value) { this.returnSpeed = value; return this; }
    public RotationConfig maxYawSpeed(float value) { this.maxYawSpeed = value; return this; }
    public RotationConfig maxPitchSpeed(float value) { this.maxPitchSpeed = value; return this; }
    public RotationConfig acceleration(float value) { this.acceleration = value; return this; }
    public RotationConfig deceleration(float value) { this.deceleration = value; return this; }
    public RotationConfig aimSmoothing(float value) { this.aimSmoothing = value; return this; }

    public static RotationConfig fast() {
        return new RotationConfig(42.0f, 28.0f, 22.0f, 65.0f, 35.0f, 0.42f, 0.38f, 0.92f);
    }

    public static RotationConfig smooth() {
        return new RotationConfig(22.0f, 12.0f, 9.0f, 32.0f, 20.0f, 0.20f, 0.18f, 0.86f);
    }

    public static RotationConfig legit() {
        return new RotationConfig(17.0f, 8.0f, 7.0f, 25.0f, 15.0f, 0.14f, 0.12f, 0.78f);
    }
}
