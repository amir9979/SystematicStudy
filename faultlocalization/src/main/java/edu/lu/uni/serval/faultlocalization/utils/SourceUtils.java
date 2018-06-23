package edu.lu.uni.serval.faultlocalization.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

import edu.lu.uni.serval.config.Configuration;

public class SourceUtils {

	public static void commentCodeInSourceFile(File file, int line){
        int i=0;
        while (new File(Configuration.TEMP_FILES_PATH +"/source"+i+".temp").exists()){
            i++;
        }
        File tempFile = new File(Configuration.TEMP_FILES_PATH +"/source"+i+".temp");
        FileOutputStream outputStream = null;
        BufferedReader reader = null;
        try {
            outputStream = new FileOutputStream(tempFile);
            reader = new BufferedReader(new FileReader(file));
            String lineString = null;
            int lineNum = 0;
            int brackets = 0;
            while ((lineString = reader.readLine()) != null) {
                lineNum++;
                if (lineNum == line-1 && (!lineString.contains(";") && !lineString.contains(":") && !lineString.contains("{") && !lineString.contains("}"))){
                    lineString = "//"+lineString;
                    brackets += CodeUtils.countChar(lineString, '(');
                    brackets -= CodeUtils.countChar(lineString, ')');
                }
                if (lineNum == line || brackets > 0) {
                    lineString = "//"+lineString;
                    brackets += CodeUtils.countChar(lineString, '(');
                    brackets -= CodeUtils.countChar(lineString, ')');
                }
                outputStream.write((lineString+"\n").getBytes());
            }
            outputStream.close();
            reader.close();
        }  catch (IOException e){
            e.printStackTrace();
        }    finally {
            if (outputStream != null){
                try {
                    outputStream.close();
                } catch (IOException e){
                }
            }
            if (reader != null){
                try {
                    reader.close();
                } catch (IOException e){
                }
            }

        }
        FileUtils.copyFile(tempFile.getAbsolutePath(),file.getAbsolutePath());
        tempFile.delete();
    }

}
