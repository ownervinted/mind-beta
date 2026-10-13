package mind.beta.events.impl;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.math.Vec3d;
import mind.beta.events.api.events.Event;

@Data
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MoveEvent implements Event {
    Vec3d movement;
}