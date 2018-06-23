package edu.lu.uni.serval.search.syntactic.fixer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.lu.uni.serval.JavaFileParser.JavaFileParser;
import edu.lu.uni.serval.JavaFileParser.TypeReader;
import edu.lu.uni.serval.config.Configuration;
import edu.lu.uni.serval.faultlocalization.SuspiciousCode;
import edu.lu.uni.serval.faultlocalization.dataprepare.DataPreparer;
import edu.lu.uni.serval.faultlocalization.utils.FileUtils;
import edu.lu.uni.serval.faultlocalization.utils.JunitRunner;
import edu.lu.uni.serval.faultlocalization.utils.PathUtils;
import edu.lu.uni.serval.faultlocalization.utils.ShellUtils;
import edu.lu.uni.serval.faultlocalization.utils.TestUtils;
import edu.lu.uni.serval.jdt.tree.ITree;
import edu.lu.uni.serval.method.Method;
import edu.lu.uni.serval.search.fixer.Patch;
import edu.lu.uni.serval.search.fixer.PatchGenerator;
import edu.lu.uni.serval.utils.Checker;
import edu.lu.uni.serval.utils.FileHelper;
import edu.lu.uni.serval.utils.ListSorter;

public class MainFixer {
	
	private static Logger log = LoggerFactory.getLogger(MainFixer.class);
	
	public String metric = "null";
	public Map<String, Integer> bugSignatures;
	public String matchedSimilarMethodsPath;
	private String path = "/Users/kui.liu/Public/Defects4J/";
	private String buggyProject = "Chart_3"; 
	private String defects4jPath;
	public int minErrorTest;
	private int minErrorTestAfterFix = 0;
	private String fullBuggyProjectPath;
	public String outputPath = "";
	public File suspiciousFile = null;
	
	private List<String> triedSuspiciousCodeList = new ArrayList<>();
	private List<String> triedSuspiciousSignaturesList = new ArrayList<>();
	private Map<SuspiciousCode, Method> suspiciousMethods = new HashMap<>();
	private List<String> failedTestStrList = new ArrayList<>();
	
	public int fixedStatus = 0;
	public String dataType = "";
	public int buggyLine = 0;
//	private TimeLine timeLine;
	
	public MainFixer(String path, String projectName, int bugId, String defects4jPath) {
		this.path = path;
		this.buggyProject = projectName + "_" + bugId;
		fullBuggyProjectPath = path + buggyProject;
		this.defects4jPath = defects4jPath;
		int compileResult = TestUtils.compileProjectWithDefects4j(fullBuggyProjectPath, this.defects4jPath);
        if (compileResult == 1) {
        	 System.err.println(buggyProject + " ---Fixer: fix fail because of compile fail! ");
        }
		minErrorTest = TestUtils.getFailTestNumInProject(path + buggyProject, defects4jPath, failedTestStrList);
		System.out.println(buggyProject + " Failed Tests: " + this.minErrorTest);
	}
	
	public MainFixer(String path, String metric, String projectName, int bugId, String defects4jPath) {
		this(path, projectName, bugId, defects4jPath);
		this.metric = metric;
	}

	@SuppressWarnings("unused")
	private Map<String, Integer> errAssertBeforeFixMap = new HashMap<>();

