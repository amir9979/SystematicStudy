package edu.brown.cs.ssfix.patchgen;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import edu.brown.cs.ssfix.repair.Global;
import utils.JunitRunner;
import utils.PathUtils;
import utils.ShellUtils;
import utils.TestUtils;

public class PatchValidator {
	@SuppressWarnings("unused")
	private final boolean OUTPUT_TESTING_RESULT = true;

	@SuppressWarnings("unused")
	private String bug_id;
	// private String proj_dpath;
//	private String proj_testbuild_dpath;
//	private String dependjpath;
//	private String tsuite_fpath;
//	private String ssfix_dpath;
	@SuppressWarnings("unused")
//	private String output_bugid_dpath;
//	private String exec_dpath;

	public PatchValidator(String bug_id, String proj_testbuild_dpath, 
			String tsuitefpath, String ssfix_dpath) {
		this.bug_id = bug_id;
		// this.proj_dpath = proj_dpath;
//		this.proj_testbuild_dpath = proj_testbuild_dpath;
//		this.dependjpath = dependjpath;
//		this.tsuite_fpath = tsuitefpath;
//		this.ssfix_dpath = ssfix_dpath;
		String outputdpath = Global.outputdpath;
//		this.output_bugid_dpath = outputdpath + "/" + bug_id;
//		this.exec_dpath = outputdpath;
	}

	public Patch validate(String patch_text, String patch_fpath, String patch_dpath, String failed_testcases, File targetJavaFile, File targetClassFile) {
		// Write patch to file
		File patch_f = new File(patch_fpath);//TODO copy patch file to the buggy file.
		boolean flag0 = true;
		try {
			FileUtils.writeStringToFile(patch_f, patch_text, (String) null);
		} catch (Throwable t) {
			System.err.println("Failed in Writing the Patch File: " + patch_fpath);
			flag0 = false;
		}
		if (!flag0) {
			return new Patch(patch_fpath, false);
		}
		
		targetClassFile.delete();
//        log.info("Compiling");
        int compileResult = TestUtils.compileProjectWithDefects4j(Global.projdpath, Global.defects4jPath);
//        log.info("Finish of compiling");
        if (compileResult == 1) {
          try {
				ShellUtils.shellRun(Arrays.asList("javac -Xlint:unchecked -source 1.8 -target 1.8 -cp "
						+ buildClasspath(Arrays.asList(PathUtils.getJunitPath()), Global.dp.classPath, Global.dp.testClassPath) + " -d " + Global.dp.classPath + " "
						+ targetJavaFile.getAbsolutePath())); // Compile patched file.
          } catch (IOException e){
              System.err.println(Global.bugid + " ---Fixer: fix fail because of javac exception! ");
              return new Patch(patch_fpath, false);
          }
        }
        if (!targetClassFile.exists()) { // fail to compile
            System.err.println(Global.bugid + " ---Fixer: fix fail because of compile fail! ");
            return new Patch(patch_fpath, false);
        }
        List<String> failedTestsAfterFix = new ArrayList<>();
		int errorTestAfterFix = TestUtils.getFailTestNumInProject(Global.projdpath, Global.defects4jPath, failedTestsAfterFix);
		if (errorTestAfterFix == 0) {
			return new Patch(patch_fpath, true);
		} else {
			return new Patch(patch_fpath, false);
		}
	}

	private String buildClasspath(List<String> additionalPath, String classPath, String testClassPath){
        String path = "\"";
        path += classPath;
        path += System.getProperty("path.separator");
        path += testClassPath;
        path += System.getProperty("path.separator");
        path += JunitRunner.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        path += System.getProperty("path.separator");
        path += StringUtils.join(additionalPath,System.getProperty("path.separator"));
        path += "\"";
        return path;
    }

	@SuppressWarnings("unused")
	private String getTestCasePathsString(File testsuite_f) {

		StringBuilder rslt_sb = new StringBuilder();
		String test_case_names = null;
		try {
			test_case_names = FileUtils.readFileToString(testsuite_f);
		} catch (Throwable t) {
			System.err.println(t);
			t.printStackTrace();
		}
		if (test_case_names == null) {
			return null;
		}
		test_case_names = test_case_names.trim();

		String[] test_case_name_arr = test_case_names.split(";");
		int test_case_name_arr_length = test_case_name_arr.length;
		for (int i = 0; i < test_case_name_arr_length; i++) {
			String test_case_name = test_case_name_arr[i];
			if (i != 0) {
				rslt_sb.append(",");
			}
			int index0 = test_case_name.indexOf(".class");
			if (index0 != -1) {
				rslt_sb.append(test_case_name.substring(0, index0).replace(".", "/"));
				rslt_sb.append(".class");
			} else {
				rslt_sb.append(test_case_name.replace(".", "/"));
			}
		}

		return rslt_sb.toString();
	}
}
