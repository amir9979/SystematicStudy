package edu.lu.uni.serval.search.fixer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import edu.lu.uni.serval.config.Configuration;
import edu.lu.uni.serval.faultlocalization.utils.AssertUtils;
import edu.lu.uni.serval.faultlocalization.utils.CodeUtils;
import edu.lu.uni.serval.faultlocalization.utils.FileUtils;
import edu.lu.uni.serval.faultlocalization.utils.LineUtils;
import edu.lu.uni.serval.faultlocalization.utils.PathUtils;
import edu.lu.uni.serval.faultlocalization.utils.ShellUtils;
import edu.lu.uni.serval.faultlocalization.utils.SourceUtils;
import edu.lu.uni.serval.faultlocalization.utils.TestUtils;
import javassist.NotFoundException;

@SuppressWarnings("unused")
public class Asserts {
	private String srcPath;
	private String testSrcPath;
	private String classPath;
	private String testClassPath;
	private List<String> libPaths;
	private String testClassname;
	private String testMethodName;
	private String testCode;
	private String testMethodCode;
	private int testMethodStartLine;
	private int testMethodEndLine;
	private List<Integer> errorAssertLines = new ArrayList<>();
	private List<Integer> errorThrowLines = new ArrayList<>();
	private int assertNums;
	private List<String> asserts;
	private Map<String, Integer> assertLineMap;
	private Map<Integer, List<String>> thrownExceptionMap = new HashMap<>();
	private String project;
	private boolean timeout = false;

	public Asserts(String classPath, String srcPath, String testClasspath, String testSrcPath, String testClassname,
			String testMethodName, String project) {
		this(classPath, srcPath, testClasspath, testSrcPath, testClassname, testMethodName, new ArrayList<String>(), project);
	}

	public Asserts(String classPath, String srcPath, String testClasspath, String testSrcPath, String testClassname,
			String testMethodName, ArrayList<String> libPath, String project) {
		this.libPaths = libPath;
		this.classPath = classPath;
		this.srcPath = srcPath;
		this.testClassname = testClassname;
		this.testClassPath = testClasspath;
		this.testSrcPath = testSrcPath;
		this.testMethodName = testMethodName;
		this.project = project;
		this.testCode = FileUtils.getCodeFromFile(this.testSrcPath, this.testClassname);

		if (!testCode.contains(this.testMethodName) && testCode.contains(" extends ")) {
			String extendsClass = testCode.split(" extends ")[1].substring(0, testCode.split(" extends ")[1].indexOf("{"));
			String className = CodeUtils.getClassNameOfImportClass(testCode, extendsClass);
			if (className.equals("")) {
				className = CodeUtils.getPackageName(testCode) + "." + extendsClass;
			}
			String extendsCode = FileUtils.getCodeFromFile(this.testSrcPath, className.trim());
			if (!extendsCode.equals("")) {
				testCode = extendsCode;
			}
		}
		testMethodCode = FileUtils.getTestFunctionCodeFromCode(testCode, this.testMethodName, this.testSrcPath);
		List<Integer> testMethodLines = CodeUtils.getSingleMethodLine(testCode, this.testMethodName);
		if (testMethodLines.size() == 2) {
			testMethodStartLine = testMethodLines.get(0);
			testMethodEndLine = testMethodLines.get(1);
		} else {
			testMethodStartLine = 0;
			testMethodEndLine = 0;
		}
		assertLineMap = CodeUtils.getAssertInTest(testCode, this.testMethodName, testMethodStartLine);
		asserts = new ArrayList<>(assertLineMap.keySet());
		assertNums = asserts.size();
		errorAssertLines = getErrorAssertLine();
	}

