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

import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.core.impl.JSONManipulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * Captures configuration relating to JSON manipulation. Used by {@link JSONManipulator}.
 */
public class JSONState
                implements State
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    /**
     * Property on the command line that handles modifying JSON files. The format is
     *
     * -DjsonUpdate=<file>:<json-xpath-expression>:[<replacement-value>] [,....]
     *
     * 1. If replacement-value is blank it becomes a delete instead of replace.
     * 2. Multiple operations may be fed in via comma separator.
     *
     */
    @ConfigValue( docIndex = "json.html" )
    private static final String JSON_PROPERTY = "jsonUpdate";

    /**
     * Used to store mappings of old property to new version.
     */
    private final List<JSONOperation> jsonOperations = new ArrayList<>();

    public JSONState( final Properties userProps ) throws ManipulationException
    {
        initialise( userProps );
    }

    public void initialise( Properties userProps ) throws ManipulationException
    {
        String property = userProps.getProperty( JSON_PROPERTY );

        if ( isNotEmpty( property ) )
        {
            final String[] ops = property.split( "(?<!\\\\)," );
            for ( int i = 0; i < ops.length; i++ )
            {
                ops[i] = ops[i].replaceAll( "\\\\,", "," );
            }

            for ( String operation : ops )
            {
                String[] components = operation.split( "(?<!\\\\):", 3 );
                for ( int i = 0; i < components.length; i++ )
                {
                    components[i] = components[i].replaceAll( "\\\\:", ":" );
                }
                if ( components.length != 3 )
                {
                    throw new ManipulationException( "Unable to parse command {} from property {}", operation,
                                                     property );
                }
                logger.debug( "Adding JSONOperation with file {}, xpath {} and update {}", components[0], components[1],
                              components[2] );
                jsonOperations.add( new JSONOperation( components[0], components[1], components[2] ) );
            }
        }
    }

    /**
     * Enabled ONLY if propertyManagement is provided in the user properties / CLI -D options.
     *
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled()
    {
        return !jsonOperations.isEmpty();
    }

    public List<JSONOperation> getJSONOperations()
    {
        return jsonOperations;
    }

    public final static class JSONOperation
    {
        private String file;

        private String xpath;

        private String update;

        public JSONOperation( String file, String xpath, String update )
        {
            this.file = file;
            this.xpath = xpath;
            this.update = update;
        }

        public String getFile()
        {
            return file;
        }

        public String getXPath()
        {
            return xpath;
        }

        public String getUpdate()
        {
            return update;
        }

        @Override
        public String toString()
        {
            return "File " + file + " xpath '" + xpath + "' update " + update;
        }
    }
}
