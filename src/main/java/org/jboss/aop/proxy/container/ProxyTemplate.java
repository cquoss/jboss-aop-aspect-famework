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

import org.jboss.aop.Advisor;
import org.jboss.aop.InstanceAdvisor;
import org.jboss.aop.metadata.SimpleMetaData;

/**
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 43258 $
 */
public class ProxyTemplate implements Delegate, AspectManaged
{

   public ProxyTemplate()
   {
   }

   private Advisor classAdvisor;
   private InstanceAdvisor instanceAdvisor;
   protected volatile Advisor currentAdvisor;
   
   private Object delegate;
   private Object[] mixins; // Do not remove this
   private SimpleMetaData metadata;


   public Object getDelegate()
   {
      return delegate;
   }

   public void setDelegate(Object delegate)
   {
      this.delegate = delegate;
   }

   public Advisor getAdvisor()
   {
      return currentAdvisor;
   }

   public void setAdvisor(Advisor advisor)
   {
      this.classAdvisor = advisor;
      this.currentAdvisor = classAdvisor;
   }

   public void setMetadata(SimpleMetaData metadata)
   {
      this.metadata = metadata;
   }

   public void setInstanceAdvisor(InstanceAdvisor ia)
   {
      synchronized (this)
      {
         if (this.instanceAdvisor != null)
         {
            throw new RuntimeException("InstanceAdvisor already set");
         }
         
         if (!(ia instanceof org.jboss.aop.proxy.container.InstanceProxyContainer))
         {
            throw new RuntimeException("Wrong type for instance advisor: " + instanceAdvisor);
         }
         this.instanceAdvisor = ia;
         
         currentAdvisor = (org.jboss.aop.proxy.container.InstanceProxyContainer)ia;
      }
   }

   public InstanceAdvisor getInstanceAdvisor()
   {
      synchronized(this)
      {
         if (instanceAdvisor == null)
         {
            org.jboss.aop.proxy.container.InstanceProxyContainer ipc = ((org.jboss.aop.proxy.container.ClassProxyContainer)currentAdvisor).createInstanceProxyContainer();
            setInstanceAdvisor(ipc);
         }
      }
      return instanceAdvisor;
   }
   
}
