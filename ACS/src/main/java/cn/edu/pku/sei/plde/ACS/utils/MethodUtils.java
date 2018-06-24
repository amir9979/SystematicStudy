package cn.edu.pku.sei.plde.ACS.utils;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Created by yanrunfa on 16/4/7.
 */
public class MethodUtils {

    public static boolean isInnerMethod(String code, String methodName){
        return !CodeUtils.hasMethod(code, methodName);
    }

    public static boolean isLoopCall(String mainMethodName, String testMethodName, String code){
        String testMethodCode = CodeUtils.getMethodBody(code, testMethodName);
        if (LineUtils.isCallMethod(testMethodCode, mainMethodName)){
            return true;
        }

        List<String> methods = CodeUtils.getAllMethodName(code, true);
        for (String method: methods){
            String methodCode = CodeUtils.getMethodBody(code, method);
            if (LineUtils.isCallMethod(methodCode, mainMethodName) && LineUtils.isCallMethod(testMethodCode, method)){
                return true;
            }
        }
        return false;
    }

    public static String getMethodReturnType(String methodStatement){
        String code = "public class Test { " + methodStatement + "}";
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(code.toCharArray());
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        if (unit.types().size() == 0){
            return "";
        }
        TypeDeclaration declaration = (TypeDeclaration) unit.types().get(0);
        if (declaration.getMethods().length == 0){
            return "";
        }
        MethodDeclaration methodDeclaration = declaration.getMethods()[0];
        if (methodDeclaration.getReturnType2() == null){
            return "";
        }
        return methodDeclaration.getReturnType2().toString();
    }

}
