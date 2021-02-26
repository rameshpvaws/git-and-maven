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
package org.commonjava.maven.ext.io.rest;

import org.commonjava.maven.ext.common.ManipulationException;

/**
 * @author vdedik@redhat.com
 */
public class RestException extends ManipulationException
{
    public RestException( String msg )
    {
        super( msg );
    }

    public RestException( final String string, final Object... params )
    {
        // Note we don't extract any potential Throwable here ; we rely on the superclass to do that.
        super( string, params );
    }

    public RestException( String message, Throwable e )
    {
        super( message, e );
    }
}
