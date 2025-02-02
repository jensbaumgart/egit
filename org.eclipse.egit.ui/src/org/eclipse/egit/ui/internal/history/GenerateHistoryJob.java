/*******************************************************************************
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history;

import java.io.IOException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.JobFamilies;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.trace.GitTraceLocation;
import org.eclipse.osgi.util.NLS;

class GenerateHistoryJob extends Job {
	private static final int BATCH_SIZE = 256;

	private final GitHistoryPage page;

	private final SWTCommitList allCommits;

	private int lastUpdateCnt;

	private long lastUpdateAt;

	private boolean trace;

	GenerateHistoryJob(final GitHistoryPage ghp, final SWTCommitList list) {
		super(NLS.bind(UIText.HistoryPage_refreshJob, Activator.getDefault()
				.getRepositoryUtil().getRepositoryName(
						ghp.getInputInternal().getRepository())));
		page = ghp;
		allCommits = list;
		trace = GitTraceLocation.HISTORYVIEW.isActive();
	}

	@Override
	protected IStatus run(final IProgressMonitor monitor) {
		IStatus status = Status.OK_STATUS;
		try {
			if (trace)
				GitTraceLocation.getTrace().traceEntry(
						GitTraceLocation.HISTORYVIEW.getLocation());
			page.setErrorMessage(NLS.bind(
					UIText.GenerateHistoryJob_BuildingListMessage, page
							.getName()));
			try {
				for (;;) {
					final int oldsz = allCommits.size();
					if (trace)
						GitTraceLocation.getTrace().trace(
								GitTraceLocation.HISTORYVIEW.getLocation(),
								"Filling commit list"); //$NON-NLS-1$
					allCommits.fillTo(oldsz + BATCH_SIZE - 1);
					if (monitor.isCanceled() || oldsz == allCommits.size())
						break;

					final long now = System.currentTimeMillis();
					if (now - lastUpdateAt < 2000 && lastUpdateCnt > 0)
						continue;
					updateUI();
					lastUpdateAt = now;
				}
			} catch (IOException e) {
				status = new Status(IStatus.ERROR, Activator.getPluginId(),
						UIText.GenerateHistoryJob_errorComputingHistory, e);
			}

			if (monitor.isCanceled()) {
				page.setErrorMessage(NLS
						.bind(UIText.GenerateHistoryJob_CancelMessage, page
								.getName()));
				return Status.CANCEL_STATUS;
			}
			updateUI();
		} finally {
			monitor.done();
			if (trace)
				GitTraceLocation.getTrace().traceExit(
						GitTraceLocation.HISTORYVIEW.getLocation());
		}
		return status;
	}

	void updateUI() {
		if (trace)
			GitTraceLocation.getTrace().traceEntry(
					GitTraceLocation.HISTORYVIEW.getLocation());
		try {
			if (allCommits.size() == lastUpdateCnt)
				return;

			final SWTCommit[] asArray = new SWTCommit[allCommits.size()];
			allCommits.toArray(asArray);
			page.showCommitList(this, allCommits, asArray);
			lastUpdateCnt = allCommits.size();
		} finally {
			if (trace)
				GitTraceLocation.getTrace().traceExit(
						GitTraceLocation.HISTORYVIEW.getLocation());
		}
	}

	@Override
	public boolean belongsTo(Object family) {
		if (family.equals(JobFamilies.GENERATE_HISTORY))
			return true;
		return super.belongsTo(family);
	}

}
