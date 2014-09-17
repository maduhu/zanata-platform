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
package org.zanata.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.dbunit.operation.DatabaseOperation;
import org.infinispan.manager.CacheContainer;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.zanata.ZanataDbunitJpaTest;
import org.zanata.cache.InfinispanTestCacheContainer;
import org.zanata.common.EntityStatus;
import org.zanata.dao.DocumentDAO;
import org.zanata.dao.LocaleDAO;
import org.zanata.dao.ProjectIterationDAO;
import org.zanata.dao.RawDocumentDAO;
import org.zanata.dao.TextFlowDAO;
import org.zanata.dao.TextFlowTargetDAO;
import org.zanata.file.FileSystemPersistService;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HRawDocument;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowBuilder;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;
import org.zanata.model.HTextFlowTargetReviewComment;
import org.zanata.model.po.HPoTargetHeader;
import org.zanata.seam.SeamAutowire;
import org.zanata.security.ZanataCredentials;
import org.zanata.security.ZanataIdentity;
import com.google.common.collect.Lists;

@Test(groups = { "business-tests" })
public class CopyVersionServiceImplTest extends ZanataDbunitJpaTest {
    private SeamAutowire seam = SeamAutowire.instance();

    @Mock
    private ZanataIdentity identity;

    @Mock
    private ZanataCredentials credentials;

    @Mock
    private FileSystemPersistService fileSystemPersistService;

    private ProjectIterationDAO projectIterationDAO;

    private DocumentDAO documentDAO;

    private TextFlowTargetDAO textFlowTargetDAO;

    private TextFlowDAO textFlowDAO;

    private RawDocumentDAO rawDocumentDAO;

    private CopyVersionServiceImpl service;

    @Override
    protected void prepareDBUnitOperations() {
        beforeTestOperations.add(new DataSetOperation(
                "org/zanata/test/model/ClearAllTables.dbunit.xml",
                DatabaseOperation.CLEAN_INSERT));
        beforeTestOperations.add(new DataSetOperation(
                "org/zanata/test/model/AccountData.dbunit.xml",
                DatabaseOperation.CLEAN_INSERT));
        beforeTestOperations.add(new DataSetOperation(
                "org/zanata/test/model/LocalesData.dbunit.xml",
                DatabaseOperation.CLEAN_INSERT));
        beforeTestOperations.add(new DataSetOperation(
                "org/zanata/test/model/CopyVersionData.dbunit.xml",
                DatabaseOperation.CLEAN_INSERT));
    }

    @BeforeMethod
    protected void beforeMethod() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(identity.getCredentials()).thenReturn(credentials);
        when(credentials.getUsername()).thenReturn("mock user");

        projectIterationDAO = new ProjectIterationDAO(getSession());
        documentDAO = new DocumentDAO(getSession());
        textFlowTargetDAO = new TextFlowTargetDAO(getSession());
        textFlowDAO = new TextFlowDAO(getSession());
        rawDocumentDAO = new RawDocumentDAO((getSession()));

