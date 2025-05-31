package com.example.simplelms;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Node;
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
import java.io.FileNotFoundException;
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
import javafx.util.Pair;
import javafx.util.StringConverter;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.kernel.pdf.PdfDocument;

import java.io.FileNotFoundException;
import java.awt.print.PrinterJob;
import java.awt.print.Printable;
import java.awt.print.PageFormat;
import java.awt.Graphics;

import static org.postgresql.PGProperty.PASSWORD;
import static org.postgresql.jdbc.EscapedFunctions.USER;

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
    private String selectedCourseCode;
    // Database configuration
    private static final String DB_URL = "jdbc:postgresql://ep-lively-bush-a8n03ooe-pooler.eastus2.azure.neon.tech/Learning_management";
    private static final String DB_USER = "Learning_management_owner";
    private static final String DB_PASSWORD = "npg_9l8oIXPDaYMO";


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
            Statement stmt = conn.createStatement();
            // Create MCQ questions table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS mcq_questions (" +
                            "id SERIAL PRIMARY KEY, " +
                            "question_text TEXT NOT NULL, " +
                            "course_code VARCHAR(10), " +
                            "created_by INTEGER NOT NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (course_code) REFERENCES courses(course_code), " +
                            "FOREIGN KEY (created_by) REFERENCES users(id))"
            );
