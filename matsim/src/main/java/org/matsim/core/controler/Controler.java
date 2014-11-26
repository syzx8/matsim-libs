/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007, 2008 by the members listed in the COPYING,  *
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

package org.matsim.core.controler;

import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.matsim.analysis.CalcLinkStats;
import org.matsim.analysis.IterationStopWatch;
import org.matsim.analysis.ScoreStats;
import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.consistency.ConfigConsistencyCheckerImpl;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.SimulationConfigGroup;
import org.matsim.core.controler.corelisteners.*;
import org.matsim.core.controler.listener.ControlerListener;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.mobsim.external.ExternalMobsim;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.framework.MobsimFactory;
import org.matsim.core.mobsim.framework.ObservableMobsim;
import org.matsim.core.mobsim.framework.listeners.MobsimListener;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyFactory;
import org.matsim.core.replanning.StrategyManager;
import org.matsim.core.replanning.StrategyManagerConfigLoader;
import org.matsim.core.replanning.selectors.PlanSelectorFactory;
import org.matsim.core.router.*;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.*;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.population.algorithms.AbstractPersonAlgorithm;
import org.matsim.population.algorithms.ParallelPersonAlgorithmRunner;
import org.matsim.population.algorithms.PersonPrepareForSim;
import org.matsim.pt.PtConstants;
import org.matsim.pt.router.TransitRouterFactory;
import org.matsim.vis.snapshotwriters.SnapshotWriter;
import org.matsim.vis.snapshotwriters.SnapshotWriterFactory;
import org.matsim.vis.snapshotwriters.SnapshotWriterManager;

import java.util.*;

/**
 * The Controler is responsible for complete simulation runs, including the
 * initialization of all required data, running the iterations and the
 * replanning, analyses, etc.
 *
 * @author mrieser
 */
public class Controler extends AbstractController {
	// yyyy Design thoughts:
	// * Seems to me that we should try to get everything here final.  Flexibility is provided by the ability to set or add factories.  If this is
	// not sufficient, people should use AbstractController.  kai, jan'13

	public static final String DIRECTORY_ITERS = "ITERS";
	public static final String FILENAME_EVENTS_TXT = "events.txt.gz";
	public static final String FILENAME_EVENTS_XML = "events.xml.gz";
	public static final String FILENAME_LINKSTATS = "linkstats.txt.gz";
    public static final String FILENAME_TRAVELDISTANCESTATS = "traveldistancestats";
	public static final String FILENAME_POPULATION = "output_plans.xml.gz";
	public static final String FILENAME_NETWORK = "output_network.xml.gz";
	public static final String FILENAME_HOUSEHOLDS = "output_households.xml.gz";
	public static final String FILENAME_LANES = "output_lanes.xml.gz";
	public static final String FILENAME_CONFIG = "output_config.xml.gz";
	public static final String FILENAME_PERSON_ATTRIBUTES = "output_personAttributes.xml.gz" ; 

	protected static final Logger log = Logger.getLogger(Controler.class);

	public static final Layout DEFAULTLOG4JLAYOUT = new PatternLayout(
			"%d{ISO8601} %5p %C{1}:%L %m%n");

	protected final Config config; 
	protected final Scenario scenarioData ;

	protected final EventsManager events;

    private Injector injector;

    public static interface TerminationCriterion {
		boolean continueIterations( int iteration ) ;
	}

	private TerminationCriterion terminationCriterion = new TerminationCriterion() {

		@Override
		public boolean continueIterations(int iteration) {
			return (iteration <= config.controler().getLastIteration());
		}

	};

    // DefaultControlerModule includes submodules. If you want less than what the Controler does
    // by default, you can leave ControlerDefaultsModule out, look at what it does,
    // and only include what you want.
    private List<AbstractModule> modules = Arrays.<AbstractModule>asList(new ControlerDefaultsModule());

