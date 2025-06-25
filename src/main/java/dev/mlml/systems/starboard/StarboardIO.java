package dev.mlml.systems.starboard;

import dev.mlml.systems.IO;

/**
 * StarboardIO is responsible for managing the Starboard system's data input/output operations.
 */
public class StarboardIO extends IO {
    public StarboardIO() {
        super("Starboard");
    }

    @Override
    protected void registerDataTypes() {
        registerCollectionDataType("GUILD", StarboardGuild.class, StarboardSystem::getGuilds);

    }
}
