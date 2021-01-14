package org.praytic.discord.statsbot.client;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.mewna.catnip.entity.message.Message;
import com.mewna.catnip.entity.message.ReactionUpdate;
import com.mewna.catnip.entity.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Component
public class DatastoreClient {

    private final Datastore datastore;

    public void clearEntity(String kind, String guild, String channel) {
        String gql = String.format("SELECT * FROM `%s`", kind);
        if (guild != null || channel != null) {
            gql += " WHERE";
        }
        if (guild != null) {
            gql += String.format(" guild=\"%s\"", guild);
        }
        if (guild != null && channel != null) {
            gql += " AND";
        }
        if (channel != null) {
            gql += String.format(" channel=\"%s\"", channel);
        }
        Query<Entity> query = Query.newGqlQueryBuilder(Query.ResultType.ENTITY, gql)
                .setAllowLiteral(true).build();
        log.info("Running deletion query for entities: {}", gql);
        QueryResults<Entity> results = datastore.run(query);
        int count = 0;
        while (results.hasNext()) {
            Entity entity = results.next();
            datastore.delete(entity.getKey());
            count++;
            log.debug("Deleted entity with key [{}]", entity.getKey());
        }
        log.info("Done deleting entities for guild [{}] and channel [{}] - {} {}s deleted", guild, channel, count, kind);
    }

    public Entity getEntity(String kind, String key) {
        return datastore.get(datastore.newKeyFactory().setKind(kind).newKey(key));
    }

    public Timestamp getOldestTimestamp(String kind) {
        String gql = String.format("SELECT timestamp FROM `%s` ORDER BY timestamp ASC", kind);
        Query<Entity> query = Query.newGqlQueryBuilder(Query.ResultType.ENTITY, gql)
                .setAllowLiteral(true).build();
        QueryResults<Entity> queryResults = datastore.run(query);
        if (queryResults.hasNext()) {
            return queryResults.next().getTimestamp("timestamp");
        } else {
            return Timestamp.now();
        }
    }

    public void uploadEntity(Message.Reaction reaction, Message msg) {
        int rawKey = Objects.hash(reaction.emoji().name(), msg.id());
        Key reactionKey = datastore.newKeyFactory().setKind("reaction").newKey(rawKey);
        FullEntity reactionEntity = Entity.newBuilder(reactionKey)
                .set("emoji", reaction.emoji().name())
                .set("count", reaction.count())
                .set("message", msg.id())
                .build();
        datastore.put(reactionEntity);
    }

    public void uploadEntity(ReactionUpdate reaction, String msgId) {
        int rawKey = Objects.hash(reaction.emoji().name(), msgId);
        Key reactionKey = datastore.newKeyFactory().setKind("reaction").newKey(rawKey);
        FullEntity reactionEntity = Entity.newBuilder(reactionKey)
                .set("emoji", reaction.emoji().name())
                .set("count", 1)
                .set("message", msgId)
                .build();
        datastore.put(reactionEntity);
    }

    public void uploadEntity(User mentionedUser, Message msg) {
        int rawKey = Objects.hash(mentionedUser.id(), msg.id());
        Key mentionKey = datastore.newKeyFactory().setKind("mention").newKey(rawKey);
        FullEntity reactionEntity = Entity.newBuilder(mentionKey)
                .set("author", msg.author().id())
                .set("mentionedUser", mentionedUser.id())
                .set("message", msg.id())
                .build();
        datastore.put(reactionEntity);
    }

    public void uploadEntity(Message.Attachment attachment, Message msg) {
        Key mentionKey = datastore.newKeyFactory().setKind("attachment").newKey(attachment.id());
        FullEntity attachmentEntity = Entity.newBuilder(mentionKey)
                .set("author", msg.author().id())
                .set("fileName", attachment.fileName())
                .set("url", attachment.proxyUrl())
                .build();
        datastore.put(attachmentEntity);
    }

    public void uploadEntity(Message msg, @Nullable String guildId) {
        Key messageKey = datastore.newKeyFactory().setKind("message").newKey(msg.id());
        Entity.Builder msgEntityBuilder = Entity.newBuilder(messageKey)
                .set("content", com.google.cloud.datastore.Value.fromPb(com.google.datastore.v1.Value.newBuilder().setExcludeFromIndexes(true).setStringValue(msg.content()).build()))
                .set("channel", msg.channelId())
                .set("author", msg.author().id())
                .set("timestamp", Timestamp.parseTimestamp(msg.timestamp().toString()));
        if (msg.guildId() != null) {
            msgEntityBuilder.set("guild", msg.guildId());
        } else if (guildId != null) {
            msgEntityBuilder.set("guild", guildId);
        }
        datastore.put(msgEntityBuilder.build());
    }

    public List<Entity> getChannelMessages(String channel) {
        Query<Entity> query = Query.newGqlQueryBuilder(
                Query.ResultType.ENTITY,
                "SELECT * FROM `message` WHERE channel=@channel")
                .setBinding("channel", channel)
                .build();
        QueryResults<Entity> queryResults = datastore.run(query);
        List<Entity> entities = new ArrayList<>();
        while (queryResults.hasNext()) {
            Entity entity = queryResults.next();
            entities.add(entity);
        }
        return entities;
    }
}