    private ScoringFunctionFactory scoringFunctionFactory = null;
	private StrategyManager strategyManager = null;

	private boolean scenarioLoaded = false;

	private final List<MobsimListener> simulationListeners = new ArrayList<>();

	private TravelDisutilityFactory travelCostCalculatorFactory ; 

	private MobsimFactory thisMobsimFactory = null;

	private TripRouterFactoryBuilderWithDefaults tripRouterFactoryBuilder = new TripRouterFactoryBuilderWithDefaults();
	private TripRouterFactory tripRouterFactory = null;

	private MobsimFactoryRegister mobsimFactoryRegister;
	private SnapshotWriterFactoryRegister snapshotWriterRegister;
	private PlanStrategyFactoryRegister planStrategyFactoryRegister;
    private PlanSelectorFactoryRegister planSelectorFactoryRegister;

	private boolean dumpDataAtEnd = true; 
	private boolean overwriteFiles = false;

	private boolean routerCreated = false;
	private boolean componentOfDefaultTripRouterFactoryChanged = false;

	public static void main(final String[] args) {
		if ((args == null) || (args.length == 0)) {
			System.out.println("No argument given!");
			System.out.println("Usage: Controler config-file [dtd-file]");
			System.out.println();
		} else {
			final Controler controler = new Controler(args);
			controler.run();
		}
		System.exit(0);
	}

	/**
	 * Initializes a new instance of Controler with the given arguments.
	 *
	 * @param args
	 *            The arguments to initialize the controler with.
	 *            <code>args[0]</code> is expected to contain the path to a
	 *            configuration file, <code>args[1]</code>, if set, is expected
	 *            to contain the path to a local copy of the DTD file used in
	 *            the configuration file.
	 */
	public Controler(final String[] args) {
		this(args.length > 0 ? args[0] : null, null, null);
	}

	public Controler(final String configFileName) {
		this(configFileName, null, null);
	}

	public Controler(final Config config) {
		this(null, config, null);
	}

	public Controler(final Scenario scenario) {
		this(null, null, scenario);
	}

	private Controler(final String configFileName, final Config config, final Scenario scenario) {
        this.controlerListenerManager.setControler(this);
        if (scenario != null) {
			this.scenarioLoaded = true;
			this.scenarioData  = scenario;
			this.config = scenario.getConfig();
			this.config.addConfigConsistencyChecker(new ConfigConsistencyCheckerImpl());
		} else {
			if (configFileName == null) {
				if (config == null) {
					throw new IllegalArgumentException("Either the config or the filename of a configfile must be set to initialize the Controler.");
				}
				this.config = config;
			} else {
				this.config = ConfigUtils.loadConfig(configFileName);
			}
			this.config.addConfigConsistencyChecker(new ConfigConsistencyCheckerImpl());
			this.scenarioData  = ScenarioUtils.createScenario(this.config);
		}
		MobsimRegistrar mobsimRegistrar = new MobsimRegistrar();
		this.mobsimFactoryRegister = mobsimRegistrar.getFactoryRegister();
		SnapshotWriterRegistrar snapshotWriterRegistrar = new SnapshotWriterRegistrar();
		this.snapshotWriterRegister = snapshotWriterRegistrar.getFactoryRegister();
		PlanStrategyRegistrar planStrategyFactoryRegistrar = new PlanStrategyRegistrar();
		this.planStrategyFactoryRegister = planStrategyFactoryRegistrar.getFactoryRegister();
        PlanSelectorRegistrar planSelectorRegistrar = new PlanSelectorRegistrar();
        this.planSelectorFactoryRegister = planSelectorRegistrar.getFactoryRegister();
		
		this.events = EventsUtils.createEventsManager(this.config);

		this.travelCostCalculatorFactory = ControlerDefaults.createDefaultTravelDisutilityFactory(scenario);

		this.config.parallelEventHandling().makeLocked();
	}

