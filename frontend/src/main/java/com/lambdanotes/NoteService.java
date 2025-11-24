package com.lambdanotes;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NoteService {
    private static final Logger logger = Logger.getLogger(NoteService.class.getName());
    private static final String API_URL = "http://localhost:3286/api";
    private final HttpClient client;
    private final Gson gson;

    public NoteService() {
        this.client = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public CompletableFuture<List<String>> getNotes() {
        logger.info("Requesting notes from backend...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/notes"))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    logger.info("GetNotes response code: " + response.statusCode());
                    logger.info("GetNotes response body: " + response.body());
                    return response;
                })
                .thenApply(HttpResponse::body)
                .thenApply(body -> gson.fromJson(body, new TypeToken<List<String>>(){}.getType()));
    }

    public CompletableFuture<Note> getNoteDetail(String filename) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/notes/" + filename))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body -> gson.fromJson(body, Note.class));
    }

    public CompletableFuture<Void> saveNote(Note note) {
        String json = gson.toJson(note);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/notes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> null);
    }

    public CompletableFuture<Void> deleteNote(String filename) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/notes/" + filename))
                .DELETE()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> null);
    }

    public CompletableFuture<Void> moveNote(String oldPath, String newPath) {
        String json = String.format("{\"oldPath\": \"%s\", \"newPath\": \"%s\"}", oldPath, newPath);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/move"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Move failed: " + response.body());
                    }
                    return null;
                });
    }

    public CompletableFuture<Void> syncNotes() {
        String json = "{\"message\": \"Manual sync\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/sync"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Sync failed: " + response.body());
                    }
                    return null;
                });
    }

    public CompletableFuture<Void> saveConfig(AppConfig config) {
        String json = gson.toJson(config);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/config"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Config failed: " + response.body());
                    }
                    return null;
                });
    }

    public CompletableFuture<List<GitHubRepo>> fetchUserRepos(String token) {
        logger.info("Fetching user repos...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/user/repos?sort=updated&per_page=100"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.severe("GitHub API Error (fetchUserRepos): " + response.body());
                        throw new RuntimeException("GitHub API Error: " + response.body());
                    }
                    logger.info("User repos fetched successfully.");
                    return gson.fromJson(response.body(), new TypeToken<List<GitHubRepo>>(){}.getType());
                });
    }

    public CompletableFuture<GitHubUser> fetchGitHubUser(String token) {
        logger.info("Fetching GitHub user info...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/user"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.severe("GitHub API Error (fetchGitHubUser): " + response.body());
                        throw new RuntimeException("GitHub API Error: " + response.body());
                    }
                    logger.info("GitHub user info fetched successfully.");
                    return gson.fromJson(response.body(), GitHubUser.class);
                });
    }

    public static class GithubDeviceCodeResponse {
        public String device_code;
        public String user_code;
        public String verification_uri;
        public int expires_in;
        public int interval;
    }

    public static class GithubTokenResponse {
        public String access_token;
        public String token_type;
        public String scope;
        public String error;
        public String error_description;
    }

    public CompletableFuture<GithubDeviceCodeResponse> startGithubAuth() {
        logger.info("Starting GitHub Device Flow...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/auth/github/start"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.severe("Auth start failed: " + response.body());
                        throw new RuntimeException("Auth start failed: " + response.body());
                    }
                    logger.info("GitHub Device Flow started.");
                    return gson.fromJson(response.body(), GithubDeviceCodeResponse.class);
                });
    }

    public CompletableFuture<GithubTokenResponse> pollGithubAuth(String deviceCode) {
        String json = String.format("{\"device_code\": \"%s\"}", deviceCode);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/auth/github/poll"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                     // Log response if needed, but polling can be spammy
                     return gson.fromJson(response.body(), GithubTokenResponse.class);
                });
    }

    public CompletableFuture<AppConfig> getConfig() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/config"))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Failed to get config");
                    }
                    return gson.fromJson(response.body(), AppConfig.class);
                });
    }

    public static class GitInfo {
        public String branch;
        public String commit;
        public String remoteUrl;
    }

    public CompletableFuture<GitInfo> getGitInfo() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/git/info"))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Failed to get git info: " + response.body());
                    }
                    return gson.fromJson(response.body(), GitInfo.class);
                });
    }

    public static class GitFileStatus {
        public boolean tracked;
    }

    public CompletableFuture<Boolean> isNoteTracked(String filename) {
        String encodedFilename;
        try {
            encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            encodedFilename = filename;
        }
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/git/file-status?path=" + encodedFilename))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        return false;
                    }
                    GitFileStatus status = gson.fromJson(response.body(), GitFileStatus.class);
                    return status.tracked;
                })
                .exceptionally(e -> false);
    }

    public static class UploadResponse {
        public String url;
        public String filename;
        public String localPath;
    }

    public CompletableFuture<UploadResponse> uploadImage(java.io.File file) {
        String boundary = "---" + System.currentTimeMillis() + "---";
        
        HttpRequest.BodyPublisher bodyPublisher;
        try {
            bodyPublisher = ofMimeMultipartData(java.util.Map.of("file", file), boundary);
        } catch (java.io.IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(bodyPublisher)
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Upload failed: " + response.body());
                    }
                    return gson.fromJson(response.body(), UploadResponse.class);
                });
    }

    // Helper for multipart body
    private HttpRequest.BodyPublisher ofMimeMultipartData(java.util.Map<Object, Object> data, String boundary) throws java.io.IOException {
        var byteArrays = new ArrayList<byte[]>();
        byte[] separator = ("--" + boundary + "\r\nContent-Disposition: form-data; name=").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (var entry : data.entrySet()) {
            byteArrays.add(separator);

            if (entry.getValue() instanceof java.io.File) {
                var file = (java.io.File) entry.getValue();
                String path = file.toPath().toString();
                String mimeType = java.nio.file.Files.probeContentType(file.toPath());
                if (mimeType == null) mimeType = "application/octet-stream";
                
                byteArrays.add(("\"" + entry.getKey() + "\"; filename=\"" + file.getName() + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                byteArrays.add(java.nio.file.Files.readAllBytes(file.toPath()));
                byteArrays.add("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                byteArrays.add(("\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        byteArrays.add(("--" + boundary + "--").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
    }
}

