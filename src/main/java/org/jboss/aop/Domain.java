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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javassist.ClassPool;

import org.jboss.aop.advice.AdviceStack;
import org.jboss.aop.advice.AspectDefinition;
import org.jboss.aop.advice.InterceptorFactory;
import org.jboss.aop.metadata.ClassMetaDataLoader;
import org.jboss.aop.pointcut.CFlowStack;
import org.jboss.aop.pointcut.DynamicCFlow;
import org.jboss.aop.pointcut.Pointcut;
import org.jboss.aop.pointcut.PointcutExpression;
import org.jboss.aop.pointcut.PointcutStats;
import org.jboss.aop.pointcut.Typedef;
import org.jboss.aop.pointcut.ast.ClassExpression;

/**
 * Comment
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 59093 $
 */
public class Domain extends AspectManager
{
   protected AspectManager parent;
   protected boolean parentFirst;
   protected boolean inheritsDeclarations = true;
   protected boolean inheritsBindings = false;


   public Domain(AspectManager manager, boolean parentFirst)
   {
      //Ignore the name - it is used by AOP 2.0
      this(manager, null, parentFirst);
   }

   public Domain(AspectManager manager, String name, boolean parentFirst)
   {
      this.parent = manager;
      this.parentFirst = parentFirst;
      //Ignore the name - it is used by AOP 2.0
   }


   /**
    * Inherits interceptor, aspect, advice stack definitions
    *
    * @param inheritsDeclarations
    */
   public void setInheritsDeclarations(boolean inheritsDeclarations)
   {
      this.inheritsDeclarations = inheritsDeclarations;
   }

   public void setInheritsBindings(boolean inheritBindings)
   {
      this.inheritsBindings = inheritBindings;
      if (inheritsBindings)
      {
         parent.subscribeSubDomain(this);
      }
      else
      {
         parent.unsubscribeSubDomain(this);
      }
   }

   
   public void removeBindings(ArrayList binds)
   {
      super.removeBindings(binds);
   }

   public LinkedHashMap getBindings()
   {
      if (inheritsBindings)
      {
         if (!parentFirst)
         {
            // when child first, parent bindings go in first so that they can be overridden by child.
            LinkedHashMap map = new LinkedHashMap(parent.getBindings());
            map.putAll(this.bindings);
            return map;
         }
         else
         {
            LinkedHashMap map = new LinkedHashMap(this.bindings);
            map.putAll(parent.getBindings());
            return map;
         }
      }
      return super.getBindings();
   }

   public LinkedHashMap getPointcuts()
   {
      if (inheritsBindings)
      {
         if (!parentFirst)
         {
            // when child first, parent bindings go in first so that they can be overridden by child.
            LinkedHashMap map = new LinkedHashMap(parent.getPointcuts());
            map.putAll(this.pointcuts);
            return map;
         }
         else
         {
            LinkedHashMap map = new LinkedHashMap(this.pointcuts);
            map.putAll(parent.getPointcuts());
            return map;
         }
      }
      return super.getPointcuts();
   }

   public LinkedHashMap getPointcutInfos()
   {
      if (inheritsBindings)
      {
         if (!parentFirst)
         {
            // when child first, parent bindings go in first so that they can be overridden by child.
            LinkedHashMap map = new LinkedHashMap(parent.getPointcutInfos());
            map.putAll(this.pointcutInfos);
            return map;
         }
         else
         {
            LinkedHashMap map = new LinkedHashMap(this.pointcutInfos);
            map.putAll(parent.getPointcutInfos());
            return map;
         }
      }
      return super.getPointcutInfos();
   }

   public List getAnnotationIntroductions()
   {

      if (inheritsBindings)
      {
         List result = new ArrayList();
         if (!parentFirst)
         {
            // when child first, parent bindings go in first so that they can be overridden by child.
            result.addAll(parent.getAnnotationIntroductions());
            synchronized (annotationIntroductions)
            {
               result = new ArrayList(annotationIntroductions.values());
            }
            return result;
         }
         else
         {
            synchronized (annotationIntroductions)
            {
               result = new ArrayList(annotationIntroductions.values());
            }
            result.addAll(parent.getAnnotationIntroductions());
            return result;
         }
      }

      return super.getAnnotationIntroductions();

   }

