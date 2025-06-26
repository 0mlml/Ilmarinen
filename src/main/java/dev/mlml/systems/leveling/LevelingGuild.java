package dev.mlml.systems.leveling;

import dev.mlml.systems.Serialize;
import lombok.Data;

import java.util.Arrays;

/**
 * Represents a guild's leveling configuration and settings.
 * Manages leveling channels, announcements, and guild-specific settings.
 */
@Data
public class LevelingGuild {
    @Serialize
    private final String id;

    @Serialize
    private String levelUpChannelId;

    @Serialize
    private boolean levelUpAnnouncements = true;

    @Serialize
    private boolean levelingEnabled = true;

    @Serialize
    private long messageCooldownMs = 60000; // 1 minute default

    @Serialize
    private long baseExperiencePerMessage = 15;

    @Serialize
    private long bonusExperienceMultiplier = 1;

    @Serialize
    private String levelUpMessage = "🎉 Congratulations {user}! You've reached level {level}!";

    @Serialize
    private String excludedChannels = "";

    @Serialize
    private String excludedRoles = "";

    public LevelingGuild(String id) {
        this.id = id;
    }

    /**
     * Checks if leveling is enabled for this guild.
     * @return true if leveling is enabled, false otherwise
     */
    public boolean isLevelingEnabled() {
        return levelingEnabled;
    }

    /**
     * Enables or disables leveling for this guild.
     * @param enabled true to enable leveling, false to disable
     */
    public void setLevelingEnabled(boolean enabled) {
        this.levelingEnabled = enabled;
    }

    /**
     * Sets the channel where level-up announcements will be sent.
     * @param channelId the ID of the channel for announcements
     */
    public void setLevelUpChannel(String channelId) {
        this.levelUpChannelId = channelId;
    }

    /**
     * Gets the formatted level-up message with user and level placeholders replaced.
     * @param userMention the user mention string
     * @param level the new level
     * @return the formatted message
     */
    public String getFormattedLevelUpMessage(String userMention, int level) {
        return levelUpMessage
                .replace("{user}", userMention)
                .replace("{level}", String.valueOf(level));
    }

    /**
     * Gets the excluded channels as an array.
     * @return An array of excluded channel IDs.
     */
    public String[] getExcludedChannels() {
        if (excludedChannels == null || excludedChannels.isEmpty()) {
            return new String[0];
        }
        return excludedChannels.split(",");
    }

    /**
     * Gets the excluded roles as an array.
     * @return An array of excluded role IDs.
     */
    public String[] getExcludedRoles() {
        if (excludedRoles == null || excludedRoles.isEmpty()) {
            return new String[0];
        }
        return excludedRoles.split(",");
    }

    /**
     * Checks if a channel is excluded from leveling.
     * @param channelId the channel ID to check
     * @return true if the channel is excluded, false otherwise
     */
    public boolean isChannelExcluded(String channelId) {
        String[] channels = getExcludedChannels();
        for (String excludedChannel : channels) {
            if (excludedChannel.equals(channelId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a role is excluded from leveling.
     * @param roleId the role ID to check
     * @return true if the role is excluded, false otherwise
     */
    public boolean isRoleExcluded(String roleId) {
        String[] roles = getExcludedRoles();
        for (String excludedRole : roles) {
            if (excludedRole.equals(roleId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a channel to the excluded channels list.
     * @param channelId the channel ID to exclude
     */
    public void addExcludedChannel(String channelId) {
        String[] channels = getExcludedChannels();
        if (Arrays.stream(channels).anyMatch(id -> id.equals(channelId))) {
            return;
        }
        
        if (excludedChannels == null || excludedChannels.isEmpty()) {
            excludedChannels = channelId;
        } else {
            excludedChannels += "," + channelId;
        }
    }

    /**
     * Removes a channel from the excluded channels list.
     * @param channelId the channel ID to remove from exclusion
     */
    public void removeExcludedChannel(String channelId) {
        String[] channels = getExcludedChannels();
        excludedChannels = Arrays.stream(channels)
                .filter(id -> !id.equals(channelId))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b);
    }

    /**
     * Adds a role to the excluded roles list.
     * @param roleId the role ID to exclude
     */
    public void addExcludedRole(String roleId) {
        String[] roles = getExcludedRoles();
        if (Arrays.stream(roles).anyMatch(id -> id.equals(roleId))) {
            return;
        }
        
        if (excludedRoles == null || excludedRoles.isEmpty()) {
            excludedRoles = roleId;
        } else {
            excludedRoles += "," + roleId;
        }
    }

    /**
     * Removes a role from the excluded roles list.
     * @param roleId the role ID to remove from exclusion
     */
    public void removeExcludedRole(String roleId) {
        String[] roles = getExcludedRoles();
        excludedRoles = Arrays.stream(roles)
                .filter(id -> !id.equals(roleId))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b);
    }

    /**
     * Calculates the experience to award for a message based on guild settings.
     * @return the calculated experience amount
     */
    public long calculateExperienceForMessage() {
        return baseExperiencePerMessage * bonusExperienceMultiplier;
    }
} 