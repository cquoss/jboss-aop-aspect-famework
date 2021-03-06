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


import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;

import org.jboss.aop.AspectManager;
import org.jboss.aop.ClassAdvisor;

/**
 * Used with the GeneratedClassAdvisor
 *
 * @author <a href="mailto:kabir.khan@jboss.org">Kabir Khan</a>
 * @version $Revision$
 */
public class GeneratedAdvisorCallerTransformer extends CallerTransformer
{
   public GeneratedAdvisorCallerTransformer(Instrumentor instrumentor, AspectManager manager)
   {
      super(instrumentor, manager, true, new GeneratedAdvisorCallerInfoAdder(instrumentor));
   }

   private GeneratedAdvisorInstrumentor getInstrumentor()
   {
      return (GeneratedAdvisorInstrumentor)instrumentor;
   }

   private CtClass getGenadvisor()
   {
      return getInstrumentor().getGenadvisor();
   }

   protected CallerExprEditor callerExprEditorFactory(ClassAdvisor advisor, CtClass clazz)
   {
      return new GeneratedAdvisorCallerExprEditor(advisor, clazz);
   }

   class GeneratedAdvisorCallerExprEditor extends CallerExprEditor
   {
      public GeneratedAdvisorCallerExprEditor(ClassAdvisor advisor, CtClass callingClass)
      {
         super(advisor, callingClass);
      }

      protected void setupConstructor(ConstructorDetail cd)throws NotFoundException, CannotCompileException
      {
         if (callerInfos.get(cd.callerInfoField) == null)
         {
            callerInfos.put(cd.callerInfoField, NonOptimizedCallerTransformer.PLACEHOLDER);
            callerInfoAdder.addMethodByConInfoField(getGenadvisor(), cd.callerInfoField, cd.callingIndex, cd.classname, cd.calledHash);
            addJoinpoint(cd);
            createGenAdvisorMethodByConMethod(cd);
         }
      }

      private void addJoinpoint(ConstructorDetail cd)throws CannotCompileException, NotFoundException
      {
         CtClass joinpoint = createJoinpointClass(cd);
         CtClass genadvisor = getInstrumentor().getGenadvisor();
         CtField field = new CtField(
               joinpoint,
               MethodByConJoinPointGenerator.getInfoFieldName(cd.callingIndex, cd.classname, cd.calledHash),
               genadvisor);
         field.setModifiers(Modifier.PROTECTED);
         genadvisor.addField(field);
      }

      private CtClass createJoinpointClass(ConstructorDetail cd) throws CannotCompileException, NotFoundException
      {
         return MethodByConJoinPointGenerator.createJoinpointBaseClass(
               getInstrumentor(),
               cd.callingIndex,
               callingClass,
               cd.calledMethod,
               cd.classname,
               cd.calledHash,
               cd.callerInfoField);
      }

      private void createGenAdvisorMethodByConMethod(ConstructorDetail cd) throws NotFoundException, CannotCompileException
      {
         final boolean hasTargetObject = !Modifier.isStatic(cd.calledMethod.getModifiers());
         final int originalLength = cd.calledMethod.getParameterTypes().length;
         CtClass[] params = null;

         if (hasTargetObject)
         {
            params = new CtClass[originalLength + 2];
            params[0] = callingClass;
            params[1] = instrumentor.forName(cd.classname); //target object
            System.arraycopy(cd.calledMethod.getParameterTypes(), 0, params, 2, originalLength);
         }
         else
         {
            params = new CtClass[originalLength + 1];
            params[0] = callingClass;
            System.arraycopy(cd.calledMethod.getParameterTypes(), 0, params, 1, originalLength);
         }

         CtMethod method = CtNewMethod.make(
               cd.calledMethod.getReturnType(),
               cd.callerInfoField,
               params,
               cd.calledMethod.getExceptionTypes(),
               null,
               getGenadvisor());
         getGenadvisor().addMethod(method);

         String proceed = null;

         if (hasTargetObject)
         {
            proceed = MethodExecutionTransformer.getAopReturnStr(cd.calledMethod) + "$2." + cd.calledMethod.getName() + "(" + getArguments(params.length, 2) + ");";
         }
         else
         {
            proceed = MethodExecutionTransformer.getAopReturnStr(cd.calledMethod) + cd.classname + "." + cd.calledMethod.getName() + "(" + getArguments(params.length, 1) +");";
         }

         String infoName = MethodByConJoinPointGenerator.getInfoFieldName(cd.callingIndex, cd.classname, cd.calledHash);
         String generatorName = MethodByConJoinPointGenerator.getJoinPointGeneratorFieldName(cd.callingIndex, cd.classname, cd.calledHash);
         String code =
               "{" +
               "   if (" + infoName + " == null && " + generatorName + " != null)" +
               "   {" +
               "   " + generatorName + "." + JoinPointGenerator.GENERATE_JOINPOINT_CLASS + "();" +
               "   }" +
               "   if (" + infoName + " == null)" +
               "   { " +
               "      " + proceed +
               "   }" +
               "   else" +
               "   {" +
               "      return " + infoName + "." + JoinPointGenerator.INVOKE_JOINPOINT + "($$);" +
               "   }" +
               "}";

         try
         {
            method.setBody(code);
         }
         catch(CannotCompileException e)
         {
            System.out.println("Error for " + cd.callingIndex + " code:" + code);
            throw e;
         }
      }


