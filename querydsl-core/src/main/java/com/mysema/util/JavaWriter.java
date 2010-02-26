/*
 * Copyright (c) 2009 Mysema Ltd.
 * All rights reserved.
 * 
 */
package com.mysema.util;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.collections15.Transformer;
import org.apache.commons.lang.StringEscapeUtils;

import com.mysema.commons.lang.Assert;

/**
 * @author tiwe
 *
 */
public final class JavaWriter implements Appendable, CodeWriter{
        
    private static final String COMMA = ", ";
    
    private final Appendable appendable;
    
    private String indent = "";
    
    private String type;
    
    public JavaWriter(Appendable appendable){
        this.appendable = Assert.notNull(appendable);
    }

    @Override
    public CodeWriter annotation(Annotation annotation) throws IOException {
        append(indent).append("@" + annotation.annotationType().getName()).append("(");
        boolean first = true;        
        for (Method method : annotation.annotationType().getDeclaredMethods()){
            if (!first){
                append(COMMA);
            }
            append(method.getName()+"=");
            try {
                Object value = method.invoke(annotation);
                annotationConstant(value);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            first = false;
        }        
        return append(")").nl();
     }
    
    
    @SuppressWarnings("unchecked")
    private void annotationConstant(Object value) throws IOException{
         if (value instanceof Class){
             Class<?> clazz = (Class)value;
             if (!clazz.getPackage().getName().equals("java.lang")){
                 append(clazz.getName()+".class");
             }else{
                 append(clazz.getSimpleName()+".class");
             }
         }else if (value instanceof Number){
             append(value.toString());
         }else if (value instanceof Enum){
             Enum enumValue = (Enum)value;
             append(enumValue.getDeclaringClass().getName()+"."+enumValue.name());
         }else if (value instanceof String){
             append("\"" + StringEscapeUtils.escapeJava(value.toString())+"\"");
         }else{
             throw new IllegalArgumentException("Unsupported annotation value : " + value);
         }
     }
 
    @Override
    public JavaWriter append(char c) throws IOException {
        appendable.append(c);
        return this;
    }

    @Override
    public JavaWriter append(CharSequence csq) throws IOException {
        appendable.append(csq);
        return this;
    }

    @Override
    public JavaWriter append(CharSequence csq, int start, int end) throws IOException {
        appendable.append(csq, start, end);
        return this;
    }

    @Override
    public CodeWriter beginClass(String simpleName, String superClass, String... interfaces) throws IOException{
        append(indent + "public class " + simpleName);
        if (superClass != null){
            append(" extends " + superClass);
        }
        if (interfaces.length > 0){
            append(" implements ").params(interfaces);
        }
        append(" {\n\n");
        goIn();
        
        type = simpleName;
        if (type.contains("<")){
            type = type.substring(0, type.indexOf('<'));
        }
        return this;
    }
 
    @Override
    public <T> CodeWriter beginConstructor(Collection<T> parameters, Transformer<T,String> transformer) throws IOException {
        append(indent + "public " + type + "(").params(parameters, transformer).append(") {\n");
        return goIn();        
    }
    
    @Override
    public CodeWriter beginConstructor(String... parameters) throws IOException{
        append(indent + "public " + type + "(").params(parameters).append(") {\n");
        return goIn();
    }
    
    @Override
    public CodeWriter beginInterface(String simpleName, String... interfaces) throws IOException {
        append(indent + "public interface " + simpleName);
        if (interfaces.length > 0){
            append(" extends ").params(interfaces);
        }
        append(" {\n\n");
        goIn();
        
        type = simpleName;
        if (type.contains("<")){
            type = type.substring(0, type.indexOf('<'));
        }
        return this;
        
    }
 
    @Override
    public CodeWriter beginLine(String... segments) throws IOException {
        append(indent);
        for (String segment : segments){
            append(segment);
        }
        return this;
    }
    
    @Override
    public <T> CodeWriter beginMethod(String returnType, String methodName, Collection<T> parameters, Transformer<T, String> transformer) throws IOException {
        append(indent + "public " + returnType + " " + methodName + "(").params(parameters, transformer).append(") {\n");
        return goIn();
    }
    
    @Override
    public CodeWriter beginMethod(String returnType, String methodName, String... args) throws IOException{
        append(indent + "public " + returnType + " " + methodName + "(").params(args).append(") {\n");
        return goIn();
    }

    @Override
    public <T> CodeWriter beginStaticMethod(String returnType, String methodName, Collection<T> parameters, Transformer<T, String> transformer) throws IOException {
        append(indent + "public static " + returnType + " " + methodName + "(").params(parameters, transformer).append(") {\n");
        return goIn();        
    }

    @Override
    public CodeWriter end() throws IOException{
        goOut();
        return line("}\n");
    }
 
    private CodeWriter goIn(){
        indent += "    ";
        return this;
    }

    private CodeWriter goOut(){
        if (indent.length() >= 4){
            indent = indent.substring(0, indent.length() - 4);
        }
        return this;
    }
    
    @Override
    public CodeWriter imports(Class<?>... imports) throws IOException{
        for (Class<?> cl : imports){
            line("import " + cl.getName() + ";");
        }
        return this;
    }

    @Override
    public CodeWriter imports(Package... imports) throws IOException {
        for (Package p : imports){
            line("import " + p.getName() + ".*;");
        }
        return this;
    }
    
    @Override
    public CodeWriter javadoc(String... lines) throws IOException {
        line("/**");
        for (String line : lines){
            line(" * " + line);
        }
        return line(" */");
    }

    @Override
    public String join(String prefix, String suffix, Iterable<String> args) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String arg : args){
            if (!first){
                builder.append(COMMA);
            }
            builder.append(prefix).append(arg).append(suffix);
            first = false;
        }
        return builder.toString();
    }

    @Override
    public String join(String prefix, String suffix, String... args) {
        return join(prefix, suffix, Arrays.asList(args));
    }
    
    @Override
    public CodeWriter line(String... segments) throws IOException{
        append(indent);
        for (String segment : segments){
            append(segment);
        }        
        return append("\n");
    }

    @Override
    public CodeWriter lines(String... lines) throws IOException{
        for (String line : lines){
            line(line);
        }
        return this;
    }

    @Override
    public CodeWriter nl() throws IOException {
        return append("\n");        
    }

    @Override
    public CodeWriter packageDecl(String packageName) throws IOException{
        return line("package " + packageName + ";");
    }

    private <T> CodeWriter params(Collection<T> parameters, Transformer<T,String> transformer) throws IOException{
        boolean first = true;
        for (T param : parameters){
            if (!first){
                append(COMMA);
            }
            append(transformer.transform(param));
            first = false;
        }
        return this;
    }

    private JavaWriter params(String... params) throws IOException{
        for (int i = 0; i < params.length; i++){
            if (i > 0){
                append(COMMA);
            }
            append(params[i]);
        }
        return this;
    }

    private CodeWriter stmt(String stmt) throws IOException{
        return line(stmt + ";").nl();
    }
    
    @Override
    public CodeWriter privateStaticFinal(String type, String name, String value) throws IOException {
        return stmt("private static final " + type + " " + name + " = " + value);
    }

    @Override
    public CodeWriter protectedField(String type, String name) throws IOException {
        return stmt("protected " + type + " " + name);        
    }

    @Override
    public CodeWriter publicFinal(String type, String name) throws IOException {
        return stmt("public final " + type + " " + name);        
    }

    @Override
    public CodeWriter publicFinal(String type, String name, String value) throws IOException {
        return stmt("public final " + type + " " + name + " = " + value);
    }

    @Override
    public CodeWriter publicStaticFinal(String type, String name, String value) throws IOException {
        return stmt("public static final " + type + " " + name + " = " + value);
    }

    @Override
    public CodeWriter staticimports(Class<?>... imports) throws IOException{
        for (Class<?> cl : imports){
            line("import static " + cl.getName() + ".*;");
        }
        return this;
    }
    
    @Override
    public CodeWriter suppressWarnings(String type) throws IOException{
        return line("@SuppressWarnings(\"" + type +"\")");
    }

}