   public List getAnnotationOverrides()
   {
      if (inheritsBindings)
      {
         ArrayList list = new ArrayList();
         if (!parentFirst)
         {
            list.addAll(parent.getAnnotationOverrides());
            synchronized (annotationOverrides)
            {
               list.addAll(annotationOverrides.values());
            }
            return list;
         }
         else
         {
            synchronized (annotationOverrides)
            {
               list.addAll(annotationOverrides.values());
            }
            list.addAll(parent.getAnnotationOverrides());
            return list;
         }
      }
      return super.getAnnotationOverrides();
   }

   public Map getInterfaceIntroductions()
   {
      if (inheritsBindings)
      {
         HashMap map = new HashMap();
         if (!parentFirst)
         {
            map.putAll(parent.getInterfaceIntroductions());
            synchronized (interfaceIntroductions)
            {
               map.putAll(interfaceIntroductions);
            }
            return map;
         }
         else
         {
            synchronized (interfaceIntroductions)
            {
               map.putAll(interfaceIntroductions);
            }
            map.putAll(parent.getInterfaceIntroductions());
            return map;
         }
      }
      return super.getInterfaceIntroductions();
   }

   public Map getTypedefs()
   {
      if (inheritsBindings)
      {
         HashMap map = new HashMap();
         if (!parentFirst)
         {
            map.putAll(parent.getTypedefs());
            synchronized (typedefs)
            {
               map.putAll(typedefs);
            }
            return map;
         }
         else
         {
            synchronized (typedefs)
            {
               map.putAll(typedefs);
            }
            map.putAll(parent.getTypedefs());
            return map;
         }
      }
      return super.getTypedefs();
   }

   public Map getInterceptorStacks()
   {
      if (inheritsBindings)
      {
         HashMap map = new HashMap();
         if (!parentFirst)
         {
            map.putAll(parent.getInterceptorStacks());
            synchronized (interceptorStacks)
            {
               map.putAll(interceptorStacks);
            }
            return map;
         }
         else
         {
            synchronized (interceptorStacks)
            {
               map.putAll(interceptorStacks);
            }
            map.putAll(parent.getInterceptorStacks());
            return map;
         }
      }
      return super.getInterceptorStacks();
   }

   public Map getClassMetaDataLoaders()
   {
      if (inheritsBindings)
      {
         HashMap map = new HashMap();
         if (!parentFirst)
         {
            map.putAll(parent.getClassMetaDataLoaders());
            synchronized (classMetaDataLoaders)
            {
               map.putAll(classMetaDataLoaders);
            }
            return map;
         }
         else
         {
            synchronized (classMetaDataLoaders)
            {
               map.putAll(classMetaDataLoaders);
            }
            map.putAll(parent.getClassMetaDataLoaders());
            return map;
         }
      }
      return super.getClassMetaDataLoaders();
   }

   public Map getCflowStacks()
   {
      if (inheritsBindings)
      {
         HashMap map = new HashMap();
         if (!parentFirst)
         {
            map.putAll(parent.getCflowStacks());
            synchronized (cflowStacks)
            {
               map.putAll(cflowStacks);
            }
            return map;
         }
         else
         {
            synchronized (cflowStacks)
            {
               map.putAll(cflowStacks);
            }
            map.putAll(parent.getCflowStacks());
            return map;
         }
      }
      return super.getCflowStacks();
   }

   public Map getDynamicCFlows()
   {
      if (inheritsBindings)
      {
         HashMap map = new HashMap();
         if (!parentFirst)
         {
            map.putAll(parent.getDynamicCFlows());
            synchronized (dynamicCFlows)
            {
               map.putAll(dynamicCFlows);
            }
            return map;
         }
         else
         {
            synchronized (dynamicCFlows)
            {
               map.putAll(dynamicCFlows);
            }
            map.putAll(parent.getDynamicCFlows());
            return map;
         }
      }
      return super.getDynamicCFlows();   
   }

   public Map getPerVMAspects()
   {
      if (inheritsBindings)
      {
         HashMap map = new HashMap();
         if (!parentFirst)
         {
            map.putAll(parent.getPerVMAspects());
            synchronized (perVMAspects)
            {
               map.putAll(perVMAspects);
            }
            return map;
         }
         else
         {
            synchronized (perVMAspects)
            {
               map.putAll(perVMAspects);
            }
            map.putAll(parent.getPerVMAspects());
            return map;
         }
      }
      return super.getPerVMAspects();   
   }

