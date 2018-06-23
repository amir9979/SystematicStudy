package edu.lu.uni.serval.main;

import java.io.Serializable;
import java.util.List;

public class SearchSpaceMethod implements Serializable {

	private static final long serialVersionUID = -7167782716671835262L;
	
	public String methodName = null;
	public String signature;
	public List<String> rawTokens;
	public String info;
	public String bodyCode;
	public Integer levenshteinDistance;
}
