package hk.polyu.comp.fixja.fixer;

import hk.polyu.comp.fixja.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.fixja.fixaction.*;
import hk.polyu.comp.fixja.fixer.config.Config;
import hk.polyu.comp.fixja.fixer.config.FailureHandling;
import hk.polyu.comp.fixja.fixer.config.FixerOutput;
import hk.polyu.comp.fixja.fixer.log.LoggingService;
import hk.polyu.comp.fixja.java.ClassToFixPreprocessor;
import hk.polyu.comp.fixja.java.JavaProject;
import hk.polyu.comp.fixja.java.MutableDiagnosticCollector;
import hk.polyu.comp.fixja.java.ProjectCompiler;
import hk.polyu.comp.fixja.monitor.ExpressionToMonitor;
import hk.polyu.comp.fixja.monitor.LineLocation;
import hk.polyu.comp.fixja.monitor.MethodToMonitor;
import hk.polyu.comp.fixja.monitor.jdi.debugger.*;
import hk.polyu.comp.fixja.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.fixja.monitor.snapshot.StateSnapshotExpression;
import hk.polyu.comp.fixja.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.fixja.monitor.state.ProgramState;
import hk.polyu.comp.fixja.util.LogUtil;
import org.eclipse.jdt.core.dom.*;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.*;
import java.util.stream.Collectors;

import static hk.polyu.comp.fixja.fixer.config.FixerOutput.LogFile.*;
import static hk.polyu.comp.fixja.fixer.log.LoggingService.shouldLogDebug;
import static hk.polyu.comp.fixja.util.LogUtil.*;

public class Fixer {

    public void execute() throws Exception {

        Config config = Session.getSession().getConfig();
        JavaProject javaProject = config.getJavaProject();

        ClassToFixPreprocessor preprocessor = new ClassToFixPreprocessor(javaProject, config);
        preprocessor.preprocess();

        javaProject.registerMethodToMonitor(config);
        javaProject.initMethodToMonitor();
        javaProject.compile();

        LocationSelector locationSelector = selectLocationsToMonitor(javaProject);
        ProgramStateMonitor programStateMonitor = monitorProgramStates(javaProject, locationSelector);
        List<TestExecutionResult> testResults = programStateMonitor.getResults();

        List<StateSnapshot> stateSnapshots = ConstructSnapshot(javaProject, testResults);
        stateSnapshots = stateSnapshots.subList(0, Math.min(stateSnapshots.size(), MAXIMUM_STATE_SNAPSHOTS));
        LoggingService.infoAll("Valid snapshots ::"+stateSnapshots.size());

        if(shouldLogDebug()) {
            int rank = 0;
            for (StateSnapshot stateSnapshot : stateSnapshots) {
                LoggingService.debugFileOnly(rank++ + ":: " + stateSnapshot.toString(), SUSPICIOUS_STATE_SNAPSHOT);
            }
        }

        Map<LineLocation, Set<FixAction>> fixActions = GenerateFixActions(javaProject, stateSnapshots);
        List<FixAction> allFixActions = new LinkedList<>();
        for(LineLocation location: fixActions.keySet()){
            allFixActions.addAll(fixActions.get(location));
        }
        removeAllIllformedFixActions(javaProject, allFixActions);

        // Sort all fix actions for validation.
        allFixActions.sort(new Comparator<FixAction>() {
            @Override
            public int compare(FixAction o1, FixAction o2) {
                return Double.compare(o2.getStateSnapshot().getSuspiciousness(), o1.getStateSnapshot().getSuspiciousness());
            }
        });

        List<FixAction> validFixes = validateFixActions(javaProject, allFixActions);

    }

    private LocationSelector selectLocationsToMonitor(JavaProject project){
        LocationSelector locationSelector = new LocationSelector(project,
                Session.getSession().getConfig().getLogLevel(),
                project.getTimeoutPerTest() * 40, FailureHandling.CONTINUE);
        locationSelector.launch();
        locationSelector.pruneIrrelevantLocationsAndTests();
        project.retainOnlyRelevantTests(locationSelector.getRelevantTestResults());
        return locationSelector;
    }