	/**
	 * Starts the iterations.
	 */
	public final void run() {
		setupOutputDirectory(this.config.controler().getOutputDirectory(), this.config.controler().getRunId(), this.overwriteFiles);
		if (this.config.scenario().isUseTransit()) {
			setupTransitSimulation();
		}
		loadData();
		// I find it difficult (to teach) that the behavior between the constructor and controler.run() depends on which constructor
		// you call. kai, apr'14

		run(config);

		// "run(config)" is:
		//		loadCoreListeners();
		//		this.controlerListenerManager.fireControlerStartupEvent();
		//		checkConfigConsistencyAndWriteToLog(config, "config dump before iterations start" ) ;
		//		prepareForSim();
		//		doIterations(config.controler().getFirstIteration(), config.global().getRandomSeed());
		//		shutdown(false);

	}

	private void setupTransitSimulation() {
		// yyyy this should go away somehow. :-)
		
		log.info("setting up transit simulation");
		if (!this.config.scenario().isUseVehicles()) {
			log.warn("Your are using Transit but not Vehicles. This most likely won't work.");
		}

		ActivityParams transitActivityParams = new ActivityParams(PtConstants.TRANSIT_ACTIVITY_TYPE);
		transitActivityParams.setTypicalDuration(120.0);

		// The following two lines were introduced in nov/12.  _In addition_, the conversion of ActivityParams to
		// ActivityUtilityParameters will set the scoreAtAll flag to false (also introduced in nov/12).  kai, nov'12
		transitActivityParams.setOpeningTime(0.) ;
		transitActivityParams.setClosingTime(0.) ;

		this.config.planCalcScore().addActivityParams(transitActivityParams);
		// yy would this overwrite user-defined definitions of "pt interaction"?
		// No, I think that the user-defined parameters are set later, in fact overwriting this setting here.
		// kai, nov'12

		// the QSim reads the config by itself, and configures itself as a
		// transit-enabled mobsim. kai, nov'11
	}

	/**
	 * Loads the Scenario if it was not given in the constructor.
	 */
	protected void loadData() {
		// yyyy cannot make this final since it is overridden about 16 times. kai, jan'13
		// confirmed as evil. nov'14

		if (!this.scenarioLoaded) {
			ScenarioUtils.loadScenario(this.scenarioData );
			this.scenarioLoaded = true;
		}
	}

	/**
	 * Loads a default set of {@link org.matsim.core.controler.listener
	 * ControlerListener} to provide basic functionality. 
	 * <p/>
	 * Method is final now.  If you think that you need to over-write this method, start from AbstractController instead.
	 */
	@Override
	protected final void loadCoreListeners() {
		/*
		 * The order how the listeners are added is very important! As
		 * dependencies between different listeners exist or listeners may read
		 * and write to common variables, the order is important.
		 *
		 * IMPORTANT: The execution order is reverse to the order the listeners
		 * are added to the list.
		 */

		if (this.dumpDataAtEnd) {
			this.addCoreControlerListener(new DumpDataAtEnd(scenarioData , getControlerIO()));
		}


		if (this.getScoringFunctionFactory() == null) {
			this.setScoringFunctionFactory(ControlerDefaults.createDefaultScoringFunctionFactory(this.scenarioData )) ;
		}

        this.injector = Injector.createInjector(
                config,
                new AbstractModule() {
                    @Override
                    public void install() {
                        for (AbstractModule module : modules) {
                            include(module);
                        }
                        // Bootstrap it with the Scenario and some controler context.
                        bindToInstance(OutputDirectoryHierarchy.class, getControlerIO());
                        bindToInstance(IterationStopWatch.class, stopwatch);
                        bindToInstance(Scenario.class, scenarioData);
                    }
                });
        this.injector.retrofitScoringFunctionFactory(this.getScoringFunctionFactory());
        PlansScoring plansScoring = new PlansScoring(this.scenarioData , this.events, getControlerIO(), this.getScoringFunctionFactory());
		this.addCoreControlerListener(plansScoring);

		this.strategyManager = loadStrategyManager() ;
        this.addCoreControlerListener(new PlansReplanning(this.getStrategyManager(), getScenario().getPopulation()));
		this.addCoreControlerListener(new PlansDumping(this.scenarioData , this.getConfig().controler().getFirstIteration(), this.config.controler().getWritePlansInterval(),
				this.stopwatch, this.getControlerIO() ));

		this.addCoreControlerListener(new EventsHandling(this.events, this.getConfig().controler().getWriteEventsInterval(),
				this.getConfig().controler().getEventsFileFormats(), this.getControlerIO() ));
		// must be last being added (=first being executed)


		loadControlerListeners();
	}

