package com.lambdanotes;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class GitHistoryView extends BorderPane {

    private final NoteService noteService;
    private final TableView<NoteService.GitLogEntry> table;
    private final Label statusLabel;
    private Consumer<String> onCommitSelected;

    public GitHistoryView(NoteService noteService, Consumer<String> onCommitSelected) {
        this.noteService = noteService;
        this.onCommitSelected = onCommitSelected;
        this.getStyleClass().add("git-history-view");

        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10));
        header.getStyleClass().add("sidebar-header");

        Label title = new Label("Git History");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #abb2bf;");

        Button refreshBtn = new Button("🔄");
        refreshBtn.getStyleClass().add("sidebar-action-button");
        refreshBtn.setOnAction(e -> refresh());

        header.getChildren().addAll(title, new javafx.scene.layout.Region(), refreshBtn);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);

        setTop(header);

        // Table
        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<NoteService.GitLogEntry, String> graphCol = new TableColumn<>("Graph");
        graphCol.setPrefWidth(50);
        graphCol.setMinWidth(50);
        graphCol.setMaxWidth(50);
        // We will implement a simple visual indicator for now, full graph is complex
        graphCol.setCellFactory(col -> new TableCell<>() {
            private final Canvas canvas = new Canvas(40, 20);
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    GraphicsContext gc = canvas.getGraphicsContext2D();
                    gc.clearRect(0, 0, 40, 20);
                    gc.setFill(Color.web("#61afef"));
                    gc.fillOval(15, 5, 10, 10);
                    // Draw line to next if not last (simplified)
                    if (getIndex() < getTableView().getItems().size() - 1) {
                        gc.setStroke(Color.web("#4b5263"));
                        gc.setLineWidth(2);
                        gc.strokeLine(20, 10, 20, 30);
                    }
                    setGraphic(canvas);
                }
            }
        });

        TableColumn<NoteService.GitLogEntry, String> messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().message));
        
        TableColumn<NoteService.GitLogEntry, String> authorCol = new TableColumn<>("Author");
        authorCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().author));
        authorCol.setPrefWidth(100);
        authorCol.setMaxWidth(150);

        TableColumn<NoteService.GitLogEntry, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(data -> {
            try {
                // Parse ISO date
                String iso = data.getValue().date;
                LocalDateTime dt = LocalDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                return new SimpleStringProperty(dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            } catch (Exception e) {
                return new SimpleStringProperty(data.getValue().date);
            }
        });
        dateCol.setPrefWidth(120);
        dateCol.setMaxWidth(150);

        table.getColumns().addAll(graphCol, messageCol, authorCol, dateCol);
        
        // Style the table to look like VS Code list
        table.getStyleClass().add("git-log-table");

        // Selection Listener
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onCommitSelected != null) {
                onCommitSelected.accept(newVal.hash);
            }
        });

        setCenter(table);

        statusLabel = new Label("");
        statusLabel.setPadding(new Insets(5));
        setBottom(statusLabel);

        refresh();
    }

    public void refresh() {
        statusLabel.setText("Loading...");
        noteService.getGitLog().thenAccept(entries -> Platform.runLater(() -> {
            table.getItems().setAll(entries);
            statusLabel.setText(entries.size() + " commits");
        })).exceptionally(e -> {
            Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage()));
            return null;
        });
    }
}
