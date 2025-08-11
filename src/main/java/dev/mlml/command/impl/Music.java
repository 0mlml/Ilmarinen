package dev.mlml.command.impl;

import dev.mlml.Ilmarinen;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.SubcommandCommand;
import dev.mlml.command.SubcommandContext;
import dev.mlml.command.argument.StringArgument;
import dev.mlml.command.argument.SubcommandArgument;
import dev.mlml.systems.music.GuildMusicManager;
import dev.mlml.systems.music.MusicSystem;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

@CommandInfo(
        name = "music",
        description = "Music commands",
        keywords = {"music"}
)
public class Music extends SubcommandCommand {

    private static final StringArgument URL_ARG = new StringArgument.Builder("url").description(
                    "YouTube URL or search query")
            .require()
            .get();

    public Music() {
        super(SubcommandArgument.builder("action")
                      .description("Music action to perform")
                      .require()
                      .addSubcommand("play", URL_ARG)
                      .addSubcommand("add", URL_ARG)
                      .addSubcommand("queue")
                      .addSubcommand("skip")
                      .addSubcommand("stop")
                      .addSubcommand("nowplaying")
                      .addSubcommand("np")
                      .get());
    }

    @Override
    public void executeSubcommand(SubcommandContext subCtx) {
        switch (subCtx.subcommand()) {
            case "play" -> handlePlay(subCtx);
            case "add" -> handleAdd(subCtx);
            case "queue" -> handleQueue(subCtx);
            case "skip" -> handleSkip(subCtx);
            case "stop" -> handleStop(subCtx);
            case "nowplaying", "np" -> handleNowPlaying(subCtx);
            default -> subCtx.reply("Unknown subcommand: " + subCtx.subcommand());
        }
    }

    private void handlePlay(SubcommandContext subCtx) {
        Member member = subCtx.baseContext().getMember();
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            subCtx.reply("You must be in a voice channel to use this command");
            return;
        }
        AudioChannel voiceChannel = voiceState.getChannel();
        String url = subCtx.getSubcommandArgument(URL_ARG).value();
        Ilmarinen.getMusicSystem().loadAndPlay(subCtx.baseContext(), voiceChannel, url);
        subCtx.baseContext().succeed("Playing: " + url);
    }

    private void handleAdd(SubcommandContext subCtx) {
        Member member = subCtx.baseContext().getMember();
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            subCtx.reply("You must be in a voice channel to use this command");
            return;
        }
        AudioChannel voiceChannel = voiceState.getChannel();
        String url = subCtx.getSubcommandArgument(URL_ARG).value();
        Ilmarinen.getMusicSystem().loadAndPlay(subCtx.baseContext(), voiceChannel, url);
        subCtx.baseContext().succeed("Added to queue: " + url);
    }

    private void handleQueue(SubcommandContext subCtx) {
        GuildMusicManager musicManager = Ilmarinen.getMusicSystem()
                                                  .getGuildAudioPlayer(subCtx.baseContext().getGuild());
        if (musicManager.getScheduler().getNowPlaying() == null) {
            subCtx.reply("No tracks in the queue.");
            return;
        }
        StringBuilder queueList = new StringBuilder();
        queueList.append("Now Playing: ")
                 .append(musicManager.getScheduler().getNowPlaying().getInfo().title)
                 .append("\n\n");
        queueList.append("Current Queue:\n");
        musicManager.getScheduler()
                    .getQueue()
                    .forEach(track -> queueList.append("- ").append(track.getInfo().title).append("\n"));
        subCtx.baseContext().inform(queueList.toString());
    }

    private void handleSkip(SubcommandContext subCtx) {
        GuildMusicManager musicManager = Ilmarinen.getMusicSystem()
                                                  .getGuildAudioPlayer(subCtx.baseContext().getGuild());
        if (musicManager.getScheduler().getNowPlaying() == null) {
            subCtx.reply("Not playing anything to skip.");
            return;
        }
        musicManager.getScheduler().nextTrack();
        subCtx.baseContext().succeed("Skipped to the next track.");
    }

    private void handleStop(SubcommandContext subCtx) {
        GuildMusicManager musicManager = Ilmarinen.getMusicSystem()
                                                  .getGuildAudioPlayer(subCtx.baseContext().getGuild());
        musicManager.getScheduler().stop();
        Ilmarinen.getMusicSystem().leave(subCtx.baseContext().getGuild());
        subCtx.baseContext().succeed("Stopped playback and cleared the queue.");
    }

    private void handleNowPlaying(SubcommandContext subCtx) {
        GuildMusicManager musicManager = Ilmarinen.getMusicSystem()
                                                  .getGuildAudioPlayer(subCtx.baseContext().getGuild());
        if (musicManager.getScheduler().getNowPlaying() == null) {
            subCtx.reply("Not playing anything.");
            return;
        }
        subCtx.baseContext().inform("Now playing: " + musicManager.getScheduler().getNowPlaying()
                .getInfo().title + " by " + musicManager.getScheduler().getNowPlaying()
                .getInfo().author);
    }
}