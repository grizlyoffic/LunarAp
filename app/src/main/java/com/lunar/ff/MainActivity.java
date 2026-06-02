package com.lunar.ff;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;
import org.json.JSONObject;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 1001;
    
    private EditText tokenInput;
    private MaterialButton verifyBtn;
    private MaterialButton startStopBtn;
    private MaterialButton logoutBtn;
    private TextView statusText;
    private TextView jwtPreviewText;
    private ProgressBar progressBar;
    
    private String jwtToken = "";
    private String currentUid = "";
    private boolean isActive = false;
    private boolean isShizukuAvailable = false;
    
    private ShizukuManager shizukuManager;
    private FileHelper fileHelper;
    private ExecutorService executor;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        shizukuManager = new ShizukuManager();
        fileHelper = new FileHelper();
        
        initViews();
        checkShizukuStatus();
        checkPermissions();
    }

    private void initViews() {
        tokenInput = findViewById(R.id.tokenInput);
        verifyBtn = findViewById(R.id.verifyBtn);
        startStopBtn = findViewById(R.id.startStopBtn);
        logoutBtn = findViewById(R.id.logoutBtn);
        statusText = findViewById(R.id.statusText);
        jwtPreviewText = findViewById(R.id.jwtPreviewText);
        progressBar = findViewById(R.id.progressBar);
        
        // Initial state
        startStopBtn.setEnabled(false);
        logoutBtn.setEnabled(false);
        startStopBtn.setText("START");
        updateStatus("Waiting for Shizuku...", R.color.yellow);
        
        // Verify Token Button
        verifyBtn.setOnClickListener(v -> verifyToken());
        
        // Start/Stop Button
        startStopBtn.setOnClickListener(v -> {
            if (isActive) {
                stopService();
            } else {
                startService();
            }
        });
        
        // Logout Button
        logoutBtn.setOnClickListener(v -> logoutUser());
    }

    private void checkShizukuStatus() {
        try {
            Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        isShizukuAvailable = true;
                        updateStatus("Shizuku Connected ✓", R.color.green);
                    }
                }
            });
            
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                isShizukuAvailable = true;
                updateStatus("Shizuku Ready ✓", R.color.green);
            } else {
                Shizuku.requestPermission(0);
                updateStatus("Grant Shizuku Permission", R.color.yellow);
            }
        } catch (Exception e) {
            updateStatus("Shizuku Error: " + e.getMessage(), R.color.red);
        }
    }

    private void checkPermissions() {
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
            Toast.makeText(this, "Please enter Lunar Token", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!isShizukuAvailable) {
            Toast.makeText(this, "Shizuku permission required!", Toast.LENGTH_LONG).show();
            return;
        }
        
        showLoading(true);
        verifyBtn.setEnabled(false);
        
        executor.execute(() -> {
            try {
                String apiUrl = "https://127.0.0.1:8080/check?token=" + inputToken;
                String response = ApiClient.get(apiUrl);
                
                mainHandler.post(() -> {
                    showLoading(false);
                    verifyBtn.setEnabled(true);
                    
                    if (response != null && !response.isEmpty()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            
                            if (jsonResponse.has("JwtToken")) {
                                jwtToken = jsonResponse.getString("JwtToken");
                                
                                // Show preview
                                String preview = jwtToken.length() > 50 ? 
                                    jwtToken.substring(0, 50) + "..." : jwtToken;
                                jwtPreviewText.setText("Token: " + preview);
                                jwtPreviewText.setVisibility(View.VISIBLE);
                                
                                // Extract UID
                                extractUidFromJwt(jwtToken);
                                
                                updateStatus("Token Verified ✓ | UID: " + currentUid, R.color.green);
                                startStopBtn.setEnabled(true);
                                logoutBtn.setEnabled(true);
                                
                                Toast.makeText(MainActivity.this, 
                                    "Verification Successful!", Toast.LENGTH_SHORT).show();
                            } else {
                                updateStatus("Invalid token response", R.color.red);
                                Toast.makeText(MainActivity.this, 
                                    "Invalid token! Try again.", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            updateStatus("Parse Error: " + e.getMessage(), R.color.red);
                        }
                    } else {
                        updateStatus("API Connection Failed", R.color.red);
                        Toast.makeText(MainActivity.this, 
                            "Failed to verify token!", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    verifyBtn.setEnabled(true);
                    updateStatus("Error: " + e.getMessage(), R.color.red);
                });
            }
        });
    }

    private void extractUidFromJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String payload = parts[1];
                // Add padding
                int padding = 4 - (payload.length() % 4);
                if (padding != 4) {
                    for (int i = 0; i < padding; i++) {
                        payload += "=";
                    }
                }
                byte[] decodedBytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT);
                String decodedString = new String(decodedBytes);
                JSONObject jsonPayload = new JSONObject(decodedString);
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
        
        if (!isShizukuAvailable) {
            Toast.makeText(this, "Shizuku permission required!", Toast.LENGTH_LONG).show();
            return;
        }
        
        showLoading(true);
        startStopBtn.setEnabled(false);
        
        executor.execute(() -> {
            try {
                // Create localconfig.json
                String configPath = "/storage/emulated/0/Android/data/com.dts.freefireth/files/localconfig.json";
                String serverLoginUrl = "http://203.175.125.151:10136/" + jwtToken + "/";
                
                JSONObject config = new JSONObject();
                config.put("verAddr", "https://version-ggbluellama.vercel.app/live/");
                config.put("serverLoginUrl", serverLoginUrl);
                
                boolean success = fileHelper.createConfigFile(configPath, config.toString(2));
                
                mainHandler.post(() -> {
                    showLoading(false);
                    startStopBtn.setEnabled(true);
                    
                    if (success) {
                        isActive = true;
                        startStopBtn.setText("STOP");
                        startStopBtn.setBackgroundTintList(
                            getColorStateList(R.color.red));
                        updateStatus("Active ✓ | Config Created", R.color.green);
                        Toast.makeText(MainActivity.this, 
                            "Config created successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        updateStatus("Failed to create config", R.color.red);
                        Toast.makeText(MainActivity.this, 
                            "Failed! Check Shizuku permissions.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    startStopBtn.setEnabled(true);
                    updateStatus("Error: " + e.getMessage(), R.color.red);
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
                boolean success = fileHelper.deleteConfigFile(configPath);
                
                mainHandler.post(() -> {
                    showLoading(false);
                    startStopBtn.setEnabled(true);
                    
                    if (success) {
                        isActive = false;
                        startStopBtn.setText("START");
                        startStopBtn.setBackgroundTintList(
                            getColorStateList(R.color.green));
                        updateStatus("Stopped | Config Deleted", R.color.yellow);
                        Toast.makeText(MainActivity.this, 
                            "Config deleted!", Toast.LENGTH_SHORT).show();
                    } else {
                        updateStatus("Failed to delete config", R.color.red);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    startStopBtn.setEnabled(true);
                    updateStatus("Error: " + e.getMessage(), R.color.red);
                });
            }
        });
    }

    private void logoutUser() {
        jwtToken = "";
        currentUid = "";
        tokenInput.setText("");
        jwtPreviewText.setVisibility(View.GONE);
        startStopBtn.setEnabled(false);
        logoutBtn.setEnabled(false);
        
        // Stop if active
        if (isActive) {
            stopService();
        }
        
        updateStatus("Logged Out | Waiting for token...", R.color.yellow);
        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateStatus(String message, int colorRes) {
        statusText.setText(message);
        statusText.setTextColor(getColor(colorRes));
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
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (Shizuku.pingBinder()) {
            Shizuku.removeRequestPermissionResultListener(null);
        }
    }
}
