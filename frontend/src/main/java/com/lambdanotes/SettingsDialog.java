package com.lambdanotes;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.scene.paint.Color;

public class SettingsDialog extends Stage {

    private static final Logger logger = Logger.getLogger(SettingsDialog.class.getName());
    private final NoteService noteService;
    private TextField tokenField;
    private ComboBox<GitHubRepo> repoComboBox;
    private Label statusLabel;
    private GitHubUser currentUser;
    private AppConfig result = null;

    public SettingsDialog(NoteService noteService, AppConfig currentConfig) {
        this.noteService = noteService;
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.TRANSPARENT); // UNDECORATED -> TRANSPARENT

        VBox root = new VBox(20);
        root.getStyleClass().add("custom-dialog");
        root.setPadding(new Insets(25));
        root.setPrefWidth(550);

        // Header
        Label headerLabel = new Label("GitHub Ayarları");
        headerLabel.getStyleClass().add("dialog-header");
        
        Label subHeader = new Label("GitHub hesabınızı bağlayın ve notlarınızı yedekleyin.");
        subHeader.setStyle("-fx-text-fill: #7c7f88; -fx-font-size: 13px;");

        // GitHub Login Button
        Button githubLoginBtn = new Button("GitHub ile Bağlan (Önerilen)");
        githubLoginBtn.setMaxWidth(Double.MAX_VALUE);
        githubLoginBtn.getStyleClass().add("dialog-button-primary");

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

        // Form
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(20);

        tokenField = new TextField();
        tokenField.setPromptText("Personal Access Token");
        tokenField.setPrefWidth(300);
        tokenField.getStyleClass().add("dialog-input");
        
        Button loadReposBtn = new Button("Giriş Yap");
        loadReposBtn.getStyleClass().add("dialog-button-secondary");
        loadReposBtn.setOnAction(e -> loadRepos());

        repoComboBox = new ComboBox<>();
        repoComboBox.setPromptText("Repository Seçin");
        repoComboBox.setPrefWidth(300);
        repoComboBox.getStyleClass().add("dialog-input");

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #7c7f88; -fx-font-size: 12px;");

        grid.add(tokenField, 0, 0);
        grid.add(loadReposBtn, 1, 0);
        grid.add(repoComboBox, 0, 1, 2, 1);
        grid.add(statusLabel, 0, 2, 2, 1);

        // Footer (Connected User Info)
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 0, 0, 0));
        footer.setStyle("-fx-border-color: #393b40; -fx-border-width: 1 0 0 0;");
        
        Label connectedUserLabel = new Label("Bağlı değil");
        connectedUserLabel.setStyle("-fx-text-fill: #7c7f88; -fx-font-size: 12px;");
        footer.getChildren().add(connectedUserLabel);

        // Buttons
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button btnCancel = new Button("İptal");
        btnCancel.getStyleClass().add("dialog-button-cancel");
        btnCancel.setOnAction(e -> close());
        
        Button btnSave = new Button("Kaydet ve Kur");
        btnSave.getStyleClass().add("dialog-button-primary");
        btnSave.setOnAction(e -> {
            if (repoComboBox.getValue() != null && currentUser != null) {
                result = new AppConfig(
                    repoComboBox.getValue().getCloneUrl(),
                    tokenField.getText(),
                    currentUser.getLogin(),
                    currentUser.getEmail() != null ? currentUser.getEmail() : currentUser.getLogin() + "@users.noreply.github.com"
                );
                close();
            } else {
                statusLabel.setText("Lütfen önce giriş yapın ve repo seçin.");
            }
        });
        
        buttons.getChildren().addAll(btnCancel, btnSave);

        root.getChildren().addAll(headerLabel, subHeader, githubLoginBtn, authInfoBox, grid, footer, buttons);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT); // Scene background transparent
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

        // Pre-fill if config exists
        if (currentConfig != null && currentConfig.getToken() != null && !currentConfig.getToken().isEmpty()) {
            tokenField.setText(currentConfig.getToken());
            connectedUserLabel.setText("Giriş yapılıyor...");
            
            // Auto-load user info
            noteService.fetchGitHubUser(currentConfig.getToken())
                .thenCompose(user -> {
                    currentUser = user;
                    return noteService.fetchUserRepos(currentConfig.getToken());
                })
                .thenAccept(repos -> Platform.runLater(() -> {
                    repoComboBox.getItems().setAll(repos);
                    
                    // Select current repo if exists
                    if (currentConfig.getRepoUrl() != null) {
                        for (GitHubRepo repo : repos) {
                            if (repo.getCloneUrl().equals(currentConfig.getRepoUrl())) {
                                repoComboBox.getSelectionModel().select(repo);
                                break;
                            }
                        }
                    }
                    
                    statusLabel.setText("Hazır");
                    connectedUserLabel.setText("Bağlı Kullanıcı: " + currentUser.getLogin() + " (" + currentUser.getName() + ")");
                    connectedUserLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 12px;");
                }))
                .exceptionally(e -> {
                    Platform.runLater(() -> {
                        statusLabel.setText("Token geçersiz veya bağlantı hatası.");
                        connectedUserLabel.setText("Bağlantı hatası");
                        connectedUserLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
                    });
                    return null;
                });
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
        logger.info("Loading repos with token...");
        
        noteService.fetchGitHubUser(token)
            .thenCompose(user -> {
                currentUser = user;
                return noteService.fetchUserRepos(token);
            })
            .thenAccept(repos -> Platform.runLater(() -> {
                repoComboBox.getItems().setAll(repos);
                statusLabel.setText("Giriş başarılı: " + currentUser.getName() + " (" + repos.size() + " repo bulundu)");
                logger.info("Repos loaded successfully.");
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> statusLabel.setText("Hata: " + e.getMessage()));
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
                
                // Pencere boyutunu içeriğe göre güncelle
                sizeToScene();
                
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
                            sizeToScene();
                            
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

