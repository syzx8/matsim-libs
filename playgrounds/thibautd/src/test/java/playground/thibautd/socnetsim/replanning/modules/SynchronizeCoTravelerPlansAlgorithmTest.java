/* *********************************************************************** *
 * project: org.matsim.*
 * SynchronizeCoTravelerPlansAlgorithmTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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
package playground.thibautd.socnetsim.replanning.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.router.EmptyStageActivityTypes;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;

import playground.thibautd.socnetsim.population.DriverRoute;
import playground.thibautd.socnetsim.population.JointActingTypes;
import playground.thibautd.socnetsim.population.JointPlan;
import playground.thibautd.socnetsim.population.JointPlanFactory;
import playground.thibautd.socnetsim.population.PassengerRoute;

/**
 * @author thibautd
 */
public class SynchronizeCoTravelerPlansAlgorithmTest {
	private final List<Fixture> fixtures = new ArrayList<Fixture>();

	// /////////////////////////////////////////////////////////////////////////
	// fixtures
	// /////////////////////////////////////////////////////////////////////////
	private static class Fixture {
		final JointPlan jointPlan;
		final Map<Activity, Double> expectedEndTimes;

		public Fixture(
				final JointPlan jp,
				final Map<Activity, Double> expectedEndTimes) {
			this.jointPlan = jp;
			this.expectedEndTimes = expectedEndTimes;
		}
	}

	@After
	public void clear() {
		fixtures.clear();
	}

	@Before
	public void createSimpleFixture() {
		final FixtureBuilder builder = new FixtureBuilder();

		final Id driverId = new IdImpl( "driver" );
		final Id passengerId1 = new IdImpl( "p1" );
		final Id passengerId2 = new IdImpl( "p2" );

		final Id link1 = new IdImpl( "link1" );
		final Id link2 = new IdImpl( "link2" );
		final Id link3 = new IdImpl( "link3" );
		final Id link4 = new IdImpl( "link4" );

		// DRIVER
		builder.startPerson( driverId );

		builder.startActivity( "h" , link1 );
		builder.setCurrentActivityEndTime( 100 );

		builder.startLeg( TransportMode.car , 100 );

		builder.startActivity( JointActingTypes.INTERACTION , link2 );

		builder.startLeg( JointActingTypes.DRIVER , 100 );
		final DriverRoute dr1 = new DriverRoute( link2 , link3 );
		dr1.addPassenger( passengerId1 );
		builder.setCurrentLegRoute( dr1 );

		builder.startActivity( JointActingTypes.INTERACTION , link3 );

		builder.startLeg( JointActingTypes.DRIVER , 100 );
		final DriverRoute dr2 = new DriverRoute( link3 , link4 );
		dr2.addPassenger( passengerId1 );
		dr2.addPassenger( passengerId2 );
		builder.setCurrentLegRoute( dr2 );

		builder.startActivity( JointActingTypes.INTERACTION , link4 );

		builder.startLeg( TransportMode.car , 100 );

		builder.startActivity( "h" , link1 );

		// PASSENGER 1
		builder.startPerson( passengerId1 );

		builder.startActivity( "h" , link3 );
		builder.setCurrentActivityEndTime( 200 );
		builder.setCurrentActivityExpectedEndTime( 0d );

		builder.startLeg( TransportMode.walk , 200 );

		builder.startActivity( JointActingTypes.INTERACTION , link2 );

		builder.startLeg( JointActingTypes.PASSENGER , 200 );
		final PassengerRoute pr1 = new PassengerRoute( link2 , link4 );
		pr1.setDriverId( driverId );
		builder.setCurrentLegRoute( pr1 );

		builder.startActivity( JointActingTypes.INTERACTION , link4 );

		builder.startLeg( TransportMode.car , 200 );

		builder.startActivity( "h" , link1 );

		// PASSENGER 2
		builder.startPerson( passengerId2 );

		builder.startActivity( "h" , link2 );
		builder.setCurrentActivityEndTime( 50 );
		builder.setCurrentActivityExpectedEndTime(  250d );

		builder.startLeg( TransportMode.walk );
		final Route walkRoute = new GenericRouteImpl( link2 , link3 );
		walkRoute.setTravelTime( 50 );
		builder.setCurrentLegRoute( walkRoute );

		builder.startActivity( JointActingTypes.INTERACTION , link3 );

		builder.startLeg( JointActingTypes.PASSENGER );
		final PassengerRoute pr2 = new PassengerRoute( link3 , link4 );
		pr2.setDriverId( driverId );
		pr2.setTravelTime( 50 );
		builder.setCurrentLegRoute( pr2 );

		builder.startActivity( JointActingTypes.INTERACTION , link4 );

		builder.startLeg( TransportMode.walk , 50 );

		builder.startActivity( "h" , link2 );

		fixtures.add( builder.build() );
	}

