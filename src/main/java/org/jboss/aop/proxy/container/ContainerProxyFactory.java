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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.SerialVersionUID;

import org.jboss.aop.Advised;
import org.jboss.aop.Advisor;
import org.jboss.aop.AspectManager;
import org.jboss.aop.ClassContainer;
import org.jboss.aop.InstanceAdvised;
import org.jboss.aop.MethodInfo;
import org.jboss.aop.introduction.InterfaceIntroduction;
import org.jboss.aop.util.JavassistMethodHashing;


/**
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 44253 $
 */
public class ContainerProxyFactory
{
   private static final String ADVISED = Advised.class.getName();
   private static final String INSTANCE_ADVISED = InstanceAdvised.class.getName();
   private static final CtClass[] EMPTY_CTCLASS_ARRAY = new CtClass[0];
   public static final String PROXY_NAME_PREFIX = "AOPContainerProxy$";
   
   private static Object maplock = new Object();
   private static WeakHashMap proxyCache = new WeakHashMap();
   private static volatile int counter = 0;


   /** True if java.lang.Object should be used as the super class for this proxy */
   private boolean objectAsSuper;

   /** The Advisor for this proxy */
   private Advisor advisor;

   /** The class we are generating this proxy for */
   private Class clazz;

   /** The generated proxy */
   private CtClass proxy;
   
   /** The class pool for the proxy (i.e for the class we are proxying */
   private ClassPool pool;
   
   /** The interface introductions and mixins that should be used for this proxy*/
   private ArrayList mixins;
   
   /** True if we are proxying a class already woven by jboss aop, false otherwise */
   private boolean isAdvised;
   
   public static Class getProxyClass(Class clazz, AspectManager manager) throws Exception
   {
      ContainerProxyCacheKey key = new ContainerProxyCacheKey(clazz);
      ClassContainer container = getTempClassContainer(clazz, manager);
      return getProxyClass(false, key, container);
   }
   
   public static Class getProxyClass(boolean objectAsSuper, ContainerProxyCacheKey key, Advisor advisor)
           throws Exception
   {
      Class clazz = key.getClazz();
      // Don't make a proxy of a proxy !
      if (Delegate.class.isAssignableFrom(clazz)) clazz = clazz.getSuperclass();

      Class proxyClass = null;
      synchronized (maplock)
      {
         Map map = (Map)proxyCache.get(clazz);
         if (map == null)
         {
            map = new HashMap();
            proxyCache.put(clazz, map);
         }
         else
         {
            proxyClass = (Class) map.get(key);
         }
         
         if (proxyClass == null)
         {
            proxyClass = generateProxy(objectAsSuper, clazz, advisor);
            map.put(key, proxyClass);
         }
      }
      return proxyClass;
   }

   private static Class generateProxy(boolean objectAsSuper, Class clazz, Advisor advisor) throws Exception
   {
      ArrayList introductions = advisor.getInterfaceIntroductions();
      CtClass proxy = createProxyCtClass(objectAsSuper, introductions, clazz, advisor);
      
      Class proxyClass = proxy.toClass();
      return proxyClass;
   }

   private static ClassProxyContainer getTempClassContainer(Class clazz, AspectManager manager)
   {
      ClassProxyContainer container = new ClassProxyContainer("temp", manager);
      container.setClass(clazz);

      Iterator it = container.getManager().getInterfaceIntroductions().values().iterator();
      while (it.hasNext())
      {
         InterfaceIntroduction intro = (InterfaceIntroduction) it.next();
         if (intro.matches(container, container.getClazz()))
         {
            container.addInterfaceIntroduction(intro);
         }
      }

      return container;
   }
   
