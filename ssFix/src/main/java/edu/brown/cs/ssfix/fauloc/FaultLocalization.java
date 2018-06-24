package edu.brown.cs.ssfix.fauloc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;

import com.gzoltar.core.GZoltar;
import com.gzoltar.core.components.Statement;
import com.gzoltar.core.instr.testing.TestResult;

import utils.DataPreparer;

public class FaultLocalization {
	private static Options options;

	static {
		options = new Options();
		options.addOption("bugid", true, "Bug ID");
		options.addOption("dependjpath", true, "The Dependency Jar Path");
		options.addOption("projdpath", true, "Faulty Project's Directory Path");
		options.addOption("projsrcdpath", true, "Faulty Project's Source Directory Path");
		options.addOption("projbuilddpath", true,
				"Faulty Project's Build Directory Path (where the binaries for the source files are saved)");
		options.addOption("projtestbuilddpath", true,
				"Faulty Project's Test Build Directory Path (where the binaries for the test source files are saved)");
		options.addOption("tsuitefpath", true,
				"The File Containing the Class Names of All the Test Cases (Separated by Semi-colon)");
		options.addOption("tpackage", true,
				"The Name of the Package used for Fault Localization. Multiple package names should be connected by colons.");
		options.addOption("failedtestcases", true,
				"The Full Class Name of the Failed Test Cases (if more than one exist, put them together connected by colons)");
		options.addOption("outputdpath", true, "Output Directory");
		options.addOption("cockerdpath", true, "Cocker Directory Path");
		options.addOption("ssfixdpath", true, "ssFix Directory Path");
		options.addOption("maxfaultylines", true, "The Maximum Number of Faulty Lines to be Looked at for Repair");
		options.addOption("maxcandidates", true, "The Maximum Number of Candidate Chunks to be Looked at for Repair");
		options.addOption("parallelgranularity", true, "The Number of Patches to be Validated Simultaneously");
		options.addOption("analysismethod", true, "The Cocker Search Method");
		options.addOption("faulocfpath", true, "The Path of the Fault Localization Result File");
		options.addOption("faulocaddstacktrace", false, "Use the Stack Trace Information for Fault Localization?");
		options.addOption("usesearchcache", false, "Use Cached Search Result?");

		options.addOption("useextendedcodebase", false,
				"Use Extended Code Database (including Manually Retrieved Projects from GitHub)?");
		// options.addOption("runparallel", false, "Run in parallel?");
		options.addOption("deletefailedpatches", false, "Delete Failed Patches?");
	}

	/**
	 *  // The path of the faulty program's source directory (this is where all the source files are located)
	 *  "-projsrcdpath", "", ///Users/qi/Lang_21_buggy/src/main/java
	 *  // The path of the faulty program's source-binary directory (this is where all the binaries of the source files are located)
	 *  "-projbuilddpath", "",///Users/qi/Lang_21_buggy/target/classes
	 *  // The path of the faulty program's test-source-binary directory (this is where all the binaries of the test source files are located)
	 *  "-projtestbuilddpath", "",// /Users/qi/Lang_21_buggy/target/test-classes 
	 */
	public static void main(String[] args) throws IOException {
		String bugid = "Chart_1"; // The bug id
		// The path of the faulty program's directory
		String projdpath = "/Users/kui.liu/Public/Defects4JDataBackup/" + bugid;
		/*
		 * The path of the dependency jar file of the faulty program.
		 *  (You should create a single jar file for the compiled faulty program to be repaired including the test files and all the dependencies.)
		 */
//		String dependjpath = projdpath + "/lib/"; // /Users/qi/Lang_21_buggy/all0.jar; TODO
//		String tsuitefpath = projdpath + "/testsuite_classes";  // /Users/qi/Lang_21_buggy/testsuite_classes 
		String outputdpath = "output/";    // The output directory (to store the generated patches)
//		String ssfixdpath = "/Users/kui.liu/eclipse-workspace/ssFix"; // The directory of ssFix 
		String projtestbuilddpath = projdpath + "/build-tests";// /Users/qi/Lang_21_buggy/target/test-classes
		boolean addstacktrace = false;
		File outputd0 = new File(outputdpath + "/" + bugid);
		if (!outputd0.exists()) outputd0.mkdirs();

		List<String> tpackage_list = new ArrayList<String>();
		// We add the names of directories directly under the project's test build directory.
		// E.g., you have the directory named "org", then "org" is used as the package name.
		File ptbd = new File(projtestbuilddpath);
		File[] ptbd_files = ptbd.listFiles();
		for (File ptbd_file : ptbd_files) {
			if (ptbd_file.isDirectory()) {
				tpackage_list.add(ptbd_file.getName());
			}
		}
		
		DataPreparer dp = new DataPreparer("/Users/kui.liu/Public/Defects4JDataBackup/");
		dp.prepareData(bugid);
		if (!dp.validPaths) return;
		
		FaultLocalization fl = new FaultLocalization();
		List<String> rslt_list = fl.searchGZoltar(projdpath, Arrays.asList(dp.testCases), tpackage_list, addstacktrace, dp.testClassPath, dp.classPath, dp.libPaths);
		StringBuilder sb = new StringBuilder();
		for (String rslt_line : rslt_list) {
			sb.append(rslt_line).append("\n");
		}
		
		File outputf = new File(outputdpath + "/" + bugid + "/gzoltar_fauloc");
		FileUtils.writeStringToFile(outputf, sb.toString().trim());
	}
	
