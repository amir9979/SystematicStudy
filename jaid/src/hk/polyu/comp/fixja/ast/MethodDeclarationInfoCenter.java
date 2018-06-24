package hk.polyu.comp.fixja.ast;

import hk.polyu.comp.fixja.fixer.config.FixerOutput;
import hk.polyu.comp.fixja.fixer.log.LoggingService;
import hk.polyu.comp.fixja.java.ClassToFixPreprocessor;
import hk.polyu.comp.fixja.java.JavaProject;
import hk.polyu.comp.fixja.monitor.ExpressionToMonitor;
import hk.polyu.comp.fixja.monitor.LineLocation;
import hk.polyu.comp.fixja.monitor.MethodToMonitor;
import hk.polyu.comp.fixja.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.fixja.monitor.snapshot.StateSnapshotExpression;
import hk.polyu.comp.fixja.monitor.snapshot.StateSnapshotExpressionBuilder;
import hk.polyu.comp.fixja.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.fixja.util.CommonUtils;
import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hk.polyu.comp.fixja.fixer.config.FixerOutput.LogFile.ALL_STATE_SNAPSHOT;
import static hk.polyu.comp.fixja.fixer.config.FixerOutput.LogFile.MONITORED_EXPS;
import static hk.polyu.comp.fixja.fixer.log.LoggingService.shouldLogDebug;

/**
 * Created by Max PEI.
 */
public class MethodDeclarationInfoCenter {

    private final MethodToMonitor contextMethod;

    public MethodDeclarationInfoCenter(MethodToMonitor contextMethod) {
        this.contextMethod = contextMethod;
    }

    public void init(){
        originalBodyBlock = ((TryStatement)getMethodDeclaration().getBody().statements().get(ClassToFixPreprocessor.getTryStatementIndex())).getBody();
        recordEntryAndExitLocation();
        constructLocationStatementMap();
        constructExpressionsAppearAtLocationMap();
        constructVarDefAssignMap();
        collectExpressionsToMonitor();
    }

    public void buildStateSnapshotExpressions(){
        buildStateSnapshotExpressionsWithinMethod();
        buildStateSnapshotExpressionsAtMethodExit();
    }

    private void constructExpressionsAppearAtLocationMap(){
        expressionsAppearAtLocationMap = new HashMap<>();

        Set<LineLocation> locations = getAllLocationStatementMap().keySet();
        Map<Integer, LineLocation> lineNoToLocationMap = locations.stream().collect(Collectors.toMap(LineLocation::getLineNo, Function.identity()));

        ExpressionFromStatementCollector collector = new ExpressionFromStatementCollector();
        for(LineLocation location: getAllLocationStatementMap().keySet()){
            Statement[] statements = new Statement[3];
            // collect also from lines directly above/below the current line
            statements[0] = getAllLocationStatementMap().get(location);
            statements[1] = getAllLocationStatementMap().getOrDefault(lineNoToLocationMap.get(location.getLineNo() - 1), null);
            statements[2] = getAllLocationStatementMap().getOrDefault(lineNoToLocationMap.get(location.getLineNo() + 1), null);

            collector.collect(statements);
            Set<ExpressionToMonitor> expressionToMonitorSet = collector.getExpressionsToMonitor();
            expressionsAppearAtLocationMap.put(location, expressionToMonitorSet);
        }
    }

    public StateSnapshot getStateSnapshot(LineLocation location, StateSnapshotExpression expression, boolean value){
        if(!getCategorizedStateSnapshotsWithinMethod().containsKey(location))
            throw new IllegalStateException();
        Map<StateSnapshotExpression, Map<Boolean, StateSnapshot>> subMap = getCategorizedStateSnapshotsWithinMethod().get(location);
        if(!subMap.containsKey(expression))
            throw new IllegalStateException();
        Map<Boolean, StateSnapshot> subsubMap = subMap.get(expression);
        return subsubMap.get(value);
    }

