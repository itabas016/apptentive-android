/*
 * Copyright (c) 2017, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.debug;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

public class ApptentiveAttachmentFileProvider extends ContentProvider {

	private static final String CLASS_NAME = ApptentiveAttachmentFileProvider.class.getSimpleName();

	// UriMatcher used to match against incoming requests
	private UriMatcher uriMatcher;

	@Override
	public boolean onCreate() {
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

		// Add a URI to the matcher which will match against the form
		// 'content://it.my.app.ApptentiveAttachmentFileProvider/*'
		// and return 1 in the case that the incoming Uri matches this pattern
		uriMatcher.addURI(getAuthority(getContext()), "*", 1);

		return true;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {

		String LOG_TAG = CLASS_NAME + " - openFile";

		Log.v(LOG_TAG,
				"Called with uri: '" + uri + "'." + uri.getLastPathSegment());

		// Check incoming Uri against the matcher
		switch (uriMatcher.match(uri)) {

			// If it returns 1 - then it matches the Uri defined in onCreate
			case 1:

				// The desired file name is specified by the last segment of the
				// path
				// E.g.
				// 'content://com.apptentive.android.sdk.debug.ApptentiveAttachmentFileProvider/log.txt'
				// Take this and build the path to the file
				String fileLocation = getContext().getCacheDir() + File.separator
						+ uri.getLastPathSegment();

				// Create & return a ParcelFileDescriptor pointing to the file
				// Note: I don't care what mode they ask for - they're only getting
				// read only
				ParcelFileDescriptor pfd = ParcelFileDescriptor.open(new File(
						fileLocation), ParcelFileDescriptor.MODE_READ_ONLY);
				return pfd;

			// Otherwise unrecognised Uri
			default:
				Log.v(LOG_TAG, "Unsupported uri: '" + uri + "'.");
				throw new FileNotFoundException("Unsupported uri: "
						+ uri.toString());
		}
	}

	// //////////////////////////////////////////////////////////////
	// Not supported / used / required for this example
	// //////////////////////////////////////////////////////////////

	@Override
	public int update(Uri uri, ContentValues contentvalues, String s,
										String[] as) {
		return 0;
	}

	@Override
	public int delete(Uri uri, String s, String[] as) {
		return 0;
	}

	@Override
	public Uri insert(Uri uri, ContentValues contentvalues) {
		return null;
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String s, String[] as1,
											String s1) {
		return null;
	}

	/**
	 * The authority is the symbolic name for the provider class
 	 */
	public static String getAuthority(Context context) {
		return context.getApplicationContext().getPackageName() + "." + CLASS_NAME;
	}
}