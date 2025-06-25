package dev.mlml.systems.economy;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * The EconomySystem class manages the global economy state, including guilds and users.
 * It provides methods to retrieve or create guilds and users, ensuring that they are initialized when accessed.
 */
public class EconomySystem {
    @Getter
    @Setter
    private static EconGlobal econGlobal = new EconGlobal();
    @Getter
    private static final Map<String, EconGuild> guilds = new HashMap<>();
    @Getter
    private static final Map<String, EconUser> users = new HashMap<>();

    /**
     * Retrieves a guild by its ID, creating it if it does not exist.
     *
     * @param id The ID of the guild.
     * @return The EconGuild object associated with the given ID.
     */
    public static EconGuild getGuild(String id) {
        if (!guilds.containsKey(id)) {
            guilds.put(id, new EconGuild(id));
        }

        return guilds.get(id);
    }

    /**
     * Retrieves a user by their ID, creating it if it does not exist.
     *
     * @param id The ID of the user.
     * @return The EconUser object associated with the given ID.
     */
    public static EconUser getUser(String id) {
        if (!users.containsKey(id)) {
            users.put(id, new EconUser(id));
        }

        return users.get(id);
    }

    /**
     * Puts a user into the economy system, replacing any existing user with the same ID.
     *
     * @param id   The ID of the user.
     * @param user The EconUser object to be added or updated.
     */
    public static void putUser(String id, EconUser user) {
        users.put(id, user);
    }

    /**
     * Puts a guild into the economy system, replacing any existing guild with the same ID.
     *
     * @param id     The ID of the guild.
     * @param guild  The EconGuild object to be added or updated.
     */
    public static void putGuild(String id, EconGuild guild) {
        guilds.put(id, guild);
    }
}