      protected void setupMethod(MethodDetail md) throws NotFoundException, CannotCompileException
      {
         if (callerInfos.get(md.callerInfoField) == null)
         {
            callerInfos.put(md.callerInfoField, NonOptimizedCallerTransformer.PLACEHOLDER);
            callerInfoAdder.addMethodByMethodInfoField(getGenadvisor(), md.callerInfoField, md.callingHash, md.classname, md.calledHash);
            addJoinpoint(md);
            createGenAdvisorMethodByMethodMethod(md);
         }
      }

      private void addJoinpoint(MethodDetail md)throws CannotCompileException, NotFoundException
      {
         CtClass joinpoint = createJoinpointClass(md);
         CtClass genadvisor = getInstrumentor().getGenadvisor();
         CtField field = new CtField(
               joinpoint,
               MethodByMethodJoinPointGenerator.getInfoFieldName(md.callingHash, md.classname, md.calledHash),
               genadvisor);
         field.setModifiers(Modifier.PROTECTED);
         genadvisor.addField(field);
      }

      private CtClass createJoinpointClass(MethodDetail md) throws CannotCompileException, NotFoundException
      {
         return MethodByMethodJoinPointGenerator.createJoinpointBaseClass(
               getInstrumentor(),
               md.callingHash,
               !Modifier.isStatic(md.where.getModifiers()),
               callingClass,
               md.calledMethod,
               md.classname,
               md.calledHash,
               md.callerInfoField);
      }

      private void createGenAdvisorMethodByMethodMethod(MethodDetail md) throws NotFoundException, CannotCompileException
      {

         final boolean hasCallingObject = !Modifier.isStatic(md.where.getModifiers());
         final boolean hasTargetObject = !Modifier.isStatic(md.calledMethod.getModifiers());
         final int originalLength = md.calledMethod.getParameterTypes().length;

         int offset = 0;
         if (hasTargetObject) offset++;
         if (hasCallingObject) offset++;


         CtClass[] params = new CtClass[originalLength + offset];
         int index = 0;
         if (hasTargetObject) params[index++] = instrumentor.forName(md.classname); //target
         if (hasCallingObject) params[index++] = callingClass;

         System.arraycopy(md.calledMethod.getParameterTypes(), 0, params, offset, originalLength);

         CtMethod method = CtNewMethod.make(
               md.calledMethod.getReturnType(),
               md.callerInfoField,
               params,
               md.calledMethod.getExceptionTypes(),
               null,
               getGenadvisor());
         getGenadvisor().addMethod(method);

         String proceed = null;
         if (hasTargetObject)
         {
            proceed = MethodExecutionTransformer.getAopReturnStr(md.calledMethod) + "$1." + md.calledMethod.getName() + "(" + getArguments(params.length, hasCallingObject ? 2 : 1) +");";
         }
         else
         {
            proceed = MethodExecutionTransformer.getAopReturnStr(md.calledMethod) + md.classname + "." + md.calledMethod.getName() + "(" + getArguments(params.length, hasCallingObject ? 1 : 0) +");";
         }

         String infoName = MethodByMethodJoinPointGenerator.getInfoFieldName(md.callingHash, md.classname, md.calledHash);
         String generatorName = MethodByMethodJoinPointGenerator.getJoinPointGeneratorFieldName(md.callingHash, md.classname, md.calledHash);
         String code =
               "{" +
               "   if (" + infoName + " == null && " + generatorName + " != null)" +
               "   {" +
               "   " + generatorName + "." + JoinPointGenerator.GENERATE_JOINPOINT_CLASS + "();" +
               "   }" +
               "   if (" + infoName + " == null)" +
               "   { " +
               "      " + proceed +
               "   }" +
               "   else" +
               "   {" +
               "      " + MethodExecutionTransformer.getReturnStr(md.calledMethod) + infoName + "." + JoinPointGenerator.INVOKE_JOINPOINT + "($$);" +
               "   }" +
               "}";


         try
         {
            method.setBody(code);
         }
         catch(CannotCompileException e)
         {
            System.out.println("Error for " + md.where + " code:" + code);
            throw e;
         }
      }

