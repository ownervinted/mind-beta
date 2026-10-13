package mind.beta.modules.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import mind.beta.modules.module.setting.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Getter
public class SelectSetting extends Setting {
    private static final String description = "";
    @Setter
    private String selected = "";
    private final List<String> list = new ArrayList<>();

    public SelectSetting(String name) {
        super(name, description);
    }

    public synchronized SelectSetting value(String... values) {
        setValues(Arrays.asList(values), values.length > 0 ? values[0] : "");
        return this;
    }

    public synchronized SelectSetting setValues(Collection<String> values) {
        setValues(values, selected);
        return this;
    }

    public synchronized SelectSetting setValues(Collection<String> values, String fallback) {
        String previous = selected;
        list.clear();

        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank() || containsIgnoreCase(value)) {
                    continue;
                }
                list.add(value);
            }
        }

        if (containsIgnoreCase(previous)) {
            selected = canonical(previous);
        } else if (fallback != null && containsIgnoreCase(fallback)) {
            selected = canonical(fallback);
        } else {
            selected = list.isEmpty() ? "" : list.get(0);
        }
        return this;
    }

    public synchronized SelectSetting addValue(String value) {
        if (value != null && !value.isBlank() && !containsIgnoreCase(value)) {
            list.add(value);
            if (selected == null || selected.isBlank()) {
                selected = value;
            }
        }
        return this;
    }

    public synchronized SelectSetting removeValue(String value) {
        if (value == null) {
            return this;
        }
        String current = selected;
        list.removeIf(entry -> entry.equalsIgnoreCase(value));
        if (current != null && current.equalsIgnoreCase(value)) {
            selected = list.isEmpty() ? "" : list.get(0);
        }
        return this;
    }

    public synchronized boolean contains(String value) {
        return containsIgnoreCase(value);
    }

    public synchronized List<String> getList() {
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    public SelectSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public synchronized SelectSetting selected(String string) {
        if (containsIgnoreCase(string)) {
            this.selected = canonical(string);
        }
        return this;
    }

    public synchronized boolean isSelected(String name) {
        return selected != null && name != null && selected.equalsIgnoreCase(name);
    }

    private boolean containsIgnoreCase(String value) {
        if (value == null) {
            return false;
        }
        for (String entry : list) {
            if (entry.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String canonical(String value) {
        for (String entry : list) {
            if (entry.equalsIgnoreCase(value)) {
                return entry;
            }
        }
        return value;
    }
}
