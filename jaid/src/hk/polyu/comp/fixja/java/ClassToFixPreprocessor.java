package hk.polyu.comp.fixja.java;

import hk.polyu.comp.fixja.ast.MethodUtil;
import hk.polyu.comp.fixja.ast.ReturnStatementRewriter;
import hk.polyu.comp.fixja.fixer.config.Config;
import hk.polyu.comp.fixja.tester.Tester;
import hk.polyu.comp.fixja.util.FileUtil;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.RangeMarker;
import org.eclipse.text.edits.ReplaceEdit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static hk.polyu.comp.fixja.util.FileUtil.writeFile;

/**
 * Created by Max Pei on 5/24/2017.
 */
public class ClassToFixPreprocessor {

    private final JavaProject project;
    private final Config config;

    public ClassToFixPreprocessor(JavaProject project, Config config) {
        this.project = project;
        this.config = config;
    }

    public void preprocess(){
        rewriteClassToFix();
    }

    private void rewriteClassToFix(){
        MethodDeclaration methodDeclaration = getMethodDeclarationToFix();

        CompilationUnit compilationUnit = (CompilationUnit) methodDeclaration.getRoot();
        project.registerIntermediateSourceFilePaths(compilationUnit);

        // Rewrite method to fix
        Document document = new Document(FileUtil.getFileContent(project.getSourceFileToFix(), StandardCharsets.UTF_8));
        int offset = methodDeclaration.getBody().getStartPosition(), length = methodDeclaration.getBody().getLength();
        ReplaceEdit replaceEdit = new ReplaceEdit(offset, length, getFormattedMethodText(methodDeclaration));
        try {
            replaceEdit.apply(document);
            String newContent = document.get();
            writeFile(project.getFormattedSourceFileToFix(), newContent);
        } catch (BadLocationException e) {
            throw new IllegalStateException("Error! Failed to rewrite source file.");
        }
    }

    private MethodDeclaration getMethodDeclarationToFix(){
        String[] files = new String[project.getSourceFiles().size()];
        project.getSourceFiles().stream().map(x -> x.toString()).collect(Collectors.toList()).toArray(files);
        Map<String, AbstractTypeDeclaration> typeDeclarationMap = project.loadASTFromFiles(files);

        String fqClassName = config.getFaultyClassName();
        String methodSignature = config.getFaultyMethodSignature();

        if (!typeDeclarationMap.containsKey(fqClassName)) {
            throw new IllegalStateException("Error! No class with name " + fqClassName + " found in the project.");
        }

        AbstractTypeDeclaration typeDeclaration = typeDeclarationMap.get(fqClassName);
        return MethodUtil.getMethodDeclarationBySignature(typeDeclaration, methodSignature);
    }

    private String getFormattedMethodText(MethodDeclaration methodDeclaration){
        Block body = methodDeclaration.getBody();
        String bodyText = body.toString();
        String statements = bodyText.substring(bodyText.indexOf('{') + 1, bodyText.lastIndexOf('}'));

        bodyText = bodyWithTryAndReturnVariable(methodDeclaration, statements);
        bodyText = bodyWithSeparateDelcarationAndInitialization(methodDeclaration, bodyText);
        bodyText = bodyWithOneStatementOnEachLine(methodDeclaration, bodyText);
        return bodyText;
    }

    /**
     * Index of the try statement in the formatted body (See getFormattedMethodText).
     */
    private static int tryStatementIndex;

    public static int getTryStatementIndex(){
        return tryStatementIndex;
    }

    // Method body enclosed in a try-finally structure:
    //     { body } ==> { [ReturnType var;] Tester.startMethodToMonitor(); try{body} finally{ Tester.endMethodToMonitor();} }
    // Index of the try statement in the formatted body is stored in tryStatementIndex.
    private String bodyWithTryAndReturnVariable(MethodDeclaration methodDeclaration, String statements){
        ITypeBinding typeBinding = methodDeclaration.resolveBinding().getReturnType();
        String resultVariableName = "", resultDeclaration;
        if(MethodUtil.returnsVoid(methodDeclaration)) {
            resultDeclaration = "";
            tryStatementIndex = 3;
        }
        else{
            tryStatementIndex = 4;

            resultVariableName = MethodUtil.getTempReturnVariableName(methodDeclaration);
            resultDeclaration = typeBinding.getQualifiedName() + " " + resultVariableName + "; ";
        }

        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(statements.toCharArray());
        ASTNode node = parser.createAST(null);
        Block newBlock = (Block)node;

        ReturnStatementRewriter returnStatementRewriter = new ReturnStatementRewriter();
        String newStatements = returnStatementRewriter.rewrite(new Document(statements), newBlock, resultVariableName);
        newStatements = resultDeclaration + Tester.class.getName() + "." + Tester.TesterBoundaryMethodName.START_METHOD_TO_MONITOR_METHOD_NAME.getMethodName() + "(); "
                + constructCheckMtfIsMonitored()
                + " try {" + newStatements + "} finally{ " +constructResetMtfIsMonitored()+ Tester.class.getName() + "." + Tester.TesterBoundaryMethodName.END_METHOD_TO_MONITOR_METHOD_NAME.getMethodName() + "(); }";

        return newStatements;
    }

