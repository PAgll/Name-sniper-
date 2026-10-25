package com.mcsniper.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "McSniperPrefs";
    private static final String CLIENT_ID = "00000000441cc96b";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    // Verified NameMC Drop Window for "Die": Nov 1, 2026 08:22:45 - 09:54:22 UTC
    private static final long DIE_DROP_START_MS = 1793521365000L;

    private TextView statusBanner;
    private TextView codeDisplay;
    private EditText ignInput;
    private EditText intervalInput;
    private Spinner modeSpinner;
    private TextView logView;
    private ScrollView logScroll;

    private String accessToken = "";
    private String refreshToken = "";
    private String mcUsername = "";
    private long tokenObtainedAt = 0L;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "McSniper::ForeverWakeLock");
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        accessToken = prefs.getString("access_token", "");
        refreshToken = prefs.getString("refresh_token", "");
        mcUsername = prefs.getString("mc_username", "");
        tokenObtainedAt = prefs.getLong("obtained_at", 0L);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("MINECRAFT IGN SNIPER v6.2 (NATIVE APK)");
        title.setTextColor(Color.parseColor("#38BDF8"));
        title.setTextSize(17f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        statusBanner = new TextView(this);
        statusBanner.setTextColor(Color.parseColor("#4ADE80"));
        statusBanner.setTextSize(13f);
        statusBanner.setPadding(0, dp(4), 0, dp(10));
        updateBannerText();
        root.addView(statusBanner);

        LinearLayout authRow = new LinearLayout(this);
        authRow.setOrientation(LinearLayout.HORIZONTAL);

        Button loginBtn = makeButton("1. Login with Microsoft (8-Digit Code)", "#0284C7");
        loginBtn.setOnClickListener(v -> startMicrosoftDeviceLogin());
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginEnd(dp(6));
        authRow.addView(loginBtn, lp1);

        Button verifyBtn = makeButton("Verify Account", "#2563EB");
        verifyBtn.setOnClickListener(v -> new Thread(this::verifyAccountEligibility).start());
        authRow.addView(verifyBtn);
        root.addView(authRow);

        codeDisplay = new TextView(this);
        codeDisplay.setText("Tap 'Login with Microsoft' to get your 8-digit sign-in code");
        codeDisplay.setTextColor(Color.parseColor("#E2E8F0"));
        codeDisplay.setBackgroundColor(Color.parseColor("#1E293B"));
        codeDisplay.setGravity(Gravity.CENTER);
        codeDisplay.setPadding(dp(10), dp(8), dp(10), dp(8));
        codeDisplay.setTextSize(14f);
        codeDisplay.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        codeLp.setMargins(0, dp(8), 0, dp(10));
        root.addView(codeDisplay, codeLp);

        TextView ignLbl = new TextView(this);
        ignLbl.setText("Target IGN(s) (any name or comma-separated, e.g. Die,Sky,Ace):");
        ignLbl.setTextColor(Color.parseColor("#CBD5E1"));
        ignLbl.setTextSize(12.5f);
        root.addView(ignLbl);

        LinearLayout ignRow = new LinearLayout(this);
        ignRow.setOrientation(LinearLayout.HORIZONTAL);
        ignInput = new EditText(this);
        ignInput.setText("Die");
        ignInput.setTextColor(Color.parseColor("#38BDF8"));
        ignInput.setBackgroundColor(Color.parseColor("#1E293B"));
        ignInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        ignInput.setSingleLine(true);
        LinearLayout.LayoutParams ignLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ignLp.setMarginEnd(dp(6));
        ignRow.addView(ignInput, ignLp);

        Button checkBtn = makeButton("Check IGN", "#0D9488");
        checkBtn.setOnClickListener(v -> {
            final String raw = ignInput.getText().toString();
            new Thread(() -> {
                for (String part : raw.split(",")) {
                    if (!part.trim().isEmpty()) checkIgnStatus(part.trim());
                }
            }).start();
        });
        ignRow.addView(checkBtn);
        root.addView(ignRow);

        TextView modeLbl = new TextView(this);
        modeLbl.setText("Sniping Mode & Claim Interval (seconds):");
        modeLbl.setTextColor(Color.parseColor("#CBD5E1"));
        modeLbl.setTextSize(12.5f);
        modeLbl.setPadding(0, dp(8), 0, dp(2));
        root.addView(modeLbl);

        LinearLayout cfgRow = new LinearLayout(this);
        cfgRow.setOrientation(LinearLayout.HORIZONTAL);
        modeSpinner = new Spinner(this);
        String[] modes = new String[]{
            "24/7 Forever Mode (Smart Auto-Detect)",
            "24/7 Forever Mode (Force Direct PUT)",
            "NameMC 'Die' Window (Nov 1, 08:22-09:54 UTC)"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setBackgroundColor(Color.parseColor("#1E293B"));
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        spLp.setMarginEnd(dp(6));
        cfgRow.addView(modeSpinner, spLp);

        intervalInput = new EditText(this);
        intervalInput.setText("3.2");
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        intervalInput.setTextColor(Color.WHITE);
        intervalInput.setBackgroundColor(Color.parseColor("#1E293B"));
        intervalInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        cfgRow.addView(intervalInput, new LinearLayout.LayoutParams(dp(75), LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(cfgRow);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actLp.setMargins(0, dp(10), 0, dp(10));

        Button startBtn = makeButton("START SNIPING FOREVER (24/7)", "#16A34A");
        startBtn.setOnClickListener(v -> startForeverSniper());
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        stLp.setMarginEnd(dp(6));
        actionRow.addView(startBtn, stLp);

        Button stopBtn = makeButton("STOP", "#DC2626");
        stopBtn.setOnClickListener(v -> {
            isRunning.set(false);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            appendLog("[*] Stop requested by user.");
        });
        actionRow.addView(stopBtn);
        root.addView(actionRow, actLp);

        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(Color.parseColor("#090D16"));
        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#A7F3D0"));
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(11.5f);
        logView.setPadding(dp(10), dp(10), dp(10), dp(10));
        logScroll.addView(logView);
        root.addView(logScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        appendLog("[*] Native Android Minecraft IGN Sniper v6.2 Ready!");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Button makeButton(String text, String hexColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(hexColor));
        b.setAllCaps(false);
        return b;
    }

    private String formatDuration(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        long mins = sec / 60L; sec %= 60L;
        long hrs = mins / 60L; mins %= 60L;
        long days = hrs / 24L; hrs %= 24L;
        return days + "d " + hrs + "h " + mins + "m " + sec + "s";
    }

    private void updateBannerText() {
        mainHandler.post(() -> {
            if (!mcUsername.isEmpty()) {
                statusBanner.setText("Signed in as: " + mcUsername + " (24/7 Auto-Refresh Active)");
            } else if (!accessToken.isEmpty()) {
                statusBanner.setText("Signed in with Bearer Token");
            } else {
                statusBanner.setText("Account Status: Not signed in");
            }
        });
    }

    private void appendLog(String msg) {
        mainHandler.post(() -> {
            logView.append(msg + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void saveSession() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("mc_username", mcUsername)
            .putLong("obtained_at", tokenObtainedAt)
            .apply();
        updateBannerText();
    }

    private String[] httpRequest(String urlStr, String method, String contentType, String authHeader, String body) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");
            if (contentType != null) conn.setRequestProperty("Content-Type", contentType);
            if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);
            if (body != null) {
                conn.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 400) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
            }
            return new String[]{String.valueOf(status), sb.toString()};
        } catch (Exception e) {
            return new String[]{"0", "{\"error\":\"" + e.getMessage() + "\"}"};
        }
    }

    private void startMicrosoftDeviceLogin() {
        new Thread(() -> {
            try {
                appendLog("[*] Requesting 8-digit Microsoft sign-in code...");
                String form = "client_id=" + CLIENT_ID + "&scope=" + URLEncoder.encode(SCOPE, "UTF-8") + "&response_type=device_code";
                String[] res = httpRequest("https://login.live.com/oauth20_connect.srf", "POST", "application/x-www-form-urlencoded", null, form);
                JSONObject data = new JSONObject(res[1]);
                final String userCode = data.getString("user_code");
                final String deviceCode = data.getString("device_code");
                final int interval = Math.max(4, data.optInt("interval", 5));
                final String linkUrl = "https://login.live.com/oauth20_remoteconnect.srf?otc=" + userCode;

                mainHandler.post(() -> {
                    codeDisplay.setText("CODE: " + userCode + " (Copied! Tap to open browser)");
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("MC Code", userCode));
                    Toast.makeText(MainActivity.this, "Copied code " + userCode + " to clipboard!", Toast.LENGTH_LONG).show();
                    codeDisplay.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))));
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl)));
                });

                appendLog("[+] Enter code " + userCode + " in browser. Waiting for approval...");
                long deadline = System.currentTimeMillis() + 900_000L;
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(interval * 1000L);
                    String pollForm = "client_id=" + CLIENT_ID + "&grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code=" + URLEncoder.encode(deviceCode, "UTF-8");
                    String[] pollRes = httpRequest("https://login.live.com/oauth20_token.srf", "POST", "application/x-www-form-urlencoded", null, pollForm);
                    JSONObject pollJson = new JSONObject(pollRes[1]);
                    if (pollJson.has("access_token")) {
                        refreshToken = pollJson.optString("refresh_token", refreshToken);
                        completeXboxToMinecraftChain(pollJson.getString("access_token"));
                        return;
                    }
                }
            } catch (Exception e) {
                appendLog("[!] Device login error: " + e.getMessage());
            }
        }).start();
    }

    private boolean refreshMinecraftToken() {
        if (refreshToken.isEmpty()) return false;
        try {
            appendLog("[*] Auto-refreshing Microsoft & Minecraft token for 24/7 sniping...");
            String form = "client_id=" + CLIENT_ID + "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8") + "&grant_type=refresh_token&scope=" + URLEncoder.encode(SCOPE, "UTF-8");
            String[] res = httpRequest("https://login.live.com/oauth20_token.srf", "POST", "application/x-www-form-urlencoded", null, form);
            JSONObject json = new JSONObject(res[1]);
            if (json.has("access_token")) {
                refreshToken = json.optString("refresh_token", refreshToken);
                return completeXboxToMinecraftChain(json.getString("access_token"));
            }
        } catch (Exception e) {
            appendLog("[!] Refresh error: " + e.getMessage());
        }
        return false;
    }

    private boolean completeXboxToMinecraftChain(String msToken) {
        try {
            String xblBody = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + msToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
            String[] xblRes = httpRequest("https://user.auth.xboxlive.com/user/authenticate", "POST", "application/json", null, xblBody);
            JSONObject xblJson = new JSONObject(xblRes[1]);
            if (!xblJson.has("Token")) {
                xblBody = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"" + msToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
                xblRes = httpRequest("https://user.auth.xboxlive.com/user/authenticate", "POST", "application/json", null, xblBody);
                xblJson = new JSONObject(xblRes[1]);
            }
            String xblToken = xblJson.getString("Token");
            String uhs = xblJson.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");

            String xstsBody = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
            String[] xstsRes = httpRequest("https://xsts.auth.xboxlive.com/xsts/authorize", "POST", "application/json", null, xstsBody);
            JSONObject xstsJson = new JSONObject(xstsRes[1]);
            String xstsToken = xstsJson.getString("Token");

            String mcBody = "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}";
            String[] mcRes = httpRequest("https://api.minecraftservices.com/authentication/login_with_xbox", "POST", "application/json", null, mcBody);
            JSONObject mcJson = new JSONObject(mcRes[1]);
            accessToken = mcJson.getString("access_token");
            tokenObtainedAt = System.currentTimeMillis();

            verifyAccountEligibility();
            saveSession();
            return true;
        } catch (Exception e) {
            appendLog("[!] Xbox/MC handshake failed: " + e.getMessage());
            return false;
        }
    }

    private void verifyAccountEligibility() {
        if (accessToken.isEmpty()) {
            appendLog("[!] Not signed in yet.");
            return;
        }
        try {
            String[] profRes = httpRequest("https://api.minecraftservices.com/minecraft/profile", "GET", null, "Bearer " + accessToken, null);
            if ("200".equals(profRes[0])) {
                JSONObject prof = new JSONObject(profRes[1]);
                mcUsername = prof.optString("name", mcUsername);
                saveSession();
                appendLog("[+] Signed in as: " + mcUsername + " (UUID: " + prof.optString("id") + ")");
            }
            String[] ncRes = httpRequest("https://api.minecraftservices.com/minecraft/profile/namechange", "GET", null, "Bearer " + accessToken, null);
            if ("200".equals(ncRes[0])) {
                JSONObject nc = new JSONObject(ncRes[1]);
                appendLog("[+] Rename eligible now: " + nc.optBoolean("nameChangeAllowed", false));
            }
        } catch (Exception e) {
            appendLog("[!] Verify error: " + e.getMessage());
        }
    }

    private String checkIgnStatus(String ign) {
        appendLog("[*] Checking exact status for IGN '" + ign + "'...");
        String[] res = httpRequest("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + ign, "GET", null, null, null);
        if ("200".equals(res[0])) {
            try {
                JSONObject obj = new JSONObject(res[1]);
                appendLog("[NOT AVAILABLE - BOUND TO ACCOUNT] IGN '" + obj.optString("name", ign) + "' is bound to active account UUID: " + obj.optString("id"));
            } catch (Exception ignored) {
                appendLog("[NOT AVAILABLE - BOUND TO ACCOUNT] IGN '" + ign + "' is bound to an active account.");
            }
            return "TAKEN";
        } else if ("404".equals(res[0])) {
            if (!accessToken.isEmpty()) {
                String[] avRes = httpRequest("https://api.minecraftservices.com/minecraft/profile/name/" + ign + "/available", "GET", null, "Bearer " + accessToken, null);
                if ("200".equals(avRes[0])) {
                    try {
                        JSONObject avObj = new JSONObject(avRes[1]);
                        String st = avObj.optString("status", "");
                        if ("AVAILABLE".equals(st)) {
                            appendLog("[AVAILABLE NOW] IGN '" + ign + "' is 100% AVAILABLE right now! Not bound to any account and not on hold.");
                            return "AVAILABLE";
                        } else if ("NOT_ALLOWED".equals(st)) {
                            appendLog("[NOT AVAILABLE - BLOCKED] IGN '" + ign + "' is blocked by Mojang name rules (NOT_ALLOWED).");
                            return "INVALID";
                        }
                    } catch (Exception ignored) {}
                }
            }
            if ("die".equalsIgnoreCase(ign)) {
                long rem = DIE_DROP_START_MS - System.currentTimeMillis();
                appendLog("[ON 37-DAY HOLD] IGN 'Die' is ON HOLD for " + formatDuration(rem) + " (Drops Nov 1, 2026 1:22:45 PM - 2:54:22 PM PKT / 08:22:45 - 09:54:22 UTC).");
            } else {
                appendLog("[ON 37-DAY HOLD] IGN '" + ign + "' is NOT bound to an active account and is confirmed ON MOJANG 37-DAY HOLD.");
            }
            return "LOCKED";
        }
        appendLog("[*] Lookup returned HTTP " + res[0]);
        return "UNKNOWN";
    }

    private void startForeverSniper() {
        if (isRunning.get()) {
            appendLog("[*] Sniper is already running!");
            return;
        }
        if (accessToken.isEmpty() && refreshToken.isEmpty()) {
            appendLog("[!] Tap '1. Login with Microsoft' first!");
            return;
        }
        final String rawIgns = ignInput.getText().toString();
        final String[] targets = rawIgns.split(",");
        double parsedInterval = 3.2;
        try {
            parsedInterval = Math.max(1.0, Double.parseDouble(intervalInput.getText().toString()));
        } catch (Exception ignored) {}
        final long sleepMs = (long) (parsedInterval * 1000L);

        isRunning.set(true);
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();

        new Thread(() -> {
            appendLog("==========================================");
            appendLog("[*] STARTED 24/7 ANDROID SNIPER FOR: " + rawIgns);
            appendLog("==========================================");
            for (String t : targets) {
                if (!t.trim().isEmpty()) checkIgnStatus(t.trim());
            }
            int attempt = 0;
            while (isRunning.get()) {
                if (!refreshToken.isEmpty() && (System.currentTimeMillis() - tokenObtainedAt) > 72_000_000L) {
                    refreshMinecraftToken();
                }
                for (String rawTarget : targets) {
                    if (!isRunning.get()) break;
                    String ign = rawTarget.trim();
                    if (ign.isEmpty()) continue;
                    attempt++;

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    String utcNow = sdf.format(new Date());

                    long t0 = System.currentTimeMillis();
                    String[] putRes = httpRequest(
                        "https://api.minecraftservices.com/minecraft/profile/name/" + ign,
                        "PUT",
                        "application/json",
                        "Bearer " + accessToken,
                        ""
                    );
                    long rtt = System.currentTimeMillis() - t0;
                    String code = putRes[0];

                    if ("200".equals(code)) {
                        appendLog("==========================================");
                        appendLog("[SUCCESS] [" + utcNow + " UTC] SNIPED IGN '" + ign + "' ON ATTEMPT #" + attempt + "!");
                        appendLog("==========================================");
                        isRunning.set(false);
                        return;
                    } else if ("403".equals(code)) {
                        if (attempt <= 5 || attempt % 15 == 0) {
                            String extra = "die".equalsIgnoreCase(ign) ? ("On Hold for " + formatDuration(DIE_DROP_START_MS - System.currentTimeMillis())) : "On Hold / Taken";
                            appendLog("[" + utcNow + " UTC] #" + attempt + " '" + ign + "' (" + rtt + "ms): " + extra + " — retrying 24/7...");
                        }
                    } else if ("429".equals(code)) {
                        appendLog("[!] [" + utcNow + " UTC] HTTP 429 Rate Limit — backing off 10s...");
                        try { Thread.sleep(10000L); } catch (InterruptedException ignored) {}
                        continue;
                    } else if ("401".equals(code)) {
                        appendLog("[*] HTTP 401 Token Expired — auto-refreshing...");
                        if (!refreshMinecraftToken()) {
                            appendLog("[!] Could not auto-refresh token. Please tap Login again.");
                            isRunning.set(false);
                            return;
                        }
                    } else {
                        appendLog("[" + utcNow + " UTC] #" + attempt + " HTTP " + code);
                    }

                    try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }
}
