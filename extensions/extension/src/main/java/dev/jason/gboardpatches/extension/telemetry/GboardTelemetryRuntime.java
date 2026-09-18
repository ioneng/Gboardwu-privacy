package dev.jason.gboardpatches.extension.telemetry;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

public final class GboardTelemetryRuntime {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-telemetry-18.0.3] ";

    private static volatile GboardTelemetrySettings.Policy policy =
            GboardTelemetrySettings.Policy.blockAll();
    private static volatile boolean initialized;

    private GboardTelemetryRuntime() {
    }

    public static void initialize(Context context) {
        reload(context);
    }

    public static void reload(Context context) {
        if (context == null) {
            policy = GboardTelemetrySettings.Policy.blockAll();
            initialized = false;
            return;
        }
        try {
            policy = GboardTelemetrySettings.read(context);
            initialized = true;
        } catch (Throwable failure) {
            policy = GboardTelemetrySettings.Policy.blockAll();
            initialized = false;
            logFailure("failed to load telemetry preferences; blocking remains enabled", failure);
        }
    }

    public static boolean shouldBlockClearcut() {
        return policy.blockClearcut;
    }

    public static boolean shouldBlockGooglePlayServices() {
        return policy.blockGooglePlayServices;
    }

    public static boolean shouldBlockDailyPing(Object contextHint) {
        ensureInitialized(resolveContext(contextHint));
        return policy.blockDailyPing;
    }

    public static boolean shouldBlockPrimes(Object contextHint) {
        ensureInitialized(resolveContext(contextHint));
        return policy.blockPrimes;
    }

    public static boolean shouldBlockTenor() {
        return policy.blockTenor;
    }

    public static boolean shouldBlockCronet(Context context) {
        ensureInitialized(context);
        return policy.blockCronet;
    }

    private static void ensureInitialized(Context context) {
        if (!initialized && context != null) {
            reload(context);
        }
    }

    private static Context resolveContext(Object contextHint) {
        if (contextHint instanceof Context context) {
            Context applicationContext = context.getApplicationContext();
            return applicationContext != null ? applicationContext : context;
        }
        if (contextHint == null) {
            return null;
        }
        try {
            Method getter = contextHint.getClass().getMethod("getApplicationContext");
            Object value = getter.invoke(contextHint);
            if (value instanceof Context context) {
                Context applicationContext = context.getApplicationContext();
                return applicationContext != null ? applicationContext : context;
            }
        } catch (Throwable ignored) {
            // Most telemetry helper objects are not Context owners. The process cache is enough.
        }
        return null;
    }

    private static void logFailure(String message, Throwable failure) {
        try {
            Log.w(TAG, LOG_PREFIX + message, failure);
        } catch (Throwable ignored) {
            // Telemetry policy must never affect keyboard stability.
        }
    }
}
