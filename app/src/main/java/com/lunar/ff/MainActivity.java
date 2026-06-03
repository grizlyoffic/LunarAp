package com.lunar.ff;

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
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private LinearLayout rootLayout;
    private LinearLayout errorScreen;
    private ScrollView mainScroll;
    private LinearLayout mainScreen;
    
    private TextView errorIconText;
    private TextView errorTitle;
    private TextView errorDesc;
    private Button errorBtn;
    
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
    
    private String jwtToken = "";
    private String currentUid = "";
    private boolean isActive = false;
    private boolean shizukuOk = false;
    private boolean fileAccessOk = false;
    
    private ExecutorService threadPool;
    private Handler uiHandler;
    private SharedPreferences appPrefs;
    
    private static final String TARGET_PATH = "/storage/emulated/0/Android/data/com.dts.freefireth/files/";
    private static final String CONFIG_FILE = "localconfig.json";
    private static final int SAF_REQUEST_CODE = 9999;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        threadPool = Executors.newSingleThreadExecutor();
        uiHandler = new Handler(Looper.getMainLooper());
        appPrefs = getSharedPreferences("lunar_prefs", MODE_PRIVATE);
        
        buildUI();
        requestAllPermissions();
        
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                initShizuku();
            }
        }, 800);
    }

    // ==================== BUILD UI ====================
    private void buildUI() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#0A0E17"));
        rootLayout.setFitsSystemWindows(true);
        
        // === ERROR SCREEN ===
        errorScreen = new LinearLayout(this);
        errorScreen.setOrientation(LinearLayout.VERTICAL);
        errorScreen.setGravity(Gravity.CENTER);
        errorScreen.setPadding(40, 0, 40, 0);
        errorScreen.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        errorScreen.setVisibility(View.GONE);
        
        errorIconText = new TextView(this);
        errorIconText.setText("\u26A0");
        errorIconText.setTextSize(80);
        errorIconText.setTextColor(Color.parseColor("#FF5252"));
        errorIconText.setGravity(Gravity.CENTER);
        errorScreen.addView(errorIconText);
        
        errorTitle = new TextView(this);
        errorTitle.setText("Permission Required");
        errorTitle.setTextSize(22);
        errorTitle.setTextColor(Color.WHITE);
        errorTitle.setGravity(Gravity.CENTER);
        errorTitle.setPadding(0, 20, 0, 12);
        errorTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        errorScreen.addView(errorTitle);
        
        errorDesc = new TextView(this);
        errorDesc.setText("Grant all permissions to continue.");
        errorDesc.setTextSize(14);
        errorDesc.setTextColor(Color.parseColor("#8892B0"));
        errorDesc.setGravity(Gravity.CENTER);
        errorDesc.setPadding(20, 0, 20, 32);
        errorDesc.setLineSpacing(4f, 1f);
        errorScreen.addView(errorDesc);
        
        errorBtn = new Button(this);
        errorBtn.setText("GRANT PERMISSIONS");
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
                requestAllPermissions();
                uiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initShizuku();
                    }
                }, 1000);
            }
        });
        errorScreen.addView(errorBtn);
        
        // Open Shizuku button
        Button openShizukuBtn = new Button(this);
        openShizukuBtn.setText("OPEN SHIZUKU");
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
                    if (intent != null) startActivity(intent);
                    else Toast.makeText(MainActivity.this, "Install Shizuku first", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {}
            }
        });
        errorScreen.addView(openShizukuBtn);
        
        // Open Android Settings for file access
        Button openSettingsBtn = new Button(this);
        openSettingsBtn.setText("OPEN STORAGE SETTINGS");
        openSettingsBtn.setTextColor(Color.parseColor("#0A84FF"));
        openSettingsBtn.setTextSize(13);
        openSettingsBtn.setAllCaps(true);
        openSettingsBtn.setPadding(48, 14, 48, 14);
        openSettingsBtn.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams osbp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        osbp.setMargins(0, 12, 0, 0);
        openSettingsBtn.setLayoutParams(osbp);
        openSettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {}
            }
        });
        errorScreen.addView(openSettingsBtn);
        
        // SAF Button for Android/data access
        Button safBtn = new Button(this);
        safBtn.setText("ACCESS GAME FOLDER (SAF)");
        safBtn.setTextColor(Color.parseColor("#FFD740"));
        safBtn.setTextSize(13);
        safBtn.setAllCaps(true);
        safBtn.setPadding(48, 14, 48, 14);
        safBtn.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbp.setMargins(0, 12, 0, 0);
        safBtn.setLayoutParams(sbp);
        safBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestSAFPermission();
            }
        });
        errorScreen.addView(safBtn);
        
        rootLayout.addView(errorScreen);
        
        // === MAIN SCREEN ===
        mainScroll = new ScrollView(this);
        mainScroll.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mainScroll.setFillViewport(true);
        mainScroll.setVisibility(View.GONE);
        
        mainScreen = new LinearLayout(this);
        mainScreen.setOrientation(LinearLayout.VERTICAL);
        mainScreen.setPadding(24, 48, 24, 32);
        
        headerTitle = new TextView(this);
        headerTitle.setText("LUNAR FF");
        headerTitle.setTextSize(32);
        headerTitle.setTextColor(Color.parseColor("#00E676"));
        headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        headerTitle.setPadding(0, 0, 0, 4);
        mainScreen.addView(headerTitle);
        
        statusLabel = new TextView(this);
        statusLabel.setText("\u25CF Connected");
        statusLabel.setTextSize(12);
        statusLabel.setTextColor(Color.parseColor("#00E676"));
        statusLabel.setPadding(0, 0, 0, 28);
        mainScreen.addView(statusLabel);
        
        // Token Card
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
        
        uidText = new TextView(this);
        uidText.setText("");
        uidText.setTextSize(14);
        uidText.setTextColor(Color.parseColor("#00E676"));
        uidText.setTypeface(null, android.graphics.Typeface.BOLD);
        uidText.setPadding(4, 12, 0, 0);
        uidText.setVisibility(View.GONE);
        mainScreen.addView(uidText);
        
        // Control Panel
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
        
        startStopButton = new Button(this);
        startStopButton.setText("\u25B6 START");
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
                if (isActive) doStopService();
                else doStartService();
            }
        });
        controlPanel.addView(startStopButton);
        
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 12));
        controlPanel.addView(spacer);
        
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
        
        loadingSpinner = new ProgressBar(this);
        loadingSpinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(60, 60);
        lsp.gravity = Gravity.CENTER;
        lsp.setMargins(0, 24, 0, 0);
        loadingSpinner.setLayoutParams(lsp);
        mainScreen.addView(loadingSpinner);
        
        TextView footer = new TextView(this);
        footer.setText("Shizuku + Storage Access Required\nFree Fire OB53 Compatible");
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

    // ==================== PERMISSIONS ====================
    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Please enable 'All Files Access' in App Settings", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, 1001);
            }
        }
        
        requestSAFPermission();
    }
    
    private void requestSAFPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                               Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                               Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                
                Uri dataUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata");
                intent.putExtra("android.provider.extra.INITIAL_URI", dataUri);
                
                startActivityForResult(intent, SAF_REQUEST_CODE);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Please manually allow folder access", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == SAF_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            
            getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            
            appPrefs.edit().putString("saf_tree_uri", treeUri.toString()).apply();
            
            fileAccessOk = true;
            Toast.makeText(this, "\u2713 Folder access granted!", Toast.LENGTH_SHORT).show();
            
            if (shizukuOk && fileAccessOk) {
                showMainScreen();
            }
        }
    }

    // ==================== SHIZUKU ====================
    private void initShizuku() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    showErrorScreen("Storage Access Needed", 
                        "Android 11+ requires 'All Files Access' permission.", true);
                    return;
                }
            }
            
            boolean binderOk = false;
            try {
                binderOk = Shizuku.pingBinder();
            } catch (Exception e) {
                binderOk = false;
            }
            
            if (!binderOk) {
                showErrorScreen("Shizuku Not Running", 
                    "Start Shizuku service first, then reopen this app.", true);
                startPulseAnimation(errorIconText);
                return;
            }
            
            int permission = 0;
            try {
                permission = Shizuku.checkSelfPermission();
            } catch (Exception e) {
                permission = PackageManager.PERMISSION_DENIED;
            }
            
            if (permission == PackageManager.PERMISSION_GRANTED) {
                shizukuOk = true;
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    String savedUri = appPrefs.getString("saf_tree_uri", "");
                    if (!savedUri.isEmpty()) {
                        fileAccessOk = true;
                    } else {
                        fileAccessOk = false;
                        showErrorScreen("Folder Access Required",
                            "Android 11+ needs SAF permission. Tap 'ACCESS GAME FOLDER'.", true);
                        return;
                    }
                } else {
                    fileAccessOk = true;
                }
                
                if (shizukuOk && fileAccessOk) {
                    showMainScreen();
                }
            } else {
                try {
                    Shizuku.requestPermission(0);
                } catch (Exception e) {}
                
                showErrorScreen("Shizuku Permission Needed", 
                    "Grant Shizuku permission in the popup, then tap 'GRANT PERMISSIONS'.", false);
            }
            
        } catch (Exception e) {
            showErrorScreen("Error", "Something went wrong. Check Shizuku app.", false);
        }
    }

    private void showErrorScreen(String title, String desc, boolean showAllButtons) {
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
        updateStatus("\u25CF Connected - Ready", "#00E676");
    }

    private void startPulseAnimation(View view) {
        Animation anim = new AlphaAnimation(0.3f, 1.0f);
        anim.setDuration(1000);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        view.startAnimation(anim);
    }

    // ==================== TOKEN VERIFY ====================
    private void doVerifyToken() {
        final String inputToken = tokenInput.getText().toString().trim();
        
        if (inputToken.isEmpty()) {
            Toast.makeText(this, "Enter token first!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!shizukuOk || !fileAccessOk) {
            Toast.makeText(this, "Grant all permissions first!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        setLoading(true);
        
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String apiUrl = "https://host-ggclient.vercel.app/check?token=" + inputToken;
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
                                        appPrefs.edit().putString("saved_token", inputToken).apply();
                                        
                                        String preview = jt.length() > 50 ? jt.substring(0, 50) + "..." : jt;
                                        tokenPreviewText.setText(preview);
                                        tokenPreviewText.setVisibility(View.VISIBLE);
                                        uidText.setText("UID: " + currentUid);
                                        uidText.setVisibility(View.VISIBLE);
                                        controlPanel.setVisibility(View.VISIBLE);
                                        controlPanel.setAlpha(0f);
                                        controlPanel.animate().alpha(1f).setDuration(300).start();
                                        
                                        updateStatus("\u2713 Verified", "#00E676");
                                        Toast.makeText(MainActivity.this, "\u2713 Token Verified!", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "Invalid token!", Toast.LENGTH_SHORT).show();
                                    }
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, "Parse error!", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(MainActivity.this, "Network error!", Toast.LENGTH_SHORT).show();
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

    // ==================== FILE OPERATIONS ====================
    private void doStartService() {
        if (jwtToken.isEmpty()) {
            Toast.makeText(this, "Verify token first!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!shizukuOk || !fileAccessOk) {
            Toast.makeText(this, "Grant all permissions first!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        setLoading(true);
        
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String serverUrl = "http://203.175.125.151:10136/" + jwtToken + "/";
                    
                    JSONObject config = new JSONObject();
                    config.put("verAddr", "https://version-ggbluellama.vercel.app/live/");
                    config.put("serverLoginUrl", serverUrl);
                    
                    String configContent = config.toString(2);
                    boolean success = false;
                    
                    // Try direct write first (Android 10-)
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                        success = writeFileDirect(configContent);
                    }
                    
                    // Try SAF (Android 11+)
                    if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        success = writeFileViaSAF(configContent);
                    }
                    
                    // Try shell command as fallback
                    if (!success) {
                        success = writeFileViaShell(configContent);
                    }
                    
                    final boolean finalSuccess = success;
                    
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false);
                            
                            if (finalSuccess) {
                                isActive = true;
                                startStopButton.setText("\u23F9 STOP");
                                startStopButton.setBackgroundColor(Color.parseColor("#FF5252"));
                                updateStatus("\u25CF Active", "#00E676");
                                Toast.makeText(MainActivity.this, "\u2713 Config Created!", Toast.LENGTH_SHORT).show();
                            } else {
                                updateStatus("\u2717 Failed - Try SAF", "#FF5252");
                                Toast.makeText(MainActivity.this, "Failed! Use 'ACCESS GAME FOLDER' button.", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    
                } catch (Exception e) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false);
                            updateStatus("\u2717 Error: " + e.getMessage(), "#FF5252");
                        }
                    });
                }
            }
        });
    }

    private boolean writeFileDirect(String content) {
        try {
            File dir = new File(TARGET_PATH);
            if (!dir.exists()) dir.mkdirs();
            
            File file = new File(dir, CONFIG_FILE);
            if (file.exists()) file.delete();
            
            FileWriter fw = new FileWriter(file);
            fw.write(content);
            fw.flush();
            fw.close();
            
            return file.exists() && file.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean writeFileViaSAF(String content) {
        try {
            String savedUri = appPrefs.getString("saf_tree_uri", "");
            if (savedUri.isEmpty()) return false;
            
            Uri treeUri = Uri.parse(savedUri);
            DocumentFile rootDoc = DocumentFile.fromTreeUri(this, treeUri);
            
            if (rootDoc == null) return false;
            
            // Try to navigate or create directly
            DocumentFile targetFolder = rootDoc;
            
            // If we have Android folder, navigate deeper
            DocumentFile androidDir = rootDoc.findFile("Android");
            if (androidDir != null) {
                DocumentFile dataDir = androidDir.findFile("data");
                if (dataDir == null) dataDir = androidDir.createDirectory("data");
                
                if (dataDir != null) {
                    DocumentFile gameDir = dataDir.findFile("com.dts.freefireth");
                    if (gameDir == null) gameDir = dataDir.createDirectory("com.dts.freefireth");
                    
                    if (gameDir != null) {
                        DocumentFile filesDir = gameDir.findFile("files");
                        if (filesDir == null) filesDir = gameDir.createDirectory("files");
                        if (filesDir != null) targetFolder = filesDir;
                    }
                }
            }
            
            // Delete existing
            DocumentFile existing = targetFolder.findFile(CONFIG_FILE);
            if (existing != null) existing.delete();
            
            // Create new
            DocumentFile newFile = targetFolder.createFile("application/json", CONFIG_FILE);
            if (newFile == null) return false;
            
            OutputStream os = getContentResolver().openOutputStream(newFile.getUri());
            if (os == null) return false;
            
            os.write(content.getBytes());
            os.flush();
            os.close();
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean writeFileViaShell(String content) {
        try {
            String escapedContent = content.replace("'", "'\\''");
            String cmd = "mkdir -p '" + TARGET_PATH + "' && echo '" + escapedContent + "' > '" + TARGET_PATH + CONFIG_FILE + "'";
            
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            process.waitFor();
            
            // Verify
            Process checkProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", "test -f '" + TARGET_PATH + CONFIG_FILE + "' && echo 'OK' || echo 'FAIL'"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()));
            String result = reader.readLine();
            reader.close();
            checkProcess.waitFor();
            
            return "OK".equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    private void doStopService() {
        setLoading(true);
        
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                boolean deleted = false;
                
                try {
                    // Direct delete
                    File file = new File(TARGET_PATH + CONFIG_FILE);
                    if (file.exists()) {
                        deleted = file.delete();
                    }
                    
                    // SAF delete
                    if (!deleted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        String savedUri = appPrefs.getString("saf_tree_uri", "");
                        if (!savedUri.isEmpty()) {
                            Uri treeUri = Uri.parse(savedUri);
                            DocumentFile rootDoc = DocumentFile.fromTreeUri(MainActivity.this, treeUri);
                            if (rootDoc != null) {
                                DocumentFile existingFile = findFileRecursive(rootDoc, CONFIG_FILE);
                                if (existingFile != null) {
                                    deleted = existingFile.delete();
                                }
                            }
                        }
                    }
                    
                    // Shell delete
                    if (!deleted) {
                        Runtime.getRuntime().exec(new String[]{"sh", "-c", "rm -f '" + TARGET_PATH + CONFIG_FILE + "'"}).waitFor();
                        deleted = true;
                    }
                    
                } catch (Exception e) {
                    deleted = false;
                }
                
                final boolean finalDeleted = deleted;
                
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setLoading(false);
                        
                        if (finalDeleted) {
                            isActive = false;
                            startStopButton.setText("\u25B6 START");
                            startStopButton.setBackgroundColor(Color.parseColor("#00C853"));
                            updateStatus("\u25CB Stopped", "#FFD740");
                            Toast.makeText(MainActivity.this, "Config deleted", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private DocumentFile findFileRecursive(DocumentFile folder, String fileName) {
        DocumentFile found = folder.findFile(fileName);
        if (found != null) return found;
        
        DocumentFile[] children = folder.listFiles();
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                found = findFileRecursive(child, fileName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void doLogout() {
        jwtToken = "";
        currentUid = "";
        tokenInput.setText("");
        tokenPreviewText.setVisibility(View.GONE);
        uidText.setVisibility(View.GONE);
        controlPanel.setVisibility(View.GONE);
        appPrefs.edit().remove("saved_token").apply();
        
        if (isActive) doStopService();
        updateStatus("\u25CF Logged out", "#FFD740");
        Toast.makeText(this, "Logged out!", Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        loadingSpinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        verifyButton.setEnabled(!loading);
        startStopButton.setEnabled(!loading);
    }

    private void updateStatus(String msg, String colorHex) {
        statusLabel.setText(msg);
        statusLabel.setTextColor(Color.parseColor(colorHex));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!shizukuOk || !fileAccessOk) {
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    initShizuku();
                }
            }, 500);
        }
        
        String saved = appPrefs.getString("saved_token", "");
        if (!saved.isEmpty() && tokenInput.getText().toString().isEmpty()) {
            tokenInput.setText(saved);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (threadPool != null) threadPool.shutdown();
    }
}