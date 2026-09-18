package dev.jason.gboardpatches.extension.telemetry;

import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardTelemetrySettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";

    private final String title;
    private final String summary;
    private final String headerBadge;
    private final String restartSummary;
    private final String allBlockedTitle;
    private final String allBlockedSummary;
    private final String partiallyAllowedTitle;
    private final String partiallyAllowedSummary;
    private final String errorTitle;
    private final String errorSummary;

    private final String clearcutSection;
    private final String clearcutDescription;
    private final String clearcutToggle;
    private final String clearcutSummary;

    private final String gmsSection;
    private final String gmsDescription;
    private final String gmsToggle;
    private final String gmsSummary;

    private final String dailySection;
    private final String dailyDescription;
    private final String dailyToggle;
    private final String dailySummary;

    private final String primesSection;
    private final String primesDescription;
    private final String primesToggle;
    private final String primesSummary;

    private final String tenorSection;
    private final String tenorDescription;
    private final String tenorToggle;
    private final String tenorSummary;

    private final String cronetSection;
    private final String cronetDescription;
    private final String cronetToggle;
    private final String cronetSummary;

    public GboardTelemetrySettingsFeature(Context context) {
        title = text(context, R.string.gboard_patches_telemetry_title);
        summary = text(context, R.string.gboard_patches_telemetry_summary);
        headerBadge = text(context, R.string.gboard_patches_header_badge);
        restartSummary = text(context, R.string.gboard_patches_telemetry_restart_summary);
        allBlockedTitle = text(context, R.string.gboard_patches_telemetry_status_all_blocked_title);
        allBlockedSummary = text(context, R.string.gboard_patches_telemetry_status_all_blocked_summary);
        partiallyAllowedTitle =
                text(context, R.string.gboard_patches_telemetry_status_partially_allowed_title);
        partiallyAllowedSummary =
                text(context, R.string.gboard_patches_telemetry_status_partially_allowed_summary);
        errorTitle = text(context, R.string.gboard_patches_telemetry_error_title);
        errorSummary = text(context, R.string.gboard_patches_telemetry_error_summary);

        clearcutSection = text(context, R.string.gboard_patches_telemetry_clearcut_section);
        clearcutDescription =
                text(context, R.string.gboard_patches_telemetry_clearcut_description);
        clearcutToggle = text(context, R.string.gboard_patches_telemetry_clearcut_toggle);
        clearcutSummary = text(context, R.string.gboard_patches_telemetry_clearcut_summary);

        gmsSection = text(context, R.string.gboard_patches_telemetry_gms_section);
        gmsDescription = text(context, R.string.gboard_patches_telemetry_gms_description);
        gmsToggle = text(context, R.string.gboard_patches_telemetry_gms_toggle);
        gmsSummary = text(context, R.string.gboard_patches_telemetry_gms_summary);

        dailySection = text(context, R.string.gboard_patches_telemetry_daily_section);
        dailyDescription = text(context, R.string.gboard_patches_telemetry_daily_description);
        dailyToggle = text(context, R.string.gboard_patches_telemetry_daily_toggle);
        dailySummary = text(context, R.string.gboard_patches_telemetry_daily_summary);

        primesSection = text(context, R.string.gboard_patches_telemetry_primes_section);
        primesDescription = text(context, R.string.gboard_patches_telemetry_primes_description);
        primesToggle = text(context, R.string.gboard_patches_telemetry_primes_toggle);
        primesSummary = text(context, R.string.gboard_patches_telemetry_primes_summary);

        tenorSection = text(context, R.string.gboard_patches_telemetry_tenor_section);
        tenorDescription = text(context, R.string.gboard_patches_telemetry_tenor_description);
        tenorToggle = text(context, R.string.gboard_patches_telemetry_tenor_toggle);
        tenorSummary = text(context, R.string.gboard_patches_telemetry_tenor_summary);

        cronetSection = text(context, R.string.gboard_patches_telemetry_cronet_section);
        cronetDescription = text(context, R.string.gboard_patches_telemetry_cronet_description);
        cronetToggle = text(context, R.string.gboard_patches_telemetry_cronet_toggle);
        cronetSummary = text(context, R.string.gboard_patches_telemetry_cronet_summary);
    }

    @Override
    public String getEntryTitle() {
        return title;
    }

    @Override
    public String getEntrySummary() {
        return summary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_TELEMETRY_BLOCKING);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return errorScreen();
            }
            Context context = host.getContext();
            GboardTelemetrySettings.Policy policy = GboardTelemetrySettings.read(context);
            GboardPatchesSettingsContract.StatusBlock status = policy.allBlocked()
                    ? new GboardPatchesSettingsContract.StatusBlock(
                            allBlockedTitle,
                            allBlockedSummary,
                            GboardPatchesSettingsContract.StatusTone.INFO)
                    : new GboardPatchesSettingsContract.StatusBlock(
                            partiallyAllowedTitle,
                            partiallyAllowedSummary,
                            GboardPatchesSettingsContract.StatusTone.WARNING);

            return new GboardPatchesSettingsContract.Screen(
                    title,
                    headerBadge,
                    title,
                    summary,
                    Arrays.asList(
                            status,
                            new GboardPatchesSettingsContract.StatusBlock(
                                    "",
                                    restartSummary,
                                    GboardPatchesSettingsContract.StatusTone.NEUTRAL)),
                    Arrays.asList(
                            section(
                                    clearcutSection,
                                    clearcutDescription,
                                    clearcutToggle,
                                    clearcutSummary,
                                    policy.blockClearcut,
                                    value -> writeAndRefresh(
                                            host,
                                            GboardTelemetrySettings.writeBlockClearcut(
                                                    context, value))),
                            section(
                                    gmsSection,
                                    gmsDescription,
                                    gmsToggle,
                                    gmsSummary,
                                    policy.blockGooglePlayServices,
                                    value -> writeAndRefresh(
                                            host,
                                            GboardTelemetrySettings.writeBlockGooglePlayServices(
                                                    context, value))),
                            section(
                                    dailySection,
                                    dailyDescription,
                                    dailyToggle,
                                    dailySummary,
                                    policy.blockDailyPing,
                                    value -> writeAndRefresh(
                                            host,
                                            GboardTelemetrySettings.writeBlockDailyPing(
                                                    context, value))),
                            section(
                                    primesSection,
                                    primesDescription,
                                    primesToggle,
                                    primesSummary,
                                    policy.blockPrimes,
                                    value -> writeAndRefresh(
                                            host,
                                            GboardTelemetrySettings.writeBlockPrimes(
                                                    context, value))),
                            section(
                                    tenorSection,
                                    tenorDescription,
                                    tenorToggle,
                                    tenorSummary,
                                    policy.blockTenor,
                                    value -> writeAndRefresh(
                                            host,
                                            GboardTelemetrySettings.writeBlockTenor(
                                                    context, value))),
                            section(
                                    cronetSection,
                                    cronetDescription,
                                    cronetToggle,
                                    cronetSummary,
                                    policy.blockCronet,
                                    value -> writeAndRefresh(
                                            host,
                                            GboardTelemetrySettings.writeBlockCronet(
                                                    context, value)))),
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable failure) {
            Log.w(TAG, "Failed to render telemetry settings", failure);
            return errorScreen();
        }
    }

    private GboardPatchesSettingsContract.Section section(
            String sectionTitle,
            String sectionDescription,
            String toggleTitle,
            String toggleSummary,
            boolean checked,
            GboardPatchesSettingsContract.ToggleAction action) {
        return new GboardPatchesSettingsContract.Section(
                sectionTitle,
                sectionDescription,
                GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                Collections.singletonList(
                        new GboardPatchesSettingsContract.ToggleRow(
                                toggleTitle,
                                toggleSummary,
                                true,
                                checked,
                                action)));
    }

    private void writeAndRefresh(
            GboardPatchesSettingsContract.FeatureHost host,
            boolean success) {
        if (success) {
            GboardPatchesSettingsContract.refresh(host);
        }
    }

    private GboardPatchesSettingsContract.Screen errorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                title,
                headerBadge,
                title,
                summary,
                Collections.singletonList(
                        new GboardPatchesSettingsContract.StatusBlock(
                                errorTitle,
                                errorSummary,
                                GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }
}
