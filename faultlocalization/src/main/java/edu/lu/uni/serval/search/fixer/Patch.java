package edu.lu.uni.serval.search.fixer;

import java.util.List;

public class Patch {
	public String className;//org.jfree.chart.plot.XYPlot
	public String testClassName;//org.jfree.chart.plot.junit.XYPlotTests
	public String testMethodName;// testRemoveRangeMarker
	public String patchMethodCode = null;
	public List<String> patchStatementCode;

}
