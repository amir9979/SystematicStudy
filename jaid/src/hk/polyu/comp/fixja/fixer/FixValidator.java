package hk.polyu.comp.fixja.fixer;

import hk.polyu.comp.fixja.fixaction.FixAction;
import hk.polyu.comp.fixja.fixer.config.FailureHandling;
import hk.polyu.comp.fixja.fixer.config.FixerOutput;
import hk.polyu.comp.fixja.fixer.log.LogLevel;
import hk.polyu.comp.fixja.fixer.log.LoggingService;
import hk.polyu.comp.fixja.java.JavaProject;
import hk.polyu.comp.fixja.monitor.jdi.debugger.TestExecutionResult;
import hk.polyu.comp.fixja.tester.*;
import hk.polyu.comp.fixja.util.FileUtil;
import org.apache.commons.exec.*;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import static hk.polyu.comp.fixja.fixer.log.LoggingService.debug;
import static hk.polyu.comp.fixja.fixer.log.LoggingService.info;
import static hk.polyu.comp.fixja.fixer.log.LoggingService.shouldLogDebug;

/**
 * Created by Max PEI.
 */
public class FixValidator {

    protected JavaProject project;
    protected LogLevel logLevel;
    protected int maxFailingTests;
    protected long timeoutPerTest;
    protected FailureHandling failureHandling;

    protected FixAction currentFixAction;
    protected int fixActionIndex;
    protected List<TestRequest> tests;

    private DefaultExecutor exec;
    private ExecuteWatchdog watchdog;

    public FixValidator(JavaProject project, LogLevel logLevel, int maxFailingTests, long timeoutPerTest, FailureHandling failureHandling) {
        this.project = project;
        this.logLevel = logLevel;
        this.maxFailingTests = maxFailingTests;
        this.timeoutPerTest = timeoutPerTest;
        this.failureHandling = failureHandling;
        this.tests = project.getTestsToRun();
    }

