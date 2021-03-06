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
     * api????????????
     * @parameter
     */
    private String apiBasePackage;

    /**
     * ??????????????????
     * @parameter
     */
    private String basePackage;

    /**
     * ????????????
     * @parameter
     */
    private String docVersion;

    /**
     * ???????????????
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
            getLog().error("?????????apiBasePackage???????????????????????????????????????com.yupaopao.xx.xx");
            throw new RuntimeException("?????????apiBasePackage???????????????????????????????????????com.yupaopao.xx.xx");
        }
        // ?????????????????????
        QingProjectUtils.initStringTemplate();
        // ??????????????????
        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("pom_groupId", project.getGroupId());
        paramMap.put("pom_artifactId", project.getArtifactId());
        paramMap.put("pom_version", project.getVersion());
        paramMap.put("methodDocs",buildMethodDoc());
        // ?????????????????????
        String resourcesDirectory = sourceDirectory.replace("java", "resources");
        String outFileName = outputFileName;
        if(StringUtil.isEmpty(outFileName)){
            outFileName = project.getArtifactId() + "-" + project.getVersion();
            if(!StringUtil.isEmpty(docVersion)){
                outFileName += "-" + docVersion;
            }
        }
        String resultFilePath = resourcesDirectory + "/doc/" + outFileName + ".md";
        // ????????????
        QingProjectUtils.generateWithStringTemplate("interface-doc-template.ftl", paramMap, resultFilePath);
    }

    public String buildMethodDoc() throws MojoExecutionException {
        // ??????javadoc?????????classpath
        String javaDocClassPath = buildClassPath();
        getLog().info("javaDocClassPath:" + javaDocClassPath);
        // ?????????????????????
        if(StringUtil.isEmpty(basePackage)){
            basePackage = LocalStringUtils.getParentPackage(apiBasePackage, 2);
        }
        getLog().info("basePackage:"  + basePackage);
        String basePackagePath = FilePathUtils.buildDirPath(sourceDirectory, apiBasePackage);
        getLog().info(basePackagePath);
        // ????????????????????????
        StringBuilder methods = new StringBuilder();
        try {
            // ?????????javadoc???????????????????????????ClassDoc??????
            Map<String, ClassDoc> classDocMap = DocletUtils.getClassDocMap(javaDocClassPath, sourceDirectory, basePackage);
            // ?????????????????????????????????ClassDoc
            DirSearchUtils.deepCheckDir(basePackagePath, new DirSearchUtils.FileHandler() {
                @Override
                public void handle(File file) throws IOException {
                    if(file.getName().endsWith(".java")){
                        RootDoc docInfo = DocletUtils.getDocInfo(javaDocClassPath, file.getAbsolutePath());
                        if(docInfo != null){
                            ClassDoc classDoc = docInfo.classes()[0];
                            // ??????classDoc?????????????????????????????????????????????
                            if(isNeedDoc(classDoc)){
                                getLog().info("packageName:" + classDoc.containingPackage().name());
                                String classDesc = JavaDocUtils.getClassComment(classDoc);
                                if(StringUtil.isEmpty(classDesc)){
                                    classDesc = classDoc.simpleTypeName();
                                }
                                // ???????????????
                                methods.append("## " + classDesc).append("\r\n");
                                for (MethodDoc methodDoc : classDoc.methods()) {
                                    try{
                                        if(isNeedDoc(classDoc, methodDoc)){
                                            methods.append(toMethodDoc(classDoc, methodDoc, classDocMap));
                                        }
                                    }catch (Exception e){
                                        getLog().warn("???????????????????????????????????????" + classDoc.simpleTypeName() + ", ????????????" + methodDoc.name(), e);
                                    }
                                }
                            }
                        }
                    }
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("??????basePackage??????", e);
        }

        return methods.toString();
    }

    /**
     * ??????javaDoc?????????classpath??????
     * @return
     * @throws MojoExecutionException
     */
    private String buildClassPath() throws MojoExecutionException {
        // ??????????????????classpath
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
     * ??????????????????pom????????????????????????classpath
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
     * ??????methodDoc????????????????????????????????????
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
     * ??????classDoc??????????????????????????????????????????
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
     * ????????????????????????????????????????????????????????????
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
     * ???????????????freemark??????????????????????????????????????????????????????
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
        // ??????tag???????????????@return???
        Map<String, String> methodTagMap = new HashMap<>();
        if(methodDoc.tags() != null && methodDoc.tags().length > 0){
            for (Tag tag : methodDoc.tags()) {
                methodTagMap.put(tag.name(), tag.text());
            }
        }
        // ??????????????????
        String paramJson = "???";
        String paramClass = "";
        if(methodDoc.parameters() != null && methodDoc.parameters().length > 0){
            paramJson = JsonUtils.format(JsonUtils.generateDefaultValue(methodDoc.parameters()[0].type().asClassDoc(), methodDoc.parameters()[0].type(), methodDoc.parameters()[0].type()));
            paramClass = MarkdownUtils.buildTable(methodDoc.parameters()[0].type(), classDocMap, methodTagMap);
        }
        // ?????????????????????
        String resultJson = JsonUtils.format(JsonUtils.generateDefaultValue(methodDoc.returnType().asClassDoc(), methodDoc.returnType(), methodDoc.returnType()));
        // ??????????????????????????????
        Map<String, Object> params = new HashMap<>();
        params.put("interfaceName", desc);
        params.put("service", classDoc.containingPackage().name() + "." + classDoc.name());
        params.put("method", methodDoc.name());
        params.put("paramClass", paramClass);
        params.put("paramJson", paramJson);
        params.put("resultClass", MarkdownUtils.buildTable(methodDoc.returnType(), classDocMap, methodTagMap));
        params.put("resultJson", resultJson);
        params.put("signature", JavaDocUtils.toString(methodDoc, classDoc));
        // ??????????????????String
        return QingProjectUtils.generateWithStringTemplate("method-doc-template.ftl", params);
    }

    /**
     * ????????????????????????????????????????????????
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
     * ???????????????????????????????????????????????????
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
            throw new RuntimeException("??????basePackage??????", e);
        }
    }
}
