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
import javafx.stage.FileChooser;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SettingsDialog extends Stage {

    private static final Logger logger = Logger.getLogger(SettingsDialog.class.getName());
    private final NoteService noteService;
    private final Consumer<String> onThemeChange;
    private AppConfig result = null;
    private AppConfig currentConfig;
    private String originalTheme;
    private boolean saved = false;

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

    // Editor Settings Components
    private Slider fontSizeSlider;
    private Label fontSizeValueLabel;
    private ComboBox<String> fontFamilyComboBox;
    private CheckBox showLineNumbersCheckBox;
    private CheckBox showTabsCheckBox;
    private CheckBox showTitleInPreviewCheckBox;
    
    // General Settings Components
    private ComboBox<String> languageComboBox;

    // Appearance Settings Components
    private ComboBox<String> themeComboBox;

    // Views Cache
    private VBox githubView;
    private VBox editorView;
    private VBox generalView;
    private VBox appearanceView;

    private String getThemeStylesheet(String theme) {
        if ("Light".equals(theme)) {
            return getClass().getResource("light_theme.css").toExternalForm();
        } else if ("Tokyo Night".equals(theme)) {
            return getClass().getResource("tokyo_night.css").toExternalForm();
        } else if ("Retro Night".equals(theme)) {
            return getClass().getResource("retro_night.css").toExternalForm();
        } else {
            return getClass().getResource("styles.css").toExternalForm();
        }
    }

    public SettingsDialog(NoteService noteService, AppConfig currentConfig, Consumer<String> onThemeChange) {
        this.noteService = noteService;
        this.currentConfig = currentConfig;
        this.onThemeChange = onThemeChange;
        
        if (currentConfig != null && currentConfig.getTheme() != null) {
            this.originalTheme = currentConfig.getTheme();
        } else {
            this.originalTheme = "Dark";
        }
        
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.TRANSPARENT);

        root = new BorderPane();
        root.getStyleClass().add("settings-dialog");
        root.setPrefSize(800, 600);

        // Sidebar
        categoryList = new ListView<>();
        categoryList.getStyleClass().add("settings-sidebar");
        categoryList.getItems().addAll(
            LanguageManager.get("settings.general"), 
            LanguageManager.get("settings.appearance"), 
            LanguageManager.get("settings.editor"), 
            LanguageManager.get("settings.github"), 
            LanguageManager.get("settings.about")
        );
        categoryList.getSelectionModel().select(LanguageManager.get("settings.github")); // Default selection
        categoryList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchView(newVal);
        });

        // Content Area
        contentArea = new StackPane();
        contentArea.getStyleClass().add("settings-content");
        
        // Pre-create views to preserve state
        githubView = createGitHubSettingsView();
        editorView = createEditorSettingsView();
        generalView = createGeneralSettingsView();
        appearanceView = createAppearanceSettingsView();

        // Initial View
        switchView("GitHub");

        root.setLeft(categoryList);
        root.setCenter(contentArea);
        
        // Footer (Buttons)
        HBox footer = createFooter();
        root.setBottom(footer);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getThemeStylesheet(originalTheme));
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
        
        // Revert theme if closed without saving
        setOnHidden(e -> {
            if (!saved && onThemeChange != null) {
                onThemeChange.accept(originalTheme);
            }
        });
    }

    private void switchView(String category) {
        contentArea.getChildren().clear();
        
        Region view = null;
        
        if (category.equals(LanguageManager.get("settings.github"))) {
            view = githubView;
        } else if (category.equals(LanguageManager.get("settings.editor"))) {
            view = editorView;
        } else if (category.equals(LanguageManager.get("settings.general"))) {
            view = generalView;
        } else if (category.equals(LanguageManager.get("settings.appearance"))) {
            view = appearanceView;
        } else if (category.equals(LanguageManager.get("settings.about"))) {
            Label placeholder = new Label(java.text.MessageFormat.format(LanguageManager.get("settings.about.placeholder"), category));
            placeholder.setStyle("-fx-text-fill: #7c7f88; -fx-font-size: 14px;");
            view = placeholder;
        }

        if (view != null) {
            ScrollPane scrollPane = new ScrollPane(view);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
            
            // Make viewport transparent
            scrollPane.skinProperty().addListener((obs, oldSkin, newSkin) -> {
                if (newSkin != null) {
                    javafx.scene.Node viewport = scrollPane.lookup(".viewport");
                    if (viewport != null) {
                        viewport.setStyle("-fx-background-color: transparent;");
                    }
                }
            });
            
            contentArea.getChildren().add(scrollPane);
        }
    }

    private VBox createAppearanceSettingsView() {
        VBox view = new VBox(20);
        view.setAlignment(Pos.TOP_LEFT);

        Label header = new Label(LanguageManager.get("settings.appearance.header"));
        header.getStyleClass().add("settings-header-label");

        // Theme Section
        VBox themeSection = new VBox(10);
        Label themeLabel = new Label(LanguageManager.get("settings.appearance.theme"));
        themeLabel.getStyleClass().add("settings-section-label");

        themeComboBox = new ComboBox<>();
        themeComboBox.getItems().addAll("Dark", "Light", "Tokyo Night", "Retro Night");
        themeComboBox.setMaxWidth(200);
        themeComboBox.getStyleClass().add("settings-combo-box");
        
        if (currentConfig != null && currentConfig.getTheme() != null) {
            String currentTheme = currentConfig.getTheme();
            // Case insensitive search for theme
            for (String item : themeComboBox.getItems()) {
                if (item.equalsIgnoreCase(currentTheme)) {
                    themeComboBox.getSelectionModel().select(item);
                    break;
                }
            }
            if (themeComboBox.getSelectionModel().getSelectedItem() == null) {
                 themeComboBox.getSelectionModel().select("Dark");
            }
        } else {
            themeComboBox.getSelectionModel().select("Dark");
        }

        // Apply theme immediately on change
        themeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                try {
                    // Update global app theme
                    if (onThemeChange != null) {
                        onThemeChange.accept(newVal);
                    }

                    switch (newVal) {
                        case "Light":
                            javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
                            break;
                        case "Dark":
                            javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
                            break;
                        case "Tokyo Night":
                            javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
                            break;
                        case "Retro Night":
                             javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
                             break;
                    }
                    // Re-apply custom styles to this dialog
                    if (getScene() != null) {
                        getScene().getStylesheets().clear();
                        getScene().getStylesheets().add(getThemeStylesheet(newVal));
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to apply theme: " + newVal, e);
                }
            }
        });

        Label themeHint = new Label(LanguageManager.get("settings.appearance.theme_hint"));
        themeHint.getStyleClass().add("settings-hint-label");
        
        // Preview Settings
        VBox previewSection = new VBox(10);
        Label previewLabel = new Label(LanguageManager.get("settings.appearance.preview"));
        previewLabel.getStyleClass().add("settings-section-label");
        
        showTitleInPreviewCheckBox = new CheckBox(LanguageManager.get("settings.appearance.show_title"));
        showTitleInPreviewCheckBox.setStyle("-fx-text-fill: #dfe1e5;");
        if (currentConfig != null) {
            showTitleInPreviewCheckBox.setSelected(currentConfig.isShowTitleInPreview());
        } else {
            showTitleInPreviewCheckBox.setSelected(true);
        }
        
        previewSection.getChildren().addAll(previewLabel, showTitleInPreviewCheckBox);

        themeSection.getChildren().addAll(themeLabel, themeComboBox, themeHint);

        view.getChildren().addAll(header, themeSection, new Separator(), previewSection);
        return view;
    }

    private VBox createEditorSettingsView() {
        VBox view = new VBox(20);
        view.setAlignment(Pos.TOP_LEFT);

        Label header = new Label(LanguageManager.get("settings.editor.header"));
        header.getStyleClass().add("settings-header-label");

        // Font Family Section
        VBox fontFamilySection = new VBox(10);
        Label fontFamilyLabel = new Label(LanguageManager.get("settings.editor.font_family"));
        fontFamilyLabel.getStyleClass().add("settings-section-label");

        fontFamilyComboBox = new ComboBox<>();
        fontFamilyComboBox.getItems().addAll("JetBrains Mono", "Inter", "Roboto");
        fontFamilyComboBox.setMaxWidth(200);
        fontFamilyComboBox.getStyleClass().add("settings-combo-box");

        if (currentConfig != null && currentConfig.getFontFamily() != null) {
            fontFamilyComboBox.getSelectionModel().select(currentConfig.getFontFamily());
        } else {
            fontFamilyComboBox.getSelectionModel().select("JetBrains Mono");
        }

        fontFamilySection.getChildren().addAll(fontFamilyLabel, fontFamilyComboBox);

        // Font Size Section
        VBox fontSection = new VBox(10);
        Label fontLabel = new Label(LanguageManager.get("settings.editor.font_size"));
        fontLabel.getStyleClass().add("settings-section-label");

        HBox sliderBox = new HBox(15);
        sliderBox.setAlignment(Pos.CENTER_LEFT);

        fontSizeSlider = new Slider(10, 30, 14);
        if (currentConfig != null) {
            fontSizeSlider.setValue(currentConfig.getEditorFontSize());
        } else {
            fontSizeSlider.setValue(16); // Default if no config
        }
        fontSizeSlider.setShowTickMarks(true);
        fontSizeSlider.setShowTickLabels(true);
        fontSizeSlider.setMajorTickUnit(2);
        fontSizeSlider.setBlockIncrement(1);
        fontSizeSlider.setPrefWidth(300);

        fontSizeValueLabel = new Label((int) fontSizeSlider.getValue() + "px");
        fontSizeValueLabel.setStyle("-fx-text-fill: #dfe1e5; -fx-font-weight: bold;");

        fontSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            fontSizeValueLabel.setText(newVal.intValue() + "px");
        });

        sliderBox.getChildren().addAll(fontSizeSlider, fontSizeValueLabel);
        fontSection.getChildren().addAll(fontLabel, sliderBox);



        // Line Numbers Section
        VBox lineNumbersSection = new VBox(10);
        Label lineNumbersLabel = new Label(LanguageManager.get("settings.appearance"));
        lineNumbersLabel.getStyleClass().add("settings-section-label");

        showLineNumbersCheckBox = new CheckBox(LanguageManager.get("settings.editor.show_line_numbers"));
        showLineNumbersCheckBox.setStyle("-fx-text-fill: #dfe1e5;");
        if (currentConfig != null) {
            showLineNumbersCheckBox.setSelected(currentConfig.isShowLineNumbers());
        } else {
            showLineNumbersCheckBox.setSelected(true);
        }

        showTabsCheckBox = new CheckBox(LanguageManager.get("settings.editor.show_tabs"));
        showTabsCheckBox.setStyle("-fx-text-fill: #dfe1e5;");
        if (currentConfig != null) {
            showTabsCheckBox.setSelected(currentConfig.isShowTabs());
        } else {
            showTabsCheckBox.setSelected(false);
        }

        lineNumbersSection.getChildren().addAll(lineNumbersLabel, showLineNumbersCheckBox, showTabsCheckBox);

        // Title in Preview Section
        VBox titlePreviewSection = new VBox(10);
        Label titlePreviewLabel = new Label(LanguageManager.get("settings.editor.title_preview"));
        titlePreviewLabel.getStyleClass().add("settings-section-label");

        showTitleInPreviewCheckBox = new CheckBox(LanguageManager.get("settings.editor.show_title_preview"));
        showTitleInPreviewCheckBox.setStyle("-fx-text-fill: #dfe1e5;");
        if (currentConfig != null) {
            showTitleInPreviewCheckBox.setSelected(currentConfig.isShowTitleInPreview());
        } else {
            showTitleInPreviewCheckBox.setSelected(true);
        }

        titlePreviewSection.getChildren().addAll(titlePreviewLabel, showTitleInPreviewCheckBox);

        view.getChildren().addAll(header, fontFamilySection, fontSection, new Separator(), lineNumbersSection, titlePreviewSection);
        return view;
    }


    private VBox createGitHubSettingsView() {
        VBox view = new VBox(20);
        view.setAlignment(Pos.TOP_LEFT);
        
        Label header = new Label(LanguageManager.get("settings.github.header"));
        header.getStyleClass().add("settings-header-label");
        
        // Section 1: Authentication
        VBox authSection = new VBox(10);
        Label authLabel = new Label(LanguageManager.get("settings.github.account"));
        authLabel.getStyleClass().add("settings-section-label");
        
        Button githubLoginBtn = new Button(LanguageManager.get("settings.github.connect_device"));
        githubLoginBtn.getStyleClass().add("dialog-button-primary");
        githubLoginBtn.setMaxWidth(Double.MAX_VALUE);
        
        // Auth Info Box (Hidden by default)
        VBox authInfoBox = new VBox(10);
        authInfoBox.setVisible(false);
        authInfoBox.setManaged(false);
        authInfoBox.setStyle("-fx-background-color: #2b2d30; -fx-padding: 15; -fx-background-radius: 6; -fx-border-color: #43454a; -fx-border-radius: 6;");

        Label authInstruction = new Label(LanguageManager.get("settings.github.copy_code"));
        authInstruction.setWrapText(true);
        authInstruction.setStyle("-fx-text-fill: #bcbec4; -fx-font-size: 13px;");

        HBox codeBox = new HBox(10);
        codeBox.setAlignment(Pos.CENTER_LEFT);

        TextField userCodeField = new TextField();
        userCodeField.setEditable(false);
        userCodeField.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #1e1f22; -fx-text-fill: #98c379; -fx-border-color: #43454a; -fx-border-radius: 4;");
        userCodeField.setPrefWidth(150);

        Button copyBtn = new Button(LanguageManager.get("settings.github.copy_open"));
        copyBtn.getStyleClass().add("dialog-button-secondary");

        codeBox.getChildren().addAll(userCodeField, copyBtn);
        authInfoBox.getChildren().addAll(authInstruction, codeBox);
        
        githubLoginBtn.setOnAction(e -> startGithubLogin(githubLoginBtn, authInfoBox, userCodeField, copyBtn));
        
        // Manual Token Input
        VBox tokenBox = new VBox(5);
        Label tokenLabel = new Label(LanguageManager.get("settings.github.token_hint"));
        tokenLabel.getStyleClass().add("settings-hint-label");
        
        HBox tokenInputBox = new HBox(10);
        tokenField = new TextField();
        tokenField.setPromptText("ghp_...");
        tokenField.getStyleClass().add("settings-text-field");
        HBox.setHgrow(tokenField, Priority.ALWAYS);
        
        Button loadReposBtn = new Button(LanguageManager.get("settings.github.login"));
        loadReposBtn.getStyleClass().add("dialog-button-secondary");
        loadReposBtn.setOnAction(e -> loadRepos());
        
        tokenInputBox.getChildren().addAll(tokenField, loadReposBtn);
        tokenBox.getChildren().addAll(tokenLabel, tokenInputBox);
        
        authSection.getChildren().addAll(authLabel, githubLoginBtn, authInfoBox, tokenBox);
        
        // Section 2: Repository Selection
        VBox repoSection = new VBox(10);
        Label repoLabel = new Label(LanguageManager.get("settings.github.repo_select"));
        repoLabel.getStyleClass().add("settings-section-label");
        
        HBox repoSelectionBox = new HBox(10);
        repoComboBox = new ComboBox<>();
        repoComboBox.setPromptText(LanguageManager.get("settings.github.repo_placeholder"));
        repoComboBox.setMaxWidth(Double.MAX_VALUE);
        repoComboBox.getStyleClass().add("settings-combo-box");
        HBox.setHgrow(repoComboBox, Priority.ALWAYS);

        Button createRepoBtn = new Button("+");
        createRepoBtn.getStyleClass().add("dialog-button-secondary");
        createRepoBtn.setTooltip(new Tooltip(LanguageManager.get("settings.github.create_new")));
        createRepoBtn.setOnAction(e -> showCreateRepoDialog());
        
        repoSelectionBox.getChildren().addAll(repoComboBox, createRepoBtn);
        
        Label repoHint = new Label(LanguageManager.get("settings.github.repo_hint"));
        repoHint.getStyleClass().add("settings-hint-label");
        
        repoSection.getChildren().addAll(repoLabel, repoSelectionBox, repoHint);
        
        // Status & Info
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #7c7f88; -fx-font-size: 12px;");
        
        connectedUserLabel = new Label(LanguageManager.get("settings.github.not_connected"));
        connectedUserLabel.setStyle("-fx-text-fill: #7c7f88; -fx-font-size: 12px;");

        view.getChildren().addAll(header, authSection, new Separator(), repoSection, statusLabel, connectedUserLabel);
        
        // Initialize Data if available
        if (currentConfig != null && currentConfig.getToken() != null && !currentConfig.getToken().isEmpty()) {
            tokenField.setText(currentConfig.getToken());
            connectedUserLabel.setText(LanguageManager.get("settings.status.connecting"));
            loadRepos(); // Reuse loadRepos logic
        }
        
        return view;
    }

    private VBox createGeneralSettingsView() {
        VBox view = new VBox(20);
        view.setAlignment(Pos.TOP_LEFT);

        Label header = new Label(LanguageManager.get("settings.general.header"));
        header.getStyleClass().add("settings-header-label");

        // Language Section
        VBox languageSection = new VBox(10);
        Label languageLabel = new Label(LanguageManager.get("settings.general.language"));
        languageLabel.getStyleClass().add("settings-section-label");

        languageComboBox = new ComboBox<>();
        languageComboBox.getItems().addAll("English", "Türkçe");
        languageComboBox.setMaxWidth(200);
        languageComboBox.getStyleClass().add("settings-combo-box");

        if (currentConfig != null && currentConfig.getLanguage() != null) {
            if ("tr".equals(currentConfig.getLanguage())) {
                languageComboBox.getSelectionModel().select("Türkçe");
            } else {
                languageComboBox.getSelectionModel().select("English");
            }
        } else {
            languageComboBox.getSelectionModel().select("English");
        }
        
        Label languageHint = new Label(LanguageManager.get("settings.general.language_hint"));
        languageHint.getStyleClass().add("settings-hint-label");
        
        languageSection.getChildren().addAll(languageLabel, languageComboBox, languageHint);

        // Config Management Section
        VBox configSection = new VBox(10);
        Label configLabel = new Label(LanguageManager.get("settings.general.config_mgmt"));
        configLabel.getStyleClass().add("settings-section-label");

        Label hintLabel = new Label(LanguageManager.get("settings.general.config_hint"));
        hintLabel.getStyleClass().add("settings-hint-label");

        HBox buttonBox = new HBox(10);
        
        Button btnImport = new Button(LanguageManager.get("settings.general.import"));
        btnImport.getStyleClass().add("dialog-button-secondary");
        btnImport.setOnAction(e -> importSettings());

        Button btnExport = new Button(LanguageManager.get("settings.general.export"));
        btnExport.getStyleClass().add("dialog-button-secondary");
        btnExport.setOnAction(e -> exportSettings());

        buttonBox.getChildren().addAll(btnImport, btnExport);
        configSection.getChildren().addAll(configLabel, hintLabel, buttonBox);

        view.getChildren().addAll(header, languageSection, new Separator(), configSection);
        return view;
    }

    private HBox createFooter() {
        HBox footer = new HBox(10);
        footer.getStyleClass().add("settings-footer");
        
        Button btnCancel = new Button(LanguageManager.get("dialog.cancel"));
        btnCancel.getStyleClass().add("dialog-button-cancel");
        btnCancel.setOnAction(e -> close());
        
        Button btnSave = new Button(LanguageManager.get("dialog.ok"));
        btnSave.getStyleClass().add("dialog-button-primary");
        btnSave.setOnAction(e -> saveAndClose());
        
        footer.getChildren().addAll(btnCancel, btnSave);
        return footer;
    }

    private void saveAndClose() {
        // Start with existing config or defaults
        String repoUrl = (currentConfig != null) ? currentConfig.getRepoUrl() : "";
        String token = (currentConfig != null) ? currentConfig.getToken() : "";
        String username = (currentConfig != null) ? currentConfig.getUsername() : "";
        String email = (currentConfig != null) ? currentConfig.getEmail() : "";
        int fontSize = (currentConfig != null) ? currentConfig.getEditorFontSize() : 16;
        String fontFamily = (currentConfig != null && currentConfig.getFontFamily() != null) ? currentConfig.getFontFamily() : "JetBrains Mono";
        String theme = (currentConfig != null && currentConfig.getTheme() != null) ? currentConfig.getTheme() : "Dark";

        // Update from UI
        if (tokenField != null && !tokenField.getText().isEmpty()) {
            token = tokenField.getText();
        }
        
        if (repoComboBox != null && repoComboBox.getValue() != null) {
            repoUrl = repoComboBox.getValue().getCloneUrl();
        }
        
        if (currentUser != null) {
            username = currentUser.getLogin();
            email = currentUser.getEmail() != null ? currentUser.getEmail() : currentUser.getLogin() + "@users.noreply.github.com";
        }
        
        if (fontSizeSlider != null) {
            fontSize = (int) fontSizeSlider.getValue();
        }

        if (fontFamilyComboBox != null && fontFamilyComboBox.getValue() != null) {
            fontFamily = fontFamilyComboBox.getValue();
        }
        
        boolean showLineNumbers = true;
        if (showLineNumbersCheckBox != null) {
            showLineNumbers = showLineNumbersCheckBox.isSelected();
        }
        
        boolean showTabs = false;
        if (showTabsCheckBox != null) {
            showTabs = showTabsCheckBox.isSelected();
        }
        
        boolean showTitleInPreview = true;
        if (showTitleInPreviewCheckBox != null) {
            showTitleInPreview = showTitleInPreviewCheckBox.isSelected();
        }
        
        if (themeComboBox != null && themeComboBox.getValue() != null) {
            theme = themeComboBox.getValue();
        }
        
        String language = "en";
        if (languageComboBox != null && languageComboBox.getValue() != null) {
            language = "Türkçe".equals(languageComboBox.getValue()) ? "tr" : "en";
        }

        result = new AppConfig(repoUrl, token, username, email);
        result.setEditorFontSize(fontSize);
        result.setFontFamily(fontFamily);
        result.setShowLineNumbers(showLineNumbers);
        result.setShowTabs(showTabs);
        result.setShowTitleInPreview(showTitleInPreview);
        result.setTheme(theme);
        result.setLanguage(language);
        
        saved = true;
        close();
    }


    public Optional<AppConfig> showAndWaitResult() {
        showAndWait();
        return Optional.ofNullable(result);
    }

    private void showCreateRepoDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(this);
        dialog.initStyle(StageStyle.TRANSPARENT);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #2b2d30; -fx-text-fill: #dfe1e5; -fx-border-color: #43454a; -fx-border-width: 1;");
        
        // Custom Header
        Label titleLabel = new Label(LanguageManager.get("settings.github.create_new_title"));
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #dfe1e5;");
        
        // Name
        VBox nameBox = new VBox(5);
        Label nameLabel = new Label(LanguageManager.get("settings.github.repo_name"));
        nameLabel.setStyle("-fx-text-fill: #dfe1e5;");
        TextField nameField = new TextField();
        nameField.getStyleClass().add("settings-text-field");
        nameBox.getChildren().addAll(nameLabel, nameField);
        
        // Description
        VBox descBox = new VBox(5);
        Label descLabel = new Label(LanguageManager.get("settings.github.repo_desc"));
        descLabel.setStyle("-fx-text-fill: #dfe1e5;");
        TextField descField = new TextField();
        descField.getStyleClass().add("settings-text-field");
        descBox.getChildren().addAll(descLabel, descField);
        
        // Visibility
        CheckBox privateCheck = new CheckBox(LanguageManager.get("settings.github.repo_private"));
        privateCheck.setStyle("-fx-text-fill: #dfe1e5;");
        privateCheck.setSelected(true);
        
        // Buttons
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button cancelBtn = new Button(LanguageManager.get("dialog.cancel"));
        cancelBtn.getStyleClass().add("dialog-button-cancel");
        cancelBtn.setOnAction(e -> dialog.close());
        
        Button createBtn = new Button(LanguageManager.get("dialog.create"));
        createBtn.getStyleClass().add("dialog-button-primary");
        createBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                nameField.setStyle("-fx-border-color: #e06c75;");
                return;
            }
            
            createBtn.setDisable(true);
            String token = tokenField.getText();
            if (token == null || token.isEmpty()) {
                statusLabel.setText(LanguageManager.get("settings.status.enter_token"));
                createBtn.setDisable(false);
                dialog.close();
                return;
            }
            
            noteService.createRepository(token, name, descField.getText(), privateCheck.isSelected())
                .thenAccept(repo -> Platform.runLater(() -> {
                    repoComboBox.getItems().add(0, repo);
                    repoComboBox.getSelectionModel().select(repo);
                    dialog.close();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        createBtn.setDisable(false);
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle(LanguageManager.get("dialog.error"));
                        alert.setHeaderText(null);
                        alert.setContentText(ex.getMessage());
                        alert.showAndWait();
                    });
                    return null;
                });
        });
        
        buttons.getChildren().addAll(cancelBtn, createBtn);
        
        root.getChildren().addAll(titleLabel, nameBox, descBox, privateCheck, buttons);
        
        Scene scene = new Scene(root, 400, 350);
        scene.setFill(Color.TRANSPARENT);
        
        // Drag Logic
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        root.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            dialog.setX(event.getScreenX() - xOffset[0]);
            dialog.setY(event.getScreenY() - yOffset[0]);
        });

        // Apply theme
        if (currentConfig != null && currentConfig.getTheme() != null) {
             scene.getStylesheets().add(getThemeStylesheet(currentConfig.getTheme()));
        } else {
             scene.getStylesheets().add(getThemeStylesheet("Dark"));
        }
        
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void loadRepos() {
        String token = tokenField.getText();
        if (token.isEmpty()) {
            statusLabel.setText(LanguageManager.get("settings.status.enter_token"));
            return;
        }

        statusLabel.setText(LanguageManager.get("settings.status.connecting"));
        statusLabel.setStyle("-fx-text-fill: #7c7f88;");
        logger.info("Loading repos with token...");
        
        noteService.fetchGitHubUser(token)
            .thenCompose(user -> {
                currentUser = user;
                return noteService.fetchUserRepos(token);
            })
            .thenAccept(repos -> Platform.runLater(() -> {
                repoComboBox.getItems().setAll(repos);
                statusLabel.setText(java.text.MessageFormat.format(LanguageManager.get("settings.status.login_success"), currentUser.getName()));
                statusLabel.setStyle("-fx-text-fill: #98c379;");
                
                connectedUserLabel.setText(java.text.MessageFormat.format(LanguageManager.get("settings.status.connected_user"), currentUser.getLogin()));
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
                    statusLabel.setText(java.text.MessageFormat.format(LanguageManager.get("dialog.error"), e.getMessage()));
                    statusLabel.setStyle("-fx-text-fill: #e06c75;");
                });
                logger.log(Level.SEVERE, "Failed to load repos", e);
                return null;
            });
    }

    private void startGithubLogin(Button loginBtn, VBox infoBox, TextField codeField, Button copyBtn) {
        loginBtn.setDisable(true);
        statusLabel.setText(LanguageManager.get("settings.status.contacting_github"));
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
                        statusLabel.setText(java.text.MessageFormat.format(LanguageManager.get("settings.status.browser_error"), resp.verification_uri));
                        logger.log(Level.WARNING, "Failed to open browser", ex);
                    }
                });
                
                statusLabel.setText(LanguageManager.get("settings.status.confirm_code"));
                pollGithub(resp.device_code, resp.interval, infoBox);
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    statusLabel.setText(java.text.MessageFormat.format(LanguageManager.get("dialog.error"), e.getMessage()));
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
                            statusLabel.setText(LanguageManager.get("settings.status.loading_repos"));
                            
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
                    
                    Platform.runLater(() -> statusLabel.setText(java.text.MessageFormat.format(LanguageManager.get("settings.status.login_error"), resp.error_description)));
                    break;
                }
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText(java.text.MessageFormat.format(LanguageManager.get("settings.status.polling_error"), e.getMessage())));
            }
        }).start();
    }

    private void importSettings() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(LanguageManager.get("settings.general.import"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(LanguageManager.get("dialog.json_files"), "*.json"));
        File file = fileChooser.showOpenDialog(this);

        if (file != null) {
            try {
                String json = new String(Files.readAllBytes(file.toPath()));
                Gson gson = new Gson();
                AppConfig importedConfig = gson.fromJson(json, AppConfig.class);
                
                // Update UI with imported config
                if (importedConfig != null) {
                    if (importedConfig.getToken() != null) tokenField.setText(importedConfig.getToken());
                    if (fontSizeSlider != null) fontSizeSlider.setValue(importedConfig.getEditorFontSize());
                    if (fontFamilyComboBox != null && importedConfig.getFontFamily() != null) fontFamilyComboBox.getSelectionModel().select(importedConfig.getFontFamily());
                    if (showLineNumbersCheckBox != null) showLineNumbersCheckBox.setSelected(importedConfig.isShowLineNumbers());
                    if (showTabsCheckBox != null) showTabsCheckBox.setSelected(importedConfig.isShowTabs());
                    if (themeComboBox != null && importedConfig.getTheme() != null) themeComboBox.getSelectionModel().select(importedConfig.getTheme());
                    if (languageComboBox != null && importedConfig.getLanguage() != null) {
                        if ("tr".equals(importedConfig.getLanguage())) {
                            languageComboBox.getSelectionModel().select("Türkçe");
                        } else {
                            languageComboBox.getSelectionModel().select("English");
                        }
                    }
                    
                    // Reload repos if token is present
                    if (importedConfig.getToken() != null && !importedConfig.getToken().isEmpty()) {
                        loadRepos();
                    }
                    
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle(LanguageManager.get("dialog.success"));
                    alert.setHeaderText(null);
                    alert.setContentText(LanguageManager.get("settings.general.import_success"));
                    alert.showAndWait();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to import settings", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(LanguageManager.get("dialog.error"));
                alert.setHeaderText(null);
                alert.setContentText(java.text.MessageFormat.format(LanguageManager.get("settings.general.import_error"), e.getMessage()));
                alert.showAndWait();
            }
        }
    }

    private void exportSettings() {
        // Create current config state for export
        String repoUrl = (currentConfig != null) ? currentConfig.getRepoUrl() : "";
        String token = (currentConfig != null) ? currentConfig.getToken() : "";
        String username = (currentConfig != null) ? currentConfig.getUsername() : "";
        String email = (currentConfig != null) ? currentConfig.getEmail() : "";
        int fontSize = (int) fontSizeSlider.getValue();
        String fontFamily = fontFamilyComboBox.getValue();
        boolean showLineNumbers = showLineNumbersCheckBox.isSelected();
        boolean showTabs = showTabsCheckBox.isSelected();
        boolean showTitleInPreview = showTitleInPreviewCheckBox.isSelected();
        String theme = themeComboBox.getValue();
        String language = "en";
        if (languageComboBox != null && languageComboBox.getValue() != null) {
            language = "Türkçe".equals(languageComboBox.getValue()) ? "tr" : "en";
        }

        if (tokenField != null && !tokenField.getText().isEmpty()) {
            token = tokenField.getText();
        }
        if (repoComboBox != null && repoComboBox.getValue() != null) {
            repoUrl = repoComboBox.getValue().getCloneUrl();
        }

        AppConfig configToExport = new AppConfig(repoUrl, token, username, email);
        configToExport.setEditorFontSize(fontSize);
        configToExport.setFontFamily(fontFamily);
        configToExport.setShowLineNumbers(showLineNumbers);
        configToExport.setShowTabs(showTabs);
        configToExport.setShowTitleInPreview(showTitleInPreview);
        configToExport.setTheme(theme);
        configToExport.setLanguage(language);

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(LanguageManager.get("settings.general.export"));
        fileChooser.setInitialFileName("lambdanotes-config.json");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(LanguageManager.get("dialog.json_files"), "*.json"));
        File file = fileChooser.showSaveDialog(this);

        if (file != null) {
            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(configToExport);
                Files.write(file.toPath(), json.getBytes());
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(LanguageManager.get("dialog.success"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.get("settings.general.export_success"));
                alert.showAndWait();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to export settings", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(LanguageManager.get("dialog.error"));
                alert.setHeaderText(null);
                alert.setContentText(java.text.MessageFormat.format(LanguageManager.get("settings.general.export_error"), e.getMessage()));
                alert.showAndWait();
            }
        }
    }
}

