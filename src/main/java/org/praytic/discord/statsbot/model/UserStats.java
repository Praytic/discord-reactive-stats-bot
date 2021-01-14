package org.praytic.discord.statsbot.model;


import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class UserStats {
    private String userName;
    private long messagesCount;
}
