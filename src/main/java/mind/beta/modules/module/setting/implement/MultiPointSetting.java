package mind.beta.modules.module.setting.implement;

import mind.beta.modules.module.setting.Setting;
import java.util.function.Supplier;

public final class MultiPointSetting extends Setting {
    public MultiPointSetting(String name) {
        super(name, "");
    }

    public MultiPointSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }
}
