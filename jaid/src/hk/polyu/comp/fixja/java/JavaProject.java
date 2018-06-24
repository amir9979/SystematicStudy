package hk.polyu.comp.fixja.java;

import hk.polyu.comp.fixja.ast.MethodUtil;
import hk.polyu.comp.fixja.ast.TypeCollector;
import hk.polyu.comp.fixja.fixer.config.CmdOptions;
import hk.polyu.comp.fixja.fixer.config.Config;
import hk.polyu.comp.fixja.fixer.config.FixerOutput;
import hk.polyu.comp.fixja.fixer.log.LoggingService;
import hk.polyu.comp.fixja.monitor.MethodToMonitor;
import hk.polyu.comp.fixja.monitor.jdi.debugger.TestExecutionResult;
import hk.polyu.comp.fixja.test.TestCollector;
import hk.polyu.comp.fixja.tester.TestRequest;
import hk.polyu.comp.fixja.tester.Tester;
import hk.polyu.comp.fixja.tester.TesterConfig;
import hk.polyu.comp.fixja.util.FileUtil;
import org.eclipse.jdt.core.dom.*;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hk.polyu.comp.fixja.fixer.log.LoggingService.shouldLogDebug;
import static hk.polyu.comp.fixja.util.FileUtil.*;
import static hk.polyu.comp.fixja.util.LogUtil.logCompilationErrorForDebug;

/**
 * Created by Max PEI.
 */
public class JavaProject {

    private JavaEnvironment javaEnvironment;
    private String targetJavaVersion;
    private String encoding;

    private Path rootDir;
    private List<Path> libs;
    private List<Path> sourceDirs;
    private Path outputDir;
    private List<Path> testSourceDirs;
    private Path testOutputDir;
    private long timeoutPerTest;

    private List<Path> extraClasspath;

    private List<String> testsToInclude;
    private List<String> testsToExclude;

    private String specificCompilationOptions;
    private String specificExecutionOptions;

    private MethodToMonitor methodToMonitor;

    // Derived
    private Path sourceFileToFix;
    private Path formattedSourceFileToFix;
    private Path sourceFileWithAllFixes;
    private List<TestRequest> testsToRun;
    public static List<String> expToExclude;

    // Cache
    private List<Path> sourceFiles;
    private List<Path> testSourceFiles;
    private List<Path> classpath;
    private String classpathStr;
    private List<Path> classpathForFixing;
    private String classpathForFixingStr;
    private Map<String, AbstractTypeDeclaration> topLevelTypesByFQNames;


    public static final String COMPILATION_UNIT_PROPERTY_PATH = "path";

    // Getters & setters
    public JavaEnvironment  getJavaEnvironment()            { return javaEnvironment; }
    public String           getTargetJavaVersion()          { return targetJavaVersion; }
    public String           getEncoding()                   { return encoding; }

    public Path             getRootDir()                    { return rootDir; }
    public List<Path>       getLibs()                       { return libs; }
    public List<Path>       getSourceDirs()                 { return sourceDirs; }
    public Path             getOutputDir()                  { return outputDir; }
    public List<Path>       getTestSourceDirs()             { return testSourceDirs; }
    public Path             getTestOutputDir()              { return testOutputDir;}

    public List<Path>       getExtraClasspath()             { return extraClasspath; }
    public List<String>     getTestsToInclude()             { return testsToInclude; }
    public List<String>     getTestsToExclude()             { return testsToExclude; }
    public String           getSpecificCompilationOptions() { return specificCompilationOptions; }
    public String           getSpecificExecutionOptions()   { return specificExecutionOptions; }
    public long             getTimeoutPerTest()             { return timeoutPerTest; }

    public List<Path>       getSourceFiles()                { return sourceFiles; }
    public List<Path>       getTestSourceFiles()            { return testSourceFiles; }
    public List<Path>       getClasspath()                  { return classpath; }
    public String           getClasspathStr()               { return classpathStr; }
    public List<Path>       getClasspathForFixing()         { return classpathForFixing; }
    public String           getClasspathForFixingStr()      { return classpathForFixingStr; }
    public Path             getSourceFileToFix()            { return sourceFileToFix; }
    public Path             getFormattedSourceFileToFix()   { return formattedSourceFileToFix; }
    public Path             getSourceFileWithAllFixes()     { return sourceFileWithAllFixes; }
    public Map<String, AbstractTypeDeclaration>
                            getTopLevelTypesByFQNames()     { return topLevelTypesByFQNames; }

    public MethodToMonitor  getMethodToMonitor()            { return methodToMonitor; }
    public void             setMethodToMonitor(MethodToMonitor methodToMonitor)
                                                            { this.methodToMonitor = methodToMonitor; }

