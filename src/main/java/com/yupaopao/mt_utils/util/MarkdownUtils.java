package com.yupaopao.mt_utils.util;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Type;
import com.yupaopao.platform.common.annotation.Description;
import com.yupaopao.platform.common.dto.Response;
import com.yupaopao.platform.common.utils.StringUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * markdown相关工具类
 * Created by zhujianxing on 2021/6/8
 */
public class MarkdownUtils {
    /**
     * 忽略的字段集
     */
    private static final Set<String> IGNORE_PROPERTIES = Sets.newHashSet("serialVersionUID");
    // 生成table相关数据
    private static final String TITLE = "|  字段   | 类型  | 备注  |";
    private static final String SPLI = "|  ----  | ----  | ----  |";
    private static final String LINE = "|  %s  | %s  | %s  |";

    /**
     * 生成类中属性字段对应的markdown对应的表格格式，并将其引用的其他类型，也生成table放入结果
     * @param type
     * @param classDocMap
     * @param methodTagMap
     * @return
     */
    public static String buildTable(Type type, Map<String, ClassDoc> classDocMap, Map<String, String> methodTagMap){
        StringBuilder table = new StringBuilder();

        List<Type> todoList = Lists.newLinkedList();
        todoList.add(type);

        Set<Type> doneSet = new HashSet<>();
        while (!todoList.isEmpty()){
            Type toType = todoList.remove(0);
            if(doneSet.contains(toType)){
                continue;
            }
            List<Type> subSet = innerBuild(table, toType, classDocMap, methodTagMap);
            doneSet.add(toType);
            for (Type subType : subSet) {
                if(!doneSet.contains(subType)){
                    todoList.add(0, subType);
                }
            }
            table.append("\r\n\r\n");
        }

        return table.toString();
    }

    /**
     * 生成单个table逻辑
     * @param table
     * @param type
     * @param classDocMap
     * @param methodTagMap
     * @return
     */
    private static List<Type> innerBuild(StringBuilder table, Type type, Map<String, ClassDoc> classDocMap, Map<String, String> methodTagMap){
        List<Type> subTypeList = new LinkedList<>();

        table.append("类名：" + type.simpleTypeName()).append("\r\n\r\n");
        table.append(TITLE).append("\r\n");
        table.append(SPLI).append("\r\n");

        fillField(table, type, classDocMap, methodTagMap, subTypeList);
        return subTypeList;
    }

    /**
     * 生成table中的字段
     * @param table
     * @param type
     * @param classDocMap
     * @param methodTagMap
     * @param subTypeList
     */
    private static void fillField(StringBuilder table, Type type, Map<String, ClassDoc> classDocMap, Map<String, String> methodTagMap, List<Type> subTypeList){
        ClassDoc classDoc = classDocMap.get(type.qualifiedTypeName());
        if(classDoc == null){
            classDoc = type.asClassDoc();
        }
        for (FieldDoc field : classDoc.fields(false)) {
            if(IGNORE_PROPERTIES.contains(field.name())){
                continue;
            }

            Type fieldType = field.type();
            Type genericType = JavaDocUtils.findGenericType(classDoc, type, fieldType.qualifiedTypeName());
            if(genericType != null){
                fieldType = genericType;
            }

            if(needBuildTable(fieldType)){
                subTypeList.add(0, fieldType);
            }

            if(fieldType.asParameterizedType() != null){
                for (Type typeArgument : fieldType.asParameterizedType().typeArguments()) {
                    Type subType = typeArgument;
                    Type subGenericType = JavaDocUtils.findGenericType(classDoc, type, subType.qualifiedTypeName());
                    if(subGenericType != null){
                        subType = subGenericType;
                    }
                    if(needBuildTable(typeArgument)){
                        subTypeList.add(0, subType);
                    }
                }
            }

            table.append(String.format(LINE, field.name(), buildFieldName(fieldType, classDoc, type), buildFieldDesc(type, field, classDocMap, methodTagMap))).append("\r\n");
        }

        if(classDoc.superclass() != null && needBuildTable(classDoc.superclassType())){
            fillField(table, classDoc.superclassType(), classDocMap, methodTagMap, subTypeList);
        }
    }

