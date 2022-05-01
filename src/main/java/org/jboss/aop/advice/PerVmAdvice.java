/*
  * JBoss, Home of Professional Open Source
  * Copyright 2005, JBoss Inc., and individual contributors as indicated
  * by the @authors tag. See the copyright.txt in the distribution for a
  * full listing of individual contributors.
  *
  * This is free software; you can redistribute it and/or modify it
  * under the terms of the GNU Lesser General Public License as
  * published by the Free Software Foundation; either version 2.1 of
  * the License, or (at your option) any later version.
  *
  * This software is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  * Lesser General Public License for more details.
  *
  * You should have received a copy of the GNU Lesser General Public
  * License along with this software; if not, write to the Free
  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
  */
package org.jboss.aop.advice;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;

import org.jboss.aop.AspectManager;
import org.jboss.aop.instrument.OptimizedMethodInvocations;
import org.jboss.aop.joinpoint.Invocation;
import org.jboss.aop.joinpoint.Joinpoint;
import org.jboss.aop.joinpoint.MethodInvocation;
import org.jboss.aop.joinpoint.MethodJoinpoint;
import org.jboss.aop.util.ReflectToJavassist;

/**
 * Comment
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 60461 $
 */
public class PerVmAdvice
{
   private static long counter = 0;

   public static synchronized Interceptor generateOptimized(Joinpoint joinpoint, AspectManager manager, String adviceName, AspectDefinition a) throws Exception
   {
      Object aspect = manager.getPerVMAspect(a);
      return generateInterceptor(joinpoint, aspect, adviceName);

   }

   public static Interceptor generateInterceptor(Joinpoint joinpoint, Object aspect, String adviceName) throws Exception
   {
      Method[] methods = aspect.getClass().getMethods();
      ArrayList matches = new ArrayList();
      for (int i = 0; i < methods.length; i++)
      {
         if (methods[i].getName().equals(adviceName)) matches.add(methods[i]);
      }

      // TODO: Need to have checks on whether the advice is overloaded and it is an argument type interception
      if (matches.size() == 1)
      {
         Method method = (Method) matches.get(0);
         if (joinpoint instanceof MethodJoinpoint)
         {
            if (method.getParameterTypes().length == 0 || method.getParameterTypes().length > 1 || !Invocation.class.isAssignableFrom(method.getParameterTypes()[0]))
            {
               return generateArgsInterceptor(aspect, method, joinpoint);
            }
         }
      }

      ClassPool pool = AspectManager.instance().findClassPool(aspect.getClass().getClassLoader());
      CtClass clazz = pool.makeClass("org.jboss.aop.advice." + aspect.getClass().getName() + counter++);
      
      // We need to know whether this Interceptor is actually advice.
      CtClass interceptorInterface = pool.get("org.jboss.aop.advice.Interceptor");
      CtClass abstractAdviceClass = pool.get("org.jboss.aop.advice.AbstractAdvice");
      clazz.setSuperclass(abstractAdviceClass);

      // aspect field
      CtClass aspectClass = pool.get(aspect.getClass().getName());
      CtField field = new CtField(aspectClass, "aspectField", clazz);
      field.setModifiers(javassist.Modifier.PUBLIC);
      clazz.addField(field);
      // getName()
      CtMethod getNameTemplate = interceptorInterface.getDeclaredMethod("getName");
      CtMethod getName = CtNewMethod.make(getNameTemplate.getReturnType(), "getName", getNameTemplate.getParameterTypes(), getNameTemplate.getExceptionTypes(), null, clazz);
      String getNameBody =
      "{ " +
      "   return \"" + aspect.getClass().getName() + "." + adviceName + "\"; " +
      "}";
      getName.setBody(getNameBody);
      getName.setModifiers(javassist.Modifier.PUBLIC);
      clazz.addMethod(getName);

      // invoke
      String invokeBody =
      "public Object invoke(org.jboss.aop.joinpoint.Invocation invocation) throws java.lang.Throwable " +
      "{  ";
      if (matches.size() > 1)
      {
         for (int i = 0; i < matches.size(); i++)
         {
            Method advice = (Method) matches.get(i);
            String param = advice.getParameterTypes()[0].getName();
            invokeBody += "   if (invocation instanceof " + param + ") return aspectField." + adviceName + "((" + param + ")invocation); ";
         }
         invokeBody += "   return (org.jboss.aop.joinpoint.Invocation)null; ";
      }
      else
      {
         Method advice = (Method) matches.get(0);
         String param = advice.getParameterTypes()[0].getName();
         invokeBody += "return aspectField." + adviceName + "((" + param + ")invocation); ";
      }
      invokeBody += "}";
      CtMethod invoke = CtNewMethod.make(invokeBody, clazz);
      invoke.setModifiers(javassist.Modifier.PUBLIC);
      clazz.addMethod(invoke);
      Class iclass = clazz.toClass();

      Interceptor rtn = (Interceptor) iclass.newInstance();
      Field f = iclass.getField("aspectField");
      f.set(rtn, aspect);
      return rtn;
   }

