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

import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.Properties;
import org.codehaus.jackson.map.ObjectMapper;
import org.forgerock.json.fluent.JsonNode;
import org.forgerock.json.fluent.JsonPath;
import org.forgerock.openidm.objset.ForbiddenException;
import org.forgerock.openidm.objset.NotFoundException;
import org.forgerock.openidm.objset.ObjectSetException;
import org.forgerock.openidm.objset.Patch;
import org.forgerock.openidm.provisioner.ProvisionerService;
import org.forgerock.openidm.provisioner.SystemIdentifier;
import org.forgerock.openidm.provisioner.openicf.ConnectorInfoProvider;
import org.forgerock.openidm.provisioner.openicf.ConnectorReference;
import org.forgerock.openidm.provisioner.openicf.OperationHelper;
import org.forgerock.openidm.provisioner.openicf.commons.ConnectorUtil;
import org.forgerock.openidm.provisioner.openicf.commons.OperationOptionInfoHelper;
import org.forgerock.openidm.provisioner.openicf.script.ConnectorScript;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.operations.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author $author$
 * @version $Revision$ $Date$
 */
@Component(name = "org.forgerock.openidm.provisioner.openicf.ProvisionerService", policy = ConfigurationPolicy.REQUIRE,
        description = "OpenIDM System Object Set Service")
@Service
@Properties({
        @Property(name = Constants.SERVICE_VENDOR, value = "ForgeRock AS"),
        @Property(name = Constants.SERVICE_DESCRIPTION, value = "OpenIDM System Object Set Service")
})
public class OpenICFProvisionerService implements ProvisionerService {
    private final static Logger TRACE = LoggerFactory.getLogger(OpenICFProvisionerService.class);
    private ComponentContext context = null;
    private SimpleSystemIdentifier systemIdentifier = null;
    private OperationHelperBuilder operationHelperBuilder = null;

    @Reference(name = "ConnectorInfoProviderServiceReference", referenceInterface = ConnectorInfoProvider.class, bind = "bind", unbind = "unbind", cardinality = ReferenceCardinality.MANDATORY_UNARY, policy = ReferencePolicy.STATIC)
    private ConnectorInfoProvider connectorInfoProvider = null;

    protected void bind(ConnectorInfoProvider connectorInfoProvider) {
        TRACE.info("ConnectorInfoProvider is bound.");
        this.connectorInfoProvider = connectorInfoProvider;

    }

    protected void unbind(ConnectorInfoProvider connectorInfoProvider) {
        connectorInfoProvider = null;
        TRACE.info("ConnectorInfoProvider is unbound.");
    }

    @Activate
    protected void activate(ComponentContext context) {
        this.context = context;
        ConnectorInfo connectorInfo = null;
        JsonNode jsonConfiguration = null;
        ConnectorReference connectorReference = null;
        try {
            jsonConfiguration = getConfiguration(context.getProperties());
            connectorReference = ConnectorUtil.getConnectorReference(jsonConfiguration);
            connectorInfo = connectorInfoProvider.findConnectorInfo(connectorReference);
        } catch (Exception e) {
            TRACE.error("Invalid configuration", e);
            throw new ComponentException("Invalid configuration, service can not be started", e);
        }
        if (null == connectorInfo) {
            throw new ComponentException("ConnectorInfo can not retrieved for " + connectorReference);
        }
        try {
            operationHelperBuilder = new OperationHelperBuilder(jsonConfiguration, connectorInfo.createDefaultAPIConfiguration());
            systemIdentifier = new SimpleSystemIdentifier(jsonConfiguration);
        } catch (Exception e) {
            TRACE.error("Invalid configuration", e);
            throw new ComponentException("Invalid configuration, service can not be started", e);
        }
        TRACE.info("Component is activated.");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        this.context = null;
        this.systemIdentifier = null;
        this.operationHelperBuilder = null;
        TRACE.info("Component is deactivated.");
    }

    /**
     * Gets the unique {@link org.forgerock.openidm.provisioner.SystemIdentifier} of this instance.
     * <p/>
     * The service which refers to this service instance can distinguish between multiple instances by this value.
     *
     * @return
     */
    @Override
    public SystemIdentifier getSystemIdentifier() {
        return systemIdentifier;
    }

    /**
     * Gets a brief stats report about the current status of this service instance.
     * </p/>
     * {@code {
     * "name" : "LDAP",
     * "component.id" : "1",
     * "component.name" : "org.forgerock.openidm.provisioner.openicf.ProvisionerService",
     * "ok" : true
     * }}
     *
     * @return
     */
    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            JsonNode node = new JsonNode(result, new JsonPath("$"));
            node.put("name", systemIdentifier.getName());