    public void validate(List<FixAction> fixActions, int fixActionIndex){
        this.fixActionIndex = fixActionIndex;
        this.currentFixAction = fixActions.get(fixActionIndex);
        this.currentFixAction.setTestExecutionResults(new LinkedList<>());

        try {
            if(shouldLogDebug()) {
                LoggingService.debug("Validating fix " + currentFixAction.getFixId() + " at index " + fixActionIndex + " using " + tests.size() + " tests.");
            }

            execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ====================================== Implementation details

    private boolean execute() throws Exception {

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

        exec = new DefaultExecutor();
        exec.setWorkingDirectory(project.getRootDir().toFile());

        watchdog = new ExecuteWatchdog(timeoutPerTest * tests.size() + 5000);
        exec.setWatchdog(watchdog);

        CommandLine commandLine = CommandLine.parse(buildCommandLineStr());
        if(shouldLogDebug()) {
            debug(commandLine.toString());
        }

        exec.execute(commandLine, resultHandler);
        resultHandler.waitFor();

        if (watchdog.killedProcess()) {
            // it was killed on purpose by the watchdog
            info(LoggingService.STD_OUTPUT_IS_KILLED);
            info(currentFixAction.toString());
        }

        ResultReader resultReader = new ResultReader();
        List<TestExecutionResult> results = resultReader.getNewResults();
        currentFixAction.getTestExecutionResults().addAll(results);

        return true;
    }

    private String buildCommandLineStr() throws Exception {
        StringBuilder sb = new StringBuilder();

        sb.append(project.getJavaEnvironment().getJvmPath().toString().replace(".exe", "") + " ").append(" ")
                .append(project.commandLineStringForAgents())
                // Enable assertions which are often used in tests to express the oracle.
                .append("-ea ")
                .append("-cp \"" + project.getClasspathForFixingStr() + "\" ");

        // Entry class for test execution
        sb.append(Tester.class.getName()).append(' ');

        // Arguments
        sb.append(TesterConfig.LOG_FILE_OPT).append(" \"").append(getLogPath()).append("\" ")
                .append(TesterConfig.LOG_LEVEL_OPT).append(" ").append(logLevel.name()).append(" ")
                .append(TesterConfig.FAILURE_HANDLING_OPT).append(" ").append(failureHandling.name()).append(" ")
                .append(TesterConfig.TIMEOUT_PER_TEST_OPT).append(" ").append(timeoutPerTest).append(" ")
                .append(TesterConfig.MAX_FAILING_TESTS_OPT).append(" ").append(maxFailingTests).append(" ")
                .append(TesterConfig.SHOULD_QUIT_UPON_MAX_FAILING_TESTS_OPT).append(" ").append(true).append(" ")
                .append(TesterConfig.SHOULD_COMMUNICATE_VIA_STDOUT_OPT).append(" ").append(true).append(" ")
                .append(TesterConfig.BATCH_SIZE_OPT).append(" ").append(BatchFixInstrumentor.BATCH_SIZE).append(" ")
                .append(TesterConfig.ACTIVE_FIX_INDEX_OPT).append(" ").append(this.fixActionIndex).append(" ")
                .append(TesterConfig.ACTIVE_FIX_INDEX_OPT).append(" ").append(this.fixActionIndex).append(" ")
                .append(TesterConfig.BATCH_SIZE_OPT).append(" ").append(BatchFixInstrumentor.BATCH_SIZE).append(" ");

        sb.append(project.commandLineArgumentForTestsToRun());

        return sb.toString();
    }

    private Path getLogPath() {
        return FixerOutput.getFixLogFilePath(currentFixAction.getFixId());
    }

    // =========================================== Internal class

    private class ResultReader {
        private List<TestExecutionResult> results;
        private TestExecutionResult lastResult;
        private int lastFixIndex;

        private List<TestExecutionResult> getNewResults() {
            results = new LinkedList<>();

            Path logFilePath = getLogPath();
            String fileContent = FileUtil.getFileContent(logFilePath, Charset.defaultCharset());
            BufferedReader bufferedReader = new BufferedReader(new StringReader(fileContent));
            bufferedReader.lines().forEach(x -> processLine(x));
            return results;
        }

        protected void processLine(String line) {
            String testClassAndMethod = "";
            int prefixPosition = line.indexOf(Tester.PREFIX_MARKER);
            if(prefixPosition >= 0)
                line = line.substring(prefixPosition, line.length());
            else
                return;

            if (line.startsWith(Tester.TEST_START_PREFIX)) {
                lastFixIndex = -1;
                testClassAndMethod = "";

                String[] parts = line.split(Tester.SEPERATOR_SYMBOL);
                for (String part : parts) {
                    if (part.startsWith(Tester.FIX_INDEX_PREFIX)) {
                        lastFixIndex = Integer.valueOf(part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length()));
                    } else if (part.startsWith(Tester.TEST_REQUEST_PREFIX)) {
                        testClassAndMethod = part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length());
                    }
                }

                if (lastFixIndex == -1 || testClassAndMethod.isEmpty())
                    throw new IllegalStateException();

                lastResult = new TestExecutionResult(testClassAndMethod.substring(0, testClassAndMethod.lastIndexOf('.')),
                        testClassAndMethod.substring(testClassAndMethod.lastIndexOf('.') + 1, testClassAndMethod.length()));
                results.add(lastResult);

            } else if (line.startsWith(Tester.TEST_END_PREFIX)) {
                if(shouldLogDebug()){
                    LoggingService.debug(line);
                }

                testClassAndMethod = "";
                int fixIndex = -1;
                boolean wasSuccessful = false;
                long runTime = 0;

                String[] parts = line.split(Tester.SEPERATOR_SYMBOL);
                for (String part : parts) {
                    try {
                        if (part.startsWith(Tester.FIX_INDEX_PREFIX)) {
                            fixIndex = Integer.valueOf(part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length()));
                        } else if (part.startsWith(Tester.TEST_REQUEST_PREFIX)) {
                            testClassAndMethod = part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length());
                        } else if (part.startsWith(Tester.WAS_SUCCESSFUL_PREFIX)) {
                            wasSuccessful = Boolean.valueOf(part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length()));
                        } else if (part.startsWith(Tester.RUN_TIME_PREFIX)) {
                            runTime = Long.valueOf(part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length()));
                        }
                    }
                    catch(Exception e){

                    }
                }
                if (fixIndex == -1 || fixIndex != lastFixIndex || testClassAndMethod.isEmpty() || !testClassAndMethod.equals(lastResult.getTestClassAndMethod())) {
                    LoggingService.errorAll("Test end message does not match the previous test start message!");
                }

                if(lastResult != null) {
                    lastResult.setWasSuccessful(wasSuccessful);
                    lastResult.setRunTime(runTime);
                }
            }
        }
    }

}

