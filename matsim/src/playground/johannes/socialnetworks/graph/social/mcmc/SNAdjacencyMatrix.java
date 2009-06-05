/* *********************************************************************** *
 * project: org.matsim.*
 * SNAdjacencyMatrix.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

/**
 * 
 */
package playground.johannes.socialnetworks.graph.social.mcmc;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;

import org.matsim.api.basic.v01.population.BasicPerson;

import playground.johannes.socialnetworks.graph.social.Ego;
import playground.johannes.socialnetworks.graph.social.SocialNetwork;
import playground.johannes.socialnetworks.graph.social.SocialNetworkFactory;
import playground.johannes.socialnetworks.graph.spatial.SpatialAdjacencyMatrix;

/**
 * @author illenberger
 *
 */
public class SNAdjacencyMatrix<P extends BasicPerson<?>> extends SpatialAdjacencyMatrix {

	public SNAdjacencyMatrix(SocialNetwork<P> g) {
		super(g);
	}

	
	public SocialNetwork<P> getGraph() {
		SocialNetworkFactory<P> factory = new SocialNetworkFactory<P>(null);
		SocialNetwork<P> g = new SocialNetwork<P>();

		TIntObjectHashMap<Ego<P>> vertexIdx = new TIntObjectHashMap<Ego<P>>();
		for(int i = 0; i < getVertexCount(); i++) {
			Ego<P> ego = factory.addVertex(g, getVertex(i).getPerson());
			vertexIdx.put(i, ego);
		}
		
		for(int i = 0; i < getVertexCount(); i++) {
			TIntArrayList row = getNeighbours(i);
			if(row != null) {
				for(int idx = 0; idx < row.size(); idx++) {
					int j = row.get(idx);
					if(j > i) {
						if(factory.addEdge(g, vertexIdx.get(i), vertexIdx.get(j)) == null)
							throw new RuntimeException();
					}
				}
			}
		}
		
		return g;
	}
	
	@SuppressWarnings("unchecked")
	public Ego<P> getVertex(int i) {
		return (Ego<P>) super.getVertex(i);
	}
}
