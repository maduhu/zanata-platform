/*
 * Copyright 2012, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.hibernate.search;

import org.apache.lucene.search.Filter;
import org.hibernate.search.annotations.Factory;
import org.hibernate.search.annotations.Key;
import org.hibernate.search.filter.FilterKey;
import org.hibernate.search.filter.StandardFilterKey;
import org.zanata.common.LocaleId;

public class TextFlowFilterFactory
{
   private LocaleId locale;

   @Factory
   public Filter getFilter()
   {
      TextFlowFilter filter = new TextFlowFilter();
      filter.setLocale(this.locale);
      return filter;
   }

   /**
    * @return the locale
    */
   public LocaleId getLocale()
   {
      return locale;
   }

   /**
    * @param locale the locale to set
    */
   public void setLocale(LocaleId locale)
   {
      this.locale = locale;
   }

   @Key
   public FilterKey getKey()
   {
      StandardFilterKey key = new StandardFilterKey();
      key.addParameter(locale);
      return key;
   }

}
