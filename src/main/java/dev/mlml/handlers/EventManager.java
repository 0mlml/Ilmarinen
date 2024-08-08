package dev.mlml.handlers;

import dev.mlml.command.CommandRegistry;
import dev.mlml.command.impl.Crash;
import dev.mlml.command.impl.CrossyRoad;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class EventManager extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(EventManager.class);

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        logger.info("[{}] {}: {}", event.getChannel(), event.getAuthor(), event.getMessage().getContentRaw());
        if (!event.getMessage().getAttachments().isEmpty()) {
            logger.info("Attachments: {}", event.getMessage().getAttachments());
        }
        CommandRegistry.executeCommand(event.getMessage());
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        logger.info("[{}] Message deleted", event.getChannel());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getButton().getId();
        logger.debug("Button interaction: {}", buttonId);
        if (Objects.isNull(buttonId)) {
            return;
        }
        if (buttonId.startsWith("crash")) {
            Crash.handleCrashLeaveButton(event);
        }
        if (buttonId.startsWith("crossy_road")) {
            CrossyRoad.handleCrossButton(event);
        }
    }
}