	public void fixProcess() throws IOException {
		// Read paths of the buggy project.
		DataPreparer dp = new DataPreparer(path);
		dp.prepareData(buggyProject);
		if (!dp.validPaths) return;
		
		// Read suspicious positions.
		List<SuspiciousCode> suspiciousCodeList = readSuspiciousCodeFromFile(metric, path, buggyProject, dp);
		if (suspiciousCodeList == null) return;
//		timeLine = new TimeLine(-180);
//		System.out.println(dateFormat.format(new Date()));
		log.info("=======Fixing Beginning======");
//		System.out.println(buggyProject + ": " + bugSignatures.size());
		for (SuspiciousCode suspiciousCode : suspiciousCodeList) {
			String suspiciousClassName = suspiciousCode.getClassName();
			int buggyLine = suspiciousCode.getLineNumber();
			String suspiciousCodeStr = suspiciousClassName + "#" + buggyLine;// + methodName + "#"
			if (triedSuspiciousCodeList.contains(suspiciousCodeStr)) continue;
			triedSuspiciousCodeList.add(suspiciousCodeStr);
			
			String suspiciousJavaFile = suspiciousClassName.replace(".", "/") + ".java";
			String filePath = dp.srcPath + suspiciousJavaFile;
			JavaFileParser jfp = new JavaFileParser();
			jfp.parseSuspiciousJavaFile(buggyProject, new File(filePath), buggyLine);
			List<Method> methods = jfp.getMethods();
			if (methods == null || methods.isEmpty()) {
//				System.err.println(buggyProject + "  " + suspiciousCodeStr);
				continue;
			}
			Method method = methods.get(0);//InnerClassMethod TODO
			String returnType = method.getReturnTypeString();
			String arguments = method.getArgumentsStr();
			String methodName1 = method.getName();
			returnType = TypeReader.canonicalType(returnType);//TODO exact the same return type and argument types.
			String argumentTypes = TypeReader.readArgumentTypes(arguments);
			String signature = buggyProject + "#" + suspiciousJavaFile + "#" + methodName1 + "#" + returnType + "#" + argumentTypes;//argumentTypes;
			if (triedSuspiciousSignaturesList.contains(signature + "#" + buggyLine)) continue;
			triedSuspiciousSignaturesList.add(signature + "#" + buggyLine);
			
			Integer index = this.bugSignatures.get(signature);
			if (index == null) continue;
			
			if (buggyLine != this.buggyLine && this.buggyLine != 0) continue;
			
			method.setSignature(signature);
			suspiciousMethods.put(suspiciousCode, method);
//			String bugPosition = this.bugPositions.get(index);
			ITree suspStmtTree = jfp.getSuspiciousStmt();
//			if (suspStmtTree == null) {
//				System.out.println("Failed to identify the buggy statement: " + signature);
//				continue;
//			}
			if (suspStmtTree != null) {
				int suspType = suspStmtTree.getType();
				if (suspType != 60 && suspType != 21 && suspType != 41 && suspType != 25 && suspType != 24) {
					// VariableDeclarationStatement, ExpressionStatement, ReturnStatement, IfStatement, ForStatement
					continue;
				}
			}
			
			String exceptionStr = jfp.getMethodTree().getLabel();
			int indexExp = exceptionStr.indexOf("@@Exp:");
			if (indexExp > 0) {
				exceptionStr = exceptionStr.substring(indexExp + 6);
				if (exceptionStr.contains("+")) exceptionStr = "";
			} else {
				exceptionStr = "";
			}
			
			int startLine = buggyLine - method.getStartLine();
//			int endLine = method.getEndLine();
			int methodStartPos = method.getStartPosition();
			int methodEndPos = method.getEndPosition();
			int startPos = suspStmtTree == null ? -1 : suspStmtTree.getPos();
			int endPos = suspStmtTree == null ? -1 : (startPos + suspStmtTree.getLength());
			String suspiciousStmtCode = suspStmtTree == null ? "" :FileHelper.readFile(filePath).substring(startPos, endPos);
			String suspiciousMethodCode = method.getBody();
			
			File targetJavaFile = new File(FileUtils.getFileAddressOfJava(dp.srcPath, suspiciousClassName));
	        File targetClassFile = new File(FileUtils.getFileAddressOfClass(dp.classPath, suspiciousClassName));
	        File javaBackup = new File(FileUtils.tempJavaPath(suspiciousClassName, "Fixer"));
	        File classBackup = new File(FileUtils.tempClassPath(suspiciousClassName, "Fixer"));
	        FileHelper.outputToFile(javaBackup, FileHelper.readFile(targetJavaFile), false);
	        FileHelper.outputToFile(classBackup, FileHelper.readFile(targetClassFile), false);
	        
			// Read matched similar methods with signature, and try to fix the bug with similar methods.
			List<String> methodInfoList = new ArrayList<>();
			List<String> similarMethods = readSimilarMethods(index, methodInfoList, this.dataType);
			
			if (similarMethods.size() > 0) {
				fixWithMatchedSimilarMethods(similarMethods, suspiciousMethodCode, suspiciousStmtCode, suspiciousClassName, javaBackup, classBackup, targetJavaFile,
						targetClassFile, startLine, methodStartPos, methodEndPos, startPos, endPos, dp, methodInfoList, methodName1, outputPath + this.dataType, signature, suspStmtTree, returnType, argumentTypes, exceptionStr);
//				fixWithMatchedSimilarMethods(similarMethods, suspiciousStmtCode, suspiciousClassName, javaBackup, classBackup, targetJavaFile,
//						targetClassFile, startPos, endPos, dp, methodInfoList, methodName1, outputPath + this.dataType, signature, suspStmtTree, returnType, exceptionStr);
			}
			if (minErrorTest == 0) break;
        }
	}

