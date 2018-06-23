package edu.lu.uni.serval.search.fixer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main3 {

	public static void main(String[] args) throws IOException {
		String buggyProjectsPath = args[0];// "/Users/kui.liu/Public/Defects4J/";//
		String matchedSimilarMethodsPath = args[1];// "../OUTPUT/LocalizedBugs/";//
		String defects4jPath = args[2];// "/Users/kui.liu/Public/git/defects4j/";// 
		
		//Read the bugIds which have been matched with similar methods.
		String bugSignaturesFile = matchedSimilarMethodsPath + "BugSignatures.txt";
		Map<String, Map<String, Integer>> locliazedBugSignatures = null;
		try {
			locliazedBugSignatures = readBugSignatures(bugSignaturesFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (locliazedBugSignatures == null) return;
		
//		String suspiciousFileStr = "logs/suspicious.log";
//		Map<String, String> suspiciousFileNames = readSuspiciousFileNames(suspiciousFileStr);
		String buggyProjectName = args[3];//"Math_96";//
		String dataType = args[4];//EMS or EMSWN
//		if ("EMSWN".equals(dataType)) {
//			if (buggyProjectName.equals("Chart_6") || buggyProjectName.equals("Chart_10") || buggyProjectName.equals("Chart_17") || buggyProjectName.equals("Chart_18")
//					|| buggyProjectName.equals("Math_15") || buggyProjectName.equals("Math_16") || buggyProjectName.equals("Math_19") || buggyProjectName.equals("Math_33")
//					|| buggyProjectName.equals("Math_36") || buggyProjectName.equals("Math_54") || buggyProjectName.equals("Math_60") || buggyProjectName.equals("Math_65")
//					|| buggyProjectName.equals("Math_77") || buggyProjectName.equals("Math_80") || buggyProjectName.equals("Math_81") || buggyProjectName.equals("Math_89") 
//					|| buggyProjectName.equals("Math_90") || buggyProjectName.equals("Math_93") || buggyProjectName.equals("Math_94") || buggyProjectName.equals("Math_95")
//					|| buggyProjectName.equals("Math_96") || buggyProjectName.equals("Math_97") || buggyProjectName.equals("Math_99") || buggyProjectName.equals("Math_103") 
//					|| buggyProjectName.equals("Math_105") 
//					|| buggyProjectName.equals("Closure_12") || buggyProjectName.equals("Closure_13") || buggyProjectName.equals("Closure_20") || buggyProjectName.equals("Closure_30")
//					|| buggyProjectName.equals("Closure_37") || buggyProjectName.equals("Closure_38") || buggyProjectName.equals("Closure_39")
//					|| buggyProjectName.equals("Closure_51") || buggyProjectName.equals("Closure_52") || buggyProjectName.equals("Closure_55") || buggyProjectName.equals("Closure_56")
//					|| buggyProjectName.equals("Closure_82") || buggyProjectName.equals("Closure_128") || buggyProjectName.equals("Closure_131") || buggyProjectName.equals("Closure_132") 
//					|| buggyProjectName.equals("Closure_133") || buggyProjectName.equals("Closure_45") || buggyProjectName.equals("Closure_9") || buggyProjectName.equals("Closure_68")
//					|| buggyProjectName.equals("Lang_24") || buggyProjectName.equals("Lang_40") || buggyProjectName.equals("Lang_41") || buggyProjectName.equals("Lang_46")
//					|| buggyProjectName.equals("Lang_51") || buggyProjectName.equals("Lang_55") || buggyProjectName.equals("Lang_60") || buggyProjectName.equals("Lang_61") 
//					|| buggyProjectName.equals("Lang_62") || buggyProjectName.equals("Lang_64")
//					|| buggyProjectName.equals("Time_15") || buggyProjectName.equals("Time_19") || buggyProjectName.equals("Time_23") || buggyProjectName.equals("Time_25")
//					|| buggyProjectName.equals("Mockito_8") || buggyProjectName.equals("Mockito_27")) return;
//		} else if ("EMS".equals(dataType)) {
//			if (buggyProjectName.equals("Lang_55") || buggyProjectName.equals("Lang_64")
//					|| buggyProjectName.equals("Closure_44")
//					|| buggyProjectName.equals("Math_96")
//					|| buggyProjectName.equals("Chart_6") || buggyProjectName.equals("Chart_17")) return;
//		}
		String[] elements = buggyProjectName.split("_");
		String projectName = elements[0];
		int bugId;
		try {
			bugId = Integer.valueOf(elements[1]);
		} catch (NumberFormatException e) {
			System.out.println("Wrong Project name: " + buggyProjectName);
			return;
		}
		Map<String, Integer> bugSignatures = locliazedBugSignatures.get(projectName + "_" + bugId);
		if (bugSignatures == null) {
			System.out.println("===Failed to localized this Bug: " + buggyProjectName);
			return;
		}
		
//		String suspiciousFileName = suspiciousFileNames.get(buggyProjectName);
		
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
		fixer.matchedSimilarMethodsPath = matchedSimilarMethodsPath;
		fixer.dataType = dataType;
//		File suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + suspiciousFileName);
//		fixer.suspiciousFile = suspiciousFile;
		if (fixer.minErrorTest == 2147483647) {
			System.out.println("Failed to defects4j compile bug " + buggyProjectName);
			return;
		}
//		fixer.index = Integer.valueOf(args[5]);
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

//	private static Map<String, String> readSuspiciousFileNames(String suspiciousFileStr) throws IOException {
//		Map<String, String> suspiciousFileNames = new HashMap<>();
//		FileReader fileReader = new FileReader(suspiciousFileStr);
//		BufferedReader reader = new BufferedReader(fileReader);
//		String line = null;
//		while ((line = reader.readLine()) != null) {
//			String[] elements = line.split("@");
//			suspiciousFileNames.put(elements[0], elements[1]);
//		}
//		reader.close();
//		fileReader.close();
//		return suspiciousFileNames;
//	}

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
