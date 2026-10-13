package mind.beta.modules.impl.render;

import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;

public class StorageViewer extends ModuleStructure {
    public static StorageViewer INSTANCE;

    private final BooleanSetting silentScan = new BooleanSetting(
            "Silent Scan",
            "Automatically scan unopened containers through a normal interaction"
    ).setValue(false);

    public StorageViewer() {
        super("StorageViewer", ModuleCategory.RENDER);
        INSTANCE = this;
        settings(silentScan);
    }

    public static boolean isEnabled() {
        return INSTANCE != null && INSTANCE.isState();
    }

    public static boolean isScanEnabled() {
        return isEnabled() && INSTANCE.silentScan.isValue();
    }
}
