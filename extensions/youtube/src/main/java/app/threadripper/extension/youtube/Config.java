package app.threadripper.extension.youtube;

import java.lang.reflect.Method;

/**
 * Runtime settings from system properties, so tests can switch modes instantly with adb
 * (for example {@code adb shell setprop debug.tr.threads 1}); the next media request uses them.
 * Unset properties use defaults. Properties reset on reboot.
 *
 * <pre>
 * debug.tr.enabled        true
 * debug.tr.threads        8      concurrent requests per range
 * debug.tr.chunk_kib      1024   chunk size
 * debug.tr.min_split_kib  1024   smaller ranges stay with the app
 * debug.tr.log            false  per-request log lines (info level)
 * </pre>
 */
final class Config {
    final boolean enabled;
    final int threads;
    final int chunkBytes;
    final long minSplitBytes;
    final boolean log;

    private static volatile Config last;

    private Config() {
        enabled = bool("enabled", true);
        threads = clamp(integer("threads", 8), 1, 32);
        chunkBytes = clamp(integer("chunk_kib", 1024), 64, 65536) * 1024;
        minSplitBytes = clamp(integer("min_split_kib", 1024), 0, 1 << 20) * 1024L;
        log = bool("log", false);
    }

    static Config get() {
        Config c = new Config();
        Config prev = last;
        if (prev == null || !c.equals(prev)) {
            last = c;
            Log.i("Config: " + c);
        }
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Config)) return false;
        Config c = (Config) o;
        return enabled == c.enabled && threads == c.threads && chunkBytes == c.chunkBytes
                && minSplitBytes == c.minSplitBytes && log == c.log;
    }

    @Override
    public int hashCode() {
        return threads;
    }

    @Override
    public String toString() {
        return "enabled=" + enabled + " threads=" + threads + " chunk=" + (chunkBytes / 1024)
                + "KiB minSplit=" + (minSplitBytes / 1024) + "KiB log=" + log;
    }

    private static final Method GET;

    static {
        Method m = null;
        try {
            m = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
        } catch (Exception ex) {
            android.util.Log.e("ThreadRipper", "SystemProperties unavailable, using defaults", ex);
        }
        GET = m;
    }

    private static String prop(String key) {
        if (GET == null) return "";
        try {
            return (String) GET.invoke(null, "debug.tr." + key);
        } catch (Exception ex) {
            return "";
        }
    }

    private static boolean bool(String key, boolean def) {
        String v = prop(key);
        return v == null || v.isEmpty() ? def : Boolean.parseBoolean(v.trim());
    }

    private static int integer(String key, int def) {
        try {
            String v = prop(key);
            return v == null || v.isEmpty() ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
