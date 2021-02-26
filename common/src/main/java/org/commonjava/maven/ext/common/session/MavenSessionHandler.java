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
package org.commonjava.maven.ext.common.session;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.settings.Settings;
import org.commonjava.maven.ext.common.ManipulationException;

import java.io.File;
import java.util.List;
import java.util.Properties;

public interface MavenSessionHandler
{
    Properties getUserProperties();

    List<ArtifactRepository> getRemoteRepositories();

    File getPom() throws ManipulationException;

    File getTargetDir();

    ArtifactRepository getLocalRepository();

    List<String> getActiveProfiles();

    Settings getSettings();

    List<String> getExcludedScopes();
}
