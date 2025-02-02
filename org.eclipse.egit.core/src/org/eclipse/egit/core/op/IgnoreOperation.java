/*******************************************************************************
 * Copyright (C) 2009, Alex Blewitt <alex.blewitt@gmail.com>
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.op;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.CoreText;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.Team;

/**
 * IgnoreOperation adds resources to a .gitignore file
 *
 */
public class IgnoreOperation implements IEGitOperation {

	private IResource[] resources;

	private boolean gitignoreOutsideWSChanged;

	private ISchedulingRule schedulingRule;

	/**
	 * construct an IgnoreOperation
	 *
	 * @param resources
	 */
	public IgnoreOperation(IResource[] resources) {
		this.resources = resources;
		gitignoreOutsideWSChanged = false;
		schedulingRule = calcSchedulingRule();
	}

	public void execute(IProgressMonitor monitor) throws CoreException {
		monitor.beginTask(CoreText.IgnoreOperation_taskName, resources.length);
		try {
			for (IResource resource : resources) {
				if (monitor.isCanceled())
					break;
				// TODO This is pretty inefficient; multiple ignores in
				// the same directory cause multiple writes.

				// NB This does the same thing in
				// DecoratableResourceAdapter, but neither currently
				// consult .gitignore

				if (!Team.isIgnoredHint(resource)) {
					addIgnore(monitor, resource);
				}
				monitor.worked(1);
			}
			monitor.done();
		} catch (CoreException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(Activator.error(
					CoreText.IgnoreOperation_error, e));
		}
	}

	/**
	 * @return true if a gitignore file outside the workspace was changed. In
	 *         this case the caller may need to perform manual UI refreshes
	 *         because there was no ResourceChanged event.
	 */
	public boolean isGitignoreOutsideWSChanged() {
		return gitignoreOutsideWSChanged;
	}

	public ISchedulingRule getSchedulingRule() {
		return schedulingRule;
	}

	private void addIgnore(IProgressMonitor monitor, IResource resource)
			throws UnsupportedEncodingException, CoreException {
		IContainer container = resource.getParent();
		String entry = "/" + resource.getName() + "\n"; //$NON-NLS-1$  //$NON-NLS-2$
		ByteArrayInputStream entryBytes = asStream(entry);

		if (container instanceof IWorkspaceRoot) {
			Repository repository = RepositoryMapping.getMapping(
					resource.getProject()).getRepository();
			// .gitignore is not accessible as resource
			IPath gitIgnorePath = resource.getLocation().removeLastSegments(1)
					.append(Constants.GITIGNORE_FILENAME);
			IPath repoPath = new Path(repository.getWorkTree()
					.getAbsolutePath());
			if (!repoPath.isPrefixOf(gitIgnorePath)) {
				String message = NLS.bind(
						CoreText.IgnoreOperation_parentOutsideRepo, resource
								.getLocation().toOSString(), repoPath
								.toOSString());
				IStatus status = Activator.error(message, null);
				throw new CoreException(status);
			}
			File gitIgnore = new File(gitIgnorePath.toOSString());
			updateGitIgnore(gitIgnore, entry);
			// no resource change event when updating .gitignore outside
			// workspace => trigger manual decorator refresh
			gitignoreOutsideWSChanged = true;
		} else {
			IFile gitignore = container.getFile(new Path(
					Constants.GITIGNORE_FILENAME));
			IProgressMonitor subMonitor = new SubProgressMonitor(monitor, 1);
			if (gitignore.exists())
				gitignore.appendContents(entryBytes, true, true, subMonitor);
			else
				gitignore.create(entryBytes, true, subMonitor);
		}
	}

	private void updateGitIgnore(File gitIgnore, String entry)
			throws CoreException {
		try {
			if (!gitIgnore.exists())
				if (!gitIgnore.createNewFile()) {
					String error = NLS.bind(
							CoreText.IgnoreOperation_creatingFailed,
							gitIgnore.getAbsolutePath());
					throw new CoreException(Activator.error(error, null));
				}

			FileOutputStream os = new FileOutputStream(gitIgnore, true);
			try {
				os.write(entry.getBytes());
			} finally {
				os.close();
			}
		} catch (IOException e) {
			String error = NLS.bind(CoreText.IgnoreOperation_updatingFailed,
					gitIgnore.getAbsolutePath());
			throw new CoreException(Activator.error(error, e));
		}
	}

	private ByteArrayInputStream asStream(String entry)
			throws UnsupportedEncodingException {
		return new ByteArrayInputStream(
				entry.getBytes(Constants.CHARACTER_ENCODING));
	}

	private ISchedulingRule calcSchedulingRule() {
		List<ISchedulingRule> rules = new ArrayList<ISchedulingRule>();
		IResourceRuleFactory ruleFactory = ResourcesPlugin.getWorkspace()
				.getRuleFactory();
		for (IResource resource : resources) {
			IContainer container = resource.getParent();
			if (!(container instanceof IWorkspaceRoot)) {
				ISchedulingRule rule = ruleFactory.modifyRule(container);
				if (rule != null)
					rules.add(rule);
			}
		}
		if (rules.size() == 0)
			return null;
		else
			return new MultiRule(rules.toArray(new IResource[rules.size()]));
	}
}
