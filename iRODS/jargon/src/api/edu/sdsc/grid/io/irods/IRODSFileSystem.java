//  Copyright (c) 2008, Regents of the University of California
//  All rights reserved.
//
//  Redistribution and use in source and binary forms, with or without
//  modification, are permitted provided that the following conditions are
//  met:
//
//    * Redistributions of source code must retain the above copyright notice,
//  this list of conditions and the following disclaimer.
//    * Redistributions in binary form must reproduce the above copyright
//  notice, this list of conditions and the following disclaimer in the
//  documentation and/or other materials provided with the distribution.
//    * Neither the name of the University of California, San Diego (UCSD) nor
//  the names of its contributors may be used to endorse or promote products
//  derived from this software without specific prior written permission.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
//  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
//  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
//  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
//  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
//  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
//  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
//  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
//  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
//  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
//
//  FILE
//  IRODSFileSystem.java  -  edu.sdsc.grid.io.irods.IRODSFileSystem
//
//  CLASS HIERARCHY
//  java.lang.Object
//      |
//      +-edu.sdsc.grid.io.GeneralFileSystem
//            |
//            +-edu.sdsc.grid.io.RemoteFileSystem
//                  |
//                  +-edu.sdsc.grid.io.irods.IRODSFileSystem
//
//  PRINCIPAL AUTHOR
//  Lucas Gilbert, SDSC/UCSD
//
//
package edu.sdsc.grid.io.irods;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.ietf.jgss.GSSException;
import org.irods.jargon.core.accessobject.IRODSAccessObjectFactory;
import org.irods.jargon.core.accessobject.IRODSAccessObjectFactoryImpl;
import org.irods.jargon.core.exception.JargonException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.sdsc.grid.io.GeneralAccount;
import edu.sdsc.grid.io.GeneralFileSystem;
import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.Namespace;
import edu.sdsc.grid.io.ProtocolCatalog;
import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.UserMetaData;

/**
 * The IRODSFileSystem class is the class for connection implementations to
 * iRods servers. It provides the framework to support a wide range of iRODS
 * semantics. Specifically, the functions needed to interact with a iRODS
 * server.
 *<P>
 * 
 * @author Lucas Gilbert, San Diego Supercomputer Center
 * @since JARGON2.0
 * @see edu.sdsc.grid.io.irods.IRODSCommands
 */
public class IRODSFileSystem extends RemoteFileSystem {

	/**
	 * The iRODS like Unix only has one root, "/".
	 */
	public static final String IRODS_ROOT = "/";

	static int BUFFER_SIZE = 65535;

	private Logger log = LoggerFactory.getLogger(IRODSFileSystem.class);

	// Add the metadata query attributes
	static {
		if (!ProtocolCatalog.has(new IRODSProtocol()))
			ProtocolCatalog.add(new IRODSProtocol());
	}

	/**
	 * Use this account object instead of the parent class's GeneralAccount
	 * object. Just so you don't have to recast it all the time.
	 */

	/**
	 * All the socket and protocol methods to communicate with the irods server
	 * use this object.
	 */
	protected IRODSCommands commands;

	/**
	 * Opens a socket connection to read from and write to. Loads the default
	 * iRODS user account information from their home directory. The account
	 * information stored in this object cannot be changed once instantiated.
	 *<P>
	 * This constructor is provided for convenience however, it is recommended
	 * that all necessary data be sent to the constructor and not left to the
	 * defaults.
	 * 
	 * @throws FileNotFoundException
	 *             if the user data file cannot be found.
	 * @throws IOException
	 *             if an IOException occurs.
	 */
	public IRODSFileSystem() throws IOException {
		this(new IRODSAccount());
	}

	/**
	 * Opens a socket connection to read from and write to. Opens the account
	 * held in the IRODSAccount object. The account information stored in this
	 * object cannot be changed once constructed.
	 * 
	 * @param irodsAccount
	 *            The iRODS account information object.
	 * @throws NullPointerException
	 *             if IRODSAccount is null.
	 * @throws IOException
	 *             if an IOException occurs.
	 */

