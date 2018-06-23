package edu.lu.uni.serval.search.fixer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

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
import edu.lu.uni.serval.method.Method;
import edu.lu.uni.serval.utils.FileHelper;

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
	
//	public int index = 0;
	
	public int fixedStatus = 0;
	
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

	public String dataType = "";

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
			
			JavaFileParser gtp = new JavaFileParser();
			gtp.parseSuspiciousJavaFile(buggyProject, new File(filePath), buggyLine);
			List<Method> methods = gtp.getMethods();
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
			if (triedSuspiciousSignaturesList.contains(signature)) continue;
			triedSuspiciousSignaturesList.add(signature);
			
			Integer index = this.bugSignatures.get(signature);
			if (index == null) continue;
			
			method.setSignature(signature);
			suspiciousMethods.put(suspiciousCode, method);
			
//			int startLine = method.getStartLine();
//			int endLine = method.getEndLine();
			int startPos = method.getStartPosition();
			int endPos = method.getEndPosition();
			String suspiciousMethodBodyCode = method.getBody();
			
//			String failedTest;
//			try {
//				failedTest = suspiciousCode.getFailedTests().get(0);
//				System.out.println("Failed Tests: " + suspiciousCode.getFailedTests().size());
//			} catch (Exception e) {
////				e.printStackTrace();
//				continue;
//			}
//			String[] f = failedTest.split("#");
//			String testClassName = f[0];
//			String testMethodName = f[1];
			
			Integer errAssertBeforeFix = 0;//errAssertBeforeFixMap.get(testClassName + "#" + testMethodName);
//			if (errAssertBeforeFix == null) {
//				Asserts asserts1 = new Asserts(dp.classPath, dp.srcPath, dp.testClassPath, dp.testSrcPath, testClassName, testMethodName, fullBuggyProjectPath);
//	            errAssertBeforeFix = asserts1.errorNum();
//	            errAssertBeforeFixMap.put(testClassName + "#" + testMethodName, errAssertBeforeFix);
//			}
            
			File targetJavaFile = new File(FileUtils.getFileAddressOfJava(dp.srcPath, suspiciousClassName));
	        File targetClassFile = new File(FileUtils.getFileAddressOfClass(dp.classPath, suspiciousClassName));
	        File javaBackup = new File(FileUtils.tempJavaPath(suspiciousClassName, "Fixer"));
	        File classBackup = new File(FileUtils.tempClassPath(suspiciousClassName, "Fixer"));
	        FileHelper.outputToFile(javaBackup, FileHelper.readFile(targetJavaFile), false);
	        FileHelper.outputToFile(classBackup, FileHelper.readFile(targetClassFile), false);
	        
			// Read matched similar methods with signature, and try to fix the bug with similar methods.
			String dataTypeStr = "";
			if ("EMS".endsWith(dataType)) {
				dataTypeStr = "ExactMatchedSignatures";
			} else if ("EMSWN".equals(dataType)) {
				dataTypeStr = "ExactMatchedSignaturesWithoutName";
			} else {
				return;
			}
			
			List<CandidateMethod> similarMethods = readSimilarMethods(index, dataTypeStr);
			System.out.println(signature + " " + dataType + ": " + similarMethods.size());
			
			if (similarMethods.size() > 0) {
				fixWithMatchedSimilarMethods(similarMethods, suspiciousMethodBodyCode, suspiciousClassName, javaBackup, classBackup, targetJavaFile,
						targetClassFile, startPos, endPos, dp,
						errAssertBeforeFix, methodName1, outputPath + dataType, signature);
			}
			if (minErrorTest == 0) break;
        }
		
