package fr.inria.main.evolution;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.ParseException;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import fr.inria.astor.approaches.cardumen.CardumenApproach;
import fr.inria.astor.approaches.deeprepair.DeepRepairEngine;
import fr.inria.astor.approaches.jgenprog.JGenProg;
import fr.inria.astor.approaches.jkali.JKaliEngine;
import fr.inria.astor.approaches.jmutrepair.MutationalExhaustiveRepair;
import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.faultlocalization.entity.SuspiciousCode;
import fr.inria.astor.core.ingredientbased.ExhaustiveIngredientBasedEngine;
import fr.inria.astor.core.manipulation.MutationSupporter;
import fr.inria.astor.core.setup.ConfigurationProperties;
import fr.inria.astor.core.setup.FinderTestCases;
import fr.inria.astor.core.setup.ProjectRepairFacade;
import fr.inria.astor.core.solutionsearch.AstorCoreEngine;
import fr.inria.astor.core.solutionsearch.population.ProgramVariantFactory;
import fr.inria.main.AbstractMain;
import fr.inria.main.ExecutionMode;

/**
 * Astor main
 * 
 * @author Matias Martinez, matias.martinez@inria.fr
 *
 */
public class AstorMain extends AbstractMain {

	protected Logger log = Logger.getLogger(AstorMain.class.getName());

	AstorCoreEngine astorCore = null;

	public void initProject(String location, String projectName, String dependencies, String packageToInstrument,
			double thfl, String failing) throws Exception {

		List<String> failingList = (failing != null) ? Arrays.asList(failing.split(File.pathSeparator))
				: new ArrayList<>();
		String method = this.getClass().getSimpleName();

		projectFacade = getProjectConfiguration(location, projectName, method, failingList, dependencies, true);

		projectFacade.getProperties().setExperimentName(this.getClass().getSimpleName());

		projectFacade.setupWorkingDirectories(ProgramVariant.DEFAULT_ORIGINAL_VARIANT);

		if (ConfigurationProperties.getPropertyBool("autocompile")) {
			compileProject(projectFacade.getProperties());
		}

	}

	/**
	 * It creates a repair engine according to an execution mode.
	 * 
	 * 
	 * @param removeMode
	 * @return
	 * @throws Exception
	 */

	public AstorCoreEngine createEngine(ExecutionMode mode) throws Exception {
		astorCore = null;
		MutationSupporter mutSupporter = new MutationSupporter();

		if (ExecutionMode.DeepRepair.equals(mode)) {
			astorCore = new DeepRepairEngine(mutSupporter, projectFacade);

		} else if (ExecutionMode.CARDUMEN.equals(mode)) {
			astorCore = new CardumenApproach(mutSupporter, projectFacade);

		} else if (ExecutionMode.jKali.equals(mode)) {
			astorCore = new JKaliEngine(mutSupporter, projectFacade);

		} else if (ExecutionMode.jGenProg.equals(mode)) {
			astorCore = new JGenProg(mutSupporter, projectFacade);

		} else if (ExecutionMode.MutRepair.equals(mode)) {
			astorCore = new MutationalExhaustiveRepair(mutSupporter, projectFacade);

		} else if (ExecutionMode.EXASTOR.equals(mode)) {
			astorCore = new ExhaustiveIngredientBasedEngine(mutSupporter, projectFacade);

		} else {
			// If the execution mode is any of the predefined, Astor
			// interpretes as
			// a custom engine, where the value corresponds to the class name of
			// the engine class
			String customengine = ConfigurationProperties.getProperty("customengine");
			astorCore = createEngineFromArgument(customengine, mutSupporter, projectFacade);

		}

		// Loading extension Points
		astorCore.loadExtensionPoints();

		astorCore.setVariantFactory(new ProgramVariantFactory(astorCore.getTargetElementProcessors()));
		// Find test cases to use in validation
		List<String> tr = FinderTestCases.findTestCasesForRegression(
				projectFacade.getOutDirWithPrefix(ProgramVariant.DEFAULT_ORIGINAL_VARIANT), projectFacade);
		projectFacade.getProperties().setRegressionCases(tr);

		// Initialize Population

		if (ConfigurationProperties.getPropertyBool("skipfaultlocalization")) {
			// Read suspicious code from the file that contains the suspicious code.
			List<SuspiciousCode> suspicious = astorCore.readSuspicious();
			astorCore.initPopulation(suspicious);
		} else {
			List<SuspiciousCode> suspicious = astorCore.calculateSuspicious();
			astorCore.initPopulation(suspicious);
		}

		return astorCore;

	}
	
