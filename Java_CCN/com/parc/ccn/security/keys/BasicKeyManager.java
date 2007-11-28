package com.parc.ccn.security.keys;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import com.parc.ccn.Library;
import com.parc.ccn.config.ConfigurationException;
import com.parc.ccn.config.UserConfiguration;
import com.parc.ccn.data.CompleteName;
import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.security.ContentAuthenticator;
import com.parc.ccn.data.security.KeyLocator;
import com.parc.ccn.data.security.PublisherID;
import com.parc.ccn.network.CCNRepositoryManager;
import com.parc.ccn.security.crypto.certificates.BCX509CertificateGenerator;

public class BasicKeyManager extends KeyManager {
	
	protected KeyStore _keystore = null;
	protected String _defaultAlias = null;
	protected PublisherID _defaultKeyID = null;
	protected X509Certificate _certificate = null;
	protected PrivateKey _privateKey = null;
	protected KeyLocator _keyLocator = null;
	
	public BasicKeyManager() throws ConfigurationException {
		loadKeyStore();
	}
	
	protected void loadKeyStore() throws ConfigurationException {
		File keyStoreFile = new File(UserConfiguration.keystoreFileName());
		if (!keyStoreFile.exists()) {
			_keystore = createKeyStore();	
		}
		if (null == _keystore) {
			KeyStore ks = null;
		    FileInputStream in = null;
			try {
				ks = KeyStore.getInstance(KeyStore.getDefaultType());
				in = new FileInputStream(UserConfiguration.keystoreFileName());
		    	ks.load(in, UserConfiguration.keystorePassword().toCharArray());
			} catch (NoSuchAlgorithmException e) {
				Library.logger().warning("Cannot load empty default keystore.");
				throw new ConfigurationException("Cannot load empty default keystore.");
			} catch (CertificateException e) {
				Library.logger().warning("Cannot load empty default keystore with no certificates.");
				throw new ConfigurationException("Cannot load empty default keystore with no certificates.");
			} catch (FileNotFoundException e) {
				Library.logger().warning("Cannot open existing key store file: " + UserConfiguration.keystoreFileName());
				throw new ConfigurationException("Cannot open existing key store file: " + UserConfiguration.keystoreFileName());
			} catch (IOException e) {
				Library.logger().warning("Cannot open existing key store file: " + UserConfiguration.keystoreFileName() + ": " + e.getMessage());
				throw new ConfigurationException(e);
			} catch (KeyStoreException e) {
				Library.logger().warning("Cannot create instance of default key store type: " + e.getMessage());
				Library.warningStackTrace(e);
				throw new ConfigurationException("Cannot create instance of default key store type: " + e.getMessage());
			} finally {
				if (null != in)
					try {
						in.close();
					} catch (IOException e) {
						Library.logger().warning("IOException closing key store file after load.");
						Library.warningStackTrace(e);
					}
			}
			
		}
	    _defaultAlias = UserConfiguration.defaultKeyAlias();
		KeyStore.PrivateKeyEntry entry = null;
		try {
			entry = (KeyStore.PrivateKeyEntry)_keystore.getEntry(_defaultAlias, new KeyStore.PasswordProtection(UserConfiguration.keystorePassword().toCharArray()));
		    _privateKey = entry.getPrivateKey();
		    _certificate = (X509Certificate)entry.getCertificate();
		    _defaultKeyID = new PublisherID(_certificate.getPublicKey(), false);
		} catch (Exception e) {
			generateConfigurationException("Cannot retrieve default user keystore entry.", e);
		}    
	}
	
