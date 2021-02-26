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
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.util.WildcardMap;
import org.commonjava.maven.ext.core.util.PropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Captures configuration relating to groupId relocation for POMs.
 */
public class RelocationState
    implements State
{
    /**
     * The name of the property which contains a groupId and optional version relocation to perform. Multiple can be
     * passed using multiple dependencyRelocations arguments.
     *
     * <pre>
     * <code>-DdependencyRelocations.oldGroupId:[oldArtifactId]@newGroupId:[newArtifactId]=[newVersion</code>
     * </pre>
     * <ul>
     * <li>If oldArtifactId and newArtifactId is specified we relocate artifactIds as well.</li>
     * <li>If one of oldArtifactId/newArtifactId are specified its an error.</li>
     * <li>If none of oldArtifactId/newArtifactId are specified then we relocate all artifactIds (wildcard).</li>
     * </ul>
     * <ul>
     * <li>If version is specified we force set the version.</li>
     * </ul>
     */
    @ConfigValue( docIndex = "dep-manip.html#dependency-relocations" )
    public static final String DEPENDENCY_RELOCATIONS = "dependencyRelocations.";

    private static final Logger logger = LoggerFactory.getLogger( RelocationState.class );

    private final WildcardMap<ProjectVersionRef> dependencyRelocations = new WildcardMap<>();

    public RelocationState( final Properties userProps )
                    throws ManipulationException
    {
        initialise( userProps );
    }

    public void initialise( Properties userProps ) throws ManipulationException
    {
        // This contains everything before the equals and a possibly null set of values. We now need to further
        // post-process this into something useful i.e. establish whether we are relocating groupIds and artifactIds.
        Map<String,String> propRelocs = PropertiesUtils.getPropertiesByPrefix( userProps, DEPENDENCY_RELOCATIONS );
        for ( Map.Entry<String, String> entry : propRelocs.entrySet() )
        {
            String[] split = entry.getKey().split( ":", 3 );
            if ( split.length != 3 )
            {
                throw new ManipulationException(
                                "Incorrect relocation format; should be <..> : [ <...> ] @ <..> : [ <...> ]" );
            }
            String groupId = split[0];
            String newArtifactId = split[2];
            split = split[1].split( "@" );

            if ( split.length != 2 )
            {
                throw new ManipulationException(
                                "Incorrect relocation format for oldArtifactId/newGroupdId; should be <..> : [ <...> ] @ <..> : [ <...> ]" );
            }
            String artifactId = split[0];
            String newGroupId = split[1];

            // Sanity checks
            if ( ! ( isEmpty( artifactId ) == isEmpty( newArtifactId ) ) )
            {
                throw new ManipulationException(
                                "Incorrect relocation format for artifactId ({} : {}); should be <..> : [ <...> ] @ <..> : [ <...> ]",
                                artifactId, newArtifactId );
            }

            if ( isEmpty( artifactId ) )
            {
                artifactId = WildcardMap.WILDCARD;
            }
            if ( isEmpty( newArtifactId ) )
            {
                newArtifactId = WildcardMap.WILDCARD;
            }

            if ( groupId.length() == 0 || newGroupId.length() == 0 )
            {
                throw new ManipulationException(
                                "Incorrect relocation format for groupIds ({} : {}); should be <..> : [ <...> ] @ <..> : [ <...> ]",
                                groupId, newGroupId );
            }

            String version = ( isEmpty( entry.getValue() ) ? WildcardMap.WILDCARD : entry.getValue() );

            logger.debug( "Relocation found oldGroupId '{}' : oldArtifactId '{}' -> newGroupId '{}' : newArtifactId '{}' and version '{}' ",
                          groupId, artifactId, newGroupId, newArtifactId, version );

            ProjectRef sp = new SimpleProjectRef( groupId, artifactId );

            dependencyRelocations.put( sp, new SimpleProjectVersionRef( newGroupId, newArtifactId, version ) );
        }

        logger.trace ("Wildcard map {} ", dependencyRelocations);
    }

    /**
     * Enabled ONLY if dependencyRelocation is provided in the user properties / CLI -D options.
     *
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled()
    {
        return !dependencyRelocations.isEmpty();
    }

    public WildcardMap<ProjectVersionRef> getDependencyRelocations()
    {
        return dependencyRelocations;
    }
}