   public static Interceptor generateArgsInterceptor(Object aspect, Method advice, Joinpoint joinpoint) throws Exception
   {
      ClassPool pool = AspectManager.instance().findClassPool(aspect.getClass().getClassLoader());
      CtClass clazz = pool.makeClass("org.jboss.aop.advice." + aspect.getClass().getName() + counter++);

      // We need to know whether this Interceptor is actually advice.
      CtClass interceptorInterface = pool.get("org.jboss.aop.advice.Interceptor");
      clazz.addInterface(interceptorInterface);

      // aspect field
      CtClass aspectClass = pool.get(aspect.getClass().getName());
      CtField field = new CtField(aspectClass, "aspectField", clazz);
      field.setModifiers(javassist.Modifier.PUBLIC);
      clazz.addField(field);
      // getName()
      CtMethod getNameTemplate = interceptorInterface.getDeclaredMethod("getName");
      CtMethod getName = CtNewMethod.make(getNameTemplate.getReturnType(), "getName", getNameTemplate.getParameterTypes(), getNameTemplate.getExceptionTypes(), null, clazz);
      String getNameBody =
      "{ " +
      "   return \"" + aspect.getClass().getName() + "." + advice.getName() + "\"; " +
      "}";
      getName.setBody(getNameBody);
      getName.setModifiers(javassist.Modifier.PUBLIC);
      clazz.addMethod(getName);

      // invoke
      Method method = ((MethodJoinpoint) joinpoint).getMethod();
      String invocationType = null;
      if (AspectManager.optimize)
      {
         invocationType =   OptimizedMethodInvocations.getOptimizedInvocationClassName(method);
      }
      else
      {
         invocationType = MethodInvocation.class.getName();
      }

      StringBuffer invokeBody = new StringBuffer("public Object invoke(org.jboss.aop.joinpoint.Invocation invocation) throws java.lang.Throwable ");
      invokeBody.append("{     ").append(invocationType).append(" typedInvocation = (");
      invokeBody.append(invocationType).append(")invocation; ");
      if (!AspectManager.optimize)
      {
         invokeBody.append("   Object[] arguments = typedInvocation.getArguments();");
      }
      if (advice.getParameterTypes().length > 0 &&
            Invocation.class.isAssignableFrom(advice.getParameterTypes()[0]))
      {
         fillInvocationBody(invokeBody, advice, method);
      }
      else
      {
         fillThreadStackBody(invokeBody, advice, method);
      }
      invokeBody.append('}');
      CtMethod invoke = null;
      try
      {
         invoke = CtNewMethod.make(invokeBody.toString(), clazz);
      }
      catch(CannotCompileException e)
      {
         System.out.println(invokeBody);
         throw e;
      }
      invoke.setModifiers(javassist.Modifier.PUBLIC);
      clazz.addMethod(invoke);
      Class iclass = clazz.toClass();

      Interceptor rtn = (Interceptor) iclass.newInstance();
      Field f = iclass.getField("aspectField");
      f.set(rtn, aspect);
      return rtn;
   }

   private static void fillThreadStackBody(StringBuffer invokeBody, Method advice, Method method) throws Exception
   {
      invokeBody.append("   org.jboss.aop.joinpoint.CurrentInvocation.push(invocation); ");
      invokeBody.append("   try {");
      invokeBody.append("return ($w)aspectField.").append(advice.getName());
      invokeBody.append("(");
      appendParamList(invokeBody, 0, advice.getParameterTypes(), method.getParameterTypes());
      invokeBody.append(");");
      invokeBody.append("   } finally { org.jboss.aop.joinpoint.CurrentInvocation.pop(); }");
   }

   private static void fillInvocationBody(StringBuffer invokeBody, Method advice, Method method)
   {
      invokeBody.append("   return ($w)aspectField.").append(advice.getName());
      invokeBody.append("(typedInvocation, ");
      appendParamList(invokeBody, 1, advice.getParameterTypes(), method.getParameterTypes());
      invokeBody.append(");");
   }

   /**
    * Appends the joinpoint parameter list to <code>code</code>.
    * 
    * @param code             buffer to where generated code is appended
    * @param offset           indicates from which advice parameter index are the
    *                         joinpoint parameters. All advice parameters that 
    *                         come before the <code>offset</code> stand for other
    *                         values, that are not joinpoint parameters.
    * @param adviceParams     list of advice parameter types
    * @param joinPointParams  list of joinpoint parameter types
    */
   private static void appendParamList(StringBuffer code, int offset, Class adviceParams[], Class[] joinPointParams)
   {
      if (adviceParams.length > 0)
      {
         int [] paramIndexes = new int[adviceParams.length];
         boolean[] assignedParams = new boolean[joinPointParams.length];
         for (int i = offset; i < adviceParams.length; i++)
         {
            int j;
            for (j = 0; j < joinPointParams.length; j++)
            {
               if (adviceParams[i].equals(joinPointParams[j]) && !assignedParams[j])
               {
                  break;
               }
            }
            // if didn't find the same type, look for supertypes
            if (j == joinPointParams.length)
            {
               for (j = 0; j < joinPointParams.length; j++)
               {
                  if (adviceParams[i].isAssignableFrom(joinPointParams[j]) && !assignedParams[j])
                     break;
               }
               // didn't find super types either
               if (j == joinPointParams.length)
                  throw new RuntimeException();
            }
            assignedParams[j] = true;
            paramIndexes[i] = j;
         }
         if (AspectManager.optimize)
         {
            code.append("typedInvocation.arg").append(paramIndexes[offset]);
            for (int i = offset + 1; i < paramIndexes.length; i++)
            {
               code.append(", typedInvocation.arg");
               code.append(paramIndexes[i]);
            }
         }
         else
         {
            code.append(ReflectToJavassist.castInvocationValueToTypeString(
                  adviceParams[offset],
                  "arguments[" + paramIndexes[offset] + "]"));
            for (int i = offset + 1; i < paramIndexes.length; i++)
            {
               code.append(", ");
               code.append(ReflectToJavassist.castInvocationValueToTypeString(
                     adviceParams[i],
                     "arguments[" + paramIndexes[i] + "]"));
            }
         }
      }
   }
}