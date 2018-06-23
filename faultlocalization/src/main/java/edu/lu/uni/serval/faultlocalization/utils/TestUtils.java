package edu.lu.uni.serval.faultlocalization.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.gzoltar.core.GZoltar;
import com.gzoltar.core.instr.testing.TestResult;

import edu.lu.uni.serval.config.Configuration;
import javassist.NotFoundException;

public class TestUtils {

	public static String getTestTrace(List<String> classpath, String testPath, String classname, String functionname, String buggyProject) throws NotFoundException{
        ArrayList<String> classpaths = new ArrayList<String>();
        for (String path: classpath){
            classpaths.add(path);
        }
        classpaths.add(testPath);
        GZoltar gzoltar;
        try {
            gzoltar = new GZoltar(System.getProperty("user.dir"));//new GZoltar(buggyProject + "/");//
            gzoltar.setClassPaths(classpaths);
            gzoltar.addPackageNotToInstrument("org.junit");
            gzoltar.addPackageNotToInstrument("junit.framework");
            gzoltar.addTestPackageNotToExecute("junit.framework");
            gzoltar.addTestPackageNotToExecute("org.junit");
            gzoltar.addTestToExecute(classname);
            gzoltar.addClassNotToInstrument(classname);
//            gzoltar.run();
            ExecutorService service = Executors.newSingleThreadExecutor();
            Future<Boolean> future = service.submit(new GzoltarRunProcess(gzoltar));
            try {
                future.get(Configuration.GZOLTAR_RUN_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException e){
                future.cancel(true);
                service.shutdownNow();
                RuntimeUtils.killProcess();
                e.printStackTrace();
                return "timeout";
            } catch (TimeoutException e){
                future.cancel(true);
                service.shutdownNow();
                RuntimeUtils.killProcess();
                e.printStackTrace();
                return "timeout";
            } catch (ExecutionException e){
                service.shutdownNow();
                future.cancel(true);
                RuntimeUtils.killProcess();
                return "timeout";
            }
        } catch (NullPointerException e){
            throw new NotFoundException("Test Class " + classname +  " No Found in Test Class Path " + testPath);
        } catch (IOException e){
            return "";
        }
        List<TestResult> testResults = gzoltar.getTestResults();
        for (TestResult testResult: testResults){
            if (testResult.getName().substring(testResult.getName().lastIndexOf('#')+1).equals(functionname)){
                return testResult.getTrace();
            }
        }
        throw new NotFoundException("No Test Named "+functionname + " Found in Test Class " + classname);
    }

	public static int getFailTestNumInProject(String projectName, String defects4jPath, List<String> failedTests){
        String testResult = getDefects4jResult(projectName, defects4jPath, "test");
        if (testResult.equals("")){//error occurs in run
            return Integer.MAX_VALUE;
        }
        if (!testResult.contains("Failing tests:")){
            return Integer.MAX_VALUE;
        }
        int errorNum = 0;
        String[] lines = testResult.trim().split("\n");
        for (String lineString: lines){
            if (lineString.contains("Failing tests:")){
                errorNum =  Integer.valueOf(lineString.split(":")[1].trim());
            } else {
            	failedTests.add(lineString);
            }
        }
        return errorNum;
	}
	
	public static int compileProjectWithDefects4j(String projectName, String defects4jPath) {
		String compileResults = getDefects4jResult(projectName, defects4jPath, "compile").trim();
		String[] lines = compileResults.trim().split("\n");
		if (lines.length != 2) return 1;
        for (String lineString: lines){
        	if (!lineString.endsWith("OK")) return 1;
        }
		return 0;
	}

	private static String getDefects4jResult(String projectName, String defects4jPath, String cmdType) {
		try {
            String result = ShellUtils.shellRun(Arrays.asList("cd " + projectName + "\n", defects4jPath + "framework/bin/defects4j " + cmdType));
            return result;
        } catch (IOException e){
            return "";
        }
	}

	public static String recoverWithGitCmd(String projectName) {
		try {
            ShellUtils.shellRun(Arrays.asList("cd " + projectName + "\n", "git checkout -- ."));
            return "";
        } catch (IOException e){
            return "Failed to recover.";
        }
	}

}
