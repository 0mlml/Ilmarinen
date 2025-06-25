package dev.mlml.command;

import net.dv8tion.jda.api.Permission;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * CommandInfo is an annotation used to provide metadata for commands in the system.
 * It includes keywords, name, description, permissions, cooldown, and category.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandInfo {
    String[] keywords();

    String name();

    String description() default "no description";

    Permission[] permissions() default {Permission.MESSAGE_SEND};

    /**
     * Extended permissions are additional permissions that can be used to control access to commands.
     * These are not standard JDA permissions but are defined by the application.
     * See: {@link ExtendedPermission}
     */
    ExtendedPermission[] extendedPermissions() default {};

    int cooldown() default 0;

    Category category() default Category.Miscellaneous;

    enum Category {
        Util, Fun, Economy, Moderation, Miscellaneous;
    }

    enum ExtendedPermission {
        BOT_ADMIN;
    }
}