	private List<Integer> getErrorAssertLine() {
		List<Integer> result = new ArrayList<>();
		File tempJavaFile = FileUtils.copyFile(FileUtils.getFileAddressOfJava(testSrcPath, testClassname), tempJavaPath(testClassname));
		File originClassFile = new File(FileUtils.getFileAddressOfClass(testClassPath, testClassname));
		File backupClassFile = FileUtils.copyFile(originClassFile.getAbsolutePath(), originClassFile.getAbsolutePath() + ".AssertsBackup");
		String oldTrace = "";
		while (true) {
			int lineNum = 0;
			try {
				List<String> classpaths = new ArrayList<>(libPaths);
				classpaths.add(classPath);
				String trace = TestUtils.getTestTrace(classpaths, testClassPath, testClassname, testMethodName, project);
				if (trace == null || trace.equals(oldTrace) || trace.contains("NoClassDefFoundError")
						|| trace.contains("NoSuchMethodError")) {
					break;
				}
				if (trace.equals("timeout")) {
					timeout = true;
					break;
				}
				oldTrace = trace;
				List<String> thrownExceptions = new ArrayList<>();
				for (String line : trace.split("\n")) {
					if (line.contains(testClassname) && line.contains(testMethodName) && line.contains("(")
							&& line.contains(")") && line.contains(":")) {
						lineNum = Integer.valueOf(
								line.substring(line.lastIndexOf("(") + 1, line.lastIndexOf(")")).split(":")[1]);
					}
					if (line.contains("Exception")) {
						thrownExceptions.add(line);
					}
				}
				if (result.contains(lineNum)) {
					break;
				}
				String code = FileUtils.getCodeFromFile(tempJavaFile);
				String lineString = CodeUtils.getLineFromCode(code, lineNum).trim();
				if (AssertUtils.isAssertLine(lineString, code)) {
					thrownExceptionMap.put(lineNum, thrownExceptions);
					result.add(lineNum);
				}
				if (lineString.startsWith("fail(")) {
					int num = lineNum;
					while (!CodeUtils.getLineFromCode(FileUtils.getCodeFromFile(tempJavaFile), num).trim()
							.startsWith("try")) {
						SourceUtils.commentCodeInSourceFile(tempJavaFile, num);
						num--;
					}
				} else if (LineUtils.isLineInFailBlock(code, lineNum)) {// 如果在fail的语句内抛出异常
					for (int i = lineNum; i < testMethodEndLine; i++) {
						if (CodeUtils.getLineFromCode(code, i).trim().startsWith("fail(")) {
							int num = i;
							while (!CodeUtils.getLineFromCode(FileUtils.getCodeFromFile(tempJavaFile), num).trim()
									.startsWith("try")) {
								SourceUtils.commentCodeInSourceFile(tempJavaFile, num);
								num--;
							}
							thrownExceptionMap.put(i, thrownExceptions);
							result.add(i);
							errorThrowLines.add(lineNum);
							break;
						}
					}
				} else if (!AssertUtils.isAssertLine(lineString, code)) {
					errorThrowLines.add(lineNum);
					break;
				}
				SourceUtils.commentCodeInSourceFile(tempJavaFile, lineNum);
				ShellUtils.shellRun(Arrays.asList("javac -Xlint:unchecked -source 1.8 -target 1.8 -cp "
						+ buildClasspath(Arrays.asList(PathUtils.getJunitPath(), testClassPath, classPath)) + " -d "
						+ testClassPath + " " + tempJavaFile.getAbsolutePath()));
			} catch (NotFoundException e) {
				System.out.println("ERROR: Cannot Find Source File: " + testClassname + " in temp file package\n");
				break;
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
		originClassFile.delete();
		tempJavaFile.delete();
		backupClassFile.renameTo(originClassFile);
		return result;
	}

	private String tempJavaPath(String classname) {
		if (!new File(Configuration.TEMP_FILES_PATH + "/assert/").exists()) {
			new File(Configuration.TEMP_FILES_PATH + "/assert/").mkdirs();
		}
		return Configuration.TEMP_FILES_PATH + "/assert/" + classname.substring(classname.lastIndexOf(".") + 1) + ".java";
	}

	private String buildClasspath(List<String> pathList) {
		pathList = new ArrayList<>(pathList);
		if (libPaths != null) {
			pathList.addAll(libPaths);
		}
		String path = "\"";
		path += StringUtils.join(pathList, System.getProperty("path.separator"));
		path += "\"";
		return path;
	}

	public int errorNum() {
		return errorAssertLines.size() + errorThrowLines.size();
	}
}
