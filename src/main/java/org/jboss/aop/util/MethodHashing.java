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
package org.jboss.aop.util;
 
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Create a unique hash for  
 * 
 * @author  <a href="mailto:marc@jboss.org">Marc Fleury</a>
 * @version $Revision: 37406 $
 */
public class MethodHashing
{
   // Constants -----------------------------------------------------
   
   // Static --------------------------------------------------------
   static Map hashMap = new WeakHashMap();

   public static Method findMethodByHash(Class clazz, long hash) throws Exception
   {
      Method[] methods = clazz.getDeclaredMethods();
      for (int i = 0; i < methods.length; i++)
      {
         if (methodHash(methods[i]) == hash) return methods[i];
      }

      if (clazz.isInterface())
      {
         final Class[] interfaces = clazz.getInterfaces() ;
         final int numInterfaces = interfaces.length ;
         for(int count = 0 ; count < numInterfaces ; count++)
         {
            final Method method = findMethodByHash(interfaces[count], hash) ;
            if (method != null)
            {
               return method ;
            }
         }
      }
      else if (clazz.getSuperclass() != null)
      {
         return findMethodByHash(clazz.getSuperclass(), hash);
      }
      return null;
   }

   public static Constructor findConstructorByHash(Class clazz, long hash) throws Exception
   {
      Constructor[] cons = clazz.getDeclaredConstructors();
      for (int i = 0; i < cons.length; i++)
      {
         if (constructorHash(cons[i]) == hash) return cons[i];
      }
      if (clazz.getSuperclass() != null)
      {
         return findConstructorByHash(clazz.getSuperclass(), hash);
      }
      return null;
   }

   public static long methodHash(Method method)
      throws Exception
   {
      Class[] parameterTypes = method.getParameterTypes();
      String methodDesc = method.getName()+"(";
      for(int j = 0; j < parameterTypes.length; j++)
      {
         methodDesc += getTypeString(parameterTypes[j]);
      }
      methodDesc += ")"+getTypeString(method.getReturnType());
      return createHash(methodDesc);
   }
   
   public static long createHash(String methodDesc)
   	throws Exception
   {
      long hash = 0;
      ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream(512);
      MessageDigest messagedigest = MessageDigest.getInstance("SHA");
      DataOutputStream dataoutputstream = new DataOutputStream(new DigestOutputStream(bytearrayoutputstream, messagedigest));
      dataoutputstream.writeUTF(methodDesc);
      dataoutputstream.flush();
      byte abyte0[] = messagedigest.digest();
      for(int j = 0; j < Math.min(8, abyte0.length); j++)
         hash += (long)(abyte0[j] & 0xff) << j * 8;
      return hash;
      
   }

   public static long constructorHash(Constructor method)
      throws Exception
   {
      Class[] parameterTypes = method.getParameterTypes();
      String methodDesc = method.getName()+"(";
      for(int j = 0; j < parameterTypes.length; j++)
      {
         methodDesc += getTypeString(parameterTypes[j]);
      }
      methodDesc += ")";

      long hash = 0;
      ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream(512);
      MessageDigest messagedigest = MessageDigest.getInstance("SHA");
      DataOutputStream dataoutputstream = new DataOutputStream(new DigestOutputStream(bytearrayoutputstream, messagedigest));
      dataoutputstream.writeUTF(methodDesc);
      dataoutputstream.flush();
      byte abyte0[] = messagedigest.digest();
      for(int j = 0; j < Math.min(8, abyte0.length); j++)
         hash += (long)(abyte0[j] & 0xff) << j * 8;
      return hash;
   }

   /**
   * Calculate method hashes. This algo is taken from RMI.
   *
   * @param   intf  
   * @return     
   */
   public static Map getInterfaceHashes(Class intf)
   {
      // Create method hashes
      Method[] methods = intf.getDeclaredMethods();
      HashMap map = new HashMap();
      for (int i = 0; i < methods.length; i++)
      {
         Method method = methods[i];
         try
         {
            long hash = methodHash(method);
            map.put(method.toString(), new Long(hash));
         }
         catch (Exception e)
         {
         }
      }
      
      return map;
   }
   
   static String getTypeString(Class cl)
   {
      if (cl == Byte.TYPE)
      {
         return "B";
      } else if (cl == Character.TYPE)
      {
         return "C";
      } else if (cl == Double.TYPE)
      {
         return "D";
      } else if (cl == Float.TYPE)
      {
         return "F";
      } else if (cl == Integer.TYPE)
      {
         return "I";
      } else if (cl == Long.TYPE)
      {
         return "J";
      } else if (cl == Short.TYPE)
      {
         return "S";
      } else if (cl == Boolean.TYPE)
      {
         return "Z";
      } else if (cl == Void.TYPE)
      {
         return "V";
      } else if (cl.isArray())
      {
         return "["+getTypeString(cl.getComponentType());
      } else
      {
         return "L"+cl.getName().replace('.','/')+";";
      }
   }
   
   /*
   * The use of hashCode is not enough to differenciate methods
   * we override the hashCode
   *
   * The hashes are cached in a static for efficiency
   * RO: WeakHashMap needed to support undeploy
   */
   public static long calculateHash(Method method)
   {
      Map methodHashes = (Map)hashMap.get(method.getDeclaringClass());
      
      if (methodHashes == null)
      {
         methodHashes = getInterfaceHashes(method.getDeclaringClass());
         
         // Copy and add
         WeakHashMap newHashMap = new WeakHashMap();
         newHashMap.putAll(hashMap);
         newHashMap.put(method.getDeclaringClass(), methodHashes);
         hashMap = newHashMap;
      }
      
      return ((Long)methodHashes.get(method.toString())).longValue();
   }

}
