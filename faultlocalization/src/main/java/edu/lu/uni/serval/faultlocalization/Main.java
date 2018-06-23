package edu.lu.uni.serval.faultlocalization;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import edu.lu.uni.serval.config.Configuration;
import edu.lu.uni.serval.faultlocalization.Metrics.Metric;
import edu.lu.uni.serval.faultlocalization.dataprepare.DataPreparer;
import edu.lu.uni.serval.faultlocalization.utils.FileUtils;
import edu.lu.uni.serval.faultlocalization.utils.PathUtils;
import edu.lu.uni.serval.utils.FileHelper;

public class Main {
	
	public static void main(String[] args) throws IOException {
		String outputPath = "bugPositions/";
		FileHelper.deleteDirectory(outputPath);
		String path = "/work/users/kliu/Defects4JDataBackup/";
		File[] projects = new File(path).listFiles();
		for (File project : projects) {
			if (project.isDirectory()) {
				String projectName = project.getName();
//				if (projectName.equals("Mockito_20") || projectName.equals("Mockito_19") || projectName.equals("Mockito_18")
//						 || projectName.equals("Mockito_1") || projectName.equals("Mockito_3"))
//					continue;
				Main test = new Main();
				test.locateSuspiciousCode(path, projectName, outputPath);
			}
		}
		
//		// merge suspicious positions.
//		String defects4jRootPath = "../defects4j/";
//		MergeBugPositions.merge(Configuration.METRICS, path, defects4jRootPath, outputPath);
	}

	private List<SuspiciousCode> allCandidates = new ArrayList<>();
	
	public void locateSuspiciousCode(String path, String buggyProject, String outputPath) throws IOException {
		
		if (!buggyProject.contains("_")) {
			System.out.println("Main: cannot recognize project name \"" + buggyProject + "\"");
			return;
		}

		String[] elements = buggyProject.split("_");
		try {
			Integer.valueOf(elements[1]);
		} catch (NumberFormatException e) {
			System.out.println("Main: cannot recognize project name \"" + buggyProject + "\"");
			return;
		}

		System.out.println(buggyProject);
		
		DataPreparer dp = new DataPreparer(path);
		dp.prepareData(buggyProject);
		if (!dp.validPaths) return;

		GZoltarFaultLoclaization gzfl = new GZoltarFaultLoclaization();
		gzfl.threshold = 0.0;
		gzfl.maxSuspCandidates = -1;
		gzfl.srcPath = path + buggyProject + PathUtils.getSrcPath(buggyProject).get(2);
		gzfl.localizeSuspiciousCodeWithGZoltar(dp.classPaths, checkNotNull(Arrays.asList("")), dp.testCases);
		
		for (String metricStr : Configuration.METRICS) {
			Metric metric = new Metrics().generateMetric(metricStr);
			System.out.println(metricStr);
			gzfl.sortSuspiciousCode(metric);
			List<SuspiciousCode> candidates = new ArrayList<SuspiciousCode>(gzfl.candidates.size());
			int num = gzfl.candidates.size();
			if (num > 0) {
				for (int i = 0; i < num; i ++) {
					
				}
			}
			candidates.addAll(gzfl.candidates);
			
			File suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + FileUtils.getMD5(StringUtils.join(dp.testCases, "")
					+ dp.classPath + dp.testClassPath + dp.srcPath + dp.testSrcPath + metric) + ".sps");
			File parentFile = suspiciousFile.getParentFile();
			if (!parentFile.exists()) parentFile.mkdirs();
			suspiciousFile.createNewFile();
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(suspiciousFile));
            objectOutputStream.writeObject(candidates);
            objectOutputStream.close();
            
			StringBuilder builder = new StringBuilder();
			for (int index = 0, size = candidates.size(); index < size; index ++) {
				SuspiciousCode candidate = candidates.get(index);
				String className = candidate.getClassName();
				int lineNumber = candidate.lineNumber;
				builder.append(className).append("@@@").append(lineNumber).append("\n");
				
				// Merge Candidates.
				int indexC = allCandidates.indexOf(candidate);
				if (indexC == -1) {
					allCandidates.add(candidate);
				} else {
					SuspiciousCode sc = allCandidates.get(indexC);
					if (sc.getSuspiciousValue() < candidate.getSuspiciousValue()) {
						sc.setSusp(candidate.getSuspiciousValue());
						sc.setTests(candidate.getTests());
						sc.setFailedTests(candidate.getFailedTests());
					}
				}
			}
			FileHelper.outputToFile(outputPath + buggyProject + "/" + metricStr + ".txt", builder, false);
			gzfl.candidates = new ArrayList<SuspiciousCode>();
		}
		
		Collections.sort(allCandidates, new Comparator<SuspiciousCode>() {
			@Override
			public int compare(SuspiciousCode o1, SuspiciousCode o2) {
				// reversed parameters because we want a descending order list
                if (o2.getSuspiciousValue() == o1.getSuspiciousValue()){
                	int compareName = o2.getClassName().compareTo(o1.getClassName());
                	if (compareName == 0) {
                		return Integer.compare(o2.getLineNumber(),o1.getLineNumber());
                	}
                    return compareName;
                }
                return Double.compare(o2.getSuspiciousValue(), o1.getSuspiciousValue());
			}
		});
		
		List<SuspiciousCode> allCandidates = new ArrayList<SuspiciousCode>(this.allCandidates.size());
		allCandidates.addAll(this.allCandidates);
		File suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + FileUtils.getMD5(StringUtils.join(dp.testCases, "")
				+ dp.classPath + dp.testClassPath + dp.srcPath + dp.testSrcPath + "All") + ".sps");
		File parentFile = suspiciousFile.getParentFile();
		if (!parentFile.exists()) parentFile.mkdirs();
		suspiciousFile.createNewFile();
		ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(suspiciousFile));
        objectOutputStream.writeObject(allCandidates);
        objectOutputStream.close();
        
        StringBuilder builder = new StringBuilder();
		for (int index = 0, size = allCandidates.size(); index < size; index ++) {
			SuspiciousCode candidate = allCandidates.get(index);
			String className = candidate.getClassName();
			int lineNumber = candidate.lineNumber;
			builder.append(className).append("@@@").append(lineNumber).append("\n");
		}
		FileHelper.outputToFile(outputPath + buggyProject + "/All.txt", builder, false);
	}

}