   public static CtClass createProxyCtClass(boolean objectAsSuper, ArrayList mixins, Class clazz, Advisor advisor)
           throws Exception
   {
      ContainerProxyFactory factory = new ContainerProxyFactory(objectAsSuper, mixins, clazz, advisor);
      return factory.createProxyCtClass();
   }

   
   private ContainerProxyFactory(boolean objectAsSuper, ArrayList mixins, Class clazz, Advisor advisor)
   {
      this.objectAsSuper = objectAsSuper;
      this.mixins = mixins;
      this.clazz = clazz;
      this.advisor = advisor;
      isAdvised = Advised.class.isAssignableFrom(clazz);
   }
  
   
   private CtClass createProxyCtClass() throws Exception
   {
      this.pool = AspectManager.instance().findClassPool(clazz.getClassLoader());
      if (pool == null) throw new NullPointerException("Could not find ClassPool");

      createBasics();
      addMethodsAndMixins();
      overrideSpecialMethods(clazz, proxy);
      
      SerialVersionUID.setSerialVersionUID(proxy);
      
      return proxy;
   }
   

   private CtClass createBasics() throws Exception
   {
      Class proxySuper  = (objectAsSuper) ? Object.class : this.clazz; 
      String classname = getClassName();

      CtClass template = pool.get("org.jboss.aop.proxy.container.ProxyTemplate");
      CtClass superclass = pool.get(proxySuper.getName());

      this.proxy = pool.makeClass(classname, superclass);
      proxy.addInterface(pool.get("org.jboss.aop.instrument.Untransformable"));
      
      //Add all the interfaces of the class
      Class[] interfaces = proxySuper.getInterfaces();
      for (int i = 0 ; i < interfaces.length ; i++)
      {
         CtClass interfaze = pool.get(interfaces[i].getName());
         proxy.addInterface(interfaze);
      }
      
      ensureDefaultConstructor(superclass, proxy);
      addFieldFromTemplate(template, "mixins");

      //Add methods/fields needed for Delegate interface
      proxy.addInterface(pool.get("org.jboss.aop.proxy.container.Delegate"));
      
      addFieldFromTemplate(template, "delegate", superclass);
      addMethodFromTemplate(template, "getDelegate", "{ return delegate; }");
      addMethodFromTemplate(template, "setDelegate", "{ this.delegate = (" + proxySuper.getName() + ")$1; }");

      //Add methods/fields needed for AspectManaged interface 
      proxy.addInterface(pool.get("org.jboss.aop.proxy.container.AspectManaged"));

      addFieldFromTemplate(template, "currentAdvisor");
      addFieldFromTemplate(template, "classAdvisor");
      addMethodFromTemplate(template, "getAdvisor", "{return classAdvisor;}");
      addMethodFromTemplate(template, "setAdvisor", "{this.classAdvisor = $1;currentAdvisor = classAdvisor;}");
      
      addFieldFromTemplate(template, "metadata");
      addMethodFromTemplate(template, "setMetadata", "{this.metadata = $1;}");
      
      addFieldFromTemplate(template, "instanceAdvisor");     
      addMethodFromTemplate(template, "setInstanceAdvisor", instanceAdvisorSetterBody());
      addMethodFromTemplate(template, "getInstanceAdvisor", instanceAdvisorGetterBody());
      
      return proxy;
   }
   
   private String instanceAdvisorSetterBody()
   {
      return 
         "{" +
         "   synchronized (this)" +
         "   {" +
         "   if (this.instanceAdvisor != null)" +
         "   {" +
         "      throw new RuntimeException(\"InstanceAdvisor already set\");" +
         "   }" +
         "   if (!($1 instanceof org.jboss.aop.proxy.container.InstanceProxyContainer))" +
         "   {" +
         "      throw new RuntimeException(\"Wrong type for instance advisor: \" + $1);" +
         "   }" +
         "   this.instanceAdvisor = $1;" +
         "   currentAdvisor = (org.jboss.aop.proxy.container.InstanceProxyContainer)$1;" +
         "   }" +
         "}";
   }

   private String instanceAdvisorGetterBody()
   {
      return 
         "{" +
         "   synchronized(this)" +
         "   {" +
         "      if (instanceAdvisor == null)" +
         "      {" +
         "         org.jboss.aop.proxy.container.InstanceProxyContainer ipc = ((org.jboss.aop.proxy.container.ClassProxyContainer)currentAdvisor).createInstanceProxyContainer();" +
         "         setInstanceAdvisor(ipc);" +
         "      }" +
         "   }" +
         "   return instanceAdvisor;" +
         "}";
   }

