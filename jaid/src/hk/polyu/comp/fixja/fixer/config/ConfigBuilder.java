package hk.polyu.comp.fixja.fixer.config;

import hk.polyu.comp.fixja.fixer.log.LogLevel;
import hk.polyu.comp.fixja.java.JavaEnvironment;
import hk.polyu.comp.fixja.java.JavaProject;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Created by Max PEI.
 */
public class ConfigBuilder {

    private Config config;

    public Config getConfig() {
        return config;
    }

    public void buildConfig(CommandLine commandLine) {
        Properties properties = null;
        String fixjaSettingFileVal = commandLine.getOptionValue(CmdOptions.FIXJA_SETTING_FILE_OPT, null);
        if (fixjaSettingFileVal == null) {
            properties = getPropertiesFromCommandLine(commandLine);
        } else {
            properties = getPropertiesFromFile(Paths.get(fixjaSettingFileVal));
        }

        config = buildConfigFromProperties(properties);
    }

    private Properties getPropertiesFromFile(Path fixjaSettingFile) {
        Properties properties = new Properties();

        try (FileInputStream in = new FileInputStream(fixjaSettingFile.toString())) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Error: Failed to load properties from file.\n\t" + fixjaSettingFile.toString());
        }

        return properties;
    }

    private Properties getPropertiesFromCommandLine(CommandLine commandLine) {
        Properties properties = new Properties();
        for (Option opt : commandLine.getOptions()) {
            String propertyID = opt.getArgName();
            properties.setProperty(propertyID, commandLine.getOptionValue(propertyID));
        }

        return properties;
    }

    private Config buildConfigFromProperties(Properties properties) {
        Config config = new Config();

        initJavaEnvironment(config, properties);
        initLogLevel(config,properties);
        initJavaProject(config, properties);
        initMethodToFix(config, properties);
        initSnippetConstructionStrategy(config, properties);
        return config;
    }

    private void initMethodToFix(Config config, Properties properties) {
        config.setMethodToFix(properties.getProperty(CmdOptions.METHOD_TO_FIX_OPT));
    }

    private void initJavaProject(Config config, Properties properties) {
        config.setJavaProject(new JavaProject(config.getJavaEnvironment(), properties));
    }

    private void initJavaEnvironment(Config config, Properties properties) {
        String jdkDirVal = properties.getProperty(CmdOptions.JDK_DIR_OPT, null);
        if (jdkDirVal == null)
            throw new IllegalStateException("Error: JDK environment not set (" + CmdOptions.JDK_DIR_OPT + ").");
        else {
            config.setJavaEnvironment(new JavaEnvironment(jdkDirVal));
        }
    }
    private void initLogLevel(Config config, Properties properties){
        LogLevel level=LogLevel.valueOf(properties.getProperty(CmdOptions.LOG_LEVEL_OPT));
        config.setLogLevel(level);
    }

    private void initSnippetConstructionStrategy(Config config, Properties properties){
        String strategyStr = properties.getProperty(CmdOptions.SNIPPET_CONSTRUCTION_STRATEGY_OPT);
        if(CmdOptions.SNIPPET_CONSTRUCTION_STRATEGY_COMPREHENSIVE.equals(strategyStr)){
            config.setSnippetConstructionStrategy(Config.SnippetConstructionStrategy.COMPREHENSIVE);
        }
        else{
            config.setSnippetConstructionStrategy(Config.SnippetConstructionStrategy.BASIC);
        }
    }

}
