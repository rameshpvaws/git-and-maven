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

import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.ProfileRemovalState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.commonjava.maven.ext.core.util.IdUtils.ga;

/**
 * {@link Manipulator} implementation remove profile(s) from the project's pom file. Configuration is stored in a
 * {@link ProfileRemovalState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named("profile-removal")
@Singleton
public class ProfileRemovalManipulator
    implements Manipulator
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private ManipulationSession session;

    /**
     * Initialize the {@link ProfileRemovalState} state holder in the {@link ManipulationSession}. This state holder detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available for
     * later.
     */
    @Override
    public void init( final ManipulationSession session )
    {
        this.session = session;
        session.setState( new ProfileRemovalState( session.getUserProperties() ) );
    }

    /**
     * Apply the profile removal changes to all pom files.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects )
    {
        final ProfileRemovalState state = session.getState( ProfileRemovalState.class );
        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        final List<String> profilesToRemove = state.getProfileRemoval();
        final Set<Project> changed = new HashSet<>();

        for ( final Project project : projects )
        {
            final String ga = ga( project );
            logger.info( "Applying changes to: " + ga );

            final Model model = project.getModel();
            final List<Profile> profiles = model.getProfiles();

            Iterator<Profile> i = profiles.iterator();

            while (i.hasNext())
            {
                Profile p = i.next();
                for (String id : profilesToRemove)
                {
                    if (p.getId().equals( id ))
                    {
                        logger.debug ("Removing profile {}", p.getId());
                        i.remove();
                        changed.add( project );
                        break;
                    }
                }
            }
        }

        return changed;
    }

    @Override
    public int getExecutionIndex()
    {
        return 55;
    }
}
