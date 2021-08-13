package com.yupaopao.mt_utils.util;

import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.BufferedWriter;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * freemark的相关工具类，包含预加载模板
 * Created by zhujianxing on 2018/1/24.
 */
public class QingProjectUtils {


    private static final boolean isWins = File.separator.equals("\\");
    public static final String TEMPLATE_FILE_SUFFIX = ".ftl";

    // 预先加载的string模板
    private static Configuration stringTemplateConfig;
    private static MyStringTemplateLoader stringTemplateLoader;

    /**
     * 预加载所需要的模板
     */
    public static void initStringTemplate(){
        stringTemplateConfig  = new Configuration();
        stringTemplateLoader = new MyStringTemplateLoader();
        stringTemplateConfig.setTemplateLoader(stringTemplateLoader);

        initTemplate(stringTemplateLoader);
    }

    /**
     * 预加载模板到stringTemplateLoader
     * @param stringTemplateLoader
     */
     private static void initTemplate(MyStringTemplateLoader stringTemplateLoader){
         // 以jar包运行
         String jarPath = QingProjectUtils.class.getProtectionDomain().getCodeSource().getLocation().getFile();
         print(jarPath);
         JarFile jarFile = null;
         try {
             jarPath = java.net.URLDecoder.decode(jarPath, "UTF-8");
             int firstIdx = jarPath.indexOf("!");
             if(firstIdx != -1){
                 jarPath = jarPath.substring(0, firstIdx);
             }

             jarFile = new JarFile(new File(jarPath));
             Enumeration<JarEntry> es = jarFile.entries();
             while (es.hasMoreElements()) {
                 JarEntry jarEntry = (JarEntry) es.nextElement();
                 String nameEntryName = new String(jarEntry.getName().getBytes(), "UTF-8");
                 if(nameEntryName.endsWith(QingProjectUtils.TEMPLATE_FILE_SUFFIX)){
                     String templateKey = nameEntryName;
                     System.out.println(templateKey);

                     stringTemplateLoader.putTemplate(templateKey, QingJarUtils.readFromJar(nameEntryName));
                 }
             }
         } catch (Exception e) {
             System.out.println(e.toString());
             throw new RuntimeException("generate file in jar error", e);
         }
    }

    private static boolean isInJar(){
        return QingProjectUtils.class.getResource("QingProjectUtils.class").getPath().contains(".jar!");
    }

    /**
     * 结合模板和数据生成最终的文件
     * @param templateDir
     * @param templateFileName
     * @param dataMap
     * @param outputFilePath
     */
    private static void generateFileWithTemplate(String templateDir, String templateFileName, Map<String, Object> dataMap, String outputFilePath) {
        // step1 创建freeMarker配置实例
        Configuration configuration = new Configuration();
        Writer out = null;
        try {
            // step2 获取模版路径
            configuration.setDirectoryForTemplateLoading(new File(templateDir));
            // step4 加载模版文件
            Template template = configuration.getTemplate(templateFileName);
            // step5 生成数据
            File docFile = new File(outputFilePath);
            if (!docFile.getParentFile().exists()) {
                docFile.getParentFile().mkdirs();
            }
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(docFile)));
            // step6 输出文件
            template.process(dataMap, out);
            out.flush();
        } catch (Exception e) {
            throw new RuntimeException("generate file fail, templateFileName:" + templateFileName + ", outputFilePath:" + outputFilePath, e);
        } finally {
            try {
                if (null != out) {
                    out.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }

    /**
     * 根据templateKey的模板及提供的数据dataMap生成最终的文件到outputFilePath
     * @param templateKey
     * @param dataMap
     * @param outputFilePath
     */
    public static void generateWithStringTemplate(String templateKey, Map<String, Object> dataMap, String outputFilePath){
        generateFileWithStringTemplate(stringTemplateConfig, templateKey, dataMap, outputFilePath);
    }

    private static void generateFileWithStringTemplate(Configuration stringTemplateConfig, String templateKey, Map<String, Object> dataMap, String outputFilePath) {
        Template template = null;
        Writer out = null;
        try {
            template = stringTemplateConfig.getTemplate(templateKey,"UTF-8");
            if(template == null){
                throw new RuntimeException("unknown template for templateKey:" + templateKey);
            }

            File outputFile = new File(outputFilePath);
            if(!outputFile.exists()){
                outputFile.getParentFile().mkdirs();
            }
            out = new BufferedWriter(new FileWriter(outputFilePath));
            template.process(dataMap, out);
            out.flush();
        } catch (Exception e) {
            throw new RuntimeException("generate file fail, templateFileName:" + templateKey + ", outputFilePath:" + outputFilePath, e);
        }finally {
            if(out != null){
                try {
                    out.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * 根据templateKey模板及dataMap生成最终的内容
     * @param templateKey
     * @param dataMap
     * @return
     */
    public static String generateWithStringTemplate(String templateKey, Map<String, Object> dataMap){
        Template template = null;
        Writer out = null;
        try {
            template = stringTemplateConfig.getTemplate(templateKey,"UTF-8");
            if(template == null){
                throw new RuntimeException("unknown template for templateKey:" + templateKey);
            }

            CharArrayWriter result = new CharArrayWriter(512);
            out = new BufferedWriter(result);
            template.process(dataMap, out);
            out.flush();

            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException("generate file fail, templateFileName:" + templateKey, e);
        }finally {
            if(out != null){
                try {
                    out.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static void print(String text){
        System.out.println(text);
    }


    public static void main(String[] args) {
        StringBuilder result = new StringBuilder();
        int id = 1;
        while (id <= 38){
            result.append("(" + id + ",'数据库执行平均超过30ms',1,'SQL','',2,'{\"\"booleanExpression\"\":\"\"avg>30\"\",\"\"valueExpressions\"\":[\"\"totalCount\"\",\"\"avg\"\"]}',1,'system','system')");
            if(id != 38){
                result.append(",\r\n");
            }
            id++;
        }
        System.out.println(result.toString());
    }
}
