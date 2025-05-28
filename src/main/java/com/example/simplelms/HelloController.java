package com.example.simplelms;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.awt.Desktop;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.util.Duration;
import javafx.util.StringConverter;

public class HelloController extends Application {
    // UI Components
    private BorderPane root;
    private MenuBar menuBar;
    private TextField usernameField = new TextField();
    private PasswordField passwordField = new PasswordField();
    private Button loginButton = new Button("Login");
    private Label errorLabel = new Label();
    private VBox loginContainer = new VBox(20);
    private StackPane mainContainer = new StackPane();
    private VBox studentDashboard = new VBox();
    private Label welcomeLabel = new Label();
    private VBox lecturerDashboard = new VBox();
    private Label lecturerWelcomeLabel = new Label();
    private VBox lecturerCourseManagementView;
    private TextField newCourseCodeField;
    private TextField newCourseNameField;
    private TextField newCourseTeacherField;
    private TabPane lecturerTabPane;

    // Data
    private int userId;
    private String fullName;
    private String role;
    private ObservableList<Announcement> announcements = FXCollections.observableArrayList();
    private ObservableList<Assignment> assignments = FXCollections.observableArrayList();
    private ObservableList<Course> courses = FXCollections.observableArrayList();
    private ObservableList<Course> lecturerCourses = FXCollections.observableArrayList();

    // Database configuration
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/Learning_management";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "karabelo";

    @Override
    public void start(Stage primaryStage) {
        initializeDatabase();
        setupMenuBar();
        setupLoginUI();
        initializeLecturerAccounts();

        root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(loginContainer);

        // Check for new results every 30 seconds
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> checkForPublishedResults()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        // Check for new content every 5 minutes
        setupContentChecker();

        Scene scene = new Scene(root, 1200, 800);
        addGlobalStyles(scene);
        primaryStage.setTitle("Learning Management System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initializeDatabase() {
        try (Connection conn = getConnection()) {
            // Create tables if they don't exist
            Statement stmt = conn.createStatement();

            // Courses table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS courses (" +
                            "course_code VARCHAR(20) PRIMARY KEY, " +
                            "course_name VARCHAR(100) NOT NULL, " +
                            "teacher VARCHAR(100) NOT NULL, " +
                            "progress INTEGER DEFAULT 0)"
            );

            // Enrollments table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS enrollments (" +
                            "id SERIAL PRIMARY KEY, " +
                            "student_id INTEGER NOT NULL, " +
                            "course_code VARCHAR(20) NOT NULL, " +
                            "enrollment_date DATE NOT NULL, " +
                            "FOREIGN KEY (course_code) REFERENCES courses(course_code), " +
                            "UNIQUE (student_id, course_code))"
            );

            // Assignments table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS assignments (" +
                            "id SERIAL PRIMARY KEY, " +
                            "title VARCHAR(100) NOT NULL, " +
                            "description TEXT, " +
                            "instructions TEXT, " +
                            "due_date DATE NOT NULL, " +
                            "course_code VARCHAR(20) NOT NULL, " +
                            "max_points INTEGER DEFAULT 100, " +
                            "grading_criteria TEXT, " +
                            "FOREIGN KEY (course_code) REFERENCES courses(course_code))"
            );

