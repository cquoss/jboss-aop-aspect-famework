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
package org.jboss.aop.instrument;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.NotFoundException;

import org.jboss.aop.GeneratedClassAdvisor;
import org.jboss.aop.MethodInfo;
import org.jboss.aop.advice.AdviceMethodProperties;
import org.jboss.aop.joinpoint.MethodInvocation;
import org.jboss.aop.util.ReflectToJavassist;

/** Creates the Joinpoint invocation replacement classes used with Generated advisors
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision$
 */
public class MethodJoinPointGenerator extends JoinPointGenerator
{
   private static final Class INVOCATION_TYPE = MethodInvocation.class;
   private static final CtClass INVOCATION_CT_TYPE;
   static
   {
      try
      {
         INVOCATION_CT_TYPE = ReflectToJavassist.classToJavassist(INVOCATION_TYPE);
      }
      catch (NotFoundException e)
      {
         throw new RuntimeException(e);
      }
   }

   public MethodJoinPointGenerator(GeneratedClassAdvisor advisor, MethodInfo info)
   {
      super(advisor, info);
   }

   protected void initialiseJoinPointNames()
   {
      joinpointClassName = getInfoClassName(
               advisedMethodName(), 
               methodHash());
      
      joinpointFieldName = getInfoFieldName(
               advisedMethodName(), 
               methodHash());
   }
   
   private String advisedMethodName()
   {
      return ((MethodInfo)info).getAdvisedMethod().getName();
   }
   
   private long methodHash()
   {
      return ((MethodInfo)info).getHash();
   }
      
   protected boolean isVoid()
   {
      return ((MethodInfo)info).getUnadvisedMethod().getReturnType().equals(Void.TYPE);
   }

   protected Class getReturnType()
   {
      if (isVoid()) return null;
      return ((MethodInfo)info).getUnadvisedMethod().getReturnType();
   }

   protected AdviceMethodProperties getAdviceMethodProperties(AdviceSetup setup)
   {
      Method method = ((MethodInfo)info).getAdvisedMethod();
      return new AdviceMethodProperties(
            setup.getAspectClass(), 
            setup.getAdviceName(), 
            info.getClass(), 
            INVOCATION_TYPE, 
            method.getReturnType(), 
            method.getParameterTypes(), 
            method.getExceptionTypes());
   }
   

   protected boolean hasTargetObject()
   {
      return !Modifier.isStatic(((MethodInfo)info).getAdvisedMethod().getModifiers()); 
   }

   protected String getInfoName()
   {
      return MethodExecutionTransformer.getMethodInfoFieldName(
            ((MethodInfo)info).getAdvisedMethod().getName(), ((MethodInfo)info).getHash());
   }

   protected static CtClass createJoinpointBaseClass(
         GeneratedAdvisorInstrumentor instrumentor, 
         CtClass advisedClass, 
         CtMethod targetMethod,
         String miname, 
         String originalMethodName, 
         String wrappedMethodName, 
         long hash) throws CannotCompileException, NotFoundException
   {
      instrumentor.addJoinPointGeneratorFieldToGenAdvisor(
            getJoinPointGeneratorFieldName(originalMethodName, hash));

      BaseClassGenerator factory = new BaseClassGenerator(instrumentor, advisedClass, targetMethod, miname, originalMethodName, wrappedMethodName, hash);
      return factory.generate();
   }

   protected String getJoinPointGeneratorFieldName()
   {
      return getJoinPointGeneratorFieldName(advisedMethodName(), methodHash());
   }
   
   protected static String getInfoFieldName(String methodName, long hash)
   {
      return JOINPOINT_FIELD_PREFIX + MethodExecutionTransformer.getMethodNameHash(methodName, hash);
   }

   private static String getInfoClassName(String methodName, long hash)
   {
      return JOINPOINT_CLASS_PREFIX + MethodExecutionTransformer.getMethodNameHash(methodName, hash);
   }
   
   protected static String getJoinPointGeneratorFieldName(String methodName, long hash)
   {
      return GENERATOR_PREFIX + MethodExecutionTransformer.getMethodNameHash(methodName, hash);
   }
   
   private static class BaseClassGenerator
   {
      GeneratedAdvisorInstrumentor instrumentor; 
      CtClass advisedClass; 
      CtMethod advisedMethod;
      String miname; 
      String originalMethodName; 
      String wrappedMethodName; 
      long hash;
      boolean hasTargetObject;
      
