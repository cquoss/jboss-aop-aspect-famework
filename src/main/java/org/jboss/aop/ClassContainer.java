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
package org.jboss.aop;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;

import org.jboss.aop.advice.AdviceBinding;
import org.jboss.aop.metadata.ClassMetaDataBinding;
import org.jboss.aop.metadata.ClassMetaDataLoader;
import org.jboss.aop.util.ConstructorComparator;
import org.jboss.aop.util.FieldComparator;
import org.jboss.aop.util.MethodHashing;
import gnu.trove.TLongObjectHashMap;

/**
 * Comment
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 59703 $
 */
public class ClassContainer extends Advisor
{
   private boolean chainOverridingForInheritedMethods;

   public ClassContainer(String name, AspectManager manager)
   {
      super(name, manager);
   }

   public void initializeClassContainer()
   {
      initializeMetadata();
      rebuildInterceptors();
   }
   
   public void setClass(Class clazz)
   {
      this.clazz = clazz;
   }

   public void initializeMetadata()
   {
      createMethodMap();
      createConstructorTables();
      createFieldTable();
      rebindClassMetaData();
      deployAnnotationOverrides();
   }

   protected Field[] advisedFields;

   private void populateFieldTable(ArrayList fields, Class superclass)
   {
      if (superclass == null) return;
      if (superclass.equals(Object.class)) return;

      populateFieldTable(fields, superclass.getSuperclass());

      // if (!isAdvised(superclass)) return;

      ArrayList temp = new ArrayList();
      Field[] declaredFields = superclass.getDeclaredFields();
      for (int i = 0; i < declaredFields.length; i++)
      {
         if (ClassAdvisor.isAdvisable(declaredFields[i]))
         {
            temp.add(declaredFields[i]);
         }
      }
      Collections.sort(temp, FieldComparator.INSTANCE);
      fields.addAll(temp);
   }

   /**
    * Gets advised methods.
    */
   protected void createFieldTable()
   {
      ArrayList fields = new ArrayList();

      populateFieldTable(fields, clazz);

      advisedFields = (Field[]) fields.toArray(new Field[fields.size()]);

   }

   protected void rebuildInterceptors()
   {
      adviceBindings.clear();
      createInterceptorChains();
   }

   public void addClassMetaData(ClassMetaDataBinding data)
   {
      classMetaDataBindings.add(data);
      if (this.clazz == null) return;  // don't bind till later.

      bindClassMetaData(data);
      // Recalculate interceptorPointcuts because of MetaDataInterceptorPointcuts
      adviceBindings.clear();
      doesHaveAspects = false;
      rebuildInterceptors();

   }



   public void removeClassMetaData(ClassMetaDataBinding data)
   {
      if (classMetaDataBindings.remove(data))
      {
         if (this.clazz == null) return; // not bound yet
         rebindClassMetaData();
         // Recalculate interceptorPointcuts because of MetaDataInterceptorPointcuts
         adviceBindings.clear();
         doesHaveAspects = false;
         rebuildInterceptors();
      }
   }

   protected void bindClassMetaData(ClassMetaDataBinding data)
   {
      try
      {
         ClassMetaDataLoader loader = data.getLoader();
         Object[] objs = advisedMethods.getValues();
         Method[] methods = new Method[objs.length];
         for (int i = 0; i < objs.length; i++) methods[i] = (Method) objs[i];
         loader.bind(this, data, methods, advisedFields, clazz.getDeclaredConstructors());
      }
      catch (Exception ex)
      {
         // REVISIT:  Need to know how errors affects deployment
         ex.printStackTrace();
      }
   }

   protected void rebindClassMetaData()
   {
      defaultMetaData.clear();
      methodMetaData.clear();
      fieldMetaData.clear();
      constructorMetaData.clear();
      classMetaData.clear();

      for (int i = 0; i < classMetaDataBindings.size(); i++)
      {
         ClassMetaDataBinding data = (ClassMetaDataBinding) classMetaDataBindings.get(i);
         bindClassMetaData(data);
      }
   }

   protected void createMethodMap()
   {
      try
      {
         Method[] declaredMethods = clazz.getMethods();
         for (int i = 0; i < declaredMethods.length; i++)
         {
            if (ClassAdvisor.isAdvisable(declaredMethods[i]))
            {
               long hash = MethodHashing.methodHash(declaredMethods[i]);
               advisedMethods.put(hash, declaredMethods[i]);
            }
         }
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   protected MethodInfo createMethodInfo()
   {
      return new MethodInfo();
   }

   protected TLongObjectHashMap initializeMethodChain()
   {
      TLongObjectHashMap newInterceptors = new TLongObjectHashMap();
      long[] keys = advisedMethods.keys();
      for (int i = 0; i < keys.length; i++)
      {
         MethodInfo info = createMethodInfo();
         Method amethod = (Method) advisedMethods.get(keys[i]);
         info.setAdvisedMethod(amethod);
         info.setUnadvisedMethod(amethod);
         info.setHash(keys[i]);
         info.setAdvisor(this);
         newInterceptors.put(keys[i], info);
      }
      return newInterceptors;
   }

   protected void createConstructorTables()
   {
      constructors = clazz.getDeclaredConstructors();
      if (constructors.length > 0)
      {
         for (int i = 0; i < constructors.length; i++)
         {
            constructors[i].setAccessible(true);
         }
         Arrays.sort(constructors, ConstructorComparator.INSTANCE);
      }
   }
   
   protected void createInterceptorChains()
   {
      TLongObjectHashMap newMethodInfos = initializeMethodChain();
      ArrayList newConstructorInfos = initializeConstructorChain();
      
      LinkedHashMap bindings = manager.getBindings();
      synchronized (bindings)
      {
         if (bindings.size() > 0)
         {
            Iterator it = bindings.values().iterator();
            while (it.hasNext())
            {
               AdviceBinding binding = (AdviceBinding) it.next();
               if (AspectManager.verbose) System.out.println("iterate binding " + binding.getName());
               resolveMethodPointcut(newMethodInfos, binding);
               resolveConstructorPointcut(newConstructorInfos, binding);
            }
         }
      }
      finalizeConstructorChain(newConstructorInfos);
      finalizeMethodChain(newMethodInfos);
      constructorInfos = new ConstructorInfo[newConstructorInfos.size()];
      if (constructorInfos.length > 0)
         constructorInfos = (ConstructorInfo[]) newConstructorInfos.toArray(constructorInfos);
      methodInterceptors = newMethodInfos;
      
      populateInterceptorsFromInfos();
      
      doesHaveAspects = adviceBindings.size() > 0;
   }
   
   /**
    * @return the value of chainOverridingForInheritedMethods
    * @see Advisor#chainOverridingForInheritedMethods()
    */
   public boolean chainOverridingForInheritedMethods()
   {
      return chainOverridingForInheritedMethods;
   }

   /**
    * @param overriding the new value of chainOverridingForInheritedMethods
    * @see Advisor#chainOverridingForInheritedMethods()
    */
   protected void setChainOverridingForInheritedMethods(boolean overriding)
   {
      this.chainOverridingForInheritedMethods = overriding;
   }
}
