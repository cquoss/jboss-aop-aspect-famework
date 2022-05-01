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

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 55907 $
 */
public class ContainerProxyCacheKey implements Serializable
{
   private static final WeakReference[] EMTPY_ARRAY = new WeakReference[0];
   private static final long serialVersionUID = 8758283842273747310L;
   
   WeakReference clazzRef;
   WeakReference[] addedInterfaces = EMTPY_ARRAY;
   int hashcode = 0;
   
   public ContainerProxyCacheKey(Class clazz)
   {
      this.clazzRef = new WeakReference(clazz); 
   }
   
   public ContainerProxyCacheKey(Class clazz, Class[] addedInterfaces)
   {
      this.clazzRef = new WeakReference(clazz); 
      populateAddedInterfaces(addedInterfaces);
   }

   private void populateAddedInterfaces(Class[] addedIfaces)
   {
      if (addedIfaces == null)
      {
         return;
      }
      
      addedInterfaces = new WeakReference[addedIfaces.length];
      
      for (int i = 0 ; i < addedIfaces.length ; i++)
      {
         addedInterfaces[i] = new WeakReference(addedIfaces[i]);
      }
      
      Arrays.sort(addedInterfaces, Alphabetical.singleton);
   }

   public Class getClazz()
   {
      Class clazz = (Class)clazzRef.get();
      if (clazz != null)
      {
         return clazz; 
      }
      return null;
   }
   
   public boolean equals(Object obj)
   {
      if (this == obj)
      {
         return true;
      }
      
      if (obj.getClass() != ContainerProxyCacheKey.class)
      {
         return false;
      }
      
      ContainerProxyCacheKey other = (ContainerProxyCacheKey)obj;

      Class thisClass = (Class)this.clazzRef.get();
      Class otherClass = (Class)other.clazzRef.get(); 
      
      if (thisClass == null || otherClass == null)
      {
         return false;
      }
      
      if (!thisClass.equals(otherClass))
      {
         return false;
      }
      
      if ((this.addedInterfaces == null && other.addedInterfaces != null) ||
            (this.addedInterfaces == null && other.addedInterfaces != null))
      {
         return false;
      }
      
      if (this.addedInterfaces != null && other.addedInterfaces != null)
      {
         if (this.addedInterfaces.length != other.addedInterfaces.length)
         {
            return false;
         }
         
         for (int i = 0 ; i < this.addedInterfaces.length ; i++)
         {
            Class thisIf = (Class)addedInterfaces[i].get();
            Class otherIf = (Class)other.addedInterfaces[i].get();
            
            if (!thisIf.equals(otherIf))
            {
               return false;
            }
         }
      }
      
      return true;
   }

   public int hashCode()
   {
      if (hashcode == 0)
      {
         
         Class clazz = (Class)clazzRef.get();
         StringBuffer sb = new StringBuffer();
         
         if (clazz != null)
         {
            sb.append(clazz.getName());
         }
         
         if (addedInterfaces != null)
         {
            for (int i = 0 ; i < addedInterfaces.length ; i++)
            {
               sb.append(";");
               sb.append(((Class)addedInterfaces[i].get()).getName());
            }
         }
         
         hashcode = sb.toString().hashCode(); 
         
      }
      
      return hashcode;
   }
   
   public String toString()
   {
      Class clazz = (Class)clazzRef.get();
      return clazz + ";" + Arrays.asList(addedInterfaces);
   }
   
   static class Alphabetical implements Comparator
   {
      static Alphabetical singleton = new Alphabetical();
      
      public int compare(Object o1, Object o2)
      {
         String name1 = ((Class)((WeakReference)o1).get()).getName();
         String name2 = ((Class)((WeakReference)o2).get()).getName();
         return (name1).compareTo(name2);
      }
   }
}
