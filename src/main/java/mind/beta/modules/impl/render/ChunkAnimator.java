/*
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.render;

import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.SliderSettings;

public class ChunkAnimator extends ModuleStructure {
    private static ChunkAnimator instance;

    private final SliderSettings speed = new SliderSettings("Speed", "").range(1, 20).setValue(10);

    public ChunkAnimator() {
        super("Chunk Animator", "Animate newly loaded chunks", ModuleCategory.RENDER);
        instance = this;
        settings(speed);
    }

    public static ChunkAnimator getInstance() {
        return instance;
    }

    public float getSpeed() {
        return speed.getValue();
    }
}