            //TODO component.id and component.name can cause problems in JsonPath
            node.put(ComponentConstants.COMPONENT_ID, context.getProperties().get(ComponentConstants.COMPONENT_ID));
            node.put(ComponentConstants.COMPONENT_NAME, context.getProperties().get(ComponentConstants.COMPONENT_NAME));
            ConnectorFacade connectorFacade = getConnectorFacade(operationHelperBuilder.getRuntimeAPIConfiguration());
            connectorFacade.test();
            node.put("ok", true);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Creates a new object in the object set.
     * <p/>
     * This method sets the {@code _id} property to the assigned identifier for the object,
     * and the {@code _rev} property to the revised object version if optimistic concurrency
     * is supported.
     *
     * @param id     the client-generated identifier to use, or {@code null} if server-generated identifier is requested.
     * @param object the contents of the object to create in the object set.
     * @throws org.forgerock.openidm.objset.NotFoundException
     *          if the specified id could not be resolve.
     * @throws org.forgerock.openidm.objset.ForbiddenException
     *          if access to the object or object set is forbidden.
     */
    @Override
    public void create(String id, Map<String, Object> object) throws ObjectSetException {
        String objectClass = getObjectType(id);
        String localId = getLocalId(id);
        OperationHelper helper = operationHelperBuilder.build(objectClass, object);

        if (helper.isOperationPermitted(CreateApiOp.class)) {
            try {
                ConnectorObject connectorObject = helper.build(CreateApiOp.class, object);
                OperationOptionsBuilder operationOptionsBuilder = helper.getOperationOptionsBuilder(CreateApiOp.class, connectorObject, object);
                Uid uid = getConnectorFacade(helper.getRuntimeAPIConfiguration()).create(connectorObject.getObjectClass(), AttributeUtil.filterUid(connectorObject.getAttributes()), operationOptionsBuilder.build());
                helper.resetUid(uid, object);
            } catch (Exception e) {
                TRACE.error("Error at Creating of {}", id, e);
                throw new ObjectSetException(e);
            }
        }
    }

    /**
     * Reads an object from the object set.
     * <p/>
     * The object will contain metadata properties, including object identifier {@code _id},
     * and object version {@code _rev} if optimistic concurrency is supported. If optimistic
     * concurrency is not supported, then {@code _rev} must be absent or {@code null}.
     *
     * @param id the identifier of the object to retrieve from the object set.
     * @return the requested object.
     * @throws org.forgerock.openidm.objset.NotFoundException
     *          if the specified object could not be found.
     * @throws org.forgerock.openidm.objset.ForbiddenException
     *          if access to the object is forbidden.
     */
    @Override
    public Map<String, Object> read(String id) throws ObjectSetException {
        String objectClass = getObjectType(id);
        String localId = getLocalId(id);
        OperationHelper helper = operationHelperBuilder.build(objectClass, null);

        if (helper.isOperationPermitted(GetApiOp.class)) {
            try {
                ConnectorFacade facade = getConnectorFacade(helper.getRuntimeAPIConfiguration());
                ConnectorObject connectorObject = helper.build(CreateApiOp.class, localId, null);
                if (facade.getSupportedOperations().contains(GetApiOp.class)) {
                    try {
                        OperationOptionsBuilder operationOptionsBuilder = helper.getOperationOptionsBuilder(GetApiOp.class, connectorObject, null);
                        connectorObject = facade.getObject(helper.getObjectClass(), new Uid(localId), operationOptionsBuilder.build());
                        return helper.build(connectorObject);
                    } catch (UnsupportedOperationException e) {
                        TRACE.debug("GetApiOp operation is not supported");
                    }
                } else if (facade.getSupportedOperations().contains(SearchApiOp.class)) {
                    try {
                        OperationOptionsBuilder operationOptionsBuilder = helper.getOperationOptionsBuilder(SearchApiOp.class, connectorObject, null);
                        Filter name = new EqualsFilter(new Name(localId));
                        facade.search(helper.getObjectClass(), name, helper.getResultsHandler(), operationOptionsBuilder.build());

                        if (!helper.getQueryResult().isEmpty()) {
                            return helper.getQueryResult().get(0);
                        }
                    } catch (UnsupportedOperationException e) {
                        TRACE.debug("SearchApiOp operation is not supported");
                    }
                }
            } catch (Exception e) {
                TRACE.error("Error at Creating of {}", id, e);
                throw new ObjectSetException(e);
            }
        }
        throw new NotFoundException();
    }