	/**
	 * Loads the default set of {@link org.matsim.core.controler.listener
	 * ControlerListener} to provide some more basic functionality. Unlike the
	 * core ControlerListeners the order in which the listeners of this method
	 * are added must not affect the correctness of the code.
	 */
	protected void loadControlerListeners() {
		// Cannot make this method final since is is overridden about 13 times.  kai, jan'13
		// Yet it looks like this will remain non-final since it makes some sense to override these (with or without super....).
		// The core controler listeners are separate, after all.  kai, feb'13
		// yyyy should be possible to make this final. kn/mz, nov'14

        Set<EventHandler> eventHandlersDeclaredByModules = this.injector.getEventHandlersDeclaredByModules();
        for (EventHandler eventHandler : eventHandlersDeclaredByModules) {
            this.events.addHandler(eventHandler);
        }
        Set<ControlerListener> controlerListenersDeclaredByModules = this.injector.getControlerListenersDeclaredByModules();
        for (ControlerListener controlerListener : controlerListenersDeclaredByModules) {
            this.addControlerListener(controlerListener);
        }
	}

	@Override
	protected final void prepareForSim() {

		if ( scenarioData  instanceof ScenarioImpl ) {
			((ScenarioImpl)scenarioData ).setLocked();
			// see comment in ScenarioImpl. kai, sep'14
		}

		setUp();

		// make sure all routes are calculated.
        ParallelPersonAlgorithmRunner.run(getScenario().getPopulation(), this.config.global().getNumberOfThreads(),
				new ParallelPersonAlgorithmRunner.PersonAlgorithmProvider() {
			@Override
			public AbstractPersonAlgorithm getPersonAlgorithm() {
				return new PersonPrepareForSim(new PlanRouter(
				getTripRouterFactory().instantiateAndConfigureTripRouter(),
				getScenario().getActivityFacilities()
				), Controler.this.scenarioData );
			}
		});
	}

	/**
	 * Initializes the Controler with the parameters from the configuration.
	 * This method is called after the configuration is loaded, after the
	 * scenario data (network, population) is read, and after all ControlerListeners
	 * have processed their startup event.
	 * <p/>
	 * Design comments/questions:<ul>
	 * <li> "from the configuration" sounds too narrow.  Should be something like "from everything that is there at this point,
	 * including, say, factories."  kai, dec'12
	 * </ul>
	 */
	@Deprecated // overwriting this method is deprecated.  Please talk to MZ or KN if you think that you really need this. nov'14
	protected void setUp() {
		// yyyy MZ will, class by class, see if other constructs are possible.  kn/mz, nov'14
		
		// yyyy cannot make this final since it is overridden at about 25 locations.  kai, jan'13
        if ( tripRouterFactory == null ) {
			// This builder is just for compatibility, in case somebody
			// uses setTransitRouter or setLeastCostPathCalculatorFactory.
			tripRouterFactory = tripRouterFactoryBuilder.build(scenarioData );

			// Special case
			/* dg 09-2013: I do not see the "special" case for link to link routing. Compared to the other "special cases" that are considered 
			* in TripRouterFactoryImpl explicitly, creating a inverted network (a line graph for routing) considering turning move restrictions
			* during construction of the graph used for routing seems to be conceptually straight forward to be used with 
			* shortest path algorithms. */
			if (this.getScenario().getConfig().controler().isLinkToLinkRoutingEnabled()) {
                tripRouterFactory = new LinkToLinkTripRouterFactory(
						getScenario(),
						new DijkstraFactory(),
						getTravelDisutilityFactory(),
                        injector.getInstance(LinkToLinkTravelTime.class),
						getScenario().getPopulation().getFactory(),
						(DefaultTripRouterFactoryImpl) tripRouterFactory);
			}
		}
		routerCreated = true;

	}