    public void buildStateSnapshotsWithinMethod() {
        categorizedStateSnapshotsWithinMethod = new HashMap<>();
        stateSnapshotsWithinMethod = new HashSet<>();

        for(LineLocation location: locationExpressionMap.keySet()){
            if(!getRelevantLocationStatementMap().containsKey(location))
                continue;

            Map<StateSnapshotExpression, Map<Boolean, StateSnapshot>> subMap = new HashMap<>();
            categorizedStateSnapshotsWithinMethod.put(location, subMap);

            for(StateSnapshotExpression stateSnapshotExpression: stateSnapshotExpressionsWithinMethod){
                Map<Boolean, StateSnapshot> subsubMap = new HashMap<>();
                subMap.put(stateSnapshotExpression, subsubMap);

                if(stateSnapshotExpression.isValidAtLocation(location, locationExpressionMap)){
                    StateSnapshot stateSnapshotTrue = new StateSnapshot(location, stateSnapshotExpression, DebuggerEvaluationResult.BooleanDebuggerEvaluationResult.getBooleanDebugValue(true));
                    StateSnapshot stateSnapshotFalse = new StateSnapshot(location, stateSnapshotExpression, DebuggerEvaluationResult.BooleanDebuggerEvaluationResult.getBooleanDebugValue(false));
                    subsubMap.put(true, stateSnapshotTrue);
                    subsubMap.put(false, stateSnapshotFalse);

                    stateSnapshotsWithinMethod.add(stateSnapshotTrue);
                    stateSnapshotsWithinMethod.add(stateSnapshotFalse);
                }
            }
        }
    }

    private void buildStateSnapshotExpressionsWithinMethod() {
        StateSnapshotExpressionBuilder builder = new StateSnapshotExpressionBuilder();
        builder.build(getExpressionsToMonitorWithinMethod().stream().filter(ExpressionToMonitor::isSideEffectFree).collect(Collectors.toList()),
                getConstantIntegers().stream().collect(Collectors.toList()));
        stateSnapshotExpressionsWithinMethod = builder.getStateSnapshotExpressions();
        LoggingService.infoFileOnly("stateSnapshotExpressionsWithinMethod size:"+stateSnapshotExpressionsWithinMethod.size(), FixerOutput.LogFile.MONITORED_EXPS);
    }

    private void buildStateSnapshotExpressionsAtMethodExit() {
        StateSnapshotExpressionBuilder builder = new StateSnapshotExpressionBuilder();
        builder.build(getExpressionsToMonitorAtMethodExit().stream().filter(ExpressionToMonitor::isSideEffectFree).collect(Collectors.toList()),
                getConstantIntegers().stream().collect(Collectors.toList()));
        stateSnapshotExpressionsAtMethodExit = builder.getStateSnapshotExpressions();
    }

    public void registerSideEffectFreeExpressionsToLocation() {
        locationExpressionMap = new HashMap<>();
        SortedSet<ExpressionToMonitor> sideEffectFreeExpressions = getSideEffectFreeExpressionsToMonitorWithinMethod();
        for(LineLocation location: getAllLocationStatementMap().keySet()){
            locationExpressionMap.put(location, new TreeSet<>(sideEffectFreeExpressions));
        }
    }

    // ============================ Getters

    public List<StateSnapshot> orderedStateSnapshots(){
        List<StateSnapshot> result=getStateSnapshotsWithinMethod().stream().collect(Collectors.toList());
        LoggingService.debugFileOnly("All snapshot size:"+getStateSnapshotsWithinMethod().size() , ALL_STATE_SNAPSHOT);
        if(shouldLogDebug()){
            Collections.sort(result, (StateSnapshot s1, StateSnapshot s2) -> Double.compare(s2.getSuspiciousness(), s1.getSuspiciousness()));
            result.stream().forEach(x -> LoggingService.debugFileOnly(x.toString(), ALL_STATE_SNAPSHOT));
            result = result.stream().filter(x -> x.getOccurrenceInFailing() > 0).collect(Collectors.toList());
        }else{
            result = getStateSnapshotsWithinMethod().stream().filter(x -> x.getOccurrenceInFailing() > 0).collect(Collectors.toList());
            Collections.sort(result, (StateSnapshot s1, StateSnapshot s2) -> Double.compare(s2.getSuspiciousness(), s1.getSuspiciousness()));
        }
        return  result;
    }

