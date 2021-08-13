package com.yupaopao.mt_utils.util;

import com.yupaopao.platform.common.utils.StringUtil;

/**
 * string工具类
 * Created by zhujianxing on 2021/6/10
 */
public class LocalStringUtils {

    /**
     * 取package的dep层，比如packagePath=com.zhu.test.a,dep=2时，返回com.zhu
     * @param packagePath
     * @param dep
     * @return
     */
    public static String getParentPackage(String packagePath, int dep){
        if(StringUtil.isEmpty(packagePath) || dep < 1){
            return packagePath;
        }

        String[] packageArr = packagePath.trim().split("\\.");
        if(packageArr.length < 2 || packageArr.length <= dep){
            return packagePath;
        }

        StringBuilder result = new StringBuilder();
        int idx = 0;
        while (idx < dep){
            result.append(packageArr[idx]);
            if(idx != dep - 1){
                result.append(".");
            }
            idx++;
        }
        return result.toString();
    }
}
