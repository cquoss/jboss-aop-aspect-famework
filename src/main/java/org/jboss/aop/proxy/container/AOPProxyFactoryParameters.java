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

import org.jboss.aop.metadata.SimpleMetaData;

/**
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 55907 $
 */
public class AOPProxyFactoryParameters
{
   private Class proxiedClass;
   private Object target;
   private Class[] interfaces;
   private boolean objectAsSuperClass;
   private SimpleMetaData simpleMetaData;
   
   public AOPProxyFactoryParameters()
   {
   }

   public AOPProxyFactoryParameters(
         Class proxiedClass, 
         Object target, 
         Class[] interfaces, 
         boolean objectAsSuperClass,
         SimpleMetaData simpleMetaData)
   {
      this.interfaces = interfaces;
      this.objectAsSuperClass = objectAsSuperClass;
      this.proxiedClass = proxiedClass;
      this.target = target;
      this.simpleMetaData = simpleMetaData;
   }

   public Class[] getInterfaces()
   {
      return interfaces;
   }

   public void setInterfaces(Class[] interfaces)
   {
      this.interfaces = interfaces;
   }

   public boolean isObjectAsSuperClass()
   {
      return objectAsSuperClass;
   }

   public void setObjectAsSuperClass(boolean objectAsSuperClass)
   {
      this.objectAsSuperClass = objectAsSuperClass;
   }

   public Class getProxiedClass()
   {
      return proxiedClass;
   }

   public void setProxiedClass(Class proxiedClass)
   {
      this.proxiedClass = proxiedClass;
   }

   public SimpleMetaData getSimpleMetaData()
   {
      return simpleMetaData;
   }

   public void setSimpleMetaData(SimpleMetaData simpleMetaData)
   {
      this.simpleMetaData = simpleMetaData;
   }

   public Object getTarget()
   {
      return target;
   }

   public void setTarget(Object target)
   {
      this.target = target;
   }
   
}
