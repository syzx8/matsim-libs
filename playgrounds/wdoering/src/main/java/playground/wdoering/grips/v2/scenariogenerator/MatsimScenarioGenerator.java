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

package playground.wdoering.grips.v2.scenariogenerator;

import java.awt.image.BufferedImage;

import playground.wdoering.grips.scenariomanager.control.Controller;
import playground.wdoering.grips.scenariomanager.model.AbstractModule;
import playground.wdoering.grips.scenariomanager.model.AbstractToolBox;
import playground.wdoering.grips.scenariomanager.model.Constants;
import playground.wdoering.grips.scenariomanager.model.imagecontainer.BufferedImageContainer;
import playground.wdoering.grips.scenariomanager.model.process.BasicProcess;
import playground.wdoering.grips.scenariomanager.view.DefaultWindow;

public class MatsimScenarioGenerator extends AbstractModule {

	private MSGMask msgMask;
	public static void main(String[] args) {
		final Controller controller = new Controller();

		// set up controller and image interface
		BufferedImage image = new BufferedImage(width - border * 2, height - border * 2, BufferedImage.TYPE_INT_ARGB);
		BufferedImageContainer imageContainer = new BufferedImageContainer(image, border);
		controller.setImageContainer(imageContainer);

		// inform controller that this module is running stand alone
		controller.setStandAlone(true);

		// instantiate evacuation area selector
		MatsimScenarioGenerator scenario = new MatsimScenarioGenerator(controller);

		// create default window for running this module standalone
		DefaultWindow frame = new DefaultWindow(controller);

		// set parent component to forward the (re)paint event
		controller.setParentComponent(frame);

		// start the process chain
		scenario.start();
		frame.requestFocus();
	}

	public MatsimScenarioGenerator(Controller controller) {
		super(controller.getLocale().moduleMatsimScenarioGenerator(), Constants.ModuleType.MATSIMSCENARIO, controller);

		this.processList.add(new BasicProcess(this, this.controller) {
			@Override
			public void start() {
				// in case this is only part of something bigger
				controller.disableAllRenderLayers();

				msgMask = new MSGMask(controller);
				this.controller.setMainPanel(msgMask, false);

				this.controller.setToolBoxVisible(false);

				// check if Matsim config (including the OSM network) has been
				// loaded
				if (!controller.isMatsimConfigOpened())
					if (!controller.openMastimConfig())
						exit(locale.msgOpenMatsimConfigFailed());

				msgMask.readConfig();

				// finally: enable all layers
				controller.enableAllRenderLayers();

			}
		});
	}

	@Override
	public AbstractToolBox getToolBox() {
		return null;
	}


}