	/**
	 * We create an instance of the Engine which name is passed as argument.
	 * 
	 * @param customEngine
	 * @param mutSupporter
	 * @param projectFacade
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private AstorCoreEngine createEngineFromArgument(String customEngine, MutationSupporter mutSupporter,
			ProjectRepairFacade projectFacade) throws Exception {
		Object object = null;
		try {
			Class classDefinition = Class.forName(customEngine);
			object = classDefinition.getConstructor(mutSupporter.getClass(), projectFacade.getClass())
					.newInstance(mutSupporter, projectFacade);
		} catch (Exception e) {
			log.error("Loading custom engine: " + customEngine + " --" + e);
			throw new Exception("Error Loading Engine: " + e);
		}
		if (object instanceof AstorCoreEngine)
			return (AstorCoreEngine) object;
		else
			throw new Exception(
					"The strategy " + customEngine + " does not extend from " + AstorCoreEngine.class.getName());

	}

	@Override
	public void run(String location, String projectName, String dependencies, String packageToInstrument, double thfl,
			String failing) throws Exception {

		long startT = System.currentTimeMillis();
		initProject(location, projectName, dependencies, packageToInstrument, thfl, failing);

		String mode = ConfigurationProperties.getProperty("mode").toLowerCase();
		String customEngine = ConfigurationProperties.getProperty("customengine");

		if (customEngine != null && !customEngine.isEmpty())
			astorCore = createEngine(ExecutionMode.custom);
		else if ("deeprepair".equals(mode))
			astorCore = createEngine(ExecutionMode.DeepRepair);
		else if ("cardumen".equals(mode))
			astorCore = createEngine(ExecutionMode.CARDUMEN);
		else if ("statement".equals(mode) || "jgenprog".equals(mode))
			astorCore = createEngine(ExecutionMode.jGenProg);
		else if ("statement-remove".equals(mode) || "jkali".equals(mode))
			astorCore = createEngine(ExecutionMode.jKali);
		else if ("mutation".equals(mode) || "jmutrepair".equals(mode))
			astorCore = createEngine(ExecutionMode.MutRepair);
		else if ("exhaustive".equals(mode) || "exastor".equals(mode))
			astorCore = createEngine(ExecutionMode.EXASTOR);

		else {
			System.err.println("Unknown mode of execution: '" + mode + "',  modes are: "
					+ Arrays.toString(ExecutionMode.values()));
			return;
		}

		ConfigurationProperties.print();

		astorCore.startEvolution();

		astorCore.atEnd();

		long endT = System.currentTimeMillis();
		log.info("Time Total(s): " + (endT - startT) / 1000d);
	}

	/**
	 * @param args
	 * @throws Exception
	 * @throws ParseException
	 */
	public static void main(String[] args) throws Exception {
		/*   java -cp $(cat /tmp/astor-classpath.txt):target/classes fr.inria.main.evolution.AstorMain -mode jGenProg -srcjavafolder /src/java/ -srctestfolder /src/test/ -binjavafolder /target/classes/ -bintestfolder /target/test-classes/ -location /Users/kui.liu/eclipse-workspace/astor/examples/Math-issue-280/ -dependencies examples/Math-issue-280/lib
//              *  java -cp 
//              *  $(cat /tmp/astor-classpath.txt):target/classes 
//              *  fr.inria.main.evolution.AstorMain 
//              *  -mode jGenProg 
//              *  -srcjavafolder examples/Math-issue-280/src/java/ 
//              *  -srctestfolder examples/Math-issue-280/src/test/  
//              *  -binjavafolder examples/Math-issue-280/target/classes/ 
//              *  -bintestfolder examples/Math-issue-280/target/test-classes/ 
//              *  -location /home/user/astor/examples/Math-issue-280/ 
//              *  -dependencies examples/Math-issue-280/lib
//              */
//             args = new String[]{
//                             "-mode", "jgenprog", 
//                             "-srcjavafolder", "/Users/kui.liu/eclipse-workspace/astor/examples/chart_1/source/",//"examples/Math-issue-280/src/java/",
//                             "-srctestfolder", "/Users/kui.liu/eclipse-workspace/astor/examples/chart_1/tests/",//"examples/Math-issue-280/src/test/", 
//                             "-binjavafolder", "/Users/kui.liu/eclipse-workspace/astor/examples/chart_1/build/",//"examples/Math-issue-280/target/classes/",
//                             "-bintestfolder", "/Users/kui.liu/eclipse-workspace/astor/examples/chart_1/build-tests/",//"examples/Math-issue-280/target/test-classes/", 
//                             "-location", "/Users/kui.liu/eclipse-workspace/astor/",
//                             "-dependencies", "/Users/kui.liu/eclipse-workspace/astor/examples/chart_1/lib/",
//                             "-suspiciousCodeFile", ""};//"examples/Math-issue-280/lib"};
		String bugProject = "Math_53";
		List<String> paths = getSrcPath(bugProject);
		args = new String[]{
                "-mode", "jgenprog", 
                "-srcjavafolder", "/Users/kui.liu/Public/Defects4JData/" + bugProject + paths.get(2),//"examples/Math-issue-280/src/java/",
                "-srctestfolder", "/Users/kui.liu/Public/Defects4JData/" + bugProject + paths.get(3),//"examples/Math-issue-280/src/test/", 
                "-binjavafolder", "/Users/kui.liu/Public/Defects4JData/" + bugProject + paths.get(0),//"examples/Math-issue-280/target/classes/",
                "-bintestfolder", "/Users/kui.liu/Public/Defects4JData/" + bugProject + paths.get(1),//"examples/Math-issue-280/target/test-classes/", 
                "-location", "/Users/kui.liu/eclipse-workspace/astor/",
                "-dependencies", "/Users/kui.liu/Public/Defects4JData/" + bugProject + "/lib/"
//                ,
//                "-suspiciousCodeFile", "/Users/kui.liu/eclipse-workspace/SuspiciousCodeFiles/" + bugProject + ".susp"
                };

		AstorMain m = new AstorMain();
		m.execute(args);
	}
	
