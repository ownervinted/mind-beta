package mind.beta.modules.module.setting.implement;

import mind.beta.modules.module.setting.Setting;
import java.util.function.Supplier;

public final class BezierSetting extends Setting {
    public final SliderSettings x1, y1, x2, y2;
    public BezierSetting(String name, SliderSettings x1, SliderSettings y1, SliderSettings x2, SliderSettings y2) {
        super(name, "");
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
    }
    public BezierSetting visible(Supplier<Boolean> visible) { setVisible(visible); return this; }
}
