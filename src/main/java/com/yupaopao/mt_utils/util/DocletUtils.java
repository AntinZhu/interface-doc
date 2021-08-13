package com.yupaopao.mt_utils.util;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.RootDoc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Doclet工具类，通过javadoc方式获取ClassDoc等对象
 * Created by zhujianxing on 2021/5/21
 */
public class DocletUtils {
    private static ThreadLocal<RootDoc> root = new ThreadLocal<>();

    public static  class Doclet {

        public Doclet() {
        }
        public static boolean start(RootDoc root) {
            DocletUtils.root.set(root);
            return true;
        }

        public static LanguageVersion languageVersion() {
            return LanguageVersion.JAVA_1_5;
        }
    }

    public static RootDoc getDocInfo(String classpathDir, String javaPath) {
        com.sun.tools.javadoc.Main.execute(new String[] {"-doclet",
                DocletUtils.Doclet.class.getName(),
                "-encoding","utf-8",
                "-classpath",
                classpathDir,
                javaPath});

        return root.get();
    }

    public static Map<String, ClassDoc> getClassDocMap(String classpathDir, String sourcePath, String subPackage) {
        com.sun.tools.javadoc.Main.execute(new String[] {"-doclet",
                DocletUtils.Doclet.class.getName(),
                "-encoding","utf-8",
                "-classpath",
                classpathDir,
                "-sourcepath", sourcePath,
                "-subpackages", subPackage
        });

        if(DocletUtils.root.get() == null){
            return Collections.emptyMap();
        }else{
            Map<String, ClassDoc> resultMap = new HashMap<>();
            for (ClassDoc classDoc : DocletUtils.root.get().classes()) {
                resultMap.put(classDoc.qualifiedTypeName(), classDoc);
            }
            return resultMap;
        }
    }

    public static void main(String[] args) {
        String classpath = "/Users/dz0400284/work/project-template/target/classes:/Users/dz0400284/.m2/repository/com/yupaopao/platform-common/platform-common/1.4.14/platform-common-1.4.14.jar:";
        com.sun.tools.javadoc.Main.execute(new String[] {"-doclet",
                DocletUtils.Doclet.class.getName(),
                "-encoding","utf-8",
                "-classpath",
                classpath,
                "-sourcepath", "/Users/dz0400284/work/project-template/src/main/java",
                "-subpackages", "com.zhu.test"
        });

        if(DocletUtils.root == null){
            System.out.println("NPE");
        }else{
            for (ClassDoc aClass : DocletUtils.root.get().classes()) {
                System.out.println(aClass.name());
            }
        }
    }
}
