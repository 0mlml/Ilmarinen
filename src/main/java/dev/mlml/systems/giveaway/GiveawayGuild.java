package dev.mlml.systems.giveaway;

import dev.mlml.systems.Serialize;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a guild in the giveaway system, containing active giveaways.
 * This class is used for the {@link GiveawayIO} system to manage giveaways within a guild.
 */
@Data
public class GiveawayGuild {
    @Serialize
    private final String id;

    private Map<String, GiveawayData> activeGiveaways = new HashMap<>();

    public GiveawayGuild(String id) {
        this.id = id;
    }

    /**
     * Adds a giveaway to the guild.
     *
     * @param giveaway The giveaway to add.
     */
    public void addGiveaway(GiveawayData giveaway) {
        activeGiveaways.put(giveaway.getMessageId(), giveaway);
    }

    /**
     * Removes a giveaway from the guild by its message ID.
     *
     * @param messageId The message ID of the giveaway to remove.
     */
    public void removeGiveaway(String messageId) {
        activeGiveaways.remove(messageId);
    }

    /**
     * Retrieves a giveaway by its message ID.
     *
     * @param messageId The message ID of the giveaway to retrieve.
     * @return The GiveawayData object if found, null otherwise.
     */
    public GiveawayData getGiveaway(String messageId) {
        return activeGiveaways.get(messageId);
    }
}