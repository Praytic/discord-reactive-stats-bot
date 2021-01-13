package org.praytic.discord.statsbot.config;

import com.google.api.client.util.Value;
import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.FullEntity;
import com.google.cloud.datastore.Key;
import com.mewna.catnip.Catnip;
import com.mewna.catnip.entity.channel.Channel;
import com.mewna.catnip.entity.channel.GuildChannel;
import com.mewna.catnip.entity.channel.TextChannel;
import com.mewna.catnip.entity.message.Message;
import com.mewna.catnip.entity.message.ReactionUpdate;
import com.mewna.catnip.entity.user.User;
import com.mewna.catnip.rest.ResponseException;
import com.mewna.catnip.shard.DiscordEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praytic.discord.statsbot.config.properties.BotProperties;
import org.praytic.discord.statsbot.config.properties.GoogleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class CatnipConfig {

    private final Datastore datastore;
    private final BotProperties botProperties;

    @Bean
    public Catnip catnip() {
        Catnip catnip = Catnip.catnip(botProperties.getToken());
        addInitialLoad(catnip);
        addCommandHandler(catnip);
        addPerMessageLoad(catnip);
        addPerReactionLoad(catnip);
        return catnip.connect();
    }

    private void addInitialLoad(Catnip catnip) {
        catnip.observable(DiscordEvent.GUILD_AVAILABLE)
                .subscribe(guild -> {
                    log.info("Updating guild {}:{}", guild.name(), guild.id());
                    for (GuildChannel channel : guild.channels().stream()
                            .filter(Channel::isText)
                            .collect(Collectors.toList())) {
                        try {
                            log.info("Updating channel {}:{}", channel.name(), channel.id());
                            TextChannel textChannel = channel.asTextChannel();
                            final AtomicInteger counter = new AtomicInteger(0);

                            textChannel.fetchMessages().limit(Integer.MAX_VALUE).fetchWhile(msg -> {
                                msg.reactions().forEach(reaction -> uploadEntity(reaction, msg));
                                msg.mentionedUsers().forEach(mention -> uploadEntity(mention, msg));
                                msg.attachments().forEach(attachment -> uploadEntity(attachment, msg));
                                uploadEntity(msg, guild.id());

                                int cur = counter.incrementAndGet();
                                if (cur % 1000 == 0) {
                                    log.info("Messages processed for guild {}:{} in channel {}:{} - {}",
                                            guild.name(), guild.id(), channel.name(), channel.id(), cur);
                                }
                                return true;
                            }).subscribe();
                            log.info("Total messages processed for guild {}:{} in channel {}:{} - {}",
                                    guild.name(), guild.id(), channel.name(), channel.id(), counter.get());
                        } catch (ResponseException e) {
                            log.warn("Unable to fetch channel {}:{}. Error code {}. Reason: {}",
                                    channel.name(), channel.id(), e.statusCode(), e.statusMessage());
                        }
                    }
                });
    }

    private void addPerMessageLoad(Catnip catnip) {
        catnip.observable(DiscordEvent.MESSAGE_CREATE)
                .subscribe(msg -> {
                    log.info("New message {} from {}", msg.id(), msg.author());
                    uploadEntity(msg, msg.guildId());
                    msg.mentionedUsers().forEach(mention -> uploadEntity(mention, msg));
                });
    }

    private void addPerReactionLoad(Catnip catnip) {
        catnip.observable(DiscordEvent.MESSAGE_REACTION_ADD)
                .subscribe(reaction -> {
                    log.info("New reaction {} from {}", reaction.emoji().id(), reaction.user());
                    uploadEntity(reaction, reaction.messageId());
                });
    }

    private void addCommandHandler(Catnip catnip) {
        catnip.observable(DiscordEvent.MESSAGE_CREATE)
                .filter(msg -> msg.content().equals("!ping"))
                .subscribe(msg -> {
                    msg.channel().sendMessage("pong!");
                }, Throwable::printStackTrace);
    }

    private void uploadEntity(Message.Reaction reaction, Message msg) {
        Key reactionKey = datastore.allocateId(datastore.newKeyFactory().setKind("reaction").newKey());
        FullEntity reactionEntity = Entity.newBuilder(reactionKey)
                .set("emoji", reaction.emoji().name())
                .set("count", reaction.count())
                .set("message", msg.id())
                .build();
        datastore.put(reactionEntity);
    }

    private void uploadEntity(ReactionUpdate reaction, String msgId) {
        Key reactionKey = datastore.allocateId(datastore.newKeyFactory().setKind("reaction").newKey());
        FullEntity reactionEntity = Entity.newBuilder(reactionKey)
                .set("emoji", reaction.emoji().name())
                .set("count", 1)
                .set("message", msgId)
                .build();
        datastore.put(reactionEntity);
    }

    private void uploadEntity(User mentionedUser, Message msg) {
        Key mentionKey = datastore.allocateId(datastore.newKeyFactory().setKind("mention").newKey());
        FullEntity reactionEntity = Entity.newBuilder(mentionKey)
                .set("author", msg.author().id())
                .set("mentionedUser", mentionedUser.id())
                .set("message", msg.id())
                .build();
        datastore.put(reactionEntity);
    }

    private void uploadEntity(Message.Attachment attachment, Message msg) {
        Key mentionKey = datastore.newKeyFactory().setKind("attachment").newKey(attachment.id());
        FullEntity attachmentEntity = Entity.newBuilder(mentionKey)
                .set("author", msg.author().id())
                .set("fileName", attachment.fileName())
                .set("url", attachment.proxyUrl())
                .build();
        datastore.put(attachmentEntity);
    }

    private void uploadEntity(Message msg, @Nullable String guildId) {
        Key messageKey = datastore.newKeyFactory().setKind("message").newKey(msg.id());
        Entity.Builder msgEntityBuilder = Entity.newBuilder(messageKey)
                .set("content", com.google.cloud.datastore.Value.fromPb(com.google.datastore.v1.Value.newBuilder().setExcludeFromIndexes(true).setStringValue(msg.content()).build()))
                .set("channel", msg.channelId())
                .set("author", msg.author().id())
                .set("timestamp", Timestamp.parseTimestamp(msg.timestamp().toString()));
        if (msg.guildId() != null) {
            msgEntityBuilder.set("guild", guildId);
        }
        datastore.put(msgEntityBuilder.build());
    }
}
