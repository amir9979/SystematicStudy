package edu.lu.uni.serval.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.lu.uni.serval.JavaFileParser.JavaFileParser;
import edu.lu.uni.serval.config.Configuration;
import edu.lu.uni.serval.faultlocalization.SuspiciousCode;
import edu.lu.uni.serval.faultlocalization.dataprepare.DataPreparer;
import edu.lu.uni.serval.method.Method;

public class FLMetric {

	public static void main(String[] args) throws IOException {
		String buggyProject = "Chart_11";
//		String buggyProjectName = buggyProject.split("_")[0];
//		int bugId = Integer.valueOf(buggyProject.split("_")[1]);
		String[] metrics = {"Ochiai","Zoltar","Tarantula","Ample","Anderberg","Dice","Gp13","Hamming","Jaccard","Kulczynski1","M1","Naish1","Naish2","Qe","SorensenDice","Wong1","Null"};
		for (String metric : metrics) {
//			String metric = "All";
			
			
			String dataPath = "../../Defects4JData/";
			
			DataPreparer dp = new DataPreparer(dataPath);
			dp.prepareData(buggyProject);
			if (!dp.validPaths) return;
			
			String suspiciousFileStr = "logs/suspicious.log";
			Map<String, String> suspiciousFileNames = readSuspiciousFileNames(suspiciousFileStr);
			String suspiciousFileName = suspiciousFileNames.get(buggyProject);
			File suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + suspiciousFileName);
			
			Map<String, Map<String, Integer>> locliazedBugSignatures = read("../OUTPUT/LocalizedBugs/BugInfo.txt");
			Map<String, Integer> positions = locliazedBugSignatures.get(buggyProject);
			for (Map.Entry<String, Integer> entity : positions.entrySet()) {
				String filePath = dataPath + buggyProject + "/" + entity.getKey();
				JavaFileParser gtp = new JavaFileParser();
				gtp.parseSuspiciousJavaFile(buggyProject, new File(filePath), entity.getValue());
				List<Method> methods = gtp.getMethods();
				if (methods == null || methods.isEmpty()) {
//					System.err.println(buggyProject + "  " + suspiciousCodeStr);
					continue;
				}
				Method method = methods.get(0);//InnerClassMethod TODO
				int startLine = method.getStartLine();
				int endLine = method.getEndLine();
				
				// Read suspicious positions.
				List<SuspiciousCode> suspiciousCodeList = readSuspiciousCodeFromFile(metric, buggyProject, dp, suspiciousFile);
				
				for (int index = 0, size = suspiciousCodeList.size(); index < size; index ++) {
					SuspiciousCode suspiciousCode = suspiciousCodeList.get(index);
					String suspiciousClassName = suspiciousCode.getClassName().replace(".", "/") + ".java";
					int buggyLine = suspiciousCode.getLineNumber();
					 if (filePath.endsWith(suspiciousClassName)) {
						 if (startLine <= buggyLine && buggyLine <= endLine) {
							 System.out.println(buggyProject + "-" + metric + "-" + index);
							 break;
						 }
					 }
				}
			}
		}
	}

	private static Map<String, String> readSuspiciousFileNames(String suspiciousFileStr) throws IOException {
		Map<String, String> suspiciousFileNames = new HashMap<>();
		FileReader fileReader = new FileReader(suspiciousFileStr);
		BufferedReader reader = new BufferedReader(fileReader);
		String line = null;
		while ((line = reader.readLine()) != null) {
			String[] elements = line.split("@");
			suspiciousFileNames.put(elements[0], elements[1]);
		}
		reader.close();
		fileReader.close();
		return suspiciousFileNames;
	}

	private static Map<String, Map<String, Integer>> read(String bugSignaturesFile) throws IOException {
		Map<String, Map<String, Integer>> locliazedBugSignatures = new HashMap<>();
		FileReader fileReader = new FileReader(bugSignaturesFile);
		BufferedReader reader = new BufferedReader(fileReader);
		String line = null;
		while ((line = reader.readLine()) != null) {
			String[] elements = line.split("#");
			String bugId = elements[0];
			String buggyFile = elements[1];
			int buggyLine = Integer.valueOf(elements[2]);
			Map<String, Integer> signatures = locliazedBugSignatures.get(bugId);
			if (signatures == null) {
				signatures = new HashMap<>();
				locliazedBugSignatures.put(bugId, signatures);
			}
			signatures.put(buggyFile, buggyLine);
		}
		reader.close();
		fileReader.close();
		return locliazedBugSignatures;
	}

	private static List<SuspiciousCode> readSuspiciousCodeFromFile(String metric, String buggyProject, DataPreparer dp, File suspiciousFile) {
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


}
