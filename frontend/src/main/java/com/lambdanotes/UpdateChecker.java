package com.lambdanotes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {

    private static final String REPO_OWNER = "sefacicekli";
    private static final String REPO_NAME = "lambdanotes";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases/latest";

    public static class UpdateInfo {
        public final String version;
        public final String url;
        public final String body;

        public UpdateInfo(String version, String url, String body) {
            this.version = version;
            this.url = url;
            this.body = body;
        }
    }

    public CompletableFuture<UpdateInfo> checkForUpdates(String currentVersion) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API_URL))
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            String tagName = json.get("tag_name").getAsString();
                            String htmlUrl = json.get("html_url").getAsString();
                            String body = json.has("body") && !json.get("body").isJsonNull() ? json.get("body").getAsString() : "";

                            if (isNewerVersion(currentVersion, tagName)) {
                                return new UpdateInfo(tagName, htmlUrl, body);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                });
    }

    private boolean isNewerVersion(String current, String latest) {
        String v1 = current.replaceAll("^v", "");
        String v2 = latest.replaceAll("^v", "");
        
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (num2 > num1) return true;
            if (num2 < num1) return false;
        }
        return false;
    }
}