	private void fixWithMatchedSimilarMethods(List<String> similarMethods, String suspiciousMethodCode, String suspiciousStmtCode, String suspiciousClassName, File javaBackup, File classBackup, File targetJavaFile,
			File targetClassFile, int startLine, int methodStartPos, int methodEndPos, int startPos, int endPos, DataPreparer dp,
			List<String> methodInfoList, String methodName, String type, String signature, ITree suspStmtTree, String returnType, String argumentTypes, String exceptionStr) {
		// Chart_1#org/jfree/chart/renderer/category/AbstractCategoryItemRenderer.java#getLegendItems#LegendItemCollection#null
		String[] elements = signature.split("#");
		String packageName = elements[1].replace("/", ".");
		packageName = packageName.substring(0, packageName.length() - 5);
		@SuppressWarnings("unused")
		int a = 0;
		for (int i = 0, size = similarMethods.size(); i < size; i ++) {
			String similarMethodInfo = methodInfoList.get(i);
			elements = similarMethodInfo.split(":");
//			String packageName_ = elements[1] + "." + elements[2];
//			if (timeLine.isTimeout()) {
//				System.out.println(buggyProject + " ---Fixer: fix fail because of time out! ");
//				break;
//			}
//			if (packageName_.equals(packageName)) continue;
//			if (++ a > 10) break;
			String similarMethod = similarMethods.get(i);
			Patch patch = new PatchGenerator().generatePatch(suspiciousMethodCode, suspiciousStmtCode, startLine, suspiciousClassName, similarMethod, suspStmtTree, returnType, argumentTypes, exceptionStr);
			String patchMethodCode = patch.patchMethodCode;
			if (patchMethodCode != null) {
				FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
				addCodeToFile(targetJavaFile, patchMethodCode, methodStartPos, methodEndPos);// Insert the patch.
	            targetClassFile.delete();
	            log.info("Compiling");
	            int compileResult = TestUtils.compileProjectWithDefects4j(fullBuggyProjectPath, this.defects4jPath);
	            log.info("Finish of compiling");
	            if (compileResult == 1) {
	              try {
	  				ShellUtils.shellRun(Arrays.asList("javac -Xlint:unchecked -source 1.8 -target 1.8 -cp "
	  						+ buildClasspath(Arrays.asList(PathUtils.getJunitPath()), dp.classPath, dp.testClassPath) + " -d " + dp.classPath + " "
	  						+ targetJavaFile.getAbsolutePath())); // Compile patched file.
	              } catch (IOException e){
	                  System.err.println(buggyProject + " ---Fixer: fix fail because of javac exception! " + i);
	                  continue;
	              }
	            }
	            if (!targetClassFile.exists()) { // fail to compile
	                System.err.println(buggyProject + " ---Fixer: fix fail because of compile fail! " + i);
	                continue;
	            }
	            
	            //TODO: output mutations.
	            FileHelper.outputToFile("Mutations/" + this.buggyProject + "_mutations.txt", "//======MUTATION======//\n" + patchMethodCode + "\n\n", true);
	            
	            List<String> failedTestsAfterFix = new ArrayList<>();
				int errorTestAfterFix = TestUtils.getFailTestNumInProject(fullBuggyProjectPath, this.defects4jPath, failedTestsAfterFix);
				if (errorTestAfterFix < minErrorTest) {
					failedTestsAfterFix.removeAll(this.failedTestStrList);
					if (failedTestsAfterFix.size() > 0) {
						System.err.println(buggyProject + " ---Generated new bugs: " + failedTestsAfterFix.size());
						continue;
					}
					if (errorTestAfterFix == 0) {
						if (type.endsWith("EMS")) fixedStatus = 1;
						else fixedStatus = 3;
						minErrorTest = errorTestAfterFix;
						log.info("Succeeded to fix the bug " + buggyProject + "====================");
						FileHelper.outputToFile(type + "/FixedBugs/" + buggyProject + "/buggyMethod_" + methodName + "_" + methodStartPos + "_" + i + ".txt", 
								"//**********************************************************\n//  " + signature + 
								"\n//**********************************************************\n" + suspiciousMethodCode, false);
						FileHelper.outputToFile( type + "/FixedBugs/" + buggyProject + "/patchMethod_" + methodName + "_" + methodStartPos + "_" + i + ".txt",
								"//**********************************************************\n//  " + similarMethodInfo + 
								"\n//**********************************************************\n" + patch.patchMethodCode, false);
//						break;
					} else {
						if (minErrorTestAfterFix == 0 || errorTestAfterFix < minErrorTestAfterFix) {
							minErrorTestAfterFix = errorTestAfterFix;
							if (type.endsWith("EMS") && fixedStatus != 1) fixedStatus = 2;
							else if (type.endsWith("EMSWN") && fixedStatus != 3) fixedStatus = 4;
							log.info("Partially Succeeded to fix the bug " + buggyProject + "====================");
							FileHelper.outputToFile( type + "/PartialFixedBugs/" + buggyProject + "/buggyMethod_" + methodName + "_" + methodStartPos + "_" + i + ".txt", 
									"//**********************************************************\n//  " + signature + 
									"\n//**********************************************************\n" + suspiciousMethodCode, false);
							FileHelper.outputToFile( type + "/PartialFixedBugs/" + buggyProject + "/patchMethod_" + methodName + "_" + methodStartPos + "_" + i + ".txt",
									"//**********************************************************\n//  " + similarMethodInfo + 
									"\n//**********************************************************\n" + patch.patchMethodCode, false);
						}
					}
				} else {
					System.err.println("Failed Tests after fixing: " + errorTestAfterFix + " " + buggyProject + " " + i);
				}
			}
			
			List<String> patchStmts = patch.patchStatementCode;
			if (patchStmts == null) continue;
			for (int index = 0, size2 = patchStmts.size(); index < size2; index ++) {
				String patchStmt = patchStmts.get(index);
				FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
				String patchCode = addCodeToFile(targetJavaFile, patchStmt, startPos, endPos, suspiciousMethodCode, suspStmtTree);// Insert the patch.

	            targetClassFile.delete();
	            log.info("Compiling");
	            int compileResult = TestUtils.compileProjectWithDefects4j(fullBuggyProjectPath, this.defects4jPath);
	            log.info("Finish of compiling");
	            if (compileResult == 1) {
	              try {
	  				ShellUtils.shellRun(Arrays.asList("javac -Xlint:unchecked -source 1.8 -target 1.8 -cp "
	  						+ buildClasspath(Arrays.asList(PathUtils.getJunitPath()), dp.classPath, dp.testClassPath) + " -d " + dp.classPath + " "
	  						+ targetJavaFile.getAbsolutePath())); // Compile patched file.
	              } catch (IOException e){
	                  System.err.println(buggyProject + " ---Fixer: fix fail because of javac exception! " + i);
	                  continue;
	              }
	            }
	            if (!targetClassFile.exists()) { // fail to compile
	                System.err.println(buggyProject + " ---Fixer: fix fail because of compile fail! " + i);
	                continue;
	            }
	            
	            //TODO: output mutations.
	            FileHelper.outputToFile("Mutations/" + this.buggyProject + "_mutations.txt", "//======MUTATION======//\n" + patchCode + "\n\n", true);
	            
	            List<String> failedTestsAfterFix = new ArrayList<>();
				int errorTestAfterFix = TestUtils.getFailTestNumInProject(fullBuggyProjectPath, this.defects4jPath, failedTestsAfterFix);
				if (errorTestAfterFix < minErrorTest) {
					failedTestsAfterFix.removeAll(this.failedTestStrList);
					if (failedTestsAfterFix.size() > 0) {
						System.err.println(buggyProject + " ---Generated new bugs: " + failedTestsAfterFix.size());
						continue;
					}
					if (errorTestAfterFix == 0) {
						fixedStatus = 1;
//						minErrorTest = errorTestAfterFix;
						log.info("Succeeded to fix the bug " + buggyProject + "====================");
						FileHelper.outputToFile(type + "/FixedBugs/" + buggyProject + "/Patch_" + methodName + "_" + startPos + "_" + i + "_" + index + ".txt", 
								"//**********************************************************\n//  " + signature + 
								"\n//**********************************************************\n===Buggy Code===\n" + suspiciousStmtCode + 
								"\n===Patch Code===\n" + similarMethodInfo + "\n" + patchCode, false);
//						break;
					} else {
						if (minErrorTestAfterFix == 0 || errorTestAfterFix <= minErrorTestAfterFix) {
							minErrorTestAfterFix = errorTestAfterFix;
							if (fixedStatus != 1) fixedStatus = 2;
							log.info("Partially Succeeded to fix the bug " + buggyProject + "====================");
							FileHelper.outputToFile( type + "/PartialFixedBugs/" + buggyProject + "/Patch_" + methodName + "_" + startPos + "_" + i + "_" + index + ".txt", 
									"//**********************************************************\n//  " + signature + 
									"\n//**********************************************************\n===Buggy Code===\n" + suspiciousStmtCode + 
									"\n===Patch Code===\n" + similarMethodInfo + "\n" + patchCode, false);
						}
					}
				} else {
					System.err.println("Failed Tests after fixing: " + errorTestAfterFix + " " + buggyProject + " " + i);
				}
			}
//			if (minErrorTest == 0) break;
		}
		FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
	    FileHelper.outputToFile(targetClassFile, FileHelper.readFile(classBackup), false);
	}

