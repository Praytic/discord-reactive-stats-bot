package org.praytic.discord.statsbot;

import com.mewna.catnip.Catnip;
import com.mewna.catnip.entity.Snowflake;
import com.mewna.catnip.entity.channel.Channel;
import com.mewna.catnip.entity.channel.GuildChannel;
import com.mewna.catnip.entity.guild.Guild;
import lombok.RequiredArgsConstructor;
import org.praytic.discord.statsbot.client.DiscordClient;
import org.praytic.discord.statsbot.model.ChannelStats;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

@RequiredArgsConstructor
@RestController
@RequestMapping("/bot")
class DiscordController {

    private final Catnip catnip;
    private final DiscordClient discordClient;

    @GetMapping("/guilds")
    public Map<String, String> getGuilds() {
        return catnip
                .cache()
                .guilds()
                .stream()
                .collect(toMap(Snowflake::id, Guild::name));
    }

    @GetMapping("/channels")
    public Map<String, String> getChannels(@RequestParam("guild") String guild) {
        return catnip
                .cache()
                .guild(guild)
                .channels()
                .stream()
                .filter(Channel::isText)
                .map(Channel::asTextChannel)
                .collect(toMap(Snowflake::id, GuildChannel::name));
    }

    @PostMapping("/initial-load")
    public void initialLoad(@RequestParam(value = "guild", required = false) String guild) {
        discordClient.initialLoad(guild);
    }

    @GetMapping("/channels/{channel-id}/user-stats")
    public ChannelStats getChannelStats(@PathVariable("channel-id") String channel) {
        return discordClient.getChannelStats(channel);
    }
}