   public Map getClassMetaData()
   {
      if (inheritsBindings)
      {
         HashMap map = new HashMap();
         if (!parentFirst)
         {
            map.putAll(parent.getClassMetaData());
            synchronized (classMetaData)
            {
               map.putAll(classMetaData);
            }
            return map;
         }
         else
         {
            synchronized (classMetaData)
            {
               map.putAll(classMetaData);
            }
            map.putAll(parent.getClassMetaData());
            return map;
         }
      }
      return super.getClassMetaData();   
   }

   public InterceptorFactory getInterceptorFactory(String name)
   {
      InterceptorFactory factory = null;
      if (!parentFirst)
      {
         factory = parent.getInterceptorFactory(name);
         if (factory != null) return factory;
      }
      factory = super.getInterceptorFactory(name);

      if (factory != null) return factory;
      return parent.getInterceptorFactory(name);
   }

   public AdviceStack getAdviceStack(String name)
   {
      AdviceStack factory = null;
      if (!parentFirst)
      {
         factory = parent.getAdviceStack(name);
         if (factory != null) return factory;
      }
      factory = super.getAdviceStack(name);

      if (factory != null) return factory;
      return parent.getAdviceStack(name);
   }

   public Object getPerVMAspect(AspectDefinition def)
   {
      Object factory = null;
      if (!parentFirst)
      {
         factory = parent.getPerVMAspect(def);
         if (factory != null) return factory;
      }
      factory = super.getPerVMAspect(def);

      if (factory != null) return factory;
      return parent.getPerVMAspect(def);
   }

   public Object getPerVMAspect(String def)
   {
      Object factory = null;
      if (!parentFirst)
      {
         factory = parent.getPerVMAspect(def);
         if (factory != null) return factory;
      }
      factory = super.getPerVMAspect(def);

      if (factory != null) return factory;
      return parent.getPerVMAspect(def);
   }

   public AspectDefinition getAspectDefinition(String name)
   {
      AspectDefinition factory = null;
      if (!parentFirst)
      {
         factory = parent.getAspectDefinition(name);
         if (factory != null) return factory;
      }
      factory = super.getAspectDefinition(name);

      if (factory != null) return factory;
      return parent.getAspectDefinition(name);
   }

   public Typedef getTypedef(String name)
   {
      Typedef factory = null;
      if (!parentFirst)
      {
         factory = parent.getTypedef(name);
         if (factory != null) return factory;
      }
      factory = super.getTypedef(name);

      if (factory != null) return factory;
      return parent.getTypedef(name);
   }
   
   public DomainDefinition getContainer(String name)
   {
      DomainDefinition container = null;
      if (!parentFirst)
      {
         container = parent.getContainer(name);
         if (container != null) return container;
      }
      container = super.getContainer(name);
      if (container != null) return container;
      return parent.getContainer(name);
   }


   
   /**
    * Find a pointcut of with a given name
    */
   public Pointcut getPointcut(String name)
   {
      Pointcut pointcut = null;
      
      if (!parentFirst)
      {
         pointcut = parent.getPointcut(name);
         if (pointcut != null) return pointcut;
      }
      pointcut = super.getPointcut(name);
      if (pointcut != null) return pointcut;
      return parent.getPointcut(name);
   }

   public void attachMetaData(ClassAdvisor advisor, Class clazz)
   {
      if (inheritsBindings)
      {
         if (!parentFirst)
         {
            // when child first, parent bindings go in first so that they can be overridden by child.
            parent.attachMetaData(advisor, clazz);
            super.attachMetaData(advisor, clazz);
         }
         else
         {
            super.attachMetaData(advisor, clazz);
            parent.attachMetaData(advisor, clazz);
         }
      }
      else
      {
         super.attachMetaData(advisor, clazz);
      }
   }

   public CFlowStack getCFlowStack(String name)
   {
      if (inheritsDeclarations)
      {
         if (!parentFirst)
         {
            CFlowStack cflow = super.getCFlowStack(name);
            if (cflow == null)
            {
               cflow = parent.getCFlowStack(name);
            }
            return cflow;
         }
         else
         {
            CFlowStack cflow = parent.getCFlowStack(name);
            if (cflow == null)
            {
               cflow = super.getCFlowStack(name);
            }
            return cflow;
         }
      }
      else
      {
         return super.getCFlowStack(name);
      }
   }


