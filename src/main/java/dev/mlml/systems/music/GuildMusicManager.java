package dev.mlml.systems.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import lombok.Data;
import lombok.Getter;

@Data
public class GuildMusicManager {
    private final AudioPlayerManager manager;
    private final AudioPlayer player;
    private final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;

    public GuildMusicManager(AudioPlayerManager manager) {
        this.manager = manager;
        player = manager.createPlayer();
        scheduler = new TrackScheduler(player);
        player.addListener(scheduler);
        sendHandler = new AudioPlayerSendHandler(player);
    }
}
