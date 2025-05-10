package com.aircraft.controller;

import com.aircraft.dao.AircraftDAO;
import com.aircraft.dao.MissionDAO;
import com.aircraft.dao.RecordedDataDAO;
import com.aircraft.model.Aircraft;
import com.aircraft.model.Mission;
import com.aircraft.model.RecordedData;
import com.aircraft.util.AlertUtils;
import com.aircraft.util.DBUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Post Flight Management Data (PFMD) module.
 * Handles recording and updating flight data after missions.
 */
public class PFMDController {
    @FXML
    private ComboBox<String> aircraftComboBox;

    @FXML
    private ComboBox<String> missionComboBox;

    @FXML
    private TextField gloadMaxField;

    @FXML
    private TextField gloadMinField;

    @FXML
    private TextField quotaMediaField;

    @FXML
    private TextField velocitaMassimaField;

    // Missile position panes
    @FXML
    private Pane positionTIP1;

    @FXML
    private Pane positionO3;

    @FXML
    private Pane positionCTR5;

    @FXML
    private Pane position17;

    @FXML
    private Pane positionFWD9;

    @FXML
    private Pane positionCL13;

    @FXML
    private Pane positionFWD10;

    @FXML
    private Pane positionREA12;

    @FXML
    private Pane position18;

    @FXML
    private Pane positionCTR6;

    @FXML
    private Pane positionO4;

    @FXML
    private Pane positionTIP2;

    private final AircraftDAO aircraftDAO = new AircraftDAO();
    private final MissionDAO missionDAO = new MissionDAO();
    private final RecordedDataDAO recordedDataDAO = new RecordedDataDAO();

    // Map to track missile positions and their status
    private final Map<String, Boolean> missileStatusMap = new HashMap<>();

    // Map to track loaded weapons based on mission
    private Map<String, Map<String, String>> loadedWeapons = new HashMap<>();

    // Current selected mission ID
    private Integer currentMissionId;

    /**
     * Initializes the controller after its root element has been processed.
     * Sets up event handlers and initializes UI components.
     */
    @FXML
    public void initialize() {
        // Load aircraft data
        loadAircraftData();

        // Set up event handler for aircraft selection
        aircraftComboBox.setOnAction(event -> {
            String selectedAircraft = aircraftComboBox.getValue();
            if (selectedAircraft != null) {
                loadMissions(selectedAircraft);
            }
        });

        // Initialize missile status map
        initializeMissileStatusMap();

        // Initialize missile position styles
        updateMissilePositionStyles();
    }

    /**
     * Initializes the missile status map with all positions.
     */
    private void initializeMissileStatusMap() {
        missileStatusMap.put("TIP1", false);
        missileStatusMap.put("O/3", false);
        missileStatusMap.put("CTR 5", false);
        missileStatusMap.put("1/7", false);
        missileStatusMap.put("FWD 9", false);
        missileStatusMap.put("CL 13", false);
        missileStatusMap.put("FWD 10", false);
        missileStatusMap.put("REA 12", false);
        missileStatusMap.put("1/8", false);
        missileStatusMap.put("CTR 6", false);
        missileStatusMap.put("O/4", false);
        missileStatusMap.put("TIP 2", false);
    }

    /**
     * Loads aircraft data into the aircraft combo box.
     */
    private void loadAircraftData() {
        List<Aircraft> aircraftList = aircraftDAO.getAll();
        ObservableList<String> aircraftOptions = FXCollections.observableArrayList();

        for (Aircraft aircraft : aircraftList) {
            aircraftOptions.add(aircraft.getMatricolaVelivolo());
        }

        aircraftComboBox.setItems(aircraftOptions);
    }

    /**
     * Loads available missions for an aircraft.
     * Only shows missions that don't have recorded data yet.
     *
     * @param matricolaVelivolo The aircraft serial number
     */
    private void loadMissions(String matricolaVelivolo) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            connection = DBUtil.getConnection();

            // Query missions that don't have recorded data yet
            String query = "SELECT m.ID, m.NumeroVolo FROM missione m " +
                    "WHERE m.MatricolaVelivolo = ? " +
                    "AND NOT EXISTS (SELECT 1 FROM dati_registrati dr " +
                    "               WHERE dr.MatricolaVelivolo = m.MatricolaVelivolo " +
                    "               AND dr.NumeroVolo = m.NumeroVolo)";

            statement = connection.prepareStatement(query);
            statement.setString(1, matricolaVelivolo);
            resultSet = statement.executeQuery();

