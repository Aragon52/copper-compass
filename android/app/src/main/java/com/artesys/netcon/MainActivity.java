package com.artesys.netcon;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int VPN_PERMISSION_REQUEST = 4183;
    private static final String CONFIG_KEY = "xray_config";
    private static final String SERVER_NAME_KEY = "server_name";
    private static final int BACKGROUND = Color.rgb(32, 35, 39);
    private static final int SURFACE = Color.rgb(45, 49, 54);
    private static final int FOREGROUND = Color.rgb(242, 242, 238);
    private static final int MUTED = Color.rgb(169, 174, 178);
    private static final int ACCENT = Color.rgb(190, 211, 187);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText configurationInput;
    private TextView serverLabel;
    private TextView statusLabel;
    private TextView errorLabel;
    private Button connectButton;
    private String pendingConfig;
    private boolean polling;

    private final Runnable statusPoll = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            if (polling) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(0);
        createScreen();
        loadSavedConfig();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        polling = true;
        handler.post(statusPoll);
    }

    @Override
    protected void onPause() {
        polling = false;
        handler.removeCallbacks(statusPoll);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VPN_PERMISSION_REQUEST) {
            return;
        }
        if (resultCode == RESULT_OK && pendingConfig != null) {
            startVpn(pendingConfig);
        } else {
            showError("Android VPN permission was not granted.");
        }
        pendingConfig = null;
    }

    private void createScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        scroll.addView(content);

        TextView brand = text("NETCON", 13, ACCENT);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setLetterSpacing(0.18f);
        content.addView(brand, params(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 14));

        TextView title = text("VPN connection", 28, FOREGROUND);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 8));

        TextView intro = text("Import a server link, then connect through Android's VPN service.", 15, MUTED);
        content.addView(intro, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 24));

        TextView serverHeading = text("SERVER CONFIGURATION", 12, MUTED);
        serverHeading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(serverHeading, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 8));

        configurationInput = new EditText(this);
        configurationInput.setTextColor(FOREGROUND);
        configurationInput.setHintTextColor(MUTED);
        configurationInput.setHint("Paste a vless://, vmess://, trojan://, or ss:// link");
        configurationInput.setTextSize(14);
        configurationInput.setGravity(Gravity.TOP | Gravity.START);
        configurationInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        configurationInput.setMinLines(3);
        configurationInput.setMaxLines(6);
        configurationInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        configurationInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        configurationInput.setBackgroundColor(SURFACE);
        content.addView(configurationInput, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 10));

        Button pasteButton = button("Paste from clipboard", SURFACE, MUTED);
        content.addView(pasteButton, params(LinearLayout.LayoutParams.MATCH_PARENT, dp(48), 0, 0, 0, 8));
        pasteButton.setOnClickListener(view -> pasteConfiguration());

        Button importButton = button("Save server configuration", SURFACE, FOREGROUND);
        content.addView(importButton, params(LinearLayout.LayoutParams.MATCH_PARENT, dp(52), 0, 0, 0, 20));
        importButton.setOnClickListener(view -> importConfiguration());

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(76, 81, 86));
        content.addView(divider, params(LinearLayout.LayoutParams.MATCH_PARENT, dp(1), 0, 0, 0, 20));

        serverLabel = text("No server saved", 16, FOREGROUND);
        serverLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(serverLabel, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 6));

        statusLabel = text("Disconnected", 14, MUTED);
        content.addView(statusLabel, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 14));

        connectButton = button("Connect", ACCENT, BACKGROUND);
        connectButton.setTextSize(16);
        connectButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(connectButton, params(LinearLayout.LayoutParams.MATCH_PARENT, dp(56), 0, 0, 0, 10));
        connectButton.setOnClickListener(view -> onConnectClicked());

        Button disconnectButton = button("Disconnect", SURFACE, FOREGROUND);
        content.addView(disconnectButton, params(LinearLayout.LayoutParams.MATCH_PARENT, dp(50), 0, 0, 0, 16));
        disconnectButton.setOnClickListener(view -> disconnectVpn());

        Button settingsButton = button("Android VPN settings", SURFACE, MUTED);
        content.addView(settingsButton, params(LinearLayout.LayoutParams.MATCH_PARENT, dp(48), 0, 0, 0, 0));
        settingsButton.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
            } catch (Exception error) {
                showError("Unable to open Android VPN settings: " + error.getMessage());
            }
        });

        errorLabel = text("", 13, Color.rgb(255, 148, 138));
        content.addView(errorLabel, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 14, 0, 0));
        setContentView(scroll);
    }

    private void loadSavedConfig() {
        String config = getPreferences(MODE_PRIVATE).getString(CONFIG_KEY, null);
        String name = getPreferences(MODE_PRIVATE).getString(SERVER_NAME_KEY, null);
        if (config != null && !config.trim().isEmpty()) {
            pendingConfig = config;
            serverLabel.setText(name == null || name.trim().isEmpty() ? "Saved server" : name);
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String sharedText = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            sharedText = intent.getData().toString();
        }
        if (sharedText != null && !sharedText.trim().isEmpty() && configurationInput != null) {
            configurationInput.setText(sharedText.trim());
            importConfiguration();
        }
    }

    private void importConfiguration() {
        String raw = configurationInput.getText().toString().trim();
        try {
            if (raw.length() > 256_000) {
                throw new IllegalArgumentException("Configuration text must be 256 KB or smaller.");
            }
            NativeXrayConfig.ImportedServer imported = NativeXrayConfig.fromInput(raw);
            getPreferences(MODE_PRIVATE).edit()
                .putString(CONFIG_KEY, imported.config.toString())
                .putString(SERVER_NAME_KEY, imported.name)
                .apply();
            pendingConfig = imported.config.toString();
            serverLabel.setText(imported.name);
            configurationInput.setText("");
            errorLabel.setText("");
            hideKeyboard();
            Toast.makeText(this, "Server configuration saved on this device.", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            showError(error.getMessage() == null ? "Invalid server configuration." : error.getMessage());
        }
    }

    private void pasteConfiguration() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            showError("There is no configuration text on the clipboard.");
            return;
        }
        CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text == null || text.toString().trim().isEmpty()) {
            showError("The clipboard does not contain configuration text.");
            return;
        }
        configurationInput.setText(text);
        errorLabel.setText("");
    }

    private void onConnectClicked() {
        errorLabel.setText("");
        String config = getPreferences(MODE_PRIVATE).getString(CONFIG_KEY, null);
        if (config == null || config.trim().isEmpty()) {
            showError("Save a server link before connecting.");
            return;
        }
        Intent permissionIntent = VpnService.prepare(this);
        if (permissionIntent != null) {
            pendingConfig = config;
            startActivityForResult(permissionIntent, VPN_PERMISSION_REQUEST);
        } else {
            startVpn(config);
        }
    }

    private void startVpn(String config) {
        try {
            Intent intent = new Intent(this, NetconVpnService.class)
                .setAction(NetconVpnService.ACTION_CONNECT)
                .putExtra(NetconVpnService.EXTRA_CONFIG, config);
            androidx.core.content.ContextCompat.startForegroundService(this, intent);
            errorLabel.setText("");
        } catch (Exception error) {
            showError("Unable to start the Android VPN service: " + error.getMessage());
        }
    }

    private void disconnectVpn() {
        try {
            startService(new Intent(this, NetconVpnService.class).setAction(NetconVpnService.ACTION_DISCONNECT));
            errorLabel.setText("");
        } catch (Exception error) {
            showError("Unable to stop the Android VPN service: " + error.getMessage());
        }
    }

    private void updateStatus() {
        NetconVpnService.Status status = NetconVpnService.getStatus();
        String state = status.state;
        switch (state) {
            case "connected":
                statusLabel.setText("Connected · " + formatBytes(status.receivedBytes) + " received · " + formatBytes(status.sentBytes) + " sent");
                statusLabel.setTextColor(ACCENT);
                connectButton.setText("Reconnect");
                break;
            case "connecting":
                statusLabel.setText("Connecting to server…");
                statusLabel.setTextColor(ACCENT);
                connectButton.setText("Connecting…");
                break;
            case "error":
                statusLabel.setText("Connection failed");
                statusLabel.setTextColor(Color.rgb(255, 148, 138));
                connectButton.setText("Try again");
                showError(status.error.trim().isEmpty() ? "The VPN service failed." : status.error);
                break;
            default:
                statusLabel.setText("Disconnected");
                statusLabel.setTextColor(MUTED);
                connectButton.setText("Connect");
                break;
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    private void hideKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputManager != null) {
            inputManager.hideSoftInputFromWindow(configurationInput.getWindowToken(), 0);
        }
        configurationInput.clearFocus();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value, int backgroundColor, int textColor) {
        Button view = new Button(this);
        view.setText(value);
        view.setTextColor(textColor);
        view.setAllCaps(false);
        view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(backgroundColor));
        return view;
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams result = new LinearLayout.LayoutParams(width, height);
        result.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return result;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1_000) return bytes + " B";
        if (bytes < 1_000_000) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1_000d);
        if (bytes < 1_000_000_000) return String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000d);
        return String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000d);
    }
}
