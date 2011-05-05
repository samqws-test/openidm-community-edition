/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
 */

package org.forgerock.openidm.action;

/**
 * An exception that may be raised when evaluating an {@link org.forgerock.openidm.action.Action}
 * implementation. Any underlying excretions are wrapped.
 */
public class ActionException extends Exception {

    private final static long serialVersionUID = 1l;

    /**
     * Create a new exception with no massage details.
     */
    public ActionException() {
    }

    /**
     * Create a new exception with message details
     *
     * @param message details
     */
    public ActionException(String message) {
        super(message);
    }

    /**
     * Create a new exception based on the parent cause.
     *
     * @param cause of this exception
     */
    public ActionException(Throwable cause) {
        super(cause);
    }

    /**
     * Create a new exception with message details and the parent cause.
     *
     * @param message details
     * @param cause   of this exception
     */
    public ActionException(String message, Throwable cause) {
        super(message, cause);
    }

}