      protected void setupMethod(ConByMethodDetail cd) throws NotFoundException, CannotCompileException
      {
         if (callerInfos.get(cd.callerInfoField) == null)
         {
            callerInfos.put(cd.callerInfoField, NonOptimizedCallerTransformer.PLACEHOLDER);
            callerInfoAdder.addConByMethodInfoField(getGenadvisor(), cd.callerInfoField, cd.callingHash, cd.classname, cd.calledHash);
            addJoinpoint(cd);
            createGenAdvisorConByMethodMethod(cd);
         }
      }

      private void addJoinpoint(ConByMethodDetail cd)throws CannotCompileException, NotFoundException
      {
         CtClass joinpoint = createJoinpointClass(cd);
         CtClass genadvisor = getInstrumentor().getGenadvisor();
         CtField field = new CtField(
               joinpoint,
               ConByMethodJoinPointGenerator.getInfoFieldName(cd.callingHash, cd.classname, cd.calledHash),
               genadvisor);
         field.setModifiers(Modifier.PROTECTED);
         genadvisor.addField(field);
      }

      private CtClass createJoinpointClass(ConByMethodDetail cd) throws CannotCompileException, NotFoundException
      {
         return ConByMethodJoinPointGenerator.createJoinpointBaseClass(
               getInstrumentor(),
               cd.callingHash,
               !Modifier.isStatic(cd.where.getModifiers()),
               callingClass,
               cd.calledConstructor,
               cd.classname,
               cd.calledHash,
               cd.callerInfoField);
      }

      private void createGenAdvisorConByMethodMethod(ConByMethodDetail cd) throws NotFoundException, CannotCompileException
      {
         final boolean hasCallingObject = !Modifier.isStatic(cd.where.getModifiers());
         final String info = cd.callerInfoField;

         final int originalLength = cd.calledConstructor.getParameterTypes().length;
         CtClass[] params = null;

         if (hasCallingObject)
         {
            params = new CtClass[originalLength + 1];
            params[0] = callingClass;
            System.arraycopy(cd.calledConstructor.getParameterTypes(), 0, params, 1, originalLength);
         }
         else
         {
            params = cd.calledConstructor.getParameterTypes();
         }

         CtMethod method = CtNewMethod.make(
               cd.calledConstructor.getDeclaringClass(),
               info,
               params,
               cd.calledConstructor.getExceptionTypes(),
               null,
               getGenadvisor());
         getGenadvisor().addMethod(method);

         String infoName = ConByMethodJoinPointGenerator.getInfoFieldName(cd.callingHash, cd.classname, cd.calledHash);
         String generatorName = ConByMethodJoinPointGenerator.getJoinPointGeneratorFieldName(cd.callingHash, cd.classname, cd.calledHash);
         StringBuffer code = new StringBuffer();
         code.append("{");
         code.append("   if (" + infoName + " == null && " + generatorName + " != null)");
         code.append("   {");
         code.append("   " + generatorName + "." + JoinPointGenerator.GENERATE_JOINPOINT_CLASS + "();");
         code.append("   }");
         code.append("   if (" + infoName + " == null)");
         code.append("   { ");
         code.append("      return new " + cd.calledConstructor.getDeclaringClass().getName() + "(" + getArguments(params.length, hasCallingObject ? 1 : 0) + "); ");
         code.append("   }");
         code.append("   else");
         code.append("   {");
         code.append("      return " + infoName + "." + JoinPointGenerator.INVOKE_JOINPOINT + "($$);");
         code.append("   }");
         code.append("}");

         try
         {
            method.setBody(code.toString());
         }
         catch(CannotCompileException e)
         {
            System.out.println("Error for " + cd.where + ":" + method + " code:" + code);
            throw e;
         }
      }