    public void             setFormattedSourceFileToFix(Path formattedSourceFileToFix)
                                                            { this.formattedSourceFileToFix = formattedSourceFileToFix; }
    public void             setSourceFileToFix(Path sourceFileToFix)
                                                            { this.sourceFileToFix = sourceFileToFix; }
    public void             setSourceFileWithAllFixes(Path path)
                                                            { this.sourceFileWithAllFixes = path; }


    /**
     * Constructor.
     *
     * @param javaEnvironment
     * @param properties
     */
    public JavaProject(JavaEnvironment javaEnvironment, Properties properties) {
        this.javaEnvironment = javaEnvironment;

        rootDir = Paths.get(getProperty(properties, CmdOptions.PROJECT_ROOT_DIR_OPT));
        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir))
            throw new IllegalStateException("Error: " + rootDir + " does not exist or is not a directory.");
        FixerOutput.init(rootDir);

        sourceDirs = loadPaths(properties, CmdOptions.PROJECT_SOURCE_DIR_OPT);
        outputDir = loadPaths(properties, CmdOptions.PROJECT_OUTPUT_DIR_OPT).get(0);
        ensureEmptyDir(outputDir);
        libs = loadPaths(properties, CmdOptions.PROJECT_LIB_OPT);
        extraClasspath = loadPaths(properties, CmdOptions.PROJECT_EXTRA_CLASSPATH_OPT);
        testSourceDirs = loadPaths(properties, CmdOptions.PROJECT_TEST_SOURCE_DIR_OPT);
        testOutputDir = loadPaths(properties, CmdOptions.PROJECT_TEST_OUTPUT_DIR_OPT).get(0);
        ensureEmptyDir(testOutputDir);
        testsToInclude = loadStrings(properties, CmdOptions.PROJECT_TESTS_TO_INCLUDE_OPT);
        testsToExclude = loadStrings(properties, CmdOptions.PROJECT_TESTS_TO_EXCLUDE_OPT);
        timeoutPerTest = Long.valueOf(getProperty(properties, CmdOptions.TIMEOUT_PER_TEST_OPT, "2500"));

        specificCompilationOptions = getProperty(properties, CmdOptions.PROJECT_COMPILATION_COMMAND_OPT, "");
        specificExecutionOptions = getProperty(properties, CmdOptions.PROJECT_EXECUTION_COMMAND_OPT, "");
        encoding = getProperty(properties, CmdOptions.ENCODING_OPT, "");
        targetJavaVersion = getProperty(properties, CmdOptions.TARGET_JAVA_VERSION_OPT, "").trim();

        expToExclude = loadStrings(properties, CmdOptions.EXCLUDE_EXP_OPT);

        // init
        collectSourceFiles();
        collectTestSourceFiles();
        collectClasspath();
        collectClasspathForFixing();
    }

    public void registerMethodToMonitor(Config config){
        String[] files = new String[]{getFormattedSourceFileToFix().toString()};
        topLevelTypesByFQNames = loadASTFromFiles(files);

        // Register method to fix.
        String fqClassName = config.getFaultyClassName();
        String methodSignature = config.getFaultyMethodSignature();

        AbstractTypeDeclaration typeDeclaration = topLevelTypesByFQNames.get(fqClassName);
        MethodDeclaration methodDeclaration = MethodUtil.getMethodDeclarationBySignature(typeDeclaration, methodSignature);
        methodToMonitor = new MethodToMonitor(fqClassName, config.getMethodToFix(), methodDeclaration);
    }

    public void initMethodToMonitor(){
        methodToMonitor.initMethodDeclarationInfoCenter();
    }

    public void compile() {
        //Log Source and test code compilation error
        ProjectCompiler projectCompiler = new ProjectCompiler(this);

        DiagnosticCollector<JavaFileObject> diagnosticCollector = projectCompiler.compileOriginalSource();
        if(shouldLogDebug()) logCompilationErrorForDebug(diagnosticCollector);

        diagnosticCollector = projectCompiler.compileTestSource();
        if(shouldLogDebug()) logCompilationErrorForDebug(diagnosticCollector);

        projectCompiler.compileFormattedCTF();
        MutableDiagnosticCollector<JavaFileObject> diagnostics = projectCompiler.getSharedDiagnostics();
        boolean hasError = false;
        for(Diagnostic diagnostic: diagnostics.getDiagnostics()){
            switch (diagnostic.getKind()){
                case ERROR:
                    LoggingService.errorAll(diagnostic.toString());
                    hasError = true;
                    break;
                case WARNING:
                case MANDATORY_WARNING:
                    LoggingService.warnAll(diagnostic.toString());
                    break;
                default:
                    break;
            }
        }

        if(hasError)
            throw new IllegalStateException();
    }


    public void registerIntermediateSourceFilePaths(CompilationUnit unit) {
        Path sourceFileToFix = Paths.get((String) unit.getProperty(JavaProject.COMPILATION_UNIT_PROPERTY_PATH));
        this.setSourceFileToFix(sourceFileToFix);

        String fileName = Paths.get((String)unit.getProperty(JavaProject.COMPILATION_UNIT_PROPERTY_PATH)).getFileName().toString();

        Path formattedSourceFileToFix = FileUtil.getPathFromNewRoot(unit, fileName, FixerOutput.getFormattedSourceDirPath());
        this.setFormattedSourceFileToFix(formattedSourceFileToFix);

        Path sourceFileWithAllFixesPath = FileUtil.getPathFromNewRoot(unit, fileName, FixerOutput.getTempBatchFixDirPath());
        this.setSourceFileWithAllFixes(sourceFileWithAllFixesPath);
    }

    // Lazy-get all tests of the project.
    public List<TestRequest> getTestsToRun() {
        if (testsToRun == null) {
            TestCollector collector = new TestCollector();
            testsToRun = collector.getAllTestsToRun(this);
        }
        return testsToRun;
    }

    public void retainOnlyRelevantTests(Collection<TestExecutionResult> relevantTestResults) {
        Map<String, TestExecutionResult> testMethods = relevantTestResults.stream().collect(Collectors.toMap(TestExecutionResult::getTestClassAndMethod, Function.identity()));
        List<TestRequest> testRequests = getTestsToRun();
        int passingCount = 0, totalCount = testRequests.size();
        for (int i = testRequests.size() - 1; i >= 0; i--) {
            TestRequest request = testRequests.get(i);
            if (!testMethods.containsKey(request.getTestClassAndMethod())) {
                testRequests.remove(i);
            } else {
                // Move passing tests to the end, so that during validation, failing tests are run first
                TestExecutionResult testResult = testMethods.get(request.getTestClassAndMethod());
                if (testResult.wasSuccessful()) {
                    passingCount++;
                    TestRequest testRequest = testRequests.get(i);
                    testRequests.remove(i);
                    testRequests.add(testRequest);
                }
            }
        }
        LoggingService.infoAll("TotalTest::" + totalCount + ";validTestSize::" + testRequests.size() + ";passingTimes::" + passingCount);
    }

    /**
     * Command line argument to be used for specifying tests-to-run.
     *
     * @return
     */
    public String commandLineArgumentForTestsToRun() {
        List<TestRequest> tests = getTestsToRun();
        return commandLineArgumentForTestsToRun(tests);
    }

    public String commandLineArgumentForTestsToRun(List<TestRequest> tests) {
        StringBuilder testList = new StringBuilder();
        tests.stream().filter(x -> x != null).forEach(x -> testList.append(x).append(File.pathSeparator));
        Path testListPath = FixerOutput.getTestCasesListFilePath();
        FileUtil.writeFile(FixerOutput.getTestCasesListFilePath(), testList.toString());

        StringBuilder sb = new StringBuilder();
        sb.append(TesterConfig.TESTS_LIST_FILE_OPT).append(" \"");
        sb.append(testListPath).append("\" ");

        return sb.toString();
    }

    /**
     * Command line argument to be used for specifying necessary agents in compiling/running the project.
     *
     * @return
     */
    public String commandLineStringForAgents() {
        StringBuilder sb = new StringBuilder();
        Path cofojaJarPath = this.getCofojaJar();
        if (cofojaJarPath != null) {
            sb.append("-javaagent:").append(cofojaJarPath.toString()).append(" ");
        }
        return sb.toString();
    }

    /**
     * Java parser that is aware of the context of this project.
     *
     * @return
     */
    public ASTParser getProjectSpecificParser() {
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);

        List<String> sourceList = getSourceDirs().stream().map(s -> s.toString()).collect(Collectors.toList());
        String[] sources = new String[sourceList.size()];
        sourceList.toArray(sources);

        // The length of 'encodings' has to match that of 'sources'.
        String[] encodings = new String[sourceList.size()];
        Arrays.fill(encodings, StandardCharsets.UTF_8.name());

        List<String> classpathList = getClasspath().stream().map(s -> s.toString()).collect(Collectors.toList());
        String[] classpaths = new String[classpathList.size()];
        classpathList.toArray(classpaths);

        parser.setEnvironment(classpaths, sources, encodings, true);

        return parser;
    }

    // Collect Java source files of the project into 'sourceFiles'.
    private void collectSourceFiles() {
        sourceFiles = new LinkedList<>();
        sourceDirs.stream().forEach(x -> sourceFiles.addAll(FileUtil.javaFiles(x, true,false)));
    }

    // Collect Java source files of the project into 'sourceFiles'.
    private void collectTestSourceFiles() {
        testSourceFiles = new LinkedList<>();
        testSourceDirs.stream().forEach(x -> testSourceFiles.addAll(FileUtil.javaFiles(x, true,false)));
    }

    // Collect class path for fixing into 'classpathForFixing'.
    // Compared with 'classpath', 'classpathForFixing' contains also the paths to 'Tester' and the modified class-to-fix.
    private void collectClasspathForFixing() {
        classpathForFixing = new LinkedList<>();
        classpathForFixing.add(FileUtil.getClasspath(Tester.class));
        classpathForFixing.add(FixerOutput.getTempDestDirPath());
        classpathForFixing.addAll(getClasspath());

        classpathForFixingStr = String.join(File.pathSeparator, classpathForFixing.stream().map(x -> x.toString()).collect(Collectors.toList()));
    }

    // Collect class path of the project into 'classpath'.
    private void collectClasspath() {
        classpath = new ArrayList<>();
        classpath.addAll(getExtraClasspath());
        classpath.add(outputDir);
        classpath.add(getTestOutputDir());
        classpath.addAll(getLibs());

        classpathStr = String.join(File.pathSeparator, classpath.stream().map(x -> x.toString()).collect(Collectors.toList()));
    }

    // Path to the optional Cofoja library.
    private Path getCofojaJar() {
        List<Path> paths = getClasspath();
        paths = paths.stream().filter(x -> x.toString().toLowerCase().contains("cofoja")).collect(Collectors.toList());
        if (!paths.isEmpty()) {
            return paths.get(0);
        } else {
            return null;
        }
    }

