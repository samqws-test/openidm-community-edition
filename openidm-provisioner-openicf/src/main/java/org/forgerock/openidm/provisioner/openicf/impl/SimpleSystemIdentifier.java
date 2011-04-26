/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * $Id$
 */

package org.forgerock.openidm.provisioner.openicf.impl;

import org.forgerock.json.fluent.JsonNode;
import org.forgerock.json.fluent.JsonNodeException;
import org.forgerock.openidm.provisioner.SystemIdentifier;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * @author $author$
 * @version $Revision$ $Date$
 */
public class SimpleSystemIdentifier implements SystemIdentifier {

    private String name;
    private Pattern pattern;

    public SimpleSystemIdentifier(JsonNode configuration) throws JsonNodeException {
        name = configuration.get("name").expect(String.class).asString();
        pattern = Pattern.compile(".*system/" + name + "/.*");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean is(SystemIdentifier other) {
        return equals(other);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean is(URI uri) {
        return pattern.matcher(uri.toString()).matches();
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimpleSystemIdentifier that = (SimpleSystemIdentifier) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
