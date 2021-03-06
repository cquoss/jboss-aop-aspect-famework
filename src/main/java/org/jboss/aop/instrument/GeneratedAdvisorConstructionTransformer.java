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
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;

import org.jboss.aop.ClassAdvisor;
import org.jboss.aop.util.JavassistMethodHashing;

/**
 * Comment
 *
 * @author <a href="mailto:kabir.khan@jboss.org">Kabir Khan</a>
 * @version $Revision$
 */
public class GeneratedAdvisorConstructionTransformer extends ConstructionTransformer
{

   public GeneratedAdvisorConstructionTransformer(Instrumentor instrumentor)
   {
      super(instrumentor);
   }

   protected void generateConstructionInfoField(CtConstructor constructor, int index) throws NotFoundException, CannotCompileException
   {
      CtClass genadvisor = ((GeneratedAdvisorInstrumentor)instrumentor).getGenadvisor();
      String ciname = addConstructionInfoField(
            Modifier.PROTECTED,
            genadvisor,
            constructor,
            index);

      addJoinpoint(constructor, ciname, index);

      long constructorHash = JavassistMethodHashing.constructorHash(constructor);
      ((GeneratedAdvisorInstrumentor)instrumentor).initialiseConstructionInfoField(ciname, index, constructorHash);
   }

   private void addJoinpoint(CtConstructor constructor, String ciname, int index)throws CannotCompileException, NotFoundException
   {
      CtClass clazz = constructor.getDeclaringClass();
      CtClass joinpoint = createJoinpointClass(clazz, constructor, ciname, index);
      CtClass genadvisor = ((GeneratedAdvisorInstrumentor)instrumentor).getGenadvisor();
      CtField field = new CtField(
            joinpoint,
            ConstructionJoinPointGenerator.getInfoFieldName(clazz.getSimpleName(), index),
            genadvisor);
      field.setModifiers(Modifier.PROTECTED);
      genadvisor.addField(field);
   }

   private CtClass createJoinpointClass(CtClass clazz, CtConstructor constructor, String ciname, int index) throws CannotCompileException, NotFoundException
   {
      return ConstructionJoinPointGenerator.createJoinpointBaseClass(
            (GeneratedAdvisorInstrumentor)instrumentor,
            clazz,
            constructor,
            ciname,
            index);
   }


   protected void generateNotMatchedConstructionInfoField(CtConstructor constructor, int index) throws NotFoundException, CannotCompileException
   {
      generateConstructionInfoField(constructor, index);
   }

   protected boolean addInfoAsWeakReference()
   {
      return false;
   }

   public static String constructionFactory(String className)
   {
      if (className.indexOf('.') >= 0)throw new RuntimeException("constructorFactory() takes a simple class name:" + className);
      return className + "_construction_" + ClassAdvisor.NOT_TRANSFORMABLE_SUFFIX;
   }

   protected void insertInterception(CtConstructor constructor, int index) throws Exception
   {
      final String constructionWrapperName = constructor.getDeclaringClass().getSimpleName() + "_con_" + ClassAdvisor.NOT_TRANSFORMABLE_SUFFIX;

      CtMethod wrapper = createWrapperMethod(constructor, constructionWrapperName);
      insertWrapperCallInCtor(constructor, constructionWrapperName);
      createInterceptingWrapperBody(constructor, wrapper, index);
   }

   private CtMethod createWrapperMethod(CtConstructor constructor, String wrapperName)throws CannotCompileException, NotFoundException
   {
      final CtClass genadvisor = ((GeneratedAdvisorInstrumentor)instrumentor).getGenadvisor();
      CtClass[] params = constructor.getParameterTypes();
      CtClass[] wrapperParams = new CtClass[params.length + 1];
      wrapperParams[0] = constructor.getDeclaringClass();
      System.arraycopy(params, 0, wrapperParams, 1, params.length);

      CtMethod wrapper = CtNewMethod.make(
            CtClass.voidType,
            wrapperName,
            wrapperParams,
            constructor.getExceptionTypes(),
            null,
            genadvisor);
      genadvisor.addMethod(wrapper);
      return wrapper;
   }

   private void insertWrapperCallInCtor(CtConstructor constructor, String wrapperName)throws NotFoundException, CannotCompileException
   {
      String wrapperCall =
         "((" + GeneratedAdvisorInstrumentor.getAdvisorFQN(constructor.getDeclaringClass())+ ")" + GeneratedAdvisorInstrumentor.GET_CURRENT_ADVISOR + ")." +
         wrapperName + "(this" + ((constructor.getParameterTypes().length == 0) ? "" : ", $$") + ");";
      constructor.insertAfter(wrapperCall, false);
   }

   private void createInterceptingWrapperBody(CtConstructor constructor, CtMethod wrapper, int index)throws NotFoundException, CannotCompileException
   {
      String infoName = ConstructionJoinPointGenerator.getInfoFieldName(constructor.getDeclaringClass().getSimpleName(), index);
      String generatorName = ConstructionJoinPointGenerator.getJoinPointGeneratorFieldName(constructor.getDeclaringClass().getSimpleName(), index);
      String code =
         "{" +
         "   if (" + infoName + " == null && " + generatorName + " != null)" +
         "   {" +
         "   " + generatorName + "." + JoinPointGenerator.GENERATE_JOINPOINT_CLASS + "();" +
         "   }" +
         "   if (" + infoName + " != null)" +
         "   { " +
         "    " + infoName + "." + JoinPointGenerator.INVOKE_JOINPOINT + "($$);" +
         "   }" +
         "}";

      wrapper.setBody(code);
   }
}