            ObservableList<String> missionOptions = FXCollections.observableArrayList();

            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                int flightNumber = resultSet.getInt("NumeroVolo");
                missionOptions.add(id + " - Flight #" + flightNumber);
            }

            missionComboBox.setItems(missionOptions);
        } catch (SQLException e) {
            Window owner = aircraftComboBox.getScene().getWindow();
            AlertUtils.showError(owner, "Database Error", "Failed to load missions: " + e.getMessage());
        } finally {
            DBUtil.closeResources(connection, statement, resultSet);
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
     * Loads the weapons/missiles configuration for a mission.
     * Handles cases where tables don't exist yet.
     *
     * @param missionId The mission ID
     */
    private void loadMissionWeapons(int missionId) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            connection = DBUtil.getConnection();

            // Clear existing data
            loadedWeapons.clear();

            // Check if tables exist before trying to query them
            boolean historicalLoadExists = tableExists(connection, "historical_load");
            boolean historicalLauncherExists = tableExists(connection, "historical_launcher");

            if (historicalLoadExists) {
                // Query weapons from historical_load
                String loadQuery = "SELECT 'weapon' as type, position, weapon_id as item_id, serial_number " +
                        "FROM historical_load WHERE mission_id = ?";
                statement = connection.prepareStatement(loadQuery);
                statement.setInt(1, missionId);
                resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    String position = resultSet.getString("position");
                    String type = resultSet.getString("type");
                    String itemId = resultSet.getString("item_id");
                    String serialNumber = resultSet.getString("serial_number");

                    Map<String, String> itemData = new HashMap<>();
                    itemData.put("type", type);
                    itemData.put("id", itemId);
                    itemData.put("serialNumber", serialNumber);

                    loadedWeapons.put(position, itemData);

                    // Initialize all loaded positions as "not fired"
                    String statusKey = getStatusKeyForPosition(position);
                    if (statusKey != null) {
                        missileStatusMap.put(statusKey, false);
                    }
                }

                // Close resources
                DBUtil.closeResources(null, statement, resultSet);
            }

            if (historicalLauncherExists) {
                // Query launchers from historical_launcher
                String launcherQuery = "SELECT 'launcher' as type, position, launcher_id as item_id, serial_number " +
                        "FROM historical_launcher WHERE mission_id = ?";
                statement = connection.prepareStatement(launcherQuery);
                statement.setInt(1, missionId);
                resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    String position = resultSet.getString("position");
                    String type = resultSet.getString("type");
                    String itemId = resultSet.getString("item_id");
                    String serialNumber = resultSet.getString("serial_number");

                    Map<String, String> itemData = new HashMap<>();
                    itemData.put("type", type);
                    itemData.put("id", itemId);
                    itemData.put("serialNumber", serialNumber);

                    loadedWeapons.put(position, itemData);

                    // Launchers themselves can't be fired, but we add them to the map for display purposes
                    String statusKey = getStatusKeyForPosition(position);
                    if (statusKey != null) {
                        missileStatusMap.put(statusKey, false);
                    }
                }
            }

            if (!historicalLoadExists && !historicalLauncherExists) {
                // Both tables don't exist, show a friendlier message
                Window owner = aircraftComboBox.getScene().getWindow();
                AlertUtils.showInformation(owner, "No Weapon Configuration",
                        "No weapon configuration found for this mission. Please configure weapons in the Mission Management screen first.");
            }
        } catch (SQLException e) {
            Window owner = aircraftComboBox.getScene().getWindow();
            AlertUtils.showError(owner, "Database Error", "Failed to load mission weapons: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBUtil.closeResources(connection, statement, resultSet);
        }
    }

    /**
     * Maps position code to status key used in the missileStatusMap.
     *
     * @param position The position code from the database
     * @return The corresponding status key
     */
    private String getStatusKeyForPosition(String position) {
        // Map database position codes to UI position identifiers
        switch (position) {
            case "TIP 1": return "TIP1";
            case "O/B 3": return "O/3";
            case "CTR 5": return "CTR 5";
            case "I/B 7": return "1/7";
            case "FWD 9": return "FWD 9";
            case "CL 13": return "CL 13";
            case "FWD 10": return "FWD 10";
            case "REA 12": return "REA 12";
            case "I/B 8": return "1/8";
            case "CTR 6": return "CTR 6";
            case "O/B 4": return "O/4";
            case "TIP 2": return "TIP 2";
            default: return null;
        }
    }

    /**
     * Gets database position code from status key.
     *
     * @param statusKey The status key used in missileStatusMap
     * @return The corresponding database position code
     */
    private String getPositionForStatusKey(String statusKey) {
        // Map UI position identifiers to database position codes
        switch (statusKey) {
            case "TIP1": return "TIP 1";
            case "O/3": return "O/B 3";
            case "CTR 5": return "CTR 5";
            case "1/7": return "I/B 7";
            case "FWD 9": return "FWD 9";
            case "CL 13": return "CL 13";
            case "FWD 10": return "FWD 10";
            case "REA 12": return "REA 12";
            case "1/8": return "I/B 8";
            case "CTR 6": return "CTR 6";
            case "O/4": return "O/B 4";
            case "TIP 2": return "TIP 2";
            default: return null;
        }
    }

    /**
     * Handles the "Load Mission Data" button click.
     * Loads mission data and weapon configuration.
     *
     * @param event The ActionEvent object
     */
    @FXML
    protected void onLoadMissionDataClick(ActionEvent event) {
        String selectedAircraft = aircraftComboBox.getValue();
        String selectedMissionStr = missionComboBox.getValue();

        if (selectedAircraft == null || selectedMissionStr == null) {
            Window owner = aircraftComboBox.getScene().getWindow();
            AlertUtils.showError(owner, "Selection Error", "Please select both an aircraft and a mission");
            return;
        }

        // Extract mission ID from the selection (format: "ID - Flight #XX")
        int missionId = Integer.parseInt(selectedMissionStr.split(" - ")[0]);
        currentMissionId = missionId;

        // Load mission weapons configuration
        loadMissionWeapons(missionId);

        // Clear form fields
        clearFormFields();

        // Update UI to reflect loaded weapons
        updateMissilePositionStyles();
    }

    /**
     * Clears the form fields.
     */
    private void clearFormFields() {
        gloadMaxField.clear();
        gloadMinField.clear();
        quotaMediaField.clear();
        velocitaMassimaField.clear();
    }

    /**
     * Updates the missile position pane styles based on their status.
     */
    private void updateMissilePositionStyles() {
        updateMissilePositionStyle(positionTIP1, "TIP1");
        updateMissilePositionStyle(positionO3, "O/3");
        updateMissilePositionStyle(positionCTR5, "CTR 5");
        updateMissilePositionStyle(position17, "1/7");
        updateMissilePositionStyle(positionFWD9, "FWD 9");
        updateMissilePositionStyle(positionCL13, "CL 13");
        updateMissilePositionStyle(positionFWD10, "FWD 10");
        updateMissilePositionStyle(positionREA12, "REA 12");
        updateMissilePositionStyle(position18, "1/8");
        updateMissilePositionStyle(positionCTR6, "CTR 6");
        updateMissilePositionStyle(positionO4, "O/4");
        updateMissilePositionStyle(positionTIP2, "TIP 2");
    }

    /**
     * Updates the style of a single missile position pane.
     *
     * @param pane The Pane object
     * @param position The position identifier
     */
    private void updateMissilePositionStyle(Pane pane, String position) {
        if (pane == null) return;

        // Get database position code
        String dbPosition = getPositionForStatusKey(position);

        // Check if weapon is loaded at this position
        boolean weaponLoaded = dbPosition != null && loadedWeapons.containsKey(dbPosition);
        boolean fired = missileStatusMap.getOrDefault(position, false);

        // Remove existing status classes
        pane.getStyleClass().removeAll("missile-position", "missile-fired", "missile-onboard", "missile-empty");

        // Add appropriate status class
        pane.getStyleClass().add("missile-position");

        if (!weaponLoaded) {
            pane.getStyleClass().add("missile-empty");
            pane.setStyle("-fx-background-color: #DDDDDD; -fx-opacity: 0.5;");
        } else if (fired) {
            pane.getStyleClass().add("missile-fired");
            pane.setStyle("-fx-background-color: #FF6666; -fx-border-color: #990000; -fx-border-width: 2;");
        } else {
            pane.getStyleClass().add("missile-onboard");
            pane.setStyle("-fx-background-color: #66FF66; -fx-border-color: #009900; -fx-border-width: 2;");
        }
    }

    /**
     * Handles clicks on missile position panes.
     * Toggles the status of the clicked missile position.
     *
     * @param event The MouseEvent object
     */
    @FXML
    protected void onMissilePositionClick(MouseEvent event) {
        if (currentMissionId == null) {
            Window owner = aircraftComboBox.getScene().getWindow();
            AlertUtils.showWarning(owner, "No Mission Selected", "Please load a mission first");
            return;
        }

        Pane clickedPane = (Pane) event.getSource();
        String position = (String) clickedPane.getUserData();

        // Get database position code
        String dbPosition = getPositionForStatusKey(position);

        // Check if weapon is loaded at this position
        if (dbPosition == null || !loadedWeapons.containsKey(dbPosition)) {
            Window owner = aircraftComboBox.getScene().getWindow();
            AlertUtils.showWarning(owner, "No Weapon", "No weapon loaded at position " + position);
            return;
        }

        // Only weapons can be fired, not launchers
        Map<String, String> itemData = loadedWeapons.get(dbPosition);
        if ("launcher".equals(itemData.get("type"))) {
            Window owner = aircraftComboBox.getScene().getWindow();
            AlertUtils.showWarning(owner, "Cannot Fire Launcher", "Launchers cannot be fired. Only weapons can be fired.");
            return;
        }

        // Toggle the status
        boolean currentStatus = missileStatusMap.getOrDefault(position, false);
        missileStatusMap.put(position, !currentStatus);

        // Update the UI
        updateMissilePositionStyle(clickedPane, position);
    }

    /**
     * Builds a missile status string from the current state of the status map.
     *
     * @return The formatted missile status string
     */
    private String buildMissileStatusString() {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Boolean> entry : missileStatusMap.entrySet()) {
            String position = entry.getKey();
            boolean fired = entry.getValue();

            // Get database position code
            String dbPosition = getPositionForStatusKey(position);

            // Only include positions with weapons loaded
            if (dbPosition != null && loadedWeapons.containsKey(dbPosition)) {
                // Only weapons can be fired, not launchers
                Map<String, String> itemData = loadedWeapons.get(dbPosition);
                if ("weapon".equals(itemData.get("type"))) {
                    if (sb.length() > 0) {
                        sb.append("; ");
                    }

                    sb.append(position)
                            .append(":")
                            .append(fired ? "SPARATO" : "A_BORDO");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Handles the "Save Data" button click.
     * Validates and saves the recorded flight data.
     *
     * @param event The ActionEvent object
     */
    @FXML
    protected void onSaveDataClick(ActionEvent event) {
        Window owner = gloadMaxField.getScene().getWindow();

        if (currentMissionId == null) {
            AlertUtils.showError(owner, "No Mission Selected", "Please load a mission first");
            return;
        }

        // Get aircraft and mission number
        String selectedAircraft = aircraftComboBox.getValue();

        // Get mission number from database based on mission ID
        Integer missionNumber = getMissionNumber(currentMissionId);

        if (missionNumber == null) {
            AlertUtils.showError(owner, "Database Error", "Failed to get mission number");
            return;
        }

        // Validate numeric fields
        BigDecimal gloadMax, gloadMin;
        Integer quotaMedia, velocitaMassima;

        try {
            gloadMax = new BigDecimal(gloadMaxField.getText().replace(',', '.'));
            gloadMin = new BigDecimal(gloadMinField.getText().replace(',', '.'));
            quotaMedia = Integer.parseInt(quotaMediaField.getText());
            velocitaMassima = Integer.parseInt(velocitaMassimaField.getText());
        } catch (NumberFormatException e) {
            AlertUtils.showError(owner, "Validation Error", "Please enter valid numeric values for all fields");
            return;
        }

        // Create recorded data object
        RecordedData recordedData = new RecordedData();
        recordedData.setMatricolaVelivolo(selectedAircraft);
        recordedData.setNumeroVolo(missionNumber);
        recordedData.setGloadMax(gloadMax);
        recordedData.setGloadMin(gloadMin);
        recordedData.setQuotaMedia(quotaMedia);
        recordedData.setVelocitaMassima(velocitaMassima);
        recordedData.setStatoMissili(buildMissileStatusString());
        recordedData.setStatoElaborato(true);

        // Save data to database
        boolean success = recordedDataDAO.insert(recordedData);

        // Show appropriate message
        if (success) {
            AlertUtils.showInformation(owner, "Success", "Flight data saved successfully");

            // Clear form and update mission list
            clearForm();
            loadMissions(selectedAircraft);
        } else {
            AlertUtils.showError(owner, "Error", "Failed to save flight data");
        }
    }

    /**
     * Gets the mission number from the mission ID.
     *
     * @param missionId The mission ID
     * @return The mission number or null if not found
     */
    private Integer getMissionNumber(int missionId) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            connection = DBUtil.getConnection();

            String query = "SELECT NumeroVolo FROM missione WHERE ID = ?";
            statement = connection.prepareStatement(query);
            statement.setInt(1, missionId);
            resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getInt("NumeroVolo");
            }

            return null;
        } catch (SQLException e) {
            Window owner = aircraftComboBox.getScene().getWindow();
            AlertUtils.showError(owner, "Database Error", "Failed to get mission number: " + e.getMessage());
            return null;
        } finally {
            DBUtil.closeResources(connection, statement, resultSet);
        }
    }

    /**
     * Handles the "Clear Form" button click.
     * Clears all form fields and resets missile status.
     *
     * @param event The ActionEvent object
     */
    @FXML
    protected void onClearFormClick(ActionEvent event) {
        clearForm();
    }

    /**
     * Clears all form fields and resets missile status.
     */
    private void clearForm() {
        clearFormFields();
        currentMissionId = null;

        // Reset missile status
        for (String position : missileStatusMap.keySet()) {
            missileStatusMap.put(position, false);
        }

        // Clear loaded weapons
        loadedWeapons.clear();

        // Update UI
        updateMissilePositionStyles();
    }
}