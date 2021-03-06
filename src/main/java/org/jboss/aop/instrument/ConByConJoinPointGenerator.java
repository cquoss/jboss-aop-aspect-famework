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
import java.lang.reflect.Modifier;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.NotFoundException;

import org.jboss.aop.ConByConInfo;
import org.jboss.aop.GeneratedClassAdvisor;
import org.jboss.aop.JoinPointInfo;
import org.jboss.aop.advice.AdviceMethodProperties;
import org.jboss.aop.joinpoint.ConstructorCalledByConstructorInvocation;
import org.jboss.aop.util.ReflectToJavassist;

/**
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 59675 $
 */
public class ConByConJoinPointGenerator extends JoinPointGenerator
{
   public static final String GENERATOR_PREFIX = JoinPointGenerator.GENERATOR_PREFIX + "CByC_";
   public static final String JOINPOINT_CLASS_PREFIX = JoinPointGenerator.JOINPOINT_CLASS_PREFIX + "CByC_";
   public static final String JOINPOINT_FIELD_PREFIX = JoinPointGenerator.JOINPOINT_FIELD_PREFIX + "CByC_";
   
   private static final Class INVOCATION_TYPE = ConstructorCalledByConstructorInvocation.class;
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

   public ConByConJoinPointGenerator(GeneratedClassAdvisor advisor, JoinPointInfo info)
   {
      super(advisor, info);
   }

   protected void initialiseJoinPointNames()
   {
      joinpointClassName = getInfoClassName(
               callingIndex(),
               calledClass(),
               calledConHash());
      
      joinpointFieldName = getInfoFieldName(
            callingIndex(),
            calledClass(),
            calledConHash());

   }

   private int callingIndex()
   {
      return ((ConByConInfo)info).getCallingIndex();
   }
   
   private String calledClass()
   {
      return ((ConByConInfo)info).getCalledClass().getName();
   }
   
   private long calledConHash()
   {
      return ((ConByConInfo)info).getCalledConHash();
   }
   
   
   protected boolean isVoid()
   {
      return false;
   }
   
   protected Class getReturnType()
   {
      return ((ConByConInfo)info).getCalledClass();
   }
   
   protected AdviceMethodProperties getAdviceMethodProperties(AdviceSetup setup)
   {
      Constructor ctor = ((ConByConInfo)info).getConstructor();
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
      return true; 
   }
   
   protected boolean hasTargetObject()
   {
      return false; 
   }


   protected void overrideDispatchMethods(CtClass superClass, CtClass clazz, JoinPointInfo newInfo) throws CannotCompileException, NotFoundException
   {
      super.overrideDispatchMethods(superClass, clazz, (ConByConInfo)newInfo);
   }
   
   protected static CtClass createJoinpointBaseClass(            
            GeneratedAdvisorInstrumentor instrumentor,
            int callingIndex,
            CtClass callingClass,
            CtConstructor targetCtor,
            String classname,
            long calledHash,
            String ciname) throws NotFoundException, CannotCompileException
   {
      instrumentor.addJoinPointGeneratorFieldToGenAdvisor(
            getJoinPointGeneratorFieldName(callingIndex, classname, calledHash));
      
      BaseClassGenerator generator = new BaseClassGenerator(instrumentor, callingClass, callingIndex, classname, targetCtor, calledHash, ciname);
      return generator.generate();
   }
   
   protected String getJoinPointGeneratorFieldName()
   {
      return getJoinPointGeneratorFieldName(
            callingIndex(),
            calledClass(),
            calledConHash());
   }
   
   protected static String getInfoClassName(int callingIndex, String classname, long calledHash)
   {
      return JOINPOINT_CLASS_PREFIX + CallerTransformer.getUniqueInvocationFieldname(callingIndex, classname, calledHash);
   }
   
   protected static String getInfoFieldName(int callingIndex, String classname, long calledHash)
   {
      return JOINPOINT_FIELD_PREFIX + CallerTransformer.getUniqueInvocationFieldname(callingIndex, classname, calledHash);
   }
   
   protected static String getJoinPointGeneratorFieldName(int callingIndex, String classname, long calledHash)
   {
      return GENERATOR_PREFIX + CallerTransformer.getUniqueInvocationFieldname(callingIndex, classname, calledHash);
   }
   
   private static class BaseClassGenerator
   {
      GeneratedAdvisorInstrumentor instrumentor;
      CtClass callingClass;
      int callingIndex;
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
            int callingIndex,
            String classname, 
            CtConstructor targetCtor,
            long calledHash, 
            String ciname) throws NotFoundException
      {
         this.instrumentor = instrumentor;
         this.callingClass = callingClass;
         this.callingIndex = callingIndex;
         this.classname = classname;
         this.targetClass = instrumentor.forName(classname);
         this.targetCtor = targetCtor;
         this.calledHash = calledHash;
         this.ciname = ciname;
         this.params = targetCtor.getParameterTypes();
         constructorInfoClass = instrumentor.forName(CallerTransformer.CON_BY_CON_INFO_CLASS_NAME);
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
         String className = getInfoClassName(callingIndex, targetClass.getName(), calledHash);
         
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
         CtClass[] ctorParams = new CtClass[params.length + 2];
         ctorParams[0] = jp;
         ctorParams[1] = callingClass;
         System.arraycopy(params, 0, ctorParams, 2, params.length);
         protectedConstructor = CtNewConstructor.make(
               ctorParams,
               new CtClass[0],
               jp);
         protectedConstructor.setModifiers(Modifier.PROTECTED);
         
         StringBuffer body = new StringBuffer();
         body.append("{");
         body.append("   this($1." + INFO_FIELD + ");");
		 body.append("   super.callingObject=$2;");
         
         int offset = 2;
         for (int i = offset ; i < ctorParams.length ; i++)
         {
            body.append("   arg" + (i - offset) + " = $" + (i + 1)  + ";");
         }
         
         body.append("}");
         protectedConstructor.setBody(body.toString());
               
         jp.addConstructor(protectedConstructor);
         
      }
      
      private CtClass[] getInvokeJoinPointParameters()
      {
         CtClass[] invokeParams = new CtClass[params.length + 1];
         invokeParams[0] = callingClass;
         System.arraycopy(params, 0, invokeParams, 1, params.length);
         return invokeParams;
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
               getInvokeJoinPointParameters(), 
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
         // since caller is available,
         // it is necessary to generate invoke join point dispatch method
         addInvokeJoinpointDispatchMethod();
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

      private void addInvokeJoinpointDispatchMethod() throws CannotCompileException, NotFoundException
      {
         //This dispatch method will be called by the invokeJoinPoint() method if the joinpoint has no around advices
         CtMethod dispatch = CtNewMethod.make(
               targetClass, 
               MethodJoinPointGenerator.DISPATCH, 
               getInvokeJoinPointParameters(), 
               targetCtor.getExceptionTypes(), 
               null, 
               jp);
         dispatch.setModifiers(Modifier.PROTECTED);
         
         final int offset = 1;
         StringBuffer parameters = new StringBuffer();
         for (int i = 0 ; i < params.length ; i++)
         {
            if (i > 0)parameters.append(", ");
            parameters.append("$" + (i + offset + 1));
         }
         
         String body = 
            "{" +
            "   " + targetClass.getName() + " obj = new " + targetClass.getName() + "(" + parameters.toString() + ");" +
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
