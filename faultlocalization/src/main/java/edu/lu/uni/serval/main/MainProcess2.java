package edu.lu.uni.serval.main;

/**
 * 1. Localize bug.
 * 2. Search similar methods.
 * 3. Fix the bug with similar methods.
 * 
 * @author kui.liu
 *
 */
public class MainProcess2 {
	
	public static void main(String[] args) {
		String buggyProjectsPath = args[0];//"/work/users/kliu/Defects4JData/";
		String defects4jPath = args[1];// "/work/users/kliu/SearchAPR/defects4j/";
		String buggyProjects = args[2]; // Chart_1
		String searchPath = args[3];   // "/work/users/kliu/SearchAPR/data/existingMethods/";
		String metricStr = args[4];    // Ochiai
		boolean readSearchSpace = Boolean.valueOf(args[8]);
		
		String[] buggyProjectsArray = buggyProjects.split(",");
		SearchSpace searchSpace = null;
		for (String buggyProject : buggyProjectsArray) {
			MainProcess main = new MainProcess();
			main.isOneByOne = Boolean.valueOf(args[5]);
			main.withoutPriority = Boolean.valueOf(args[6]);
			int expire = Integer.valueOf(args[7]);
			if (searchSpace != null) {
				main.searchSpace = searchSpace;
			}
			main.fixProcess(buggyProjectsPath, defects4jPath, buggyProject, searchPath, metricStr, expire, readSearchSpace);
			if (searchSpace == null) {
				searchSpace = main.searchSpace;
			}
		}
		
	}

	
}