            // Course materials table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS course_materials (" +
                            "id SERIAL PRIMARY KEY, " +
                            "title VARCHAR(100) NOT NULL, " +
                            "file_path TEXT NOT NULL, " +
                            "course_code VARCHAR(20) NOT NULL, " +
                            "upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (course_code) REFERENCES courses(course_code))"
            );

            // Announcements table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS announcements (" +
                            "id SERIAL PRIMARY KEY, " +
                            "title VARCHAR(100) NOT NULL, " +
                            "content TEXT NOT NULL, " +
                            "date DATE NOT NULL, " +
                            "assignment_id INTEGER, " +
                            "course_code VARCHAR(20), " +
                            "FOREIGN KEY (assignment_id) REFERENCES assignments(id), " +
                            "FOREIGN KEY (course_code) REFERENCES courses(course_code))"
            );

            // Submissions table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS submissions (" +
                            "id SERIAL PRIMARY KEY, " +
                            "assignment_id INTEGER NOT NULL, " +
                            "student_id INTEGER NOT NULL, " +
                            "file_path TEXT NOT NULL, " +
                            "submission_date TIMESTAMP NOT NULL, " +
                            "grade INTEGER, " +
                            "feedback TEXT, " +
                            "published BOOLEAN DEFAULT FALSE, " +
                            "publish_date TIMESTAMP, " +
                            "last_notified TIMESTAMP, " +
                            "FOREIGN KEY (assignment_id) REFERENCES assignments(id), " +
                            "UNIQUE (assignment_id, student_id))"
            );

            // Student notifications table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS student_notifications (" +
                            "student_id INTEGER PRIMARY KEY, " +
                            "last_checked TIMESTAMP NOT NULL)"
            );

            // Add some default courses if they don't exist
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM courses");
            rs.next();
            if (rs.getInt(1) == 0) {
                String[][] defaultCourses = {
                        {"CS101", "Introduction to Computer Science", "Dr. Motletle"},
                        {"MATH201", "Advanced Mathematics", "Prof. Kiddah"},
                        {"ENG102", "English Composition", "Dr. Motletle"}
                };

                for (String[] course : defaultCourses) {
                    stmt.executeUpdate(
                            "INSERT INTO courses (course_code, course_name, teacher) VALUES " +
                                    "('" + course[0] + "', '" + course[1] + "', '" + course[2] + "')"
                    );
                }
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to initialize database: " + e.getMessage());
        }
    }

    private void addGlobalStyles(Scene scene) {
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
    }

    private void initializeLecturerAccounts() {
        try (Connection conn = getConnection()) {
            String[][] lecturers = {
                    {"Dr. Motletle", "motletle@university.edu", "kmotletle", "prof123"},
                    {"Prof. Kiddah", "kiddah@university.edu", "rkiddah", "teach400"},
                    {"Ratsebe", "ratsebe@university.edu", "ratsebe", "ratsebek"}
            };

            for (String[] lecturer : lecturers) {
                String checkQuery = "SELECT id FROM users WHERE username = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                    checkStmt.setString(1, lecturer[2]);
                    ResultSet rs = checkStmt.executeQuery();
                    if (!rs.next()) {
                        String insertQuery = "INSERT INTO users (full_name, email, username, password, role) " +
                                "VALUES (?, ?, ?, ?, 'lecturer')";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                            insertStmt.setString(1, lecturer[0]);
                            insertStmt.setString(2, lecturer[1]);
                            insertStmt.setString(3, lecturer[2]);
                            insertStmt.setString(4, lecturer[3]);
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to initialize lecturer accounts: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private void setupMenuBar() {
        Menu fileMenu = new Menu("File");
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> System.exit(0));
        fileMenu.getItems().add(exitItem);

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(aboutItem);

        menuBar = new MenuBar();
        menuBar.getMenus().addAll(fileMenu, helpMenu);
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About LMS");
        alert.setHeaderText("Learning Management System");
        alert.setContentText("Version 2.0\nDeveloped for Educational Institutions");
        alert.showAndWait();
    }
    private void setupLoginUI() {
        Text title = new Text("Learning Management System");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-fill: #2a60b9;");

        GridPane loginForm = new GridPane();
        loginForm.setAlignment(Pos.CENTER);
        loginForm.setHgap(15);
        loginForm.setVgap(15);
        loginForm.setPadding(new Insets(25));

        Label usernameLabel = new Label("Username:");
        usernameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2a60b9;");

        Label passwordLabel = new Label("Password:");
        passwordLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2a60b9;");

        Label roleLabel = new Label("Role:");
        roleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2a60b9;");

        // Create role combo box
        ComboBox<String> roleComboBox = new ComboBox<>();
        roleComboBox.getItems().addAll("Student", "Lecturer");
        roleComboBox.setValue("Student");  // default selection

        // Style combo box (blue themed)
        roleComboBox.setStyle(
                "-fx-background-color: #e3f0ff;" +
                        "-fx-border-color: #5a9bf6;" +
                        "-fx-border-radius: 5px;" +
                        "-fx-background-radius: 5px;" +
                        "-fx-padding: 5 10 5 10;" +
                        "-fx-font-size: 14px;" +
                        "-fx-text-fill: #1a3c72;"
        );

        // Add focus effect on combo box (blue glow)
        roleComboBox.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                roleComboBox.setEffect(new DropShadow(10, Color.web("#1e90ff")));
                roleComboBox.setStyle(roleComboBox.getStyle() + "-fx-border-color: #1e90ff;");
            } else {
                roleComboBox.setEffect(null);
                roleComboBox.setStyle(roleComboBox.getStyle().replace("-fx-border-color: #1e90ff;", "-fx-border-color: #5a9bf6;"));
            }
        });

        // Style text fields with blue background and border
        usernameField.setStyle(
                "-fx-background-color: #e3f0ff;" +
                        "-fx-border-color: #5a9bf6;" +
                        "-fx-border-radius: 5px;" +
                        "-fx-background-radius: 5px;" +
                        "-fx-padding: 8 10 8 10;" +
                        "-fx-font-size: 14px;" +
                        "-fx-text-fill: #1a3c72;"
        );

        passwordField.setStyle(
                "-fx-background-color: #e3f0ff;" +
                        "-fx-border-color: #5a9bf6;" +
                        "-fx-border-radius: 5px;" +
                        "-fx-background-radius: 5px;" +
                        "-fx-padding: 8 10 8 10;" +
                        "-fx-font-size: 14px;" +
                        "-fx-text-fill: #1a3c72;"
        );

        // Add focus effect on text fields: blue glow
        usernameField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                usernameField.setEffect(new DropShadow(10, Color.web("#1e90ff")));
                usernameField.setStyle(usernameField.getStyle() + "-fx-border-color: #1e90ff;");
            } else {
                usernameField.setEffect(null);
                usernameField.setStyle(usernameField.getStyle().replace("-fx-border-color: #1e90ff;", "-fx-border-color: #5a9bf6;"));
            }
        });

        passwordField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                passwordField.setEffect(new DropShadow(10, Color.web("#1e90ff")));
                passwordField.setStyle(passwordField.getStyle() + "-fx-border-color: #1e90ff;");
            } else {
                passwordField.setEffect(null);
                passwordField.setStyle(passwordField.getStyle().replace("-fx-border-color: #1e90ff;", "-fx-border-color: #5a9bf6;"));
            }
        });

        // Add controls to grid
        loginForm.add(usernameLabel, 0, 0);
        loginForm.add(usernameField, 1, 0);

        loginForm.add(passwordLabel, 0, 1);
        loginForm.add(passwordField, 1, 1);

        loginForm.add(roleLabel, 0, 2);
        loginForm.add(roleComboBox, 1, 2);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        // Style login button (blue)
        loginButton.setStyle(
                "-fx-background-color: #1e90ff;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 5px;" +
                        "-fx-padding: 10 20 10 20;" +
                        "-fx-cursor: hand;" +
                        "-fx-font-size: 14px;"
        );
        addButtonHoverEffect(loginButton, "#1e90ff", "#0f74e0");

        loginButton.setOnAction(e -> {
            String selectedRole = roleComboBox.getValue();
            handleLogin(selectedRole); // Modify your handleLogin to accept role if needed
        });

        // Style register button (green)
        Button registerButton = new Button("Register Student");
        registerButton.setStyle(
                "-fx-background-color: #28a745;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 5px;" +
                        "-fx-padding: 10 20 10 20;" +
                        "-fx-cursor: hand;" +
                        "-fx-font-size: 14px;"
        );
        addButtonHoverEffect(registerButton, "#28a745", "#1e7e34");

        registerButton.setOnAction(e -> showRegistrationForm());

        buttonBox.getChildren().addAll(loginButton, registerButton);
        loginForm.add(buttonBox, 0, 3, 2, 1);

        // Style error label
        errorLabel.setStyle("-fx-text-fill: #d9534f; -fx-font-weight: bold; -fx-font-size: 13px;");

        loginContainer.setAlignment(Pos.CENTER);
        loginContainer.setSpacing(20);
        loginContainer.getChildren().addAll(title, loginForm, errorLabel);
    }


    // Helper method to add hover effects on buttons
    private void addButtonHoverEffect(Button button, String baseColor, String hoverColor) {
        button.setOnMouseEntered(e -> button.setStyle(
                "-fx-background-color: " + hoverColor + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 5px;" +
                        "-fx-padding: 10 20 10 20;" +
                        "-fx-cursor: hand;" +
                        "-fx-font-size: 14px;"
        ));
        button.setOnMouseExited(e -> button.setStyle(
                "-fx-background-color: " + baseColor + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 5px;" +
                        "-fx-padding: 10 20 10 20;" +
                        "-fx-cursor: hand;" +
                        "-fx-font-size: 14px;"
        ));
    }


    private void showRegistrationForm() {
        Stage registrationStage = new Stage();
        registrationStage.setTitle("Register New Student Account");

        GridPane registrationForm = new GridPane();
        registrationForm.setAlignment(Pos.CENTER);
        registrationForm.setHgap(15);
        registrationForm.setVgap(15);
        registrationForm.setPadding(new Insets(30));
        registrationForm.setStyle("-fx-background-color: #ffffff;");

        // Header with drop shadow
        Text header = new Text("Register New Student Account");
        header.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        header.setFill(Color.web("#1e90ff"));
        header.setEffect(new DropShadow(10, Color.web("#1e90ff")));

        // Input fields
        TextField fullNameField = new TextField();
        TextField emailField = new TextField();
        TextField regUsernameField = new TextField();
        PasswordField regPasswordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();

        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.web("#d9534f"));
        errorLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        // Apply blue styles and focus effect
        styleTextField(fullNameField);
        styleTextField(emailField);
        styleTextField(regUsernameField);
        styleTextField(regPasswordField);
        styleTextField(confirmPasswordField);

        // Form layout
        registrationForm.add(header, 0, 0, 2, 1);
        registrationForm.add(new Label("Full Name:"), 0, 1);
        registrationForm.add(fullNameField, 1, 1);
        registrationForm.add(new Label("Email:"), 0, 2);
        registrationForm.add(emailField, 1, 2);
        registrationForm.add(new Label("Username:"), 0, 3);
        registrationForm.add(regUsernameField, 1, 3);
        registrationForm.add(new Label("Password:"), 0, 4);
        registrationForm.add(regPasswordField, 1, 4);
        registrationForm.add(new Label("Confirm Password:"), 0, 5);
        registrationForm.add(confirmPasswordField, 1, 5);

        // Buttons
        Button registerButton = new Button("Register");
        Button cancelButton = new Button("Cancel");

        styleButton(registerButton, "#1e90ff", "#0f74e0");  // Blue button
        styleButton(cancelButton, "#6c757d", "#5a6268");   // Gray button

        registerButton.setOnAction(e -> {
            if (validateRegistration(fullNameField.getText(), emailField.getText(),
                    regUsernameField.getText(), regPasswordField.getText(),
                    confirmPasswordField.getText(), errorLabel)) {
                registerUser(fullNameField.getText(), emailField.getText(),
                        regUsernameField.getText(), regPasswordField.getText(),
                        "student", registrationStage, errorLabel);
            }
        });

        cancelButton.setOnAction(e -> registrationStage.close());

        HBox buttonBox = new HBox(15, registerButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        registrationForm.add(errorLabel, 0, 6, 2, 1);
        registrationForm.add(buttonBox, 1, 7);

        Scene scene = new Scene(registrationForm, 480, 420);
        registrationStage.setScene(scene);
        registrationStage.show();
    }

    // Style a text field with blue theme and glow on focus
    private void styleTextField(TextField field) {
        field.setStyle(
                "-fx-background-color: #e3f2fd;" +
                        "-fx-border-color: #90caf9;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;" +
                        "-fx-font-size: 14px;" +
                        "-fx-text-fill: #0d47a1;" +
                        "-fx-padding: 8 10 8 10;"
        );

        field.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                field.setEffect(new DropShadow(12, Color.web("#42a5f5")));
                field.setStyle(field.getStyle().replace("-fx-border-color: #90caf9;", "-fx-border-color: #1e88e5;"));
            } else {
                field.setEffect(null);
                field.setStyle(field.getStyle().replace("-fx-border-color: #1e88e5;", "-fx-border-color: #90caf9;"));
            }
        });
    }

    // Style button and add hover effect
    private void styleButton(Button button, String baseColor, String hoverColor) {
        button.setStyle(
                "-fx-background-color: " + baseColor + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 10 20 10 20;" +
                        "-fx-font-size: 14px;" +
                        "-fx-cursor: hand;"
        );

        button.setOnMouseEntered(e -> button.setStyle(
                "-fx-background-color: " + hoverColor + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 10 20 10 20;" +
                        "-fx-font-size: 14px;" +
                        "-fx-cursor: hand;"
        ));

        button.setOnMouseExited(e -> button.setStyle(
                "-fx-background-color: " + baseColor + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 10 20 10 20;" +
                        "-fx-font-size: 14px;" +
                        "-fx-cursor: hand;"
        ));
    }


    private boolean validateRegistration(String fullName, String email, String username,
                                         String password, String confirmPassword, Label errorLabel) {
        if (fullName.isEmpty() || email.isEmpty() || username.isEmpty() ||
                password.isEmpty() || confirmPassword.isEmpty()) {
            errorLabel.setText("All fields are required");
            return false;
        }

        if (!password.equals(confirmPassword)) {
            errorLabel.setText("Passwords do not match");
            return false;
        }

        if (password.length() < 6) {
            errorLabel.setText("Password must be at least 6 characters");
            return false;
        }

        if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            errorLabel.setText("Please enter a valid email address");
            return false;
        }

        errorLabel.setText("");
        return true;
    }

    private void registerUser(String fullName, String email, String username,
                              String password, String role, Stage registrationStage, Label errorLabel) {
        try (Connection conn = getConnection()) {
            String checkQuery = "SELECT id FROM users WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, username);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    errorLabel.setText("Username already exists");
                    return;
                }
            }

            String insertQuery = "INSERT INTO users (full_name, email, username, password, role) " +
                    "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                insertStmt.setString(1, fullName);
                insertStmt.setString(2, email);
                insertStmt.setString(3, username);
                insertStmt.setString(4, password);
                insertStmt.setString(5, role);
                insertStmt.executeUpdate();
            }

            this.userId = getUserId(username);
            this.fullName = fullName;
            this.role = role;

            registrationStage.close();
            loginAsStudent();
        } catch (SQLException e) {
            errorLabel.setText("Registration failed: " + e.getMessage());
        }
    }

    private int getUserId(String username) throws SQLException {
        try (Connection conn = getConnection()) {
            String query = "SELECT id FROM users WHERE username = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }
    private void initializeStudentData() {
        try (Connection conn = getConnection()) {
            courses.clear();
            assignments.clear();
            announcements.clear();

            // Load enrolled courses with progress
            String enrolledCoursesQuery = "SELECT c.* FROM courses c " +
                    "JOIN enrollments e ON c.course_code = e.course_code " +
                    "WHERE e.student_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(enrolledCoursesQuery)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    courses.add(new Course(
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getString("teacher"),
                            rs.getInt("progress")
                    ));
                }
            }

            // Load all assignments for enrolled courses
            String assignmentsQuery = "SELECT a.* FROM assignments a " +
                    "JOIN enrollments e ON a.course_code = e.course_code " +
                    "WHERE e.student_id = ? ORDER BY a.due_date ASC";
            try (PreparedStatement stmt = conn.prepareStatement(assignmentsQuery)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Assignment assignment = new Assignment(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getString("instructions"),
                            rs.getString("due_date"),
                            getSubmissionStatus(userId, rs.getInt("id")),
                            rs.getString("course_code"),
                            rs.getInt("max_points"),
                            rs.getString("grading_criteria")
                    );
                    assignment.setSubmittedFilePath(getSubmittedFilePath(userId, rs.getInt("id")));
                    assignments.add(assignment);
                }
            }

            // Load all announcements (general and for enrolled courses)
            String announcementsQuery = "SELECT a.* FROM announcements a " +
                    "WHERE a.course_code IS NULL OR a.course_code IN " +
                    "(SELECT course_code FROM enrollments WHERE student_id = ?) " +
                    "ORDER BY a.date DESC";
            try (PreparedStatement stmt = conn.prepareStatement(announcementsQuery)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Integer assignmentId = rs.getInt("assignment_id");
                    if (rs.wasNull()) assignmentId = null;
                    announcements.add(new Announcement(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("content"),
                            rs.getString("date"),
                            assignmentId,
                            rs.getString("course_code")
                    ));
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to load student data: " + e.getMessage());
        }
    }
    private String getSubmissionStatus(int studentId, int assignmentId) throws SQLException {
        try (Connection conn = getConnection()) {
            String query = "SELECT file_path FROM submissions WHERE student_id = ? AND assignment_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, studentId);
                pstmt.setInt(2, assignmentId);
                ResultSet rs = pstmt.executeQuery();
                return rs.next() ? "Submitted" : "Not Submitted";
            }
        }
    }

    private String getSubmittedFilePath(int studentId, int assignmentId) throws SQLException {
        try (Connection conn = getConnection()) {
            String query = "SELECT file_path FROM submissions WHERE student_id = ? AND assignment_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, studentId);
                pstmt.setInt(2, assignmentId);
                ResultSet rs = pstmt.executeQuery();
                return rs.next() ? rs.getString("file_path") : null;
            }
        }
    }

    private void enrollStudentInCourse(int studentId, String courseCode) {
        try (Connection conn = getConnection()) {
            String checkQuery = "SELECT id FROM enrollments WHERE student_id = ? AND course_code = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setInt(1, studentId);
                checkStmt.setString(2, courseCode);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    showAlert("Enrollment", "You are already enrolled in this course.");
                    return;
                }
            }

            String enrollQuery = "INSERT INTO enrollments (student_id, course_code, enrollment_date) " +
                    "VALUES (?, ?, ?)";
            try (PreparedStatement enrollStmt = conn.prepareStatement(enrollQuery)) {
                enrollStmt.setInt(1, studentId);
                enrollStmt.setString(2, courseCode);
                enrollStmt.setDate(3, new java.sql.Date(new Date().getTime()));
                enrollStmt.executeUpdate();
            }

            showAlert("Success", "You have been successfully enrolled in the course.");
            initializeStudentData();
            setupStudentDashboard();
        } catch (SQLException e) {
            showAlert("Error", "Failed to enroll in course: " + e.getMessage());
        }
    }

    private void showAvailableCourses() {
        Stage enrollmentStage = new Stage();
        enrollmentStage.setTitle("Available Courses");

        VBox enrollmentLayout = new VBox(20);
        enrollmentLayout.setPadding(new Insets(20));
        enrollmentLayout.setAlignment(Pos.TOP_CENTER);

        Label titleLabel = new Label("Available Courses");
        titleLabel.getStyleClass().add("title");

        TableView<Course> coursesTable = new TableView<>();
        coursesTable.getStyleClass().add("table-view");

        TableColumn<Course, String> codeColumn = new TableColumn<>("Code");
        codeColumn.setCellValueFactory(new PropertyValueFactory<>("courseCode"));

        TableColumn<Course, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("courseName"));

        TableColumn<Course, String> teacherColumn = new TableColumn<>("Teacher");
        teacherColumn.setCellValueFactory(new PropertyValueFactory<>("teacher"));

        coursesTable.getColumns().addAll(codeColumn, nameColumn, teacherColumn);

        try (Connection conn = getConnection()) {
            String query = "SELECT c.course_code, c.course_name, c.teacher, c.progress " +
                    "FROM courses c " +
                    "WHERE c.course_code NOT IN " +
                    "(SELECT course_code FROM enrollments WHERE student_id = ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();

                ObservableList<Course> availableCourses = FXCollections.observableArrayList();
                while (rs.next()) {
                    availableCourses.add(new Course(
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getString("teacher"),
                            rs.getInt("progress")
                    ));
                }
                coursesTable.setItems(availableCourses);
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to load available courses: " + e.getMessage());
        }

        Button enrollButton = new Button("Enroll in Selected Course");
        enrollButton.getStyleClass().add("button-primary");
        enrollButton.setOnAction(e -> {
            Course selectedCourse = coursesTable.getSelectionModel().getSelectedItem();
            if (selectedCourse != null) {
                enrollStudentInCourse(userId, selectedCourse.getCourseCode());
                enrollmentStage.close();
            } else {
                showAlert("Error", "Please select a course to enroll in.");
            }
        });

        enrollmentLayout.getChildren().addAll(titleLabel, coursesTable, enrollButton);

        Scene scene = new Scene(enrollmentLayout, 600, 400);
        enrollmentStage.setScene(scene);
        enrollmentStage.show();
    }

    private void initializeLecturerData() {
        try (Connection conn = getConnection()) {
            lecturerCourses.clear();
            assignments.clear();
            announcements.clear();

            // Load lecturer's courses
            String courseQuery = "SELECT course_code, course_name, teacher, progress FROM courses WHERE teacher = ?";
            try (PreparedStatement stmt = conn.prepareStatement(courseQuery)) {
                stmt.setString(1, fullName);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    lecturerCourses.add(new Course(
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getString("teacher"),
                            rs.getInt("progress")
                    ));
                }
            }

            // Load assignments
            String assignmentQuery = "SELECT a.id, a.title, a.description, a.instructions, a.due_date, " +
                    "a.course_code, a.max_points, a.grading_criteria " +
                    "FROM assignments a JOIN courses c ON a.course_code = c.course_code " +
                    "WHERE c.teacher = ?";
            try (PreparedStatement stmt = conn.prepareStatement(assignmentQuery)) {
                stmt.setString(1, fullName);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    assignments.add(new Assignment(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getString("instructions"),
                            rs.getString("due_date"),
                            "Not Submitted", // Default status for lecturer view
                            rs.getString("course_code"),
                            rs.getInt("max_points"),
                            rs.getString("grading_criteria")
                    ));
                }
            }

            // Load announcements
            String announcementQuery = "SELECT id, title, content, date, assignment_id, course_code FROM announcements";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(announcementQuery)) {
                while (rs.next()) {
                    Integer assignmentId = rs.getInt("assignment_id");
                    if (rs.wasNull()) assignmentId = null;
                    announcements.add(new Announcement(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("content"),
                            rs.getString("date"),
                            assignmentId,
                            rs.getString("course_code")
                    ));
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to load lecturer data: " + e.getMessage());
        }
    }

    private void handleLogin(String selectedRole) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Username and password are required");
            return;
        }

        try (Connection conn = getConnection()) {
            String query = "SELECT id, full_name, role FROM users WHERE username = ? AND password = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    userId = rs.getInt("id");
                    fullName = rs.getString("full_name");
                    role = rs.getString("role").toLowerCase();

                    if ("student".equals(role)) {
                        loginAsStudent();
                    } else if ("lecturer".equals(role)) {
                        loginAsLecturer();
                    }
                } else {
                    errorLabel.setText("Invalid username or password");
                }
            }
        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
        }
    }

    private void loginAsStudent() {
        initializeStudentData();
        welcomeLabel.setText("Welcome, " + fullName + " (Student ID: " + userId + ")");
        setupStudentDashboard();

        root.setCenter(mainContainer);
        mainContainer.getChildren().setAll(studentDashboard);
    }

    private void loginAsLecturer() {
        initializeLecturerData();
        lecturerWelcomeLabel.setText("Welcome, " + fullName);
        setupLecturerDashboard();

        root.setCenter(lecturerDashboard);
    }

    private void setupStudentDashboard() {
        studentDashboard.getChildren().clear();
        // Set light blue background color
        studentDashboard.setStyle("-fx-background-color: #add8e6; -fx-padding: 20;"); // Light blue color

        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(10);
        header.setPadding(new Insets(15));
        header.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 10;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0.5, 0, 3);"
        );

        welcomeLabel.setStyle(
                "-fx-font-size: 22px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: #333;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Refresh Button
        Button refreshButton = new Button("Refresh");
        stylePrimaryButton(refreshButton, "#42a5f5", "#1e88e5");
        refreshButton.setOnAction(e -> {
            initializeStudentData();
            setupStudentDashboard();
        });

        // Profile Menu Button
        MenuButton profileButton = new MenuButton(fullName);
        profileButton.setGraphic(new Circle(15, Color.LIGHTGRAY));
        profileButton.setStyle(
                "-fx-background-color: #f1f3f4;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 6 14;" +
                        "-fx-background-radius: 6;" +
                        "-fx-cursor: hand;"
        );

        MenuItem profileItem = new MenuItem("View Profile");
        MenuItem settingsItem = new MenuItem("Settings");
        MenuItem logoutItem = new MenuItem("Logout");
        logoutItem.setOnAction(e -> handleLogout());

        profileButton.getItems().addAll(profileItem, settingsItem, new SeparatorMenuItem(), logoutItem);

        header.getChildren().addAll(welcomeLabel, spacer, refreshButton, profileButton);

        // Stats Cards
        HBox statsContainer = new HBox(20);
        statsContainer.setPadding(new Insets(20, 0, 20, 0));
        statsContainer.setAlignment(Pos.CENTER);

        VBox coursesStat = createDashboardCard("Enrolled Courses", String.valueOf(courses.size()), "#1e88e5");
        VBox assignmentsStat = createDashboardCard("Active Assignments",
                String.valueOf(assignments.stream().filter(a -> "Not Submitted".equals(a.getSubmissionStatus())).count()),
                "#43a047");
        VBox announcementsStat = createDashboardCard("New Announcements",
                String.valueOf(announcements.size()), "#fdd835");

        statsContainer.getChildren().addAll(coursesStat, assignmentsStat, announcementsStat);

        // TabPane
        TabPane studentTabPane = new TabPane();
        studentTabPane.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 10;"
        );

        Tab dashboardTab = new Tab("Dashboard");
        dashboardTab.setClosable(false);
        setupStudentDashboardTab(dashboardTab);

        Tab coursesTab = new Tab("My Courses");
        coursesTab.setClosable(false);
        setupStudentCoursesTab(coursesTab);

        Tab gradesTab = new Tab("My Grades");
        gradesTab.setClosable(false);
        setupStudentGradesTab(gradesTab);

        studentTabPane.getTabs().addAll(dashboardTab, coursesTab, gradesTab);
        studentDashboard.getChildren().addAll(header, statsContainer, studentTabPane);
    }

    // Helper to style buttons with hover
    private void stylePrimaryButton(Button button, String baseColor, String hoverColor) {
        button.setStyle(
                "-fx-background-color: " + baseColor + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 8 20;" +
                        "-fx-cursor: hand;"
        );

        button.setOnMouseEntered(e -> button.setStyle(
                "-fx-background-color: " + hoverColor + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 8 20;" +
                        "-fx-cursor: hand;"
        ));

        button.setOnMouseExited(e -> button.setStyle(
                "-fx-background-color: " + baseColor + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 8 20;" +
                        "-fx-cursor: hand;"
        ));
    }

    // Helper to create a dashboard card
    private VBox createDashboardCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(15));
        card.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 10;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0.5, 0, 4);" +
                        "-fx-pref-width: 200;"
        );

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #333; -fx-font-weight: bold;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private void setupStudentGradesTab(Tab gradesTab) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: white; -fx-background-radius: 8px;");

        Label title = new Label("My Grades");
        title.getStyleClass().add("subtitle");

        TableView<Submission> gradesTable = new TableView<>();
        gradesTable.getStyleClass().add("table-view");

        // Columns
        TableColumn<Submission, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(cellData -> {
            try {
                String courseCode = getCourseCodeForAssignment(cellData.getValue().getAssignmentId());
                return new SimpleStringProperty(courseCode);
            } catch (SQLException e) {
                return new SimpleStringProperty("Unknown");
            }
        });

        TableColumn<Submission, String> assignmentCol = new TableColumn<>("Assignment");
        assignmentCol.setCellValueFactory(cellData -> {
            try {
                String assignmentTitle = getAssignmentTitle(cellData.getValue().getAssignmentId());
                return new SimpleStringProperty(assignmentTitle);
            } catch (SQLException e) {
                return new SimpleStringProperty("Unknown");
            }
        });

        TableColumn<Submission, String> gradeCol = new TableColumn<>("Grade");
        gradeCol.setCellValueFactory(cellData -> {
            int maxPoints = getAssignmentMaxPoints(cellData.getValue().getAssignmentId());
            return new SimpleStringProperty(cellData.getValue().getGrade() + "/" + maxPoints);
        });

        TableColumn<Submission, String> feedbackCol = new TableColumn<>("Feedback");
        feedbackCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFeedback()));

        TableColumn<Submission, String> dateCol = new TableColumn<>("Published");
        dateCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getSubmissionDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))));

        gradesTable.getColumns().addAll(courseCol, assignmentCol, gradeCol, feedbackCol, dateCol);

        // Load data
        try {
            ObservableList<Submission> submissions = DatabaseUtil.executeQuery(
                    "SELECT s.* FROM submissions s " +
                            "JOIN assignments a ON s.assignment_id = a.id " +
                            "JOIN enrollments e ON a.course_code = e.course_code " +
                            "WHERE e.student_id = ? AND s.published = true",
                    pstmt -> {
                        pstmt.setInt(1, userId);
                        ResultSet rs = pstmt.executeQuery();

                        ObservableList<Submission> results = FXCollections.observableArrayList();
                        while (rs.next()) {
                            results.add(new Submission(
                                    rs.getInt("id"),
                                    rs.getInt("assignment_id"),
                                    rs.getInt("student_id"),
                                    rs.getString("file_path"),
                                    rs.getTimestamp("submission_date").toLocalDateTime(),
                                    rs.getInt("grade"),
                                    rs.getString("feedback"),
                                    rs.getBoolean("published")
                            ));
                        }
                        return results;
                    }
            );
            gradesTable.setItems(submissions);
        } catch (Exception e) {
            showAlert("Error", "Failed to load grades: " + e.getMessage());
        }

        content.getChildren().addAll(title, gradesTable);
        gradesTab.setContent(new ScrollPane(content));
    }

    private void setupStudentDashboardTab(Tab dashboardTab) {
        VBox dashboardContent = new VBox(20);
        dashboardContent.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 8px;");

        // General Announcements
        Label generalLabel = new Label("General Announcements");
        generalLabel.getStyleClass().add("subtitle");

        VBox generalAnnouncements = new VBox(10);
        announcements.stream()
                .filter(a -> a.getCourseCode() == null)
                .forEach(a -> generalAnnouncements.getChildren().add(createAnnouncementCard(a)));

        // Course Announcements
        Label courseLabel = new Label("Course Announcements");
        courseLabel.getStyleClass().add("subtitle");

        VBox courseAnnouncements = new VBox(10);
        announcements.stream()
                .filter(a -> a.getCourseCode() != null)
                .forEach(a -> courseAnnouncements.getChildren().add(createAnnouncementCard(a)));

        dashboardContent.getChildren().addAll(generalLabel, generalAnnouncements, courseLabel, courseAnnouncements);
        dashboardTab.setContent(new ScrollPane(dashboardContent));
    }

    private void setupStudentCoursesTab(Tab coursesTab) {
        VBox coursesContent = new VBox(20);
        coursesContent.setPadding(new Insets(20));
        coursesContent.setStyle("-fx-background-color: white; -fx-background-radius: 8px;");

        // Enroll button
        Button enrollButton = new Button("Enroll in New Courses");
        enrollButton.getStyleClass().add("button-primary");
        enrollButton.setOnAction(e -> showAvailableCourses());

        // Courses list
        FlowPane coursesGrid = new FlowPane();
        coursesGrid.setHgap(20);
        coursesGrid.setVgap(20);
        coursesGrid.setPrefWrapLength(1000);
        courses.forEach(course -> coursesGrid.getChildren().add(createCourseCard(course)));

        coursesContent.getChildren().addAll(enrollButton, coursesGrid);
        coursesTab.setContent(new ScrollPane(coursesContent));
    }

    private VBox createAnnouncementCard(Announcement announcement) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(15));

        Label title = new Label(announcement.getTitle());
        title.getStyleClass().add("card-title");

        Label content = new Label(announcement.getContent());
        content.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 14px;");
        content.setWrapText(true);

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label(announcement.getDate());
        dateLabel.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 12px;");

        footer.getChildren().add(dateLabel);

        if (announcement.getCourseCode() != null) {
            Label courseLabel = new Label("Course: " + announcement.getCourseCode());
            courseLabel.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 12px;");
            footer.getChildren().add(courseLabel);
        }

        if (announcement.getAssignmentId() != null) {
            try {
                String assignmentTitle = getAssignmentTitle(announcement.getAssignmentId());
                Label assignmentLabel = new Label("Assignment: " + assignmentTitle);
                assignmentLabel.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 12px;");
                footer.getChildren().add(assignmentLabel);
            } catch (SQLException e) {
                // Ignore if we can't get assignment title
            }
        }

        card.getChildren().addAll(title, content, footer);
        return card;
    }

    private VBox createCourseCard(Course course) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPrefWidth(300);
        card.setPrefHeight(150);

        Label code = new Label(course.getCourseCode());
        code.getStyleClass().add("card-title");

        Label name = new Label(course.getCourseName());
        name.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 14px;");
        name.setWrapText(true);

        Label teacher = new Label("Taught by: " + course.getTeacher());
        teacher.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 12px;");

        ProgressBar progress = new ProgressBar(course.getProgress() / 100.0);
        progress.getStyleClass().add("progress-bar");

        Label progressLabel = new Label("Progress: " + course.getProgress() + "%");
        progressLabel.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 12px;");

        card.getChildren().addAll(code, name, teacher, progress, progressLabel);
        card.setOnMouseClicked(e -> showCourseDetails(course));

        // Add hover effect
        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 0, 4, 6, 0);");
        });

        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 0, 2, 4, 0);");
        });

        return card;
    }
    private void showCourseDetails(Course course) {
        VBox courseView = new VBox(20);
        courseView.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 20;");

        HBox header = new HBox(10);
        header.getStyleClass().add("header");

        Button backButton = new Button("← Back to Courses");
        backButton.getStyleClass().add("button-secondary");
        backButton.setOnAction(e -> mainContainer.getChildren().setAll(studentDashboard));

        Label courseTitle = new Label(course.getCourseCode() + " - " + course.getCourseName());
        courseTitle.getStyleClass().add("title");

        header.getChildren().addAll(backButton, courseTitle);

        ScrollPane scrollContent = new ScrollPane();
        scrollContent.setFitToWidth(true);

        VBox content = new VBox(20);
        content.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 8px;");

        // Course info
        HBox courseInfo = new HBox(20);
        Label teacherLabel = new Label("Instructor: " + course.getTeacher());
        teacherLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #3c4043;");

        Label progressLabel = new Label("Your Progress: " + course.getProgress() + "%");
        progressLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #3c4043;");

        courseInfo.getChildren().addAll(teacherLabel, progressLabel);

        // Announcements
        VBox announcementsSection = new VBox(10);
        Label announcementsTitle = new Label("Course Announcements");
        announcementsTitle.getStyleClass().add("subtitle");

        VBox announcementsList = new VBox(10);
        List<Announcement> courseAnnouncements = announcements.stream()
                .filter(a -> a.getCourseCode() != null && a.getCourseCode().equals(course.getCourseCode()))
                .collect(Collectors.toList());

        if (courseAnnouncements.isEmpty()) {
            announcementsList.getChildren().add(new Label("No announcements for this course yet"));
        } else {
            courseAnnouncements.forEach(a -> announcementsList.getChildren().add(createAnnouncementCard(a)));
        }

        announcementsSection.getChildren().addAll(announcementsTitle, announcementsList);

        // Course Materials Section
        VBox materialsSection = new VBox(10);
        Label materialsTitle = new Label("Course Materials");
        materialsTitle.getStyleClass().add("subtitle");

        VBox materialsList = new VBox(10);
        List<CourseMaterial> courseMaterials = getMaterialsForCourse(course.getCourseCode());

        if (courseMaterials.isEmpty()) {
            materialsList.getChildren().add(new Label("No materials available for this course yet"));
        } else {
            for (CourseMaterial material : courseMaterials) {
                HBox materialItem = new HBox(10);
                materialItem.setAlignment(Pos.CENTER_LEFT);

                Label materialLabel = new Label(material.getTitle() + " (Uploaded: " + material.getUploadDate() + ")");
                materialLabel.setStyle("-fx-text-fill: #3c4043; -fx-font-size: 14px;");

                Button downloadButton = new Button("Download");
                downloadButton.getStyleClass().add("button-secondary");
                downloadButton.setOnAction(e -> downloadMaterial(material));

                materialItem.getChildren().addAll(materialLabel, downloadButton);
                materialsList.getChildren().add(materialItem);
            }
        }

        materialsSection.getChildren().addAll(materialsTitle, materialsList);

        // Assignments
        VBox assignmentsSection = new VBox(10);
        Label assignmentsTitle = new Label("Assignments");
        assignmentsTitle.getStyleClass().add("subtitle");

        VBox assignmentsList = new VBox(10);
        List<Assignment> courseAssignments = assignments.stream()
                .filter(a -> a.getCourseCode().equals(course.getCourseCode()))
                .collect(Collectors.toList());

        if (courseAssignments.isEmpty()) {
            assignmentsList.getChildren().add(new Label("No assignments for this course yet"));
        } else {
            courseAssignments.forEach(a -> assignmentsList.getChildren().add(createAssignmentCard(a)));
        }

        assignmentsSection.getChildren().addAll(assignmentsTitle, assignmentsList);

        // Add all sections to the content in the desired order
        content.getChildren().addAll(
                courseInfo,
                announcementsSection,
                materialsSection,
                assignmentsSection
        );

        scrollContent.setContent(content);
        courseView.getChildren().addAll(header, scrollContent);
        mainContainer.getChildren().setAll(courseView);
    }
    private void downloadMaterial(CourseMaterial material) {
        File sourceFile = new File(material.getFilePath());
        if (!sourceFile.exists()) {
            showAlert("Error", "The file does not exist on the server.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Material");
        fileChooser.setInitialFileName(sourceFile.getName());

        // Set initial directory to user's downloads folder
        String userHome = System.getProperty("user.home");
        fileChooser.setInitialDirectory(new File(userHome + "/Downloads"));

        File destinationFile = fileChooser.showSaveDialog(null);
        if (destinationFile != null) {
            try {
                Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                showAlert("Success", "File downloaded successfully to: " + destinationFile.getAbsolutePath());
            } catch (IOException e) {
                showAlert("Error", "Failed to download file: " + e.getMessage());
            }
        }
    }

    private List<CourseMaterial> getMaterialsForCourse(String courseCode) {
        List<CourseMaterial> materials = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String query = "SELECT id, title, file_path, upload_date FROM course_materials WHERE course_code = ? ORDER BY upload_date DESC";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, courseCode);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    materials.add(new CourseMaterial(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("file_path"),
                            courseCode,
                            rs.getString("upload_date")
                    ));
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to load course materials: " + e.getMessage());
        }
        return materials;
    }

    private VBox createAssignmentCard(Assignment assignment) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPrefWidth(300);

        Label title = new Label(assignment.getTitle());
        title.getStyleClass().add("card-title");

        Label description = new Label(assignment.getDescription());
        description.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 14px;");
        description.setWrapText(true);

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);

        Label dueDate = new Label("Due: " + assignment.getDueDate());
        dueDate.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 12px;");

        Label status = new Label("Status: " + assignment.getSubmissionStatus());
        status.setStyle("-fx-text-fill: " +
                ("Submitted".equals(assignment.getSubmissionStatus()) ? "#34a853" : "#d93025") +
                "; -fx-font-size: 12px; -fx-font-weight: bold;");

        footer.getChildren().addAll(dueDate, status);

        card.getChildren().addAll(title, description, footer);
        card.setOnMouseClicked(e -> showAssignmentDetails(assignment));

        // Add hover effect
        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 0, 4, 6, 0);");
        });

        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 0, 2, 4, 0);");
        });

        return card;
    }

    private void showAssignmentDetails(Assignment assignment) {
        VBox assignmentView = new VBox(20);
        assignmentView.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 20;");

        HBox header = new HBox(10);
        header.getStyleClass().add("header");

        Button backButton = new Button("← Back to Course");
        backButton.getStyleClass().add("button-secondary");
        backButton.setOnAction(e -> {
            Optional<Course> course = courses.stream()
                    .filter(c -> c.getCourseCode().equals(assignment.getCourseCode()))
                    .findFirst();
            if (course.isPresent()) {
                showCourseDetails(course.get());
            }
        });

        Label assignmentTitle = new Label(assignment.getTitle());
        assignmentTitle.getStyleClass().add("title");

        header.getChildren().addAll(backButton, assignmentTitle);

        ScrollPane scrollContent = new ScrollPane();
        scrollContent.setFitToWidth(true);

        VBox content = new VBox(20);
        content.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 8px;");

        // Assignment details
        Label description = new Label(assignment.getDescription());
        description.setStyle("-fx-text-fill: #3c4043; -fx-font-size: 14px;");
        description.setWrapText(true);

        Label instructions = new Label("Instructions:\n" + assignment.getInstructions());
        instructions.setStyle("-fx-text-fill: #3c4043; -fx-font-size: 14px;");
        instructions.setWrapText(true);

        Label grading = new Label("Grading Criteria (" + assignment.getMaxPoints() + " points):\n" +
                assignment.getGradingCriteria());
        grading.setStyle("-fx-text-fill: #3c4043; -fx-font-size: 14px;");
        grading.setWrapText(true);

        // Submission section
        VBox submissionBox = new VBox(10);
        Label submissionTitle = new Label("Submission");
        submissionTitle.getStyleClass().add("subtitle");

        if ("Submitted".equals(assignment.getSubmissionStatus())) {
            Label submittedLabel = new Label("Status: Submitted");
            submittedLabel.setStyle("-fx-text-fill: #34a853; -fx-font-weight: bold;");

            if (assignment.getSubmittedFilePath() != null) {
                Label fileLabel = new Label("Submitted file: " + assignment.getSubmittedFilePath());
                fileLabel.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 14px;");
                submissionBox.getChildren().add(fileLabel);
            }

            Button unsubmitButton = new Button("Unsubmit");
            unsubmitButton.getStyleClass().add("button-danger");
            unsubmitButton.setOnAction(e -> unsubmitAssignment(assignment));
            submissionBox.getChildren().addAll(submittedLabel, unsubmitButton);
        } else {
            Button uploadButton = new Button("Upload File");
            uploadButton.getStyleClass().add("button-primary");
            Label fileLabel = new Label("No file selected");
            fileLabel.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 14px;");

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Assignment File");

            uploadButton.setOnAction(e -> {
                File selectedFile = fileChooser.showOpenDialog(null);
                if (selectedFile != null) {
                    fileLabel.setText("Selected: " + selectedFile.getName());

                    Button submitButton = new Button("Submit Assignment");
                    submitButton.getStyleClass().add("button-success");
                    submitButton.setOnAction(ev -> submitAssignment(assignment, selectedFile));

                    submissionBox.getChildren().clear();
                    submissionBox.getChildren().addAll(fileLabel, submitButton);
                }
            });

            submissionBox.getChildren().addAll(uploadButton, fileLabel);
        }

        try {
            Optional<Submission> submissionOpt = DatabaseUtil.executeQuery(
                    "SELECT * FROM submissions WHERE assignment_id = ? AND student_id = ?",
                    pstmt -> {
                        pstmt.setInt(1, assignment.getId());
                        pstmt.setInt(2, userId);
                        ResultSet rs = pstmt.executeQuery();
                        if (rs.next()) {
                            return Optional.of(new Submission(
                                    rs.getInt("id"),
                                    rs.getInt("assignment_id"),
                                    rs.getInt("student_id"),
                                    rs.getString("file_path"),
                                    rs.getTimestamp("submission_date").toLocalDateTime(),
                                    rs.getInt("grade"),
                                    rs.getString("feedback"),
                                    rs.getBoolean("published")
                            ));
                        }
                        return Optional.empty();
                    }
            );

            if (submissionOpt.isPresent() && submissionOpt.get().isPublished()) {
                Submission submission = submissionOpt.get();

                VBox resultsBox = new VBox(10);
                resultsBox.setStyle("-fx-background-color: #e8f0fe; -fx-padding: 15; -fx-background-radius: 8px;");

                Label resultsTitle = new Label("Your Results");
                resultsTitle.getStyleClass().add("subtitle");

                Label gradeLabel = new Label("Grade: " + submission.getGrade() + "/" + assignment.getMaxPoints());
                gradeLabel.setStyle("-fx-text-fill: #202124; -fx-font-size: 16px;");

                Label feedbackLabel = new Label("Feedback: " + submission.getFeedback());
                feedbackLabel.setStyle("-fx-text-fill: #3c4043; -fx-font-size: 14px;");
                feedbackLabel.setWrapText(true);

                resultsBox.getChildren().addAll(resultsTitle, gradeLabel, feedbackLabel);
                content.getChildren().add(resultsBox);
            }
        } catch (Exception e) {
            DatabaseUtil.logger.severe("Error checking for published results: " + e.getMessage());
        }

        content.getChildren().addAll(description, instructions, grading, submissionTitle, submissionBox);
        scrollContent.setContent(content);
        assignmentView.getChildren().addAll(header, scrollContent);

        mainContainer.getChildren().setAll(assignmentView);
    }

    private void submitAssignment(Assignment assignment, File file) {
        // Save file to server
        String uploadDir = "submissions/";
        new File(uploadDir).mkdirs();
        String destPath = uploadDir + assignment.getId() + "_" + userId + "_" + file.getName();

        try {
            Files.copy(file.toPath(), Paths.get(destPath), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            showAlert("Error", "Failed to save file: " + e.getMessage());
            return;
        }

        DatabaseUtil.executeUpdate(
                "INSERT INTO submissions (assignment_id, student_id, file_path, submission_date) VALUES (?, ?, ?, ?)",
                pstmt -> {
                    try {
                        pstmt.setInt(1, assignment.getId());
                        pstmt.setInt(2, userId);
                        pstmt.setString(3, destPath);
                        pstmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        // Update assignment status
        assignment.setSubmissionStatus("Submitted");
        assignment.setSubmittedFilePath(destPath);
        showAssignmentDetails(assignment);
        showAlert("Success", "Assignment submitted successfully");
    }

    private void unsubmitAssignment(Assignment assignment) {
        try (Connection conn = getConnection()) {
            String query = "DELETE FROM submissions WHERE assignment_id = ? AND student_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, assignment.getId());
                pstmt.setInt(2, userId);
                pstmt.executeUpdate();
            }

            assignment.setSubmissionStatus("Not Submitted");
            assignment.setSubmittedFilePath(null);
            showAssignmentDetails(assignment);
        } catch (SQLException ex) {
            showAlert("Error", "Failed to unsubmit assignment: " + ex.getMessage());
        }
    }

    private void setupLecturerDashboard() {
        lecturerDashboard.getChildren().clear();
        lecturerDashboard.getStyleClass().add("content");

        // Header
        HBox header = new HBox();
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);

        lecturerWelcomeLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #202124;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Refresh button
        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("button-secondary");
        refreshButton.setOnAction(e -> {
            initializeLecturerData();
            setupLecturerDashboard();
        });

        // Create a user profile menu button with dropdown
        MenuButton profileButton = new MenuButton(fullName);
        profileButton.setGraphic(new Circle(15, Color.LIGHTGRAY));
        profileButton.getStyleClass().add("button-secondary");

        MenuItem profileItem = new MenuItem("View Profile");
        MenuItem settingsItem = new MenuItem("Settings");
        MenuItem logoutItem = new MenuItem("Logout");
        logoutItem.setOnAction(e -> handleLogout());

        profileButton.getItems().addAll(profileItem, settingsItem, new SeparatorMenuItem(), logoutItem);

        header.getChildren().addAll(lecturerWelcomeLabel, spacer, refreshButton, profileButton);

        // Dashboard stats
        HBox statsContainer = new HBox(20);
        statsContainer.setPadding(new Insets(0, 0, 20, 0));

        VBox coursesStat = createDashboardCard("My Courses", String.valueOf(lecturerCourses.size()), "#1a73e8");
        VBox assignmentsStat = createDashboardCard("Assignments",
                String.valueOf(assignments.size()), "#34a853");
        VBox studentsStat = createDashboardCard("Students",
                String.valueOf(getStudentCount()), "#fbbc04");

        statsContainer.getChildren().addAll(coursesStat, assignmentsStat, studentsStat);

        // Main content with tabs
        lecturerTabPane = new TabPane();
        lecturerTabPane.getStyleClass().add("tab-pane");

        // Courses Tab
        Tab coursesTab = new Tab("My Courses");
        coursesTab.setClosable(false);
        setupLecturerCoursesTab(coursesTab);

        // Management Tab
        Tab managementTab = new Tab("Course Management");
        managementTab.setClosable(false);
        setupManagementTab(managementTab);

        // Grading Tab
        Tab gradingTab = new Tab("Grading");
        gradingTab.setClosable(false);
        setupGradingTab(gradingTab);

        lecturerTabPane.getTabs().addAll(coursesTab, managementTab, gradingTab);
        lecturerDashboard.getChildren().addAll(header, statsContainer, lecturerTabPane);
    }

    private int getStudentCount() {
        try {
            return DatabaseUtil.executeQuery(
                    "SELECT COUNT(DISTINCT e.student_id) FROM enrollments e " +
                            "JOIN courses c ON e.course_code = c.course_code " +
                            "WHERE c.teacher = ?",
                    pstmt -> {
                        pstmt.setString(1, fullName);
                        ResultSet rs = pstmt.executeQuery();
                        return rs.next() ? rs.getInt(1) : 0;
                    }
            );
        } catch (Exception e) {
            return 0;
        }
    }

    private void setupLecturerCoursesTab(Tab coursesTab) {
        VBox content = new VBox(20);
        content.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 8px;");

        Label title = new Label("Your Courses");
        title.getStyleClass().add("subtitle");

        FlowPane coursesGrid = new FlowPane();
        coursesGrid.setHgap(20);
        coursesGrid.setVgap(20);
        coursesGrid.setPrefWrapLength(1000);

        for (Course course : lecturerCourses) {
            VBox courseCard = new VBox(10);
            courseCard.getStyleClass().add("card");
            courseCard.setPrefWidth(300);

            Label code = new Label(course.getCourseCode());
            code.getStyleClass().add("card-title");

            Label name = new Label(course.getCourseName());
            name.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 14px;");

            Label progress = new Label("Average Progress: " + course.getProgress() + "%");
            progress.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 12px;");

            Button manageButton = new Button("Manage Course");
            manageButton.getStyleClass().add("button-primary");
            manageButton.setOnAction(e -> showLecturerCourseView(course));

            courseCard.getChildren().addAll(code, name, progress, manageButton);

            // Add hover effect
            courseCard.setOnMouseEntered(e -> {
                courseCard.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 0, 4, 6, 0);");
            });

            courseCard.setOnMouseExited(e -> {
                courseCard.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 0, 2, 4, 0);");
            });

            coursesGrid.getChildren().add(courseCard);
        }

        content.getChildren().addAll(title, coursesGrid);
        coursesTab.setContent(new ScrollPane(content));
    }

    private void setupManagementTab(Tab managementTab) {
        VBox content = new VBox(20);
        content.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 8px;");

        Label title = new Label("Add New Course");
        title.getStyleClass().add("subtitle");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(20));

        newCourseCodeField = new TextField();
        newCourseNameField = new TextField();
        newCourseTeacherField = new TextField(fullName);
        newCourseTeacherField.setDisable(true);

        form.add(new Label("Course Code:"), 0, 0);
        form.add(newCourseCodeField, 1, 0);
        form.add(new Label("Course Name:"), 0, 1);
        form.add(newCourseNameField, 1, 1);
        form.add(new Label("Teacher:"), 0, 2);
        form.add(newCourseTeacherField, 1, 2);

        Button addButton = new Button("Add Course");
        addButton.getStyleClass().add("button-success");
        addButton.setOnAction(e -> {
            if (newCourseCodeField.getText().isEmpty() || newCourseNameField.getText().isEmpty()) {
                showAlert("Error", "Please fill in all fields");
                return;
            }

            Course newCourse = new Course(
                    newCourseCodeField.getText(),
                    newCourseNameField.getText(),
                    newCourseTeacherField.getText(),
                    0
            );

            try (Connection conn = getConnection()) {
                String query = "INSERT INTO courses (course_code, course_name, teacher, progress) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setString(1, newCourse.getCourseCode());
                    pstmt.setString(2, newCourse.getCourseName());
                    pstmt.setString(3, newCourse.getTeacher());
                    pstmt.setInt(4, newCourse.getProgress());
                    pstmt.executeUpdate();
                }
                lecturerCourses.add(newCourse);
                newCourseCodeField.clear();
                newCourseNameField.clear();
                showAlert("Success", "Course added successfully");
            } catch (SQLException ex) {
                showAlert("Error", "Failed to add course: " + ex.getMessage());
            }
        });

        content.getChildren().addAll(title, form, addButton);
        managementTab.setContent(new ScrollPane(content));
    }

    private void setupGradingTab(Tab gradingTab) {
        VBox content = new VBox(20);
        content.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 8px;");

        // Title section
        Label title = new Label("Grade Assignments");
        title.getStyleClass().add("subtitle");

        // Course selection
        ComboBox<Course> courseComboBox = createCourseComboBox();

        // Submissions table
        TableView<Submission> submissionsTable = createSubmissionsTable();

        // Grade form with publishing controls
        VBox gradeForm = createGradeForm(submissionsTable);

        // Publish controls (separate section)
        VBox publishControls = createPublishControls(submissionsTable, courseComboBox);

        // Load submissions when course is selected
        courseComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newCourse) -> {
            if (newCourse != null) {
                try {
                    loadSubmissionsForCourse(newCourse, submissionsTable);
                } catch (Exception e) {
                    showAlert("Error", "Failed to load submissions: " + e.getMessage());
                    submissionsTable.getItems().clear();
                }
            } else {
                submissionsTable.getItems().clear();
            }
        });

        // Layout organization
        VBox courseSelectionBox = new VBox(10, new Label("Select Course:"), courseComboBox);
        VBox submissionsBox = new VBox(10, new Label("Submissions:"), submissionsTable);

        content.getChildren().addAll(
                title,
                courseSelectionBox,
                submissionsBox,
                gradeForm,
                new Separator(),
                publishControls
        );

        gradingTab.setContent(new ScrollPane(content));
    }

    private ComboBox<Course> createCourseComboBox() {
        ComboBox<Course> comboBox = new ComboBox<>();
        comboBox.setPromptText("Select a course");
        comboBox.setItems(lecturerCourses);
        comboBox.setConverter(new StringConverter<Course>() {
            @Override
            public String toString(Course course) {
                if (course == null || course.getCourseCode() == null || course.getCourseName() == null) {
                    return "";
                }
                return course.getCourseCode() + " - " + course.getCourseName();
            }

            @Override
            public Course fromString(String string) {
                return null;
            }
        });
        return comboBox;
    }

    private TableView<Submission> createSubmissionsTable() {
        TableView<Submission> table = new TableView<>();
        table.getStyleClass().add("table-view");

        // Student column
        TableColumn<Submission, String> studentCol = new TableColumn<>("Student");
        studentCol.setCellValueFactory(cellData -> {
            try {
                String studentName = getStudentName(cellData.getValue().getStudentId());
                return new SimpleStringProperty(studentName != null ? studentName : "Unknown");
            } catch (Exception e) {
                return new SimpleStringProperty("Unknown");
            }
        });

        // File column
        TableColumn<Submission, String> fileCol = new TableColumn<>("File");
        fileCol.setCellValueFactory(cellData -> {
            String path = cellData.getValue().getFilePath();
            return new SimpleStringProperty(path != null ? new File(path).getName() : "No file");
        });

        // Date column
        TableColumn<Submission, String> dateCol = new TableColumn<>("Submitted");
        dateCol.setCellValueFactory(cellData -> {
            LocalDateTime date = cellData.getValue().getSubmissionDate();
            return new SimpleStringProperty(date != null ?
                    date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")) : "");
        });

        // Grade column
        TableColumn<Submission, String> gradeCol = new TableColumn<>("Grade");
        gradeCol.setCellValueFactory(cellData -> {
            int grade = cellData.getValue().getGrade();
            int maxPoints = getAssignmentMaxPoints(cellData.getValue().getAssignmentId());
            return new SimpleStringProperty(grade + "/" + maxPoints);
        });

        // Published status column
        TableColumn<Submission, String> publishedCol = new TableColumn<>("Status");
        publishedCol.setCellValueFactory(cellData -> {
            boolean published = cellData.getValue().isPublished();
            return new SimpleStringProperty(published ? "Published" : "Not Published");
        });

        table.getColumns().addAll(studentCol, fileCol, dateCol, gradeCol, publishedCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return table;
    }

    private VBox createGradeForm(TableView<Submission> submissionsTable) {
        VBox form = new VBox(10);
        form.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 8px;");

        Label selectedLabel = new Label("No submission selected");
        selectedLabel.getStyleClass().add("subtitle");

        // Feedback area
        TextArea feedbackArea = new TextArea();
        feedbackArea.setPromptText("Enter feedback here...");
        feedbackArea.setPrefHeight(100);

        // Grade controls
        HBox gradeControls = new HBox(10);
        gradeControls.setAlignment(Pos.CENTER_LEFT);

        Slider gradeSlider = new Slider(0, 100, 0);
        gradeSlider.setShowTickLabels(true);
        gradeSlider.setShowTickMarks(true);
        gradeSlider.setMajorTickUnit(10);
        gradeSlider.setMinorTickCount(5);
        gradeSlider.setPrefWidth(200);

        Label gradeLabel = new Label("Grade: 0/100");
        gradeLabel.setStyle("-fx-font-weight: bold;");
        gradeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            gradeLabel.setText("Grade: " + Math.round(newVal.doubleValue()) + "/100");
        });

        gradeControls.getChildren().addAll(gradeLabel, gradeSlider);

        // Submit button
        Button submitGradeButton = new Button("Save Grade");
        submitGradeButton.getStyleClass().add("button-primary");
        submitGradeButton.setDisable(true);

        // Selection listener
        submissionsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newSubmission) -> {
            if (newSubmission != null) {
                try {
                    String assignmentTitle = getAssignmentTitle(newSubmission.getAssignmentId());
                    selectedLabel.setText("Grading: " + assignmentTitle);
                    feedbackArea.setText(newSubmission.getFeedback() != null ? newSubmission.getFeedback() : "");
                    gradeSlider.setValue(newSubmission.getGrade());
                    submitGradeButton.setDisable(false);
                } catch (SQLException e) {
                    selectedLabel.setText("Grading: Assignment");
                    submitGradeButton.setDisable(true);
                }
            } else {
                selectedLabel.setText("No submission selected");
                feedbackArea.clear();
                gradeSlider.setValue(0);
                submitGradeButton.setDisable(true);
            }
        });

        // Submit action
        submitGradeButton.setOnAction(e -> {
            Submission selected = submissionsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    updateGrade(selected, (int) gradeSlider.getValue(), feedbackArea.getText());
                    submissionsTable.refresh();
                    showAlert("Success", "Grade saved successfully");
                } catch (Exception ex) {
                    showAlert("Error", "Failed to save grade: " + ex.getMessage());
                }
            }
        });

        form.getChildren().addAll(
                selectedLabel,
                new Label("Feedback:"),
                feedbackArea,
                new Label("Grade:"),
                gradeControls,
                submitGradeButton
        );

        return form;
    }

    private VBox createPublishControls(TableView<Submission> submissionsTable, ComboBox<Course> courseComboBox) {
        VBox publishBox = new VBox(10);
        publishBox.setStyle("-fx-background-color: #f0f7ff; -fx-padding: 15; -fx-background-radius: 8px;");

        Label publishTitle = new Label("Publish Results");
        publishTitle.getStyleClass().add("subtitle");

        // Individual publish button
        Button publishSingleButton = new Button("Publish Selected");
        publishSingleButton.getStyleClass().add("button-success");
        publishSingleButton.setDisable(true);

        // Bulk publish button
        Button publishAllButton = new Button("Publish All for Course");
        publishAllButton.getStyleClass().add("button-success");
        publishAllButton.setDisable(true);

        // Enable/disable based on selection
        submissionsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newSubmission) -> {
            publishSingleButton.setDisable(newSubmission == null);
        });

        courseComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newCourse) -> {
            publishAllButton.setDisable(newCourse == null);
        });

        // Publish single action
        publishSingleButton.setOnAction(e -> {
            Submission selected = submissionsTable.getSelectionModel().getSelectedItem();
            if (selected != null && showConfirmation("Publish Results",
                    "Publish this student's results?")) {
                publishResults(Collections.singletonList(selected), submissionsTable);
            }
        });

        // Publish all action
        publishAllButton.setOnAction(e -> {
            Course course = courseComboBox.getSelectionModel().getSelectedItem();
            if (course != null && showConfirmation("Publish All Results",
                    "Publish all results for " + course.getCourseCode() + "?")) {
                publishAllForCourse(course, submissionsTable);
            }
        });

        publishBox.getChildren().addAll(
                publishTitle,
                new HBox(10, publishSingleButton, publishAllButton)
        );

        return publishBox;
    }

    private void publishResults(List<Submission> submissions, TableView<Submission> table) {
        try {
            // Update database
            DatabaseUtil.executeUpdate(
                    "UPDATE submissions SET published = true, publish_date = NOW() WHERE id IN (" +
                            submissions.stream().map(s -> "?").collect(Collectors.joining(",")) + ")",
                    pstmt -> {
                        for (int i = 0; i < submissions.size(); i++) {
                            try {
                                pstmt.setInt(i + 1, submissions.get(i).getId());
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
            );

            // Update UI
            submissions.forEach(s -> s.setPublished(true));
            table.refresh();
            showAlert("Success", "Published " + submissions.size() + " result(s)");

        } catch (Exception ex) {
            showAlert("Error", "Failed to publish results: " + ex.getMessage());
        }
    }

    private void publishAllForCourse(Course course, TableView<Submission> table) {
        try {
            // Get all gradable submissions for course
            List<Submission> toPublish = DatabaseUtil.executeQuery(
                    "SELECT s.* FROM submissions s " +
                            "JOIN assignments a ON s.assignment_id = a.id " +
                            "WHERE a.course_code = ? AND s.grade IS NOT NULL AND s.published = false",
                    pstmt -> {
                        pstmt.setString(1, course.getCourseCode());
                        ResultSet rs = pstmt.executeQuery();

                        List<Submission> results = new ArrayList<>();
                        while (rs.next()) {
                            results.add(new Submission(
                                    rs.getInt("id"),
                                    rs.getInt("assignment_id"),
                                    rs.getInt("student_id"),
                                    rs.getString("file_path"),
                                    rs.getTimestamp("submission_date").toLocalDateTime(),
                                    rs.getInt("grade"),
                                    rs.getString("feedback"),
                                    rs.getBoolean("published")
                            ));
                        }
                        return results;
                    }
            );

            if (!toPublish.isEmpty()) {
                publishResults(toPublish, table);
            } else {
                showAlert("Information", "No gradable submissions found for this course");
            }
        } catch (Exception ex) {
            showAlert("Error", "Failed to load submissions: " + ex.getMessage());
        }
    }

    private int getAssignmentMaxPoints(int assignmentId) {
        try (Connection conn = getConnection()) {
            String query = "SELECT max_points FROM assignments WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, assignmentId);
                ResultSet rs = pstmt.executeQuery();
                return rs.next() ? rs.getInt("max_points") : 100;
            }
        } catch (SQLException e) {
            return 100; // Default value if error occurs
        }
    }

    private boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void loadSubmissionsForCourse(Course course, TableView<Submission> table) {
        try {
            ObservableList<Submission> submissions = DatabaseUtil.executeQuery(
                    "SELECT s.* FROM submissions s JOIN assignments a ON s.assignment_id = a.id WHERE a.course_code = ?",
                    pstmt -> {
                        pstmt.setString(1, course.getCourseCode());
                        ResultSet rs = pstmt.executeQuery();

                        ObservableList<Submission> results = FXCollections.observableArrayList();
                        while (rs.next()) {
                            Submission submission = new Submission(
                                    rs.getInt("id"),
                                    rs.getInt("assignment_id"),
                                    rs.getInt("student_id"),
                                    rs.getString("file_path"),
                                    rs.getTimestamp("submission_date").toLocalDateTime(),
                                    rs.getInt("grade"),
                                    rs.getString("feedback"),
                                    rs.getBoolean("published")
                            );
                            // Set student name if needed
                            submission.setStudentName(getStudentName(rs.getInt("student_id")));
                            results.add(submission);
                        }
                        return results;
                    }
            );
            table.setItems(submissions);
        } catch (RuntimeException e) {
            showAlert("Database Error", "Failed to load submissions: " + e.getMessage());
            table.setItems(FXCollections.emptyObservableList());
        }
    }

    private void checkForPublishedResults() {
        if ("student".equals(role)) {
            try {
                int newResults = DatabaseUtil.executeQuery(
                        "SELECT COUNT(*) FROM submissions s " +
                                "JOIN assignments a ON s.assignment_id = a.id " +
                                "JOIN enrollments e ON a.course_code = e.course_code " +
                                "WHERE e.student_id = ? AND s.published = true AND s.grade IS NOT NULL " +
                                "AND (s.last_notified IS NULL OR s.last_notified < s.publish_date)",
                        pstmt -> {
                            pstmt.setInt(1, userId);
                            ResultSet rs = pstmt.executeQuery();
                            return rs.next() ? rs.getInt(1) : 0;
                        }
                );

                if (newResults > 0) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("New Results Available");
                        alert.setHeaderText("You have " + newResults + " new graded assignments");
                        alert.setContentText("Check your courses to view the results.");
                        alert.showAndWait();

                        // Update notification status
                        DatabaseUtil.executeUpdate(
                                "UPDATE submissions SET last_notified = NOW() " +
                                        "WHERE id IN (SELECT s.id FROM submissions s " +
                                        "JOIN assignments a ON s.assignment_id = a.id " +
                                        "JOIN enrollments e ON a.course_code = e.course_code " +
                                        "WHERE e.student_id = ? AND s.published = true)",
                                pstmt -> {
                                    try {
                                        pstmt.setInt(1, userId);
                                    } catch (SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                        );
                    });
                }
            } catch (Exception e) {
                DatabaseUtil.logger.severe("Error checking for published results: " + e.getMessage());
            }
        }
    }

    private void checkForNewContent() {
        if ("student".equals(role)) {
            try {
                int newAnnouncements = DatabaseUtil.executeQuery(
                        "SELECT COUNT(*) FROM announcements a " +
                                "WHERE (a.course_code IS NULL OR a.course_code IN " +
                                "(SELECT course_code FROM enrollments WHERE student_id = ?)) " +
                                "AND a.date > (SELECT COALESCE(MAX(last_checked), '1970-01-01') " +
                                "FROM student_notifications WHERE student_id = ?",
                        pstmt -> {
                            pstmt.setInt(1, userId);
                            pstmt.setInt(2, userId);
                            ResultSet rs = pstmt.executeQuery();
                            return rs.next() ? rs.getInt(1) : 0;
                        }
                );

                if (newAnnouncements > 0) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("New Content Available");
                        alert.setHeaderText("You have " + newAnnouncements + " new announcements");
                        alert.setContentText("Check your dashboard to view the latest updates.");
                        alert.showAndWait();
                    });
                }

                // Update last checked time
                DatabaseUtil.executeUpdate(
                        "INSERT INTO student_notifications (student_id, last_checked) " +
                                "VALUES (?, CURRENT_TIMESTAMP) " +
                                "ON CONFLICT (student_id) DO UPDATE SET last_checked = CURRENT_TIMESTAMP",
                        pstmt -> {
                            try {
                                pstmt.setInt(1, userId);
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                );
            } catch (Exception e) {
                DatabaseUtil.logger.severe("Error checking for new content: " + e.getMessage());
            }
        }
    }

    private void setupContentChecker() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.minutes(5), e -> checkForNewContent()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void updateGrade(Submission submission, int grade, String feedback) {
        try (Connection conn = getConnection()) {
            String query = "UPDATE submissions SET grade = ?, feedback = ?, grade_updated = NOW() WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, grade);
                pstmt.setString(2, feedback);
                pstmt.setInt(3, submission.getId());
                pstmt.executeUpdate();
            }
            submission.setGrade(grade);
            submission.setFeedback(feedback);
        } catch (SQLException e) {
            showAlert("Error", "Failed to update grade: " + e.getMessage());
        }
    }

    private String getStudentName(int studentId) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT full_name FROM users WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, studentId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("full_name");
                }
            }
        } catch (SQLException e) {
            DatabaseUtil.logger.severe("Failed to get student name: " + e.getMessage());
        }
        return "Unknown";
    }

    private String getAssignmentTitle(int assignmentId) throws SQLException {
        try (Connection conn = getConnection()) {
            String query = "SELECT title FROM assignments WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, assignmentId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("title");
                }
            }
        }
        return "Assignment";
    }

    private String getCourseCodeForAssignment(int assignmentId) throws SQLException {
        try (Connection conn = getConnection()) {
            String query = "SELECT course_code FROM assignments WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, assignmentId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("course_code");
                }
            }
        }
        return "Unknown";
    }
    private void showLecturerCourseView(Course course) {
        lecturerCourseManagementView = new VBox(20);
        lecturerCourseManagementView.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 20;");

        HBox header = new HBox(10);
        header.getStyleClass().add("header");

        Button backButton = new Button("← Back to Courses");
        backButton.getStyleClass().add("button-secondary");
        backButton.setOnAction(e -> lecturerDashboard.getChildren().set(1, lecturerTabPane));

        Label title = new Label("Managing: " + course.getCourseCode() + " - " + course.getCourseName());
        title.getStyleClass().add("title");

        header.getChildren().addAll(backButton, title);

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("tab-pane");

        // Materials Tab
        Tab materialsTab = new Tab("Materials");
        materialsTab.setClosable(false);
        VBox materialsContent = new VBox(20);
        materialsContent.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 8px;");

        Button uploadMaterialButton = new Button("Upload Material");
        uploadMaterialButton.getStyleClass().add("button-primary");
        FileChooser materialFileChooser = new FileChooser();

        uploadMaterialButton.setOnAction(e -> {
            File materialFile = materialFileChooser.showOpenDialog(null);
            if (materialFile != null) {
                try {
                    String materialsDir = "course_materials/";
                    new File(materialsDir).mkdirs();

                    String destPath = materialsDir + course.getCourseCode() + "_" + materialFile.getName();
                    Files.copy(materialFile.toPath(), Paths.get(destPath), StandardCopyOption.REPLACE_EXISTING);

                    try (Connection conn = getConnection()) {
                        String query = "INSERT INTO course_materials (title, file_path, course_code) VALUES (?, ?, ?)";
                        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                            pstmt.setString(1, materialFile.getName());
                            pstmt.setString(2, destPath);
                            pstmt.setString(3, course.getCourseCode());
                            pstmt.executeUpdate();
                        }
                    }

                    addAnnouncement("New Material: " + materialFile.getName(),
                            "A new material '" + materialFile.getName() + "' has been uploaded for " + course.getCourseCode(),
                            null, course.getCourseCode());

                    Label materialLabel = new Label("Uploaded: " + materialFile.getName());
                    materialLabel.setStyle("-fx-text-fill: #3c4043; -fx-font-size: 14px;");
                    materialsContent.getChildren().add(materialLabel);
                } catch (IOException | SQLException ex) {
                    showAlert("Error", "Failed to upload material: " + ex.getMessage());
                }
            }
        });

        VBox materialsList = new VBox(10);
        List<CourseMaterial> courseMaterials = getMaterialsForCourse(course.getCourseCode());
        for (CourseMaterial material : courseMaterials) {
            HBox materialItem = new HBox(10);
            materialItem.setAlignment(Pos.CENTER_LEFT);

            Label materialLabel = new Label(material.getTitle() + " (Uploaded: " + material.getUploadDate() + ")");
            materialLabel.setStyle("-fx-text-fill: #3c4043; -fx-font-size: 14px;");

            Button deleteButton = new Button("Delete");
            deleteButton.getStyleClass().add("button-danger");
            deleteButton.setOnAction(e -> {
                try (Connection conn = getConnection()) {
                    String query = "DELETE FROM course_materials WHERE id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                        pstmt.setInt(1, material.getId());
                        pstmt.executeUpdate();
                    }
                    materialsContent.getChildren().remove(materialItem);
                    showAlert("Success", "Material deleted successfully");
                } catch (SQLException ex) {
                    showAlert("Error", "Failed to delete material: " + ex.getMessage());
                }
            });

            materialItem.getChildren().addAll(materialLabel, deleteButton);
            materialsList.getChildren().add(materialItem);
        }

        materialsContent.getChildren().addAll(uploadMaterialButton, new Separator(), materialsList);
        materialsTab.setContent(new ScrollPane(materialsContent));

        // Assignments Tab
        Tab assignmentsTab = new Tab("Assignments");
        assignmentsTab.setClosable(false);
        VBox assignmentsContent = new VBox(20);
        assignmentsContent.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-background-radius: 8px;");

        // List of existing assignments
        ListView<Assignment> assignmentsList = new ListView<>();
        ObservableList<Assignment> courseAssignments = FXCollections.observableArrayList();

        // Load existing assignments from database
        try (Connection conn = getConnection()) {
            String query = "SELECT * FROM assignments WHERE course_code = ? ORDER BY due_date ASC";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, course.getCourseCode());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    courseAssignments.add(new Assignment(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getString("instructions"),
                            rs.getString("due_date"),
                            "Not Submitted",
                            rs.getString("course_code"),
                            rs.getInt("max_points"),
                            rs.getString("grading_criteria")
                    ));
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to load assignments: " + e.getMessage());
        }

        assignmentsList.setItems(courseAssignments);
        assignmentsList.setCellFactory(param -> new ListCell<Assignment>() {
            @Override
            protected void updateItem(Assignment assignment, boolean empty) {
                super.updateItem(assignment, empty);
                if (empty || assignment == null) {
                    setText(null);
                } else {
                    setText(assignment.getTitle() + " - Due: " + assignment.getDueDate());
                }
            }
        });

        // Form to create new assignment
        GridPane assignmentForm = new GridPane();
        assignmentForm.setHgap(10);
        assignmentForm.setVgap(10);

        TextField titleField = new TextField();
        TextArea descriptionArea = new TextArea();
        descriptionArea.setPrefRowCount(3);
        TextArea instructionsArea = new TextArea();
        instructionsArea.setPrefRowCount(3);
        DatePicker dueDatePicker = new DatePicker(LocalDate.now().plusDays(7));
        TextField maxPointsField = new TextField("100");
        TextArea gradingCriteriaArea = new TextArea();
        gradingCriteriaArea.setPrefRowCount(3);

        assignmentForm.add(new Label("Title:"), 0, 0);
        assignmentForm.add(titleField, 1, 0);
        assignmentForm.add(new Label("Description:"), 0, 1);
        assignmentForm.add(descriptionArea, 1, 1);
        assignmentForm.add(new Label("Instructions:"), 0, 2);
        assignmentForm.add(instructionsArea, 1, 2);
        assignmentForm.add(new Label("Due Date:"), 0, 3);
        assignmentForm.add(dueDatePicker, 1, 3);
        assignmentForm.add(new Label("Max Points:"), 0, 4);
        assignmentForm.add(maxPointsField, 1, 4);
        assignmentForm.add(new Label("Grading Criteria:"), 0, 5);
        assignmentForm.add(gradingCriteriaArea, 1, 5);

        Button createAssignmentButton = new Button("Create Assignment");
        createAssignmentButton.getStyleClass().add("button-success");
        createAssignmentButton.setOnAction(e -> {
            if (titleField.getText().isEmpty() || dueDatePicker.getValue() == null) {
                showAlert("Error", "Title and due date are required");
                return;
            }

            try {
                int maxPoints = Integer.parseInt(maxPointsField.getText());
                if (maxPoints <= 0) {
                    showAlert("Error", "Max points must be greater than 0");
                    return;
                }

                try (Connection conn = getConnection()) {
                    String query = "INSERT INTO assignments (title, description, instructions, due_date, " +
                            "course_code, max_points, grading_criteria) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setString(1, titleField.getText());
                        pstmt.setString(2, descriptionArea.getText());
                        pstmt.setString(3, instructionsArea.getText());
                        pstmt.setDate(4, java.sql.Date.valueOf(dueDatePicker.getValue()));
                        pstmt.setString(5, course.getCourseCode());
                        pstmt.setInt(6, maxPoints);
                        pstmt.setString(7, gradingCriteriaArea.getText());
                        pstmt.executeUpdate();

                        // Get the generated ID
                        ResultSet rs = pstmt.getGeneratedKeys();
                        if (rs.next()) {
                            Assignment newAssignment = new Assignment(
                                    rs.getInt(1),
                                    titleField.getText(),
                                    descriptionArea.getText(),
                                    instructionsArea.getText(),
                                    dueDatePicker.getValue().toString(),
                                    "Not Submitted",
                                    course.getCourseCode(),
                                    maxPoints,
                                    gradingCriteriaArea.getText()
                            );
                            courseAssignments.add(newAssignment);

                            // Create an announcement about the new assignment
                            addAnnouncement("New Assignment: " + titleField.getText(),
                                    "A new assignment '" + titleField.getText() + "' has been posted for " +
                                            course.getCourseCode() + ". Due date: " + dueDatePicker.getValue().toString(),
                                    rs.getInt(1), course.getCourseCode());
                        }
                    }

                    // Clear form
                    titleField.clear();
                    descriptionArea.clear();
                    instructionsArea.clear();
                    dueDatePicker.setValue(LocalDate.now().plusDays(7));
                    maxPointsField.setText("100");
                    gradingCriteriaArea.clear();

                    showAlert("Success", "Assignment created successfully");
                }
            } catch (SQLException ex) {
                showAlert("Error", "Failed to create assignment: " + ex.getMessage());
            } catch (NumberFormatException ex) {
                showAlert("Error", "Max points must be a valid number");
            }
        });

        assignmentsContent.getChildren().addAll(
                new Label("Existing Assignments:"),
                assignmentsList,
                new Separator(),
                new Label("Create New Assignment:"),
                assignmentForm,
                createAssignmentButton
        );

        assignmentsTab.setContent(new ScrollPane(assignmentsContent));

        tabs.getTabs().addAll(materialsTab, assignmentsTab);
        lecturerCourseManagementView.getChildren().addAll(header, tabs);

        ScrollPane scrollPane = new ScrollPane(lecturerCourseManagementView);
        scrollPane.setFitToWidth(true);
        lecturerDashboard.getChildren().set(1, scrollPane);
    }
    private void addAnnouncement(String title, String content, Integer assignmentId, String courseCode) {
        try (Connection conn = getConnection()) {
            String query = "INSERT INTO announcements (title, content, date, assignment_id, course_code) " +
                    "VALUES (?, ?, CURRENT_DATE, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, title);
                pstmt.setString(2, content);
                if (assignmentId != null) {
                    pstmt.setInt(3, assignmentId);
                } else {
                    pstmt.setNull(3, Types.INTEGER);
                }
                pstmt.setString(4, courseCode);
                pstmt.executeUpdate();
            }

            // Refresh announcements list
            if ("student".equals(role)) {
                initializeStudentData();
            } else if ("lecturer".equals(role)) {
                initializeLecturerData();
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to create announcement: " + e.getMessage());
        }
    }
    private void handleLogout() {
        userId = 0;
        fullName = null;
        role = null;
        courses.clear();
        assignments.clear();
        announcements.clear();

        usernameField.clear();
        passwordField.clear();
        errorLabel.setText("");

        root.setCenter(loginContainer);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static class DatabaseUtil {
        static final Logger logger = Logger.getLogger(DatabaseUtil.class.getName());

        public static Connection getConnection() throws SQLException {
            try {
                return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            } catch (SQLException e) {
                logger.severe("Database connection failed: " + e.getMessage());
                throw e;
            }
        }

        public static <T> T executeQuery(String sql, SQLFunction<PreparedStatement, T> processor) {
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                return processor.apply(pstmt);
            } catch (SQLException e) {
                logger.severe("Query execution failed: " + e.getMessage());
                throw new RuntimeException("Database error", e);
            }
        }

        public static void executeUpdate(String sql, Consumer<PreparedStatement> preparer) {
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                preparer.accept(pstmt);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Update execution failed: " + e.getMessage());
                throw new RuntimeException("Database error", e);
            }
        }

        @FunctionalInterface
        public interface SQLFunction<T, R> {
            R apply(T t) throws SQLException;
        }
    }
}

 class Course {
    private final StringProperty courseCode;
    private final StringProperty courseName;
    private final StringProperty teacher;
    private final IntegerProperty progress;

    public Course(String courseCode, String courseName, String teacher, int progress) {
        this.courseCode = new SimpleStringProperty(courseCode);
        this.courseName = new SimpleStringProperty(courseName);
        this.teacher = new SimpleStringProperty(teacher);
        this.progress = new SimpleIntegerProperty(progress);
    }

    // Property getters
    public StringProperty courseCodeProperty() { return courseCode; }
    public StringProperty courseNameProperty() { return courseName; }
    public StringProperty teacherProperty() { return teacher; }
    public IntegerProperty progressProperty() { return progress; }

    // Regular getters
    public String getCourseCode() { return courseCode.get(); }
    public String getCourseName() { return courseName.get(); }
    public String getTeacher() { return teacher.get(); }
    public int getProgress() { return progress.get(); }

    // Setters
    public void setCourseCode(String code) { this.courseCode.set(code); }
    public void setCourseName(String name) { this.courseName.set(name); }
    public void setTeacher(String teacher) { this.teacher.set(teacher); }
    public void setProgress(int progress) { this.progress.set(progress); }
}