    private ProgramStateMonitor monitorProgramStates(JavaProject project, LocationSelector locationSelector) throws Exception {
        MethodDeclarationInfoCenter infoCenter = project.getMethodToMonitor().getMethodDeclarationInfoCenter();
        Set<LineLocation> relevantLocations = locationSelector.getRelevantLocations();
        List<TestExecutionResult> testResults = locationSelector.getRelevantTestResults();
        infoCenter.pruneIrrelevantLocation(relevantLocations);

        boolean hasSideEffectExpressions = true;
        while(hasSideEffectExpressions) {
            ExpressionSelector selector = new ExpressionSelector(
                    project, Session.getSession().getConfig().getLogLevel(),
                    project.getTimeoutPerTest() * 40 * relevantLocations.size(),
                    FailureHandling.CONTINUE, infoCenter.getAllLocationStatementMap().keySet(), testResults);
            selector.doSelection();
            hasSideEffectExpressions = selector.hasFoundExpressionWithSideEffect();
        }

        infoCenter.registerSideEffectFreeExpressionsToLocation();
        infoCenter.removeUnsuitableExpressionsAtMethodExit();
        infoCenter.buildStateSnapshotExpressions();
        infoCenter.buildStateSnapshotsWithinMethod();

        Set<ExpressionToMonitor> sideEffectFreeExpressions = project.getMethodToMonitor().getMethodDeclarationInfoCenter().getExpressionsToMonitorWithinMethod()
                .stream().filter(ExpressionToMonitor::isSideEffectFree).collect(Collectors.toSet());

        if(shouldLogDebug()) {
            LogUtil.logLocationForDebug(relevantLocations);
            LoggingService.infoAll("============ AllNonSideEffectETM size::" + sideEffectFreeExpressions.size());
            sideEffectFreeExpressions.stream().forEach(f -> LoggingService.debugFileOnly(f.toString(), MONITORED_EXPS));
        }

        //ValidExps for each location will be stored in the corresponding Location.
        ProgramStateMonitor programStateMonitor = new ProgramStateMonitor(
                project,
                Session.getSession().getConfig().getLogLevel(),
                project.getTimeoutPerTest() * 40 * relevantLocations.size() * sideEffectFreeExpressions.size(),
                FailureHandling.CONTINUE, infoCenter.getRelevantLocationStatementMap().keySet());
        programStateMonitor.launch();
        LogUtil.logSessionCosting("Finish evaluate program states.");
        return programStateMonitor;
    }



    private List<StateSnapshot> ConstructSnapshot(JavaProject project, Collection<TestExecutionResult> testResults) throws Exception {
        MethodDeclarationInfoCenter infoCenter = project.getMethodToMonitor().getMethodDeclarationInfoCenter();

        List<TestExecutionResult> failingTestResults = testResults.stream().filter(x -> !x.wasSuccessful() && !x.getObservedStates().isEmpty()).collect(Collectors.toList());
        computeLocationDistanceToFailure(failingTestResults.get(0), infoCenter);
//        computeStateSnapshotSuspiciousness(testResults, infoCenter);
        computeStateSnapshotSuspiciousnessWithoutDeriveStates(testResults, infoCenter);
        List<StateSnapshot> stateSnapshots = infoCenter.orderedStateSnapshots();
        return stateSnapshots;
    }

