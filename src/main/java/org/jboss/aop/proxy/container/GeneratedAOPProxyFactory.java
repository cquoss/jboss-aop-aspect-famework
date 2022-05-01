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

import org.jboss.aop.Advised;
import org.jboss.aop.AspectManager;
import org.jboss.aop.instrument.Untransformable;
import org.jboss.aop.metadata.SimpleMetaData;

/**
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 55907 $
 */
public class GeneratedAOPProxyFactory implements AOPProxyFactory
{
   public Object createAdvisedProxy(AOPProxyFactoryParameters params)
   {
      return createAdvisedProxy(
            params.isObjectAsSuperClass(), 
            params.getProxiedClass(), 
            params.getInterfaces(), 
            params.getSimpleMetaData(), 
            params.getTarget());
   }
   
   private Object createAdvisedProxy(boolean objectAsSuper, Class proxiedClass, Class[] interfaces, SimpleMetaData metadata, Object target)
   {
      AspectManager manager = AspectManager.instance();
      
      if (target != null)
      {
         if (proxiedClass != null)
         {
            if (proxiedClass.isAssignableFrom(target.getClass()) == false)
            {
               throw new RuntimeException("Specified class type " + proxiedClass.getName() + " and target " + target.getClass().getName() + " are not compatible");
            }
         }
         else
         {
            proxiedClass = target.getClass();
         }
      }
      else if (proxiedClass == null)
      {
         proxiedClass = Object.class;
      }
      
      return getProxy(objectAsSuper, manager, proxiedClass, interfaces, metadata, target);
   }

   private Object getProxy(boolean objectAsSuper, AspectManager manager, Class proxiedClass,  
         Class[] interfaces, SimpleMetaData metadata, Object target)
   {
      try
      {
         ContainerCache cache = null;
         Class proxyClass = null;
         
         boolean isAdvised = Advised.class.isAssignableFrom(proxiedClass);
         
         if (target instanceof Untransformable)
         {
            return target;
         }
         
         
         synchronized (ContainerCache.mapLock)
         {
            cache = ContainerCache.initialise(manager, proxiedClass, interfaces);
            if (!cache.hasAspects() && !cache.requiresInstanceAdvisor())
            {
               return target;
            }
            else
            {  
               proxyClass = generateProxy(objectAsSuper, cache);
            }
         }
         
         return instantiateAndConfigureProxy(proxyClass, cache, metadata, target);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }
   
   private Class generateProxy(boolean objectAsSuper, ContainerCache cache) throws Exception
   {
      Class proxyClass = ContainerProxyFactory.getProxyClass(objectAsSuper, cache.getKey(), cache.getAdvisor());
      
      return proxyClass;
   }
   
   private Object instantiateAndConfigureProxy(Class proxyClass, ContainerCache cache, SimpleMetaData metadata, Object target) throws Exception
   {
      AspectManaged proxy = (AspectManaged)proxyClass.newInstance();
      proxy.setAdvisor(cache.getClassAdvisor());
      
      if (cache.getInstanceContainer() != null)
      {
         proxy.setInstanceAdvisor(cache.getInstanceContainer());
      }
      
      if (metadata != null)
      {
         proxy.setMetadata(metadata);
      }
      
      if (target != null)
      {
         ((Delegate)proxy).setDelegate(target);
      }
      else
      {
         ((Delegate)proxy).setDelegate(new Object());
      }
   
      return proxy;
   }
}
