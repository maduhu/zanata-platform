/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
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
package org.zanata.service;

import org.zanata.adapter.FileFormatAdapter;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

import java.io.InputStream;

/**
 * Provides basic services to transform and process translation files.
 *
 * @author Carlos Munoz <a href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
public interface TranslationFileService
{
   TranslationsResource parseTranslationFile(InputStream fileContents, String fileName);

   Resource parseDocumentFile(InputStream fileContents, String path, String fileName);

   /**
    * Check whether a handler for the given file type is available.
    * 
    * @param fileNameOrExtension full filename with extension, or just extension
    * @return
    */
   boolean hasAdapterFor(String fileNameOrExtension);

   FileFormatAdapter getAdapterFor(String fileNameOrExtension);


   /**
    * Add a document to persistent storage, overwriting any equivalent existing document.
    * 
    * A document is equivalent if it has the same project and version slug, docPath and docName.
    * 
    * @param docContents contents of the document, will be in a closed state when this method completes.
    * @param projectSlug
    * @param iterationSlug
    * @param docPath
    * @param docNameAndExt
    */
   void persistDocument(InputStream docContents, String projectSlug, String iterationSlug, String docPath, String docName);

   boolean hasPersistedDocument(String projectSlug, String iterationSlug, String docPath, String docName);

   /**
    * Stream the contents of a document from persistence.
    * 
    * @param projectSlug
    * @param iterationSlug
    * @param docPath
    * @param docNameAndExt
    * @return the document as an InputStream, or null if no document is in persistence with the given credentials.
    */
   InputStream streamDocument(String projectSlug, String iterationSlug, String docPath, String docName);


}
