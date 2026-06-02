package com.lunar.ff;

import android.content.pm.PackageManager;
import rikka.shizuku.Shizuku;

public class ShizukuManager {
    
    public boolean checkPermission() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }
    
    public void requestPermission() {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public boolean isBinderAlive() {
        try {
            return Shizuku.pingBinder();
        } catch (Exception e) {
            return false;
        }
    }
}