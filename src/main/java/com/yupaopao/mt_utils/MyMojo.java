package com.yupaopao.mt_utils;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.collect.Lists;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.Type;
import com.yupaopao.mt_utils.util.LocalStringUtils;
import com.yupaopao.platform.common.annotation.Description;
import com.yupaopao.platform.common.utils.StringUtil;
import com.yupaopao.mt_utils.util.DirSearchUtils;
import com.yupaopao.mt_utils.util.DocletUtils;
import com.yupaopao.mt_utils.util.FilePathUtils;
import com.yupaopao.mt_utils.util.JavaDocUtils;
import com.yupaopao.mt_utils.util.JsonUtils;
import com.yupaopao.mt_utils.util.MarkdownUtils;
import com.yupaopao.mt_utils.util.MavenUtils;
import com.yupaopao.mt_utils.util.QingProjectUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.codehaus.plexus.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Goal which generate a markdown format document.
 *
 * @goal md-doc
 * @phase package
 * @requiresDependencyResolution compile+runtime
 */
public class MyMojo
    extends AbstractMojo
{
    /**
     * Location of the file.
     * @parameter property="project.build.sourceDirectory"
     * @required
     */
    private String sourceDirectory;

    /**
     * api扫描路径
     * @parameter
     */
    private String apiBasePackage;

    /**
     * 项目基本路径
     * @parameter
     */
    private String basePackage;

    /**
     * 指定版本
     * @parameter
     */
    private String docVersion;

    /**
     * 生成文件名
     * @parameter
     */
    private String outputFileName;

    /**
     * @parameter default-value="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;

    /**
     * @component
     */
    private ProjectBuilder projectBuilder;

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        if(StringUtil.isEmpty(apiBasePackage)){
            getLog().error("请配置apiBasePackage参数指定接口所在包名，如：com.yupaopao.xx.xx");
            throw new RuntimeException("请配置apiBasePackage参数指定接口所在包名，如：com.yupaopao.xx.xx");
        }
        // 初始化文档模板
        QingProjectUtils.initStringTemplate();
        // 构造模板参数
        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("pom_groupId", project.getGroupId());
        paramMap.put("pom_artifactId", project.getArtifactId());
        paramMap.put("pom_version", project.getVersion());
        paramMap.put("methodDocs",buildMethodDoc());
        // 生成文档文件名
        String resourcesDirectory = sourceDirectory.replace("java", "resources");
        String outFileName = outputFileName;
        if(StringUtil.isEmpty(outFileName)){
            outFileName = project.getArtifactId() + "-" + project.getVersion();
            if(!StringUtil.isEmpty(docVersion)){
                outFileName += "-" + docVersion;
            }
        }
        String resultFilePath = resourcesDirectory + "/doc/" + outFileName + ".md";
        // 生成文档
        QingProjectUtils.generateWithStringTemplate("interface-doc-template.ftl", paramMap, resultFilePath);
    }

    public String buildMethodDoc() throws MojoExecutionException {
        // 构造javadoc需要的classpath
        String javaDocClassPath = buildClassPath();
        getLog().info("javaDocClassPath:" + javaDocClassPath);
        // 需要扫描的目录
        if(StringUtil.isEmpty(basePackage)){
            basePackage = LocalStringUtils.getParentPackage(apiBasePackage, 2);
        }
        getLog().info("basePackage:"  + basePackage);
        String basePackagePath = FilePathUtils.buildDirPath(sourceDirectory, apiBasePackage);
        getLog().info(basePackagePath);
        // 方法文档数据保存
        StringBuilder methods = new StringBuilder();
        try {
            // 先通过javadoc获取项目源码对应的ClassDoc对象
            Map<String, ClassDoc> classDocMap = DocletUtils.getClassDocMap(javaDocClassPath, sourceDirectory, basePackage);
            // 处理需要扫描的目录中的ClassDoc
            DirSearchUtils.deepCheckDir(basePackagePath, new DirSearchUtils.FileHandler() {
                @Override
                public void handle(File file) throws IOException {
                    if(file.getName().endsWith(".java")){
                        RootDoc docInfo = DocletUtils.getDocInfo(javaDocClassPath, file.getAbsolutePath());
                        if(docInfo != null){
                            ClassDoc classDoc = docInfo.classes()[0];
                            // 判断classDoc中是否有需要生成接口文档的方法
                            if(isNeedDoc(classDoc)){
                                getLog().info("packageName:" + classDoc.containingPackage().name());
                                String classDesc = JavaDocUtils.getClassComment(classDoc);
                                if(StringUtil.isEmpty(classDesc)){
                                    classDesc = classDoc.simpleTypeName();
                                }
                                // 已类名聚合
                                methods.append("## " + classDesc).append("\r\n");
                                for (MethodDoc methodDoc : classDoc.methods()) {
                                    try{
                                        if(isNeedDoc(classDoc, methodDoc)){
                                            methods.append(toMethodDoc(classDoc, methodDoc, classDocMap));
                                        }
                                    }catch (Exception e){
                                        getLog().warn("生成接口文档失败，接口类：" + classDoc.simpleTypeName() + ", 方法名：" + methodDoc.name(), e);
                                    }
                                }
                            }
                        }
                    }
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("扫描basePackage失败", e);
        }

        return methods.toString();
    }

    /**
     * 构建javaDoc需要的classpath参数
     * @return
     * @throws MojoExecutionException
     */
    private String buildClassPath() throws MojoExecutionException {
        // 处理依赖包的classpath
        StringBuilder classPath = new StringBuilder(project.getBuild().getOutputDirectory() + MavenUtils.CLASSPATH_SPLIT);
        for (Object dependencyObj : project.getDependencies()) {
            Dependency dependency = (Dependency)dependencyObj;
            classPath.append(MavenUtils.buildClassPath(dependency)).append(MavenUtils.CLASSPATH_SPLIT);
        }
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildLoopClassPath(classPath, buildingRequest);

        return classPath.toString();
    }

    /**
     * 依据项目中的pom来循环构建需要的classpath
     * @param classPath
     * @param buildingRequest
     * @throws MojoExecutionException
     */
    private void buildLoopClassPath(StringBuilder classPath, ProjectBuildingRequest buildingRequest) throws MojoExecutionException {
        getLog().info("buildLoopClassPath start");
        Queue<Artifact> artifactQueue = new LinkedList<>();
        artifactQueue.addAll(project.getDependencyArtifacts());

        getLog().info("buildLoopClassPath loop");
        while (!artifactQueue.isEmpty()){
            Artifact dependencyArtifact = artifactQueue.poll();

            try{
                MavenProject dependencyProject = MavenUtils.buildMavenProject(dependencyArtifact, buildingRequest, projectBuilder);
                getLog().info(MavenUtils.toString(dependencyArtifact) + "->" + (dependencyProject == null? "null":dependencyProject));
                if(dependencyProject != null) {
                    if (dependencyProject.getDependencies() == null) {
                        continue;
                    }

                    Set<String> needLoopSet = new HashSet<>();
                    for (Dependency dependency : (List<Dependency>) dependencyProject.getDependencies()) {
                        if (Artifact.SCOPE_TEST.equals(dependency.getScope())) {
                            continue;
                        }
                        String jarPath = MavenUtils.buildJarPath(dependency);
                        if (classPath.toString().contains(jarPath)) {
                            continue;
                        }
                        classPath.append(MavenUtils.buildClassPath(dependency)).append(MavenUtils.CLASSPATH_SPLIT);
                        needLoopSet.add(MavenUtils.toString(dependency));
                    }
                    if(needLoopSet.isEmpty() || dependencyProject.getDependencyArtifacts() == null){
                        continue;
                    }

                    for (Artifact artifact : (Set<Artifact>) dependencyProject.getDependencyArtifacts()) {
                        if (needLoopSet.contains(MavenUtils.toString(artifact))) {
                            artifactQueue.offer(artifact);
                        }
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 判断methodDoc方法是否需要生成接口文档
     * @param classDoc
     * @param methodDoc
     * @return
     */
    private boolean isNeedDoc(ClassDoc classDoc, MethodDoc methodDoc){
        if(!checkParam(methodDoc) || !checkReturn(methodDoc)){
            getLog().info("method:" + methodDoc.name() + " in class:" + classDoc.name() + " not suit my rule, ignore");
            return false;
        }

        if(!isRightDocVersion(methodDoc)){
            return false;
        }

        return true;
    }

    /**
     * 判断classDoc是否有需要生成接口文档的方法
     * @param classDoc
     * @return
     */
    private boolean isNeedDoc(ClassDoc classDoc){
        if(classDoc == null){
            return false;
        }
        if(!classDoc.isInterface()){
            return false;
        }
        if(classDoc.methods() == null || classDoc.methods().length == 0){
            return false;
        }

        if(StringUtil.isNotEmpty(docVersion)){
            boolean existRightDocVersion = false;
            for (MethodDoc method : classDoc.methods()) {
                if(isRightDocVersion(method)){
                    existRightDocVersion = true;
                }
            }
            if(!existRightDocVersion){
                return false;
            }
        }
        return true;
    }

    /**
     * 如果指定了接口版本，则过滤符合版本的方法
     * @param methodDoc
     * @return
     */
    private boolean isRightDocVersion(MethodDoc methodDoc){
        if(StringUtil.isEmpty(docVersion)){
            return true;
        }

        Tag[] versionTags = methodDoc.tags("@docVersion");
        if(versionTags != null && versionTags.length > 0 && docVersion.equals(versionTags[0].text().trim())){
            return true;
        }
        return false;
    }

    /**
     * 同样是依据freemark模板生成方法文档，然后拼装至最终文档
     * @param classDoc
     * @param methodDoc
     * @param classDocMap
     * @return
     */
    private String toMethodDoc(ClassDoc classDoc, MethodDoc methodDoc, Map<String, ClassDoc> classDocMap){
        String desc;
        AnnotationDesc description = JavaDocUtils.findAnnotation(methodDoc.annotations(), Description.class);
        if(description != null){
            desc = JavaDocUtils.getDescriptionValue(description);
        }else{
            desc = methodDoc.commentText();
        }
        // 方法tag信息，比如@return等
        Map<String, String> methodTagMap = new HashMap<>();
        if(methodDoc.tags() != null && methodDoc.tags().length > 0){
            for (Tag tag : methodDoc.tags()) {
                methodTagMap.put(tag.name(), tag.text());
            }
        }
        // 生成参数示例
        String paramJson = "无";
        String paramClass = "";
        if(methodDoc.parameters() != null && methodDoc.parameters().length > 0){
            paramJson = JsonUtils.format(JsonUtils.generateDefaultValue(methodDoc.parameters()[0].type().asClassDoc(), methodDoc.parameters()[0].type(), methodDoc.parameters()[0].type()));
            paramClass = MarkdownUtils.buildTable(methodDoc.parameters()[0].type(), classDocMap, methodTagMap);
        }
        // 生成返回值示例
        String resultJson = JsonUtils.format(JsonUtils.generateDefaultValue(methodDoc.returnType().asClassDoc(), methodDoc.returnType(), methodDoc.returnType()));
        // 组装生成文档所需参数
        Map<String, Object> params = new HashMap<>();
        params.put("interfaceName", desc);
        params.put("service", classDoc.containingPackage().name() + "." + classDoc.name());
        params.put("method", methodDoc.name());
        params.put("paramClass", paramClass);
        params.put("paramJson", paramJson);
        params.put("resultClass", MarkdownUtils.buildTable(methodDoc.returnType(), classDocMap, methodTagMap));
        params.put("resultJson", resultJson);
        params.put("signature", JavaDocUtils.toString(methodDoc, classDoc));
        // 生成接口文档String
        return QingProjectUtils.generateWithStringTemplate("method-doc-template.ftl", params);
    }

    /**
     * 检查方法参数是否满足生成接口文档
     * @param methodDoc
     * @return
     */
    private static boolean checkParam(MethodDoc methodDoc){
        if(methodDoc.parameters() == null || methodDoc.parameters().length == 0){
            return true;
        }

        if(methodDoc.parameters().length > 1){
            return false;
        }

        Type paramType = methodDoc.parameters()[0].type();
        if(paramType.isPrimitive()){
            return false;
        }
        if(JsonUtils.isWrapClass(paramType)){
            return false;
        }

        return true;
    }

    /**
     * 检查方法返回值是否满足生成接口文档
     * @param methodDoc
     * @return
     */
    private static boolean checkReturn(MethodDoc methodDoc){
        return !methodDoc.returnType().isPrimitive() && !JsonUtils.isWrapClass(methodDoc.returnType());
    }

    public static void main(String[] args) {
        MyMojo myMojo = new MyMojo();

        String sourceDirectory = "/Users/dz0400284/work/project-template/src/main/java";
        String basePackage = "com.zhu.test.api";
        String javaDocClassPath = "/Users/dz0400284/work/project-template/target/classes:/Users/dz0400284/.m2/repository/com/yupaopao/platform-common/platform-common/1.4.14/platform-common-1.4.14.jar:/Users/dz0400284/.m2/repository/org/projectlombok/lombok/1.16.16/lombok-1.16.16.jar";
        String basePackagePath = FilePathUtils.buildDirPath(sourceDirectory, basePackage);
        try {
            Map<String, ClassDoc> classDocMap = DocletUtils.getClassDocMap(javaDocClassPath, sourceDirectory, "com");

            DirSearchUtils.deepCheckDir(basePackagePath, new DirSearchUtils.FileHandler() {
                @Override
                public void handle(File file) throws IOException {
                    if(file.getName().endsWith(".java")){
                        RootDoc docInfo = DocletUtils.getDocInfo(javaDocClassPath, file.getAbsolutePath());
                        if(docInfo != null){
                            ClassDoc classDoc = docInfo.classes()[0];
                            if(myMojo.isNeedDoc(classDoc)){
                                String classDesc = JavaDocUtils.getClassComment(classDoc);
                                if(StringUtil.isEmpty(classDesc)){
                                    classDesc = classDoc.simpleTypeName();
                                }

                                for (MethodDoc methodDoc : classDoc.methods()) {
                                    try{
                                        if(myMojo.isNeedDoc(classDoc, methodDoc)){
                                            System.out.println(JavaDocUtils.toString(methodDoc, classDoc));
                                        }
                                    }catch (Exception e){
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("扫描basePackage失败", e);
        }
    }
}
