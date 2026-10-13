package mind.beta.modules.module.setting.implement;

import mind.beta.modules.module.setting.Setting;
import java.util.function.Supplier;

public final class HitboxPointSetting extends Setting {
    public final SliderSettings pointX;
    public final SliderSettings pointY;

    public HitboxPointSetting(String name, SliderSettings x, SliderSettings y) {
        super(name, "");
        this.pointX = x;
        this.pointY = y;
    }

    public HitboxPointSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }
}
