package com.yupaopao.mt_utils.util;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationTypeElementDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.Type;
import com.yupaopao.platform.common.utils.StringUtil;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * javaDoc相关对象的工具类
 * Created by zhujianxing on 2021/6/8
 */
public class JavaDocUtils {
    /**
     * 获取annotationDescs中annotation的注解
     * @param annotationDescs
     * @param annotation
     * @return
     */
    public static AnnotationDesc findAnnotation(AnnotationDesc[] annotationDescs, Class annotation){
        if(annotationDescs != null && annotationDescs.length > 0){
            for (AnnotationDesc annotationDesc : annotationDescs) {
                if(annotation.getName().equals(annotationDesc.annotationType().qualifiedName())){
                    return annotationDesc;
                }

                System.out.println("注解名称：" + annotationDesc.annotationType().qualifiedName());
                for (AnnotationTypeElementDoc element : annotationDesc.annotationType().elements()) {
                    System.out.println("属性名称：" + element.name());
                    System.out.println("属性默认值：" + element.defaultValue());
                }
                System.out.println("属性值：" + annotationDesc.elementValues()[0].value());
            }
        }

        return null;
    }

    /**
     * 获取annotationDesc的value属性
     * @param annotationDesc
     * @return
     */
    public static String getDescriptionValue(AnnotationDesc annotationDesc){
        if(annotationDesc == null){
            return null;
        }

        for (AnnotationDesc.ElementValuePair elementValuePair : annotationDesc.elementValues()) {
            if(elementValuePair.element().name().equals("value")){
                return String.valueOf(elementValuePair.value().value());
            }
        }

        return null;
    }

    /**
     * 获取classDoc中泛型等于genericName的type
     * @param classDoc
     * @param type
     * @param genericName
     * @return
     */
    public static Type findGenericType(ClassDoc classDoc, Type type, String genericName){
        if(classDoc.typeParameters().length == 0){
            return null;
        }

        int idx = 0;
        while (idx < classDoc.typeParameters().length) {
            if(classDoc.typeParameters()[idx].typeName().equals(genericName)){
                return type.asParameterizedType().typeArguments()[idx];
            }

            idx++;
        }

        return null;
    }

    /**
     * 获取classdoc的注释文本
     * @param classDoc
     * @return
     */
    public static String getClassComment(ClassDoc classDoc){
        if(StringUtil.isEmpty(classDoc.commentText())){
            return classDoc.commentText();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(classDoc.commentText().getBytes(StandardCharsets.UTF_8))));
        String line = null;
        try {
            while ((line = reader.readLine()) != null){
                line = line.trim();
                if(StringUtil.isNotEmpty(line)){
                    if(line.startsWith("@") || line.startsWith("Created")){
                        return "";
                    }else{
                        return line;
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("getClassComment fail for class:" + classDoc.name() + ", exception:" + e.getMessage());
        }
        return "";
    }

    /**
     * 生成方法的源码，包含备注及方法签名等
     * @param methodDoc
     * @param classDoc
     * @return
     */
    public static String toString(MethodDoc methodDoc, ClassDoc classDoc){
        StringBuilder result = new StringBuilder();
        result.append(buildComment(methodDoc.getRawCommentText())).append("\r\n");
        result.append(buildMethodSignature(methodDoc, classDoc));

        return result.toString();
    }

    /**
     * 生成类型名称，支持Response<T>  其中T会被替换成真正的类型名称
     * @param fieldType
     * @param parentClassDoc
     * @param parentType
     * @return
     */
    public static String buildFieldName(Type fieldType, ClassDoc parentClassDoc, Type parentType){
        return buildFieldName(fieldType, parentClassDoc, parentType, "<");
    }

    /**
     * 生成类型名称，支持Response<T>  其中T会被替换成真正的类型名称
     * @param fieldType
     * @param parentClassDoc
     * @param parentType
     * @param parameterizedTypeSuffer
     * @return
     */
    public static String buildFieldName(Type fieldType, ClassDoc parentClassDoc, Type parentType, String parameterizedTypeSuffer){
        if(fieldType.asParameterizedType() != null){
            StringBuilder result = new StringBuilder(fieldType.simpleTypeName());
            result.append(parameterizedTypeSuffer);
            int total = fieldType.asParameterizedType().typeArguments().length;
            int idx = 0;
            while (idx < total){
                Type type = fieldType.asParameterizedType().typeArguments()[idx];
                Type genericType = JavaDocUtils.findGenericType(parentClassDoc, parentType, type.qualifiedTypeName());
                if(genericType != null){
                    type = genericType;
                }
                if(type.asParameterizedType() != null){
                    result.append(buildFieldName(type, parentClassDoc, parentType, parameterizedTypeSuffer));
                }else{
                    String genericTypeName = type.simpleTypeName();
                    result.append(genericTypeName);
                }
                if(idx != total - 1){
                    result.append(", ");
                }
                idx++;
            }
            result.append(">");
            return result.toString();
        }else{
            String simpleTypeName = fieldType.simpleTypeName();
            if(!fieldType.qualifiedTypeName().equals(fieldType.toString())){
                String fullName = fieldType.toString();
                simpleTypeName = fullName;
                if(simpleTypeName.indexOf(".") != -1){
                    simpleTypeName = simpleTypeName.substring(simpleTypeName.lastIndexOf(".") + 1);
                }
            }
            return simpleTypeName;
        }
    }

    /**
     * 还原备注信息
     * @param rawComment
     * @return
     */
    public static String buildComment(String rawComment){
        BufferedReader reader = null;
        try{
            StringBuilder result = new StringBuilder("/**").append("\r\n");
            reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(rawComment.getBytes())));
            String line = null;
            while((line = reader.readLine()) != null){
                result.append(" * ").append(line).append("\r\n");
            }
            result.append(" */");
            return result.toString();
        }catch (Exception e){
            System.err.println("generate comment fail");
            e.printStackTrace();
        }finally {
            if(reader != null){
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        return "";
    }

    /**
     * 生成方法签名
     * @param methodDoc
     * @param classDoc
     * @return
     */
    public static String buildMethodSignature(MethodDoc methodDoc, ClassDoc classDoc){
        StringBuilder result = new StringBuilder();
        result.append(
                buildFieldName(methodDoc.returnType(), classDoc, methodDoc.returnType())
        ).append(" ")
                .append(methodDoc.name()).append(buildParameter(methodDoc, classDoc));

        return result.toString();
    }

    /**
     * 生成方法参数String
     * @param methodDoc
     * @param classDoc
     * @return
     */
    private static String buildParameter(MethodDoc methodDoc, ClassDoc classDoc){
        StringBuilder result = new StringBuilder("(");
        if(methodDoc.parameters() != null && methodDoc.parameters().length > 0){
            int idx = 0;
            for (Parameter parameter : methodDoc.parameters()) {
                result.append(buildFieldName(parameter.type(), classDoc, parameter.type())).append(" ").append(parameter.name());
                if(idx != methodDoc.parameters().length - 1){
                    result.append(", ");
                }
                idx++;
            }
        }
        result.append(")");
        return result.toString();
    }
}