      CtClass jp;
      CtMethod invokeJoinpointMethod;
      CtConstructor publicConstructor;
      CtConstructor protectedConstructor;
      CtField targetField;
      CtClass[] originalParams;
      CtClass[] params;
      CtClass methodInfoClass;
      
      BaseClassGenerator(GeneratedAdvisorInstrumentor instrumentor,  CtClass advisedClass, 
                        CtMethod targetMethod, String miname, 
                       String originalMethodName, String wrappedMethodName,long hash) throws NotFoundException
      {
         this.instrumentor = instrumentor;
         this.advisedClass = advisedClass;
         this.advisedMethod = targetMethod;
         this.miname = miname;
         this.originalMethodName = originalMethodName;
         this.wrappedMethodName = wrappedMethodName;
         this.hash = hash;
         this.originalParams = targetMethod.getParameterTypes();
         this.params = GeneratedAdvisorMethodExecutionTransformer.addTargetToParamsForNonStaticMethod(advisedClass, targetMethod);
         methodInfoClass = instrumentor.forName(MethodExecutionTransformer.METHOD_INFO_CLASS_NAME);
         hasTargetObject = !Modifier.isStatic(advisedMethod.getModifiers());
      }

      protected CtClass generate() throws CannotCompileException, NotFoundException
      {
         jp = setupClass();
         addArgumentsFieldsAndAccessors();      
         if (hasTargetObject)
         {
            addTypedTargetField();
         }
         addInvokeJoinpointMethod();
         addMethodInfoField();
         addDefaultConstructor();
         addPublicConstructor();
         addProtectedConstructor();
         addDispatchMethods();
         
         TransformerCommon.compileOrLoadClass(advisedClass, jp);
         return jp; 
      }

      
      private CtClass setupClass()throws NotFoundException, CannotCompileException
      {
         String className = getInfoClassName(originalMethodName, hash);
         
         //Create inner joinpoint class in advised class, super class is MethodInvocation
         jp = advisedClass.makeNestedClass(className, true);
         int mod = jp.getModifiers();
         jp.setModifiers(mod | Modifier.PUBLIC);
         
         CtClass methodInvocation = INVOCATION_CT_TYPE;
         jp.setSuperclass(methodInvocation);
         addUntransformableInterface(instrumentor, jp);
         
         return jp;
      }

      private void addArgumentsFieldsAndAccessors() throws NotFoundException, CannotCompileException
      {
         OptimizedBehaviourInvocations.addArgumentFieldsToInvocation(jp, originalParams);
         OptimizedBehaviourInvocations.addSetArguments(instrumentor.getClassPool(), jp, originalParams);
         //We might be marshalled so add special get arguments code
         OptimizedBehaviourInvocations.addGetArguments(instrumentor.getClassPool(), jp, originalParams, true);
      }
      
      private void addTypedTargetField()throws CannotCompileException
      {
         targetField = new CtField(advisedClass, TARGET_FIELD, jp);
         jp.addField(targetField);
         targetField.setModifiers(Modifier.PROTECTED | Modifier.TRANSIENT);
      }
      
      /**
       * We need a default constructor so we can serialize
       */
      private void addDefaultConstructor() throws CannotCompileException
      {
         CtConstructor defaultConstructor = CtNewConstructor.defaultConstructor(jp);
         jp.addConstructor(defaultConstructor);
      }
      
      /**
       * This constructor is used by the advisor when we have regenerated the joinpoint.
       * This just creates a generic JoinPoint instance with no data specific to the 
       * method call
       */
      private void addPublicConstructor() throws CannotCompileException
      {
         publicConstructor = CtNewConstructor.make(
               new CtClass[] {methodInfoClass}, 
               new CtClass[0], 
               jp);
               
         publicConstructor.setBody("{super($1, $1.getInterceptors()); this." + INFO_FIELD + " = $1;}");
         jp.addConstructor(publicConstructor);
      }
      
      /**
       * This constructor will be called by invokeJoinpoint in the generated subclass when we need to 
       * instantiate a joinpoint containing target and args
       */
      protected void addProtectedConstructor() throws CannotCompileException
      {
         CtClass[] ctorParams = new CtClass[params.length + 1];
         ctorParams[0] = jp;
         System.arraycopy(params, 0, ctorParams, 1, params.length);
         protectedConstructor = CtNewConstructor.make(
               ctorParams,
               new CtClass[0],
               jp);
         protectedConstructor.setModifiers(Modifier.PROTECTED);
         
         StringBuffer body = new StringBuffer();
         body.append("{");
         body.append("   this($1." + INFO_FIELD + ");");
         
         int offset = 1;
         if (hasTargetObject)
         {
            body.append("   this." + TARGET_FIELD + " = $2;");
            body.append("   super.setTargetObject($2);");
            offset = 2;
         }
         
         for (int i = offset ; i < ctorParams.length ; i++)
         {
            body.append("   arg" + (i - offset) + " = $" + (i + 1)  + ";");
         }
         
         body.append("}");
         protectedConstructor.setBody(body.toString());
               
         jp.addConstructor(protectedConstructor);
         
      }
      
