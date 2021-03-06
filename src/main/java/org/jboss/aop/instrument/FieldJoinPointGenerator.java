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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.NotFoundException;

import org.jboss.aop.FieldInfo;
import org.jboss.aop.GeneratedClassAdvisor;
import org.jboss.aop.JoinPointInfo;
import org.jboss.aop.advice.AdviceMethodProperties;
import org.jboss.aop.joinpoint.FieldReadInvocation;
import org.jboss.aop.joinpoint.FieldWriteInvocation;
import org.jboss.aop.util.JavassistToReflect;
import org.jboss.aop.util.ReflectToJavassist;

/**
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision$
 */
public class FieldJoinPointGenerator extends JoinPointGenerator
{
   public static final String WRITE_GENERATOR_PREFIX = GENERATOR_PREFIX + "w_";
   public static final String READ_GENERATOR_PREFIX = GENERATOR_PREFIX + "r_";
   public static final String WRITE_JOINPOINT_FIELD_PREFIX = JOINPOINT_FIELD_PREFIX + "w_";
   public static final String READ_JOINPOINT_FIELD_PREFIX = JOINPOINT_FIELD_PREFIX + "r_";
   public static final String WRITE_JOINPOINT_CLASS_PREFIX = JOINPOINT_CLASS_PREFIX + "w_";
   public static final String READ_JOINPOINT_CLASS_PREFIX = JOINPOINT_CLASS_PREFIX + "r_";
   private static final Class READ_INVOCATION_TYPE = FieldReadInvocation.class;
   private static final Class WRITE_INVOCATION_TYPE = FieldWriteInvocation.class;
   private static final CtClass READ_INVOCATION_CT_TYPE;
   private static final CtClass WRITE_INVOCATION_CT_TYPE;
   static
   {
      try
      {
         READ_INVOCATION_CT_TYPE = ReflectToJavassist.classToJavassist(READ_INVOCATION_TYPE);
         WRITE_INVOCATION_CT_TYPE = ReflectToJavassist.classToJavassist(WRITE_INVOCATION_TYPE);
      }
      catch (NotFoundException e)
      {
         throw new RuntimeException(e);
      }
   }

   public FieldJoinPointGenerator(GeneratedClassAdvisor advisor, JoinPointInfo info)
   {
      super(advisor, info);
   }

   protected void initialiseJoinPointNames()
   {
      joinpointClassName = 
         getInfoClassName(fieldName(), read()); 
      
      joinpointFieldName =
         getInfoFieldName(fieldName(), read());
   }
   
   private String fieldName()
   {
      return ((FieldInfo)info).getAdvisedField().getName();
   }
   
   private boolean read()
   {
      return ((FieldInfo)info).isRead();
   }
   
   protected boolean isVoid()
   {
      return !((FieldInfo)info).isRead();
   }

   protected Class getReturnType()
   {
      if (!read())
      {
         return null;
      }
      return ((FieldInfo)super.info).getAdvisedField().getType();
   }

   protected AdviceMethodProperties getAdviceMethodProperties(AdviceSetup setup)
   {
      Field field = ((FieldInfo)info).getAdvisedField();
      return new AdviceMethodProperties(
            setup.getAspectClass(), 
            setup.getAdviceName(), 
            info.getClass(), 
            (read()) ? READ_INVOCATION_TYPE : WRITE_INVOCATION_TYPE, 
            (read()) ? getReturnType() : Void.TYPE, 
            (read()) ? new Class[] {} : new Class[] {field.getType()}, 
            null);
   }
   
   protected CtClass[] getJoinpointParameters() throws NotFoundException
   {
      if (isVoid()) return new CtClass[0];

      CtClass type = ReflectToJavassist.fieldToJavassist(((FieldInfo)super.info).getAdvisedField()).getType();
      return new CtClass[] {type};
   }

   protected boolean hasTargetObject()
   {
      return !Modifier.isStatic(((FieldInfo)info).getAdvisedField().getModifiers()); 
   }

   protected String getJoinPointGeneratorFieldName()
   {
      return getJoinPointGeneratorFieldName(fieldName(), read());
   }
   
   protected static String getInfoFieldName(String fieldName, boolean read)
   {
      if (read)
      {
         return READ_JOINPOINT_FIELD_PREFIX + fieldName;
      }
      else
      {
         return WRITE_JOINPOINT_FIELD_PREFIX + fieldName;
      }
   }

