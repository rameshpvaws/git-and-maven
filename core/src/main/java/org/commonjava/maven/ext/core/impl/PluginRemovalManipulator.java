/*
 * Copyright (C) 2012 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.maven.ext.core.impl;

import java.util.List;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Singleton;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.PluginRemovalState;
import org.commonjava.maven.ext.core.state.PluginState;

/**
 * {@link Manipulator} implementation that can remove specified plugins from a project's pom file.
 * Configuration is stored in a {@link PluginState} instance, which is in turn stored in the
 * {@link ManipulationSession}.
 */
@Named("plugin-removal-manipulator")
@Singleton
public class PluginRemovalManipulator extends BasePluginRemovalManipulator
        implements Manipulator
{
    /**
     * Initialize the {@link PluginState} state holder in the {@link ManipulationSession}. This state holder detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available
     * for later.
     *
     * @param session the session
     */
    @Override
    public void init( final ManipulationSession session )
    {
        this.session = session;
        session.setState( new PluginRemovalState( session.getUserProperties() ) );
    }

    /**
     *
     * @param projects the projects to apply the changes to
     * @return the set of projects with changes
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects )
    {
        return applyChanges( projects, session.getState( PluginRemovalState.class ) );
    }

    /**
     * Determines the order in which manipulators are run, with the lowest number running first.
     * Uses a 100-point scale.
     *
     * @return the execution index for {@code PluginRemovalManipulator} which is 52
     */
    @Override
    public int getExecutionIndex()
    {
        return 52;
    }
}
