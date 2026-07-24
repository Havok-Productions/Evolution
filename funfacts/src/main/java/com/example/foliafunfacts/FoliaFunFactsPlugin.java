package com.example.foliafunfacts;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class FoliaFunFactsPlugin extends JavaPlugin implements Listener {
    private static final List<String> RANDOM_COLORS = List.of(
            "aqua", "green", "yellow", "gold", "light_purple", "blue", "red"
    );
    private static final Set<String> ALLOWED_COLORS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "dark_gray", "blue", "green",
            "aqua", "red", "light_purple", "yellow", "white"
    );
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final FactPicker picker = new FactPicker();
    private volatile PluginSettings settings;
    private volatile long scheduleGeneration;
    private ScheduledTask announcementTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        PluginCommand command = getCommand("funfacts");
        if (command == null) {
            throw new IllegalStateException("The funfacts command is missing from plugin.yml");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        scheduleAnnouncements();

        getLogger().info("FoliaFunFacts enabled with " + settings.facts().size() + " facts.");
    }

    @Override
    public void onDisable() {
        cancelAnnouncementTask();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        PluginSettings current = settings;
        if (!current.joinEnabled() || current.facts().isEmpty()) {
            return;
        }

        event.getPlayer().getScheduler().runDelayed(
                this,
                task -> sendFact(event.getPlayer()),
                null,
                Math.max(1L, current.joinDelayTicks())
        );
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length != 1) {
            sendConfiguredMessage(sender, "usage");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "now" -> handleNow(sender);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            default -> {
                sendConfiguredMessage(sender, "usage");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        addIfAllowed(options, sender, "funfacts.now", "now", prefix);
        addIfAllowed(options, sender, "funfacts.reload", "reload", prefix);
        addIfAllowed(options, sender, "funfacts.list", "list", prefix);
        return options;
    }

    private boolean handleNow(CommandSender sender) {
        if (!checkPermission(sender, "funfacts.now")) {
            return true;
        }
        if (settings.facts().isEmpty()) {
            sendConfiguredMessage(sender, "no-facts");
            return true;
        }
        getServer().getGlobalRegionScheduler().execute(this, this::broadcastNextFact);
        sendConfiguredMessage(sender, "sent");
        return true;
    }

    private synchronized boolean handleReload(CommandSender sender) {
        if (!checkPermission(sender, "funfacts.reload")) {
            return true;
        }
        reloadConfig();
        reloadSettings();
        scheduleAnnouncements();
        sendConfiguredMessage(sender, "reloaded");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!checkPermission(sender, "funfacts.list")) {
            return true;
        }

        List<String> facts = settings.facts();
        sendTemplate(sender, getConfig().getString("messages.list-header",
                "<gold>Configured fun facts (<count>):</gold>"),
                "<count>", Integer.toString(facts.size()));
        for (int i = 0; i < facts.size(); i++) {
            String template = getConfig().getString("messages.list-entry",
                    "<gray><number>.</gray> <white><fact></white>");
            sendTemplate(sender, template,
                    "<number>", Integer.toString(i + 1),
                    "<fact>", facts.get(i));
        }
        return true;
    }

    private void reloadSettings() {
        settings = PluginSettings.from(getConfig());
        picker.reset();
    }

    private synchronized void scheduleAnnouncements() {
        cancelAnnouncementTask();
        PluginSettings current = settings;
        if (!current.scheduleEnabled() || current.facts().isEmpty()) {
            return;
        }

        long generation = scheduleGeneration;
        if (current.randomInterval()) {
            scheduleRandomAnnouncement(current.initialDelayTicks(), generation);
            return;
        }

        announcementTask = getServer().getGlobalRegionScheduler().runAtFixedRate(
                this,
                task -> {
                    PluginSettings latest = settings;
                    if (!latest.requireOnlinePlayers() || !Bukkit.getOnlinePlayers().isEmpty()) {
                        broadcastNextFact();
                    }
                },
                current.initialDelayTicks(),
                current.intervalTicks()
        );
    }

    private synchronized void cancelAnnouncementTask() {
        scheduleGeneration++;
        ScheduledTask task = announcementTask;
        announcementTask = null;
        if (task != null) {
            task.cancel();
        }
    }

    private synchronized void scheduleRandomAnnouncement(long delayTicks, long generation) {
        if (generation != scheduleGeneration || !isEnabled()) {
            return;
        }
        announcementTask = getServer().getGlobalRegionScheduler().runDelayed(
                this,
                task -> {
                    if (generation != scheduleGeneration) {
                        return;
                    }
                    PluginSettings current = settings;
                    if (!current.requireOnlinePlayers() || !Bukkit.getOnlinePlayers().isEmpty()) {
                        broadcastNextFact();
                    }
                    scheduleRandomAnnouncement(randomIntervalTicks(current), generation);
                },
                delayTicks
        );
    }

    private long randomIntervalTicks(PluginSettings current) {
        long min = current.randomIntervalMinTicks();
        long max = current.randomIntervalMaxTicks();
        if (min == max) {
            return min;
        }
        if (max == Long.MAX_VALUE) {
            return ThreadLocalRandom.current().nextLong(min, max);
        }
        return ThreadLocalRandom.current().nextLong(min, max + 1L);
    }
    private void broadcastNextFact() {
        PluginSettings current = settings;
        String fact = picker.pick(current.facts(), current.order());
        if (fact == null) {
            return;
        }

        Component message = formatFact(current, fact);
        getServer().getConsoleSender().sendMessage(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().execute(this, () -> player.sendMessage(message), null, 1L);
        }
    }

    private void sendFact(Player player) {
        PluginSettings current = settings;
        String fact = picker.pick(current.facts(), current.order());
        if (fact != null) {
            player.sendMessage(formatFact(current, fact));
        }
    }

    private Component formatFact(PluginSettings current, String fact) {
        String color = resolveColor(current.messageColor());
        String message = current.messageFormat()
                .replace("<fact>", miniMessage.escapeTags(fact));
        return miniMessage.deserialize("<color:" + color + ">" + message + "</color>");
    }

    private String resolveColor(String configuredColor) {
        String normalized = configuredColor == null
                ? "random"
                : configuredColor.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("random")) {
            return RANDOM_COLORS.get(ThreadLocalRandom.current().nextInt(RANDOM_COLORS.size()));
        }
        if (ALLOWED_COLORS.contains(normalized) || normalized.matches("#[0-9a-f]{6}")) {
            return normalized;
        }
        return "white";
    }

    private boolean checkPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sendConfiguredMessage(sender, "no-permission");
        return false;
    }

    private void sendConfiguredMessage(CommandSender sender, String name) {
        sender.sendMessage(miniMessage.deserialize(settings.message(name)));
    }

    private void sendTemplate(CommandSender sender, String template, String... replacements) {
        String rendered = template;
        for (int i = 0; i < replacements.length; i += 2) {
            rendered = rendered.replace(replacements[i], miniMessage.escapeTags(replacements[i + 1]));
        }
        sender.sendMessage(miniMessage.deserialize(rendered));
    }

    private static void addIfAllowed(
            List<String> options,
            CommandSender sender,
            String permission,
            String option,
            String prefix
    ) {
        if (sender.hasPermission(permission) && option.startsWith(prefix)) {
            options.add(option);
        }
    }
}