//    // Load ASTs from 'files' into 'topLevelTypesByFQNames', overwrite existing entries if 'shouldOverwrite'.
//    private void loadASTFromFiles(String[] files,
//                                  boolean shouldOverwrite) {
//        topLevelTypesByFQNames = new HashMap<>();
//        final Map<String, AbstractTypeDeclaration> typeDeclarationMap = getTopLevelTypesByFQNames();
//        String[] encodes = new String[files.length];
//        Arrays.fill(encodes, StandardCharsets.UTF_8.name());
//
//        final TypeCollector collector = new TypeCollector(typeDeclarationMap, shouldOverwrite);
//
//        ASTParser astParser = getProjectSpecificParser();
//        astParser.createASTs(files, encodes, new String[]{}, collector.getASTRequestor(), null);
//    }

    // Load ASTs from 'files' into 'topLevelTypesByFQNames', overwrite existing entries if 'shouldOverwrite'.
    public Map<String, AbstractTypeDeclaration> loadASTFromFiles(String[] files) {
        String[] encodes = new String[files.length];
        Arrays.fill(encodes, StandardCharsets.UTF_8.name());

        final TypeCollector collector = new TypeCollector();

        ASTParser astParser = getProjectSpecificParser();
        astParser.createASTs(files, encodes, new String[]{}, collector.getASTRequestor(), null);

        return collector.getTypes();
    }

    private List<Path> loadPaths(Properties properties, String property) {
        String propertyVal = getProperty(properties, property).trim();
        if (propertyVal.isEmpty())
            return new ArrayList<>();
        else {
            List<String> stringList = Arrays.asList(propertyVal.split(File.pathSeparator));
            return stringList.stream().map(x -> x.trim()).filter(x -> !x.isEmpty()).map(x -> getRootDir().resolve(x)).collect(Collectors.toList());
        }
    }

    private List<String> loadStrings(Properties properties, String property) {
        String propertyVal = getProperty(properties, property, "").trim();
        if (propertyVal.isEmpty())
            return new ArrayList<>();
        else {
            List<String> result = Arrays.asList(propertyVal.split(File.pathSeparator));
            return result.stream().map(x -> x.trim()).filter(t -> !t.isEmpty()).collect(Collectors.toList());
        }
    }

    private String getProperty(Properties properties, String name) {
        if (properties.containsKey(name))
            return properties.getProperty(name);
        else
            throw new IllegalStateException("Error: Property not specified in configuration (" + name + ").");
    }

    private String getProperty(Properties properties, String name, String defaultVal) {
        if (properties.containsKey(name))
            return properties.getProperty(name);
        else
            return defaultVal;
    }

}
