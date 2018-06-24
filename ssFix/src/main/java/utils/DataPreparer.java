package utils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prepare data for fault localization.
 * 
 * @author kui.liu
 *
 */
public class DataPreparer {

    private String buggyProjectParentPath;
    
    public String classPath;
    public String srcPath;
    public String testClassPath;
    public String testSrcPath;
    public List<String> libPaths = new ArrayList<>();
    public boolean validPaths = true;
    public String[] testCases;
    public URL[] classPaths;
    
    public DataPreparer(String path){
        if (!path.endsWith("/")){
            path += "/";
        }
        buggyProjectParentPath = path;
    }
    
    public void prepareData(String buggyProject){
//		libPath.add(FromString.class.getProtectionDomain().getCodeSource().getLocation().getFile());
//		libPath.add(EasyMock.class.getProtectionDomain().getCodeSource().getLocation().getFile());
//		libPath.add(IOUtils.class.getProtectionDomain().getCodeSource().getLocation().getFile());
		
		loadPaths(buggyProject);
		
		if (!checkProjectDirectories()){
			validPaths = false;
			return;
		}
		
		loadTestCases();
		
		loadClassPaths();
    }

	private void loadPaths(String buggyProject) {
		String projectDir = buggyProjectParentPath;
		List<String> paths = PathUtils.getSrcPath(buggyProject);
		classPath = projectDir + buggyProject + paths.get(0);
		testClassPath = projectDir + buggyProject + paths.get(1);
		srcPath = projectDir + buggyProject + paths.get(2);
		testSrcPath = projectDir + buggyProject + paths.get(3);

		File libPkg = new File(projectDir + buggyProject + "/lib/");
        if (libPkg.exists() && libPkg.list() != null){
            for (String p: libPkg.list()){
                if (p.endsWith(".jar")){
                    libPaths.add(libPkg.getAbsolutePath()+"/"+p);
                }
            }
        }
        libPkg = new File(projectDir + buggyProject + "/build/lib/");
        if (libPkg.exists() && libPkg.list() != null){
            for (String p: libPkg.list()){
                if (p.endsWith(".jar")){
                    libPaths.add(libPkg.getAbsolutePath()+"/"+p);
                }
            }
        }
	}
	
	private boolean checkProjectDirectories() {
		if (!new File(classPath).exists()) {
			System.err.println("Class path: " + classPath + " does not exist!");
			return false;
		}
		if (!new File(srcPath).exists()) {
			System.err.println("Source code path: " + srcPath + " does not exist!");
			return false;
		}
		if (!new File(testClassPath).exists()) {
			System.err.println("Test class path: " + testClassPath + " does not exist!");
			return false;
		}
		if (!new File(testSrcPath).exists()) {
			System.err.println("Test source path: " + testSrcPath + " does not exist!");
			return false;
		}
		return true;
	}

	private void loadTestCases() {
		testCases = new TestClassesFinder().findIn(JavaLibrary.classPathFrom(testClassPath), false);
		Arrays.sort(testCases);
	}

	private void loadClassPaths() {
		classPaths = JavaLibrary.classPathFrom(testClassPath);
		classPaths = JavaLibrary.extendClassPathWith(classPath, classPaths);
		if (libPaths != null) {
			for (String lpath : libPaths) {
				classPaths = JavaLibrary.extendClassPathWith(lpath, classPaths);
			}
		}
	}
    
}
