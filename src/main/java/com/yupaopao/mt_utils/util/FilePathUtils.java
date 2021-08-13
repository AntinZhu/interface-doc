package com.yupaopao.mt_utils.util;

import java.io.File;

/**
 * 文件路径相关工具类
 * Created by zhujianxing on 2021/5/21
 */
public class FilePathUtils {
    /**
     * 操作系统的目录分隔符
     */
    public static final String SP = File.separator.equals("\\")? "\\\\":File.separator;

    /**
     * 构建目录路径
     * @param rootDir 原始路基
     * @param pathNames 各级子目录名称，按顺序一次往下构建
     * @return
     */
    public static final String buildDirPath(String rootDir, String ... pathNames){
        String resultPath = rootDir.replaceAll("\\.", SP);
        for (String pathName : pathNames) {
            resultPath += File.separator + pathName.replaceAll("\\.", SP);
        }

        return resultPath;
    }

    /**
     * 构建文件路径
     * @param rootDir 原始路基
     * @param pathNames 各级子目录名称，按顺序一次往下构建，最后一个为文件名
     * @return
     */
    public static final String buildFilePath(String rootDir, String ... pathNames){
        String resultPath = rootDir.replaceAll("\\.", SP);
        int i = 1;
        for (String pathName : pathNames) {
            String filePath = (i == pathNames.length)? pathName : pathName.replaceAll("\\.", SP);
            resultPath += File.separator + filePath;
            i++;
        }

        return resultPath;
    }

    /**
     * package路径转为文件路径
     * @param packagePath
     * @return
     */
    public static String packageToFilePath(String packagePath){
        return packagePath.replaceAll("\\.", SP);
    }
}
