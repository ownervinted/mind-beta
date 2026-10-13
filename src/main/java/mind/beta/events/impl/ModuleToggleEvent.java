package mind.beta.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mind.beta.events.api.events.Event;
import mind.beta.modules.module.ModuleStructure;

@Getter
@AllArgsConstructor
public class ModuleToggleEvent implements Event {
    private final ModuleStructure module;
    private final boolean enabled;
}