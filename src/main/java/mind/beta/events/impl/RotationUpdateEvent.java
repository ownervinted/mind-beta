package mind.beta.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mind.beta.events.api.events.Event;

@Getter
@AllArgsConstructor
public class RotationUpdateEvent implements Event {
    byte type;
}