    /**
     * 生成列中的描述字段
     * @param type
     * @param field
     * @param classDocMap
     * @param methodTagMap
     * @return
     */
    private static String buildFieldDesc(Type type, FieldDoc field, Map<String, ClassDoc> classDocMap, Map<String, String> methodTagMap){
        // Response类中result字段的备注，优先使用@return注释的内容
        if(Response.class.getName().equals(type.qualifiedTypeName()) && field.name().equals("result")){
            String returnDesc = methodTagMap.get("@return");
            if(StringUtil.isNotEmpty(returnDesc)){
                return returnDesc;
            }
        }

        String desc;
        AnnotationDesc description = JavaDocUtils.findAnnotation(field.annotations(), Description.class);
        if(description != null){
            desc = JavaDocUtils.getDescriptionValue(description);
        }else{
            desc = field.commentText();
        }

        if(field.type().asClassDoc() != null && field.type().asClassDoc().isEnum()){
            ClassDoc enumClassDoc = classDocMap.get(field.type().qualifiedTypeName());
            if(enumClassDoc == null){
                enumClassDoc = field.type().asClassDoc();
            }
            FieldDoc[] enumDocs = enumClassDoc.enumConstants();
            if(enumDocs != null && enumDocs.length > 0){
                String firstEnumName = enumDocs[0].name();
                if(!desc.contains(firstEnumName)){
                    desc += "：" + linkField(enumDocs);
                }
            }
        }
        
        return desc;
    }

    /**
     * 生成枚举类的所有枚举值描述
     * @param fieldDocs
     * @return
     */
    private static String linkField(FieldDoc[] fieldDocs){
        if(fieldDocs == null || fieldDocs.length == 0){
            return "";
        }

        StringBuilder result = new StringBuilder();
        int idx = 0;
        while (idx < fieldDocs.length){
            FieldDoc fieldDoc = fieldDocs[idx];
            result.append(fieldDoc.name());
            if(StringUtil.isNotEmpty(fieldDoc.commentText())){
                result.append("-" + fieldDoc.commentText());
            }
            if(idx != fieldDocs.length - 1){
                result.append(",");
            }
            idx++;
        }
        return result.toString();
    }

    /**
     * 判断类型是否需要生成表格
     * @param type
     * @return
     */
    private static boolean needBuildTable(Type type){
        return !type.isPrimitive()
                && !type.qualifiedTypeName().startsWith("java")
                && !type.asClassDoc().isEnum()
                ;
    }

    /**
     * 生成字段类型名称
     * @param fieldType
     * @param parentClassDoc
     * @param parentType
     * @return
     */
    public static String buildFieldName(Type fieldType, ClassDoc parentClassDoc, Type parentType){
        return JavaDocUtils.buildFieldName(fieldType, parentClassDoc, parentType, "\\<");
    }

    public static void main(String[] args) throws IOException {
        StringBuilder table = new StringBuilder();
        table.append("|  日期  | 服务  | 第一负责人  | 监控项  | 指标类型  | 指标名称  | 判定规则  | 相关数据 | 持续天数  | 查看链接  |").append("\r\n");
        table.append("|  ----  | ----  | ----  | ----  |  ----  | ----  | ----  | ----  | ----  | ----  |").append("\r\n");
        InputStream csvFile = MarkdownUtils.class.getClassLoader().getResourceAsStream("data.csv");
        CSVParser csvParser = CSVParser.parse(csvFile, Charset.defaultCharset(), CSVFormat.DEFAULT);
        Iterator<CSVRecord> iter = csvParser.getRecords().iterator();
        iter.next();
        while (iter.hasNext()){
            CSVRecord record = iter.next();
            List<String> dataList = JSON.parseArray(record.get(8), String.class);
            StringBuilder data = new StringBuilder();
            for (int i = 0; i < dataList.size(); i++) {
                data.append(dataList.get(i));
                if(i != dataList.size() - 1){
                    data.append(",");
                }
            }
            String link = "[查看](" + record.get(9) + ")";
            table.append(String.format("|  %s  | %s  | %s  | %s  | %s  | %s  | %s  | %s  | %s  | %s  |",
                    record.get(3), record.get(1), record.get(2), record.get(4), record.get(5), record.get(6), record.get(7).replaceAll("\\|", "\\\\|"), data.toString(), record.get(10), link)
                    ).append("\r\n");
        }

        System.out.println(table.toString());
    }
}
