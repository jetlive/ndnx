package org.ccnx.ccn.io;

import java.io.IOException;
import java.sql.Timestamp;

import javax.xml.stream.XMLStreamException;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.impl.security.crypto.ContentKeys;
import org.ccnx.ccn.impl.support.Log;
import org.ccnx.ccn.profiles.SegmentationProfile;
import org.ccnx.ccn.profiles.VersionMissingException;
import org.ccnx.ccn.profiles.VersioningProfile;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.ccnx.ccn.protocol.SignedInfo.ContentType;


/**
 * A CCNInputStream that embodies the Versioning profile. If you
 * ask to open a name that is already versioned, it opens that
 * version for you. If you ask to open a name without a version,
 * it attempts to open the latest version of that name. If you
 * attempt to open a name with a segment marker on it as well,
 * it opens that version of that content at that segment.
 * 
 * The only behavior we have to change from superclass is that
 * involved in getting the first segment -- header or regular segment.
 * We need to make an interest that gets the latest version, and
 * then fills in the version information on the name we
 * are working with, to make sure we continue to get blocks
 * from the same version (even if, say someone writes another
 * version on top of us).
 * 
 * TODO -- outstanding concern -- depending on when the header arrives,
 * the response of this class may differ (not entirely clear). Given
 * that we're moving away from headers, perhaps, this may not be an
 * issue, but it brings up the point that we have to write unit tests
 * that seed ccnd or the repo with potentially complicating data and
 * make sure we can still retrieve it.
 */
public class CCNVersionedInputStream extends CCNInputStream {

	public CCNVersionedInputStream(ContentName name,
			Long startingSegmentNumber, PublisherPublicKeyDigest publisher,
			ContentKeys keys, CCNHandle library)
			throws XMLStreamException, IOException {
		super(name, startingSegmentNumber, publisher, keys, library);
	}

	public CCNVersionedInputStream(ContentName name,
			Long startingSegmentNumber, PublisherPublicKeyDigest publisher,
			CCNHandle library) throws XMLStreamException, IOException {
		super(name, startingSegmentNumber, publisher, library);
	}

	public CCNVersionedInputStream(ContentName name, PublisherPublicKeyDigest publisher,
			CCNHandle library) throws XMLStreamException, IOException {
		super(name, publisher, library);
	}

	public CCNVersionedInputStream(ContentName name) throws XMLStreamException,
			IOException {
		super(name);
	}

	public CCNVersionedInputStream(ContentName name, CCNHandle library)
			throws XMLStreamException, IOException {
		super(name, library);
	}

	public CCNVersionedInputStream(ContentName name, Long startingSegmentNumber)
			throws XMLStreamException, IOException {
		super(name, startingSegmentNumber);
	}

	public CCNVersionedInputStream(ContentObject firstSegment,
			CCNHandle library) throws XMLStreamException, IOException {
		super(firstSegment, library);
	}
	
	@Override
	protected ContentObject getFirstSegment() throws IOException {
		if (VersioningProfile.hasTerminalVersion(_baseName)) {
			// Get exactly this version
			return super.getFirstSegment();
		}
		Log.info("getFirstSegment: getting latest version of " + _baseName);
		ContentObject result = 
			VersioningProfile.getFirstBlockOfLatestVersion(_baseName, _startingSegmentNumber, _publisher, _timeout, this, _library);
		if (null != result){
			Log.info("getFirstSegment: retrieved latest version object " + result.name() + " type: " + result.signedInfo().getTypeName());
			_baseName = result.name().cut(_baseName.count() + 1);
			if (result.signedInfo().getType().equals(ContentType.GONE)) {
				_goneSegment = result;
				Log.info("getFirstSegment: got gone segment: " + _goneSegment.name());
				return null;
			}
		} else {
			Log.info("getFirstSegment: no segment available for latest version of " + _baseName);
		}
		return result;
	}
	
	/**
	 * Version of isFirstSegment that expects names to be versioned, and allows that desiredName
	 * won't know what version it wants but will want some version.
	 */
	public static boolean isVersionedFirstSegment(ContentName desiredName, ContentObject potentialFirstSegment, Long startingSegmentNumber) {
		if ((null != potentialFirstSegment) && (SegmentationProfile.isSegment(potentialFirstSegment.name()))) {
			Log.info("is " + potentialFirstSegment.name() + " a first segment of " + desiredName);
			// In theory, the segment should be at most a versioning component different from desiredName.
			// In the case of complex segmented objects (e.g. a KeyDirectory), where there is a version,
			// then some name components, then a segment, desiredName should contain all of those other
			// name components -- you can't use the usual versioning mechanisms to pull first segment anyway.
			if (!desiredName.isPrefixOf(potentialFirstSegment.name())) {
				Log.info("Desired name :" + desiredName + " is not a prefix of segment: " + potentialFirstSegment.name());
				return false;
			}
			int difflen = potentialFirstSegment.name().count() - desiredName.count();
			if (difflen > 2) {
				Log.info("Have " + difflen + " extra components between " + potentialFirstSegment.name() + " and desired " + desiredName);
				return false;
			}
			// Now need to make sure that if the difference is more than 1, that difference is
			// a version component.
			if ((difflen == 2) && (!VersioningProfile.isVersionComponent(potentialFirstSegment.name().component(potentialFirstSegment.name().count()-2)))) {
				Log.info("The " + difflen + " extra component between " + potentialFirstSegment.name() + " and desired " + desiredName + " is not a version.");
				
			}
			if ((null != startingSegmentNumber) && (SegmentationProfile.baseSegment() != startingSegmentNumber)) {
				return (startingSegmentNumber.equals(SegmentationProfile.getSegmentNumber(potentialFirstSegment.name())));
			} else {
				return SegmentationProfile.isFirstSegment(potentialFirstSegment.name());
			}
		}
		return false;
	}
	
	protected boolean isFirstSegment(ContentName desiredName, ContentObject potentialFirstSegment) {
		return isVersionedFirstSegment(desiredName, potentialFirstSegment, _startingSegmentNumber);
	}
	
	public Timestamp getVersionAsTimestamp() throws VersionMissingException {
		if (null == _baseName)
			throw new VersionMissingException("Have not yet retrieved content name!");
		return VersioningProfile.getLastVersionAsTimestamp(_baseName);
	}
}
