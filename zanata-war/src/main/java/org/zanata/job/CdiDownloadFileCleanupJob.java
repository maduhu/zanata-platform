/*
 * Copyright 2015, Red Hat, Inc. and individual contributors
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
package org.zanata.job;

import java.io.File;

import javax.inject.Inject;

import org.apache.deltaspike.scheduler.api.Scheduled;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.service.FileSystemService;

/**
 * CDI version of background job scheduler. Deltaspike will register this job
 * automatically on bootstrap.
 *
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@Scheduled(cronExpression = "0 0 0 * * ?", startScopes = {})
public class CdiDownloadFileCleanupJob extends JobDetail implements Job {
    private static final Logger log =
            LoggerFactory.getLogger(CdiDownloadFileCleanupJob.class);
    private static final long serialVersionUID = 4401137227756319418L;

    @Inject
    private FileSystemService fileSystemServiceImpl;

    public CdiDownloadFileCleanupJob() {
        // null group name will become default group name
        super("Download File Cleanup", null, CdiDownloadFileCleanupJob.class);
    }

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        log.debug("executing job: {}", this);
        File[] toRemove =
                this.fileSystemServiceImpl.getAllExpiredDownloadFiles();

        // Remove all files that match the filter
        for (File f : toRemove) {
            log.debug("Removing file {}", f.getName());
            f.delete();
        }
    }
}
