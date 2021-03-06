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

import java.lang.reflect.Constructor;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;

import org.jboss.aop.ConByMethodInfo;
import org.jboss.aop.GeneratedClassAdvisor;
import org.jboss.aop.JoinPointInfo;
import org.jboss.aop.advice.AdviceMethodProperties;
import org.jboss.aop.joinpoint.ConstructorCalledByMethodInvocation;
import org.jboss.aop.util.ReflectToJavassist;

/**
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 44253 $
 */
public class ConByMethodJoinPointGenerator extends JoinPointGenerator
{
   public static final String GENERATOR_PREFIX = JoinPointGenerator.GENERATOR_PREFIX + "CByM_";
   public static final String JOINPOINT_CLASS_PREFIX = JoinPointGenerator.JOINPOINT_CLASS_PREFIX + "CByM_";
   public static final String JOINPOINT_FIELD_PREFIX = JoinPointGenerator.JOINPOINT_FIELD_PREFIX + "CByM_";
   private static final Class INVOCATION_TYPE = ConstructorCalledByMethodInvocation.class;
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


   public ConByMethodJoinPointGenerator(GeneratedClassAdvisor advisor, JoinPointInfo info)
   {
      super(advisor, info);
   }

   protected void initialiseJoinPointNames()
   {
      joinpointClassName = getInfoClassName(
               callingMethodHash(),
               calledClass(),
               calledConHash());
      
      joinpointFieldName = getInfoFieldName(
            callingMethodHash(),
            calledClass(),
            calledConHash());

   }

   private long callingMethodHash()
   {
      return ((ConByMethodInfo)info).getCallingMethodHash();
   }
   
   private String calledClass()
   {
      return ((ConByMethodInfo)info).getCalledClass().getName();
   }
   
   private long calledConHash()
   {
      return ((ConByMethodInfo)info).getCalledConHash();
   }
   
   protected boolean isVoid()
   {
      return false;
   }

   protected Class getReturnType()
   {
      return ((ConByMethodInfo)info).getCalledClass();
   }

   protected AdviceMethodProperties getAdviceMethodProperties(AdviceSetup setup)
   {
      Constructor ctor = ((ConByMethodInfo)info).getConstructor();
      return new AdviceMethodProperties(
            setup.getAspectClass(), 
            setup.getAdviceName(), 
            info.getClass(), 
            INVOCATION_TYPE, 
            ctor.getDeclaringClass(), 
            ctor.getParameterTypes(), 
            ctor.getExceptionTypes());
   }

   protected boolean isCaller()
   {
      return true;
   }
   
   protected boolean hasCallingObject()
   {
      return !java.lang.reflect.Modifier.isStatic(((ConByMethodInfo)info).getCallingMethod().getModifiers()); 
   }
   
   protected boolean hasTargetObject()
   {
      return false; 
   }

   protected void overrideDispatchMethods(CtClass superClass, CtClass clazz, JoinPointInfo newInfo) throws CannotCompileException, NotFoundException
   {
      super.overrideDispatchMethods(superClass, clazz, (ConByMethodInfo)newInfo);
   }
   
   protected static CtClass createJoinpointBaseClass(            
            GeneratedAdvisorInstrumentor instrumentor,
            long callingHash,
            boolean hasCallingObject,
            CtClass callingClass,
            CtConstructor targetCtor,
            String classname,
            long calledHash,
            String ciname) throws NotFoundException, CannotCompileException
   {
      instrumentor.addJoinPointGeneratorFieldToGenAdvisor(
            getJoinPointGeneratorFieldName(callingHash, classname, calledHash));
      
      BaseClassGenerator generator = new BaseClassGenerator(instrumentor, callingClass, callingHash, hasCallingObject, classname, targetCtor, calledHash, ciname);
      return generator.generate();
   }
   
   protected String getJoinPointGeneratorFieldName()
   {
      return getJoinPointGeneratorFieldName(
            callingMethodHash(),
            calledClass(),
            calledConHash());
   }
   
   protected static String getInfoClassName(long callingHash, String classname, long calledHash)
   {
      return JOINPOINT_CLASS_PREFIX + CallerTransformer.getUniqueInvocationFieldname(callingHash, classname, calledHash);
   }
   
