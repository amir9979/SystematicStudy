package edu.lu.uni.serval.search.syntactic.fixer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main2 {

	public static void main(String[] args) throws IOException {
		String buggyProjectsPath = args[0];// "/Users/kui.liu/Public/Defects4JData/";//
		String bugInfoPath = args[1];// "../OUTPUT/LocalizedBugs/";//
		String defects4jPath = args[2];// "/Users/kui.liu/Public/git/defects4j/";// 
		
		//Read the bugIds which have been matched with similar methods.
		String bugSignaturesFile = bugInfoPath + "BugSignatures.txt";
		Map<String, Map<String, Integer>> localizedBugSignatures = null;
		try {
			localizedBugSignatures = readBugSignatures(bugSignaturesFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (localizedBugSignatures == null) return;
		
		String buggyProjectName = args[3];// "Chart_1";
		String dataType = args[4];// "Facoy";// SyntacticLD, SyntacticDL.
		
		String[] elements = buggyProjectName.split("_");
		String projectName = elements[0];
		int bugId;
		try {
			bugId = Integer.valueOf(elements[1]);
		} catch (NumberFormatException e) {
			System.out.println("Wrong Project name: " + buggyProjectName);
			return;
		}
		
		Map<String, Integer> bugSignatures = localizedBugSignatures.get(projectName + "_" + bugId);
		if (bugSignatures == null) {
			System.out.println("Failed to localized this Bug: " + buggyProjectName);
			return;
		}
		
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
		fixer.metric = "All";
		fixer.bugSignatures = bugSignatures;
		fixer.matchedSimilarMethodsPath = bugInfoPath;
		fixer.dataType = dataType;
//		File suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + suspiciousFileName);
//		fixer.suspiciousFile = suspiciousFile;
		if (fixer.minErrorTest == 2147483647) {
			System.out.println("Failed to defects4j compile bug " + buggyProjectName);
			return;
		}
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
			System.out.println("Succeeded to fix bug " + buggyProjectName + " with " + dataType + " similar method.");
			break;
		case 2:
			System.out.println("Partial succeeded to fix bug " + buggyProjectName + " with " + dataType + " similar method.");
			break;
		}
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
