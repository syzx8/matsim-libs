/* *********************************************************************** *
 * project: org.matsim.*
 * MyMapViewer.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.wdoering.grips.v2.popareaselector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Rectangle2D;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import playground.wdoering.grips.scenariomanager.control.Controller;
import playground.wdoering.grips.scenariomanager.control.ShapeFactory;
import playground.wdoering.grips.scenariomanager.model.AbstractModule;
import playground.wdoering.grips.scenariomanager.model.AbstractToolBox;
import playground.wdoering.grips.scenariomanager.model.Constants;
import playground.wdoering.grips.scenariomanager.model.shape.PolygonShape;
import playground.wdoering.grips.scenariomanager.model.shape.Shape;

/**
 * the population area selector tool box
 * 
 * - open button: opens the grips configuration file - save button: saves the
 * population shape according to the destination given in the configuration
 * 
 * 
 * @author wdoering
 * 
 */
class PopToolBox extends AbstractToolBox {
	private static final long serialVersionUID = 1L;
	private JButton openBtn;
	private JButton saveButton;
	private JButton popDeleteBt;

	private Object[][] areaTableData;
	private JTable areaTable;
	private JLabel popLabel;
	private JTextField popInput;
	private String selectedAreaID = "-1";
	protected boolean editing = false;

	PopToolBox(AbstractModule module, final Controller controller) {
		super(module, controller);

		this.setLayout(new BorderLayout());

		JPanel tools = new JPanel(new BorderLayout());

		Object[] columnNames = { locale.titleAreaID(), locale.titlePopulation() };

		@SuppressWarnings("serial")
		DefaultTableModel tableModel = new DefaultTableModel() {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		areaTableData = new Object[][] {};

		tableModel.setDataVector(areaTableData, columnNames);
		areaTable = new JTable();
		areaTable.setModel(tableModel);
		areaTable.getSelectionModel().addListSelectionListener(new SelectionListener(this));
		areaTable.setSelectionBackground(Color.blue);
		areaTable.setSelectionForeground(Color.white);
		areaTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JPanel editAreaPanel = new JPanel(new GridLayout(0, 3));

		popLabel = new JLabel(locale.titlePopulation() + ": ");

		popInput = new JTextField();
		popInput.addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {
				if (!Character.toString(e.getKeyChar()).matches("[0-9]"))
					e.consume();
			}

			@Override
			public void keyReleased(KeyEvent e) {

				int i = -1;
				String id = "";
				while (i < areaTable.getRowCount()) {
					i++;
					id = (String) (areaTable.getModel()).getValueAt(i, 0);
					if (id.equals(selectedAreaID))
						break;
				}
				((DefaultTableModel) areaTable.getModel()).setValueAt(popInput.getText(), i, 1);

				if (id != "") {
					Shape shape = PopToolBox.this.controller.getShapeById(id);
					shape.putMetaData("population", popInput.getText());

				}

			}

			@Override
			public void keyPressed(KeyEvent e) {
			}
		});

		popDeleteBt = new JButton(locale.btRemove());
		popDeleteBt.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				int sel = areaTable.getSelectedRow();

