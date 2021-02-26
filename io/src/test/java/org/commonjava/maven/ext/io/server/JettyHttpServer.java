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
package org.commonjava.maven.ext.io.server;

import org.commonjava.maven.ext.io.server.exception.ServerInternalException;
import org.commonjava.maven.ext.io.server.exception.ServerSetupException;
import org.commonjava.test.http.util.PortFinder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;

import java.net.InetSocketAddress;

/**
 * @author vdedik@redhat.com
 */
public class JettyHttpServer
        implements HttpServer
{
    private Integer port;

    private final Server jettyServer;

    private Handler handler;

    public JettyHttpServer( Handler handler )
    {
        this( handler, PortFinder.findOpenPort(5) );
    }

    public JettyHttpServer( Handler handler, Integer port )
    {
        this.port = port;
        this.handler = handler;
        this.jettyServer = createAndStartJetty( port );
    }

    public Integer getPort()
    {
        return this.port;
    }

    public void shutdown()
    {
        try
        {
            this.jettyServer.stop();
        }
        catch ( Exception e )
        {
            throw new ServerInternalException( "Error shutting down jetty", e );
        }
    }

    private Server createAndStartJetty( Integer port )
    {
        Server jetty = new Server(new InetSocketAddress( "127.0.0.1", this.port ) );
        jetty.setHandler( handler );

        try
        {
            jetty.start();
        }
        catch ( Exception e )
        {
            throw new ServerSetupException( "Error starting jetty on port " + port, e );
        }

        return jetty;
    }
}
