package mind.beta.modules.module.setting.implement;

import mind.beta.modules.module.setting.Setting;
import java.util.function.Supplier;

public final class DividerSetting extends Setting {
    public DividerSetting() { super("", ""); }
    public DividerSetting visible(Supplier<Boolean> visible) { setVisible(visible); return this; }
}
