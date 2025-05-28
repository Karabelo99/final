module com.example.simplelms {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;


    opens com.example.simplelms to javafx.fxml;
    exports com.example.simplelms;
}