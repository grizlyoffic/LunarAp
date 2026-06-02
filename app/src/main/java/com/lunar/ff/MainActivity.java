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

    // Main UI
    private LinearLayout mainContent;
    private LinearLayout shizukuErrorLayout;
    
    // Error UI
    private ImageView errorIcon;
    private TextView errorTitle;
    private TextView errorMessage;
    private MaterialButton errorRetryBtn;
    
    // Token UI
    private EditText tokenInput;
    private MaterialButton verifyBtn;
    
    // Control UI
    private MaterialCardView controlCard;
    private MaterialButton startStopBtn;
    private MaterialButton logoutBtn;
    
    // Status
    private TextView statusText;
    private TextView jwtPreviewText;
    private TextView uidText;
    private ProgressBar progressBar;
    private View statusDot;
    
    // Data
    private String jwtToken = "";
    private String currentUid = "";
    private boolean isActive = false;
    private boolean isShizukuReady = false;
    
    private ExecutorService executor;
    private Handler mainHandler;
    private SharedPreferences prefs;

    // Shizuku Listener
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        runOnUiThread(() -> checkShizukuStatus());
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        runOnUiThread(() -> {
            isShizukuReady = false;
            showShizukuError(true);
        });
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = 
        (requestCode, grantResult) -> {
            runOnUiThread(() -> {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    checkShizukuStatus();
                } else {
                    showShizukuError(true);
                }
            });
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("LunarFF", MODE_PRIVATE);
        
        initViews();
        setupShizukuListeners();
        checkStoragePermission();
        
        // Auto check Shizuku
        if (Shizuku.pingBinder()) {
            checkShizukuStatus();
        } else {
            showShizukuError(true);
        }
    }

    private void initViews() {
        // Layouts
        mainContent = findViewById(R.id.mainContent);
        shizukuErrorLayout = findViewById(R.id.shizukuErrorLayout);
        
        // Error UI
        errorIcon = findViewById(R.id.errorIcon);
        errorTitle = findViewById(R.id.errorTitle);
        errorMessage = findViewById(R.id.errorMessage);
        errorRetryBtn = findViewById(R.id.errorRetryBtn);
        
        // Token UI
        tokenInput = findViewById(R.id.tokenInput);
        verifyBtn = findViewById(R.id.verifyBtn);
        
        // Control UI
        controlCard = findViewById(R.id.controlCard);
        startStopBtn = findViewById(R.id.startStopBtn);
        logoutBtn = findViewById(R.id.logoutBtn);
        
        // Status
        statusText = findViewById(R.id.statusText);
        jwtPreviewText = findViewById(R.id.jwtPreviewText);
        uidText = findViewById(R.id.uidText);
        progressBar = findViewById(R.id.progressBar);
        statusDot = findViewById(R.id.statusDot);
        
        // Initial state
        controlCard.setVisibility(View.GONE);
        startStopBtn.setEnabled(false);
        logoutBtn.setEnabled(false);
        
        // Button listeners
        verifyBtn.setOnClickListener(v -> verifyToken());
        
        startStopBtn.setOnClickListener(v -> {
            if (!isShizukuReady) {
                showShizukuNotConnected();
                return;
            }
            if (isActive) {
                stopService();
            } else {
                startService();
            }
        });
        
        logoutBtn.setOnClickListener(v -> logoutUser());
        
        errorRetryBtn.setOnClickListener(v -> {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(0);
                checkShizukuStatus();
            } else {
                Toast.makeText(this, "Please open Shizuku app first", Toast.LENGTH_LONG).show();
            }
        });
        
        // Restore saved token
        String savedToken = prefs.getString("lunar_token", "");
        if (!savedToken.isEmpty()) {
            tokenInput.setText(savedToken);
        }
    }

    private void setupShizukuListeners() {
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
            Shizuku.addRequestPermissionResultListener(permissionResultListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkShizukuStatus() {
        try {
            boolean hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            boolean isBinderAlive = Shizuku.pingBinder();
            
            if (hasPermission && isBinderAlive) {
                isShizukuReady = true;
                showShizukuError(false);
                updateStatus("Shizuku Connected ✓", "#00E676");
                
                // Animate UI appearance
                animateMainContentIn();
            } else if (isBinderAlive) {
                // Has Shizuku but no permission
                Shizuku.requestPermission(0);
                showShizukuError(true);
                errorTitle.setText("Permission Required");
                errorMessage.setText("Grant Shizuku permission to continue");
            } else {
                showShizukuError(true);
            }
        } catch (Exception e) {
            isShizukuReady = false;
            showShizukuError(true);
        }
    }

    private void showShizukuError(boolean show) {
        if (show) {
            shizukuErrorLayout.setVisibility(View.VISIBLE);
            mainContent.setVisibility(View.GONE);
            
            // Animate error icon
            AlphaAnimation anim = new AlphaAnimation(0.3f, 1.0f);
            anim.setDuration(1000);
            anim.setRepeatMode(Animation.REVERSE);
            anim.setRepeatCount(Animation.INFINITE);
            errorIcon.startAnimation(anim);
            
        } else {
            shizukuErrorLayout.setVisibility(View.GONE);
            mainContent.setVisibility(View.VISIBLE);
            errorIcon.clearAnimation();
        }
    }

    private void animateMainContentIn() {
        mainContent.setAlpha(0f);
        mainContent.animate()
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }

    private void showShizukuNotConnected() {
        Snackbar.make(findViewById(android.R.id.content), 
            "⚠️ Shizuku not connected! Please open Shizuku app first.", 
            Snackbar.LENGTH_LONG)
            .setAction("OPEN SHIZUKU", v -> {
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (intent != null) {
                        startActivity(intent);
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Shizuku app not installed", Toast.LENGTH_SHORT).show();
                }
            })
            .setActionTextColor(getColor(android.R.color.holo_green_light))
            .show();
    }

    private void checkStoragePermission() {
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
                                
                                // Extract UID
                                extractUidFromJwt(jwtToken);
                                
                                // Save token
                                prefs.edit().putString("lunar_token", inputToken).apply();
                                
                                // Update UI
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
                                
                                // Animation
                                controlCard.setAlpha(0f);
                                controlCard.animate().alpha(1f).setDuration(300).start();
                                
                                Toast.makeText(MainActivity.this, 
                                    "✓ Verification Successful!", Toast.LENGTH_SHORT).show();
                            } else {
                                updateStatus("✗ Invalid Token", "#FF5252");
                                Toast.makeText(MainActivity.this, 
                                    "Invalid token! Try again.", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            updateStatus("✗ Parse Error", "#FF5252");
                        }
                    } else {
                        updateStatus("✗ Connection Failed", "#FF5252");
                        Toast.makeText(MainActivity.this, 
                            "Network error! Check connection.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    verifyBtn.setEnabled(true);
                    updateStatus("✗ Error: " + e.getMessage(), "#FF5252");
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
        if (jwtToken.isEmpty()) {
            Toast.makeText(this, "Verify token first!", Toast.LENGTH_SHORT).show();
            return;
        }
        
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
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
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
                        startStopBtn.setStrokeColorResource(android.R.color.holo_red_dark);
                        updateStatus("● Active - Config Injected", "#00E676");
                        
                        // Pulse animation on dot
                        pulseStatusDot();
                        
                        Toast.makeText(MainActivity.this, 
                            "✓ Config created! Start Free Fire now.", Toast.LENGTH_LONG).show();
                    } else {
                        updateStatus("✗ Failed to create config", "#FF5252");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    startStopBtn.setEnabled(true);
                    updateStatus("✗ Error: " + e.getMessage(), "#FF5252");
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
                        startStopBtn.setStrokeColorResource(android.R.color.holo_green_light);
                        updateStatus("○ Stopped - Config Removed", "#FFD740");
                        statusDot.clearAnimation();
                        Toast.makeText(MainActivity.this, "Config deleted!", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    startStopBtn.setEnabled(true);
                    updateStatus("✗ Error: " + e.getMessage(), "#FF5252");
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
        Toast.makeText(this, "Logged out!", Toast.LENGTH_SHORT).show();
    }

    private void pulseStatusDot() {
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 0.3f);
        animator.setDuration(800);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(animation -> {
            statusDot.setAlpha((float) animation.getAnimatedValue());
        });
        animator.start();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            verifyBtn.setEnabled(false);
            startStopBtn.setEnabled(false);
        }
    }

    private void updateStatus(String message, String colorHex) {
        statusText.setText(message);
        statusText.setTextColor(android.graphics.Color.parseColor(colorHex));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check Shizuku when app resumes
        if (Shizuku.pingBinder()) {
            checkShizukuStatus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
            Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}