	public static ArrayList<String> getSrcPath(String bugProject) {
		ArrayList<String> path = new ArrayList<String>();
		String[] words = bugProject.split("_");
		String projectName = words[0];
		int bugId = Integer.parseInt(words[1]);
		if (projectName.equals("Math")) {
			if (bugId < 85) {
				path.add("/target/classes/");
				path.add("/target/test-classes/");
				path.add("/src/main/java/");
				path.add("/src/test/java/");
			} else {
				path.add("/target/classes/");
				path.add("/target/test-classes/");
				path.add("/src/java/");
				path.add("/src/test/");
			}
		} else if (projectName.equals("Time")) {
			if (bugId < 12) {
				path.add("/target/classes/");
				path.add("/target/test-classes/");
				path.add("/src/main/java/");
				path.add("/src/test/java/");
			} else {
				path.add("/build/classes/");
				path.add("/build/tests/");
				path.add("/src/main/java/");
				path.add("/src/test/java/");
			}
		} else if (projectName.equals("Lang")) {
			if (bugId <= 20) {
				path.add("/target/classes/");
				path.add("/target/tests/");
				path.add("/src/main/java/");
				path.add("/src/test/java/");
			} else if (bugId >= 21 && bugId <= 35) {
				path.add("/target/classes/");
				path.add("/target/test-classes/");
				path.add("/src/main/java/");
				path.add("/src/test/java/");
			} else if (bugId >= 36 && bugId <= 41) {
				path.add("/target/classes/");
				path.add("/target/test-classes/");
				path.add("/src/java/");
				path.add("/src/test/");
			} else if (bugId >= 42 && bugId <= 65) {
				path.add("/target/classes/");
				path.add("/target/tests/");
				path.add("/src/java/");
				path.add("/src/test/");
			}
		} else if (projectName.equals("Chart")) {
			path.add("/build/");
			path.add("/build-tests/");
			path.add("/source/");
			path.add("/tests/");

		} else if (projectName.equals("Closure")) {
			path.add("/build/classes/");
			path.add("/build/test/");
			path.add("/src/");
			path.add("/test/");
		} else if (projectName.equals("Mockito")) {
			if (bugId <= 11 || (bugId >= 18 && bugId <= 21)) {
				path.add("/build/classes/main/");
				path.add("/build/classes/test/");
				path.add("/src/");
				path.add("/test/");
			} else {
				path.add("/target/classes/");
				path.add("/target/test-classes/");
				path.add("/src/");
				path.add("/test/");
			}
		}
		return path;
	}

