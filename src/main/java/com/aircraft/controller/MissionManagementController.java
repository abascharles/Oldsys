package com.aircraft.controller;

import com.aircraft.dao.AircraftDAO;
import com.aircraft.dao.MissionDAO;
import com.aircraft.model.Aircraft;
import com.aircraft.model.Mission;
import com.aircraft.util.AlertUtils;
import com.aircraft.util.DBUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.DatabaseMetaData;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Mission Management screen.
 * Handles creating, updating, and deleting mission data.
 */
public class MissionManagementController {

    @FXML
    private ComboBox<Aircraft> aircraftComboBox;

    @FXML
    private TextField flightNumberField;

    @FXML
    private DatePicker missionDatePicker;

    @FXML
    private TextField departureTimeField;

    @FXML
    private TextField arrivalTimeField;

    @FXML
    private Button saveButton;

    @FXML
    private TableView<Mission> missionTable;

    @FXML
    private TableColumn<Mission, Integer> idColumn;

    @FXML
    private TableColumn<Mission, String> aircraftColumn;

    @FXML
    private TableColumn<Mission, Integer> flightNumberColumn;

    @FXML
    private TableColumn<Mission, Date> missionDateColumn;

    @FXML
    private TableColumn<Mission, Time> departureTimeColumn;

    @FXML
    private TableColumn<Mission, Time> arrivalTimeColumn;

    @FXML
    private TableColumn<Mission, Void> actionsColumn;

    private final MissionDAO missionDAO = new MissionDAO();
    private final AircraftDAO aircraftDAO = new AircraftDAO();
    private ObservableList<Mission> missionList = FXCollections.observableArrayList();
    private ObservableList<Aircraft> aircraftList = FXCollections.observableArrayList();
    private Mission selectedMission = null;

    // Map to store selected weapons and their positions
    private Map<String, Map<String, String>> selectedPositions = new HashMap<>();

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Initializes the controller after its root element has been processed.
     * Sets up event handlers and initializes UI components.
     */
    @FXML
    public void initialize() {
        // Load aircraft data for the combo box
        loadAircraftData();

        // Set up table columns
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        aircraftColumn.setCellValueFactory(new PropertyValueFactory<>("matricolaVelivolo"));
        flightNumberColumn.setCellValueFactory(new PropertyValueFactory<>("numeroVolo"));
        missionDateColumn.setCellValueFactory(new PropertyValueFactory<>("dataMissione"));
        departureTimeColumn.setCellValueFactory(new PropertyValueFactory<>("oraPartenza"));
        arrivalTimeColumn.setCellValueFactory(new PropertyValueFactory<>("oraArrivo"));

        // Set up action column with Edit, Weapons, and Delete buttons
        setupActionsColumn();

        // Set default date to today
        missionDatePicker.setValue(LocalDate.now());

        // Load mission data
        refreshMissionTable();
    }

