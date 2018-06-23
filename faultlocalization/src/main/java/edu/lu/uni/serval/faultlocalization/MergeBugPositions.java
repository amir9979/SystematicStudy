package edu.lu.uni.serval.faultlocalization;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.lu.uni.serval.faultlocalization.utils.PathUtils;
import edu.lu.uni.serval.test.PatchDiff;
import edu.lu.uni.serval.utils.FileHelper;

public class MergeBugPositions {

	public static void main(String[] args) throws IOException {
		String[] metrics = {"Ample", "Anderberg", "Dice", "Gp13", "Hamming", "Jaccard", "Kulczynski1", "M1",
				"Naish1", "Naish2", "Ochiai", "Qe", "SorensenDice", "Wong1", "Zoltar", "Tarantula", "NoMetric"};
		String path = "/work/users/kliu/Defects4JData/";
		String defects4jRootPath = "../../defects4j/";
		String outputPath = "bugPositions/";
		merge(metrics, path, defects4jRootPath, outputPath);
	}
	
	public static void merge(String[] metrics, String buggyProjectsPath, String defects4jRootPath, String outputPath) throws IOException {
		File[] buggyProjects = new File(buggyProjectsPath).listFiles();// Get all project names.
		for (File buggyProject : buggyProjects) {
			if (buggyProject.isDirectory()) {
				String projectName = buggyProject.getName();
				if (projectName.equals("Mockito_20") || projectName.equals("Mockito_19") || projectName.equals("Mockito_18")
						 || projectName.equals("Mockito_1") || projectName.equals("Mockito_3"))
					continue;
//				String zoltar = "bugPositions/Zoltar/" + project.getName() + ".txt";
//				String noMetric = "bugPositions/NoMetric/" + project.getName() + ".txt";
//				String zoltarBugs = FileHelper.readFile(zoltar);
//				String noMetricBugs = FileHelper.readFile(noMetric);
//				if (!zoltarBugs.equals(noMetricBugs)) {
//					System.out.println(project.getName());
//				}
				List<String> allSuspiciousPositions = new ArrayList<>();
				for (String metricStr : metrics) {
					String bugPositionFile = outputPath + metricStr + "/" + projectName + ".txt";
					List<String> bugPositionsList = readBugPositions(bugPositionFile);
					bugPositionsList.removeAll(allSuspiciousPositions);
					allSuspiciousPositions.addAll(bugPositionsList);
				}
				
				StringBuilder builder = new StringBuilder();
				for (String s : allSuspiciousPositions) {
					builder.append(s).append("\n");
				}
				FileHelper.outputToFile(outputPath + "All/" + projectName + ".txt", builder, false);
			}
		}
		
		
		// Match suspicious position with known bug fixes.
		Map<String, List<PatchDiff>> patchMap = new HashMap<>();
		Map<String, Map<String, List<PatchDiff>>> patchMap2 = new HashMap<>();
		TestFL.parshPatches(defects4jRootPath, patchMap, patchMap2);
		
		List<String> metrics2 = new ArrayList<>();
		metrics2.addAll(Arrays.asList(metrics));
		metrics2.add("All");
		for (String metricStr : metrics2) {
			// BugId, FileName, Ranking.
			Map<String, Map<String, List<Integer>>> buggyCodeRankingMaps = new HashMap<>(); // localized bug lines.
			Map<String, Map<String, List<Integer>>> buggyCodeRankingMaps2 = new HashMap<>();// localized bug hunks.
			Map<String, Map<String, List<Integer>>> buggyCodeRankingMaps3 = new HashMap<>();// localized with Fixed lines.
			// BugId, FileName, BugLines. 
			Map<String, Map<String, List<Integer>>> buggyCodeLinesMaps = new HashMap<>();
			Map<String, Map<String, List<Integer>>> buggyCodeLinesMaps2 = new HashMap<>();
			Map<String, Map<String, List<Integer>>> buggyCodeLinesMaps3 = new HashMap<>();
			
			List<String> bugIdsIdentifiedByBugLines = new ArrayList<>();
			List<String> bugIds2 = new ArrayList<>();
			
			for (File project : buggyProjects) {
				if (project.isDirectory()) {
					String projectName = project.getName();
					if (projectName.equals("Mockito_20") || projectName.equals("Mockito_19") || projectName.equals("Mockito_18")
							 || projectName.equals("Mockito_1") || projectName.equals("Mockito_3"))
						continue;
					
					String bugPositionFile = outputPath + metricStr + "/" + projectName + ".txt";
					List<String> bugPositionsList = readBugPositions(bugPositionFile);
					
					List<PatchDiff> pds = patchMap.get(projectName);
					Map<String, List<Integer>> suspCodeRankings = new HashMap<>();
					Map<String, List<Integer>> suspCodeLines = new HashMap<>();
					Map<String, List<Integer>> suspCodeRankings2 = new HashMap<>();
					Map<String, List<Integer>> suspCodeLines2 = new HashMap<>();
					Map<String, List<Integer>> suspCodeRankings3 = new HashMap<>();
					Map<String, List<Integer>> suspCodeLines3 = new HashMap<>();
					for (int index = 0, size = bugPositionsList.size(); index < size; index ++) {
						String candidate = bugPositionsList.get(index);// org.jfree.data.time.Day@@@250
						String[] info = candidate.split("@@@");
						String className = info[0];
						
						String filePath = buggyProjectsPath + projectName + PathUtils.getSrcPath(projectName) + className.replace(".", "/") + ".java";
						File file = new File(filePath);
						if (!file.exists()) {
							System.err.println(metricStr + "==" + candidate);
							continue;
						}
						
						
						int lineNumber = Integer.parseInt(info[1]);
						for (PatchDiff pd : pds) {
							String bugFileName = pd.fileName.replace("/", ".");
							bugFileName = bugFileName.substring(0, bugFileName.length() - 5);
							List<Integer> bugLines = pd.buggyLines;
							if (bugFileName.endsWith(className)) {
								if (bugLines.size() == 0) {// Pure inserting.
									int bugHunkStartLine = pd.buggyHunkStartLine;
									int bugHunkRange = pd.buggyHunkRange;
									if (bugHunkStartLine <= lineNumber && lineNumber <= bugHunkStartLine + bugHunkRange - 1) {
										// Coarsely Identified
										addToMap(suspCodeLines2, className, lineNumber);
										addToMap(suspCodeRankings2, className, index);
										if (metricStr.equals("All"))
											System.out.println("B#" + projectName + "#" + candidate);
										
										// little fine identified. -2, fixedLine, +2
										int bugLine = pd.buggyHunkStartLine + pd.fixedLines.get(0) - pd.fixedHunkStartLine;
										int bugStartLine = bugLine - 2;
										int bugEndLine = bugLine + 2;
										if (bugStartLine <= lineNumber && lineNumber <= bugEndLine) {
											addToMap(suspCodeLines3, className, lineNumber);
											addToMap(suspCodeRankings3, className, index);
											if (metricStr.equals("All"))
												System.out.println("C#" + projectName + "#" + candidate);
										}
										if (!bugIds2.contains(projectName)) bugIds2.add(projectName);
									}
								} else {
									if (bugLines.contains(lineNumber)) {
										if (metricStr.equals("All"))
											System.out.println("A#" + projectName + "#" + candidate);
										addToMap(suspCodeLines, className, lineNumber);
										addToMap(suspCodeRankings, className, index);
										if (!bugIdsIdentifiedByBugLines.contains(projectName)) bugIdsIdentifiedByBugLines.add(projectName);
									}
								}
							}
						}
					}
					if (suspCodeRankings.size() > 0) buggyCodeRankingMaps.put(projectName, suspCodeRankings);
					if (suspCodeLines.size() > 0) buggyCodeLinesMaps.put(projectName, suspCodeLines);
					if (suspCodeRankings2.size() > 0) buggyCodeRankingMaps2.put(projectName, suspCodeRankings2);
					if (suspCodeLines2.size() > 0) buggyCodeLinesMaps2.put(projectName, suspCodeLines2);
					if (suspCodeRankings3.size() > 0) buggyCodeRankingMaps3.put(projectName, suspCodeRankings3);
					if (suspCodeLines3.size() > 0) buggyCodeLinesMaps3.put(projectName, suspCodeLines3);
				}
			}
			
			System.out.println(metricStr);
			System.out.println("Matched bugs by bug lines: " + buggyCodeRankingMaps.size());// localized bug lines.
			System.out.println("Matched bugs by bug hunks: " + buggyCodeRankingMaps2.size());// localized bug hunks.
			System.out.println("Matched bugs by fixed lines: " + buggyCodeRankingMaps3.size());// localized with Fixed lines.
		}
	}

	private static void addToMap(Map<String, List<Integer>> suspCodeMap, String className, int num) {
		if (suspCodeMap.containsKey(className)) {
			suspCodeMap.get(className).add(num);
		} else {
			List<Integer> lines = new ArrayList<>();
			lines.add(num);
			suspCodeMap.put(className, lines);
		}
	}

	private static List<String> readBugPositions(String bugPositionFile) throws IOException {
		List<String> bugPositions = new ArrayList<>();
		FileReader fileReader = new FileReader(bugPositionFile);
		BufferedReader reader = new BufferedReader(fileReader);
		String line = null;
		while ((line = reader.readLine()) != null) {
			bugPositions.add(line);
		}
		reader.close();
		fileReader.close();
		return bugPositions;
	}

}
