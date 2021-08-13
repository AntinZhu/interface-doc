package com.yupaopao.mt_utils.util;

import java.io.InputStream;

/**
 * 读取当前jar包中的文件
 * Created by zhujianxing on 2020/4/21.
 */
public class QingJarUtils {

    public static final String readFromJar(String filePath){
        // 标准输入流
        InputStream in = null;
        try{
            in = QingJarUtils.class.getResourceAsStream("/" + filePath);
            byte[] buf = new byte[in.available()];
            in.read(buf);

            return new String(buf, "utf-8");
        }catch (Exception e){
            throw new RuntimeException("read from jar fail, filePath:" + filePath, e);
        }finally {
            if(in  != null){
                try{
                    in.close();
                }catch(Exception e){
                    // ignore
                }
            }
        }
    }
}