    /**
	 * @return A fully initialized StrategyManager for the plans replanning.
	 */
	protected StrategyManager loadStrategyManager() {
		// yy cannot make this final: overridden at about 40 locations.  kai, jan'2013
		// now about 20 locations.  kai, may'2013
		// now about 15 locations.  kai, oct'14
		
		// this is not too bad since this is essentially a factory method.  So overwriting this method essentially means replacing the factory.
		// With current coding standard, would need setStrategyManagerFactory(...).  However, with future coding standard, this
		// will just be a module which can be replaced.  kai/mz, nov'14
		
        for (final Map.Entry<String, PlanStrategy> entry : this.injector.getPlanStrategiesDeclaredByModules().entrySet()) {
            this.planStrategyFactoryRegister.register(entry.getKey(), new PlanStrategyFactory() {
                @Override
                public PlanStrategy createPlanStrategy(Scenario scenario, EventsManager eventsManager) {
                    return entry.getValue();
                }
            });
        }
		StrategyManager manager = new StrategyManager();
		StrategyManagerConfigLoader.load(getScenario(), getControlerIO(), getEvents(), manager, this.planStrategyFactoryRegister, this.planSelectorFactoryRegister);
		return manager;
	}

	@Override
	protected final boolean continueIterations(int it) {
		return terminationCriterion.continueIterations(it);
	}

	@Override
	protected void runMobSim() {
		// yyyy cannot make this final: overridden at about 15 locations.  kai, jan'13
		Mobsim sim = getNewMobsim();
		sim.run();
	}

	private Mobsim getNewMobsim() {
		// overridden once for a test case (not so bad since it is package protected). kai, jan'13
		if (this.thisMobsimFactory != null) {
			Mobsim simulation = this.thisMobsimFactory.createMobsim(this.getScenario(), this.getEvents());
			enrichSimulation(simulation);
			return simulation;
		} else if (this.config.getModule(SimulationConfigGroup.GROUP_NAME) != null && 
				((SimulationConfigGroup) this.config.getModule(SimulationConfigGroup.GROUP_NAME)).getExternalExe() != null ) {
			ExternalMobsim simulation = new ExternalMobsim(this.scenarioData , this.events);
			simulation.setControlerIO(this.getControlerIO());
			simulation.setIterationNumber(this.getIterationNumber());
			return simulation;
		} else {
			MobsimFactory f = this.mobsimFactoryRegister.getInstance(this.config.controler().getMobsim());
			Mobsim simulation = f.createMobsim(this.getScenario(), this.getEvents());
			enrichSimulation(simulation);
			return simulation;
		}
	}

	private void enrichSimulation(final Mobsim simulation) {
		if (simulation instanceof ObservableMobsim) {
			for (MobsimListener l : this.getMobsimListeners()) {
				((ObservableMobsim) simulation).addQueueSimulationListeners(l);
			}

			if (config.controler().getWriteSnapshotsInterval() != 0 && this.getIterationNumber() % config.controler().getWriteSnapshotsInterval() == 0) {
				SnapshotWriterManager manager = new SnapshotWriterManager(config);
				for (String snapshotFormat : this.config.controler().getSnapshotFormat()) {
					SnapshotWriterFactory snapshotWriterFactory = this.snapshotWriterRegister.getInstance(snapshotFormat);
					String baseFileName = snapshotWriterFactory.getPreferredBaseFilename();
					String fileName = this.getControlerIO().getIterationFilename(this.getIterationNumber(), baseFileName);
					SnapshotWriter snapshotWriter = snapshotWriterFactory.createSnapshotWriter(fileName, this.scenarioData );
					manager.addSnapshotWriter(snapshotWriter);
				}
				((ObservableMobsim) simulation).addQueueSimulationListeners(manager);
			}
		}

	}

