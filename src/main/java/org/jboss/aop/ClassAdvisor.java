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

import gnu.trove.TLongObjectHashMap;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jboss.aop.advice.AdviceBinding;
import org.jboss.aop.advice.AspectDefinition;
import org.jboss.aop.advice.Interceptor;
import org.jboss.aop.instrument.ConstructorExecutionTransformer;
import org.jboss.aop.instrument.FieldAccessTransformer;
import org.jboss.aop.instrument.MethodExecutionTransformer;
import org.jboss.aop.introduction.InterfaceIntroduction;
import org.jboss.aop.joinpoint.ConstructorCalledByConstructorInvocation;
import org.jboss.aop.joinpoint.ConstructorCalledByConstructorJoinpoint;
import org.jboss.aop.joinpoint.ConstructorCalledByMethodInvocation;
import org.jboss.aop.joinpoint.ConstructorCalledByMethodJoinpoint;
import org.jboss.aop.joinpoint.ConstructorInvocation;
import org.jboss.aop.joinpoint.FieldJoinpoint;
import org.jboss.aop.joinpoint.FieldReadInvocation;
import org.jboss.aop.joinpoint.FieldWriteInvocation;
import org.jboss.aop.joinpoint.Invocation;
import org.jboss.aop.joinpoint.MethodCalledByConstructorInvocation;
import org.jboss.aop.joinpoint.MethodCalledByConstructorJoinpoint;
import org.jboss.aop.joinpoint.MethodCalledByMethodInvocation;
import org.jboss.aop.joinpoint.MethodCalledByMethodJoinpoint;
import org.jboss.aop.joinpoint.MethodInvocation;
import org.jboss.aop.metadata.ClassMetaDataBinding;
import org.jboss.aop.metadata.ClassMetaDataLoader;
import org.jboss.aop.util.ConstructorComparator;
import org.jboss.aop.util.FieldComparator;
import org.jboss.aop.util.MethodHashing;
import org.jboss.util.NotImplementedException;

/**
 * Advises a class and provides access to the class's aspect chain.
 * Each advisable class has an associated <code>Advisor</code> instance.
 * References methods using <code>int</code> IDs rather than the actual
 * instances for
 * optimal performance. Provides ability to invoke methods on an advised
 * object without advice (see
 * <code>Advisor.invokeWithoutAdvisement()</code>).
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 60810 $
 */
public class ClassAdvisor extends Advisor
{
   /**
    * Suffix added to unadvised methods.
    */
   public static final String NOT_TRANSFORMABLE_SUFFIX = "$aop";

   protected TLongObjectHashMap unadvisedMethods = new TLongObjectHashMap();


   // caller pointcut support for methods calling methods only
   protected TLongObjectHashMap methodCalledByMethodBindings = new TLongObjectHashMap();
   protected HashMap backrefMethodCalledByMethodBindings = new HashMap();
   protected TLongObjectHashMap methodCalledByMethodInterceptors = new TLongObjectHashMap();

   // constructor caller pointcut support for methods calling methods only
   protected TLongObjectHashMap conCalledByMethodBindings = new TLongObjectHashMap();
   protected HashMap backrefConCalledByMethodBindings = new HashMap();
   protected TLongObjectHashMap conCalledByMethodInterceptors = new TLongObjectHashMap();

   // caller pointcut support for constructors calling methods
   protected HashMap[] methodCalledByConBindings;
   protected HashMap[] methodCalledByConInterceptors;
   protected HashMap backrefMethodCalledByConstructorBindings = new HashMap();

   // caller pointcut support for constructors calling methods
   protected HashMap[] conCalledByConBindings;
   protected HashMap[] conCalledByConInterceptors;
   protected HashMap backrefConCalledByConstructorBindings = new HashMap();

   // Used by instrumentor to access separate interceptor chains for read and write access
   /** @deprecated Use fieldReadInfos instead*/
   private Interceptor[][] fieldReadInterceptors;
   private FieldInfo[] fieldReadInfos;
   /** @deprecated Use fieldWriteInfos instead */
   private Interceptor[][] fieldWriteInterceptors;
   private FieldInfo[] fieldWriteInfos;
   
   
   protected Field[] advisedFields;
   //PER_JOINPOINT aspects for static fields or PER_CLASS_JOINPOINT aspects
   //all apply to fields, and we need this since the same aspect should be used for 
   //read and write
   private HashMap fieldAspectsWithNoInstance = new HashMap();

   protected boolean initialized = false;

   public ClassAdvisor(String classname, AspectManager manager)
   {
      super(classname, manager);
   }

   public ClassAdvisor(Class clazz, AspectManager manager)
   {
      this(clazz.getName(), manager);
      this.clazz = clazz;
   }


   /**
    * This method is to support PER_JOINPOINT scoping of Aspects for static fields
    * Fields are special in that a get and set do not create separate aspect instances.
    *
    * Also used to support PER_CLASS_JOINPOINT, since that behaves similarly to static fields
    *
    * @param joinpoint
    * @param def
    * @return
    */
   public Object getFieldAspect(FieldJoinpoint joinpoint, AspectDefinition def)
   {
      HashMap map = (HashMap)fieldAspectsWithNoInstance.get(def);
      if (map == null)
      {
         synchronized (fieldAspectsWithNoInstance)
         {
            map = (HashMap)fieldAspectsWithNoInstance.get(def);
            if (map == null)
            {
               map = new HashMap();
               fieldAspectsWithNoInstance.put(def, map);
            }
         }
      }
      
      Object aspect = map.get(joinpoint);
      if (aspect == null)
      {
         synchronized (map)
         {
            aspect = map.get(joinpoint);
            if (aspect == null)
            {
               aspect = def.getFactory().createPerJoinpoint(this, joinpoint);
               map.put(joinpoint, aspect);
            }
         }
      }
      
      return aspect;
   }

   public Field[] getAdvisedFields()
   {
      return advisedFields;
   }

   public TLongObjectHashMap getAdvisedMethods()
   {
      return advisedMethods;
   }

   protected TLongObjectHashMap getUnadvisedMethods()
   {
      return unadvisedMethods;
   }

   public Constructor[] getConstructors()
   {
      return constructors;
   }

   public TLongObjectHashMap getMethodCalledByMethodInterceptors()
   {
      return methodCalledByMethodInterceptors;
   }

   public HashMap[] getMethodCalledByConInterceptors()
   {
      return methodCalledByConInterceptors;
   }

   public HashMap[] getConCalledByConInterceptors()
   {
      return conCalledByConInterceptors;
   }

   public TLongObjectHashMap getConCalledByMethodInterceptors()
   {
      return conCalledByMethodInterceptors;
   }

   public TLongObjectHashMap getMethodCalledByMethodBindings()
   {
      return methodCalledByMethodBindings;
   }

   /** @deprecated use getFieldReadInfos instead */
   public Interceptor[][] getFieldReadInterceptors()
   {
      throw new NotImplementedException("Use getFieldReadInfos");
   }

   public FieldInfo[] getFieldReadInfos()
   {
      return fieldReadInfos;
   }

   /** @deprecated use getFieldWriteInfos instead */
   public Interceptor[][] getFieldWriteInterceptors()
   {
      throw new NotImplementedException("Use getFieldWriteInfos");
   }

   public FieldInfo[] getFieldWriteInfos()
   {
      return fieldWriteInfos;
   }
   
   public TLongObjectHashMap getMethodInterceptors()
   {
      return methodInterceptors;
   }

   
   /**
    * Constructs a new helper.
    */
   public synchronized void attachClass(final Class clazz)
   {
      if (initialized) return;
      try
      {
         //long start = System.currentTimeMillis();

         final ClassAdvisor THIS = this;
         final AspectManager theManager = manager;
         //register class loader: necessary when clazz was precompiled through aopc
         manager.registerClassLoader(clazz.getClassLoader());
         AccessController.doPrivileged(new PrivilegedExceptionAction()
         {
            public Object run() throws Exception
            {
               theManager.attachMetaData(THIS, clazz);
               interfaceIntroductions.clear();
               // metadata should always come before creation of interceptor chain
               // so that the interceptor factories have access to metadata.
               // and so that metadata joinpoints can be checked
               //
               // Also metadata needs to be applied before applyIntroductionPointcuts because
               // an annotation may be triggered by XML metadata as well as
               // after populateMixinMethods so that proper metadata is applied to added methods
               rebindClassMetaData();
               theManager.applyInterfaceIntroductions(THIS, clazz);
               THIS.clazz = clazz;
               createFieldTable();
               createMethodTables();
               createConstructorTables();
               populateMixinMethods();
               // metadata should always come before creation of interceptor chain
               // so that the interceptor factories have access to metadata.
               // and so that metadata joinpoints can be checked
               //
               // Also metadata needs to be applied before applyIntroductionPointcuts because
               // an annotation may be triggered by XML metadata as well as
               // after populateMixinMethods so that proper metadata is applied to added methods
               rebindClassMetaData();
               createInterceptorChains();
               initialized = true;
               return null;
            }
         });
         /*
         System.out.println("******************");
         System.out.println("attachClass: " + clazz.getName() + " took " + (System.currentTimeMillis() - start));
         System.out.println("******************");
         */
      }
      catch (PrivilegedActionException e)
      {
         throw new RuntimeException(e.getException());
      }
   }

