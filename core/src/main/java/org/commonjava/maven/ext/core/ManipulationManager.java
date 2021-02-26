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
package org.commonjava.maven.ext.core;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.profiles.activation.ProfileActivationException;
import org.apache.maven.project.ProjectBuilder;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.json.PME;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.util.JSONUtils;
import org.commonjava.maven.ext.common.util.ProjectComparator;
import org.commonjava.maven.ext.common.util.WildcardMap;
import org.commonjava.maven.ext.core.impl.Manipulator;
import org.commonjava.maven.ext.core.state.CommonState;
import org.commonjava.maven.ext.core.state.RelocationState;
import org.commonjava.maven.ext.core.util.ManipulatorPriorityComparator;
import org.commonjava.maven.ext.io.PomIO;
import org.commonjava.maven.ext.io.resolver.ExtensionInfrastructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;

import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.commonjava.maven.ext.common.util.ProfileUtils.PROFILE_SCANNING;
import static org.commonjava.maven.ext.common.util.ProfileUtils.PROFILE_SCANNING_DEFAULT;

/**
 * Coordinates manipulation of the POMs in a build, by providing methods to read the project set from files ahead of the build proper (using
 * {@link ProjectBuilder}), then other methods to coordinate all potential {@link Manipulator} implementations (along with the {@link PomIO}
 * raw-model reader/rewriter).
 * <p>
 * Sequence of calls:
 * <ol>
 *   <li>{@link #init(ManipulationSession)}</li>
 *   <li>{@link #applyManipulations(List)}</li>
 * </ol>
 *
 * @author jdcasey
 */
@Named
@Singleton
public class ManipulationManager
{
    private static final String MARKER_PATH = "target";

    public static final String MARKER_FILE = MARKER_PATH + File.separatorChar + "pom-manip-ext-marker.txt";

    private static final String REPORT_JSON_DEFAULT = File.separatorChar + "manipulation.json";

    @ConfigValue( docIndex = "index.html#summary-logging")
    public static final String REPORT_TXT_OUTPUT_FILE = "reportTxtOutputFile";

    @ConfigValue( docIndex = "index.html#summary-logging")
    public static final String REPORT_JSON_OUTPUT_FILE = "reportJSONOutputFile";

    @ConfigValue( docIndex = "index.html#deprecated-and-unknown-properties")
    public static final String DEPRECATED_PROPERTIES = "enabledDeprecatedProperties";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final Map<String, Manipulator> manipulators;

    private final Map<String, ExtensionInfrastructure> infrastructure;

    private final PomIO pomIO;

    private final PME jsonReport = new PME();

    @Inject
    public ManipulationManager( Map<String, Manipulator> manipulators,
                                Map<String, ExtensionInfrastructure> infrastructure, PomIO pomIO)
    {
        this.manipulators = manipulators;
        this.infrastructure = infrastructure;
        this.pomIO = pomIO;
    }

    /**
     * Determined from {@link Manipulator#getExecutionIndex()} comparisons during {@link #init(ManipulationSession)}.
     */
    @Getter
    private List<Manipulator> orderedManipulators;

    /**
     * Initialize {@link ManipulationSession} using the given {@link MavenSession} instance, along with any state managed by the individual
     * {@link Manipulator} components. Entry point for both CLI and EXT modes.
     *
     * @param session the container session for manipulation.
     * @throws ManipulationException if an error occurs.
     */
    public void init( final ManipulationSession session )
        throws ManipulationException {
        logger.debug("Initialising ManipulationManager with user properties {}", session.getUserProperties());

        // We invert it as the property is to _enable_ deprecated properties - which is off by default.
        final boolean deprecatedDisabled = !Boolean.parseBoolean(session.getUserProperties().getProperty(DEPRECATED_PROPERTIES,
                "false"));
        final HashMap<String, String> deprecatedUsage = new HashMap<>();

        session.getUserProperties().stringPropertyNames().forEach(
                p -> {
                    if (!p.equals("maven.repo.local") && ConfigList.allConfigValues.keySet().stream().noneMatch(p::startsWith)) {
                        logger.warn("Unknown configuration value {}", p);
                    }
                    // Track deprecated properties. We have to do a rather ugly starts with instead of direct keying as
                    // some properties operate upon a prefix basis.
                    logger.debug("Examining for deprecated properties for {}", p);
                    ConfigList.allConfigValues.entrySet().stream().
                            filter(e -> p.startsWith(e.getKey())).
                            filter(Map.Entry::getValue).
                            forEach(up -> deprecatedUsage.put(p, up.getKey()));
                });

        if (deprecatedUsage.size() > 0)
        {
            deprecatedUsage.forEach( (k,v) -> logger.warn ("Located deprecated property {} in user properties (with matcher of {})", k, v) );
            if ( deprecatedDisabled )
            {
                throw new ManipulationException("Deprecated properties are being used. Either remove them or set enabledDeprecatedProperties=true");
            }
        }

        for ( final ExtensionInfrastructure infra : infrastructure.values() )
        {
            infra.init( );
        }

        orderedManipulators = new ArrayList<>( manipulators.values() );
        // The RESTState depends upon the VersionState being initialised. Therefore initialise in reverse order
        // and do a final sort to run in the correct order. See the Manipulator interface for detailed discussion
        // on ordered.
        orderedManipulators.sort( Collections.reverseOrder( new ManipulatorPriorityComparator() ) );

        for ( final Manipulator manipulator : orderedManipulators )
        {
            logger.debug( "Initialising manipulator " + manipulator.getClass()
                                                                   .getSimpleName() );
            manipulator.init( session );
        }
        orderedManipulators.sort( new ManipulatorPriorityComparator() );

        // Now init the common state
        session.setState( new CommonState( session.getUserProperties()) );
    }

