package com.lambdanotes;

import atlantafx.base.theme.PrimerDark;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.layout.StackPane;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Hyperlink;
import javafx.animation.FadeTransition;
import javafx.scene.layout.Region;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Callback;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.event.EventType;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.Level;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.scene.text.Font;

public class App extends Application {

    private static final Logger logger = Logger.getLogger(App.class.getName());

    private NoteService noteService;
    private TreeView<String> noteTreeView;
    private TextArea editorArea;
    private WebView previewArea;
    private TextField titleField;
    private Parser parser;
    private HtmlRenderer renderer;
    private SplitPane splitPane;
    private boolean isPreviewOpen = false;
    private Button btnPreview;
    private VBox editorPanel;
    private VBox previewPanel;
    private Label editorStatsLabel;
    private Label previewStatusLabel;
    private Label viewModeLabel; // New label for footer
    private PauseTransition autoSaveTimer;
    private BackendManager backendManager;
    
    private double currentEditorFontSize = 16;
    private static final double MAX_CONTENT_WIDTH = 900;
    
    // Layout Components
    private BorderPane mainLayout;
    private VBox mainContent;
    private StackPane emptyState;

    // Window State for Custom Maximize
    private double savedX, savedY, savedWidth, savedHeight;
    private boolean isMaximized = false;
    
    // Status Bar Components
    private Label statusLabel;
    private ProgressIndicator syncSpinner;
    private StackPane rootStack;
    
    // Version
    private static final String APP_VERSION = "0.0.2";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    // Explorer Components
    private TextField searchField;
    private List<String> allNotes = new ArrayList<>(); // Cache for filtering

    private enum ViewMode {
        READING, WRITING, SPLIT
    }
    
    private ViewMode currentMode = ViewMode.READING;
    private HBox modeSwitcher;

