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

import org.jboss.aop.joinpoint.Joinpoint;
import org.jboss.aop.joinpoint.MethodJoinpoint;
import org.jboss.aop.util.MethodHashing;

import java.lang.reflect.Method;

/**
 *  This class is here to eliminate a hash lookup in invokeMethod
 */
public class MethodInfo extends JoinPointInfo
{
   /** @deprecated use accessors instead */
   public Method advisedMethod;
   /** @deprecated use accessors instead */
   public Method unadvisedMethod;
   /** @deprecated use accessors instead */
   public long hash;
   
   public MethodInfo()
   {
      
   }
   
   public MethodInfo(Class clazz, long hash, long unadvisedHash, Advisor advisor)
   {
      super(advisor);

      try
      {
         this.hash = hash;
         advisedMethod = MethodHashing.findMethodByHash(clazz, hash);
         unadvisedMethod = MethodHashing.findMethodByHash(clazz, unadvisedHash);
         this.setAdvisor(advisor);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
      
   }
   
   /*
    * For copying
    */
   private MethodInfo(MethodInfo other)
   {
      super(other);
      this.advisedMethod = other.advisedMethod;
      this.unadvisedMethod = other.unadvisedMethod;
      this.hash = other.hash;
   }
   
   protected Joinpoint internalGetJoinpoint()
   {
      return new MethodJoinpoint(advisedMethod);
   }
   
   public JoinPointInfo copy()
   {
      return new MethodInfo(this);
   }

   public Method getAdvisedMethod() 
   {
      return advisedMethod;
   }

   public void setAdvisedMethod(Method advisedMethod) 
   {
      this.advisedMethod = advisedMethod;
   }

   public long getHash() {
      return hash;
   }

   public void setHash(long hash) 
   {
      this.hash = hash;
   }

   public Method getUnadvisedMethod() 
   {
      return unadvisedMethod;
   }

   public void setUnadvisedMethod(Method unadvisedMethod) {
      this.unadvisedMethod = unadvisedMethod;
   }
   
   public String toString()
   {
      StringBuffer sb = new StringBuffer("Method");
      sb.append("[");
      sb.append("method=" + advisedMethod);
      sb.append("]");
      return sb.toString();
   }
}