   protected static String getInfoFieldName(long callingHash, String classname, long calledHash)
   {
      return JOINPOINT_FIELD_PREFIX + CallerTransformer.getUniqueInvocationFieldname(callingHash, classname, calledHash);
   }
   
   protected static String getJoinPointGeneratorFieldName(long callingHash, String classname, long calledHash)
   {
      return GENERATOR_PREFIX + CallerTransformer.getUniqueInvocationFieldname(callingHash, classname, calledHash);
   }
   
   private static class BaseClassGenerator
   {
      GeneratedAdvisorInstrumentor instrumentor;
      CtClass callingClass;
      long callingHash;
      boolean hasCallingObject;
      String classname;
      CtClass targetClass;
      CtConstructor targetCtor;
      long calledHash; 
      String ciname;
      
      CtClass jp;
      CtMethod invokeJoinpointMethod;
      CtConstructor publicConstructor;
      CtConstructor protectedConstructor;
      CtClass[] params;
      CtClass constructorInfoClass;
      
      BaseClassGenerator(
            GeneratedAdvisorInstrumentor instrumentor,
            CtClass callingClass,
            long callingHash,
            boolean hasCallingObject,
            String classname, 
            CtConstructor targetCtor,
            long calledHash, 
            String ciname) throws NotFoundException
      {
         this.instrumentor = instrumentor;
         this.callingClass = callingClass;
         this.callingHash = callingHash;
         this.hasCallingObject = hasCallingObject;
         this.classname = classname;
         this.targetClass = instrumentor.forName(classname);
         this.targetCtor = targetCtor;
         this.calledHash = calledHash;
         this.ciname = ciname;
         this.params = targetCtor.getParameterTypes(); 
         constructorInfoClass = instrumentor.forName(CallerTransformer.CON_BY_METHOD_INFO_CLASS_NAME);
      }
      
      protected CtClass generate() throws CannotCompileException, NotFoundException
      {
         jp = setupClass();
         addArgumentsFieldsAndAccessors();      
         addInvokeJoinpointMethod();
         addMethodInfoField();
         addPublicConstructor();
         addProtectedConstructor();
         addDispatchMethods();
         
         TransformerCommon.compileOrLoadClass(callingClass, jp);
         return jp; 
      }

      
      private CtClass setupClass()throws NotFoundException, CannotCompileException
      {
         String className = getInfoClassName(callingHash, targetClass.getName(), calledHash);
         
         //Create inner joinpoint class in advised class, super class is ConstructorInvocation
         jp = callingClass.makeNestedClass(className, true);
         int mod = jp.getModifiers();
         jp.setModifiers(mod | Modifier.PUBLIC);
         
         CtClass invocation = INVOCATION_CT_TYPE;
         jp.setSuperclass(invocation);
         addUntransformableInterface(instrumentor, jp);
         return jp;
      }

      private void addArgumentsFieldsAndAccessors() throws NotFoundException, CannotCompileException
      {
         OptimizedBehaviourInvocations.addArgumentFieldsToInvocation(jp, params);
         OptimizedBehaviourInvocations.addSetArguments(instrumentor.getClassPool(), jp, params);
         OptimizedBehaviourInvocations.addGetArguments(instrumentor.getClassPool(), jp, params);
      }
      
      /**
       * This constructor is used by the advisor when we have regenerated the joinpoint.
       * This just creates a generic JoinPoint instance with no data specific to the 
       * method call
       */
      private void addPublicConstructor() throws CannotCompileException
      {
         publicConstructor = CtNewConstructor.make(
               new CtClass[] {constructorInfoClass}, 
               new CtClass[0], 
               jp);
               
         publicConstructor.setBody("{super($1, null, $1.getInterceptors()); this." + INFO_FIELD + " = $1;}");
         jp.addConstructor(publicConstructor);
      }
      
