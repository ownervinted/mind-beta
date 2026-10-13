package mind.beta.events.api;

import mind.beta.events.api.types.Priority;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {
    byte value() default Priority.MEDIUM;
}