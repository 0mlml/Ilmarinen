package dev.mlml.command;

import dev.mlml.Ilmarinen;
import dev.mlml.Utils;
import net.dv8tion.jda.api.EmbedBuilder;

public class Replies {
    private static final int ERROR_COLOR = 0xFF5555;
    private static final int SUCCESS_COLOR = 0x55FF55;
    private static final int INFO_COLOR = 0x5555FF;

    private static final String ERROR_TITLE = ":x: Error";
    private static final String SUCCESS_TITLE = ":white_check_mark: Success";
    private static final String INFO_TITLE = ":information_source: Info";

    /**
     * Creates a base EmbedBuilder with the author's name and avatar.
     *
     * @param ctx the command context
     * @return a new EmbedBuilder instance
     */
    public static EmbedBuilder base(Context ctx) {
        EmbedBuilder eb = new EmbedBuilder();
        String version = Utils.getVersion();
        eb.setFooter("Ilmarinen v" + version, Ilmarinen.getJda().getSelfUser().getAvatarUrl());
        eb.setAuthor(ctx.getAuthor().getEffectiveName(), null, ctx.getAuthor().getAvatarUrl());
        return eb;
    }

    /**
     * Creates a failure EmbedBuilder with the specified message.
     *
     * @param ctx     the command context
     * @param message the failure message
     * @return a new EmbedBuilder instance with error styling
     */
    public static EmbedBuilder fail(Context ctx, String message) {
        EmbedBuilder eb = base(ctx);
        eb.setColor(ERROR_COLOR);
        eb.setTitle(ERROR_TITLE);
        eb.setDescription(message);
        return eb;
    }

    /**
     * Creates a success EmbedBuilder with the specified message.
     *
     * @param ctx     the command context
     * @param message the success message
     * @return a new EmbedBuilder instance with success styling
     */
    public static EmbedBuilder success(Context ctx, String message) {
        EmbedBuilder eb = base(ctx);
        eb.setColor(SUCCESS_COLOR);
        eb.setTitle(SUCCESS_TITLE);
        eb.setDescription(message);
        return eb;
    }

    /**
     * Creates an info EmbedBuilder with the specified message.
     *
     * @param ctx     the command context
     * @param message the info message
     * @return a new EmbedBuilder instance with info styling
     */
    public static EmbedBuilder info(Context ctx, String message) {
        EmbedBuilder eb = base(ctx);
        eb.setColor(INFO_COLOR);
        eb.setTitle(INFO_TITLE);
        eb.setDescription(message);
        return eb;
    }
}
