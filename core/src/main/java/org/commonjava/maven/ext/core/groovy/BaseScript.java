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
package org.commonjava.maven.ext.core.groovy;

import lombok.Getter;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.session.MavenSessionHandler;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.impl.InitialGroovyManipulator;
import org.commonjava.maven.ext.io.FileIO;
import org.commonjava.maven.ext.io.ModelIO;
import org.commonjava.maven.ext.io.PomIO;

import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 * Abstract class that should be used by developers wishing to implement groovy scripts
 * for PME.
 */
public abstract class BaseScript extends BaseScriptUtils
{
    private List<Project> projects;

    private Project project;

    private ProjectVersionRef gav;

    private File basedir;

    private Properties userProperties;

    private MavenSessionHandler sessionHandler;

    private InvocationStage stage;

    /**
     * Get the modelIO instance for remote artifact resolving.
     */
    @Getter
    private ModelIO modelIO;

    @Getter
    private FileIO fileIO;

    @Getter
    private PomIO pomIO;

    /**
     * Return the current Project
     * @return a {@link org.commonjava.maven.ext.common.model.Project} instance.
     */
    public Project getProject()
    {
        return project;
    }

    /**
    * Returns the entire collection of Projects
    * @return an {@link java.util.ArrayList} of {@link org.commonjava.maven.ext.common.model.Project} instances.
    */
    public List<Project> getProjects()
    {
        return projects;
    }

    /**
     * Obtain the GAV of the current project
     * @return a {@link org.commonjava.maven.atlas.ident.ref.ProjectVersionRef}
     */
    public ProjectVersionRef getGAV()
    {
        return gav;
    }

    /**
     * Get the working directory (the execution root).
     * @return a {@link java.io.File} reference.
     */
    public File getBaseDir()
    {
        return basedir;
    }

    /**
     * Get the user properties
     * @return a {@link java.util.Properties} reference.
     */
    public Properties getUserProperties()
    {
        return userProperties;
    }

    /**
     * Get the MavenSessionHandler instance
     * @return a {@link MavenSessionHandler} reference.
     */
    public MavenSessionHandler getSession()
    {
        return sessionHandler;
    }

    /**
     * Get the current stage
     * @return a {@link InvocationStage} reference.
     */
    public InvocationStage getInvocationStage()
    {
        return stage;
    }

    /**
     * Internal use only - the {@link InitialGroovyManipulator} uses this to
     * initialise the values
     * @param pomIO the pomIO instance.
     * @param fileIO the fileIO instance.
     * @param modelIO the modelIO instance.
     * @param session the Session instance.
     * @param projects ArrayList of Project instances
     * @param project Current project
     * @param stage the current InvocationStage of the groovy script
     */
    public void setValues(PomIO pomIO, FileIO fileIO, ModelIO modelIO, ManipulationSession session, List<Project> projects, Project project,
                          InvocationStage stage)
    {
        this.fileIO = fileIO;
        this.pomIO = pomIO;
        this.modelIO = modelIO;
        this.sessionHandler = session;
        this.projects = projects;
        this.project = project;
        this.gav = project.getKey();
        this.basedir = project.getPom().getParentFile();
        this.userProperties = session.getUserProperties();
        this.stage = stage;

        logger.info ("Injecting values. Project is " + project + " with basedir " + basedir + " and properties " + userProperties);
    }
}