   private static String getInfoClassName(String fieldName, boolean read)
   {
      if (read)
      {
         return READ_JOINPOINT_CLASS_PREFIX + fieldName;
      }
      else
      {
         return WRITE_JOINPOINT_CLASS_PREFIX + fieldName;
      }
   }
   
   public static String getJoinPointGeneratorFieldName(String fieldName, boolean read)
   {
      if (read)
      {
         return READ_GENERATOR_PREFIX + fieldName;
      }
      else
      {
         return WRITE_GENERATOR_PREFIX + fieldName;
      }
   }
   
   protected static CtClass createReadJoinpointBaseClass(
         GeneratedAdvisorInstrumentor instrumentor, 
         CtClass advisedClass, 
         CtField advisedField,
         String finame, 
         int index)throws NotFoundException, CannotCompileException
   {
      instrumentor.addJoinPointGeneratorFieldToGenAdvisor(
            getJoinPointGeneratorFieldName(advisedField.getName(), true));
      
      BaseClassGenerator factory = new ReadBaseClassGenerator(instrumentor, advisedClass, advisedField, finame, index);
      return factory.generate();
   }
   
   protected static CtClass createWriteJoinpointBaseClass(
         GeneratedAdvisorInstrumentor instrumentor, 
         CtClass advisedClass, 
         CtField advisedField,
         String finame, 
         int index)throws NotFoundException, CannotCompileException
   {
      instrumentor.addJoinPointGeneratorFieldToGenAdvisor(
            getJoinPointGeneratorFieldName(advisedField.getName(), false));

      BaseClassGenerator factory = new WriteBaseClassGenerator(instrumentor, advisedClass, advisedField, finame, index);
      return factory.generate();
   }
   
   
   static abstract class BaseClassGenerator
   {
      GeneratedAdvisorInstrumentor instrumentor; 
      CtClass advisedClass; 
      CtField advisedField;
      String finame; 
      int index;
      boolean hasTargetObject;
      
      CtClass jp;
      CtMethod invokeJoinpointMethod;
      CtConstructor publicConstructor;
      CtConstructor protectedConstructor;
      CtField targetField;
      CtClass fieldType;
      CtClass fieldInfoClass;
      boolean read;
      
      BaseClassGenerator(GeneratedAdvisorInstrumentor instrumentor,  CtClass advisedClass, 
                        CtField advisedField, String finame, int index, boolean read) throws NotFoundException
      {
         this.instrumentor = instrumentor;
         this.advisedClass = advisedClass;
         this.advisedField = advisedField;
         this.finame = finame;
         this.fieldType = advisedField.getType();
         this.read = read;
         fieldInfoClass = instrumentor.forName(FieldAccessTransformer.FIELD_INFO_CLASS_NAME);
         hasTargetObject = !Modifier.isStatic(advisedField.getModifiers());
      }

      protected CtClass generate() throws CannotCompileException, NotFoundException
      {
         jp = setupClass();
         if (hasTargetObject)
         {
            addTypedTargetField();
         }
         addInvokeJoinpointMethod();
         addFieldInfoField();
         addPublicConstructor();
         addProtectedConstructor();
         addDispatchMethods();
         
         TransformerCommon.compileOrLoadClass(advisedClass, jp);
         return jp; 
      }

      
      private CtClass setupClass()throws NotFoundException, CannotCompileException
      {
         String className = getInfoClassName(advisedField.getName(), read);
         
         //Create inner joinpoint class in advised class, super class is 
         jp = advisedClass.makeNestedClass(className, true);
         int mod = jp.getModifiers();
         jp.setModifiers(mod | Modifier.PUBLIC);
         
         jp.setSuperclass(getSuperClass());
         addUntransformableInterface(instrumentor, jp);
         return jp;
      }

      protected abstract CtClass getSuperClass() throws NotFoundException;
      
      private void addTypedTargetField()throws CannotCompileException
      {
         targetField = new CtField(advisedClass, TARGET_FIELD, jp);
         jp.addField(targetField);
         targetField.setModifiers(Modifier.PROTECTED);
      }
      
      /**
       * This constructor is used by the advisor when we have regenerated the joinpoint.
       * This just creates a generic JoinPoint instance with no data specific to the 
       * method call
       */
      private void addPublicConstructor() throws CannotCompileException
      {
         publicConstructor = CtNewConstructor.make(
               new CtClass[] {fieldInfoClass}, 
               new CtClass[0], 
               jp);
               
         publicConstructor.setBody("{super($1, $1.getInterceptors()); this." + INFO_FIELD + " = $1;}");
         jp.addConstructor(publicConstructor);
      }
      
