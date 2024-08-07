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
        keywords = {"dog", "doggo"},
        name = "Dog",
        description = "Get a dog media",
        category = CommandInfo.Category.Fun,
        cooldown = 5
)
public class Dog extends Command {
    private static final String DOG_API_URL = "https://random.dog/woof.json";

    @Override
    public void execute(Context ctx) {
        final DataObject response = Utils.sendGetRequest(DOG_API_URL);

        if (Objects.isNull(response)) {
            ctx.fail("Failed to get dog media");
            return;
        }

        String mediaURl = response.get("url").toString();

        try {
            URL url = new URL(mediaURl);
            String fileName = url.getFile();

            try (InputStream in = url.openStream()) {
                byte[] fileData = IOUtil.readFully(in);
                FileUpload fileUpload = FileUpload.fromData(fileData, fileName);
                ctx.getMessage().replyFiles(fileUpload).queue();
            }
        } catch (Exception e) {
            ctx.fail("Failed to get dog media");
        }
    }
}
