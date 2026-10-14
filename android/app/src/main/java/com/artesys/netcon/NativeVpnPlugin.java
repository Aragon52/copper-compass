package com.artesys.netcon;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;

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
        String config = call.getString("config");
        if (config == null || config.isBlank()) {
            call.reject("A valid Xray configuration is required.");
            return;
        }

        Intent consentIntent = VpnService.prepare(getContext());
        if (consentIntent != null) {
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
        if (result.getResultCode() != Activity.RESULT_OK || config == null) {
            call.reject("Android VPN permission was not granted.");
            return;
        }
        startVpn(config, call);
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        Intent intent = new Intent(getContext(), NetconVpnService.class)
            .setAction(NetconVpnService.ACTION_DISCONNECT);
        getContext().startService(intent);
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        call.resolve(NetconVpnService.getStatus());
    }

    private void startVpn(String config, PluginCall call) {
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
}