      /**
       * This constructor will be called by invokeJoinpoint in the generated subclass when we need to 
       * instantiate a joinpoint containing target and args
       */
      protected void addProtectedConstructor() throws CannotCompileException, NotFoundException
      {
         
         protectedConstructor = CtNewConstructor.make(
               createProtectedCtorParams(),
               new CtClass[0],
               jp);
         protectedConstructor.setModifiers(Modifier.PROTECTED);
         protectedConstructor.setBody(createProtectedCtorBody());
               
         jp.addConstructor(protectedConstructor);
      }
      
      protected abstract CtClass[] createProtectedCtorParams() throws NotFoundException;
      protected abstract String createProtectedCtorBody();
      protected abstract CtClass[] getInvokeJoinPointParams() throws NotFoundException;
      /**
       * Add an empty invokeJoinpoint() method. This method will be overridden by generated subclasses,
       * when the interceptors are rebuilt
       */
      protected abstract CtMethod addInvokeJoinpointMethod() throws CannotCompileException, NotFoundException;
      
      private void addFieldInfoField()throws CannotCompileException
      {
         CtField infoField = new CtField(fieldInfoClass, INFO_FIELD, jp);
         jp.addField(infoField);
      }      
      
      private void addDispatchMethods() throws CannotCompileException, NotFoundException
      {
         addInvokeNextDispatchMethod();
         addInvokeJoinPointDispatchMethod();
      }
      
