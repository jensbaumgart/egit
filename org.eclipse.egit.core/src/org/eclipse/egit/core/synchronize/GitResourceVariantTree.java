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
package org.eclipse.egit.core.synchronize;

import static org.eclipse.jgit.lib.ObjectId.zeroId;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.egit.core.CoreText;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.NotIgnoredFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.variants.IResourceVariant;
import org.eclipse.team.core.variants.ResourceVariantByteStore;
import org.eclipse.team.core.variants.ResourceVariantTree;

abstract class GitResourceVariantTree extends ResourceVariantTree {

	private final GitSynchronizeDataSet gsds;

	GitResourceVariantTree(ResourceVariantByteStore store,
			GitSynchronizeDataSet gsds) {
		super(store);
		this.gsds = gsds;
	}

	public IResource[] roots() {
		Set<IResource> roots = new HashSet<IResource>();
		for (GitSynchronizeData gsd : gsds)
			roots.addAll(gsd.getProjects());

		return roots.toArray(new IResource[roots.size()]);
	}

	@Override
	protected IResourceVariant fetchVariant(IResource resource, int depth,
			IProgressMonitor monitor) throws TeamException {
		SubMonitor subMonitor = SubMonitor.convert(monitor);
		if (resource == null || resource.getLocation() == null) {
			subMonitor.done();
			return null;
		}

		subMonitor.beginTask(NLS.bind(
				CoreText.GitResourceVariantTree_fetchingVariant,
				resource.getName()), IProgressMonitor.UNKNOWN);
		try {
			return fetchVariant(resource, subMonitor);
		} finally {
			subMonitor.done();
		}
	}

	private IResourceVariant fetchVariant(IResource resource,
			IProgressMonitor monitor) throws TeamException {
		GitSynchronizeData gsd = gsds.getData(resource.getProject());
		if (gsd == null)
			return null;

		Repository repo = gsd.getRepository();
		String path = getPath(resource, repo);
		RevCommit revCommit = getRevCommit(gsd);
		if (revCommit == null)
			return null;

		if (path.length() == 0)
			return handleRepositoryRoot(resource, repo, revCommit);

		try {
			if (monitor.isCanceled())
				throw new OperationCanceledException();

			TreeWalk tw = initializeTreeWalk(repo, path);

			int nth = tw.addTree(revCommit.getTree());
			if (resource.getType() == IResource.FILE) {
				tw.setRecursive(true);
				if (tw.next() && !tw.getObjectId(nth).equals(zeroId()))
					return new GitBlobResourceVariant(repo, revCommit,
							tw.getObjectId(nth), path);
			} else {
				while (tw.next() && !path.equals(tw.getPathString())) {
					if (monitor.isCanceled())
						throw new OperationCanceledException();

					if (tw.isSubtree())
						tw.enterSubtree();
				}

				ObjectId objectId = tw.getObjectId(nth);
				if (!objectId.equals(zeroId()))
					return new GitFolderResourceVariant(repo, revCommit, objectId, path);
			}
		} catch (IOException e) {
			throw new TeamException(
					NLS.bind(
							CoreText.GitResourceVariantTree_couldNotFindResourceVariant,
							resource), e);
		}

		return null;
	}

	@Override
	protected IResourceVariant[] fetchMembers(IResourceVariant variant,
			IProgressMonitor progress) throws TeamException {
		if (variant == null || !(variant instanceof GitFolderResourceVariant))
			return new IResourceVariant[0];

		GitFolderResourceVariant gitVariant = (GitFolderResourceVariant) variant;

		try {
			return gitVariant.getMembers(progress);
		} catch (IOException e) {
			throw new TeamException(NLS.bind(
					CoreText.GitResourceVariantTree_couldNotFetchMembers,
					gitVariant), e);
		}
	}

	public IResourceVariant getResourceVariant(final IResource resource)
			throws TeamException {
		return fetchVariant(resource, 0, null);
	}

	/**
	 *
	 * @param gsd
	 * @return instance of {@link RevTree} for given {@link GitSynchronizeData}
	 *         or <code>null</code> if rev tree was not found
	 * @throws TeamException
	 */
	protected abstract RevCommit getRevCommit(GitSynchronizeData gsd)
			throws TeamException;

	private IResourceVariant handleRepositoryRoot(final IResource resource,
			Repository repo, RevCommit revCommit) throws TeamException {
		try {
			return new GitFolderResourceVariant(repo, revCommit,
					revCommit.getTree(), resource.getLocation().toString());
		} catch (IOException e) {
			throw new TeamException(
					NLS.bind(
							CoreText.GitResourceVariantTree_couldNotFindResourceVariant,
							resource), e);
		}
	}

	private TreeWalk initializeTreeWalk(Repository repo, String path)
			throws CorruptObjectException {
		TreeWalk tw = new TreeWalk(repo);
		tw.reset();
		int ignoreNth = tw.addTree(new FileTreeIterator(repo));

		TreeFilter pathFilter = PathFilter.create(path);
		TreeFilter ignoreFilter = new NotIgnoredFilter(ignoreNth);
		tw.setFilter(AndTreeFilter.create(pathFilter, ignoreFilter));

		return tw;
	}

	private String getPath(final IResource resource, Repository repo) {
		return Repository.stripWorkDir(repo.getWorkTree(), resource
				.getLocation().toFile());
	}

}
