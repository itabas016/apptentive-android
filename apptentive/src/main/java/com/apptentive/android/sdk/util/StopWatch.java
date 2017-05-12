/*
 * Copyright (c) 2017, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.util;

public class StopWatch {
	private final long startTime;

	public StopWatch() {
		startTime = System.currentTimeMillis();
	}

	public long getElaspedMillis() {
		return System.currentTimeMillis() - startTime;
	}
}