    /**
     * Construct following statements
     * if(Tester.isMonitorMode && Tester.isMonitoringMTF){
     *     throw new Tester.MTFIsMonitoredException();
     * }
     * Tester.isMonitoringMTF=true;
     * @return
     */
    private String constructCheckMtfIsMonitored(){
        StringBuilder checker=new StringBuilder("if(")
                .append(Tester.class.getName()).append(".").append(Tester.IS_MONITOR_MODE).append(" && ")
                .append(Tester.class.getName()).append(".").append(Tester.IS_Executing_MTF).append("){")
                .append("throw new ").append(Tester.class.getName()).append(".").append(Tester.MTFIsMonitoredException.class.getSimpleName()).append("();}")
                .append(Tester.class.getName()).append(".").append(Tester.IS_Executing_MTF).append(" = true;");
        return checker.toString();
    }
    private String constructResetMtfIsMonitored(){
        StringBuilder checker=new StringBuilder();
        checker.append(Tester.class.getName()).append(".").append(Tester.IS_Executing_MTF).append(" = false;\n");
        return checker.toString();
    }

    // Variable initialization separated from variable declaration;
    private String bodyWithSeparateDelcarationAndInitialization(MethodDeclaration methodDeclaration, String statements){
        Document document = new Document(statements);

        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(statements.toCharArray());
        ASTNode node = parser.createAST(null);

        ASTRewrite rewrite = ASTRewrite.create(node.getAST());
        ITrackedNodePosition nodePosition = rewrite.track(node);
        RangeMarker rangeMarker = new RangeMarker(nodePosition.getStartPosition(), nodePosition.getLength());

        if(node instanceof Block){
            Block bodyStatements = (Block)node;
            bodyStatements.accept(new ASTVisitor() {
                @Override
                public boolean visit(VariableDeclarationStatement node) {
                    StringBuilder typeText = new StringBuilder();
                    for(Object modifier: node.modifiers()){
                        typeText.append(modifier.toString()).append(" ");
                    }
                    typeText.append(node.getType().toString());

                    StringBuilder statementText = new StringBuilder();
                    List<VariableDeclarationFragment> fragments = node.fragments();
                    boolean hasInitializer = false;
                    for(VariableDeclarationFragment fragment: fragments){
                        if(fragment.getExtraDimensions() > 0) {
                            statementText.append(typeText.toString()).append(" ")
                                    .append(fragment.toString()).append("; ");
                        }
                        else{
                            statementText.append(typeText.toString()).append(" ")
                                    .append(fragment.getName().toString()).append("; ")
                                    .append(fragment.toString()).append("; ");
                        }
                        if(fragment.getInitializer() != null)
                            hasInitializer = true;
                    }

                    if(hasInitializer){
                        // replace statement
                        ITrackedNodePosition statementPosition = rewrite.track(node);
                        ReplaceEdit replaceEdit = new ReplaceEdit(statementPosition.getStartPosition(), statementPosition.getLength(), statementText.toString());
                        rangeMarker.addChild(replaceEdit);
                    }

                    return super.visit(node);
                }
            });

            try {
                rangeMarker.apply(document);
                String newBody = document.get(rangeMarker.getOffset(), rangeMarker.getLength());
                return newBody;
            }
            catch(BadLocationException e){
                throw new IllegalStateException("Error! Failed to separate variable declaration and initialization.");
            }
        }
        else{
            throw new IllegalStateException();
        }
    }

    // Each statement in a separate line;
    private String bodyWithOneStatementOnEachLine(MethodDeclaration methodDeclaration, String statements){
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(statements.toCharArray());
        ASTNode newBodyAST = parser.createAST(null);
        return newBodyAST.toString();
    }


}
