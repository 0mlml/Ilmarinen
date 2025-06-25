package dev.mlml.command.impl;

import dev.mlml.Utils;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.SubcommandCommand;
import dev.mlml.command.SubcommandContext;
import dev.mlml.command.argument.IntegerArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.command.argument.StringArgument;
import dev.mlml.command.argument.SubcommandArgument;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.awt.Color;
import java.util.*;
import java.util.stream.Collectors;

@CommandInfo(
        keywords = {"dog", "doggo", "puppy"},
        name = "Dog",
        description = "Get dog images and information using various filters",
        category = CommandInfo.Category.Fun,
        cooldown = 3
)
public class Dog extends SubcommandCommand {
    private static final String DOG_API_BASE = "https://dog.ceo/api";
    private static final int MAX_IMAGES = 10;

    private static final StringArgument BREED_ARG = new StringArgument.Builder("breed")
            .description("The dog breed name")
            .require()
            .get();

    private static final StringArgument SUB_BREED_ARG = new StringArgument.Builder("sub-breed")
            .description("The sub-breed name")
            .require()
            .get();

    private static final IntegerArgument COUNT_ARG = new IntegerArgument.Builder("count")
            .description("Number of images to get (1-10)")
            .require()
            .min(1)
            .max(MAX_IMAGES)
            .get();

    public Dog() {
        super(SubcommandArgument.builder("action")
                      .description("What you want to do")
                      .require()
                      .addSubcommand("random")
                      .addSubcommand("breeds")
                      .addSubcommand("breed", BREED_ARG)
                      .addSubcommand("sub-breeds", BREED_ARG)
                      .addSubcommand("sub-breed", BREED_ARG, SUB_BREED_ARG)
                      .addSubcommand("multiple", COUNT_ARG)
                      .addSubcommand("breed-multiple", BREED_ARG, COUNT_ARG)
                      .addSubcommand("sub-breed-multiple", BREED_ARG, SUB_BREED_ARG, COUNT_ARG)
                      .get());
    }

    @Override
    public void executeSubcommand(SubcommandContext subCtx) {
        switch (subCtx.subcommand()) {
            case "random" -> handleRandomImage(subCtx);
            case "breeds" -> handleListBreeds(subCtx);
            case "breed" -> handleBreedImage(subCtx);
            case "sub-breeds" -> handleListSubBreeds(subCtx);
            case "sub-breed" -> handleSubBreedImage(subCtx);
            case "multiple" -> handleMultipleImages(subCtx);
            case "breed-multiple" -> handleBreedMultipleImages(subCtx);
            case "sub-breed-multiple" -> handleSubBreedMultipleImages(subCtx);
            default -> subCtx.reply("Unknown subcommand: " + subCtx.subcommand());
        }
    }

    private void handleRandomImage(SubcommandContext subCtx) {
        DataObject response = Utils.sendGetRequest(DOG_API_BASE + "/breeds/image/random");

        if (response == null || !response.getString("status").equals("success")) {
            subCtx.reply("Failed to get random dog image");
            return;
        }

        String imageUrl = response.getString("message");
        sendImageEmbed(subCtx, imageUrl, "Random Dog", null);
    }

