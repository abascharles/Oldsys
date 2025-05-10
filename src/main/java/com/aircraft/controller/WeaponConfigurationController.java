package com.aircraft.controller;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import com.aircraft.util.AlertUtils;
import com.aircraft.util.DBUtil;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller for the Weapon Configuration screen.
 * This screen allows users to select and configure weapons and launchers
 * at different positions on the aircraft.
 */
public class WeaponConfigurationController implements Initializable {

    @FXML private ImageView aircraftImageView;
    @FXML private GridPane positionsGrid;
    @FXML private Label positionLabel;
    @FXML private ComboBox<String> weaponTypeComboBox;
    @FXML private ComboBox<String> weaponIdComboBox;
    @FXML private TextField serialNumberField;
    @FXML private Button savePositionButton;
    @FXML private Button clearPositionButton;
    @FXML private Button closeButton;

    private MissionManagementController parentController;
    private Map<String, Map<String, String>> selectedPositions = new HashMap<>();
    private String currentSelectedPosition;

    // Positions on the aircraft hardpoints
    private final String[] positions = {
            "TIP 1", "O/B 3", "CTR 5", "I/B 7", "REA 11",
            "FWD 9", "CL 13", "FWD 10", "REA 12",
            "I/B 8", "CTR 6", "O/B 4", "TIP 2"
    };

