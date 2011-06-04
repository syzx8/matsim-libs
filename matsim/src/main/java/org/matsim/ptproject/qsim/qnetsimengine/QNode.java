/* *********************************************************************** *
 * project: org.matsim.*
 * QueueNode.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.ptproject.qsim.qnetsimengine;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.internal.MatsimComparator;
import org.matsim.core.events.AgentStuckEventImpl;
import org.matsim.ptproject.qsim.interfaces.NetsimEngine;
import org.matsim.ptproject.qsim.interfaces.NetsimLink;
import org.matsim.ptproject.qsim.interfaces.NetsimNode;
import org.matsim.vis.snapshots.writers.VisData;

/**
 * Represents a node in the QueueSimulation.
 */
public class QNode implements NetsimNode {

	private static final Logger log = Logger.getLogger(QNode.class);

	private static final QueueLinkIdComparator qlinkIdComparator = new QueueLinkIdComparator();

	protected final QLinkInternalI[] inLinksArrayCache;
	private final QLinkInternalI[] tempLinks;

	private boolean active = false;

	private final Node node;

	// necessary if Nodes are (de)activated
	private NetElementActivator activator = null;
	
	// for Customizable
	private Map<String, Object> customAttributes = new HashMap<String, Object>();

	/**
	 * Indicates whether this node is signalized or not
	 */
  private QSimEngineImpl simEngine;

	protected QNode(final Node n, final NetsimEngine simEngine) {
		this.node = n;
		this.simEngine = (QSimEngineImpl) simEngine; // needs to be of correct impl type when it arrives here.  kai, jun'10
		this.activator = this.simEngine;	// by default (single threaded QSim)
		int nofInLinks = this.node.getInLinks().size();
		this.inLinksArrayCache = new QLinkInternalI[nofInLinks];
		this.tempLinks = new QLinkInternalI[nofInLinks];
	}

	/**
	 * Loads the inLinks-array with the corresponding links.
	 * Cannot be called in constructor, as the queueNetwork does not yet know
	 * the queueLinks. Should be called by QueueNetwork, after creating all
	 * QueueNodes and QueueLinks.
	 */
	/*package*/ void init() {
		int i = 0;
		for (Link l : this.node.getInLinks().values()) {
			this.inLinksArrayCache[i] = this.simEngine.getNetsimNetwork().getNetsimLinks().get(l.getId());
			// yyyy changed simEngine.getQSim.getQNetwork to simEngine.getQNetwork.  Not sure if this is the same in parallel
			// implementations.  kai, oct'10
			i++;
		}
		/* As the order of nodes has an influence on the simulation results,
		 * the nodes are sorted to avoid indeterministic simulations. dg[april08]
		 */
		Arrays.sort(this.inLinksArrayCache, QNode.qlinkIdComparator);
	}

	@Override
	public Node getNode() {
		return this.node;
	}

	/*
	 * The ParallelQSim replaces the activator with the QSimEngineRunner 
	 * that handles this node.
	 */
	/*package*/ void setNetElementActivator(NetElementActivator activator) {
		this.activator = activator;
	}
	
	protected final void activateNode() {
		if (!this.active) {
			this.activator.activateNode(this);
			this.active = true;
		}
	}

	 final boolean isActive() {
		return this.active;
	}

	/**
	 * Moves vehicles from the inlinks' buffer to the outlinks where possible.<br>
	 * The inLinks are randomly chosen, and for each link all vehicles in the
	 * buffer are moved to their desired outLink as long as there is space. If the
	 * front most vehicle in a buffer cannot move across the node because there is
	 * no free space on its destination link, the work on this inLink is finished
	 * and the next inLink's buffer is handled (this means, that at the node, all
	 * links have only like one lane, and there are no separate lanes for the
	 * different outLinks. Thus if the front most vehicle cannot drive further,
	 * all other vehicles behind must wait, too, even if their links would be
	 * free).
	 *
	 * @param now
	 *          The current time in seconds from midnight.
	 * @param random the random number generator to be used
	 */
	protected void moveNode(final double now, final Random random) {
		int inLinksCounter = 0;
		double inLinksCapSum = 0.0;
		// Check all incoming links for buffered agents
		for (QLinkInternalI link : this.inLinksArrayCache) {
			if (!link.bufferIsEmpty()) {
				this.tempLinks[inLinksCounter] = link;
				inLinksCounter++;
				inLinksCapSum += link.getLink().getCapacity(now);
			}
		}

		if (inLinksCounter == 0) {
			this.active = false;
			return; // Nothing to do
		}

		int auxCounter = 0;
		// randomize based on capacity
		while (auxCounter < inLinksCounter) {
			double rndNum = random.nextDouble() * inLinksCapSum;
			double selCap = 0.0;
			for (int i = 0; i < inLinksCounter; i++) {
				QLinkInternalI link = this.tempLinks[i];
				if (link == null)
					continue;
				selCap += link.getLink().getCapacity(now);
				if (selCap >= rndNum) {
					auxCounter++;
					inLinksCapSum -= link.getLink().getCapacity(now);
					this.tempLinks[i] = null;
					//move the link
					this.clearLinkBuffer(link, now);
					break;
				}
			}
		}
	}