	// ******** --------- *******
	// The following is the internal interface of the Controler, which
	// is meant to be called while the Controler is running (not before),
	// meaning mostly from ControlerListeners, except possibly the StartupListener,
	// as the TravelTimeCalculator and the TripRouterFactory aren't available there yet.
	//
	// These, or some of these, would probably go on the ControlerEvents,
	// when they could stop passing the whole Controler.
	// 
	// Please try and do not use them for cascaded setting from the outside,
	// i.e. get something, look if it is instance of something,
	// and then change something on it. Or wrap it in something else and set it again.
	// These things are basically made to be
	// used, not changed. Send me (michaz) a mail if you need to do it.
	// I really want to sort this out.
	// ******** --------- *******

	public final TravelTime getLinkTravelTimes() {
        return injector.getInstance(TravelTime.class);
	}

    public final TripRouterFactoryInternal getTripRouterFactory() {
		return new TripRouterFactoryInternal() {

			@Override
			public TripRouter instantiateAndConfigureTripRouter() {
				return Controler.this.tripRouterFactory.instantiateAndConfigureTripRouter(new RoutingContext() {

					@Override
					public TravelDisutility getTravelDisutility() {
						return createTravelDisutilityCalculator();
					}

					@Override
					public TravelTime getTravelTime() {
						return getLinkTravelTimes();
					}

				});
			}

		};
	}
	
	public final TravelDisutility createTravelDisutilityCalculator() {
        return this.travelCostCalculatorFactory.createTravelDisutility(
                getLinkTravelTimes(), this.config.planCalcScore());
	}

	public final LeastCostPathCalculatorFactory getLeastCostPathCalculatorFactory() {
		if (!(this.tripRouterFactory instanceof DefaultTripRouterFactoryImpl)) {
			throw new RuntimeException("You've set a custom TripRouter. Then please also keep the routing algorithm by yourself.");
		}
		return ((DefaultTripRouterFactoryImpl) this.tripRouterFactory).getLeastCostPathCalculatorFactory();
	}

	/**
	 * @return the currently used
	 *         {@link org.matsim.core.scoring.ScoringFunctionFactory} for
	 *         scoring plans.
	 */
	public final ScoringFunctionFactory getScoringFunctionFactory() {
		return this.scoringFunctionFactory;
	}

	public final Config getConfig() {
		return this.config;
	}

    public final EventsManager getEvents() {
		return this.events;
	}

	public final Scenario getScenario() {
		return this.scenarioData ;
	}

	/**
	 * @deprecated Do not use this, as it may not contain values in every
	 *             iteration
	 * @return
	 */
	@Deprecated
	public final CalcLinkStats getLinkStats() {
		return this.injector.getInstance(CalcLinkStats.class);
	}

    public final VolumesAnalyzer getVolumes() {
		return this.injector.getInstance(VolumesAnalyzer.class);
	}

	public final ScoreStats getScoreStats() {
		return this.injector.getInstance(ScoreStats.class);
	}

	@SuppressWarnings("static-method")
	@Deprecated
	public final PlansScoring getPlansScoring() {
		throw new RuntimeException("To modify scoring for your Agents, please either:" +
                "(1) throw a PersonMoneyEvent for an appropriate amount or" +
                "(2) set a custom ScoringFunctionFactory which calculates what you need or" +
                "(3) talk to developers list.");
	}

