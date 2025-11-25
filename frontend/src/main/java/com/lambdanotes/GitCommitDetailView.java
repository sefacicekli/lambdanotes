package com.lambdanotes;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.scene.paint.Color;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GitCommitDetailView extends BorderPane {

    private final NoteService noteService;
    private final String commitHash;
    private final ListView<NoteService.GitFileChange> fileList;
    private final WebView diffView;
    private final Label messageLabel;
    private final Label authorLabel;
    private final Label dateLabel;

    public GitCommitDetailView(NoteService noteService, String commitHash) {
        this.noteService = noteService;
        this.commitHash = commitHash;
        this.getStyleClass().add("commit-detail-view");

        // Header
        VBox header = new VBox(5);
        header.setPadding(new Insets(15));
        header.getStyleClass().add("commit-header");

        messageLabel = new Label("Loading...");
        messageLabel.getStyleClass().add("commit-message-large");
        messageLabel.setWrapText(true);

        HBox metaBox = new HBox(15);
        authorLabel = new Label("");
        authorLabel.getStyleClass().add("commit-meta");
        dateLabel = new Label("");
        dateLabel.getStyleClass().add("commit-meta");
        
        Label hashLabel = new Label(commitHash.substring(0, 7));
        hashLabel.getStyleClass().add("commit-hash-badge");

        metaBox.getChildren().addAll(hashLabel, authorLabel, dateLabel);
        header.getChildren().addAll(messageLabel, metaBox);

        setTop(header);

        // Content SplitPane
        SplitPane contentSplit = new SplitPane();
        contentSplit.setOrientation(Orientation.HORIZONTAL);
        contentSplit.getStyleClass().add("main-split-pane");

        // File List
        VBox fileBox = new VBox();
        fileBox.setMinWidth(200);
        Label filesTitle = new Label("Changed Files");
        filesTitle.getStyleClass().add("section-title");
        filesTitle.setPadding(new Insets(10));
        
        fileList = new ListView<>();
        fileList.getStyleClass().add("file-list");
        fileList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(NoteService.GitFileChange item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.path);
                    Label statusBadge = new Label(item.status);
                    statusBadge.getStyleClass().add("status-badge");
                    if (item.status.startsWith("M")) statusBadge.getStyleClass().add("status-modified");
                    else if (item.status.startsWith("A")) statusBadge.getStyleClass().add("status-added");
                    else if (item.status.startsWith("D")) statusBadge.getStyleClass().add("status-deleted");
                    
                    HBox graphic = new HBox(10, statusBadge, new Label(item.path));
                    graphic.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(graphic);
                    setText(null);
                }
            }
        });
        
        fileList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadDiff(newVal.path);
            }
        });

        fileBox.getChildren().addAll(filesTitle, fileList);
        VBox.setVgrow(fileList, Priority.ALWAYS);

        // Diff View
        diffView = new WebView();
        diffView.setContextMenuEnabled(false);
        
        contentSplit.getItems().addAll(fileBox, diffView);
        contentSplit.setDividerPositions(0.3);

        setCenter(contentSplit);

        loadDetails();
    }

    private void loadDetails() {
        noteService.getCommitDetail(commitHash).thenAccept(detail -> Platform.runLater(() -> {
            messageLabel.setText(detail.message);
            authorLabel.setText(detail.author);
            try {
                LocalDateTime dt = LocalDateTime.parse(detail.date, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                dateLabel.setText(dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            } catch (Exception e) {
                dateLabel.setText(detail.date);
            }
            
            fileList.getItems().setAll(detail.files);
            if (!detail.files.isEmpty()) {
                fileList.getSelectionModel().selectFirst();
            }
        })).exceptionally(e -> {
            Platform.runLater(() -> messageLabel.setText("Error: " + e.getMessage()));
            return null;
        });
    }

    private void loadDiff(String path) {
        noteService.getDiff(commitHash, path).thenAccept(diff -> Platform.runLater(() -> {
            String html = generateDiffHtml(diff);
            diffView.getEngine().loadContent(html);
        })).exceptionally(e -> {
            Platform.runLater(() -> diffView.getEngine().loadContent("<html><body>Error loading diff: " + e.getMessage() + "</body></html>"));
            return null;
        });
    }

    private String generateDiffHtml(String diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>");
        sb.append("body { font-family: 'JetBrains Mono', monospace; font-size: 13px; background-color: #1e1f22; color: #abb2bf; margin: 0; padding: 10px; }");
        sb.append("pre { margin: 0; white-space: pre-wrap; }");
        sb.append(".line { display: block; width: 100%; }");
        sb.append(".added { background-color: rgba(40, 167, 69, 0.2); color: #e6ffed; }");
        sb.append(".deleted { background-color: rgba(203, 36, 49, 0.2); color: #ffeef0; }");
        sb.append(".header { color: #61afef; font-weight: bold; }");
        sb.append("</style></head><body><pre>");

        // Basic Diff Parsing
        String[] lines = diff.split("\n");
        for (String line : lines) {
            // Escape HTML
            String escaped = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            
            if (line.startsWith("+") && !line.startsWith("+++")) {
                sb.append("<span class='line added'>").append(escaped).append("</span>");
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                sb.append("<span class='line deleted'>").append(escaped).append("</span>");
            } else if (line.startsWith("diff") || line.startsWith("index") || line.startsWith("---") || line.startsWith("+++")) {
                sb.append("<span class='line header'>").append(escaped).append("</span>");
            } else {
                sb.append("<span class='line'>").append(escaped).append("</span>");
            }
            sb.append("\n");
        }

        sb.append("</pre></body></html>");
        return sb.toString();
    }
}
