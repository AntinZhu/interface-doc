package com.yupaopao.mt_utils.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.collect.Sets;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.Type;
import com.yupaopao.platform.common.dto.Response;
import com.yupaopao.platform.common.utils.ClassUtil;
import com.yupaopao.platform.common.utils.CommonDateUtil;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 类转json字符串的工具类
 * Created by zhujianxing on 2021/5/21
 */
public class JsonUtils {

    private static final Set<String> IGNORE_PROPERTIES = Sets.newHashSet("serialVersionUID");
    private static final Map<String, Object> RESPONSE_DEFAULT_VALUE_MAP = new HashMap<>();
    static {
        RESPONSE_DEFAULT_VALUE_MAP.put("code", "8000");
        RESPONSE_DEFAULT_VALUE_MAP.put("msg", "SUCCESS");
        RESPONSE_DEFAULT_VALUE_MAP.put("tid", "");
        RESPONSE_DEFAULT_VALUE_MAP.put("success", true);
    }

    /**
     * 将对象格式化
     * @param obj
     * @return
     */
    public static String format(Object obj){
        return JSON.toJSONString(obj, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue, SerializerFeature.WriteDateUseDateFormat);
    }

    /**
     * 传入类型信息，生成默认值，如果是自定义类，就生成json，其他基本数据类型，生成对应默认值
     * @param parentClassDoc
     * @param parentType
     * @param type
     * @return
     */
    public static Object generateDefaultValue(ClassDoc parentClassDoc, Type parentType, Type type){
        return generateDefaultValue(parentClassDoc, parentType, type, 1);
    }

    public static Object generateDefaultValue(ClassDoc parentClassDoc, Type parentType, Type type, int dep){
        if(type.isPrimitive()){
            // 基础数据类型
            return primitiveValue(type.qualifiedTypeName());
        }

        Type genericType = JavaDocUtils.findGenericType(parentClassDoc, parentType, type.qualifiedTypeName());
        if(genericType != null){
            type = genericType;
        }
        Class clazz = null;
        try {
            clazz = JsonUtils.class.getClassLoader().loadClass(type.qualifiedTypeName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("加载类失败，class:" + type.qualifiedTypeName());
        }

        if(isWrapClass(clazz)){
            // 基础数据类型对应的包装类
            return ClassUtil.getDefaultValue(getWrapClass(clazz));
        }else{
            if(Number.class.isAssignableFrom(clazz)){
                return 0;
            }
            if(String.class.equals(clazz)){
                return "value";
            }
            if(Date.class.isAssignableFrom(clazz)){
                return CommonDateUtil.formatDate(new Date(), CommonDateUtil.STRIKE_DATE_TIME);
            }

            if(Set.class.isAssignableFrom(clazz) || List.class.isAssignableFrom(clazz)){
                JSONArray array = new JSONArray();
                array.add(generateDefaultValue(parentClassDoc, parentType, type.asParameterizedType().typeArguments()[0], dep + 1));
                return array;
            }
            if(Map.class.isAssignableFrom(clazz)){
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("key", generateDefaultValue(parentClassDoc, parentType, type.asParameterizedType().typeArguments()[1], dep + 1));
                return jsonObject;
            }
            if(clazz.isEnum()){
                Object[] enums = clazz.getEnumConstants();
                if(enums == null || enums.length == 0){
                    return "";
                }else{
                    try {
                        Method nameMethod = clazz.getMethod("name");
                        return nameMethod.invoke(enums[0]);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return "";
                    }
                }
            }
        }
        // 其他
        return toJsonObject(type, dep + 1);
    }

    /**
     * 对象，生成json话需要的map
     * @param type
     * @param dep
     * @return
     */
    private static Map<String, Object> toJsonObject(Type type, int dep){
        if(dep > 10){
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        fillField(type, result, dep);
        return result;
    }

    /**
     * 填充对象的属性值
     * @param type
     * @param result
     * @param dep
     */
    private static void fillField(Type type,  Map<String, Object> result, int dep){
        ClassDoc classDoc = type.asClassDoc();
        for (FieldDoc field : classDoc.fields(false)) {
            if(IGNORE_PROPERTIES.contains(field.name())){
                continue;
            }

            Object defaultValue;
            if(Response.class.getName().equals(type.qualifiedTypeName())){
                defaultValue = RESPONSE_DEFAULT_VALUE_MAP.get(field.name());
                if(defaultValue == null){
                    defaultValue = generateDefaultValue(classDoc, type, field.type(), dep + 1);
                }
            }else{
                if(field.type().getElementType() != null){
                    JSONArray array = new JSONArray();
                    array.add(generateDefaultValue(classDoc, type, field.type().getElementType(), dep + 1));
                    defaultValue = array;
                }else{
                    defaultValue = generateDefaultValue(classDoc, type, field.type(), dep + 1);
                }
            }

            result.put(field.name(), defaultValue);
        }

        if(type.asClassDoc().superclass() != null){
            fillField(type.asClassDoc().superclassType(), result, dep);
        }
    }

    /**
     * 基础数据默认值
     * @param type
     * @return
     */
    private static Object primitiveValue(String type){
        switch (type){
            case "boolean":
                return false;
            case "byte":
                return "a";
            case "double":
            case "float":
                return 0.0;
            case "int":
            case "long":
            case "short":
                return 0;
            case "void":
                return "";
            default:
                throw new RuntimeException("unknown primitive type for " + type);
        }
    }

    /**
     * 是否值包装类
     * @param type
     * @return
     */
    public static boolean isWrapClass(Type type){
        Class clazz = null;
        try {
            clazz = JsonUtils.class.getClassLoader().loadClass(type.qualifiedTypeName());
        } catch (ClassNotFoundException e) {
            return false;
        }

        return isWrapClass(clazz);
    }

    /**
     * 是否是包装类
     * @param clz
     * @return
     */
    public static boolean isWrapClass(Class clz) {
        Class wrapClazz = getWrapClass(clz);
        if(wrapClazz == null){
            return false;
        }
        return wrapClazz.isPrimitive();
    }

    /**
     * 获取包装类对应的类型
     * @param clz
     * @return
     */
    public static Class getWrapClass(Class clz) {
        try {
            return ((Class) clz.getField("TYPE").get(null));
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        System.out.println(Date.class.isAssignableFrom(Timestamp.class));
    }
}