class Announcement {
    private final int id;
    private final String title;
    private final String content;
    private final String date;
    private final Integer assignmentId;
    private final String courseCode;

    public Announcement(int id, String title, String content, String date, Integer assignmentId, String courseCode) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.date = date;
        this.assignmentId = assignmentId;
        this.courseCode = courseCode;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getDate() { return date; }
    public Integer getAssignmentId() { return assignmentId; }
    public String getCourseCode() { return courseCode; }
}

class Assignment {
    private int id;
    private final String title;
    private final String description;
    private final String instructions;
    private final String dueDate;
    private String submissionStatus;
    private final String courseCode;
    private final int maxPoints;
    private final String gradingCriteria;
    private String submittedFilePath;

    public Assignment(int id, String title, String description, String instructions,
                      String dueDate, String submissionStatus, String courseCode,
                      int maxPoints, String gradingCriteria) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.instructions = instructions;
        this.dueDate = dueDate;
        this.submissionStatus = submissionStatus;
        this.courseCode = courseCode;
        this.maxPoints = maxPoints;
        this.gradingCriteria = gradingCriteria;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getInstructions() { return instructions; }
    public String getDueDate() { return dueDate; }
    public String getSubmissionStatus() { return submissionStatus; }
    public void setSubmissionStatus(String status) { this.submissionStatus = status; }
    public String getCourseCode() { return courseCode; }
    public int getMaxPoints() { return maxPoints; }
    public String getGradingCriteria() { return gradingCriteria; }
    public String getSubmittedFilePath() { return submittedFilePath; }
    public void setSubmittedFilePath(String path) { this.submittedFilePath = path; }
    public void setId(int id) { this.id = id; }
}