// Create student_transcripts table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS student_transcripts (" +
                            "id SERIAL PRIMARY KEY, " +
                            "student_id INTEGER NOT NULL, " +
                            "transcript_content TEXT NOT NULL, " +
                            "generated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "last_viewed TIMESTAMP, " +
                            "FOREIGN KEY (student_id) REFERENCES users(id))"
            );
            // Create MCQ options table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS mcq_options (" +
                            "id SERIAL PRIMARY KEY, " +
                            "question_id INTEGER NOT NULL, " +
                            "option_text TEXT NOT NULL, " +
                            "is_correct BOOLEAN DEFAULT FALSE, " +
                            "FOREIGN KEY (question_id) REFERENCES mcq_questions(id) ON DELETE CASCADE)"
            );stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS mcq_student_answers (" +
                            "id SERIAL PRIMARY KEY, " +
                            "student_id INTEGER NOT NULL, " +
                            "question_id INTEGER NOT NULL, " +
                            "option_id INTEGER NOT NULL, " +
                            "answered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (student_id) REFERENCES users(id), " +
                            "FOREIGN KEY (question_id) REFERENCES mcq_questions(id), " +
                            "FOREIGN KEY (option_id) REFERENCES mcq_options(id))"
            );

            // Create forum categories table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS forum_categories (" +
                            "id SERIAL PRIMARY KEY, " +
                            "name VARCHAR(100) NOT NULL, " +
                            "description TEXT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // Create forum posts table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS forum_posts (" +
                            "id SERIAL PRIMARY KEY, " +
                            "category_id INTEGER NOT NULL, " +
                            "user_id INTEGER NOT NULL, " +
                            "title VARCHAR(200) NOT NULL, " +
                            "content TEXT NOT NULL, " +
                            "media_path TEXT, " +
                            "media_type VARCHAR(50), " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (category_id) REFERENCES forum_categories(id), " +
                            "FOREIGN KEY (user_id) REFERENCES users(id))"
            );

            // Create forum comments table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS forum_comments (" +
                            "id SERIAL PRIMARY KEY, " +
                            "post_id INTEGER NOT NULL, " +
                            "user_id INTEGER NOT NULL, " +
                            "content TEXT NOT NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (post_id) REFERENCES forum_posts(id), " +
                            "FOREIGN KEY (user_id) REFERENCES users(id))"
            );

            // Create reports table (fixed missing parenthesis)
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS reports (" +
                            "id SERIAL PRIMARY KEY, " +
                            "student_id INTEGER NOT NULL, " +
                            "course_code VARCHAR(10) NOT NULL, " +
                            "report_content TEXT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (student_id) REFERENCES users(id), " +
                            "FOREIGN KEY (course_code) REFERENCES courses(course_code))"
            );

            // Create certifications table (fixed missing parenthesis)
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS certifications (" +
                            "id SERIAL PRIMARY KEY, " +
                            "student_id INTEGER NOT NULL, " +
                            "course_code VARCHAR(10) NOT NULL, " +
                            "certification_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (student_id) REFERENCES users(id), " +
                            "FOREIGN KEY (course_code) REFERENCES courses(course_code))"
            );

            // Create transcripts table (fixed missing parenthesis)
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS transcripts (" +
                            "id SERIAL PRIMARY KEY, " +
                            "student_id INTEGER NOT NULL, " +
                            "course_code VARCHAR(10) NOT NULL, " +
                            "grade INTEGER, " +
                            "FOREIGN KEY (student_id) REFERENCES users(id), " +
                            "FOREIGN KEY (course_code) REFERENCES courses(course_code))"
            );

            // Add default forum categories if they don't exist
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM forum_categories");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate(
                        "INSERT INTO forum_categories (name, description) VALUES " +
                                "('General Discussion', 'Discuss anything related to the LMS'), " +
                                "('Course Questions', 'Ask questions about specific courses'), " +
                                "('Technical Support', 'Get help with technical issues')"
                );
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void addGlobalStyles(Scene scene) {
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
    }
    private void generateReport(int studentId) {
        StringBuilder reportContent = new StringBuilder();
        reportContent.append("Report for Student ID: ").append(studentId).append("\n\n");

        try (Connection conn = getConnection()) {
            // Fetch courses
            String coursesQuery = "SELECT c.course_code, c.course_name FROM courses c " +
                    "JOIN enrollments e ON c.course_code = e.course_code " +
                    "WHERE e.student_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(coursesQuery)) {
                pstmt.setInt(1, studentId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String courseCode = rs.getString("course_code");
                    String courseName = rs.getString("course_name");
                    reportContent.append("Course: ").append(courseName).append(" (").append(courseCode).append(")\n");

                    // Fetch assignments and grades for the course
                    String assignmentsQuery = "SELECT a.title, t.grade FROM assignments a " +
                            "JOIN transcripts t ON a.id = t.assignment_id " +
                            "WHERE t.student_id = ? AND a.course_code = ?";
                    try (PreparedStatement assignmentStmt = conn.prepareStatement(assignmentsQuery)) {
                        assignmentStmt.setInt(1, studentId);
                        assignmentStmt.setString(2, courseCode);
                        ResultSet assignmentRs = assignmentStmt.executeQuery();

                        while (assignmentRs.next()) {
                            String assignmentTitle = assignmentRs.getString("title");
                            int grade = assignmentRs.getInt("grade");
                            reportContent.append("  Assignment: ").append(assignmentTitle)
                                    .append(" - Grade: ").append(grade).append("\n");
                        }
                    }
                    reportContent.append("\n"); // Add space between courses
                }
            }

            // Save to database
            saveReportToDatabase(studentId, reportContent.toString());
        } catch (SQLException e) {
            showAlert("Error", "Failed to generate report: " + e.getMessage());
        }
    }



    private void saveReportToDatabase(int studentId, String reportContent) {
        // Insert report into the reports table
        try (Connection conn = getConnection()) {
            String query = "INSERT INTO reports (student_id, report_content) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, studentId);
                pstmt.setString(2, reportContent);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to save report: " + e.getMessage());
        }
    }
    private void issueCertification(int studentId, String courseCode) {
        try (Connection conn = getConnection()) {
            String query = "INSERT INTO certifications (student_id, course_code) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, studentId);
                pstmt.setString(2, courseCode);
                pstmt.executeUpdate();
            }
            showAlert("Success", "Certification issued for course: " + courseCode);
        } catch (SQLException e) {
            showAlert("Error", "Failed to issue certification: " + e.getMessage());
        }
    }
    private Map<String, Integer> calculateClassPerformance(String courseCode) {
        Map<String, Integer> performanceMetrics = new HashMap<>();
        int totalScore = 0;
        int totalSubmissions = 0;

        try (Connection conn = getConnection()) {
            // Calculate average score and total submissions
            String query = "SELECT grade FROM transcripts WHERE course_code = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, courseCode);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    int grade = rs.getInt("grade");
                    totalScore += grade;
                    totalSubmissions++;
                }
            }

            // Calculate average
            int averageScore = totalSubmissions == 0 ? 0 : totalScore / totalSubmissions;
            performanceMetrics.put("Average Score", averageScore);
            performanceMetrics.put("Total Submissions", totalSubmissions);
        } catch (SQLException e) {
            showAlert("Error", "Failed to calculate class performance: " + e.getMessage());
        }

        return performanceMetrics;
    }
    private void initializeLecturerAccounts() {
        try (Connection conn = getConnection()) {
            String[][] accounts = {
                    // Lecturers
                    {"Dr. Motletle", "motletle@university.edu", "kmotletle", "prof123", "lecturer"},
                    {"Prof. Kiddah", "kiddah@university.edu", "rkiddah", "teach400", "lecturer"},
                    {"Ratsebe", "ratsebe@university.edu", "ratsebe", "ratsebek", "lecturer"},
                    // Manager
                    {"Admin Manager", "manager@university.edu", "admin", "admin123", "manager"}
            };

            for (String[] account : accounts) {
                String checkQuery = "SELECT id FROM users WHERE username = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                    checkStmt.setString(1, account[2]);
                    ResultSet rs = checkStmt.executeQuery();
                    if (!rs.next()) {
                        String insertQuery = "INSERT INTO users (full_name, email, username, password, role) " +
                                "VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                            insertStmt.setString(1, account[0]);
                            insertStmt.setString(2, account[1]);
                            insertStmt.setString(3, account[2]);
                            insertStmt.setString(4, account[3]);
                            insertStmt.setString(5, account[4]);
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to initialize accounts: " + e.getMessage());
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

        // Role ComboBox
        ComboBox<String> roleComboBox = new ComboBox<>();
        roleComboBox.getItems().addAll("Student", "Lecturer", "Manager");
        roleComboBox.setValue("Student");
        roleComboBox.setStyle(
                "-fx-background-color: #e3f0ff;" +
                        "-fx-border-color: #5a9bf6;" +
                        "-fx-border-radius: 5px;" +
                        "-fx-background-radius: 5px;" +
                        "-fx-padding: 5 10 5 10;" +
                        "-fx-font-size: 14px;" +
                        "-fx-text-fill: #1a3c72;"
        );

        // Username Field with fixed focus effect
        usernameField.setStyle(
                "-fx-background-color: #e3f0ff;" +
                        "-fx-border-color: #5a9bf6;" +
                        "-fx-border-radius: 5px;" +
                        "-fx-background-radius: 5px;" +
                        "-fx-padding: 8 10 8 10;" +
                        "-fx-font-size: 14px;" +
                        "-fx-text-fill: #1a3c72;"
        );
        usernameField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                usernameField.setEffect(new DropShadow(10, Color.web("#1e90ff")));
                usernameField.setStyle(usernameField.getStyle() + "-fx-border-color: #1e90ff;");
            } else {
                usernameField.setEffect(null);
                usernameField.setStyle(usernameField.getStyle().replace("-fx-border-color: #1e90ff;", "-fx-border-color: #5a9bf6;"));
            }
        });

        // Password Field with fixed focus effect
        passwordField.setStyle(
                "-fx-background-color: #e3f0ff;" +
                        "-fx-border-color: #5a9bf6;" +
                        "-fx-border-radius: 5px;" +
                        "-fx-background-radius: 5px;" +
                        "-fx-padding: 8 10 8 10;" +
                        "-fx-font-size: 14px;" +
                        "-fx-text-fill: #1a3c72;"
        );
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

        // Login Button
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
            handleLogin(selectedRole);
        });

        // Register Button
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

        // Error Label
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
                    } else if ("manager".equals(role)) {
                        loginAsManager();
                    }
                } else {
                    errorLabel.setText("Invalid username or password");
                }
            }
        } catch (SQLException e) {
            errorLabel.setText("Database error: " + e.getMessage());
        }
    }

    private void loginAsManager() {
        welcomeLabel.setText("Welcome, " + fullName + " (Manager)");
        setupManagerDashboard();
        root.setCenter(mainContainer);
        mainContainer.getChildren().setAll(studentDashboard); // Reusing studentDashboard container
    }

    private void setupManagerDashboard() {
        studentDashboard.getChildren().clear();
        studentDashboard.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 20;");

        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(10);
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0.5, 0, 3);");

        welcomeLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #333;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(e -> handleLogout());

        header.getChildren().addAll(welcomeLabel, spacer, logoutButton);

        // TabPane for manager functions
        TabPane managerTabPane = new TabPane();

        // Transcripts Tab
        Tab transcriptsTab = new Tab("Generate Transcripts");
        setupTranscriptsTab(transcriptsTab);

        // Certificates Tab
        Tab certificatesTab = new Tab("Issue Certificates");
        setupCertificatesTab(certificatesTab);

        // Reports Tab
        Tab reportsTab = new Tab("Generate Reports");
        setupReportsTab(reportsTab);

        managerTabPane.getTabs().addAll(transcriptsTab, certificatesTab, reportsTab);

        studentDashboard.getChildren().addAll(header, managerTabPane);
    }

    private void setupTranscriptsTab(Tab tab) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        Label title = new Label("Generate Student Transcripts");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Student selection
        ComboBox<Student> studentCombo = new ComboBox<>();
        studentCombo.setPromptText("Select a student");
        try {
            List<Student> students = DatabaseUtil.executeQuery(
                    "SELECT id, full_name FROM users WHERE role = 'student' ORDER BY full_name",
                    pstmt -> {
                        ResultSet rs = pstmt.executeQuery();
                        List<Student> results = new ArrayList<>();
                        while (rs.next()) {
                            results.add(new Student(rs.getInt("id"), rs.getString("full_name")));
                        }
                        return results;
                    }
            );
            studentCombo.getItems().addAll(students);
        } catch (Exception e) {
            showAlert("Error", "Failed to load students: " + e.getMessage());
        }

        // Generate button
        Button generateBtn = new Button("Generate Transcript");
        generateBtn.setOnAction(e -> {
            Student selected = studentCombo.getValue();
            if (selected != null) {
                generateTranscript(selected.getId());
            } else {
                showAlert("Error", "Please select a student");
            }
        });

        content.getChildren().addAll(title, new Label("Select Student:"), studentCombo, generateBtn);
        tab.setContent(content);
    }

    private void setupCertificatesTab(Tab tab) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        Label title = new Label("Issue Certificates");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Student selection
        ComboBox<Student> studentCombo = new ComboBox<>();
        studentCombo.setPromptText("Select a student");

        // Course selection
        ComboBox<Course> courseCombo = new ComboBox<>();
        courseCombo.setPromptText("Select a course");

        // Load students and courses
        try {
            // Load students
            List<Student> students = DatabaseUtil.executeQuery(
                    "SELECT id, full_name FROM users WHERE role = 'student' ORDER BY full_name",
                    pstmt -> {
                        ResultSet rs = pstmt.executeQuery();
                        List<Student> results = new ArrayList<>();
                        while (rs.next()) {
                            results.add(new Student(rs.getInt("id"), rs.getString("full_name")));
                        }
                        return results;
                    }
            );
            studentCombo.getItems().addAll(students);

            // Load courses
            List<Course> courses = DatabaseUtil.executeQuery(
                    "SELECT course_code, course_name FROM courses ORDER BY course_name",
                    pstmt -> {
                        ResultSet rs = pstmt.executeQuery();
                        List<Course> results = new ArrayList<>();
                        while (rs.next()) {
                            results.add(new Course(rs.getString("course_code"), rs.getString("course_name"), "", 0));
                        }
                        return results;
                    }
            );
            courseCombo.getItems().addAll(courses);
        } catch (Exception e) {
            showAlert("Error", "Failed to load data: " + e.getMessage());
        }

        // Issue button
        Button issueBtn = new Button("Issue Certificate");
        issueBtn.setOnAction(e -> {
            Student student = studentCombo.getValue();
            Course course = courseCombo.getValue();
            if (student != null && course != null) {
                issueCertification(student.getId(), course.getCourseCode());
            } else {
                showAlert("Error", "Please select both student and course");
            }
        });

        content.getChildren().addAll(
                title,
                new Label("Select Student:"), studentCombo,
                new Label("Select Course:"), courseCombo,
                issueBtn
        );
        tab.setContent(content);
    }
    class Student {
        private final int id;
        private final String name;

        public Student(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() { return id; }
        public String getName() { return name; }

        @Override
        public String toString() {
            return name;
        }
    }

    private void setupReportsTab(Tab tab) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        Label title = new Label("Generate Course Reports");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Course selection
        ComboBox<Course> courseCombo = new ComboBox<>();
        courseCombo.setPromptText("Select a course");
        try {
            List<Course> courses = DatabaseUtil.executeQuery(
                    "SELECT course_code, course_name FROM courses ORDER BY course_name",
                    pstmt -> {
                        ResultSet rs = pstmt.executeQuery();
                        List<Course> results = new ArrayList<>();
                        while (rs.next()) {
                            results.add(new Course(rs.getString("course_code"), rs.getString("course_name"), "", 0));
                        }
                        return results;
                    }
            );
            courseCombo.getItems().addAll(courses);
        } catch (Exception e) {
            showAlert("Error", "Failed to load courses: " + e.getMessage());
        }

        // Generate button
        Button generateBtn = new Button("Generate Report");
        generateBtn.setOnAction(e -> {
            Course course = courseCombo.getValue();
            if (course != null) {
                generateCourseReport(course.getCourseCode());
            } else {
                showAlert("Error", "Please select a course");
            }
        });

        content.getChildren().addAll(title, new Label("Select Course:"), courseCombo, generateBtn);
        tab.setContent(content);
    }

    private void generateCourseReport(String courseCode) {
        try (Connection conn = getConnection()) {
            // Get course info
            String courseQuery = "SELECT course_name, teacher FROM courses WHERE course_code = ?";
            String courseName = "";
            String teacher = "";

            try (PreparedStatement pstmt = conn.prepareStatement(courseQuery)) {
                pstmt.setString(1, courseCode);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    courseName = rs.getString("course_name");
                    teacher = rs.getString("teacher");
                }
            }

            // Get enrollment count
            int enrollmentCount = 0;
            String enrollmentQuery = "SELECT COUNT(*) FROM enrollments WHERE course_code = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(enrollmentQuery)) {
                pstmt.setString(1, courseCode);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    enrollmentCount = rs.getInt(1);
                }
            }

            // Get assignment stats
            StringBuilder assignmentStats = new StringBuilder();
            String assignmentQuery = "SELECT a.title, COUNT(s.id) as submissions, AVG(s.grade) as avg_grade " +
                    "FROM assignments a LEFT JOIN submissions s ON a.id = s.assignment_id " +
                    "WHERE a.course_code = ? GROUP BY a.title";
            try (PreparedStatement pstmt = conn.prepareStatement(assignmentQuery)) {
                pstmt.setString(1, courseCode);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    assignmentStats.append(rs.getString("title"))
                            .append(" - Submissions: ").append(rs.getInt("submissions"))
                            .append(", Avg Grade: ").append(rs.getDouble("avg_grade")).append("\n");
                }
            }

            // Build report
            String report = "Course Report: " + courseName + " (" + courseCode + ")\n" +
                    "Instructor: " + teacher + "\n" +
                    "Enrollment: " + enrollmentCount + " students\n\n" +
                    "Assignment Statistics:\n" + assignmentStats.toString();

            // Show report
            TextArea reportArea = new TextArea(report);
            reportArea.setEditable(false);
            reportArea.setWrapText(true);

            Stage reportStage = new Stage();
            reportStage.setTitle("Course Report - " + courseCode);
            reportStage.setScene(new Scene(new StackPane(reportArea), 600, 400));
            reportStage.show();

        } catch (SQLException e) {
            showAlert("Error", "Failed to generate report: " + e.getMessage());
        }
    }
    private void loginAsStudent() {
        initializeStudentData();
        welcomeLabel.setText("Welcome, " + fullName + " (Student ID: " + userId + ")");
        setupStudentDashboard();

        root.setCenter(mainContainer);
        mainContainer.getChildren().setAll(studentDashboard);
    }
    private void generateTranscript(int studentId) {
        try (Connection conn = getConnection()) {
            // Get comprehensive student information
            String studentQuery = "SELECT u.full_name, u.email, u.username, s.program, s.enrollment_date " +
                    "FROM users u LEFT JOIN student_details s ON u.id = s.user_id " +
                    "WHERE u.id = ?";

            StringBuilder transcript = new StringBuilder();
            String studentName = "";
            String program = "";
            String enrollmentDate = "";

            try (PreparedStatement pstmt = conn.prepareStatement(studentQuery)) {
                pstmt.setInt(1, studentId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    studentName = rs.getString("full_name");
                    program = rs.getString("program") != null ? rs.getString("program") : "Not specified";
                    enrollmentDate = rs.getString("enrollment_date") != null ?
                            rs.getString("enrollment_date") : "Unknown";

                    transcript.append("OFFICIAL ACADEMIC TRANSCRIPT\n");
                    transcript.append("============================\n\n");
                    transcript.append(String.format("%-20s: %s\n", "Student Name", studentName));
                    transcript.append(String.format("%-20s: %d\n", "Student ID", studentId));
                    transcript.append(String.format("%-20s: %s\n", "Program", program));
                    transcript.append(String.format("%-20s: %s\n", "Enrollment Date", enrollmentDate));
                    transcript.append("\n");
                }
            }

            // Get all academic records
            transcript.append("ACADEMIC RECORD\n");
            transcript.append("---------------\n");
            transcript.append(String.format("%-10s %-40s %-10s %-10s %-15s\n",
                    "Code", "Course", "Grade", "Credits", "Status"));
            transcript.append("--------------------------------------------------------------------\n");

            String academicQuery = "SELECT c.course_code, c.course_name, t.grade, c.credits, " +
                    "CASE WHEN cert.course_code IS NOT NULL THEN 'Completed' ELSE 'In Progress' END as status " +
                    "FROM transcripts t " +
                    "JOIN courses c ON t.course_code = c.course_code " +
                    "LEFT JOIN certifications cert ON t.course_code = cert.course_code AND t.student_id = cert.student_id " +
                    "WHERE t.student_id = ? ORDER BY c.course_code";

            double totalGradePoints = 0;
            int totalCredits = 0;

            try (PreparedStatement pstmt = conn.prepareStatement(academicQuery)) {
                pstmt.setInt(1, studentId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    String courseCode = rs.getString("course_code");
                    String courseName = rs.getString("course_name");
                    int grade = rs.getInt("grade");
                    int credits = rs.getInt("credits");
                    String status = rs.getString("status");

                    transcript.append(String.format("%-10s %-40s %-10d %-10d %-15s\n",
                            courseCode,
                            courseName.length() > 35 ? courseName.substring(0, 35) + "..." : courseName,
                            grade,
                            credits,
                            status));

                    // Calculate GPA (assuming grade is out of 100)
                    if (status.equals("Completed")) {
                        totalGradePoints += (grade / 20.0) * credits; // Convert to 5.0 scale
                        totalCredits += credits;
                    }
                }
            }

            // Calculate GPA
            double gpa = totalCredits > 0 ? totalGradePoints / totalCredits : 0.0;
            transcript.append("\n");
            transcript.append(String.format("%-20s: %.2f/5.00\n", "Cumulative GPA", gpa));
            transcript.append("\n");

            // Add certifications section
            transcript.append("CERTIFICATIONS EARNED\n");
            transcript.append("---------------------\n");

            String certQuery = "SELECT c.course_code, co.course_name, c.certification_date " +
                    "FROM certifications c JOIN courses co ON c.course_code = co.course_code " +
                    "WHERE c.student_id = ? ORDER BY c.certification_date";

            boolean hasCertifications = false;

            try (PreparedStatement pstmt = conn.prepareStatement(certQuery)) {
                pstmt.setInt(1, studentId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    hasCertifications = true;
                    transcript.append(String.format("%s - %s (Awarded: %s)\n",
                            rs.getString("course_code"),
                            rs.getString("course_name"),
                            rs.getString("certification_date")));
                }
            }

            if (!hasCertifications) {
                transcript.append("No certifications earned yet\n");
            }

            // Add footer
            transcript.append("\n\n");
            transcript.append("OFFICIAL DOCUMENT - " + LocalDate.now() + "\n");
            transcript.append("Learning Management System\n");
            transcript.append("University of Example\n");

            // Save the transcript to the database
            saveTranscriptToDatabase(studentId, transcript.toString());

            // Display to manager or student
            if (userId == studentId) {
                showTranscript(transcript.toString());
            } else {
                // For manager view, show more options
                Stage transcriptStage = new Stage();
                transcriptStage.setTitle("Transcript for " + studentName);

                TextArea transcriptArea = new TextArea(transcript.toString());
                transcriptArea.setEditable(false);
                transcriptArea.setWrapText(true);
                transcriptArea.setStyle("-fx-font-family: monospace;");

                Button printButton = new Button("Print");
                printButton.setOnAction(e -> printTranscript(transcript.toString()));

                Button saveButton = new Button("Save as PDF");
                String finalStudentName = studentName;
                saveButton.setOnAction(e -> saveAsPDF(transcript.toString(), finalStudentName));

                HBox buttonBox = new HBox(10, printButton, saveButton);
                buttonBox.setAlignment(Pos.CENTER);

                VBox container = new VBox(10, transcriptArea, buttonBox);
                container.setPadding(new Insets(15));

                transcriptStage.setScene(new Scene(container, 700, 600));
                transcriptStage.show();
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to generate transcript: " + e.getMessage());
        }
    }

    private void saveAsPDF(String content, String finalStudentName) {
        // Define the filename with path inside user's Documents folder
        String filename = Paths.get(System.getProperty("user.home"), "Documents", finalStudentName + "_Transcript.pdf").toString();

        try {
            // Initialize PDF writer with full path
            PdfWriter writer = new PdfWriter(filename);
            // Initialize PDF document
            PdfDocument pdfDoc = new PdfDocument(writer);
            // Initialize document
            Document document = new Document(pdfDoc);

            // Add content to PDF
            document.add(new Paragraph(content));

            // Close document
            document.close();

            System.out.println("PDF saved successfully: " + filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("Error creating PDF: " + e.getMessage());
        }
    }
    private void printTranscript(String content) {
        PrinterJob printerJob = PrinterJob.getPrinterJob();

        // Set printable content
        printerJob.setPrintable(new Printable() {
            @Override
            public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
                if (pageIndex > 0) {
                    return Printable.NO_SUCH_PAGE;
                }
                graphics.translate((int) pageFormat.getImageableX(), (int) pageFormat.getImageableY());

                // Draw the text content
                graphics.drawString(content, 100, 100);
                // Alternatively, for multi-line text, you can split by lines and draw each line

                return Printable.PAGE_EXISTS;
            }
        });

        // Show print dialog
        if (printerJob.printDialog()) {
            try {
                printerJob.print();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Printing failed: " + e.getMessage());
            }
        }
    }
    private void saveTranscriptToDatabase(int studentId, String transcriptContent) {
        try (Connection conn = getConnection()) {
            // First delete any existing transcript for this student
            String deleteQuery = "DELETE FROM student_transcripts WHERE student_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteQuery)) {
                pstmt.setInt(1, studentId);
                pstmt.executeUpdate();
            }

            // Insert the new transcript
            String insertQuery = "INSERT INTO student_transcripts (student_id, transcript_content, generated_date) " +
                    "VALUES (?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                pstmt.setInt(1, studentId);
                pstmt.setString(2, transcriptContent);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to save transcript: " + e.getMessage());
        }
    }

    private void showTranscript(String transcript) {
        TextArea transcriptArea = new TextArea(transcript);
        transcriptArea.setEditable(false);
        transcriptArea.setWrapText(true);
        transcriptArea.setStyle("-fx-font-family: monospace;");

        Stage transcriptStage = new Stage();
        transcriptStage.setTitle("Your Transcript");
        transcriptStage.setScene(new Scene(new StackPane(transcriptArea), 600, 400));
        transcriptStage.show();
    }
    private void checkForNewTranscripts() {
        if ("student".equals(role)) {
            try {
                boolean hasNewTranscript = DatabaseUtil.executeQuery(
                        "SELECT COUNT(*) FROM student_transcripts " +
                                "WHERE student_id = ? AND " +
                                "(last_viewed IS NULL OR last_viewed < generated_date)",
                        pstmt -> {
                            pstmt.setInt(1, userId);
                            ResultSet rs = pstmt.executeQuery();
                            return rs.next() && rs.getInt(1) > 0;
                        }
                );

                if (hasNewTranscript) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("New Transcript Available");
                        alert.setHeaderText("Your transcript has been updated");
                        alert.setContentText("Check the Transcript section in your dashboard to view it.");
                        alert.showAndWait();

                        // Update last viewed time
                        DatabaseUtil.executeUpdate(
                                "UPDATE student_transcripts SET last_viewed = CURRENT_TIMESTAMP " +
                                        "WHERE student_id = ?",
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
                DatabaseUtil.logger.severe("Error checking for new transcripts: " + e.getMessage());
            }
        }
    }
    private void loginAsLecturer() {
        initializeLecturerData();
        lecturerWelcomeLabel.setText("Welcome, " + fullName);
        setupLecturerDashboard();

        root.setCenter(lecturerDashboard);
    }
    private void setupStudentDashboard() {
        studentDashboard.getChildren().clear();

        // Main dashboard styling
        studentDashboard.setStyle(
                "-fx-background-color: #e3f2fd;" +  // Lighter blue for a clean look
                        "-fx-padding: 20;" +
                        "-fx-spacing: 15;" // space between components
        );

        // --- Header Pane ---
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(10);
        header.setPadding(new Insets(15));
        header.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 10;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0.2, 0, 2);" +
                        "-fx-border-color: #ddd;" +
                        "-fx-border-radius: 10;"
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

        // --- Stats Cards Container ---
        HBox statsContainer = new HBox(20);
        statsContainer.setPadding(new Insets(20));
        statsContainer.setAlignment(Pos.CENTER);
        statsContainer.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 10;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0.2, 0, 1);" +
                        "-fx-border-color: #ccc;" +
                        "-fx-border-radius: 10;"
        );

        VBox coursesStat = createDashboardCard("Enrolled Courses", String.valueOf(courses.size()), "#1e88e5");
        VBox assignmentsStat = createDashboardCard("Active Assignments",
                String.valueOf(assignments.stream().filter(a -> "Not Submitted".equals(a.getSubmissionStatus())).count()),
                "#43a047");
        VBox announcementsStat = createDashboardCard("New Announcements",
                String.valueOf(announcements.size()), "#fdd835");

        // Add borders and background to individual cards (optional enhancement)
        for (VBox card : new VBox[]{coursesStat, assignmentsStat, announcementsStat}) {
            card.setStyle(
                    "-fx-background-color: #ffffff;" +
                            "-fx-background-radius: 8;" +
                            "-fx-border-color: #ddd;" +
                            "-fx-border-radius: 8;" +
                            "-fx-padding: 15;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.03), 3, 0.1, 0, 1);"
            );
        }

        statsContainer.getChildren().addAll(coursesStat, assignmentsStat, announcementsStat);

        // --- TabPane ---
        TabPane studentTabPane = new TabPane();
        studentTabPane.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 10;" +
                        "-fx-padding: 10;" +
                        "-fx-border-color: #ddd;" +
                        "-fx-border-radius: 10;" +
                        "-fx-tab-min-width: 120;" +
                        "-fx-tab-max-height: 40;"
        );

        Tab dashboardTab = new Tab("Dashboard");
        dashboardTab.setClosable(false);
        setupStudentDashboardTab(dashboardTab);
        applyTabContentStyle(dashboardTab);

        Tab coursesTab = new Tab("My Courses");
        coursesTab.setClosable(false);
        setupStudentCoursesTab(coursesTab);
        applyTabContentStyle(coursesTab);

        Tab gradesTab = new Tab("My Grades");
        gradesTab.setClosable(false);
        setupStudentGradesTab(gradesTab);
        applyTabContentStyle(gradesTab);

        Tab assessmentTab = new Tab("Assessment");
        assessmentTab.setClosable(false);
        setupStudentAssessmentTab(assessmentTab);
        applyTabContentStyle(assessmentTab);

        Tab transcriptTab = new Tab("Transcript");
        transcriptTab.setClosable(false);
        setupTranscriptTab(transcriptTab);
        applyTabContentStyle(transcriptTab);

        Tab forumTab = new Tab("Forum");
        setupStudentForumTab(forumTab);
        forumTab.setClosable(false);
        applyTabContentStyle(forumTab);

        studentTabPane.getTabs().addAll(dashboardTab, coursesTab, gradesTab, assessmentTab, forumTab, transcriptTab);

        // --- Bottom Button Section ---
        Button reportButton = new Button("Generate Report");
        Button certificationButton = new Button("Issue Certification");
        Button transcriptButton = new Button("View Transcript");

        reportButton.setOnAction(e -> generateReport(userId));
        certificationButton.setOnAction(e -> issueCertification(userId, selectedCourseCode));
        transcriptButton.setOnAction(e -> generateTranscript(userId));

        HBox buttonBox = new HBox(10, reportButton, certificationButton, transcriptButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10));
        buttonBox.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 10;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 5, 0.1, 0, 1);" +
                        "-fx-border-color: #ddd;" +
                        "-fx-border-radius: 10;"
        );

        checkForNewTranscripts();

        // --- Final layout setup ---
        studentDashboard.getChildren().addAll(header, statsContainer, studentTabPane, buttonBox);
    }

    private void applyTabContentStyle(Tab tab) {
        if (tab.getContent() instanceof Region region) {
            region.setStyle(
                    "-fx-background-color: white;" +
                            "-fx-border-color: #ccc;" +
                            "-fx-border-radius: 10;" +
                            "-fx-background-radius: 10;" +
                            "-fx-padding: 15;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 4, 0.1, 0, 1);"
            );
        }
    }

    private void setupTranscriptTab(Tab transcriptTab) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: white; -fx-background-radius: 8px;");

        Label title = new Label("Academic Transcript");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        TextArea transcriptArea = new TextArea();
        transcriptArea.setEditable(false);
        transcriptArea.setWrapText(true);
        transcriptArea.setStyle("-fx-font-family: monospace;");
        transcriptArea.setPrefHeight(500);

        Button loadTranscriptButton = new Button("Load Transcript");
        loadTranscriptButton.setOnAction(e -> {
            try {
                String transcript = DatabaseUtil.executeQuery(
                        "SELECT transcript_content FROM student_transcripts " +
                                "WHERE student_id = ? ORDER BY generated_date DESC LIMIT 1",
                        pstmt -> {
                            pstmt.setInt(1, userId);
                            ResultSet rs = pstmt.executeQuery();
                            return rs.next() ? rs.getString("transcript_content") : "No transcript available";
                        }
                );
                transcriptArea.setText(transcript);

                // Update last viewed time
                DatabaseUtil.executeUpdate(
                        "UPDATE student_transcripts SET last_viewed = CURRENT_TIMESTAMP " +
                                "WHERE student_id = ? AND (last_viewed IS NULL OR last_viewed < generated_date)",
                        pstmt -> {
                            try {
                                pstmt.setInt(1, userId);
                            } catch (SQLException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                );
            } catch (Exception ex) {
                transcriptArea.setText("Error loading transcript: " + ex.getMessage());
            }
        });

        Button printTranscriptButton = new Button("Print Transcript");
        printTranscriptButton.setOnAction(e -> {
            if (!transcriptArea.getText().isEmpty()) {
                // Implement printing functionality here
                showAlert("Print", "Transcript printing functionality would be implemented here");
            }
        });

        HBox buttonBox = new HBox(10, loadTranscriptButton, printTranscriptButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(title, buttonBox, transcriptArea);
        transcriptTab.setContent(new ScrollPane(content));
    }

    private void setupStudentForumTab(Tab forumTab) {
        VBox forumLayout = new VBox(15);
        forumLayout.setPadding(new Insets(20));
        forumLayout.setStyle("-fx-background-color: #f0f0f0;");

        // Messages display
        ListView<String> forumMessages = new ListView<>();
        forumMessages.setPrefHeight(400);
        refreshStudentForumMessages(forumMessages);

        // Input area
        TextArea messageInput = new TextArea();
        messageInput.setPromptText("Write your message...");
        messageInput.setPrefRowCount(3);

        // Send button
        Button sendButton = new Button("Send");
        sendButton.setStyle("-fx-background-color: #1e90ff; -fx-text-fill: white;");
        sendButton.setOnAction(e -> {
            String message = messageInput.getText().trim();
            if (!message.isEmpty()) {
                saveForumMessage(message);
                messageInput.clear();
                refreshStudentForumMessages(forumMessages);
            } else {
                showAlert("Please write a message before sending.");
            }
        });

        // Assemble layout
        forumLayout.getChildren().addAll(new Label("Forum Messages"), forumMessages,
                new Label("New Message"), messageInput, sendButton);

        forumTab.setContent(forumLayout);
    }
    private void refreshStudentForumMessages(ListView<String> forumMessages) {
        List<String> messages = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT author, message, timestamp FROM forum_posts ORDER BY timestamp DESC";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String author = rs.getString("author");
                String message = rs.getString("message");
                String timestamp = rs.getString("timestamp");
                messages.add("[" + timestamp + "] " + author + ": " + message);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error loading forum messages.");
        }
        Platform.runLater(() -> {
            forumMessages.getItems().setAll(messages);
        });
    }

    private void saveForumMessage(String message) {
        String author = fullName; // Or retrieve from current user context
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "INSERT INTO forum_posts (author, message, timestamp) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, author);
            pstmt.setString(2, message);
            pstmt.setString(3, timestamp);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error saving your message.");
        }
    }

    private void setupStudentAssessmentTab(Tab assessmentTab) {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8;");

        Label title = new Label("Course Assessments");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        // Course selection
        ComboBox<Course> courseCombo = new ComboBox<>();
        courseCombo.setItems(courses);
        courseCombo.setConverter(new StringConverter<Course>() {
            @Override
            public String toString(Course course) {
                return course == null ? "" : course.getCourseCode() + " - " + course.getCourseName();
            }

            @Override
            public Course fromString(String string) {
                return null;
            }
        });

        // Questions container
        VBox questionsContainer = new VBox(10);
        questionsContainer.setPadding(new Insets(10));

        // Load questions when course is selected
        courseCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newCourse) -> {
            questionsContainer.getChildren().clear();
            if (newCourse != null) {
                loadAssessmentQuestions(newCourse.getCourseCode(), questionsContainer);
            }
        });

        container.getChildren().addAll(title, new Label("Select Course:"), courseCombo, questionsContainer);
        assessmentTab.setContent(new ScrollPane(container));
    }
    private void loadAssessmentQuestions(String courseCode, VBox container) {
        try (Connection conn = getConnection()) {
            // Load MCQ questions
            String mcqQuery = "SELECT q.id, q.question_text FROM mcq_questions q " +
                    "WHERE q.course_code = ? ORDER BY q.created_at DESC";

            List<MCQQuestion> mcqQuestions = new ArrayList<>();

            try (PreparedStatement pstmt = conn.prepareStatement(mcqQuery)) {
                pstmt.setString(1, courseCode);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    MCQQuestion question = new MCQQuestion(
                            rs.getInt("id"),
                            rs.getString("question_text"),
                            courseCode,
                            -1, // creator ID not needed here
                            null // creation date not needed here
                    );

                    // Load options for this question
                    String optionQuery = "SELECT id, option_text, is_correct FROM mcq_options " +
                            "WHERE question_id = ?";
                    try (PreparedStatement optionStmt = conn.prepareStatement(optionQuery)) {
                        optionStmt.setInt(1, question.getId());
                        ResultSet optionRs = optionStmt.executeQuery();

                        while (optionRs.next()) {
                            question.addOption(new MCQOption(
                                    optionRs.getInt("id"),
                                    question.getId(),
                                    optionRs.getString("option_text"),
                                    optionRs.getBoolean("is_correct")
                            ));
                        }
                    }
                    mcqQuestions.add(question);
                }
            }

            // Load short answer questions
            String saQuery = "SELECT id, question_text FROM short_answer_questions " +
                    "WHERE course_code = ? ORDER BY created_at DESC";

            List<ShortAnswerQuestion> saQuestions = new ArrayList<>();

            try (PreparedStatement pstmt = conn.prepareStatement(saQuery)) {
                pstmt.setString(1, courseCode);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    saQuestions.add(new ShortAnswerQuestion(
                            rs.getInt("id"),
                            rs.getString("question_text"),
                            courseCode
                    ));
                }
            }

            // Display questions
            if (mcqQuestions.isEmpty() && saQuestions.isEmpty()) {
                container.getChildren().add(new Label("No assessments available for this course yet."));
                return;
            }

            // Add MCQ questions
            if (!mcqQuestions.isEmpty()) {
                Label mcqTitle = new Label("Multiple Choice Questions");
                mcqTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
                container.getChildren().add(mcqTitle);

                for (MCQQuestion question : mcqQuestions) {
                    container.getChildren().add(createMCQQuestionCard(question));
                }
            }

            // Add short answer questions
            if (!saQuestions.isEmpty()) {
                Label saTitle = new Label("Short Answer Questions");
                saTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
                container.getChildren().add(saTitle);

                for (ShortAnswerQuestion question : saQuestions) {
                    container.getChildren().add(createSAQuestionCard(question));
                }
            }

            // Add submit button if there are questions
            if (!mcqQuestions.isEmpty() || !saQuestions.isEmpty()) {
                Button submitBtn = new Button("Submit All Answers");
                submitBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
                submitBtn.setOnAction(e -> submitAllAnswers(courseCode, container));
                container.getChildren().add(submitBtn);
            }

        } catch (SQLException e) {
            showAlert("Error", "Failed to load assessment questions: " + e.getMessage());
        }
    }
    private VBox createMCQQuestionCard(MCQQuestion question) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 8;");

        Label questionLabel = new Label(question.getQuestionText());
        questionLabel.setWrapText(true);
        questionLabel.setStyle("-fx-font-weight: bold;");

        ToggleGroup optionsGroup = new ToggleGroup();
        VBox optionsBox = new VBox(5);

        for (MCQOption option : question.getOptions()) {
            RadioButton radioBtn = new RadioButton(option.getOptionText());
            radioBtn.setToggleGroup(optionsGroup);
            radioBtn.setUserData(option.getId());
            optionsBox.getChildren().add(radioBtn);
        }

        // Load previous answer if exists
        try {
            String answerQuery = "SELECT option_id FROM mcq_student_answers " +
                    "WHERE student_id = ? AND question_id = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(answerQuery)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, question.getId());
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    int selectedOptionId = rs.getInt("option_id");
                    for (Node node : optionsBox.getChildren()) {
                        if (node instanceof RadioButton) {
                            RadioButton rb = (RadioButton) node;
                            if (rb.getUserData().equals(selectedOptionId)) {
                                rb.setSelected(true);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            // Ignore if we can't load previous answer
        }

        card.getChildren().addAll(questionLabel, optionsBox);
        return card;
    }
    private VBox createSAQuestionCard(ShortAnswerQuestion question) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 8;");

        Label questionLabel = new Label(question.getQuestionText());
        questionLabel.setWrapText(true);
        questionLabel.setStyle("-fx-font-weight: bold;");

        TextArea answerArea = new TextArea();
        answerArea.setPromptText("Type your answer here...");
        answerArea.setPrefRowCount(3);

        // Load previous answer if exists
        try {
            String answerQuery = "SELECT answer FROM short_answer_student_answers " +
                    "WHERE student_id = ? AND question_id = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(answerQuery)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, question.getId());
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    answerArea.setText(rs.getString("answer"));
                }
            }
        } catch (SQLException e) {
            // Ignore if we can't load previous answer
        }

        card.getChildren().addAll(questionLabel, answerArea);
        return card;
    }
    private void submitAllAnswers(String courseCode, VBox container) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Process MCQ answers
                for (Node node : container.getChildren()) {
                    if (node instanceof VBox) {
                        VBox card = (VBox) node;
                        if (card.getChildren().size() > 1 && card.getChildren().get(1) instanceof VBox) {
                            VBox optionsBox = (VBox) card.getChildren().get(1);
                            if (optionsBox.getChildren().size() > 0 &&
                                    optionsBox.getChildren().get(0) instanceof RadioButton) {

                                // This is an MCQ question card
                                Label questionLabel = (Label) card.getChildren().get(0);
                                String questionText = questionLabel.getText();

                                // Find the selected option
                                RadioButton selected = null;
                                for (Node optionNode : optionsBox.getChildren()) {
                                    if (optionNode instanceof RadioButton) {
                                        RadioButton rb = (RadioButton) optionNode;
                                        if (rb.isSelected()) {
                                            selected = rb;
                                            break;
                                        }
                                    }
                                }

                                if (selected != null) {
                                    int optionId = (int) selected.getUserData();
                                    saveMCQAnswer(conn, questionText, optionId);
                                }
                            }
                        } else if (card.getChildren().size() > 1 && card.getChildren().get(1) instanceof TextArea) {
                            // This is a short answer question card
                            Label questionLabel = (Label) card.getChildren().get(0);
                            String questionText = questionLabel.getText();
                            TextArea answerArea = (TextArea) card.getChildren().get(1);

                            if (!answerArea.getText().trim().isEmpty()) {
                                saveShortAnswer(conn, questionText, answerArea.getText());
                            }
                        }
                    }
                }

                conn.commit();
                showAlert("Success", "Your answers have been submitted successfully!");
            } catch (SQLException e) {
                conn.rollback();
                showAlert("Error", "Failed to submit answers: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            showAlert("Error", "Database error: " + e.getMessage());
        }
    }

    private void saveMCQAnswer(Connection conn, String questionText, int optionId) throws SQLException {
        // Check if answer already exists
        String checkQuery = "SELECT id FROM mcq_student_answers WHERE student_id = ? AND question_id = " +
                "(SELECT id FROM mcq_questions WHERE question_text = ?)";

        try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
            checkStmt.setInt(1, userId);
            checkStmt.setString(2, questionText);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Update existing answer
                String updateQuery = "UPDATE mcq_student_answers SET option_id = ?, answered_at = NOW() " +
                        "WHERE id = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                    updateStmt.setInt(1, optionId);
                    updateStmt.setInt(2, rs.getInt("id"));
                    updateStmt.executeUpdate();
                }
            } else {
                // Insert new answer
                String insertQuery = "INSERT INTO mcq_student_answers (student_id, question_id, option_id, answered_at) " +
                        "VALUES (?, (SELECT id FROM mcq_questions WHERE question_text = ?), ?, NOW())";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                    insertStmt.setInt(1, userId);
                    insertStmt.setString(2, questionText);
                    insertStmt.setInt(3, optionId);
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    private void saveShortAnswer(Connection conn, String questionText, String answer) throws SQLException {
        // Check if answer already exists
        String checkQuery = "SELECT id FROM short_answer_student_answers WHERE student_id = ? AND question_id = " +
                "(SELECT id FROM short_answer_questions WHERE question_text = ?)";

        try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
            checkStmt.setInt(1, userId);
            checkStmt.setString(2, questionText);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Update existing answer
                String updateQuery = "UPDATE short_answer_student_answers SET answer = ?, answered_at = NOW() " +
                        "WHERE id = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                    updateStmt.setString(1, answer);
                    updateStmt.setInt(2, rs.getInt("id"));
                    updateStmt.executeUpdate();
                }
            } else {
                // Insert new answer
                String insertQuery = "INSERT INTO short_answer_student_answers (student_id, question_id, answer, answered_at) " +
                        "VALUES (?, (SELECT id FROM short_answer_questions WHERE question_text = ?), ?, NOW())";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                    insertStmt.setInt(1, userId);
                    insertStmt.setString(2, questionText);
                    insertStmt.setString(3, answer);
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    // Represents a question
    public static class AssessmentQuestion {
        private final int id;
        private final String question;

        public AssessmentQuestion(int id, String question) {
            this.id = id;
            this.question = question;
        }
        public int getId() { return id; }
        public String getQuestion() { return question; }
    }

    // Fetch questions (replace with DB fetch as needed)
    private List<AssessmentQuestion> getAssessmentQuestions() {
        // Fetch questions from the database or static list
        // Example with static questions:
        return Arrays.asList(
                new AssessmentQuestion(1, "Explain the concept of polymorphism."),
                new AssessmentQuestion(2, "Describe the lifecycle of a JavaFX application."),
                new AssessmentQuestion(3, "What is dependency injection?")
        );
    }
    private Map<Integer, String> getStudentAnswers() {
        Map<Integer, String> answersMap = new HashMap<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT question_id, answer FROM student_answers WHERE student_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    answersMap.put(rs.getInt("question_id"), rs.getString("answer"));
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to load your previous answers: " + e.getMessage());
        }
        return answersMap;
    }

    // Save answers (store in DB)
    private void saveStudentAnswer(AssessmentQuestion q, String answer) {
        try (Connection conn = getConnection()) {
            // Check if answer already exists
            String checkSql = "SELECT answer FROM student_answers WHERE student_id = ? AND question_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, userId);
                checkStmt.setInt(2, q.getId());
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    // Update existing answer
                    String updateSql = "UPDATE student_answers SET answer = ?, answer_date = NOW() WHERE student_id = ? AND question_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, answer);
                        updateStmt.setInt(2, userId);
                        updateStmt.setInt(3, q.getId());
                        updateStmt.executeUpdate();
                    }
                } else {
                    // Insert new answer
                    String insertSql = "INSERT INTO student_answers (student_id, question_id, answer, answer_date) VALUES (?, ?, ?, NOW())";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, userId);
                        insertStmt.setInt(2, q.getId());
                        insertStmt.setString(3, answer);
                        insertStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to save your answer: " + e.getMessage());
        }
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
        selectedCourseCode = course.getCourseCode();
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

        // Assessment Tab - New
        Tab assessmentTab = new Tab("Assessment");
        assessmentTab.setClosable(false);
        setupAssessmentTab(assessmentTab);
        Tab forumTab = new Tab("Forum");
        forumTab.setClosable(false);
        setupForumTab(forumTab);

        // Add all tabs to the TabPane
        lecturerTabPane.getTabs().addAll(coursesTab, managementTab, gradingTab, assessmentTab, forumTab);
        lecturerDashboard.getChildren().addAll(header, statsContainer, lecturerTabPane);
    }
    private void setupForumTab(Tab forumTab) {
        VBox forumLayout = new VBox(10);
        forumLayout.setPadding(new Insets(10));

        ListView<String> postsListView = new ListView<>();
        postsListView.setPrefHeight(300);
        refreshForumPosts(postsListView); // Load existing posts

        TextArea newPostArea = new TextArea();
        newPostArea.setPromptText("Write your message...");
        newPostArea.setPrefRowCount(3);

        Button postButton = new Button("Post");
        postButton.setOnAction(e -> {
            String message = newPostArea.getText().trim();
            if (!message.isEmpty()) {
                saveForumPost(message);
                newPostArea.clear();
                refreshForumPosts(postsListView); // Refresh display
            } else {
                showAlert("Please write a message before posting");
            }
        });

        forumLayout.getChildren().addAll(
                new Label("Forum Posts"),
                postsListView,
                new Label("New Post"),
                newPostArea,
                postButton
        );

        forumTab.setContent(forumLayout);
    }    private void refreshForumPosts(ListView<String> postsListView) {
        List<String> posts = getForumPosts();
        Platform.runLater(() -> {
            postsListView.getItems().setAll(posts);
        });
    }
    private List<String> getForumPosts() {
        List<String> posts = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, DB_PASSWORD)) {
            String sql = "SELECT author, message, timestamp FROM forum_posts ORDER BY timestamp DESC";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String author = rs.getString("author");
                String message = rs.getString("message");
                String timestamp = rs.getString("timestamp");
                posts.add("[" + timestamp + "] " + author + ": " + message);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error loading forum posts.");
        }
        return posts;
    }
    private void saveForumPost(String message) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, DB_PASSWORD)) {
            String sql = "INSERT INTO forum_posts (author, message, timestamp) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, fullName);
            pstmt.setString(2, message);
            pstmt.setString(3, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error saving your post.");
        }
    }




    // Method to setup Assessment Tab content
    private void setupAssessmentTab(Tab assessmentTab) {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8;");

        // TabPane for different assessment types
        TabPane assessmentTypes = new TabPane();

        // Short Answer Questions Tab
        Tab shortAnswerTab = new Tab("Short Answer");
        setupShortAnswerTab(shortAnswerTab); // Your existing implementation

        // Multiple Choice Questions Tab
        Tab mcqTab = new Tab("Multiple Choice");
        setupMCQTab(mcqTab);

        assessmentTypes.getTabs().addAll(shortAnswerTab, mcqTab);
        container.getChildren().add(assessmentTypes);

        assessmentTab.setContent(new ScrollPane(container));

    }

    private void setupShortAnswerTab(Tab shortAnswerTab) {

    }

    private void setupMCQTab(Tab mcqTab) {
        VBox mcqContainer = new VBox(15);
        mcqContainer.setPadding(new Insets(20));

        // Course selection
        ComboBox<Course> courseCombo = new ComboBox<>();
        courseCombo.setItems(lecturerCourses);
        courseCombo.setConverter(new StringConverter<Course>() {
            @Override
            public String toString(Course course) {
                return course == null ? "" : course.getCourseCode() + " - " + course.getCourseName();
            }

            @Override
            public Course fromString(String string) {
                return null;
            }
        });

        // Question input
        TextArea questionArea = new TextArea();
        questionArea.setPromptText("Enter the question text...");
        questionArea.setPrefRowCount(3);

        // Options container
        VBox optionsContainer = new VBox(10);

        // Add option button
        Button addOptionBtn = new Button("Add Option");
        addOptionBtn.setOnAction(e -> addOptionField(optionsContainer));

        // Create question button
        Button createQuestionBtn = new Button("Create MCQ");
        createQuestionBtn.setOnAction(e -> {
            if (courseCombo.getValue() == null || questionArea.getText().trim().isEmpty()) {
                showAlert("Error", "Please select a course and enter a question");
                return;
            }

            // Get all options
            List<Pair<String, Boolean>> options = new ArrayList<>();
            for (Node node : optionsContainer.getChildren()) {
                if (node instanceof HBox) {
                    HBox optionBox = (HBox) node;
                    TextField textField = (TextField) optionBox.getChildren().get(0);
                    CheckBox correctBox = (CheckBox) optionBox.getChildren().get(1);

                    if (!textField.getText().trim().isEmpty()) {
                        options.add(new Pair<>(textField.getText().trim(), correctBox.isSelected()));
                    }
                }
            }

            if (options.size() < 2) {
                showAlert("Error", "Please add at least 2 options");
                return;
            }

            if (options.stream().noneMatch(Pair::getValue)) {
                showAlert("Error", "Please mark at least one option as correct");
                return;
            }

            createMCQQuestion(courseCombo.getValue().getCourseCode(),
                    questionArea.getText().trim(), options);

            // Clear form
            questionArea.clear();
            optionsContainer.getChildren().clear();
        });

        // Existing questions list
        ListView<MCQQuestion> questionsList = new ListView<>();
        questionsList.setCellFactory(param -> new ListCell<MCQQuestion>() {
            @Override
            protected void updateItem(MCQQuestion question, boolean empty) {
                super.updateItem(question, empty);
                if (empty || question == null) {
                    setText(null);
                } else {
                    setText(question.getQuestionText() + " (" + question.getCourseCode() + ")");
                }
            }
        });
        Button viewResultsBtn = new Button("View Results");
        viewResultsBtn.setOnAction(e -> {
            MCQQuestion selected = questionsList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showMCQResults(selected);
            } else {
                showAlert("Error", "Please select a question to view results");
            }
        });

        mcqContainer.getChildren().add(viewResultsBtn);

        // Load questions when course is selected
        courseCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newCourse) -> {
            if (newCourse != null) {
                loadMCQQuestions(newCourse.getCourseCode(), questionsList);
            }
        });

        mcqContainer.getChildren().addAll(
                new Label("Select Course:"),
                courseCombo,
                new Label("Question Text:"),
                questionArea,
                new Label("Options:"),
                optionsContainer,
                addOptionBtn,
                createQuestionBtn,
                new Separator(),
                new Label("Existing Questions:"),
                questionsList
        );

        mcqTab.setContent(new ScrollPane(mcqContainer));

    }
    private void showMCQResults(MCQQuestion question) {
        Stage resultsStage = new Stage();
        resultsStage.setTitle("Results for: " + question.getQuestionText());

        VBox container = new VBox(15);
        container.setPadding(new Insets(20));

        // Question display
        Label questionLabel = new Label(question.getQuestionText());
        questionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        questionLabel.setWrapText(true);

        // Correct answer(s)
        Label correctLabel = new Label("Correct answer(s): " +
                question.getOptions().stream()
                        .filter(MCQOption::isCorrect)
                        .map(MCQOption::getOptionText)
                        .collect(Collectors.joining(", ")));
        correctLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2E7D32;");

        // Results table
        TableView<Map<String, String>> resultsTable = new TableView<>();

        // Student column
        TableColumn<Map<String, String>, String> studentCol = new TableColumn<>("Student");
        studentCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get("student_name")));

        // Answer column
        TableColumn<Map<String, String>, String> answerCol = new TableColumn<>("Answer");
        answerCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get("answer_text")));

        // Correct column
        TableColumn<Map<String, String>, String> correctCol = new TableColumn<>("Correct?");
        correctCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get("is_correct")));
        correctCol.setCellFactory(column -> new TableCell<Map<String, String>, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("Yes".equals(item)) {
                        setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #C62828; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // Time column
        TableColumn<Map<String, String>, String> timeCol = new TableColumn<>("Submitted At");
        timeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get("answered_at")));

        resultsTable.getColumns().addAll(studentCol, answerCol, correctCol, timeCol);

        // Load results
        try (Connection conn = getConnection()) {
            String query = "SELECT u.full_name AS student_name, o.option_text AS answer_text, " +
                    "o.is_correct, a.answered_at " +
                    "FROM mcq_student_answers a " +
                    "JOIN users u ON a.student_id = u.id " +
                    "JOIN mcq_options o ON a.option_id = o.id " +
                    "WHERE a.question_id = ? " +
                    "ORDER BY a.answered_at DESC";

            ObservableList<Map<String, String>> results = FXCollections.observableArrayList();

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, question.getId());
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("student_name", rs.getString("student_name"));
                    row.put("answer_text", rs.getString("answer_text"));
                    row.put("is_correct", rs.getBoolean("is_correct") ? "Yes" : "No");
                    row.put("answered_at", rs.getTimestamp("answered_at").toLocalDateTime()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    results.add(row);
                }
            }

            resultsTable.setItems(results);
        } catch (SQLException e) {
            showAlert("Error", "Failed to load results: " + e.getMessage());
        }

        // Stats
        int totalAnswers = resultsTable.getItems().size();
        long correctAnswers = resultsTable.getItems().stream()
                .filter(row -> "Yes".equals(row.get("is_correct")))
                .count();

        Label statsLabel = new Label(String.format("Correct answers: %d/%d (%.1f%%)",
                correctAnswers, totalAnswers,
                totalAnswers > 0 ? (correctAnswers * 100.0 / totalAnswers) : 0));
        statsLabel.setStyle("-fx-font-weight: bold;");

        container.getChildren().addAll(
                questionLabel,
                correctLabel,
                new Separator(),
                statsLabel,
                resultsTable
        );

        Scene scene = new Scene(container, 600, 500);
        resultsStage.setScene(scene);
        resultsStage.show();
    }

    private void addOptionField(VBox container) {
        HBox optionBox = new HBox(10);
        TextField optionField = new TextField();
        optionField.setPromptText("Option text...");
        CheckBox correctBox = new CheckBox("Correct?");

        Button removeBtn = new Button("X");
        removeBtn.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
        removeBtn.setOnAction(e -> container.getChildren().remove(optionBox));

        optionBox.getChildren().addAll(optionField, correctBox, removeBtn);
        container.getChildren().add(optionBox);
    }


    private void createMCQQuestion(String courseCode, String questionText, List<Pair<String, Boolean>> options) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert question
                String questionQuery = "INSERT INTO mcq_questions (question_text, course_code, created_by) VALUES (?, ?, ?)";
                int questionId;

                try (PreparedStatement pstmt = conn.prepareStatement(questionQuery, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, questionText);
                    pstmt.setString(2, courseCode);
                    pstmt.setInt(3, userId);
                    pstmt.executeUpdate();

                    ResultSet rs = pstmt.getGeneratedKeys();
                    if (rs.next()) {
                        questionId = rs.getInt(1);
                    } else {
                        throw new SQLException("Failed to get question ID");
                    }
                }

                // Insert options
                String optionQuery = "INSERT INTO mcq_options (question_id, option_text, is_correct) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(optionQuery)) {
                    for (Pair<String, Boolean> option : options) {
                        pstmt.setInt(1, questionId);
                        pstmt.setString(2, option.getKey());
                        pstmt.setBoolean(3, option.getValue());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                conn.commit();
                showAlert("Success", "MCQ question created successfully");
            } catch (SQLException e) {
                conn.rollback();
                showAlert("Error", "Failed to create MCQ: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            showAlert("Error", "Database error: " + e.getMessage());
        }
    }

    private void loadMCQQuestions(String courseCode, ListView<MCQQuestion> listView) {
        try (Connection conn = getConnection()) {
            // Load questions
            String questionQuery = "SELECT * FROM mcq_questions WHERE course_code = ? ORDER BY created_at DESC";
            List<MCQQuestion> questions = new ArrayList<>();

            try (PreparedStatement pstmt = conn.prepareStatement(questionQuery)) {
                pstmt.setString(1, courseCode);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    MCQQuestion question = new MCQQuestion(
                            rs.getInt("id"),
                            rs.getString("question_text"),
                            rs.getString("course_code"),
                            rs.getInt("created_by"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    );

                    // Load options for this question
                    String optionQuery = "SELECT * FROM mcq_options WHERE question_id = ?";
                    try (PreparedStatement optionStmt = conn.prepareStatement(optionQuery)) {
                        optionStmt.setInt(1, question.getId());
                        ResultSet optionRs = optionStmt.executeQuery();

                        while (optionRs.next()) {
                            question.addOption(new MCQOption(
                                    optionRs.getInt("id"),
                                    optionRs.getInt("question_id"),
                                    optionRs.getString("option_text"),
                                    optionRs.getBoolean("is_correct")
                            ));
                        }
                    }

                    questions.add(question);
                }
            }

            listView.getItems().setAll(questions);
        } catch (SQLException e) {
            showAlert("Error", "Failed to load questions: " + e.getMessage());
        }
    }

    private List<String> getSavedQuestions() {
        // Return list of saved questions
        return new ArrayList<>();
    }

    private void createQuizFromQuestions() {
        // Implement quiz creation logic
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #333;");

        FlowPane coursesGrid = new FlowPane();
        coursesGrid.setHgap(20);
        coursesGrid.setVgap(20);
        coursesGrid.setPrefWrapLength(1000);

        for (Course course : lecturerCourses) {
            VBox courseCard = new VBox(10);
            courseCard.setStyle("-fx-background-color: white; -fx-background-radius: 8px; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 0, 2, 4, 0); " +
                    "-fx-padding: 15;");
            courseCard.setPrefWidth(300);

            // Course Code
            Label code = new Label(course.getCourseCode());
            code.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2a60b9;");

            // Course Name
            Label name = new Label(course.getCourseName());
            name.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 14px;");
            name.setWrapText(true);

            // Progress
            Label progress = new Label("Average Progress: " + course.getProgress() + "%");
            progress.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 12px;");

            // Button Container
            HBox buttonBox = new HBox(10);
            buttonBox.setAlignment(Pos.CENTER);

            // 1. Manage Button (Blue)
            Button manageButton = new Button("Manage");
            manageButton.setStyle(
                    "-fx-background-color: #1e90ff;" +
                            "-fx-text-fill: white;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 6;" +
                            "-fx-padding: 6 12 6 12;" +
                            "-fx-font-size: 12px;" +
                            "-fx-cursor: hand;"
            );
            manageButton.setOnMouseEntered(e -> manageButton.setStyle(
                    "-fx-background-color: #0f74e0;" +
                            "-fx-text-fill: white;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 6;" +
                            "-fx-padding: 6 12 6 12;" +
                            "-fx-font-size: 12px;" +
                            "-fx-cursor: hand;"
            ));
            manageButton.setOnMouseExited(e -> manageButton.setStyle(
                    "-fx-background-color: #1e90ff;" +
                            "-fx-text-fill: white;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 6;" +
                            "-fx-padding: 6 12 6 12;" +
                            "-fx-font-size: 12px;" +
                            "-fx-cursor: hand;"
            ));
            manageButton.setOnAction(e -> showLecturerCourseView(course));

            // 2. Edit Button (Yellow)
            Button editButton = new Button("Edit");
            editButton.setStyle(
                    "-fx-background-color: #ffc107;" +
                            "-fx-text-fill: #212529;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 6;" +
                            "-fx-padding: 6 12 6 12;" +
                            "-fx-font-size: 12px;" +
                            "-fx-cursor: hand;"
            );
            editButton.setOnMouseEntered(e -> editButton.setStyle(
                    "-fx-background-color: #e0a800;" +
                            "-fx-text-fill: #212529;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 6;" +
                            "-fx-padding: 6 12 6 12;" +
                            "-fx-font-size: 12px;" +
                            "-fx-cursor: hand;"
            ));
            editButton.setOnMouseExited(e -> editButton.setStyle(
                    "-fx-background-color: #ffc107;" +
                            "-fx-text-fill: #212529;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 6;" +
                            "-fx-padding: 6 12 6 12;" +
                            "-fx-font-size: 12px;" +
                            "-fx-cursor: hand;"
            ));
            editButton.setOnAction(e -> editCourse(course));

            // 3. Delete Button (Red)
            Button deleteButton = new Button("Delete");
            deleteButton.setStyle(
                    "-fx-background-color: #dc3545;" +
                            "-fx-text-fill: white;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 6;" +
                            "-fx-padding: 6 12 6 12;" +
                            "-fx-font-size: 12px;" +
                            "-fx-cursor: hand;"
            );
            deleteButton.setOnMouseEntered(e -> deleteButton.setStyle(
                    "-fx-background-color: #c82333;" +
                            "-fx-text-fill: white;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 6;" +
                            "-fx-padding: 6 12 6 12;" +
                            "-fx-font-size: 12px;" +
                            "-fx-cursor: hand;"
            ));
            deleteButton.setOnMouseExited(e -> deleteButton.setStyle(
                    "-fx-background-color: #dc3545;" +
                            "-fx-text-fill: white;" +
                            "-fx-font-weight: bold;" +
                            "-fx-background-radius: 6;" +
                            "-fx-padding: 6 12 6 12;" +
                            "-fx-font-size: 12px;" +
                            "-fx-cursor: hand;"
            ));
            deleteButton.setOnAction(e -> deleteCourse(course));

            buttonBox.getChildren().addAll(manageButton, editButton, deleteButton);
            courseCard.getChildren().addAll(code, name, progress, buttonBox);

            // Hover effect for the entire card
            courseCard.setOnMouseEntered(e -> {
                courseCard.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8px; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 0, 4, 6, 0);");
            });
            courseCard.setOnMouseExited(e -> {
                courseCard.setStyle("-fx-background-color: white; -fx-background-radius: 8px; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 0, 2, 4, 0);");
            });

            coursesGrid.getChildren().add(courseCard);
        }

        content.getChildren().addAll(title, coursesGrid);
        coursesTab.setContent(new ScrollPane(content));
    }

    private void editCourse(Course course) {
        // Create edit dialog
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Edit Course");
        dialog.setHeaderText("Editing: " + course.getCourseCode());

        // Set buttons
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField codeField = new TextField(course.getCourseCode());
        TextField nameField = new TextField(course.getCourseName());

        grid.add(new Label("Course Code:"), 0, 0);
        grid.add(codeField, 1, 0);
        grid.add(new Label("Course Name:"), 0, 1);
        grid.add(nameField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Convert result to Pair when Save clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new Pair<>(codeField.getText(), nameField.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();

        result.ifPresent(newValues -> {
            String newCode = newValues.getKey();
            String newName = newValues.getValue();

            if (newCode.isEmpty() || newName.isEmpty()) {
                showAlert("Error", "Course code and name cannot be empty");
                return;
            }

            try (Connection conn = getConnection()) {
                // Begin transaction
                conn.setAutoCommit(false);

                try {
                    // Update course
                    String updateQuery = "UPDATE courses SET course_code = ?, course_name = ? WHERE course_code = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                        pstmt.setString(1, newCode);
                        pstmt.setString(2, newName);
                        pstmt.setString(3, course.getCourseCode());
                        int rowsAffected = pstmt.executeUpdate();

                        if (rowsAffected > 0) {
                            // Update all references if course code changed
                            if (!newCode.equals(course.getCourseCode())) {
                                updateCourseReferences(conn, course.getCourseCode(), newCode);
                            }

                            // Update the course object
                            course.setCourseCode(newCode);
                            course.setCourseName(newName);

                            // Commit transaction
                            conn.commit();

                            showAlert("Success", "Course updated successfully");
                            setupLecturerDashboard(); // Refresh view
                        } else {
                            conn.rollback();
                            showAlert("Error", "Failed to update course");
                        }
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    showAlert("Error", "Failed to update course: " + e.getMessage());
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                showAlert("Error", "Database error: " + e.getMessage());
            }
        });
    }

    private void updateCourseReferences(Connection conn, String oldCode, String newCode) throws SQLException {
        String[] tables = {"enrollments", "assignments", "announcements", "course_materials", "submissions", "transcripts", "certifications"};

        for (String table : tables) {
            try {
                String updateQuery = "UPDATE " + table + " SET course_code = ? WHERE course_code = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                    pstmt.setString(1, newCode);
                    pstmt.setString(2, oldCode);
                    pstmt.executeUpdate();
                }
            } catch (SQLException e) {
                // Log but continue with other tables
                System.err.println("Warning: Failed to update references in " + table + ": " + e.getMessage());
            }
        }
    }

    private void deleteCourse(Course course) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Course: " + course.getCourseCode());
        confirm.setContentText("This will permanently delete the course and all associated data. Continue?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // First delete dependent records
                    String[] tables = {"enrollments", "assignments", "announcements",
                            "course_materials", "submissions", "transcripts", "certifications"};

                    for (String table : tables) {
                        try {
                            String deleteQuery = "DELETE FROM " + table + " WHERE course_code = ?";
                            try (PreparedStatement pstmt = conn.prepareStatement(deleteQuery)) {
                                pstmt.setString(1, course.getCourseCode());
                                pstmt.executeUpdate();
                            }
                        } catch (SQLException e) {
                            System.err.println("Warning: Failed to delete from " + table + ": " + e.getMessage());
                        }
                    }

                    // Then delete the course
                    String deleteCourseQuery = "DELETE FROM courses WHERE course_code = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(deleteCourseQuery)) {
                        pstmt.setString(1, course.getCourseCode());
                        int rows = pstmt.executeUpdate();

                        if (rows > 0) {
                            conn.commit();
                            lecturerCourses.remove(course);
                            showAlert("Success", "Course deleted successfully");
                            setupLecturerDashboard(); // Refresh view
                        } else {
                            conn.rollback();
                            showAlert("Error", "Failed to delete course");
                        }
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    showAlert("Error", "Failed to delete course: " + e.getMessage());
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                showAlert("Error", "Database error: " + e.getMessage());
            }
        }
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

        private static Connection getConnection() throws SQLException {
            try {
                Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                System.out.println("Database connection established successfully");
                return conn;
            } catch (SQLException e) {
                System.err.println("Database connection failed:");
                System.err.println("URL: " + DB_URL);
                System.err.println("User: " + DB_USER);
                e.printStackTrace();
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
class ForumCategory {
    private final int id;
    private final String name;
    private final String description;
    private final LocalDateTime createdAt;

    public ForumCategory(int id, String name, String description, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    // Getters
    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
// Add this class to your model classes
class ShortAnswerQuestion {
    private final int id;
    private final String questionText;
    private final String courseCode;

    public ShortAnswerQuestion(int id, String questionText, String courseCode) {
        this.id = id;
        this.questionText = questionText;
        this.courseCode = courseCode;
    }

    public int getId() { return id; }
    public String getQuestionText() { return questionText; }
    public String getCourseCode() { return courseCode; }
}
class ForumPost {
    private final int id;
    private final int categoryId;
    private final int userId;
    private final String userName;
    private final String title;
    private final String content;
    private final String mediaPath;
    private final String mediaType;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public ForumPost(int id, int categoryId, int userId, String userName, String title,
                     String content, String mediaPath, String mediaType,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.categoryId = categoryId;
        this.userId = userId;
        this.userName = userName;
        this.title = title;
        this.content = content;
        this.mediaPath = mediaPath;
        this.mediaType = mediaType;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public int getId() { return id; }
    public int getCategoryId() { return categoryId; }
    public int getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getMediaPath() { return mediaPath; }
    public String getMediaType() { return mediaType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
class MCQQuestion {
    private final int id;
    private final String questionText;
    private final String courseCode;
    private final int createdBy;
    private final LocalDateTime createdAt;
    private final List<MCQOption> options;

    public MCQQuestion(int id, String questionText, String courseCode,
                       int createdBy, LocalDateTime createdAt) {
        this.id = id;
        this.questionText = questionText;
        this.courseCode = courseCode;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.options = new ArrayList<>();
    }

    // Getters
    public int getId() { return id; }
    public String getQuestionText() { return questionText; }
    public String getCourseCode() { return courseCode; }
    public int getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<MCQOption> getOptions() { return options; }

    public void addOption(MCQOption option) {
        options.add(option);
    }
}

class MCQOption {
    private final int id;
    private final int questionId;
    private final String optionText;
    private final boolean isCorrect;

    public MCQOption(int id, int questionId, String optionText, boolean isCorrect) {
        this.id = id;
        this.questionId = questionId;
        this.optionText = optionText;
        this.isCorrect = isCorrect;
    }

    // Getters
    public int getId() { return id; }
    public int getQuestionId() { return questionId; }
    public String getOptionText() { return optionText; }
    public boolean isCorrect() { return isCorrect; }
}
class ForumComment {
    private final int id;
    private final int postId;
    private final int userId;
    private final String userName;
    private final String content;
    private final LocalDateTime createdAt;

    public ForumComment(int id, int postId, int userId, String userName,
                        String content, LocalDateTime createdAt) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.userName = userName;
        this.content = content;
        this.createdAt = createdAt;
    }

    // Getters
    public int getId() { return id; }
    public int getPostId() { return postId; }
    public int getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
// Represents a question for assessment
class AssessmentQuestion {
    private final int id;
    private final String question;

    public AssessmentQuestion(int id, String question) {
        this.id = id;
        this.question = question;
    }

    public int getId() { return id; }
    public String getQuestion() { return question; }
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