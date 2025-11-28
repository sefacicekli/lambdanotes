package com.lambdanotes;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RecentFilesDialog extends Stage {

    private final TextField searchField;
    private final ListView<String> resultList;
    private final List<String> recentFiles;
    private final Consumer<String> onSelect;
    private final String currentTheme;

    // Theme Colors
    private String bgColor;
    private String fieldBgColor;
    private String borderColor;
    private String textColor;
    private String secondaryTextColor;
    private String accentColor;
    private String selectedBgColor;
    private String selectedTextColor;

    public RecentFilesDialog(List<String> recentFiles, String themeName, Consumer<String> onSelect) {
        this.recentFiles = recentFiles;
        this.currentTheme = themeName;
        this.onSelect = onSelect;

        initStyle(StageStyle.TRANSPARENT);
        initModality(Modality.APPLICATION_MODAL);

        resolveThemeColors();

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        // Apply root style with drop shadow
        root.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 8; -fx-border-color: %s; -fx-border-radius: 8; -fx-border-width: 1; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 20, 0, 0, 0);", bgColor, borderColor));
        root.setPrefSize(500, 400);

        // Header with Search Icon
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 5; -fx-padding: 8 12;", fieldBgColor));
        
        Label searchIcon = new Label("🕒"); // Clock icon for recent files
        searchIcon.setStyle("-fx-text-fill: " + secondaryTextColor + "; -fx-font-size: 14px;");

        searchField = new TextField();
        searchField.setPromptText("Recent Files");
        searchField.setStyle(String.format("-fx-background-color: transparent; -fx-text-fill: %s; -fx-font-size: 14px; -fx-prompt-text-fill: %s;", textColor, secondaryTextColor));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        searchBox.getChildren().addAll(searchIcon, searchField);

        resultList = new ListView<>();
        resultList.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 5 0 0 0;");
        
        // Custom Cell Factory
        resultList.setCellFactory(param -> new RecentFileCell());
        
        VBox.setVgrow(resultList, Priority.ALWAYS);

        root.getChildren().addAll(searchBox, resultList);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        
        // Add base styles for scrollbar
        try {
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        } catch (Exception e) { }
        
        setScene(scene);

        // Initial population
        filterList("");

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterList(newVal);
        });

        // Keyboard handling
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN) {
                resultList.requestFocus();
                if (!resultList.getItems().isEmpty()) {
                    resultList.getSelectionModel().selectFirst();
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                close();
            } else if (e.getCode() == KeyCode.ENTER) {
                selectCurrent();
            }
        });

        resultList.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                selectCurrent();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                close();
            } else if (e.getCode() == KeyCode.UP && resultList.getSelectionModel().getSelectedIndex() == 0) {
                searchField.requestFocus();
            }
        });
        
        resultList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                selectCurrent();
            }
        });

        // Focus field on show
        setOnShown(e -> searchField.requestFocus());
        
        // Close on lost focus
        focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) close();
        });
    }

    private void resolveThemeColors() {
        if ("Light".equalsIgnoreCase(currentTheme)) {
            bgColor = "#ffffff";
            fieldBgColor = "#f2f2f2";
            borderColor = "#d1d5da";
            textColor = "#24292e";
            secondaryTextColor = "#6a737d";
            accentColor = "#0366d6";
            selectedBgColor = "#e8f0fe";
            selectedTextColor = "#24292e";
        } else if ("Tokyo Night".equalsIgnoreCase(currentTheme)) {
            bgColor = "#1a1b26";
            fieldBgColor = "#16161e";
            borderColor = "#414868";
            textColor = "#c0caf5";
            secondaryTextColor = "#565f89";
            accentColor = "#7aa2f7";
            selectedBgColor = "#292e42";
            selectedTextColor = "#c0caf5";
        } else if ("Retro Night".equalsIgnoreCase(currentTheme)) {
            bgColor = "#2d2a2e";
            fieldBgColor = "#221f22";
            borderColor = "#403e41";
            textColor = "#fcfcfa";
            secondaryTextColor = "#727072";
            accentColor = "#ff7edb";
            selectedBgColor = "#403e41";
            selectedTextColor = "#fcfcfa";
        } else {
            // Dark (Default)
            bgColor = "#282c34";
            fieldBgColor = "#21252b";
            borderColor = "#181a1f";
            textColor = "#abb2bf";
            secondaryTextColor = "#5c6370";
            accentColor = "#61afef";
            selectedBgColor = "#3e4451";
            selectedTextColor = "#ffffff";
        }
    }

    private void filterList(String query) {
        if (query == null || query.trim().isEmpty()) {
            resultList.getItems().setAll(recentFiles);
        } else {
            String lowerQuery = query.toLowerCase();
            List<String> filtered = recentFiles.stream()
                .filter(f -> f.toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
            resultList.getItems().setAll(filtered);
        }
        
        if (!resultList.getItems().isEmpty()) {
            resultList.getSelectionModel().selectFirst();
        }
    }

    private void selectCurrent() {
        String selected = resultList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            onSelect.accept(selected);
            close();
        }
    }

    private class RecentFileCell extends ListCell<String> {
        private final VBox root;
        private final Label fileLabel;
        private final Label pathLabel;
        private final HBox headerBox;
        private final Label iconLabel;

        public RecentFileCell() {
            root = new VBox(2);
            root.setPadding(new Insets(6, 10, 6, 10));
            
            headerBox = new HBox(8);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            
            iconLabel = new Label("📄");
            iconLabel.setStyle("-fx-font-size: 12px;");
            
            fileLabel = new Label();
            fileLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            
            headerBox.getChildren().addAll(iconLabel, fileLabel);
            
            pathLabel = new Label();
            pathLabel.setStyle("-fx-font-size: 11px;");
            pathLabel.setWrapText(false);
            
            root.getChildren().addAll(headerBox, pathLabel);
            
            // Handle selection changes
            selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (getItem() != null) {
                    updateStyle(isSelected);
                }
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                // Split filename and path
                String name = item;
                String path = "";
                if (item.contains("/")) {
                    name = item.substring(item.lastIndexOf("/") + 1);
                    path = item.substring(0, item.lastIndexOf("/"));
                }
                
                fileLabel.setText(name);
                pathLabel.setText(path.isEmpty() ? item : path);
                
                updateStyle(isSelected());
                setGraphic(root);
            }
        }
        
        private void updateStyle(boolean isSelected) {
            if (isSelected) {
                root.setStyle("-fx-background-color: " + selectedBgColor + "; -fx-background-radius: 4;");
                fileLabel.setStyle("-fx-text-fill: " + selectedTextColor + "; -fx-font-weight: bold; -fx-font-size: 13px;");
                pathLabel.setStyle("-fx-text-fill: " + secondaryTextColor + "; -fx-font-size: 11px; -fx-opacity: 0.8;");
                iconLabel.setStyle("-fx-text-fill: " + selectedTextColor + ";");
            } else {
                root.setStyle("-fx-background-color: transparent;");
                fileLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-weight: bold; -fx-font-size: 13px;");
                pathLabel.setStyle("-fx-text-fill: " + secondaryTextColor + "; -fx-font-size: 11px;");
                iconLabel.setStyle("-fx-text-fill: " + secondaryTextColor + ";");
            }
        }
    }
}
