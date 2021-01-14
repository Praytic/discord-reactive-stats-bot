package org.praytic.discord.statsbot.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class ChannelStats {
    private String channelName;
    private int messagesCount;
    private double messagesPerDay;
    private List<UserStats> topUsersByTotalMessages;
}
