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
package org.jboss.aop.proxy.container;

import java.util.Arrays;
import java.lang.reflect.Method;

import org.jboss.aop.ClassContainer;
import org.jboss.aop.AspectManager;
import org.jboss.aop.ClassAdvisor;
import org.jboss.aop.introduction.InterfaceIntroduction;
import org.jboss.aop.util.ConstructorComparator;
import org.jboss.aop.util.MethodHashing;

/**
 * Extension of ClassContainer needed because of Mixins
 * we want to be able to match pointcut expressions on the base class of the delegate
 * we also want to be able to match pointcuts of instanceof{} of the Mixin interfaces.
 *
 * We also want to create constructor tables based on the constructor of the delegate so we can intercept
 * construction
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 */
public class ClassProxyContainer extends ClassContainer
{
   public ClassProxyContainer(String name, AspectManager manager)
   {
      super(name, manager);
   }
   
   protected void createConstructorTables()
   {
      Class superClass = clazz.getSuperclass();
      if (superClass != null)
      {
         constructors = clazz.getSuperclass().getDeclaredConstructors();
         for (int i = 0; i < constructors.length; i++)
         {
            constructors[i].setAccessible(true);
         }
         Arrays.sort(constructors, ConstructorComparator.INSTANCE);
      }
   }


   protected void createMethodMap()
   {
      try
      {
         Method[] declaredMethods = clazz.getMethods();
         Class superclass = clazz.getSuperclass();
         for (int i = 0; i < declaredMethods.length; i++)
         {
            Method method = declaredMethods[i];
            if (ClassAdvisor.isAdvisable(method))
            {
               long hash = MethodHashing.methodHash(method);
               try
               {
                  if (method.getDeclaringClass().getName().indexOf(ContainerProxyFactory.PROXY_NAME_PREFIX) >= 0 && superclass != null)
                     method = superclass.getMethod(method.getName(), method.getParameterTypes());
               }
               catch (NoSuchMethodException ignored)
               {
                  // this is a mixin method or a proxy method
               }
               advisedMethods.put(hash, method);
            }
         }
         
         for (int i = 0; i < interfaceIntroductions.size(); ++i)
         {
            InterfaceIntroduction ii = (InterfaceIntroduction) interfaceIntroductions.get(i);
            String[] intf = ii.getInterfaces();
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            for (int j = 0; j < intf.length; ++j)
            {
               Class iface = cl.loadClass(intf[j]);
               Method[] ifaceMethods = iface.getMethods();
               for (int k = 0; k < ifaceMethods.length; k++)
               {
                  long hash = MethodHashing.methodHash(ifaceMethods[k]);
                  
                  if (advisedMethods.get(hash) == null)
                  {
                     advisedMethods.put(hash, ifaceMethods[k]);
                  }
               }
            }
         }
         
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   public InstanceProxyContainer createInstanceProxyContainer(InterfaceIntroduction introduction)
   {
      ProxyAdvisorDomain domain = new ProxyAdvisorDomain(manager, clazz, false);
      domain.setInheritsBindings(true);
      domain.setInheritsDeclarations(true);
      if (introduction != null)
      {
         domain.addInterfaceIntroduction(introduction);
      }

      InstanceProxyContainer ia = new InstanceProxyContainer(super.getName(), domain, this);
      
      return ia;
   }
   
   public InstanceProxyContainer createInstanceProxyContainer()
   {
      return createInstanceProxyContainer(null);
   }

   public void initialise(Class proxiedClass)
   {
      setClass(proxiedClass);
      ((ProxyAdvisorDomain)manager).attachAdvisor();
      initializeInterfaceIntroductions(proxiedClass);
      super.initializeClassContainer();
   }

   public boolean chainOverridingForInheritedMethods()
   {
      return true;
   }
}
