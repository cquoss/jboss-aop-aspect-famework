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
package org.jboss.aop.proxy;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import org.jboss.aop.AspectManager;
import org.jboss.aop.ClassAdvisor;
import org.jboss.aop.ClassInstanceAdvisor;
import org.jboss.aop.InstanceAdvisor;
import org.jboss.aop.util.JavassistMethodHashing;
import org.jboss.aop.util.reference.MethodPersistentReference;
import org.jboss.aop.util.reference.PersistentReference;
import org.jboss.util.collection.WeakValueHashMap;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.SerialVersionUID;


/**
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 44253 $
 */
public class ClassProxyFactory
{
   private static Object maplock = new Object();
   private static WeakValueHashMap classnameMap = new WeakValueHashMap();
   private static WeakHashMap proxyCache = new WeakHashMap();
   private static WeakHashMap methodMapCache = new WeakHashMap();

   public static ClassProxy newInstance(Class clazz) throws Exception
   {
      return newInstance(clazz, null);
   }

   public static ClassProxy newInstance(Class clazz, ProxyMixin[] mixins) throws Exception
   {
      return newInstance(clazz, mixins, new ClassInstanceAdvisor());
   }

   private static Class getProxyClass(Class clazz, ProxyMixin[] mixins)
   throws Exception
   {
      // Don't make a proxy of a proxy !
      if (ClassProxy.class.isAssignableFrom(clazz)) clazz = clazz.getSuperclass();

      Class proxyClass = null;
      synchronized (maplock)
      {
         WeakReference ref = (WeakReference) proxyCache.get(clazz);
         if (ref != null)
         {
            proxyClass = (Class)ref.get();
         }
         if (proxyClass == null)
         {
            proxyClass = generateProxy(clazz, mixins);
            classnameMap.put(clazz.getName(), proxyClass);
            proxyCache.put(clazz, new WeakReference(proxyClass));
            HashMap map = methodMap(clazz);
            methodMapCache.put(proxyClass, map);
         }
      }
      return proxyClass;
   }

   public static ClassProxy newInstance(Class clazz, ProxyMixin[] mixins, InstanceAdvisor advisor) throws Exception
   {
      Class proxyClass = getProxyClass(clazz, mixins);
      ClassProxy proxy = (ClassProxy) proxyClass.newInstance();
      proxy.setMixins(mixins);
      proxy._setInstanceAdvisor(advisor);
      return proxy;

   }

   public static HashMap getMethodMap(String classname)
   {
      synchronized (maplock)
      {
         Class clazz = (Class) classnameMap.get(classname);
         if (clazz == null) return null;
         return (HashMap) methodMapCache.get(clazz);
      }
   }