      protected void setupConstructor(ConByConDetail cd)throws NotFoundException, CannotCompileException
      {
         if (callerInfos.get(cd.callerInfoField) == null)
         {
            callerInfos.put(cd.callerInfoField, NonOptimizedCallerTransformer.PLACEHOLDER);
            callerInfoAdder.addConByConInfoField(getGenadvisor(), cd.callerInfoField, cd.callingIndex, cd.classname, cd.calledHash);
            addJoinpoint(cd);
            createGenAdvisorConByConMethod(cd);
         }
      }

      private void addJoinpoint(ConByConDetail cd)throws CannotCompileException, NotFoundException
      {
         CtClass joinpoint = createJoinpointClass(cd);
         CtClass genadvisor = getInstrumentor().getGenadvisor();
         CtField field = new CtField(
               joinpoint,
               ConByConJoinPointGenerator.getInfoFieldName(cd.callingIndex, cd.classname, cd.calledHash),
               genadvisor);
         field.setModifiers(Modifier.PROTECTED);
         genadvisor.addField(field);
      }

      private CtClass createJoinpointClass(ConByConDetail cd) throws CannotCompileException, NotFoundException
      {
         return ConByConJoinPointGenerator.createJoinpointBaseClass(
               getInstrumentor(),
               cd.callingIndex,
               callingClass,
               cd.calledConstructor,
               cd.classname,
               cd.calledHash,
               cd.callerInfoField);
      }


      private void createGenAdvisorConByConMethod(ConByConDetail cd)throws CannotCompileException, NotFoundException
      {
         final int originalLength = cd.calledConstructor.getParameterTypes().length;
         CtClass[] params = new CtClass[originalLength + 1];
         params[0] = callingClass;
         System.arraycopy(cd.calledConstructor.getParameterTypes(), 0, params, 1, originalLength);
         
         CtMethod method = CtNewMethod.make(
               cd.calledConstructor.getDeclaringClass(),
               cd.callerInfoField,
               params,
               cd.calledConstructor.getExceptionTypes(),
               null,
               getGenadvisor());
         getGenadvisor().addMethod(method);

      String infoName = ConByConJoinPointGenerator.getInfoFieldName(cd.callingIndex, cd.classname, cd.calledHash);
      String generatorName = ConByConJoinPointGenerator.getJoinPointGeneratorFieldName(cd.callingIndex, cd.classname, cd.calledHash);
      String code =
            "{" +
            "   if (" + infoName + " == null && " + generatorName + " != null)" +
            "   {" +
            "   " + generatorName + "." + JoinPointGenerator.GENERATE_JOINPOINT_CLASS + "();" +
            "   }" +
            "   if (" + infoName + " == null)" +
            "   { " +
            "      return new " + cd.calledConstructor.getDeclaringClass().getName() + "(" + getArguments(params.length, 1) + "); " +
            "   }" +
            "   else" +
            "   {" +
            "      return " + infoName + "." + JoinPointGenerator.INVOKE_JOINPOINT + "($$);" +
            "   }" +
            "}";

         try
         {
            method.setBody(code);
         }
         catch(CannotCompileException e)
         {
            System.out.println("Error for " + cd.callingIndex + " code:" + code);
            throw e;
         }
      }

      protected void replaceMethodCallInCon(ConstructorDetail cd)throws CannotCompileException, NotFoundException
      {
         final String advisor = " ((" + GeneratedAdvisorInstrumentor.getAdvisorFQN(callingClass) + ")" + GeneratedAdvisorInstrumentor.GET_CURRENT_ADVISOR + ")";
         final int paramsLength = cd.calledMethod.getParameterTypes().length;
         String args = null;
         if (Modifier.isStatic(cd.calledMethod.getModifiers()))
         {
            args = "this" + ((paramsLength > 0 ) ? ", $$" : "");
         }
         else
         {
            args = "this, $0" + ((paramsLength > 0 ) ? ", $$" : "");
         }

         final String ret = (!cd.calledMethod.getReturnType().equals(CtClass.voidType)) ? "$_ = " : "";

         String replaced = ret + advisor + "." + cd.callerInfoField + "(" + args + ");";

         try
         {
            cd.call.replace(replaced);
         }
         catch(CannotCompileException e)
         {
            System.out.println("Error for " + cd.con + " code:" + replaced);
            throw e;
         }
      }

