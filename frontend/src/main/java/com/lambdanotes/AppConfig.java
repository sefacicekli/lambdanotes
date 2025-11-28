package com.lambdanotes;

public class AppConfig {
    private String repoUrl;
    private String token;
    private String username;
    private String email;
    private int editorFontSize = 14; // Default font size
    private String fontFamily = "JetBrains Mono"; // Default font family
    private boolean showLineNumbers = true; // Default show line numbers
    private String theme = "Dark"; // Default theme
    private boolean showTabs = false; // Default show tabs
    private boolean showTitleInPreview = true; // Default show title in preview
    private String language = "en"; // Default language

    public AppConfig(String repoUrl, String token, String username, String email) {
        this.repoUrl = repoUrl;
        this.token = token;
        this.username = username;
        this.email = email;
    }

    // Getters and Setters
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getEditorFontSize() { return editorFontSize; }
    public void setEditorFontSize(int editorFontSize) { this.editorFontSize = editorFontSize; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public boolean isShowLineNumbers() { return showLineNumbers; }
    public void setShowLineNumbers(boolean showLineNumbers) { this.showLineNumbers = showLineNumbers; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public boolean isShowTabs() { return showTabs; }
    public void setShowTabs(boolean showTabs) { this.showTabs = showTabs; }

    public boolean isShowTitleInPreview() { return showTitleInPreview; }
    public void setShowTitleInPreview(boolean showTitleInPreview) { this.showTitleInPreview = showTitleInPreview; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