	@SuppressWarnings("unused")
	private void fixWithMatchedSimilarMethods(List<String> similarMethods, String suspiciousStmtCode, String suspiciousClassName, File javaBackup, File classBackup, File targetJavaFile,
			File targetClassFile, int startPos, int endPos, DataPreparer dp,
			List<String> methodInfoList, String methodName, String type, String signature, ITree suspStmtTree, String returnType, String exceptionStr) {
		// Chart_1#org/jfree/chart/renderer/category/AbstractCategoryItemRenderer.java#getLegendItems#LegendItemCollection#null
		String[] elements = signature.split("#");
		String packageName = elements[1].replace("/", ".");
		packageName = packageName.substring(0, packageName.length() - 5);
		int a = 0;
		for (int i = 0, size = similarMethods.size(); i < size; i ++) {
			String similarMethodInfo = methodInfoList.get(i);
			elements = similarMethodInfo.split(":");
			String packageName_ = elements[1] + "." + elements[2];
//			if (timeLine.isTimeout()) {
//				System.out.println(buggyProject + " ---Fixer: fix fail because of time out! ");
//				break;
//			}
			if (packageName_.equals(packageName)) continue;
			if (++ a > 10) break;
			if (a < 9) continue;
			String similarMethod = similarMethods.get(i);
			Patch patch = new PatchGenerator().generatePatch(suspiciousStmtCode, suspiciousClassName, similarMethod, suspStmtTree, returnType, exceptionStr);
			List<String> patchStmts = patch.patchStatementCode;
			for (int index = 0, size2 = patchStmts.size(); index < size2; index ++) {
				String patchStmt = patchStmts.get(index);
				if (!"".equals(TestUtils.recoverWithGitCmd(fullBuggyProjectPath))) {
					FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
				}
				String patchCode = addCodeToFile(targetJavaFile, patchStmt, startPos, endPos, suspiciousStmtCode, suspStmtTree);// Insert the patch.

	            targetClassFile.delete();
	            log.info("Compiling");
	            int compileResult = TestUtils.compileProjectWithDefects4j(fullBuggyProjectPath, this.defects4jPath);
	            log.info("Finish of compiling");
	            if (compileResult == 1) {
	              try {
	  				ShellUtils.shellRun(Arrays.asList("javac -Xlint:unchecked -source 1.8 -target 1.8 -cp "
	  						+ buildClasspath(Arrays.asList(PathUtils.getJunitPath()), dp.classPath, dp.testClassPath) + " -d " + dp.classPath + " "
	  						+ targetJavaFile.getAbsolutePath())); // Compile patched file.
	              } catch (IOException e){
	                  System.err.println(buggyProject + " ---Fixer: fix fail because of javac exception! " + i);
	                  continue;
	              }
//	            	 System.err.println(buggyProject + " ---Fixer: fix fail because of compile fail! " + i);
//	                 continue;
	            }
	            if (!targetClassFile.exists()) { // fail to compile
	                System.err.println(buggyProject + " ---Fixer: fix fail because of compile fail! " + i);
	                continue;
	            }
	            List<String> failedTestsAfterFix = new ArrayList<>();
				int errorTestAfterFix = TestUtils.getFailTestNumInProject(fullBuggyProjectPath, this.defects4jPath, failedTestsAfterFix);
				if (errorTestAfterFix < minErrorTest) {
					failedTestsAfterFix.removeAll(this.failedTestStrList);
					if (failedTestsAfterFix.size() > 0) {
						System.err.println(buggyProject + " ---Generated new bugs: " + failedTestsAfterFix.size());
						continue;
					}
					if (errorTestAfterFix == 0) {
						fixedStatus = 1;
//						minErrorTest = errorTestAfterFix;
						log.info("Succeeded to fix the bug " + buggyProject + "====================");
						FileHelper.outputToFile(type + "/FixedBugs/" + buggyProject + "/Patch_" + methodName + "_" + startPos + "_" + i + "_" + index + ".txt", 
								"//**********************************************************\n//  " + signature + 
								"\n//**********************************************************\n===Buggy Code===\n" + suspiciousStmtCode + 
								"\n===Patch Code===\n" + similarMethodInfo + "\n" + patchCode, false);
//						break;
					} else {
						if (minErrorTestAfterFix == 0 || errorTestAfterFix <= minErrorTestAfterFix) {
							minErrorTestAfterFix = errorTestAfterFix;
							if (fixedStatus != 1) fixedStatus = 2;
							log.info("Partially Succeeded to fix the bug " + buggyProject + "====================");
							FileHelper.outputToFile( type + "/PartialFixedBugs/" + buggyProject + "/Patch_" + methodName + "_" + startPos + "_" + i + "_" + index + ".txt", 
									"//**********************************************************\n//  " + signature + 
									"\n//**********************************************************\n===Buggy Code===\n" + suspiciousStmtCode + 
									"\n===Patch Code===\n" + similarMethodInfo + "\n" + patchCode, false);
						}
					}
				} else {
					System.err.println("Failed Tests after fixing: " + errorTestAfterFix + " " + buggyProject + " " + i);
				}
			}
		}
		if (!"".equals(TestUtils.recoverWithGitCmd(fullBuggyProjectPath))) {
			FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
		}
	    FileHelper.outputToFile(targetClassFile, FileHelper.readFile(classBackup), false);
	}

