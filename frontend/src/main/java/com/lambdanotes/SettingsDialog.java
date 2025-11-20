package com.lambdanotes;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Optional;

public class SettingsDialog extends Stage {

    private final NoteService noteService;
    private TextField tokenField;
    private ComboBox<GitHubRepo> repoComboBox;
    private Label statusLabel;
    private GitHubUser currentUser;
    private AppConfig result = null;

    public SettingsDialog(NoteService noteService) {
        this.noteService = noteService;
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UNDECORATED);

        VBox root = new VBox(20);
        root.getStyleClass().add("custom-dialog");
        root.setPadding(new Insets(20));
        root.setPrefWidth(500);

        // Header
        Label headerLabel = new Label("GitHub Ayarları");
        headerLabel.getStyleClass().add("dialog-header");
        
        Label subHeader = new Label("GitHub hesabınızı bağlayın ve notlarınızı yedekleyin.");
        subHeader.setStyle("-fx-text-fill: #abb2bf; -fx-font-size: 12px;");

        // Form
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(20);

        tokenField = new TextField();
        tokenField.setPromptText("Personal Access Token");
        tokenField.setPrefWidth(300);
        tokenField.getStyleClass().add("dialog-input");
        
        Button loadReposBtn = new Button("Giriş Yap");
        loadReposBtn.getStyleClass().add("dialog-button-ok");
        loadReposBtn.setOnAction(e -> loadRepos());

        repoComboBox = new ComboBox<>();
        repoComboBox.setPromptText("Repository Seçin");
        repoComboBox.setPrefWidth(300);
        repoComboBox.getStyleClass().add("dialog-input");

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #aaa;");

        grid.add(tokenField, 0, 0);
        grid.add(loadReposBtn, 1, 0);
        grid.add(repoComboBox, 0, 1, 2, 1);
        grid.add(statusLabel, 0, 2, 2, 1);

        // Buttons
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button btnCancel = new Button("İptal");
        btnCancel.getStyleClass().add("dialog-button-cancel");
        btnCancel.setOnAction(e -> close());
        
        Button btnSave = new Button("Kaydet ve Kur");
        btnSave.getStyleClass().add("dialog-button-ok");
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

        root.getChildren().addAll(headerLabel, subHeader, grid, buttons);

        Scene scene = new Scene(root);
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
        
        noteService.fetchGitHubUser(token)
            .thenCompose(user -> {
                currentUser = user;
                return noteService.fetchUserRepos(token);
            })
            .thenAccept(repos -> Platform.runLater(() -> {
                repoComboBox.getItems().setAll(repos);
                statusLabel.setText("Giriş başarılı: " + currentUser.getName() + " (" + repos.size() + " repo bulundu)");
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> statusLabel.setText("Hata: " + e.getMessage()));
                return null;
            });
    }
}