	public final TravelDisutilityFactory getTravelDisutilityFactory() {
		return this.travelCostCalculatorFactory;
	}

	public final TransitRouterFactory getTransitRouterFactory() {
		if (!(this.tripRouterFactory instanceof DefaultTripRouterFactoryImpl)) {
			throw new RuntimeException("You've set a custom TripRouter. Then please also keep the routing algorithm by yourself.");
		}
		return ((DefaultTripRouterFactoryImpl) this.tripRouterFactory).getTransitRouterFactory();
	}


	// ******** --------- *******
	// The following methods are the outer interface of the Controler. They are used
	// to set up infrastructure from the outside, before calling run().
	// Some of them may also work from the StartupListeners, I haven't sorted that out yet.
	// Contrast to the outermost interface, see below.
	// ******** --------- *******

	/**
	 * It should be possible to add or remove MobsimListeners between iterations, no problem.
	 */
	public final List<MobsimListener> getMobsimListeners() {
		return this.simulationListeners;
	}

	public final void removeControlerListener(final ControlerListener l) {
		// Not sure if necessary or when this is allowed to be called.
		this.controlerListenerManager.removeControlerListener(l);
	}

	public final void setTravelDisutilityFactory(
			final TravelDisutilityFactory travelCostCalculatorFactory) {
		this.travelCostCalculatorFactory = travelCostCalculatorFactory;
	}

	public final void setMobsimFactory(final MobsimFactory mobsimFactory) {
		this.thisMobsimFactory = mobsimFactory;
	}

	public final void setScoringFunctionFactory(
			final ScoringFunctionFactory factory) {
		this.scoringFunctionFactory = factory;
	}

	public final void setTerminationCriterion(TerminationCriterion terminationCriterion) {
		this.terminationCriterion = terminationCriterion;
	}

	public final void setTransitRouterFactory(
			final TransitRouterFactory transitRouterFactory) {
		if (!routerCreated) {
			this.tripRouterFactoryBuilder.setTransitRouterFactory(transitRouterFactory);
			this.componentOfDefaultTripRouterFactoryChanged = true;
		} else {
			throw new RuntimeException("Too late for that.");
		}
	}

	public final void setLeastCostPathCalculatorFactory(final LeastCostPathCalculatorFactory factory) {
		if (!routerCreated) {
			this.tripRouterFactoryBuilder.setLeastCostPathCalculatorFactory(factory);
			this.componentOfDefaultTripRouterFactoryChanged = true;
		} else {
			throw new RuntimeException("Too late for that.");
		}
	}

	public final void setTripRouterFactory(final TripRouterFactory factory) {
		if (this.componentOfDefaultTripRouterFactoryChanged) {
			throw new RuntimeException("Setting a new TripRouterFactory means you cannot set a TransitRouterFactory or a LeastCostPathCalculatorFactory before." +
											" A custom TripRouterFactory should instantiate its own components.");
		}
		if (!routerCreated) {
			tripRouterFactory = factory;
		} else {
			throw new RuntimeException("Too late for that.");
		}
	}

	/**
	 * Sets whether the Controler is allowed to overwrite files in the output
	 * directory or not. <br>
	 * When starting, the Controler can check that the output directory is empty
	 * or does not yet exist, so no files will be overwritten (default setting).
	 * While useful in a productive environment, this security feature may be
	 * interfering in test cases or while debugging. <br>
	 * <strong>Use this setting with caution, as it can result in data
	 * loss!</strong>
	 *
	 * @param overwrite
	 *            whether files and directories should be overwritten (true) or
	 *            not (false)
	 * @deprecated the danger represented by this method seems too great compared to
	 * the small convenience it brings: it will thus be removed soon.
	 * If you are sure that you want to get rid of previous results, call
	 * {@link org.matsim.core.utils.io.IOUtils#deleteDirectory(java.io.File)} on your output directory.
	 * If you want to write stuff to the output directory before lanching the controler,
	 * you might (i) write this stuff to another directory, or (ii) put your
	 * IO code in a StartupListenner, to get it executed after the controler
	 * created the output directory.
	 */
	@Deprecated
	public final void setOverwriteFiles(final boolean overwrite) {
		this.overwriteFiles = overwrite;
	}