    /**
     * Loads aircraft data for the combo box.
     */
    private void loadAircraftData() {
        List<Aircraft> aircraft = aircraftDAO.getAll();
        aircraftList.clear();
        aircraftList.addAll(aircraft);
        aircraftComboBox.setItems(aircraftList);

        // Set up cell factory to display aircraft matricola
        aircraftComboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Aircraft item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getMatricolaVelivolo());
                }
            }
        });

        // Set up button cell to display selected aircraft matricola
        aircraftComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Aircraft item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getMatricolaVelivolo());
                }
            }
        });
    }

    /**
     * Sets up the actions column with Edit, Weapons, and Delete buttons.
     */
    private void setupActionsColumn() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button weaponsButton = new Button("Weapons");
            private final Button deleteButton = new Button("Delete");
            private final HBox buttons = new HBox(5, editButton, weaponsButton, deleteButton);

            {
                // Set up button handlers
                editButton.setOnAction(event -> {
                    Mission mission = getTableView().getItems().get(getIndex());
                    editMission(mission);
                });

                weaponsButton.setOnAction(event -> {
                    Mission mission = getTableView().getItems().get(getIndex());
                    openWeaponConfiguration(mission);
                });

                deleteButton.setOnAction(event -> {
                    Mission mission = getTableView().getItems().get(getIndex());
                    deleteMission(mission);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(buttons);
                }
            }
        });
    }

    /**
     * Opens the weapon configuration screen for a mission.
     *
     * @param mission The mission to configure weapons for
     */
    private void openWeaponConfiguration(Mission mission) {
        try {
            // Load the weapon configuration screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/weapon_configuration.fxml"));
            Parent root = loader.load();

            // Get the controller and set up data
            WeaponConfigurationController controller = loader.getController();
            controller.setParentController(this);

            // Load existing weapon configuration for the mission
            loadMissionWeapons(mission.getId());
            controller.setSelectedPositions(selectedPositions);

            // Open in a new modal window
            Stage stage = new Stage();
            stage.setTitle("Weapon Configuration - Mission #" + mission.getId());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            // After window is closed, save the weapon configuration if needed
            saveMissionWeapons(mission.getId());
        } catch (IOException e) {
            Window owner = missionTable.getScene().getWindow();
            AlertUtils.showError(owner, "Navigation Error", "Failed to open weapon configuration: " + e.getMessage());
        }
    }

    /**
     * Checks if a table exists in the database.
     *
     * @param connection The database connection
     * @param tableName The name of the table to check
     * @return true if the table exists, false otherwise
     */
    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        ResultSet resultSet = null;
        try {
            // Get the database metadata
            DatabaseMetaData metaData = connection.getMetaData();

            // Get the current catalog (database)
            String catalog = connection.getCatalog();

            // Check if the table exists in the current database
            resultSet = metaData.getTables(catalog, null, tableName, new String[] {"TABLE"});
            return resultSet.next();
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }

    /**
     * Loads weapons configuration for a mission.
     * Handles cases where tables don't exist yet.
     *
     * @param missionId The ID of the mission
     */
    private void loadMissionWeapons(int missionId) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            connection = DBUtil.getConnection();

            // Clear existing data
            selectedPositions.clear();

            // Check if tables exist before trying to query them
            boolean historicalLoadExists = tableExists(connection, "historical_load");
            boolean historicalLauncherExists = tableExists(connection, "historical_launcher");

            if (historicalLoadExists) {
                // Query historical_load table (weapons)
                String loadQuery = "SELECT position, weapon_id, serial_number FROM historical_load WHERE mission_id = ?";
                statement = connection.prepareStatement(loadQuery);
                statement.setInt(1, missionId);
                resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    String position = resultSet.getString("position");

                    Map<String, String> weaponData = new HashMap<>();
                    weaponData.put("type", "weapon");
                    weaponData.put("id", resultSet.getString("weapon_id"));
                    weaponData.put("serialNumber", resultSet.getString("serial_number"));

                    selectedPositions.put(position, weaponData);
                }

                // Close resources
                DBUtil.closeResources(null, statement, resultSet);
            }

            if (historicalLauncherExists) {
                // Query historical_launcher table (launchers)
                String launcherQuery = "SELECT position, launcher_id, serial_number FROM historical_launcher WHERE mission_id = ?";
                statement = connection.prepareStatement(launcherQuery);
                statement.setInt(1, missionId);
                resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    String position = resultSet.getString("position");

                    Map<String, String> launcherData = new HashMap<>();
                    launcherData.put("type", "launcher");
                    launcherData.put("id", resultSet.getString("launcher_id"));
                    launcherData.put("serialNumber", resultSet.getString("serial_number"));

                    selectedPositions.put(position, launcherData);
                }
            }
        } catch (SQLException e) {
            Window owner = missionTable.getScene().getWindow();
            AlertUtils.showError(owner, "Database Error", "Failed to load mission weapons: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBUtil.closeResources(connection, statement, resultSet);
        }
    }

    /**
     * Saves weapons configuration for a mission.
     * Creates tables if they don't exist and handles different database naming conventions.
     *
     * @param missionId The ID of the mission
     */
    private void saveMissionWeapons(int missionId) {
        Connection connection = null;
        PreparedStatement createStatement = null;
        PreparedStatement deleteStatement = null;
        PreparedStatement insertStatement = null;

        try {
            connection = DBUtil.getConnection();

            // Begin transaction
            connection.setAutoCommit(false);

            // Create tables if they don't exist - using the full database schema
            try {
                // First check if tables exist
                boolean historicalLoadExists = tableExists(connection, "historical_load");
                boolean historicalLauncherExists = tableExists(connection, "historical_launcher");

                // Create historical_load table if it doesn't exist
                if (!historicalLoadExists) {
                    String createHistoricalLoadTable =
                            "CREATE TABLE `historical_load` (" +
                                    "  `id` int(11) NOT NULL AUTO_INCREMENT," +
                                    "  `mission_id` int(11) NOT NULL," +
                                    "  `position` varchar(20) NOT NULL," +
                                    "  `weapon_id` varchar(50) NOT NULL," +
                                    "  `serial_number` varchar(50) NOT NULL," +
                                    "  PRIMARY KEY (`id`)," +
                                    "  KEY `mission_id` (`mission_id`)" +
                                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;";

                    createStatement = connection.prepareStatement(createHistoricalLoadTable);
                    createStatement.executeUpdate();
                    createStatement.close();
                    System.out.println("Successfully created historical_load table");
                }

                // Create historical_launcher table if it doesn't exist
                if (!historicalLauncherExists) {
                    String createHistoricalLauncherTable =
                            "CREATE TABLE `historical_launcher` (" +
                                    "  `id` int(11) NOT NULL AUTO_INCREMENT," +
                                    "  `mission_id` int(11) NOT NULL," +
                                    "  `position` varchar(20) NOT NULL," +
                                    "  `launcher_id` varchar(50) NOT NULL," +
                                    "  `serial_number` varchar(50) NOT NULL," +
                                    "  PRIMARY KEY (`id`)," +
                                    "  KEY `mission_id` (`mission_id`)" +
                                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;";

                    createStatement = connection.prepareStatement(createHistoricalLauncherTable);
                    createStatement.executeUpdate();
                    createStatement.close();
                    System.out.println("Successfully created historical_launcher table");
                }
            } catch (SQLException e) {
                // Log error but continue with execution
                System.out.println("Warning creating tables: " + e.getMessage());
                e.printStackTrace();
            }

            // Delete existing weapon records - use try/catch for each operation to ensure robustness
            try {
                String deleteLoadQuery = "DELETE FROM historical_load WHERE mission_id = ?";
                deleteStatement = connection.prepareStatement(deleteLoadQuery);
                deleteStatement.setInt(1, missionId);
                deleteStatement.executeUpdate();
                deleteStatement.close();
            } catch (SQLException e) {
                System.out.println("Warning when deleting from historical_load: " + e.getMessage());
                // Continue with operation
            }

            try {
                String deleteLauncherQuery = "DELETE FROM historical_launcher WHERE mission_id = ?";
                deleteStatement = connection.prepareStatement(deleteLauncherQuery);
                deleteStatement.setInt(1, missionId);
                deleteStatement.executeUpdate();
                deleteStatement.close();
            } catch (SQLException e) {
                System.out.println("Warning when deleting from historical_launcher: " + e.getMessage());
                // Continue with operation
            }

            // Insert new records
            for (String position : selectedPositions.keySet()) {
                Map<String, String> itemData = selectedPositions.get(position);
                String type = itemData.get("type");

                try {
                    if ("weapon".equals(type)) {
                        // Insert into historical_load
                        String insertLoadQuery = "INSERT INTO historical_load (mission_id, position, weapon_id, serial_number) VALUES (?, ?, ?, ?)";
                        insertStatement = connection.prepareStatement(insertLoadQuery);
                        insertStatement.setInt(1, missionId);
                        insertStatement.setString(2, position);
                        insertStatement.setString(3, itemData.get("id"));
                        insertStatement.setString(4, itemData.get("serialNumber"));
                        insertStatement.executeUpdate();
                        insertStatement.close();
                    } else if ("launcher".equals(type)) {
                        // Insert into historical_launcher
                        String insertLauncherQuery = "INSERT INTO historical_launcher (mission_id, position, launcher_id, serial_number) VALUES (?, ?, ?, ?)";
                        insertStatement = connection.prepareStatement(insertLauncherQuery);
                        insertStatement.setInt(1, missionId);
                        insertStatement.setString(2, position);
                        insertStatement.setString(3, itemData.get("id"));
                        insertStatement.setString(4, itemData.get("serialNumber"));
                        insertStatement.executeUpdate();
                        insertStatement.close();
                    }
                } catch (SQLException e) {
                    System.out.println("Error inserting record for position " + position + ": " + e.getMessage());
                    // Continue with next record
                }
            }

            // Commit transaction
            connection.commit();

            // Show success message
            Window owner = missionTable.getScene().getWindow();
            AlertUtils.showInformation(owner, "Success", "Weapon configuration saved successfully");

        } catch (SQLException e) {
            // Rollback transaction in case of error
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException ex) {
                Window owner = missionTable.getScene().getWindow();
                AlertUtils.showError(owner, "Database Error", "Failed to rollback transaction: " + ex.getMessage());
            }

            Window owner = missionTable.getScene().getWindow();
            AlertUtils.showError(owner, "Database Error", "Failed to save mission weapons: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                Window owner = missionTable.getScene().getWindow();
                AlertUtils.showError(owner, "Database Error", "Failed to reset auto-commit: " + e.getMessage());
            }

            DBUtil.closeResources(connection, null, null);
        }
    }

    /**
     * Loads a mission for editing.
     *
     * @param mission The Mission object to edit
     */
    private void editMission(Mission mission) {
        // Set selected mission
        selectedMission = mission;

        // Populate form fields
        for (Aircraft aircraft : aircraftList) {
            if (aircraft.getMatricolaVelivolo().equals(mission.getMatricolaVelivolo())) {
                aircraftComboBox.setValue(aircraft);
                break;
            }
        }

        flightNumberField.setText(String.valueOf(mission.getNumeroVolo()));

        if (mission.getDataMissione() != null) {
            missionDatePicker.setValue(mission.getDataMissione().toLocalDate());
        }

        if (mission.getOraPartenza() != null) {
            departureTimeField.setText(mission.getOraPartenza().toLocalTime().format(timeFormatter));
        }

        if (mission.getOraArrivo() != null) {
            arrivalTimeField.setText(mission.getOraArrivo().toLocalTime().format(timeFormatter));
        }
    }

    /**
     * Deletes a mission after confirmation.
     *
     * @param mission The Mission object to delete
     */
    private void deleteMission(Mission mission) {
        Window owner = missionTable.getScene().getWindow();

        // Confirm deletion
        boolean confirmed = AlertUtils.showConfirmation(
                owner,
                "Confirm Deletion",
                "Are you sure you want to delete mission #" + mission.getId() + "?"
        );

        if (confirmed) {
            // Delete mission
            boolean success = missionDAO.delete(mission.getId());

            if (success) {
                AlertUtils.showInformation(owner, "Success", "Mission deleted successfully");
                refreshMissionTable();
            } else {
                AlertUtils.showError(owner, "Error", "Failed to delete mission");
            }
        }
    }

    /**
     * Refreshes the mission table with data from the database.
     */
    private void refreshMissionTable() {
        List<Mission> missions = missionDAO.getAll();
        missionList.clear();
        missionList.addAll(missions);
        missionTable.setItems(missionList);
    }

    /**
     * Handles the "Save" button click.
     * Validates and saves the mission data to the database.
     *
     * @param event The ActionEvent object
     */
    @FXML
    protected void onSaveButtonClick(ActionEvent event) {
        Window owner = saveButton.getScene().getWindow();

        // Validate input fields
        if (aircraftComboBox.getValue() == null) {
            AlertUtils.showError(owner, "Validation Error", "Aircraft is required");
            return;
        }

        if (flightNumberField.getText().isEmpty()) {
            AlertUtils.showError(owner, "Validation Error", "Flight Number is required");
            return;
        }

        if (missionDatePicker.getValue() == null) {
            AlertUtils.showError(owner, "Validation Error", "Mission Date is required");
            return;
        }

        if (departureTimeField.getText().isEmpty()) {
            AlertUtils.showError(owner, "Validation Error", "Departure Time is required");
            return;
        }

        if (arrivalTimeField.getText().isEmpty()) {
            AlertUtils.showError(owner, "Validation Error", "Arrival Time is required");
            return;
        }

        // Parse flight number
        int flightNumber;
        try {
            flightNumber = Integer.parseInt(flightNumberField.getText());
        } catch (NumberFormatException e) {
            AlertUtils.showError(owner, "Validation Error", "Flight Number must be a valid number");
            return;
        }

        // Parse times
        LocalTime departureTime;
        LocalTime arrivalTime;
        try {
            departureTime = LocalTime.parse(departureTimeField.getText(), timeFormatter);
            arrivalTime = LocalTime.parse(arrivalTimeField.getText(), timeFormatter);
        } catch (DateTimeParseException e) {
            AlertUtils.showError(owner, "Validation Error", "Times must be in the format HH:MM");
            return;
        }

        // Check if arrival time is after departure time
        if (arrivalTime.isBefore(departureTime)) {
            AlertUtils.showError(owner, "Validation Error", "Arrival Time must be after Departure Time");
            return;
        }

        // Create or update mission object
        Mission mission;
        if (selectedMission == null) {
            // Create new mission
            mission = new Mission();
        } else {
            // Update existing mission
            mission = selectedMission;
        }

        mission.setMatricolaVelivolo(aircraftComboBox.getValue().getMatricolaVelivolo());
        mission.setNumeroVolo(flightNumber);
        mission.setDataMissione(Date.valueOf(missionDatePicker.getValue()));
        mission.setOraPartenza(Time.valueOf(departureTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
        mission.setOraArrivo(Time.valueOf(arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))));

        // Save mission
        boolean success;
        if (selectedMission == null) {
            success = missionDAO.insert(mission);
        } else {
            success = missionDAO.update(mission);
        }

        if (success) {
            // Ask if user wants to configure weapons for this mission
            boolean configureWeapons = AlertUtils.showConfirmation(
                    owner,
                    "Configure Weapons",
                    "Mission saved successfully. Do you want to configure weapons for this mission?"
            );

            if (configureWeapons) {
                // Refresh mission object to get updated ID if it was a new mission
                if (selectedMission == null) {
                    // Get the latest mission
                    List<Mission> latestMissions = missionDAO.getLatestMissions(1);
                    if (!latestMissions.isEmpty()) {
                        mission = latestMissions.get(0);
                    }
                }

                // Open weapon configuration screen
                openWeaponConfiguration(mission);
            }

            clearForm();
            selectedMission = null;
            refreshMissionTable();
        } else {
            AlertUtils.showError(owner, "Error", "Failed to save mission");
        }
    }

    /**
     * Handles the "Clear" button click.
     * Clears the form fields.
     *
     * @param event The ActionEvent object
     */
    @FXML
    protected void onClearButtonClick(ActionEvent event) {
        clearForm();
        selectedMission = null;
    }

    /**
     * Clears all form fields.
     */
    private void clearForm() {
        aircraftComboBox.setValue(null);
        flightNumberField.clear();
        missionDatePicker.setValue(LocalDate.now());
        departureTimeField.clear();
        arrivalTimeField.clear();
        selectedPositions.clear();
    }

    /**
     * Updates the selected positions map.
     * Called from the WeaponConfigurationController.
     *
     * @param positions The map of selected positions
     */
    public void updateSelectedPositions(Map<String, Map<String, String>> positions) {
        this.selectedPositions = new HashMap<>(positions);
    }
}