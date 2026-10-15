package com.artesys.netcon;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(NativeVpnPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
