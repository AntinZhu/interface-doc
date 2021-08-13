package com.yupaopao.mt_utils.util;

import java.io.File;
import java.io.IOException;

/**
 * Created by zhujianxing on 2019/7/4.
 */
public class DirSearchUtils {

    /**
     * 遍历目录及其下子目录，并用fileHandler处理每个文件
     * @param dir
     * @param fileHandler
     * @throws IOException
     */
    public static void deepCheckDir(String dir, FileHandler fileHandler) throws IOException {
        File dirFile = new File(dir);
        deepCheckDir(dirFile, fileHandler);
    }

    /**
     * 遍历目录及其下子目录，并用fileHandler处理每个文件
     * @param dir
     * @param fileHandler
     * @throws IOException
     */
    public static void deepCheckDir(File dir, FileHandler fileHandler) throws IOException {
        File[] xmlFiles = dir.listFiles();
            for (File xmlFile : xmlFiles) {
                if(xmlFile.isDirectory()){
                    deepCheckDir(xmlFile, fileHandler);
                }else{
                    fileHandler.handle(xmlFile);
                }
            }

        fileHandler.doAfterFileChecked(dir);
    }

    /**
     * 查找目录dir及其子目录，并通过fileHandler转换
     * @param dir
     * @param fileHandler
     * @param <T>
     * @return
     * @throws IOException
     */
    public static <T> T findDir(String dir, DirHandler<T> fileHandler) throws IOException {
        File dirFile = new File(dir);
        return findDir(dirFile, fileHandler);
    }

    /**
     * 查找目录dir及其子目录，并通过fileHandler转换
     * @param dir
     * @param fileHandler
     * @param <T>
     * @return
     * @throws IOException
     */
    public static  <T> T findDir(File dir, DirHandler<T> fileHandler) throws IOException {
        File[] xmlFiles = dir.listFiles();
        for (File xmlFile : xmlFiles) {
            T t = fileHandler.handle(xmlFile);
            if(t != null){
                return t;
            }
        }

        return null;
    }

    public interface FileHandler {

        void handle(File file) throws IOException;

        default void doAfterFileChecked(File dir){};
    }

    public interface DirHandler<T> {
        T handle(File file) throws IOException;
    }
}
