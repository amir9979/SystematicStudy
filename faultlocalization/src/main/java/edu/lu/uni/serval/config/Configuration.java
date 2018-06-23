package edu.lu.uni.serval.config;

public class Configuration {
	public static final String[] METRICS = {"Ample", "Anderberg", "Barinel", "Dice", "DStar", "Fagge", "Gp13", "Hamann", "Hamming", "Jaccard", "Kulczynski1", "Kulczynski2", "M1",
			"McCon", "Minus", "Naish1", "Naish2", "Ochiai", "Ochiai2", "Qe", "RogersTanimoto", "RussellRao", "SimpleMatching", "Sokal", "Tarantula", 
			"Wong1", "Wong2", "Wong3", "Zoltar", "null"};
	
    //---------------Timeout Config------------------
    public static int TOTAL_RUN_TIMEOUT = 10800;

    public static final int SHELL_RUN_TIMEOUT = 10800;
    public static final int GZOLTAR_RUN_TIMEOUT = 600;
    public static final int SEARCH_BOUNDARY_TIMEOUT = TOTAL_RUN_TIMEOUT/5;

    //--------------Result Path Config------------------
    public static final String RESULT_PATH = "resultMessage";
    public static final String PATCH_PATH = RESULT_PATH + "/patch";
    public static final String PATCH_SOURCE_PATH = RESULT_PATH + "/patchSource";
    public static final String LOCALIZATION_PATH = RESULT_PATH + "/localization";
    public static final String RUNTIMEMESSAGE_PATH = RESULT_PATH + "/runtimeMessage";
    public static final String PREDICATE_MESSAGE_PATH = RESULT_PATH + "/predicateMessage";
    public static final String FIX_RESULT_FILE_PATH = RESULT_PATH + "/fixResult.log";


    //--------------Runtime Path Config--------------------
    public static final String TEMP_FILES_PATH = ".temp/";
    public static final String LOCALIZATION_RESULT_CACHE = ".suspicious/";
}