    private Map<String, Rectangle> positionRectangles = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize weapon type combo box
        weaponTypeComboBox.getItems().addAll("Weapon", "Launcher");
        weaponTypeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadWeaponIds(newVal);
            }
        });

        // Initialize position selectors
        initializePositionRectangles();

        // Disable position selection controls initially
        disablePositionControls(true);
    }

    /**
     * Initializes the position rectangles in the grid.
     * Creates clickable rectangles for each position on the aircraft.
     */
    private void initializePositionRectangles() {
        // Clear existing grid
        positionsGrid.getChildren().clear();

        // Create rectangles for each position
        for (int i = 0; i < positions.length; i++) {
            final String position = positions[i];

            // Create rectangle
            Rectangle rect = new Rectangle(60, 100);
            rect.setFill(Color.LIGHTGRAY);
            rect.setStroke(Color.BLACK);
            rect.setOpacity(0.7);

            // Create label
            Text label = new Text(position);
            label.setFill(Color.BLACK);

            // Create stack pane to hold rectangle and label
            StackPane stackPane = new StackPane();
            stackPane.getChildren().addAll(rect, label);

            // Add to grid
            positionsGrid.add(stackPane, i, 0);

            // Store rectangle for later reference
            positionRectangles.put(position, rect);

            // Add event handlers
            stackPane.setOnMouseClicked(e -> selectPosition(position));

            // Update rectangle color if position is already selected
            if (selectedPositions.containsKey(position)) {
                rect.setFill(Color.GREEN);
            }
        }
    }

    /**
     * Handles selection of a position on the aircraft.
     * Updates the UI and loads existing data if available.
     *
     * @param position The selected position identifier
     */
    private void selectPosition(String position) {
        // Update current selected position
        currentSelectedPosition = position;
        positionLabel.setText("Selected Position: " + position);

        // Enable position controls
        disablePositionControls(false);

        // Load existing data if position is already selected
        if (selectedPositions.containsKey(position)) {
            Map<String, String> data = selectedPositions.get(position);

            String type = data.get("type");
            weaponTypeComboBox.setValue(type.substring(0, 1).toUpperCase() + type.substring(1)); // Capitalize first letter

            weaponIdComboBox.setValue(data.get("id"));
            serialNumberField.setText(data.get("serialNumber"));
        } else {
            // Clear fields
            weaponTypeComboBox.setValue(null);
            weaponIdComboBox.setValue(null);
            serialNumberField.clear();
        }

        // Highlight selected position
        for (String pos : positions) {
            Rectangle rect = positionRectangles.get(pos);

            if (pos.equals(position)) {
                rect.setStroke(Color.RED);
                rect.setStrokeWidth(3);
            } else {
                rect.setStroke(Color.BLACK);
                rect.setStrokeWidth(1);
            }
        }
    }

    /**
     * Loads weapon IDs (part numbers) based on the selected weapon type.
     *
     * @param type The weapon type ("Weapon" or "Launcher")
     */
    private void loadWeaponIds(String type) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            connection = DBUtil.getConnection();

            String query;
            if ("Weapon".equals(type)) {
                query = "SELECT PartNumber FROM anagrafica_carichi";
            } else { // Launcher
                query = "SELECT PartNumber FROM anagrafica_lanciatore";
            }

            statement = connection.prepareStatement(query);
            resultSet = statement.executeQuery();

            weaponIdComboBox.getItems().clear();

            while (resultSet.next()) {
                weaponIdComboBox.getItems().add(resultSet.getString("PartNumber"));
            }
        } catch (SQLException e) {
            Window owner = weaponTypeComboBox.getScene().getWindow();
            AlertUtils.showError(owner, "Database Error", "Failed to load weapon IDs: " + e.getMessage());
        } finally {
            DBUtil.closeResources(connection, statement, resultSet);
        }
    }

    /**
     * Enables or disables the position controls.
     *
     * @param disable True to disable controls, false to enable them
     */
    private void disablePositionControls(boolean disable) {
        weaponTypeComboBox.setDisable(disable);
        weaponIdComboBox.setDisable(disable);
        serialNumberField.setDisable(disable);
        savePositionButton.setDisable(disable);
        clearPositionButton.setDisable(disable);
    }

    /**
     * Handles the "Save Position" button click.
     * Validates inputs and saves the position data.
     *
     * @param event The ActionEvent object
     */
    @FXML
    private void handleSavePosition(ActionEvent event) {
        Window owner = savePositionButton.getScene().getWindow();

        // Validate inputs
        if (weaponTypeComboBox.getValue() == null) {
            AlertUtils.showError(owner, "Validation Error", "Weapon type is required");
            return;
        }

        if (weaponIdComboBox.getValue() == null) {
            AlertUtils.showError(owner, "Validation Error", "Part Number is required");
            return;
        }

        if (serialNumberField.getText().isEmpty()) {
            AlertUtils.showError(owner, "Validation Error", "Serial Number is required");
            return;
        }

        // Save position data
        Map<String, String> data = new HashMap<>();
        data.put("type", weaponTypeComboBox.getValue().toLowerCase());
        data.put("id", weaponIdComboBox.getValue());
        data.put("serialNumber", serialNumberField.getText());

        selectedPositions.put(currentSelectedPosition, data);

        // Update UI
        Rectangle rect = positionRectangles.get(currentSelectedPosition);
        rect.setFill(Color.GREEN);

        // Clear selection
        clearSelection();
    }

    /**
     * Clears the current selection.
     */
    private void clearSelection() {
        currentSelectedPosition = null;
        positionLabel.setText("Selected Position: None");
        disablePositionControls(true);

        // Remove highlight from all positions
        for (String pos : positions) {
            Rectangle rect = positionRectangles.get(pos);
            rect.setStroke(Color.BLACK);
            rect.setStrokeWidth(1);
        }
    }

    /**
     * Handles the "Clear Position" button click.
     * Removes the weapon from the selected position.
     *
     * @param event The ActionEvent object
     */
    @FXML
    private void handleClearPosition(ActionEvent event) {
        if (currentSelectedPosition != null) {
            // Remove position data
            selectedPositions.remove(currentSelectedPosition);

            // Update UI
            Rectangle rect = positionRectangles.get(currentSelectedPosition);
            rect.setFill(Color.LIGHTGRAY);

            // Clear selection
            clearSelection();
        }
    }

    /**
     * Handles the "Close" button click.
     * Updates the parent controller and closes the window.
     *
     * @param event The ActionEvent object
     */
    @FXML
    private void handleClose(ActionEvent event) {
        // Update parent controller
        if (parentController != null) {
            parentController.updateSelectedPositions(selectedPositions);
        }

        // Close window
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Sets the selected positions from the parent controller.
     *
     * @param positions The map of selected positions
     */
    public void setSelectedPositions(Map<String, Map<String, String>> positions) {
        this.selectedPositions = new HashMap<>(positions);

        // Update UI after positions grid is initialized
        if (!positionRectangles.isEmpty()) {
            for (String position : positions.keySet()) {
                Rectangle rect = positionRectangles.get(position);
                if (rect != null) {
                    rect.setFill(Color.GREEN);
                }
            }
        }
    }

    /**
     * Sets the parent controller.
     *
     * @param controller The MissionManagementController instance
     */
    public void setParentController(MissionManagementController controller) {
        this.parentController = controller;
    }
}