      /**
       * Add an empty invokeJoinpoint() method. This method will be overridden by generated subclasses,
       * when the interceptors are rebuilt
       */
      private CtMethod addInvokeJoinpointMethod() throws CannotCompileException, NotFoundException
      {
         invokeJoinpointMethod  = CtNewMethod.make(
               advisedMethod.getReturnType(), 
               INVOKE_JOINPOINT, 
               params, 
               advisedMethod.getExceptionTypes(), 
               null, 
               jp);
         invokeJoinpointMethod.setModifiers(Modifier.PROTECTED);
         jp.addMethod(invokeJoinpointMethod);
         return invokeJoinpointMethod;
       }
      
      private void addMethodInfoField()throws CannotCompileException
      {
         CtField infoField = new CtField(methodInfoClass, INFO_FIELD, jp);
         jp.addField(infoField);
         infoField.setModifiers(Modifier.PROTECTED | Modifier.TRANSIENT);
      }      
      
      private void addDispatchMethods() throws CannotCompileException, NotFoundException
      {
         addInvokeNextDispatchMethod();
         
         if (params.length > 0)
         {
            addInvokeJoinPointDispatchMethod();
         }
      }
      
      private void addInvokeNextDispatchMethod() throws CannotCompileException, NotFoundException
      {
         //This dispatch method will be called by the invokeNext() methods for around advice
         CtMethod dispatch = CtNewMethod.make(
               advisedMethod.getReturnType(), 
               JoinPointGenerator.DISPATCH, 
               new CtClass[0], 
               advisedMethod.getExceptionTypes(), 
               null, 
               jp);
         dispatch.setModifiers(Modifier.PROTECTED);
         
         StringBuffer parameters = new StringBuffer();
         for (int i = 0 ; i < originalParams.length ; i++)
         {
            if (i > 0)parameters.append(", ");
            parameters.append("arg" + i);
         }
         
         String body = (!hasTargetObject) ?
               "{" + MethodExecutionTransformer.getReturnStr(advisedMethod) + advisedClass.getName() + "." + wrappedMethodName + "(" + parameters + ");}" :
               "{" + MethodExecutionTransformer.getAopReturnStr(advisedMethod) + TARGET_FIELD + "." + wrappedMethodName + "(" + parameters + ");}"; 
      
         try
         {
            dispatch.setBody(body);
         }
         catch (CannotCompileException e)
         {
            throw new RuntimeException("Could not compile code " + body + " for method " + dispatch, e);
         }
         jp.addMethod(dispatch);
      }
      
      private void addInvokeJoinPointDispatchMethod() throws CannotCompileException, NotFoundException
      {
         //This dispatch method will be called by the invokeJoinPoint() method if the joinpoint has no around advices
         CtMethod dispatch =CtNewMethod.make(
               advisedMethod.getReturnType(), 
               JoinPointGenerator.DISPATCH, 
               params, 
               advisedMethod.getExceptionTypes(), 
               null, 
               jp);
         dispatch.setModifiers(Modifier.PROTECTED);

         int offset = hasTargetObject ? 1 : 0;
         StringBuffer parameters = new StringBuffer();
         for (int i = 0 ; i < originalParams.length ; i++)
         {
            if (i > 0)parameters.append(", ");
            parameters.append("$" + (i + offset + 1));
         }
      
         String body = (!hasTargetObject) ?
               "{" + MethodExecutionTransformer.getReturnStr(advisedMethod) + advisedClass.getName() + "." + wrappedMethodName + "(" + parameters + ");}" :
               "{" + MethodExecutionTransformer.getAopReturnStr(advisedMethod) + "$1." + wrappedMethodName + "(" + parameters + ");}"; 
      
         
         try
         {
            dispatch.setBody(body);
         }
         catch (CannotCompileException e)
         {
            throw new RuntimeException("Could not compile code " + body + " for method " + dispatch, e);
         }
         jp.addMethod(dispatch);
      }
   }
}