class CourseMaterial {
    private final int id;
    private final String title;
    private final String filePath;
    private final String courseCode;
    private final String uploadDate;

    public CourseMaterial(int id, String title, String filePath, String courseCode, String uploadDate) {
        this.id = id;
        this.title = title;
        this.filePath = filePath;
        this.courseCode = courseCode;
        this.uploadDate = uploadDate;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getFilePath() { return filePath; }
    public String getCourseCode() { return courseCode; }
    public String getUploadDate() { return uploadDate; }
}

class Submission {
    private final int id;
    private final int assignmentId;
    private final int studentId;
    private String studentName;
    private final String filePath;
    private final LocalDateTime submissionDate;
    private int grade;
    private String feedback;
    private boolean published;

    public Submission(int id, int assignmentId, int studentId, String filePath,
                      LocalDateTime submissionDate, int grade, String feedback, boolean published) {
        this.id = id;
        this.assignmentId = assignmentId;
        this.studentId = studentId;
        this.filePath = filePath;
        this.submissionDate = submissionDate;
        this.grade = grade;
        this.feedback = feedback;
        this.published = published;
    }

    public int getId() { return id; }
    public int getAssignmentId() { return assignmentId; }
    public int getStudentId() { return studentId; }
    public String getStudentName() {return studentName;}
    public String getFilePath() { return filePath; }
    public LocalDateTime getSubmissionDate() { return submissionDate; }
    public int getGrade() { return grade; }
    public String getFeedback() { return feedback; }
    public boolean isPublished() { return published; }

    public void setGrade(int grade) { this.grade = grade; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public void setStudentName(String name) {this.studentName = name;}
    public void setPublished(boolean published) { this.published = published; }
}