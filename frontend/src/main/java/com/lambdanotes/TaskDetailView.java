package com.lambdanotes;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import javafx.scene.control.MenuButton;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.CheckBox;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TaskDetailView extends VBox {
    private final NoteService noteService;
    private final String itemId; // ProjectV2Item ID
    private final String projectId; // Project ID
    private TextField titleField;
    private TextArea bodyArea;
    private Label statusLabel;
    
    private ComboBox<StatusOption> statusCombo;
    private DatePicker datePicker;
    private MenuButton assigneesMenu;
    
    private String statusFieldId;
    private String dateFieldId;
    
    private String contentId;
    private String contentType;
    private boolean isDraft = true;
    private Set<String> selectedAssigneeIds = new HashSet<>();
    private List<User> allUsers = new ArrayList<>();

    public TaskDetailView(NoteService noteService, String itemId, String projectId) {
        this.noteService = noteService;
        this.itemId = itemId;
        this.projectId = projectId;
        
        this.getStyleClass().add("task-detail-view");
        this.setPadding(new Insets(20));
        this.setSpacing(15);
        
        Label header = new Label("Task Details");
        header.getStyleClass().add("settings-header");
        
        statusLabel = new Label("Loading...");
        statusLabel.getStyleClass().add("status-label");
        
        titleField = new TextField();
        titleField.setPromptText("Task Title");
        titleField.getStyleClass().add("title-field");
        
        // Meta Fields
        HBox metaBox = new HBox(20);
        metaBox.setAlignment(Pos.CENTER_LEFT);
        
        statusCombo = new ComboBox<>();
        statusCombo.setPromptText("Status");
        statusCombo.getStyleClass().add("settings-combo-box");
        statusCombo.setConverter(new StringConverter<StatusOption>() {
            @Override
            public String toString(StatusOption object) {
                return object == null ? "" : object.name;
            }
            @Override
            public StatusOption fromString(String string) {
                return null; // Not needed
            }
        });
        
        datePicker = new DatePicker();
        datePicker.setPromptText("Target Date");
        datePicker.getStyleClass().add("task-detail-date-picker");
        
        assigneesMenu = new MenuButton("Select Assignees");
        assigneesMenu.getStyleClass().add("settings-combo-box");
        assigneesMenu.setPrefWidth(200);
        
        metaBox.getChildren().addAll(new Label("Status:"), statusCombo, new Label("Date:"), datePicker, new Label("Assignees:"), assigneesMenu);
        
        bodyArea = new TextArea();
        bodyArea.setPromptText("Description...");
        bodyArea.getStyleClass().add("editor-area");
        VBox.setVgrow(bodyArea, Priority.ALWAYS);
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("dialog-button-ok");
        saveBtn.setOnAction(e -> saveTask());
        
        buttonBox.getChildren().add(saveBtn);
        
        this.getChildren().addAll(header, statusLabel, titleField, metaBox, bodyArea, buttonBox);
        
        initialize();
    }
    
    private void initialize() {
        // 1. Load Config to get Repo info -> Load Users
        noteService.getConfig().thenAccept(config -> {
            if (config != null && config.getRepoUrl() != null) {
                String url = config.getRepoUrl();
                if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);
                String[] parts = url.split("/");
                if (parts.length >= 2) {
                    String owner = parts[parts.length - 2];
                    String name = parts[parts.length - 1];
                    loadAssignableUsers(owner, name);
                }
            }
        });
        
        // 2. Load Project Fields -> Load Task Details
        loadProjectFields();
    }
    
    private static class StatusOption {
        String id;
        String name;
        
        public StatusOption(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
    
    private static class User {
        String id;
        String login;
        String avatarUrl;
        String name;
        
        public User(String id, String login, String avatarUrl, String name) {
            this.id = id;
            this.login = login;
            this.avatarUrl = avatarUrl;
            this.name = name;
        }
    }
    
    private void loadAssignableUsers(String owner, String name) {
        String query = String.format(
            "query { repository(owner: \"%s\", name: \"%s\") { assignableUsers(first: 100) { nodes { id login avatarUrl name } } } }",
            owner, name
        );
        
        noteService.sendGraphQLRequest(query).thenAccept(json -> Platform.runLater(() -> {
            parseAssignableUsers(json);
        }));
    }
    
    private void parseAssignableUsers(String json) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonObject data = root.getAsJsonObject("data");
            if (data != null && data.has("repository")) {
                JsonArray users = data.getAsJsonObject("repository").getAsJsonObject("assignableUsers").getAsJsonArray("nodes");
                allUsers.clear();
                for (JsonElement u : users) {
                    JsonObject obj = u.getAsJsonObject();
                    String id = obj.get("id").getAsString();
                    String login = obj.get("login").getAsString();
                    String avatarUrl = obj.get("avatarUrl").getAsString();
                    String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : login;
                    allUsers.add(new User(id, login, avatarUrl, name));
                }
                updateAssigneesMenu();
            }
        } catch (Exception e) {
            System.err.println("Error parsing users: " + e.getMessage());
        }
    }
    
    private void updateAssigneesMenu() {
        assigneesMenu.getItems().clear();
        List<String> selectedNames = new ArrayList<>();
        
        for (User user : allUsers) {
            CheckBox checkBox = new CheckBox(user.name);
            checkBox.setSelected(selectedAssigneeIds.contains(user.id));
            checkBox.setOnAction(e -> {
                if (checkBox.isSelected()) {
                    selectedAssigneeIds.add(user.id);
                } else {
                    selectedAssigneeIds.remove(user.id);
                }
                updateAssigneesMenuText();
            });
            
            ImageView avatar = new ImageView(new Image(user.avatarUrl, 24, 24, true, true));
            avatar.setFitWidth(24);
            avatar.setFitHeight(24);
            
            HBox itemContent = new HBox(10, avatar, checkBox);
            itemContent.setAlignment(Pos.CENTER_LEFT);
            
            CustomMenuItem item = new CustomMenuItem(itemContent);
            item.setHideOnClick(false);
            assigneesMenu.getItems().add(item);
            
            if (selectedAssigneeIds.contains(user.id)) {
                selectedNames.add(user.login);
            }
        }
        updateAssigneesMenuText();
    }
    
    private void updateAssigneesMenuText() {
        if (selectedAssigneeIds.isEmpty()) {
            assigneesMenu.setText("Select Assignees");
        } else {
            assigneesMenu.setText(selectedAssigneeIds.size() + " selected");
        }
    }

    private void loadProjectFields() {
        String query = String.format(
            "query { node(id: \"%s\") { ... on ProjectV2 { fields(first: 20) { nodes { ... on ProjectV2SingleSelectField { id name options { id name } } ... on ProjectV2Field { id name } } } } } }",
            projectId
        );
        
        noteService.sendGraphQLRequest(query).thenAccept(json -> Platform.runLater(() -> {
            parseProjectFields(json);
            loadTaskDetails();
        })).exceptionally(e -> {
            Platform.runLater(() -> statusLabel.setText("Error loading fields: " + e.getMessage()));
            return null;
        });
    }

    private void parseProjectFields(String json) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonObject data = root.getAsJsonObject("data");
            JsonObject node = data.getAsJsonObject("node");
            JsonArray fields = node.getAsJsonObject("fields").getAsJsonArray("nodes");
            
            for (JsonElement field : fields) {
                JsonObject obj = field.getAsJsonObject();
                String name = obj.get("name").getAsString();
                if ("Status".equalsIgnoreCase(name)) {
                    statusFieldId = obj.get("id").getAsString();
                    if (obj.has("options")) {
                        JsonArray options = obj.getAsJsonArray("options");
                        for (JsonElement opt : options) {
                            JsonObject optObj = opt.getAsJsonObject();
                            statusCombo.getItems().add(new StatusOption(optObj.get("id").getAsString(), optObj.get("name").getAsString()));
                        }
                    }
                } else if ("Date".equalsIgnoreCase(name) || "Target Date".equalsIgnoreCase(name)) {
                    dateFieldId = obj.get("id").getAsString();
                }
            }
        } catch (Exception e) {
            statusLabel.setText("Error parsing fields: " + e.getMessage());
        }
    }

    private void loadTaskDetails() {
        String query = String.format(
            "query { node(id: \"%s\") { ... on ProjectV2Item { fieldValues(first: 10) { nodes { ... on ProjectV2ItemFieldSingleSelectValue { field { ... on ProjectV2FieldCommon { name } } name optionId } ... on ProjectV2ItemFieldDateValue { field { ... on ProjectV2FieldCommon { name } } date } } } content { __typename ... on DraftIssue { id title body assignees(first: 10) { nodes { id } } } ... on Issue { id title body assignees(first: 10) { nodes { id } } } } } } }",
            itemId
        );
        
        noteService.sendGraphQLRequest(query).thenAccept(json -> Platform.runLater(() -> {
            parseAndDisplayTask(json);
        })).exceptionally(e -> {
            Platform.runLater(() -> statusLabel.setText("Error loading task: " + e.getMessage()));
            return null;
        });
    }
    
    private void parseAndDisplayTask(String json) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            
            if (root.has("errors")) {
                statusLabel.setText("Error: " + root.get("errors").toString());
                return;
            }
            
            JsonObject data = root.getAsJsonObject("data");
            JsonObject node = data.getAsJsonObject("node");
            
            if (node != null) {
                // Content (Title, Body, Assignees)
                JsonObject content = node.getAsJsonObject("content");
                if (content != null) {
                    if (content.has("id")) contentId = content.get("id").getAsString();
                    if (content.has("__typename")) contentType = content.get("__typename").getAsString();
                    
                    if (content.has("title")) titleField.setText(content.get("title").getAsString());
                    if (content.has("body")) bodyArea.setText(content.get("body").getAsString());
                    
                    if (content.has("assignees")) {
                        JsonArray assignees = content.getAsJsonObject("assignees").getAsJsonArray("nodes");
                        selectedAssigneeIds.clear();
                        for (JsonElement a : assignees) {
                            selectedAssigneeIds.add(a.getAsJsonObject().get("id").getAsString());
                        }
                        updateAssigneesMenu();
                    }
                }
                
                // Field Values (Status, Date)
                if (node.has("fieldValues")) {
                    JsonArray values = node.getAsJsonObject("fieldValues").getAsJsonArray("nodes");
                    for (JsonElement val : values) {
                        JsonObject obj = val.getAsJsonObject();
                        if (!obj.has("field")) continue;
                        String fieldName = obj.getAsJsonObject("field").get("name").getAsString();
                        
                        if ("Status".equalsIgnoreCase(fieldName) && obj.has("optionId")) {
                            String optId = obj.get("optionId").getAsString();
                            for (StatusOption opt : statusCombo.getItems()) {
                                if (opt.id.equals(optId)) {
                                    statusCombo.setValue(opt);
                                    break;
                                }
                            }
                        } else if (("Date".equalsIgnoreCase(fieldName) || "Target Date".equalsIgnoreCase(fieldName)) && obj.has("date")) {
                            String dateStr = obj.get("date").getAsString();
                            try {
                                datePicker.setValue(LocalDate.parse(dateStr));
                            } catch (Exception e) {}
                        }
                    }
                }
                statusLabel.setText("Loaded.");
            } else {
                statusLabel.setText("Task not found.");
            }
            
        } catch (Exception e) {
            statusLabel.setText("Error parsing: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void saveTask() {
        statusLabel.setText("Saving...");
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // 1. Update Status
        if (statusFieldId != null && statusCombo.getValue() != null) {
            String query = String.format(
                "mutation { updateProjectV2ItemFieldValue(input: {projectId: \"%s\", itemId: \"%s\", fieldId: \"%s\", value: {singleSelectOptionId: \"%s\"}}) { projectV2Item { id } } }",
                projectId, itemId, statusFieldId, statusCombo.getValue().id
            );
            futures.add(noteService.sendGraphQLRequest(query).thenAccept(r -> {}));
        }
        
        // 2. Update Date
        if (dateFieldId != null && datePicker.getValue() != null) {
            String query = String.format(
                "mutation { updateProjectV2ItemFieldValue(input: {projectId: \"%s\", itemId: \"%s\", fieldId: \"%s\", value: {date: \"%s\"}}) { projectV2Item { id } } }",
                projectId, itemId, dateFieldId, datePicker.getValue().toString()
            );
            futures.add(noteService.sendGraphQLRequest(query).thenAccept(r -> {}));
        }
        
        // 3. Update Content (Title, Body, Assignees)
        if (contentId != null) {
            String assigneeIdsStrTemp = "[";
            for (String id : selectedAssigneeIds) {
                assigneeIdsStrTemp += "\"" + id + "\",";
            }
            if (assigneeIdsStrTemp.length() > 1) assigneeIdsStrTemp = assigneeIdsStrTemp.substring(0, assigneeIdsStrTemp.length() - 1);
            assigneeIdsStrTemp += "]";
            
            final String assigneeIdsStr = assigneeIdsStrTemp;
            
            String title = titleField.getText().replace("\"", "\\\"");
            String body = bodyArea.getText().replace("\n", "\\n").replace("\"", "\\\"");
            
            String mutation = null;
            if ("Issue".equals(contentType)) {
                mutation = String.format(
                    "mutation { updateIssue(input: {id: \"%s\", title: \"%s\", body: \"%s\", assigneeIds: %s}) { issue { id } } }",
                    contentId, title, body, assigneeIdsStr
                );
            } else if ("DraftIssue".equals(contentType)) {
                mutation = String.format(
                    "mutation { updateProjectV2DraftIssue(input: {draftIssueId: \"%s\", title: \"%s\", body: \"%s\", assigneeIds: %s}) { draftIssue { id } } }",
                    contentId, title, body, assigneeIdsStr
                );
            }
            
            if (mutation != null) {
                futures.add(noteService.sendGraphQLRequest(mutation).thenAccept(r -> {}));
            }
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> Platform.runLater(() -> statusLabel.setText("All changes saved.")))
            .exceptionally(e -> {
                Platform.runLater(() -> statusLabel.setText("Error saving: " + e.getMessage()));
                return null;
            });
    }
}