   /**
    * Get method from clazz .If method not found,get the method
    * from the clazz's parent.
    */
   static private Method getMethod(Class clazz, Method method) throws NoSuchMethodException
   {

      if ((clazz == null) || (clazz.equals(Object.class))) throw new NoSuchMethodException(method.getName());
      try
      {
         String wrappedName = ClassAdvisor.notAdvisedMethodName(clazz.getName(), method.getName());
         return clazz.getMethod(wrappedName, method.getParameterTypes());
      }
      catch (NoSuchMethodException e)
      {
         return getMethod(clazz.getSuperclass(), method);
      }
   }

   /**
    * Get a constructor's index in the class. Returns -1 if not there
    */
   public int getConstructorIndex(Constructor constructor)
   {
      for (int i = 0; i < constructors.length; i++)
      {
         if (constructor.equals(constructors[i]))
         {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get a field's index in the class. Returns -1 if not there
    */
   public int getFieldIndex(Field field)
   {
      for (int i = 0; i < advisedFields.length; i++)
      {
         if (field.equals(advisedFields[i]))
         {
            return i;
         }
      }

      return -1;
   }


   /**
    * Put mixin methods from mixin class into unadvisedMethods map so that
    * they can be correctly invoked upon.
    */
   protected void populateMixinMethods() throws Exception
   {
      ArrayList pointcuts = getInterfaceIntroductions();
      Iterator it = pointcuts.iterator();
      while (it.hasNext())
      {
         InterfaceIntroduction pointcut = (InterfaceIntroduction) it.next();
         ArrayList mixins = pointcut.getMixins();
         for (int i = 0; i < mixins.size(); i++)
         {
            InterfaceIntroduction.Mixin mixin = (InterfaceIntroduction.Mixin) mixins.get(i);
            Thread.currentThread().getContextClassLoader().loadClass(mixin.getClassName());
            String[] interfaces = mixin.getInterfaces();
            for (int j = 0; j < interfaces.length; j++)
            {
               Class intf = Thread.currentThread().getContextClassLoader().loadClass(interfaces[j]);
               if (intf.isAssignableFrom(clazz))//This is a fix for JBAOP-365. Class may have been woven, with the extra mixin information only available at init time 
               {
                  Method[] methods = intf.getMethods();
                  for (int k = 0; k < methods.length; k++)
                  {
                     //Put wrapped method in the class itself into the unadvisedMethods map
                     //   String wrappedName = ClassAdvisor.notAdvisedMethodName(clazz.getName(), methods[k].getName());
                     //   Method method = clazz.getMethod(wrappedName, methods[k].getParameterTypes());
                     Method method = getMethod(clazz, methods[k]);
                     long hash = MethodHashing.methodHash(method);
                     unadvisedMethods.put(hash, method);
                  }
               }
            }
         }
      }
   }


   public synchronized void removeAdviceBinding(AdviceBinding binding)
   {
      removeCallerPointcut(binding); // if binding is a caller remove references to it
      super.removeAdviceBinding(binding);
   }

   public synchronized void removeAdviceBindings(ArrayList bindings)
   {
      for (int i = 0; i < bindings.size(); i++)
      {
         AdviceBinding binding = (AdviceBinding) bindings.get(i);
         removeCallerPointcut(binding);
      }
      adviceBindings.removeAll(bindings);
      rebuildInterceptors();
      doesHaveAspects = adviceBindings.size() > 0;
   }

   private void resolveFieldPointcut(ArrayList newFieldInfos, AdviceBinding binding, boolean write)
   {
      for (int i = 0; i < advisedFields.length; i++)
      {
         Field field = advisedFields[i];
         
         if ((!write && binding.getPointcut().matchesGet(this, field))
         || (write && binding.getPointcut().matchesSet(this, field)))
         {
            if (AspectManager.verbose) System.err.println("field matched binding " + binding.getName());
            adviceBindings.add(binding);
            binding.addAdvisor(this);
            FieldInfo info = (FieldInfo)newFieldInfos.get(i);
            pointcutResolved(info, binding, new FieldJoinpoint(field));         
         }
      }
   }

   protected TLongObjectHashMap initializeMethodChain()
   {
      TLongObjectHashMap newInterceptors = new TLongObjectHashMap();
      long[] keys = advisedMethods.keys();
      for (int i = 0; i < keys.length; i++)
      {
         // Keep compatible with AOP 1.3.x until AOP 2.0 is FINAL 
         MethodInfo info = new MethodInfo();
         Method amethod = (Method) advisedMethods.get(keys[i]);
         info.setAdvisedMethod(amethod);
         Method umethod = (Method) unadvisedMethods.get(keys[i]);

         if (umethod == null) umethod = amethod;
         info.setUnadvisedMethod(umethod);
         info.setHash(keys[i]);
         info.setAdvisor(this);
         newInterceptors.put(keys[i], info);
         try
         {
            Field infoField = clazz.getDeclaredField(MethodExecutionTransformer.getMethodInfoFieldName(amethod.getName(), keys[i]));
            infoField.setAccessible(true);
            infoField.set(null, new WeakReference(info));
         }
         catch (NoSuchFieldException e)
         {
            // ignore, method may not be advised.
         }
         catch (IllegalAccessException e)
         {
            throw new RuntimeException(e);  //To change body of catch statement use Options | File Templates.
         }
      }
      return newInterceptors;
   }

   protected ArrayList initializeFieldReadChain()
   {
      ArrayList chain = new ArrayList(advisedFields.length);
      for (int i = 0; i < advisedFields.length; i++)
      {
         FieldInfo info = new FieldInfo();
         info.setAdvisedField(advisedFields[i]);
         info.setAdvisor(this);
         info.setIndex(i);

         try
         {
            info.setWrapper(clazz.getDeclaredMethod(
                  FieldAccessTransformer.fieldRead(advisedFields[i].getName()),
                  new Class[] {Object.class}));
         }
         catch (NoSuchMethodException e)
         {
            //Just means not advised
         }

         chain.add(info);
         
         try
         {
            Field infoField = clazz.getDeclaredField(FieldAccessTransformer.getFieldReadInfoFieldName(advisedFields[i].getName()));
            infoField.setAccessible(true);
            infoField.set(null, new WeakReference(info));
         }
         catch (NoSuchFieldException e)
         {
            // ignore, method may not be advised.
         }
         catch (IllegalAccessException e)
         {
            throw new RuntimeException(e);
         }
         
      }
      return chain;
   }

   protected ArrayList initializeFieldWriteChain()
   {
      ArrayList chain = new ArrayList(advisedFields.length);
      for (int i = 0; i < advisedFields.length; i++)
      {
         FieldInfo info = new FieldInfo();
         info.setAdvisedField(advisedFields[i]);
         info.setAdvisor(this);
         info.setIndex(i);

         try
         {
            info.setWrapper(clazz.getDeclaredMethod(
                  FieldAccessTransformer.fieldWrite(advisedFields[i].getName()),
                  new Class[] {Object.class, advisedFields[i].getType()}));
         }
         catch (NoSuchMethodException e)
         {
            //Just means not advised
         }

         chain.add(info);
         
         try
         {
            Field infoField = clazz.getDeclaredField(FieldAccessTransformer.getFieldWriteInfoFieldName(advisedFields[i].getName()));
            infoField.setAccessible(true);
            infoField.set(null, new WeakReference(info));
         }
         catch (NoSuchFieldException e)
         {
            // ignore, method may not be advised.
         }
         catch (IllegalAccessException e)
         {
            throw new RuntimeException(e);
         }
         
      }
      return chain;
   }

   protected void finalizeFieldReadChain(ArrayList newFieldInfos)
   {
      for (int i = 0; i < newFieldInfos.size(); i++)
      {
         FieldInfo info = (FieldInfo)newFieldInfos.get(i);
         ArrayList list = info.getInterceptorChain();        
         Interceptor[] interceptors = null;
         if (list.size() > 0)
         {
          interceptors = applyPrecedence((Interceptor[]) list.toArray(new Interceptor[list.size()]));
         }
         info.setInterceptors(interceptors);
      }
   }

   protected void finalizeFieldWriteChain(ArrayList newFieldInfos)
   {
      for (int i = 0; i < newFieldInfos.size(); i++)
      {
         FieldInfo info = (FieldInfo)newFieldInfos.get(i);
         ArrayList list = info.getInterceptorChain();        
         Interceptor[] interceptors = null;
         if (list.size() > 0)
         {
          interceptors = applyPrecedence((Interceptor[]) list.toArray(new Interceptor[list.size()]));
         }
         info.setInterceptors(interceptors);
      }
   }

   private void createInterceptorChains() throws Exception
   {
      TLongObjectHashMap newMethodInfos = initializeMethodChain();
      ArrayList newFieldReadInfos = initializeFieldReadChain();
      ArrayList newFieldWriteInfos = initializeFieldWriteChain();
      ArrayList newConstructorInfos = initializeConstructorChain();
      ArrayList newConstructionInfos = initializeConstructionChain();
      
      synchronized (manager.getBindings())
      {
         Iterator it = manager.getBindings().values().iterator();
         while (it.hasNext())
         {
            AdviceBinding binding = (AdviceBinding) it.next();
            if (AspectManager.verbose) System.out.println("iterate binding " + binding.getName());
            resolveMethodPointcut(newMethodInfos, binding);
            resolveFieldPointcut(newFieldReadInfos, binding, false);
            resolveFieldPointcut(newFieldWriteInfos, binding, true);
            resolveConstructorPointcut(newConstructorInfos, binding);
            resolveConstructionPointcut(newConstructionInfos, binding);
         }
      }
      finalizeMethodChain(newMethodInfos);
      finalizeFieldReadChain(newFieldReadInfos);
      finalizeFieldWriteChain(newFieldWriteInfos);
      finalizeConstructorChain(newConstructorInfos);
      finalizeConstructionChain(newConstructionInfos);
      methodInterceptors = newMethodInfos;
      fieldReadInfos = (FieldInfo[]) newFieldReadInfos.toArray(new FieldInfo[newFieldReadInfos.size()]);
      fieldWriteInfos = (FieldInfo[]) newFieldWriteInfos.toArray(new FieldInfo[newFieldWriteInfos.size()]);
      constructorInfos = (ConstructorInfo[]) newConstructorInfos.toArray(new ConstructorInfo[newConstructorInfos.size()]);
      constructionInfos = (ConstructionInfo[]) newConstructionInfos.toArray(new ConstructionInfo[newConstructionInfos.size()]);
      
      populateInterceptorsFromInfos();
      
      doesHaveAspects = adviceBindings.size() > 0;
      // Notify observer about this change
      if (this.interceptorChainObserver != null)
      {
         this.interceptorChainObserver.interceptorChainsUpdated(fieldReadInterceptors, fieldWriteInterceptors,
               constructorInterceptors, methodInterceptors);
      }
   }
   
   private MethodByMethodInfo initializeCallerInterceptorsMap(long callingMethodHash, String calledClass, long calledMethodHash, Method callingMethod, Method calledMethod) throws Exception
   {
      HashMap calledClassesMap = (HashMap) methodCalledByMethodInterceptors.get(callingMethodHash);
      if (calledClassesMap == null)
      {
         calledClassesMap = new HashMap();
         methodCalledByMethodInterceptors.put(callingMethodHash, calledClassesMap);
      }
      TLongObjectHashMap calledMethodsMap = (TLongObjectHashMap) calledClassesMap.get(calledClass);
      if (calledMethodsMap == null)
      {
         calledMethodsMap = new TLongObjectHashMap();
         calledClassesMap.put(calledClass, calledMethodsMap);
      }
      //The standard MethodCalledByXXXXInvocation class calls by reflection and needs access
      calledMethod.setAccessible(true);
      
      Class calledClazz = Thread.currentThread().getContextClassLoader().loadClass(calledClass);
      MethodByMethodInfo info = new MethodByMethodInfo(this, calledClazz, calledMethod, callingMethodHash, calledMethodHash, null);
      calledMethodsMap.put(calledMethodHash, info);
      return info;
   }

   private ConByMethodInfo initializeConCalledByMethodInterceptorsMap(long callingMethodHash, String calledClass, long calledConHash, Constructor calledCon) throws Exception
   {       
      HashMap calledClassesMap = (HashMap) conCalledByMethodInterceptors.get(callingMethodHash);
      if (calledClassesMap == null)
      {
         calledClassesMap = new HashMap();
         conCalledByMethodInterceptors.put(callingMethodHash, calledClassesMap);
      }
      TLongObjectHashMap calledMethodsMap = (TLongObjectHashMap) calledClassesMap.get(calledClass);
      if (calledMethodsMap == null)
      {
         calledMethodsMap = new TLongObjectHashMap();
         calledClassesMap.put(calledClass, calledMethodsMap);
      }

      ConByMethodInfo info = createConByMethodInfo(calledClass, callingMethodHash, calledCon, calledConHash);
      calledMethodsMap.put(calledConHash, info);
      return info;
   }

   private ConByMethodInfo createConByMethodInfo(String calledClass, long callingMethodHash, Constructor calledCon, long calledConHash) throws Exception
   {
      //The standard ConstructorCalledByXXXXInvocation class calls by reflection and needs access
      calledCon.setAccessible(true);

      Class calledClazz = Thread.currentThread().getContextClassLoader().loadClass(calledClass);
      try
      {
         int index = calledClass.lastIndexOf('.');
         String baseClassName = calledClass.substring(index + 1);
         Method wrapper = calledCon.getDeclaringClass().getDeclaredMethod(ConstructorExecutionTransformer.constructorFactory(baseClassName), calledCon.getParameterTypes());
         return new ConByMethodInfo(this, calledClazz, callingMethodHash, calledCon, calledConHash, wrapper, null);
      }
      catch (NoSuchMethodException e)
      {
         return new ConByMethodInfo(this, calledClazz, callingMethodHash, calledCon, calledConHash, null, null);
      }
   }

   private MethodByConInfo initializeConstructorCallerInterceptorsMap(int callingIndex, String calledClass, long calledMethodHash, Method calledMethod) throws Exception
   {
      HashMap calledClassesMap = methodCalledByConInterceptors[callingIndex];
      if (calledClassesMap == null)
      {
         calledClassesMap = new HashMap();
         methodCalledByConInterceptors[callingIndex] = calledClassesMap;
      }
      TLongObjectHashMap calledMethodsMap = (TLongObjectHashMap) calledClassesMap.get(calledClass);
      if (calledMethodsMap == null)
      {
         calledMethodsMap = new TLongObjectHashMap();
         calledClassesMap.put(calledClass, calledMethodsMap);
      }

      //The standard MethodCalledByXXXXInvocation class calls by reflection and needs access
      calledMethod.setAccessible(true);
      Class calledClazz = Thread.currentThread().getContextClassLoader().loadClass(calledClass);
      MethodByConInfo info = new MethodByConInfo(this, calledClazz, callingIndex, calledMethod, calledMethodHash, null);
      calledMethodsMap.put(calledMethodHash, info);
      return info;
   }

   private ConByConInfo initializeConCalledByConInterceptorsMap(int callingIndex, String calledClass, long calledConHash, Constructor calledCon) throws Exception
   {
      HashMap calledClassesMap = conCalledByConInterceptors[callingIndex];
      if (calledClassesMap == null)
      {
         calledClassesMap = new HashMap();
         conCalledByConInterceptors[callingIndex] = calledClassesMap;
      }
      TLongObjectHashMap calledMethodsMap = (TLongObjectHashMap) calledClassesMap.get(calledClass);
      if (calledMethodsMap == null)
      {
         calledMethodsMap = new TLongObjectHashMap();
         calledClassesMap.put(calledClass, calledMethodsMap);
      }
      ConByConInfo info = createConByConInfo(callingIndex, calledClass, calledCon, calledConHash);
      calledMethodsMap.put(calledConHash, info);
      return info;
   }


   private ConByConInfo createConByConInfo(int callingIndex, String calledClass, Constructor calledCon, long calledConHash) throws Exception
   {
      //The standard ConstructorCalledByXXXXInvocation class calls by reflection and needs access
      calledCon.setAccessible(true);
      Class calledClazz = Thread.currentThread().getContextClassLoader().loadClass(calledClass);

      try
      {
         int index = calledClass.lastIndexOf('.');
         String baseClassName = calledClass.substring(index + 1);
         Method wrapper = calledCon.getDeclaringClass().getDeclaredMethod(ConstructorExecutionTransformer.constructorFactory(baseClassName), calledCon.getParameterTypes());
         return new ConByConInfo(this, calledClazz, callingIndex, calledCon, calledConHash, wrapper, null);
      }
      catch (NoSuchMethodException e)
      {
         return new ConByConInfo(this, calledClazz, callingIndex, calledCon, calledConHash, null, null);
      }
   }
   
   protected void rebuildCallerInterceptors() throws Exception
   {
      long[] callingKeys = methodCalledByMethodInterceptors.keys();
      for (int i = 0; i < callingKeys.length; i++)
      {
         long callingHash = callingKeys[i];
         HashMap calledClasses = (HashMap) methodCalledByMethodInterceptors.get(callingHash);
         Iterator classesIterator = calledClasses.entrySet().iterator();
         while (classesIterator.hasNext())
         {
            Map.Entry entry = (Map.Entry) classesIterator.next();
            String cname = (String) entry.getKey();
            TLongObjectHashMap calledMethods = (TLongObjectHashMap) entry.getValue();
            long[] calledKeys = calledMethods.keys();
            for (int j = 0; j < calledKeys.length; j++)
            {
               long calledHash = calledKeys[j];
               ArrayList bindings = getCallerBindings(callingHash, cname, calledHash);
               Method calling = MethodHashing.findMethodByHash(clazz, callingHash);
               bindCallerInterceptorChain(bindings, callingHash, cname, calledHash, calling);
            }
         }
      }
      for (int i = 0; i < methodCalledByConInterceptors.length; i++)
      {
         HashMap calledClasses = methodCalledByConInterceptors[i];
         if (calledClasses == null) continue;
         Iterator classesIterator = calledClasses.entrySet().iterator();
         while (classesIterator.hasNext())
         {
            Map.Entry entry = (Map.Entry) classesIterator.next();
            String cname = (String) entry.getKey();
            TLongObjectHashMap calledMethods = (TLongObjectHashMap) entry.getValue();
            long[] calledKeys = calledMethods.keys();
            for (int j = 0; j < calledKeys.length; j++)
            {
               long calledHash = calledKeys[j];
               ArrayList bindings = getConstructorCallerBindings(i, cname, calledHash);
               bindConstructorCallerInterceptorChain(bindings, i, cname, calledHash);
            }
         }
      }
      callingKeys = conCalledByMethodInterceptors.keys();
      for (int i = 0; i < callingKeys.length; i++)
      {
         long callingHash = callingKeys[i];
         HashMap calledClasses = (HashMap) conCalledByMethodInterceptors.get(callingHash);
         Iterator classesIterator = calledClasses.entrySet().iterator();
         while (classesIterator.hasNext())
         {
            Map.Entry entry = (Map.Entry) classesIterator.next();
            String cname = (String) entry.getKey();
            TLongObjectHashMap calledMethods = (TLongObjectHashMap) entry.getValue();
            long[] calledKeys = calledMethods.keys();
            for (int j = 0; j < calledKeys.length; j++)
            {
               long calledHash = calledKeys[j];
               ArrayList bindings = getConCalledByMethodBindings(callingHash, cname, calledHash);
               bindConCalledByMethodInterceptorChain(bindings, callingHash, cname, calledHash);
            }
         }
      }
      for (int i = 0; i < conCalledByConInterceptors.length; i++)
      {
         HashMap calledClasses = conCalledByConInterceptors[i];
         if (calledClasses == null) continue;
         Iterator classesIterator = calledClasses.entrySet().iterator();
         while (classesIterator.hasNext())
         {
            Map.Entry entry = (Map.Entry) classesIterator.next();
            String cname = (String) entry.getKey();
            TLongObjectHashMap calledMethods = (TLongObjectHashMap) entry.getValue();
            long[] calledKeys = calledMethods.keys();
            for (int j = 0; j < calledKeys.length; j++)
            {
               long calledHash = calledKeys[j];
               ArrayList bindings = getConCalledByConBindings(i, cname, calledHash);
               bindConCalledByConInterceptorChain(bindings, i, cname, calledHash);
            }
         }
      }
   }

   private ArrayList getCallerBindings(long callingHash, String cname, long calledHash)
   {
      HashMap calledClasses = (HashMap) methodCalledByMethodBindings.get(callingHash);
      TLongObjectHashMap calledMethods = (TLongObjectHashMap) calledClasses.get(cname);
      return (ArrayList) calledMethods.get(calledHash);
   }

   private ArrayList getConCalledByMethodBindings(long callingHash, String cname, long calledHash)
   {
      HashMap calledClasses = (HashMap) conCalledByMethodBindings.get(callingHash);
      TLongObjectHashMap calledCons = (TLongObjectHashMap) calledClasses.get(cname);
      return (ArrayList) calledCons.get(calledHash);
   }

   private ArrayList getConstructorCallerBindings(int callingIndex, String cname, long calledHash)
   {
      HashMap calledClasses = methodCalledByConBindings[callingIndex];
      TLongObjectHashMap calledMethods = (TLongObjectHashMap) calledClasses.get(cname);
      return (ArrayList) calledMethods.get(calledHash);
   }

   private ArrayList getConCalledByConBindings(int callingIndex, String cname, long calledHash)
   {
      HashMap calledClasses = conCalledByConBindings[callingIndex];
      TLongObjectHashMap calledMethods = (TLongObjectHashMap) calledClasses.get(cname);
      return (ArrayList) calledMethods.get(calledHash);
   }

   private void bindCallerInterceptorChain(ArrayList bindings, long callingHash, String cname, long calledHash, Method calling)
   {
      MethodByMethodInfo info = getCallerMethodInfo(callingHash, cname, calledHash);
      info.clear();
      Iterator it = bindings.iterator();
      while (it.hasNext())
      {
         AdviceBinding binding = (AdviceBinding) it.next();
         pointcutResolved(info, binding, new MethodCalledByMethodJoinpoint(info.getCallingMethod(), info.getMethod()));
      }
      finalizeMethodCalledByMethodInterceptorChain(info);
   }
   
   protected void finalizeMethodCalledByMethodInterceptorChain(MethodByMethodInfo info)
   {
      ArrayList list = info.getInterceptorChain();
      Interceptor[] interceptors = null;
      if (list.size() > 0)
      {
         interceptors = (Interceptor[]) list.toArray(new Interceptor[list.size()]);
      }
      info.setInterceptors(interceptors);
   }

   private void bindConCalledByMethodInterceptorChain(ArrayList bindings, long callingHash, String cname, long calledHash) throws Exception
   {
      ConByMethodInfo info = getConCalledByMethod(callingHash, cname, calledHash);
      info.clear();
      Iterator it = bindings.iterator();
      while (it.hasNext())
      {
         AdviceBinding binding = (AdviceBinding) it.next();
         pointcutResolved(info, binding, new ConstructorCalledByMethodJoinpoint(info.getCallingMethod(), info.getConstructor()));
      }
      finalizeConCalledByMethodInterceptorChain(info);
   }
   
   protected void finalizeConCalledByMethodInterceptorChain(ConByMethodInfo info)
   {
      ArrayList list = info.getInterceptorChain();
      Interceptor[] interceptors = null;
      if (list.size() > 0)
      {
         interceptors = (Interceptor[]) list.toArray(new Interceptor[list.size()]);
      }
      info.setInterceptors(interceptors);
   }

   private void bindConCalledByConInterceptorChain(ArrayList bindings, int callingIndex, String cname, long calledHash)
   {
      ConByConInfo info = getConCalledByCon(callingIndex, cname, calledHash);
      info.clear();
      Iterator it = bindings.iterator();
      while (it.hasNext())
      {
         AdviceBinding binding = (AdviceBinding) it.next();
         pointcutResolved(info, binding, new ConstructorCalledByConstructorJoinpoint(info.getCalling(), info.getConstructor()));
      }
      finalizeConCalledByConInterceptorChain(info);
   }

   protected void finalizeConCalledByConInterceptorChain(ConByConInfo info)
   {
      ArrayList list = info.getInterceptorChain();
      Interceptor[] interceptors = null;
      if (list.size() > 0)
      {
         interceptors = (Interceptor[]) list.toArray(new Interceptor[list.size()]);
      }
      info.setInterceptors(interceptors);
   }

   private void bindConstructorCallerInterceptorChain(ArrayList bindings, int callingIndex, String cname, long calledHash)
   {
      MethodByConInfo info = getConstructorCallerMethodInfo(callingIndex, cname, calledHash);
      info.clear();
      Iterator it = bindings.iterator();
      while (it.hasNext())
      {
         AdviceBinding binding = (AdviceBinding) it.next();
         pointcutResolved(info, binding, new MethodCalledByConstructorJoinpoint(info.getCalling(), info.getMethod()));
      }
      finalizeMethodCalledByConInterceptorChain(info);
   }
   
   protected void finalizeMethodCalledByConInterceptorChain(MethodByConInfo info)
   {
      ArrayList list = info.getInterceptorChain();
      Interceptor[] interceptors = null;
      if (list.size() > 0)
      {
         interceptors = (Interceptor[]) list.toArray(new Interceptor[list.size()]);
      }
      info.setInterceptors(interceptors);
   }

   protected void rebuildInterceptors()
   {
      if (initialized)
      {
         try
         {
            adviceBindings.clear();
            createInterceptorChains();
            rebuildCallerInterceptors();
         }
         catch (Exception ex)
         {
            throw new RuntimeException(ex);
         }
      }
   }

   protected void bindClassMetaData(ClassMetaDataBinding data)
   {
      try
      {
         ClassMetaDataLoader loader = data.getLoader();
         Object[] objs = advisedMethods.getValues();
         Method[] methods = new Method[objs.length];
         Field[] fields = advisedFields;
         // set to empty array because advisedFields may not have been initialized yet
         if (fields == null) fields = new Field[0];
         Constructor[] cons = constructors;
         // set to empty array because constructors may not have been initialized yet
         if (cons == null) cons = new Constructor[0];
         for (int i = 0; i < objs.length; i++) methods[i] = (Method) objs[i];
         loader.bind(this, data, methods, fields, cons);
      }
      catch (Exception ex)
      {
         // REVISIT:  Need to know how errors affects deployment
         ex.printStackTrace();
      }
   }

   protected void rebindClassMetaData()
   {
      defaultMetaData.clear();
      methodMetaData.clear();
      fieldMetaData.clear();
      constructorMetaData.clear();
      classMetaData.clear();

      for (int i = 0; i < classMetaDataBindings.size(); i++)
      {
         ClassMetaDataBinding data = (ClassMetaDataBinding) classMetaDataBindings.get(i);
         bindClassMetaData(data);
      }
   }


   public synchronized void addClassMetaData(ClassMetaDataBinding data)
   {
      classMetaDataBindings.add(data);
      if (this.clazz == null) return;  // don't bind till later.

      bindClassMetaData(data);
      // Recalculate interceptorPointcuts because of MetaDataInterceptorPointcuts
      adviceBindings.clear();
      doesHaveAspects = false;
      rebuildInterceptors();
   }

   public synchronized void removeClassMetaData(ClassMetaDataBinding data)
   {
      if (classMetaDataBindings.remove(data))
      {
         if (this.clazz == null) return; // not bound yet
         rebindClassMetaData();
         // Recalculate interceptorPointcuts because of MetaDataInterceptorPointcuts
         adviceBindings.clear();
         doesHaveAspects = false;
         rebuildInterceptors();
      }
      
      
   }

   private void initializeEmptyCallerChain(long callingMethodHash, String calledClass, long calledMethodHash) throws Exception
   {
      HashMap callingMethod = (HashMap) methodCalledByMethodBindings.get(callingMethodHash);
      if (callingMethod == null)
      {
         callingMethod = new HashMap();
         methodCalledByMethodBindings.put(callingMethodHash, callingMethod);
      }
      TLongObjectHashMap classMap = (TLongObjectHashMap) callingMethod.get(calledClass);
      if (classMap == null)
      {
         classMap = new TLongObjectHashMap();
         callingMethod.put(calledClass, classMap);
      }
      ArrayList bindings = (ArrayList) classMap.get(calledMethodHash);
      if (bindings == null)
      {
         bindings = new ArrayList();
         classMap.put(calledMethodHash, bindings);
      }
   }

   private void initializeConCalledByMethodEmptyChain(long callingMethodHash, String calledClass, long calledConHash) throws Exception
   {
      HashMap callingMethod = (HashMap) conCalledByMethodBindings.get(callingMethodHash);
      if (callingMethod == null)
      {
         callingMethod = new HashMap();
         conCalledByMethodBindings.put(callingMethodHash, callingMethod);
      }
      TLongObjectHashMap classMap = (TLongObjectHashMap) callingMethod.get(calledClass);
      if (classMap == null)
      {
         classMap = new TLongObjectHashMap();
         callingMethod.put(calledClass, classMap);
      }
      ArrayList bindings = (ArrayList) classMap.get(calledConHash);
      if (bindings == null)
      {
         bindings = new ArrayList();
         classMap.put(calledConHash, bindings);
      }
   }

   private void initializeEmptyConstructorCallerChain(int callingIndex, String calledClass, long calledMethodHash) throws Exception
   {
      HashMap callingCon = methodCalledByConBindings[callingIndex];
      if (callingCon == null)
      {
         callingCon = new HashMap();
         methodCalledByConBindings[callingIndex] = callingCon;
      }
      TLongObjectHashMap classMap = (TLongObjectHashMap) callingCon.get(calledClass);
      if (classMap == null)
      {
         classMap = new TLongObjectHashMap();
         callingCon.put(calledClass, classMap);
      }
      ArrayList bindings = (ArrayList) classMap.get(calledMethodHash);
      if (bindings == null)
      {
         bindings = new ArrayList();
         classMap.put(calledMethodHash, bindings);
      }
   }

   private void initializeConCalledByConEmptyChain(int callingIndex, String calledClass, long calledConHash) throws Exception
   {
      HashMap callingCon = conCalledByConBindings[callingIndex];
      if (callingCon == null)
      {
         callingCon = new HashMap();
         conCalledByConBindings[callingIndex] = callingCon;
      }
      TLongObjectHashMap classMap = (TLongObjectHashMap) callingCon.get(calledClass);
      if (classMap == null)
      {
         classMap = new TLongObjectHashMap();
         callingCon.put(calledClass, classMap);
      }
      ArrayList bindings = (ArrayList) classMap.get(calledConHash);
      if (bindings == null)
      {
         bindings = new ArrayList();
         classMap.put(calledConHash, bindings);
      }
   }

   private void addMethodCalledByMethodPointcut(long callingMethodHash, String calledClass, long calledMethodHash, AdviceBinding binding) throws Exception
   {
      if (AspectManager.verbose) System.err.println("method call matched binding " + binding.getPointcut().getExpr());
      adviceBindings.add(binding);
      binding.addAdvisor(this);
      HashMap callingMethod = (HashMap) methodCalledByMethodBindings.get(callingMethodHash);
      if (callingMethod == null)
      {
         callingMethod = new HashMap();
         methodCalledByMethodBindings.put(callingMethodHash, callingMethod);
      }
      TLongObjectHashMap classMap = (TLongObjectHashMap) callingMethod.get(calledClass);
      if (classMap == null)
      {
         classMap = new TLongObjectHashMap();
         callingMethod.put(calledClass, classMap);
      }
      ArrayList bindings = (ArrayList) classMap.get(calledMethodHash);
      boolean createdBindings = false;
      if (bindings == null)
      {
         bindings = new ArrayList();
         classMap.put(calledMethodHash, bindings);
         createdBindings = true;
      }
      if (!bindings.contains(binding)) bindings.add(binding);

      // this is so that we can undeploy a caller
      ArrayList backrefs = (ArrayList) backrefMethodCalledByMethodBindings.get(binding.getName());
      if (backrefs == null)
      {
         backrefs = new ArrayList();
         backrefMethodCalledByMethodBindings.put(binding.getName(), backrefs);
         backrefs.add(bindings);
      }
      else if (createdBindings) backrefs.add(bindings);
   }

   private void addConstructorCalledByMethodPointcut(long callingMethodHash, String calledClass, long calledMethodHash, AdviceBinding binding) throws Exception
   {
      if (AspectManager.verbose) System.err.println("method call matched binding " + binding.getPointcut().getExpr());
      adviceBindings.add(binding);
      binding.addAdvisor(this);
      HashMap callingMethod = (HashMap) conCalledByMethodBindings.get(callingMethodHash);
      if (callingMethod == null)
      {
         callingMethod = new HashMap();
         conCalledByMethodBindings.put(callingMethodHash, callingMethod);
      }
      TLongObjectHashMap classMap = (TLongObjectHashMap) callingMethod.get(calledClass);
      if (classMap == null)
      {
         classMap = new TLongObjectHashMap();
         callingMethod.put(calledClass, classMap);
      }
      ArrayList bindings = (ArrayList) classMap.get(calledMethodHash);
      boolean createdBindings = false;
      if (bindings == null)
      {
         bindings = new ArrayList();
         classMap.put(calledMethodHash, bindings);
         createdBindings = true;
      }
      if (!bindings.contains(binding)) bindings.add(binding);

      // this is so that we can undeploy a caller
      ArrayList backrefs = (ArrayList) backrefConCalledByMethodBindings.get(binding.getName());
      if (backrefs == null)
      {
         backrefs = new ArrayList();
         backrefConCalledByMethodBindings.put(binding.getName(), backrefs);
         backrefs.add(bindings);
      }
      else if (createdBindings) backrefs.add(bindings);
   }

   public void addConstructorCallerPointcut(int callingIndex, String calledClass, long calledMethodHash, AdviceBinding binding) throws Exception
   {
      if (AspectManager.verbose) System.err.println("constructor call matched binding " + binding.getPointcut().getExpr());
      adviceBindings.add(binding);
      binding.addAdvisor(this);
      HashMap callingCon = methodCalledByConBindings[callingIndex];
      if (callingCon == null)
      {
         callingCon = new HashMap();
         methodCalledByConBindings[callingIndex] = callingCon;
      }
      TLongObjectHashMap classMap = (TLongObjectHashMap) callingCon.get(calledClass);
      if (classMap == null)
      {
         classMap = new TLongObjectHashMap();
         callingCon.put(calledClass, classMap);
      }
      ArrayList bindings = (ArrayList) classMap.get(calledMethodHash);
      boolean createdBindings = false;
      if (bindings == null)
      {
         bindings = new ArrayList();
         classMap.put(calledMethodHash, bindings);
         createdBindings = true;
      }
      if (!bindings.contains(binding)) bindings.add(binding);

      // this is so that we can undeploy a caller
      ArrayList backrefs = (ArrayList) backrefMethodCalledByConstructorBindings.get(binding.getName());
      if (backrefs == null)
      {
         backrefs = new ArrayList();
         backrefMethodCalledByConstructorBindings.put(binding.getName(), backrefs);
         backrefs.add(bindings);
      }
      else if (createdBindings) backrefs.add(bindings);
   }

   private void addConstructorCalledByConPointcut(int callingIndex, String calledClass, long calledConHash, AdviceBinding binding) throws Exception
   {
      if (AspectManager.verbose) System.err.println("constructor call matched binding " + binding.getPointcut().getExpr());
      adviceBindings.add(binding);
      binding.addAdvisor(this);
      HashMap callingCon = conCalledByConBindings[callingIndex];
      if (callingCon == null)
      {
         callingCon = new HashMap();
         conCalledByConBindings[callingIndex] = callingCon;
      }
      TLongObjectHashMap classMap = (TLongObjectHashMap) callingCon.get(calledClass);
      if (classMap == null)
      {
         classMap = new TLongObjectHashMap();
         callingCon.put(calledClass, classMap);
      }
      ArrayList bindings = (ArrayList) classMap.get(calledConHash);
      boolean createdBindings = false;
      if (bindings == null)
      {
         bindings = new ArrayList();
         classMap.put(calledConHash, bindings);
         createdBindings = true;
      }
      if (!bindings.contains(binding)) bindings.add(binding);

      // this is so that we can undeploy a caller
      ArrayList backrefs = (ArrayList) backrefConCalledByConstructorBindings.get(binding.getName());
      if (backrefs == null)
      {
         backrefs = new ArrayList();
         backrefConCalledByConstructorBindings.put(binding.getName(), backrefs);
         backrefs.add(bindings);
      }
      else if (createdBindings) backrefs.add(bindings);
   }

   private void removeCallerPointcut(AdviceBinding binding)
   {
      ArrayList backrefs = (ArrayList) backrefMethodCalledByMethodBindings.get(binding.getName());
      if (backrefs == null) return;
      for (int i = 0; i < backrefs.size(); i++)
      {
         ArrayList ref = (ArrayList) backrefs.get(i);
         ref.remove(binding);
      }
   }

   /**
    * Generates internal, unadvised version of a method name.
    */
   public static String notAdvisedMethodName(String className,
                                             String methodName)
   {
      return className.replace('.', '$') + "$" + methodName +
      NOT_TRANSFORMABLE_SUFFIX;
   }

   /**
    * Is this the name of a private, unadvised thing?
    */
   public static boolean isWithoutAdvisement(String name)
   {
      return name.endsWith(NOT_TRANSFORMABLE_SUFFIX);
   }

   /**
    * Is the field advisable?
    */
   public static boolean isAdvisable(Field field)
   {
      // note: this should match the implementation in the instrumentor.
      int modifiers = field.getModifiers();
      return
      !Modifier.isFinal(modifiers) &&
      !isWithoutAdvisement(field.getName()) &&
      !field.getName().equals("_instanceAdvisor") &&
      !field.getName().endsWith("$aop$mixin") &&
      (field.getName().indexOf('$') == -1);
   }

   /**
    * Is the method advisable?
    */
   public static boolean isAdvisable(Method method)
   {
      // note: this should match the implementation in the instrumentor.
      int modifiers = method.getModifiers();
      return (
      !isWithoutAdvisement(method.getName()) &&
      !Modifier.isAbstract(modifiers) &&
      !Modifier.isNative(modifiers) &&
      !(method.getName().equals("_getAdvisor") && 
            method.getParameterTypes().length == 0 && 
            method.getReturnType().equals(Advisor.class)) &&
      !(method.getName().equals("_getClassAdvisor") && 
            method.getParameterTypes().length == 0 && 
            method.getReturnType().equals(Advisor.class)) &&
      !(method.getName().equals("_getInstanceAdvisor") && 
            method.getParameterTypes().length == 0 &&
            method.getReturnType().equals(InstanceAdvisor.class)) &&
      !(method.getName().equals("_setInstanceAdvisor") && 
            method.getParameterTypes().length == 1 && 
            method.getParameterTypes()[0].equals(InstanceAdvisor.class)));
   }
   
   private void populateFieldTable(ArrayList fields, Class superclass)
   throws Exception
   {
      if (superclass == null) return;
      if (superclass.equals(Object.class)) return;

      populateFieldTable(fields, superclass.getSuperclass());

      // if (!isAdvised(superclass)) return;

      ArrayList temp = new ArrayList();
      Field[] declaredFields = superclass.getDeclaredFields();
      for (int i = 0; i < declaredFields.length; i++)
      {
         if (ClassAdvisor.isAdvisable(declaredFields[i]))
         {
            // Need to do this because notadvisable fields maybe private or protected
            declaredFields[i].setAccessible(true);
            temp.add(declaredFields[i]);
         }
      }
      Collections.sort(temp, FieldComparator.INSTANCE);
      fields.addAll(temp);
   }

   /**
    * Gets advised methods.
    */
   private void createFieldTable() throws Exception
   {
      ArrayList fields = new ArrayList();

      populateFieldTable(fields, clazz);

      advisedFields = (Field[]) fields.toArray(new Field[fields.size()]);

   }

   protected void addDeclaredMethods(Class superclass) throws Exception
   {
      Method[] declaredMethods = superclass.getDeclaredMethods();
      for (int i = 0; i < declaredMethods.length; i++)
      {
         if (ClassAdvisor.isAdvisable(declaredMethods[i]))
         {
            long hash = MethodHashing.methodHash(declaredMethods[i]);
            advisedMethods.put(hash, declaredMethods[i]);
            try
            {
               Method m = declaredMethods[i];
               Method un = superclass.getDeclaredMethod(ClassAdvisor.notAdvisedMethodName(superclass.getName(),
               m.getName()),
               m.getParameterTypes());
               un.setAccessible(true);
               unadvisedMethods.put(hash, un);
            }
            catch (NoSuchMethodException ignored)
            {
            }
         }
      }
   }

   /**
    * Create a HashMap of method hash and Method
    * Superclasses get added first so subclasses will override with
    * correct overriden method
    */
   private void populateMethodTables(Class superclass)
   throws Exception
   {
      if (superclass == null) return;
      if (superclass.equals(Object.class)) return;

      populateMethodTables(superclass.getSuperclass());

      //The advisor for the superclass may be a container
      Advisor superAdvisor = manager.getAnyAdvisorIfAdvised(superclass);
      if (superAdvisor != null && superAdvisor instanceof ClassAdvisor)
      {
         TLongObjectHashMap superHash = ((ClassAdvisor)superAdvisor).getUnadvisedMethods();
         long[] keys = superHash.keys();
         for (int i = 0; i < keys.length; i++)
         {
            unadvisedMethods.put(keys[i], superHash.get(keys[i]));
         }
      }
      addDeclaredMethods(superclass);
   }

   private void createMethodTables()
   throws Exception
   {
      populateMethodTables(clazz.getSuperclass());
      addDeclaredMethods(clazz);
   }

   private void createConstructorTables() throws Exception
   {
      constructors = clazz.getDeclaredConstructors();
      methodCalledByConBindings = new HashMap[constructors.length];
      methodCalledByConInterceptors = new HashMap[constructors.length];

      conCalledByConBindings = new HashMap[constructors.length];
      conCalledByConInterceptors = new HashMap[constructors.length];
      for (int i = 0; i < constructors.length; i++)
      {
         constructors[i].setAccessible(true);
      }
      Arrays.sort(constructors, ConstructorComparator.INSTANCE);
   }

   public MethodByMethodInfo resolveCallerMethodInfo(long callingMethodHash, String calledClass, long calledMethodHash)
   {
      try
      {
         Method callingMethod = MethodHashing.findMethodByHash(clazz, callingMethodHash);
         if (callingMethod == null) throw new RuntimeException("Unable to figure out calling method of a caller pointcut");
         Class called = Thread.currentThread().getContextClassLoader().loadClass(calledClass);
         Method calledMethod = MethodHashing.findMethodByHash(called, calledMethodHash);
         if (calledMethod == null) throw new RuntimeException("Unable to figure out calledmethod of a caller pointcut");

         Iterator it = manager.getBindings().values().iterator();
         boolean matched = false;
         while (it.hasNext())
         {
            AdviceBinding binding = (AdviceBinding) it.next();
            if (binding.getPointcut().matchesCall(this, callingMethod, called, calledMethod))
            {
               addMethodCalledByMethodPointcut(callingMethodHash, calledClass, calledMethodHash, binding);
               matched = true;
            }
         }
         if (!matched) initializeEmptyCallerChain(callingMethodHash, calledClass, calledMethodHash);
         MethodByMethodInfo info = initializeCallerInterceptorsMap(callingMethodHash, calledClass, calledMethodHash, callingMethod, calledMethod);
         ArrayList bindings = getCallerBindings(callingMethodHash, calledClass, calledMethodHash);
         bindCallerInterceptorChain(bindings, callingMethodHash, calledClass, calledMethodHash, callingMethod);
         return info;
      }
      catch (Exception x)
      {
         throw new RuntimeException(x);
      }
   }

   public WeakReference resolveCallerMethodInfoAsWeakReference(long callingMethodHash, String calledClass, long calledMethodHash)
   {
      //Javassist doesn't like this in a field initialiser hence this method
      return new WeakReference(resolveCallerMethodInfo(callingMethodHash, calledClass, calledMethodHash));
   }
   
   public ConByMethodInfo resolveCallerConstructorInfo(long callingMethodHash, String calledClass, long calledConHash)
   {
      try
      {
         Method callingMethod = MethodHashing.findMethodByHash(clazz, callingMethodHash);
         if (callingMethod == null) throw new RuntimeException("Unable to figure out calling method of a constructor caller pointcut");
         Class called = Thread.currentThread().getContextClassLoader().loadClass(calledClass);
         Constructor calledCon = MethodHashing.findConstructorByHash(called, calledConHash);
         if (calledCon == null) throw new RuntimeException("Unable to figure out calledcon of a constructor caller pointcut");

         boolean matched = false;
         synchronized (manager.getBindings())
         {
            Iterator it = manager.getBindings().values().iterator();
            while (it.hasNext())
            {
               AdviceBinding binding = (AdviceBinding) it.next();
               if (binding.getPointcut().matchesCall(this, callingMethod, called, calledCon))
               {
                  addConstructorCalledByMethodPointcut(callingMethodHash, calledClass, calledConHash, binding);
                  matched = true;
               }
            }
         }
         if (!matched) initializeConCalledByMethodEmptyChain(callingMethodHash, calledClass, calledConHash);
         ConByMethodInfo info = initializeConCalledByMethodInterceptorsMap(callingMethodHash, calledClass, calledConHash, calledCon);
         ArrayList bindings = getConCalledByMethodBindings(callingMethodHash, calledClass, calledConHash);
         bindConCalledByMethodInterceptorChain(bindings, callingMethodHash, calledClass, calledConHash);
         return info;
      }
      catch (Exception x)
      {
         throw new RuntimeException(x);
      }
   }

   public WeakReference resolveCallerConstructorInfoAsWeakReference(long callingMethodHash, String calledClass, long calledConHash)
   {
      //Javassist doesn't like this in a field initialiser hence this method
      return new WeakReference(resolveCallerConstructorInfo(callingMethodHash, calledClass, calledConHash));
   }

   public MethodByConInfo resolveConstructorCallerMethodInfo(int callingIndex, String calledClass, long calledMethodHash)
   {
      try
      {
         Constructor callingConstructor = constructors[callingIndex];
         if (callingConstructor == null) throw new RuntimeException("Unable to figure out calling method of a caller pointcut");
         Class called = Thread.currentThread().getContextClassLoader().loadClass(calledClass);
         Method calledMethod = MethodHashing.findMethodByHash(called, calledMethodHash);
         if (calledMethod == null) throw new RuntimeException("Unable to figure out calledmethod of a caller pointcut");

         boolean matched = false;
         
         synchronized (manager.getBindings())
         {
            Iterator it = manager.getBindings().values().iterator();
            while (it.hasNext())
            {
               AdviceBinding binding = (AdviceBinding) it.next();
               if (binding.getPointcut().matchesCall(this, callingConstructor, called, calledMethod))
               {
                  addConstructorCallerPointcut(callingIndex, calledClass, calledMethodHash, binding);
                  matched = true;
               }
            }
         }
         if (!matched) initializeEmptyConstructorCallerChain(callingIndex, calledClass, calledMethodHash);
         MethodByConInfo info = initializeConstructorCallerInterceptorsMap(callingIndex, calledClass, calledMethodHash, calledMethod);
         ArrayList bindings = getConstructorCallerBindings(callingIndex, calledClass, calledMethodHash);
         bindConstructorCallerInterceptorChain(bindings, callingIndex, calledClass, calledMethodHash);
         return info;
      }
      catch (Exception x)
      {
         throw new RuntimeException(x);
      }
   }

   public WeakReference resolveConstructorCallerMethodInfoAsWeakReference(int callingIndex, String calledClass, long calledMethodHash)
   {
      //Javassist doesn't like this in a field initialiser hence this method
      return new WeakReference(resolveConstructorCallerMethodInfo(callingIndex, calledClass, calledMethodHash));
   }
   
   public ConByConInfo resolveConstructorCallerConstructorInfo(int callingIndex, String calledClass, long calledConHash)
   {
      try
      {
         Constructor callingConstructor = constructors[callingIndex];
         if (callingConstructor == null) throw new RuntimeException("Unable to figure out calling method of a caller pointcut");
         Class called = Thread.currentThread().getContextClassLoader().loadClass(calledClass);
         Constructor calledCon = MethodHashing.findConstructorByHash(called, calledConHash);
         if (calledCon == null) throw new RuntimeException("Unable to figure out calledcon of a caller pointcut");

         boolean matched = false;
         synchronized (manager.getBindings())
         {
            Iterator it = manager.getBindings().values().iterator();
            while (it.hasNext())
            {
               AdviceBinding binding = (AdviceBinding) it.next();
               if (binding.getPointcut().matchesCall(this, callingConstructor, called, calledCon))
               {
                  addConstructorCalledByConPointcut(callingIndex, calledClass, calledConHash, binding);
                  matched = true;
               }
            }
         }
         if (!matched) initializeConCalledByConEmptyChain(callingIndex, calledClass, calledConHash);
         ConByConInfo info = initializeConCalledByConInterceptorsMap(callingIndex, calledClass, calledConHash, calledCon);
         ArrayList bindings = getConCalledByConBindings(callingIndex, calledClass, calledConHash);
         bindConCalledByConInterceptorChain(bindings, callingIndex, calledClass, calledConHash);
         return info;
      }
      catch (Exception x)
      {
         throw new RuntimeException(x);
      }
   }

   public WeakReference resolveConstructorCallerConstructorInfoAsWeakReference(int callingIndex, String calledClass, long calledConHash)
   {
      //Javassist doesn't like this in a field initialiser hence this method
      return new WeakReference(resolveConstructorCallerConstructorInfo(callingIndex, calledClass, calledConHash));
   }
   /////////////////////////
   // Invoking

   /**
    * Invokes target object without applying interceptors.
    */
   public Object invokeWithoutAdvisement(Object target, long methodHash,
                                         Object[] arguments) throws Throwable
   {
      try
      {
         Method method = (Method) unadvisedMethods.get(methodHash);
         return method.invoke(target, arguments);
      }
      catch (InvocationTargetException e)
      {
         throw e.getTargetException();
      }
   }

   public Object invokeNewWithoutAdvisement(Object[] arguments, Constructor constructor) throws Throwable
   {
      try
      {
         return constructor.newInstance(arguments);
      }
      catch (InstantiationException in)
      {
         throw new RuntimeException("failed to call constructor", in);
      }
      catch (IllegalAccessException ill)
      {
         throw new RuntimeException("illegal access", ill);
      }
      catch (InvocationTargetException ite)
      {
         throw ite.getCause();
      }
   }


   public Object invokeMethod(long methodHash, Object[] arguments) throws Throwable
   {
      return invokeMethod(null, methodHash, arguments);
   }

   public Object invokeMethod(Object target, long methodHash, Object[] arguments) throws Throwable
   {
      InstanceAdvisor advisor = null;
      if (target != null)
      {
         InstanceAdvised advised = (InstanceAdvised) target;
         advisor = advised._getInstanceAdvisor();
      }
      MethodInfo info = (MethodInfo) methodInterceptors.get(methodHash);
      info.initialiseInterceptors();
      return invokeMethod(advisor, target, methodHash, arguments, info);
   }


   public Object invokeMethod(InstanceAdvisor instanceAdvisor, Object target, long methodHash, Object[] arguments)
   throws Throwable
   {
      MethodInfo info = (MethodInfo) methodInterceptors.get(methodHash);
      if (info == null)
      {
         System.out.println("info is null for hash: " + methodHash + " of " + clazz.getName());
      }
      return invokeMethod(instanceAdvisor, target, methodHash, arguments, info);
   }

   public Object invokeMethod(InstanceAdvisor instanceAdvisor, Object target, long methodHash, Object[] arguments, MethodInfo info)
   throws Throwable
   {
      Interceptor[] aspects = info.getInterceptors();
      info.initialiseInterceptors();
      if (instanceAdvisor != null && (instanceAdvisor.hasInterceptors()))
      {
         aspects = instanceAdvisor.getInterceptors(aspects);
      }
      MethodInvocation invocation = new MethodInvocation(info, aspects);

      invocation.setArguments(arguments);
      invocation.setTargetObject(target);
      return invocation.invokeNext();
   }

   /**
    *@deprecated
    */
   public Object invokeCaller(long callingMethodHash, Object target, Object[] args, CallerMethodInfo info, Object callingObject)
   throws Throwable
   {
      return invokeCaller((MethodByMethodInfo)info, callingObject, target, args);
   }

   public Object invokeCaller(MethodByMethodInfo info, Object callingObject, Object target, Object[] args) throws Throwable
   {
      info.initialiseInterceptors();
      MethodCalledByMethodInvocation invocation = new MethodCalledByMethodInvocation(info, callingObject, target, args, info.getInterceptors());
      invocation.setTargetObject(target);
      return invocation.invokeNext();
   }

   /**
    *@deprecated
    */
   public Object invokeConCalledByMethod(long callingMethodHash, Object[] args, CallerConstructorInfo info, Object callingObject)
   throws Throwable
   {
      return invokeConCalledByMethod((ConByMethodInfo)info, callingObject, args);
   }
   
   public Object invokeConCalledByMethod(ConByMethodInfo info, Object callingObject, Object[] args)
   throws Throwable
   {
      info.initialiseInterceptors();
      ConstructorCalledByMethodInvocation invocation = new ConstructorCalledByMethodInvocation(info, callingObject, args, info.getInterceptors());
      return invocation.invokeNext();
   }
   
   /**
    *@deprecated
    */
   public Object invokeConstructorCaller(int callingIndex, Object target, Object[] args, CallerMethodInfo info)
   throws Throwable
   {
      return invokeConstructorCaller((MethodByConInfo)info, null, target, args);
   }
   
   /**
    *@deprecated
    */
   public Object invokeConstructorCaller(int callingIndex, Object callingObject, Object target, Object[] args, CallerMethodInfo info)
   throws Throwable
   {
      return invokeConstructorCaller((MethodByConInfo)info, callingObject, target, args);
   }
    
   /**
    * @deprecated
    * 
    * Prefer using the version with <code>callingObject</code> instead of this one,
    * since this object is available for call invocations made inside constructors.
    * 
    * @see #invokeConstructorCaller(MethodByConInfo, Object, Object, Object[])
    */
   public Object invokeConstructorCaller(MethodByConInfo info, Object target, Object[] args)
   throws Throwable
   {
      return invokeConstructorCaller(info, null, target, args);
   }
   
   public Object invokeConstructorCaller(MethodByConInfo info, Object callingObject, Object target, Object[] args)
   throws Throwable
   {
      info.initialiseInterceptors();
      MethodCalledByConstructorInvocation invocation = new MethodCalledByConstructorInvocation(info, callingObject, target, args, info.getInterceptors());
      invocation.setTargetObject(target);
      return invocation.invokeNext();
   }

   /**
    *@deprecated
    */
   public Object invokeConCalledByCon(int callingIndex, Object[] args, CallerConstructorInfo info)
   throws Throwable
   {
      return invokeConCalledByCon((ConByConInfo)info, null, args);
   }
   
   /**
    *@deprecated
    */
   public Object invokeConCalledByCon(int callingIndex, Object callingObject, Object[] args, CallerConstructorInfo info)
   throws Throwable
   {
      return invokeConCalledByCon((ConByConInfo)info, null, args);
   }
   
   /**
    * @deprecated
    * 
    * Prefer using the version with <code>callingObject</code> instead of this one,
    * since this object is available for call invocations made inside constructors.
    * 
    * @see #invokeConCalledByCon(ConByConInfo, Object, Object[])
    */
   public Object invokeConCalledByCon(ConByConInfo info, Object[] args)
   throws Throwable
   {
      return this.invokeConCalledByCon(info, null, args);
   }
   
   public Object invokeConCalledByCon(ConByConInfo info, Object callingObject, Object[] args)
   throws Throwable
   {
      info.initialiseInterceptors();
      ConstructorCalledByConstructorInvocation invocation = new ConstructorCalledByConstructorInvocation(info, callingObject, args, info.getInterceptors());
      return invocation.invokeNext();
   }

   private MethodByMethodInfo getCallerMethodInfo(long callingMethodHash, String calledClass, long calledMethodHash)
   {
      HashMap calledClasses = (HashMap) methodCalledByMethodInterceptors.get(callingMethodHash);
      TLongObjectHashMap calledMethods = (TLongObjectHashMap) calledClasses.get(calledClass);
      MethodByMethodInfo info = (MethodByMethodInfo) calledMethods.get(calledMethodHash);
      return info;
   }

   private ConByMethodInfo getConCalledByMethod(long callingMethodHash, String calledClass, long calledConHash)
   {
      HashMap calledClasses = (HashMap) conCalledByMethodInterceptors.get(callingMethodHash);
      TLongObjectHashMap calledMethods = (TLongObjectHashMap) calledClasses.get(calledClass);
      ConByMethodInfo info = (ConByMethodInfo) calledMethods.get(calledConHash);
      return info;
   }

   private MethodByConInfo getConstructorCallerMethodInfo(int callingIndex, String calledClass, long calledMethodHash)
   {
      HashMap calledClasses = methodCalledByConInterceptors[callingIndex];
      TLongObjectHashMap calledMethods = (TLongObjectHashMap) calledClasses.get(calledClass);
      MethodByConInfo info = (MethodByConInfo) calledMethods.get(calledMethodHash);
      return info;
   }

   private ConByConInfo getConCalledByCon(int callingIndex, String calledClass, long calledConHash)
   {
      HashMap calledClasses = conCalledByConInterceptors[callingIndex];
      TLongObjectHashMap calledMethods = (TLongObjectHashMap) calledClasses.get(calledClass);
      ConByConInfo info = (ConByConInfo) calledMethods.get(calledConHash);
      return info;
   }


   public Object invokeNew(Object[] args, int idx) throws Throwable
   {
      constructorInfos[idx].initialiseInterceptors();
      Interceptor[] cInterceptors = constructorInfos[idx].getInterceptors();
      if (cInterceptors == null) cInterceptors = new Interceptor[0];
      ConstructorInvocation invocation = new ConstructorInvocation(cInterceptors);

      invocation.setAdvisor(this);
      invocation.setArguments(args);
      invocation.setConstructor(constructors[idx]);
      return invocation.invokeNext();
   }

   /**
    * Invokes interceptor chain.
    * This is the beginning
    */
   public Object invokeRead(Object target, int index)
   throws Throwable
   {
      fieldReadInfos[index].initialiseInterceptors();
      Interceptor[] aspects = fieldReadInfos[index].getInterceptors();
      if (aspects == null) aspects = new Interceptor[0];
      FieldReadInvocation invocation;
      if (target != null)
      {
         InstanceAdvised advised = (InstanceAdvised) target;
         InstanceAdvisor advisor = advised._getInstanceAdvisor();
         if (advisor != null && advisor.hasInterceptors())
         {
            aspects = advisor.getInterceptors(aspects);
         }
      }
      invocation = new FieldReadInvocation(advisedFields[index], index, aspects);
      invocation.setAdvisor(this);
      invocation.setTargetObject(target);
      return invocation.invokeNext();
   }

   /**
    * Invokes interceptor chain.
    * This is the beginning
    */
   public Object invokeWrite(Object target, int index, Object value)
   throws Throwable
   {
      fieldWriteInfos[index].initialiseInterceptors();
      Interceptor[] aspects = fieldWriteInfos[index].getInterceptors();
      if (aspects == null) aspects = new Interceptor[0];
      FieldWriteInvocation invocation;
      if (target != null)
      {
         InstanceAdvised advised = (InstanceAdvised) target;
         InstanceAdvisor advisor = advised._getInstanceAdvisor();
         if (advisor != null && advisor.hasInterceptors())
         {
            aspects = advised._getInstanceAdvisor().getInterceptors(aspects);
         }
      }
      invocation = new FieldWriteInvocation(advisedFields[index], index, value, aspects);
      invocation.setAdvisor(this);
      invocation.setTargetObject(target);
      return invocation.invokeNext();
   }

   /**
    * Invokes interceptor chain.
    * This is the beginning
    */
   public Object invoke(Invocation invocation) throws Throwable
   {
      if (invocation instanceof FieldWriteInvocation)
      {
         FieldWriteInvocation fieldInvocation = (FieldWriteInvocation) invocation;
         Object target = fieldInvocation.getTargetObject();
         Object val = fieldInvocation.getValue();
         Field field = fieldInvocation.getField();
         field.set(target, val);
         return null;
      }
      else if (invocation instanceof FieldReadInvocation)
      {
         FieldReadInvocation fieldInvocation = (FieldReadInvocation) invocation;
         Object target = fieldInvocation.getTargetObject();
         Field field = fieldInvocation.getField();
         return field.get(target);
      }
      else if (invocation instanceof MethodInvocation)
      {
         MethodInvocation methodInvocation = (MethodInvocation) invocation;
         return invokeWithoutAdvisement(methodInvocation.getTargetObject(),
         methodInvocation.getMethodHash(),
         methodInvocation.getArguments());
      }
      else if (invocation instanceof ConstructorInvocation)
      {
         ConstructorInvocation cInvocation = (ConstructorInvocation) invocation;
         Object[] arguments = cInvocation.getArguments();
         Constructor constructor = cInvocation.getConstructor();
         return invokeNewWithoutAdvisement(arguments, constructor);
      }
      throw new IllegalStateException("Unknown Invocation type: " + invocation.getClass().getName());
   }

   // interceptor chain observer
   private InterceptorChainObserver interceptorChainObserver;
   
   /**
    * Returns the interceptor chain observer associated with this advisor.
    */
   protected InterceptorChainObserver getInterceptorChainObserver()
   {
      return this.interceptorChainObserver;
   }
   
   /**
    * Defines the interceptor chain observer associated with this advisor.
    * @param observer the interceptor chain observer.
    */
   protected void setInterceptorChainObserver(InterceptorChainObserver observer)
   {
      if (observer != null)
      {
         observer.initialInterceptorChains(this.clazz, fieldReadInterceptors, fieldWriteInterceptors,
               constructorInterceptors, methodInterceptors);
      }
      this.interceptorChainObserver = observer;
   }

   /** @deprecated We should just be using xxxxInfos */
   protected void populateInterceptorsFromInfos()
   {
      super.populateInterceptorsFromInfos();
      fieldReadInterceptors = new Interceptor[fieldReadInfos.length][];
      for (int i = 0 ; i < fieldReadInfos.length ; i++)
      {
         fieldReadInterceptors[i] = fieldReadInfos[i].getInterceptors();
      }
      fieldWriteInterceptors = new Interceptor[fieldWriteInfos.length][];
      for (int i = 0 ; i < fieldWriteInfos.length ; i++)
      {
         fieldWriteInterceptors[i] = fieldWriteInfos[i].getInterceptors(); 
      }
      constructionInterceptors = new Interceptor[constructionInfos.length][];
      for (int i = 0 ; i < constructionInfos.length ; i++)
      {
         constructionInterceptors[i] = constructionInfos[i].getInterceptors(); 
      }

   }
   
   public static boolean isGeneratedClassAdvisor(Class clazz)
   {
      if (clazz != null && clazz != Object.class)
      {
         if (clazz == GeneratedClassAdvisor.class)
         {
            return true;
         }
         return isGeneratedClassAdvisor(clazz.getSuperclass());
      }
      return false;
   }

}