	@Before
	public void createFixtureWithPotentiallyNegativeEndTimes() {
		final FixtureBuilder builder = new FixtureBuilder();

		final Id driverId = new IdImpl( "driver" );
		final Id passengerId1 = new IdImpl( "p1" );
		final Id passengerId2 = new IdImpl( "p2" );

		final Id link1 = new IdImpl( "link1" );
		final Id link2 = new IdImpl( "link2" );
		final Id link3 = new IdImpl( "link3" );
		final Id link4 = new IdImpl( "link4" );

		// DRIVER
		builder.startPerson( driverId );

		builder.startActivity( "h" , link1 );
		builder.setCurrentActivityEndTime( 100 );

		builder.startLeg( TransportMode.car , 100 );

		builder.startActivity( JointActingTypes.INTERACTION , link2 );

		builder.startLeg( JointActingTypes.DRIVER , 100 );
		final DriverRoute dr1 = new DriverRoute( link2 , link3 );
		dr1.addPassenger( passengerId1 );
		builder.setCurrentLegRoute( dr1 );

		builder.startActivity( JointActingTypes.INTERACTION , link3 );

		builder.startLeg( JointActingTypes.DRIVER , 100 );
		final DriverRoute dr2 = new DriverRoute( link3 , link4 );
		dr2.addPassenger( passengerId1 );
		dr2.addPassenger( passengerId2 );
		builder.setCurrentLegRoute( dr2 );

		builder.startActivity( JointActingTypes.INTERACTION , link4 );

		builder.startLeg( TransportMode.car , 100 );

		builder.startActivity( "h" , link1 );

		// PASSENGER 1
		builder.startPerson( passengerId1 );

		builder.startActivity( "h" , link3 );
		builder.setCurrentActivityEndTime( 100 );
		builder.setCurrentActivityExpectedEndTime( 0d );

		builder.startLeg( TransportMode.walk , 2000 );

		builder.startActivity( JointActingTypes.INTERACTION , link2 );

		builder.startLeg( JointActingTypes.PASSENGER , 200 );
		final PassengerRoute pr1 = new PassengerRoute( link2 , link4 );
		pr1.setDriverId( driverId );
		builder.setCurrentLegRoute( pr1 );

		builder.startActivity( JointActingTypes.INTERACTION , link4 );

		builder.startLeg( TransportMode.car , 200 );

		builder.startActivity( "h" , link1 );

		// PASSENGER 2
		builder.startPerson( passengerId2 );

		builder.startActivity( "h" , link2 );
		builder.setCurrentActivityEndTime( 50 );
		builder.setCurrentActivityExpectedEndTime( 0 );

		builder.startLeg( TransportMode.walk );
		final Route walkRoute = new GenericRouteImpl( link2 , link3 );
		walkRoute.setTravelTime( 500000000 );
		builder.setCurrentLegRoute( walkRoute );

		builder.startActivity( JointActingTypes.INTERACTION , link3 );

		builder.startLeg( JointActingTypes.PASSENGER );
		final PassengerRoute pr2 = new PassengerRoute( link3 , link4 );
		pr2.setDriverId( driverId );
		pr2.setTravelTime( 50 );
		builder.setCurrentLegRoute( pr2 );

		builder.startActivity( JointActingTypes.INTERACTION , link4 );

		builder.startLeg( TransportMode.walk , 50 );

		builder.startActivity( "h" , link2 );

		fixtures.add( builder.build() );
	}

	// to help creation of fixtures
	private static class FixtureBuilder {
		final PopulationFactory popFact = ScenarioUtils.createScenario( ConfigUtils.createConfig() ).getPopulation().getFactory();
		private final Map<Id, Plan> plans = new HashMap<Id, Plan>();
		private Plan currentPlan = null;
		private Activity currentActivity = null;
		private Leg currentLeg = null;

		private final Map<Activity, Double> expectedEndTimes = new HashMap<Activity, Double>();

		public void startPerson(final Id id) {
			final Person person = popFact.createPerson( id );
			this.currentPlan = popFact.createPlan();
			currentPlan.setPerson( person );
			person.addPlan( currentPlan );
			plans.put( id , currentPlan );
		}

		public void startActivity(final String type, final Id link) {
			currentLeg = null;
			currentActivity = popFact.createActivityFromLinkId( type , link );
			currentPlan.addActivity( currentActivity );
		}

		public void setCurrentActivityEndTime(final double endTime) {
			currentActivity.setEndTime( endTime );
		}

		public void setCurrentActivityExpectedEndTime(final double expectedEndTime) {
			expectedEndTimes.put( currentActivity , expectedEndTime );
		}

		public void startLeg(final String transportMode, double tt) {
			startLeg( transportMode );
			currentLeg.setTravelTime( tt );
		}

		public void startLeg(final String transportMode) {
			currentActivity = null;
			currentLeg = popFact.createLeg( transportMode );
			currentPlan.addLeg( currentLeg );
		}

		public void setCurrentLegRoute(final Route route) {
			currentLeg.setRoute( route );
		}

		public Fixture build() {
			return new Fixture(
						new JointPlanFactory().createJointPlan( plans ),
						expectedEndTimes);
		}
	}

	// /////////////////////////////////////////////////////////////////////////
	// tests
	// /////////////////////////////////////////////////////////////////////////
	@Test
	public void testDepartureTimes() throws Exception {
		final SynchronizeCoTravelerPlansAlgorithm testee = new SynchronizeCoTravelerPlansAlgorithm( EmptyStageActivityTypes.INSTANCE );
		for ( Fixture fixture : fixtures ) {
			testee.run( fixture.jointPlan );

			for ( Plan p : fixture.jointPlan.getIndividualPlans().values() ) {
				for ( Activity activity : TripStructureUtils.getActivities( p , EmptyStageActivityTypes.INSTANCE ) ) {
					final Double endTime = fixture.expectedEndTimes.remove( activity );
					if ( endTime == null ) continue;

					Assert.assertEquals(
							"unexpected end time for "+activity,
							endTime.doubleValue(),
							activity.getEndTime(),
							MatsimTestUtils.EPSILON);
				}
			}

			Assert.assertTrue(
					"some activities were not found: "+fixture.expectedEndTimes.keySet(),
					fixture.expectedEndTimes.isEmpty() );
		}
	}


}