	/**
	 * Sets whether graphs showing some analyses should automatically be
	 * generated during the simulation. The generation of graphs usually takes a
	 * small amount of time that does not have any weight in big simulations,
	 * but add a significant overhead in smaller runs or in test cases where the
	 * graphical output is not even requested.
	 *
	 * @param createGraphs
	 *            true if graphs showing analyses' output should be generated.
	 */
	public final void setCreateGraphs(final boolean createGraphs) {
		this.config.controler().setCreateGraphs(createGraphs);
	}

	/**
	 * @param dumpData
	 *            <code>true</code> if at the end of a run, plans, network,
	 *            config etc should be dumped to a file.
	 */
	public final void setDumpDataAtEnd(final boolean dumpData) {
		this.dumpDataAtEnd = dumpData;
	}
	
	
	// ******** --------- *******
	// The following methods are the outermost interface of the Controler. They are used
	// to register infrastructure provided by components, which may or may not be used
	// then, depending on what is in the Config file.
	// This is the point at which a component loader would operate - it would 
	// create this thing, go through the components to see what they provide, and put them
	// here.
	// These methods in principle be factored out to a Controller or OuterController or
	// something, which then creates and configures a Controler based on the config,
	// using the methods above.
	// ******** --------- *******

    public final void setModules(AbstractModule... modules) {
        this.modules = Arrays.asList(modules);
    }

	/**
	 * Register a {@link MobsimFactory} with a given name.
	 *
	 * @param mobsimName
	 * @param mobsimFactory
	 *
	 * @see ControlerConfigGroup#getMobsim()
	 */
	public final void addMobsimFactory(final String mobsimName, final MobsimFactory mobsimFactory) {
		this.mobsimFactoryRegister.register(mobsimName, mobsimFactory);
	}

	public final void addSnapshotWriterFactory(final String snapshotWriterName, final SnapshotWriterFactory snapshotWriterFactory) {
		this.snapshotWriterRegister.register(snapshotWriterName, snapshotWriterFactory);
	}

	public final void addPlanStrategyFactory(final String planStrategyFactoryName, final PlanStrategyFactory planStrategyFactory) {
		this.planStrategyFactoryRegister.register(planStrategyFactoryName, planStrategyFactory);
	}

    public final void addPlanSelectorFactory(final String planSelectorFactoryName, final PlanSelectorFactory<Plan, Person> planSelectorFactory) {
        this.planSelectorFactoryRegister.register(planSelectorFactoryName, planSelectorFactory);
    }


    // ******** --------- *******
	// The following are methods which should not be used at all,
	// or where I am not sure when it is allowed to call them.
	// ******** --------- *******
	/**
	 * @return Returns the {@link org.matsim.core.replanning.StrategyManager}
	 *         used for the replanning of plans.
	 * @deprecated -- try to use controler.addPlanStrategyFactory or controler.addPlanSelectoryFactory.
	 * There are cases when this does not work, which is in particular necessary if you need to re-configure the StrategyManager
	 * during the iterations, <i>and</i> you cannot do this before the iterations start.  In such cases, using this
	 * method may be ok. kai/mzilske, aug'14
	 */
	@Deprecated // see javadoc above
	public final StrategyManager getStrategyManager() {
		return this.strategyManager;
	}

	protected boolean isScenarioLoaded() {
		return scenarioLoaded;
	}

	protected void setScenarioLoaded(boolean scenarioLoaded) {
		this.scenarioLoaded = scenarioLoaded;
	}

}
