package mind.beta.util.config.impl;

import com.google.gson.*;
import mind.beta.modules.impl.combat.Aura;
import mind.beta.modules.impl.combat.aura.custom.RotationBuilder;
import org.lwjgl.glfw.GLFW;
import mind.beta.util.render.theme.ClientTheme;
import mind.beta.util.render.theme.ThemeColors;
import mind.beta.Initialization;
import mind.beta.modules.module.ModuleRepository;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.setting.Setting;
import mind.beta.modules.module.setting.implement.*;
import mind.beta.modules.module.setting.implement.*;
import mind.beta.util.config.impl.consolelogger.Logger;

import java.util.ArrayList;
import java.util.List;

public class ConfigSerializer {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public String serialize() {
        JsonObject root = new JsonObject();
        JsonObject modulesJson = new JsonObject();

        ModuleRepository repository = getModuleRepository();
        if (repository != null) {
            for (ModuleStructure module : repository.modules()) {
                JsonObject moduleJson = serializeModule(module);
                modulesJson.add(module.getName(), moduleJson);
            }
        }

        root.add("modules", modulesJson);
        root.add("customRotation", GSON.toJsonTree(RotationBuilder.INSTANCE.active()));
        root.add("customRotationPresets", GSON.toJsonTree(RotationBuilder.INSTANCE.exportPresets()));
        root.addProperty("customRotationSelected", RotationBuilder.INSTANCE.savedConfig);
        root.addProperty("theme", ThemeColors.selected().name());
        root.addProperty("version", "github-26.09.2026");
        root.addProperty("timestamp", System.currentTimeMillis());
        root.addProperty("client", "Minecraft 1.21.11 Mind [beta]");

        return GSON.toJson(root);
    }

    private JsonObject serializeModule(ModuleStructure module) {
        JsonObject moduleJson = new JsonObject();
        moduleJson.addProperty("enabled", module.isState());
        moduleJson.addProperty("key", module.getKey());
        moduleJson.addProperty("type", module.getType());
        moduleJson.addProperty("favorite", module.isFavorite());

        JsonObject settingsJson = new JsonObject();
        for (Setting setting : module.settings()) {
            JsonElement element = serializeSetting(setting);
            if (element != null) {
                settingsJson.add(setting.getName(), element);
            }
        }
        moduleJson.add("settings", settingsJson);

        return moduleJson;
    }

    private JsonElement serializeSetting(Setting setting) {
        if (setting instanceof BooleanSetting boolSetting) {
            return new JsonPrimitive(boolSetting.isValue());
        }
        if (setting instanceof SliderSettings sliderSetting) {
            return new JsonPrimitive(sliderSetting.getValue());
        }
        if (setting instanceof BindSetting bindSetting) {
            JsonObject bindJson = new JsonObject();
            bindJson.addProperty("key", bindSetting.getKey());
            bindJson.addProperty("type", bindSetting.getType());
            return bindJson;
        }
        if (setting instanceof TextSetting textSetting) {
            return new JsonPrimitive(textSetting.getText() != null ? textSetting.getText() : "");
        }
        if (setting instanceof SelectSetting selectSetting) {
            return new JsonPrimitive(selectSetting.getSelected());
        }
        if (setting instanceof ColorSetting colorSetting) {
            JsonObject colorJson = new JsonObject();
            colorJson.addProperty("hue", colorSetting.getHue());
            colorJson.addProperty("saturation", colorSetting.getSaturation());
            colorJson.addProperty("brightness", colorSetting.getBrightness());
            colorJson.addProperty("alpha", colorSetting.getAlpha());
            return colorJson;
        }
        if (setting instanceof MultiSelectSetting multiSetting) {
            JsonArray array = new JsonArray();
            for (String value : multiSetting.getSelected()) {
                array.add(value);
            }
            return array;
        }
        if (setting instanceof GroupSetting groupSetting) {
            JsonObject groupJson = new JsonObject();
            groupJson.addProperty("value", groupSetting.isValue());
            JsonObject subSettingsJson = new JsonObject();
            for (Setting subSetting : groupSetting.getSubSettings()) {
                JsonElement element = serializeSetting(subSetting);
                if (element != null) {
                    subSettingsJson.add(subSetting.getName(), element);
                }
            }
            groupJson.add("subSettings", subSettingsJson);
            return groupJson;
        }
        return null;
    }