    /**
     * Updates an existing specified object in the object set.
     * <p/>
     * This method updates the {@code _rev} property to the revised object version on update
     * if optimistic concurrency is supported.
     *
     * @param id     the identifier of the object to be updated.
     * @param rev    the version of the object to update; or {@code null} if not provided.
     * @param object the contents of the object to updated in the object set.
     * @throws org.forgerock.openidm.objset.ConflictException
     *          if version is required but is {@code null}.
     * @throws org.forgerock.openidm.objset.ForbiddenException
     *          if access to the object is forbidden.
     * @throws org.forgerock.openidm.objset.NotFoundException
     *          if the specified object could not be found.
     * @throws org.forgerock.openidm.objset.PreconditionFailedException
     *          if version did not match the existing object in the set.
     */
    @Override
    public void update(String id, String rev, Map<String, Object> object) throws ObjectSetException {
        String objectClass = getObjectType(id);
        String localId = getLocalId(id);
        OperationHelper helper = operationHelperBuilder.build(objectClass, object);

        if (helper.isOperationPermitted(UpdateApiOp.class)) {
            try {
                ConnectorObject connectorObject = helper.build(UpdateApiOp.class, localId, object);
                OperationOptionsBuilder operationOptionsBuilder = helper.getOperationOptionsBuilder(UpdateApiOp.class, connectorObject, object);

                Uid uid = getConnectorFacade(helper.getRuntimeAPIConfiguration()).update(connectorObject.getObjectClass(), connectorObject.getUid(), connectorObject.getAttributes(), operationOptionsBuilder.build());
            } catch (Exception e) {
                TRACE.error("Error at Creating of {}", id, e);
                throw new ObjectSetException(e);
            }
        }
    }

    /**
     * Deletes the specified object from the object set.
     *
     * @param id  the identifier of the object to be deleted.
     * @param rev the version of the object to delete or {@code null} if not provided.
     * @throws org.forgerock.openidm.objset.NotFoundException
     *          if the specified object could not be found.
     * @throws org.forgerock.openidm.objset.ForbiddenException
     *          if access to the object is forbidden.
     * @throws org.forgerock.openidm.objset.ConflictException
     *          if version is required but is {@code null}.
     * @throws org.forgerock.openidm.objset.PreconditionFailedException
     *          if version did not match the existing object in the set.
     */
    @Override
    public void delete(String id, String rev) throws ObjectSetException {
        String objectClass = getObjectType(id);
        String localId = getLocalId(id);
        OperationHelper helper = operationHelperBuilder.build(objectClass, null);

        if (helper.isOperationPermitted(DeleteApiOp.class)) {
            try {
                ConnectorObject connectorObject = helper.build(DeleteApiOp.class, localId, null);
                OperationOptionsBuilder operationOptionsBuilder = helper.getOperationOptionsBuilder(DeleteApiOp.class, connectorObject, null);
                getConnectorFacade(helper.getRuntimeAPIConfiguration()).delete(connectorObject.getObjectClass(), connectorObject.getUid(), operationOptionsBuilder.build());
            } catch (Exception e) {
                TRACE.error("Error at Creating of {}", id, e);
                throw new ObjectSetException(e);
            }
        }
    }

    /**
     * Applies a patch (partial change) to the specified object in the object set.
     *
     * @param id    the identifier of the object to be patched.
     * @param rev   the version of the object to patch or {@code null} if not provided.
     * @param patch the partial change to apply to the object.
     * @throws org.forgerock.openidm.objset.ConflictException
     *          if patch could not be applied object state or if version is required.
     * @throws org.forgerock.openidm.objset.ForbiddenException
     *          if access to the object is forbidden.
     * @throws org.forgerock.openidm.objset.NotFoundException
     *          if the specified object could not be found.
     * @throws org.forgerock.openidm.objset.PreconditionFailedException
     *          if version did not match the existing object in the set.
     */
    @Override
    public void patch(String id, String rev, Patch patch) throws ObjectSetException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Performs a query on the specified object and returns the associated results.
     * <p/>
     * Queries are parametric; a set of named parameters is provided as the query criteria.
     * The query result is a JSON object structure composed of basic Java types.
     *
     * @param id     identifies the object to query.
     * @param params the parameters of the query to perform.
     * @return the query results object.
     * @throws org.forgerock.openidm.objset.NotFoundException
     *                                  if the specified object could not be found.
     * @throws org.forgerock.openidm.objset.ForbiddenException
     *                                  if access to the object or specified query is forbidden.
     * @throws IllegalArgumentException
     */
    @Override
    public Map<String, Object> query(String id, Map<String, Object> params) throws ObjectSetException {
        String objectClass = getObjectType(id);
        String localId = getLocalId(id);
        OperationHelper helper = operationHelperBuilder.build(objectClass, null);

        if (helper.isOperationPermitted(SearchApiOp.class)) {
            try {
                ConnectorObject connectorObject = helper.build(SearchApiOp.class, id, null);
                OperationOptionsBuilder operationOptionsBuilder = helper.getOperationOptionsBuilder(SearchApiOp.class, connectorObject, null);
                Filter filter = helper.build(params, params);
                getConnectorFacade(helper.getRuntimeAPIConfiguration()).search(connectorObject.getObjectClass(), filter, helper.getResultsHandler(), operationOptionsBuilder.build());
            } catch (Exception e) {
                TRACE.error("Error at Creating of {}", id, e);
                throw new ObjectSetException(e);
            }
        }
        return null;
    }

