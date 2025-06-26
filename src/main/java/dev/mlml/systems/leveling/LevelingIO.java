package dev.mlml.systems.leveling;

import dev.mlml.systems.IO;

/**
 * LevelingIO is responsible for managing the Leveling system's data input/output operations.
 */
public class LevelingIO extends IO {

    public LevelingIO() {
        super("Leveling");
    }

    @Override
    protected void registerDataTypes() {
        registerGlobalDataType("GLOBAL", LevelingGlobal.class, LevelingSystem::getLevelingGlobal);
        registerCollectionDataType("USER", LevelingUser.class, LevelingSystem::getUsers);
        registerCollectionDataType("GUILD", LevelingGuild.class, LevelingSystem::getGuilds);
    }
} 