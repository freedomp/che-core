/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.projectimport.wizard;

import com.google.gwt.event.shared.EventBus;
import com.google.web.bindery.event.shared.Event;

import org.eclipse.che.api.core.model.workspace.ProjectProblem;
import org.eclipse.che.api.project.gwt.client.ProjectServiceClient;
import org.eclipse.che.api.project.gwt.client.ProjectTypeServiceClient;
import org.eclipse.che.api.project.shared.dto.ProjectTypeDto;
import org.eclipse.che.api.project.shared.dto.SourceEstimation;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.vfs.gwt.client.VfsServiceClient;
import org.eclipse.che.api.vfs.shared.dto.Item;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.SourceStorageDto;
import org.eclipse.che.ide.CoreLocalizationConstant;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.project.type.ProjectTypeRegistry;
import org.eclipse.che.ide.api.project.wizard.ImportProjectNotificationSubscriber;
import org.eclipse.che.ide.api.wizard.Wizard;
import org.eclipse.che.ide.commons.exception.ServerException;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.ide.websocket.rest.RequestCallback;
import org.eclipse.che.test.GwtReflectionUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dmitry Shnurenko
 */
@RunWith(MockitoJUnitRunner.class)
public class ProjectImporterTest {
    private static final String PROJECT_NAME = "project1";
    private static final String ID           = "id";

    @Captor
    private ArgumentCaptor<AsyncRequestCallback<Item>>                   callbackCaptorForItem;
    @Captor
    private ArgumentCaptor<RequestCallback<Void>>                        callbackCaptorForProject;
    @Captor
    private ArgumentCaptor<AsyncRequestCallback<Void>>                   callbackCaptorForVoid;
    @Captor
    private ArgumentCaptor<AsyncRequestCallback<Item>>                   callbackCaptorForItemReference;
    @Captor
    private ArgumentCaptor<AsyncRequestCallback<ProjectConfigDto>>       asyncDescriptorCaptor;
    @Captor
    private ArgumentCaptor<AsyncRequestCallback<List<SourceEstimation>>> estimationCaptor;
    @Captor
    private ArgumentCaptor<Operation<ProjectTypeDto>>                    typeDefinitionCaptor;

    @Mock
    private ProjectServiceClient                projectServiceClient;
    @Mock
    private Promise<ProjectTypeDto>             definitionPromise;
    @Mock
    private AppContext                          appContext;
    @Mock
    private SourceEstimation                    estimation;
    @Mock
    private ProjectTypeDto                      projectTypeDefinition;
    @Mock
    private ProjectConfigDto                    projectConfig;
    @Mock
    private ProjectTypeRegistry                 projectTypeRegistry;
    @Mock
    private ProjectTypeServiceClient            projectTypeServiceClient;
    @Mock
    private VfsServiceClient                    vfsServiceClient;
    @Mock
    private DtoUnmarshallerFactory              dtoUnmarshallerFactory;
    @Mock
    private DtoFactory                          dtoFactory;
    @Mock
    private EventBus                            eventBus;
    @Mock
    private SourceStorageDto                    source;
    @Mock
    private CoreLocalizationConstant            localizationConstant;
    @Mock
    private ImportProjectNotificationSubscriber importProjectNotificationSubscriber;
    @Mock
    private NotificationManager                 notificationManager;

    @Mock
    private Wizard.CompleteCallback completeCallback;

    private ProjectImporter importer;

    @Before
    public void setUp() {
        when(appContext.getWorkspaceId()).thenReturn("id");
        when(projectConfig.getName()).thenReturn(PROJECT_NAME);
        when(projectConfig.getSource()).thenReturn(source);
        when(projectTypeRegistry.getProjectType(anyString())).thenReturn(projectTypeDefinition);
        when(projectTypeDefinition.getId()).thenReturn(ID);

        importer = new ProjectImporter(projectServiceClient,
                                       projectTypeRegistry,
                                       dtoUnmarshallerFactory,
                                       dtoFactory,
                                       vfsServiceClient,
                                       eventBus,
                                       localizationConstant,
                                       importProjectNotificationSubscriber,
                                       appContext);
    }

    @Test
    public void shouldInvokeCallbackWhenFolderAlreadyExists() throws Exception {
        importer.checkFolderExistenceAndImport(completeCallback, projectConfig);

        verify(vfsServiceClient).getItemByPath(anyString(), eq(PROJECT_NAME), callbackCaptorForItem.capture());

        AsyncRequestCallback<Item> callback = callbackCaptorForItem.getValue();
        GwtReflectionUtils.callOnSuccess(callback, mock(Item.class));

        verify(completeCallback).onFailure(any(Throwable.class));
    }

    @Test
    public void shouldImportAndOpenProject() throws Exception {
        when(projectTypeDefinition.isPrimaryable()).thenReturn(true);
        when(projectTypeServiceClient.getProjectType(anyString(), anyString())).thenReturn(definitionPromise);

        importer.checkFolderExistenceAndImport(completeCallback, projectConfig);

        verify(vfsServiceClient).getItemByPath(anyString(), eq(PROJECT_NAME), callbackCaptorForItem.capture());

        callOnSuccessUpdateProject();

        verify(eventBus).fireEvent(Matchers.<Event<Object>>anyObject());
        verify(completeCallback).onCompleted();
    }

    @Test
    public void shouldImportAndOpenProjectForConfiguring() throws Exception {
        ProjectProblem problem = mock(ProjectProblem.class);

        when(projectTypeDefinition.isPrimaryable()).thenReturn(true);
        when(projectConfig.getProblems()).thenReturn(Arrays.asList(problem));
        when(projectTypeServiceClient.getProjectType(anyString(), anyString())).thenReturn(definitionPromise);

        importer.checkFolderExistenceAndImport(completeCallback, projectConfig);

        verify(vfsServiceClient).getItemByPath(anyString(), eq(PROJECT_NAME), callbackCaptorForItem.capture());

        callOnSuccessUpdateProject();

        //first time method is called for create project
        verify(eventBus, times(2)).fireEvent(Matchers.<Event<Object>>anyObject());
    }

    private void callOnSuccessUpdateProject() throws Exception {
        ServerException throwable = mock(ServerException.class);

        when(throwable.getHTTPStatus()).thenReturn(404);
        when(estimation.getType()).thenReturn(ID);
        when(projectConfig.getType()).thenReturn(ID);

        AsyncRequestCallback<Item> itemCallback = callbackCaptorForItem.getValue();
        GwtReflectionUtils.callOnFailure(itemCallback, throwable);

        verify(projectServiceClient).importProject(anyString(),
                                                   eq(PROJECT_NAME),
                                                   eq(false),
                                                   eq(source),
                                                   callbackCaptorForProject.capture());
        GwtReflectionUtils.callOnSuccessVoidParameter(callbackCaptorForProject.getValue());

        verify(projectServiceClient).resolveSources(anyString(), anyString(), estimationCaptor.capture());
        GwtReflectionUtils.callOnSuccess(estimationCaptor.getValue(), Arrays.asList(estimation));

        verify(projectConfig).withType(anyString());

        verify(projectServiceClient).updateProject(anyString(),
                                                   anyString(),
                                                   Matchers.<ProjectConfigDto>anyObject(),
                                                   asyncDescriptorCaptor.capture());
        GwtReflectionUtils.callOnSuccess(asyncDescriptorCaptor.getValue(), projectConfig);
    }
}