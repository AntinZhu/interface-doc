package com.yupaopao.mt_utils.util;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * maven相关数据的工具类
 * Created by zhujianxing on 2021/5/21
 */
public class MavenUtils {

    /**
     * 操作系统对应的classpath分隔符
     */
    public static final String CLASSPATH_SPLIT = File.separator.equals("\\")? ";":":";
    // 本地maven仓库jar包存储的相关格式
    private static final String JAR_PATH_TEMPLATE = "%s" + FilePathUtils.SP + "%s" + FilePathUtils.SP;
    private static final String CLASSPATH_TEMPLATE = "%s" + FilePathUtils.SP + "%s" + FilePathUtils.SP+ "%s" + FilePathUtils.SP + "%s";
    private static final String FILE_NAME_TEMPLATE = "%s-%s.jar";
    // 本地maven默认仓库的地址
    private static final String LOCAL_PATH = System.getProperty("user.home") + FilePathUtils.SP + ".m2" + FilePathUtils.SP + "repository" + FilePathUtils.SP;
    // 一些默认需要引入的jar
    private static final List<Pair<String, String>> DEFAULT_LIST = Lists.newArrayList(
            Pair.of("org.projectlombok", "lombok"),
            Pair.of("com.yupaopao.platform-common", "platform-common"),
            Pair.of("com.yupaopao.arthur", "arthur-sdk-mobileapi-common")
    );

    /**
     * 根据dependency中的信息，生成其在本地maven库中的地址
     * @param dependency
     * @return
     */
    public static String buildClassPath(Dependency dependency){
        return buildClassPath(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
    }

    /**
     * 根据dependency信息，生成除去version信息，对应的maven仓库中的目录
     * @param dependency
     * @return
     */
    public static String buildJarPath(Dependency dependency){
        return buildJarPath(dependency.getGroupId(), dependency.getArtifactId());
    }

    public static String toString(Dependency dependency){
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion();
    }

    /**
     * 根据相关信息，生成其在本地maven库中的地址
     * @param groupId
     * @param artifactId
     * @param version
     * @return
     */
    public static String buildClassPath(String groupId, String artifactId, String version){
        return LOCAL_PATH + String.format(CLASSPATH_TEMPLATE,
                FilePathUtils.packageToFilePath(groupId),
                artifactId,
                version,
                String.format(FILE_NAME_TEMPLATE, artifactId, version)
        );
    }

    /**
     * 根据groupId,artifactId，对应的maven仓库中的目录
     * @param groupId
     * @param artifactId
     * @return
     */
    public static String buildJarPath(String groupId, String artifactId){
        return LOCAL_PATH + String.format(JAR_PATH_TEMPLATE,
                FilePathUtils.packageToFilePath(groupId),
                artifactId
        );
    }

    public static String toString(Artifact dependency){
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion();
    }

    /**
     * artifact转化为MavenProject，用于遍历项目依赖的所有jar信息
     * @param artifact
     * @param buildingRequest
     * @param projectBuilder
     * @return
     * @throws MojoExecutionException
     */
    public static MavenProject buildMavenProject(Artifact artifact, ProjectBuildingRequest buildingRequest, ProjectBuilder projectBuilder) throws MojoExecutionException {
        try {
            buildingRequest.setProject(null);
            MavenProject mavenProject = projectBuilder.build(artifact, buildingRequest).getProject();
            return mavenProject;
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException("Error while building project", e);
        }
    }

    /**
     * 生成默认需要依赖的classpath,其中的version取决于当前maven仓库中有的值
     * @param existClassPath
     * @return
     */
    public static String buildDefaultClassPath(String existClassPath){
        StringBuilder result = new StringBuilder();
        for (Pair<String, String> defaultPath : DEFAULT_LIST) {
            String filePathDir = LOCAL_PATH + FilePathUtils.buildFilePath(defaultPath.getKey(), defaultPath.getValue());
            System.out.println("init default classpath for:" + filePathDir);
            if(existClassPath.contains(filePathDir)){
                continue;
            }

            try{
                String version = DirSearchUtils.findDir(filePathDir, new DirSearchUtils.DirHandler<String>() {
                    @Override
                    public String handle(File file) throws IOException {
                        if(file.isDirectory()){
                            return file.getName();
                        }

                        return null;
                    }
                });
                if(version != null){
                    System.out.println("build default class path,pom:" + defaultPath.getKey() + ":" + defaultPath.getValue() + "：" + version);
                    result.append(buildClassPath(defaultPath.getKey(), defaultPath.getValue(), version)).append(CLASSPATH_SPLIT);
                }else{
                    System.out.println("fond default version for:" + filePathDir);
                }
            }catch (Exception e){
                e.printStackTrace();
                System.out.println("构建默认classpath失败，for groupId:" + defaultPath.getKey() + ", artifactId:" + defaultPath.getValue());
            }
        }

        return result.toString();
    }

    public static void main(String[] args) {
        System.out.println(buildDefaultClassPath(""));
    }
}
