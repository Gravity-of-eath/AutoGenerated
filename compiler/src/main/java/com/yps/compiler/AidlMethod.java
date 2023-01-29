package com.yps.compiler;

import com.squareup.javapoet.ClassName;

import java.util.List;

public class AidlMethod {
    public Class retCls;//PrimitiveType
    public ClassName retClsName;
    public List<ParamData> params;
    public int code;
    public String name;

    public boolean hasReturn() {
        return retCls != null || retClsName != null;
    }

    public String retType() {
        if (retCls != null) return retCls + "";
        return retClsName + "";
    }

    public String getRetType() {//首字母大写的
        if (retCls != null) return retCls.toString();
        String text = retClsName.toString();
        if (!"java.lang.String".equals(text)) {
            text = "android.os.Parcel";
        }
        return text.substring(text.lastIndexOf(".") + 1);
    }

    public String getRetCapitalizeType() {
        String type = getRetType();
        return type.substring(0, 1).toUpperCase() + type.substring(1);
    }

    @Override
    public String toString() {
        return "AidlMethod{" +
                "retCls=" + retCls +
                ", retClsName=" + retClsName +
                ", params=" + params +
                ", code=" + code +
                ", name='" + name + '\'' +
                '}';
    }
}
