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
package org.commonjava.maven.ext.io;

import org.commonjava.maven.ext.common.ManipulationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

public class YamlTest
{
    private File yamlFile;

    @Rule
    public TemporaryFolder tf = new TemporaryFolder(  );

    @Before
    public void setup() throws IOException
    {
        URL yamlResource = this.getClass().getResource( "pme.yaml");
        yamlFile = new File( yamlResource.getFile() );
    }


    @Test
    public void readYamlViaMap ()
                    throws ManipulationException, IOException
    {
        Representer representer = new Representer();
        representer.getPropertyUtils().setSkipMissingProperties(true);

        Yaml y = new Yaml(representer);

        Map config = (Map) y.load( new FileInputStream( yamlFile ));
        Map usersConfig = (Map) config.get( "pme");

        assertTrue ( usersConfig.size() > 0);
    }

    @Test
    public void readYamlViaPojo ()
                    throws ManipulationException, IOException
    {

        Properties p = new ConfigIO().parse( yamlFile.getParentFile() );

        assertTrue (p.size() > 0);
    }
}
