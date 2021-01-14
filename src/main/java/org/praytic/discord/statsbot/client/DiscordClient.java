package org.praytic.discord.statsbot.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.datastore.Entity;
import com.mewna.catnip.Catnip;
import com.mewna.catnip.entity.builder.EmbedBuilder;
import com.mewna.catnip.entity.channel.Channel;
import com.mewna.catnip.entity.channel.ChannelMention;
import com.mewna.catnip.entity.channel.TextChannel;
import com.mewna.catnip.rest.ResponseException;
import com.mewna.catnip.shard.DiscordEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praytic.discord.statsbot.model.ChannelStats;
import org.praytic.discord.statsbot.model.UserStats;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.*;

@Slf4j
@Component
public class DiscordClient {

    private final Catnip catnip;
    private final DatastoreClient datastoreClient;
    private final ObjectMapper objectMapper;

    public DiscordClient(Catnip catnip, DatastoreClient datastoreClient, ObjectMapper objectMapper) {
        this.catnip = catnip;
        this.datastoreClient = datastoreClient;
        this.objectMapper = objectMapper;
        addCommandHandler(catnip);
        addPerMessageLoad(catnip);
        addPerReactionLoad(catnip);
    }

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

    public ChannelStats getChannelStats(String channel) {
        List<Entity> channelMessages = datastoreClient.getChannelMessages(channel);
        String channelName = catnip.rest().channel().getChannelById(channel).blockingGet().asTextChannel().name();
        double messagesPerDayStats = channelMessages
                .stream()
                .collect(groupingBy(
                        entity -> entity.getTimestamp("timestamp").getSeconds() / 86400,
                        summarizingInt(entity -> 1)))
                .values()
                .stream()
                .mapToLong(IntSummaryStatistics::getSum)
                .average()
                .orElse(0.0);
        int messagesCount = channelMessages.size();
        List<UserStats> topUsersByTotalMessages = channelMessages
                .stream()
                .collect(groupingBy(
                        entity -> entity.getString("author"),
                        summarizingInt(entity -> 1)))
                .entrySet()
                .stream()
                .map(entry -> new UserStats(
                        catnip.rest().user().getUser(entry.getKey()).blockingGet().username(),
                        entry.getValue().getSum()))
                .sorted(Comparator.comparing(UserStats::getMessagesCount).reversed())
                .limit(10)
                .collect(toList());

        return new ChannelStats(
                channelName,
                messagesCount,
                messagesPerDayStats,
                topUsersByTotalMessages);
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
                .filter(msg -> msg.content().startsWith("!channelstats"))
                .subscribe(msg -> {
                    List<String> channelMentions = new ArrayList<>();
                    Matcher matcher = Pattern.compile("<#!?(\\d+)>").matcher(msg.content());
                    while (matcher.find()) {
                        channelMentions.add(matcher.group(1));
                    }

                    if (channelMentions.size() != 1) {
                        msg.channel().sendMessage(new EmbedBuilder()
                                .title("Invalid request")
                                .description("Please mention a single channel after command. Example: `!channelstats #general`")
                                .build());
                    } else {
                        String channelMention = channelMentions.get(0);
                        Channel channel = catnip.rest().channel().getChannelById(channelMention).blockingGet();
                        if (!channel.isText()) {
                            msg.channel().sendMessage(new EmbedBuilder()
                                    .title("Invalid request")
                                    .description("Please specify text channel. Other types of channels are not supported.`")
                                    .build());
                        } else {
                            ChannelStats channelStats = getChannelStats(channelMention);
                            msg.channel().sendMessage(new EmbedBuilder()
                                    .title("Channel stats")
                                    .description(String.format("```json\n%s\n```",
                                            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(channelStats)))
                                    .build());
                        }
                    }
                }, Throwable::printStackTrace);
    }
}
