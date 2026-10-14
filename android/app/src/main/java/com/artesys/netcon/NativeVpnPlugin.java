package com.artesys.netcon;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.provider.Settings;

import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "NativeVpn")
public final class NativeVpnPlugin extends Plugin {
    private String pendingConfig;

    @PluginMethod
    public void connect(PluginCall call) {
        if (!isTrustedOrigin()) {
            call.reject("VPN controls are available only from the configured Netcon app origin.");
            return;
        }
        String config = call.getString("config");
        if (config == null || config.isBlank() || config.length() > 256_000) {
            call.reject("A valid Xray configuration is required.");
            return;
        }

        Intent consentIntent = VpnService.prepare(getContext());
        if (consentIntent != null) {
            if (pendingConfig != null) {
                call.reject("Another Android VPN permission request is already in progress.");
                return;
            }
            pendingConfig = config;
            startActivityForResult(call, consentIntent, "vpnConsentResult");
            return;
        }
        startVpn(config, call);
    }

    @ActivityCallback
    private void vpnConsentResult(PluginCall call, ActivityResult result) {
        String config = pendingConfig;
        pendingConfig = null;
        if (call == null) {
            return;
        }
        if (!isTrustedOrigin()) {
            call.reject("VPN controls are available only from the configured Netcon app origin.");
            return;
        }
        if (result.getResultCode() != Activity.RESULT_OK || config == null) {
            call.reject("Android VPN permission was not granted.");
            return;
        }
        startVpn(config, call);
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        if (!isTrustedOrigin()) {
            call.reject("VPN controls are available only from the configured Netcon app origin.");
            return;
        }
        Intent intent = new Intent(getContext(), NetconVpnService.class)
            .setAction(NetconVpnService.ACTION_DISCONNECT);
        try {
            getContext().startService(intent);
            call.resolve();
        } catch (Exception error) {
            call.reject("Unable to stop the Android VPN service.", error);
        }
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        if (!isTrustedOrigin()) {
            call.reject("VPN status is available only from the configured Netcon app origin.");
            return;
        }
        call.resolve(NetconVpnService.getStatus());
    }

    @PluginMethod
    public void openVpnSettings(PluginCall call) {
        if (!isTrustedOrigin()) {
            call.reject("VPN settings are available only from the configured Netcon app origin.");
            return;
        }
        try {
            getContext().startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            call.resolve();
        } catch (Exception error) {
            call.reject("Unable to open Android VPN settings.", error);
        }
    }

    private void startVpn(String config, PluginCall call) {
        if (!isTrustedOrigin()) {
            call.reject("VPN controls are available only from the configured Netcon app origin.");
            return;
        }
        Intent intent = new Intent(getContext(), NetconVpnService.class)
            .setAction(NetconVpnService.ACTION_CONNECT)
            .putExtra(NetconVpnService.EXTRA_CONFIG, config);
        try {
            ContextCompat.startForegroundService(getContext(), intent);
            JSObject result = new JSObject();
            result.put("state", "connecting");
            call.resolve(result);
        } catch (Exception error) {
            call.reject("Unable to start the Android VPN service.", error);
        }
    }

    private boolean isTrustedOrigin() {
        String pageUrl = getBridge().getWebView().getUrl();
        String configuredUrl = getBridge().getServerUrl();
        if (configuredUrl == null || configuredUrl.isBlank()) {
            configuredUrl = getBridge().getScheme() + "://" + getBridge().getHost();
        }
        if (pageUrl == null) {
            return false;
        }
        Uri page = Uri.parse(pageUrl);
        Uri configured = Uri.parse(configuredUrl);
        return page.getScheme() != null
            && page.getScheme().equals(configured.getScheme())
            && page.getHost() != null
            && page.getHost().equalsIgnoreCase(configured.getHost())
            && effectivePort(page) == effectivePort(configured);
    }

    private int effectivePort(Uri uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}