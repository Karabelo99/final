module com.example.simplelms {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;
    requires org.postgresql.jdbc;
    requires kernel;
    requires layout;


    opens com.example.simplelms to javafx.fxml;
    exports com.example.simplelms;
}