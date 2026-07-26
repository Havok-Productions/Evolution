package org.evolution.coreparts;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ResourceReporter {
    private final EvolutionPlugin plugin;
    private final ConcurrentMap<String, TaskStats> taskStats = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ModuleStats> moduleStats = new ConcurrentHashMap<>();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private final Deque<String> slowSamples = new ArrayDeque<>();
    private final String sessionStartedAt = Instant.now().toString();
    private volatile ResourceReporterConfig config;

    ResourceReporter(EvolutionPlugin plugin) {
        this.plugin = plugin;
        this.config = ResourceReporterConfig.load(plugin);
    }

    void resetForStartup(EvolutionPlugin plugin) {
        taskStats.clear();
        moduleStats.clear();
        synchronized (recentEvents) {
            recentEvents.clear();
        }
        synchronized (slowSamples) {
            slowSamples.clear();
        }
        nextSaveMillis.set(0L);
        save(plugin);
    }

    void reload(EvolutionPlugin plugin) {
        this.config = ResourceReporterConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "resource", "config.reload", "resource reporter config refreshed");
    }

    public ReportSample begin(String module, String task) {
        ResourceReporterConfig currentConfig = config;
        if (!currentConfig.enabled()) {
            return ReportSample.disabled();
        }
        return new ReportSample(this, module, task, System.nanoTime());
    }

    public void count(EvolutionPlugin plugin, String module, String task, long workUnits, long changedUnits, String detail) {
        ResourceReporterConfig currentConfig = config;
        if (!currentConfig.enabled()) {
            return;
        }
        record(plugin, currentConfig, module, task, 0L, workUnits, changedUnits, detail);
    }

    void saveNow(EvolutionPlugin plugin) {
        save(plugin);
    }

    private void record(
            EvolutionPlugin plugin,
            ResourceReporterConfig currentConfig,
            String module,
            String task,
            long elapsedNanos,
            long workUnits,
            long changedUnits,
            String detail
    ) {
        String path = module + "." + task;
        TaskStats stats = taskStats.computeIfAbsent(path, key -> new TaskStats(module, task));
        long runs = stats.record(elapsedNanos, workUnits, changedUnits);
        moduleStats.computeIfAbsent(module, ModuleStats::new).record(elapsedNanos, workUnits, changedUnits);

        boolean slow = elapsedNanos >= currentConfig.slowSampleMillis() * 1_000_000L;
        if (slow) {
            recordSlow(currentConfig, module, stats.task(), elapsedNanos, workUnits, changedUnits, detail);
            if (currentConfig.traceToArchitectureDebug()) {
                plugin.pathDebug().traceSampled(plugin, "resource", "resource.slow-sample",
                        path + " " + millis(elapsedNanos) + "ms " + safeDetail(detail));
            }
        }
        if (slow || runs <= 3 || Long.bitCount(runs) == 1) {
            recordRecent(currentConfig, module, stats.task(), elapsedNanos, workUnits, changedUnits, detail, runs);
        }
        saveSoon(plugin, currentConfig);
    }

    private void saveSoon(EvolutionPlugin plugin, ResourceReporterConfig currentConfig) {
        if (!plugin.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + currentConfig.saveIntervalMillis())) {
            return;
        }

        saveAsync(plugin);
    }

    private void saveAsync(EvolutionPlugin plugin) {
        if (!saveRunning.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                save(plugin);
            } finally {
                saveRunning.set(false);
            }
        });
    }

    private void save(EvolutionPlugin plugin) {
        ResourceReporterConfig currentConfig = config;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", currentConfig.enabled());
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("recent-event-limit", currentConfig.recentEvents());
        yaml.set("save-interval-millis", currentConfig.saveIntervalMillis());
        yaml.set("slow-sample-millis", currentConfig.slowSampleMillis());
        yaml.set("notes", "## Resource reporter. Times are measured inside this plugin only; use spark for whole-server flame graphs.");
        writeModuleSummary(yaml.createSection("module-summary"));
        writeTasks(yaml.createSection("top-by-total-time"), Comparator.comparingLong(TaskSnapshot::totalNanos).reversed());
        writeTasks(yaml.createSection("top-by-average-time"), Comparator.comparingDouble(TaskSnapshot::averageNanos).reversed());
        writeTasks(yaml.createSection("top-by-max-spike"), Comparator.comparingLong(TaskSnapshot::maxNanos).reversed());
        yaml.set("recent-slow-samples", snapshot(slowSamples));
        yaml.set("recent-events", snapshot(recentEvents));

        File file = new File(plugin.getDataFolder(), "resource-report.debug.yml");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for resource-report.debug.yml.");
            return;
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save resource-report.debug.yml.", ex);
        }
    }

    private void writeModuleSummary(ConfigurationSection section) {
        moduleStats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> writeStats(section.createSection(entry.getKey()), entry.getValue().snapshot()));
    }

    private void writeTasks(ConfigurationSection section, Comparator<TaskSnapshot> comparator) {
        List<TaskSnapshot> snapshots = taskStats.values().stream()
                .map(TaskStats::snapshot)
                .sorted(comparator)
                .limit(config.topTaskLimit())
                .toList();
        for (int index = 0; index < snapshots.size(); index++) {
            TaskSnapshot snapshot = snapshots.get(index);
            ConfigurationSection entry = section.createSection(Integer.toString(index + 1));
            entry.set("path", snapshot.path());
            entry.set("module", snapshot.module());
            entry.set("task", snapshot.task());
            writeStats(entry, snapshot);
        }
    }

    private void writeStats(ConfigurationSection section, StatsSnapshot snapshot) {
        section.set("runs", snapshot.runs());
        section.set("total-ms", millis(snapshot.totalNanos()));
        section.set("average-ms", snapshot.runs() == 0L ? 0.0D : roundMillis(snapshot.averageNanos()));
        section.set("max-ms", millis(snapshot.maxNanos()));
        section.set("work-units", snapshot.workUnits());
        section.set("changed-units", snapshot.changedUnits());
    }

    private void recordRecent(
            ResourceReporterConfig currentConfig,
            String module,
            String task,
            long elapsedNanos,
            long workUnits,
            long changedUnits,
            String detail,
            long runs
    ) {
        int limit = currentConfig.recentEvents();
        if (limit <= 0) {
            return;
        }
        synchronized (recentEvents) {
            recentEvents.addLast(Instant.now()
                    + " [RESOURCE][" + module + "] " + task
                    + " -> ms=" + millis(elapsedNanos)
                    + " runs=" + runs
                    + " work=" + workUnits
                    + " changed=" + changedUnits
                    + " " + safeDetail(detail));
            while (recentEvents.size() > limit) {
                recentEvents.removeFirst();
            }
        }
    }

    private void recordSlow(
            ResourceReporterConfig currentConfig,
            String module,
            String task,
            long elapsedNanos,
            long workUnits,
            long changedUnits,
            String detail
    ) {
        int limit = currentConfig.recentEvents();
        if (limit <= 0) {
            return;
        }
        synchronized (slowSamples) {
            slowSamples.addLast(Instant.now()
                    + " [RESOURCE][" + module + "] " + task
                    + " -> ms=" + millis(elapsedNanos)
                    + " work=" + workUnits
                    + " changed=" + changedUnits
                    + " " + safeDetail(detail));
            while (slowSamples.size() > limit) {
                slowSamples.removeFirst();
            }
        }
    }

    private List<String> snapshot(Deque<String> events) {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    private static long millis(long nanos) {
        return nanos <= 0L ? 0L : Math.max(1L, nanos / 1_000_000L);
    }

    private static double roundMillis(double nanos) {
        return Math.round((nanos / 1_000_000D) * 100.0D) / 100.0D;
    }

    private static String safeDetail(String detail) {
        return detail == null || detail.isBlank() ? "" : detail;
    }

    public static final class ReportSample implements AutoCloseable {
        private static final ReportSample DISABLED = new ReportSample(null, "", "", 0L);
        private final ResourceReporter reporter;
        private final String module;
        private final String task;
        private final long startedNanos;
        private long workUnits;
        private long changedUnits;
        private String detail = "";
        private boolean closed;

        private ReportSample(ResourceReporter reporter, String module, String task, long startedNanos) {
            this.reporter = reporter;
            this.module = module;
            this.task = task;
            this.startedNanos = startedNanos;
        }

        static ReportSample disabled() {
            return DISABLED;
        }

        public ReportSample workUnits(long amount) {
            this.workUnits += Math.max(0L, amount);
            return this;
        }

        public ReportSample changedUnits(long amount) {
            this.changedUnits += Math.max(0L, amount);
            return this;
        }

        public ReportSample detail(String value) {
            this.detail = value == null ? "" : value;
            return this;
        }

        @Override
        public void close() {
            if (reporter == null || closed) {
                return;
            }
            closed = true;
            reporter.record(
                    reporter.plugin,
                    reporter.config,
                    module,
                    task,
                    System.nanoTime() - startedNanos,
                    workUnits,
                    changedUnits,
                    detail
            );
        }
    }

    private static final class TaskStats {
        private final String module;
        private final String task;
        private final AtomicLong runs = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong maxNanos = new AtomicLong();
        private final AtomicLong workUnits = new AtomicLong();
        private final AtomicLong changedUnits = new AtomicLong();

        private TaskStats(String module, String task) {
            this.module = module;
            this.task = task;
        }

        private String task() {
            return task;
        }

        private long record(long nanos, long work, long changed) {
            long runCount = runs.incrementAndGet();
            totalNanos.addAndGet(Math.max(0L, nanos));
            workUnits.addAndGet(Math.max(0L, work));
            changedUnits.addAndGet(Math.max(0L, changed));
            updateMax(maxNanos, nanos);
            return runCount;
        }

        private TaskSnapshot snapshot() {
            long runCount = runs.get();
            long total = totalNanos.get();
            return new TaskSnapshot(
                    module + "." + task,
                    module,
                    task,
                    runCount,
                    total,
                    maxNanos.get(),
                    workUnits.get(),
                    changedUnits.get(),
                    runCount == 0L ? 0.0D : (double) total / (double) runCount
            );
        }
    }

    private static final class ModuleStats {
        private final String module;
        private final AtomicLong runs = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong maxNanos = new AtomicLong();
        private final AtomicLong workUnits = new AtomicLong();
        private final AtomicLong changedUnits = new AtomicLong();

        private ModuleStats(String module) {
            this.module = module;
        }

        private void record(long nanos, long work, long changed) {
            runs.incrementAndGet();
            totalNanos.addAndGet(Math.max(0L, nanos));
            workUnits.addAndGet(Math.max(0L, work));
            changedUnits.addAndGet(Math.max(0L, changed));
            updateMax(maxNanos, nanos);
        }

        private StatsSnapshot snapshot() {
            long runCount = runs.get();
            long total = totalNanos.get();
            return new ModuleSnapshot(
                    module,
                    runCount,
                    total,
                    maxNanos.get(),
                    workUnits.get(),
                    changedUnits.get(),
                    runCount == 0L ? 0.0D : (double) total / (double) runCount
            );
        }
    }

    private static void updateMax(AtomicLong max, long value) {
        long current;
        long next = Math.max(0L, value);
        do {
            current = max.get();
            if (next <= current) {
                return;
            }
        } while (!max.compareAndSet(current, next));
    }

    private interface StatsSnapshot {
        String path();

        long runs();

        long totalNanos();

        long maxNanos();

        long workUnits();

        long changedUnits();

        double averageNanos();
    }

    private record TaskSnapshot(
            String path,
            String module,
            String task,
            long runs,
            long totalNanos,
            long maxNanos,
            long workUnits,
            long changedUnits,
            double averageNanos
    ) implements StatsSnapshot {
    }

    private record ModuleSnapshot(
            String path,
            long runs,
            long totalNanos,
            long maxNanos,
            long workUnits,
            long changedUnits,
            double averageNanos
    ) implements StatsSnapshot {
    }
}
