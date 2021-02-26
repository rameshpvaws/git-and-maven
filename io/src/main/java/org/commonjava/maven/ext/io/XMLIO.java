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

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

@Named
@Singleton
public class XMLIO
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final DocumentBuilder builder;
    private final Transformer transformer;

    public XMLIO()
    {
        try
        {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty( OutputKeys.INDENT, "yes");
            transformer.setOutputProperty( OutputKeys.ENCODING, StandardCharsets.UTF_8.name() );
            transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, "yes" );
        }
        catch ( ParserConfigurationException e )
        {
            logger.error( "Unable to create new DocumentBuilder", e );
            throw new RuntimeException("Unable to create new DocumentBuilder" );
        }
        catch ( TransformerConfigurationException e )
        {
            logger.error( "Unable to create new Transformer", e );
            throw new RuntimeException("Unable to create new Transformer" );
        }
    }


    public Document parseXML ( final File xmlFile) throws ManipulationException
    {
        if ( xmlFile == null || !xmlFile.exists() )
        {
            logger.error( "Unable to locate XML File {} ", xmlFile );
            throw new ManipulationException( "XML file ({}) not found.", xmlFile);
        }
        Document doc;
        try
        {
            doc = builder.parse( xmlFile);
        }
        catch ( SAXException | IOException e )
        {
            logger.error( "Unable to parse XML File", e );
            throw new ManipulationException( "Unable to parse XML File", e );
        }
        return doc;
    }


    public void writeXML (File target, Document contents) throws ManipulationException
    {
        try
        {
            // TODO: https://stackoverflow.com/questions/24551962/adding-linebreak-in-xml-file-before-root-node
            // TODO: Can't get a clean round trip due to newline differences.
            // StreamResult result = new StreamResult( new FileWriter( target));
            // transformer.transform( new DOMSource( contents ), result);
            String result = convert( contents );
            // Adjust for comment before root node and possibly insert a newline.
            result = result.replaceFirst("(?s)(<!--.*-->)<", "$1\n<");
            FileUtils.writeStringToFile( target, result, StandardCharsets.UTF_8);
        }
        catch ( IOException e )
        {
            logger.error( "XML transformer failure", e );
            throw new ManipulationException( "XML transformer failure", e );
        }
    }

    public String convert( Document contents ) throws ManipulationException
    {
        StringWriter outWriter = new StringWriter();
        try
        {
            StreamResult streamResult = new StreamResult( outWriter );
            transformer.transform( new DOMSource( contents ), streamResult);
        }
        catch ( TransformerException e )
        {
            logger.error( "XML transformer failure", e );
            throw new ManipulationException( "XML transformer failure", e );
        }
        return outWriter.toString();
    }
}
