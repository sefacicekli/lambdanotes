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
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.event.EventType;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

public class App extends Application {

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
    private PauseTransition autoSaveTimer;
    private BackendManager backendManager;
    
    // Window State for Custom Maximize
    private double savedX, savedY, savedWidth, savedHeight;
    private boolean isMaximized = false;
    
    // Status Bar Components
    private Label statusLabel;
    private ProgressIndicator syncSpinner;
    private StackPane rootStack;
    
    // Explorer Components
    private TextField searchField;
    private List<String> allNotes = new ArrayList<>(); // Cache for filtering

    @Override
    public void start(Stage stage) {
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
        BorderPane mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("root-pane");
        
        // Custom Window Title Bar
        HBox titleBar = createTitleBar(stage);
        
        // Toolbar
        HBox toolBar = createToolBar();
        
        // Top Container (TitleBar + ToolBar)
        VBox topContainer = new VBox(titleBar, toolBar);
        mainLayout.setTop(topContainer);

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
        HBox explorerHeader = new HBox(10);
        explorerHeader.setAlignment(Pos.CENTER_LEFT);
        explorerHeader.setPadding(new Insets(10, 10, 5, 10));
        
        Label sidebarLabel = new Label("EXPLORER");
        sidebarLabel.getStyleClass().add("sidebar-header");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button btnCollapse = new Button("-");
        btnCollapse.setTooltip(new Tooltip("Tümünü Daralt"));
        btnCollapse.getStyleClass().add("icon-button");
        btnCollapse.setOnAction(e -> collapseAll());

        Button btnExpand = new Button("+");
        btnExpand.setTooltip(new Tooltip("Tümünü Genişlet"));
        btnExpand.getStyleClass().add("icon-button");
        btnExpand.setOnAction(e -> expandAll());

        explorerHeader.getChildren().addAll(sidebarLabel, spacer, btnCollapse, btnExpand);

        // Search Field
        searchField = new TextField();
        searchField.setPromptText("Ara...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterNotes(newVal));
        
        VBox sidebarTop = new VBox(5, explorerHeader, searchField);
        sidebarTop.setPadding(new Insets(0, 10, 10, 10));

        VBox sidebar = new VBox(sidebarTop, noteTreeView);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(0));
        sidebar.setSpacing(0);
        VBox.setVgrow(noteTreeView, Priority.ALWAYS);
        mainLayout.setLeft(sidebar);

        // Main Content
        VBox mainContent = new VBox();
        mainContent.getStyleClass().add("main-content");
        mainContent.setPadding(new Insets(20, 40, 20, 40));
        mainContent.setSpacing(20);

        titleField = new TextField();
        titleField.setPromptText("Not Başlığı");
        titleField.setPrefWidth(500);
        titleField.getStyleClass().add("title-field");

        splitPane = new SplitPane();
        splitPane.getStyleClass().add("main-split-pane");
        
        editorArea = new TextArea();
        editorArea.setPromptText("Markdown yazmaya başla...");
        editorArea.getStyleClass().add("editor-area");
        editorArea.setWrapText(true); // Infinite canvas feel (wrapping)
        editorArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isPreviewOpen) updatePreview(newVal);
            autoSaveTimer.playFromStart(); // Reset timer on change
        });
        
        // Editor Context Menu
        setupEditorContextMenu();

        previewArea = new WebView();

        // Default: Only Editor
        splitPane.getItems().add(editorArea);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        mainContent.getChildren().addAll(titleField, splitPane);
        mainLayout.setCenter(mainContent);

        // Status Bar
        HBox statusBar = createStatusBar();
        mainLayout.setBottom(statusBar);

        rootStack.getChildren().add(mainLayout);

        // Scene & Styling
        Scene scene = new Scene(rootStack, 1200, 800);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        
        stage.setTitle("LambdaNotes");
        stage.setScene(scene);
        
        // Resize Logic
        ResizeHelper.addResizeListener(stage, () -> isMaximized);
        
        stage.show();

        refreshNoteList();
        
        // Default to Preview Mode
        togglePreview();
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
        titleBar.setPadding(new Insets(5, 10, 5, 10));
        titleBar.setSpacing(10);

        Label titleLabel = new Label("LambdaNotes");
        titleLabel.getStyleClass().add("window-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnMinimize = new Button("—");
        btnMinimize.getStyleClass().add("window-button");
        btnMinimize.setOnAction(e -> stage.setIconified(true));

        Button btnMaximize = new Button("☐");
        btnMaximize.getStyleClass().add("window-button");
        btnMaximize.setOnAction(e -> toggleMaximize(stage));

        Button btnClose = new Button("✕");
        btnClose.getStyleClass().add("window-button-close");
        btnClose.setOnAction(e -> stage.close());

        titleBar.getChildren().addAll(titleLabel, spacer, btnMinimize, btnMaximize, btnClose);

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

    private HBox createToolBar() {
        Button btnNew = new Button("📄 Yeni Not");
        btnNew.setOnAction(e -> clearEditor());
        styleButton(btnNew, "btn-new");

        Button btnNewFolder = new Button("📁 Yeni Klasör");
        btnNewFolder.setOnAction(e -> createNewFolder());
        styleButton(btnNewFolder, "btn-new");

        // Spacer
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnPreview = new Button("👁 Önizleme");
        btnPreview.setOnAction(e -> togglePreview());
        styleButton(btnPreview, "btn-preview");

        Button btnSync = new Button("🔄 Senkronize Et");
        btnSync.setOnAction(e -> syncNotes());
        styleButton(btnSync, "btn-sync");

        Button btnSettings = new Button("⚙ Ayarlar");
        btnSettings.setOnAction(e -> openSettings());
        styleButton(btnSettings, "btn-settings");

        HBox toolbar = new HBox(10);
        toolbar.getStyleClass().add("tool-bar-container");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        
        toolbar.getChildren().addAll(btnNew, btnNewFolder, spacer, btnPreview, btnSync, btnSettings);
        return toolbar;
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
        }
    }

    private void insertTextAtCursor(String text) {
        int caret = editorArea.getCaretPosition();
        editorArea.insertText(caret, text);
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
            splitPane.getItems().add(previewArea);
            splitPane.setDividerPositions(0.5);
            updatePreview(editorArea.getText());
            btnPreview.setStyle("-fx-background-color: #3e4451; -fx-text-fill: white;");
        } else {
            splitPane.getItems().remove(previewArea);
            btnPreview.setStyle(""); // Reset to default style class
        }
    }

    private void styleButton(Button btn, String styleClass) {
        btn.getStyleClass().add(styleClass);
        // btn.setRippleColor(Color.valueOf("#ffffff33")); // Removed for AtlantaFX
    }


    private void openSettings() {
        SettingsDialog dialog = new SettingsDialog(noteService);
        Optional<AppConfig> result = dialog.showAndWaitResult();

        result.ifPresent(config -> {
            noteService.saveConfig(config).thenRun(() -> Platform.runLater(() -> {
                showAlert("Başarılı", "Ayarlar kaydedildi ve Git yapılandırıldı.");
            })).exceptionally(e -> {
                Platform.runLater(() -> showAlert("Hata", "Ayarlar kaydedilemedi: " + e.getMessage()));
                return null;
            });
        });
    }


    private void updatePreview(String markdown) {
        String html = renderer.render(parser.parse(markdown));
        String styledHtml = "<html><head>" +
                "<link rel='preconnect' href='https://fonts.googleapis.com'>" +
                "<link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>" +
                "<link href='https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap' rel='stylesheet'>" +
                "<style>" +
                "body { font-family: 'Roboto', sans-serif; color: #abb2bf; background-color: #282c34; padding: 40px; line-height: 1.6; max-width: 900px; margin: 0 auto; }" +
                "h1, h2, h3 { color: #e06c75; border-bottom: 1px solid #3e4451; padding-bottom: 10px; margin-top: 20px; font-weight: 600; }" +
                "h1 { font-size: 2.2em; } h2 { font-size: 1.8em; }" +
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
                "</style></head><body>" + html + "</body></html>";
        previewArea.getEngine().loadContent(styledHtml);
    }

    private void loadNote(String filename) {
        noteService.getNoteDetail(filename).thenAccept(note -> Platform.runLater(() -> {
            titleField.setText(note.getFilename());
            editorArea.setText(note.getContent());
            if (isPreviewOpen) updatePreview(note.getContent());
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
        }));
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

        Label branchLabel = new Label("main*"); // Mock branch name
        branchLabel.getStyleClass().add("status-branch");

        statusBar.getChildren().addAll(syncSpinner, statusLabel, spacer, branchLabel);
        return statusBar;
    }

    private void showNotification(String title, String url) {
        VBox notification = new VBox(5);
        notification.getStyleClass().add("notification-popup");
        notification.setMaxSize(300, 100);
        notification.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
        
        Hyperlink repoLink = new Hyperlink("GitHub Reposunu Aç");
        repoLink.setOnAction(e -> getHostServices().showDocument(url));
        repoLink.setStyle("-fx-text-fill: #61afef; -fx-border-color: transparent;");

        notification.getChildren().addAll(titleLabel, repoLink);

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
        fadeOut.setDelay(Duration.seconds(5));
        fadeOut.setOnFinished(e -> rootStack.getChildren().remove(notification));

        fadeIn.play();
        fadeIn.setOnFinished(e -> fadeOut.play());
    }

    private void syncNotes() {
        statusLabel.setText("Senkronize ediliyor...");
        syncSpinner.setVisible(true);
        
        noteService.syncNotes().thenRun(() -> Platform.runLater(() -> {
            refreshNoteList();
            statusLabel.setText("Hazır");
            syncSpinner.setVisible(false);
            showNotification("Senkronizasyon Tamamlandı", "https://github.com/sefacicekli/devopsnotes");
        })).exceptionally(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Hata");
                syncSpinner.setVisible(false);
                showAlert("Hata", "Senkronizasyon hatası: " + e.getMessage());
            });
            return null;
        });
    }

    private void refreshNoteList() {
        noteService.getNotes().thenAccept(notes -> Platform.runLater(() -> {
            allNotes = notes; // Cache for search
            buildTreeFromList(notes);
        }));
    }

    private void clearEditor() {
        titleField.clear();
        editorArea.clear();
        noteTreeView.getSelectionModel().clearSelection();
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
