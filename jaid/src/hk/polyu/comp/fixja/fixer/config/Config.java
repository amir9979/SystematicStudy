package hk.polyu.comp.fixja.fixer.config;

import hk.polyu.comp.fixja.fixer.log.LogLevel;
import hk.polyu.comp.fixja.java.JavaEnvironment;
import hk.polyu.comp.fixja.java.JavaProject;

/**
 * Created by Max PEI.
 */
public class Config {

    private LogLevel logLevel;

    public enum SnippetConstructionStrategy{BASIC, COMPREHENSIVE};

    private SnippetConstructionStrategy snippetConstructionStrategy;

    public SnippetConstructionStrategy getSnippetConstructionStrategy() {
        return snippetConstructionStrategy;
    }

    public void setSnippetConstructionStrategy(SnippetConstructionStrategy snippetConstructionStrategy) {
        this.snippetConstructionStrategy = snippetConstructionStrategy;
    }

    private JavaEnvironment javaEnvironment;
    private JavaProject javaProject;
    private String methodToFix;

    public String getMethodToFix() {
        return methodToFix;
    }

    public void setMethodToFix(String methodToFix) {
        this.methodToFix = methodToFix;
    }

    public String getFaultyClassName(){
        return getMethodToFix().substring(getMethodToFix().indexOf('@') + 1, getMethodToFix().length());
    }

    public String getFaultyMethodSignature() {
        return getMethodToFix().substring(0, getMethodToFix().indexOf('@'));
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public JavaProject getJavaProject() {
        return javaProject;
    }

    public void setJavaProject(JavaProject javaProject) {
        this.javaProject = javaProject;
    }

    public JavaEnvironment getJavaEnvironment() {
        return javaEnvironment;
    }

    public void setJavaEnvironment(JavaEnvironment javaEnvironment) {
        this.javaEnvironment = javaEnvironment;
    }
}

