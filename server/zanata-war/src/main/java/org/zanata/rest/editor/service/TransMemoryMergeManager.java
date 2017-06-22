/*
 * Copyright 2016, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.zanata.rest.editor.service;

import java.io.Serializable;
import java.util.Objects;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.async.AsyncTaskHandle;
import org.zanata.async.AsyncTaskHandleManager;
import org.zanata.async.GenericAsyncTaskKey;
import org.zanata.async.handle.MergeTranslationsTaskHandle;
import org.zanata.async.handle.TransMemoryMergeTaskHandle;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.VersionTMMerge;
import org.zanata.security.ZanataIdentity;
import org.zanata.service.TransMemoryMergeService;
import org.zanata.webtrans.shared.model.DocumentId;
import org.zanata.webtrans.shared.rest.dto.TransMemoryMergeCancelRequest;
import org.zanata.webtrans.shared.rest.dto.TransMemoryMergeRequest;
import com.google.common.base.MoreObjects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import static org.zanata.async.AsyncTaskKey.joinFields;

/**
 * @author Patrick Huang
 *         <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@Dependent
public class TransMemoryMergeManager implements Serializable {
    private static final Logger log =
            LoggerFactory.getLogger(TransMemoryMergeManager.class);
    private static final long serialVersionUID = 1364316697376958035L;
    private final AsyncTaskHandleManager asyncTaskHandleManager;

    private final TransMemoryMergeService transMemoryMergeService;

    private final ZanataIdentity identity;

    @Inject
    public TransMemoryMergeManager(
            AsyncTaskHandleManager asyncTaskHandleManager,
            TransMemoryMergeService transMemoryMergeService,
            ZanataIdentity identity) {
        this.asyncTaskHandleManager = asyncTaskHandleManager;
        this.transMemoryMergeService = transMemoryMergeService;
        this.identity = identity;
    }

    /**
     * start an async TM merge operation for given request.
     *
     * @param request
     *            REST request for TM merge
     * @return number of untranslated text flows for given document and locale
     * @throws UnsupportedOperationException
     *             if there is already a task running
     */
    public boolean startTransMemoryMerge(TransMemoryMergeRequest request) {
        TMMergeForDocTaskKey key =
                new TMMergeForDocTaskKey(
                        request.documentId, request.localeId);
        AsyncTaskHandle handleByKey =
                asyncTaskHandleManager.getHandleByKey(key);
        if (handleByKey == null || handleByKey.isCancelled()
                || handleByKey.isDone()) {
            TransMemoryMergeTaskHandle handle = new TransMemoryMergeTaskHandle();
            handle.setTriggeredBy(identity.getAccountUsername());
            asyncTaskHandleManager.registerTaskHandle(handle, key);
            transMemoryMergeService.executeMergeAsync(request, handle);
            return true;
        }
        return false;
    }

    public boolean cancelTransMemoryMerge(TransMemoryMergeCancelRequest request) {
        TMMergeForDocTaskKey key =
                new TMMergeForDocTaskKey(
                        request.documentId, request.localeId);
        AsyncTaskHandle handleByKey =
                asyncTaskHandleManager.getHandleByKey(key);
        if (handleByKey != null && !(handleByKey.isDone() || handleByKey.isCancelled())) {
            TransMemoryMergeTaskHandle handle =
                    (TransMemoryMergeTaskHandle) handleByKey;
            String triggeredBy = handle.getTriggeredBy();
            if (Objects.equals(identity.getAccountUsername(), triggeredBy)) {
                handle.cancel(true);
                handle.setCancelledTime(System.currentTimeMillis());
                handle.setCancelledBy(identity.getAccountUsername());
                log.info("task: {} cancelled by its creator", handle);
                return true;
            } else {
                log.warn("{} is attempting to cancel {}", identity.getAccountUsername(), handle);
            }
        }
        return false;
    }

    public boolean start(Long versionId, VersionTMMerge mergeRequest) {
        TransMemoryMergeManager.MergeTranslationTaskKey key =
                new TransMemoryMergeManager.MergeTranslationTaskKey(versionId, mergeRequest.getLocaleId());
        AsyncTaskHandle handleByKey =
                asyncTaskHandleManager.getHandleByKey(key);
        if (handleByKey == null || handleByKey.isCancelled()
                || handleByKey.isDone()) {
            MergeTranslationsTaskHandle handle = new MergeTranslationsTaskHandle();

            handle.setTriggeredBy(identity.getAccountUsername());
            asyncTaskHandleManager.registerTaskHandle(handle, key);
            transMemoryMergeService.startMergeTranslations(versionId,
                    mergeRequest, handle);
            return true;
        }
        return false;
    }

    @SuppressFBWarnings(value = "EQ_DOESNT_OVERRIDE_EQUALS", justification = "super class equals method is sufficient")
    static class TMMergeForDocTaskKey extends
            GenericAsyncTaskKey {

        private static final long serialVersionUID = -7210004008208642L;
        private static final String KEY_NAME = "TMMergeForDocKey";
        private final DocumentId documentId;
        private final LocaleId localeId;

        TMMergeForDocTaskKey(DocumentId documentId, LocaleId localeId) {
            // here we use numeric id to form the string id because it doesn't require URL encoding
            super(joinFields(KEY_NAME, documentId.getId().toString(), localeId.getId()));
            this.documentId = documentId;
            this.localeId = localeId;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("documentId", documentId)
                    .add("localeId", localeId)
                    .toString();
        }
    }

    public static class MergeTranslationTaskKey implements Serializable {

        private static final long serialVersionUID = 5671982177725183233L;
        private final Long versionId;
        private final LocaleId localeId;

        public MergeTranslationTaskKey(Long versionId, LocaleId localeId) {
            this.versionId = versionId;
            this.localeId = localeId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MergeTranslationTaskKey that = (MergeTranslationTaskKey) o;
            return Objects.equals(versionId, that.versionId) &&
                    Objects.equals(localeId, that.localeId);
        }

        @Override
        public int hashCode() {
            return Objects
                    .hash(versionId, localeId);
        }
    }
}