    public MethodToMonitor getContextMethod() {
        return contextMethod;
    }

    public MethodDeclaration getMethodDeclaration() {
        return contextMethod.getMethodAST();
    }

    public LineLocation getExitLocation() {
        return exitLocation;
    }

    public LineLocation getEntryLocation() {
        return entryLocation;
    }

    public ITypeBinding getIntTypeBinding() {
        if(intTypeBinding == null)
            intTypeBinding = getMethodDeclaration().getAST().resolveWellKnownType("int");

        return intTypeBinding;
    }

    public Statement getStatementAtLocation(LineLocation location){
        return getAllLocationStatementMap().getOrDefault(location, null);
    }

    public ITypeBinding getBooleanTypeBinding() {
        if(booleanTypeBinding == null)
            booleanTypeBinding = getMethodDeclaration().getAST().resolveWellKnownType("boolean");

        return booleanTypeBinding;
    }

    public ExpressionToMonitor getThisExpressionToMonitor(){
        if(thisExpressionToMonitor == null) {
            ThisExpression thisExp = getMethodDeclaration().getAST().newThisExpression();
            thisExpressionToMonitor = new ExpressionToMonitor(thisExp, getMethodDeclaration().resolveBinding().getDeclaringClass());
        }
        return thisExpressionToMonitor;
    }

    public ExpressionToMonitor getResultExpressionToMonitor(){
        if(resultExpressionToMonitor == null) {
            if(getContextMethod().returnsVoid())
                throw new IllegalStateException();

            VariableDeclarationStatement resultDeclaration = (VariableDeclarationStatement) getMethodDeclaration().getBody().statements().get(0);
            SimpleName resultExpression = ((VariableDeclarationFragment) resultDeclaration.fragments().get(0)).getName();
            if(!resultExpression.getIdentifier().equals(getContextMethod().getReturnVariableName()))
                throw new IllegalStateException();

            resultExpressionToMonitor = new ExpressionToMonitor(resultExpression, resultExpression.resolveTypeBinding());
        }
        return resultExpressionToMonitor;
    }

    public boolean isStatic(){
        return Modifier.isStatic(getMethodDeclaration().getModifiers());
    }

    public Map<LineLocation, Statement> getRelevantLocationStatementMap() {
        return relevantLocationStatementMap;
    }

    public Map<LineLocation, Statement> getAllLocationStatementMap() {
        return allLocationStatementMap;
    }

    public SortedSet<ExpressionToMonitor> getExpressionsToMonitorAtMethodExit() {
        return expressionsToMonitorAtMethodExit;
    }

    /**
     * Remove expressions with side effect and expressions invoke the MTF
     */
    public void removeUnsuitableExpressionsAtMethodExit(){
        Set<ExpressionToMonitor> toRemove = new HashSet<>();
        getExpressionsToMonitorAtMethodExit().stream().filter(x -> !x.isSideEffectFree()|| x.isInvokeMTF()).forEach(x -> toRemove.add(x));
        getExpressionsToMonitorAtMethodExit().removeAll(toRemove);
    }

    public SortedSet<ExpressionToMonitor> getExpressionsToMonitorWithinMethod() {
        return expressionsToMonitorWithinMethod;
    }

    public SortedSet<ExpressionToMonitor> getSideEffectFreeExpressionsToMonitorWithinMethod(){
        SortedSet<ExpressionToMonitor> sideEffectFreeExpressions = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        getExpressionsToMonitorWithinMethod().stream().filter(x -> x.isSideEffectFree()).forEach(x -> sideEffectFreeExpressions.add(x));
        return sideEffectFreeExpressions;
    }

    public Set<StateSnapshotExpression> getStateSnapshotExpressionsWithinMethod() {
        return stateSnapshotExpressionsWithinMethod;
    }

