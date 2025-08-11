package dev.mlml.systems.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.mlml.command.Context;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import java.util.HashMap;
import java.util.Map;

public class MusicSystem {

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    public MusicSystem() {
        this.playerManager = new DefaultAudioPlayerManager();
        this.musicManagers = new HashMap<>();
        playerManager.registerSourceManager(new YoutubeAudioSourceManager());
    }

    public synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = guild.getIdLong();
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    public void loadAndPlay(Context ctx, AudioChannel voiceChannel, final String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(ctx.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
                                          @Override
                                          public void trackLoaded(AudioTrack track) {
                                              play(ctx.getGuild(), musicManager, track, voiceChannel);
                                          }

                                          @Override
                                          public void playlistLoaded(AudioPlaylist playlist) {
                                              AudioTrack firstTrack = playlist.getTracks()
                                                      .get(0);
                                              play(ctx.getGuild(), musicManager, firstTrack, voiceChannel);
                                          }

                                          @Override
                                          public void noMatches() {
                                              ctx.fail("Cannot find anything by " + trackUrl);
                                          }

                                          @Override
                                          public void loadFailed(FriendlyException exception) {
                                              ctx.fail("Could not play track: " + exception.getMessage());
                                              exception.printStackTrace();
                                          }
                                      }
        );
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track, AudioChannel voiceChannel) {
        guild.getAudioManager().openAudioConnection(voiceChannel);
        musicManager.getScheduler().queue(track);
    }

    public void leave(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild);
        musicManager.getScheduler().stop();
        guild.getAudioManager().closeAudioConnection();
        musicManagers.remove(guild.getIdLong());
    }
}