	public IRODSFileSystem(IRODSAccount irodsAccount) throws IOException,
			NullPointerException {
		commands = new IRODSCommands();

		try {
			log.info("connecting the commands to the irods socket");
			commands.connect(irodsAccount);
		} catch (JargonException e1) {
			log
					.error(
							"jargon exception, will be rethrown as unchecked exception",
							e1);
			e1.printStackTrace();
			throw new RuntimeException(e1);
		}

		
		// NOTE: factor out...
		// Get the username if they logged in with just a GSSCredential
		irodsAccount = lookupUserIfGSI(irodsAccount);
		setAccount(irodsAccount);
	}

	/**
	 * @param irodsAccount
	 * @return 
	 */
	protected IRODSAccount lookupUserIfGSI(IRODSAccount irodsAccount) {
		if (irodsAccount.getUserName() == null
				|| irodsAccount.getUserName().equals("")) {
			log.info("user logged in with GSI credential");
			MetaDataRecordList[] rl = null;
			try {
				rl = query(
						new MetaDataCondition[] { buildMetaDataConditionForGSIUser(irodsAccount) },
						new MetaDataSelect[] {
								MetaDataSet
										.newSelection(UserMetaData.USER_NAME),
								MetaDataSet
										.newSelection(UserMetaData.USER_ZONE) },
						10);
			} catch (Exception e) {
				IOException x = new IOException();
				x.initCause(e);
				log.warn("IO Exception logged and ignored", x);
			}

			if (rl != null && rl.length > 0) {
				if (log.isDebugEnabled()) {
					try {
					log.debug("setting irods account for GSI user:"
						+ irodsAccount.getGSSCredential().getName().toString());
					} catch (Exception e) {
						// ignore
					}
				}
				irodsAccount.setUserName(rl[0].getStringValue(0));
				irodsAccount.setZone(rl[0].getStringValue(1));
				irodsAccount.setHomeDirectory("/" + irodsAccount.getZone()
						+ "/home/" + irodsAccount.getUserName());
				commands.getIrodsAccount().setUserName(rl[0].getStringValue(0));
				commands.getIrodsAccount().setZone(rl[0].getStringValue(1));
				commands.getIrodsAccount().setHomeDirectory("/" + irodsAccount.getZone()
						+ "/home/" + irodsAccount.getUserName());
			}
		}
		
		if (log.isDebugEnabled()) {
			log.debug("account after GSI checks:" + irodsAccount.toString());
		}
		
		return irodsAccount;
	}

	/**
	 * @param {@link edu.sdsc.grid.io.irods.IRODSAccount IRODSAccount}
	 *        containing the connect information for this file system.
	 * @return (@link edu.sdsc.grid.io.MetaDataCondition MetaDataCondition} that
	 *         contains the correct query for the user dn
	 * @throws GSSException
	 */
	protected MetaDataCondition buildMetaDataConditionForGSIUser(
			IRODSAccount irodsAccount) throws GSSException {
		
		log.info("building a meta data condition for GSI");

		// check the version number and obtain the user dn using alternative
		// metadata values, the rods2.2 version
		// saw a change in the metadata value for user DN from 205 to 1601
		int versionValue = commands.getIrodsServerProperties().getRelVersion().compareTo(
				"rods2.2");
		
		if (log.isDebugEnabled()) {
			log.debug("reported irods version was:" + commands.getIrodsServerProperties().getRelVersion());
		}
		
		if (versionValue < 0) {
			// reported version is less than the 'arguement', or prior to the
			// protocol change
			log.info("reported version is prior to irods 2.2, using USER_DN_2_1");
			return MetaDataSet.newCondition(UserMetaData.USER_DN_2_1,
					MetaDataCondition.EQUAL, irodsAccount.getGSSCredential()
							.getName().toString());

		} else {
			// version is after the rods2.2 cutoff
			log.info("reported version is after the irods 2.2 cutoff, using  USER_DN");
			return MetaDataSet.newCondition(UserMetaData.USER_DN,
					MetaDataCondition.EQUAL, irodsAccount.getGSSCredential()
							.getName().toString());
		}

	}

