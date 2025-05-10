package com.aircraft.controller;

import com.aircraft.dao.WeaponDAO;
import com.aircraft.model.Weapon;
import com.aircraft.util.AlertUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller for the Material Handling screen.
 * Handles creating, updating, and deleting weapon/cargo data.
 */
public class MaterialHandlingController {

    @FXML
    private TextField partNumberField;

    @FXML
    private TextField nomenclaturaField;

    @FXML
    private TextField codiceDittaField;

    @FXML
    private TextField massaField;

    @FXML
    private Button saveButton;

    @FXML
    private TableView<Weapon> weaponTable;

    @FXML
    private TableColumn<Weapon, String> partNumberColumn;

    @FXML
    private TableColumn<Weapon, String> nomenclaturaColumn;

    @FXML
    private TableColumn<Weapon, String> codiceDittaColumn;

    @FXML
    private TableColumn<Weapon, BigDecimal> massaColumn;

    @FXML
    private TableColumn<Weapon, Void> actionsColumn;

    private final WeaponDAO weaponDAO = new WeaponDAO();
    private ObservableList<Weapon> weaponList = FXCollections.observableArrayList();
    private Weapon selectedWeapon = null;

    /**
     * Initializes the controller after its root element has been processed.
     * Sets up event handlers and initializes UI components.
     */
    @FXML
    public void initialize() {
        // Set up table columns
        partNumberColumn.setCellValueFactory(new PropertyValueFactory<>("partNumber"));
        nomenclaturaColumn.setCellValueFactory(new PropertyValueFactory<>("nomenclatura"));
        codiceDittaColumn.setCellValueFactory(new PropertyValueFactory<>("codiceDitta"));
        massaColumn.setCellValueFactory(new PropertyValueFactory<>("massa"));

        // Set up action column with Edit and Delete buttons
        setupActionsColumn();

        // Load weapon data
        refreshWeaponTable();
    }

    /**
     * Sets up the actions column with Edit and Delete buttons.
     */
    private void setupActionsColumn() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Delete");
            private final HBox buttons = new HBox(5, editButton, deleteButton);

            {
                // Set up button handlers
                editButton.setOnAction(event -> {
                    Weapon weapon = getTableView().getItems().get(getIndex());
                    editWeapon(weapon);
                });

                deleteButton.setOnAction(event -> {
                    Weapon weapon = getTableView().getItems().get(getIndex());
                    deleteWeapon(weapon);
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
     * Loads a weapon for editing.
     *
     * @param weapon The Weapon object to edit
     */
    private void editWeapon(Weapon weapon) {
        // Set selected weapon
        selectedWeapon = weapon;

        // Populate form fields
        partNumberField.setText(weapon.getPartNumber());
        nomenclaturaField.setText(weapon.getNomenclatura());
        codiceDittaField.setText(weapon.getCodiceDitta());
        massaField.setText(weapon.getMassa() != null ? weapon.getMassa().toString() : "");
    }

    /**
     * Deletes a weapon after confirmation.
     *
     * @param weapon The Weapon object to delete
     */
    private void deleteWeapon(Weapon weapon) {
        Window owner = weaponTable.getScene().getWindow();

        // Confirm deletion
        boolean confirmed = AlertUtils.showConfirmation(
                owner,
                "Confirm Deletion",
                "Are you sure you want to delete weapon: " + weapon.getNomenclatura() + "?"
        );

        if (confirmed) {
            // Delete weapon
            boolean success = weaponDAO.delete(weapon.getPartNumber());

            if (success) {
                AlertUtils.showInformation(owner, "Success", "Weapon deleted successfully");
                refreshWeaponTable();
            } else {
                AlertUtils.showError(owner, "Error", "Failed to delete weapon");
            }
        }
    }

    /**
     * Refreshes the weapon table with data from the database.
     */
    private void refreshWeaponTable() {
        List<Weapon> weapons = weaponDAO.getAll();
        weaponList.clear();
        weaponList.addAll(weapons);
        weaponTable.setItems(weaponList);
    }

    /**
     * Handles the "Save" button click.
     * Validates and saves the weapon data to the database.
     *
     * @param event The ActionEvent object
     */
    @FXML
    protected void onSaveButtonClick(ActionEvent event) {
        Window owner = saveButton.getScene().getWindow();

        // Validate input fields
        if (partNumberField.getText().isEmpty()) {
            AlertUtils.showError(owner, "Validation Error", "Part Number is required");
            return;
        }

        if (nomenclaturaField.getText().isEmpty()) {
            AlertUtils.showError(owner, "Validation Error", "Nomenclatura is required");
            return;
        }

        // Create or update weapon object
        Weapon weapon;
        if (selectedWeapon == null) {
            // Create new weapon
            weapon = new Weapon();
        } else {
            // Update existing weapon
            weapon = selectedWeapon;
        }

        weapon.setPartNumber(partNumberField.getText());
        weapon.setNomenclatura(nomenclaturaField.getText());
        weapon.setCodiceDitta(codiceDittaField.getText());

        // Parse massa field
        try {
            if (!massaField.getText().isEmpty()) {
                BigDecimal massa = new BigDecimal(massaField.getText());
                weapon.setMassa(massa);
            } else {
                weapon.setMassa(null);
            }
        } catch (NumberFormatException e) {
            AlertUtils.showError(owner, "Validation Error", "Massa must be a valid number");
            return;
        }

        // Save weapon
        boolean success;
        if (selectedWeapon == null) {
            // Check if weapon already exists
            if (weaponDAO.existsByPartNumber(weapon.getPartNumber())) {
                AlertUtils.showError(owner, "Validation Error", "Weapon with this Part Number already exists");
                return;
            }

            success = weaponDAO.insert(weapon);
        } else {
            success = weaponDAO.update(weapon);
        }

        if (success) {
            AlertUtils.showInformation(owner, "Success", "Weapon saved successfully");
            clearForm();
            selectedWeapon = null;
            refreshWeaponTable();
        } else {
            AlertUtils.showError(owner, "Error", "Failed to save weapon");
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
        selectedWeapon = null;
    }

    /**
     * Clears all form fields.
     */
    private void clearForm() {
        partNumberField.clear();
        nomenclaturaField.clear();
        codiceDittaField.clear();
        massaField.clear();
    }
}