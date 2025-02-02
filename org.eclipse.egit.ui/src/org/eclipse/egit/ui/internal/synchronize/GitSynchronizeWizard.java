/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Dariusz Luksza <dariusz@luksza.org>
 *******************************************************************************/
package org.eclipse.egit.ui.internal.synchronize;

import java.io.IOException;
import java.util.Map;

import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Synchronization wizard for Git repositories
 */
public class GitSynchronizeWizard extends Wizard {

	private GitSynchronizeWizardPage page;

	/**
	 * Instantiates a new wizard for synchronizing resources that are being
	 * managed by EGit.
	 */
	public GitSynchronizeWizard() {
		setWindowTitle(UIText.GitSynchronizeWizard_synchronize);
	}

	@Override
	public void addPages() {
		page = new GitSynchronizeWizardPage();
		addPage(page);
	}

	@Override
	public boolean performFinish() {
		GitSynchronizeDataSet gsdSet = new GitSynchronizeDataSet();

		Map<Repository, String> branches = page.getSelectedBranches();
		for (Repository repo : branches.keySet())
			try {
				gsdSet.add(new GitSynchronizeData(repo, Constants.HEAD, branches.get(repo), false));
			} catch (IOException e) {
				Activator.logError(e.getMessage(), e);
			}

		new GitSynchronize(gsdSet);

		return true;
	}

}
