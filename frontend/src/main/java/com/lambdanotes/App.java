package com.lambdanotes;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.pdf.converter.PdfConverterExtension;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.layout.StackPane;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Hyperlink;
import javafx.animation.FadeTransition;
import javafx.scene.layout.Region;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.scene.text.Font;
import javafx.scene.shape.SVGPath;
import javafx.scene.control.ScrollBar;
import javafx.geometry.Orientation;
import java.util.Set;
import javafx.scene.Node;
import javafx.stage.Modality;
import javafx.scene.layout.Pane;
import java.util.concurrent.atomic.AtomicBoolean;

public class App extends Application {

    private static final Logger logger = Logger.getLogger(App.class.getName());

    private NoteService noteService;
    private TreeView<String> noteTreeView;
    private TextArea editorArea;
    private TextArea lineNumbers;
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
    private boolean isSynced = true; // Track sync status
    private String currentTheme = "Dark"; // Track current theme
    
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
    private Label branchLabel;
    
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

    // Tab related fields
    private TabPane editorTabPane;
    private boolean showTabs = false;
    private java.util.Map<String, Tab> openTabs = new java.util.HashMap<>();
    private java.util.Map<Tab, VBox> tabEditorPanels = new java.util.HashMap<>();

    // Track title visibility in preview
    private boolean showTitleInPreview = true;

    // Header Pane (Mode Switcher only)
    private AnchorPane headerPane;

    private Stage primaryStage;
    private AppConfig currentConfig;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        setupLogging();
        
        // Load Fonts
        try {
            Font.loadFont(getClass().getResourceAsStream("fonts/JetBrainsMono-Regular.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("fonts/JetBrainsMono-Bold.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("fonts/JetBrainsMono-Italic.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("fonts/JetBrainsMono-BoldItalic.ttf"), 10);
        } catch (Exception e) {
            logger.warning("Could not load fonts: " + e.getMessage());
        }

        // Start Backend
        backendManager = new BackendManager();
        backendManager.startBackend();

        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        stage.initStyle(StageStyle.UNDECORATED); // Remove default OS window decorations
        
        noteService = new NoteService();
        parser = Parser.builder()
                .extensions(Arrays.asList(TablesExtension.create()))
                .build();
        renderer = HtmlRenderer.builder()
                .extensions(Arrays.asList(TablesExtension.create()))
                .softBreak("<br />")
                .build();

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
        // Removed selection listener to prevent opening on right click.
        // Opening is now handled in DraggableTreeCell.setOnMouseClicked

        mainLayout.setLeft(createSidebar());

        // Main Content
        mainContent = new VBox();
        mainContent.getStyleClass().add("main-content");
        mainContent.setPadding(new Insets(20, 40, 20, 40));
        mainContent.setSpacing(20);

        titleField = new TextField();
        titleField.setPromptText(LanguageManager.get("editor.title_placeholder"));
        titleField.setPrefWidth(Double.MAX_VALUE);
        titleField.setMaxWidth(Double.MAX_VALUE);
        titleField.setAlignment(Pos.CENTER_LEFT);
        titleField.getStyleClass().add("title-field");

        // Header Pane (Mode Switcher only)
        headerPane = new AnchorPane();
        headerPane.setMinHeight(40); // Ensure some height
        
        HBox modeSwitch = createModeSwitcher();
        
        // Mode Switcher at Top Right
        AnchorPane.setRightAnchor(modeSwitch, 10.0);
        AnchorPane.setTopAnchor(modeSwitch, 10.0);
        AnchorPane.setBottomAnchor(modeSwitch, 5.0);

        headerPane.getChildren().addAll(modeSwitch);

        splitPane = new SplitPane();
        splitPane.getStyleClass().add("main-split-pane");
        
        editorArea = new TextArea();
        editorArea.setPromptText(LanguageManager.get("editor.placeholder"));
        editorArea.getStyleClass().add("editor-area");
        editorArea.setWrapText(false); // Disable wrap for code editor feel and line number sync
        editorArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentMode == ViewMode.READING || currentMode == ViewMode.SPLIT) updatePreview(newVal);
            updateEditorStats(newVal);
            updateLineNumbers();
            autoSaveTimer.playFromStart(); // Reset timer on change
        });

