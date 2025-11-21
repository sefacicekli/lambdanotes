package com.lambdanotes;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.paint.Color;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SettingsDialog extends Stage {

    private static final Logger logger = Logger.getLogger(SettingsDialog.class.getName());
    private final NoteService noteService;
    private AppConfig result = null;
    private AppConfig currentConfig;

    // UI Components
    private BorderPane root;
    private ListView<String> categoryList;
    private StackPane contentArea;
    
    // GitHub Settings Components
    private TextField tokenField;
    private ComboBox<GitHubRepo> repoComboBox;
    private Label statusLabel;
    private GitHubUser currentUser;
    private Label connectedUserLabel;

    public SettingsDialog(NoteService noteService, AppConfig currentConfig) {
        this.noteService = noteService;
        this.currentConfig = currentConfig;
        
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.TRANSPARENT);

        root = new BorderPane();
        root.getStyleClass().add("settings-dialog");
        root.setPrefSize(800, 600);

        // Sidebar
        categoryList = new ListView<>();
        categoryList.getStyleClass().add("settings-sidebar");
        categoryList.getItems().addAll("Genel", "Görünüm", "GitHub", "Hakkında");
        categoryList.getSelectionModel().select("GitHub"); // Default selection
        categoryList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchView(newVal);
        });

        // Content Area
        contentArea = new StackPane();
        contentArea.getStyleClass().add("settings-content");
        
        // Initial View
        switchView("GitHub");

        root.setLeft(categoryList);
        root.setCenter(contentArea);
        
        // Footer (Buttons)
        HBox footer = createFooter();
        root.setBottom(footer);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        setScene(scene);

        // Drag Logic
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        root.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            setX(event.getScreenX() - xOffset[0]);
            setY(event.getScreenY() - yOffset[0]);
        });
    }

    private void switchView(String category) {
        contentArea.getChildren().clear();
        switch (category) {
            case "GitHub":
                contentArea.getChildren().add(createGitHubSettingsView());
                break;
            case "Genel":
            case "Görünüm":
            case "Hakkında":
                Label placeholder = new Label(category + " ayarları yakında...");
                placeholder.setStyle("-fx-text-fill: #7c7f88; -fx-font-size: 14px;");
                contentArea.getChildren().add(placeholder);
                break;
        }
    }

    private VBox createGitHubSettingsView() {
        VBox view = new VBox(20);
        view.setAlignment(Pos.TOP_LEFT);
        
        Label header = new Label("GitHub Entegrasyonu");
        header.getStyleClass().add("settings-header-label");
        
        // Section 1: Authentication
        VBox authSection = new VBox(10);
        Label authLabel = new Label("Hesap Bağlantısı");
        authLabel.getStyleClass().add("settings-section-label");
        
        Button githubLoginBtn = new Button("GitHub ile Bağlan (Device Flow)");
        githubLoginBtn.getStyleClass().add("dialog-button-primary");
        githubLoginBtn.setMaxWidth(Double.MAX_VALUE);
        
        // Auth Info Box (Hidden by default)
        VBox authInfoBox = new VBox(10);
        authInfoBox.setVisible(false);
        authInfoBox.setManaged(false);
        authInfoBox.setStyle("-fx-background-color: #2b2d30; -fx-padding: 15; -fx-background-radius: 6; -fx-border-color: #43454a; -fx-border-radius: 6;");

        Label authInstruction = new Label("Aşağıdaki kodu kopyalayın ve açılan sayfaya yapıştırın:");
        authInstruction.setWrapText(true);
        authInstruction.setStyle("-fx-text-fill: #bcbec4; -fx-font-size: 13px;");

        HBox codeBox = new HBox(10);
        codeBox.setAlignment(Pos.CENTER_LEFT);

        TextField userCodeField = new TextField();
        userCodeField.setEditable(false);
        userCodeField.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #1e1f22; -fx-text-fill: #98c379; -fx-border-color: #43454a; -fx-border-radius: 4;");
        userCodeField.setPrefWidth(150);

        Button copyBtn = new Button("Kopyala & Aç");
        copyBtn.getStyleClass().add("dialog-button-secondary");

        codeBox.getChildren().addAll(userCodeField, copyBtn);
        authInfoBox.getChildren().addAll(authInstruction, codeBox);
        
        githubLoginBtn.setOnAction(e -> startGithubLogin(githubLoginBtn, authInfoBox, userCodeField, copyBtn));
        
        // Manual Token Input
        VBox tokenBox = new VBox(5);
        Label tokenLabel = new Label("veya Personal Access Token girin:");
        tokenLabel.getStyleClass().add("settings-hint-label");
        
        HBox tokenInputBox = new HBox(10);
        tokenField = new TextField();
        tokenField.setPromptText("ghp_...");
        tokenField.getStyleClass().add("settings-text-field");
        HBox.setHgrow(tokenField, Priority.ALWAYS);
        
        Button loadReposBtn = new Button("Giriş Yap");
        loadReposBtn.getStyleClass().add("dialog-button-secondary");
        loadReposBtn.setOnAction(e -> loadRepos());
        
        tokenInputBox.getChildren().addAll(tokenField, loadReposBtn);
        tokenBox.getChildren().addAll(tokenLabel, tokenInputBox);
        
        authSection.getChildren().addAll(authLabel, githubLoginBtn, authInfoBox, tokenBox);
        
        // Section 2: Repository Selection
        VBox repoSection = new VBox(10);
        Label repoLabel = new Label("Depo Seçimi");
        repoLabel.getStyleClass().add("settings-section-label");
        
        repoComboBox = new ComboBox<>();
        repoComboBox.setPromptText("Repository Seçin");
        repoComboBox.setMaxWidth(Double.MAX_VALUE);
        repoComboBox.getStyleClass().add("settings-combo-box");
        
        Label repoHint = new Label("Notlarınızın senkronize edileceği GitHub deposunu seçin.");
        repoHint.getStyleClass().add("settings-hint-label");
        
        repoSection.getChildren().addAll(repoLabel, repoComboBox, repoHint);
        
        // Status & Info
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #7c7f88; -fx-font-size: 12px;");
        
        connectedUserLabel = new Label("Bağlı değil");
        connectedUserLabel.setStyle("-fx-text-fill: #7c7f88; -fx-font-size: 12px;");

        view.getChildren().addAll(header, authSection, new Separator(), repoSection, statusLabel, connectedUserLabel);
        
        // Initialize Data if available
        if (currentConfig != null && currentConfig.getToken() != null && !currentConfig.getToken().isEmpty()) {
            tokenField.setText(currentConfig.getToken());
            connectedUserLabel.setText("Giriş yapılıyor...");
            loadRepos(); // Reuse loadRepos logic
        }
        
        return view;
    }

    private HBox createFooter() {
        HBox footer = new HBox(10);
        footer.getStyleClass().add("settings-footer");
        
        Button btnCancel = new Button("İptal");
        btnCancel.getStyleClass().add("dialog-button-cancel");
        btnCancel.setOnAction(e -> close());
        
        Button btnSave = new Button("Tamam");
        btnSave.getStyleClass().add("dialog-button-primary");
        btnSave.setOnAction(e -> saveAndClose());
        
        footer.getChildren().addAll(btnCancel, btnSave);
        return footer;
    }

    private void saveAndClose() {
        if (repoComboBox.getValue() != null && currentUser != null) {
            result = new AppConfig(
                repoComboBox.getValue().getCloneUrl(),
                tokenField.getText(),
                currentUser.getLogin(),
                currentUser.getEmail() != null ? currentUser.getEmail() : currentUser.getLogin() + "@users.noreply.github.com"
            );
            close();
        } else {
            // If no changes or not logged in, just close if token is empty, else warn
            if (tokenField.getText().isEmpty()) {
                close();
            } else {
                statusLabel.setText("Lütfen bir repo seçin veya iptal edin.");
                statusLabel.setStyle("-fx-text-fill: #e06c75;");
            }
        }
    }

    public Optional<AppConfig> showAndWaitResult() {
        showAndWait();
        return Optional.ofNullable(result);
    }

    private void loadRepos() {
        String token = tokenField.getText();
        if (token.isEmpty()) {
            statusLabel.setText("Lütfen bir token girin.");
            return;
        }

        statusLabel.setText("Bağlanıyor...");
        statusLabel.setStyle("-fx-text-fill: #7c7f88;");
        logger.info("Loading repos with token...");
        
        noteService.fetchGitHubUser(token)
            .thenCompose(user -> {
                currentUser = user;
                return noteService.fetchUserRepos(token);
            })
            .thenAccept(repos -> Platform.runLater(() -> {
                repoComboBox.getItems().setAll(repos);
                statusLabel.setText("Giriş başarılı: " + currentUser.getName());
                statusLabel.setStyle("-fx-text-fill: #98c379;");
                
                connectedUserLabel.setText("Bağlı Kullanıcı: " + currentUser.getLogin());
                connectedUserLabel.setStyle("-fx-text-fill: #98c379; -fx-font-weight: bold;");
                
                // Select current repo if exists
                if (currentConfig != null && currentConfig.getRepoUrl() != null) {
                    for (GitHubRepo repo : repos) {
                        if (repo.getCloneUrl().equals(currentConfig.getRepoUrl())) {
                            repoComboBox.getSelectionModel().select(repo);
                            break;
                        }
                    }
                }
                
                logger.info("Repos loaded successfully.");
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    statusLabel.setText("Hata: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #e06c75;");
                });
                logger.log(Level.SEVERE, "Failed to load repos", e);
                return null;
            });
    }

    private void startGithubLogin(Button loginBtn, VBox infoBox, TextField codeField, Button copyBtn) {
        loginBtn.setDisable(true);
        statusLabel.setText("GitHub ile iletişim kuruluyor...");
        logger.info("Starting GitHub login process...");

        noteService.startGithubAuth()
            .thenAccept(resp -> Platform.runLater(() -> {
                infoBox.setVisible(true);
                infoBox.setManaged(true);
                codeField.setText(resp.user_code);
                
                copyBtn.setOnAction(ev -> {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(resp.user_code);
                    clipboard.setContent(content);
                    
                    try {
                        java.awt.Desktop.getDesktop().browse(java.net.URI.create(resp.verification_uri));
                    } catch (Exception ex) {
                        statusLabel.setText("Tarayıcı açılamadı: " + resp.verification_uri);
                        logger.log(Level.WARNING, "Failed to open browser", ex);
                    }
                });
                
                statusLabel.setText("Lütfen kodu onaylayın...");
                pollGithub(resp.device_code, resp.interval, infoBox);
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    statusLabel.setText("Hata: " + e.getMessage());
                    loginBtn.setDisable(false);
                });
                logger.log(Level.SEVERE, "Failed to start GitHub auth", e);
                return null;
            });
    }

    private void pollGithub(String deviceCode, int interval, VBox infoBox) {
        new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(interval * 1000L + 500); 
                    
                    NoteService.GithubTokenResponse resp = noteService.pollGithubAuth(deviceCode).join();
                    
                    if (resp.access_token != null) {
                        Platform.runLater(() -> {
                            tokenField.setText(resp.access_token);
                            statusLabel.setText("Giriş başarılı! Repolar yükleniyor...");
                            
                            // Auth kutusunu gizle
                            infoBox.setVisible(false);
                            infoBox.setManaged(false);
                            
                            loadRepos();
                        });
                        break;
                    }
                    
                    if ("authorization_pending".equals(resp.error)) {
                        continue;
                    }
                    
                    if ("slow_down".equals(resp.error)) {
                        Thread.sleep(5000);
                        continue;
                    }
                    
                    Platform.runLater(() -> statusLabel.setText("Giriş hatası: " + resp.error_description));
                    break;
                }
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Polling hatası: " + e.getMessage()));
            }
        }).start();
    }
}

