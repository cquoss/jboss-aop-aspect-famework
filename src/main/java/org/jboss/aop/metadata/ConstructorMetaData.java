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
import javassist.CtConstructor;
import org.jboss.aop.joinpoint.ConstructorInvocation;
import org.jboss.aop.joinpoint.Invocation;
import org.jboss.aop.util.PayloadKey;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Iterator;
/**
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 37406 $
 *
 */
public class ConstructorMetaData implements MetaDataResolver
{
   HashMap constructorMetaData = new HashMap();

   public boolean hasTag(String tag)
   {
      Iterator values = constructorMetaData.values().iterator();
      while (values.hasNext())
      {
         SimpleMetaData map = (SimpleMetaData)values.next();
         if (map.hasTag(tag)) return true;
      }
      return false;
   }
   public synchronized boolean hasTag(Constructor constructor, String tag)
   {
      SimpleMetaData meta = getConstructorMetaData(constructor);
      if (meta == null) return false;
      return meta.hasTag(tag);
   }

   public void tagConstructor(Constructor con, Object tag)
   {
      addConstructorMetaData(con, tag, EMPTY_TAG, new Object(), PayloadKey.TRANSIENT);
   }

   public void addConstructorMetaData(Constructor constructor, Object tag, Object attr, Object value)
   {
      addConstructorMetaData(constructor.toString(), tag, attr, value, PayloadKey.MARSHALLED);
   }
   public void addConstructorMetaData(Constructor constructor, Object tag, Object attr, Object value, PayloadKey type)
   {
      addConstructorMetaData(constructor.toString(), tag, attr, value, type);
   }
   public synchronized void addConstructorMetaData(String key, Object tag, Object attr, Object value, PayloadKey type)
   {
      SimpleMetaData constructorData = (SimpleMetaData)constructorMetaData.get(key);
      if (constructorData == null)
      {
         constructorData = new SimpleMetaData();
         constructorMetaData.put(key, constructorData);
      }
      constructorData.addMetaData(tag, attr, value, type);
   }

   public synchronized Iterator getConstructors()
   {
      return constructorMetaData.keySet().iterator();
   }

   public synchronized SimpleMetaData getConstructorMetaData(Constructor constructor)
   {
      return (SimpleMetaData)constructorMetaData.get(constructor.toString());
   }

   public synchronized SimpleMetaData getConstructorMetaData(String constructor)
   {
      return (SimpleMetaData)constructorMetaData.get(constructor);
   }

   public synchronized Object getConstructorMetaData(Constructor constructor, Object tag, Object attr)
   {
      SimpleMetaData constructorData = (SimpleMetaData)constructorMetaData.get(constructor.toString());
      if (constructorData == null) return null;
      return constructorData.getMetaData(tag, attr);
   }

   public synchronized Object getConstructorMetaData(String constructor, Object tag, Object attr)
   {
      SimpleMetaData constructorData = (SimpleMetaData)constructorMetaData.get(constructor);
      if (constructorData == null) return null;
      return constructorData.getMetaData(tag, attr);
   }

   public synchronized void clear()
   {
      constructorMetaData.clear();
   }

   public Object resolve(Invocation invocation, Object tag, Object attr)
   {
      Constructor constructor = ((ConstructorInvocation)invocation).getConstructor();
      return getConstructorMetaData(constructor, tag, attr);
   }

   public synchronized SimpleMetaData getAllMetaData(Invocation invocation)
   {
      Constructor constructor = ((ConstructorInvocation)invocation).getConstructor();
      return (SimpleMetaData)constructorMetaData.get(constructor);
   }

   // temporary interface so that loader/compiler can get annotations

   public void tagConstructor(CtConstructor con, Object tag)
   {
      addConstructorMetaData(con, tag, EMPTY_TAG, new Object());
   }

   public void addConstructorMetaData(CtConstructor constructor, Object tag, Object attr, Object value)
   {
      addConstructorMetaData(constructor.getSignature(), tag, attr, value, PayloadKey.TRANSIENT);
   }

   public synchronized boolean hasGroup(CtConstructor constructor, String tag)
   {
      SimpleMetaData meta = (SimpleMetaData)constructorMetaData.get(constructor.getSignature());
      if (meta == null) return false;
      return meta.hasTag(tag);
   }


}