      /**
       * This constructor will be called by invokeJoinpoint in the generated subclass when we need to 
       * instantiate a joinpoint containing target and args
       */
      protected void addProtectedConstructor() throws CannotCompileException
      {
         final int offset = (hasCallingObject) ? 2 : 1; 
         CtClass[] ctorParams = new CtClass[params.length + offset];
         ctorParams[0] = jp;
         
         if (hasCallingObject)
         {
            ctorParams[1] = callingClass;
         }
         System.arraycopy(params, 0, ctorParams, offset, params.length);
         protectedConstructor = CtNewConstructor.make(
               ctorParams,
               new CtClass[0],
               jp);
         protectedConstructor.setModifiers(Modifier.PROTECTED);
         
         StringBuffer body = new StringBuffer();
         body.append("{");
         body.append("   this($1." + INFO_FIELD + ");");
         
         if (hasCallingObject)
         {
            body.append("   super.callingObject=$" + offset + ";");
         }
         
         for (int i = offset ; i < ctorParams.length ; i++)
         {
            body.append("   arg" + (i - offset) + " = $" + (i + 1)  + ";");
         }
         
         body.append("}");
         protectedConstructor.setBody(body.toString());
               
         jp.addConstructor(protectedConstructor);
         
      }
      
      private CtClass[] getInvokeJoinPointParams()
      {
         if (hasCallingObject)
         {
            CtClass[] invokeParams = null;
            invokeParams = new CtClass[params.length + 1];
            invokeParams[0] = callingClass;
            System.arraycopy(params, 0, invokeParams, 1, params.length);
            return invokeParams;
         }
         return params;
      }
      
      /**
       * Add an empty invokeJoinpoint() method. This method will be overridden by generated subclasses,
       * when the interceptors are rebuilt
       */
      private CtMethod addInvokeJoinpointMethod() throws CannotCompileException, NotFoundException
      {
         invokeJoinpointMethod  = CtNewMethod.make(
               targetClass, 
               INVOKE_JOINPOINT, 
               getInvokeJoinPointParams(), 
               targetCtor.getExceptionTypes(), 
               null, 
               jp);
         invokeJoinpointMethod.setModifiers(Modifier.PROTECTED);
         jp.addMethod(invokeJoinpointMethod);
         return invokeJoinpointMethod;
       }
      
      private void addMethodInfoField()throws CannotCompileException
      {
         CtField infoField = new CtField(constructorInfoClass, INFO_FIELD, jp);
         jp.addField(infoField);
      }      
      
      private void addDispatchMethods() throws CannotCompileException, NotFoundException
      {
         addInvokeNextDispatchMethod();
         
         if (hasCallingObject || params.length > 0)
         {
            addInvokeJoinPointDispatchMethod();
         }
      }
      
      private void addInvokeNextDispatchMethod() throws CannotCompileException, NotFoundException
      {
         //This dispatch method will be called by the invokeNext() methods for around advice
         CtMethod dispatch = CtNewMethod.make(
               targetClass, 
               MethodJoinPointGenerator.DISPATCH, 
               new CtClass[0], 
               targetCtor.getExceptionTypes(), 
               null, 
               jp);
         dispatch.setModifiers(Modifier.PROTECTED);
         
         StringBuffer parameters = new StringBuffer("(");
         for (int i = 0 ; i < params.length ; i++)
         {
            if (i > 0)parameters.append(", ");
            parameters.append("arg" + i);
         }
         parameters.append(")");
         
         String body = 
            "{" +
            "   " + targetClass.getName() + " obj = new " + targetClass.getName() + parameters + ";" +
            "   setTargetObject(obj);" +
            "   return obj;" + 
            "}";
      
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
         CtMethod dispatch = CtNewMethod.make(
               targetClass, 
               MethodJoinPointGenerator.DISPATCH, 
               getInvokeJoinPointParams(), 
               targetCtor.getExceptionTypes(), 
               null, 
               jp);
         dispatch.setModifiers(Modifier.PROTECTED);
         
         int offset = hasCallingObject ? 1 : 0;
         StringBuffer parameters = new StringBuffer("(");
         for (int i = 0 ; i < params.length ; i++)
         {
            if (i > 0)parameters.append(", ");
            parameters.append("$" + (i + offset + 1));
         }
         parameters.append(")");
         
         String body = 
            "{" +
            "   " + targetClass.getName() + " obj = new " + targetClass.getName() + parameters + ";" +
            "   setTargetObject(obj);" +
            "   return obj;" + 
            "}";
      
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

