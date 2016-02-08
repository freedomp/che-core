/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/

package org.eclipse.che.api.workspace.server.stack;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.workspace.server.dao.StackDao;
import org.eclipse.che.api.workspace.server.model.impl.stack.StackImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.annotation.PostConstruct;

import static java.lang.String.format;

/**
 * Class for loading list predefined {@link org.eclipse.che.api.workspace.server.model.impl.stack.Stack} to the {@link StackDao}.
 *
 * @author Alexander Andrienko
 */
@Singleton
public class StackLoader {
    private static final Logger LOG = LoggerFactory.getLogger(StackLoader.class);

    private final Path        stackJsonPath;
    private final StackDao    stackDao;
    private final Gson        gson;

    @Inject
    public StackLoader(StackGsonFactory stackGsonFactory,
                       @Named("stack.predefined.list.json") String stacksPath,
                       StackDao stackDao) {
        this.stackJsonPath = Paths.get(stacksPath);
        this.stackDao = stackDao;
        this.gson = stackGsonFactory.getGson();
    }

    /**
     * Load predefined stacks to the {@link StackDao}
     */
    @PostConstruct
    public void start() {
        if (Files.exists(stackJsonPath) && Files.isRegularFile(stackJsonPath)) {
            try(BufferedReader reader = Files.newBufferedReader(stackJsonPath)) {
                List<StackImpl> stacks = gson.fromJson(reader, new TypeToken<List<StackImpl>>() {}.getType());
                stacks.forEach(this::loadStack);
            } catch (IOException e) {
                LOG.error("Failed to store stacks ", e);
            }
        }
    }

    private void loadStack(StackImpl stack) {
        try {
            stackDao.update(stack);
        } catch (NotFoundException | ServerException e) {
            try {
                stackDao.create(stack);
            } catch (Exception ex) {
                LOG.error(format("Failed to load stack with id '%s' ", stack.getId()), ex.getMessage());
            }
        }
    }
}
