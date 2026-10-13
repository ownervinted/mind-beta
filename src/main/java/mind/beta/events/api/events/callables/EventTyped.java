package mind.beta.events.api.events.callables;

import mind.beta.events.api.events.Event;
import mind.beta.events.api.events.Typed;

public abstract class EventTyped implements Event, Typed {

    private final byte type;

    protected EventTyped(byte eventType) {
        type = eventType;
    }

    @Override
    public byte getType() {
        return type;
    }

}