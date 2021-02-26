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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.commonjava.maven.atlas.ident.ref.ArtifactRef;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.DependencyRemovalState;
import org.commonjava.maven.ext.core.state.DependencyState;
import org.commonjava.maven.ext.core.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.commonjava.maven.ext.core.util.IdUtils.ga;

/**
 * {@link Manipulator} implementation that can remove specified plugins from a project's pom file.
 * Configuration is stored in a {@link DependencyState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named("dependency-removal-manipulator")
@Singleton
public class DependencyRemovalManipulator
        implements Manipulator
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private ManipulationSession session;

    /**
     * Initialize the {@link DependencyState} state holder in the {@link ManipulationSession}. This state holder detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available for
     * later.
     */
    @Override
    public void init( final ManipulationSession session )
    {
        session.setState( new DependencyRemovalState( session.getUserProperties() ) );
        this.session = session;
    }

    /**
     * Apply the alignment changes to the list of {@link Project}'s given.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects )
            throws ManipulationException
    {
        final State state = session.getState( DependencyRemovalState.class );

        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug( getClass().getSimpleName() + ": Nothing to do!" );
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();

        for ( final Project project : projects )
        {
            final Model model = project.getModel();

            if ( apply( project, model ) )
            {
                changed.add( project );
            }
        }

        return changed;
    }

    private boolean apply( final Project project, final Model model ) throws ManipulationException
    {
        final DependencyRemovalState state = session.getState(DependencyRemovalState.class);

        logger.info("Applying Dependency changes to: " + ga(project));

        List<ProjectRef> dependenciesToRemove = state.getDependencyRemoval();
        boolean result = scanDependencies( project.getAllResolvedDependencies( session ), dependenciesToRemove, model.getDependencies());

        if ( model.getDependencyManagement() != null &&
             scanDependencies(project.getResolvedManagedDependencies( session ), dependenciesToRemove, model.getDependencyManagement().getDependencies()))
        {
            result = true;
        }

        final Map<Profile, Map<ArtifactRef, Dependency>> pd = project.getAllResolvedProfileDependencies( session );
        final Map<Profile, Map<ArtifactRef, Dependency>> pmd = project.getResolvedProfileManagedDependencies( session );
        for ( Profile profile : pd.keySet())
        {
            int index = model.getProfiles().indexOf( profile );
            if ( scanDependencies( pd.get( profile ), dependenciesToRemove, model.getProfiles().get( index ).getDependencies() ) )
            {
                result = true;
            }
        }
        for ( Profile profile : pmd.keySet())
        {
            int index = model.getProfiles().indexOf( profile );
            DependencyManagement dm = model.getProfiles().get( index ).getDependencyManagement();
            if ( dm != null )
            {
                if ( scanDependencies( pmd.get( profile ), dependenciesToRemove, dm.getDependencies() ) )
                {
                    result = true;
                }
            }
        }
        return result;
    }

    private boolean scanDependencies( Map<ArtifactRef, Dependency> resolvedDependencies,
                                      List<ProjectRef> dependenciesToRemove, List<Dependency> dependencies )
    {
        boolean result = false;
        if ( dependencies != null )
        {
            for ( ArtifactRef pvr : resolvedDependencies.keySet() )
            {
                if ( dependenciesToRemove.contains( pvr.asProjectRef() ) )
                {
                    logger.debug( "Removing {} ", resolvedDependencies.get( pvr ) );
                    dependencies.remove( resolvedDependencies.get( pvr ) );
                    result = true;
                }
            }
        }
        return result;
    }

    @Override
    public int getExecutionIndex()
    {
        return 51;
    }
}
