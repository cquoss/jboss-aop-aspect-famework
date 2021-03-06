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
package org.jboss.aop.metadata;
import javassist.CtMethod;
import org.jboss.aop.joinpoint.Invocation;
import org.jboss.aop.joinpoint.MethodInvocation;
import org.jboss.aop.util.PayloadKey;

import EDU.oswego.cs.dl.util.concurrent.ConcurrentReaderHashMap;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
/**
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 46037 $
 *
 */
public class MethodMetaData implements MetaDataResolver
{
   Map methodMetaData = new ConcurrentReaderHashMap();
   HashMap inexactMatches;

   public boolean hasTag(String group)
   {
      Iterator values = methodMetaData.values().iterator();
      while (values.hasNext())
      {
         SimpleMetaData map = (SimpleMetaData)values.next();
         if (map.hasTag(group)) return true;
      }
      return false;
   }
   public void tagMethod(Method method, Object tag)
   {
      addMethodMetaData(method, tag, EMPTY_TAG, new Object(), PayloadKey.TRANSIENT);
   }
   public void addMethodMetaData(Method method, Object tag, Object attr, Object value)
   {
      addMethodMetaData(method, tag, attr, value, PayloadKey.MARSHALLED, true);
   }
   public void addMethodMetaData(Method method, Object tag, Object attr, Object value, boolean exactMatch)
   {
      addMethodMetaData(method, tag, attr, value, PayloadKey.MARSHALLED, exactMatch);
   }
   public void addMethodMetaData(Method method, Object tag, Object attr, Object value, PayloadKey type)
   {
      addMethodMetaData(method.toString(), tag, attr, value, type, true);
   }
   public void addMethodMetaData(Method method, Object tag, Object attr, Object value, PayloadKey type, boolean exactMatch)
   {
      addMethodMetaData(method.toString(), tag, attr, value, type, exactMatch);
   }

   private void addMethodMetaData(String key, Object tag, Object attr, Object value, PayloadKey type, boolean exactMatch)
   {
      synchronized (methodMetaData)
      {
      SimpleMetaData methodData = (SimpleMetaData)methodMetaData.get(key);
      if (methodData == null)
      {
         methodData = new SimpleMetaData();
         methodMetaData.put(key, methodData);
      }
      methodData.addMetaData(tag, attr, value, type);
      }      
      manageInexactMatches(key, tag, attr, exactMatch);
   }
   
   private synchronized void manageInexactMatches(String key, Object tag, Object attr, boolean exactMatch)
   {
      if (!exactMatch)
      {
         if (inexactMatches == null)
         {
            inexactMatches = new HashMap();
         }
         
         HashMap tags = (HashMap)inexactMatches.get(key);
         if (tags == null)
         {
            tags = new HashMap();
            inexactMatches.put(key, tags);
         }
         
         HashMap attrs = (HashMap)tags.get(tag);
         if (attrs == null)
         {
            attrs = new HashMap();
            tags.put(tag, attrs);
         }
         attrs.put(attr, Boolean.TRUE);
      }
      else
      {
         if (inexactMatches == null)return;
         HashMap tags = (HashMap)inexactMatches.get(key);
         if (tags == null) return;
         HashMap attrs = (HashMap)tags.get(tag);
         if (attrs == null) return;
         attrs.remove(attr);
      }

   }

   public synchronized boolean tagWasMatchedInexactly(Method method, Object tag, Object attr)
   {
      if (inexactMatches == null) return false;
      HashMap tags = (HashMap)inexactMatches.get(method.toString());
      if (tags == null) return false;
      HashMap attrs = (HashMap)tags.get(tag);
      if (attrs == null) return false;
      return (attrs.get(attr) != null);
   }
   
   public boolean hasTag(Method method, String tag)
   {
      SimpleMetaData meta = getMethodMetaData(method);
      if (meta == null) return false;
      return meta.hasTag(tag);
   }

   public Iterator getMethods()
   {
      return methodMetaData.keySet().iterator();
   }

   /**
    * This requires a method signature derived from java.lang.reflect.Method.toString()
    * @param method
    * @return
    */
   public SimpleMetaData getMethodMetaData(String method)
   {
      return (SimpleMetaData)methodMetaData.get(method);
   }

   public SimpleMetaData getMethodMetaData(Method method)
   {
      return getMethodMetaData(method.toString());
   }

   public Object getMethodMetaData(Method method, Object tag, Object attr)
   {
      SimpleMetaData methodData = (SimpleMetaData)methodMetaData.get(method.toString());
      if (methodData == null) return null;
      return methodData.getMetaData(tag, attr);
   }

   public void clear()
   {
      methodMetaData.clear();
   }

   public Object resolve(Invocation invocation, Object tag, Object attr)
   {
      MethodInvocation methodInvocation = (MethodInvocation)invocation;
      return getMethodMetaData(methodInvocation.getMethod(), tag, attr);
   }

   public SimpleMetaData getAllMetaData(Invocation invocation)
   {
      MethodInvocation methodInvocation = (MethodInvocation)invocation;
      return (SimpleMetaData)methodMetaData.get(methodInvocation.getMethod().toString());
   }

   //--- temporary interface until metadata is bound to actual class, this is needed for loader/compiler to
   // match annotations to pointcuts

   private String getCtMethodKey(CtMethod method)
   {
      return method.getName() + ":" + method.getSignature();
   }

   public void tagMethod(CtMethod method, Object tag)
   {
      addMethodMetaData(method, tag, EMPTY_TAG, PayloadKey.TRANSIENT);
   }
   public void addMethodMetaData(CtMethod method, Object tag, Object attr, Object value)
   {
      addMethodMetaData(getCtMethodKey(method), tag, attr, value, PayloadKey.MARSHALLED, true);
   }
   public void addMethodMetaData(CtMethod method, Object tag, Object attr, Object value, PayloadKey type)
   {
      addMethodMetaData(getCtMethodKey(method), tag, attr, value, type, true);
   }

   public boolean hasGroup(CtMethod method, String tag)
   {
      SimpleMetaData meta = getMethodMetaData(getCtMethodKey(method));
      if (meta == null) return false;
      return meta.hasTag(tag);
   }


}