	/**
	 * Finalizes the object by explicitly letting go of each of its internally
	 * held values.
	 */
	@Override
	protected void finalize() throws Throwable {
		
		if (log.isDebugEnabled()) {
			log.debug("IRODSFileSystem finalizer");
		}
		
		close();

		if (account != null)
			account = null;
		if (commands != null)
			commands = null;
	}

	/**
	 * Loads the account information for this file system.
	 */
	@Override
	protected void setAccount(GeneralAccount account) throws IOException {
		if (account == null)
			account = new IRODSAccount();

		this.account = (IRODSAccount) account.clone();
	}

	/**
	 * Returns the account used by this IRODSFileSystem.
	 */
	@Override
	public GeneralAccount getAccount() throws NullPointerException {
		if (((IRODSAccount) account) != null)
			return (IRODSAccount) account.clone();

		throw new NullPointerException();
	}

	/**
	 * Returns the root directories of the iRODS file system.
	 */
	@Override
	public String[] getRootDirectories() {
		String[] root = { IRODS_ROOT };

		return root;
	}

	/**
	 * Only used by the IRODSFile( uri ) constructor.
	 */
	void setDefaultStorageResource(String resource) {
		((IRODSAccount) account).setDefaultStorageResource(resource);
	}

	/**
	 * @return the default storage resource.
	 */
	public String getDefaultStorageResource() {
		return ((IRODSAccount) account).getDefaultStorageResource();
	}

	/**
	 * @return the options
	 */
	public String getAuthenticationScheme() {
		return ((IRODSAccount) account).getAuthenticationScheme();
	}

	/**
	 * @return the domain name used by the client. Only different from the
	 *         proxyDomainName for ticketed users.
	 */
	public String getServerDN() {
		return ((IRODSAccount) account).getServerDN();
	}

	/**
	 * @return the iRODS version
	 */
	public String getVersion() {
		return IRODSAccount.getVersion();
	}

	/**
	 * @return the version number
	 */
	public float getVersionNumber() {
		return IRODSAccount.getVersionNumber();
	}

	public String getZone() {
		return ((IRODSAccount) account).getZone();
	}

	/**
	 * Proxy Operation that executes a command. The results of the command will
	 * be returned by the InputStream. The protocol of the return value on the
	 * InputStream depends on the command that was run. IRODS will return the
	 * standard out and standard error stream produced on the serverside.
	 * 
	 * @param command
	 *            The command to run.
	 * @param commandArgs
	 *            The command argument string.
	 * 
	 * @return any byte stream output.
	 * @throws IOException
	 *             If an IOException occurs.
	 */
	public InputStream executeProxyCommand(final String command, final String commandArgs)
			throws IOException {
		return commands.executeCommand(command, commandArgs, "");
	}
	
	/**
	 * Proxy Operation that executes a command. The results of the command will
	 * be returned by the InputStream. The protocol of the return value on the
	 * InputStream depends on the command that was run. IRODS will return the
	 * standard out and standard error stream produced on the serverside.
	 * 
	 * @param command
	 *            The command to run.
	 * @param commandArgs
	 *            The command argument string.
	 *  * @param hostName
	 *            The host upon which the command should be run;
	 * 
	 * @return any byte stream output.
	 * @throws IOException
	 *             If an IOException occurs.
	 */
	public InputStream executeProxyCommandWithHostnameSpecified(final String command, final String commandArgs, final String hostName)
			throws IOException {
		return commands.executeCommand(command, commandArgs, hostName);
	}

	/**
	 * Execute a rule and return the results as a <code>HashMap</code> of
	 * <code>String</code> objects. Note that for complex structures (such as an
	 * array of results from an execGenQuery), this method is not particularly
	 * useful.
	 * 
	 * @param ruleStream
	 *            <code>java.io.InputStream</code> containing the rule to be
	 *            executed
	 * @return <code>HashMap<String, String></code> with the result of the rule
	 *         execution
	 * @throws java.io.IOException
	 */
	public HashMap<String, String> executeRule(java.io.InputStream ruleStream)
			throws IOException {
		Parameter[] parameters = Rule.executeRule(this, ruleStream);
		HashMap<String, String> map = new HashMap<String, String>(
				parameters.length);
		for (Parameter p : parameters) {
			map.put(p.getUniqueName(), p.getStringValue());
		}
		return map;
	}

