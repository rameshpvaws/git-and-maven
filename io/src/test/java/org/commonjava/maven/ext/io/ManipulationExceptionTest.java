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
import org.commonjava.maven.ext.io.rest.RestException;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class ManipulationExceptionTest
{
    private UUID uuid = UUID.randomUUID();
    private IndexOutOfBoundsException exception = new IndexOutOfBoundsException(  );

    @Test
    public void testManipulationExceptionParams ()
    {
        try
        {
            throw new ManipulationException( "TEST {} WITH {} PARAM", 1, uuid, exception );
        }
        catch (ManipulationException e)
        {
            assertSame( e.getCause(), exception );
            assertEquals( e.getMessage(), "TEST 1 WITH " + uuid + " PARAM");
        }
        try
        {
            throw new ManipulationException( "TEST {} WITH {} PARAM", 1, uuid );
        }
        catch (ManipulationException e)
        {
            assertSame( e.getCause(), null );
            assertEquals( e.getMessage(), "TEST 1 WITH " + uuid + " PARAM" );
        }
    }

    @Test
    public void testRestExceptionParams ()
    {
        try
        {
            throw new RestException( "TEST {} WITH {} PARAM", 1, uuid, exception );
        }
        catch (ManipulationException e)
        {
            assertSame( e.getCause(), exception );
            assertEquals( e.getMessage(), "TEST 1 WITH " + uuid + " PARAM" );
        }
    }


    @Test
    public void testRestException ()
    {
        try
        {
            throw new RestException( "TEST PARAM", exception );
        }
        catch (ManipulationException e)
        {
            assertSame( e.getCause(), exception );
            assertEquals( "TEST PARAM", e.getMessage() );
        }
    }
}