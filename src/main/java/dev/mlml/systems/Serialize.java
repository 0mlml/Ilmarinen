package dev.mlml.systems;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Serialize is an annotation used to mark classes that should be serialized.
 * This is typically used in systems that require data persistence, such as databases or file storage.
 * This annotation should only be used on primitive types, Strings, or classes that are serializable.
 * Otherwise, it may lead to unexpected behavior or errors during deserialization.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Serialize {
}