    public Set<StateSnapshot> getStateSnapshotsWithinMethod() {
        return stateSnapshotsWithinMethod;
    }

    public Set<StateSnapshotExpression> getStateSnapshotExpressionsAtMethodExit() {
        return stateSnapshotExpressionsAtMethodExit;
    }

    public Map<LineLocation, Double> getLocationDistanceToFailureMap() {
        if(locationDistanceToFailureMap == null)
            locationDistanceToFailureMap = new HashMap<>();

        return locationDistanceToFailureMap;
    }

    public Map<LineLocation, Set<ExpressionToMonitor>> getExpressionsAppearAtLocationMap() {
        return expressionsAppearAtLocationMap;
    }



    public Map<LineLocation, Map<StateSnapshotExpression, Map<Boolean, StateSnapshot>>> getCategorizedStateSnapshotsWithinMethod() {
        return categorizedStateSnapshotsWithinMethod;
    }

    public Set<ExpressionToMonitor> getConstantIntegers() {
        return constantIntegers;
    }

    public Map<LineLocation, SortedSet<ExpressionToMonitor>> getLocationExpressionMap() {
        return locationExpressionMap;
    }

    public Map<IVariableBinding, LineScope> getVariableDefinitionLocationMap() {
        return variableDefinitionLocationMap;
    }

    public Map<IVariableBinding, LineScope> getVariableAssignmentLocationMap() {
        return variableAssignmentLocationMap;
    }


    // ============================ Implementation

    private Block getOriginalBodyBlock(){
        return originalBodyBlock;
    }

    private void constructLocationStatementMap(){
        StatementLocationCollector collector = new StatementLocationCollector(this.getContextMethod());
        Block block = getOriginalBodyBlock();
        collector.collectStatements(block);
        allLocationStatementMap = collector.getLineNoLocationMap();
        relevantLocationStatementMap = new HashMap<>(allLocationStatementMap);

        locationReturnStatementMap = new HashMap<>();
        for(LineLocation location: allLocationStatementMap.keySet()){
            Statement statement = allLocationStatementMap.get(location);
            if(statement instanceof ReturnStatement){
                locationReturnStatementMap.put(location, (ReturnStatement)statement);
            }
        }
    }

    public void pruneIrrelevantLocation(Set<LineLocation> relevantLocations){
        Set <LineLocation> locationToRemove=new HashSet<>();
        for (LineLocation line:relevantLocationStatementMap.keySet()){
            if (! relevantLocations.contains(line)){
                locationToRemove.add(line);
            }
        }
        for (LineLocation location:locationToRemove){
            relevantLocationStatementMap.remove(location);
        }
    }

    private void constructVarDefAssignMap(){
        LocalVariableDefAssignCollector collector = new LocalVariableDefAssignCollector();
        collector.collect(getContextMethod());
        variableDefinitionLocationMap = collector.getVariableDefinitionLocationMap();

        Map<IVariableBinding, LineLocation> assignmentLocations = collector.getVariableAssignmentLocationMap();
        variableAssignmentLocationMap = new HashMap<>();
        for(IVariableBinding var: assignmentLocations.keySet()){
            LineLocation firstAssignLoc = assignmentLocations.get(var);
            LineScope scope = variableDefinitionLocationMap.get(var);
            LineScope writeScope = new LineScope(firstAssignLoc, scope.getEndLocation());
            variableAssignmentLocationMap.put(var, writeScope);
        }
    }

    public Map<String, ITypeBinding> getExpressionTextToTypeMap(){
        if(expressionTextToTypeMap == null){
            expressionTextToTypeMap = new HashMap<>();
        }
        return expressionTextToTypeMap;
    }

    public ITypeBinding getTypeByExpressionText(String text){
        return getExpressionTextToTypeMap().getOrDefault(text, null);
    }

    public boolean hasExpressionTextRegistered(String text){
        return getExpressionTextToTypeMap().containsKey(text);
    }

