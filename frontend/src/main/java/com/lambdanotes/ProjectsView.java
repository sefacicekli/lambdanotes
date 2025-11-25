package com.lambdanotes;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.logging.Logger;
import java.util.function.BiConsumer;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class ProjectsView extends VBox {
    private static final Logger logger = Logger.getLogger(ProjectsView.class.getName());
    private final NoteService noteService;
    private final AppConfig config;
    private VBox contentArea;
    private Label statusLabel;
    private BiConsumer<String, String> onTaskSelect;
    
    // Views
    private ListView<ProjectTask> taskList;
    private ScrollPane kanbanScroll;
    private HBox kanbanBoard;
    
    // State
    private String currentProjectId;
    private TextField searchField;
    private List<ProjectTask> allTasks = new ArrayList<>();
    private List<String> statusOptions = new ArrayList<>();
    private String statusFieldId;
    private boolean isKanbanMode = false;

    public ProjectsView(NoteService noteService, AppConfig config, BiConsumer<String, String> onTaskSelect) {
        this.noteService = noteService;
        this.config = config;
        this.onTaskSelect = onTaskSelect;
        
        this.getStyleClass().add("projects-view");
        this.setPadding(new Insets(20));
        this.setSpacing(20);
        
        // Header Row
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label header = new Label("GitHub Projects");
        header.getStyleClass().add("settings-header");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // View Toggles
        HBox viewToggles = new HBox(0);
        viewToggles.getStyleClass().add("mode-switcher");
        
        ToggleButton btnList = new ToggleButton("List");
        btnList.getStyleClass().add("mode-button");
        btnList.getStyleClass().add("mode-button-left");
        btnList.setSelected(true);
        
        ToggleButton btnKanban = new ToggleButton("Kanban");
        btnKanban.getStyleClass().add("mode-button");
        btnKanban.getStyleClass().add("mode-button-right");
        
        ToggleGroup group = new ToggleGroup();
        btnList.setToggleGroup(group);
        btnKanban.setToggleGroup(group);
        
        btnList.setOnAction(e -> {
            if (btnList.isSelected()) {
                isKanbanMode = false;
                refreshView();
            } else {
                btnList.setSelected(true); // Prevent unselecting
            }
        });
        
        btnKanban.setOnAction(e -> {
            if (btnKanban.isSelected()) {
                isKanbanMode = true;
                refreshView();
            } else {
                btnKanban.setSelected(true);
            }
        });
        
        viewToggles.getChildren().addAll(btnList, btnKanban);
        headerBox.getChildren().addAll(header, spacer, viewToggles);
        
        statusLabel = new Label("Loading...");
        statusLabel.getStyleClass().add("status-label");
        
        searchField = new TextField();
        searchField.setPromptText("Search tasks...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterTasks(newVal));
        
        contentArea = new VBox(10);
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        
        this.getChildren().addAll(headerBox, statusLabel, searchField, contentArea);
        
        loadProject();
    }

    private void filterTasks(String query) {
        refreshView();
    }
    
    private void refreshView() {
        contentArea.getChildren().clear();
        
        List<ProjectTask> filtered = allTasks;
        String query = searchField.getText();
        if (query != null && !query.isEmpty()) {
            String lower = query.toLowerCase();
            filtered = allTasks.stream()
                .filter(t -> t.title.toLowerCase().contains(lower) || t.status.toLowerCase().contains(lower))
                .collect(Collectors.toList());
        }
        
        if (isKanbanMode) {
            renderKanban(filtered);
        } else {
            renderList(filtered);
        }
        
        // Add Task Input (Common)
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(10, 0, 0, 0));
        
        TextField newTaskField = new TextField();
        newTaskField.setPromptText("New task title...");
        newTaskField.getStyleClass().add("dialog-input");
        HBox.setHgrow(newTaskField, Priority.ALWAYS);
        
        Button addBtn = new Button("Add Task");
        addBtn.getStyleClass().add("dialog-button-ok");
        addBtn.setOnAction(e -> {
            if (!newTaskField.getText().isEmpty()) {
                addTask(currentProjectId, newTaskField.getText());
                newTaskField.clear();
            }
        });
        
        inputBox.getChildren().addAll(newTaskField, addBtn);
        contentArea.getChildren().add(inputBox);
    }
    
    private void renderList(List<ProjectTask> tasks) {
        taskList = new ListView<>();
        taskList.getStyleClass().add("note-tree-view");
        taskList.setCellFactory(param -> new ListCell<ProjectTask>() {
            @Override
            protected void updateItem(ProjectTask item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setContextMenu(null);
                } else {
                    setText(item.title + (item.status.isEmpty() ? "" : " [" + item.status + "]"));
                    
                    ContextMenu contextMenu = new ContextMenu();
                    MenuItem deleteItem = new MenuItem("Delete Task");
                    deleteItem.setOnAction(e -> deleteTask(item));
                    contextMenu.getItems().add(deleteItem);
                    setContextMenu(contextMenu);
                }
            }
        });
        
        taskList.setOnMouseClicked(e -> {
            ProjectTask selected = taskList.getSelectionModel().getSelectedItem();
            if (selected != null && onTaskSelect != null) {
                onTaskSelect.accept(selected.itemId, currentProjectId);
            }
        });
        
        taskList.getItems().addAll(tasks);
        VBox.setVgrow(taskList, Priority.ALWAYS);
        contentArea.getChildren().add(0, taskList);
    }
    
    private void renderKanban(List<ProjectTask> tasks) {
        kanbanScroll = new ScrollPane();
        kanbanScroll.setFitToHeight(true);
        kanbanScroll.setFitToWidth(true);
        kanbanScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        
        kanbanBoard = new HBox(15);
        kanbanBoard.setAlignment(Pos.TOP_LEFT);
        kanbanBoard.setPadding(new Insets(10));
        
        // Group tasks by status
        Map<String, List<ProjectTask>> grouped = new HashMap<>();
        // Initialize with known options
        for (String status : statusOptions) {
            grouped.put(status, new ArrayList<>());
        }
        // Add "No Status" column
        grouped.put("No Status", new ArrayList<>());
        
        for (ProjectTask task : tasks) {
            String s = task.status.isEmpty() ? "No Status" : task.status;
            grouped.computeIfAbsent(s, k -> new ArrayList<>()).add(task);
        }
        
        // Create columns
        // Order: "No Status" first, then statusOptions in order
        createColumn("No Status", grouped.get("No Status"));
        
        for (String status : statusOptions) {
            createColumn(status, grouped.get(status));
        }
        
        kanbanScroll.setContent(kanbanBoard);
        VBox.setVgrow(kanbanScroll, Priority.ALWAYS);
        contentArea.getChildren().add(0, kanbanScroll);
    }
    
    private void createColumn(String title, List<ProjectTask> tasks) {
        VBox column = new VBox(10);
        column.setPrefWidth(250);
        column.setMinWidth(250);
        column.setMaxWidth(250);
        column.getStyleClass().add("kanban-column");
        column.setPadding(new Insets(10));
        
        Label header = new Label(title + " (" + (tasks == null ? 0 : tasks.size()) + ")");
        header.getStyleClass().add("kanban-header");
        
        VBox taskListContainer = new VBox(8);
        taskListContainer.setStyle("-fx-min-height: 100px;"); // Drop target area
        
        if (tasks != null) {
            for (ProjectTask task : tasks) {
                VBox card = new VBox(5);
                card.getStyleClass().add("kanban-card");
                card.setPadding(new Insets(10));
                
                Label cardTitle = new Label(task.title);
                cardTitle.setWrapText(true);
                cardTitle.setStyle("-fx-text-fill: #dfe1e5; -fx-font-weight: bold;");
                
                card.getChildren().add(cardTitle);
                
                card.setOnMouseClicked(e -> {
                    if (onTaskSelect != null) onTaskSelect.accept(task.itemId, currentProjectId);
                });
                
                // Drag Source
                card.setOnDragDetected(event -> {
                    Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(task.itemId);
                    db.setContent(content);
                    event.consume();
                });
                
                taskListContainer.getChildren().add(card);
            }
        }
        
        // Drop Target
        column.setOnDragOver(event -> {
            if (event.getGestureSource() != column && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        
        column.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String itemId = db.getString();
                updateTaskStatus(itemId, title);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
        
        column.getChildren().addAll(header, taskListContainer);
        kanbanBoard.getChildren().add(column);
    }
    


    public void refresh() {
        loadProject();
    }
    
    private void loadProject() {
        if (config == null || config.getRepoUrl() == null || config.getRepoUrl().isEmpty()) {
            statusLabel.setText("No repository configured.");
            return;
        }
        
        String url = config.getRepoUrl();
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);
        String[] parts = url.split("/");
        if (parts.length < 2) {
            statusLabel.setText("Invalid repository URL.");
            return;
        }
        String name = parts[parts.length - 1];
        String owner = parts[parts.length - 2];
        
        // Updated query to fetch Fields and Item Field Values
        String query = String.format(
            "query { repository(owner: \"%s\", name: \"%s\") { id owner { id } projectsV2(first: 1) { nodes { id title fields(first: 20) { nodes { ... on ProjectV2SingleSelectField { id name options { id name } } ... on ProjectV2Field { id name } } } items(first: 100) { nodes { id fieldValues(first: 10) { nodes { ... on ProjectV2ItemFieldSingleSelectValue { name field { ... on ProjectV2FieldCommon { name } } } } } content { ... on DraftIssue { id title body } ... on Issue { id title body state } ... on PullRequest { title state } } } } } } } }",
            owner, name
        );
        
        noteService.sendGraphQLRequest(query).thenAccept(json -> Platform.runLater(() -> {
            parseAndDisplayProject(json, owner, name);
        })).exceptionally(e -> {
            Platform.runLater(() -> statusLabel.setText("Error loading project: " + e.getMessage()));
            return null;
        });
    }
    
    // Map to store Option Name -> Option ID
    private Map<String, String> statusOptionIds = new HashMap<>();
    
    private void parseAndDisplayProject(String json, String owner, String repoName) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            
            if (root.has("errors")) {
                statusLabel.setText("Error from GitHub: " + root.get("errors").toString());
                return;
            }
            
            JsonObject data = root.getAsJsonObject("data");
            if (data == null || !data.has("repository")) {
                statusLabel.setText("No data received.");
                return;
            }
            
            JsonObject repository = data.getAsJsonObject("repository");
            String repoId = repository.get("id").getAsString();
            String ownerId = repository.getAsJsonObject("owner").get("id").getAsString();
            
            JsonArray projects = repository.getAsJsonObject("projectsV2").getAsJsonArray("nodes");
            
            if (projects.size() == 0) {
                contentArea.getChildren().clear();
                statusLabel.setText("No project found.");
                Button createBtn = new Button("Create Project");
                createBtn.getStyleClass().add("dialog-button-ok");
                createBtn.setOnAction(e -> createProject(ownerId, repoId, repoName));
                contentArea.getChildren().add(createBtn);
                return;
            }
            
            JsonObject project = projects.get(0).getAsJsonObject();
            this.currentProjectId = project.get("id").getAsString();
            String projectTitle = project.get("title").getAsString();
            statusLabel.setText("Project: " + projectTitle);
            
            // Parse Fields to get Status Options
            statusOptions.clear();
            statusOptionIds.clear();
            JsonArray fields = project.getAsJsonObject("fields").getAsJsonArray("nodes");
            for (JsonElement f : fields) {
                JsonObject field = f.getAsJsonObject();
                String fieldName = field.get("name").getAsString();
                if ("Status".equalsIgnoreCase(fieldName)) {
                    statusFieldId = field.get("id").getAsString();
                    if (field.has("options")) {
                        JsonArray opts = field.getAsJsonArray("options");
                        for (JsonElement o : opts) {
                            JsonObject opt = o.getAsJsonObject();
                            String optName = opt.get("name").getAsString();
                            String optId = opt.get("id").getAsString();
                            statusOptions.add(optName);
                            statusOptionIds.put(optName, optId);
                        }
                    }
                }
            }
            
            // Parse Items
            allTasks.clear();
            JsonArray items = project.getAsJsonObject("items").getAsJsonArray("nodes");
            for (JsonElement item : items) {
                if (!item.isJsonObject()) continue;
                JsonObject itemObj = item.getAsJsonObject();
                String itemId = itemObj.get("id").getAsString();
                
                String status = "";
                // Check field values for Status
                if (itemObj.has("fieldValues")) {
                    JsonArray values = itemObj.getAsJsonObject("fieldValues").getAsJsonArray("nodes");
                    for (JsonElement v : values) {
                        JsonObject val = v.getAsJsonObject();
                        if (val.has("field") && val.getAsJsonObject("field").get("name").getAsString().equalsIgnoreCase("Status")) {
                            status = val.get("name").getAsString();
                            break;
                        }
                    }
                }
                
                JsonObject content = itemObj.getAsJsonObject("content");
                if (content != null && content.has("title")) {
                    String title = content.get("title").getAsString();
                    String contentId = "";
                    if (content.has("id")) contentId = content.get("id").getAsString();
                    
                    allTasks.add(new ProjectTask(itemId, contentId, title, status));
                }
            }
            
            refreshView();
            
        } catch (Exception e) {
            statusLabel.setText("Error parsing response: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Re-implement updateTaskStatus with ID lookup
    private void updateTaskStatus(String itemId, String newStatus) {
        ProjectTask task = allTasks.stream().filter(t -> t.itemId.equals(itemId)).findFirst().orElse(null);
        if (task == null) return;
        
        if (task.status.equals(newStatus)) return;
        
        String oldStatus = task.status;
        task.status = newStatus;
        refreshView(); // Optimistic
        
        if ("No Status".equals(newStatus)) {
            // Clear status - Mutation to clear field? Not implemented in this simple version
            // Just return for now or implement clear
            return; 
        }
        
        String optionId = statusOptionIds.get(newStatus);
        if (optionId == null || statusFieldId == null) {
            statusLabel.setText("Cannot update status: Missing ID");
            task.status = oldStatus;
            refreshView();
            return;
        }
        
        String query = String.format(
            "mutation { updateProjectV2ItemFieldValue(input: {projectId: \"%s\", itemId: \"%s\", fieldId: \"%s\", value: {singleSelectOptionId: \"%s\"}}) { projectV2Item { id } } }",
            currentProjectId, itemId, statusFieldId, optionId
        );
        
        noteService.sendGraphQLRequest(query).thenAccept(json -> {
            // Success
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Error updating status: " + e.getMessage());
                task.status = oldStatus;
                refreshView();
            });
            return null;
        });
    }
    
    private void createProject(String ownerId, String repoId, String repoName) {
        statusLabel.setText("Creating project...");
        String query = String.format(
            "mutation { createProjectV2(input: {ownerId: \"%s\", title: \"%s\", repositoryId: \"%s\"}) { projectV2 { id } } }",
            ownerId, "Project for " + repoName, repoId
        );
        
        noteService.sendGraphQLRequest(query).thenAccept(json -> Platform.runLater(() -> {
            loadProject();
        })).exceptionally(e -> {
            Platform.runLater(() -> statusLabel.setText("Error creating project: " + e.getMessage()));
            return null;
        });
    }
    
    private void addTask(String projectId, String title) {
        ProjectTask tempTask = new ProjectTask("temp", "temp", title, "");
        allTasks.add(tempTask);
        refreshView();

        String query = String.format(
            "mutation { addProjectV2DraftIssue(input: {projectId: \"%s\", title: \"%s\"}) { projectItem { id content { ... on DraftIssue { id } } } } }",
            projectId, title
        );
        
        noteService.sendGraphQLRequest(query).thenAccept(json -> Platform.runLater(() -> {
            try {
                Gson gson = new Gson();
                JsonObject root = gson.fromJson(json, JsonObject.class);
                JsonObject data = root.getAsJsonObject("data");
                JsonObject addResult = data.getAsJsonObject("addProjectV2DraftIssue");
                JsonObject item = addResult.getAsJsonObject("projectItem");
                String itemId = item.get("id").getAsString();
                JsonObject content = item.getAsJsonObject("content");
                String contentId = content.get("id").getAsString();
                
                allTasks.remove(tempTask);
                allTasks.add(new ProjectTask(itemId, contentId, title, ""));
                refreshView();
            } catch (Exception e) {
                statusLabel.setText("Error parsing add response: " + e.getMessage());
            }
        })).exceptionally(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Error adding task: " + e.getMessage());
                allTasks.remove(tempTask);
                refreshView();
            });
            return null;
        });
    }

    private void deleteTask(ProjectTask task) {
        if (task == null) return;
        allTasks.remove(task);
        refreshView();
        
        String query = String.format(
            "mutation { deleteProjectV2Item(input: {projectId: \"%s\", itemId: \"%s\"}) { deletedItemId } }",
            currentProjectId, task.itemId
        );
        
        noteService.sendGraphQLRequest(query).thenAccept(json -> Platform.runLater(() -> {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root.has("errors")) {
                statusLabel.setText("Error deleting task: " + root.get("errors").toString());
                allTasks.add(task);
                refreshView();
            }
        })).exceptionally(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Error deleting task: " + e.getMessage());
                allTasks.add(task);
                refreshView();
            });
            return null;
        });
    }
    
    private static class ProjectTask {
        String itemId;
        String contentId;
        String title;
        String status;
        
        public ProjectTask(String itemId, String contentId, String title, String status) {
            this.itemId = itemId;
            this.contentId = contentId;
            this.title = title;
            this.status = status;
        }
    }
}
