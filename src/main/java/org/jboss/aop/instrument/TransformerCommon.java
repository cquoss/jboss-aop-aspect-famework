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

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

import org.jboss.aop.standalone.Compiler;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;

/** 
 *  A few handy methods and common things used by the other Transformers
 * @author <a href="mailto:kabirkhan@bigfoot.com">Kabir Khan</a>
 * @version $Revision: 43757 $
 *
 */
public class TransformerCommon {

   final static URL[] NO_URLS = new URL[0];
   final static CtClass[] EMPTY_CTCLASS_ARRAY = new CtClass[0];
   final static String WEAK_REFERENCE = WeakReference.class.getName();
   
   public static boolean isCompileTime()
   {
      return Compiler.loader != null;
   }
   
   public static void compileOrLoadClass(CtClass classForPackage, CtClass newClass)
   {
      compileOrLoadClass(classForPackage, newClass, isCompileTime());
   }
   
   /** Compiles the class to file or adds it to the class pool  
    * 
    * @param classForPackage The class to be used to determine the directory to place the class in
    * @param invocation The class to be comiled/added to class pool
    * @throws Exception
    */
   public static void compileOrLoadClass(CtClass classForPackage, CtClass newClass, boolean compile)
   {
      try
      {
         // If compile time
         if (compile)
         {
            File file;
            URLClassLoader loader = Compiler.loader;
            if (loader == null)
            {
               loader = new URLClassLoader(NO_URLS, Thread.currentThread().getContextClassLoader());
            }
            URL url = loader.getResource(
                  classForPackage.getName().replace('.', '/') + ".class");
            String path = url.toString();
            path = path.substring(0, path.lastIndexOf('/') + 1);
            path = path + newClass.getSimpleName() + ".class";
            URI newUrl = new URI(path);
            file = new File(newUrl);
            FileOutputStream fp = new FileOutputStream(file);
            fp.write(newClass.toBytecode());
            fp.close();
         }
         else
         // if load time
         {
            newClass.toClass();
         }
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   protected static void addInfoField(Instrumentor instrumentor, String infoClassName, String infoName, 
         int modifiers, CtClass addTo, boolean weak, CtField.Initializer init) throws NotFoundException, CannotCompileException
   {
      if (weak)
      {
         addWeakReferenceInfoField(instrumentor, infoClassName, infoName, modifiers, addTo, init);
      }
      else
      {
         addStrongReferenceInfoField(instrumentor, infoClassName, infoName, modifiers, addTo, init);
      }
   }

   private static void addWeakReferenceInfoField(Instrumentor instrumentor, String infoClassName, String infoName, 
         int modifiers, CtClass addTo, CtField.Initializer init) throws NotFoundException, CannotCompileException
   {
      CtClass type = instrumentor.forName(WEAK_REFERENCE);
      CtField field = new CtField(type, infoName, addTo);
      field.setModifiers(modifiers);
      addTo.addField(field, init);
   }
   
   private static void addStrongReferenceInfoField(Instrumentor instrumentor, String infoClassName, String infoName, 
         int modifiers, CtClass addTo, CtField.Initializer init) throws NotFoundException, CannotCompileException
   {
      CtClass type = instrumentor.forName(infoClassName);
      CtField field = new CtField(type, infoName, addTo);
      field.setModifiers(modifiers);
      addTo.addField(field, init);
   }

   protected static String infoFromWeakReference(String infoClassName, String localName, String infoName)
   {
         return infoClassName + " " + localName + " = (" + infoClassName + ")" + infoName + ".get();";      
   }
   
}