  protected void clearLinkBuffer(final QLinkInternalI link, final double now){
  	if (link instanceof QLinkImpl){
      while (!link.bufferIsEmpty()) {
        QVehicle veh = link.getFirstFromBuffer();
        if (!moveVehicleOverNode(veh, link, now)) {
          break;
        }
      }
    }
    else {
      for (QLane lane : ((QLinkLanesImpl)link).getToNodeQueueLanes()) {
      	if (lane.isThisTimeStepGreen()){
      		while (!lane.bufferIsEmpty()) {
      			QVehicle veh = lane.getFirstFromBuffer();
      			if (!moveVehicleOverNode(veh, lane, now)) {
      				break;
      			}
      		}
      	}
      }
    }
  }


	protected void checkNextLinkSemantics(Link currentLink, Link nextLink, QVehicle veh){
    if (currentLink.getToNode() != nextLink.getFromNode()) {
      throw new RuntimeException("Cannot move vehicle " + veh.getId() +
          " from link " + currentLink.getId() + " to link " + nextLink.getId());
    }
	}

  // ////////////////////////////////////////////////////////////////////
  // Queue related movement code
  // ////////////////////////////////////////////////////////////////////
  /**
   * @param veh
   * @param qbufferedItem
   * @param now
   * @return <code>true</code> if the vehicle was successfully moved over the node, <code>false</code>
   * otherwise (e.g. in case where the next link is jammed)
   */
  protected boolean moveVehicleOverNode(final QVehicle veh, final QBufferItem qbufferedItem, final double now) {
    Id nextLinkId = veh.getDriver().chooseNextLinkId();
    Link currentLink = veh.getCurrentLink();
    if ((!qbufferedItem.hasGreenForToLink(nextLinkId))) {
    		if (!((now - qbufferedItem.getBufferLastMovedTime()) > this.simEngine.getStuckTime())){
    			return false;
    		}
    }
    // veh has to move over node
    if (nextLinkId != null) {
      QLinkInternalI nextQueueLink = this.simEngine.getNetsimNetwork().getNetsimLinks().get(nextLinkId);
      Link nextLink = nextQueueLink.getLink();
      this.checkNextLinkSemantics(currentLink, nextLink, veh);
      if (nextQueueLink.hasSpace()) {
        qbufferedItem.popFirstFromBuffer();
        veh.getDriver().notifyMoveOverNode(nextLinkId);
        nextQueueLink.addFromIntersection(veh);
        return true;
      }

      // check if veh is stuck!

      if ((now - qbufferedItem.getBufferLastMovedTime()) > this.simEngine.getStuckTime()) {
        /* We just push the vehicle further after stucktime is over, regardless
         * of if there is space on the next link or not.. optionally we let them
         * die here, we have a config setting for that!
         */
        if (this.simEngine.getMobsim().getScenario().getConfig().getQSimConfigGroup().isRemoveStuckVehicles()) {
          qbufferedItem.popFirstFromBuffer();
          this.simEngine.getMobsim().getAgentCounter().decLiving();
          this.simEngine.getMobsim().getAgentCounter().incLost();
          this.simEngine.getMobsim().getEventsManager().processEvent(
              new AgentStuckEventImpl(now, veh.getDriver().getId(), currentLink.getId(), veh.getDriver().getMode()));
        } else {
          qbufferedItem.popFirstFromBuffer();
          veh.getDriver().notifyMoveOverNode(nextLinkId);
          nextQueueLink.addFromIntersection(veh);
          return true;
        }
      }
      return false;
    }

    // --> nextLink == null
    qbufferedItem.popFirstFromBuffer();
    this.simEngine.getMobsim().getAgentCounter().decLiving();
    this.simEngine.getMobsim().getAgentCounter().incLost();
    log.error(
        "Agent has no or wrong route! agentId=" + veh.getDriver().getId()
            + " currentLink=" + currentLink.getId().toString()
            + ". The agent is removed from the simulation.");
    return true;
  }


	protected static class QueueLinkIdComparator implements Comparator<NetsimLink>, Serializable, MatsimComparator {
		private static final long serialVersionUID = 1L;
		@Override
		public int compare(final NetsimLink o1, final NetsimLink o2) {
			return o1.getLink().getId().compareTo(o2.getLink().getId());
		}
	}

	@Override
	public VisData getVisData() {
		return null;
	}

	@Override
	public Map<String, Object> getCustomAttributes() {
		return customAttributes;
	}

}
