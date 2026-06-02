package com.lunar.ff;

import android.os.Environment;
import java.io.File;
import java.io.FileWriter;

public class FileHelper {
    
    public boolean createConfigFile(String path, String content) {
        try {
            File configFile = new File(path);
            
            // Create parent directories
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Delete if exists
            if (configFile.exists()) {
                configFile.delete();
            }
            
            // Create new file
            configFile.createNewFile();
            
            // Write content
            FileWriter writer = new FileWriter(configFile);
            writer.write(content);
            writer.flush();
            writer.close();
            
            // Set permissions
            configFile.setReadable(true, false);
            configFile.setWritable(true, false);
            
            return configFile.exists() && configFile.length() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean deleteConfigFile(String path) {
        try {
            File configFile = new File(path);
            if (configFile.exists()) {
                return configFile.delete();
            }
            return true; // File doesn't exist, consider it deleted
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}