    private JsonNode getConfiguration(Dictionary properties) {
        Object config = properties.get("jsonconfig");
        TRACE.debug("JSON Configuration is: {}", config);
        JsonNode result = null;
        if (config instanceof String) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map map = mapper.readValue((String) config, Map.class);
                result = new JsonNode(map);
            } catch (IOException e) {
                TRACE.error("JSON configuration can not be read.", e);
            }
        }
        if (null == result) {
            throw new ComponentException("Required JSON Configuration is missing.");
        }
        return result;
    }

    private ConnectorFacade getConnectorFacade(APIConfiguration runtimeAPIConfiguration) {
        ConnectorFacadeFactory connectorFacadeFactory = ConnectorFacadeFactory.getInstance();
        return connectorFacadeFactory.newInstance(runtimeAPIConfiguration);
    }

    private boolean isOperationPermitted(OperationOptionInfoHelper operation, String operationName) throws ForbiddenException {
        if (null == operation) {
            return true;
        } else if (operation.isDenied() && OperationOptionInfoHelper.OnDenyAction.DO_NOTHING.equals(operation.getOnDeny())) {
            return false;
        }
        throw new ForbiddenException("Operation " + operationName + " is denied");
    }

    // TODO: replace with common utility to handle ID, this is temporary
    private String getLocalId(String id) {
        String localId = null;
        int lastSlashPos = id.lastIndexOf("/");
        if (lastSlashPos > -1) {
            localId = id.substring(id.lastIndexOf("/") + 1);
        }
        TRACE.trace("Full id: {} Extracted local id: {}", id, localId);
        return localId;
    }

    // TODO: replace with common utility to handle ID, this is temporary
    private String getObjectType(String id) {
        String type = null;
        int lastSlashPos = id.lastIndexOf("/");
        if (lastSlashPos > -1 && id.startsWith("/")) {
            int secondLastSlashPos = id.lastIndexOf("/", lastSlashPos - 1);
            type = id.substring(1, lastSlashPos);
            TRACE.trace("Full id: {} Extracted type: {}", id, type);
        }
        return type;
    }


    protected List<Map<String, Object>> doSyncronization(ConnectorFacade connector, Object syncToken, OperationHelper helper) {
        final Collection<SyncDelta> result = new HashSet<SyncDelta>();
        SyncApiOp operation = (SyncApiOp) connector.getOperation(SyncApiOp.class);
        if (null == operation) {
            throw new UnsupportedOperationException();
        }
        //TODO get the persisted token
        SyncToken token = null;
        OperationOptionsBuilder operationOptionsBuilder = null;//helper.getOperationOptionsBuilder(SyncApiOp.class, null, null);
        if (null == syncToken) {
            token = operation.getLatestSyncToken(helper.getObjectClass());
            List<Map<String, Object>> resultList = new ArrayList<Map<String, Object>>(1);
            Map<String, Object> lastToken = new HashMap<String, Object>(1);
            resultList.add(lastToken);
            //@TODO Token serialization problem.
            lastToken.put("token", token.getValue());
            return resultList;
        } else {
            token = new SyncToken(syncToken);
        }
        operation.sync(helper.getObjectClass(), token, helper.getSyncResultsHandler(), operationOptionsBuilder.build());
        return helper.getQueryResult();
    }

    /**
     * @param connector
     * @param script
     * @param operationOptions
     * @throws UnsupportedOperationException
     */
    public void doExecuteScript(ConnectorFacade connector, ConnectorScript script, OperationOptionsBuilder operationOptions) {
        ScriptContext ctxt = new ScriptContext(script.getScriptContextBuilder().getScriptLanguage(), script.getScriptContextBuilder().getScriptText(), script.getScriptContextBuilder().getScriptArguments());
        if ("connector".equals(script.getExecMode())) {
            connector.runScriptOnConnector(ctxt, new OperationOptionsBuilder().build());
        } else if ("resource".equals(script.getExecMode())) {
            connector.runScriptOnResource(ctxt, new OperationOptionsBuilder().build());
        }
    }
}
