package org.praytic.discord.statsbot.client;

import com.mewna.catnip.Catnip;
import com.mewna.catnip.entity.channel.Channel;
import com.mewna.catnip.entity.channel.TextChannel;
import com.mewna.catnip.rest.ResponseException;
import com.mewna.catnip.shard.DiscordEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Slf4j
@Component
public class DiscordClient {

    private final Catnip catnip;
    private final DatastoreClient datastoreClient;

    public void initialLoad(String guild) {
        catnip.rest().guild().getGuild(guild)
                .subscribe(guild1 -> log.info("Updating guild {}:{}", guild1.name(), guild1.id()));
        catnip.rest().guild().getGuildChannels(guild)
                .filter(Channel::isText)
                .subscribe(channel -> {
                    try {
                        log.info("Updating channel {}:{}", channel.name(), channel.id());
                        TextChannel textChannel = channel.asTextChannel();
                        final AtomicInteger counter = new AtomicInteger(0);

                        textChannel.fetchMessages().limit(Integer.MAX_VALUE).fetchWhile(msg -> {
                            msg.reactions().forEach(reaction -> datastoreClient.uploadEntity(reaction, msg));
                            msg.mentionedUsers().forEach(mention -> datastoreClient.uploadEntity(mention, msg));
                            msg.attachments().forEach(attachment -> datastoreClient.uploadEntity(attachment, msg));
                            datastoreClient.uploadEntity(msg, channel.guildId());

                            int cur = counter.incrementAndGet();
                            if (cur % 1000 == 0) {
                                log.info("Messages processed for guild {}:{} in channel {}:{} - {}",
                                        channel.guild().name(), channel.guildId(), channel.name(), channel.id(), cur);
                            }
                            return true;
                        }).subscribe();
                        log.info("Total messages processed for guild {}:{} in channel {}:{} - {}",
                                channel.guild().name(), channel.guildId(), channel.name(), channel.id(), counter.get());
                    } catch (ResponseException e) {
                        log.warn("Unable to fetch channel {}:{}. Error code {}. Reason: {}",
                                channel.name(), channel.id(), e.statusCode(), e.statusMessage());
                    }
                });
    }
}
