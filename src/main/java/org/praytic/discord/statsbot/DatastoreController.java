package org.praytic.discord.statsbot;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import lombok.RequiredArgsConstructor;
import org.praytic.discord.statsbot.client.DatastoreClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/datastore")
class DatastoreController {

    private final DatastoreClient datastoreClient;

    @GetMapping
    public Entity getObject(@RequestParam("kind") String kind,
                            @RequestParam("key") String key) {
        return datastoreClient.getEntity(kind, key);
    }

    @DeleteMapping
    public void deleteGuildEntity(@RequestParam("kind") String kind,
                                  @RequestParam(value = "guild", required = false) String guild,
                                  @RequestParam(value = "channel", required = false) String channel) {
        datastoreClient.clearEntity(kind, guild, channel);
    }

    @GetMapping("/oldest-timestamp")
    public Timestamp getOldestTimestamp(@RequestParam("kind") String kind) {
        return datastoreClient.getOldestTimestamp(kind);
    }
}