   private CtField addFieldFromTemplate(CtClass template, String name) throws Exception
   {
      return addFieldFromTemplate(template, name, null);
   }

   private CtField addFieldFromTemplate(CtClass template, String name, CtClass type) throws Exception
   {
      CtField templateField = template.getField(name);
      CtClass fieldType = (type == null) ? templateField.getType() : type;
      CtField field = new CtField(fieldType, name, proxy);
      field.setModifiers(Modifier.PRIVATE);
      proxy.addField(field);
      return field;
   }

   private CtMethod addMethodFromTemplate(CtClass template, String name) throws Exception
   {
      return addMethodFromTemplate(template, name, null);
   }
   
   private CtMethod addMethodFromTemplate(CtClass template, String name, String body) throws Exception
   {
      CtMethod templateMethod = template.getDeclaredMethod(name);
      CtMethod method = CtNewMethod.make(templateMethod.getReturnType(), name, templateMethod.getParameterTypes(), templateMethod.getExceptionTypes(), body, proxy);
      method.setModifiers(templateMethod.getModifiers());
      proxy.addMethod(method);
      return method;
   }
   
   private void ensureDefaultConstructor(CtClass superclass, CtClass proxy) throws Exception
   {
      
      CtConstructor[] ctors = superclass.getConstructors();
      int minParameters = Integer.MAX_VALUE;
      CtConstructor bestCtor = null;
      for (int i = 0 ; i < ctors.length ; i++)
      {
         CtClass[] params = ctors[i].getParameterTypes(); 
         if (params.length < minParameters)
         {
            bestCtor = ctors[i];
            minParameters = params.length;
         }
      }
      
      if (minParameters > 0)
      {
         //We don't have a default constructor, we need to create one passing in null to the super ctor

         //TODO We will get problems if the super class does some validation of the parameters, resulting in exceptions thrown
         CtConstructor ctor = CtNewConstructor.make(EMPTY_CTCLASS_ARRAY, EMPTY_CTCLASS_ARRAY, proxy);
         CtClass params[] = bestCtor.getParameterTypes();
         
         StringBuffer superCall = new StringBuffer("super(");
         
         for (int i = 0 ; i < params.length ; i++)
         {
            if (i > 0)
            {
               superCall.append(", ");
            }
            
            if (!params[i].isPrimitive())
            {
               superCall.append("null");
            }
            else
            {
               if (params[i].equals(CtClass.booleanType)) superCall.append("false");
               else if (params[i].equals(CtClass.charType)) superCall.append("'0'");
               else if (params[i].equals(CtClass.byteType)) superCall.append("0");
               else if (params[i].equals(CtClass.shortType)) superCall.append("0");
               else if (params[i].equals(CtClass.intType)) superCall.append("0");
               else if (params[i].equals(CtClass.longType)) superCall.append("0L");
               else if (params[i].equals(CtClass.floatType)) superCall.append("0f");
               else if (params[i].equals(CtClass.doubleType)) superCall.append("0d");               
            }
         }
         
         superCall.append(");");
         
         ctor.setBody("{" + superCall.toString() + "}");
         proxy.addConstructor(ctor);
      }
   }
   
   private void addMethodsAndMixins()throws Exception
   {
      HashSet addedMethods = new HashSet();

      createMixinsAndIntroductions(addedMethods);
      createProxyMethods(addedMethods);
   }

