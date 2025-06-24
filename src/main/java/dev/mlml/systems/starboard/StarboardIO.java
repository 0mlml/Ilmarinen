package dev.mlml.systems.starboard;

import dev.mlml.systems.IO;

public class StarboardIO extends IO {
    public StarboardIO() {
        super("Starboard");
    }

    @Override
    protected void registerDataTypes() {
        registerCollectionDataType("GUILD", StarboardGuild.class, StarboardSystem::getGuilds);

    }
}
