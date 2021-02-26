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
package org.commonjava.maven.ext.cli;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.reflect.FieldUtils;
import org.apache.maven.execution.MavenSession;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.fixture.TestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import static org.commonjava.maven.ext.core.fixture.TestUtils.INTEGRATION_TEST;
import static org.commonjava.maven.ext.core.fixture.TestUtils.ROOT_DIRECTORY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CliTest
{
    @Rule
    public final ProvideSystemProperty ansiOFF = new ProvideSystemProperty( "picocli.ansi", "false" );

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    private File writeSettings( File f ) throws IOException
    {
        FileUtils.writeStringToFile( f, "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                        + "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\""
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                        + "xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd\">"
                        + "</settings>", StandardCharsets.UTF_8 );
        return f;
    }

    @Test
    public void checkHelpAndExit()
    {
        exit.expectSystemExitWithStatus( 0 );
        exit.checkAssertionAfterwards( () -> assertTrue( systemOutRule.getLog().contains( "Usage: PME" ) ) );
        Cli.main( new String[] { "-h" } );
    }

    @Test
    public void checkInvalidParam()
    {
        new Cli().run( new String[] { "-FOOBAR" } );
        assertTrue( systemErrRule.getLog().contains( "Unknown option" ) );
    }

    @Test
    public void checkTargetMatches() throws Exception
    {
        Cli c = new Cli();
        File pom1 = temp.newFile();
        File settings = writeSettings( temp.newFile() );

        TestUtils.executeMethod( c, "createSession", new Class[] { File.class, File.class }, new Object[] { pom1, settings } );

        assertEquals( "Session file should match", pom1,
                      ( (ManipulationSession) FieldUtils.readField( c, "session", true ) ).getPom() );
    }

    @Test
    public void checkTargetMatchesWithRun() throws Exception
    {
        Cli c = new Cli();
        File pom1 = temp.newFile();

        c.run( new String[] { "-f", pom1.toString() } );

        assertEquals( "Session file should match", pom1,
                      ( (ManipulationSession) FieldUtils.readField( c, "session", true ) ).getPom() );
    }

    @Test
    public void checkTargetDefaultMatches() throws Exception
    {
        Cli c = new Cli();
        c.run( new String[] {} );

        ManipulationSession session = (ManipulationSession) FieldUtils.readField( c, "session", true );
        File defaultTarget = (File) FieldUtils.readField( c, "target", true );

        assertEquals( "Session file should match", defaultTarget, session.getPom() );
    }

    @Test
    public void checkLocalRepositoryWithDefaults() throws Exception
    {
        Cli c = new Cli();
        File settings = writeSettings( temp.newFile() );
        c.run( new String[] { "-s", settings.toString()} );

        ManipulationSession session = (ManipulationSession) FieldUtils.readField( c, "session", true );
        MavenSession ms = (MavenSession) FieldUtils.readField( session, "mavenSession", true );

        assertEquals( ms.getRequest().getLocalRepository().getBasedir(), ms.getRequest().getLocalRepositoryPath().toString() );
        assertEquals( "File " + new File( ms.getRequest().getLocalRepository().getBasedir() ).getParentFile().toString()
                                      + " was not equal to " + System.getProperty( "user.home" ) + File.separatorChar + ".m2",
                      new File( ms.getRequest().getLocalRepository().getBasedir() ).getParentFile().toString(),
                      System.getProperty( "user.home" ) + File.separatorChar + ".m2" );

    }

    @Test
    public void checkLocalRepositoryWithDefaultsAndModifiedUserSettings() throws Exception
    {
        boolean restore = false;
        Path source = Paths.get( System.getProperty( "user.home" ) + File.separatorChar + ".m2" + File.separatorChar
                                                 + "settings.xml" );
        Path backup = Paths.get( source.toString() + '.' + UUID.randomUUID().toString() );
        Path tmpSettings = Paths.get( getClass().getResource( "/settings-test.xml" ).toURI() );

        try
        {
            if ( source.toFile().exists() )
            {
                System.out.println( "Backing up settings.xml to " + backup );
                restore = true;
                Files.move( source, backup, StandardCopyOption.ATOMIC_MOVE );
            }
            Files.copy( tmpSettings, source );

            Cli c = new Cli();
            c.run( new String[]{} );

            ManipulationSession session = (ManipulationSession) FieldUtils.readField( c, "session", true );
            MavenSession ms = (MavenSession) FieldUtils.readField( session, "mavenSession", true );

            assertEquals( ms.getRequest().getLocalRepository().getBasedir(),
                          ms.getRequest().getLocalRepositoryPath().toString() );
            assertEquals( ms.getLocalRepository().getBasedir(),
                          System.getProperty( "user.home" ) + File.separatorChar + ".m2-mead-test" );

        }
        finally
        {
            if ( restore )
            {
                Files.move( backup, source, StandardCopyOption.ATOMIC_MOVE );
            }
            else
            {
                Files.delete( source );
            }
        }
    }

    @Test
    public void checkProfileActivation() throws Exception
    {
        File folder = temp.newFolder();
        File target = temp.newFile();
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        Files.copy( Paths.get( INTEGRATION_TEST.toString(), "pom.xml" ), target.toPath(), StandardCopyOption.REPLACE_EXISTING );

        Cli c = new Cli();
        c.run( new String[] { "-d", "--settings=" + getClass().getResource( "/settings-test.xml" ).getFile(),
                                        "-Dmaven.repo.local=" + folder.toString(), "-Prun-its", "--file",
                                        target.getCanonicalPath() } );

        assertTrue( systemOutRule.getLog().contains( "Explicitly activating [run-its]" ) );
        assertTrue( systemOutRule.getLog().contains( "Will not scan all profiles and returning active profiles of [run-its]" ) );
    }

    @Test
    public void checkLocalRepositoryWithSettings() throws Exception
    {
        Cli c = new Cli();
        c.run( new String[] { "-settings=" + getClass().getResource( "/settings-test.xml").getFile() } );

        ManipulationSession session = (ManipulationSession) FieldUtils.readField( c, "session", true );
        MavenSession ms = (MavenSession) FieldUtils.readField( session, "mavenSession", true );

        assertEquals( ms.getRequest().getLocalRepository().getBasedir(), ms.getRequest().getLocalRepositoryPath().toString() );
    }

    @Test
    public void checkLocalRepositoryWithExplicitMavenRepo() throws Exception
    {
        File folder = temp.newFolder();
        Cli c = new Cli();
        c.run( new String[] { "-Dmaven.repo.local=" + folder.toString() } );

        ManipulationSession session = (ManipulationSession) FieldUtils.readField( c, "session", true );
        MavenSession ms = (MavenSession) FieldUtils.readField( session, "mavenSession", true );

        assertEquals( ms.getRequest().getLocalRepository().getBasedir(), ms.getRequest().getLocalRepositoryPath().toString() );
    }

    @Test
    public void checkLocalRepositoryWithExplicitMavenRepoAndSettings() throws Exception
    {
        File folder = temp.newFolder();
        Cli c = new Cli();
        c.run( new String[]
                        { "--settings=" + getClass().getResource("/settings-test.xml").getFile(),
                                        "-Dmaven.repo.local=" + folder.toString() } );

        ManipulationSession session = (ManipulationSession) FieldUtils.readField( c, "session", true );
        MavenSession ms = (MavenSession) FieldUtils.readField( session, "mavenSession", true );

        assertEquals( ms.getLocalRepository().getBasedir(), folder.toString() );
        assertEquals( ms.getRequest().getLocalRepository().getBasedir(), ms.getRequest().getLocalRepositoryPath().toString() );
    }

    @Test
    public void checkUnknownProperty() throws Exception
    {
        File folder = temp.newFolder();
        File target = temp.newFile();
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        Files.copy( Paths.get( INTEGRATION_TEST.toString(), "pom.xml" ), target.toPath(), StandardCopyOption.REPLACE_EXISTING );

        Cli c = new Cli();
        c.run( new String[] { "-d", "--settings=" + getClass().getResource( "/settings-test.xml" ).getFile(),
                        "-Dmaven.repo.local=" + folder.toString(), "-DUNKNOWN_PROPERTY=DUMMY", "-Prun-its", "--file",
                        target.getCanonicalPath() } );

        assertTrue( systemOutRule.getLog().contains( "Unknown configuration value UNKNOWN_PROPERTY" ) );
    }

    @Test
    public void checkPropertyWithoutValue() throws Exception
    {
        File folder = temp.newFolder();
        File target = temp.newFile();
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        Files.copy( Paths.get( INTEGRATION_TEST.toString(), "pom.xml" ), target.toPath(), StandardCopyOption.REPLACE_EXISTING );

        Cli c = new Cli();
        c.run( new String[] { "-d", "--settings=" + getClass().getResource( "/settings-test.xml" ).getFile(),
                "-Dmaven.repo.local=" + folder.toString(), "-DBOOLEAN_PROPERTY", "-Prun-its", "--file",
                target.getCanonicalPath() } );

        assertTrue( systemOutRule.getLog().contains( "BOOLEAN_PROPERTY=true" ) );
    }

    @Test
    public void checkVersionAndExit()
    {
        new Cli().run( new String[] { "--version" } );
        assertTrue( systemOutRule.getLog().contains( "PME CLI" ) );
    }

    @Test
    public void checkDependencies()
    {
        final File root = Paths.get( ROOT_DIRECTORY.toString(), "pom.xml" ).toFile();

        new Cli().run( new String[] { "--printProjectDeps", "--file", root.getAbsolutePath() } );

        // Strip out PME itself otherwise it causes issues on releasing a new version.
        String cliOutput = systemOutRule.getLogWithNormalizedLineSeparator().replaceAll( "org.commonjava.maven.ext:pom-manipulation-.*\\n", "" );

        assertTrue( cliOutput.contains( "Found 83" ) );
        assertTrue( cliOutput.matches( "(?s).*"
                + "ch.qos.logback:logback-classic:1.2.3                                            jar                                     compile             \n"
                + "ch.qos.logback:logback-core:1.2.3                                               jar                                     compile             \n"
                + "com.fasterxml.jackson.core:jackson-annotations:2.[.\\d+]+\\s+                   jar                                     compile             \n"
                + "com.fasterxml.jackson.core:jackson-core:2.[.\\d+]+\\s+                          jar                                     compile             \n"
                + "com.fasterxml.jackson.core:jackson-databind:2.[.\\d+]+\\s+                      jar                                     compile             \n"
                + "com.github.olivergondza:maven-jdk-tools-wrapper:0.1                             jar                                     compile             \n"
                + "com.github.stefanbirkner:system-rules:1.[.\\d+]+\\s+                            jar                                     test                \n"
                + "com.google.inject:guice:4.0                                                     jar                 no_aop              compile             \n"
                + "com.jayway.jsonpath:json-path:2.3.0                                             jar                                     compile             \n"
                + "com.konghq:unirest-java:3.[.\\d+]+\\s+                                          jar                                     compile             \n"
                + "com.konghq:unirest-objectmapper-jackson:3.[.\\d+]+\\s+                          jar                                     compile             \n"
                + "com.redhat.rcm:redhat-releng-tools:10                                           pom                                     compile             \n"
                + "com.soebes.maven.plugins:iterator-maven-plugin:0.3                              maven-plugin                                                \n"
                + "com.squareup:javapoet:1.[.\\d+]+\\s+                                            jar                                     compile             \n"
                + "commons-codec:commons-codec:1.11                                                jar                                     compile             \n"
                + "commons-io:commons-io:2.6                                                       jar                                     compile             \n"
                + "commons-lang:commons-lang:2.6                                                   jar                                     compile             \n"
                + "commons-logging:commons-logging:1.2                                             jar                                     compile             \n"
                + "info.picocli:picocli:4.[.\\d+]+\\s+                                             jar                                     compile             \n"
                + "javax.inject:javax.inject:1                                                     jar                                     compile             \n"
                + "junit:junit:4[.\\d+]+\\s+                                                       jar                                     test                \n"
                + "org.apache.httpcomponents:httpclient:4.[.\\d+]+\\s+                             jar                                     compile             \n"
                + "org.apache.ivy:ivy:2.4.0                                                        jar                                     compile             \n"
                + "org.apache.maven:apache-maven:3.5.0                                             zip                 bin                 test                \n"
                + "org.apache.maven:maven-artifact:3.5.0                                           jar                                     provided            \n"
                + "org.apache.maven:maven-compat:3.5.0                                             jar                                     provided            \n"
                + "org.apache.maven:maven-core:3.5.0                                               jar                                     provided            \n"
                + "org.apache.maven:maven-model:3.5.0                                              jar                                     provided            \n"
                + "org.apache.maven:maven-model-builder:3.5.0                                      jar                                     provided            \n"
                + "org.apache.maven:maven-settings:3.5.0                                           jar                                     provided            \n"
                + "org.apache.maven:maven-settings-builder:3.5.0                                   jar                                     provided            \n"
                + "org.apache.maven.plugins:maven-assembly-plugin:2.2-beta-5                       maven-plugin                                                \n"
                + "org.apache.maven.plugins:maven-dependency-plugin:3.1.1                          maven-plugin                                                \n"
                + "org.apache.maven.plugins:maven-invoker-plugin:3.2.1                             maven-plugin                                                \n"
                + "org.apache.maven.plugins:maven-jar-plugin:2.4                                   maven-plugin                                                \n"
                + "org.apache.maven.plugins:maven-project-info-reports-plugin:3.0.0                maven-plugin                                                \n"
                + "org.apache.maven.plugins:maven-release-plugin:2.3.2                             maven-plugin                                                \n"
                + "org.apache.maven.plugins:maven-resources-plugin:2.6                             maven-plugin                                                \n"
                + "org.apache.maven.plugins:maven-shade-plugin:3.2.1                               maven-plugin                                                \n"
                + "org.apache.maven.plugins:maven-surefire-plugin:2.12.4                           maven-plugin                                                \n"
                + "org.apache.maven.release:maven-release-api:3.0.0-M1                             jar                                     compile             \n"
                + "org.apache.maven.release:maven-release-manager:3.0.0-M1                         jar                                     compile             \n"
                + "org.bsc.maven:maven-processor-plugin:3.3.3                                      maven-plugin                                                \n"
                + "org.codehaus.groovy:groovy:[.\\d+]+\\s+                                         jar                                     compile             \n"
                + "org.codehaus.groovy:groovy-json:[.\\d+]+\\s+                                    jar                                     compile             \n"
                + "org.codehaus.groovy:groovy-xml:[.\\d+]+\\s+                                     jar                                     compile             \n"
                + "org.codehaus.mojo:animal-sniffer-maven-plugin:1.18                              maven-plugin                                                \n"
                + "org.codehaus.plexus:plexus-interpolation:1.24                                   jar                                     provided            \n"
                + "org.codehaus.plexus:plexus-utils:3.1.0                                          jar                                     compile             \n"
                + "org.commonjava.maven.atlas:atlas-identities:0.17.1                              jar                                     compile             \n"
                + "org.commonjava.maven.galley:galley-api:0.16.6                                   jar                                     compile             \n"
                + "org.commonjava.maven.galley:galley-core:0.16.6                                  jar                                     compile             \n"
                + "org.commonjava.maven.galley:galley-maven:0.16.6                                 jar                                     compile             \n"
                + "org.commonjava.maven.galley:galley-transport-filearc:0.16.6                     jar                                     compile             \n"
                + "org.commonjava.maven.galley:galley-transport-httpclient:0.16.6                  jar                                     compile             \n"
                + "org.commonjava.util:http-testserver:1.1                                         jar                                     test                \n"
                + "org.eclipse.aether:aether-api:1.1.0                                             jar                                     provided            \n"
                + "org.eclipse.jetty:jetty-server:9.[.\\d+]+[v\\d+]*\\s+                           jar                                     compile             \n"
                + "org.eclipse.sisu:org.eclipse.sisu.plexus:0.3.4                                  jar                                     compile             \n"
                + "org.goots.hiderdoclet:doclet:1.1                                                jar                                     compile             \n"
                + "org.hamcrest:hamcrest-all:1.3                                                   jar                                     test                \n"
                + "org.jacoco:jacoco-maven-plugin:0.8[.\\d+]+\\s+                                  maven-plugin                                                \n"
                + "org.jboss.byteman:byteman-bmunit:4[.\\d+]+\\s+                                  jar                                     test                \n"
                + "org.jboss.da:reports-model:1.7.0                                                jar                                     compile             \n"
                + "org.jdom:jdom:1.1.3                                                             jar                                     compile             \n"
                + "org.projectlombok:lombok:1.[.\\d+]+\\s+                                         jar                                     provided            \n"
                + "org.projectlombok:lombok-maven-plugin:1.[.\\d+]+\\s+                            maven-plugin                                                \n"
                + "org.slf4j:slf4j-api:1.[.\\d+]+\\s+                                              jar                                     compile             \n"
                + "org.xmlunit:xmlunit-core:2.7.0                                                  jar                                     test                \n"
                + "org.xmlunit:xmlunit-matchers:2.7.0                                              jar                                     test                \n"
                + "org.yaml:snakeyaml:1.17                                                         jar                                     compile.*") );

    }

    @Test
    public void checkManipulatorOrder()
    {
        new Cli().run( new String[] { "--printManipulatorOrder" } );
        assertTrue( systemOutRule.getLogWithNormalizedLineSeparator().contains( "Manipulator order is:" ) );
        assertTrue( systemOutRule.getLogWithNormalizedLineSeparator().contains( ""
                                                            + "         1          InitialGroovyManipulator                \n"
                                                            + "         2          RangeResolver                           \n"
                                                            + "         4          RESTBOMCollector                        \n"
                                                            + "         5          ProfileInjectionManipulator             \n"
                                                            + "         6          SuffixManipulator                       \n"
                                                            + "         7          RelocationManipulator                   \n"
                                                            + "         10         RESTCollector                           \n"
                                                            + "         20         ProjectVersioningManipulator            \n"
                                                            + "         25         ParentInjectionManipulator              \n"
                                                            + "         30         PropertyManipulator                     \n"
                                                            + "         35         PluginManipulator                       \n"
                                                            + "         40         DependencyManipulator                   \n"
                                                            + "         50         RepoAndReportingRemovalManipulator      \n"
                                                            + "         51         DependencyRemovalManipulator            \n"
                                                            + "         52         PluginRemovalManipulator                \n"
                                                            + "         53         NexusStagingMavenPluginRemovalManipulator\n"
                                                            + "         55         ProfileRemovalManipulator               \n"
                                                            + "         60         PluginInjectingManipulator              \n"
                                                            + "         65         RepositoryInjectionManipulator          \n"
                                                            + "         70         ProjectVersionEnforcingManipulator      \n"
                                                            + "         75         DistributionEnforcingManipulator        \n"
                                                            + "         80         BOMBuilderManipulator                   \n"
                                                            + "         90         JSONManipulator                         \n"
                                                            + "         91         XMLManipulator                          \n"
                                                            + "         99         FinalGroovyManipulator                  \n" ) );
    }

    @Test
    public void checkLogLevels() throws Exception
    {
        File folder = temp.newFolder();
        File target = new File( folder, "pom.xml" );

        FileUtils.copyDirectory(
                        ROOT_DIRECTORY.toFile(),
                        folder,
                        FileFilterUtils.or( DirectoryFileFilter.DIRECTORY, FileFilterUtils.suffixFileFilter("xml")));

        Cli c = new Cli();
        c.run( new String[] { "--settings=" + getClass().getResource( "/settings-test.xml" ).getFile(),
                                        "-Dmaven.repo.local=" + folder.toString(), "-Prun-its", "--file",
                                        target.getCanonicalPath() } );
        assertTrue( systemOutRule.getLog().contains( "Running manipulator" ) );

        systemOutRule.clearLog();

        c = new Cli();
        c.run( new String[] { "-q", "--settings=" + getClass().getResource( "/settings-test.xml" ).getFile(),
                                        "-Dmaven.repo.local=" + folder.toString(), "-Prun-its", "--file",
                                        target.getCanonicalPath() } );

        assertFalse( systemOutRule.getLog().contains( "Running manipulator" ) );

        systemOutRule.clearLog();

        c = new Cli();
        c.run( new String[] { "-t", "--settings=" + getClass().getResource( "/settings-test.xml" ).getFile(),
                                        "-DdependencyRelocations.commons-io:commons0io@newGroupId:newArtifactId=1.0",
                                        "-DversionSuffix=rebuild-1",
                                        "-Dmaven.repo.local=" + folder.toString(), "-Prun-its", "--file",
                                        target.getCanonicalPath() } );

        assertTrue( systemOutRule.getLog().contains( "Wildcard map " ) );
    }
}
