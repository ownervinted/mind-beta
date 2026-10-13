package mind.beta.modules.module.setting;

import mind.beta.modules.module.setting.implement.*;
import mind.beta.screens.clickgui.impl.settingsrender.*;
import mind.beta.modules.module.setting.implement.*;
import mind.beta.screens.clickgui.impl.settingsrender.*;
import mind.beta.util.interfaces.AbstractSettingComponent;

import java.util.List;

public class SettingComponentAdder {

    public void addSettingComponent(List<Setting> settings, List<AbstractSettingComponent> components) {
        settings.forEach(setting -> {
            if (setting instanceof DividerSetting d) components.add(new DividerComponent(d));
            if (setting instanceof BezierSetting b) components.add(new BezierComponent(b));
            if (setting instanceof HitboxPointSetting h) components.add(new HitboxPointComponent(h));
            if (setting instanceof MultiPointSetting m) components.add(new MultiPointComponent(m));
            if (setting instanceof BooleanSetting booleanSetting) {
                components.add(new CheckboxComponent(booleanSetting));
            }

            if (setting instanceof BindSetting bindSetting) {
                components.add(new BindComponent(bindSetting));
            }

            if (setting instanceof ColorSetting colorSetting) {
                components.add(new ColorComponent(colorSetting));
            }

            if (setting instanceof TextSetting textSetting) {
                components.add(new TextComponent(textSetting));
            }

            if (setting instanceof SliderSettings valueSetting) {
                components.add(new SliderComponent(valueSetting));
            }

            if (setting instanceof ButtonSetting buttonSetting) {
                components.add(new ButtonComponent(buttonSetting));
            }

            if (setting instanceof SelectSetting selectSetting) {
                components.add(new SelectComponent(selectSetting));
            }

            if (setting instanceof MultiSelectSetting multiSelectSetting) {
                components.add(new MultiSelectComponent(multiSelectSetting));
            }
        });
    }
}
