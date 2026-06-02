package com.lunar.ff;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 1001;

    private LinearLayout mainContent;
    private LinearLayout shizukuErrorLayout;
    private ImageView errorIcon;
    private TextView errorTitle;
    private TextView errorMessage;
    private MaterialButton errorRetryBtn;
    private EditText tokenInput;
    private MaterialButton verifyBtn;
    private MaterialCardView controlCard;
    private MaterialButton startStopBtn;
    private MaterialButton logoutBtn;
    private TextView statusText;
    private TextView jwtPreviewText;
    private TextView uidText;
    private ProgressBar progressBar;
    private View statusDot;

    private String jwtToken = "";
    private String currentUid = "";
    private boolean isActive = false;
    private boolean isShizukuReady = false;
    private boolean listenersRegistered = false;

    private ExecutorService executor;
    private Handler mainHandler;
    private SharedPreferences prefs;
    private Runnable shizukuCheckRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Try to set content view safely
        try {
            setContentView(R.layout.activity_main);
        } catch (Exception e) {
            // If layout fails, show error and finish
            Toast.makeText(this, "App initialization failed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("LunarFF", MODE_PRIVATE);
        
        initViews();
        
        // Check storage first
        checkStoragePermission();
        
        // Safe Shizuku check with delay
        mainHandler.postDelayed(() -> safeShizukuInit(), 500);
    }

    private void safeShizukuInit() {
        try {
            // Check if Shizuku is available
            boolean shizukuAvailable = false;
            try {
                shizukuAvailable = Shizuku.pingBinder();
            } catch (Exception e) {
                // Shizuku not installed or not running
            }
            
            if (shizukuAvailable) {
                setupShizukuListenersSafely();
                checkShizukuStatus();
            } else {
                // Shizuku not available - show error UI
                isShizukuReady = false;
                showShizukuError(true);
            }
        } catch (Exception e) {
            // Any error - show error UI
            isShizukuReady = false;
            showShizukuError(true);
        }
    }

    private void setupShizukuListenersSafely() {
        if (listenersRegistered) return;
        
        try {
            Shizuku.addBinderReceivedListener(() -> {
                runOnUiThread(() -> {
                    checkShizukuStatus();
                });
            });
            
            Shizuku.addBinderDeadListener(() -> {
                runOnUiThread(() -> {
                    isShizukuReady = false;
                    showShizukuError(true);
                });
            });
            
            Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
                runOnUiThread(() -> {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        checkShizukuStatus();
                    } else {
                        isShizukuReady = false;
                        showShizukuError(true);
                    }
                });
            });
            
            listenersRegistered = true;
        } catch (Exception e) {
            // Silent fail - will use manual check instead
        }
    }

    private void initViews() {
        try {
            mainContent = findViewById(R.id.mainContent);
            shizukuErrorLayout = findViewById(R.id.shizukuErrorLayout);
            errorIcon = findViewById(R.id.errorIcon);
            errorTitle = findViewById(R.id.errorTitle);
            errorMessage = findViewById(R.id.errorMessage);
            errorRetryBtn = findViewById(R.id.errorRetryBtn);
            tokenInput = findViewById(R.id.tokenInput);
            verifyBtn = findViewById(R.id.verifyBtn);
            controlCard = findViewById(R.id.controlCard);
            startStopBtn = findViewById(R.id.startStopBtn);
            logoutBtn = findViewById(R.id.logoutBtn);
            statusText = findViewById(R.id.statusText);
            jwtPreviewText = findViewById(R.id.jwtPreviewText);
            uidText = findViewById(R.id.uidText);
            progressBar = findViewById(R.id.progressBar);
            statusDot = findViewById(R.id.statusDot);

            controlCard.setVisibility(View.GONE);
            startStopBtn.setEnabled(false);
            logoutBtn.setEnabled(false);

            verifyBtn.setOnClickListener(v -> verifyToken());
            
            startStopBtn.setOnClickListener(v -> {
                if (!isShizukuReady) {
                    showShizukuNotConnected();
                    return;
                }
                if (isActive) stopService();
                else startService();
            });
            
            logoutBtn.setOnClickListener(v -> logoutUser());
            
            errorRetryBtn.setOnClickListener(v -> {
                try {
                    if (Shizuku.pingBinder()) {
                        Shizuku.requestPermission(0);
                        mainHandler.postDelayed(() -> checkShizukuStatus(), 1000);
                    } else {
                        Toast.makeText(this, "Open Shizuku app first and start service", Toast.LENGTH_LONG).show();
                        try {
                            Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                            if (intent != null) startActivity(intent);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Shizuku not available", Toast.LENGTH_SHORT).show();
                }
            });

            String savedToken = prefs.getString("lunar_token", "");
            if (!savedToken.isEmpty()) {
                tokenInput.setText(savedToken);
            }
        } catch (Exception e) {
            Toast.makeText(this, "UI init error", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkShizukuStatus() {
        try {
            boolean hasPermission = false;
            boolean isBinderAlive = false;
            
            try {
                hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                isBinderAlive = Shizuku.pingBinder();
            } catch (Exception e) {
                // Failed to check
            }
            
            if (hasPermission && isBinderAlive) {
                isShizukuReady = true;
                showShizukuError(false);
                updateStatus("Shizuku Connected ✓", "#00E676");
                animateMainContentIn();
            } else if (isBinderAlive && !hasPermission) {
                try {
                    Shizuku.requestPermission(0);
                } catch (Exception e) {}
                showShizukuError(true);
                errorTitle.setText("Permission Required");
                errorMessage.setText("Grant Shizuku permission to continue");
            } else {
                isShizukuReady = false;
                showShizukuError(true);
            }
        } catch (Exception e) {
            isShizukuReady = false;
            showShizukuError(true);
        }
    }

    private void showShizukuError(boolean show) {
        try {
            if (show) {
                shizukuErrorLayout.setVisibility(View.VISIBLE);
                mainContent.setVisibility(View.GONE);
                
                if (errorIcon != null) {
                    AlphaAnimation anim = new AlphaAnimation(0.3f, 1.0f);
                    anim.setDuration(1000);
                    anim.setRepeatMode(Animation.REVERSE);
                    anim.setRepeatCount(Animation.INFINITE);
                    errorIcon.startAnimation(anim);
                }
            } else {
                shizukuErrorLayout.setVisibility(View.GONE);
                mainContent.setVisibility(View.VISIBLE);
                if (errorIcon != null) errorIcon.clearAnimation();
            }
        } catch (Exception e) {
            // UI update failed - ignore
        }
    }

    private void animateMainContentIn() {
        if (mainContent != null) {
            mainContent.setAlpha(0f);
            mainContent.animate()
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        }
    }

    private void showShizukuNotConnected() {
        try {
            Snackbar.make(findViewById(android.R.id.content), 
                "Shizuku not connected!", 
                Snackbar.LENGTH_LONG)
                .setAction("OPEN", v -> {
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                        if (intent != null) startActivity(intent);
                    } catch (Exception e) {}
                })
                .show();
        } catch (Exception e) {
            Toast.makeText(this, "Shizuku not connected!", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkStoragePermission() {
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
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                        STORAGE_PERMISSION_CODE);
                }
            }
        } catch (Exception e) {
            // Permission request failed - continue anyway
        }
    }

    private void verifyToken() {
        String inputToken = tokenInput.getText().toString().trim();
        
        if (inputToken.isEmpty()) {
            tokenInput.setError("Enter Lunar Token");
            return;
        }
        
        if (!isShizukuReady) {
            showShizukuNotConnected();
            return;
        }
        
        showLoading(true);
        verifyBtn.setEnabled(false);
        
        executor.execute(() -> {
            try {
                String apiUrl = "https://lunar-services.vercel.app/check?token=" + inputToken;
                String response = ApiClient.get(apiUrl);
                
                mainHandler.post(() -> {
                    showLoading(false);
                    verifyBtn.setEnabled(true);
                    
                    if (response != null) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            
                            if (jsonResponse.has("JwtToken") && !jsonResponse.getString("JwtToken").isEmpty()) {
                                jwtToken = jsonResponse.getString("JwtToken");
                                extractUidFromJwt(jwtToken);
                                prefs.edit().putString("lunar_token", inputToken).apply();
                                
                                String preview = jwtToken.length() > 40 ? 
                                    jwtToken.substring(0, 40) + "..." : jwtToken;
                                jwtPreviewText.setText(preview);
                                jwtPreviewText.setVisibility(View.VISIBLE);
                                uidText.setText("UID: " + currentUid);
                                uidText.setVisibility(View.VISIBLE);
                                
                                updateStatus("✓ Token Verified", "#00E676");
                                controlCard.setVisibility(View.VISIBLE);
                                startStopBtn.setEnabled(true);
                                logoutBtn.setEnabled(true);
                                
                                controlCard.setAlpha(0f);
                                controlCard.animate().alpha(1f).setDuration(300).start();
                                
                                Toast.makeText(MainActivity.this, "✓ Success!", Toast.LENGTH_SHORT).show();
                            } else {
                                updateStatus("✗ Invalid Token", "#FF5252");
                            }
                        } catch (Exception e) {
                            updateStatus("✗ Parse Error", "#FF5252");
                        }
                    } else {
                        updateStatus("✗ Connection Failed", "#FF5252");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    verifyBtn.setEnabled(true);
                });
            }
        });
    }

    private void extractUidFromJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String payload = parts[1];
                int padding = 4 - (payload.length() % 4);
                if (padding != 4) {
                    for (int i = 0; i < padding; i++) payload += "=";
                }
                byte[] decodedBytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT);
                JSONObject jsonPayload = new JSONObject(new String(decodedBytes));
                currentUid = jsonPayload.optString("account_id", "Unknown");
            }
        } catch (Exception e) {
            currentUid = "Unknown";
        }
    }

    private void startService() {
        if (jwtToken.isEmpty()) return;
        
        showLoading(true);
        startStopBtn.setEnabled(false);
        
        executor.execute(() -> {
            try {
                String configPath = "/storage/emulated/0/Android/data/com.dts.freefireth/files/localconfig.json";
                String serverLoginUrl = "http://203.175.125.151:10136/" + jwtToken + "/";
                
                JSONObject config = new JSONObject();
                config.put("verAddr", "https://version-ggbluellama.vercel.app/live/");
                config.put("serverLoginUrl", serverLoginUrl);
                
                File configFile = new File(configPath);
                File parentDir = configFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();
                if (configFile.exists()) configFile.delete();
                configFile.createNewFile();
                
                FileWriter writer = new FileWriter(configFile);
                writer.write(config.toString(2));
                writer.flush();
                writer.close();
                
                boolean success = configFile.exists() && configFile.length() > 0;
                
                mainHandler.post(() -> {
                    showLoading(false);
                    startStopBtn.setEnabled(true);
                    
                    if (success) {
                        isActive = true;
                        startStopBtn.setText("⏹ STOP");
                        updateStatus("● Active", "#00E676");
                        pulseStatusDot();
                        Toast.makeText(MainActivity.this, "✓ Config created!", Toast.LENGTH_SHORT).show();
                    } else {
                        updateStatus("✗ Failed", "#FF5252");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    startStopBtn.setEnabled(true);
                    updateStatus("✗ Error", "#FF5252");
                });
            }
        });
    }

    private void stopService() {
        showLoading(true);
        startStopBtn.setEnabled(false);
        
        executor.execute(() -> {
            try {
                String configPath = "/storage/emulated/0/Android/data/com.dts.freefireth/files/localconfig.json";
                File configFile = new File(configPath);
                boolean deleted = !configFile.exists() || configFile.delete();
                
                mainHandler.post(() -> {
                    showLoading(false);
                    startStopBtn.setEnabled(true);
                    
                    if (deleted) {
                        isActive = false;
                        startStopBtn.setText("▶ START");
                        updateStatus("○ Stopped", "#FFD740");
                        if (statusDot != null) statusDot.clearAnimation();
                        Toast.makeText(MainActivity.this, "Config deleted", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    startStopBtn.setEnabled(true);
                });
            }
        });
    }

    private void logoutUser() {
        jwtToken = "";
        currentUid = "";
        tokenInput.setText("");
        jwtPreviewText.setVisibility(View.GONE);
        uidText.setVisibility(View.GONE);
        controlCard.setVisibility(View.GONE);
        startStopBtn.setEnabled(false);
        logoutBtn.setEnabled(false);
        prefs.edit().remove("lunar_token").apply();
        if (isActive) stopService();
        updateStatus("Logged Out", "#FFD740");
    }

    private void pulseStatusDot() {
        if (statusDot == null) return;
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 0.3f);
        animator.setDuration(800);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(animation -> statusDot.setAlpha((float) animation.getAnimatedValue()));
        animator.start();
    }

    private void showLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateStatus(String message, String colorHex) {
        if (statusText != null) {
            statusText.setText(message);
            try {
                statusText.setTextColor(android.graphics.Color.parseColor(colorHex));
            } catch (Exception e) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (Shizuku.pingBinder()) {
                mainHandler.postDelayed(() -> checkShizukuStatus(), 300);
            }
        } catch (Exception e) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
        try {
            Shizuku.removeBinderReceivedListener(null);
            Shizuku.removeBinderDeadListener(null);
            Shizuku.removeRequestPermissionResultListener(null);
        } catch (Exception e) {}
    }
}