    private Map<LineLocation, Set<FixAction>> GenerateFixActions(JavaProject javaProject, List<StateSnapshot> snapshots) throws Exception {
        // fixme: originally, enableBasicStrategies are not used in comprehensive mode. Is that really what we want?
        SnippetBuilder snippetBuilder = new SnippetBuilder();
        snippetBuilder.enableBasicStrategies();
        snippetBuilder.enableComprehensiveStrategies(Session.getSession().getConfig().getSnippetConstructionStrategy() != Config.SnippetConstructionStrategy.BASIC);

        for(StateSnapshot snapshot: snapshots){
            snippetBuilder.buildSnippets(snapshot);
        }

        Map<StateSnapshot, Set<Snippet>> snippets = snippetBuilder.getSnippets();

        LoggingService.infoAll("Finish building snippets");
        logSnippetsForDebug(snippets);

        MethodToMonitor methodToMonitor = javaProject.getMethodToMonitor();
        MethodDeclarationInfoCenter infoCenter = methodToMonitor.getMethodDeclarationInfoCenter();
        Set<IVariableBinding> fieldSet = new HashSet<>(infoCenter.getThisExpressionToMonitor().getFieldsToMonitor().keySet());
        if(!methodToMonitor.returnsVoid())
            fieldSet.add((IVariableBinding) ((SimpleName)infoCenter.getResultExpressionToMonitor().getExpressionAST()).resolveBinding());

        FixActionBuilder fixActionBuilder = new FixActionBuilder();
        for (Map.Entry<StateSnapshot, Set<Snippet>> snippetEntry : snippets.entrySet()) {
            StateSnapshot snapshot = snippetEntry.getKey();
            for (Snippet snippet : snippetEntry.getValue()) {
                fixActionBuilder.buildFixActions(snapshot, snippet);
            }
        }
        Map<LineLocation, Set<FixAction>> fixActions = fixActionBuilder.getFixActionMap();

        LoggingService.infoAll("Finish building fixes");
        logFixActionsForDebug(fixActions);

        return fixActions;
    }

    private static final int MAXIMUM_STATE_SNAPSHOTS = 1500;

    private void removeAllIllformedFixActions(JavaProject project, List<FixAction> fixActions) {
        IllformedFixActionsRemover remover = new IllformedFixActionsRemover(project);
        int nbrRemoved = 0, totalRemoved = 0;

        // repeatedly remove all ill-formed fix actions
        do {
            nbrRemoved = remover.removeIllformedFixActions(fixActions);
            totalRemoved += nbrRemoved;

            if (shouldLogDebug()) {
                LoggingService.debugFileOnly("Number of ill-formed fix actions removed: " + nbrRemoved + " in this round, " + totalRemoved + " int total.", COMPILATION_ERRORS);
            }
        } while (nbrRemoved != 0);
    }

