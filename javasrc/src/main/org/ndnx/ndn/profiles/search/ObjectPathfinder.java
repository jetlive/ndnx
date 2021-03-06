/*
 * Part of the NDNx Java Library.
 *
 * Portions Copyright (C) 2013 Regents of the University of California.
 * 
 * Based on the CCNx C Library by PARC.
 * Copyright (C) 2010-2012 Palo Alto Research Center, Inc.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation. 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. You should have received
 * a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.ndnx.ndn.profiles.search;

import java.io.IOException;
import java.util.Set;

import org.ndnx.ndn.NDNHandle;
import org.ndnx.ndn.profiles.VersioningProfile;
import org.ndnx.ndn.protocol.ContentName;
import org.ndnx.ndn.protocol.Interest;


/**
 * This subclass of Pathfinder searches for an object with a specific <postfix>
 * along the path from a starting point to a stopping point.
 * Whereas Pathfinder places no restriction on the number or types of components
 * that follow the <postfix>, ObjectPathfinder allows only a version and segment number.
 *
 */

public class ObjectPathfinder extends Pathfinder {

	public ObjectPathfinder(ContentName startingPoint, ContentName stoppingPoint, ContentName desiredPostfix, 
			  boolean closestOnPath, boolean goneOK,
			  int timeout, 
			  Set<ContentName> searchedPathCache,
			  NDNHandle handle) throws IOException {
		super(startingPoint, stoppingPoint, desiredPostfix, closestOnPath, goneOK, timeout, searchedPathCache, handle);
	}
	
	@Override
	protected Interest constructInterest(ContentName searchPoint) {
		ContentName targetName = searchPoint.append(_postfix);
		return VersioningProfile.firstBlockLatestVersionInterest(targetName, null);
	}
	
}
