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


import org.jboss.aop.classpool.AOPClassPool;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtNewConstructor;
import javassist.NotFoundException;

/**
 * Comment
 *
 * @author <a href="mailto:kabir.khan@jboss.org">Kabir Khan</a>
 * @version $Revision$
 */
public abstract class OptimizedInvocations
{
   final static String STANDARD_INTERCEPTOR_CODE 
   = "{ "
   + "   if (currentInterceptor < interceptors.length) "
   + "   { "
   + "      try   "
   + "      { "
   + "         return interceptors[currentInterceptor++].invoke(this); "
   + "      }  " 
   + "      catch (Throwable t)" 
   + "      {"
   + "         throw t; "
   + "      }"
   + "      finally"
   + "      {"
   + "         currentInterceptor--; "
   + "      }"
   + "   } ";

   public static void defrostClassIfExists(AOPClassPool pool, String className)
   {
      try
      {
         //In some cases we get a class frozen exception. I guess if the invocation class 
         //existed, method was unwrapped and the wrapped again
         CtClass existing = pool.get(className);
         existing.defrost();
      }
      catch (NotFoundException e)
      {
         //Ignore, we are creating the class the first time
      }
   }

   /**
    * 
    * @param pool The AOPClassPool to create the optimized invocation class in
    * @param makeInnerClass If true creates the new class as an inner class of className
    * @param outerClass The class to create the invocation class as an inner class of if makeInnerClass==true
    * @param className The full class name (including package info) of the invocation class to be created
    * @param superInvocation The super class of this invocation
    * @return The created invocation class
    */
   public static CtClass makeInvocationClass(AOPClassPool pool, 
         boolean makeInnerClass, 
         CtClass outerClass, 
         String className, 
         CtClass superInvocation)throws CannotCompileException, NotFoundException
   {
      CtClass untransformable = pool.get("org.jboss.aop.instrument.Untransformable");
   
      CtClass invocation;
      if (makeInnerClass)
      {
         //Strip away the package from classname
         String innerClassName = className.substring(className.lastIndexOf('.') + 1);
         defrostClassIfExists(pool, outerClass.getName() + "$" + innerClassName);
      
         //Only static nested classes are supported
         boolean classStatic = true;
         invocation = outerClass.makeNestedClass(innerClassName, classStatic); 
         invocation.setSuperclass(superInvocation);
      }
      else
      {
         defrostClassIfExists(pool, className);
         invocation = pool.makeClass(className, superInvocation);
      }
   
      //Add the invocation constructor
      invocation.addInterface(untransformable);
      CtConstructor[] cons = superInvocation.getDeclaredConstructors();
      for (int i = 0; i < cons.length; i++)
      {
         CtConstructor conTemplate = superInvocation.getDeclaredConstructors()[i];
         CtConstructor icon = CtNewConstructor.make(conTemplate.getParameterTypes(),
                  conTemplate.getExceptionTypes(),
                  invocation);
         invocation.addConstructor(icon);
      }
   
      return invocation;
   }
}
