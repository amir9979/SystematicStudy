package edu.lu.uni.serval.faultlocalization.utils;

import java.util.concurrent.Callable;

import com.gzoltar.core.GZoltar;

public class GzoltarRunProcess implements Callable<Boolean> {
    public GZoltar gzoltar;

    public GzoltarRunProcess(GZoltar gzoltar) {
        this.gzoltar = gzoltar;
    }

    public synchronized Boolean call() {
        if (Thread.interrupted()){
            return false;
        }
        gzoltar.run();
        return true;
    }
}