	public void execute(String[] args) throws Exception {
		boolean correct = processArguments(args);
		if (!correct) {
			System.err.println("Problems with commands arguments");
			return;
		}
		if (isExample(args)) {
			executeExample(args);
			return;
		}

		String dependencies = ConfigurationProperties.getProperty("dependenciespath");
		String failing = ConfigurationProperties.getProperty("failing");
		String location = ConfigurationProperties.getProperty("location");
		String packageToInstrument = ConfigurationProperties.getProperty("packageToInstrument");
		double thfl = ConfigurationProperties.getPropertyDouble("flthreshold");
		String projectName = ConfigurationProperties.getProperty("projectIdentifier");

		setupLogging();

		run(location, projectName, dependencies, packageToInstrument, thfl, failing);

	}

	public AstorCoreEngine getEngine() {
		return astorCore;
	}

	public void setupLogging() throws IOException {

		String patternLayout = "";
		if (ConfigurationProperties.getPropertyBool("disablelog")) {
			patternLayout = "%m%n";
		} else {
			patternLayout = ConfigurationProperties.getProperty("logpatternlayout");
		}

		Logger.getRootLogger().getLoggerRepository().resetConfiguration();
		ConsoleAppender console = new ConsoleAppender();
		console.setLayout(new PatternLayout(patternLayout));
		console.activateOptions();
		Logger.getRootLogger().addAppender(console);

		String loglevelSelected = ConfigurationProperties.properties.getProperty("loglevel");
		if (loglevelSelected != null)
			LogManager.getRootLogger().setLevel(Level.toLevel(loglevelSelected));

		if (ConfigurationProperties.hasProperty("logfilepath")) {
			FileAppender fa = new FileAppender();
			String filePath = ConfigurationProperties.getProperty("logfilepath");
			File fileLog = new File(filePath);
			if (!fileLog.exists()) {
				fileLog.getParentFile().mkdirs();
				fileLog.createNewFile();
			}

			fa.setName("FileLogger");
			fa.setFile(fileLog.getAbsolutePath());
			fa.setLayout(new PatternLayout(patternLayout));
			fa.setThreshold(LogManager.getRootLogger().getLevel());
			fa.setAppend(true);
			fa.activateOptions();
			Logger.getRootLogger().addAppender(fa);
			this.log.info("Log file at: " + filePath);
		}
	}

}
