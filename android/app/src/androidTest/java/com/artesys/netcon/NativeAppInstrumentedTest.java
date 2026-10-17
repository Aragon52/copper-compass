package com.artesys.netcon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NativeAppInstrumentedTest {
    @Test
    public void usesNetconApplicationId() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.artesys.netcon", appContext.getPackageName());
    }

    @Test
    public void parsesVlessLinkOnDevice() throws Exception {
        NativeXrayConfig.ImportedServer imported = NativeXrayConfig.fromInput(
            "vless://123e4567-e89b-12d3-a456-426614174000@example.com:443?security=tls&type=ws&path=%2Fvpn#Test"
        );
        JSONObject outbound = imported.config.getJSONArray("outbounds").getJSONObject(0);
        assertEquals("Test", imported.name);
        assertEquals("vless", outbound.getString("protocol"));
        assertEquals("example.com", outbound.getJSONObject("settings")
            .getJSONArray("vnext").getJSONObject(0).getString("address"));
    }

    @Test
    public void rejectsEmptyConfigurationOnDevice() {
        assertThrows(IllegalArgumentException.class, () -> NativeXrayConfig.fromInput(" "));
    }
}