    public void registerExpressionToMonitor(ExpressionToMonitor expressionToMonitor){
        if(!getExpressionTextToTypeMap().containsKey(expressionToMonitor.getText()))
            getExpressionTextToTypeMap().put(expressionToMonitor.getText(), expressionToMonitor.getType());
    }

    private void collectExpressionsToMonitor() {
        Set<ExpressionToMonitor> expressionToMonitorSet = new HashSet<>();

        // Collect sub-expressions from source code
        ExpressionCollector expressionCollector = new ExpressionCollector(true);
        expressionCollector.collect(getMethodDeclaration());
        expressionCollector.getSubExpressionSet().stream()
                .filter(x -> !(x instanceof NumberLiteral))
                .forEach(x -> expressionToMonitorSet.add(new ExpressionToMonitor(x, x.resolveTypeBinding())));

        if(!isStatic()) {
            expressionToMonitorSet.add(getThisExpressionToMonitor());
        }

        Set<ExpressionToMonitor> enrichedExpressions = enrichExpressions(expressionToMonitorSet);
        expressionToMonitorSet.addAll(enrichedExpressions);

        // sort expressions
        expressionsToMonitorWithinMethod = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        for(ExpressionToMonitor expressionToMonitor: expressionToMonitorSet)
            expressionsToMonitorWithinMethod.add(expressionToMonitor);

        expressionsToMonitorAtMethodExit = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        for(ExpressionToMonitor expressionToMonitor: expressionsToMonitorWithinMethod){
            expressionsToMonitorAtMethodExit.add(expressionToMonitor);
        }

        if(!getContextMethod().returnsVoid()){
            Set<ExpressionToMonitor> resultRelatedExpressions = new HashSet<>();
            resultRelatedExpressions.add(getResultExpressionToMonitor());
            // resultRelatedExpressions.addAll(getResultExpressionToMonitor().getFieldsToMonitor().values());

            expressionsToMonitorAtMethodExit.addAll(enrichExpressions(resultRelatedExpressions));
        }

        // register expression text and type.
        expressionsToMonitorAtMethodExit.forEach(x -> registerExpressionToMonitor(x));

        // collect integral literals
        constantIntegers = new HashSet<>();
        expressionCollector.getSubExpressionSet().stream()
                .filter(x -> x instanceof NumberLiteral)
                .forEach(x -> constantIntegers.add(new ExpressionToMonitor(x, x.resolveTypeBinding())));
        enrichConstantIntegers();

        if(shouldLogDebug()) {
            LoggingService.debugFileOnly("============ Expressions To Monitor At Method Exit (total number: " + getExpressionsToMonitorAtMethodExit().size() + ")", MONITORED_EXPS);
            getExpressionsToMonitorAtMethodExit().forEach(f -> LoggingService.debugFileOnly(f.getType().getQualifiedName() + " :: " + f.toString(), MONITORED_EXPS));

            LoggingService.debugFileOnly("============ Expressions To Monitor Within Method (total number: " + getExpressionsToMonitorWithinMethod().size() + ")", MONITORED_EXPS);
            getExpressionsToMonitorWithinMethod().forEach(f -> LoggingService.debugFileOnly(f.getType().getQualifiedName() + " :: " + f.toString(), MONITORED_EXPS));

            LoggingService.debugFileOnly("============ Constant Integers (total number: " + getConstantIntegers().size() + ")", MONITORED_EXPS);
            getConstantIntegers().forEach(f -> LoggingService.debugFileOnly(f.getType().getQualifiedName() + " :: " + f.toString(), MONITORED_EXPS));
        }
    }

