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
package org.jboss.aop.advice;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.jboss.aop.Advisor;
import org.jboss.aop.AspectManager;
import org.jboss.aop.ClassAdvisor;
import org.jboss.aop.GeneratedClassAdvisor;
import org.jboss.aop.GeneratedInstanceAdvisorMixin;
import org.jboss.aop.InstanceAdvisor;
import org.jboss.aop.joinpoint.FieldJoinpoint;
import org.jboss.aop.joinpoint.Joinpoint;
import org.jboss.aop.pointcut.ast.ASTCFlowExpression;

/**
 * Intermediate class wrapping an interceptor factory, so that generated advisors have 
 * all the information they need about the contained advices for generating the invokation
 * methods, and we don't need to generate the interceptor chains until absolutely needed.
 * Old skool class advisors do not use this class
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 39000 $
 */
public class InterceptorFactoryWrapper 
{
   InterceptorFactory factory;
   volatile Object instance; 
   String cflowString;
   ASTCFlowExpression cflowExpression;
   
   
   public InterceptorFactoryWrapper(
         InterceptorFactory factory, 
         GeneratedClassAdvisor advisor, 
         Joinpoint joinpoint, 
         String cflowString, 
         ASTCFlowExpression cflowExpr)
   {
      this(factory, advisor, joinpoint);
      this.cflowString = cflowString;
      this.cflowExpression = cflowExpr;
   }
   
   public InterceptorFactoryWrapper(InterceptorFactory factory, GeneratedClassAdvisor advisor, Joinpoint joinpoint)
   {
      this.factory = factory;
      
      if (!(factory instanceof GenericInterceptorFactory))
      {
         if (getScope() == Scope.PER_INSTANCE)
         {
            if (!advisor.getPerInstanceAspectDefinitions().contains(factory.getAspect()))
            {
               advisor.addPerInstanceAspect(factory.getAspect());
            }
         }
         else if (getScope() == Scope.PER_JOINPOINT)
         {
            advisor.addPerInstanceJoinpointAspect(joinpoint, factory.getAspect());
         }
         else if (getScope() == Scope.PER_CLASS_JOINPOINT)
         {
            if (advisor.getPerClassJoinpointAspect(factory.getAspect(), joinpoint) == null)
            {
               advisor.addPerClassJoinpointAspect(factory.getAspect(), joinpoint);
            }
         }
      }
   }

   public Interceptor create(Advisor advisor, Joinpoint joinpoint)
   {
      return factory.create(advisor, joinpoint);
   }

   /**
    * Create a new aspect instance to figure out what class it is
    * Also used as a convenience method to create aspect instances for the JoinPointGenerator
    * PER_INSTANCE or PER_JOINPOINT (for non-static fields) aspects cannot be created "properly" 
    * until at runtime, since that requires access to the instance advisor
    */
   public Object getAspect(Advisor advisor, Joinpoint joinpoint)
   {
      if (factory instanceof GenericInterceptorFactory)
      {
         if (instance == null)
         {
            instance = ((GenericInterceptorFactory)factory).create(advisor, joinpoint);
         }
         return instance;
      }
      else if (factory instanceof GeneratedInstanceAdvisorMixin.InstanceInterceptorFactory)
      {
         return ((GeneratedInstanceAdvisorMixin.InstanceInterceptorFactory)factory).create(advisor, joinpoint);
      }
      else if (factory instanceof ScopedInterceptorFactory || factory instanceof AdviceFactory)
      {
         return getAspectInstance(factory.getAspect(), advisor, joinpoint, null);
      }
      
      return null;
   }
   
   public Object getPerInstanceAspect(Advisor advisor, Joinpoint joinpoint, InstanceAdvisor ia)
   {
      if (factory instanceof GenericInterceptorFactory)
      {
         if (instance == null)
         {
            instance = ((GenericInterceptorFactory)factory).create(advisor, joinpoint);
         }
         return instance;
      }
      else if (factory instanceof ScopedInterceptorFactory || factory instanceof AdviceFactory)
      {
         return getAspectInstance(factory.getAspect(), advisor, joinpoint, ia);
      }
      
      return null;
   }
   
   private Object getAspectInstance(AspectDefinition def, Advisor advisor, Joinpoint joinpoint, InstanceAdvisor ia)
   {
      if (def.getScope() == Scope.PER_VM)
      {
         if (instance == null)
         {
            instance = advisor.getManager().getPerVMAspect(def);
         }
         return instance;
      }
      else if (def.getScope() == Scope.PER_CLASS)
      {
         if (instance == null)
         {
            instance = advisor.getPerClassAspect(def);
            if (instance != null)
            {
               return instance;
            }
            advisor.addPerClassAspect(def);
            instance = advisor.getPerClassAspect(def);
         }
         return instance;
      }
      else if (def.getScope() == Scope.PER_INSTANCE)
      {
         return getPerInstanceAspect(def, advisor, joinpoint, ia);
      }
      else if (def.getScope() == Scope.PER_JOINPOINT)
      {
         return getPerJoinPointAspect(def, advisor, joinpoint, ia);
      }
      else if (def.getScope() == Scope.PER_CLASS_JOINPOINT)
      {
         if (instance == null)
         {
            instance = ((GeneratedClassAdvisor)advisor).getPerClassJoinpointAspect(def, joinpoint);
            if (instance != null)
            {
               return instance;
            }
            
            ((GeneratedClassAdvisor)advisor).addPerClassJoinpointAspect(def, joinpoint);
            instance = ((GeneratedClassAdvisor)advisor).getPerClassJoinpointAspect(def, joinpoint);
         }
         return instance;
      }
      else
      {
         //if (aspect.getScope() == null) System.err.println("scope is null: " + aspect.getName() + "." + advice);
      }
      return null;
   }

