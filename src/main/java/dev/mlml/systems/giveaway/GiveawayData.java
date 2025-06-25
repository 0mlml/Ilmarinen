package dev.mlml.systems.giveaway;

import dev.mlml.systems.Serialize;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a giveaway in the giveaway system.
 * This class is used for the {@link GiveawayIO} system to manage giveaways.
 */
@Data
public class GiveawayData {
    @Serialize
    private String messageId;
    @Serialize
    private String guildId;
    @Serialize
    private String channelId;
    @Serialize
    private String hostId;
    @Serialize
    private String prize;
    @Serialize
    private int winners;
    @Serialize
    private long endTimeEpoch;
    @Serialize
    private String participants;
    @Serialize
    private boolean active;

    public GiveawayData(String messageId) {
        this.messageId = messageId;
        this.participants = "";
        this.active = true;
    }

    /**
     * Returns a set of participant IDs.
     * If no participants are set, returns an empty set.
     *
     * @return Set of participant IDs.
     */
    public Set<String> getParticipantSet() {
        if (participants == null || participants.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(participants.split(",")));
    }

    /**
     * Sets the participants from a set of user IDs.
     * If the set is null or empty, participants will be set to an empty string.
     *
     * @param participantSet Set of user IDs to set as participants.
     */
    public void setParticipantSet(Set<String> participantSet) {
        if (participantSet == null || participantSet.isEmpty()) {
            this.participants = "";
        } else {
            this.participants = String.join(",", participantSet);
        }
    }

    /**
     * Adds a participant to the giveaway.
     * If the user ID is already a participant, it will not be added again.
     *
     * @param userId The user ID to add as a participant.
     */
    public void addParticipant(String userId) {
        Set<String> current = getParticipantSet();
        current.add(userId);
        setParticipantSet(current);
    }

    /**
     * Removes a participant from the giveaway.
     * If the user ID is not a participant, it will not affect the participant set.
     *
     * @param userId The user ID to remove from participants.
     */
    public void removeParticipant(String userId) {
        Set<String> current = getParticipantSet();
        current.remove(userId);
        setParticipantSet(current);
    }

    /**
     * Checks if a user is a participant in the giveaway.
     *
     * @param userId The user ID to check.
     * @return true if the user is a participant, false otherwise.
     */
    public boolean hasParticipant(String userId) {
        return getParticipantSet().contains(userId);
    }

    /**
     * Gets the end time of the giveaway as a LocalDateTime.
     * The end time is stored as an epoch second.
     *
     * @return The end time as LocalDateTime.
     */
    public LocalDateTime getEndTime() {
        return LocalDateTime.ofEpochSecond(endTimeEpoch, 0, ZoneOffset.UTC);
    }

    /**
     * Sets the end time of the giveaway using a LocalDateTime.
     * The end time will be converted to epoch seconds for storage.
     *
     * @param endTime The end time to set as LocalDateTime.
     */
    public void setEndTime(LocalDateTime endTime) {
        this.endTimeEpoch = endTime.toEpochSecond(ZoneOffset.UTC);
    }
}