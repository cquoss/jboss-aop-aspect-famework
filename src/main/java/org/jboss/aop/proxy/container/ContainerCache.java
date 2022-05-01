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

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jboss.aop.Advised;
import org.jboss.aop.Advisor;
import org.jboss.aop.AspectManager;
import org.jboss.aop.introduction.InterfaceIntroduction;

/**
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 55907 $
 */
public class ContainerCache
{
   private static volatile int counter;
   public static final Object mapLock = new Object();
   private static WeakHashMap containerCache = new WeakHashMap();

   private AspectManager manager;
   private ContainerProxyCacheKey key;
   /** This will be ClassAdvisor if class has been woven i.e implements Advised, or ClassProxyContainer*/
   private Advisor classAdvisor; 
   private InstanceProxyContainer instanceContainer;
   boolean isClassProxyContainer;
   Class[] interfaces;

   public ContainerCache(AspectManager manager, Class proxiedClass, Class[] interfaces)
   {
      this.manager = manager;
      this.interfaces = interfaces;
      key = new ContainerProxyCacheKey(proxiedClass, interfaces);
   }
   
   public static ContainerCache initialise(AspectManager manager, Class proxiedClass)
   {
      return initialise(manager, proxiedClass, null);
   }
   
   public static ContainerCache initialise(AspectManager manager, Class proxiedClass, Class[] interfaces)
   {
      ContainerCache factory = new ContainerCache(manager, proxiedClass, interfaces);
      synchronized (mapLock)
      {
         factory.initClassContainer();
         factory.initInstanceContainer();
      }
      
      return factory;
   }

   public ContainerProxyCacheKey getKey()
   {
      return key;
   }
   
   public Advisor getAdvisor()
   {
      return (instanceContainer != null) ? instanceContainer : classAdvisor;
   }

   public Advisor getClassAdvisor()
   {
      return classAdvisor;
   }

   public InstanceProxyContainer getInstanceContainer()
   {
      return instanceContainer;
   }

   public boolean hasAspects()
   {
      if (instanceContainer != null)
      {
         return instanceContainer.hasAspects();
      }
      return classAdvisor.hasAspects();
   }
   
   public boolean requiresInstanceAdvisor()
   {
      return (interfaces != null && interfaces.length > 0);
   }
   
   public boolean isAdvised()
   {
      return Advised.class.isAssignableFrom(key.getClazz());
   }

   private void initClassContainer()
   {
      if (Advised.class.isAssignableFrom(key.getClazz()))
      {
         classAdvisor = AspectManager.instance().getAdvisor(key.getClazz());
      }
      else
      {
         classAdvisor = getCachedContainer(manager);
         if (classAdvisor == null)
         {
            classAdvisor = createAndCacheContainer();
         }
      }
   }

   private ClassProxyContainer getCachedContainer(AspectManager manager)
   {
      Map map = (Map)containerCache.get(key.getClazz());
      
      ClassProxyContainer container = null;
      if (map != null)
      {
         container = (ClassProxyContainer)map.get(key);
      }
      
      return container;
   }
   
   private ClassProxyContainer createAndCacheContainer()
   {
      ClassProxyContainer container = createContainer();
      cacheContainer(key, key.getClazz(), container);
      return container;
   }
   
   private ClassProxyContainer createContainer()
   {
      ProxyAdvisorDomain domain = new ProxyAdvisorDomain(manager, key.getClazz(), false);
      String classname = (key.getClazz() != null) ? key.getClazz().getName() : "AOP$Hollow";
      ClassProxyContainer container = new ClassProxyContainer(classname /*+ " ClassProxy" + (counter++)*/, domain);
      domain.setAdvisor(container);
      container.initialise(key.getClazz());
      
      return container;
   }
   
   private void cacheContainer(ContainerProxyCacheKey key, Class proxiedClass, ClassProxyContainer container)
   {
      Map map = (Map)containerCache.get(proxiedClass);
      if (map == null)
      {
         map = new HashMap();
         containerCache.put(proxiedClass, map);
      }
      
      map.put(key, container);      
   }
   
   
   private InterfaceIntroduction getInterfaceIntroduction(Class proxiedClass, Class[] interfaces)
   {
      String[] classNames = new String[interfaces.length];
      for (int i = 0 ; i < interfaces.length ; i++)
      {
         classNames[i] = interfaces[i].getName();
      }
      
      Class clazz = (proxiedClass != null) ? proxiedClass : Object.class;
      InterfaceIntroduction intro = new InterfaceIntroduction("Introduction" + counter++, clazz.getName(), classNames);
      return intro;
   }

   private void initInstanceContainer()
   {
      if (requiresInstanceAdvisor())
      {
         InterfaceIntroduction introduction = null;
         if (interfaces != null && interfaces.length > 0)
         {
            introduction = getInterfaceIntroduction(classAdvisor.getClazz(), interfaces);
         }

         instanceContainer = InstanceProxyContainer.createInstanceProxyContainer(classAdvisor, introduction);
      }
   }
}




