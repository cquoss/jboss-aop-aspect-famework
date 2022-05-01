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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.jboss.aop.joinpoint.FieldJoinpoint;
import org.jboss.aop.joinpoint.Joinpoint;
import org.jboss.aop.util.MethodHashing;

/**
 * Comment
 *
 * @author <a href="mailto:kabir.khan@jboss.org">Kabir Khan</a>
 * @version $Revision$
 */
public class FieldInfo extends JoinPointInfo
{
   private int index;
   private Field advisedField;
   private Method wrapper;
   private boolean read;
   
   public FieldInfo()
   {
      
   }
   
   public FieldInfo(Class clazz, int index, String fieldName, long wrapperHash, Advisor advisor, boolean read)
   {
      super(advisor);

      try
      {
         this.index = index;
         this.advisedField = clazz.getDeclaredField(fieldName);
         this.wrapper = MethodHashing.findMethodByHash(clazz, wrapperHash);
         this.setAdvisor(advisor);
         this.read = read;
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }
   
   /*
    * For copying
    */
   private FieldInfo(FieldInfo other)
   {
      super(other);
      this.index = other.index;
      this.advisedField = other.advisedField;
      this.wrapper = other.wrapper;
      this.read = other.read;
   }
   
   protected Joinpoint internalGetJoinpoint()
   {
      return new FieldJoinpoint(advisedField);
   }   
   
   public JoinPointInfo copy()
   {
      return new FieldInfo(this);
   }
   
   public String toString()
   {
      StringBuffer sb = new StringBuffer("Field ");
      sb.append(read ? " Read" : "Write");
      sb.append("[");
      sb.append("field=" + advisedField);
      sb.append("]");
      return sb.toString();
   }

   public void setIndex(int index)
   {
      this.index = index;
   }

   public int getIndex()
   {
      return index;
   }

   public void setAdvisedField(Field advisedField)
   {
      this.advisedField = advisedField;
   }

   public Field getAdvisedField()
   {
      return advisedField;
   }

   public void setWrapper(Method wrapper)
   {
      this.wrapper = wrapper;
   }

   public Method getWrapper()
   {
      return wrapper;
   }

   public void setRead(boolean read)
   {
      this.read = read;
   }

   public boolean isRead()
   {
      return read;
   }

}