    /**
     * Encapsulates {@link #applyManipulations(List)}
     *
     * @param session the container session for manipulation.
     * @throws ManipulationException if an error occurs.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void scanAndApply( final ManipulationSession session )
                    throws ManipulationException
    {
        final List<Project> currentProjects = pomIO.parseProject( session.getPom() );
        final List<Project> originalProjects = new ArrayList<>(  );
        currentProjects.forEach( p -> originalProjects.add( new Project( p ) ) );

        final Project originalExecutionRoot = originalProjects.get( 0 );
        if ( ! originalExecutionRoot.isExecutionRoot() )
        {
            throw new ManipulationException( "First project is not execution root : {}", originalProjects );
        }

        session.getActiveProfiles().addAll( parseActiveProfiles( session, currentProjects ) );
        session.setProjects( currentProjects );

        Set<Project> changed = applyManipulations( currentProjects );

        // Create a marker file if we made some changes to prevent duplicate runs.
        if ( !changed.isEmpty() )
        {
            logger.info( "Maven-Manipulation-Extension: Rewrite changed: {}", currentProjects );

            ProjectVersionRef executionRoot = pomIO.rewritePOMs( changed );

            jsonReport.getGav().setPVR( executionRoot );
            jsonReport.getGav().setOriginalGAV( originalExecutionRoot.getKey().toString() );


            try
            {
                session.getTargetDir().mkdir();
                new File( session.getTargetDir().getParentFile(), ManipulationManager.MARKER_FILE ).createNewFile();

                WildcardMap<ProjectVersionRef> map = (session.getState( RelocationState.class) == null ? new WildcardMap<>() : session.getState( RelocationState.class ).getDependencyRelocations());
                String report = ProjectComparator.compareProjects( session, jsonReport, map , originalProjects, currentProjects );
                logger.info( report );

                final String reportTxtOutputFile = session.getUserProperties().getProperty( REPORT_TXT_OUTPUT_FILE, "");
                if ( isNotEmpty( reportTxtOutputFile ) )
                {
                    File reportFile = new File ( reportTxtOutputFile);
                    FileUtils.writeStringToFile( reportFile, report, StandardCharsets.UTF_8 );
                }
                final String reportJsonOutputFile = session.getUserProperties().getProperty( REPORT_JSON_OUTPUT_FILE,
                                                                                             session.getTargetDir() + File.separator + REPORT_JSON_DEFAULT);

                try (FileWriter writer = new FileWriter( new File( reportJsonOutputFile ) ) )
                {
                    writer.write( JSONUtils.jsonToString( jsonReport ) );
                }
            }
            catch ( IOException e )
            {
                logger.error( "Unable to create marker or result file", e );
                throw new ManipulationException( "Marker/result file creation failed", e );
            }
        }

        // Ensure shutdown of GalleyInfrastructure Executor Service
        for ( ExtensionInfrastructure e : infrastructure.values() )
        {
            e.finish();
        }

        logger.info( "Maven-Manipulation-Extension: Finished." );
    }


    @SuppressWarnings( {"unchecked", "deprecation" } )
    private Set<String> parseActiveProfiles( ManipulationSession session, List<Project> projects ) throws ManipulationException
    {
        final Set<String> activeProfiles = new HashSet<>();
        final DefaultProfileManager dpm = new DefaultProfileManager( session.getSession().getContainer(), session.getUserProperties() );

        logger.debug("Explicitly activating {}", session.getActiveProfiles());
        dpm.explicitlyActivate( session.getActiveProfiles() );

        for ( Project p : projects )
        {
            // We clone the original profile here to prevent the DefaultProfileManager affecting the original list
            // during its activation calculation.
            p.getModel().getProfiles().stream().filter( newProfile -> ! dpm.getProfilesById().containsKey( newProfile.getId() ) ).
                            forEach( newProfile -> dpm.addProfile( newProfile.clone() ) );

            try
            {
                List<org.apache.maven.model.Profile> ap = dpm.getActiveProfiles();
                activeProfiles.addAll( ap.stream().map( org.apache.maven.model.Profile::getId ).collect( Collectors.toList() ) );
            }
            catch ( ProfileActivationException e )
            {
                throw new ManipulationException( "Activation detection failure", e );
            }
        }


        if (logger.isDebugEnabled())
        {
            logger.debug("Will {}scan all profiles and returning active profiles of {} ",
                    Boolean.parseBoolean(session.getUserProperties().getProperty(PROFILE_SCANNING, PROFILE_SCANNING_DEFAULT)) ? "not " : "",
                    activeProfiles);
        }

        return activeProfiles;
    }

    /**
     * After projects are scanned for modifications, apply any modifications and rewrite POMs as needed. This method performs the following:
     * <ul>
     *   <li>read the raw models (uninherited, with only a bare minimum interpolation) from disk to escape any interpretation happening during project-building</li>
     *   <li>apply any manipulations
     *   <li>rewrite any POMs that were changed</li>
     * </ul>
     *
     * @param projects the list of Projects to apply the changes to.
     * @return collection of the changed projects.
     * @throws ManipulationException if an error occurs.
     */
    private Set<Project> applyManipulations( final List<Project> projects )
        throws ManipulationException
    {
        final Set<Project> changed = new HashSet<>();
        for ( final Manipulator manipulator : orderedManipulators )
        {
            logger.info( "Running manipulator {}", manipulator.getClass().getName() );
            final Set<Project> mChanged = manipulator.applyChanges( projects );

            if ( mChanged != null )
            {
                changed.addAll( mChanged );
            }
        }

        if ( changed.isEmpty() )
        {
            logger.info( "Maven-Manipulation-Extension: No changes." );
        }

        return changed;
    }

}
