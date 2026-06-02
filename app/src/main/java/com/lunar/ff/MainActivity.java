package com.lunar.ff;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    // Views
    private LinearLayout rootLayout;
    private LinearLayout errorScreen;
    private LinearLayout mainScreen;
    private ScrollView mainScroll;
    
    // Error Screen
    private TextView errorIconText;
    private TextView errorTitle;
    private TextView errorDesc;
    private Button errorBtn;
    
    // Main Screen
    private TextView headerTitle;
    private TextView statusLabel;
    private EditText tokenInput;
    private Button verifyButton;
    private TextView tokenPreviewText;
    private TextView uidText;
    private LinearLayout controlPanel;
    private Button startStopButton;
    private Button logoutButton;
    private ProgressBar loadingSpinner;
    
    // Data
    private String jwtToken = "";
    private String currentUid = "";
    private boolean isActive = false;
    private boolean shizukuOk = false;
    
    private ExecutorService threadPool;
    private Handler uiHandler;
    private SharedPreferences appPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        threadPool = Executors.newSingleThreadExecutor();
        uiHandler = new Handler(Looper.getMainLooper());
        appPrefs = getSharedPreferences("lunar_prefs", MODE_PRIVATE);
        
        buildUI();
        requestStoragePermission();
        
        // Delayed Shizuku check
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                initShizuku();
            }
        }, 600);
    }

    // ==================== BUILD UI ====================
    private void buildUI() {
        // ROOT
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#0A0E17"));
        rootLayout.setFitsSystemWindows(true);
        
        // === ERROR SCREEN ===
        errorScreen = new LinearLayout(this);
        errorScreen.setOrientation(LinearLayout.VERTICAL);
        errorScreen.setGravity(Gravity.CENTER);
        errorScreen.setPadding(40, 0, 40, 0);
        LinearLayout.LayoutParams errParam = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
        errorScreen.setLayoutParams(errParam);
        errorScreen.setVisibility(View.GONE);
        
        // Error Icon (Text based)
        errorIconText = new TextView(this);
        errorIconText.setText("⚠");
        errorIconText.setTextSize(80);
        errorIconText.setTextColor(Color.parseColor("#FF5252"));
        errorIconText.setGravity(Gravity.CENTER);
        errorScreen.addView(errorIconText);
        
        // Error Title
        errorTitle = new TextView(this);
        errorTitle.setText("Shizuku Required");
        errorTitle.setTextSize(22);
        errorTitle.setTextColor(Color.WHITE);
        errorTitle.setGravity(Gravity.CENTER);
        errorTitle.setPadding(0, 20, 0, 12);
        errorTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        errorScreen.addView(errorTitle);
        
        // Error Description
        errorDesc = new TextView(this);
        errorDesc.setText("Open Shizuku app, start the service, then come back.");
        errorDesc.setTextSize(14);
        errorDesc.setTextColor(Color.parseColor("#8892B0"));
        errorDesc.setGravity(Gravity.CENTER);
        errorDesc.setPadding(20, 0, 20, 32);
        errorDesc.setLineSpacing(4f, 1f);
        errorScreen.addView(errorDesc);
        
        // Retry Button
        errorBtn = new Button(this);
        errorBtn.setText("TRY AGAIN");
        errorBtn.setTextColor(Color.WHITE);
        errorBtn.setTextSize(15);
        errorBtn.setAllCaps(true);
        errorBtn.setPadding(48, 18, 48, 18);
        
        GradientDrawable errBtnBg = new GradientDrawable();
        errBtnBg.setShape(GradientDrawable.RECTANGLE);
        errBtnBg.setCornerRadius(50);
        errBtnBg.setColor(Color.parseColor("#FF5252"));
        errorBtn.setBackground(errBtnBg);
        
        errorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initShizuku();
            }
        });
        errorScreen.addView(errorBtn);
        
        // Open Shizuku button
        Button openShizukuBtn = new Button(this);
        openShizukuBtn.setText("OPEN SHIZUKU APP");
        openShizukuBtn.setTextColor(Color.parseColor("#00E676"));
        openShizukuBtn.setTextSize(13);
        openShizukuBtn.setAllCaps(true);
        openShizukuBtn.setPadding(48, 14, 48, 14);
        openShizukuBtn.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams obp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        obp.setMargins(0, 16, 0, 0);
        openShizukuBtn.setLayoutParams(obp);
        openShizukuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (intent != null) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(MainActivity.this, "Shizuku app not installed", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Cannot open Shizuku", Toast.LENGTH_SHORT).show();
                }
            }
        });
        errorScreen.addView(openShizukuBtn);
        
        rootLayout.addView(errorScreen);
        
        // === MAIN SCREEN ===
        mainScroll = new ScrollView(this);
        mainScroll.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mainScroll.setFillViewport(true);
        mainScroll.setVisibility(View.GONE);
        
        mainScreen = new LinearLayout(this);
        mainScreen.setOrientation(LinearLayout.VERTICAL);
        mainScreen.setPadding(24, 48, 24, 32);
        
        // HEADER
        headerTitle = new TextView(this);
        headerTitle.setText("LUNAR FF");
        headerTitle.setTextSize(32);
        headerTitle.setTextColor(Color.parseColor("#00E676"));
        headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        headerTitle.setPadding(0, 0, 0, 4);
        mainScreen.addView(headerTitle);
        
        // Status
        statusLabel = new TextView(this);
        statusLabel.setText("● Connected");
        statusLabel.setTextSize(12);
        statusLabel.setTextColor(Color.parseColor("#00E676"));
        statusLabel.setPadding(0, 0, 0, 28);
        mainScreen.addView(statusLabel);
        
        // === TOKEN CARD ===
        LinearLayout tokenCard = makeCard();
        
        TextView tokenLabel = new TextView(this);
        tokenLabel.setText("Lunar Token");
        tokenLabel.setTextSize(15);
        tokenLabel.setTextColor(Color.parseColor("#CCD6F6"));
        tokenLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tokenLabel.setPadding(0, 0, 0, 12);
        tokenCard.addView(tokenLabel);
        
        tokenInput = new EditText(this);
        tokenInput.setHint("Paste your Lunar Token");
        tokenInput.setHintTextColor(Color.parseColor("#4A5568"));
        tokenInput.setTextColor(Color.WHITE);
        tokenInput.setTextSize(13);
        tokenInput.setSingleLine(true);
        tokenInput.setPadding(20, 18, 20, 18);
        
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE);
        inputBg.setCornerRadius(12);
        inputBg.setColor(Color.parseColor("#0A0E17"));
        inputBg.setStroke(1, Color.parseColor("#1E2A3A"));
        tokenInput.setBackground(inputBg);
        
        tokenCard.addView(tokenInput);
        
        // Verify Button
        verifyButton = new Button(this);
        verifyButton.setText("VERIFY TOKEN");
        verifyButton.setTextColor(Color.WHITE);
        verifyButton.setTextSize(15);
        verifyButton.setAllCaps(true);
        verifyButton.setPadding(0, 18, 0, 18);
        
        GradientDrawable verifyBg = new GradientDrawable();
        verifyBg.setShape(GradientDrawable.RECTANGLE);
        verifyBg.setCornerRadius(14);
        verifyBg.setColor(Color.parseColor("#0A84FF"));
        verifyButton.setBackground(verifyBg);
        
        LinearLayout.LayoutParams vbp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vbp.setMargins(0, 16, 0, 0);
        verifyButton.setLayoutParams(vbp);
        
        verifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doVerifyToken();
            }
        });
        tokenCard.addView(verifyButton);
        
        mainScreen.addView(tokenCard);
        
        // Token Preview
        tokenPreviewText = new TextView(this);
        tokenPreviewText.setText("");
        tokenPreviewText.setTextSize(11);
        tokenPreviewText.setTextColor(Color.parseColor("#64FFDA"));
        tokenPreviewText.setPadding(16, 14, 16, 14);
        tokenPreviewText.setBackgroundColor(Color.parseColor("#0A0E17"));
        tokenPreviewText.setVisibility(View.GONE);
        LinearLayout.LayoutParams tpp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tpp.setMargins(0, 16, 0, 0);
        tokenPreviewText.setLayoutParams(tpp);
        mainScreen.addView(tokenPreviewText);
        
        // UID
        uidText = new TextView(this);
        uidText.setText("");
        uidText.setTextSize(14);
        uidText.setTextColor(Color.parseColor("#00E676"));
        uidText.setTypeface(null, android.graphics.Typeface.BOLD);
        uidText.setPadding(4, 12, 0, 0);
        uidText.setVisibility(View.GONE);
        mainScreen.addView(uidText);
        
        // === CONTROL PANEL ===
        controlPanel = makeCard();
        controlPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams cpp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cpp.setMargins(0, 20, 0, 0);
        controlPanel.setLayoutParams(cpp);
        
        TextView controlLabel = new TextView(this);
        controlLabel.setText("Control Panel");
        controlLabel.setTextSize(15);
        controlLabel.setTextColor(Color.parseColor("#CCD6F6"));
        controlLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        controlLabel.setPadding(0, 0, 0, 16);
        controlPanel.addView(controlLabel);
        
        // Start/Stop Button
        startStopButton = new Button(this);
        startStopButton.setText("▶ START");
        startStopButton.setTextColor(Color.WHITE);
        startStopButton.setTextSize(18);
        startStopButton.setAllCaps(true);
        startStopButton.setPadding(0, 20, 0, 20);
        
        GradientDrawable ssBg = new GradientDrawable();
        ssBg.setShape(GradientDrawable.RECTANGLE);
        ssBg.setCornerRadius(14);
        ssBg.setColor(Color.parseColor("#00C853"));
        startStopButton.setBackground(ssBg);
        
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isActive) {
                    doStopService();
                } else {
                    doStartService();
                }
            }
        });
        controlPanel.addView(startStopButton);
        
        // Spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 12));
        controlPanel.addView(spacer);
        
        // Logout Button
        logoutButton = new Button(this);
        logoutButton.setText("LOGOUT");
        logoutButton.setTextColor(Color.WHITE);
        logoutButton.setTextSize(14);
        logoutButton.setAllCaps(true);
        logoutButton.setPadding(0, 16, 0, 16);
        logoutButton.setBackgroundColor(Color.parseColor("#FF5252"));
        
        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLogout();
            }
        });
        controlPanel.addView(logoutButton);
        
        mainScreen.addView(controlPanel);
        
        // Loading
        loadingSpinner = new ProgressBar(this);
        loadingSpinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(
            60, 60);
        lsp.gravity = Gravity.CENTER;
        lsp.setMargins(0, 24, 0, 0);
        loadingSpinner.setLayoutParams(lsp);
        mainScreen.addView(loadingSpinner);
        
        // Footer
        TextView footer = new TextView(this);
        footer.setText("Shizuku Required • Android 8+\nFree Fire OB53 Compatible");
        footer.setTextSize(10);
        footer.setTextColor(Color.parseColor("#4A5568"));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 32, 0, 0);
        footer.setLineSpacing(4f, 1f);
        mainScreen.addView(footer);
        
        mainScroll.addView(mainScreen);
        rootLayout.addView(mainScroll);
        
        setContentView(rootLayout);
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 20, 20, 20);
        
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(18);
        cardBg.setColor(Color.parseColor("#141A26"));
        cardBg.setStroke(1, Color.parseColor("#1E2A3A"));
        card.setBackground(cardBg);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        
        return card;
    }

    // ==================== SHIZUKU ====================
    private void initShizuku() {
        try {
            boolean binderOk = Shizuku.pingBinder();
            
            if (!binderOk) {
                showErrorScreen("Shizuku Not Running", "Start Shizuku service first, then open this app.");
                startPulseAnimation(errorIconText);
                return;
            }
            
            int permission = Shizuku.checkSelfPermission();
            
            if (permission == PackageManager.PERMISSION_GRANTED) {
                shizukuOk = true;
                showMainScreen();
            } else {
                Shizuku.requestPermission(0);
                showErrorScreen("Permission Needed", "Grant permission in the popup, then try again.");
            }
            
        } catch (Exception e) {
            showErrorScreen("Error", "Shizuku check failed. Is it installed?");
        }
    }

    private void showErrorScreen(String title, String desc) {
        shizukuOk = false;
        errorScreen.setVisibility(View.VISIBLE);
        mainScroll.setVisibility(View.GONE);
        errorTitle.setText(title);
        errorDesc.setText(desc);
        errorScreen.setAlpha(0f);
        errorScreen.animate().alpha(1f).setDuration(300).start();
    }

    private void showMainScreen() {
        errorScreen.setVisibility(View.GONE);
        mainScroll.setVisibility(View.VISIBLE);
        mainScroll.setAlpha(0f);
        mainScroll.animate().alpha(1f).setDuration(400).start();
        statusLabel.setText("● Connected");
        statusLabel.setTextColor(Color.parseColor("#00E676"));
    }

    private void startPulseAnimation(View view) {
        AlphaAnimation anim = new AlphaAnimation(0.3f, 1.0f);
        anim.setDuration(1000);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        view.startAnimation(anim);
    }

    // ==================== STORAGE ====================
    private void requestStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
                }
            }
        } catch (Exception e) {
            // Continue without storage permission
        }
    }

    // ==================== TOKEN VERIFY ====================
    private void doVerifyToken() {
        final String inputToken = tokenInput.getText().toString().trim();
        
        if (inputToken.isEmpty()) {
            Toast.makeText(this, "Enter token first!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!shizukuOk) {
            Toast.makeText(this, "Connect Shizuku first!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        setLoading(true);
        
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String apiUrl = "https://127.0.0.1:8080/check?token=" + inputToken;
                    String response = ApiClient.get(apiUrl);
                    
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false);
                            
                            if (response != null) {
                                try {
                                    JSONObject json = new JSONObject(response);
                                    String jt = json.optString("JwtToken", "");
                                    
                                    if (!jt.isEmpty()) {
                                        jwtToken = jt;
                                        extractUID(jt);
                                        
                                        // Save
                                        appPrefs.edit().putString("saved_token", inputToken).apply();
                                        
                                        // Show preview
                                        String preview = jt.length() > 50 ? jt.substring(0, 50) + "..." : jt;
                                        tokenPreviewText.setText(preview);
                                        tokenPreviewText.setVisibility(View.VISIBLE);
                                        
                                        uidText.setText("UID: " + currentUid);
                                        uidText.setVisibility(View.VISIBLE);
                                        
                                        controlPanel.setVisibility(View.VISIBLE);
                                        controlPanel.setAlpha(0f);
                                        controlPanel.animate().alpha(1f).setDuration(300).start();
                                        
                                        updateStatus("✓ Verified", "#00E676");
                                        Toast.makeText(MainActivity.this, "✓ Token Verified!", Toast.LENGTH_SHORT).show();
                                    } else {
                                        updateStatus("✗ Invalid Token", "#FF5252");
                                        Toast.makeText(MainActivity.this, "Invalid token!", Toast.LENGTH_SHORT).show();
                                    }
                                } catch (Exception e) {
                                    updateStatus("✗ Parse Error", "#FF5252");
                                }
                            } else {
                                updateStatus("✗ Network Error", "#FF5252");
                                Toast.makeText(MainActivity.this, "Check your internet!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false);
                            updateStatus("✗ Failed", "#FF5252");
                        }
                    });
                }
            }
        });
    }

    private void extractUID(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String payload = parts[1];
                while (payload.length() % 4 != 0) payload += "=";
                byte[] data = android.util.Base64.decode(payload, android.util.Base64.DEFAULT);
                JSONObject json = new JSONObject(new String(data));
                currentUid = json.optString("account_id", "Unknown");
            }
        } catch (Exception e) {
            currentUid = "N/A";
        }
    }

    // ==================== START SERVICE ====================
    private void doStartService() {
        if (jwtToken.isEmpty()) {
            Toast.makeText(this, "Verify token first!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        setLoading(true);
        
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String configPath = "/storage/emulated/0/Android/data/com.dts.freefireth/files/localconfig.json";
                    String serverUrl = "http://203.175.125.151:10136/" + jwtToken + "/";
                    
                    JSONObject config = new JSONObject();
                    config.put("verAddr", "https://version-ggbluellama.vercel.app/live/");
                    config.put("serverLoginUrl", serverUrl);
                    
                    File file = new File(configPath);
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    if (file.exists()) {
                        file.delete();
                    }
                    file.createNewFile();
                    
                    FileWriter fw = new FileWriter(file);
                    fw.write(config.toString(2));
                    fw.flush();
                    fw.close();
                    
                    final boolean success = file.exists() && file.length() > 0;
                    
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false);
                            
                            if (success) {
                                isActive = true;
                                startStopButton.setText("⏹ STOP");
                                startStopButton.setBackgroundColor(Color.parseColor("#FF5252"));
                                updateStatus("● Active - Config Injected", "#00E676");
                                Toast.makeText(MainActivity.this, "✓ Config Created! Open Free Fire now.", Toast.LENGTH_LONG).show();
                            } else {
                                updateStatus("✗ Failed to create config", "#FF5252");
                                Toast.makeText(MainActivity.this, "Failed! Check permissions.", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false);
                            updateStatus("✗ Error: " + e.getMessage(), "#FF5252");
                        }
                    });
                }
            }
        });
    }

    // ==================== STOP SERVICE ====================
    private void doStopService() {
        setLoading(true);
        
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String configPath = "/storage/emulated/0/Android/data/com.dts.freefireth/files/localconfig.json";
                    File file = new File(configPath);
                    final boolean deleted = !file.exists() || file.delete();
                    
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false);
                            
                            if (deleted) {
                                isActive = false;
                                startStopButton.setText("▶ START");
                                startStopButton.setBackgroundColor(Color.parseColor("#00C853"));
                                updateStatus("○ Stopped - Config Removed", "#FFD740");
                                Toast.makeText(MainActivity.this, "Config deleted!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false);
                        }
                    });
                }
            }
        });
    }

    // ==================== LOGOUT ====================
    private void doLogout() {
        jwtToken = "";
        currentUid = "";
        tokenInput.setText("");
        tokenPreviewText.setVisibility(View.GONE);
        uidText.setVisibility(View.GONE);
        controlPanel.setVisibility(View.GONE);
        appPrefs.edit().remove("saved_token").apply();
        
        if (isActive) {
            doStopService();
        }
        
        updateStatus("● Logged out", "#FFD740");
        Toast.makeText(this, "Logged out!", Toast.LENGTH_SHORT).show();
    }

    // ==================== HELPERS ====================
    private void setLoading(boolean loading) {
        loadingSpinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        verifyButton.setEnabled(!loading);
        startStopButton.setEnabled(!loading);
    }

    private void updateStatus(String msg, String colorHex) {
        statusLabel.setText(msg);
        statusLabel.setTextColor(Color.parseColor(colorHex));
    }

    // ==================== LIFECYCLE ====================
    @Override
    protected void onResume() {
        super.onResume();
        // Re-check Shizuku when returning to app
        if (!shizukuOk) {
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    initShizuku();
                }
            }, 400);
        }
        
        // Restore saved token
        String saved = appPrefs.getString("saved_token", "");
        if (!saved.isEmpty() && tokenInput.getText().toString().isEmpty()) {
            tokenInput.setText(saved);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (threadPool != null) {
            threadPool.shutdown();
        }
    }
}