	public List<String> searchGZoltar(String projdpath, List<String> testsuite_classes,
			List<String> tpackage_list, boolean addstacktrace, String testClassPath, String classPath, List<String> libPaths) {

		List<String> rslt_list = new ArrayList<String>();
		double threshold = 0;
		
		URL[] clazzPaths = loadClassPaths(testClassPath, classPath, libPaths);
		ArrayList<String> classpaths = new ArrayList<String>();
        for (URL url : clazzPaths) {
            if ("file".equals(url.getProtocol())) {
            	classpaths.add(url.getPath());
            } else {
            	classpaths.add(url.toExternalForm());
            }
        }

		GZoltar gz = null;
		try {
			gz = new GZoltar(new File(projdpath).getAbsolutePath());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (gz == null) {
			return rslt_list;
		}
		gz.setClassPaths(classpaths);

		// Add instr package
		for (String tpackage : tpackage_list) {
			gz.addPackageToInstrument(tpackage);
		}

		// Add test-suite classes
//		File testsuite_classes_f = new File(tsuitefpath);
//		List<String> testsuite_classes = getTestSuiteClasses(testsuite_classes_f);
		for (String testsuite_class : testsuite_classes) {
			// System.out.println(testsuite_class);
			gz.addTestToExecute(testsuite_class);
			gz.addClassNotToInstrument(testsuite_class);
		}
		gz.run();

		int[] sum = new int[2];
		List<TestResult> test_rslts = gz.getTestResults();
		for (TestResult tr : test_rslts) {
			sum[0]++;
			sum[1] += tr.wasSuccessful() ? 0 : 1;
			if (!tr.wasSuccessful()) {
				if (addstacktrace) {
					rslt_list.add("Test failed: " + tr.getName());
					rslt_list.add(tr.getTrace());
				} else {
					System.out.println("Test failed: " + tr.getName());
					System.out.println(tr.getTrace());
				}
			}
		}
		System.out.println("Gzoltar Test Result Total:" + sum[0] + ", fails: " + sum[1] + ", GZoltar suspicious "
				+ gz.getSuspiciousStatements().size());

		List<Statement> stmt_list = new ArrayList<Statement>();
		List<Statement> gz_susp_stmt_list = gz.getSuspiciousStatements();
		for (Statement s : gz_susp_stmt_list) {
			@SuppressWarnings("unused")
			String compName = s.getMethod().getParent().getLabel();
			double susp = s.getSuspiciousness();
			if (susp > threshold) {
				stmt_list.add(s);
			}
		}

		Collections.sort(stmt_list, new StmtComparator());

		for (Statement s : stmt_list) {
			String rslt_s = "Suspicious line:" + s.getMethod().getParent().getLabel() + "," + s.getLineNumber() + ","
					+ s.getSuspiciousness();
			System.out.println(rslt_s);
			rslt_list.add(rslt_s);
		}

		return rslt_list;
	}

	private URL[] loadClassPaths(String testClassPath, String classPath, List<String> libPaths) {
		URL[] classPaths = classPathFrom(testClassPath);
		classPaths = extendClassPathWith(classPath, classPaths);
		if (libPaths != null) {
			for (String lpath : libPaths) {
				classPaths = extendClassPathWith(lpath, classPaths);
			}
		}
		return classPaths;
	}
	
	private URL[] extendClassPathWith(String classPath, URL[] destination) {
		List<URL> extended = newLinkedList(destination);
		extended.addAll(Arrays.asList(classPathFrom(classPath)));
		return extended.toArray(new URL[extended.size()]);
	}
	
	@SuppressWarnings("unchecked")
	private <T> List<T> newLinkedList(T... elements) {
		return newLinkedList(Arrays.asList(elements));
	}
	
	private <T> List<T> newLinkedList(Collection<? extends T> collection) {
		List<T> newList = newLinkedList();
		return (List<T>) withAll(newList, collection);
	}

	private static <T> List<T> newLinkedList() {
		return new LinkedList<T>();
	}

	private static <T> Collection<T> withAll(Collection<T> destination, Iterable<? extends T> elements) {
		addAll(destination, elements);
		return destination;
	}

	private static <T> boolean addAll(Collection<T> destination, Iterable<? extends T> elements) {
		boolean changed = false;
		for (T element : elements) {
			changed |= destination.add(element);
		}
		return changed;
	}

	private URL[] classPathFrom(String classpath) {
		List<String> folderNames = split(classpath, File.pathSeparatorChar);
		URL[] folders = new URL[folderNames.size()];
		int index = 0;
		for (String folderName : folderNames) {
			folders[index] = urlFrom(folderName);
			index += 1;
		}
		return folders;
	}
	
	private URL urlFrom(String path) {
		URL url = null;
		try {
			url = openFrom(path).toURI().toURL();
		} catch (MalformedURLException e) {
			System.err.println("Illegal name for '" + path + "' while converting to URL");
		}
		return url;
	}
	private static File openFrom(String path) {
		File file = new File(path);
		if (!file.exists()) {
			System.err.println("File does not exist in: '" + path + "'");
		}
		return file;
	}
	private List<String> split(String chainedStrings, Character character) {
		return split(chainedStrings, String.format("[%c]", character));
	}
	
	private static List<String> split(String chainedStrings, String splittingRegex) {
		return Arrays.asList(chainedStrings.split(splittingRegex));
	}

	public class StmtComparator implements Comparator<Statement> {
		@Override
		public int compare(Statement s1, Statement s2) {
			if (s1 == null || s2 == null) {
				return 0;
			}
			return Double.compare(s2.getSuspiciousness(), s1.getSuspiciousness());
		}
	}
}