    private void handleListBreeds(SubcommandContext subCtx) {
        DataObject response = Utils.sendGetRequest(DOG_API_BASE + "/breeds/list/all");

        if (response == null || !response.getString("status").equals("success")) {
            subCtx.reply("Failed to get breed list");
            return;
        }

        DataObject breedsData = response.getObject("message");
        List<String> breeds = breedsData.keys().stream().sorted().collect(Collectors.toList());
        StringBuilder breedList = new StringBuilder();

        for (String breed : breeds) {
            breedList.append("**").append(capitalizeBreed(breed)).append("**");

            DataArray subBreeds = breedsData.getArray(breed);
            if (!subBreeds.isEmpty()) {
                breedList.append(" (");
                for (int i = 0; i < subBreeds.length(); i++) {
                    breedList.append(capitalizeBreed(subBreeds.getString(i)));
                    if (i < subBreeds.length() - 1) breedList.append(", ");
                }
                breedList.append(")");
            }
            breedList.append("\n");
        }

        String fullList = breedList.toString();
        if (fullList.length() > 2000) {
            String[] parts = splitMessage(fullList, 2000);
            for (int i = 0; i < parts.length; i++) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(i == 0 ? "Dog Breeds" : "Dog Breeds (continued)")
                        .setDescription(parts[i])
                        .setColor(Color.ORANGE);
                subCtx.baseContext().getMessage().replyEmbeds(embed.build()).queue();
            }
        } else {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Dog Breeds")
                    .setDescription(fullList)
                    .setColor(Color.ORANGE);
            subCtx.baseContext().getMessage().replyEmbeds(embed.build()).queue();
        }
    }

    private void handleBreedImage(SubcommandContext subCtx) {
        ParsedArgument<String> breedArg = subCtx.getSubcommandArgument(BREED_ARG);
        if (breedArg == null || breedArg.value() == null) {
            subCtx.reply("Please specify a breed name");
            return;
        }

        String breed = breedArg.value().toLowerCase();
        DataObject response = Utils.sendGetRequest(DOG_API_BASE + "/breed/" + breed + "/images/random");

        if (response == null || !response.getString("status").equals("success")) {
            subCtx.reply("Failed to get image for breed: " + breed + ". Make sure the breed name is correct.");
            return;
        }

        String imageUrl = response.getString("message");
        sendImageEmbed(subCtx, imageUrl, capitalizeBreed(breed), null);
    }

    private void handleListSubBreeds(SubcommandContext subCtx) {
        ParsedArgument<String> breedArg = subCtx.getSubcommandArgument(BREED_ARG);
        if (breedArg == null || breedArg.value() == null) {
            subCtx.reply("Please specify a breed name");
            return;
        }

        String breed = breedArg.value().toLowerCase();
        DataObject response = Utils.sendGetRequest(DOG_API_BASE + "/breed/" + breed + "/list");

        if (response == null || !response.getString("status").equals("success")) {
            subCtx.reply("Failed to get sub-breeds for: " + breed);
            return;
        }

        DataArray subBreeds = response.getArray("message");
        if (subBreeds.isEmpty()) {
            subCtx.reply("No sub-breeds found for: " + capitalizeBreed(breed));
            return;
        }

        StringBuilder subBreedList = new StringBuilder();
        for (int i = 0; i < subBreeds.length(); i++) {
            subBreedList.append("• ").append(capitalizeBreed(subBreeds.getString(i))).append("\n");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Sub-breeds of " + capitalizeBreed(breed))
                .setDescription(subBreedList.toString())
                .setColor(Color.BLUE);

        subCtx.baseContext().getMessage().replyEmbeds(embed.build()).queue();
    }

    private void handleSubBreedImage(SubcommandContext subCtx) {
        ParsedArgument<String> breedArg = subCtx.getSubcommandArgument(BREED_ARG);
        ParsedArgument<String> subBreedArg = subCtx.getSubcommandArgument(SUB_BREED_ARG);

        if (breedArg == null || breedArg.value() == null ||
                subBreedArg == null || subBreedArg.value() == null) {
            subCtx.reply("Please specify both breed and sub-breed names");
            return;
        }

        String breed = breedArg.value().toLowerCase();
        String subBreed = subBreedArg.value().toLowerCase();

        DataObject response = Utils.sendGetRequest(DOG_API_BASE + "/breed/" + breed + "/" + subBreed + "/images/random");

        if (response == null || !response.getString("status").equals("success")) {
            subCtx.reply("Failed to get image for " + breed + " " + subBreed + ". Make sure both names are correct.");
            return;
        }

        String imageUrl = response.getString("message");
        sendImageEmbed(subCtx, imageUrl, capitalizeBreed(breed) + " " + capitalizeBreed(subBreed), null);
    }

    private void handleMultipleImages(SubcommandContext subCtx) {
        ParsedArgument<Integer> countArg = subCtx.getSubcommandArgument(COUNT_ARG);
        int count = (countArg != null && countArg.value() != null) ? countArg.value() : 3;

        DataObject response = Utils.sendGetRequest(DOG_API_BASE + "/breeds/image/random/" + count);

        if (response == null || !response.getString("status").equals("success")) {
            subCtx.reply("Failed to get multiple dog images");
            return;
        }

        DataArray images = response.getArray("message");
        sendMultipleImages(subCtx, images, "Random Dogs (" + count + " images)");
    }

    private void handleBreedMultipleImages(SubcommandContext subCtx) {
        ParsedArgument<String> breedArg = subCtx.getSubcommandArgument(BREED_ARG);
        ParsedArgument<Integer> countArg = subCtx.getSubcommandArgument(COUNT_ARG);

        if (breedArg == null || breedArg.value() == null) {
            subCtx.reply("Please specify a breed name");
            return;
        }

        String breed = breedArg.value().toLowerCase();
        int count = (countArg != null && countArg.value() != null) ? countArg.value() : 3;

        DataObject response = Utils.sendGetRequest(DOG_API_BASE + "/breed/" + breed + "/images/random/" + count);

        if (response == null || !response.getString("status").equals("success")) {
            subCtx.reply("Failed to get multiple images for breed: " + breed);
            return;
        }

        DataArray images = response.getArray("message");
        sendMultipleImages(subCtx, images, capitalizeBreed(breed) + " Dogs (" + count + " images)");
    }

    private void handleSubBreedMultipleImages(SubcommandContext subCtx) {
        ParsedArgument<String> breedArg = subCtx.getSubcommandArgument(BREED_ARG);
        ParsedArgument<String> subBreedArg = subCtx.getSubcommandArgument(SUB_BREED_ARG);
        ParsedArgument<Integer> countArg = subCtx.getSubcommandArgument(COUNT_ARG);

        if (breedArg == null || breedArg.value() == null ||
                subBreedArg == null || subBreedArg.value() == null) {
            subCtx.reply("Please specify both breed and sub-breed names");
            return;
        }

        String breed = breedArg.value().toLowerCase();
        String subBreed = subBreedArg.value().toLowerCase();
        int count = (countArg != null && countArg.value() != null) ? countArg.value() : 3;

        DataObject response = Utils.sendGetRequest(DOG_API_BASE + "/breed/" + breed + "/" + subBreed + "/images/random/" + count);

        if (response == null || !response.getString("status").equals("success")) {
            subCtx.reply("Failed to get multiple images for " + breed + " " + subBreed);
            return;
        }

        DataArray images = response.getArray("message");
        sendMultipleImages(subCtx, images, capitalizeBreed(breed) + " " + capitalizeBreed(subBreed) + " Dogs (" + count + " images)");
    }

    private void sendImageEmbed(SubcommandContext subCtx, String imageUrl, String title, String description) {
        try {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(title)
                    .setImage(imageUrl)
                    .setColor(Color.ORANGE)
                    .setFooter("Powered by Dog CEO API");

            if (description != null) {
                embed.setDescription(description);
            }

            subCtx.baseContext().getMessage().replyEmbeds(embed.build()).queue();
        } catch (Exception e) {
            subCtx.reply("Failed to send dog image: " + e.getMessage());
        }
    }

    private void sendMultipleImages(SubcommandContext subCtx, DataArray images, String title) {
        if (images.isEmpty()) {
            subCtx.reply("No images found");
            return;
        }

        String firstImage = images.getString(0);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setImage(firstImage)
                .setColor(Color.ORANGE)
                .setFooter("Powered by Dog CEO API");

        subCtx.baseContext().getMessage().replyEmbeds(embed.build()).queue();

        for (int i = 1; i < images.length(); i++) {
            EmbedBuilder additionalEmbed = new EmbedBuilder()
                    .setImage(images.getString(i))
                    .setColor(Color.ORANGE);

            subCtx.baseContext().getMessage().getChannel().sendMessageEmbeds(additionalEmbed.build()).queue();
        }
    }

    private String capitalizeBreed(String breed) {
        if (breed == null || breed.isEmpty()) return breed;
        return Arrays.stream(breed.split("\\s+"))
                     .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                     .collect(Collectors.joining(" "));
    }

    private String[] splitMessage(String message, int maxLength) {
        if (message.length() <= maxLength) {
            return new String[]{message};
        }

        List<String> parts = new ArrayList<>();
        String[] lines = message.split("\n");
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (current.length() + line.length() + 1 > maxLength) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                }
            }
            current.append(line).append("\n");
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }
}