    private Set<ExpressionToMonitor> enrichExpressions(Set<ExpressionToMonitor> existingExpressions) {
        Set<ExpressionToMonitor> newExpressions = new HashSet<>();
        Set<ExpressionToMonitor> referenceExp = existingExpressions.stream().filter(etm -> !etm.getType().isPrimitive() && !etm.hasMethodInvocation()).collect(Collectors.toSet());
        for (ExpressionToMonitor etm : referenceExp) {
            //Methods from binary types that reference unresolved types may not be included.
            IMethodBinding[] methodBindings=etm.getType().getDeclaredMethods();
            if(methodBindings.length==0) methodBindings= etm.getType().getTypeDeclaration().getDeclaredMethods();
            List<IMethodBinding> selectedInvocations =
                    ASTUtils4SelectInvocation.selectGetStateMethods(methodBindings, JavaProject.expToExclude);
            for (IMethodBinding binding : selectedInvocations) {
                MethodInvocation methodInvocation = CommonUtils.appendInvoking(etm.getExpressionAST(), binding.getName(), null);

                ExpressionToMonitor newExp = new ExpressionToMonitor(methodInvocation, binding.getReturnType());
                newExpressions.add(newExp);
            }

            if (etm.getType().isArray() && etm.getExpressionAST() instanceof Name) {
                AST ast = etm.getExpressionAST().getAST();
                FieldAccess fieldAccess = ast.newFieldAccess();
                fieldAccess.setExpression((Expression)ASTNode.copySubtree(ast, etm.getExpressionAST()));
                fieldAccess.setName(ast.newSimpleName("length"));

                ExpressionToMonitor newExp = new ExpressionToMonitor(fieldAccess, getIntTypeBinding());
                newExpressions.add(newExp);
            }
        }
        return newExpressions;
    }

    private void enrichConstantIntegers() {
        for (Integer i : new Integer[]{0, 1}) {
            Expression expr = getMethodDeclaration().getAST().newNumberLiteral(i.toString());
            constantIntegers.add(new ExpressionToMonitor(expr, getIntTypeBinding()));
        }
    }

    private void recordEntryAndExitLocation(){
        CompilationUnit compilationUnit = (CompilationUnit) getMethodDeclaration().getRoot();
        Statement entryStatement = (Statement) (getMethodDeclaration().getBody().statements().get(ClassToFixPreprocessor.getTryStatementIndex() - 1));
        Statement exitStatement = (Statement) (((TryStatement)getMethodDeclaration().getBody().statements().get(ClassToFixPreprocessor.getTryStatementIndex()))
                .getFinally().statements().get(0));
        entryLocation = new LineLocation(getContextMethod(), compilationUnit.getLineNumber(entryStatement.getStartPosition()));
        exitLocation = new LineLocation(getContextMethod(), compilationUnit.getLineNumber(exitStatement.getStartPosition()));
    }

    // ============================== Storage

    private Block originalBodyBlock;
    private LineLocation entryLocation;
    private LineLocation exitLocation;
    private Map<LineLocation, Statement> relevantLocationStatementMap;
    private Map<LineLocation, Statement> allLocationStatementMap;
    private Map<LineLocation, ReturnStatement> locationReturnStatementMap;
    private Map<LineLocation, Double> locationDistanceToFailureMap;
    private Map<LineLocation, SortedSet<ExpressionToMonitor>> locationExpressionMap;
    private Map<LineLocation, Set<ExpressionToMonitor>> expressionsAppearAtLocationMap;
    private ExpressionToMonitor thisExpressionToMonitor;
    private ExpressionToMonitor resultExpressionToMonitor;
    private Map<IVariableBinding, LineScope> variableDefinitionLocationMap;
    private Map<IVariableBinding, LineScope> variableAssignmentLocationMap;
    private SortedSet<ExpressionToMonitor> expressionsToMonitorWithinMethod;
    private SortedSet<ExpressionToMonitor> expressionsToMonitorAtMethodExit;
    private Map<String, ITypeBinding> expressionTextToTypeMap;
    private Set<ExpressionToMonitor> constantIntegers;
    private Set<StateSnapshotExpression> stateSnapshotExpressionsWithinMethod;
    private Set<StateSnapshotExpression> stateSnapshotExpressionsAtMethodExit;
    private Map<LineLocation, Map<StateSnapshotExpression, Map<Boolean, StateSnapshot>>> categorizedStateSnapshotsWithinMethod;
    private Set<StateSnapshot> stateSnapshotsWithinMethod;

    private ITypeBinding intTypeBinding;
    private ITypeBinding booleanTypeBinding;


}