   private void createMixinsAndIntroductions(HashSet addedMethods) throws Exception
   {
      HashSet addedInterfaces = new HashSet();
      Set implementedInterfaces = interfacesAsSet();
      
      if (mixins != null)
      {
         HashMap intfs = new HashMap();
         HashMap mixinIntfs = new HashMap();
         ArrayList mixes = new ArrayList();
         for (int i = 0; i < mixins.size(); i++)
         {
            InterfaceIntroduction introduction = (InterfaceIntroduction) mixins.get(i);
            getIntroductionInterfaces(intfs, mixinIntfs, introduction, mixes, i);
         }
         if (mixes.size() > 0)
         {
            CtConstructor con = CtNewConstructor.defaultConstructor(proxy);
            con.insertAfter("mixins = new Object[" + mixes.size() + "];");
            for (int i = 0; i < mixes.size(); i++)
            {
               InterfaceIntroduction.Mixin mixin = (InterfaceIntroduction.Mixin) mixes.get(i);
               String initializer = (mixin.getConstruction() == null) ? ("new " + mixin.getClassName() + "()") : mixin.getConstruction();
               con.insertAfter("mixins[" + i + "] = " + initializer + ";");
            }
            proxy.addConstructor(con);
         }
         
         createMixins(addedMethods, mixinIntfs, addedInterfaces, implementedInterfaces);
         createIntroductions(addedMethods, intfs, addedInterfaces, implementedInterfaces);
      }
   }
   
   private void getIntroductionInterfaces(HashMap intfs, HashMap mixins, InterfaceIntroduction intro, ArrayList mixes, int idx)
   {
      if (intro.getInterfaces() != null)
      {
         for (int i = 0; i < intro.getInterfaces().length; i++)
         {
            if (intfs.containsKey(intro.getInterfaces()[i]) || mixins.containsKey(intro.getInterfaces()[i])) throw new RuntimeException("cannot have an IntroductionInterface that introduces same interfaces");
            intfs.put(intro.getInterfaces()[i], new Integer(idx));
         }
      }
      Iterator it = intro.getMixins().iterator();
      while (it.hasNext())
      {
         InterfaceIntroduction.Mixin mixin = (InterfaceIntroduction.Mixin) it.next();
         mixes.add(mixin);
         for (int i = 0; i < mixin.getInterfaces().length; i++)
         {
            if (intfs.containsKey(mixin.getInterfaces()[i]) || mixins.containsKey(mixin.getInterfaces()[i])) throw new RuntimeException("cannot have an IntroductionInterface that introduces same interfaces");
            mixins.put(mixin.getInterfaces()[i], new Integer(idx));
         }
      }
   }

   private void createMixins(HashSet addedMethods, HashMap mixinIntfs, HashSet addedInterfaces, Set implementedInterfaces) throws Exception
   {
      Iterator it = mixinIntfs.keySet().iterator();
      while (it.hasNext())
      {
         String intf = (String) it.next();
         Integer idx = (Integer) mixinIntfs.get(intf);
         if (addedInterfaces.contains(intf)) throw new Exception("2 mixins are implementing the same interfaces " + intf);
         if (implementedInterfaces.contains(intf))  throw new Exception("Attempting to mixin interface already used by class " + intf);

         CtClass intfClass = pool.get(intf);
         CtMethod[] methods = intfClass.getMethods();
         HashSet mixinMethods = new HashSet();
         for (int m = 0; m < methods.length; m++)
         {
            if (methods[m].getDeclaringClass().getName().equals("java.lang.Object")) continue;
            Long hash = new Long(JavassistMethodHashing.methodHash(methods[m]));
            if (mixinMethods.contains(hash)) continue;
            if (addedMethods.contains(hash)) throw new Exception("More than one mixin has same method");
            mixinMethods.add(hash);
            addedMethods.add(hash);
            String aopReturnStr = (methods[m].getReturnType().equals(CtClass.voidType)) ? "" : "return ($r)";
            String returnStr = (methods[m].getReturnType().equals(CtClass.voidType)) ? "" : "return ";
            String args = "null";
            if (methods[m].getParameterTypes().length > 0) args = "$args";
            String code = "{   " +
                          "   try{" +
                          "      " + intf + " mixin = (" + intf + ")mixins[" + idx + "];" +
                          "       org.jboss.aop.MethodInfo mi = currentAdvisor.getMethodInfo(" + hash.longValue() + "L); " +
                          "       org.jboss.aop.advice.Interceptor[] interceptors = mi.getInterceptors();" +
                          "       if (mi != null && interceptors != (Object[])null && interceptors.length > 0) { " +
                          "          org.jboss.aop.proxy.container.ContainerProxyMethodInvocation invocation = new org.jboss.aop.proxy.container.ContainerProxyMethodInvocation(mi, interceptors, this); " +
                          "          invocation.setArguments(" + args + "); " +
                          "          invocation.setTargetObject(mixin); " +
                          "          invocation.setMetaData(metadata);" +
                          "          " + aopReturnStr + " invocation.invokeNext(); " +
                          "       } else { " +
                          "       " + returnStr + " mixin." + methods[m].getName() + "($$);" +
                          "       } " +
                          "    }finally{" +
                          "    }" +
                          "}";
            CtMethod newMethod = CtNewMethod.make(methods[m].getReturnType(), methods[m].getName(), methods[m].getParameterTypes(), methods[m].getExceptionTypes(), code, proxy);
            newMethod.setModifiers(Modifier.PUBLIC);
            proxy.addMethod(newMethod);
         }

         proxy.addInterface(intfClass);
         addedInterfaces.add(intfClass.getName());
      }
   }

