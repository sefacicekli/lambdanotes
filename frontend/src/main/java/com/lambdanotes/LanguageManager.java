package com.lambdanotes;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class LanguageManager {
    private static final Logger logger = Logger.getLogger(LanguageManager.class.getName());
    private static ResourceBundle bundle;
    private static Locale currentLocale;

    static {
        // Default to English
        setLanguage("en");
    }

    public static void setLanguage(String langCode) {
        try {
            currentLocale = new Locale(langCode);
            bundle = ResourceBundle.getBundle("com.lambdanotes.messages", currentLocale);
        } catch (Exception e) {
            logger.warning("Could not load language bundle for " + langCode + ": " + e.getMessage());
            // Fallback to English if failed
            if (!"en".equals(langCode)) {
                setLanguage("en");
            }
        }
    }

    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return key; // Return key if not found
        }
    }
    
    public static Locale getCurrentLocale() {
        return currentLocale;
    }
}