	private List<String> readSimilarMethods(int index, List<String> methodInfoList, String type) throws IOException {
		String methodsFile = matchedSimilarMethodsPath + "Signature_" + index + "/" + type + "/Methods.txt";
		List<String> similarMethods = new ArrayList<>();
		
		if (!new File(methodsFile).exists()) return similarMethods;
		
		FileInputStream fis = new FileInputStream(methodsFile);
		Scanner scanner = new Scanner(fis);
		StringBuilder singleMethod = new StringBuilder();
		boolean isMethodBody = false;
		
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if ("#METHOD_BODY#========================".equals(line)) {
				if (isMethodBody) similarMethods.add(singleMethod.toString());
				singleMethod.setLength(0);
				isMethodBody = false;
			} else {
				if (isMethodBody) {
					singleMethod.append(line).append("\n");
				}
				else {
					isMethodBody = true;
					methodInfoList.add(line);
				}
			}
		}
		if (singleMethod.length() > 0) {
			similarMethods.add(singleMethod.toString());
		}
		scanner.close();
		fis.close();
		
		return similarMethods;
	}

	private List<SuspiciousCode> readSuspiciousCodeFromFile(String metric, String path, String buggyProject, DataPreparer dp) {
		File suspiciousFile = null;
		if (this.suspiciousFile == null) {
			suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + FileUtils.getMD5(StringUtils.join(dp.testCases, "")
					 					+ dp.classPath + dp.testClassPath + dp.srcPath + dp.testSrcPath + metric) + ".sps");
		} else {
			suspiciousFile = this.suspiciousFile;
		}
		if (!suspiciousFile.exists()) return null;
		List<SuspiciousCode> suspiciousCodeList = new ArrayList<>();
		List<?> results = null;
		try {
            ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(suspiciousFile));
            Object object = objectInputStream.readObject();
            if (object instanceof List<?>) {
            	results = (List<?>) object;
            }
            if (results != null) {
            	for (Object result : results) {
                	if (result instanceof SuspiciousCode) {
                		suspiciousCodeList.add((SuspiciousCode) result);
                	}
                }
            }
            objectInputStream.close();
        }catch (Exception e){
            System.out.println("Reloading Localization Result...");
            return null;
        }
		if (suspiciousCodeList.isEmpty()) return null;
		return suspiciousCodeList;
	}
	
	private void addCodeToFile(File file, String patchMethodCode, int startPos, int endPos) {
		File newFile = new File(file.getAbsolutePath() + ".temp");
        String javaCode = FileHelper.readFile(file);
        String patchCode = javaCode.substring(0, startPos) + patchMethodCode + javaCode.substring(endPos);
        FileHelper.outputToFile(newFile, patchCode, false);
        if (file.delete()){
            newFile.renameTo(file);
        }
	}
	
	private String addCodeToFile(File file, String patchStatementCode, int startPos, int endPos, String suspiciousStmtCode, ITree suspStmtTree) {
		String patch = "";
		File newFile = new File(file.getAbsolutePath() + ".temp");
        String content = FileHelper.readFile(file);
        
        if (patchStatementCode.endsWith("{")) {
        	if (suspStmtTree.getType() == 60) {// VariableDeclarationStatement.
        		List<String> variables = new ArrayList<>();
        		boolean isFollowingStmt = false;
        		List<ITree> stmts = suspStmtTree.getParent().getChildren();
        		ITree lastStmt = null;
        		for (ITree stmt : stmts) {
        			if (isFollowingStmt) {
        				if (isCorelatedStmt(stmt, variables, stmt.getType(), null, null, null)) {
        					lastStmt = stmt;
        				} else {
        					break;
        				}
        			} else {
        				if (stmt == suspStmtTree) {
        					isFollowingStmt = true;
        					for (ITree childExp : suspStmtTree.getChildren()) {
        						if (childExp.getType() == 42) {
        							variables.add(childExp.getLabel());
        							break;
        						}
        					}
        				}
        			}
        		}
        		if (lastStmt != null) {
        			endPos = lastStmt.getPos() + lastStmt.getLength();
        		}
        	} else {
        		patch = patchStatementCode + "\n" + FileHelper.readFile(file).substring(startPos, endPos) + "}";
        	}
        } else if (patchStatementCode.endsWith("=TYPE=")) { // VariableDeclarationStatement.
        	patch = patchStatementCode.substring(0, patchStatementCode.length() - 6);
        	int index = patch.lastIndexOf("=");
        	String oldType = patch.substring(index + 1);
        	patch = patch.substring(0, index) + "\n";
        	index = patch.lastIndexOf("=");
        	String newType = patch.substring(index + 1);
        	patch = patch.substring(0, index) + "\n";
        	List<String> variables = new ArrayList<>();
        	for (ITree child : suspStmtTree.getChildren()) {
        		if (child.getType() == 59) {//VariableDeclarationFragment
        			variables.add(child.getChildren().get(0).getLabel());
        			break;
        		}
        	}
    		List<ITree> stmts = suspStmtTree.getParent().getChildren();
    		ITree lastStmt = null;
    		boolean isFollowingStmt = false;
    		List<Integer> positionsList = new ArrayList<>();
    		for (int i = 0, size = stmts.size(); i < size; i ++) {
    			ITree stmt = stmts.get(i);
    			if (isFollowingStmt) {
    				List<Integer> posList = new ArrayList<>();
    				identifySameTypes(stmt, oldType, variables, (i == size - 1 ? null : stmts.subList(i + 1, size)), posList);
        			if (posList.size() > 0) {
        				lastStmt = stmt;
        				positionsList.addAll(posList);
        			}
    			} else if (stmt == suspStmtTree) {
    				isFollowingStmt = true;
    			}
    			
    		}
    		if (lastStmt != null) {
//    			positionsList = positionsList.stream().distinct().collect(Collectors.toList());// JDK 1.8
    			positionsList = distinctPositions(positionsList);
    			ListSorter<Integer> sorter = new ListSorter<Integer>(positionsList);
    			positionsList = sorter.sortAscending();
    			int s = positionsList.size();
    			for (int i = 0; i < s; i ++) {
    				int prevPos = i == 0 ? endPos : (positionsList.get(i - 1) + oldType.length());
    				int currPos = positionsList.get(i);
    				patch += content.substring(prevPos, currPos) + newType;
				}
    			int prevPos = positionsList.get(s - 1) + oldType.length();
    			endPos = lastStmt.getPos() + lastStmt.getLength();
    			patch += content.substring(prevPos, endPos);
    		}
        } else {
        	patch = patchStatementCode;
        }
        
        String patchFile = content.substring(0, startPos) + patch + content.substring(endPos);
        FileHelper.outputToFile(newFile, patchFile, false);
        if (file.delete()){
            newFile.renameTo(file);
        }
        return patch;
	}

	private List<Integer> distinctPositions(List<Integer> positionsList) {
		Set<Integer> set = new LinkedHashSet<Integer>();
		for (int i = 0, size = positionsList.size(); i < size; i ++) {
			set.add(positionsList.get(i));
		}
		return new ArrayList<Integer>(set);
	}

	private void identifySameTypes(ITree stmt, String oldType, List<String> variables, List<ITree> peerStmts, List<Integer> posList) {
		if (stmt.getType() == 60) {
			ITree dataType = null;
			String variable = null;
			List<ITree> children = stmt.getChildren();
			for (ITree child : children) {
				if (child.getType() != 83) {// non-modifier
					if (child.getType() == 59) {//VariableDeclarationFragment
						variable = child.getChildren().get(0).getLabel();
						break;
					} else {
						dataType = child;
					}
				}
			}
			if (dataType != null && dataType.getLabel().equals(oldType)) {
				if (isCorelatedStmt(stmt, variables, 60, peerStmts, posList, oldType)) {
					posList.add(dataType.getPos());
					variables.add(variable);
				}
			}
		} else if (Checker.withBlockStatement(stmt.getType())) {
			List<ITree> children = stmt.getChildren();
			for (int index = 0, size = children.size(); index < size; index ++) {
				ITree child = children.get(index);
				if (Checker.isStatement(child.getType())) {
//					posList.addAll(identifySameTypes(child, oldType, variables, (index == size - 1 ? null : children.subList(index + 1, size)), posList));
					identifySameTypes(child, oldType, variables, (index == size - 1 ? null : children.subList(index + 1, size)), posList);
				}
			}
		}
//		return posList;
	}

	private boolean isCorelatedStmt(ITree stmt, List<String> variables, int stmtType, List<ITree> peerStmts, List<Integer> posList, String oldType) {
		List<ITree> children = stmt.getChildren();
		boolean isCorelatedStmt = false;
		for (int index = 0, size = children.size(); index < size; index ++) {
			ITree child = children.get(index);
			// variables in stmt are int variable list.
			int type = child.getType();
			if (type == 42) {
				String variable = child.getLabel();
				if (variables.contains(variable)) {
					isCorelatedStmt = true;
				} else if (stmtType == 60) {// VariableDeclarationStatement
					variables.add(variable);
				}
			} else if (Checker.isComplexExpression(type)) {
				isCorelatedStmt = isCorelatedStmt(child, variables, stmtType, null, posList, oldType);
				if (isCorelatedStmt) return isCorelatedStmt;
			} else if (Checker.isStatement(child.getType())) {
//				posList.addAll(identifySameTypes(child, oldType, variables, (index == size - 1 ? null : children.subList(index + 1, size)), posList));
				identifySameTypes(child, oldType, variables, (index == size - 1 ? null : children.subList(index + 1, size)), posList);
			}
		}
		if (peerStmts != null) {
			for (ITree peerStmt : peerStmts) {
				isCorelatedStmt = isCorelatedStmt(peerStmt, variables, stmtType, null, posList, oldType);
				if (isCorelatedStmt) return isCorelatedStmt;
			}
		}
		return isCorelatedStmt;
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

}
