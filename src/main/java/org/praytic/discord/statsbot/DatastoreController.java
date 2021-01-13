package org.praytic.discord.statsbot;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
class DatastoreController {

    private final Datastore datastore;

    @GetMapping
    public String indexPage() {
        return "";
    }

    @PostMapping
    public Entity getObject(@RequestParam("type") String type,
                            @RequestParam("key") String key) {
        return datastore.get(datastore.newKeyFactory().setKind(type).newKey(key));
    }
}