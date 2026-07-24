package com.example.foliafunfacts;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;

record PluginSettings(
        boolean scheduleEnabled,
        long initialDelayTicks,
        boolean randomInterval,
        long intervalTicks,
        long randomIntervalMinTicks,
        long randomIntervalMaxTicks,
        boolean requireOnlinePlayers,
        boolean joinEnabled,
        long joinDelayTicks,
        FactOrder order,
        String messageColor,
        String messageFormat,
        Map<String, String> messages,
        List<String> facts
) {
    static PluginSettings from(FileConfiguration config) {
        long initialDelay = Math.max(1L, config.getLong("schedule.initial-delay-seconds", 60L));
        long configuredInterval = config.getLong("schedule.interval-seconds", 300L);
        boolean randomInterval = configuredInterval == -1L;
        long interval = Math.max(1L, configuredInterval);
        long randomMin = Math.max(1L,
                config.getLong("schedule.random-interval-min-seconds", 60L));
        long randomMax = Math.max(randomMin,
                config.getLong("schedule.random-interval-max-seconds", 600L));
        long joinDelay = Math.max(0L, config.getLong("on-join.delay-seconds", 5L));
        List<String> facts = config.getStringList("facts").stream()
                .map(String::trim)
                .filter(fact -> !fact.isEmpty())
                .toList();

        return new PluginSettings(
                config.getBoolean("schedule.enabled", true),
                secondsToTicks(initialDelay),
                randomInterval,
                secondsToTicks(interval),
                secondsToTicks(randomMin),
                secondsToTicks(randomMax),
                config.getBoolean("schedule.require-online-players", true),
                config.getBoolean("on-join.enabled", false),
                secondsToTicks(joinDelay),
                FactOrder.parse(config.getString("order")),
                config.getString("message-color", "RANDOM"),
                config.getString("message-format",
                        "<bold>Fun Fact:</bold> <fact>"),
                Map.of(
                        "reloaded", config.getString("messages.reloaded",
                                "<green>FoliaFunFacts configuration reloaded.</green>"),
                        "sent", config.getString("messages.sent",
                                "<green>Sent a fun fact.</green>"),
                        "no-facts", config.getString("messages.no-facts",
                                "<red>No fun facts are configured.</red>"),
                        "no-permission", config.getString("messages.no-permission",
                                "<red>You do not have permission to do that.</red>"),
                        "usage", config.getString("messages.usage",
                                "<yellow>Usage: /funfacts &lt;now|reload|list&gt;</yellow>"),
                        "list-header", config.getString("messages.list-header",
                                "<gold>Configured fun facts (<count>):</gold>"),
                        "list-entry", config.getString("messages.list-entry",
                                "<gray><number>.</gray> <white><fact></white>")
                ),
                List.copyOf(facts)
        );
    }

    String message(String name) {
        return messages.get(name);
    }

    private static long secondsToTicks(long seconds) {
        if (seconds > Long.MAX_VALUE / 20L) {
            return Long.MAX_VALUE;
        }
        return seconds * 20L;
    }
}
