package edu.lu.uni.serval.faultlocalization;

/**
 * Metrics of computing suspicious value of suspicious lines.
 * 
 * https://hal.inria.fr/hal-01018935/document
 * 
 * @author kui.liu
 *
 */
public class Metrics {
	
	public Metric generateMetric(String metricStr) {
		Metric metric = null;
		if (metricStr.equals("Ample")) {
			metric = new Ample();
		} else if (metricStr.equals("Anderberg")) {
			metric = new Anderberg();
		} else if (metricStr.equals("Barinel")) {
			metric = new Barinel();
		} else if (metricStr.equals("Dice")) {
			metric = new Dice();
		} else if (metricStr.equals("DStar")) {
			metric = new DStar();
		} else if (metricStr.equals("Fagge")) {
			metric = new Fagge();
		} else if (metricStr.equals("Gp13")) {
			metric = new Gp13();
		} else if (metricStr.equals("Hamann")) {
			metric = new Hamann();
		} else if (metricStr.equals("Hamming")) {
			metric = new Hamming();
		} else if (metricStr.equals("Jaccard")) {
			metric = new Jaccard();
		} else if (metricStr.equals("Kulczynski1")) {
			metric = new Kulczynski1();
		} else if (metricStr.equals("Kulczynski2")) {
			metric = new Kulczynski2();
		} else if (metricStr.equals("M1")) {
			metric = new M1();
		} else if (metricStr.equals("McCon")) {
			metric = new McCon();
		} else if (metricStr.equals("Minus")) {
			metric = new Minus();
		} else if (metricStr.equals("Naish1")) {
			metric = new Naish1();
		} else if (metricStr.equals("Naish2")) {
			metric = new Naish2();
		} else if (metricStr.equals("Ochiai")) {
			metric = new Ochiai();
		} else if (metricStr.equals("Ochiai2")) {
			metric = new Ochiai2();
		} else if (metricStr.equals("Qe")) {
			metric = new Qe();
		} else if (metricStr.equals("RogersTanimoto")) {
			metric = new RogersTanimoto();
		} else if (metricStr.equals("RussellRao")) {
			metric = new RussellRao();
		} else if (metricStr.equals("SimpleMatching")) {
			metric = new SimpleMatching();
		} else if (metricStr.equals("Sokal")) {
			metric = new Sokal();
		} else if (metricStr.equals("Tarantula")) {
			metric = new Tarantula();
		} else if (metricStr.equals("Wong1")) {
			metric = new Wong1();
		} else if (metricStr.equals("Wong2")) {
			metric = new Wong2();
		} else if (metricStr.equals("Wong3")) {
			metric = new Wong3();
		} else if (metricStr.equals("Zoltar")) {
			metric = new Zoltar();
		}
		return metric;
	}

	public interface Metric {
		/**
		 * @param ef: number of executed and failed test cases.
		 * @param ep: number of executed and passed test cases.
		 * @param nf: number of un-executed and failed test cases.
		 * @param np: number of un-executed and passed test cases.
		 * @return
		 */
		double value(double ef, double ep, double nf, double np);
	}
	
	private class Ample implements Metric {
		public double value(double ef, double ep, double nf, double np) {
			return Math.abs(ef / (ef + nf) - ep / (ep + np));
		}
	}
	
	private class Anderberg implements Metric {
		public double value(double ef, double ep, double nf, double np) {
	        return ef / (ef + 2 * (ep + nf));
	    }
	}
	
	private class Barinel implements Metric {
		public double value(double ef, double ep, double nf, double np) {
			return 1 - ep / (ep + ef);
		}
	}
	
	private class Dice implements Metric {//SorensenDice
		public double value(double ef, double ep, double nf, double np) {
	        return 2 * ef / (2 * ef + (ep + nf));
	    }
	}
	
	private class DStar implements Metric {
		public double value(double ef, double ep, double nf, double np) {
			return ef / (ep + nf);
		}
	}

	private class Fagge implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	Double _false = Double.valueOf(ef + nf);
	    	if (_false.compareTo(0d) == 0) {
	    		return 0d;
	    	} else {
	    		return ef /_false;
	    	}
	    }
	}
	
	private class Gp13 implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	return ef * (1 + 1 / (2 * ep + ef));
	    }
	}
	
	private class Hamann implements Metric {
		public double value(double ef, double ep, double nf, double np) {
			return (ef + np - ep - nf) / (ef + ep + nf + np);
		}
	}
	
	private class Hamming implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	return ef + np;
	    }
	}
	
	private class Jaccard implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	return ef / (ef + ep + nf);
	    }
	}
	
	private class Kulczynski1 implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	return ef / (nf + ep);
	    }
	}
	
	private class Kulczynski2 implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	        return (ef / (nf + ep) + ef / (ef + nf)) / 2;
	    }
	}
	
	private class M1 implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	        return (ef + np) / (nf + ep);
	    }
	}
	
	private class McCon implements Metric {
		public double value(double ef, double ep, double nf, double np) {
			return (ef * ef - ep * nf) / ((ef + nf) * (ef + ep));
		}
	}
	
	private class Minus implements Metric {
		public double value(double ef, double ep, double nf, double np) {
			return ef / (ef + nf) / (ef / (ef + nf) + ep / (ep + np)) - (1 - ef / (ef + nf) / (2 - ef / (ef + nf) - ep / (ep + np)));
		}
	}
	
	private class Naish1 implements Metric {
		public double value(double ef, double ep, double nf, double np) {
			if (ef == 0)
				return np;
			return -1;
		}
	}
	
	private class Naish2 implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	return ef - ep / (ep + np + 1);
	    }
	}
	
	private class Ochiai implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	return ef / Math.sqrt((ef + ep) * (ef + nf));
	    }
	}
	
	private class Ochiai2 implements Metric {
		public double value(double ef, double ep, double nf, double np) {
			return ef * np / Math.sqrt((ef + ep) * (ef + nf) * (np + ep) * (np + nf));
		}
	}
	
	private class Qe implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	return  ef / (ef + ep);
	    }
	}
	
	private class RogersTanimoto implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	return (ef + np) / (ef + np + 2 * (nf + ep));
	    }
	}
	
	private class RussellRao implements Metric {
		public double value(double ef, double ep, double nf, double np) {
			return ef / (ef + ep + nf + np);
		}
	}
	
	private class SimpleMatching implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	return (ef + np) / (ef + nf + ep + np);
	    }
	}
	
	private class Sokal implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	return 2 * (ef + np) / ( 2 * (ef + np) + nf + ep);
	    }
	}
	
	private class Tarantula implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	Double _false = Double.valueOf(ef + nf);
	    	if (_false.compareTo(0d) == 0) {
				return 0;
			}
			return (ef / (ef + nf)) / ((ef / (ef + nf)) + (ep / (ep + np)));
	    }
	}
	
	private class Wong1 implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	        return ef;
	    }
	}
	
	private class Wong2 implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	        return  ef - ep;
	    }
	}
	
	private class Wong3 implements Metric {
	    public double value(double ef, double ep, double nf, double np) {
	    	double h;
	    	if (ep <= 2) {
	    		h = ep;
	    	} else if (ep <= 10) {
	    		h = 2 + 0.1 *(ep - 2);
	    	} else {
	    		h = 2.8 + 0.01 *(ep -10);
	    	}
	    	return ef - h;
	    }
	}
	
	private class Zoltar implements Metric {
		public double value(double ef, double ep, double nf, double np) {
			return ef / (ef + nf + ep + (10000 * nf * ep) /ef);
		}
	}
}
