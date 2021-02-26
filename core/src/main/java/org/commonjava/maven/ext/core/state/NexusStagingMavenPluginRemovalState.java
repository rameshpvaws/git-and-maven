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
package org.commonjava.maven.ext.core.state;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.SimpleVersionlessArtifactRef;
import org.commonjava.maven.ext.annotation.ConfigValue;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Captures configuration relating to nexus-staging-maven-plugin removal from the POMs.
 */
public final class NexusStagingMavenPluginRemovalState
        extends PluginRemovalState
{
    /** The name of the property containing a boolean controlling whether this is enabled, {@code -DnexusStagingMavenPluginRemoval=<true|false>}. */
    @ConfigValue( docIndex = "plugin-manip.html#nexus-staging-maven-plugin-removal" )
    private static final String NEXUS_STAGING_MAVEN_PLUGIN_REMOVAL_PROPERTY = "nexusStagingMavenPluginRemoval";

    /** The {@code <groupId>:<artifactId>} coordinates of nexus-staging-maven-plugin. */
    private static final String NEXUS_STAGING_MAVEN_PLUGIN_SPEC = "org.sonatype.plugins:nexus-staging-maven-plugin";

    /** The list version of the {@code <groupId>:<artifactId>} coordinates of nexus-staging-maven-plugin. */
    private static final List<ProjectRef> PLUGIN_REMOVAL = Collections.singletonList( SimpleVersionlessArtifactRef.parse( NEXUS_STAGING_MAVEN_PLUGIN_SPEC ) );

    /** Whether or not this manipulator is enabled. Defaults to {@code true}. */
    private boolean enabled = true;

    static
    {
        State.activeByDefault.add( NexusStagingMavenPluginRemovalState.class );
    }

    public NexusStagingMavenPluginRemovalState( final Properties userProps )
    {
        initialise( userProps );
    }

    /**
     * Initializes this state with the given {@code userProps}.
     *
     * The value {@link #isEnabled} will be set based on the value of the {@code nexusStagingMavenPluginRemoval}
     * boolean property.
     *
     * @param userProps the user properties
     */
    @Override
    public void initialise( final Properties userProps )
    {
        enabled = Boolean.parseBoolean( userProps.getProperty( NEXUS_STAGING_MAVEN_PLUGIN_REMOVAL_PROPERTY,
                Boolean.TRUE.toString() ) );
    }

    /**
     * Always enabled unless {@code nexusStagingMavenPluginRemoval} is set to {@code false} in the user properties
     * or the CLI {@code -D} options.
     *
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Always returns a singleton {@link List} containing a {@link ProjectRef} with the coordinates
     * {@code org.sonatype.plugins:nexus-staging-maven-plugin}.
     *
     * @return a singleton {@link List} containing a {@link ProjectRef} with the coordinates
     * {@code org.sonatype.plugins:nexus-staging-maven-plugin}
     */
    @Override
    public List<ProjectRef> getPluginRemoval()
    {
        return PLUGIN_REMOVAL;
    }
}
