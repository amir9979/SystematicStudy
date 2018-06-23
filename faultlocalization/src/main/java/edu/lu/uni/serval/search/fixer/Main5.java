package edu.lu.uni.serval.search.fixer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.lu.uni.serval.config.Configuration;

public class Main5 {

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
		/*
		 */
		String buggyProjectName = "Math_75";
		String dataType = "EMSWN";//
		/*

Math_75 = []
		 */
		Integer[] indexes = {0, 11, 14, 99, 102, 122, 123, 130, 131, 133, 151, 155, 157, 247, 275, 278, 287, 288, 322, 325, 362, 404, 425, 438, 439, 444, 448, 458, 574, 590, 591, 592, 593, 605, 625, 639, 681, 732, 747, 748, 749, 751, 752, 762, 772, 879, 881, 894, 918, 919, 929, 939, 942, 1036, 1043, 1046, 1065, 1096, 1147, 1211, 1224, 1228, 1229, 1243, 1250, 1253, 1256, 1270, 1355, 1360, 1367, 1368, 1377, 1379, 1421, 1461, 1537, 1538, 1568, 1607, 1653, 1722, 1869, 1887, 1906, 2041, 2093, 2105, 2119, 2195, 2199, 2202, 2217, 2237, 2260, 2302, 2336, 2342, 2360, 2362, 2365, 2370, 2404, 2421, 2432, 2434, 2435, 2456, 2460, 2480, 2511, 2585, 2591, 2594, 2595, 2596, 2600, 2605, 2625, 2632, 2637, 2650, 2659, 2671, 2683, 2684, 2706, 2712, 2718, 2734, 2735, 2744, 2756, 2762, 2767, 2769, 2772, 2774, 2776, 2784, 2786, 2787, 2821, 2822, 2827, 2828, 2830, 2831, 2832, 2844, 2862, 2867, 2869, 2879, 2891, 2892, 2893, 2917, 2924, 2926, 2929, 2931, 2932, 2936, 2946, 3001, 3004, 3019, 3025, 3031, 3037, 3048, 3051, 3159, 3273, 3326, 3440, 3441, 3451, 3466, 3470, 3476, 3482, 3625, 3632, 3691, 3700, 3715, 3792, 3844, 3847, 3855, 3896, 3898, 3903, 3905, 3915, 3918, 3920, 3930, 3936, 3943, 3947, 3949, 3953, 3965, 3975, 3998, 4011, 4024, 4025, 4042, 4044, 4045, 4057, 4079, 4081, 4083, 4088, 4116, 4128, 4144, 4145, 4160, 4167, 4195, 4203, 4223, 4239, 4244, 4245, 4249, 4256, 4257, 4265, 4266, 4267, 4268, 4269, 4273, 4274, 4447};
		List<Integer> indexList = new ArrayList<>();
		indexList.addAll(Arrays.asList(indexes));
		String[] elements = buggyProjectName.split("_");
		String projectName = elements[0];
		int bugId;
		try {
			bugId = Integer.valueOf(elements[1]);
		} catch (NumberFormatException e) {
			System.out.println();
			return;
		}
		
		Map<String, Integer> bugSignatures = locliazedBugSignatures.get(projectName + "_" + bugId);
		if (bugSignatures == null) {
			System.out.println("Failed to localized this Bug: " + buggyProjectName);
			return;
		}
		
		String suspiciousFileName = suspiciousFileNames.get(buggyProjectName);
		
		MainFixer2 fixer = new MainFixer2(buggyProjectsPath, projectName, bugId, defects4jPath);
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
		File suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + suspiciousFileName);
		fixer.suspiciousFile = suspiciousFile;
		fixer.dataType = dataType;
		fixer.indexList = indexList;
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
