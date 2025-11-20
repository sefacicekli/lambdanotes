package com.lambdanotes;

public class AppConfig {
    private String repoUrl;
    private String token;
    private String username;
    private String email;

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
}
