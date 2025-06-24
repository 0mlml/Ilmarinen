package dev.mlml.systems.economy;

import dev.mlml.systems.IO;

public class EconIO extends IO {

    public EconIO() {
        super("Economy");
    }

    @Override
    protected void registerDataTypes() {
        registerGlobalDataType("GLOBAL", EconGlobal.class, EconomySystem::getEconGlobal);
        registerCollectionDataType("USER", EconUser.class, EconomySystem::getUsers);
        registerCollectionDataType("GUILD", EconGuild.class, EconomySystem::getGuilds);
    }
}