    private List<FixAction> validateFixActions(JavaProject project, List<FixAction> fixActionList) {
        int originalAmount = fixActionList.size();
        int batchSize = BatchFixInstrumentor.TOTAL_BATCH_SIZE;
        int nbrBatches = (fixActionList.size() + batchSize - 1) / batchSize;
        List<FixAction> validFixes = new LinkedList<>();

        LoggingService.infoAll("Number of fix actions to validate:: " + fixActionList.size());

        for (int i = 0; i < nbrBatches; i++) {
            int startIndex = i * batchSize;
            int endIndex = startIndex + batchSize > fixActionList.size() ? fixActionList.size() : startIndex + batchSize;
            List<FixAction> currentBatch = fixActionList.subList(startIndex, endIndex);

            // instrument all fixes
            BatchFixInstrumentor instrumentor = new BatchFixInstrumentor(project);
            instrumentor.instrument(currentBatch);

            // Recompile only the class-to-fix, but not other classes or tests.
            ProjectCompiler compiler = new ProjectCompiler(project);
            compiler.compileFixCandidatesInBatch();
            MutableDiagnosticCollector<JavaFileObject> diagnostics = compiler.getSharedDiagnostics();
            if(!diagnostics.getDiagnostics().isEmpty()) {
                boolean hasError = false;
                for(Diagnostic diagnostic: diagnostics.getDiagnostics()){
                    if(shouldLogDebug()){
                        LoggingService.debugAll(diagnostic.toString());
                    }
                    if(diagnostic.getKind().equals(Diagnostic.Kind.ERROR))
                        hasError = true;
                }
                if(hasError)
                    throw new IllegalStateException();
            }

            int nbrValid = 0;
            for (int j = 0; j < currentBatch.size(); j++) {

                FixAction fixAction = currentBatch.get(j);

                if(shouldLogDebug()){
                    LoggingService.debug("===== Validating fix " + j + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====");
                }

                FixValidator fixValidator = new FixValidator(project, Session.getSession().getConfig().getLogLevel(), 0, project.getTimeoutPerTest() * 5, FailureHandling.CONTINUE);
                fixValidator.validate(currentBatch, j);

                if (fixAction.getSuccessfulTestExecutionResults().size() == project.getTestsToRun().size()) {
                    LoggingService.infoAll("NO."+ ++nbrValid +" valid fix found::" + fixAction.getFixId());
                    LoggingService.infoFileOnly(fixAction.toString(), FixerOutput.LogFile.PLAUSIBLE_LOG);

                    fixAction.setValid(true);
                    validFixes.add(fixAction);
                }
            }
        }

        LoggingService.infoAll("Number of valid fix actions:: " + validFixes.size());
        return validFixes;
    }




    private void computeLocationDistanceToFailure(TestExecutionResult testExecutionResult, MethodDeclarationInfoCenter infoCenter) {
        Map<LineLocation, Double> map = infoCenter.getLocationDistanceToFailureMap();
        List<ProgramState> states = new ArrayList<>(testExecutionResult.getObservedStates());

        ListIterator iterator = states.listIterator(states.size());
        int distance = 0, maximumDistance = -1;
        while (iterator.hasPrevious()) {
            LineLocation location = ((ProgramState)iterator.previous()).getLocation();
            if(map.containsKey(location))
                continue;

            map.put(location, (double) distance);
            if(maximumDistance < distance)
                maximumDistance = distance;

            distance++;
        }
        LineLocation.setMaximumDistanceToFailure(maximumDistance);
    }

    private void computeStateSnapshotSuspiciousnessWithoutDeriveStates(Collection<TestExecutionResult> testResultList, MethodDeclarationInfoCenter methodDeclarationInfoCenter) {
        for(TestExecutionResult testResult: testResultList){
            boolean wasSuccessful = testResult.wasSuccessful();
            for (ProgramState oneObservedState : testResult.getObservedStates()) {
                LineLocation location = oneObservedState.getLocation();
                if(!methodDeclarationInfoCenter.getRelevantLocationStatementMap().containsKey(location))
                    continue;
                for(StateSnapshotExpression expressionToMonitor:methodDeclarationInfoCenter.getStateSnapshotExpressionsWithinMethod()){
                    DebuggerEvaluationResult evaluationResult = expressionToMonitor.evaluate(oneObservedState);
                    if(evaluationResult.hasSemanticError() || evaluationResult.hasSyntaxError())
                        continue;

                    if(!(evaluationResult instanceof DebuggerEvaluationResult.BooleanDebuggerEvaluationResult))
                        throw new IllegalStateException();
                    boolean booleanEvaluationResult = ((DebuggerEvaluationResult.BooleanDebuggerEvaluationResult)evaluationResult).getValue();

                    StateSnapshot stateSnapshot = methodDeclarationInfoCenter.getStateSnapshot(location, expressionToMonitor, booleanEvaluationResult);
                    if(wasSuccessful)
                        stateSnapshot.increaseOccurrenceInPassing();
                    else
                        stateSnapshot.increaseOccurrenceInFailing();
                }
            }
        }

        for(StateSnapshot stateSnapshot: methodDeclarationInfoCenter.getStateSnapshotsWithinMethod()){
            LineLocation location = stateSnapshot.getLocation();
            double distanceToFailure = methodDeclarationInfoCenter.getLocationDistanceToFailureMap().getOrDefault(location, (double)LineLocation.getMaximumDistanceToFailure());
            stateSnapshot.setDistanceToFailure(distanceToFailure);
            stateSnapshot.computeSuspiciousness(methodDeclarationInfoCenter);
        }
    }

}
