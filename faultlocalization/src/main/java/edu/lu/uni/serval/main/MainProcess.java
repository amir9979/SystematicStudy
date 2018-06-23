package edu.lu.uni.serval.main;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.lu.uni.serval.JavaFileParser.JavaFileParser;
import edu.lu.uni.serval.JavaFileParser.TypeReader;
import edu.lu.uni.serval.config.Configuration;
import edu.lu.uni.serval.faultlocalization.GZoltarFaultLoclaization;
import edu.lu.uni.serval.faultlocalization.Metrics;
import edu.lu.uni.serval.faultlocalization.Metrics.Metric;
import edu.lu.uni.serval.faultlocalization.dataprepare.DataPreparer;
import edu.lu.uni.serval.faultlocalization.SuspiciousCode;
import edu.lu.uni.serval.faultlocalization.utils.FileUtils;
import edu.lu.uni.serval.faultlocalization.utils.JunitRunner;
import edu.lu.uni.serval.faultlocalization.utils.PathUtils;
import edu.lu.uni.serval.faultlocalization.utils.ShellUtils;
import edu.lu.uni.serval.faultlocalization.utils.TestUtils;
import edu.lu.uni.serval.method.Method;
import edu.lu.uni.serval.search.fixer.Patch;
import edu.lu.uni.serval.search.fixer.PatchGenerator;
import edu.lu.uni.serval.search.fixer.TimeLine;
import edu.lu.uni.serval.utils.FileHelper;

/**
 * 1. Localize bug.
 * 2. Search similar methods.
 * 3. Fix the bug with similar methods.
 * 
 * @author kui.liu
 *
 */
public class MainProcess {
	
	private static Logger log = LoggerFactory.getLogger(MainProcess.class);
	
	private TimeLine timeLine;
	
	public SearchSpace searchSpace = null;
	private List<SuspiciousCode> suspiciousCandidates;
	private String defects4jPath;
	private String buggyProjectsPath;
	private String buggyProject;
	private List<String> failedTestStrList = new ArrayList<>();
	public int minErrorTest = 0;
	private int minErrorTestAfterFix = 0;
	public boolean isOneByOne = true; // Fine one similar method, then test this one.
	public boolean withoutPriority = false;// Without the priority of EMS or EMSWN.

	public static void main(String[] args) {
		String buggyProjectsPath = args[0];//"/work/users/kliu/Defects4JData/";
		String defects4jPath = args[1];// "/work/users/kliu/SearchAPR/defects4j/";
		String buggyProject = args[2]; // Chart_1
		String searchPath = args[3];   // "/work/users/kliu/SearchAPR/data/existingMethods/";
		String metricStr = args[4];    // Ochiai
		boolean readSearchSpace = Boolean.valueOf(args[8]);
		
		MainProcess main = new MainProcess();
		main.isOneByOne = Boolean.valueOf(args[5]);
		main.withoutPriority = Boolean.valueOf(args[6]);
		int expire = Integer.valueOf(args[7]);
		main.fixProcess(buggyProjectsPath, defects4jPath, buggyProject, searchPath, metricStr, expire, readSearchSpace);
	}

