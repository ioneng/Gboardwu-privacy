package dev.jason.gboardpatches.extension.telemetry;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class GboardTelemetrySettingsContractTest {
    @Test
    public void allTelemetryGroupsDefaultToBlocked() throws Exception {
        String settings = read("src/main/java/dev/jason/gboardpatches/extension/telemetry/"
                + "GboardTelemetrySettings.java");

        Assert.assertTrue(settings.contains("DEFAULT_BLOCK_CLEARCUT = true"));
        Assert.assertTrue(settings.contains("DEFAULT_BLOCK_GOOGLE_PLAY_SERVICES = true"));
        Assert.assertTrue(settings.contains("DEFAULT_BLOCK_DAILY_PING = true"));
        Assert.assertTrue(settings.contains("DEFAULT_BLOCK_PRIMES = true"));
        Assert.assertTrue(settings.contains("DEFAULT_BLOCK_TENOR = true"));
        Assert.assertTrue(settings.contains("DEFAULT_BLOCK_CRONET = true"));
    }

    @Test
    public void settingsScreenGroupsTelemetryBySource() throws Exception {
        String feature = read("src/main/java/dev/jason/gboardpatches/extension/telemetry/"
                + "GboardTelemetrySettingsFeature.java");
        String text = read("src/main/settings-text/gboard_settings_text.xml");

        Assert.assertTrue(feature.contains("GboardTelemetrySettings.writeBlockClearcut"));
        Assert.assertTrue(feature.contains("GboardTelemetrySettings.writeBlockGooglePlayServices"));
        Assert.assertTrue(feature.contains("GboardTelemetrySettings.writeBlockDailyPing"));
        Assert.assertTrue(feature.contains("GboardTelemetrySettings.writeBlockPrimes"));
        Assert.assertTrue(feature.contains("GboardTelemetrySettings.writeBlockTenor"));
        Assert.assertTrue(feature.contains("GboardTelemetrySettings.writeBlockCronet"));

        Assert.assertTrue(text.contains("Gboard &amp; ML Kit Clearcut"));
        Assert.assertTrue(text.contains("Google Play services telemetry"));
        Assert.assertTrue(text.contains("Gboard Daily Ping"));
        Assert.assertTrue(text.contains("Primes diagnostics"));
        Assert.assertTrue(text.contains("Tenor share tracking"));
        Assert.assertTrue(text.contains("Cronet network telemetry"));
        Assert.assertTrue(text.contains("Restart required"));
    }

    @Test
    public void telemetryFeatureIsMarkerGatedAndRuntimePolicyInitializesEarly() throws Exception {
        String availability = read("src/main/java/dev/jason/gboardpatches/extension/settings/"
                + "GboardPatchesFeatureAvailability.java");
        String registry = read("src/main/java/dev/jason/gboardpatches/extension/settings/"
                + "GboardPatchesSettingsFeatureRegistry.java");
        String provider = read("src/main/java/dev/jason/gboardpatches/extension/settings/"
                + "GboardPatchesSettingsProvider.java");
        String runtime = read("src/main/java/dev/jason/gboardpatches/extension/telemetry/"
                + "GboardTelemetryRuntime.java");

        Assert.assertTrue(availability.contains(
                "dev.jason.gboardpatches.feature.telemetry_blocking"));
        Assert.assertTrue(registry.contains("new GboardTelemetrySettingsFeature(context)"));
        Assert.assertTrue(provider.contains("GboardTelemetryRuntime.initialize(getContext())"));
        Assert.assertTrue(runtime.contains("Policy.blockAll()"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