	/**
	 * Execute a rule and return the results as a <code>HashMap</code> of
	 * <code>String</code> objects. Note that for complex structures (such as an
	 * array of results from an execGenQuery), this method is not particularly
	 * useful.
	 * 
	 * @param rule
	 *            <code>String</code> containing the rule to be executed
	 * @return <code>HashMap<String, String></code> with the result of the rule
	 *         execution
	 * @throws java.io.IOException
	 */
	public HashMap<String, String> executeRule(String rule) throws IOException {
		Parameter[] parameters = Rule.executeRule(this, rule);
		HashMap<String, String> map = new HashMap<String, String>(
				parameters.length);
		for (Parameter p : parameters) {
			map.put(p.getUniqueName(), p.getStringValue());
		}
		return map;
	}

	/**
	 * Execute a rule and return the results as a <code>HashMap</code> of
	 * <code>Object</code> objects that can be cast to the appropriate type.
	 * This method is especially useful when dealing with rules that return
	 * String Arrays
	 * 
	 * @param ruleStream
	 *            <code>java.io.InputStream</code> containing the rule to be
	 *            executed
	 * @return <code>HMap<String, String></code> with the result of the rule
	 *         execution
	 * @throws java.io.IOException
	 */
	public Map<String, Object> executeRuleReturnObjects(
			java.io.InputStream ruleStream) throws IOException {
		Parameter[] parameters = Rule.executeRule(this, ruleStream);
		HashMap<String, Object> map = new HashMap<String, Object>(
				parameters.length);
		for (Parameter p : parameters) {
			map.put(p.getUniqueName(), p.getValue());
		}
		return map;
	}

	/**
	 * Execute a rule and return the results as a <code>HashMap</code> of
	 * <code>Object</code> objects that can be cast to the appropriate type.
	 * This method is especially useful when dealing with rules that return
	 * String Arrays
	 * 
	 * @param rule
	 *            <code>String</code> containing the rule to be executed
	 * @return <code>HMap<String, String></code> with the result of the rule
	 *         execution
	 * @throws java.io.IOException
	 */
	public Map<String, Object> executeRuleReturnObjects(String rule)
			throws IOException {
		Parameter[] parameters = Rule.executeRule(this, rule);
		HashMap<String, Object> map = new HashMap<String, Object>(
				parameters.length);
		for (Parameter p : parameters) {
			map.put(p.getUniqueName(), p.getValue());
		}
		return map;
	}

	/*
	 * Bundle file operations. This command allows structured files such as tar
	 * files to be uploaded and downloaded to/from iRods.
	 * 
	 * A tar file containing many small files can be created with normal unix
	 * tar command on the client and then uploaded to the iRods server as a
	 * normal iRods file. The 'ibun -x' command can the be used to extract/untar
	 * the uploaded tar file. The extracted subfiles and subdirectories will
	 * appeared as normal iRods files and sub-collections. The 'ibun -c' command
	 * can be used to tar/bundle an iRods collection into a tar file.
	 * 
	 * For example, to upload a directory mydir to iRods:
	 * 
	 * tar -chlf mydir.tar -C /x/y/z/mydir . iput -Dtar mydir.tar . ibun -x
	 * mydir.tar mydir
	 * 
	 * Note the use of -C option with the tar command which will tar the content
	 * of mydir but without including the directory mydir in the paths. Also,
	 * the -Dtar option is needed with the iput command so that the dataType
	 * 'tar file' is associated with mydir.tar. The 'ibun -x' command extracts
	 * the tar file into the mydir collection. The mydir collection must either
	 * does not exist or is empty.
	 * 
	 * The following command bundles the iRods collection mydir into a tar file:
	 * 
	 * ibun -cDtar mydir1.tar mydir
	 * 
	 * NOTE: To use the tar data type for bundling, the server must be linked
	 * with the libtar library. The link:
	 * https://www.irods.org/index.php/Mounted_iRODS_Collection under the
	 * heading 'Building libtar and linking the iRods servers with libtar' gives
	 * the instructions for installing libtar. Also note that the current
	 * version of libtar 1.2.11 does not support tar file size larger than 2
	 * GBytes. We have made a mod to libtar 1.2.11 so that it can handle files
	 * larger than 2 GBytes. This mod is only needed for building the irods
	 * server software. Please contact all@diceresearch.org for this mod.
	 */
	public void createTarFile(IRODSFile newTarFile, IRODSFile directoryToTar,
			String resource) throws IOException {
		commands.createBundle(newTarFile, directoryToTar, resource);
	}