    @Override
    public void start(Stage stage) {
        setupLogging();
        
        // Load Fonts
        try {
            Font.loadFont(getClass().getResourceAsStream("fonts/Roboto-Regular.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("fonts/Roboto-Bold.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("fonts/Roboto-Italic.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("fonts/Roboto-BoldItalic.ttf"), 10);
        } catch (Exception e) {
            logger.warning("Could not load fonts: " + e.getMessage());
        }

        // Start Backend
        backendManager = new BackendManager();
        backendManager.startBackend();

        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        stage.initStyle(StageStyle.UNDECORATED); // Remove default OS window decorations
        
        noteService = new NoteService();
        parser = Parser.builder().build();
        renderer = HtmlRenderer.builder().build();

        // Auto-save timer (1 second delay)
        autoSaveTimer = new PauseTransition(Duration.seconds(1));
        autoSaveTimer.setOnFinished(e -> saveNote(true));

        // Root StackPane for Overlays (Notifications)
        rootStack = new StackPane();
        rootStack.getStyleClass().add("root-stack");

        // Main Layout
        mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("root-pane");
        
        // Custom Window Title Bar
        HBox titleBar = createTitleBar(stage);
        
        mainLayout.setTop(titleBar);

        // Sidebar (Note Tree)
        noteTreeView = new TreeView<>();
        noteTreeView.setShowRoot(false);
        noteTreeView.setCellFactory(new Callback<TreeView<String>, TreeCell<String>>() {
            @Override
            public TreeCell<String> call(TreeView<String> stringTreeView) {
                return new DraggableTreeCell();
            }
        });
        noteTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isLeaf()) {
                // Build full path from tree item
                String fullPath = buildPath(newVal);
                loadNote(fullPath);
            }
        });
        
        // Explorer Header (Label + Buttons)
        HBox explorerHeader = new HBox(5);
        explorerHeader.setAlignment(Pos.CENTER_LEFT);
        explorerHeader.setPadding(new Insets(10, 10, 5, 10));
        
        Label sidebarLabel = new Label("EXPLORER");
        sidebarLabel.getStyleClass().add("sidebar-header");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button btnNewFile = new Button("📄");
        btnNewFile.setTooltip(new Tooltip("Yeni Not"));
        btnNewFile.getStyleClass().add("sidebar-action-button");
        btnNewFile.setOnAction(e -> clearEditor());

        Button btnNewFolder = new Button("📁");
        btnNewFolder.setTooltip(new Tooltip("Yeni Klasör"));
        btnNewFolder.getStyleClass().add("sidebar-action-button");
        btnNewFolder.setOnAction(e -> createNewFolder());

        Button btnCollapse = new Button("-");
        btnCollapse.setTooltip(new Tooltip("Tümünü Daralt"));
        btnCollapse.getStyleClass().add("sidebar-action-button");
        btnCollapse.setOnAction(e -> collapseAll());

        Button btnExpand = new Button("+");
        btnExpand.setTooltip(new Tooltip("Tümünü Genişlet"));
        btnExpand.getStyleClass().add("sidebar-action-button");
        btnExpand.setOnAction(e -> expandAll());

        explorerHeader.getChildren().addAll(sidebarLabel, spacer, btnNewFile, btnNewFolder, btnCollapse, btnExpand);

        // Search Field
        searchField = new TextField();
        searchField.setPromptText("Ara...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterNotes(newVal));
        
        // Sidebar Top Container
        VBox sidebarTop = new VBox(0, explorerHeader, searchField);
        sidebarTop.setPadding(new Insets(0)); // Remove padding to let search field fill width

        VBox sidebar = new VBox(sidebarTop, noteTreeView);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(0));
        sidebar.setSpacing(0);
        VBox.setVgrow(noteTreeView, Priority.ALWAYS);
        mainLayout.setLeft(sidebar);

        // Main Content
        mainContent = new VBox();
        mainContent.getStyleClass().add("main-content");
        mainContent.setPadding(new Insets(20, 40, 20, 40));
        mainContent.setSpacing(20);

        titleField = new TextField();
        titleField.setPromptText("Not Başlığı");
        titleField.setPrefWidth(Double.MAX_VALUE);
        titleField.setMaxWidth(Double.MAX_VALUE);
        titleField.setAlignment(Pos.CENTER_LEFT);
        titleField.getStyleClass().add("title-field");

        // Header Pane (Title + Mode Switcher)
        AnchorPane headerPane = new AnchorPane();
        
        HBox modeSwitch = createModeSwitcher();
        
        // Title Field fills the header
        AnchorPane.setLeftAnchor(titleField, 0.0);
        AnchorPane.setRightAnchor(titleField, 0.0);
        AnchorPane.setTopAnchor(titleField, 0.0);
        AnchorPane.setBottomAnchor(titleField, 0.0);
        
        // Mode Switcher at Top Right
        AnchorPane.setRightAnchor(modeSwitch, 10.0);
        AnchorPane.setTopAnchor(modeSwitch, 10.0);

        headerPane.getChildren().addAll(titleField, modeSwitch);

        splitPane = new SplitPane();
        splitPane.getStyleClass().add("main-split-pane");
        
        editorArea = new TextArea();
        editorArea.setPromptText("Markdown yazmaya başla...");
        editorArea.getStyleClass().add("editor-area");
        editorArea.setWrapText(true); // Infinite canvas feel (wrapping)
        editorArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentMode == ViewMode.READING || currentMode == ViewMode.SPLIT) updatePreview(newVal);
            updateEditorStats(newVal);
            autoSaveTimer.playFromStart(); // Reset timer on change
        });
        
        // Editor Context Menu
        setupEditorContextMenu();

        // Layout Listeners for Centering
        editorArea.widthProperty().addListener((obs, oldVal, newVal) -> updateEditorStyle());
        titleField.widthProperty().addListener((obs, oldVal, newVal) -> updateTitleStyle());

        previewArea = new WebView();
        previewArea.setPageFill(Color.TRANSPARENT);
        previewArea.setContextMenuEnabled(false);

        editorPanel = createEditorPanel();
        previewPanel = createPreviewPanel();

        // Default: Only Editor
        splitPane.getItems().add(editorPanel);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        mainContent.getChildren().addAll(headerPane, splitPane);
        
        // Empty State
        emptyState = new StackPane();
        Label emptyLabel = new Label("LambdaNotes");
        emptyLabel.setStyle("-fx-text-fill: #3e4451; -fx-font-size: 48px; -fx-font-weight: bold;");
        emptyState.getChildren().add(emptyLabel);
        
        // Set initial center to Empty State
        mainLayout.setCenter(emptyState);

        // Status Bar
        HBox statusBar = createStatusBar();
        mainLayout.setBottom(statusBar);

        rootStack.getChildren().add(mainLayout);

        // Scene & Styling
        Scene scene = new Scene(rootStack, 1200, 800);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        
        stage.setTitle("LambdaNotes");
        
        // Set Application Icon
        try {
            stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("lambda_note.png")));
        } catch (Exception e) {
            System.err.println("Icon could not be loaded: " + e.getMessage());
        }

        stage.setScene(scene);
        
        // Resize Logic
        ResizeHelper.addResizeListener(stage, () -> isMaximized);
        
        stage.show();

        // Give backend a moment to start
        new PauseTransition(Duration.seconds(1)).setOnFinished(e -> {
            noteService.getConfig().thenAccept(config -> Platform.runLater(() -> {
                applySettings(config);
                if (config != null && config.getRepoUrl() != null && !config.getRepoUrl().isEmpty()) {
                    syncNotes(); // Auto-sync on startup
                } else {
                    refreshNoteList();
                }
            })).exceptionally(ex -> {
                Platform.runLater(() -> refreshNoteList());
                return null;
            });
        });
        
        // Default to Reading Mode
        setViewMode(ViewMode.READING);

        // Check for Updates
        checkForUpdates();
    }

    private void setupLogging() {
        try {
            String userHome = System.getProperty("user.home");
            Path logDir = Paths.get(userHome, ".lambdanotes");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            Path logFile = logDir.resolve("application.log");

            FileHandler fileHandler = new FileHandler(logFile.toString(), true);
            fileHandler.setFormatter(new SimpleFormatter());
            Logger rootLogger = Logger.getLogger("");
            rootLogger.addHandler(fileHandler);
            rootLogger.setLevel(Level.INFO);
            logger.info("Logging initialized. Log file: " + logFile);
        } catch (IOException e) {
            System.err.println("Failed to setup logger: " + e.getMessage());
        }
    }

    private void checkForUpdates() {
        UpdateChecker checker = new UpdateChecker();
        checker.checkForUpdates(APP_VERSION).thenAccept(updateInfo -> {
            if (updateInfo != null) {
                Platform.runLater(() -> showNotification(
                    "Güncelleme Mevcut", 
                    "Yeni sürüm (" + updateInfo.version + ") indirilebilir.", 
                    NotificationType.INFO, 
                    "İndir", 
                    () -> getHostServices().showDocument(updateInfo.url)
                ));
            }
        });
    }

    private void toggleMaximize(Stage stage) {
        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getVisualBounds();

        if (isMaximized) {
            stage.setX(savedX);
            stage.setY(savedY);
            stage.setWidth(savedWidth);
            stage.setHeight(savedHeight);
            isMaximized = false;
        } else {
            savedX = stage.getX();
            savedY = stage.getY();
            savedWidth = stage.getWidth();
            savedHeight = stage.getHeight();

            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
            isMaximized = true;
        }
    }

    private HBox createTitleBar(Stage stage) {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("window-title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(0, 10, 0, 10)); // Remove vertical padding
        titleBar.setSpacing(10);

        Label titleLabel = new Label("LambdaNotes");
        titleLabel.getStyleClass().add("window-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnSync = new Button("Sync");
        btnSync.setOnAction(e -> syncNotes());
        btnSync.getStyleClass().add("window-button");

        Button btnSettings = new Button("Settings");
        btnSettings.setOnAction(e -> openSettings());
        btnSettings.getStyleClass().add("window-button");

        Button btnMinimize = new Button("—");
        btnMinimize.getStyleClass().add("window-button");
        btnMinimize.setOnAction(e -> stage.setIconified(true));

        Button btnMaximize = new Button("☐");
        btnMaximize.getStyleClass().add("window-button");
        btnMaximize.setOnAction(e -> toggleMaximize(stage));

        Button btnClose = new Button("✕");
        btnClose.getStyleClass().add("window-button-close");
        btnClose.setOnAction(e -> stage.close());

        titleBar.getChildren().addAll(titleLabel, spacer, btnSync, btnSettings, btnMinimize, btnMaximize, btnClose);

        // Drag Window Logic
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        titleBar.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            if (!isMaximized) {
                stage.setX(event.getScreenX() - xOffset[0]);
                stage.setY(event.getScreenY() - yOffset[0]);
            }
        });
        
        // Double click to maximize
        titleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                toggleMaximize(stage);
            }
        });

        return titleBar;
    }

    private String buildPath(TreeItem<String> item) {
        StringBuilder path = new StringBuilder(item.getValue());
        TreeItem<String> parent = item.getParent();
        while (parent != null && parent.getParent() != null) { // Stop before root
            path.insert(0, parent.getValue() + "/");
            parent = parent.getParent();
        }
        return path.toString();
    }

    private void setViewMode(ViewMode mode) {
        currentMode = mode;
        splitPane.getItems().clear();
        
        switch (mode) {
            case READING:
                splitPane.getItems().add(previewPanel);
                updatePreview(editorArea.getText());
                if (viewModeLabel != null) viewModeLabel.setText("Okuma Modu");
                break;
            case WRITING:
                splitPane.getItems().add(editorPanel);
                if (viewModeLabel != null) viewModeLabel.setText("Yazma Modu");
                break;
            case SPLIT:
                splitPane.getItems().addAll(editorPanel, previewPanel);
                splitPane.setDividerPositions(0.5);
                updatePreview(editorArea.getText());
                if (viewModeLabel != null) viewModeLabel.setText("Split Modu");
                break;
        }
        updateModeSwitcherState();
        updatePreviewStatus();
    }

    private void updateModeSwitcherState() {
        if (modeSwitcher == null) return;
        
        for (javafx.scene.Node node : modeSwitcher.getChildren()) {
            if (node instanceof Button) {
                Button btn = (Button) node;
                ViewMode btnMode = (ViewMode) btn.getUserData();
                if (btnMode == currentMode) {
                    if (!btn.getStyleClass().contains("mode-button-active")) {
                        btn.getStyleClass().add("mode-button-active");
                    }
                } else {
                    btn.getStyleClass().remove("mode-button-active");
                }
            }
        }
    }

    private HBox createModeSwitcher() {
        modeSwitcher = new HBox(0);
        modeSwitcher.getStyleClass().add("mode-switcher");
        
        Button btnRead = new Button("Okuma");
        btnRead.setUserData(ViewMode.READING);
        btnRead.getStyleClass().add("mode-button");
        btnRead.getStyleClass().add("mode-button-left");
        btnRead.setOnAction(e -> setViewMode(ViewMode.READING));
        
        Button btnWrite = new Button("Yazma");
        btnWrite.setUserData(ViewMode.WRITING);
        btnWrite.getStyleClass().add("mode-button");
        btnWrite.getStyleClass().add("mode-button-center");
        btnWrite.setOnAction(e -> setViewMode(ViewMode.WRITING));
        
        Button btnSplit = new Button("Split");
        btnSplit.setUserData(ViewMode.SPLIT);
        btnSplit.getStyleClass().add("mode-button");
        btnSplit.getStyleClass().add("mode-button-right");
        btnSplit.setOnAction(e -> setViewMode(ViewMode.SPLIT));
        
        modeSwitcher.getChildren().addAll(btnRead, btnWrite, btnSplit);
        updateModeSwitcherState();
        
        return modeSwitcher;
    }

    private void setupEditorContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem bold = new MenuItem("Kalın (Bold)");
        bold.setOnAction(e -> insertFormatting("**", "**"));
        
        MenuItem italic = new MenuItem("İtalik (Italic)");
        italic.setOnAction(e -> insertFormatting("*", "*"));
        
        MenuItem h1 = new MenuItem("Başlık 1 (H1)");
        h1.setOnAction(e -> insertFormatting("# ", ""));
        
        MenuItem h2 = new MenuItem("Başlık 2 (H2)");
        h2.setOnAction(e -> insertFormatting("## ", ""));
        
        MenuItem list = new MenuItem("Liste");
        list.setOnAction(e -> insertFormatting("- ", ""));
        
        MenuItem checkList = new MenuItem("Kontrol Listesi");
        checkList.setOnAction(e -> insertFormatting("- [ ] ", ""));
        
        MenuItem codeBlock = new MenuItem("Kod Bloğu");
        codeBlock.setOnAction(e -> insertFormatting("```\n", "\n```"));
        
        MenuItem table = new MenuItem("Tablo Ekle");
        table.setOnAction(e -> insertTextAtCursor(
            "| Başlık 1 | Başlık 2 |\n" +
            "|----------|----------|\n" +
            "| Hücre 1  | Hücre 2  |\n"
        ));

        contextMenu.getItems().addAll(bold, italic, new SeparatorMenuItem(), h1, h2, new SeparatorMenuItem(), list, checkList, codeBlock, table);
        editorArea.setContextMenu(contextMenu);
    }

    private void insertFormatting(String prefix, String suffix) {
        String selected = editorArea.getSelectedText();
        if (selected == null || selected.isEmpty()) {
            insertTextAtCursor(prefix + suffix);
        } else {
            editorArea.replaceSelection(prefix + selected + suffix);
            // Force update preview if in split mode
            if (currentMode == ViewMode.SPLIT) {
                updatePreview(editorArea.getText());
            }
        }
    }

    private void insertTextAtCursor(String text) {
        int caret = editorArea.getCaretPosition();
        editorArea.insertText(caret, text);
        // Force update preview if in split mode
        if (currentMode == ViewMode.SPLIT) {
            updatePreview(editorArea.getText());
        }
    }

    private void collapseAll() {
        if (noteTreeView.getRoot() != null) {
            // Root'u kapatma, sadece altındakileri kapat
            for (TreeItem<String> child : noteTreeView.getRoot().getChildren()) {
                collapseRecursive(child);
            }
        }
    }

    private void collapseRecursive(TreeItem<String> item) {
        item.setExpanded(false);
        for (TreeItem<String> child : item.getChildren()) {
            collapseRecursive(child);
        }
    }

    private void expandAll() {
        if (noteTreeView.getRoot() != null) {
            expandRecursive(noteTreeView.getRoot());
        }
    }

    private void expandRecursive(TreeItem<String> item) {
        item.setExpanded(true);
        for (TreeItem<String> child : item.getChildren()) {
            expandRecursive(child);
        }
    }

    private void filterNotes(String query) {
        if (query == null || query.isEmpty()) {
            refreshNoteList();
            return;
        }
        
        String lowerQuery = query.toLowerCase();
        List<String> filtered = allNotes.stream()
                .filter(n -> n.toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
        
        buildTreeFromList(filtered);
    }

    private void buildTreeFromList(List<String> notes) {
        TreeItem<String> root = new TreeItem<>("root");
        root.setExpanded(true);

        for (String path : notes) {
            String[] parts = path.split("/");
            TreeItem<String> current = root;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                TreeItem<String> found = null;
                for (TreeItem<String> child : current.getChildren()) {
                    if (child.getValue().equals(part)) {
                        found = child;
                        break;
                    }
                }
                if (found == null) {
                    found = new TreeItem<>(part);
                    current.getChildren().add(found);
                }
                current = found;
                current.setExpanded(true); // Expand filtered results
            }
        }
        noteTreeView.setRoot(root);
    }

    private void createNewFolder() {
        showCustomInputDialog("Yeni Klasör", "Klasör Adı:", name -> {
            titleField.setText(name + "/yeni-not.md");
            editorArea.clear();
            editorArea.requestFocus();
        });
    }
    
    private void showCustomInputDialog(String title, String prompt, java.util.function.Consumer<String> onConfirm) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initStyle(StageStyle.UNDECORATED);
        
        VBox content = new VBox(15);
        content.getStyleClass().add("custom-dialog");
        content.setPadding(new Insets(20));
        content.setPrefWidth(400);
        
        Label header = new Label(title);
        header.getStyleClass().add("dialog-header");
        
        TextField input = new TextField();
        input.setPromptText(prompt);
        input.getStyleClass().add("dialog-input");
        
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button btnCancel = new Button("İptal");
        btnCancel.getStyleClass().add("dialog-button-cancel");
        btnCancel.setOnAction(e -> dialog.setResult(null));
        
        Button btnOk = new Button("Tamam");
        btnOk.getStyleClass().add("dialog-button-ok");
        btnOk.setOnAction(e -> dialog.setResult(input.getText()));
        
        buttons.getChildren().addAll(btnCancel, btnOk);
        content.getChildren().addAll(header, input, buttons);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        
        // Enable dragging
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        content.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        content.setOnMouseDragged(event -> {
            dialog.setX(event.getScreenX() - xOffset[0]);
            dialog.setY(event.getScreenY() - yOffset[0]);
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != null && !result.isEmpty()) {
                onConfirm.accept(result);
            }
        });
    }

    private void togglePreview() {
        isPreviewOpen = !isPreviewOpen;
        if (isPreviewOpen) {
            if (!splitPane.getItems().contains(previewPanel)) {
                splitPane.getItems().add(previewPanel);
            }
            splitPane.setDividerPositions(0.5);
            updatePreview(editorArea.getText());
            btnPreview.setStyle("-fx-background-color: #3e4451; -fx-text-fill: white;");
        } else {
            splitPane.getItems().remove(previewPanel);
            btnPreview.setStyle(""); // Reset to default style class
        }
        updatePreviewStatus();
    }

    private void styleButton(Button btn, String styleClass) {
        btn.getStyleClass().add(styleClass);
        // btn.setRippleColor(Color.valueOf("#ffffff33")); // Removed for AtlantaFX
    }


    private void openSettings() {
        // Mevcut config'i al (bunu bir yerde saklamamız lazım, şimdilik servisten çekelim)
        noteService.getConfig().thenAccept(config -> Platform.runLater(() -> {
            SettingsDialog dialog = new SettingsDialog(noteService, config);
            Optional<AppConfig> result = dialog.showAndWaitResult();

            result.ifPresent(newConfig -> {
                noteService.saveConfig(newConfig).thenRun(() -> Platform.runLater(() -> {
                    applySettings(newConfig);
                    showAlert("Başarılı", "Ayarlar kaydedildi ve Git yapılandırıldı.");
                })).exceptionally(e -> {
                    Platform.runLater(() -> showAlert("Hata", "Ayarlar kaydedilemedi: " + e.getMessage()));
                    return null;
                });
            });
        })).exceptionally(e -> {
            // Config çekilemezse boş aç
            Platform.runLater(() -> {
                SettingsDialog dialog = new SettingsDialog(noteService, null);
                Optional<AppConfig> result = dialog.showAndWaitResult();
                result.ifPresent(newConfig -> {
                     noteService.saveConfig(newConfig).thenRun(() -> Platform.runLater(() -> applySettings(newConfig)));
                });
            });
            return null;
        });
    }

    private void applySettings(AppConfig config) {
        if (config == null) return;
        if (editorArea != null) {
            currentEditorFontSize = config.getEditorFontSize();
            updateEditorStyle();
        }
    }

    private void updateEditorStyle() {
        if (editorArea == null) return;
        double width = editorArea.getWidth();
        double hPadding = 20;
        if (width > MAX_CONTENT_WIDTH) {
            hPadding = (width - MAX_CONTENT_WIDTH) / 2;
        }
        editorArea.setStyle("-fx-font-size: " + currentEditorFontSize + "px; -fx-padding: 20 " + hPadding + " 20 " + hPadding + ";");
    }

    private void updateTitleStyle() {
        if (titleField == null) return;
        double width = titleField.getWidth();
        double hPadding = 0;
        if (width > MAX_CONTENT_WIDTH) {
            hPadding = (width - MAX_CONTENT_WIDTH) / 2;
        }
        titleField.setStyle("-fx-padding: 10 " + hPadding + " 10 " + hPadding + ";");
    }

    private void updatePreview(String markdown) {
        String html = renderer.render(parser.parse(markdown));
        
        // Get font URL for WebView
        String fontUrl = getClass().getResource("fonts/Roboto-Regular.ttf").toExternalForm();
        
        String styledHtml = "<html><head>" +
                "<style>" +
                "@font-face { font-family: 'Roboto'; src: url('" + fontUrl + "'); }" +
                "body { font-family: 'Roboto', sans-serif; color: #abb2bf; background-color: transparent; padding: 40px; line-height: 1.6; max-width: 900px; margin: 0 auto; }" +
                "h1, h2, h3 { color: #61afef; border-bottom: 1px solid #3e4451; padding-bottom: 10px; margin-top: 20px; font-weight: 600; font-family: 'Roboto', sans-serif; }" +
                "h1 { font-size: 2.2em; } h2 { font-size: 1.8em; }" +
                "strong, b { color: #abb2bf; font-weight: bold; }" +
                "code { background-color: #2c313a; padding: 2px 6px; border-radius: 4px; font-family: 'JetBrains Mono', 'Consolas', monospace; color: #98c379; font-size: 0.9em; }" +
                "pre { background-color: #21252b; padding: 15px; border-radius: 8px; overflow-x: auto; border: 1px solid #181a1f; }" +
                "pre code { background-color: transparent; padding: 0; color: #abb2bf; }" +
                "blockquote { border-left: 4px solid #61afef; margin: 0; padding-left: 15px; color: #5c6370; font-style: italic; }" +
                "a { color: #61afef; text-decoration: none; }" +
                "a:hover { text-decoration: underline; }" +
                "table { border-collapse: collapse; width: 100%; margin: 15px 0; }" +
                "th, td { border: 1px solid #3e4451; padding: 8px; text-align: left; }" +
                "th { background-color: #21252b; color: #d19a66; }" +
                "img { max-width: 100%; border-radius: 5px; }" +
                "ul, ol { padding-left: 20px; }" +
                "li { margin-bottom: 5px; }" +
                "</style></head><body style='background-color: transparent;'>" + html + "</body></html>";
        previewArea.getEngine().loadContent(styledHtml);
        updatePreviewStatus();
    }

    private void loadNote(String filename) {
        noteService.getNoteDetail(filename).thenAccept(note -> Platform.runLater(() -> {
            mainLayout.setCenter(mainContent); // Switch to content view
            titleField.setText(note.getFilename());
            editorArea.setText(note.getContent());
            updateEditorStats(note.getContent());
            setViewMode(ViewMode.READING); // Default to Reading mode on load
        }));
    }

    private void saveNote(boolean silent) {
        String title = titleField.getText();
        if (title.isEmpty()) {
            if (!silent) showAlert("Hata", "Başlık boş olamaz.");
            return;
        }
        Note note = new Note(title, editorArea.getText());
        noteService.saveNote(note).thenRun(() -> Platform.runLater(() -> {
            refreshNoteList();
            if (!silent) showAlert("Başarılı", "Not kaydedildi.");
        })).exceptionally(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Kaydedilemedi!");
                if (!silent) showAlert("Hata", "Not kaydedilemedi: " + e.getMessage());
            });
            return null;
        });
    }
    
    private void saveNote() {
        saveNote(false);
    }

    private void deleteNote(TreeItem<String> item) {
        if (item == null || !item.isLeaf()) return;

        String filename = buildPath(item);
        noteService.deleteNote(filename).thenRun(() -> Platform.runLater(() -> {
            clearEditor();
            refreshNoteList();
        }));
    }
    
    private void deleteNote() {
        deleteNote(noteTreeView.getSelectionModel().getSelectedItem());
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Hazır");
        statusLabel.getStyleClass().add("status-label");

        syncSpinner = new ProgressIndicator();
        syncSpinner.setPrefSize(16, 16);
        syncSpinner.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        viewModeLabel = new Label("Okuma Modu");
        viewModeLabel.getStyleClass().add("status-label");

        previewStatusLabel = new Label("");
        previewStatusLabel.getStyleClass().add("status-label"); // Use status-label style

        editorStatsLabel = new Label("0 kelime  •  0 karakter");
        editorStatsLabel.getStyleClass().add("status-label");

        Label branchLabel = new Label("main*"); // Mock branch name
        branchLabel.getStyleClass().add("status-branch");

        statusBar.getChildren().addAll(syncSpinner, statusLabel, spacer, viewModeLabel, new Label("  |  "), previewStatusLabel, new Label("  |  "), editorStatsLabel, new Label("  |  "), branchLabel);
        return statusBar;
    }

    private enum NotificationType {
        INFO, SUCCESS, WARNING, ERROR
    }

    private void showNotification(String title, String message, NotificationType type, String actionText, Runnable action) {
        HBox notification = new HBox(0);
        notification.getStyleClass().add("notification-popup");
        notification.setMaxHeight(Region.USE_PREF_SIZE); // Prevent vertical stretching
        
        // Accent Bar
        Region accent = new Region();
        accent.getStyleClass().add("notification-accent");
        switch (type) {
            case INFO: accent.getStyleClass().add("notification-accent-info"); break;
            case SUCCESS: accent.getStyleClass().add("notification-accent-success"); break;
            case WARNING: accent.getStyleClass().add("notification-accent-warning"); break;
            case ERROR: accent.getStyleClass().add("notification-accent-error"); break;
        }
        
        // Content Container
        VBox contentBox = new VBox(5);
        contentBox.getStyleClass().add("notification-content");
        HBox.setHgrow(contentBox, Priority.ALWAYS);
        
        // Header
        HBox header = new HBox(10);
        header.getStyleClass().add("notification-header");
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("notification-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("notification-close-button");
        closeBtn.setOnAction(e -> rootStack.getChildren().remove(notification));
        
        header.getChildren().addAll(titleLabel, spacer, closeBtn);
        
        // Message
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("notification-message");
        messageLabel.setWrapText(true);
        
        contentBox.getChildren().addAll(header, messageLabel);
        
        // Action Link
        if (actionText != null && action != null) {
            Hyperlink actionLink = new Hyperlink(actionText);
            actionLink.getStyleClass().add("notification-action-link");
            actionLink.setOnAction(e -> {
                action.run();
                rootStack.getChildren().remove(notification);
            });
            contentBox.getChildren().add(actionLink);
        }
        
        notification.getChildren().addAll(accent, contentBox);

        // Position bottom-right
        StackPane.setAlignment(notification, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(notification, new Insets(0, 20, 50, 0)); // Above status bar

        rootStack.getChildren().add(notification);

        // Animation
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), notification);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        
        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), notification);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setDelay(Duration.seconds(3));
        fadeOut.setOnFinished(e -> rootStack.getChildren().remove(notification));

        fadeIn.play();
        // Stop fade out on hover
        notification.setOnMouseEntered(e -> fadeOut.stop());
        notification.setOnMouseExited(e -> fadeOut.playFromStart());
    }

    private void syncNotes() {
        statusLabel.setText("Senkronize ediliyor...");
        syncSpinner.setVisible(true);
        
        // Loading overlay göster
        VBox loadingOverlay = new VBox(10);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        
        ProgressIndicator pi = new ProgressIndicator();
        Label loadingLabel = new Label("Senkronize ediliyor, lütfen bekleyin...");
        loadingLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        loadingOverlay.getChildren().addAll(pi, loadingLabel);
        rootStack.getChildren().add(loadingOverlay);
        
        noteService.getConfig().thenAccept(config -> {
            String repoUrl = (config != null && config.getRepoUrl() != null && !config.getRepoUrl().isEmpty()) 
                             ? config.getRepoUrl() 
                             : "https://github.com";

            noteService.syncNotes().thenRun(() -> Platform.runLater(() -> {
                refreshNoteList();
                statusLabel.setText("Hazır");
                syncSpinner.setVisible(false);
                rootStack.getChildren().remove(loadingOverlay); // Overlay'i kaldır
                showNotification(
                    "Senkronizasyon Başarılı", 
                    "Notlar başarıyla senkronize edildi.", 
                    NotificationType.SUCCESS, 
                    "Repo'yu Aç", 
                    () -> getHostServices().showDocument(repoUrl)
                );
                logger.info("Sync completed successfully.");
            })).exceptionally(e -> {
                Platform.runLater(() -> {
                    statusLabel.setText("Hata");
                    syncSpinner.setVisible(false);
                    rootStack.getChildren().remove(loadingOverlay); // Overlay'i kaldır
                    showAlert("Hata", "Senkronizasyon hatası: " + e.getMessage());
                    logger.severe("Sync failed: " + e.getMessage());
                });
                return null;
            });
        }).exceptionally(e -> {
             Platform.runLater(() -> {
                 // Config fetch failed, try sync anyway
                 noteService.syncNotes().thenRun(() -> Platform.runLater(() -> {
                    refreshNoteList();
                    statusLabel.setText("Hazır");
                    syncSpinner.setVisible(false);
                    rootStack.getChildren().remove(loadingOverlay);
                    showNotification(
                        "Senkronizasyon Başarılı", 
                        "Notlar başarıyla senkronize edildi.", 
                        NotificationType.SUCCESS, 
                        "GitHub'ı Aç", 
                        () -> getHostServices().showDocument("https://github.com")
                    );
                    logger.info("Sync completed successfully (config fetch failed).");
                })).exceptionally(ex -> {
                    Platform.runLater(() -> {
                        statusLabel.setText("Hata");
                        syncSpinner.setVisible(false);
                        rootStack.getChildren().remove(loadingOverlay);
                        showAlert("Hata", "Senkronizasyon hatası: " + ex.getMessage());
                        logger.severe("Sync failed: " + ex.getMessage());
                    });
                    return null;
                });
             });
             return null;
        });
    }

    private void refreshNoteList() {
        noteService.getNotes().thenAccept(notes -> Platform.runLater(() -> {
            allNotes = notes; // Cache for search
            buildTreeFromList(notes);
        })).exceptionally(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Bağlantı Hatası!");
                // Optional: Show a placeholder in the tree view
                TreeItem<String> errorRoot = new TreeItem<>("Bağlantı Hatası");
                noteTreeView.setRoot(errorRoot);
            });
            return null;
        });
    }

    private void clearEditor() {
        mainLayout.setCenter(mainContent); // Switch to content view
        titleField.clear();
        editorArea.clear();
        noteTreeView.getSelectionModel().clearSelection();
        updateEditorStats("");
        setViewMode(ViewMode.WRITING); // Default to Writing mode for new note
    }

    private VBox createEditorPanel() {
        VBox container = new VBox();
        container.getStyleClass().add("editor-panel");

        StackPane editorBody = new StackPane(editorArea);
        editorBody.getStyleClass().add("panel-body");
        StackPane.setAlignment(editorArea, Pos.TOP_LEFT);

        VBox.setVgrow(editorBody, Priority.ALWAYS);
        container.getChildren().addAll(editorBody);
        return container;
    }

    private VBox createPreviewPanel() {
        VBox container = new VBox();
        container.getStyleClass().add("preview-panel");

        previewArea.getStyleClass().add("preview-area");
        StackPane previewBody = new StackPane(previewArea);
        previewBody.getStyleClass().add("panel-body");
        VBox.setVgrow(previewBody, Priority.ALWAYS);
        container.getChildren().addAll(previewBody);
        return container;
    }

    private void updateEditorStats(String text) {
        if (editorStatsLabel == null) return;
        String safeText = text == null ? "" : text;
        String trimmed = safeText.trim();
        int wordCount = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        int charCount = safeText.length();
        editorStatsLabel.setText(wordCount + " kelime  •  " + charCount + " karakter");
    }

    private void updatePreviewStatus() {
        if (previewStatusLabel == null) return;
        if (currentMode == ViewMode.READING || currentMode == ViewMode.SPLIT) {
            previewStatusLabel.setText("Canlı • " + LocalTime.now().format(TIME_FORMATTER));
        } else {
            previewStatusLabel.setText("Kapalı");
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Drag & Drop TreeCell Implementation
    private class DraggableTreeCell extends TreeCell<String> {
        public DraggableTreeCell() {
            setOnDragDetected(event -> {
                if (getItem() == null) return;
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(buildPath(getTreeItem()));
                db.setContent(content);
                event.consume();
            });

            setOnDragOver(event -> {
                if (event.getGestureSource() != this && event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });

            setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    String sourcePath = db.getString();
                    TreeItem<String> targetItem = getTreeItem();
                    
                    // Hedef bir klasör mü?
                    String targetFolderPath = "";
                    if (targetItem == null) {
                        // Root'a bırakıldı
                        targetFolderPath = "";
                    } else if (!targetItem.isLeaf()) {
                        // Klasöre bırakıldı
                        targetFolderPath = buildPath(targetItem);
                    } else {
                        // Dosyaya bırakıldı -> Dosyanın olduğu klasöre taşı
                        String path = buildPath(targetItem);
                        if (path.contains("/")) {
                            targetFolderPath = path.substring(0, path.lastIndexOf("/"));
                        }
                    }

                    String fileName = sourcePath.substring(sourcePath.lastIndexOf("/") + 1);
                    String newPath = targetFolderPath.isEmpty() ? fileName : targetFolderPath + "/" + fileName;

                    if (!sourcePath.equals(newPath)) {
                        noteService.moveNote(sourcePath, newPath).thenRun(() -> Platform.runLater(() -> refreshNoteList()));
                        success = true;
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
            } else {
                setText(item);
                // İkon ekleyebiliriz: Klasör veya Dosya
                if (getTreeItem().isLeaf()) {
                    setGraphic(new Label("📄")); 
                    
                    // Context Menu for Delete
                    ContextMenu contextMenu = new ContextMenu();
                    MenuItem deleteItem = new MenuItem("Sil");
                    deleteItem.setOnAction(e -> deleteNote(getTreeItem()));
                    contextMenu.getItems().add(deleteItem);
                    setContextMenu(contextMenu);
                } else {
                    setGraphic(new Label("📁"));
                    setContextMenu(null);
                }
            }
        }
    }

    // Simple Resize Helper Class
    public static class ResizeHelper {
        public static void addResizeListener(Stage stage, java.util.function.BooleanSupplier isMaximizedCheck) {
            ResizeListener listener = new ResizeListener(stage, isMaximizedCheck);
            stage.getScene().addEventHandler(javafx.scene.input.MouseEvent.MOUSE_MOVED, listener);
            stage.getScene().addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, listener);
            stage.getScene().addEventHandler(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, listener);
            stage.getScene().addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, listener);
            stage.getScene().addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED_TARGET, listener);
        }

        static class ResizeListener implements javafx.event.EventHandler<javafx.scene.input.MouseEvent> {
            private Stage stage;
            private java.util.function.BooleanSupplier isMaximizedCheck;
            private Cursor cursorEvent = Cursor.DEFAULT;
            private int border = 4;
            private double startX = 0;
            private double startY = 0;

            public ResizeListener(Stage stage, java.util.function.BooleanSupplier isMaximizedCheck) {
                this.stage = stage;
                this.isMaximizedCheck = isMaximizedCheck;
            }

            @Override
            public void handle(javafx.scene.input.MouseEvent mouseEvent) {
                if (isMaximizedCheck.getAsBoolean()) {
                    if (stage.getScene().getCursor() != Cursor.DEFAULT) {
                        stage.getScene().setCursor(Cursor.DEFAULT);
                    }
                    return;
                }

                javafx.event.EventType<? extends javafx.scene.input.MouseEvent> mouseEventType = mouseEvent.getEventType();
                Scene scene = stage.getScene();

                double mouseEventX = mouseEvent.getSceneX();
                double mouseEventY = mouseEvent.getSceneY();
                double sceneWidth = scene.getWidth();
                double sceneHeight = scene.getHeight();

                if (javafx.scene.input.MouseEvent.MOUSE_MOVED.equals(mouseEventType)) {
                    if (mouseEventX < border && mouseEventY < border) {
                        cursorEvent = Cursor.NW_RESIZE;
                    } else if (mouseEventX < border && mouseEventY > sceneHeight - border) {
                        cursorEvent = Cursor.SW_RESIZE;
                    } else if (mouseEventX > sceneWidth - border && mouseEventY < border) {
                        cursorEvent = Cursor.NE_RESIZE;
                    } else if (mouseEventX > sceneWidth - border && mouseEventY > sceneHeight - border) {
                        cursorEvent = Cursor.SE_RESIZE;
                    } else if (mouseEventX < border) {
                        cursorEvent = Cursor.W_RESIZE;
                    } else if (mouseEventX > sceneWidth - border) {
                        cursorEvent = Cursor.E_RESIZE;
                    } else if (mouseEventY < border) {
                        cursorEvent = Cursor.N_RESIZE;
                    } else if (mouseEventY > sceneHeight - border) {
                        cursorEvent = Cursor.S_RESIZE;
                    } else {
                        cursorEvent = Cursor.DEFAULT;
                    }
                    scene.setCursor(cursorEvent);
                } else if (javafx.scene.input.MouseEvent.MOUSE_EXITED.equals(mouseEventType) || javafx.scene.input.MouseEvent.MOUSE_EXITED_TARGET.equals(mouseEventType)) {
                    scene.setCursor(Cursor.DEFAULT);
                } else if (javafx.scene.input.MouseEvent.MOUSE_PRESSED.equals(mouseEventType)) {
                    startX = stage.getWidth() - mouseEventX;
                    startY = stage.getHeight() - mouseEventY;
                } else if (javafx.scene.input.MouseEvent.MOUSE_DRAGGED.equals(mouseEventType)) {
                    if (!Cursor.DEFAULT.equals(cursorEvent)) {
                        if (!Cursor.W_RESIZE.equals(cursorEvent) && !Cursor.E_RESIZE.equals(cursorEvent)) {
                            double minHeight = stage.getMinHeight() > (border * 2) ? stage.getMinHeight() : (border * 2);
                            if (Cursor.NW_RESIZE.equals(cursorEvent) || Cursor.N_RESIZE.equals(cursorEvent) || Cursor.NE_RESIZE.equals(cursorEvent)) {
                                if (stage.getHeight() > minHeight || mouseEventY < 0) {
                                    stage.setHeight(stage.getY() - mouseEvent.getScreenY() + stage.getHeight());
                                    stage.setY(mouseEvent.getScreenY());
                                }
                            } else {
                                if (stage.getHeight() > minHeight || mouseEventY + startY - stage.getHeight() > 0) {
                                    stage.setHeight(mouseEventY + startY);
                                }
                            }
                        }

                        if (!Cursor.N_RESIZE.equals(cursorEvent) && !Cursor.S_RESIZE.equals(cursorEvent)) {
                            double minWidth = stage.getMinWidth() > (border * 2) ? stage.getMinWidth() : (border * 2);
                            if (Cursor.NW_RESIZE.equals(cursorEvent) || Cursor.W_RESIZE.equals(cursorEvent) || Cursor.SW_RESIZE.equals(cursorEvent)) {
                                if (stage.getWidth() > minWidth || mouseEventX < 0) {
                                    stage.setWidth(stage.getX() - mouseEvent.getScreenX() + stage.getWidth());
                                    stage.setX(mouseEvent.getScreenX());
                                }
                            } else {
                                if (stage.getWidth() > minWidth || mouseEventX + startX - stage.getWidth() > 0) {
                                    stage.setWidth(mouseEventX + startX);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() throws Exception {
        if (backendManager != null) {
            backendManager.stopBackend();
        }
        super.stop();
    }
}