//		if (minErrorTest > 0) {
//			fixProcess2(dp, suspiciousCodeList);
//		}
	}

	@SuppressWarnings("unused")
	private void fixProcess2(DataPreparer dp, List<SuspiciousCode> suspiciousCodeList) throws IOException {
		for (Map.Entry<SuspiciousCode, Method> entity : suspiciousMethods.entrySet()) {
			SuspiciousCode suspiciousCode = entity.getKey();
			String suspiciousClassName = suspiciousCode.getClassName();
			Method method = entity.getValue();
			String signature = method.getSignature();
			if (signature.endsWith("#void#null")) {
				System.out.println(signature);
				continue;
			}
			
			Integer index = this.bugSignatures.get(signature);
			if (index == null) continue;
			
			int startLine = method.getStartLine();
			int endLine = method.getEndLine();
			String suspiciousMethodBodyCode = method.getBody();
			
//			String failedTest = suspiciousCode.getFailedTests().get(0);
//			String[] f = failedTest.split("#");
//			String testClassName = f[0];
//			String testMethodName = f[1];
			
			Integer errAssertBeforeFix = 0;//errAssertBeforeFixMap.get(testClassName + "#" + testMethodName);
            
			File targetJavaFile = new File(FileUtils.getFileAddressOfJava(dp.srcPath, suspiciousClassName));
	        File targetClassFile = new File(FileUtils.getFileAddressOfClass(dp.classPath, suspiciousClassName));
//	        File javaBackup = FileUtils.copyFile(targetJavaFile.getAbsolutePath(), FileUtils.tempJavaPath(suspiciousClassName, "Fixer"));
//	        File classBackup = FileUtils.copyFile(targetClassFile.getAbsolutePath(), FileUtils.tempClassPath(suspiciousClassName, "Fixer"));
	        File javaBackup = new File(FileUtils.tempJavaPath(suspiciousClassName, "Fixer"));
	        File classBackup = new File(FileUtils.tempClassPath(suspiciousClassName, "Fixer"));
	        if (!javaBackup.exists()) {
	        	FileHelper.outputToFile(javaBackup, FileHelper.readFile(targetJavaFile), false);
	        }
	        if (!classBackup.exists()) {
	        	FileHelper.outputToFile(classBackup, FileHelper.readFile(targetClassFile), false);
	        }

			// Read matched similar methods with signature, and try to fix the bug with similar methods.
			List<CandidateMethod> similarMethods = readSimilarMethods(index, "ExactMatchedSignaturesWithoutName");
			System.out.println(signature + " EMSWN: " + similarMethods.size());

			if (similarMethods.size() > 0) {
				fixWithMatchedSimilarMethods(similarMethods, suspiciousMethodBodyCode, suspiciousClassName, javaBackup, classBackup, targetJavaFile,
						targetClassFile, startLine, endLine, dp,
						errAssertBeforeFix, method.getName(), outputPath + "EMSWN", signature);
			}
			if (minErrorTest == 0) break;
        }
	}

	private void fixWithMatchedSimilarMethods(List<CandidateMethod> similarMethods, String suspiciousMethodBodyCode, String suspiciousClassName, File javaBackup, File classBackup, File targetJavaFile,
			File targetClassFile, int startPos, int endPos, DataPreparer dp,
			int errAssertBeforeFix, String methodName, String type, String signature) {
		// Chart_1#org/jfree/chart/renderer/category/AbstractCategoryItemRenderer.java#getLegendItems#LegendItemCollection#null
		String[] elements = signature.split("#");
		String packageName = elements[1].replace("/", ".");
		packageName = packageName.substring(0, packageName.length() - 5);
		for (int i = 0, size = similarMethods.size(); i < size; i ++) {
			CandidateMethod candidateMethod = similarMethods.get(i);
			String similarMethodInfo = candidateMethod.methodInfo;
			if (this.dataType.equals("EMS")) {
				elements = similarMethodInfo.split(":");
				String packageName_ = elements[1] + "." + elements[2];
				if (packageName.equals(packageName_)) continue;
			}
//			if (timeLine.isTimeout()) {
//				System.out.println(buggyProject + " ---Fixer: fix fail because of time out! ");
//				break;
//			}
			FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
			String similarMethod = candidateMethod.methodCode;
			Patch patch = new PatchGenerator().generatePatch(suspiciousMethodBodyCode, suspiciousClassName, similarMethod);
            addCodeToFile(targetJavaFile, patch.patchMethodCode, startPos, endPos);// Insert the patch.
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
//            Asserts asserts = new Asserts(dp.classPath, dp.srcPath, dp.testClassPath, dp.testSrcPath, testClassName, testMethodName, fullBuggyProjectPath);
//            int errAssertNumAfterFix = asserts.errorNum();
//            if (errAssertNumAfterFix < errAssertBeforeFix) {
            List<String> failedTestsAfterFix = new ArrayList<>();
			int errorTestAfterFix = TestUtils.getFailTestNumInProject(fullBuggyProjectPath, this.defects4jPath, failedTestsAfterFix);
			if (errorTestAfterFix < minErrorTest) {
				failedTestsAfterFix.removeAll(this.failedTestStrList);
				if (failedTestsAfterFix.size() > 0) {
					System.err.println(buggyProject + " ---Generated new bugs: " + failedTestsAfterFix.size());
					continue;
				}
				// minErrorTest = errorTestAterFix;
				if (errorTestAfterFix == 0) {
					if (type.endsWith("EMS")) fixedStatus = 1;
					else fixedStatus = 3;
					minErrorTest = errorTestAfterFix;
//					System.out.println(dateFormat.format(new Date()));
					log.info("Succeeded to fix the bug " + buggyProject + "====================");
					FileHelper.outputToFile(type + "/FixedBugs/" + buggyProject + "/buggyMethod_" + methodName + "_" + startPos + "_" + i + ".txt", 
							"//**********************************************************\n//  " + signature + 
							"\n//**********************************************************\n" + suspiciousMethodBodyCode, false);
					FileHelper.outputToFile( type + "/FixedBugs/" + buggyProject + "/patchMethod_" + methodName + "_" + startPos + "_" + i + ".txt",
							"//**********************************************************\n//  " + similarMethodInfo + 
							"\n//**********************************************************\n" + patch.patchMethodCode, false);
					break;
				} else {
					if (minErrorTestAfterFix == 0 || errorTestAfterFix < minErrorTestAfterFix) {
						minErrorTestAfterFix = errorTestAfterFix;
						if (type.endsWith("EMS") && fixedStatus != 1) fixedStatus = 2;
						else if (type.endsWith("EMSWN") && fixedStatus != 3) fixedStatus = 4;
//						System.out.println(dateFormat.format(new Date()));
						log.info("Partially Succeeded to fix the bug " + buggyProject + "====================");
						FileHelper.outputToFile( type + "/PartialFixedBugs/" + buggyProject + "/buggyMethod_" + methodName + "_" + startPos + "_" + i + ".txt", 
								"//**********************************************************\n//  " + signature + 
								"\n//**********************************************************\n" + suspiciousMethodBodyCode, false);
						FileHelper.outputToFile( type + "/PartialFixedBugs/" + buggyProject + "/patchMethod_" + methodName + "_" + startPos + "_" + i + ".txt",
								"//**********************************************************\n//  " + similarMethodInfo + 
								"\n//**********************************************************\n" + patch.patchMethodCode, false);
					}
				}
			} else {
				System.err.println("Failed Tests after fixing: " + errorTestAfterFix + " " + buggyProject + " " + i);
			}
		}
		FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
	    FileHelper.outputToFile(targetClassFile, FileHelper.readFile(classBackup), false);
	}

	private List<CandidateMethod> readSimilarMethods(int index, String type) throws IOException {
		String methodsFile = matchedSimilarMethodsPath + "Signature_" + index + "/" + type + "/Methods.txt";
		List<CandidateMethod> similarMethods = new ArrayList<>();
		
		if (!new File(methodsFile).exists()) return similarMethods;
		
		FileInputStream fis = new FileInputStream(methodsFile);
		Scanner scanner = new Scanner(fis);
		StringBuilder singleMethod = new StringBuilder();
		String methodInfo = "";
		boolean isMethodBody = false;
		
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if ("#METHOD_BODY#========================".equals(line)) {
				if (isMethodBody) {
					CandidateMethod candidate = new CandidateMethod();
					candidate.methodInfo = methodInfo;
					candidate.methodCode = singleMethod.toString();
					similarMethods.add(candidate);
				}
				singleMethod.setLength(0);
				isMethodBody = false;
			} else {
				if (isMethodBody) {
					singleMethod.append(line).append("\n");
				}
				else {
					isMethodBody = true;
					methodInfo = line;
				}
			}
		}
		CandidateMethod candidate = new CandidateMethod();
		candidate.methodInfo = methodInfo;
		candidate.methodCode = singleMethod.toString();
		similarMethods.add(candidate);
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
//        FileOutputStream outputStream = null;
//        BufferedReader reader = null;
//        try {
//            if (!newFile.exists()) {
//                newFile.createNewFile();
//            }
//            outputStream = new FileOutputStream(newFile);
//            reader = new BufferedReader(new FileReader(file));
//            String lineString = null;
//            int line = 0;
//            while ((lineString = reader.readLine()) != null) {
//                line++;
//                if (startLine <= line && line <= endLine) {
//                	if (patchMethodCode != null) {
//                		outputStream.write(patchMethodCode.getBytes());
//                		patchMethodCode = null;
//                	}
//                } else {
//                	outputStream.write((lineString + "\n").getBytes());
//                }
//            }
//        } catch (IOException e){
//            e.printStackTrace();
//        } finally {
//        	 try {
//        		 if (outputStream != null){
//        			 outputStream.flush();
//                     outputStream.close();
//        			 outputStream = null;
//                 }
//        		 if (reader != null){
//        			 reader.close();
//        			 reader = null;
//                 }
//             } catch (IOException e){
//             }
//        }
        if (file.delete()){
            newFile.renameTo(file);
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

}
