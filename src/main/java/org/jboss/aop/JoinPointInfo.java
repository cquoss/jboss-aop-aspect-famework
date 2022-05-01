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

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import org.jboss.aop.advice.Interceptor;
import org.jboss.aop.advice.InterceptorFactoryWrapper;
import org.jboss.aop.joinpoint.Joinpoint;

public abstract class JoinPointInfo
{
   //These get set for generated advisors
   /** @deprecated use accessors instead */
   public InterceptorFactoryWrapper[] factories;
   /** @deprecated use accessors instead */
   public ArrayList factoryChain = new ArrayList();

   //These get set for old school advisors
   /** @deprecated use accessors instead */
   public Interceptor[] interceptors;
   /** @deprecated use accessors instead */
   public ArrayList interceptorChain = new ArrayList();
   
   private WeakReference advisor;
   
   protected volatile Joinpoint joinpoint;

   protected JoinPointInfo()
   {
      
   }
   
   protected JoinPointInfo(Advisor advisor)
   {
      setAdvisor(advisor);
   }
   
   /*
    * For copying
    */
   protected JoinPointInfo(JoinPointInfo other)
   {
      this.advisor = other.advisor;
      if (other.factories != null)
      {
         this.factories = new InterceptorFactoryWrapper[other.factories.length];
         System.arraycopy(other.factories, 0, this.factories, 0, other.factories.length);
      }
      if (other.factoryChain != null)this.factoryChain = (ArrayList)factoryChain.clone();
      if (other.interceptors != null)
      {
         this.interceptors = new Interceptor[other.interceptors.length];
         System.arraycopy(other.interceptors, 0, this.interceptors, 0, other.interceptors.length);
      }
      if (other.interceptorChain != null)this.interceptorChain = (ArrayList)interceptorChain.clone();
   }

   protected void clear()
   {
      factoryChain.clear();
      interceptorChain.clear();
      interceptors = null;
      factories = null;
   }
   
   public Advisor getAdvisor() 
   {
      if (advisor == null)
      {
         return null;
      }
      return (Advisor)advisor.get();
   }

   public void setAdvisor(Advisor advisor) 
   {
      this.advisor = new WeakReference(advisor);
   }

   public boolean hasAdvices()
   {
      return (interceptors != null && interceptors.length > 0) || (factories != null && factories.length > 0);
   }
   
   /**
    * Generated advisors start off by only initialising the factories,
    * method to intitialise the interceptors
    */
   protected JoinPointInfo initialiseInterceptors()
   {
      if (factories == null || interceptors != null)
      {
         return this;
      }

      interceptors = new Interceptor[factories.length];
      
      for (int i = 0 ; i < factories.length ; i++)
      {
         //TODO: Handle CFlow
         interceptors[i] = factories[i].create(getAdvisor(), getJoinpoint());
      }
      return this;
   }
   
   public boolean equalChains(JoinPointInfo other)
   {
      if (this.factories == null && other.factories == null) return true;
      if (!(this.factories != null && other.factories != null))return false;
      if (this.factories.length != other.factories.length) return false;
      
      for (int i = 0 ; i < this.factories.length ; i++)
      {
         if(!this.factories[i].equals(other.factories[i])) return false;
      }
      
      return true;
   }
   
   public Joinpoint getJoinpoint()
   {
      if (joinpoint == null)
      {
         joinpoint = internalGetJoinpoint();
      }
      return joinpoint;
   }
   
   public ArrayList getInterceptorChain() {
      return interceptorChain;
   }

   public void setInterceptorChain(ArrayList interceptorChain) {
      this.interceptorChain = interceptorChain;
   }

   public Interceptor[] getInterceptors() {
      return interceptors;
   }

   public void setInterceptors(Interceptor[] interceptors) {
      this.interceptors = interceptors;
   }

   public InterceptorFactoryWrapper[] getFactories()
   {
      return factories;
   }

   public void setFactories(InterceptorFactoryWrapper[] factories)
   {
      this.factories = factories;
   }

   public ArrayList getFactoryChain()
   {
      return factoryChain;
   }

   public void setFactoryChain(ArrayList factoryChain)
   {
      this.factoryChain = factoryChain;
   }

   protected abstract Joinpoint internalGetJoinpoint();
   public abstract JoinPointInfo copy();
}