   private Object getPerJoinPointAspect(AspectDefinition def, Advisor advisor, Joinpoint joinpoint, InstanceAdvisor ia)
   {
      if (ia == null)
      {
         if (instance == null)
         {
            //Used by JoinPointGenerator at code generation time
            if (AspectManager.verbose)
            {
               System.out.println("[info] Calling create on PER_JOINPOINT scoped AspectFactory with no InstanceAdvisor as part of setup");
            }
            
            if (joinpoint instanceof FieldJoinpoint)
            {
               Field field = ((FieldJoinpoint)joinpoint).getField();
               if (Modifier.isStatic(field.getModifiers()))
               {
                  instance = ((ClassAdvisor)advisor).getFieldAspect((FieldJoinpoint)joinpoint, def);
               }
            }

            if (instance == null)
            {
               instance = def.getFactory().createPerJoinpoint(advisor, ia, joinpoint);
            }
         }
         return instance;
      }
      else
      {
         //Used by code generated by JoinPointGenerator at runtime
         return ia.getPerInstanceJoinpointAspect(joinpoint, def);
      }
   }
   
   private Object getPerInstanceAspect(AspectDefinition def, Advisor advisor, Joinpoint joinpoint, InstanceAdvisor ia)
   {
      if (ia == null)
      {
         //Used by JoinPointGenerator at code generation time
         if (AspectManager.verbose)
         {
            System.out.println("[info] Calling create on PER_INSTANCE scoped AspectFactory with no InstanceAdvisor as part of setup");
         }
         return def.getFactory().createPerInstance(advisor, ia);
      }
      else
      {
         return ia.getPerInstanceAspect(def);
      }
   }
   
   public boolean isAspectFactory()
   {
      if (factory instanceof GenericInterceptorFactory || factory instanceof GeneratedInstanceAdvisorMixin.InstanceInterceptorFactory)
      {
         return false;
      }
      else 
      {
         return !(factory.getAspect().getFactory() instanceof GenericAspectFactory);
      }
   }

   public InterceptorFactory getDelegate()
   {
      return factory;
   }
   
   public AspectDefinition getAspect()
   {
      return factory.getAspect();
   }

   public String getName()
   {
      return factory.getName();
   }
   
   public String getAspectClassName()
   {
      if (factory instanceof GenericInterceptorFactory)
      {
         //Dynamically added interceptors
         return ((GenericInterceptorFactory)factory).getClassName();
      }
      else if (factory instanceof GeneratedInstanceAdvisorMixin.InstanceInterceptorFactory)
      {
         return ((GeneratedInstanceAdvisorMixin.InstanceInterceptorFactory)factory).getClassName();
      }
      else 
      {
         AspectFactory af = factory.getAspect().getFactory();
         
         if (af instanceof AspectFactoryDelegator)
         {
            return ((AspectFactoryDelegator)af).getName();
         }
         else
         {
            return ((GenericAspectFactory)af).getName();
         }
      }
   }
   
   public boolean isBefore()
   {
      return factory instanceof BeforeFactory;
   }
   
   public boolean isAfter()
   {
      return factory instanceof AfterFactory;
   }
   
   public boolean isThrowing()
   {
      return factory instanceof ThrowingFactory;
   }
   
   public boolean isAround()
   {
      return !isBefore() && !isAfter() && ! isThrowing(); 
   }
   
   public boolean isInterceptor()
   {
      if (factory instanceof AdviceFactory)
      {
         return false;
      }
      return true;
   }
   
   public String getAdviceName()
   {
      if (factory instanceof AdviceFactory)
      {
         return ((AdviceFactory)factory).getAdvice();
      }
      
      return "invoke";
   }
   
   public Scope getScope()
   {
      if (factory instanceof GenericInterceptorFactory || factory instanceof GeneratedInstanceAdvisorMixin.InstanceInterceptorFactory)
      {
         return null;
      }
      else
      {
         return factory.getAspect().getScope();
      }
   }
   
   public String getRegisteredName()
   {
      if (factory instanceof GenericInterceptorFactory || factory instanceof GeneratedInstanceAdvisorMixin.InstanceInterceptorFactory)
      {
         return null;
      }
      else 
      {
         return factory.getAspect().getName();
      }
   }

   public ASTCFlowExpression getCflowExpression()
   {
      return cflowExpression;
   }

   public String getCFlowString()
   {
      return cflowString;
   }

   public boolean equals(Object obj)
   {
      if (!(obj instanceof InterceptorFactoryWrapper)) return false;
      return this.factory.equals(((InterceptorFactoryWrapper)obj).getDelegate());
   }

   
}
