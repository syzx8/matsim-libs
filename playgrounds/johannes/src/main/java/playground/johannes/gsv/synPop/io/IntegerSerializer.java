/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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

package playground.johannes.gsv.synPop.io;

/**
 * @author johannes
 *
 */
public class IntegerSerializer implements AttributeSerializer {

	private static IntegerSerializer instance;
	
	public static IntegerSerializer instance() {
		if(instance == null)
			instance = new IntegerSerializer();
		return instance;
	}
	
	@Override
	public String encode(Object value) {
		return String.valueOf(value);
	}

	@Override
	public Object decode(String value) {
		return Integer.parseInt(value);
	}

}
