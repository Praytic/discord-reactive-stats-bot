package org.praytic.discord.statsbot.config;

import com.mewna.catnip.Catnip;
import com.mewna.catnip.shard.DiscordEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praytic.discord.statsbot.client.DatastoreClient;
import org.praytic.discord.statsbot.config.properties.BotProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class DiscordConfig {

    private final DatastoreClient datastoreClient;
    private final BotProperties botProperties;

    @Bean
    public Catnip catnip() {
        Catnip catnip = Catnip.catnip(botProperties.getToken());
        addCommandHandler(catnip);
        addPerMessageLoad(catnip);
        addPerReactionLoad(catnip);
        return catnip.connect();
    }

    private void addPerMessageLoad(Catnip catnip) {
        catnip.observable(DiscordEvent.MESSAGE_CREATE)
                .subscribe(msg -> {
                    log.info("New message {} from {}", msg.id(), msg.author());
                    datastoreClient.uploadEntity(msg, msg.guildId());
                    msg.mentionedUsers().forEach(mention -> datastoreClient.uploadEntity(mention, msg));
                });
    }

    private void addPerReactionLoad(Catnip catnip) {
        catnip.observable(DiscordEvent.MESSAGE_REACTION_ADD)
                .subscribe(reaction -> {
                    log.info("New reaction {} from {}", reaction.emoji().id(), reaction.user());
                    datastoreClient.uploadEntity(reaction, reaction.messageId());
                });
    }

    private void addCommandHandler(Catnip catnip) {
        catnip.observable(DiscordEvent.MESSAGE_CREATE)
                .filter(msg -> msg.content().equals("!ping"))
                .subscribe(msg -> {
                    msg.channel().sendMessage("pong!");
                }, Throwable::printStackTrace);
    }
}