        // Shift+Enter to insert new line
        editorArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && event.isShiftDown()) {
                int caret = editorArea.getCaretPosition();
                editorArea.insertText(caret, "\n");
                event.consume();
            }
        });
        
        // Editor Context Menu
        setupEditorContextMenu();

        // Layout Listeners for Centering
        // We will handle title styling inside updateEditorStyle now since they are together
        editorArea.widthProperty().addListener((obs, oldVal, newVal) -> updateEditorStyle());
        
        // Sync Scrollbars after layout
        Platform.runLater(() -> {
            Set<Node> nodes = editorArea.lookupAll(".scroll-bar");
            for (Node node : nodes) {
                if (node instanceof ScrollBar) {
                    ScrollBar sb = (ScrollBar) node;
                    if (sb.getOrientation() == Orientation.VERTICAL) {
                        sb.valueProperty().addListener((obs, oldVal, newVal) -> {
                            if (lineNumbers != null) {
                                lineNumbers.setScrollTop(editorArea.getScrollTop());
                            }
                        });
                    }
                }
            }
        });

        previewArea = new WebView();
        previewArea.setPageFill(Color.TRANSPARENT);
        previewArea.setContextMenuEnabled(false);

        editorPanel = createEditorPanel();
        previewPanel = createPreviewPanel();

        // Tab Pane Initialization
        editorTabPane = new TabPane();
        editorTabPane.getStyleClass().add("editor-tab-pane");
        editorTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // Default: Only Editor
        splitPane.getItems().add(editorPanel);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        VBox.setVgrow(editorTabPane, Priority.ALWAYS);

        // Initial check for tabs will happen in applySettings
        mainContent.getChildren().addAll(headerPane, splitPane);
        
        // Empty State
        emptyState = new StackPane();
        
        VBox emptyContent = new VBox(20);
        emptyContent.setAlignment(Pos.CENTER);
        emptyContent.setMaxWidth(600);
        
        Label emptyLabel = new Label(LanguageManager.get("app.title"));
        emptyLabel.setStyle("-fx-text-fill: #3e4451; -fx-font-size: 48px; -fx-font-weight: bold; -fx-opacity: 0.5;");
        
        VBox actionsBox = new VBox(10);
        actionsBox.setAlignment(Pos.CENTER_LEFT);
        actionsBox.setMaxWidth(300);
        actionsBox.setStyle("-fx-padding: 20px;");

        Label startLabel = new Label(LanguageManager.get("empty.start"));
        startLabel.setStyle("-fx-text-fill: #abb2bf; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 0 10 0;");
        
        Button actionNewNote = createStartAction(LanguageManager.get("empty.new_note"), LanguageManager.get("empty.new_note_desc"), "📄");
        actionNewNote.setOnAction(e -> clearEditor());
        
        Button actionNewFolder = createStartAction(LanguageManager.get("empty.new_folder"), LanguageManager.get("empty.new_folder_desc"), "📁");
        actionNewFolder.setOnAction(e -> createNewFolder());
        
        Button actionSync = createStartAction(LanguageManager.get("empty.sync"), LanguageManager.get("empty.sync_desc"), "🔄");
        actionSync.setOnAction(e -> syncNotes());
        
        Button actionSettings = createStartAction(LanguageManager.get("empty.settings"), LanguageManager.get("empty.settings_desc"), "⚙");
        actionSettings.setOnAction(e -> openSettings());

        actionsBox.getChildren().addAll(startLabel, actionNewNote, actionNewFolder, actionSync, actionSettings);
        
        emptyContent.getChildren().addAll(emptyLabel, actionsBox);
        emptyState.getChildren().add(emptyContent);
        
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
        
        stage.setTitle(LanguageManager.get("app.title"));
        
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

        // Default to Reading Mode
        setViewMode(ViewMode.READING);

        // Check for Updates
        checkForUpdates();

        // Wait for backend to be ready with retry logic
        waitForBackendAndStart(0);
    }

    private void waitForBackendAndStart(int attempt) {
        if (attempt > 10) { // Max 5 seconds (10 * 500ms)
            logger.severe("Backend failed to start after multiple attempts.");
            Platform.runLater(() -> {
                refreshNoteList(); // Try one last time or show error
                statusLabel.setText(LanguageManager.get("status.backend_error"));
            });
            return;
        }

        noteService.getConfig().thenAccept(config -> Platform.runLater(() -> {
            logger.info("Backend connected successfully.");
            this.currentConfig = config; // Cache config
            applySettings(config);
            
            // Always load local notes first to ensure UI is not empty
            refreshNoteList();
            updateGitStatus();
            
            if (config != null && config.getRepoUrl() != null && !config.getRepoUrl().isEmpty()) {
                syncNotes(true); // Auto-sync on startup (background mode)
            }
        })).exceptionally(ex -> {
            // If failed, wait and retry
            logger.warning("Backend not ready yet (attempt " + (attempt + 1) + "), retrying...");
            new PauseTransition(Duration.millis(500)).setOnFinished(e -> waitForBackendAndStart(attempt + 1));
            return null;
        });
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
                    LanguageManager.get("dialog.update_available"), 
                    java.text.MessageFormat.format(LanguageManager.get("dialog.update_desc"), updateInfo.version), 
                    NotificationType.INFO, 
                    LanguageManager.get("dialog.download"), 
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

        Label titleLabel = new Label(LanguageManager.get("app.title"));
        titleLabel.getStyleClass().add("window-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnSync = new Button(LanguageManager.get("titlebar.sync"));
        btnSync.setOnAction(e -> syncNotes());
        btnSync.getStyleClass().add("window-button");

        Button btnSettings = new Button(LanguageManager.get("titlebar.settings"));
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
        btnClose.setOnAction(e -> requestClose(stage));

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
        
        if (showTabs) {
            // Update active tab layout
            Tab currentTab = editorTabPane.getSelectionModel().getSelectedItem();
            if (currentTab != null) {
                updateTabLayout(currentTab);
            }
        } else {
            // Classic Mode
            splitPane.getItems().clear();
            switch (mode) {
                case READING:
                    splitPane.getItems().add(previewPanel);
                    updatePreview(editorArea.getText());
                    if (viewModeLabel != null) viewModeLabel.setText(LanguageManager.get("mode.reading"));
                    break;
                case WRITING:
                    splitPane.getItems().add(editorPanel);
                    if (viewModeLabel != null) viewModeLabel.setText(LanguageManager.get("mode.writing"));
                    break;
                case SPLIT:
                    splitPane.getItems().addAll(editorPanel, previewPanel);
                    splitPane.setDividerPositions(0.5);
                    updatePreview(editorArea.getText());
                    if (viewModeLabel != null) viewModeLabel.setText(LanguageManager.get("mode.split"));
                    break;
            }
        }
        
        updateModeSwitcherState();
        updatePreviewStatus();
        updateEditorStyle();
    }

    private void updateTabLayout(Tab tab) {
        if (tab == null) return;
        
        // Tab content is now VBox(AnchorPane header, SplitPane content)
        if (!(tab.getContent() instanceof VBox)) return;
        
        VBox tabContent = (VBox) tab.getContent();
        if (tabContent.getChildren().size() < 2) return;
        
        AnchorPane tabHeader = (AnchorPane) tabContent.getChildren().get(0);
        SplitPane tabSplitPane = (SplitPane) tabContent.getChildren().get(1);
        
        // Move Mode Switcher to this tab's header
        if (modeSwitcher != null) {
            // Remove from previous parent
            if (modeSwitcher.getParent() instanceof Pane) {
                ((Pane) modeSwitcher.getParent()).getChildren().remove(modeSwitcher);
            }
            
            // Add to this tab's header
            if (!tabHeader.getChildren().contains(modeSwitcher)) {
                tabHeader.getChildren().add(modeSwitcher);
                AnchorPane.setRightAnchor(modeSwitcher, 10.0);
                AnchorPane.setTopAnchor(modeSwitcher, 5.0); // Adjusted for tab header height
                AnchorPane.setBottomAnchor(modeSwitcher, 5.0);
            }
        }
        
        VBox tabEditorPanel = tabEditorPanels.get(tab);
        if (tabEditorPanel == null) return; 
        
        tabSplitPane.getItems().clear();
        
        // Remove previewPanel from any parent to avoid "duplicate children" error
        if (previewPanel.getParent() instanceof SplitPane) {
            ((SplitPane) previewPanel.getParent()).getItems().remove(previewPanel);
        }
        
        switch (currentMode) {
            case READING:
                tabSplitPane.getItems().add(previewPanel);
                if (editorTabPane.getSelectionModel().getSelectedItem() == tab) {
                     updatePreview(editorArea.getText());
                }
                if (viewModeLabel != null) viewModeLabel.setText(LanguageManager.get("mode.reading"));
                break;
            case WRITING:
                tabSplitPane.getItems().add(tabEditorPanel);
                if (viewModeLabel != null) viewModeLabel.setText(LanguageManager.get("mode.writing"));
                break;
            case SPLIT:
                tabSplitPane.getItems().addAll(tabEditorPanel, previewPanel);
                tabSplitPane.setDividerPositions(0.5);
                if (editorTabPane.getSelectionModel().getSelectedItem() == tab) {
                    updatePreview(editorArea.getText());
                }
                if (viewModeLabel != null) viewModeLabel.setText(LanguageManager.get("mode.split"));
                break;
        }
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
        
        Button btnRead = new Button(LanguageManager.get("mode.btn.read"));
        btnRead.setUserData(ViewMode.READING);
        btnRead.getStyleClass().add("mode-button");
        btnRead.getStyleClass().add("mode-button-left");
        btnRead.setOnAction(e -> setViewMode(ViewMode.READING));
        
        Button btnWrite = new Button(LanguageManager.get("mode.btn.write"));
        btnWrite.setUserData(ViewMode.WRITING);
        btnWrite.getStyleClass().add("mode-button");
        btnWrite.getStyleClass().add("mode-button-center");
        btnWrite.setOnAction(e -> setViewMode(ViewMode.WRITING));
        
        Button btnSplit = new Button(LanguageManager.get("mode.btn.split"));
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
        
        MenuItem bold = new MenuItem(LanguageManager.get("context.bold"));
        bold.setOnAction(e -> insertFormatting("**", "**"));
        
        MenuItem italic = new MenuItem(LanguageManager.get("context.italic"));
        italic.setOnAction(e -> insertFormatting("*", "*"));
        
        MenuItem h1 = new MenuItem(LanguageManager.get("context.h1"));
        h1.setOnAction(e -> insertFormatting("# ", ""));
        
        MenuItem h2 = new MenuItem(LanguageManager.get("context.h2"));
        h2.setOnAction(e -> insertFormatting("## ", ""));
        
        MenuItem list = new MenuItem(LanguageManager.get("context.list"));
        list.setOnAction(e -> insertFormatting("- ", ""));
        
        MenuItem checkList = new MenuItem(LanguageManager.get("context.checklist"));
        checkList.setOnAction(e -> insertFormatting("- [ ] ", ""));
        
        MenuItem codeBlock = new MenuItem(LanguageManager.get("context.codeblock"));
        codeBlock.setOnAction(e -> insertFormatting("```\n", "\n```"));
        
        MenuItem table = new MenuItem(LanguageManager.get("context.table"));
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
        showCustomInputDialog(LanguageManager.get("sidebar.new_folder"), LanguageManager.get("sidebar.new_folder") + ":", name -> {
            titleField.setText(name + "/yeni-not.md");
            editorArea.clear();
            editorArea.requestFocus();
        });
    }
    
    private void showCustomInputDialog(String title, String prompt, java.util.function.Consumer<String> onConfirm) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initOwner(mainLayout.getScene().getWindow());

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
        
        Button btnCancel = new Button(LanguageManager.get("dialog.cancel"));
        btnCancel.getStyleClass().add("dialog-button-cancel");
        btnCancel.setOnAction(e -> dialogStage.close());
        
        Button btnOk = new Button(LanguageManager.get("dialog.ok"));
        btnOk.getStyleClass().add("dialog-button-ok");
        btnOk.setOnAction(e -> {
            String result = input.getText();
            if (result != null && !result.isEmpty()) {
                onConfirm.accept(result);
            }
            dialogStage.close();
        });
        
        buttons.getChildren().addAll(btnCancel, btnOk);
        content.getChildren().addAll(header, input, buttons);
        
        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getThemeStylesheet());
        
        dialogStage.setScene(scene);
        
        // Center on owner
        dialogStage.setOnShown(e -> {
            Stage owner = (Stage) dialogStage.getOwner();
            dialogStage.setX(owner.getX() + (owner.getWidth() - dialogStage.getWidth()) / 2);
            dialogStage.setY(owner.getY() + (owner.getHeight() - dialogStage.getHeight()) / 2);
        });
        
        // Enable dragging
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        content.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        content.setOnMouseDragged(event -> {
            dialogStage.setX(event.getScreenX() - xOffset[0]);
            dialogStage.setY(event.getScreenY() - yOffset[0]);
        });

        dialogStage.showAndWait();
    }

    private String getThemeStylesheet() {
        if ("Light".equals(currentTheme)) {
            return getClass().getResource("light_theme.css").toExternalForm();
        } else if ("Tokyo Night".equals(currentTheme)) {
            return getClass().getResource("tokyo_night.css").toExternalForm();
        } else if ("Retro Night".equals(currentTheme)) {
            return getClass().getResource("retro_night.css").toExternalForm();
        } else {
            return getClass().getResource("styles.css").toExternalForm();
        }
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
        // Use current config if available, otherwise fetch
        if (currentConfig != null) {
            openSettingsDialog(currentConfig);
        } else {
            noteService.getConfig().thenAccept(config -> Platform.runLater(() -> {
                this.currentConfig = config;
                openSettingsDialog(config);
            })).exceptionally(e -> {
                Platform.runLater(() -> openSettingsDialog(null));
                return null;
            });
        }
    }

    private void openSettingsDialog(AppConfig config) {
        SettingsDialog dialog = new SettingsDialog(noteService, config, this::applyTheme);
        Optional<AppConfig> result = dialog.showAndWaitResult();

        result.ifPresent(newConfig -> {
            noteService.saveConfig(newConfig).thenRun(() -> Platform.runLater(() -> {
                this.currentConfig = newConfig; // Update local cache
                applySettings(newConfig);
                showAlert(LanguageManager.get("dialog.success"), LanguageManager.get("settings.saved"));
            })).exceptionally(e -> {
                Platform.runLater(() -> showAlert(LanguageManager.get("dialog.error"), LanguageManager.get("status.save_failed") + ": " + e.getMessage()));
                return null;
            });
        });
    }

    private void applySettings(AppConfig config) {
        if (config == null) return;
        
        if (config.getLanguage() != null) {
            LanguageManager.setLanguage(config.getLanguage());
            refreshUI();
        }
        
        if (config.getTheme() != null) {
            this.currentTheme = config.getTheme();
            applyTheme(config.getTheme());
        }
        
        if (editorArea != null) {
            currentEditorFontSize = config.getEditorFontSize();
            updateEditorStyle();
        }
        if (lineNumbers != null) {
            boolean show = config.isShowLineNumbers();
            lineNumbers.setVisible(show);
            lineNumbers.setManaged(show);
            if (show) {
                updateLineNumbers();
            }
            updateEditorStyle(); // Re-align editor and title
        }
        
        // Apply Tab Settings
        this.showTabs = config.isShowTabs();
        this.showTitleInPreview = config.isShowTitleInPreview();
        
        if (showTabs) {
            // Remove headerPane from mainContent if it exists (we will move modeSwitcher inside tabs)
            mainContent.getChildren().remove(headerPane); // Remove global header
            
            if (!mainContent.getChildren().contains(editorTabPane)) {
                // Remove splitPane from mainContent if it exists
                mainContent.getChildren().remove(splitPane);
                // Add editorTabPane if not present
                if (!mainContent.getChildren().contains(editorTabPane)) {
                    mainContent.getChildren().add(0, editorTabPane); // Add to top
                }
            }
            
            // Refresh current tab layout to include mode switcher
            Tab currentTab = editorTabPane.getSelectionModel().getSelectedItem();
            if (currentTab != null) {
                updateTabLayout(currentTab);
            }
        } else {
            // Classic Mode
            if (mainContent.getChildren().contains(editorTabPane)) {
                mainContent.getChildren().remove(editorTabPane);
            }
            
            // Restore global header
            if (!mainContent.getChildren().contains(headerPane)) {
                mainContent.getChildren().add(0, headerPane);
            }
            
            if (!mainContent.getChildren().contains(splitPane)) {
                mainContent.getChildren().add(splitPane);
            }
            
            // Ensure splitPane has editorPanel
            if (!splitPane.getItems().contains(editorPanel)) {
                splitPane.getItems().add(0, editorPanel);
            }
            
            // Ensure modeSwitcher is in headerPane
            if (!headerPane.getChildren().contains(modeSwitcher)) {
                headerPane.getChildren().add(modeSwitcher);
                AnchorPane.setRightAnchor(modeSwitcher, 10.0);
                AnchorPane.setTopAnchor(modeSwitcher, 10.0);
                AnchorPane.setBottomAnchor(modeSwitcher, 5.0);
            }
        }
    }

    private void updateEditorStyle() {
        if (editorArea == null) return;
        double width = editorArea.getWidth();
        
        // Calculate horizontal padding to center content
        double hPadding = (width - MAX_CONTENT_WIDTH) / 2;
        
        // Ensure minimum padding is enough to clear the line numbers (50px) + some gap (20px)
        if (hPadding < 70) {
            hPadding = 70;
        }
        
        // Apply to Editor
        // Note: We removed .content padding in CSS to ensure exact alignment
        editorArea.setStyle("-fx-font-size: " + currentEditorFontSize + "px; -fx-padding: 20 " + hPadding + " 20 " + hPadding + ";");
        
        // Apply to Title Field (if it exists)
        if (titleField != null) {
            // Title field might need a tiny adjustment depending on the font/control, 
            // but usually 0-padding on editor content makes them match closely.
            // If title is still to the left, we might need to add a few pixels here.
            // Let's add a small buffer (e.g. 5px) to both to be safe and consistent, 
            // or just rely on the padding.
            // Let's try exact match first.
            titleField.setStyle("-fx-padding: 10 " + hPadding + " 10 " + hPadding + ";");
        }
    }

    // updateTitleStyle is no longer needed as it's handled in updateEditorStyle
    private void updateTitleStyle() {
        // Deprecated
    }

    private void updatePreview(String markdown) {
        String html = renderer.render(parser.parse(markdown));
        
        // Get font URL for WebView
        String fontUrl = getClass().getResource("fonts/JetBrainsMono-Regular.ttf").toExternalForm();
        String fontBoldUrl = getClass().getResource("fonts/JetBrainsMono-Bold.ttf").toExternalForm();
        String fontItalicUrl = getClass().getResource("fonts/JetBrainsMono-Italic.ttf").toExternalForm();
        String fontBoldItalicUrl = getClass().getResource("fonts/JetBrainsMono-BoldItalic.ttf").toExternalForm();
        
        String title = titleField != null ? titleField.getText() : "";
        // Remove extension for display if present
        if (title.endsWith(".md")) {
            title = title.substring(0, title.length() - 3);
        }
        // If title contains path, take only filename
        if (title.contains("/")) {
            title = title.substring(title.lastIndexOf("/") + 1);
        }

        // Determine colors based on theme
        String textColor, bgColor, titleColor, codeBg, codeColor, borderColor, linkColor;
        
        if ("Light".equalsIgnoreCase(currentTheme)) {
            textColor = "#24292e";
            bgColor = "#ffffff";
            titleColor = "#24292e";
            codeBg = "#f6f8fa";
            codeColor = "#24292e";
            borderColor = "#e1e4e8";
            linkColor = "#0366d6";
        } else {
            // Dark / Tokyo Night / Retro Night
            // User requested darker/more visible text. 
            // #abb2bf is standard OneDark. Let's make it brighter: #e6e6e6
            textColor = "#e6e6e6"; 
            bgColor = "transparent"; // Transparent to show app background
            titleColor = "#e6e6e6";
            codeBg = "#2c313a";
            codeColor = "#98c379";
            borderColor = "#3e4451";
            linkColor = "#61afef";
        }

        // Check if title should be shown
        boolean showTitle = true;
        // We need to access the config to check showTitleInPreview
        // Since we don't have direct access to config object here easily without passing it around,
        // we can check the SettingsDialog static or store it in App.
        // But wait, we have noteService.getConfig() which is async.
        // Let's add a field `showTitleInPreview` to App.java and update it in applySettings.
        // For now, default to true.
        
        String titleHtml = "";
        // We need to add showTitleInPreview field to App.java to control this.
        // Assuming we will add it.
        if (this.showTitleInPreview) {
             titleHtml = "<div class='note-title'>" + title + "</div><hr class='title-separator'/>";
        }

        String styledHtml = "<html><head>" +
                "<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-dark.min.css\">" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js\"></script>" +
                "<script>hljs.highlightAll();</script>" +
                "<style>" +
                "@font-face { font-family: 'JetBrains Mono'; src: url('" + fontUrl + "'); }" +
                "@font-face { font-family: 'JetBrains Mono'; font-weight: bold; src: url('" + fontBoldUrl + "'); }" +
                "@font-face { font-family: 'JetBrains Mono'; font-style: italic; src: url('" + fontItalicUrl + "'); }" +
                "@font-face { font-family: 'JetBrains Mono'; font-weight: bold; font-style: italic; src: url('" + fontBoldItalicUrl + "'); }" +
                "body { font-family: 'JetBrains Mono', sans-serif; color: " + textColor + "; background-color: " + bgColor + "; padding: 20px 40px; line-height: 1.6; max-width: 900px; margin: 0 auto; }" +
                ".note-title { font-size: 1.8em; font-weight: bold; color: " + titleColor + "; margin-bottom: 5px; border-bottom: none; opacity: 0.9; }" +
                ".title-separator { border: 0; height: 1px; background-image: linear-gradient(to right, " + borderColor + ", rgba(0,0,0,0)); margin-bottom: 20px; }" +
                "h1, h2, h3 { color: " + linkColor + "; border-bottom: 1px solid " + borderColor + "; padding-bottom: 10px; margin-top: 20px; font-weight: 600; font-family: 'JetBrains Mono', sans-serif; }" +
                "h1 { font-size: 2.2em; } h2 { font-size: 1.8em; }" +
                "strong, b { color: " + textColor + "; font-weight: bold; }" +
                "code { font-family: 'JetBrains Mono', 'Consolas', monospace; font-size: 0.9em; }" +
                ":not(pre) > code { background-color: " + codeBg + "; padding: 2px 6px; border-radius: 4px; color: " + codeColor + "; }" +
                "pre { background-color: " + codeBg + "; padding: 15px; border-radius: 8px; overflow-x: auto; border: 1px solid " + borderColor + "; margin-top: 10px; }" +
                "pre code { background-color: transparent; padding: 0; font-family: 'JetBrains Mono', 'Consolas', monospace; }" +
                ".hljs { background: transparent !important; }" +
                "blockquote { border-left: 4px solid " + linkColor + "; margin: 0; padding-left: 15px; color: #5c6370; font-style: italic; }" +
                "a { color: " + linkColor + "; text-decoration: none; }" +
                "a:hover { text-decoration: underline; }" +
                "table { border-collapse: collapse; width: 100%; margin: 15px 0; }" +
                "th, td { border: 1px solid " + borderColor + "; padding: 8px; text-align: left; }" +
                "th { background-color: " + codeBg + "; color: " + textColor + "; }" +
                "img { max-width: 100%; border-radius: 5px; }" +
                "ul, ol { padding-left: 20px; }" +
                "li { margin-bottom: 5px; }" +
                "</style></head><body style='background-color: " + bgColor + ";'>" +
                titleHtml +
                html + "</body></html>";
        previewArea.getEngine().loadContent(styledHtml);
        updatePreviewStatus();
    }

    private void loadNote(String filename) {
        noteService.getNoteDetail(filename).thenAccept(note -> Platform.runLater(() -> {
            mainLayout.setCenter(mainContent); // Switch to content view
            
            if (showTabs) {
                // Tab Logic
                if (openTabs.containsKey(filename)) {
                    editorTabPane.getSelectionModel().select(openTabs.get(filename));
                } else {
                    Tab tab = new Tab(note.getFilename());
                    tab.setUserData(filename); // Store full path
                    
                    // Create new editor instance for this tab
                    TextArea tabEditor = new TextArea(note.getContent());
                    tabEditor.getStyleClass().add("editor-area");
                    tabEditor.setWrapText(false);
                    
                    // Create Editor Panel for this tab (similar to createEditorPanel but for specific editor)
                    VBox tabEditorPanel = createEditorPanelForTab(tabEditor, note.getFilename());
                    tabEditorPanels.put(tab, tabEditorPanel);
                    
                    // Sync settings to new editor
                    double width = editorArea.getWidth(); // Use main editor width as reference
                    double hPadding = (width - MAX_CONTENT_WIDTH) / 2;
                    if (hPadding < 70) hPadding = 70;
                    tabEditor.setStyle("-fx-font-size: " + currentEditorFontSize + "px; -fx-padding: 20 " + hPadding + " 20 " + hPadding + ";");

                    // Listeners
                    tabEditor.textProperty().addListener((obs, oldVal, newVal) -> {
                        if (!tab.getText().endsWith("*")) {
                            tab.setText(tab.getText() + "*");
                        }
                        // If this is the active tab, update preview and stats
                        if (editorTabPane.getSelectionModel().getSelectedItem() == tab) {
                             if (currentMode == ViewMode.READING || currentMode == ViewMode.SPLIT) updatePreview(newVal);
                             updateEditorStats(newVal);
                        }
                        autoSaveTimer.playFromStart();
                    });
                    
                    // Handle Tab Close
                    tab.setOnClosed(e -> {
                        openTabs.remove(filename);
                        tabEditorPanels.remove(tab);
                    });

                    // Create SplitPane for this tab
                    SplitPane tabSplitPane = new SplitPane();
                    tabSplitPane.getStyleClass().add("main-split-pane");
                    VBox.setVgrow(tabSplitPane, Priority.ALWAYS);
                    
                    // Create a container for the tab content: Header (ModeSwitcher) + SplitPane
                    VBox tabContent = new VBox();
                    tabContent.getStyleClass().add("tab-content");
                    
                    // Header container for ModeSwitcher
                    AnchorPane tabHeader = new AnchorPane();
                    tabHeader.setMinHeight(40);
                    tabHeader.setPrefHeight(40);
                    tabHeader.setMaxHeight(40);
                    tabHeader.getStyleClass().add("tab-header-pane");
                    
                    tabContent.getChildren().addAll(tabHeader, tabSplitPane);
                    
                    tab.setContent(tabContent);
                    
                    editorTabPane.getTabs().add(tab);
                    editorTabPane.getSelectionModel().select(tab);
                    openTabs.put(filename, tab);
                    
                    // Update global reference when tab changes
                    editorTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                        if (newTab != null) {
                            // Find the editor for this tab
                            VBox panel = tabEditorPanels.get(newTab);
                            
                            // Find editorArea in panel
                            if (panel != null && panel.getChildren().size() > 1) {
                                Node body = panel.getChildren().get(1); // StackPane body
                                if (body instanceof StackPane) {
                                    Node stack = ((StackPane) body).getChildren().get(0);
                                    if (stack instanceof StackPane) {
                                        Node editor = ((StackPane) stack).getChildren().get(0);
                                        if (editor instanceof TextArea) {
                                            editorArea = (TextArea) editor;
                                            titleField.setText(newTab.getUserData().toString());
                                            updateEditorStats(editorArea.getText());
                                            updateTabLayout(newTab); // Ensure layout is correct (moves preview & modeSwitcher)
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    });
                    
                    // Set initial reference
                    editorArea = tabEditor;
                    updateTabLayout(tab); // Initial layout
                }
            } else {
                // Classic Mode
                titleField.setText(note.getFilename());
                editorArea.setText(note.getContent());
            }
            
            updateLineNumbers();
            updateEditorStats(note.getContent());
            if (!showTabs) setViewMode(ViewMode.READING); // Default to Reading mode on load (Classic)
        }));
    }

    private VBox createEditorPanelForTab(TextArea tabEditor, String filename) {
        VBox container = new VBox();
        container.getStyleClass().add("editor-panel");

        TextArea tabLineNumbers = new TextArea("1");
        tabLineNumbers.setEditable(false);
        tabLineNumbers.getStyleClass().add("line-numbers");
        tabLineNumbers.setWrapText(false);
        tabLineNumbers.setPrefWidth(50);
        tabLineNumbers.setMinWidth(50);
        tabLineNumbers.setMaxWidth(50);
        tabLineNumbers.setStyle("-fx-overflow-x: hidden; -fx-overflow-y: hidden;");
        tabLineNumbers.setMouseTransparent(true);

        // Sync line numbers
        tabEditor.scrollTopProperty().addListener((obs, oldVal, newVal) -> tabLineNumbers.setScrollTop(newVal.doubleValue()));
        tabEditor.textProperty().addListener((obs, oldVal, newVal) -> {
             int lines = newVal.split("\n", -1).length;
             StringBuilder sb = new StringBuilder();
             for (int i = 1; i <= lines; i++) sb.append(i).append("\n");
             tabLineNumbers.setText(sb.toString());
        });
        // Initial line numbers
        int lines = tabEditor.getText().split("\n", -1).length;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) sb.append(i).append("\n");
        tabLineNumbers.setText(sb.toString());

        StackPane editorStack = new StackPane();
        editorStack.getChildren().addAll(tabEditor, tabLineNumbers);
        StackPane.setAlignment(tabLineNumbers, Pos.TOP_LEFT);
        
        StackPane editorBody = new StackPane(editorStack);
        editorBody.getStyleClass().add("panel-body");
        VBox.setVgrow(editorBody, Priority.ALWAYS);
        
        TextField tabTitleField = new TextField(filename);
        tabTitleField.setPromptText("Not Başlığı");
        tabTitleField.setPrefWidth(Double.MAX_VALUE);
        tabTitleField.setMaxWidth(Double.MAX_VALUE);
        tabTitleField.setAlignment(Pos.CENTER_LEFT);
        tabTitleField.getStyleClass().add("title-field");
        
        // Sync with tab title
        tabTitleField.textProperty().addListener((obs, oldVal, newVal) -> {
             // Update tab user data (filename) - wait, filename includes path.
             // This is tricky. Title field usually edits content title or filename?
             // In this app, it seems to be filename.
             // Let's just let it be.
        });
        
        container.getChildren().addAll(tabTitleField, editorBody);
        return container;
    }

    private void saveNote(boolean silent) {
        String title = titleField.getText();
        // If tabs enabled, use current tab's data
        if (showTabs) {
            Tab currentTab = editorTabPane.getSelectionModel().getSelectedItem();
            if (currentTab != null) {
                title = (String) currentTab.getUserData();
                // Remove * from tab title
                String tabTitle = currentTab.getText();
                if (tabTitle.endsWith("*")) {
                    currentTab.setText(tabTitle.substring(0, tabTitle.length() - 1));
                }
            }
        }
        
        if (title.isEmpty()) {
            if (!silent) showAlert(LanguageManager.get("dialog.error"), LanguageManager.get("dialog.save_title_empty"));
            return;
        }
        Note note = new Note(title, editorArea.getText());
        noteService.saveNote(note).thenRun(() -> Platform.runLater(() -> {
            refreshNoteList();
            isSynced = false; // Mark as unsaved/unsynced
            statusLabel.setText(LanguageManager.get("status.saved_unsynced"));
            if (!silent) showAlert(LanguageManager.get("dialog.success"), LanguageManager.get("dialog.note_saved"));
        })).exceptionally(e -> {
            Platform.runLater(() -> {
                statusLabel.setText(LanguageManager.get("status.save_failed"));
                if (!silent) showAlert(LanguageManager.get("dialog.error"), LanguageManager.get("status.save_failed") + ": " + e.getMessage());
            });
            return null;
        });
    }
    
    private void saveNote() {
        saveNote(false);
    }

    private void deleteNote(TreeItem<String> item) {
        if (item == null) return;

        String path = buildPath(item);
        
        if (!item.isLeaf()) {
            if (!showCustomConfirmationDialog(LanguageManager.get("dialog.folder_delete_title"), LanguageManager.get("dialog.folder_delete_header"), 
                java.text.MessageFormat.format(LanguageManager.get("dialog.folder_delete_content"), path))) {
                return;
            }
        }

        noteService.deleteNote(path).thenRun(() -> Platform.runLater(() -> {
            refreshNoteList();
            String currentTitle = titleField.getText();
            if (currentTitle != null && (currentTitle.equals(path) || currentTitle.startsWith(path + "/"))) {
                clearEditor();
            }
        }));
    }

    private boolean showCustomConfirmationDialog(String title, String header, String contentText) {
        AtomicBoolean confirmed = new AtomicBoolean(false);
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initOwner(mainLayout.getScene().getWindow());

        VBox content = new VBox(20);
        content.getStyleClass().add("custom-dialog");
        content.setPadding(new Insets(20));
        content.setPrefWidth(450);
        
        Label headerLabel = new Label(title);
        headerLabel.getStyleClass().add("dialog-header");
        
        Label messageLabel = new Label(header + "\n\n" + contentText);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: #abb2bf; -fx-font-size: 14px;");
        
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button btnCancel = new Button(LanguageManager.get("dialog.cancel"));
        btnCancel.getStyleClass().add("dialog-button-cancel");
        btnCancel.setOnAction(e -> dialogStage.close());
        
        Button btnOk = new Button(LanguageManager.get("dialog.yes_delete"));
        btnOk.getStyleClass().add("dialog-button-secondary"); // Red style
        btnOk.setStyle("-fx-background-color: #e06c75; -fx-text-fill: white; -fx-border-color: #e06c75;");
        btnOk.setOnAction(e -> {
            confirmed.set(true);
            dialogStage.close();
        });
        
        buttons.getChildren().addAll(btnCancel, btnOk);
        content.getChildren().addAll(headerLabel, messageLabel, buttons);
        
        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getThemeStylesheet());
        
        dialogStage.setScene(scene);
        
        // Center on owner
        dialogStage.setOnShown(e -> {
            Stage owner = (Stage) dialogStage.getOwner();
            dialogStage.setX(owner.getX() + (owner.getWidth() - dialogStage.getWidth()) / 2);
            dialogStage.setY(owner.getY() + (owner.getHeight() - dialogStage.getHeight()) / 2);
        });
        
        // Enable dragging
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        content.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        content.setOnMouseDragged(event -> {
            dialogStage.setX(event.getScreenX() - xOffset[0]);
            dialogStage.setY(event.getScreenY() - yOffset[0]);
        });
        
        dialogStage.showAndWait();
        return confirmed.get();
    }
    
    private void deleteNote() {
        deleteNote(noteTreeView.getSelectionModel().getSelectedItem());
    }

    private void exportToPdf(TreeItem<String> item) {
        if (item == null || !item.isLeaf()) return;
        String filename = buildPath(item);
        
        noteService.getNoteDetail(filename).thenAccept(note -> Platform.runLater(() -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(LanguageManager.get("dialog.pdf_title"));
            fileChooser.setInitialFileName(note.getFilename().replace(".md", ".pdf"));
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(LanguageManager.get("dialog.pdf_file"), "*.pdf"));
            File file = fileChooser.showSaveDialog(mainLayout.getScene().getWindow());
            
            if (file != null) {
                try {
                    String html = renderer.render(parser.parse(note.getContent()));
                    // Add basic styling for PDF
                    String styledHtml = "<html><head><style>" +
                        "body { font-family: 'Arial', sans-serif; font-size: 12pt; line-height: 1.5; }" +
                        "h1, h2, h3 { color: #333; }" +
                        "code { background-color: #f0f0f0; padding: 2px 4px; }" +
                        "pre { background-color: #f0f0f0; padding: 10px; white-space: pre-wrap; }" +
                        "table { border-collapse: collapse; width: 100%; }" +
                        "th, td { border: 1px solid #ddd; padding: 8px; }" +
                        "th { background-color: #f2f2f2; }" +
                        "</style></head><body>" + html + "</body></html>";

                    PdfConverterExtension.exportToPdf(file.getAbsolutePath(), styledHtml, "", parser.getOptions());
                    showNotification(LanguageManager.get("dialog.success"), LanguageManager.get("dialog.pdf_success"), NotificationType.SUCCESS, LanguageManager.get("dialog.pdf_open"), () -> getHostServices().showDocument(file.getAbsolutePath()));
                } catch (Exception e) {
                    showAlert(LanguageManager.get("dialog.error"), java.text.MessageFormat.format(LanguageManager.get("dialog.pdf_error"), e.getMessage()));
                    logger.log(Level.SEVERE, "PDF export failed", e);
                }
            }
        }));
    }

    private void updateGitStatus() {
        noteService.getGitInfo().thenAccept(gitInfo -> Platform.runLater(() -> {
            if (gitInfo != null && gitInfo.branch != null) {
                branchLabel.setText(gitInfo.branch);
                if (gitInfo.remoteUrl != null && !gitInfo.remoteUrl.isEmpty()) {
                    String url = gitInfo.remoteUrl;
                    if (url.endsWith(".git")) {
                        url = url.substring(0, url.length() - 4);
                    }
                    final String finalUrl = url;
                    branchLabel.setOnMouseClicked(e -> getHostServices().showDocument(finalUrl));
                    branchLabel.setTooltip(new Tooltip(finalUrl));
                }
            }
        })).exceptionally(e -> {
            return null;
        });
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label(LanguageManager.get("status.ready"));
        statusLabel.getStyleClass().add("status-label");

        syncSpinner = new ProgressIndicator();
        syncSpinner.setPrefSize(16, 16);
        syncSpinner.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        viewModeLabel = new Label(LanguageManager.get("mode.reading"));
        viewModeLabel.getStyleClass().add("status-label");

        previewStatusLabel = new Label("");
        previewStatusLabel.getStyleClass().add("status-label"); // Use status-label style

        editorStatsLabel = new Label("0 " + LanguageManager.get("status.words") + "  •  0 " + LanguageManager.get("status.chars"));
        editorStatsLabel.getStyleClass().add("status-label");

        branchLabel = new Label("main"); 
        branchLabel.getStyleClass().add("status-branch");
        branchLabel.setCursor(Cursor.HAND);

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
        syncNotes(false);
    }

    private void syncNotes(boolean isBackground) {
        syncNotes(null, isBackground);
    }

    private void syncNotes(Runnable onSuccess) {
        syncNotes(onSuccess, false);
    }

    private void syncNotes(Runnable onSuccess, boolean isBackground) {
        statusLabel.setText(LanguageManager.get("status.syncing"));
        syncSpinner.setVisible(true);
        
        // Loading overlay göster (Sadece background değilse)
        VBox loadingOverlay = new VBox(10);
        if (!isBackground) {
            loadingOverlay.setAlignment(Pos.CENTER);
            loadingOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
            
            ProgressIndicator pi = new ProgressIndicator();
            Label loadingLabel = new Label(LanguageManager.get("status.syncing_wait"));
            loadingLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
            
            loadingOverlay.getChildren().addAll(pi, loadingLabel);
            rootStack.getChildren().add(loadingOverlay);
        }
        
        noteService.getConfig().thenAccept(config -> {
            String repoUrl = (config != null && config.getRepoUrl() != null && !config.getRepoUrl().isEmpty()) 
                             ? config.getRepoUrl() 
                             : "https://github.com";

            noteService.syncNotes().thenRun(() -> Platform.runLater(() -> {
                refreshNoteList();
                isSynced = true; // Mark as synced
                statusLabel.setText(LanguageManager.get("status.ready"));
                syncSpinner.setVisible(false);
                if (!isBackground) {
                    rootStack.getChildren().remove(loadingOverlay); // Overlay'i kaldır
                }
                
                updateGitStatus(); // Update branch info after sync

                if (onSuccess != null) {
                    onSuccess.run();
                } else if (!isBackground) {
                    showNotification(
                        LanguageManager.get("dialog.success"), 
                        LanguageManager.get("status.synced"), 
                        NotificationType.SUCCESS, 
                        LanguageManager.get("status.repo_open"), 
                        () -> getHostServices().showDocument(repoUrl)
                    );
                }
                logger.info("Sync completed successfully.");
            })).exceptionally(e -> {
                Platform.runLater(() -> {
                    refreshNoteList(); // Sync failed, but still load local notes
                    statusLabel.setText(LanguageManager.get("dialog.error"));
                    syncSpinner.setVisible(false);
                    if (!isBackground) {
                        rootStack.getChildren().remove(loadingOverlay); // Overlay'i kaldır
                        showAlert(LanguageManager.get("dialog.error"), java.text.MessageFormat.format(LanguageManager.get("status.sync_error"), e.getMessage()));
                    }
                    logger.severe("Sync failed: " + e.getMessage());
                });
                return null;
            });
        }).exceptionally(e -> {
             Platform.runLater(() -> {
                 // Config fetch failed, try sync anyway
                 noteService.syncNotes().thenRun(() -> Platform.runLater(() -> {
                    refreshNoteList();
                    isSynced = true; // Mark as synced
                    statusLabel.setText(LanguageManager.get("status.ready"));
                    syncSpinner.setVisible(false);
                    if (!isBackground) {
                        rootStack.getChildren().remove(loadingOverlay);
                    }
                    
                    updateGitStatus(); // Update branch info after sync

                    if (onSuccess != null) {
                        onSuccess.run();
                    } else if (!isBackground) {
                        showNotification(
                            LanguageManager.get("dialog.success"), 
                            LanguageManager.get("status.synced"), 
                            NotificationType.SUCCESS, 
                            LanguageManager.get("status.repo_open"), 
                            () -> getHostServices().showDocument("https://github.com")
                        );
                    }
                    logger.info("Sync completed successfully (config fetch failed).");
                })).exceptionally(ex -> {
                    Platform.runLater(() -> {
                        refreshNoteList(); // Sync failed, but still load local notes
                        statusLabel.setText(LanguageManager.get("dialog.error"));
                        syncSpinner.setVisible(false);
                        if (!isBackground) {
                            rootStack.getChildren().remove(loadingOverlay);
                            showAlert(LanguageManager.get("dialog.error"), java.text.MessageFormat.format(LanguageManager.get("status.sync_error"), ex.getMessage()));
                        }
                        logger.severe("Sync failed: " + ex.getMessage());
                    });
                    return null;
                });
             });
             return null;
        });
    }

    private void refreshNoteList() {
        logger.info("Refreshing note list...");
        noteService.getNotes().thenAccept(notes -> Platform.runLater(() -> {
            logger.info("Notes received: " + (notes != null ? notes.size() : "null"));
            allNotes = notes; // Cache for search
            buildTreeFromList(notes);
        })).exceptionally(e -> {
            logger.log(Level.SEVERE, "Failed to refresh note list", e);
            Platform.runLater(() -> {
                statusLabel.setText(LanguageManager.get("status.connection_error"));
                // Optional: Show a placeholder in the tree view
                TreeItem<String> errorRoot = new TreeItem<>(LanguageManager.get("status.connection_error"));
                noteTreeView.setRoot(errorRoot);
            });
            return null;
        });
    }

    private void clearEditor() {
        mainLayout.setCenter(mainContent); // Switch to content view
        titleField.clear();
        editorArea.clear();
        updateLineNumbers();
        noteTreeView.getSelectionModel().clearSelection();
        updateEditorStats("");
        setViewMode(ViewMode.WRITING); // Default to Writing mode for new note
    }

    private VBox createEditorPanel() {
        VBox container = new VBox();
        container.getStyleClass().add("editor-panel");

        lineNumbers = new TextArea("1");
        lineNumbers.setEditable(false);
        lineNumbers.getStyleClass().add("line-numbers");
        lineNumbers.setWrapText(false);
        lineNumbers.setPrefWidth(50);
        lineNumbers.setMinWidth(50);
        lineNumbers.setMaxWidth(50);
        // Hide scrollbar for line numbers
        lineNumbers.setStyle("-fx-overflow-x: hidden; -fx-overflow-y: hidden;");
        lineNumbers.setMouseTransparent(true); // Allow clicks to pass through to editor

        // Use StackPane to overlay line numbers (z-index style)
        StackPane editorStack = new StackPane();
        editorStack.getChildren().addAll(editorArea, lineNumbers);
        StackPane.setAlignment(lineNumbers, Pos.TOP_LEFT);
        
        StackPane editorBody = new StackPane(editorStack);
        editorBody.getStyleClass().add("panel-body");
        VBox.setVgrow(editorBody, Priority.ALWAYS);
        
        // Add TitleField to the top of the editor panel
        // This ensures they share the same width context
        container.getChildren().addAll(titleField, editorBody);
        return container;
    }

    private void updateLineNumbers() {
        if (lineNumbers == null || editorArea == null) return;
        String text = editorArea.getText();
        if (text == null) text = "";
        int lines = text.split("\n", -1).length;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            sb.append(i).append("\n");
        }
        lineNumbers.setText(sb.toString());
        lineNumbers.setScrollTop(editorArea.getScrollTop());
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
            previewStatusLabel.setText(LanguageManager.get("status.live") + " • " + LocalTime.now().format(TIME_FORMATTER));
        } else {
            previewStatusLabel.setText(LanguageManager.get("status.off"));
        }
    }

    private void showAlert(String title, String contentText) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);
        dialogStage.initOwner(mainLayout.getScene().getWindow());

        VBox content = new VBox(20);
        content.getStyleClass().add("custom-dialog");
        content.setPadding(new Insets(20));
        content.setPrefWidth(400);
        
        Label headerLabel = new Label(title);
        headerLabel.getStyleClass().add("dialog-header");
        
        Label messageLabel = new Label(contentText);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: #abb2bf; -fx-font-size: 14px;");
        
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button btnOk = new Button(LanguageManager.get("dialog.ok"));
        btnOk.getStyleClass().add("dialog-button-ok");
        btnOk.setOnAction(e -> dialogStage.close());
        
        buttons.getChildren().addAll(btnOk);
        content.getChildren().addAll(headerLabel, messageLabel, buttons);
        
        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getThemeStylesheet());
        
        dialogStage.setScene(scene);
        
        // Center on owner
        dialogStage.setOnShown(e -> {
            Stage owner = (Stage) dialogStage.getOwner();
            dialogStage.setX(owner.getX() + (owner.getWidth() - dialogStage.getWidth()) / 2);
            dialogStage.setY(owner.getY() + (owner.getHeight() - dialogStage.getHeight()) / 2);
        });
        
        // Enable dragging
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        content.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        content.setOnMouseDragged(event -> {
            dialogStage.setX(event.getScreenX() - xOffset[0]);
            dialogStage.setY(event.getScreenY() - yOffset[0]);
        });

        dialogStage.showAndWait();
    }

    private Button createStartAction(String title, String description, String icon) {
        Button btn = new Button();
        btn.getStyleClass().add("start-action-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        
        VBox content = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #61afef; -fx-font-size: 14px; -fx-font-weight: bold;");
        
        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-text-fill: #5c6370; -fx-font-size: 12px;");
        
        content.getChildren().addAll(titleLabel, descLabel);
        
        HBox layout = new HBox(15);
        layout.setAlignment(Pos.CENTER_LEFT);
        
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: #abb2bf;");
        
        layout.getChildren().addAll(iconLabel, content);
        
        btn.setGraphic(layout);
        
        // Hover effect
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 8 15;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #2c313a; -fx-cursor: hand; -fx-padding: 8 15; -fx-background-radius: 5;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 8 15;"));
        
        
        return btn;
    }

    private Node createIcon(String path, String color) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.setFill(Color.web(color));
        // Scale down if needed, but usually path should be sized correctly or scaled
        svg.setScaleX(0.8);
        svg.setScaleY(0.8);
        return svg;
    }

    private static final String FOLDER_ICON = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
    private static final String FILE_ICON = "M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z";

    // Drag & Drop TreeCell Implementation
    private class DraggableTreeCell extends TreeCell<String> {
        public DraggableTreeCell() {
            setOnMouseClicked(event -> {
                if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY && event.getClickCount() == 1) {
                    TreeItem<String> item = getTreeItem();
                    if (item != null && item.isLeaf()) {
                        String fullPath = buildPath(item);
                        loadNote(fullPath);
                    }
                }
            });

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
                // Show only filename, not full path
                String displayName = item;
                if (item.contains("/")) {
                    displayName = item.substring(item.lastIndexOf("/") + 1);
                }
                setText(displayName);
                
                // İkon ekleyebiliriz: Klasör veya Dosya
                if (getTreeItem().isLeaf()) {
                    setGraphic(createIcon(FILE_ICON, "#abb2bf")); 
                    
                    // Context Menu
                    ContextMenu contextMenu = new ContextMenu();
                    
                    MenuItem exportPdfItem = new MenuItem(LanguageManager.get("context.export_pdf"));
                    exportPdfItem.setOnAction(e -> exportToPdf(getTreeItem()));
                    
                    MenuItem revealItem = new MenuItem(LanguageManager.get("context.reveal"));
                    revealItem.setOnAction(e -> revealInExplorer(getTreeItem()));

                    MenuItem copyPermalinkItem = new MenuItem(LanguageManager.get("context.copy_permalink"));
                    copyPermalinkItem.setOnAction(e -> copyGitHubPermalink(getTreeItem()));
                    copyPermalinkItem.setVisible(false); // Default hidden
                    
                    MenuItem deleteItem = new MenuItem(LanguageManager.get("context.delete"));
                    deleteItem.setOnAction(e -> deleteNote(getTreeItem()));
                    
                    contextMenu.getItems().addAll(exportPdfItem, revealItem, copyPermalinkItem, new SeparatorMenuItem(), deleteItem);
                    
                    contextMenu.setOnShowing(e -> {
                        String path = buildPath(getTreeItem());
                        noteService.isNoteTracked(path).thenAccept(tracked -> {
                            Platform.runLater(() -> copyPermalinkItem.setVisible(tracked));
                        });
                    });

                    setContextMenu(contextMenu);
                } else {
                    setGraphic(createIcon(FOLDER_ICON, "#d19a66"));
                    
                    // Context Menu for Folders
                    ContextMenu contextMenu = new ContextMenu();
                    
                    MenuItem revealItem = new MenuItem(LanguageManager.get("context.reveal"));
                    revealItem.setOnAction(e -> revealInExplorer(getTreeItem()));

                    MenuItem copyPermalinkItem = new MenuItem(LanguageManager.get("context.copy_permalink"));
                    copyPermalinkItem.setOnAction(e -> copyGitHubPermalink(getTreeItem()));
                    copyPermalinkItem.setVisible(false); // Default hidden
                    
                    MenuItem deleteItem = new MenuItem(LanguageManager.get("context.delete"));
                    deleteItem.setOnAction(e -> deleteNote(getTreeItem()));
                    
                    contextMenu.getItems().addAll(revealItem, copyPermalinkItem, new SeparatorMenuItem(), deleteItem);
                    
                    contextMenu.setOnShowing(e -> {
                        String path = buildPath(getTreeItem());
                        noteService.isNoteTracked(path).thenAccept(tracked -> {
                            Platform.runLater(() -> copyPermalinkItem.setVisible(tracked));
                        });
                    });

                    setContextMenu(contextMenu);
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
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");
        launch(args);
    }

    @Override
    public void stop() throws Exception {
        if (backendManager != null) {
            backendManager.stopBackend();
        }
        super.stop();
    }

    private void requestClose(Stage stage) {
        if (isSynced) {
            stage.close();
        } else {
            showExitConfirmation(stage);
        }
    }

    private void showExitConfirmation(Stage ownerStage) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(ownerStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        VBox content = new VBox(20);
        content.getStyleClass().add("custom-dialog");
        content.setPadding(new Insets(20));
        content.setPrefWidth(480); // Wider for 3 buttons
        
        
        Label header = new Label(LanguageManager.get("dialog.exit_title"));
        header.getStyleClass().add("dialog-header");
        
        Label message = new Label(LanguageManager.get("dialog.exit_message"));
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #abb2bf; -fx-font-size: 14px;");
        
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        // Result holder: 0=cancel, 1=exit, 2=sync&exit
        final int[] result = {0};

        Button btnCancel = new Button(LanguageManager.get("dialog.cancel"));
        btnCancel.getStyleClass().add("dialog-button-cancel");
        btnCancel.setOnAction(e -> {
            result[0] = 0;
            dialogStage.close();
        });
        
        Button btnSyncAndExit = new Button(LanguageManager.get("dialog.sync_exit"));
        btnSyncAndExit.getStyleClass().add("dialog-button-primary");
        btnSyncAndExit.setOnAction(e -> {
            result[0] = 2;
            dialogStage.close();
        });

        Button btnExit = new Button(LanguageManager.get("dialog.exit_anyway"));
        btnExit.getStyleClass().add("dialog-button-secondary");
        btnExit.setStyle("-fx-background-color: #e06c75; -fx-text-fill: white; -fx-border-color: #e06c75;"); // Red for danger
        btnExit.setOnAction(e -> {
            result[0] = 1;
            dialogStage.close();
        });
        
        buttons.getChildren().addAll(btnCancel, btnSyncAndExit, btnExit);
        content.getChildren().addAll(header, message, buttons);
        
        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        
        // Apply current theme to dialog
        if (ownerStage.getScene() != null) {
             scene.getStylesheets().addAll(ownerStage.getScene().getStylesheets());
        }
        
        dialogStage.setScene(scene);
        
        // Center on owner
        dialogStage.setOnShown(e -> {
            dialogStage.setX(ownerStage.getX() + (ownerStage.getWidth() - dialogStage.getWidth()) / 2);
            dialogStage.setY(ownerStage.getY() + (ownerStage.getHeight() - dialogStage.getHeight()) / 2);
        });
        
        // Enable dragging
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        content.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        content.setOnMouseDragged(event -> {
            dialogStage.setX(event.getScreenX() - xOffset[0]);
            dialogStage.setY(event.getScreenY() - yOffset[0]);
        });

        dialogStage.showAndWait();

        if (result[0] == 1) { // Exit
            ownerStage.close();
        } else if (result[0] == 2) { // Sync and Exit
            syncNotes(() -> ownerStage.close());
        }
    }

    private void applyTheme(String theme) {
        if (mainLayout == null || mainLayout.getScene() == null) return;
        
        mainLayout.getScene().getStylesheets().clear();
        if ("Light".equals(theme)) {
            mainLayout.getScene().getStylesheets().add(getClass().getResource("light_theme.css").toExternalForm());
            Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
        } else if ("Tokyo Night".equals(theme)) {
            mainLayout.getScene().getStylesheets().add(getClass().getResource("tokyo_night.css").toExternalForm());
            Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
        } else if ("Retro Night".equals(theme)) {
            mainLayout.getScene().getStylesheets().add(getClass().getResource("retro_night.css").toExternalForm());
            Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
        } else {
            mainLayout.getScene().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
        }
    }

    private void revealInExplorer(TreeItem<String> item) {
        if (item == null) return;
        String relativePath = buildPath(item);
        
        // Primary location: ~/.lambdanotes/notes
        String userHome = System.getProperty("user.home");
        File notesDir = new File(userHome, ".lambdanotes/notes");
        
        File file = new File(notesDir, relativePath).getAbsoluteFile();
        
        // Fallback: Check relative "notes" directory (useful for dev environment)
        if (!file.exists()) {
             File devNotesDir = new File("notes");
             File devFile = new File(devNotesDir, relativePath).getAbsoluteFile();
             if (devFile.exists()) {
                 file = devFile;
             }
        }
        
        if (!file.exists()) {
            logger.warning("File not found for reveal: " + file.getAbsolutePath());
            Platform.runLater(() -> showNotification("Hata", "Dosya bulunamadı: " + relativePath, NotificationType.ERROR, null, null));
            return;
        }
        
        final File target = file;
        
        if (java.awt.Desktop.isDesktopSupported()) {
            new Thread(() -> {
                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        new ProcessBuilder("explorer.exe", "/select," + target.getAbsolutePath()).start();
                    } else if (os.contains("mac")) {
                        Runtime.getRuntime().exec(new String[]{"open", "-R", target.getAbsolutePath()});
                    } else {
                        File parent = target.getParentFile();
                        if (parent != null && parent.exists()) {
                            java.awt.Desktop.getDesktop().open(parent);
                        }
                    }
                } catch (IOException e) {
                    logger.warning("Failed to reveal file: " + e.getMessage());
                }
            }).start();
        }
    }

    private void copyGitHubPermalink(TreeItem<String> item) {
        if (item == null) return;
        String relativePath = buildPath(item);
        boolean isFile = item.isLeaf();
        
        showNotification("İşleniyor", "Permalink hazırlanıyor...", NotificationType.INFO, null, null);
        
        noteService.getGitInfo().thenAccept(gitInfo -> Platform.runLater(() -> {
            if (gitInfo == null || gitInfo.commit == null || gitInfo.remoteUrl == null) {
                showNotification("Hata", "Git bilgileri alınamadı.", NotificationType.ERROR, null, null);
                return;
            }
            
            String remote = gitInfo.remoteUrl.trim();
            if (remote.endsWith(".git")) {
                remote = remote.substring(0, remote.length() - 4);
            }
            
            if (remote.startsWith("git@")) {
                remote = remote.replace(":", "/").replace("git@", "https://");
            }
            
            // Encode path parts to handle spaces and special characters
            String[] pathParts = relativePath.split("/");
            StringBuilder encodedPath = new StringBuilder();
            for (int i = 0; i < pathParts.length; i++) {
                try {
                    encodedPath.append(java.net.URLEncoder.encode(pathParts[i], "UTF-8").replace("+", "%20"));
                } catch (Exception e) {
                    encodedPath.append(pathParts[i]);
                }
                if (i < pathParts.length - 1) {
                    encodedPath.append("/");
                }
            }

            String type = isFile ? "blob" : "tree";
            String permalink = String.format("%s/%s/%s/%s", remote, type, gitInfo.commit, encodedPath.toString());
            
            ClipboardContent content = new ClipboardContent();
            content.putString(permalink);
            Clipboard.getSystemClipboard().setContent(content);
            
            showNotification("Başarılı", "GitHub Permalink kopyalandı.", NotificationType.SUCCESS, null, null);
        })).exceptionally(e -> {
            Platform.runLater(() -> showNotification("Hata", "Permalink oluşturulamadı: " + e.getMessage(), NotificationType.ERROR, null, null));
            return null;
        });
    }

    private VBox createSidebar() {
        // Explorer Header (Label + Buttons)
        HBox explorerHeader = new HBox(5);
        explorerHeader.setAlignment(Pos.CENTER_LEFT);
        explorerHeader.setPadding(new Insets(10, 10, 5, 10));
        
        Label sidebarLabel = new Label(LanguageManager.get("sidebar.explorer"));
        sidebarLabel.getStyleClass().add("sidebar-header");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button btnNewFile = new Button("📄");
        btnNewFile.setTooltip(new Tooltip(LanguageManager.get("sidebar.new_note")));
        btnNewFile.getStyleClass().add("sidebar-action-button");
        btnNewFile.setOnAction(e -> clearEditor());

        Button btnNewFolder = new Button("📁");
        btnNewFolder.setTooltip(new Tooltip(LanguageManager.get("sidebar.new_folder")));
        btnNewFolder.getStyleClass().add("sidebar-action-button");
        btnNewFolder.setOnAction(e -> createNewFolder());

        Button btnCollapse = new Button("-");
        btnCollapse.setTooltip(new Tooltip(LanguageManager.get("sidebar.collapse_all")));
        btnCollapse.getStyleClass().add("sidebar-action-button");
        btnCollapse.setOnAction(e -> collapseAll());

        Button btnExpand = new Button("+");
        btnExpand.setTooltip(new Tooltip(LanguageManager.get("sidebar.expand_all")));
        btnExpand.getStyleClass().add("sidebar-action-button");
        btnExpand.setOnAction(e -> expandAll());

        explorerHeader.getChildren().addAll(sidebarLabel, spacer, btnNewFile, btnNewFolder, btnCollapse, btnExpand);

        // Search Field
        searchField = new TextField();
        searchField.setPromptText(LanguageManager.get("sidebar.search"));
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
        
        return sidebar;
    }

    private void updateEmptyState() {
        if (emptyState == null) return;
        emptyState.getChildren().clear();
        
        VBox emptyContent = new VBox(20);
        emptyContent.setAlignment(Pos.CENTER);
        emptyContent.setMaxWidth(600);
        
        Label emptyLabel = new Label(LanguageManager.get("app.title"));
        emptyLabel.setStyle("-fx-text-fill: #3e4451; -fx-font-size: 48px; -fx-font-weight: bold; -fx-opacity: 0.5;");
        
        VBox actionsBox = new VBox(10);
        actionsBox.setAlignment(Pos.CENTER_LEFT);
        actionsBox.setMaxWidth(300);
        actionsBox.setStyle("-fx-padding: 20px;");

        Label startLabel = new Label(LanguageManager.get("empty.start"));
        startLabel.setStyle("-fx-text-fill: #abb2bf; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 0 10 0;");
        
        Button actionNewNote = createStartAction(LanguageManager.get("empty.new_note"), LanguageManager.get("empty.new_note_desc"), "📄");
        actionNewNote.setOnAction(e -> clearEditor());
        
        Button actionNewFolder = createStartAction(LanguageManager.get("empty.new_folder"), LanguageManager.get("empty.new_folder_desc"), "📁");
        actionNewFolder.setOnAction(e -> createNewFolder());
        
        Button actionSync = createStartAction(LanguageManager.get("empty.sync"), LanguageManager.get("empty.sync_desc"), "🔄");
        actionSync.setOnAction(e -> syncNotes());
        
        Button actionSettings = createStartAction(LanguageManager.get("empty.settings"), LanguageManager.get("empty.settings_desc"), "⚙");
        actionSettings.setOnAction(e -> openSettings());

        actionsBox.getChildren().addAll(startLabel, actionNewNote, actionNewFolder, actionSync, actionSettings);
        
        emptyContent.getChildren().addAll(emptyLabel, actionsBox);
        emptyState.getChildren().add(emptyContent);
    }

    private void refreshUI() {
        if (primaryStage == null) return;
        
        primaryStage.setTitle(LanguageManager.get("app.title"));
        mainLayout.setTop(createTitleBar(primaryStage));
        mainLayout.setLeft(createSidebar());
        mainLayout.setBottom(createStatusBar());
        
        if (titleField != null) titleField.setPromptText(LanguageManager.get("editor.title_placeholder"));
        if (editorArea != null) editorArea.setPromptText(LanguageManager.get("editor.placeholder"));
        
        setupEditorContextMenu();
        updateEmptyState();
        
        // Refresh mode switcher if visible
        if (headerPane != null) {
            headerPane.getChildren().removeIf(node -> node instanceof HBox); // Remove old mode switcher
            HBox modeSwitch = createModeSwitcher();
            AnchorPane.setRightAnchor(modeSwitch, 10.0);
            AnchorPane.setTopAnchor(modeSwitch, 10.0);
            AnchorPane.setBottomAnchor(modeSwitch, 5.0);
            headerPane.getChildren().add(modeSwitch);
        }
        
        // If tabs are enabled, mode switcher is in tab header, which is harder to update dynamically without full rebuild.
        // But updateTabLayout might handle it if we call it?
        // For now, let's assume basic refresh is enough.

        // Restore status bar state
        updatePreviewStatus();
        if (editorArea != null) {
            updateEditorStats(editorArea.getText());
        }
        updateGitStatus();
    }
}
