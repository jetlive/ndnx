package com.parc.ccn.network;

import java.io.IOException;
import java.util.ArrayList;

import javax.jmdns.ServiceInfo;

import com.parc.ccn.CCNBase;
import com.parc.ccn.Library;
import com.parc.ccn.data.CompleteName;
import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.ContentObject;
import com.parc.ccn.data.query.CCNQueryDescriptor;
import com.parc.ccn.data.query.CCNQueryListener;
import com.parc.ccn.data.security.ContentAuthenticator;
import com.parc.ccn.network.discovery.CCNDiscoveryListener;
import com.parc.ccn.network.impl.JackrabbitCCNRepository;

/**
 * Keep track of all the repositories we know about,
 * route queries and handle synchronization. Have
 * one primary repository for puts. Most clients,
 * and the CCN library, will use this as the "CCN".
 * @author smetters
 *
 */
public class CCNRepositoryManager extends DiscoveryManager implements CCNRepository, CCNDiscoveryListener {
	
	/**
	 * Static singleton.
	 */
	protected static CCNRepositoryManager _repositoryManager = null;
	
	/**
	 * We have one local repository that we create/open
	 * and put data to, also put synchronization data into.
	 * We have many other repositories that we forward
	 * queries to and handle responses from. Right now
	 * we only do one-hop forwarding -- we ask repositories
	 * we discover the queries coming from our user, and
	 * respond to their queries using our primary
	 * repository. We don't currently query our other
	 * local repositories because of legacy security issues
	 * (e.g. they could be our raw filesystem or email), 
	 * or the repositories we've discovered, as that has
	 * routing issues we don't want to get into yet (but
	 * it would be easy to go there to play with routing).
	 * Still have interesting local security issues as
	 * we mirror stuff from local read-only repositories
	 * to the rw repository.
	 */
	protected CCNRepository _primaryRepository = null;
	
	public static CCNRepositoryManager getRepositoryManager() { 
		if (null != _repositoryManager) 
			return _repositoryManager;
		
		return createRepositoryManager(null);
	}
	
	/**
	 * If we've already initialized the RepositoryManager,
	 * ignore any handed-in repository.
	 * @return
	 */
	public static CCNRepositoryManager getRepositoryManager(CCNRepository primaryRepository) { 
		if (null != _repositoryManager) 
			return _repositoryManager;
		
		return createRepositoryManager(primaryRepository);
	}

	protected static synchronized CCNRepositoryManager 
			createRepositoryManager(CCNRepository primaryRepository) {
		if (null == _repositoryManager) {
			_repositoryManager = new CCNRepositoryManager(primaryRepository);
			// Might need to add a thread to handle discovery responses...
			_repositoryManager.start();
		}
		return _repositoryManager;
	}

	/**
	 * Default constructor to make static singleton.
	 * Start with fixed configuration, then worry about
	 * getting fancy...
	 */
	protected CCNRepositoryManager(CCNRepository primaryRepository) {
		super(true, false);
		// We have two ways of managing repositories. We
		// could either assume we own them, and make
		// them, or we could look for ones already running.
		// Which we do depends on the type of the repository.
		// For example, for jackrabbit, we really want all
		// RepositoryManagers on the same machine to talk
		// to the same Jackrabbit. For file-systems based
		// repositories, we could simply make wrappers that
		// talked to the same files (or IMAP server or...).
		// For figuring out what repositories to use,
		// we could use discovery, or we could look for them
		// in appropriate places (e.g. depending on a
		// configuration file).
		
		// Right now we're set up to do discovery, but
		// it isn't a good fit. For Jackrabbit, just see if
		// we can use Jackrabbit's own RMI interface to 
		// talk to an existing one. For other things, we
		// should use a configuration file.
		if (null == primaryRepository)
			_primaryRepository = JackrabbitCCNRepository.getLocalJackrabbitRepository();
		else
			_primaryRepository = primaryRepository;
		_repositories.add(primaryRepository);
	}
	
	/**
	 * Puts we put only to our local repository. 
	 */
	public CompleteName put(ContentName name, ContentAuthenticator authenticator, byte[] content) throws IOException {
		return _primaryRepository.put(name, authenticator, content);
	}

	/**
	 * Gets we collect across all the repositories we know about.
	 * These are immediate gets, returning only what is currently in 
	 * the repository. Query/interest results we get because they
	 * are inserted into the repository by the interest manager.
	 */
	public ArrayList<ContentObject> get(ContentName name, ContentAuthenticator authenticator) throws IOException {
		ArrayList<ContentObject> results = _primaryRepository.get(name, authenticator);
		
		for (int i=0; i < _repositories.size(); ++i) {
			results.addAll(_repositories.get(i).get(name, authenticator));
		}
		return results;
	}

	/**
	 * The rest of CCNBase. Pass it on to the CCNInterestManager to
	 * forward to the network. Also express it to the
	 * repositories we manage.
	 */
	public CCNQueryDescriptor expressInterest(
			ContentName name,
			ContentAuthenticator authenticator,
			CCNQueryListener callbackListener) throws IOException {
		
		return CCNInterestManager.getInterestManager().expressInterest(name, authenticator, callbackListener);
	}
	
	/**
	 * Cancel this query with all the repositories we sent
	 * it to.
	 */
	public void cancelInterest(CCNQueryDescriptor query) throws IOException {
		CCNInterestManager.getInterestManager().cancelInterest(query);
	}

	protected void repositoryAdded(CCNRepository newRepository) {
		// DiscoveryManager handles _repositories list.
		if (!_repositories.contains(newRepository))
			_repositories.add(newRepository);
	}
	
	protected void repositoryRemoved(ServiceInfo repositoryInfo) {
		// DiscoveryManager handles _repositories list.
		if (_primaryRepository.equals(repositoryInfo)) {
			Library.logger().warning("Lost primary repository. Replacing.");
			_primaryRepository = JackrabbitCCNRepository.getLocalJackrabbitRepository();
		}
	}

	public CompleteName addProperty(CompleteName target, String propertyName, byte[] propertyValue) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public CompleteName addProperty(CompleteName target, String propertyName, String propertyValue) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public ArrayList<CompleteName> enumerate(CompleteName name) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public byte[] getByteProperty(CompleteName target, String propertyName) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public ArrayList<ContentObject> getInternal(ContentName name, ContentAuthenticator authenticator) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public String getStringProperty(CompleteName target, String propertyName) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isInternal(CompleteName name) {
		// TODO Auto-generated method stub
		return false;
	}

	public CompleteName putInternal(ContentName name, ContentAuthenticator authenticator, byte[] content) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
}