      protected void replaceMethodCallInMethod(MethodDetail md)throws NotFoundException, CannotCompileException
      {
         final boolean hasCallingObject = !Modifier.isStatic(md.where.getModifiers());
         final boolean hasTargetObject = !Modifier.isStatic(md.calledMethod.getModifiers());
         final int paramsLength = md.calledMethod.getParameterTypes().length;
         String args = null;
         if (hasCallingObject && hasTargetObject)
         {
            args = "$0, this" + ((paramsLength > 0 ) ? ", $$" : "");
         }
         else if (!hasCallingObject && hasTargetObject)
         {
            args = "$0" + ((paramsLength > 0 ) ? ", $$" : "");
         }
         else if (hasCallingObject && !hasTargetObject)
         {
            args = "this" + ((paramsLength > 0 ) ? ", $$" : "");
         }
         else
         {
            args = "$$";
         }

         String advisor;
         if (hasCallingObject)
         {
            advisor = "((" + GeneratedAdvisorInstrumentor.getAdvisorFQN(callingClass) + ")" +
               GeneratedAdvisorInstrumentor.GET_CURRENT_ADVISOR + ")";
         }
         else
         {
            advisor = " ((" + GeneratedAdvisorInstrumentor.getAdvisorFQN(callingClass) + ")" + Instrumentor.HELPER_FIELD_NAME + ")";
         }
         final String ret = (md.calledMethod.getReturnType().equals(CtClass.voidType)) ? "" : "$_ = ";

         String replaced = ret + advisor + "." + md.callerInfoField + "(" + args + ");";

         try
         {
            md.call.replace(replaced);
         }
         catch(CannotCompileException e)
         {
            System.out.println("Error for " + md.where + " code:" + replaced);
            throw e;
         }
      }


      protected void replaceConCallInMethod(ConByMethodDetail cd) throws NotFoundException, CannotCompileException
      {
         final int paramsLength = cd.calledConstructor.getParameterTypes().length;
         String args = null;
         String advisor = null;
         if (!Modifier.isStatic(cd.where.getModifiers()))
         {
            args = "this" + ((paramsLength > 0) ? ", $$" : "");
            advisor = "((" + GeneratedAdvisorInstrumentor.getAdvisorFQN(callingClass) + ")" +
               GeneratedAdvisorInstrumentor.GET_CURRENT_ADVISOR + ")";
         }
         else
         {
            args = ((paramsLength > 0) ? "$$" : "");
            advisor = " ((" + GeneratedAdvisorInstrumentor.getAdvisorFQN(callingClass) + ")" + Instrumentor.HELPER_FIELD_NAME + ")";
         }

         String replaced = "$_ = " + advisor + "." + cd.callerInfoField + "(" + args + ");";

         try
         {
            cd.call.replace(replaced);
         }
         catch(CannotCompileException e)
         {
            System.out.println("Error for " + cd.where + " code:" + replaced);
            throw e;
         }
      }

      protected void replaceConCallInCon(ConByConDetail cd)throws CannotCompileException, NotFoundException
      {
         String advisor = "((" + GeneratedAdvisorInstrumentor.getAdvisorFQN(callingClass) + ")" +
               GeneratedAdvisorInstrumentor.GET_CURRENT_ADVISOR + ")";

         final int paramsLength = cd.calledConstructor.getParameterTypes().length;
         String args = "this" + ((paramsLength > 0) ? ", $$" : "");
         String replaced = "$_ = " + advisor + "." + cd.callerInfoField + "(" + args + ");";

         try
         {
            cd.call.replace(replaced);
         }
         catch(CannotCompileException e)
         {
            System.out.println("Error for " + cd.callingIndex + " code:" + replaced);
            throw e;
         }
      }
   }


   private String getArguments(int length, int offset)
   {
      StringBuffer sb = new StringBuffer("");
      for (int i = 0 ; i < length - offset; i++)
      {
         if (i > 0)
         {
            sb.append(", ");
         }
         sb.append("$" + (i + 1 + offset));
      }
      return sb.toString();
   }

}