   private void createProxyMethods(HashSet addedMethods) throws Exception
   {
      HashMap allMethods = JavassistMethodHashing.getMethodMap(proxy.getSuperclass());

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
         String returnStr = (m.getReturnType().equals(CtClass.voidType)) ? "" : "return ";
         String args = "null";
         
         String name = null;
         if (isAdvised)
         {
            MethodInfo info = advisor.getMethodInfo(hash.longValue());
            Method originalMethod = info.getUnadvisedMethod();
            name = originalMethod.getName();
         }
         else
         {
            name = m.getName();
         }
         
         if (m.getParameterTypes().length > 0) args = "$args";
         String code = "{   " +
                       "    try{" +
                       "       org.jboss.aop.MethodInfo mi = currentAdvisor.getMethodInfo(" + hash.longValue() + "L); " +
                       "       if (mi == null) " +
                       "          throw new NoSuchMethodError(\"" + m.getName() + m.getSignature() + "\");" +
                       "       org.jboss.aop.advice.Interceptor[] interceptors = mi.getInterceptors(); " +
                       "       if (interceptors != (Object[])null && interceptors.length > 0) { " +
                       "          org.jboss.aop.proxy.container.ContainerProxyMethodInvocation invocation = new org.jboss.aop.proxy.container.ContainerProxyMethodInvocation(mi, interceptors, this); " +
                       "          invocation.setArguments(" + args + "); " +
                       "          invocation.setTargetObject(delegate); " +
                       "          invocation.setMetaData(metadata);" +
                       "          " + aopReturnStr + " invocation.invokeNext(); " +
                       "       } else { " +
                       "          " + returnStr + " delegate." + name + "($$); " +
                       "       }" +
                       "    }finally{" +
                       "    }" +
                       "}";
         CtMethod newMethod = CtNewMethod.make(m.getReturnType(), m.getName(), m.getParameterTypes(), m.getExceptionTypes(), code, proxy);
         newMethod.setModifiers(Modifier.PUBLIC);
         proxy.addMethod(newMethod);
      }
   }
   
   private void createIntroductions(HashSet addedMethods, HashMap intfs, HashSet addedInterfaces, Set implementedInterfaces) throws Exception
   {
      Iterator it = intfs.keySet().iterator();
      while (it.hasNext())
      {
         String intf = (String) it.next();
         if (addedInterfaces.contains(intf)) throw new Exception("2 mixins are implementing the same interfaces");
         if (implementedInterfaces.contains(intf))  
         {
            continue;
         }

         CtClass intfClass = pool.get(intf);
         CtMethod[] methods = intfClass.getMethods();
         HashSet mixinMethods = new HashSet();
         for (int m = 0; m < methods.length; m++)
         {
            if (methods[m].getDeclaringClass().getName().equals("java.lang.Object")) continue;
            
            Long hash = new Long(JavassistMethodHashing.methodHash(methods[m]));
            if (mixinMethods.contains(hash)) continue;
            if (addedMethods.contains(hash)) continue;
            
            mixinMethods.add(hash);
            addedMethods.add(hash);
            String aopReturnStr = (methods[m].getReturnType().equals(CtClass.voidType)) ? "" : "return ($r)";
            String args = "null";
            if (methods[m].getParameterTypes().length > 0) args = "$args";
            String code = "{   " +
                          "    try{" +
                          "       org.jboss.aop.MethodInfo mi = currentAdvisor.getMethodInfo(" + hash.longValue() + "L); " +
                          "       if (mi == null) " +
                          "          throw new NoSuchMethodError(\"" + methods[m].getName() + methods[m].getSignature() + "\");" +
                          "       org.jboss.aop.advice.Interceptor[] interceptors = mi.getInterceptors();" +
                          "       org.jboss.aop.proxy.container.ContainerProxyMethodInvocation invocation = new org.jboss.aop.proxy.container.ContainerProxyMethodInvocation(mi, interceptors, this); " +
                          "       invocation.setArguments(" + args + "); " +
                          "       invocation.setTargetObject(delegate); " +
                          "       invocation.setMetaData(metadata);" +
                          "       " + aopReturnStr + " invocation.invokeNext(); " +
                          "    }finally{" +
                          "    }" +
                          "}";
            
            CtMethod newMethod = CtNewMethod.make(methods[m].getReturnType(), methods[m].getName(), methods[m].getParameterTypes(), methods[m].getExceptionTypes(), code, proxy);
            newMethod.setModifiers(Modifier.PUBLIC);
            proxy.addMethod(newMethod);
         }

         proxy.addInterface(intfClass);
         addedInterfaces.add(intfClass.getName());
      }
   }

   private Set interfacesAsSet() throws NotFoundException
   {
      HashSet set = new HashSet();
      CtClass[] interfaces = proxy.getInterfaces();
      
      for (int i = 0 ; i < interfaces.length ; i++)
      {
         set.add(interfaces[i].getName());
      }
      
      return set;
   }
   
   private String getClassName()
   {
      String packageName = clazz.getPackage().getName();
      if (packageName.indexOf("java.") != -1 && packageName.indexOf("sun.") != -1)
      {
         packageName += ".";
      }
      else
      {
         packageName = "";
      }
      
      return packageName + PROXY_NAME_PREFIX + counter++;
   }

   private void overrideSpecialMethods(Class clazz, CtClass proxy) throws Exception
   {
      addInstanceAdvisedMethods(clazz, proxy);
   }

   /**
    * If the class is Advised, the _getInstanceAdvisor() and _setInstanceAdvisor() methods will
    * not have been overridden. Make sure that these methods work with the instance proxy container.
    */
   private void addInstanceAdvisedMethods(Class clazz, CtClass proxy) throws Exception
   {
      CtClass advisedInterface = null;
      CtClass interfaces[] = proxy.getInterfaces();
      
      for (int i = 0 ; i < interfaces.length ; i++)
      {
         if (interfaces[i].getName().equals(ADVISED))
         {
            advisedInterface = interfaces[i];
            break;
         }
      }
      
      if (advisedInterface != null)
      {
         CtMethod[] methods = advisedInterface.getMethods();
         for (int i = 0 ; i < methods.length ; i++)
         {
            if (methods[i].getDeclaringClass().getName().equals(INSTANCE_ADVISED))
            {
               String name = methods[i].getName();
               String body = null;
               if (name.equals("_getInstanceAdvisor"))
               {
                  body = "{ return getInstanceAdvisor(); }";
               }
               else if (name.equals("_setInstanceAdvisor"))
               {
                  body = "{ setInstanceAdvisor($1); }";
               }
               
               if (body != null)
               {
                  CtMethod m = CtNewMethod.make(methods[i].getReturnType(), methods[i].getName(), methods[i].getParameterTypes(), methods[i].getExceptionTypes(), null, proxy);
                  m.setModifiers(Modifier.PUBLIC);
                  m.setBody(body);
                  proxy.addMethod(m);
               }
            }
         }
      }
   }
}   