	/**
	 * Extract the files in a given bundle (tar file) to the given iRODS collection.  
	 * @param tarFile
	 * @param extractLocation
	 * @throws IOException
	 */
	public void extractTarFile(IRODSFile tarFile, IRODSFile extractLocation)
			throws IOException {
		commands.extractBundle(tarFile, extractLocation);
	}
	
	/**
	 * Return an instance of a <code>IRODSAccessObjectFactory</code>.  The factory is an adapted version of the factory in the newer jargon-core API, and should be 
	 * considered a transitional refactoring step.  This factory can be used to obtain various 'access objects' that can assist with interactions with iRODS.  
	 * <p/>
	 * Access objects should not be considered thread-safe, and should not be shared between threads, as they share the same connection
	 * <p/>
	 * In this version of Jargon, all interactions are coded into one large <code>IRODSCommands</code> object.  Introducing a simplified version of access objects into this
	 * version of Jargon will assist in efforts to refactor and reduce the centrality of <code>IRODSCommands</code.
	 * @return {@link IRODSAccessObjectFactory} that can create objects to interact with iRODS.
	 * @throws JargonException
	 */
	public IRODSAccessObjectFactory getIrodsAccessObjectFactory() throws JargonException {
		return IRODSAccessObjectFactoryImpl.instance(commands);
	}

	/**
	 * Tests this filesystem object for equality with the given object. Returns
	 * <code>true</code> if and only if the argument is not <code>null</code>
	 * and both are filesystem objects connected to the same filesystem using
	 * the same account information.
	 * 
	 * @param obj
	 *            The object to be compared with this abstract pathname
	 * 
	 * @return <code>true</code> if and only if the objects are the same;
	 *         <code>false</code> otherwise
	 */
	@Override
	public boolean equals(Object obj) {
		try {
			if (obj == null)
				return false;

			IRODSFileSystem temp = (IRODSFileSystem) obj;

			if (getAccount().equals(temp.getAccount())) {
				if (isConnected() == temp.isConnected()) {
					return true;
				}
			}
		} catch (ClassCastException e) {
			return false;
		}
		return false;
	}

	/**
	 * Checks if the socket is connected.
	 */
	@Override
	public boolean isConnected() {
		return commands.isConnected();
	}