    public void deserialize(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement theme = root.get("theme");
            ThemeColors.select(theme != null && theme.isJsonPrimitive() && theme.getAsJsonPrimitive().isString()
                    ? ClientTheme.fromName(theme.getAsString()) : ClientTheme.MIDNIGHT);

            try {
                RotationBuilder.Profile active = root.has("customRotation") && root.get("customRotation").isJsonObject()
                        ? GSON.fromJson(root.get("customRotation"), RotationBuilder.Profile.class)
                        : new RotationBuilder.Profile();
                java.lang.reflect.Type presetType = new com.google.gson.reflect.TypeToken<java.util.Map<String, RotationBuilder.Profile>>() {}.getType();
                java.util.Map<String, RotationBuilder.Profile> library = root.has("customRotationPresets")
                        && root.get("customRotationPresets").isJsonObject()
                        ? GSON.fromJson(root.get("customRotationPresets"), presetType)
                        : java.util.Map.of();
                String selected = root.has("customRotationSelected") && root.get("customRotationSelected").isJsonPrimitive()
                        ? root.get("customRotationSelected").getAsString() : "";
                RotationBuilder.INSTANCE.loadEmbeddedLibrary(active, library, selected);
            } catch (RuntimeException ignored) {
                RotationBuilder.INSTANCE.loadEmbeddedLibrary(new RotationBuilder.Profile(), java.util.Map.of(), "");
            }

            if (root.has("modules")) {
                JsonObject modulesJson = root.getAsJsonObject("modules");
                ModuleRepository repository = getModuleRepository();
                if (repository != null) {
                    for (ModuleStructure module : repository.modules()) {
                        module.setKey(GLFW.GLFW_KEY_UNKNOWN);
                        module.setType(1);
                        for (Setting setting : module.settings()) {
                            resetBinding(setting);
                        }
                    }
                    for (ModuleStructure module : repository.modules()) {
                        JsonElement saved = modulesJson.get(module.getName());
                        if (saved != null && saved.isJsonObject()) {
                            deserializeModule(module, saved.getAsJsonObject());
                        }
                    }
                }
            }

            Aura aura = Aura.getInstance();
            if (aura != null) aura.refreshCustomRotationSettings();
        } catch (JsonSyntaxException e) {
            Logger.error("AutoConfiguration: JSON syntax error!");
        }
    }

    private void resetBinding(Setting setting) {
        if (setting instanceof BindSetting bindSetting) {
            bindSetting.setKey(GLFW.GLFW_KEY_UNKNOWN);
            bindSetting.setType(1);
        } else if (setting instanceof GroupSetting groupSetting) {
            for (Setting subSetting : groupSetting.getSubSettings()) {
                resetBinding(subSetting);
            }
        }
    }

    private void deserializeModule(ModuleStructure module, JsonObject moduleJson) {
        if (moduleJson.has("key")) {
            module.setKey(moduleJson.get("key").getAsInt());
        }
        if (moduleJson.has("type")) {
            module.setType(moduleJson.get("type").getAsInt());
        }
        if (moduleJson.has("favorite")) {
            module.setFavorite(moduleJson.get("favorite").getAsBoolean());
        }
        if (moduleJson.has("settings")) {
            JsonObject settingsJson = moduleJson.getAsJsonObject("settings");
            for (Setting setting : module.settings()) {
                JsonElement saved = LegacySettingNames.find(settingsJson, setting.getName());
                if (saved != null) {
                    deserializeSetting(setting, saved);
                }
            }
        }
        if (moduleJson.has("enabled")) {
            boolean enabled = moduleJson.get("enabled").getAsBoolean();
            if (module.isState() != enabled) {
                module.setState(enabled);
            }
        }
    }

    private void deserializeSetting(Setting setting, JsonElement element) {
        try {
            if (setting instanceof BooleanSetting boolSetting) {
                boolSetting.setValue(element.getAsBoolean());
            } else if (setting instanceof SliderSettings sliderSetting) {
                sliderSetting.setValue((float) element.getAsDouble());
            } else if (setting instanceof BindSetting bindSetting) {
                if (element.isJsonObject()) {
                    JsonObject bindJson = element.getAsJsonObject();
                    if (bindJson.has("key")) {
                        bindSetting.setKey(bindJson.get("key").getAsInt());
                    }
                    if (bindJson.has("type")) {
                        bindSetting.setType(bindJson.get("type").getAsInt());
                    }
                } else {
                    bindSetting.setKey(element.getAsInt());
                }
            } else if (setting instanceof TextSetting textSetting) {
                textSetting.setText(element.getAsString());
            } else if (setting instanceof SelectSetting selectSetting) {
                String value = element.getAsString();
                String translated = LegacySettingNames.translate(value);
                selectSetting.setSelected(selectSetting.contains(translated) ? translated : value);
            } else if (setting instanceof ColorSetting colorSetting) {
                if (element.isJsonObject()) {
                    JsonObject colorJson = element.getAsJsonObject();
                    if (colorJson.has("hue")) {
                        colorSetting.setHue(colorJson.get("hue").getAsFloat());
                    }
                    if (colorJson.has("saturation")) {
                        colorSetting.setSaturation(colorJson.get("saturation").getAsFloat());
                    }
                    if (colorJson.has("brightness")) {
                        colorSetting.setBrightness(colorJson.get("brightness").getAsFloat());
                    }
                    if (colorJson.has("alpha")) {
                        colorSetting.setAlpha(colorJson.get("alpha").getAsFloat());
                    }
                } else {
                    colorSetting.setColor(element.getAsInt());
                }
            } else if (setting instanceof MultiSelectSetting multiSetting) {
                if (element.isJsonArray()) {
                    JsonArray array = element.getAsJsonArray();
                    List<String> selected = new ArrayList<>();
                    for (JsonElement e : array) {
                        String value = e.getAsString();
                        String translated = LegacySettingNames.translate(value);
                        selected.add(multiSetting.getList().contains(translated) ? translated : value);
                    }
                    multiSetting.setSelected(selected);
                }
            } else if (setting instanceof GroupSetting groupSetting) {
                if (element.isJsonObject()) {
                    JsonObject groupJson = element.getAsJsonObject();
                    if (groupJson.has("value")) {
                        groupSetting.setValue(groupJson.get("value").getAsBoolean());
                    }
                    if (groupJson.has("subSettings")) {
                        JsonObject subSettingsJson = groupJson.getAsJsonObject("subSettings");
                        for (Setting subSetting : groupSetting.getSubSettings()) {
                            JsonElement saved = LegacySettingNames.find(subSettingsJson, subSetting.getName());
                            if (saved != null) {
                                deserializeSetting(subSetting, saved);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private ModuleRepository getModuleRepository() {
        Initialization instance = Initialization.getInstance();
        if (instance != null && instance.getManager() != null) {
            return instance.getManager().getModuleRepository();
        }
        return null;
    }
}