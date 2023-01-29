package com.yps.compiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.sun.tools.javac.code.Type;
import com.yps.compiler.annotation.AIDL;
import com.yps.compiler.annotation.LISTENER;
import com.yps.compiler.annotation.TargetService;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;


@AutoService(Processor.class)
public class AIDLProcessor extends AbstractProcessor {
    public static final String TAG = "AIDLProcessor-- ";
    //TODO 分离 接口和服务实现的注解处理流程,注册服务需要在manifest中标识 queries 字段

    TypeName iBinderType = ClassName.bestGuess("android.os.IBinder");
    TypeName stringType = ClassName.bestGuess("java.lang.String");
    TypeName binderType = ClassName.bestGuess("android.os.Binder");
    TypeName remoteExceptionType = ClassName.bestGuess("android.os.RemoteException");
    TypeName parcelType = ClassName.bestGuess("android.os.Parcel");
    TypeName serviceType = ClassName.bestGuess("android.app.Service");
    TypeName intentType = ClassName.bestGuess("android.content.Intent");

    public static final String implFiledName = "realImplInstances";
    public static final String implName = "instance";
    public static final String MANIFEST = "AndroidManifest.xml";


    private static List<String> listeners = new ArrayList<>();

    private Elements elementUtils;
    private Filer filer;
    private Messager messager;
    private final String serviceStf = "ProxyService";
    //    private String subPath = "build/intermediates/merged_manifests/debug/processDebugManifest/merged/AndroidManifest.xml";
    private String subPath = "src/main/AndroidManifest.xml";
    private String serviceAssets = "../../apps/base/src/main/assets/service/";
    private FileInputStream fileInputStream;
    private File manifestFile;
    private File servicesPath;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        elementUtils = processingEnvironment.getElementUtils();
        filer = processingEnvironment.getFiler();
        messager = processingEnvironment.getMessager();
        messager.printMessage(Diagnostic.Kind.NOTE, TAG + "-------------processingEnvironment");
        try {
            FileObject serviceNames = filer.getResource(StandardLocation.CLASS_OUTPUT, "", "a");
            String string = serviceNames.toUri().toString();
            String substring = string.substring(6, string.indexOf("build"));
            try {
                manifestFile = new File(substring, subPath);
                servicesPath = new File(substring, serviceAssets);
                messager.printMessage(Diagnostic.Kind.NOTE, TAG + "-------------" + serviceNames.toUri());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        generatedListener(roundEnvironment);
        findInterfaceImpl(roundEnvironment);
        Map<Element, List<AidlMethod>> sources = findAidlInterface(roundEnvironment);
        generateAIDL(sources);
        return true;
    }

    private void writeToConfigFile(String pkgName, String servicePkgName,String interfaceName, String serviceName) {
        try {
            if (!servicesPath.exists()) {
                servicesPath.mkdirs();
            }
            File file = new File(servicesPath, interfaceName);
            file.createNewFile();
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file));
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("pkgName", servicePkgName);
            jsonObject.put("interfaceName", pkgName + "." + interfaceName);
            jsonObject.put("serviceName", serviceName);
            jsonObject.put("proxyName", pkgName + "." + interfaceName + "Stub");
            String format = jsonObject.toString();
            writer.append(format);
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + "---DDDD-----" + format);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void findInterfaceImpl(RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(TargetService.class)) {
            TargetService annotation = element.getAnnotation(TargetService.class);
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + "-----findInterfaceImpl");
            if (annotation == null) {
                continue;
            }
            ClassName name = getClsName(annotation);
            String namess = name.packageName() + name.simpleName();
            String reflectionName = name.reflectionName();
            String qualifiedName = ((TypeElement) element).getQualifiedName().toString();
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + "--reflectionName= " + reflectionName + "--" + qualifiedName + "  namess= " + namess + "----> is  InterfaceImpl");
            generatedService(name.packageName(), qualifiedName, reflectionName + "ProxyService", name.simpleName());
        }
    }

    private Map<Element, List<AidlMethod>> findAidlInterface(RoundEnvironment roundEnvironment) {
        Map<Element, List<AidlMethod>> sources = new HashMap<>();
        for (Element e : roundEnvironment.getElementsAnnotatedWith(AIDL.class)) {
            List<AidlMethod> methods = new ArrayList<>();
            sources.put(e, methods);
            List<? extends Element> list = elementUtils.getAllMembers((TypeElement) e);
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + "-----" + e.asType().toString() + "----> is Aidl listener");
            for (Element ee : list) {
                boolean isAbstract = ee.getModifiers().contains(Modifier.ABSTRACT);
                if (isAbstract) {
                    methods.add(createAidlMethod(ee));
                }
            }
        }
        return sources;
    }

    private void generatedListener(RoundEnvironment roundEnvironment) {
        Map<Element, List<AidlMethod>> sources = new HashMap<>();
        for (Element e : roundEnvironment.getElementsAnnotatedWith(LISTENER.class)) {
            List<AidlMethod> methods = new ArrayList<>();
            sources.put(e, methods);
            List<? extends Element> list = elementUtils.getAllMembers((TypeElement) e);
            for (Element ee : list) {
                boolean isAbstract = ee.getModifiers().contains(Modifier.ABSTRACT);
                if (isAbstract) {
                    methods.add(createAidlMethod(ee));
                }
            }
            listeners.add(e.toString());
        }
        generateListenerProxy(sources);
    }

    private AidlMethod createAidlMethod(Element e) {
        AidlMethod aMethod = new AidlMethod();
        Type.MethodType mt = (Type.MethodType) e.asType();
        Type retType = mt.getReturnType();
        aMethod.name = e.getSimpleName() + "";
        if (retType instanceof Type.JCPrimitiveType) {
            aMethod.retCls = getPrimitiveType(retType);
        } else if (retType instanceof Type.ArrayType) {
            aMethod.retCls = getPrimitiveType(retType);
        } else {
            if (!"void".equals(retType + "")) {
                aMethod.retClsName = ClassName.bestGuess(retType + "");
            }
        }

        List<Type> types = mt.getParameterTypes();
        List<ParamData> params = new ArrayList<>();
        for (Type t : types) {
            ParamData p = new ParamData();
            if (t instanceof Type.JCPrimitiveType) {
                p.cls = getPrimitiveType(t);
            } else if (t instanceof Type.ClassType) {
                Type.ClassType ct = (Type.ClassType) t;
                String cname = ct + "";
                p.clsName = ClassName.bestGuess(cname);
                p.ct = ct;
                messager.printMessage(Diagnostic.Kind.NOTE, TAG + "----> createAidlMethod" + ct.toString());
//                if ("java.lang.String".equals(cname) || isParcelable(ct)) {
//                } else {
//                    ClassName className = ClassName.bestGuess(ct.toString());
//                    throw new RuntimeException("--unSupport param:" + t + ",in method:" + mt + " source:" + e + " className= " + className);
//                }
            } else {
                throw new RuntimeException("unSupport param:" + t + ",in method:" + mt + " source:" + e);
            }
            params.add(p);
        }
        aMethod.params = params;
        System.out.println(aMethod);
        return aMethod;
    }

    private ClassName getClsName(TargetService annotation) {
        if (annotation == null) {
            return null;
        }
        ClassName className = null;
        try {
            Class<?> presenter = annotation.name();
            className = ClassName.get(presenter);
            boolean b = annotation.initOnLaunch();
        } catch (MirroredTypeException e) {
            e.printStackTrace();
            //捕捉MirroredTypeException异常
            //在该异常中, 通过异常获取TypeMirror
            //通过TypeMirror获取TypeName
            TypeMirror typeMirror = e.getTypeMirror();
            if (typeMirror != null) {
                TypeName typeName = ClassName.get(typeMirror);
                if (typeName instanceof ClassName) {
                    className = (ClassName) typeName;
                }
            }
        }
        return className;
    }

    private boolean isParcelable(Type.ClassType ct) {
        for (Type t : ct.interfaces_field) {
            if ("android.os.Parcelable".equals(t.toString())) return true;
        }
        return false;
    }

    private Class getPrimitiveType(Type t) {
        Class cls = null;
        switch (t.getKind()) {
            case INT:
                cls = int.class;
                break;
            case BYTE:
                cls = byte.class;
                break;
            case LONG:
                cls = long.class;
                break;
            case DOUBLE:
                cls = double.class;
                break;
            case FLOAT:
                cls = float.class;
                break;
            case CHAR:
                cls = char.class;
                break;
        }
        if (cls == null) {
            throw new RuntimeException("unSupport type:" + t.getKind());
        }
        return cls;
    }

    private boolean isPrimitiveType(Type t) {
        switch (t.getKind()) {
            case INT:
            case BYTE:
            case LONG:
            case DOUBLE:
            case FLOAT:
            case CHAR:
                return true;
        }
        return false;
    }

    //  生成实际的功能代理字段
    private void generatedInterfaceFiled(String interfaceName, TypeSpec.Builder builder) {
        ClassName className = ClassName.bestGuess(interfaceName);
        FieldSpec descriptorField = FieldSpec.builder(className, implFiledName, Modifier.PRIVATE/*, Modifier.STATIC, Modifier.FINAL*/)
                .build();
        builder.addField(descriptorField);
        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(className, implFiledName)
                .addStatement("this.$N = $N", implFiledName, implFiledName)
                .build();
        builder.addMethod(constructor);
    }

    //  生成代理方法
    private void generatedOverrideMethodProxy(String interfaceName, AidlMethod am, TypeSpec.Builder builder) {
        MethodSpec.Builder mBuilder = MethodSpec.methodBuilder(am.name)
                .addAnnotation(Override.class)
//                        .addModifiers(Modifier.ABSTRACT)
                .addModifiers(Modifier.PUBLIC);
        if (am.retClsName != null) {
            mBuilder.returns(am.retClsName);
        } else if (am.retCls != null) {
            mBuilder.returns(am.retCls);
        }
        StringBuilder pas = new StringBuilder();
        int count = 0;
        for (ParamData p : am.params) {
            pas.append("var").append(count);
            if (am.params.indexOf(p) != am.params.size() - 1) {
                pas.append(",");
            }
            if (p.clsName != null)
                mBuilder.addParameter(p.clsName, "var" + count);
            else
                mBuilder.addParameter(p.cls, "var" + count);
            count++;
        }
        if (am.hasReturn()) {//返回值
//            isPrimitiveType(am.getRetType())
//            mBuilder.addCode("if($N==null){return $N;}", implFiledName);
            mBuilder.addCode("return this." + implFiledName + "." + am.name + "($N);", pas);
        } else {
//            mBuilder.addCode("if($N==null){return  ;}", implFiledName);
            mBuilder.addCode("this." + implFiledName + "." + am.name + "($N);", pas);
        }
        builder.addMethod(mBuilder.build());
    }

    private void generateAIDL(Map<Element, List<AidlMethod>> sources) {

        String descName = "DESCRIPTOR";
        for (Element e : sources.keySet()) {
            //generate stub class
            List<AidlMethod> methods = sources.get(e);
            TypeSpec.Builder builder = TypeSpec.classBuilder(e.getSimpleName() + "Stub")
                    .addModifiers(Modifier.PUBLIC/*, Modifier.ABSTRACT*/);
            builder.superclass(binderType);
            String pkg = String.valueOf(elementUtils.getPackageOf(e));
            String interfaceName = pkg + "." + e.getSimpleName();
            TypeName interfaceType = ClassName.bestGuess(interfaceName);
            builder.addSuperinterface(interfaceType);

            FieldSpec descriptorField = FieldSpec.builder(stringType, descName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("\"$N\"", interfaceName)
                    .build();
            builder.addField(descriptorField);
            generatedInterfaceFiled(interfaceName, builder);
            MethodSpec asInterfaceMethod = MethodSpec.methodBuilder("asInterface")
                    .addModifiers(Modifier.PUBLIC)
                    .addModifiers(Modifier.STATIC)
                    .addParameter(iBinderType, "iBinder")
                    .addCode("return new Proxy(iBinder);\n")
                    .returns(interfaceType)
                    .build();
            builder.addMethod(asInterfaceMethod);
            int index = 0;
            //add abstract methods for interface
            //override onTransact method and process data
            MethodSpec.Builder onTransactBuilder = MethodSpec.methodBuilder("onTransact")
                    .addAnnotation(Override.class)
                    .addParameter(int.class, "code")
                    .addParameter(parcelType, "data")
                    .addParameter(parcelType, "reply")
                    .addParameter(int.class, "flags")
                    .returns(boolean.class)
                    .addException(remoteExceptionType)
                    .addModifiers(Modifier.PROTECTED);
            StringBuilder onTransactCode = new StringBuilder();
            onTransactCode.append("String descriptor = " + descName + ";\n");
            onTransactCode.append("switch(code){\n");
            for (AidlMethod am : methods) {
                FieldSpec field = FieldSpec.builder(int.class, "TRANSACTION_" + am.name, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("android.os.IBinder.FIRST_CALL_TRANSACTION + " + (index++))
                        .build();
                builder.addField(field);
                messager.printMessage(Diagnostic.Kind.NOTE, TAG + "-----interfaceName" + interfaceName + "----> generateAIDL");
                generatedOverrideMethodProxy(interfaceName, am, builder);//todo------------------------------------------------
                int count = 0;
                onTransactCode.append("case TRANSACTION_" + am.name + ":{\n");
                onTransactCode.append("data.enforceInterface(descriptor);\n");
                StringBuilder params = new StringBuilder();
                for (ParamData p : am.params) {
                    addReadCode(onTransactCode, p, count);
                    params.append("_arg").append(count).append(",");
                    count++;
                }
                if (params.length() > 1) params.deleteCharAt(params.length() - 1);
                if (am.hasReturn()) {//返回值
                    onTransactCode.append(am.retType())
                            .append(" _result = this." + am.name)
                            .append("(")
                            .append(params).append(");\n");
                    onTransactCode.append("reply.writeNoException();\n");
                    onTransactCode.append("reply.write" + am.getRetCapitalizeType() + "(_result);\n");
                } else {
                    onTransactCode.append("this." + am.name)
                            .append("(")
                            .append(params).append(");\n");
                    onTransactCode.append("reply.writeNoException();\n");
                }
                onTransactCode.append("return true;\n").append("}\n");

            }
            onTransactCode.append("default: {\n")
                    .append("return super.onTransact(code, data, reply, flags);\n")
                    .append("}\n}\n");

            onTransactBuilder.addCode(onTransactCode.toString());
            builder.addMethod(onTransactBuilder.build());
            //generate proxy class
            TypeSpec.Builder proxyBuilder = TypeSpec.classBuilder("Proxy")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC);

            FieldSpec mRemoteField = FieldSpec.builder(
                    iBinderType,
                    "mRemote",
                    Modifier.PRIVATE
            ).build();
            proxyBuilder.addField(mRemoteField);
            // add constructor
            MethodSpec constructor = MethodSpec.constructorBuilder()
                    .addParameter(iBinderType, "mRemote")
                    .addStatement("this.$N = $N", "mRemote", "mRemote")
                    .build();
            proxyBuilder.addMethod(constructor);
            //add interface
            proxyBuilder.addSuperinterface(interfaceType);
            //override methods
            overrideProxyMethods(proxyBuilder, methods);

            builder.addType(proxyBuilder.build());

            JavaFile javaFile = JavaFile.builder(pkg, builder.build())
                    .build();

            try {
                javaFile.writeTo(filer);
            } catch (IOException iex) {
                iex.printStackTrace();
            }
        }
    }

    //生成接口中的监听器的AIDL 代理
    private void generateListenerProxy(Map<Element, List<AidlMethod>> sources) {

        String descName = "DESCRIPTOR";
        for (Element e : sources.keySet()) {
            //generate stub class
            List<AidlMethod> methods = sources.get(e);
            TypeSpec.Builder builder = TypeSpec.classBuilder(e.getSimpleName() + "Proxy")
                    .addModifiers(Modifier.PUBLIC);
            builder.superclass(binderType);
            String pkg = String.valueOf(elementUtils.getPackageOf(e));
            String interfaceName = pkg + "." + e.getSimpleName();
            TypeName interfaceType = ClassName.bestGuess(interfaceName);
            builder.addSuperinterface(interfaceType);

            FieldSpec descriptorField = FieldSpec.builder(stringType, descName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("\"$N\"", interfaceName)
                    .build();
            FieldSpec listener = FieldSpec.builder(interfaceType, "listener", Modifier.PRIVATE)
                    .build();
            builder.addField(descriptorField);
            builder.addField(listener);
            MethodSpec asInterfaceMethod = MethodSpec.methodBuilder("asInterface")
                    .addModifiers(Modifier.PUBLIC)
                    .addModifiers(Modifier.STATIC)
                    .addParameter(iBinderType, "iBinder")
                    .addCode("return new Proxy(iBinder);\n")
                    .returns(interfaceType)
                    .build();
            builder.addMethod(asInterfaceMethod);
            MethodSpec asProxy = MethodSpec.methodBuilder("asProxy")
                    .addModifiers(Modifier.PUBLIC)
                    .addModifiers(Modifier.STATIC)
                    .addParameter(interfaceType, "l")
                    .addCode("return new " + e.getSimpleName() + "Proxy(l);\n")
                    .returns(ClassName.bestGuess(e.getSimpleName() + "Proxy"))
                    .build();
            builder.addMethod(asProxy);
            MethodSpec proxyConstructor = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(interfaceType, "listener")
                    .addCode("this.listener=listener;\n")
//                    .returns(void.class)
                    .build();
            builder.addMethod(proxyConstructor);
            int index = 0;
            //add abstract methods for interface
            //override onTransact method and process data
            MethodSpec.Builder onTransactBuilder = MethodSpec.methodBuilder("onTransact")
                    .addAnnotation(Override.class)
                    .addParameter(int.class, "code")
                    .addParameter(parcelType, "data")
                    .addParameter(parcelType, "reply")
                    .addParameter(int.class, "flags")
                    .returns(boolean.class)
                    .addException(remoteExceptionType)
                    .addModifiers(Modifier.PROTECTED);
            StringBuilder onTransactCode = new StringBuilder();
            onTransactCode.append("String descriptor = " + descName + ";\n");
            onTransactCode.append("switch(code){\n");
            for (AidlMethod am : methods) {
                FieldSpec field = FieldSpec.builder(int.class, "TRANSACTION_" + am.name, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("android.os.IBinder.FIRST_CALL_TRANSACTION + " + (index++))
                        .build();
                builder.addField(field);
                MethodSpec.Builder mBuilder = MethodSpec.methodBuilder(am.name)
                        .addAnnotation(Override.class);
//                        .addCode("this.listener.$N($N)", am.name, p.clsName)
//                        .addModifiers(Modifier.PUBLIC);
                if (am.retClsName != null) {
                    mBuilder.returns(am.retClsName);
                } else if (am.retCls != null) {
                    mBuilder.returns(am.retCls);
                }
                int count = 0;
                onTransactCode.append("case TRANSACTION_" + am.name + ":{\n");
                onTransactCode.append("data.enforceInterface(descriptor);\n");
                StringBuilder params = new StringBuilder();
                StringBuilder pas = new StringBuilder();
                for (ParamData p : am.params) {
                    pas.append("var").append(count);
                    if (am.params.indexOf(p) != am.params.size() - 1) {
                        pas.append(",");
                    }
                    addReadCode(onTransactCode, p, count);
                    params.append("_arg").append(count).append(",");
                    if (p.clsName != null)
                        mBuilder.addParameter(p.clsName, "var" + count);
                    else
                        mBuilder.addParameter(p.cls, "var" + count);
                    count++;
                }
                if (params.length() > 1) params.deleteCharAt(params.length() - 1);
                if (am.hasReturn()) {//返回值
                    mBuilder.addCode("return this.listener." + am.name + "($N);", pas);
                    onTransactCode.append(am.retType())
                            .append(" _result = this." + am.name)
                            .append("(")
                            .append(params).append(");\n");
                    onTransactCode.append("reply.writeNoException();\n");
                    onTransactCode.append("reply.write" + am.getRetCapitalizeType() + "(_result);\n");
                } else {
                    mBuilder.addCode("this.listener." + am.name + "($N);", pas);
                    onTransactCode.append("this." + am.name)
                            .append("(")
                            .append(params).append(");\n");
                    onTransactCode.append("reply.writeNoException();\n");
                }
                mBuilder.addModifiers(Modifier.PUBLIC);
                onTransactCode.append("return true;\n").append("}\n");

                builder.addMethod(mBuilder.build());
            }
            onTransactCode.append("default: {\n")
                    .append("return super.onTransact(code, data, reply, flags);\n")
                    .append("}\n}\n");

            onTransactBuilder.addCode(onTransactCode.toString());
            builder.addMethod(onTransactBuilder.build());
            //generate proxy class
            TypeSpec.Builder proxyBuilder = TypeSpec.classBuilder("Proxy")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC);

            FieldSpec mRemoteField = FieldSpec.builder(
                    iBinderType,
                    "mRemote",
                    Modifier.PRIVATE
            ).build();
            proxyBuilder.addField(mRemoteField);
            // add constructor
            MethodSpec constructor = MethodSpec.constructorBuilder()
                    .addParameter(iBinderType, "mRemote")
                    .addStatement("this.$N = $N", "mRemote", "mRemote")
                    .build();
            proxyBuilder.addMethod(constructor);
            //add interface
            proxyBuilder.addSuperinterface(interfaceType);
            //override methods
            overrideProxyMethods(proxyBuilder, methods);

            builder.addType(proxyBuilder.build());

            JavaFile javaFile = JavaFile.builder(pkg, builder.build())
                    .build();

            try {
                javaFile.writeTo(filer);
            } catch (IOException iex) {
                iex.printStackTrace();
            }
        }
    }

    //生成对应的服务
    private void generatedService(String pkgName, String serviceName, String action, String interfaceName) {
        //TODO 自动生成对应的服务
        TypeSpec.Builder builder = TypeSpec.classBuilder(interfaceName + serviceStf)
                .addModifiers(Modifier.PUBLIC);
        FieldSpec descriptorField = FieldSpec.builder(iBinderType, implName, Modifier.PRIVATE/*, Modifier.STATIC, Modifier.FINAL*/)
                .build();
        builder.addField(descriptorField);
        builder.superclass(serviceType);
        MethodSpec onBindMethod = MethodSpec.methodBuilder("onBind")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(intentType, "intent")
                .returns(iBinderType)
                .addCode("return $N;", implName).build();
        builder.addMethod(onBindMethod);
        MethodSpec onCreateMethod = MethodSpec.methodBuilder("onCreate")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class)
                .addCode("super.onCreate();\n this.$N=new $N(new $N());", implName, interfaceName + "Stub", serviceName).build();
        builder.addMethod(onCreateMethod);
        JavaFile javaFile = JavaFile.builder(pkgName, builder.build())
                .build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException iex) {
            iex.printStackTrace();
        }
        messager.printMessage(Diagnostic.Kind.NOTE, TAG + "generatedService----------->pkgName= " + pkgName + "  serviceName= " + serviceName + "  action= " + action + "  interfaceName+ " + interfaceName);
        String servicePkgName = regisServiceToManifest(action);
        writeToConfigFile(pkgName,servicePkgName, interfaceName, action);

    }

    //注册生成的服务到AndroidManifest.xml
    private String regisServiceToManifest(String service) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
            fileInputStream = new FileInputStream(manifestFile);
            Document document = builder.parse(fileInputStream);
            NodeList application = document.getElementsByTagName("application");
            if (application != null && application.getLength() >= 1) {
                Node node = application.item(0);
                NodeList childNodes = node.getChildNodes();
                for (int i = 0; i < childNodes.getLength(); i++) {//排除重复的
                    Node item = childNodes.item(i);
                    if ("service".equals(item.getNodeName())) {
                        NamedNodeMap map = item.getAttributes();
                        Node namedItem = map.getNamedItem("android:name");
                        if (service.equals(namedItem.getNodeValue())) {
                            messager.printMessage(Diagnostic.Kind.NOTE, TAG + "-------------is registed " + namedItem.getNodeValue());
                            return null;
                        }
                    }
                }
                messager.printMessage(Diagnostic.Kind.NOTE, TAG + "----------->serviceNames .size  = " + service);
                org.w3c.dom.Element serviceElement = document.createElement("service");
                serviceElement.setAttribute("android:name", service);
                serviceElement.setAttribute("android:enabled", "true");
                serviceElement.setAttribute("android:exported", "true");
                node.appendChild(serviceElement);
                org.w3c.dom.Element intentFilter = document.createElement("intent-filter");
                serviceElement.appendChild(intentFilter);
                org.w3c.dom.Element action = document.createElement("action");
                action.setAttribute("android:name", service);
                intentFilter.appendChild(action);
                messager.printMessage(Diagnostic.Kind.NOTE, TAG + "----------->service = " + service);
                TransformerFactory transFactory = TransformerFactory.newInstance();
                Transformer transformer = transFactory.newTransformer();
//                transformer.setOutputProperty("indent", "yes");
                org.w3c.dom.Element queries = checkManifestQueriesNode(document);
                String servicePkgName = addQueriesActionNode(document, queries, service);
                DOMSource source = new DOMSource();
                source.setNode(document);
                StreamResult result = new StreamResult();
                result.setOutputStream(new FileOutputStream(manifestFile));
                transformer.transform(source, result);
                return servicePkgName;
            } else {
                messager.printMessage(Diagnostic.Kind.NOTE, TAG + "-------------" + MANIFEST + "----> not find application");
            }
        } catch (ParserConfigurationException | IOException | SAXException | TransformerException e) {
            e.printStackTrace();
        }
        return null;
    }

    //检查queries节点,没有则创建 并初始化<package> 字段
    private org.w3c.dom.Element checkManifestQueriesNode(Document document) {
        messager.printMessage(Diagnostic.Kind.NOTE, TAG + "checkManifestQueriesNode ");
        NodeList manifest = document.getElementsByTagName("manifest");
        Node manifestNode = manifest.item(0);
        NamedNodeMap map = manifestNode.getAttributes();
        Node packageName = map.getNamedItem("package");
        org.w3c.dom.Element querie;
        NodeList queries = document.getElementsByTagName("queries");
        if (queries.getLength() < 1) {
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + "------------- ---->create queries node   packageName= " + packageName.getNodeValue());
            querie = document.createElement("queries");
            org.w3c.dom.Element aPackage = document.createElement("package");
            aPackage.setAttribute("android:name", packageName.getNodeValue());
            querie.appendChild(aPackage);
            manifestNode.appendChild(querie);
        } else {
            querie = (org.w3c.dom.Element) queries.item(0);
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + "------------- ---->fid queries node   !" + querie.getNodeValue());
        }
        return querie;
    }

    private String addQueriesActionNode(Document document, org.w3c.dom.Element queries, String action) {
        if (queries == null) {
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + "------------- ----> queries create failed  action=" + action);
            return null;
        }
        NodeList intents = queries.getElementsByTagName("intent");
        for (int i = 0; i < intents.getLength(); i++) {
            Node intent = intents.item(i);
            if (intent != null) {
                NodeList childNodes = intent.getChildNodes();
                String nodeValue = childNodes.item(0).getNodeValue();
                if (action.equals(nodeValue)) {
                    return null;
                }
            }
        }

        NodeList manifest = document.getElementsByTagName("manifest");
        Node manifestNode = manifest.item(0);
        NamedNodeMap map = manifestNode.getAttributes();
        Node packageName = map.getNamedItem("package");

        org.w3c.dom.Element intent = document.createElement("intent");
        org.w3c.dom.Element action2Add = document.createElement("action");
        action2Add.setAttribute("android:name", action);
        intent.appendChild(action2Add);
        queries.appendChild(intent);
        return packageName.getNodeValue();
    }


    private void addReadCode(StringBuilder onTransactCode, ParamData p, int index) {
        String argName = " _arg" + index;
        if (p.cls != null) {//基本类型
            onTransactCode.append(p.cls + argName + " = data.read" + p.getCapitalizeType() + "();\n");
        }
        if (p.clsName != null) {
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + " p.ct  ----" + p.ct + " ->> " + listeners.contains(p.ct.toString()));
            printListenmer();
            if ("java.lang.String".equals(p.clsName.toString())) {//String
                onTransactCode.append("String" + argName + "  = data.readString();\n");
            } else if (listeners.contains(p.ct.toString())) {
                onTransactCode.append(p.clsName + argName + " = " + p.clsName + "Proxy.asInterface(data.readStrongBinder());\n");
            } else {//Parcelable
                onTransactCode.append(p.clsName + argName + " = data.readParcelable(" + p.clsName + ".class.getClassLoader());\n");
            }
        }
    }

    private void printListenmer() {
        messager.printMessage(Diagnostic.Kind.NOTE, TAG + "----printListenmer  size= " + listeners.size());
        for (String ttt : listeners) {
            messager.printMessage(Diagnostic.Kind.NOTE, TAG + "----printListenmer  " + ttt);
        }
    }

    /**
     * 生成proxy数据transact代码
     *
     * @param proxyBuilder
     * @param methods
     */
    private void overrideProxyMethods(TypeSpec.Builder proxyBuilder, List<AidlMethod> methods) {
        for (AidlMethod m : methods) {
            MethodSpec.Builder mBuilder = MethodSpec.methodBuilder(m.name)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC);
            int count = 0;
            boolean hasReturn = m.retCls != null || m.retClsName != null;

            if (m.retCls != null) mBuilder.returns(m.retCls);
            if (m.retClsName != null) mBuilder.returns(m.retClsName);
            StringBuilder codeBuilder = new StringBuilder();
            if (hasReturn) {
                String typeName = m.retCls != null ? m.retCls + "" : m.retClsName + "";
                String initValue = "0";
                if (m.retCls == boolean.class) initValue = "false";
                if (m.retClsName != null) initValue = "null";
                codeBuilder.append(typeName + " _result = " + initValue + ";\n");
            }
            codeBuilder.append("android.os.Parcel _data = android.os.Parcel.obtain();\n");
            codeBuilder.append("android.os.Parcel _reply = android.os.Parcel.obtain();\n");
            codeBuilder.append("try{\n");
            codeBuilder.append("_data.writeInterfaceToken(DESCRIPTOR);\n");
            for (ParamData p : m.params) {
                String argName = "var" + count++;

                if (p.clsName != null) {
                    String cName = p.clsName.toString();
                    mBuilder.addParameter(p.clsName, argName);
                    if ("java.lang.String".equals(cName)) {
                        codeBuilder.append("_data.writeString(" + argName + ");\n");
                    } else if (listeners.contains(p.ct.toString())) {
                        codeBuilder.append("_data.writeStrongBinder((((" + argName + "!=null))?(" + p.clsName + "Proxy.asProxy(" + argName + ") ):(null)));\n");
                    } else {
                        codeBuilder.append("_data.writeParcelable(" + argName + ",0);\n");
                    }
                } else if (p.cls != null) {
                    mBuilder.addParameter(p.cls, argName);
                    addWriteStatement(codeBuilder, p.cls, argName);
                }
            }
            String code = "TRANSACTION_" + m.name;
            codeBuilder.append("mRemote.transact(" + code + ", _data, _reply, 0);\n");
            codeBuilder.append("_reply.readException();\n");
            if (hasReturn) {//返回值
                if (m.retCls != null) {
                    String type = m.retCls + "";
                    String finalType = type.substring(0, 1).toUpperCase() + type.substring(1);
                    codeBuilder.append("_result = _reply.read" + finalType + "();");
                }
                if (m.retClsName != null) {
                    String cName = m.retClsName.toString();
                    if ("java.lang.String".equals(cName)) {
                        codeBuilder.append("_result = _data.readString();");
                    } else if ("android.os.Parcelable".equals(cName)) {
                        codeBuilder.append("_result = _data.readParcelable();\n");
                    }
                }
            }

            codeBuilder.append("}catch(Exception e){e.printStackTrace();}finally{\n");
            codeBuilder.append("_reply.recycle();\n");
            codeBuilder.append("_data.recycle();\n");
            codeBuilder.append("}\n");
            if (hasReturn) codeBuilder.append("return _result;\n");
            mBuilder.addCode(codeBuilder.toString());
            proxyBuilder.addMethod(mBuilder.build());
        }
    }

    private void addWriteStatement(StringBuilder mBuilder, Class cls, String name) {
        if (cls == int.class) {
            mBuilder.append("_data.writeInt(" + name + ");\n");
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new LinkedHashSet<>();
        annotations.add(AIDL.class.getCanonicalName());
        annotations.add(LISTENER.class.getCanonicalName());
        annotations.add(TargetService.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