	/**
	 * Returns a string representation of this file system object. The string is
	 * formated according to the iRODS URI model. Note: the user password will
	 * not be included in the URI.
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("irods://");
		builder.append(getUserName());
		builder.append('@');
		builder.append(getHost());
		builder.append(':');
		builder.append(getPort());
		return builder.toString();
	}

	/**
	 * Closes the connection to the iRODS file system. The filesystem cannot be
	 * reconnected after this method is called. If this object, or another
	 * object which uses this filesystem, tries to send a command to the server
	 * a ClosedChannelException will be thrown.
	 */
	public void close() throws IOException {
		log.info("calling close() on irodsCommands");
		try {
			commands.close();
		} catch (JargonException e) {
			log.error("error on close", e);
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns if the connection to iRODS has been closed or not.
	 * 
	 * @return true if the connection has been closed
	 */
	public boolean isClosed() throws IOException {
		return !commands.isConnected();
	}

	/**
	 * Queries the file server to find all files that match the set of
	 * conditions in <code>conditions</code>. For all those that match, the
	 * fields indicated in the <code>selects</code> are returned as a
	 * MetaDataRecordList[].  This will be a 'select distinct' style query.
	 * 
	 * @param conditions
	 *            The conditional statements that describe the values to query
	 *            the server, like WHERE in SQL.
	 * @param selects
	 *            The attributes to be returned from those values that met the
	 *            conditions, like SELECT in SQL.
	 * @param numberOfRecordsWanted
	 *            Maximum number of results of this query that should be
	 *            included in the return value. Default is
	 *            <code>DEFAULT_RECORDS_WANTED</code>. If more results are
	 *            available, they can be obtained using
	 *            <code>MetaDataRecordList.getMoreResults</code>
	 * @return The metadata results from the filesystem, returns
	 *         <code>null</code> if there are no results.
	 */
	@Override
	public MetaDataRecordList[] query(MetaDataCondition[] conditions,
			MetaDataSelect[] selects, int numberOfRecordsWanted)
			throws IOException {
		return query(conditions, selects, numberOfRecordsWanted, true);
	}

	/**
	 * Queries the file server to find all files that match the set of
	 * conditions in <code>conditions</code>. For all those that match, the
	 * fields indicated in the <code>selects</code> are returned as a
	 * MetaDataRecordList[].
	 * 
	 * @param conditions
	 *            The conditional statements that describe the values to query
	 *            the server, like WHERE in SQL.
	 * @param selects
	 *            The attributes to be returned from those values that met the
	 *            conditions, like SELECT in SQL.
	 * @param numberOfRecordsWanted
	 *            Maximum number of results of this query that should be
	 *            included in the return value. Default is
	 *            <code>DEFAULT_RECORDS_WANTED</code>. If more results are
	 *            available, they can be obtained using
	 *            <code>MetaDataRecordList.getMoreResults</code>
	 * @param distinctQuery
	 *            <code>boolean</code> that will cause the query to eith0er
	 *            select 'distinct' or select all. A <code>true</code> value
	 *            will select distinct.
	 * @return The metadata results from the filesystem, returns
	 *         <code>null</code> if there are no results.
	 */
	public MetaDataRecordList[] query(MetaDataCondition[] conditions,
			MetaDataSelect[] selects, int numberOfRecordsWanted, boolean distinctQuery)
			throws IOException {
		return query(conditions, selects, numberOfRecordsWanted, Namespace.FILE, distinctQuery);
	}

	
	
	/**
	 * Queries the file server to find all files that match the set of
	 * conditions in <code>conditions</code>. For all those that match, the
	 * fields indicated in the <code>selects</code> are returned as a
	 * MetaDataRecordList[].
	 * 
	 * @param conditions
	 *            The conditional statements that describe the values to query
	 *            the server, like WHERE in SQL.
	 * @param selects
	 *            The attributes to be returned from those values that met the
	 *            conditions, like SELECT in SQL.
	 * @param namespace
	 *            Defines which namepsace is appropriate when querying the AVU
	 *            metadata of files, directories, resources or users.
	 * @param distinctQuery
	 *            <code>boolean</code> that will cause the query to eith0er
	 *            select 'distinct' or select all. A <code>true</code> value
	 *            will select distinct.
	 * @return The metadata results from the filesystem, returns
	 *         <code>null</code> if there are no results.
	 */
	public MetaDataRecordList[] query(MetaDataCondition[] conditions,
			MetaDataSelect[] selects, Namespace namespace, boolean distinctQuery) throws IOException {
		return query(conditions, selects,
				GeneralFileSystem.DEFAULT_RECORDS_WANTED, namespace, distinctQuery);
	}
	
	/**
	 * Queries the file server to find all files that match the set of
	 * conditions in <code>conditions</code>. For all those that match, the
	 * fields indicated in the <code>selects</code> are returned as a
	 * MetaDataRecordList[].  This will be a 'select distinct' query.
	 * 
	 * @param conditions
	 *            The conditional statements that describe the values to query
	 *            the server, like WHERE in SQL.
	 * @param selects
	 *            The attributes to be returned from those values that met the
	 *            conditions, like SELECT in SQL.
	 * @param namespace
	 *            Defines which namepsace is appropriate when querying the AVU
	 *            metadata of files, directories, resources or users.
	 * @param distinctQuery
	 *            <code>boolean</code> that will cause the query to eith0er
	 *            select 'distinct' or select all. A <code>true</code> value
	 *            will select distinct.
	 * @return The metadata results from the filesystem, returns
	 *         <code>null</code> if there are no results.
	 */
	public MetaDataRecordList[] query(MetaDataCondition[] conditions,
			MetaDataSelect[] selects, Namespace namespace) throws IOException {
		return query(conditions, selects, namespace, true);
	}
	
	/**
	 * Queries the file server to find all files that match the set of
	 * conditions in <code>conditions</code>. For all those that match, the
	 * fields indicated in the <code>selects</code> are returned as a
	 * MetaDataRecordList[].  Defaults to a 'select distinct' style query
	 * 
	 * @param conditions
	 *            The conditional statements that describe the values to query
	 *            the server, like WHERE in SQL.
	 * @param selects
	 *            The attributes to be returned from those values that met the
	 *            conditions, like SELECT in SQL.
	 * @param numberOfRecordsWanted
	 *            Maximum number of results of this query that should be
	 *            included in the return value. Default is
	 *            <code>DEFAULT_RECORDS_WANTED</code>. If more results are
	 *            available, they can be obtained using
	 *            <code>MetaDataRecordList.getMoreResults</code>
	 * @param namespace
	 *            Defines which namepsace is appropriate when querying the AVU
	 *            metadata of files, directories, resources or users.
	 * @return The metadata results from the filesystem, returns
	 *         <code>null</code> if there are no results.
	 */
	public MetaDataRecordList[] query(MetaDataCondition[] conditions,
			MetaDataSelect[] selects, int numberOfRecordsWanted,
			Namespace namespace) throws IOException {
		conditions = (MetaDataCondition[]) cleanNulls(conditions);
		selects = (MetaDataSelect[]) cleanNulls(selects);
		return query(conditions, selects, numberOfRecordsWanted,
				namespace, true);
	}


	/**
	 * Queries the file server to find all files that match the set of
	 * conditions in <code>conditions</code>. For all those that match, the
	 * fields indicated in the <code>selects</code> are returned as a
	 * MetaDataRecordList[].
	 * 
	 * @param conditions
	 *            The conditional statements that describe the values to query
	 *            the server, like WHERE in SQL.
	 * @param selects
	 *            The attributes to be returned from those values that met the
	 *            conditions, like SELECT in SQL.
	 * @param numberOfRecordsWanted
	 *            Maximum number of results of this query that should be
	 *            included in the return value. Default is
	 *            <code>DEFAULT_RECORDS_WANTED</code>. If more results are
	 *            available, they can be obtained using
	 *            <code>MetaDataRecordList.getMoreResults</code>
	 * @param namespace
	 *            Defines which namepsace is appropriate when querying the AVU
	 *            metadata of files, directories, resources or users.
	 * @param distinctQuery
	 *            <code>boolean</code> that will cause the query to eith0er
	 *            select 'distinct' or select all. A <code>true</code> value
	 *            will select distinct.
	 * @return The metadata results from the filesystem, returns
	 *         <code>null</code> if there are no results.
	 */
	public MetaDataRecordList[] query(MetaDataCondition[] conditions,
			MetaDataSelect[] selects, int numberOfRecordsWanted,
			Namespace namespace, boolean distinctQuery) throws IOException {
		conditions = (MetaDataCondition[]) cleanNulls(conditions);
		selects = (MetaDataSelect[]) cleanNulls(selects);
		return commands.query(conditions, selects, numberOfRecordsWanted,
				namespace, distinctQuery);
	}

	/**
	 * Removes null values from an array.
	 */
	static final Object[] cleanNulls(Object[] obj) {
		if (obj == null)
			return null;

		Vector<Object> temp = new Vector<Object>(obj.length);
		boolean add = false;
		int i = 0;

		for (i = 0; i < obj.length; i++) {
			if (obj[i] != null) {
				temp.add(obj[i]);
				if (!add)
					add = true;
			}
		}
		if (!add)
			return null;

		// needs its own check
		if ((obj.length == 1) && (obj[0] == null)) {
			return null;
		}

		return temp.toArray((Object[]) Array.newInstance(
				temp.get(0).getClass(), 0));
	}

	
	/**
	 * Get the basic server info for the connected server.
	 * @return <code>String</code> with basic server info from iRODS.
	 * @throws IOException
	 */
	public String miscServerInfo() throws IOException {
		return commands.miscServerInfo();
	}

	public IRODSCommands getCommands() {
		return commands;
	}
}