   public static HashMap getMethodMap(Class clazz)
   {
      HashMap map = getMethodMap(clazz.getName());
      if (map != null) return map;
      try
      {
         return methodMap(clazz);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);  //To change body of catch statement use Options | File Templates.
      }
   }

   private static int counter = 0;

   private static CtClass createProxyCtClass(ProxyMixin[] mixins, Class clazz)
   throws Exception
   {
      ClassPool pool = AspectManager.instance().findClassPool(clazz.getClassLoader());
      if (pool == null) throw new NullPointerException("Could not find ClassPool");

      String classname = "AOPClassProxy$" + counter++;

      CtClass template = pool.get("org.jboss.aop.proxy.ClassProxyTemplate");
      CtClass superclass = pool.get(clazz.getName());

      CtField mixinField = template.getField("mixins");
      CtField instanceAdvisor = template.getField("instanceAdvisor");


      CtClass proxy = pool.makeClass(classname, superclass);

      mixinField = new CtField(mixinField.getType(), "mixins", proxy);
      mixinField.setModifiers(Modifier.PRIVATE);
      proxy.addField(mixinField);
      instanceAdvisor = new CtField(instanceAdvisor.getType(), "instanceAdvisor", proxy);
      instanceAdvisor.setModifiers(Modifier.PRIVATE);
      proxy.addField(instanceAdvisor);

      CtMethod writeEx = CtNewMethod.make("   public void writeExternal(java.io.ObjectOutput out)\n" +
                                          "   throws java.io.IOException\n" +
                                          "   {\n" +
                                          "   }", proxy);
      CtMethod readEx = CtNewMethod.make("   public void readExternal(java.io.ObjectInput in)\n" +
                                         "   throws java.io.IOException, ClassNotFoundException\n" +
                                         "   {\n" +
                                         "   }", proxy);
      CtMethod getInstanceAdvisor = CtNewMethod.make("   public org.jboss.aop.InstanceAdvisor _getInstanceAdvisor()\n" +
                                                     "   {\n" +
                                                     "      return instanceAdvisor;\n" +
                                                     "   }", proxy);
      CtMethod setInstanceAdvisor = CtNewMethod.make("   public void _setInstanceAdvisor(org.jboss.aop.InstanceAdvisor newAdvisor)\n" +
                                                     "   {\n" +
                                                     "      instanceAdvisor = (org.jboss.aop.ClassInstanceAdvisor) newAdvisor;\n" +
                                                     "   }", proxy);
      CtMethod dynamicInvoke = CtNewMethod.make("   public org.jboss.aop.joinpoint.InvocationResponse _dynamicInvoke(org.jboss.aop.joinpoint.Invocation invocation)\n" +
                                                "   throws Throwable\n" +
                                                "   {\n" +
                                                "      ((org.jboss.aop.joinpoint.InvocationBase) invocation).setInstanceResolver(instanceAdvisor.getMetaData());\n" +
                                                "      org.jboss.aop.advice.Interceptor[] aspects = instanceAdvisor.getInterceptors();\n" +
                                                "      return new org.jboss.aop.joinpoint.InvocationResponse(invocation.invokeNext(aspects));\n" +
                                                "   }", proxy);

      CtMethod setMixins = CtNewMethod.make("   public void setMixins(org.jboss.aop.proxy.ProxyMixin[] mixins)\n" +
                                            "   {\n" +
                                            "      this.mixins = mixins;\n" +
                                            "   }", proxy);

      CtMethod writeReplace = CtNewMethod.make("   public Object writeReplace() throws java.io.ObjectStreamException\n" +
                                               "   {\n" +
                                               "      return new org.jboss.aop.proxy.MarshalledClassProxy(this.getClass().getSuperclass(), mixins, instanceAdvisor);\n" +
                                               "   }", proxy);


      proxy.addMethod(writeEx);
      proxy.addMethod(readEx);
      proxy.addMethod(getInstanceAdvisor);
      proxy.addMethod(setInstanceAdvisor);
      proxy.addMethod(dynamicInvoke);
      proxy.addMethod(setMixins);
      proxy.addMethod(writeReplace);

      /*
      CtMethod writeEx = template.getDeclaredMethod("writeExternal");
      CtMethod readEx = template.getDeclaredMethod("readExternal");
      CtMethod getInstanceAdvisor = template.getDeclaredMethod("_getInstanceAdvisor");
      CtMethod setInstanceAdvisor = template.getDeclaredMethod("_setInstanceAdvisor");
      CtMethod dynamicInvoke = template.getDeclaredMethod("_dynamicInvoke");
      CtMethod setMixins = template.getDeclaredMethod("setMixins");
      CtMethod writeReplace = template.getDeclaredMethod("writeReplace");




      proxy.addMethod(CtNewMethod.copy(writeEx, proxy, null));
      proxy.addMethod(CtNewMethod.copy(readEx, proxy, null));
      proxy.addMethod(CtNewMethod.copy(getInstanceAdvisor, proxy, null));
      proxy.addMethod(CtNewMethod.copy(setInstanceAdvisor, proxy, null));
      proxy.addMethod(CtNewMethod.copy(dynamicInvoke, proxy, null));
      proxy.addMethod(CtNewMethod.copy(setMixins, proxy, null));
      proxy.addMethod(CtNewMethod.copy(writeReplace, proxy, null));
      */


      proxy.addInterface(pool.get("org.jboss.aop.proxy.ClassProxy"));
      proxy.addInterface(pool.get("java.io.Externalizable"));
      proxy.addInterface(pool.get("org.jboss.aop.instrument.Untransformable"));
      proxy.addInterface(pool.get("org.jboss.aop.proxy.MethodMapped"));

      CtClass map = pool.get("java.util.Map");
      CtField methodMap = new CtField(map, "methodMap", proxy);
      methodMap.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
      proxy.addField(methodMap);
      CtMethod getMethodMap = CtNewMethod.getter("getMethodMap", methodMap);
      getMethodMap.setModifiers(Modifier.PUBLIC);
      proxy.addMethod(getMethodMap);

      HashSet addedInterfaces = new HashSet();
      HashSet addedMethods = new HashSet();
      if (mixins != null)
      {
         for (int i = 0; i < mixins.length; i++)
         {
            HashSet mixinMethods = new HashSet();
            Class[] mixinf = mixins[i].getInterfaces();
            ClassPool mixPool = AspectManager.instance().findClassPool(mixins[i].getMixin().getClass().getClassLoader());
            CtClass mixClass = mixPool.get(mixins[i].getMixin().getClass().getName());
            for (int j = 0; j < mixinf.length; j++)
            {
               if (addedInterfaces.contains(mixinf[j].getName())) throw new Exception("2 mixins are implementing the same interfaces");
               ClassPool mixIntfPool = AspectManager.instance().findClassPool(mixinf[j].getClassLoader());
               CtClass intfClass = mixIntfPool.get(mixinf[j].getName());
               CtMethod[] methods = intfClass.getMethods();
               for (int m = 0; m < methods.length; m++)
               {
                  if (methods[m].getDeclaringClass().getName().equals("java.lang.Object")) continue;
                  Long hash = new Long(JavassistMethodHashing.methodHash(methods[m]));
                  if (mixinMethods.contains(hash)) continue;
                  if (addedMethods.contains(hash)) throw new Exception("More than one mixin has same method");
                  mixinMethods.add(hash);
                  addedMethods.add(hash);
                  String returnStr = (methods[m].getReturnType().equals(CtClass.voidType)) ? "" : "return ";
                  String code = "{" +
                  "   " + mixClass.getName() + " mixin = (" + mixClass.getName() + ")mixins[" + i + "].getMixin();" +
                  "   " + returnStr + " mixin." + methods[m].getName() + "($$);" +
                  "}";
                  CtMethod newMethod = CtNewMethod.make(methods[m].getReturnType(), methods[m].getName(), methods[m].getParameterTypes(), methods[m].getExceptionTypes(), code, proxy);
                  newMethod.setModifiers(Modifier.PUBLIC);
                  proxy.addMethod(newMethod);
               }

               proxy.addInterface(intfClass);
               addedInterfaces.add(intfClass.getName());
            }
         }
      }

      HashMap allMethods = JavassistMethodHashing.getMethodMap(superclass);

      Iterator it = allMethods.entrySet().iterator();
      while (it.hasNext())
      {
         Map.Entry entry = (Map.Entry) it.next();
         CtMethod m = (CtMethod) entry.getValue();
         if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;

         Long hash = (Long) entry.getKey();
         if (addedMethods.contains(hash)) continue;
         addedMethods.add(hash);
         String aopReturnStr = (m.getReturnType().equals(CtClass.voidType)) ? "" : "return ($r)";
         String args = "null";
         if (m.getParameterTypes().length > 0) args = "$args";
         String code = "{   " +
         "    org.jboss.aop.advice.Interceptor[] aspects = instanceAdvisor.getInterceptors(); " +
         "    org.jboss.aop.MethodInfo mi = new org.jboss.aop.MethodInfo(); " +
         "    mi.setHash(" + hash.longValue() + "L);" +
         "    org.jboss.aop.proxy.ProxyMethodInvocation invocation = new org.jboss.aop.proxy.ProxyMethodInvocation(this, mi, aspects); " +
         "    invocation.setInstanceResolver(instanceAdvisor.getMetaData()); " +
         "    invocation.setArguments(" + args + "); " +
         "    " + aopReturnStr + " invocation.invokeNext(); " +
         "}";
         CtMethod newMethod = CtNewMethod.make(m.getReturnType(), m.getName(), m.getParameterTypes(), m.getExceptionTypes(), code, proxy);
         newMethod.setModifiers(Modifier.PUBLIC);
         proxy.addMethod(newMethod);
      }
      SerialVersionUID.setSerialVersionUID(proxy);
      return proxy;
   }

   private static Class generateProxy(Class clazz, ProxyMixin[] mixins) throws Exception
   {
      CtClass proxy = createProxyCtClass(mixins, clazz);
      Class proxyClass = proxy.toClass();
      Map methodmap = ClassProxyFactory.getMethodMap(proxyClass);
      Field field = proxyClass.getDeclaredField("methodMap");
      field.setAccessible(true);
      field.set(null, methodmap);
      return proxyClass;
   }

   private static void populateMethodTables(HashMap advised, Class superclass)
   throws Exception
   {
      if (superclass == null) return;
      if (superclass.getName().equals("java.lang.Object")) return;

      populateMethodTables(advised, superclass.getSuperclass());

      Method[] declaredMethods = superclass.getDeclaredMethods();
      for (int i = 0; i < declaredMethods.length; i++)
      {
         if (ClassAdvisor.isAdvisable(declaredMethods[i]))
         {
            long hash = org.jboss.aop.util.MethodHashing.methodHash(declaredMethods[i]);
            advised.put(new Long(hash), new MethodPersistentReference(declaredMethods[i], PersistentReference.REFERENCE_SOFT));
         }
      }
   }

   public static HashMap methodMap(Class clazz)
   throws Exception
   {
      HashMap methods = new HashMap();
      populateMethodTables(methods, clazz);
      return methods;
   }

}