   public DynamicCFlow getDynamicCFlow(String name)
   {      
      if (inheritsBindings)
      {
         if (!parentFirst)
         {
            DynamicCFlow cflow = super.getDynamicCFlow(name);
            if (cflow == null)
            {
               cflow = parent.getDynamicCFlow(name);
            }
            return cflow;
         }
         else
         {
            DynamicCFlow cflow = parent.getDynamicCFlow(name);
            if (cflow == null)
            {
               cflow = super.getDynamicCFlow(name);
            }
            return cflow;
         }
      }
      else
      {
         return super.getDynamicCFlow(name);
      }
   }

   public LinkedHashMap getPrecedenceDefs()
   {
      if (inheritsDeclarations)
      {
         if (!parentFirst)
         {
            // when child first, parent bindings go in first so that they can be overridden by child.
            LinkedHashMap map = new LinkedHashMap(parent.getPrecedenceDefs());
            map.putAll(this.precedenceDefs);
            return map;
         }
         else
         {
            LinkedHashMap map = new LinkedHashMap(this.precedenceDefs);
            map.putAll(parent.getPrecedenceDefs());
            return map;
         }
      }
      return super.getPrecedenceDefs();
   }
   
   public ClassMetaDataLoader findClassMetaDataLoader(String group)
   {
      if (inheritsDeclarations)
      {
         if (!parentFirst)
         {
            ClassMetaDataLoader loader = super.findClassMetaDataLoader(group);
            if (loader == null)
            {
               loader = parent.findClassMetaDataLoader(group);
            }
            return loader;
         }
         else
         {
            ClassMetaDataLoader loader = parent.findClassMetaDataLoader(group);
            if (loader == null)
            {
               loader = super.findClassMetaDataLoader(group);
            }
            return loader;
         }
      }
      
      return super.findClassMetaDataLoader(group);
   }
   
   //////////////////////////////////////////////////////////////////////////
   //Methods that should delegate to the top AspectManager
   
   /** Managed by the top-level aspect manager */
   protected Map getScopedClassLoaderDomains()
   {
      return parent.getScopedClassLoaderDomains();
   }
   
   /** Managed by the top-level aspect manager */
   protected Map getSubDomainsPerClass()
   {
      return parent.getSubDomainsPerClass();
   }

   public void addConstructionInterceptionMarker(String classname)
   {
      parent.addConstructionInterceptionMarker(classname);
   }

   public void addFieldInterceptionMarker(String classname)
   {
      parent.addFieldInterceptionMarker(classname);
   }

   public void skipReference(String classname)
   {
      parent.skipReference(classname);
   }

   public boolean shouldSkipConstruction(String classname)
   {
      return parent.shouldSkipConstruction(classname);
   }

   public boolean shouldSkipFieldAccess(String classname)
   {
      return parent.shouldSkipFieldAccess(classname);
   }

   public void skipConstruction(String classname)
   {
      parent.skipConstruction(classname);
   }

   public void skipFieldAccess(String classname)
   {
      parent.skipFieldAccess(classname);
   }

   public boolean convertReference(String classname)
   {
      return parent.convertReference(classname);
   }

   /** Only set on a per vm basis */
   public ArrayList getExclude()
   {
      return parent.getExclude();
   }

   /** Only set on a per vm basis */
   public ArrayList getInclude()
   {
      return parent.getInclude();
   }

   /** Only set on a per vm basis */
   public ArrayList getIgnore()
   {
      return parent.getIgnore();
   }

   /** Only set on a per vm basis */
   public ClassExpression[] getIgnoreExpressions()
   {
      return parent.getIgnoreExpressions();
   }
   
   public DynamicAOPStrategy getDynamicAOPStrategy()
   {
      return parent.getDynamicAOPStrategy();
   }

   public void setDynamicAOPStrategy(DynamicAOPStrategy strategy)
   {
      parent.setDynamicAOPStrategy(strategy);
   }

   protected void updatePointcutStats(Pointcut pointcut)
   {
      parent.updatePointcutStats(pointcut);
   }

   protected void updateStats(PointcutStats stats)
   {
      parent.updateStats(stats);
   }

   public boolean isExecution()
   {
      return parent.isExecution();
   }

   public boolean isConstruction()
   {
      return parent.isConstruction();
   }

   public boolean isCall()
   {
      return parent.isCall();
   }

   public boolean isWithin()
   {
      return parent.isWithin();
   }

   public boolean isWithincode()
   {
      return parent.isWithincode();
   }

   public boolean isGet()
   {
      return parent.isGet();
   }

   public boolean isSet()
   {
      return parent.isSet();
   }

}
