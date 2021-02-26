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

import groovy.lang.GroovyShell;
import groovy.lang.MissingMethodException;
import groovy.lang.Script;
import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.commonjava.maven.atlas.ident.ref.ArtifactRef;
import org.commonjava.maven.atlas.ident.ref.SimpleArtifactRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.model.SimpleScopedArtifactRef;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.groovy.BaseScript;
import org.commonjava.maven.ext.core.groovy.InvocationPoint;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.commonjava.maven.ext.core.groovy.PMEInvocationPoint;
import org.commonjava.maven.ext.core.state.GroovyState;
import org.commonjava.maven.ext.io.FileIO;
import org.commonjava.maven.ext.io.ModelIO;
import org.commonjava.maven.ext.io.PomIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * {@link Manipulator} implementation that can resolve a remote groovy file and execute it on executionRoot. Configuration
 * is stored in a {@link GroovyState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
public abstract class BaseGroovyManipulator
{
    protected final Logger logger = LoggerFactory.getLogger( getClass() );

    @SuppressWarnings( "WeakerAccess" )
    protected ModelIO modelIO;

    @SuppressWarnings( "WeakerAccess" )
    protected FileIO fileIO;

    @SuppressWarnings( "WeakerAccess" )
    protected PomIO pomIO;

    protected ManipulationSession session;

    BaseGroovyManipulator( ModelIO modelIO, FileIO fileIO, PomIO pomIO )
    {
        this.modelIO = modelIO;
        this.fileIO = fileIO;
        this.pomIO = pomIO;
    }

    public abstract int getExecutionIndex();


    /**
     * Splits the value on ',', then wraps each value in {@link SimpleArtifactRef#parse(String)} and prints a warning / skips in the event of a
     * parsing error. Returns null if the input value is null.
     * @param value a comma separated list of GAVTC to parse
     * @return a collection of parsed ArtifactRef.
     * @throws ManipulationException if an error occurs.
     */
    List<File> parseGroovyScripts( final String value ) throws ManipulationException
    {
        if ( isEmpty( value ) )
        {
            return Collections.emptyList();
        }
        else
        {
            final List<File> result = new ArrayList<>();

            logger.debug( "Processing groovy scripts {} ", value );
            try
            {
                final String[] scripts = value.split( "," );
                for ( final String script : scripts )
                {
                    File found;
                    if ( script.startsWith( "http" ) || script.startsWith( "file" ))
                    {
                        logger.info( "Attempting to read URL {} ", script );
                        found = fileIO.resolveURL( script );
                    }
                    else
                    {
                        final ArtifactRef ar = SimpleScopedArtifactRef.parse( script );
                        logger.info( "Attempting to read GAV {} with classifier {} and type {} ", ar.asProjectVersionRef(), ar.getClassifier(), ar.getType() );
                        found = modelIO.resolveRawFile( ar );
                    }
                    result.add( found );
                }
            }
            catch ( IOException e )
            {
                throw new ManipulationException( "Unable to parse groovyScripts", e );
            }
            return result;
        }
    }


    void applyGroovyScript( List<Project> projects, Project project, File groovyScript ) throws ManipulationException
    {
        final GroovyShell shell = new GroovyShell( );
        final Script script;
        final InvocationStage stage;

        try
        {
            script = shell.parse( groovyScript );

            @SuppressWarnings( "deprecation" )
            PMEInvocationPoint deprecatedInvocationPoint = script.getClass().getAnnotation( PMEInvocationPoint.class );
            InvocationPoint invocationPoint = script.getClass().getAnnotation( InvocationPoint.class );
            if ( deprecatedInvocationPoint != null )
            {
                logger.warn( "Using deprecated InvocationPoint annotation (for {})", deprecatedInvocationPoint.invocationPoint() );
                logger.debug( "InvocationPoint is {}", deprecatedInvocationPoint.invocationPoint() );
                stage = deprecatedInvocationPoint.invocationPoint();
            }
            else if ( invocationPoint != null )
            {
                logger.debug( "InvocationPoint is {}", invocationPoint.invocationPoint() );
                stage = invocationPoint.invocationPoint();
            }
            else
            {
                stage = null;
            }
            if ( stage == null )
            {
                throw new ManipulationException( "Mandatory annotation '@InvocationPoint(invocationPoint = ' not declared" );
            }
            if ( script instanceof BaseScript )
            {
                ((BaseScript) script).setValues(pomIO, fileIO, modelIO, session, projects, project, stage);
            }
            else
            {
                throw new ManipulationException( "Cannot cast {} to a BaseScript to set values.", groovyScript );
            }
        }
        catch (MissingMethodException e)
        {
            try
            {
                logger.debug ( "Failure when injecting into script {} ", FileUtils.readFileToString( groovyScript, StandardCharsets.UTF_8 ), e );
            }
            catch ( IOException e1 )
            {
                logger.debug ("Unable to read script file {} for debugging! {} ", groovyScript, e1);
            }
            throw new ManipulationException( "Unable to inject values into base script", e );
        }
        catch (CompilationFailedException e)
        {
            try
            {
                logger.debug ( "Failure when parsing script {} ", FileUtils.readFileToString( groovyScript, StandardCharsets.UTF_8 ), e );
            }
            catch ( IOException e1 )
            {
                logger.debug ("Unable to read script file {} for debugging! {} ", groovyScript, e1);
            }
            throw new ManipulationException( "Unable to parse script", e );
        }
        catch ( IOException e )
        {
            throw new ManipulationException( "Unable to parse script", e );
        }

        if ( getExecutionIndex() == stage.getStageValue() || stage == InvocationStage.BOTH )
        {
            try
            {
                logger.info ("Executing {} on {} at invocation point {}", groovyScript, project, stage);

                script.run();

                logger.info ("Completed {}.", groovyScript);
            }
            catch ( Exception e )
            {
                //noinspection ConstantConditions
                if ( e instanceof ManipulationException )
                {
                    throw ((ManipulationException)e);
                }
                else
                {
                    throw new ManipulationException( "Problem running script", e );
                }
            }
        }
        else
        {
            logger.debug( "Ignoring script {} as invocation point {} does not match index {}", groovyScript, stage, getExecutionIndex() );
        }
    }
}