				if (sel > -1) {
					editing = true;
					DefaultTableModel defModel = (DefaultTableModel) areaTable.getModel();

					if (areaTable.getSelectedRow() <= defModel.getRowCount()) {
						String id = (String) (areaTable.getModel()).getValueAt(sel, 0);
						controller.removeShape(id);

						// delete action/weight (row) in table
						((DefaultTableModel) areaTable.getModel()).removeRow(areaTable.getSelectedRow());

						controller.paintLayers();

					}

					if (defModel.getRowCount() == 0) {
						popDeleteBt.setEnabled(false);
						popInput.setEnabled(false);
						popLabel.setEnabled(false);
					}

					editing = false;
				}
			}
		});

		popLabel.setEnabled(false);
		popInput.setEnabled(false);
		popDeleteBt.setEnabled(false);

		editAreaPanel.add(popLabel);
		editAreaPanel.add(popInput);
		editAreaPanel.add(popDeleteBt);

		tools.add(new JScrollPane(areaTable), BorderLayout.CENTER);
		tools.add(editAreaPanel, BorderLayout.SOUTH);

		tools.setPreferredSize(new Dimension(320, 640));
		tools.setBorder(BorderFactory.createTitledBorder(locale.titlePopAreas()));

		this.openBtn = new JButton(locale.btOpen());
		this.saveButton = new JButton(locale.btSave());
		this.saveButton.setEnabled(false);

		this.openBtn.addActionListener(this);
		this.saveButton.addActionListener(this);

		this.add(tools, BorderLayout.CENTER);
		JPanel openAndSave = new JPanel();

		if (this.controller.isStandAlone())
			openAndSave.add(this.openBtn);

		openAndSave.add(this.saveButton);
		this.add(openAndSave, BorderLayout.SOUTH);

	}

	@Override
	public void updateMask() {
		// go through all shapes, add new ones to the table list
		for (Shape shape : this.controller.getActiveShapes()) {
			if (shape instanceof PolygonShape) {
				String popCount = shape.getMetaData("population");
				if (popCount != null) {
					DefaultTableModel defModel = (DefaultTableModel) areaTable.getModel();
					String id = shape.getId();

					boolean foundShape = false;
					for (int j = 0; j < defModel.getRowCount(); j++) {
						if (((String) areaTable.getModel().getValueAt(j, 0)).equals(id)) {
							areaTable.getModel().setValueAt(popCount, j, 1);
							foundShape = true;
							break;
						}
					}
					if (!foundShape) {
						editing = true;
						((DefaultTableModel) areaTable.getModel()).addRow(new Object[] { id, "100" });
						this.saveButton.setEnabled(false);

						popInput.setEnabled(true);
						popLabel.setEnabled(true);
						popDeleteBt.setEnabled(true);

						editing = false;
					}

				}

			}
		}

	}

	public JTable getAreaTable() {
		return areaTable;
	}

	public void setSelectedAreaID(String selectedAreaID) {
		this.selectedAreaID = selectedAreaID;
	}

	public void setSelectedAreaPop(String selectedAreaPop) {
		this.popInput.setText(selectedAreaPop);
	}

	@Override
	public void setGoalAchieved(boolean goalAchieved) {
		this.saveButton.setEnabled(goalAchieved);
		super.setGoalAchieved(goalAchieved);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();

		if (cmd.equals(locale.btOpen())) {
			if (this.controller.openGripsConfig()) {
				this.controller.disableAllRenderLayers();

				// add network bounding box shape
				int shapeRendererId = controller.getVisualizer().getPrimaryShapeRenderLayer().getId();
				Rectangle2D bbRect = controller.getBoundingBox();
				controller.addShape(ShapeFactory.getNetBoxShape(shapeRendererId, bbRect, true));

				// deactivate circle shape
				Shape shape = this.controller.getShapeById(Constants.ID_EVACAREACIRCLE);
				if (shape != null)
					shape.setVisible(false);

				this.controller.getVisualizer().getActiveMapRenderLayer().setPosition(this.controller.getCenterPosition());
				this.saveButton.setEnabled(false);
				this.controller.enableAllRenderLayers();
			}
		} else if (cmd.equals(locale.btSave())) {

			this.setGoalAchieved(controller.saveShapes(controller.getGripsConfigModule().getPopulationFileName()));
			this.controller.setGoalAchieved(this.goalAchieved);
			if (this.isGoalAchieved())
				this.controller.setPopulationFileOpened(true);

			this.saveButton.setEnabled(false);

		}

	}

	class SelectionListener implements ListSelectionListener {

		PopToolBox populationAreaSelector;

		public SelectionListener(PopToolBox popAreaSelector) {
			this.populationAreaSelector = popAreaSelector;
		}

		@Override
		public synchronized void valueChanged(ListSelectionEvent e) {

			if (!populationAreaSelector.editing) {
				int sel = populationAreaSelector.getAreaTable().getSelectedRow();
				String id = (String) (populationAreaSelector.getAreaTable().getModel()).getValueAt(sel, 0);
				String pop = String.valueOf((populationAreaSelector.getAreaTable().getModel()).getValueAt(sel, 1));

				populationAreaSelector.setSelectedAreaID(id);
				populationAreaSelector.setSelectedAreaPop(pop);

				for (Shape shape : controller.getActiveShapes()) {
					if (shape.getMetaData("population") != null)
						shape.setSelected(false);
				}

				populationAreaSelector.getController().getShapeById(id).setSelected(true);
				controller.paintLayers();

			}

		}

	}

}