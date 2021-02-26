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

import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ArtifactRef;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.core.impl.DependencyManipulator;
import org.commonjava.maven.ext.core.util.IdUtils;
import org.commonjava.maven.ext.core.util.PropertiesUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.commonjava.maven.ext.core.util.PropertiesUtils.getPropertiesByPrefix;

/**
 * Captures configuration relating to dependency alignment from the POMs. Used by {@link DependencyManipulator}.
 */
public class DependencyState
                implements State
{
    /**
     * The String that needs to be prepended a system property to make it a dependencyExclusion.
     * For example to exclude junit alignment for the GAV (org.groupId:artifactId)
     * <pre>
     * <code>-DdependencyExclusion.junit:junit@org.groupId:artifactId</code>
     * </pre>
     * This has now been deprecated and is now an alias for {@link DependencyState#DEPENDENCY_OVERRIDE_PREFIX}
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @ConfigValue( docIndex = "dep-manip.html#exclusions-and-overrides", deprecated = true )
    private static final String DEPENDENCY_EXCLUSION_PREFIX = "dependencyExclusion.";

    /**
     * Defines how dependencies are located.
     */
    @ConfigValue( docIndex = "dep-manip.html#dependency-source")
    static final String DEPENDENCY_SOURCE = "dependencySource";

    /**
     * Merging precedence for dependency sources:
     * <pre>
     * <code>BOM</code> Solely Remote POM i.e. BOM.
     * <code>REST</code> Solely restURL.
     * <code>RESTBOM</code> Merges the information but takes the rest as precedence.
     * <code>BOMREST</code> Merges the information but takes the bom as precedence.
     * <code>NONE</code> No remote alignment.
     * </pre>
     * Configured by the property <code>-DdependencySource=[REST|BOM|RESTBOM|BOMREST]</code>
     */
    public enum DependencyPrecedence
    {
        REST,
        BOM,
        RESTBOM,
        BOMREST,
        NONE;

        @Override
        public String toString()
        {
            switch ( this )
            {
                case BOMREST:
                    return "BOM first, then REST";
                case RESTBOM:
                    return "REST first, then BOM";
                default:
                    return this.name();
            }
        }
    }

    /**
     * The String that needs to be prepended a system property to make it a dependencyOverride.
     * For example to exclude junit alignment for the GAV (org.groupId:artifactId)
     * <pre>
     * <code>-DdependencyOverride.junit:junit@org.groupId:artifactId</code>
     * </pre>
     */
    @ConfigValue( docIndex = "dep-manip.html#exclusions-and-overrides")
    private static final String DEPENDENCY_OVERRIDE_PREFIX = "dependencyOverride.";

    /**
     * The name of the property which contains the GAV of the remote pom from which to retrieve dependency management
     * information.
     * <pre>
     * <code>-DdependencyManagement:org.foo:bar-dep-mgmt:1.0</code>
     * </pre>
     */
    @ConfigValue( docIndex = "dep-manip.html#remote-pom")
    private static final String DEPENDENCY_MANAGEMENT_POM_PROPERTY = "dependencyManagement";

    /**
     * The String that needs to be prepended a system property to make it an extra BOM.
     * For example, used to align only parts of a project to a different BOM
     * <pre>
     * <code>-DdependencyManagement:org.foo:bar-dep-mgmt:1.0</code>
     * <code>-DdependencyManagement.xyzzy=org.foo:bar-dep-mgmt:2.0<code/>
     * <code>-DdependencyExclusion.junit:junit@org.groupId:artifactId=xyzzy</code>
     * </pre>
     */
    private static final String EXTRA_BOM_PREFIX = DEPENDENCY_MANAGEMENT_POM_PROPERTY + ".";

    private List<ProjectVersionRef> remoteBOMdepMgmt;

    private Map<String, ProjectVersionRef> extraBOMs;

    private Map<String, Map<ProjectRef, String>> extraBOMDepMgmts;

    private Map<String, String> dependencyOverrides;

    private Map<ArtifactRef, String> remoteRESTdepMgmt;

    private DependencyPrecedence precedence;

    public DependencyState( final Properties userProps ) throws ManipulationException
    {
        initialise( userProps );
    }

    public void initialise( Properties userProps ) throws ManipulationException
    {
        remoteBOMdepMgmt = IdUtils.parseGAVs( userProps.getProperty( DEPENDENCY_MANAGEMENT_POM_PROPERTY ) );

        extraBOMDepMgmts = new HashMap<>();
        extraBOMs = new HashMap<>();

        for ( Map.Entry<String, String> extra : getPropertiesByPrefix( userProps, EXTRA_BOM_PREFIX ).entrySet() )
        {
            extraBOMs.put( extra.getKey(), SimpleProjectVersionRef.parse( extra.getValue() ) );
        }

        dependencyOverrides = getPropertiesByPrefix( userProps, DEPENDENCY_EXCLUSION_PREFIX );

        Map<String, String> oP = PropertiesUtils.getPropertiesByPrefix( userProps, DEPENDENCY_OVERRIDE_PREFIX );
        for ( String s : oP.keySet() )
        {
            if ( dependencyOverrides.put( s, oP.get( s ) ) != null )
            {
                throw new ManipulationException( "Property clash between dependencyOverride and dependencyExclusion for {}", s );
            }
        }
        String sourceValue = userProps.getProperty( DEPENDENCY_SOURCE,
                                                            DependencyPrecedence.BOM.toString() ).toUpperCase();
        if ( StringUtils.isEmpty(sourceValue))
        {
            sourceValue = "NONE";
        }
        switch ( DependencyPrecedence.valueOf( sourceValue ) )
        {
            case REST:
            {
                precedence = DependencyPrecedence.REST;
                break;
            }
            case BOM:
            {
                precedence = DependencyPrecedence.BOM;
                break;
            }
            case RESTBOM:
            {
                precedence = DependencyPrecedence.RESTBOM;
                break;
            }
            case BOMREST:
            {
                precedence = DependencyPrecedence.BOMREST;
                break;
            }
            case NONE:
            {
                precedence = DependencyPrecedence.NONE;
                break;
            }
            default:
            {
                throw new ManipulationException( "Unknown value {} for {}", userProps.getProperty( DEPENDENCY_SOURCE ), DEPENDENCY_SOURCE);
            }
        }
    }

    /**
     * Enabled ONLY if dependencyManagement is provided OR restURL has been provided.
     *
     * @see org.commonjava.maven.ext.core.state.State#isEnabled()
     */
    @Override
    public boolean isEnabled()
    {
        return ( ( !( precedence == DependencyPrecedence.NONE ) ) &&
                 ( remoteBOMdepMgmt != null && !remoteBOMdepMgmt.isEmpty()   ) ||
                 ( remoteRESTdepMgmt != null && !remoteRESTdepMgmt.isEmpty() ) ||
                 ( !dependencyOverrides.isEmpty()) );
    }

    public List<ProjectVersionRef> getRemoteBOMDepMgmt()
    {
        return remoteBOMdepMgmt;
    }

    public Map<String, ProjectVersionRef> getExtraBOMs()
    {
        return extraBOMs;
    }

    public Map<String, Map<ProjectRef, String>> getExtraBOMDepMgmts( )
    {
        return extraBOMDepMgmts;
    }

    public DependencyPrecedence getPrecedence()
    {
        return precedence;
    }

    public void setRemoteRESTOverrides( Map<ArtifactRef, String> overrides )
    {
        remoteRESTdepMgmt = overrides;
    }

    public Map<ArtifactRef, String> getRemoteRESTOverrides( )
    {
        if ( remoteRESTdepMgmt == null )
        {
            remoteRESTdepMgmt = new HashMap<>(  );
        }
        return remoteRESTdepMgmt;
    }

    public Map<String, String> getDependencyOverrides( )
    {
        return dependencyOverrides;
    }
}