	public void fixProcess(String buggyProjectsPath, String defects4jPath, String buggyProject, String searchPath, String metricStr, int expire, boolean readSearchSpace) {
		if (!new File(buggyProjectsPath).exists()) {
			log.error("Wrong buggy project parent path!!!");
			return;
		}
		if (!new File(defects4jPath).exists()) {
			log.error("Wrong Defects4J path!!!");
			return;
		}
		if (!new File(buggyProjectsPath + buggyProject).exists()) {
			log.error("Wrong buggy project path!!!");
			return;
		}
		if (!new File(searchPath).exists()) {
			log.error("Wrong search space path!!!");
			return;
		}
		
		// Build search space for suspicious methods.
		if (searchSpace == null) {
			try {
				log.info("Read search space...");
				File searchSpaceFile = new File(".SearchSpace/searchSpace.ss");
				if (searchSpaceFile.exists() && readSearchSpace) {
					ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(searchSpaceFile));
		            Object object = objectInputStream.readObject();
		            if (object instanceof SearchSpace) {
		            	searchSpace = (SearchSpace) object;
		            }
		            objectInputStream.close();
		            if (searchSpace == null) {
		            	log.error("Failed to build the search space!!!");
		    			return;
		            }
				} else {
					searchSpace = new SearchSpace(searchPath);
					if (readSearchSpace) {
						searchSpaceFile.getParentFile().mkdirs();
						searchSpaceFile.createNewFile();
						ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(searchSpaceFile));
						objectOutputStream.writeObject(searchSpace);
						objectOutputStream.close();
					}
				}
				log.info("Finilize reading search space...");
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
				log.error("Failed to build the search space!!!");
				return;
			}
		}
		
		
		this.defects4jPath = defects4jPath;
		this.buggyProjectsPath = buggyProjectsPath;
		this.buggyProject = buggyProject;
		// Prepare data.
		DataPreparer dp = new DataPreparer(buggyProjectsPath);
		dp.prepareData(buggyProject);
		if (!dp.validPaths) return;
		
		// Localize suspicious code.
		try {
			localizeSuspiciousCode(buggyProjectsPath, buggyProject, dp, metricStr);
		} catch (IOException e) {
			e.printStackTrace();
			log.error("Failed to localize suspicious code!!!");
			return;
		}
		
		if (this.suspiciousCandidates == null || this.suspiciousCandidates.isEmpty()) {
			log.error("Failed to localize suspicious code!!!");
			return;
		}
		
		minErrorTest = TestUtils.getFailTestNumInProject(buggyProjectsPath + buggyProject, defects4jPath, failedTestStrList);
		int a = 0;
		while (minErrorTest == 2147483647 && a < 10) {
			minErrorTest = TestUtils.getFailTestNumInProject(buggyProjectsPath + buggyProject, defects4jPath, failedTestStrList);
			a ++;
		}
		if (minErrorTest == 2147483647) {
			System.out.println("Failed to defects4j compile bug " + buggyProject);
			return;
		}
		log.info(buggyProject + " Failed Tests: " + this.minErrorTest);
		
		timeLine = new TimeLine(expire);
		// Try to fix the bug by modifying suspicious code.
		fixSuspiciousCode(dp, buggyProject);
		
	}

	private void localizeSuspiciousCode(String path, String buggyProject, DataPreparer dp, String metricStr) throws IOException {
		Metric metric = new Metrics().generateMetric(metricStr);
		if (metricStr.equals("null") || metricStr.equals("All")) {
			metric = null;
		} else if (metric == null) {
			log.error("Incorrect Fault-Localization-Metric name: " + metricStr);
			return;
		}
		
		File suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + FileUtils.getMD5(StringUtils.join(dp.testCases, "")
				+ dp.classPath + dp.testClassPath + dp.srcPath + dp.testSrcPath + (metricStr.equals("All") ? "All" : metric)) + ".sps");
		if (suspiciousFile.exists()) {
			log.info("Suspicious file: " + suspiciousFile);
			readSuspiciousCodeFromFile(suspiciousFile);
			return;
		}
		
		if (metricStr.equals("All")) return;

		GZoltarFaultLoclaization gzfl = new GZoltarFaultLoclaization();
		gzfl.threshold = 0.01;
		gzfl.maxSuspCandidates = 1000;
		gzfl.srcPath = path + buggyProject + PathUtils.getSrcPath(buggyProject).get(2);
		gzfl.localizeSuspiciousCodeWithGZoltar(dp.classPaths, checkNotNull(Arrays.asList("")), dp.testCases);
		
		gzfl.sortSuspiciousCode(metric);
		suspiciousCandidates = new ArrayList<SuspiciousCode>(gzfl.candidates.size());
		suspiciousCandidates.addAll(gzfl.candidates);
		
		File parentFile = suspiciousFile.getParentFile();
		if (!parentFile.exists()) parentFile.mkdirs();
		suspiciousFile.createNewFile();
		ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(suspiciousFile));
        objectOutputStream.writeObject(suspiciousCandidates);
        objectOutputStream.close();
	}

	private void readSuspiciousCodeFromFile(File suspiciousFile) {
		List<?> results = null;
		try {
			this.suspiciousCandidates = new ArrayList<SuspiciousCode>();
            ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(suspiciousFile));
            Object object = objectInputStream.readObject();
            if (object instanceof List<?>) {
            	results = (List<?>) object;
            }
            if (results != null) {
            	for (Object result : results) {
                	if (result instanceof SuspiciousCode) {
                		suspiciousCandidates.add((SuspiciousCode) result);
                	}
                }
            }
            objectInputStream.close();
        }catch (Exception e){
            System.out.println("Reloading Localization Result...");
        }
		
	}
	
	Map<String, List<Method>> triedMethods = new HashMap<>();

	private void fixSuspiciousCode(DataPreparer dp, String buggyProject) {
		int size = this.suspiciousCandidates.size();
		List<String> triedSuspiciousMethods = new ArrayList<>();
		List<SecondHandCandidates> scCandidates = new ArrayList<>();//EMSWN
		boolean fixed = false;
		for (int index = 0; index < size; index ++) {
			if (timeLine.isTimeout()) break;
			SuspiciousCode suspiciousCode = this.suspiciousCandidates.get(index);
			String suspiciousClassName = suspiciousCode.getClassName();
			int lineNumber = suspiciousCode.getLineNumber();
			
			String suspiciousJavaFile = suspiciousClassName.replace(".", "/") + ".java";
			String filePath = dp.srcPath + suspiciousJavaFile;
			
			// Check whether this method has been tried or not?
			List<Method> similarTriedMethods = triedMethods.get(suspiciousJavaFile);
			if (similarTriedMethods != null) {
				boolean tried = false;
				for (Method triedMethod : similarTriedMethods) {
					if (lineNumber < triedMethod.getStartLine()) {
						break;
					} else if (lineNumber <= triedMethod.getEndLine()) {
						tried = true;
						break;
					}
				}
				if (tried) continue;
			}
			
			// Read the information of suspicious method that contains the suspicious code.
			JavaFileParser jfp = new JavaFileParser();
			jfp.parseSuspiciousJavaFile(buggyProject, new File(filePath), lineNumber);
			List<Method> suspiciousMethods = jfp.getMethods();
			List<Method> suspiciousConstructors = jfp.getConstructors();
			if (suspiciousMethods.isEmpty() && suspiciousConstructors.isEmpty()) {
				log.error("Failed to read the buggy method of " + buggyProject + " " + suspiciousClassName + "  " + lineNumber);
				continue;
			}
			Method method = suspiciousMethods.isEmpty() ? suspiciousConstructors.get(0) : suspiciousMethods.get(0);
			String returnType = method.getReturnTypeString();
			String arguments = method.getArgumentsStr();
			String suspiciousMethodName = method.getName();
			returnType = TypeReader.canonicalType(returnType);//FIXME exact the same return type and argument types.
			String argumentTypes = TypeReader.readArgumentTypes(arguments);
			String keySignature = returnType + "#" + argumentTypes;
			String suspiciousMethodSignature = buggyProject + "#" + suspiciousJavaFile + "#" + suspiciousMethodName + "#" + keySignature;
			if (triedSuspiciousMethods.contains(suspiciousMethodSignature)) continue;
			triedSuspiciousMethods.add(suspiciousMethodSignature);
			method.setSignature(suspiciousMethodSignature);
			
			// Add this method to the set of tried methods.
			if (similarTriedMethods != null) {
				similarTriedMethods.add(method);
				Collections.sort(similarTriedMethods, new Comparator<Method>() {
					@Override
					public int compare(Method m1, Method m2) {
						return m1.getStartLine() < m2.getStartLine() ? -1 : 1;
					}
				});
			} else {
				similarTriedMethods = new ArrayList<>();
				similarTriedMethods.add(method);
			}
			this.triedMethods.put(suspiciousJavaFile, similarTriedMethods);
			
			// Search similar methods.
			String packageName = suspiciousClassName;
			List<String> buggyBodyCodeRawTokens = Arrays.asList(method.getBodyCodeRawTokens().split(" "));
			List<SearchSpaceMethod> subSearchSpace = this.searchSpace.searchSpace.get(returnType);
			if (subSearchSpace == null) continue;

			File targetJavaFile = new File(FileUtils.getFileAddressOfJava(dp.srcPath, suspiciousClassName));
	        File targetClassFile = new File(FileUtils.getFileAddressOfClass(dp.classPath, suspiciousClassName));
	        File javaBackup = new File(FileUtils.tempJavaPath(suspiciousClassName, "Fixer"));
	        File classBackup = new File(FileUtils.tempClassPath(suspiciousClassName, "Fixer"));
	        FileHelper.outputToFile(javaBackup, FileHelper.readFile(targetJavaFile), false);
	        if (targetClassFile.exists()) {
	        	FileHelper.outputToFile(classBackup, FileHelper.readFile(targetClassFile), false);
	        }
			String suspiciousMethodBodyCode = method.getBody();
//			int startLine = method.getStartLine();
//			int endLine = method.getEndLine();
	        int startPos = method.getStartPosition();
	        int endPos = method.getEndPosition();
			
	        List<SearchSpaceMethod> similarMethodsEMS = new ArrayList<>();
	        List<SearchSpaceMethod> similarMethodsEMSWN = new ArrayList<>();
	        int a = 0;
			for (int i = 0, s = subSearchSpace.size(); i < s; i ++) {
				SearchSpaceMethod ssMethod = subSearchSpace.get(i);
				if (keySignature.equals(ssMethod.signature)) {
					if (suspiciousMethodName.equals(ssMethod.methodName)) {// EMS: exact match with method name.
						String existingMethodInfo = ssMethod.info;
						String[] elements = existingMethodInfo.split(":");
						String packageName_ = elements[1] + "." + elements[2];
						if (packageName.equals(packageName_)) continue;
						a ++;
						int levenshteinDistance = new LevenshteinDistance().computeLevenshteinDistance(buggyBodyCodeRawTokens, ssMethod.rawTokens);
//						if (levenshteinDistance == 0) continue;
						ssMethod.levenshteinDistance = levenshteinDistance;
						
						// Fix suspicious method with this similar method.
						if (isOneByOne) {
							fixed = fixSuspiciousMethod(suspiciousMethodBodyCode, ssMethod, targetJavaFile,
									javaBackup, suspiciousClassName, startPos, endPos, targetClassFile, dp, 
									"EMS", suspiciousMethodName, suspiciousMethodSignature, ssMethod.info, i);
							if (fixed) {
								log.info("Search Space: " + s + " --- " + a);
								break;
							}
						} else {
							similarMethodsEMS.add(ssMethod);
						}
					} else { // EMSWN: exact match without method name.
						int levenshteinDistance = new LevenshteinDistance().computeLevenshteinDistance(buggyBodyCodeRawTokens, ssMethod.rawTokens);
//						if (levenshteinDistance == 0) continue;
						ssMethod.levenshteinDistance = levenshteinDistance;
						
						if (isOneByOne && withoutPriority) {
							fixed = fixSuspiciousMethod(suspiciousMethodBodyCode, ssMethod, targetJavaFile,
									javaBackup, suspiciousClassName, startPos, endPos, targetClassFile, dp, 
									"EMSWN", suspiciousMethodName, suspiciousMethodSignature, ssMethod.info, i);
							if (fixed) break;
						} else {
							similarMethodsEMSWN.add(ssMethod);
						}
					}
				}
			}
			
			if (!isOneByOne) {
				if (similarMethodsEMS.isEmpty()) continue;
				Collections.sort(similarMethodsEMS, new Comparator<SearchSpaceMethod>() {
					@Override
					public int compare(SearchSpaceMethod m1, SearchSpaceMethod m2) {
						return Integer.compare(m1.levenshteinDistance, m2.levenshteinDistance);
					}
				});
				for (int i = 0, s = similarMethodsEMS.size(); i < s; i ++) {
					if (timeLine.isTimeout()) break;
						SearchSpaceMethod similarMethod = similarMethodsEMS.get(i);
					fixed = fixSuspiciousMethod(suspiciousMethodBodyCode, similarMethod, targetJavaFile,
							javaBackup, suspiciousClassName, startPos, endPos, targetClassFile, dp, 
							"EMS", suspiciousMethodName, suspiciousMethodSignature, similarMethod.info, i);
					if (fixed) break;
				}
			}
			
			FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
			if (classBackup.exists()) {
				FileHelper.outputToFile(targetClassFile, FileHelper.readFile(classBackup), false);
			}
			if (fixed) break;
		    
			if (!similarMethodsEMSWN.isEmpty()) {
				SecondHandCandidates scc = new SecondHandCandidates();
				scc.suspiciousCode = suspiciousCode;
				scc.suspiciousMethod = method;
				scc.similarMethodsEMSWN = similarMethodsEMSWN;
				scCandidates.add(scc);
			}
		}
		
		if (!fixed && !withoutPriority) {//EMSWN
			for (SecondHandCandidates scc : scCandidates) {
				if (timeLine.isTimeout()) break;
				SuspiciousCode sc = scc.suspiciousCode;
				Method suspiciousMethod = scc.suspiciousMethod;
				List<SearchSpaceMethod> searchSpaceMethods = scc.similarMethodsEMSWN;
				if (searchSpaceMethods == null || searchSpaceMethods.isEmpty()) continue;
				Collections.sort(searchSpaceMethods, new Comparator<SearchSpaceMethod>() {
					@Override
					public int compare(SearchSpaceMethod m1, SearchSpaceMethod m2) {
						return Integer.compare(m1.levenshteinDistance, m2.levenshteinDistance);
					}
				});
				
				String suspiciousClassName = sc.getClassName();
				
				File targetJavaFile = new File(FileUtils.getFileAddressOfJava(dp.srcPath, suspiciousClassName));
		        File targetClassFile = new File(FileUtils.getFileAddressOfClass(dp.classPath, suspiciousClassName));
		        File javaBackup = new File(FileUtils.tempJavaPath(suspiciousClassName, "Fixer"));
		        File classBackup = new File(FileUtils.tempClassPath(suspiciousClassName, "Fixer"));
		        FileHelper.outputToFile(javaBackup, FileHelper.readFile(targetJavaFile), false);
		        if (targetClassFile.exists()) {
		        	FileHelper.outputToFile(classBackup, FileHelper.readFile(targetClassFile), false);
		        }
				String suspiciousMethodBodyCode = suspiciousMethod.getBody();
//				int startLine = suspiciousMethod.getStartLine();
//				int endLine = suspiciousMethod.getEndLine();
		        int startPos = suspiciousMethod.getStartPosition();
		        int endPos = suspiciousMethod.getEndPosition();
				
				for (int i = 0, s = searchSpaceMethods.size(); i < s; i ++) {
					if (timeLine.isTimeout()) break;
					SearchSpaceMethod ssMethod = searchSpaceMethods.get(i);
					fixed = fixSuspiciousMethod(suspiciousMethodBodyCode, ssMethod, targetJavaFile,
							javaBackup, suspiciousClassName, startPos, endPos, targetClassFile, dp, 
							"EMSWN", suspiciousMethod.getName(), suspiciousMethod.getSignature(), ssMethod.info, i);
					if (fixed) break;
				}
				
				FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
				if (classBackup.exists()) {
					FileHelper.outputToFile(targetClassFile, FileHelper.readFile(classBackup), false);
				}
				
				if (fixed) break;
			}
		}
	}

	private boolean fixSuspiciousMethod(String suspiciousMethodBodyCode, SearchSpaceMethod similarMethod, File targetJavaFile,
			File javaBackup, String suspiciousClassName, int startPos, int endPos, File targetClassFile, DataPreparer dp, String type,
			String methodName, String suspiciousMethodInfo, String similarMethodInfo, int index) {
		
		if (timeLine.isTimeout()) return false;
		
		FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
		Patch patch = new PatchGenerator().generatePatch(suspiciousMethodBodyCode, suspiciousClassName, similarMethod.bodyCode);
        addCodeToFile(targetJavaFile, patch.patchMethodCode, startPos, endPos);// Insert the patch.
        
        targetClassFile.delete();
        log.info("Compiling");
        int compileResult = TestUtils.compileProjectWithDefects4j(this.buggyProjectsPath + this.buggyProject, this.defects4jPath);
        if (compileResult == 1) {
          try {
				ShellUtils.shellRun(Arrays.asList("javac -Xlint:unchecked -source 1.8 -target 1.8 -cp "
						+ buildClasspath(Arrays.asList(PathUtils.getJunitPath()), dp.classPath, dp.testClassPath) + " -d " + dp.classPath + " "
						+ targetJavaFile.getAbsolutePath())); // Compile patched file.
          } catch (IOException e){
              System.err.println(buggyProject + " ---Fixer: fix fail because of javac exception! ");
              return false;
          }
        }
        log.info("Finish of compiling");
        if (!targetClassFile.exists()) { // fail to compile
            System.err.println(buggyProject + " ---Fixer: fix fail because of compile fail! ");
            return false;
        }
        
        List<String> failedTestsAfterFix = new ArrayList<>();
        log.info("======Begin to test patch======");
		int errorTestAfterFix = TestUtils.getFailTestNumInProject(this.buggyProjectsPath + this.buggyProject, this.defects4jPath, failedTestsAfterFix);
        log.info("======Finilize testing patch======");
		if (errorTestAfterFix < minErrorTest) {
			if (errorTestAfterFix == 0) {
				log.info("Succeeded to fix the bug " + buggyProject + "====================" + type);
				FileHelper.outputToFile(type + "/FixedBugs/" + buggyProject + "/buggyMethod_" + methodName + "_" + startPos + "_" + index + ".txt", 
						"//**********************************************************\n//  " + suspiciousMethodInfo + 
						"\n//**********************************************************\n" + suspiciousMethodBodyCode, false);
				FileHelper.outputToFile(type + "/FixedBugs/" + buggyProject + "/patchMethod_" + methodName + "_" + startPos + "_" + index + ".txt",
						"//**********************************************************\n//  " + similarMethodInfo + 
						"\n//**********************************************************\n" + patch.patchMethodCode, false);
				return true;
			} else {
				failedTestsAfterFix.removeAll(this.failedTestStrList);
				if (failedTestsAfterFix.size() > 0) {
					System.err.println(buggyProject + " ---Generated " + failedTestsAfterFix.size() + " new bugs.");
					return false;
				}
				if (minErrorTestAfterFix == 0 || errorTestAfterFix < minErrorTestAfterFix) {
					minErrorTestAfterFix = errorTestAfterFix;
					log.info("Partially Succeeded to fix the bug " + buggyProject + "====================" + type);
					FileHelper.outputToFile(type + "/PartialFixedBugs/" + buggyProject + "/buggyMethod_" + methodName + "_" + startPos + "_" + index + ".txt", 
							"//**********************************************************\n//  " + suspiciousMethodInfo + 
							"\n//**********************************************************\n" + suspiciousMethodBodyCode, false);
					FileHelper.outputToFile(type + "/PartialFixedBugs/" + buggyProject + "/patchMethod_" + methodName + "_" + startPos + "_" + index + ".txt",
							"//**********************************************************\n//  " + similarMethodInfo + 
							"\n//**********************************************************\n" + patch.patchMethodCode, false);
				}
			}
		}
		return false;
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
