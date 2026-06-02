package com.lunar.ff;

import java.io.File;

public class FileHelper {
    
    public static boolean deleteFile(String path) {
        try {
            File file = new File(path);
            return !file.exists() || file.delete();
        } catch (Exception e) {
            return false;
        }
    }
    
    public static boolean fileExists(String path) {
        return new File(path).exists();
    }
}
