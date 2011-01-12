/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.tynamo.examples.federatedaccounts.pages;

import org.apache.tapestry5.annotations.Property;
import org.apache.tapestry5.annotations.SessionState;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.services.ApplicationStateManager;
import org.apache.tapestry5.services.ExceptionReporter;
import org.tynamo.examples.federatedaccounts.session.CurrentUser;
import org.tynamo.security.services.SecurityService;

public class Index implements ExceptionReporter {

	@SuppressWarnings("unused")
	@SessionState(create = false)
	@Property
	private CurrentUser currentUser;

	private Throwable exception;

	@Override
	public void reportException(Throwable exception) {
		this.exception = exception;
	}

	public Throwable getException() {
		return exception;
	}

	public String getMessage() {
		if (exception != null) {
			return exception.getMessage() + " Try login.";
		} else {
			return "";
		}
	}

	@Inject
	private ApplicationStateManager applicationStateManager;

	@Inject
	private SecurityService securityService;

	void onActivate() {
		if (securityService.getSubject().isAuthenticated() && !applicationStateManager.exists(CurrentUser.class)) {
			CurrentUser currentUser = applicationStateManager.get(CurrentUser.class);
			currentUser.merge(securityService.getSubject().getPrincipal());
		}

	}
}