	synchronized protected KeyStore createKeyStore() throws ConfigurationException {

		File ccnDir = new File(UserConfiguration.ccnDirectory());
		if (!ccnDir.exists()) {
			if (!ccnDir.mkdirs()) {
				generateConfigurationException("Cannot create user CCN directory: " + ccnDir.getAbsolutePath(), null);
			}
		}
		File keyStoreFile  = new File(UserConfiguration.keystoreFileName());
		if (keyStoreFile.exists())
			return null;
		
		KeyStore ks = null;
	    try {
			ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(null, UserConfiguration.keystorePassword().toCharArray());
		} catch (NoSuchAlgorithmException e) {
			generateConfigurationException("Cannot load empty default keystore.", e);
		} catch (CertificateException e) {
			generateConfigurationException("Cannot load empty default keystore with no certificates.", e);
		} catch (KeyStoreException e) {
			generateConfigurationException("Cannot create instance of default key store type.", e);
		} catch (IOException e) {
			generateConfigurationException("Cannot initialize instance of default key store type.", e);
		}

		KeyPairGenerator kpg = null;;
		try {
			kpg = KeyPairGenerator.getInstance(UserConfiguration.defaultKeyAlgorithm());
		} catch (NoSuchAlgorithmException e) {
			generateConfigurationException("Cannot generate key using default algorithm: " + UserConfiguration.defaultKeyAlgorithm(), e);
		}
		kpg.initialize(UserConfiguration.defaultKeyLength()); 
		KeyPair userKeyPair = kpg.generateKeyPair();
		
		// Generate a self-signed certificate.
		String subjectDN = "CN=" + UserConfiguration.userName();
		X509Certificate ssCert = null;
		try {
			 ssCert = 
				BCX509CertificateGenerator.GenerateX509Certificate(userKeyPair, subjectDN, BCX509CertificateGenerator.MSEC_IN_YEAR);
		} catch (Exception e) {
			generateConfigurationException("InvalidKeyException generating user internal certificate.", e);
		} 

		KeyStore.PrivateKeyEntry entry =
	        new KeyStore.PrivateKeyEntry(userKeyPair.getPrivate(), new X509Certificate[]{ssCert});

	    FileOutputStream out = null;
	    try {
		    ks.setEntry(UserConfiguration.defaultKeyAlias(), entry, 
			        new KeyStore.PasswordProtection(UserConfiguration.keystorePassword().toCharArray()));
	        out = new FileOutputStream(UserConfiguration.keystoreFileName());
	        ks.store(out, UserConfiguration.keystorePassword().toCharArray());
		} catch (NoSuchAlgorithmException e) {
			generateConfigurationException("Cannot save default keystore.", e);
		} catch (CertificateException e) {
			generateConfigurationException("Cannot save default keystore with no certificates.", e);
	    } catch (KeyStoreException e) {
	    	generateConfigurationException("Cannot set private key entry for user default key", e);
		} catch (FileNotFoundException e) {
			generateConfigurationException("Cannot create keystore file: " + UserConfiguration.keystoreFileName(), e);
		} catch (IOException e) {
			generateConfigurationException("Cannot write keystore file: " + UserConfiguration.keystoreFileName(), e);
		} finally {
	        if (out != null) {
	            try {
					out.close();
				} catch (IOException e) {
					Library.logger().warning("IOException closing key store file after load.");
					Library.warningStackTrace(e);
				}
	        }
	    }
		
		// publish a key locator for our use
		// as long as we can re-generate the name,
		// we know it should exist
		// problem -- we need to be able to put
		// even though we aren't done being created yet
		// go through low-level interface
		ContentName keyLocation = new ContentName(UserConfiguration.defaultUserNamespace(), UserConfiguration.defaultKeyName());
		byte [] encodedKey = ssCert.getPublicKey().getEncoded();
		// Need a key locator to stick in data entry for
		// locator. Use key itself.
		KeyLocator locatorLocator = new KeyLocator(ssCert.getPublicKey());
		ContentAuthenticator auth = null;
		try {
			auth = new ContentAuthenticator(keyLocation, 
									 new PublisherID(ssCert.getPublicKey(), false), 
									 ContentAuthenticator.ContentType.LEAF,
									 encodedKey,
									 locatorLocator,
									 userKeyPair.getPrivate());
		} catch (Exception e) {
			generateConfigurationException("Exception generating key locator for (and using) default user key.", e);
		}
		try {
			CompleteName publishedLocation = 
				CCNRepositoryManager.getCCNRepositoryManager().put(keyLocation, auth, encodedKey);
			Library.logger().info("Generated user default key. Published key locator as: " + publishedLocation.name());
		} catch (IOException e) {
			generateConfigurationException("Cannot put key locator for default key.", e);
		}
		
		return ks;
	}

	protected void generateConfigurationException(String message, Exception e) throws ConfigurationException {
		Library.logger().warning(message + " " + e.getClass().getName() + ": " + e.getMessage());
		Library.warningStackTrace(e);
		throw new ConfigurationException(message, e);
	}

	public PublisherID getDefaultKeyID() {
		return _defaultKeyID;
	}

	public PublicKey getDefaultPublicKey() {
		return _certificate.getPublicKey();
	}
	
	public PrivateKey getDefaultSigningKey() {
		return _privateKey;
	}

	public KeyLocator getKeyLocator(PrivateKey signingKey) {
		if (null == _keyLocator) {
			ContentName keyLocation = new ContentName(UserConfiguration.defaultUserNamespace(), UserConfiguration.defaultKeyName());
			_keyLocator = new KeyLocator(keyLocation);
		}
		return _keyLocator;
	}

	public PublicKey getPublicKey(String alias) {
		// TODO Auto-generated method stub
		return null;
	}

	public PublicKey getPublicKey(PublisherID publisher) {
		// TODO Auto-generated method stub
		return null;
	}

	public PrivateKey getSigningKey(String alias) {
		// TODO Auto-generated method stub
		return null;
	}

	public PrivateKey getSigningKey(PublisherID publisher) {
		// TODO Auto-generated method stub
		return null;
	}

}
