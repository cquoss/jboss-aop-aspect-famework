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

import java.lang.ref.WeakReference;

import org.jboss.aop.Advisor;
import org.jboss.aop.AspectManager;
import org.jboss.aop.GeneratedAdvisorDomain;

/**
 * The domain used by the container proxies
 * 
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 43307 $
 */
public class ProxyAdvisorDomain extends GeneratedAdvisorDomain
{
   protected WeakReference advisor;

   public ProxyAdvisorDomain(AspectManager manager, Class clazz, boolean parentFirst)
   {
      this(manager, clazz, parentFirst, false);
   }
   
   public ProxyAdvisorDomain(AspectManager manager, Class clazz, boolean parentFirst, boolean instanceDomain)
   {
      // FIXME ProxyAdvisorDomain constructor
      super(manager, clazz, parentFirst, instanceDomain);
      super.setInheritsBindings(true);
      super.setInheritsDeclarations(true);

   }

   public Advisor getAdvisor()
   {
      return (Advisor)advisor.get();
   }

   public void setAdvisor(Advisor advisor)
   {
      this.advisor = new WeakReference(advisor);
   }

   public void attachAdvisor()
   {
      Advisor adv = (Advisor)advisor.get();
      advisors.put(adv.getClazz(), new WeakReference(adv));
   }
   
   public void removeAdvisor(Advisor advisor)
   {
      advisors.remove(advisor.getClazz());
   }
   
}
