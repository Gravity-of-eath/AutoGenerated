package com.yps.compiler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//添加元注解
// @Target(ElementType.TYPE)   //接口、类、枚举、注解
@Target(ElementType.TYPE)
//注解的生命周期
//RetentionPolicy.CLASS   编译阶段
@Retention(RetentionPolicy.CLASS)
public @interface TargetService {
    Class name();

    boolean initOnLaunch() default false;
}
