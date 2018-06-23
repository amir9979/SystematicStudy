package edu.lu.uni.serval.search.fixer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.lu.uni.serval.config.Configuration;

public class Main4 {

	public static void main(String[] args) throws IOException {
		String buggyProjectsPath = "/Users/kui.liu/Public/Defects4JData/";//
		String matchedSimilarMethodsPath = "../OUTPUT/LocalizedBugs/";//
		String defects4jPath = "/Users/kui.liu/Public/git/defects4j/";// 
		
		//Read the bugIds which have been matched with similar methods.
		String bugSignaturesFile = matchedSimilarMethodsPath + "BugSignatures.txt";
		Map<String, Map<String, Integer>> locliazedBugSignatures = null;
		try {
			locliazedBugSignatures = readBugSignatures(bugSignaturesFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (locliazedBugSignatures == null) return;
		
		String suspiciousFileStr = "logs/suspicious.log";
		Map<String, String> suspiciousFileNames = readSuspiciousFileNames(suspiciousFileStr);
		
		/** Old version data.
		 * Chart_18, two buggy files.
		 * Math-15, too long time.
		 * EMSWN: failed:M_106
		 */
		// java -cp "target/dependency/*" -Xmx1g edu.lu.uni.serval.search.fixer.Main4 Math_16 EMSWN
		/*
		 */
		String buggyProjectName = "Lang_24";//args[0];//
		String dataType = "EMS";//args[1];//
		String[] elements = buggyProjectName.split("_");
		String projectName = elements[0];
		int bugId;
		try {
			bugId = Integer.valueOf(elements[1]);
		} catch (NumberFormatException e) {
			return;
		}
		
		Map<String, Integer> bugSignatures = locliazedBugSignatures.get(projectName + "_" + bugId);
		if (bugSignatures == null) {
			System.out.println("Failed to localized this Bug: " + buggyProjectName);
			return;
		}
		
		String suspiciousFileName = suspiciousFileNames.get(buggyProjectName);
		
		MainFixer fixer = new MainFixer(buggyProjectsPath, projectName, bugId, defects4jPath);
//		for (String metric : Configuration.METRICS) {
////			if (!"null".equals(metric)) continue;
//			fixer.metric = metric;
//			try {
//				fixer.fixProcess();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
		if (fixer.minErrorTest == 2147483647) {
			System.out.println("Failed to defects4j compile bug " + buggyProjectName);
			return;
		}
		
		fixer.metric = "All";
		fixer.bugSignatures = bugSignatures;
		fixer.matchedSimilarMethodsPath = matchedSimilarMethodsPath;
		File suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + suspiciousFileName);
		fixer.suspiciousFile = suspiciousFile;
		fixer.dataType = dataType;
		try {
			fixer.fixProcess();
		} catch (IOException e) {
			e.printStackTrace();
		}
		int fixedStatus = fixer.fixedStatus;
		switch (fixedStatus) {
		case 0:
			System.out.println("Failed to fix bug " + buggyProjectName);
			break;
		case 1:
			System.out.println("Succeeded to fix bug " + buggyProjectName + " with EMS similar method.");
			break;
		case 2:
			System.out.println("Partial succeeded to fix bug " + buggyProjectName + " with EMS similar method.");
			break;
		case 3:
			System.out.println("Successed to fix bug " + buggyProjectName + " with EMSWN similar method.");
			break;
		case 4:
			System.out.println("Partial successed to fix bug " + buggyProjectName + " with EMSWN similar method.");
			break;
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

	private static Map<String, Map<String, Integer>> readBugSignatures(String bugSignaturesFile) throws IOException {
		Map<String, Map<String, Integer>> locliazedBugSignatures = new HashMap<>();
		FileReader fileReader = new FileReader(bugSignaturesFile);
		BufferedReader reader = new BufferedReader(fileReader);
		String line = null;
		int index = -1;
		while ((line = reader.readLine()) != null) {
			index ++;
			String bugId = line.substring(0, line.indexOf("#"));
			Map<String, Integer> signatures = locliazedBugSignatures.get(bugId);
			if (signatures == null) {
				signatures = new HashMap<>();
				locliazedBugSignatures.put(bugId, signatures);
			}
			signatures.put(line, index);
		}
		reader.close();
		fileReader.close();
		return locliazedBugSignatures;
	}
}
