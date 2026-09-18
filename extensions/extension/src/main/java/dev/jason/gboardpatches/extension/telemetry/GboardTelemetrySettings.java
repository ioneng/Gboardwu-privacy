package dev.jason.gboardpatches.extension.telemetry;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardTelemetrySettings {
    public static final String PREF_BLOCK_CLEARCUT =
            "pref_telemetry_block_clearcut";
    public static final String PREF_BLOCK_GOOGLE_PLAY_SERVICES =
            "pref_telemetry_block_google_play_services";
    public static final String PREF_BLOCK_DAILY_PING =
            "pref_telemetry_block_daily_ping";
    public static final String PREF_BLOCK_PRIMES =
            "pref_telemetry_block_primes";
    public static final String PREF_BLOCK_TENOR =
            "pref_telemetry_block_tenor";
    public static final String PREF_BLOCK_CRONET =
            "pref_telemetry_block_cronet";

    public static final boolean DEFAULT_BLOCK_CLEARCUT = true;
    public static final boolean DEFAULT_BLOCK_GOOGLE_PLAY_SERVICES = true;
    public static final boolean DEFAULT_BLOCK_DAILY_PING = true;
    public static final boolean DEFAULT_BLOCK_PRIMES = true;
    public static final boolean DEFAULT_BLOCK_TENOR = true;
    public static final boolean DEFAULT_BLOCK_CRONET = true;

    private GboardTelemetrySettings() {
    }

    public static Policy read(Context context) {
        if (context == null) {
            return Policy.blockAll();
        }
        SharedPreferences preferences = GboardPatchesSettings.preferences(context);
        ensureDefaults(preferences);
        return read(preferences);
    }

    static Policy read(SharedPreferences preferences) {
        if (preferences == null) {
            return Policy.blockAll();
        }
        Map<String, ?> values = preferences.getAll();
        return new Policy(
                readBoolean(values, PREF_BLOCK_CLEARCUT, DEFAULT_BLOCK_CLEARCUT),
                readBoolean(values, PREF_BLOCK_GOOGLE_PLAY_SERVICES,
                        DEFAULT_BLOCK_GOOGLE_PLAY_SERVICES),
                readBoolean(values, PREF_BLOCK_DAILY_PING, DEFAULT_BLOCK_DAILY_PING),
                readBoolean(values, PREF_BLOCK_PRIMES, DEFAULT_BLOCK_PRIMES),
                readBoolean(values, PREF_BLOCK_TENOR, DEFAULT_BLOCK_TENOR),
                readBoolean(values, PREF_BLOCK_CRONET, DEFAULT_BLOCK_CRONET));
    }

    public static void ensureDefaults(Context context) {
        if (context == null) {
            return;
        }
        ensureDefaults(GboardPatchesSettings.preferences(context));
    }

    static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        Map<String, ?> values = preferences.getAll();
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        changed |= putDefault(values, editor, PREF_BLOCK_CLEARCUT, DEFAULT_BLOCK_CLEARCUT);
        changed |= putDefault(
                values,
                editor,
                PREF_BLOCK_GOOGLE_PLAY_SERVICES,
                DEFAULT_BLOCK_GOOGLE_PLAY_SERVICES);
        changed |= putDefault(values, editor, PREF_BLOCK_DAILY_PING, DEFAULT_BLOCK_DAILY_PING);
        changed |= putDefault(values, editor, PREF_BLOCK_PRIMES, DEFAULT_BLOCK_PRIMES);
        changed |= putDefault(values, editor, PREF_BLOCK_TENOR, DEFAULT_BLOCK_TENOR);
        changed |= putDefault(values, editor, PREF_BLOCK_CRONET, DEFAULT_BLOCK_CRONET);
        if (changed) {
            editor.commit();
        }
    }

    public static boolean writeBlockClearcut(Context context, boolean blocked) {
        return writeBoolean(context, PREF_BLOCK_CLEARCUT, blocked);
    }

    public static boolean writeBlockGooglePlayServices(Context context, boolean blocked) {
        return writeBoolean(context, PREF_BLOCK_GOOGLE_PLAY_SERVICES, blocked);
    }

    public static boolean writeBlockDailyPing(Context context, boolean blocked) {
        return writeBoolean(context, PREF_BLOCK_DAILY_PING, blocked);
    }

    public static boolean writeBlockPrimes(Context context, boolean blocked) {
        return writeBoolean(context, PREF_BLOCK_PRIMES, blocked);
    }

    public static boolean writeBlockTenor(Context context, boolean blocked) {
        return writeBoolean(context, PREF_BLOCK_TENOR, blocked);
    }

    public static boolean writeBlockCronet(Context context, boolean blocked) {
        return writeBoolean(context, PREF_BLOCK_CRONET, blocked);
    }

    private static boolean writeBoolean(Context context, String key, boolean value) {
        if (context == null) {
            return false;
        }
        return GboardPatchesSettings.preferences(context)
                .edit()
                .putBoolean(key, value)
                .commit();
    }

    private static boolean putDefault(
            Map<String, ?> values,
            SharedPreferences.Editor editor,
            String key,
            boolean defaultValue) {
        if (values.containsKey(key)) {
            return false;
        }
        editor.putBoolean(key, defaultValue);
        return true;
    }

    private static boolean readBoolean(
            Map<String, ?> values,
            String key,
            boolean defaultValue) {
        Object raw = values.get(key);
        if (raw instanceof Boolean value) {
            return value.booleanValue();
        }
        if (raw instanceof String value) {
            if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
                return false;
            }
        }
        if (raw instanceof Number value) {
            return value.intValue() != 0;
        }
        return defaultValue;
    }

    public static final class Policy {
        public final boolean blockClearcut;
        public final boolean blockGooglePlayServices;
        public final boolean blockDailyPing;
        public final boolean blockPrimes;
        public final boolean blockTenor;
        public final boolean blockCronet;

        Policy(
                boolean blockClearcut,
                boolean blockGooglePlayServices,
                boolean blockDailyPing,
                boolean blockPrimes,
                boolean blockTenor,
                boolean blockCronet) {
            this.blockClearcut = blockClearcut;
            this.blockGooglePlayServices = blockGooglePlayServices;
            this.blockDailyPing = blockDailyPing;
            this.blockPrimes = blockPrimes;
            this.blockTenor = blockTenor;
            this.blockCronet = blockCronet;
        }

        public boolean allBlocked() {
            return blockClearcut
                    && blockGooglePlayServices
                    && blockDailyPing
                    && blockPrimes
                    && blockTenor
                    && blockCronet;
        }

        static Policy blockAll() {
            return new Policy(true, true, true, true, true, true);
        }
    }
}
