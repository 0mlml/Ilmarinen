package dev.mlml.systems.giveaway;

import dev.mlml.systems.IO;

public class GiveawayIO extends IO {
    public GiveawayIO() {
        super("Giveaway");
        GiveawaySystem.initializeScheduledGiveaways();
    }

    @Override
    protected void registerDataTypes() {
        registerCollectionDataType("GUILD", GiveawayGuild.class, GiveawaySystem::getGuilds);
        registerCollectionDataType("GIVEAWAY", GiveawayData.class, GiveawaySystem::getAllGiveaways);
    }
}