package dev.mlml.systems.economy;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class EconomySystem {
    @Getter
    @Setter
    private static EconGlobal econGlobal = new EconGlobal();
    @Getter
    private static final Map<String, EconGuild> guilds = new HashMap<>();
    @Getter
    private static final Map<String, EconUser> users = new HashMap<>();

    public static EconGuild getGuild(String id) {
        if (!guilds.containsKey(id)) {
            guilds.put(id, new EconGuild(id));
        }

        return guilds.get(id);
    }

    public static EconUser getUser(String id) {
        if (!users.containsKey(id)) {
            users.put(id, new EconUser(id));
        }

        return users.get(id);
    }

    public static void putUser(String id, EconUser user) {
        users.put(id, user);
    }

    public static void putGuild(String id, EconGuild guild) {
        guilds.put(id, guild);
    }
}