      private void addInvokeNextDispatchMethod() throws CannotCompileException, NotFoundException
      {
         //This dispatch method will be called by the invokeNext() methods for around advice
         CtMethod dispatch = CtNewMethod.make(
               (read) ? advisedField.getType() : CtClass.voidType, 
               JoinPointGenerator.DISPATCH, 
               new CtClass[0], 
               new CtClass[0], 
               null, 
               jp);
         dispatch.setModifiers(Modifier.PROTECTED);
         
         String body = createInvokeNextDispatchMethodBody(); 
      
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
      
      protected abstract String createInvokeNextDispatchMethodBody() throws NotFoundException;
      
      protected void addInvokeJoinPointDispatchMethod() throws NotFoundException, CannotCompileException
      {
         CtClass[] params = getInvokeJoinPointParams();
         if (params.length == 0)
         {
            return;
         }
         
         //This dispatch method will be called by the invokeJoinPoint() method if the joinpoint has no around advices
         CtMethod dispatch = CtNewMethod.make(
               (read) ? advisedField.getType() : CtClass.voidType, 
               JoinPointGenerator.DISPATCH, 
               params, 
               new CtClass[0], 
               null, 
               jp);
         dispatch.setModifiers(Modifier.PROTECTED);
         
         String body = createInvokeJoinPointDispatchMethodBody(); 
      
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

      protected abstract String createInvokeJoinPointDispatchMethodBody() throws NotFoundException;
   }

   private static class ReadBaseClassGenerator extends BaseClassGenerator
   {
      ReadBaseClassGenerator(GeneratedAdvisorInstrumentor instrumentor,  CtClass advisedClass, 
            CtField advisedField, String finame, int index) throws NotFoundException
      {
         super(instrumentor, advisedClass, advisedField, finame, index, true);
      }

      protected CtClass getSuperClass() throws NotFoundException
      {
         return READ_INVOCATION_CT_TYPE;
      }

      protected CtClass[] createProtectedCtorParams() throws NotFoundException
      {
         CtClass[] ctorParams = (hasTargetObject) ? new CtClass[2] : new CtClass[1];
         ctorParams[0] = jp;
         if (hasTargetObject) ctorParams[1] = advisedClass;
         
         return ctorParams;
      }
      
      protected String createProtectedCtorBody()
      {
         StringBuffer body = new StringBuffer();
         body.append("{");
         body.append("   this($1." + INFO_FIELD + ");");
         
         if (hasTargetObject)
         {
            body.append("   this." + TARGET_FIELD + " = $2;");
            body.append("   super.setTargetObject($2);");
         }
         
         body.append("}");
         return body.toString();
      }

      protected CtClass[] getInvokeJoinPointParams() throws NotFoundException
      {
         return (hasTargetObject) ? new CtClass[] {advisedClass} : new CtClass[0];
      }

      protected CtMethod addInvokeJoinpointMethod() throws CannotCompileException, NotFoundException
      {
         invokeJoinpointMethod  = CtNewMethod.make(
               advisedField.getType(), 
               INVOKE_JOINPOINT, 
               getInvokeJoinPointParams(), 
               new CtClass[0], 
               null, 
               jp);
         invokeJoinpointMethod.setModifiers(Modifier.PROTECTED);
         jp.addMethod(invokeJoinpointMethod);
         
         return invokeJoinpointMethod;
      }

      protected String createInvokeNextDispatchMethodBody()
      {
         return (hasTargetObject) ?
            "{return "  + TARGET_FIELD + "." + advisedField.getName() + ";}" :
            "{return " + advisedClass.getName() + "." + advisedField.getName() + ";}";
      }
      
      protected String createInvokeJoinPointDispatchMethodBody()
      {
         return (hasTargetObject) ?
            "{return "  + "$1." + advisedField.getName() + ";}" :
            "{return " + advisedClass.getName() + "." + advisedField.getName() + ";}";
      }
   }
   
   private static class WriteBaseClassGenerator extends BaseClassGenerator
   {
      WriteBaseClassGenerator(GeneratedAdvisorInstrumentor instrumentor,  CtClass advisedClass, 
            CtField advisedField, String finame, int index) throws NotFoundException
      {
         super(instrumentor, advisedClass, advisedField, finame, index, false);
      }
      
      protected CtClass getSuperClass() throws NotFoundException
      {
         return WRITE_INVOCATION_CT_TYPE;
      }

      protected CtClass[] createProtectedCtorParams() throws NotFoundException
      {
         CtClass[] ctorParams = (hasTargetObject) ? new CtClass[3] : new CtClass[2];
         ctorParams[0] = jp;
         if (hasTargetObject)
         {
            ctorParams[1] = advisedClass;
            ctorParams[2] = advisedField.getType();
         }
         else
         {
            ctorParams[1] = advisedField.getType();
         }
         
         return ctorParams;
      }

      protected String createProtectedCtorBody()
      {
         StringBuffer body = new StringBuffer();
         body.append("{");
         body.append("   this($1." + INFO_FIELD + ");");
         
         if (hasTargetObject)
         {
            body.append("   this." + TARGET_FIELD + " = $2;");
            body.append("   super.setTargetObject($2);");
            body.append("   super.value = ($w)$3;");
         }
         else
         {
            body.append("   super.value = ($w)$2;");
         }
         
         body.append("}");
         return body.toString();
      }

      protected CtClass[] getInvokeJoinPointParams() throws NotFoundException
      {
         return (hasTargetObject) ? new CtClass[] {advisedClass, advisedField.getType()} : new CtClass[] {advisedField.getType()};
      }

      protected CtMethod addInvokeJoinpointMethod() throws CannotCompileException, NotFoundException
      {
         invokeJoinpointMethod  = CtNewMethod.make(
               CtClass.voidType, 
               MethodJoinPointGenerator.INVOKE_JOINPOINT, 
               getInvokeJoinPointParams(), 
               new CtClass[0], 
               null, 
               jp);
         invokeJoinpointMethod.setModifiers(Modifier.PROTECTED);
         jp.addMethod(invokeJoinpointMethod);
         
         return invokeJoinpointMethod;
      }

      protected String createInvokeNextDispatchMethodBody() throws NotFoundException
      {
         CtClass type = advisedField.getType();
         String value = JavassistToReflect.castInvocationValueToTypeString(type);

         return 
            "{" +
            ((hasTargetObject) ?
                  TARGET_FIELD + "." + advisedField.getName() + " = " +  value:
                     advisedClass.getName() + "." + advisedField.getName() + " = " +  value) +

            ((hasTargetObject) ?
                  "return "  + TARGET_FIELD + "." + advisedField.getName() + ";" :
                     "return " + advisedClass.getName() + "." + advisedField.getName() + ";") +
            "}";
      }

      protected String createInvokeJoinPointDispatchMethodBody()
      {
         return (hasTargetObject) ?
            "{$1." + advisedField.getName() + " = $2;}" :
            "{" + advisedClass.getName() + "." + advisedField.getName() + " = $1;}";
      }
   }
}