        service = seam.reset()
                .use("projectIterationDAO",
                        projectIterationDAO)
                .use("documentDAO", documentDAO)
                .use("textFlowDAO", textFlowDAO)
                .use("textFlowTargetDAO", textFlowTargetDAO)
                .use("entityManager", getEm())
                .use("session", getSession())
                .use("identity", identity)
                .use("filePersistService", fileSystemPersistService)
                .use("cacheContainer", new InfinispanTestCacheContainer())
                .useImpl(VersionStateCacheImpl.class)
                .useImpl(AsyncTaskManagerServiceImpl.class)
                .ignoreNonResolvable()
                .autowire(CopyVersionServiceImpl.class);
    }

    @Test
    public void testCopyVersionNotExist() {
        String projectSlug = "non-exists-project";
        String versionSlug = "1.0";
        String newVersionSlug = "new-version";
        service.copyVersion(projectSlug, versionSlug, newVersionSlug);
        verifyZeroInteractions(identity);
        verifyZeroInteractions(credentials);
    }

    @Test
    public void testTextFlowBatching() {
        String newVersionSlug = "new-version";
        CopyVersionServiceImpl spyService = spy(service);
        int tfCount = spyService.TF_BATCH_SIZE + 1;

        HDocument existingDoc = getTestDocWithNoTF();
        String existingProjectSlug =
                existingDoc.getProjectIteration().getProject().getSlug();
        String existingVersionSlug =
                existingDoc.getProjectIteration().getSlug();

        createNewVersion(existingProjectSlug, existingVersionSlug,
                newVersionSlug);

        insertTextFlowAndTargetToDoc(existingDoc, tfCount, false);

        spyService.copyVersion(existingProjectSlug, existingVersionSlug,
                newVersionSlug);

        int expectedTfBatchRuns =
                (tfCount / spyService.TF_BATCH_SIZE)
                        + (tfCount % spyService.TF_BATCH_SIZE == 0 ? 0 : 1);

        verify(spyService, times(expectedTfBatchRuns)).copyTextFlowBatch(
                Matchers.eq(existingDoc.getId()), Matchers.anyLong(),
                Matchers.anyInt(), Matchers.anyInt());
    }

    @Test
    public void testTextFlowTargetBatching() {
        String newVersionSlug = "new-version";
        CopyVersionServiceImpl spyService = spy(service);
        HDocument existingDoc = getTestDocWithNoTF();

        String existingProjectSlug =
                existingDoc.getProjectIteration().getProject().getSlug();
        String existingVersionSlug =
                existingDoc.getProjectIteration().getSlug();

        createNewVersion(existingProjectSlug, existingVersionSlug,
                newVersionSlug);

        int tftSize = insertTextFlowAndTargetToDoc(existingDoc, 1, true);

        spyService.copyVersion(existingProjectSlug, existingVersionSlug,
                newVersionSlug);

        int expectedTftBatchRuns =
                (tftSize / spyService.TFT_BATCH_SIZE)
                        + (tftSize % spyService.TF_BATCH_SIZE == 0 ? 0 : 1);
        verify(spyService, times(expectedTftBatchRuns))
                .copyTextFlowTargetBatch(
                        Matchers.anyLong(), Matchers.anyLong(),
                        Matchers.anyInt(), Matchers.anyInt());
    }

    /**
     * Return HDocument that has no TextFlows
     *
     */
    private HDocument getTestDocWithNoTF() {
        // test doc with no tf
        String existingProjectSlug = "sample-project";
        String existingVersionSlug = "3.0";
        String existingDocId = "my/path/document5.txt";

        return documentDAO.getByProjectIterationAndDocId(existingProjectSlug,
                existingVersionSlug, existingDocId);
    }

    private int insertTextFlowAndTargetToDoc(HDocument doc, int tfSize,
            boolean insertTargets) {
        LocaleDAO localeDAO = new LocaleDAO(getSession());
        List<HLocale> localeList = localeDAO.findAll();

        for (int i = 0; i < tfSize; i++) {
            HTextFlow tf =
                    new HTextFlowBuilder().withDocument(doc).withResId(
                            "testResId:" + i)
                            .withSourceContent("source content").build();

            if (insertTargets) {
                for (int x = 0; x < localeList.size(); x++) {
                    HTextFlowTarget tft =
                            new HTextFlowTarget(tf, localeList.get(x));
                    tft.setContents(Lists.newArrayList("test data"));
                    tf.getTargets().put(localeList.get(x).getId(), tft);
                }
            }
            doc.getAllTextFlows().put(tf.getResId(), tf);
        }
        documentDAO.makePersistent(doc);
        documentDAO.flush();
        return localeList.size();
    }

    @Test
    public void testCopyVersion() {
        String projectSlug = "sample-project";
        String versionSlug = "1.0";
        String newVersionSlug = "new-version";
        createNewVersion(projectSlug, versionSlug, newVersionSlug);

        runCopyVersion(projectSlug, versionSlug, newVersionSlug);
    }

    @Test
    public void testCopyVersion2() {
        String projectSlug = "sample-project";
        String versionSlug = "2.0";
        String newVersionSlug = "new-version2";
        createNewVersion(projectSlug, versionSlug, newVersionSlug);

        runCopyVersion(projectSlug, versionSlug, newVersionSlug);
    }

    private void runCopyVersion(String projectSlug, String versionSlug,
            String newVersionSlug) {
        service.copyVersion(projectSlug, versionSlug, newVersionSlug);

        HProjectIteration existingVersion = projectIterationDAO.getBySlug(
                projectSlug, versionSlug);
        HProjectIteration newVersion =
                projectIterationDAO.getBySlug(projectSlug, newVersionSlug);

        assertVersion(existingVersion, newVersion, newVersionSlug,
                existingVersion.getStatus());

        for (Map.Entry<String, HDocument> entry : newVersion.getDocuments()
                .entrySet()) {
            HDocument existingDoc = existingVersion.getDocuments().get(
                    entry.getValue().getDocId());
            HDocument newDoc = entry.getValue();

            assertDocument(existingDoc, newDoc);

            HRawDocument newRawDoc =
                    rawDocumentDAO.getByDocumentId(newDoc.getId());
            HRawDocument oldRawDoc = existingDoc.getRawDocument();
            assertRawDocument(newRawDoc, oldRawDoc);

            assertThat(newDoc.getTextFlows().size()).isEqualTo(
                    existingDoc.getTextFlows().size());

            for (int i = 0; i < existingDoc.getTextFlows().size(); i++) {
                HTextFlow existingTf = existingDoc.getTextFlows().get(i);
                HTextFlow newTf = newDoc.getTextFlows().get(i);

                assertTextFlow(true, existingTf, newTf, newDoc);

                assertThat(newTf.getTargets().size()).isEqualTo(
                        existingTf.getTargets().size());
                for (Map.Entry<Long, HTextFlowTarget> targetSet : existingTf
                        .getTargets().entrySet()) {

                    HTextFlowTarget existingTarget = targetSet.getValue();
                    HTextFlowTarget newTarget = newTf
                            .getTargets().get(targetSet.getKey());

                    assertTextFlowTarget(existingTarget, newTarget, newTf);

                    for (int x = 0; x < existingTarget.getReviewComments()
                            .size(); x++) {
                        HTextFlowTargetReviewComment expectedComment =
                                existingTarget.getReviewComments().get(x);
                        HTextFlowTargetReviewComment newComment =
                                newTarget.getReviewComments().get(x);

                        assertTextFlowTargetHistoryAndReview(expectedComment,
                                newComment, existingTarget, newTarget);
                    }
                }
            }
        }
    }

    private HProjectIteration createNewVersion(String projectSlug,
            String versionSlug, String newVersionSlug) {

        HProjectIteration existingVersion =
                projectIterationDAO.getBySlug(projectSlug, versionSlug);

        HProjectIteration newVersion = new HProjectIteration();
        newVersion.setSlug(newVersionSlug);
        newVersion.setProject(existingVersion.getProject());
        return projectIterationDAO.makePersistent(newVersion);
    }

    @Test
    public void testCopyVersionSettings() {
        String projectSlug = "sample-project";
        String versionSlug = "1.0";
        String newVersionSlug = "new-version";

        HProjectIteration existingVersion =
                projectIterationDAO.getBySlug(projectSlug, versionSlug);

        HProjectIteration newVersion = new HProjectIteration();
        newVersion.setSlug(newVersionSlug);
        newVersion.setProject(existingVersion.getProject());

        newVersion =
                service.copyVersionSettings(existingVersion, newVersion);

        assertVersion(existingVersion, newVersion, newVersionSlug,
                newVersion.getStatus());
    }

    @Test
    public void testCopyDocument() throws Exception {
        HProjectIteration dummyVersion = getDummyVersion("new-version");
        HDocument existingDoc = documentDAO.getById(1L);

        HDocument newDocument =
                service.copyDocument(dummyVersion, existingDoc);

        assertDocument(existingDoc, newDocument);
    }

    @Test
    public void testCopyRawDocument() throws Exception {
        HDocument existingDoc = documentDAO.getById(1L);
        HRawDocument newRawDoc =
                service.copyRawDocument(getDummyDocument(),
                        existingDoc.getRawDocument());

        assertRawDocument(newRawDoc, existingDoc.getRawDocument());
    }

    @Test
    public void testCopyTextFlow() throws Exception {
        HDocument dummyDoc = getDummyDocument();
        HTextFlow existingTextFlow = textFlowDAO.findById(1L);
        HTextFlow newTextFlow =
                service.copyTextFlow(dummyDoc, existingTextFlow);

        assertTextFlow(false, existingTextFlow, newTextFlow, dummyDoc);
    }

    @Test
    public void testCopyTextFlowTarget() throws Exception {
        HTextFlow dummyTextFlow = getDummyTextFlow();
        HTextFlowTarget existingTarget = textFlowTargetDAO.findById(3L, false);

        HTextFlowTarget newTarget =
                service.copyTextFlowTarget(dummyTextFlow, existingTarget);

        assertTextFlowTarget(existingTarget, newTarget, dummyTextFlow);
    }

    private void assertTextFlowTargetHistoryAndReview(
            HTextFlowTargetReviewComment existingComment,
            HTextFlowTargetReviewComment newComment,
            HTextFlowTarget existingTarget, HTextFlowTarget newTarget) {
        assertThat(newComment.getTextFlowTarget()).isNotEqualTo(existingTarget);
        assertThat(newComment.getComment()).isEqualTo(
                existingComment.getComment());
        if (newComment.getCommenter() != null) {
            assertThat(newComment.getCommenter()).isEqualToIgnoringGivenFields(
                    existingComment.getCommenter(), "id");
        }
        assertThat(newComment.getTargetVersion()).isEqualTo(
                newTarget.getVersionNum());

        for (Map.Entry<Integer, HTextFlowTargetHistory> entry : existingTarget
                .getHistory().entrySet()) {
            HTextFlowTargetHistory existingHistory = entry.getValue();
            HTextFlowTargetHistory newHistory =
                    newTarget.getHistory().get(entry.getKey());
            assertThat(newHistory.getTextFlowTarget()).isEqualTo(newTarget);
            assertThat(newHistory.getContents()).isEqualTo(
                    existingHistory.getContents());
            assertThat(newHistory.getLastModifiedBy()).isEqualTo(
                    existingHistory.getLastModifiedBy());
            assertThat(newHistory.getTextFlowRevision()).isEqualTo(
                    existingHistory.getTextFlowRevision());
            assertThat(newHistory.getState()).isEqualTo(
                    existingHistory.getState());
            assertThat(newHistory.getVersionNum()).isEqualTo(
                    existingHistory.getVersionNum());
        }
    }

    private void assertVersion(HProjectIteration existingVersion,
            HProjectIteration newVersion, String newVersionSlug,
            EntityStatus expectedStatus) {
        assertThat(newVersion.getSlug()).isEqualTo(newVersionSlug);
        assertThat(newVersion.getProject()).isEqualTo(
                existingVersion.getProject());
        assertThat(newVersion.isOverrideLocales()).isEqualTo(
                existingVersion.isOverrideLocales());
        assertThat(newVersion.getCustomizedLocales()).isEqualTo(
                existingVersion.getCustomizedLocales());
        assertThat(newVersion.getGroups()).isEqualTo(
                existingVersion.getGroups());
        assertThat(newVersion.getCustomizedValidations()).isEqualTo(
                existingVersion.getCustomizedValidations());
        assertThat(newVersion.getProjectType()).isEqualTo(
                existingVersion.getProjectType());
        assertThat(newVersion.getStatus()).isEqualTo(expectedStatus);
        assertThat(newVersion.getRequireTranslationReview()).isEqualTo(
                existingVersion.getRequireTranslationReview());
    }

    private void assertDocument(HDocument existingDoc, HDocument newDoc) {
        assertThat(newDoc.getProjectIteration()).isNotEqualTo(
                existingDoc.getProjectIteration());

        if (newDoc.getPoHeader() != null
                && existingDoc.getPoHeader() != null) {
            assertThat(newDoc.getPoHeader().getEntries()).isEqualTo(
                    existingDoc.getPoHeader().getEntries());
            if (newDoc.getPoHeader().getComment() != null) {
                assertThat(newDoc.getPoHeader().getComment().getId())
                        .isNotEqualTo(existingDoc.getPoHeader().getComment()
                                .getId());

                assertThat(newDoc.getPoHeader().getComment().getComment())
                        .isEqualTo(existingDoc.getPoHeader().getComment()
                                .getComment());
            }
        }

        for (Map.Entry<HLocale, HPoTargetHeader> entry : newDoc
                .getPoTargetHeaders().entrySet()) {
            HPoTargetHeader oldTargetHeader =
                    existingDoc.getPoTargetHeaders().get(
                            entry.getValue().getTargetLanguage());

            assertThat(entry.getValue().getEntries()).isEqualTo(
                    oldTargetHeader.getEntries());
            assertThat(entry.getValue().getTargetLanguage()).isEqualTo(
                    oldTargetHeader.getTargetLanguage());
            assertThat(entry.getValue().getDocument()).isEqualTo(newDoc);

            if (entry.getValue().getComment() != null) {
                assertThat(entry.getValue().getComment().getId()).isNotEqualTo(
                        oldTargetHeader.getComment().getId());
                assertThat(entry.getValue().getComment().getComment())
                        .isEqualTo(oldTargetHeader.getComment().getComment());
            }
        }
    }

    private void assertRawDocument(HRawDocument newRawDoc,
            HRawDocument existingRawDoc) {
        if (newRawDoc != null && existingRawDoc != null) {
            assertThat(newRawDoc.getContentHash()).isEqualTo(
                    existingRawDoc.getContentHash());
            assertThat(newRawDoc.getType()).isEqualTo(existingRawDoc.getType());
            assertThat(newRawDoc.getUploadedBy()).isEqualTo(
                    existingRawDoc.getUploadedBy());
        }
    }

    private void assertTextFlow(boolean checkPos, HTextFlow existingTextFlow,
            HTextFlow newTextFlow, HDocument doc) {
        assertThat(newTextFlow.getDocument()).isEqualTo(doc);
        assertThat(newTextFlow.getResId()).isEqualTo(
                existingTextFlow.getResId());
        assertThat(newTextFlow.getContents()).containsAll(
                existingTextFlow.getContents());
        assertThat(newTextFlow.isObsolete()).isEqualTo(
                existingTextFlow.isObsolete());
        assertThat(newTextFlow.isPlural()).isEqualTo(
                existingTextFlow.isPlural());
        if (checkPos) {
            assertThat(newTextFlow.getPos()).isEqualTo(
                    existingTextFlow.getPos());
        }
        assertThat(newTextFlow.getRevision()).isEqualTo(
                existingTextFlow.getRevision());

        if (existingTextFlow.getComment() != null) {
            assertThat(newTextFlow.getComment().getId()).isNotEqualTo(
                    existingTextFlow.getComment().getId());

            assertThat(newTextFlow.getComment().getComment()).isEqualTo(
                    existingTextFlow.getComment().getComment());
        }

        if (existingTextFlow.getPotEntryData() != null) {
            assertThat(newTextFlow.getPotEntryData().getContext()).isEqualTo(
                    existingTextFlow.getPotEntryData().getContext());

            assertThat(newTextFlow.getPotEntryData().getFlags()).isEqualTo(
                    existingTextFlow.getPotEntryData().getFlags());

            assertThat(newTextFlow.getPotEntryData().getReferences())
                    .isEqualTo(
                            existingTextFlow.getPotEntryData().getReferences());

            assertThat(newTextFlow.getPotEntryData().getTextFlow()).isEqualTo(
                    newTextFlow);
        }
    }

    private void assertTextFlowTarget(HTextFlowTarget existingTarget,
            HTextFlowTarget newTarget, HTextFlow textFlow) {
        if (newTarget.getTranslator() != null) {
            assertThat(newTarget.getTranslator()).isEqualToIgnoringGivenFields(
                    existingTarget.getTranslator(), "id");
        }

        if (newTarget.getComment() != null) {
            assertThat(newTarget.getComment().getId()).isNotEqualTo(
                    existingTarget.getComment().getId());

            assertThat(newTarget.getComment().getComment()).isEqualTo(
                    existingTarget.getComment().getComment());
        }

        if (newTarget.getReviewer() != null) {
            assertThat(newTarget.getReviewer()).isEqualToIgnoringGivenFields(
                    existingTarget.getReviewer(), "id");
        }
        assertThat(newTarget.getState()).isEqualTo(
                existingTarget.getState());
        assertThat(newTarget.getVersionNum()).isEqualTo(
                existingTarget.getVersionNum());
        assertThat(newTarget.getContents()).isEqualTo(
                existingTarget.getContents());
        assertThat(newTarget.getTextFlow()).isEqualTo(textFlow);
        assertThat(newTarget.getTextFlowRevision()).isEqualTo(
                textFlow.getRevision());
        assertThat(newTarget.getLocale()).isEqualTo(
                existingTarget.getLocale());
        assertThat(newTarget.getLastModifiedBy()).isEqualTo(
                existingTarget.getLastModifiedBy());
        if (existingTarget.getTranslator() != null) {
            assertThat(newTarget.getTranslator()).isEqualToIgnoringGivenFields(
                    existingTarget.getTranslator(), "id");
        }
        if (existingTarget.getReviewer() != null) {
            assertThat(newTarget.getReviewer()).isEqualToIgnoringGivenFields(
                    existingTarget.getReviewer(), "id");
        }

        for (int i = 0; i < existingTarget.getReviewComments().size(); i++) {
            HTextFlowTargetReviewComment expectedComment =
                    existingTarget.getReviewComments().get(i);
            HTextFlowTargetReviewComment newComment =
                    newTarget.getReviewComments().get(i);

            assertTextFlowTargetHistoryAndReview(expectedComment, newComment,
                    existingTarget, newTarget);
        }
    }

    private HProjectIteration getDummyVersion(String newVersionSlug) {
        HProjectIteration dummyVersion = new HProjectIteration();
        dummyVersion.setSlug(newVersionSlug);
        return dummyVersion;
    }

    private HDocument getDummyDocument() {
        HDocument dummyDoc = new HDocument();
        dummyDoc.setDocId("dummy-doc");
        return dummyDoc;
    }

    private HTextFlow getDummyTextFlow() {
        HTextFlow dummyTf = new HTextFlow();
        dummyTf.setContents(Lists.newArrayList("dummy content"));
        dummyTf.setRevision(100);
     return dummyTf;
    }
}
