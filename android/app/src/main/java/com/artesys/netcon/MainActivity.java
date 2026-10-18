package com.artesys.netcon;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int VPN_PERMISSION_REQUEST = 4183;
    private static final String CONFIG_KEY = "xray_config";
    private static final String SERVER_NAME_KEY = "server_name";
    private static final String PREF_SERVERS = "saved_servers";
    private static final String PREF_SELECTED_SERVER = "selected_server";
    private static final String PREF_DNS = "dns_server";
    private static final String PREF_ROUTING = "routing_mode";
    private static final String PREF_PROTOCOLS = "enabled_protocols";

    private static final int BACKGROUND = Color.rgb(25, 28, 32);
    private static final int DEEP_INK = Color.rgb(8, 10, 13);
    private static final int STEEL = Color.rgb(73, 79, 86);
    private static final int PLATE_LIGHT = Color.rgb(220, 220, 215);
    private static final int PLATE_MID = Color.rgb(177, 181, 181);
    private static final int PLATE_SHADE = Color.rgb(119, 127, 132);
    private static final int BEVEL = Color.rgb(66, 73, 80);
    private static final int COPPER = Color.rgb(193, 117, 70);
    private static final int COPPER_LIGHT = Color.rgb(235, 202, 154);
    private static final int COPPER_DARK = Color.rgb(111, 65, 38);
    private static final int GREEN = Color.rgb(76, 146, 92);
    private static final int RED = Color.rgb(198, 74, 66);
    private static final int TEXT = Color.rgb(235, 235, 230);
    private static final int MUTED = Color.rgb(179, 184, 186);
    private static final int INK = Color.rgb(43, 47, 50);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<SavedServer> servers = new ArrayList<>();
    private final ArrayList<InstrumentGauge> gauges = new ArrayList<>();
    private SharedPreferences preferences;
    private LinearLayout page;
    private LinearLayout chassis;
    private LinearLayout tabContent;
    private LinearLayout navBar;
    private TextView statusBadge;
    private TextView serverNameView;
    private TextView serverDetailView;
    private TextView connectionStatusView;
    private TextView connectionDurationView;
    private TextView connectionErrorView;
    private TextView downloadValueView;
    private TextView uploadValueView;
    private TextView receivedValueView;
    private TextView sentValueView;
    private Button powerButton;
    private EditText configInput;
    private String currentTab = "connection";
    private String selectedServerId = "";
    private String savedDns = "1.1.1.1";
    private String savedRouting = "Bypass LAN";
    private String pendingConfig;
    private String connectionError = "";
    private long lastReceivedBytes;
    private long lastSentBytes;
    private long lastSampleAt;
    private double downloadMbps;
    private double uploadMbps;
    private boolean polling;

    private final Runnable statusPoll = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            if (polling) handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("netcon_native", MODE_PRIVATE);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(0);
        loadSavedServers();
        loadSettings();
        buildApplication();
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
        if (requestCode != VPN_PERMISSION_REQUEST) return;
        if (resultCode == RESULT_OK && pendingConfig != null) {
            startVpn(pendingConfig);
        } else {
            showError("Android VPN permission was not granted.");
        }
        pendingConfig = null;
    }

    private void buildApplication() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(18), dp(12), dp(22));
        page.setBackgroundColor(BACKGROUND);
        scroll.addView(page);

        LinearLayout outerBrand = horizontal();
        add(outerBrand, label("NETCON", 12, MUTED, true), wrap());
        View rule = new View(this);
        rule.setBackgroundColor(BEVEL);
        outerBrand.addView(rule, new LinearLayout.LayoutParams(0, dp(1), 1));
        TextView systemLabel = label("PRECISION CONNECTION SYSTEM", 8, MUTED, false);
        systemLabel.setLetterSpacing(0.08f);
        add(outerBrand, systemLabel, wrap());
        page.addView(outerBrand, params(MATCH, dp(20), 0, 0, 0, 12));

        chassis = new LinearLayout(this);
        chassis.setOrientation(LinearLayout.VERTICAL);
        chassis.setPadding(dp(14), dp(18), dp(14), dp(8));
        chassis.setBackground(chassisBackground());
        chassis.setElevation(dp(8));
        chassis.setClipToPadding(false);
        page.addView(chassis, params(MATCH, WRAP, 0, 0, 0, 10));

        buildHeader(chassis);
        buildTabs(chassis);
        tabContent = new LinearLayout(this);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        chassis.addView(tabContent, params(MATCH, WRAP, 0, 0, 0, 0));
        buildFooter(chassis);
        page.addView(label("BRUSHED STEEL  ×  BURNISHED COPPER", 9, MUTED, true), params(MATCH, WRAP, 4, 2, 0, 0));
        setContentView(scroll);
        renderTab();
    }

    private void buildHeader(LinearLayout container) {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(2), dp(4), dp(16));

        LinearLayout brandStack = new LinearLayout(this);
        brandStack.setOrientation(LinearLayout.VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.netcon_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setAdjustViewBounds(true);
        add(brandStack, logo, params(dp(150), dp(40), 0, 0, 0, 1));
        add(brandStack, label("PRECISION CONNECTION CONTROL", 8, MUTED, true), wrap());
        header.addView(brandStack, new LinearLayout.LayoutParams(0, WRAP, 1));

        statusBadge = label("●  OFFLINE", 10, RED, true);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(dp(12), dp(9), dp(12), dp(9));
        statusBadge.setBackground(engravedStatusBackground());
        add(header, statusBadge, wrap());
        container.addView(header, params(MATCH, WRAP, 0, 0, 0, 0));
    }

    private void buildTabs(LinearLayout container) {
        if (navBar != null) container.removeView(navBar);
        LinearLayout nav = horizontal();
        navBar = nav;
        nav.setPadding(dp(3), dp(3), dp(3), dp(3));
        nav.setBackground(round(DEEP_INK, BEVEL, 6));
        nav.setElevation(dp(2));
        String[] ids = {"connection", "config", "info"};
        String[] icons = {"ϟ", "⚙", "ⓘ"};
        String[] titles = {"CONNECTION", "CONFIG", "INFO"};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            TextView tab = label(icons[i] + "  " + titles[i], 11, PLATE_LIGHT, true);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(dp(46));
            tab.setBackground(round(id.equals(currentTab) ? metalTop() : DEEP_INK, BEVEL, 4));
            tab.setOnClickListener(view -> {
                if (!currentTab.equals(id)) {
                    currentTab = id;
                    renderTab();
                    buildTabs(container);
                }
            });
            nav.addView(tab, new LinearLayout.LayoutParams(0, WRAP, 1));
        }
        container.addView(nav, params(MATCH, WRAP, 0, 0, 0, 16));
    }

    private void buildFooter(LinearLayout container) {
        View separator = new View(this);
        separator.setBackgroundColor(PLATE_SHADE);
        container.addView(separator, params(MATCH, dp(1), 0, dp(4), 0, 8));
        LinearLayout footer = horizontal();
        add(footer, label("NETCON  /  ENGINEERED FOR PRIVACY", 8, INK, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        add(footer, label("XRAY CORE  ●  ANDROID VPN", 8, INK, true), wrap());
        container.addView(footer, params(MATCH, dp(20), 0, 0, 0, 2));
    }

    private void renderTab() {
        if (tabContent == null) return;
        tabContent.removeAllViews();
        gauges.clear();
        if ("config".equals(currentTab)) {
            renderConfigTab();
        } else if ("info".equals(currentTab)) {
            renderInfoTab();
        } else {
            renderConnectionTab();
        }
        updateStatus();
    }

    private void renderConnectionTab() {
        Panel importPanel = panel();
        addSectionHeading(importPanel, "LOCAL CONFIG ENGINE", "PRIVATE · PARSED ON DEVICE");
        add(importPanel, label("IMPORT A SERVER LINK OR XRAY JSON", 10, INK, true), params(MATCH, WRAP, 0, 0, 0, 6));

        configInput = new EditText(this);
        configInput.setTextColor(TEXT);
        configInput.setHintTextColor(Color.rgb(173, 178, 177));
        configInput.setHint("vless://  vmess://  trojan://  ss://  or Xray JSON");
        configInput.setTextSize(13);
        configInput.setGravity(Gravity.TOP | Gravity.START);
        configInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        configInput.setMinLines(2);
        configInput.setMaxLines(4);
        configInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        configInput.setBackground(round(BACKGROUND, BEVEL, 4));
        importPanel.addView(configInput, params(MATCH, WRAP, 0, 2, 0, 8));

        LinearLayout importActions = horizontal();
        TextView paste = keycap("PASTE");
        paste.setOnClickListener(view -> pasteConfiguration());
        importActions.addView(paste, new LinearLayout.LayoutParams(0, dp(42), 1));
        add(importActions, spacer(8), new LinearLayout.LayoutParams(dp(8), 1));
        TextView save = keycap("SAVE SERVER");
        save.setTextColor(COPPER_DARK);
        save.setOnClickListener(view -> importConfiguration());
        importActions.addView(save, new LinearLayout.LayoutParams(0, dp(42), 1));
        importPanel.addView(importActions, params(MATCH, dp(42), 0, 0, 0, 4));
        TextView privacy = label("Credentials stay on this device. No server credentials are sent to AI.", 9, INK, false);
        privacy.setPadding(dp(2), dp(8), 0, 0);
        importPanel.addView(privacy, params(MATCH, WRAP, 0, 0, 0, 0));
        addPanel(importPanel, 0, 0, 0, 12);

        LinearLayout section = horizontal();
        add(section, label("◉  ACTIVE SERVER", 10, MUTED, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView manage = label("MANAGE SERVERS  ›", 9, COPPER_LIGHT, true);
        manage.setOnClickListener(view -> showServerManager());
        section.addView(manage, wrap());
        tabContent.addView(section, params(MATCH, dp(26), 2, 0, 2, 6));

        Panel activeServer = panel();
        LinearLayout activeRow = horizontal();
        activeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView flag = label("◎", 28, COPPER_DARK, true);
        flag.setGravity(Gravity.CENTER);
        add(activeRow, flag, params(dp(48), dp(48), 0, 0, 10, 0));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        serverNameView = label("No server selected", 15, INK, true);
        serverDetailView = label("Import a private server configuration", 10, Color.rgb(73, 77, 78), false);
        add(details, serverNameView, params(MATCH, WRAP, 0, 0, 0, 4));
        add(details, serverDetailView, wrap());
        activeRow.addView(details, new LinearLayout.LayoutParams(0, WRAP, 1));
        add(activeRow, label(selectedServer() == null ? "—" : protocolLabel(selectedServer().protocol), 10, COPPER_DARK, true), wrap());
        activeServer.addView(activeRow, params(MATCH, WRAP, 0, 0, 0, 0));
        activeServer.setOnClickListener(view -> showServerManager());
        addPanel(activeServer, 0, 0, 0, 14);

        Panel deck = panel();
        addSectionHeading(deck, "LIVE CONNECTION INSTRUMENTS", "SERVICE-REPORTED");
        LinearLayout instruments = horizontal();
        instruments.setGravity(Gravity.CENTER);
        InstrumentGauge pingGauge = new InstrumentGauge(this, "LATENCY PING", "ms", 180, 0);
        InstrumentGauge speedGauge = new InstrumentGauge(this, "SPEED RATE", "Mbps", 120, 0);
        gauges.add(pingGauge);
        gauges.add(speedGauge);
        instruments.addView(pingGauge, new LinearLayout.LayoutParams(0, dp(148), 1));

        LinearLayout powerColumn = new LinearLayout(this);
        powerColumn.setGravity(Gravity.CENTER);
        powerColumn.setOrientation(LinearLayout.VERTICAL);
        powerButton = new Button(this);
        powerButton.setText("⏻");
        powerButton.setTextSize(42);
        powerButton.setTextColor(COPPER_DARK);
        powerButton.setAllCaps(false);
        powerButton.setBackground(powerHousing());
        powerButton.setElevation(dp(4));
        powerButton.setContentDescription("Connect or disconnect VPN");
        powerButton.setOnClickListener(view -> onPowerClicked());
        add(powerColumn, powerButton, params(dp(88), dp(88), 0, 0, 0, 8));
        connectionStatusView = label("TAP TO CONNECT", 8, COPPER_DARK, true);
        connectionStatusView.setGravity(Gravity.CENTER);
        add(powerColumn, connectionStatusView, params(MATCH, WRAP, 0, 0, 0, 3));
        connectionDurationView = label("00:00:00", 9, INK, true);
        connectionDurationView.setGravity(Gravity.CENTER);
        add(powerColumn, connectionDurationView, wrap());
        instruments.addView(powerColumn, new LinearLayout.LayoutParams(0, WRAP, 1));
        instruments.addView(speedGauge, new LinearLayout.LayoutParams(0, dp(148), 1));
        deck.addView(instruments, params(MATCH, dp(155), 0, 4, 0, 6));

        View deckSeparator = new View(this);
        deckSeparator.setBackgroundColor(PLATE_SHADE);
        deck.addView(deckSeparator, params(MATCH, dp(1), 0, 0, 0, 7));
        LinearLayout connectionState = horizontal();
        TextView led = label("●", 12, RED, true);
        led.setTag("connection-led");
        add(connectionState, led, params(dp(20), WRAP, 0, 0, 3, 0));
        TextView stateText = label("Your VPN tunnel is disconnected", 10, INK, false);
        stateText.setTag("connection-description");
        connectionState.addView(stateText, new LinearLayout.LayoutParams(0, WRAP, 1));
        add(connectionState, label("NATIVE VPN", 8, COPPER_DARK, true), wrap());
        deck.addView(connectionState, params(MATCH, WRAP, 0, 0, 0, 0));
        connectionErrorView = label("", 10, RED, false);
        deck.addView(connectionErrorView, params(MATCH, WRAP, 0, 6, 0, 0));
        addPanel(deck, 0, 0, 0, 12);

        Panel telemetry = panel();
        addSectionHeading(telemetry, "SESSION TELEMETRY", "LIVE · NATIVE CORE");
        LinearLayout firstRow = horizontal();
        downloadValueView = telemetryCell(firstRow, "↓  DOWNLOAD", "0.0 Mbps");
        uploadValueView = telemetryCell(firstRow, "↑  UPLOAD", "0.0 Mbps");
        LinearLayout secondRow = horizontal();
        receivedValueView = telemetryCell(secondRow, "DATA RECEIVED", "0.0 MB");
        sentValueView = telemetryCell(secondRow, "DATA SENT", "0.0 MB");
        telemetry.addView(firstRow, params(MATCH, dp(56), 0, 2, 0, 0));
        telemetry.addView(secondRow, params(MATCH, dp(56), 0, 0, 0, 0));
        addPanel(telemetry, 0, 0, 0, 8);

        LinearLayout trust = horizontal();
        add(trust, label("◇  ZERO LOGS. ZERO COMPROMISE.", 8, MUTED, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        add(trust, label("LOCAL CONFIG ENGINE", 8, MUTED, true), wrap());
        tabContent.addView(trust, params(MATCH, WRAP, 2, 4, 2, 8));
    }

    private void renderConfigTab() {
        Panel network = panel();
        addSectionHeading(network, "DEVICE & NETWORK", "LOCAL CONFIGURATION");
        addSettingRow(network, "Android VPN lockdown", "Configure Always-on VPN and block connections in Android settings.", null, "OPEN SETTINGS", this::openVpnSettings);

        TextView dnsTitle = label("DNS PROVIDER", 10, INK, true);
        network.addView(dnsTitle, params(MATCH, WRAP, 0, 10, 0, 4));
        Spinner dns = spinner(new String[] {"Cloudflare · 1.1.1.1", "Google · 8.8.8.8", "AdGuard · 94.140.14.14", "Quad9 · 9.9.9.9"});
        String[] dnsValues = {"1.1.1.1", "8.8.8.8", "94.140.14.14", "9.9.9.9"};
        dns.setSelection(indexOf(dnsValues, savedDns));
        dns.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                savedDns = dnsValues[position];
                preferences.edit().putString(PREF_DNS, savedDns).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        network.addView(dns, params(MATCH, dp(46), 0, 0, 0, 12));

        TextView routingTitle = label("ROUTING RULES", 10, INK, true);
        network.addView(routingTitle, params(MATCH, WRAP, 0, 0, 0, 4));
        String[] routingOptions = {"Global", "Bypass LAN", "Bypass Mainland/Domestic", "Rule-based"};
        Spinner routing = spinner(routingOptions);
        routing.setSelection(indexOf(routingOptions, savedRouting));
        routing.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                savedRouting = routingOptions[position];
                preferences.edit().putString(PREF_ROUTING, savedRouting).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        network.addView(routing, params(MATCH, dp(46), 0, 0, 0, 14));
        addDivider(network);
        add(network, label("ENABLED PROTOCOLS", 10, INK, true), params(MATCH, WRAP, 0, 10, 0, 5));
        String[] protocolKeys = {"vless", "vmess", "trojan", "shadowsocks"};
        for (String protocol : protocolKeys) {
            boolean enabled = isProtocolEnabled(protocol);
            addProtocolSwitch(network, protocol, enabled);
        }
        addDivider(network);
        LinearLayout ports = horizontal();
        add(ports, label("EXPORTED PROXY PORTS", 9, INK, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        add(ports, label("SOCKS5  10808    HTTP  10809", 9, INK, true), wrap());
        network.addView(ports, params(MATCH, dp(36), 0, 12, 0, 4));
        TextView note = label("DNS is applied by the VPN service. Always-on and lockdown are controlled by Android.", 9, Color.rgb(75, 79, 80), false);
        network.addView(note, params(MATCH, WRAP, 0, 6, 0, 0));
        addPanel(network, 0, 0, 0, 12);

        Panel export = panel();
        addSectionHeading(export, "CONFIGURATION TOOLS", "CREDENTIALS STAY LOCAL");
        TextView exportJson = keycap("EXPORT XRAY JSON");
        exportJson.setOnClickListener(view -> exportConfiguration());
        export.addView(exportJson, params(MATCH, dp(46), 0, 4, 0, 7));
        TextView copy = keycap("COPY CONFIGURATION");
        copy.setOnClickListener(view -> copyConfiguration());
        export.addView(copy, params(MATCH, dp(46), 0, 0, 0, 0));
        addPanel(export, 0, 0, 0, 12);

        Panel appearance = panel();
        addSectionHeading(appearance, "MATERIAL FINISH", "NETCON INSTRUMENT");
        LinearLayout swatches = horizontal();
        add(swatches, swatch("STEEL", STEEL), new LinearLayout.LayoutParams(0, dp(52), 1));
        add(swatches, swatch("SILVER", PLATE_MID), new LinearLayout.LayoutParams(0, dp(52), 1));
        add(swatches, swatch("COPPER", COPPER), new LinearLayout.LayoutParams(0, dp(52), 1));
        add(swatches, swatch("GLOW", COPPER_LIGHT), new LinearLayout.LayoutParams(0, dp(52), 1));
        appearance.addView(swatches, params(MATCH, dp(52), 0, 4, 0, 0));
        addPanel(appearance, 0, 0, 0, 12);
    }

    private void renderInfoTab() {
        Panel diagnostics = panel();
        addSectionHeading(diagnostics, "CLIENT DIAGNOSTICS", "SYSTEM INFORMATION");
        addDiagnostic(diagnostics, "APP VERSION", "2.4.1 / ANDROID");
        addDiagnostic(diagnostics, "CLIENT MODE", "NATIVE ANDROID VPN");
        addDiagnostic(diagnostics, "VPN SERVICE", serviceStatus());
        addDiagnostic(diagnostics, "PROTOCOLS", "VLESS · VMESS · TROJAN · SS");
        addDiagnostic(diagnostics, "CLIENT IP", "Not measured");
        addDiagnostic(diagnostics, "SERVER EXIT IP", "Not measured");
        SavedServer selected = selectedServer();
        addDiagnostic(diagnostics, "ACTIVE SERVER", selected == null ? "No server selected" : selected.name);
        addPanel(diagnostics, 0, 0, 0, 12);

        Panel privacy = panel();
        addSectionHeading(privacy, "ZERO-LOG & DATA SAFETY", "ON-DEVICE IMPORT");
        add(privacy, label("YOUR CONNECTION. YOUR CONTROL.", 15, INK, true), params(MATCH, WRAP, 0, 8, 0, 6));
        add(privacy, label("Configuration links are parsed and stored on this device. Netcon does not send server credentials to AI services. The Android service reports connection state and traffic counters locally.", 11, Color.rgb(66, 70, 71), false), params(MATCH, WRAP, 0, 0, 0, 10));
        add(privacy, label("✓  LOCAL PARSING      ✓  NO AD SDK      ✓  NO SERVER CREDENTIAL RELAY", 9, COPPER_DARK, true), wrap());
        addPanel(privacy, 0, 0, 0, 12);

        Panel help = panel();
        addSectionHeading(help, "ANDROID VPN SERVICE", "PROTECTION NOTES");
        add(help, label("Only imported working server configurations can connect. Demo endpoints are not included. For lockdown, enable Always-on VPN and Block connections without VPN in Android system settings. An app status alone does not prove traffic is routed or leak-free.", 11, Color.rgb(66, 70, 71), false), params(MATCH, WRAP, 0, 8, 0, 12));
        TextView settings = keycap("OPEN ANDROID VPN SETTINGS");
        settings.setOnClickListener(view -> openVpnSettings());
        help.addView(settings, params(MATCH, dp(44), 0, 0, 0, 0));
        addPanel(help, 0, 0, 0, 12);
    }

    private void addProtocolSwitch(LinearLayout parent, String protocol, boolean initialValue) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        String title = protocol.equals("shadowsocks") ? "SHADOWSOCKS (SS)" : protocol.toUpperCase(Locale.ROOT);
        add(row, label(title, 11, INK, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setButtonTintList(ColorStateList.valueOf(COPPER));
        toggle.setChecked(initialValue);
        toggle.setOnCheckedChangeListener((button, checked) -> saveProtocol(protocol, checked));
        row.addView(toggle, wrap());
        parent.addView(row, params(MATCH, dp(42), 0, 0, 0, 0));
    }

    private void addSettingRow(LinearLayout parent, String title, String subtitle, String value, String action, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        LinearLayout top = horizontal();
        add(top, label(title.toUpperCase(Locale.ROOT), 10, INK, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView button = keycap(action);
        button.setTextSize(8);
        top.addView(button, wrap());
        button.setOnClickListener(view -> onClick.run());
        row.addView(top, params(MATCH, dp(34), 0, 0, 0, 4));
        add(row, label(subtitle, 9, Color.rgb(73, 77, 78), false), wrap());
        parent.addView(row, params(MATCH, WRAP, 0, 2, 0, 4));
        addDivider(parent);
    }

    private TextView telemetryCell(LinearLayout parent, String title, String value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(dp(8), 0, dp(5), 0);
        TextView heading = label(title, 8, INK, true);
        TextView reading = label(value, 14, COPPER_DARK, true);
        cell.addView(heading, params(MATCH, WRAP, 0, 0, 0, 3));
        cell.addView(reading, wrap());
        parent.addView(cell, new LinearLayout.LayoutParams(0, MATCH, 1));
        return reading;
    }

    private void addDiagnostic(LinearLayout parent, String key, String value) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        add(row, label(key, 9, INK, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView detail = label(value, 10, Color.rgb(56, 60, 61), true);
        detail.setGravity(Gravity.RIGHT);
        detail.setMaxWidth(dp(180));
        row.addView(detail, wrap());
        parent.addView(row, params(MATCH, WRAP, 0, 0, 0, 0));
        addDivider(parent);
    }

    private void addSectionHeading(LinearLayout parent, String title, String meta) {
        LinearLayout heading = horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(title, 9, INK, true);
        name.setLetterSpacing(0.06f);
        heading.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView metadata = label(meta, 7, Color.rgb(76, 80, 81), true);
        metadata.setGravity(Gravity.RIGHT);
        heading.addView(metadata, wrap());
        parent.addView(heading, params(MATCH, dp(22), 0, 0, 0, 10));
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(137, 143, 145));
        parent.addView(divider, params(MATCH, dp(1), 0, 2, 0, 2));
    }

    private void addPanel(Panel panel, int left, int top, int right, int bottom) {
        tabContent.addView(panel, params(MATCH, WRAP, left, top, right, bottom));
    }

    private Panel panel() {
        return new Panel(this);
    }

    private void loadSavedServers() {
        selectedServerId = preferences.getString(PREF_SELECTED_SERVER, "");
        String encoded = preferences.getString(PREF_SERVERS, "[]");
        try {
            JSONArray saved = new JSONArray(encoded);
            for (int i = 0; i < saved.length(); i++) {
                JSONObject row = saved.getJSONObject(i);
                servers.add(new SavedServer(
                    row.optString("id", UUID.randomUUID().toString()),
                    row.optString("name", "Saved server"),
                    row.optString("protocol", "Xray"),
                    row.optString("address", ""),
                    row.getString("config")
                ));
            }
        } catch (Exception error) {
            android.util.Log.e("Netcon", "Unable to read saved server list.", error);
            servers.clear();
        }
        if (servers.isEmpty()) {
            String legacyConfig = preferences.getString(CONFIG_KEY, null);
            if (legacyConfig != null && !legacyConfig.trim().isEmpty()) {
                try {
                    NativeXrayConfig.ImportedServer imported = NativeXrayConfig.fromInput(legacyConfig);
                    String name = preferences.getString(SERVER_NAME_KEY, imported.name);
                    addSavedServer(new SavedServer(UUID.randomUUID().toString(), name, imported.protocol, imported.address, legacyConfig));
                } catch (Exception error) {
                    android.util.Log.e("Netcon", "Unable to migrate the previously saved configuration.", error);
                }
            }
        }
        if (selectedServer() == null && !servers.isEmpty()) {
            selectedServerId = servers.get(0).id;
            preferences.edit().putString(PREF_SELECTED_SERVER, selectedServerId).apply();
        }
    }

    private void loadSettings() {
        savedDns = preferences.getString(PREF_DNS, "1.1.1.1");
        savedRouting = preferences.getString(PREF_ROUTING, "Bypass LAN");
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String sharedText = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            sharedText = intent.getData().toString();
        }
        if (sharedText != null && !sharedText.trim().isEmpty()) {
            currentTab = "connection";
            renderTab();
            configInput.setText(sharedText.trim());
            importConfiguration();
        }
    }

    private void importConfiguration() {
        String raw = configInput == null ? "" : configInput.getText().toString().trim();
        try {
            if (raw.length() > 256_000) {
                throw new IllegalArgumentException("Configuration text must be 256 KB or smaller.");
            }
            NativeXrayConfig.ImportedServer imported = NativeXrayConfig.fromInput(raw);
            SavedServer server = new SavedServer(UUID.randomUUID().toString(), imported.name, imported.protocol, imported.address, imported.config.toString());
            addSavedServer(server);
            configInput.setText("");
            hideKeyboard();
            Toast.makeText(this, "Configuration saved privately on this device.", Toast.LENGTH_SHORT).show();
            renderTab();
        } catch (Exception error) {
            showError(error.getMessage() == null ? "Invalid server configuration." : error.getMessage());
        }
    }

    private void addSavedServer(SavedServer server) {
        servers.removeIf(existing -> existing.address.equals(server.address) && existing.protocol.equals(server.protocol) && !server.address.isEmpty());
        servers.add(server);
        selectedServerId = server.id;
        persistServers();
    }

    private void persistServers() {
        JSONArray saved = new JSONArray();
        for (SavedServer server : servers) {
            JSONObject row = new JSONObject();
            try {
                row.put("id", server.id);
                row.put("name", server.name);
                row.put("protocol", server.protocol);
                row.put("address", server.address);
                row.put("config", server.config);
                saved.put(row);
            } catch (Exception error) {
                android.util.Log.e("Netcon", "Unable to encode saved server metadata.", error);
            }
        }
        preferences.edit()
            .putString(PREF_SERVERS, saved.toString())
            .putString(PREF_SELECTED_SERVER, selectedServerId)
            .putString(CONFIG_KEY, selectedServer() == null ? "" : selectedServer().config)
            .putString(SERVER_NAME_KEY, selectedServer() == null ? "" : selectedServer().name)
            .apply();
    }

    private SavedServer selectedServer() {
        for (SavedServer server : servers) {
            if (server.id.equals(selectedServerId)) return server;
        }
        return null;
    }

    private void showServerManager() {
        if (servers.isEmpty()) {
            showImportDialog();
            return;
        }
        String[] names = new String[servers.size()];
        int selectedIndex = 0;
        for (int i = 0; i < servers.size(); i++) {
            SavedServer server = servers.get(i);
            names[i] = server.name + "   ·   " + protocolLabel(server.protocol)
                + (server.address.isEmpty() ? "" : "   ·   " + server.address);
            if (server.id.equals(selectedServerId)) selectedIndex = i;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("SERVER MANAGEMENT")
            .setSingleChoiceItems(names, selectedIndex, (list, which) -> {
                selectedServerId = servers.get(which).id;
                persistServers();
                list.dismiss();
                renderTab();
            })
            .setPositiveButton("IMPORT SERVER", (d, which) -> showImportDialog())
            .setNeutralButton("REMOVE CURRENT", null)
            .setNegativeButton("CLOSE", null)
            .create();
        dialog.setOnShowListener(ignored -> {
            Button remove = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            remove.setOnClickListener(view -> {
                SavedServer selected = selectedServer();
                if (selected == null) return;
                new AlertDialog.Builder(this)
                    .setTitle("Remove server?")
                    .setMessage("Remove " + selected.name + " from this device?")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("REMOVE", (confirm, which) -> {
                        if ("connected".equals(NetconVpnService.getStatus().state)) disconnectVpn();
                        servers.removeIf(server -> server.id.equals(selected.id));
                        selectedServerId = servers.isEmpty() ? "" : servers.get(0).id;
                        persistServers();
                        dialog.dismiss();
                        renderTab();
                    })
                    .show();
            });
            styleDialog(dialog);
        });
        dialog.show();
    }

    private void showImportDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(8));
        TextView hint = label("Paste a VLESS, VMess, Trojan, Shadowsocks link or Xray JSON. Configuration is parsed locally.", 11, MUTED, false);
        content.addView(hint, params(MATCH, WRAP, 0, 0, 0, 10));
        EditText input = new EditText(this);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setHint("Server configuration");
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(3);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(round(BACKGROUND, BEVEL, 4));
        content.addView(input, params(MATCH, WRAP, 0, 0, 0, 0));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("ADD SERVER")
            .setView(content)
            .setPositiveButton("SAVE", null)
            .setNegativeButton("CANCEL", null)
            .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String raw = input.getText().toString().trim();
                try {
                    if (raw.length() > 256_000) throw new IllegalArgumentException("Configuration text must be 256 KB or smaller.");
                    NativeXrayConfig.ImportedServer imported = NativeXrayConfig.fromInput(raw);
                    addSavedServer(new SavedServer(UUID.randomUUID().toString(), imported.name, imported.protocol, imported.address, imported.config.toString()));
                    dialog.dismiss();
                    renderTab();
                } catch (Exception error) {
                    input.setError(error.getMessage() == null ? "Invalid server configuration." : error.getMessage());
                }
            });
            styleDialog(dialog);
        });
        dialog.show();
    }

    private void onPowerClicked() {
        connectionError = "";
        NetconVpnService.Status status = NetconVpnService.getStatus();
        if ("connected".equals(status.state) || "connecting".equals(status.state) || "error".equals(status.state)) {
            disconnectVpn();
            return;
        }
        SavedServer server = selectedServer();
        if (server == null) {
            showServerManager();
            return;
        }
        if (!isProtocolEnabled(server.protocol)) {
            showError("This protocol is disabled. Enable it in CONFIG before connecting.");
            updateStatus();
            return;
        }
        try {
            JSONObject config = new JSONObject(server.config);
            applyConnectionSettings(config);
            String connectionConfig = config.toString();
            Intent permissionIntent = VpnService.prepare(this);
            if (permissionIntent != null) {
                pendingConfig = connectionConfig;
                startActivityForResult(permissionIntent, VPN_PERMISSION_REQUEST);
            } else {
                startVpn(connectionConfig);
            }
        } catch (Exception error) {
            showError(error.getMessage() == null ? "Unable to prepare this server configuration." : error.getMessage());
        }
    }

    private void applyConnectionSettings(JSONObject config) throws Exception {
        config.put("dns", new JSONObject().put("servers", new JSONArray().put(savedDns)));
        JSONObject routing = config.optJSONObject("routing");
        if (routing == null) {
            routing = new JSONObject();
            config.put("routing", routing);
        }
        routing.put("domainStrategy", "IPIfNonMatch");
        JSONArray rules = new JSONArray();
        if (!"Global".equals(savedRouting)) {
            rules.put(new JSONObject().put("type", "field").put("ip", new JSONArray().put("geoip:private")).put("outboundTag", "direct"));
        }
        if ("Bypass Mainland/Domestic".equals(savedRouting)) {
            rules.put(new JSONObject().put("type", "field").put("ip", new JSONArray().put("geoip:cn")).put("outboundTag", "direct"));
            rules.put(new JSONObject().put("type", "field").put("domain", new JSONArray().put("geosite:cn")).put("outboundTag", "direct"));
        } else if ("Rule-based".equals(savedRouting)) {
            rules.put(new JSONObject().put("type", "field").put("domain", new JSONArray().put("geosite:category-ads-all")).put("outboundTag", "block"));
        }
        routing.put("rules", rules);
    }

    private void startVpn(String config) {
        try {
            Intent intent = new Intent(this, NetconVpnService.class)
                .setAction(NetconVpnService.ACTION_CONNECT)
                .putExtra(NetconVpnService.EXTRA_CONFIG, config);
            ContextCompat.startForegroundService(this, intent);
            connectionError = "";
        } catch (Exception error) {
            showError("Unable to start Android VPN service: " + error.getMessage());
        }
    }

    private void disconnectVpn() {
        try {
            startService(new Intent(this, NetconVpnService.class).setAction(NetconVpnService.ACTION_DISCONNECT));
            connectionError = "";
        } catch (Exception error) {
            showError("Unable to stop Android VPN service: " + error.getMessage());
        }
    }

    private void updateStatus() {
        if (statusBadge == null) return;
        NetconVpnService.Status status = NetconVpnService.getStatus();
        String state = status.state;
        int statusColor;
        String badge;
        String powerLabel;
        if ("connected".equals(state)) {
            statusColor = GREEN;
            badge = "●  CONNECTED";
            powerLabel = "TAP TO DISCONNECT";
        } else if ("connecting".equals(state)) {
            statusColor = COPPER;
            badge = "●  CONNECTING";
            powerLabel = "STARTING NATIVE VPN";
        } else if ("error".equals(state)) {
            statusColor = RED;
            badge = "●  VPN ERROR";
            powerLabel = "VPN ERROR · TAP TO STOP";
            connectionError = status.error;
        } else {
            statusColor = RED;
            badge = "●  OFFLINE";
            powerLabel = "TAP TO CONNECT";
        }
        statusBadge.setText(badge);
        statusBadge.setTextColor(statusColor);
        if (connectionStatusView != null) connectionStatusView.setText(powerLabel);
        if (connectionDurationView != null) connectionDurationView.setText(formatDuration(status.elapsedSeconds));
        if (serverNameView != null) {
            SavedServer server = selectedServer();
            serverNameView.setText(server == null ? "No server selected" : server.name);
            serverDetailView.setText(server == null
                ? "Import a private server configuration"
                : protocolLabel(server.protocol) + "  ·  " + (server.address.isEmpty() ? "Imported Xray configuration" : server.address));
        }
        if (powerButton != null) {
            powerButton.setTextColor("connected".equals(state) ? GREEN : COPPER_DARK);
            powerButton.setBackground(powerHousing("connected".equals(state)));
        }
        if (downloadValueView != null) downloadValueView.setText(String.format(Locale.US, "%.1f Mbps", downloadMbps));
        if (uploadValueView != null) uploadValueView.setText(String.format(Locale.US, "%.1f Mbps", uploadMbps));
        if (receivedValueView != null) receivedValueView.setText(String.format(Locale.US, "%.1f MB", status.receivedBytes / 1_000_000d));
        if (sentValueView != null) sentValueView.setText(String.format(Locale.US, "%.1f MB", status.sentBytes / 1_000_000d));
        if (connectionErrorView != null) connectionErrorView.setText(connectionError);
        View led = tabContent == null ? null : tabContent.findViewWithTag("connection-led");
        if (led instanceof TextView) ((TextView) led).setTextColor("connected".equals(state) ? GREEN : "error".equals(state) ? RED : PLATE_SHADE);
        View stateDescription = tabContent == null ? null : tabContent.findViewWithTag("connection-description");
        if (stateDescription instanceof TextView) {
            ((TextView) stateDescription).setText(
                "connected".equals(state) ? "Native VPN tunnel connected"
                    : "connecting".equals(state) ? "Starting Android VPN service…"
                    : "error".equals(state) ? "Android VPN service error"
                    : "Your VPN tunnel is disconnected");
        }

        long now = android.os.SystemClock.elapsedRealtime();
        if (lastSampleAt > 0 && now > lastSampleAt) {
            long interval = now - lastSampleAt;
            downloadMbps = Math.max(0, status.receivedBytes - lastReceivedBytes) * 8d / interval / 1000d;
            uploadMbps = Math.max(0, status.sentBytes - lastSentBytes) * 8d / interval / 1000d;
        }
        lastReceivedBytes = status.receivedBytes;
        lastSentBytes = status.sentBytes;
        lastSampleAt = now;
        for (InstrumentGauge gauge : gauges) {
            gauge.setValue(gauge.unit.equals("Mbps") ? downloadMbps + uploadMbps : 0);
        }
    }

    private void openVpnSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
        } catch (Exception error) {
            showError("Unable to open Android VPN settings: " + error.getMessage());
        }
    }

    private void exportConfiguration() {
        SavedServer server = selectedServer();
        if (server == null) {
            showError("Save a server before exporting its configuration.");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/json");
        send.putExtra(Intent.EXTRA_TEXT, server.config);
        startActivity(Intent.createChooser(send, "Export Xray configuration"));
    }

    private void copyConfiguration() {
        SavedServer server = selectedServer();
        if (server == null) {
            showError("Save a server before copying its configuration.");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            showError("Android clipboard is unavailable.");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Xray configuration", server.config));
        Toast.makeText(this, "Configuration copied to clipboard.", Toast.LENGTH_SHORT).show();
    }

    private boolean isProtocolEnabled(String protocol) {
        String enabled = preferences.getString(PREF_PROTOCOLS, "vless,vmess,trojan,shadowsocks");
        return ("," + enabled + ",").contains("," + protocol + ",");
    }

    private void saveProtocol(String protocol, boolean enabled) {
        String current = preferences.getString(PREF_PROTOCOLS, "vless,vmess,trojan,shadowsocks");
        ArrayList<String> values = new ArrayList<>();
        for (String item : current.split(",")) if (!item.equals(protocol) && !item.isEmpty()) values.add(item);
        if (enabled) values.add(protocol);
        preferences.edit().putString(PREF_PROTOCOLS, android.text.TextUtils.join(",", values)).apply();
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
        configInput.setText(text);
        configInput.setSelection(configInput.length());
    }

    private void showError(String message) {
        connectionError = message;
        if (connectionErrorView != null) connectionErrorView.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void hideKeyboard() {
        if (configInput == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(configInput.getWindowToken(), 0);
        configInput.clearFocus();
    }

    private Spinner spinner(String[] items) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(INK);
                view.setTextSize(12);
                view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                view.setPadding(dp(12), dp(6), dp(12), dp(6));
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(INK);
                view.setTextSize(13);
                view.setPadding(dp(14), dp(12), dp(14), dp(12));
                view.setBackgroundColor(PLATE_MID);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(round(PLATE_MID, PLATE_SHADE, 4));
        return spinner;
    }

    private TextView swatch(String title, int color) {
        TextView view = label(title, 8, Color.BLACK, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(color, PLATE_SHADE, 3));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(44), 1);
        layout.setMargins(dp(2), 0, dp(2), 0);
        view.setLayoutParams(layout);
        return view;
    }

    private TextView keycap(String title) {
        TextView view = label(title, 9, INK, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(7), dp(10), dp(7));
        view.setBackground(keyBackground());
        view.setElevation(dp(2));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private TextView label(String value, int size, int color, boolean mono) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(mono ? "monospace" : "sans-serif", mono ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private void add(LinearLayout parent, View child, ViewGroup.LayoutParams layout) {
        parent.addView(child, layout);
    }

    private View spacer(int size) {
        return new View(this);
    }

    private void styleDialog(AlertDialog dialog) {
        dialog.getWindow().setBackgroundDrawable(round(PLATE_MID, BEVEL, 6));
    }

    private GradientDrawable chassisBackground() {
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {PLATE_SHADE, PLATE_LIGHT, PLATE_MID, PLATE_LIGHT, PLATE_SHADE}
        );
        background.setCornerRadius(dp(9));
        background.setStroke(dp(2), BEVEL);
        return background;
    }

    private GradientDrawable metalTop() {
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {PLATE_LIGHT, PLATE_MID, PLATE_SHADE}
        );
        background.setCornerRadius(dp(4));
        background.setStroke(dp(1), BEVEL);
        return background;
    }

    private GradientDrawable engravedStatusBackground() {
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {PLATE_MID, PLATE_SHADE, PLATE_MID}
        );
        background.setCornerRadius(dp(18));
        background.setStroke(dp(2), DEEP_INK);
        return background;
    }

    private GradientDrawable round(int color, int stroke, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        background.setStroke(dp(1), stroke);
        return background;
    }

    private GradientDrawable keyBackground() {
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {PLATE_LIGHT, PLATE_MID, PLATE_SHADE}
        );
        background.setCornerRadius(dp(4));
        background.setStroke(dp(1), BEVEL);
        return background;
    }

    private GradientDrawable powerHousing() {
        return powerHousing(false);
    }

    private GradientDrawable powerHousing(boolean connected) {
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {PLATE_LIGHT, PLATE_MID, PLATE_SHADE, PLATE_MID}
        );
        background.setShape(GradientDrawable.OVAL);
        background.setStroke(dp(connected ? 3 : 2), connected ? GREEN : BEVEL);
        return background;
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    private String serviceStatus() {
        String state = NetconVpnService.getStatus().state;
        if ("connected".equals(state)) return "CONNECTED";
        if ("connecting".equals(state)) return "CONNECTING";
        if ("error".equals(state)) return "ERROR";
        return "AVAILABLE · DISCONNECTED";
    }

    private String formatDuration(long seconds) {
        return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    private String protocolLabel(String protocol) {
        return "shadowsocks".equals(protocol) ? "SS" : protocol.toUpperCase(Locale.ROOT);
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(width, height);
        layout.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return layout;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(WRAP, WRAP);
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class Panel extends LinearLayout {
        private final Paint grain = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint screw = new Paint(Paint.ANTI_ALIAS_FLAG);

        Panel(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(14), dp(14), dp(14), dp(12));
            setWillNotDraw(false);
            setBackground(round(PLATE_MID, PLATE_SHADE, 7));
            setElevation(dp(2));
            grain.setStrokeWidth(getResources().getDisplayMetrics().density * 0.6f);
            screw.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            for (int y = dp(2); y < getHeight(); y += dp(4)) {
                grain.setColor((y / dp(4)) % 2 == 0 ? 0x1effffff : 0x10000000);
                canvas.drawLine(dp(8), y, width - dp(8), y, grain);
            }
            drawScrew(canvas, dp(10), dp(10));
            drawScrew(canvas, width - dp(10), dp(10));
        }

        private void drawScrew(Canvas canvas, float x, float y) {
            screw.setShader(new LinearGradient(x - dp(4), y - dp(4), x + dp(4), y + dp(4),
                new int[] {PLATE_LIGHT, BEVEL, DEEP_INK, PLATE_MID}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, dp(3), screw);
            screw.setShader(null);
            screw.setColor(DEEP_INK);
            screw.setStrokeWidth(dp(1));
            canvas.drawLine(x - dp(2), y + dp(1), x + dp(2), y - dp(1), screw);
            screw.setStrokeWidth(0);
        }
    }

    private static final class SavedServer {
        final String id;
        final String name;
        final String protocol;
        final String address;
        final String config;

        SavedServer(String id, String name, String protocol, String address, String config) {
            this.id = id;
            this.name = name;
            this.protocol = protocol;
            this.address = address;
            this.config = config;
        }
    }

    private final class InstrumentGauge extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String title;
        private final String unit;
        private final float max;
        private float value;

        InstrumentGauge(Context context, String title, String unit, float max, float value) {
            super(context);
            this.title = title;
            this.unit = unit;
            this.max = max;
            this.value = value;
            setContentDescription(title + ", not measured");
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        void setValue(double value) {
            this.value = (float) Math.max(0, Math.min(max, value));
            setContentDescription(title + ": " + String.format(Locale.US, "%.1f %s", this.value, unit));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2f;
            float centerY = dp(70);
            float radius = Math.min(getWidth() * 0.46f, dp(61));
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(centerX - radius, centerY - radius, centerX + radius, centerY + radius,
                new int[] {PLATE_SHADE, PLATE_LIGHT, PLATE_MID, PLATE_LIGHT, PLATE_SHADE}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(centerX, centerY, radius, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(PLATE_LIGHT);
            canvas.drawCircle(centerX, centerY, radius - dp(2), paint);
            paint.setStrokeWidth(dp(1));
            paint.setColor(BEVEL);
            canvas.drawCircle(centerX, centerY, radius - dp(7), paint);

            float dialRadius = radius - dp(12);
            paint.setStrokeWidth(dp(2));
            for (int i = 0; i <= 36; i++) {
                double angle = Math.toRadians(135 + i * 7.5);
                boolean major = i % 6 == 0;
                float inner = dialRadius - dp(major ? 10 : 5);
                float outer = dialRadius - dp(1);
                paint.setColor(major ? INK : PLATE_SHADE);
                canvas.drawLine(
                    centerX + (float) Math.cos(angle) * inner,
                    centerY + (float) Math.sin(angle) * inner,
                    centerX + (float) Math.cos(angle) * outer,
                    centerY + (float) Math.sin(angle) * outer,
                    paint
                );
                if (major) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
                    paint.setTextSize(dp(7));
                    paint.setTextAlign(Paint.Align.CENTER);
                    float labelRadius = dialRadius - dp(17);
                    canvas.drawText(String.valueOf(Math.round(max * i / 36f)),
                        centerX + (float) Math.cos(angle) * labelRadius,
                        centerY + (float) Math.sin(angle) * labelRadius + dp(2),
                        paint);
                    paint.setStyle(Paint.Style.STROKE);
                }
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            paint.setColor(COPPER_DARK);
            paint.setTextSize(dp(7));
            canvas.drawText("NETCON", centerX, centerY - dp(19), paint);
            paint.setColor(INK);
            paint.setTextSize(dp(15));
            canvas.drawText(value == 0 ? "—" : String.format(Locale.US, "%.1f", value), centerX, centerY + dp(13), paint);
            paint.setTextSize(dp(8));
            canvas.drawText(unit, centerX, centerY + dp(25), paint);

            double needleAngle = Math.toRadians(135 + (value / max) * 270);
            float tipX = centerX + (float) Math.cos(needleAngle) * (dialRadius - dp(9));
            float tipY = centerY + (float) Math.sin(needleAngle) * (dialRadius - dp(9));
            Path needle = new Path();
            needle.moveTo(centerX - dp(2), centerY + dp(3));
            needle.lineTo(tipX, tipY);
            needle.lineTo(centerX + dp(2), centerY + dp(3));
            needle.close();
            paint.setColor(COPPER_DARK);
            canvas.drawPath(needle, paint);
            paint.setColor(INK);
            canvas.drawCircle(centerX, centerY, dp(4), paint);
            paint.setColor(COPPER_LIGHT);
            canvas.drawCircle(centerX, centerY, dp(1.5f), paint);

            paint.setColor(INK);
            paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            paint.setTextSize(dp(7));
            canvas.drawText(title, centerX, getHeight() - dp(4), paint);
        }

        private int dp(float value) {
            return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
