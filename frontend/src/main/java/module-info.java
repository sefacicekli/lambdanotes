module com.lambdanotes {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.desktop;
    requires java.net.http;
    requires java.logging;
    requires com.google.gson;
    requires flexmark;
    requires flexmark.util.ast;
    requires flexmark.util.builder;
    requires flexmark.util.data;
    requires flexmark.util.misc;
    requires flexmark.util.sequence;
    requires flexmark.util.visitor;
    requires atlantafx.base;

    opens com.lambdanotes to javafx.fxml, com.google.gson;
    exports com.lambdanotes;
}
