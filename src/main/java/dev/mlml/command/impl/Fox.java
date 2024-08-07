package dev.mlml.command.impl;

import dev.mlml.Utils;
import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.utils.IOUtil;

import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

@CommandInfo(
        keywords = {"fox", "foxie"},
        name = "Fox",
        description = "Get a fox media",
        category = CommandInfo.Category.Fun,
        cooldown = 5
)
public class Fox extends Command {
    private static final String FOX_API_URL = "https://randomfox.ca/floof/";

    @Override
    public void execute(Context ctx) {
        final DataObject response = Utils.sendGetRequest(FOX_API_URL);

        if (Objects.isNull(response)) {
            ctx.fail("Failed to get fox media");
            return;
        }

        String mediaURl = response.get("image").toString();

        try {
            URL url = new URL(mediaURl);
            String fileName = url.getFile();

            try (InputStream in = url.openStream()) {
                byte[] fileData = IOUtil.readFully(in);
                FileUpload fileUpload = FileUpload.fromData(fileData, fileName);
                ctx.getMessage().replyFiles(fileUpload).queue();
            }
        } catch (Exception e) {
            ctx.fail("Failed to get fox media");
        }
    }
}
