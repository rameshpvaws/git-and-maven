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

import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.fixture.TestUtils;
import org.commonjava.maven.ext.io.PomIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 22/08/2019
 */
public class InitialGroovyManipulatorTest
{
    @Rule
    public TemporaryFolder tf = new TemporaryFolder();

    @Test
    public void shouldRemoveProjectInGroovyScript() throws Exception
    {
        final File groovy = TestUtils.resolveFileResource( "groovy-project-removal", "manipulation.groovy" );
        final File base = TestUtils.resolveFileResource( "groovy-project-removal", "" );
        final File root = tf.newFolder();
        FileUtils.copyDirectory( base, root );
        final File projectroot = new File ( root, "pom.xml");

        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
        config.setClassPathScanning( PlexusConstants.SCANNING_ON );
        config.setComponentVisibility( PlexusConstants.GLOBAL_VISIBILITY );
        config.setName( "PME-CLI" );
        PlexusContainer container = new DefaultPlexusContainer( config );

        PomIO pomIO = container.lookup( PomIO.class );

        List<Project> projects = pomIO.parseProject( projectroot );

        assertThat( projects.size(), equalTo( 3 ) );

        Properties userProperties = new Properties();
        userProperties.setProperty( "versionIncrementalSuffix", "rebuild" );
        userProperties.setProperty( "groovyScripts", groovy.toURI().toString() );

        TestUtils.SMContainer smc = TestUtils.createSessionAndManager( userProperties );
        smc.getRequest().setPom( projectroot );
        smc.getManager().scanAndApply( smc.getSession() );

        // re-read the projects:
        projects = pomIO.parseProject( projectroot );
        assertThat( projects.size(), equalTo( 3 ) );

        assertEquals( 1, projectForArtifactId( projects, "groovy-project-removal").getModel().getProfiles().size() );
        assertThat( projectForArtifactId( projects, "groovy-project-removal" ).getVersion(), containsString( "rebuild" ) );
        assertThat( projectForArtifactId( projects, "groovy-project-removal-moduleA" ).getVersion(),
                    containsString( "rebuild" ) );
        // moduleB was removed from projects by the groovy script and therefore should not be reversioned:
        assertThat( projectForArtifactId( projects, "groovy-project-removal-moduleB" ).getVersion(), equalTo( "1.0.0" ) );
    }

    private Project projectForArtifactId( List<Project> projects, String artifactId )
    {
        Optional<Project> maybeProject = projects.stream().filter( p -> p.getArtifactId().equals( artifactId ) ).findAny();
        if ( !maybeProject.isPresent() )
        {
            fail( "Unable to find project " + artifactId + " in the groovy-project-removal project" );
        }
        return maybeProject.get();
    }
}
