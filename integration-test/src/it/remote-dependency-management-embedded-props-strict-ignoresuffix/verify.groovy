/**
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
def pomFile = new File( basedir, 'pom.xml' )
System.out.println( "Slurping POM: ${pomFile.getAbsolutePath()}" )

def pom = new XmlSlurper().parse( pomFile )

// Junit
def passed = false
pom.properties.each {
    if ( it.text().contains ("4.1.0") )
    {
        passed = true
    }
}
assert (passed == true)
pom.properties.each {
    if ( it.text().contains ("3.1-redhat-4") )
    {
        passed = true
    }
}
assert (passed == true)

def message = 0

pomFile.eachLine {
    if ( it.contains ( "{commons.lang.major}.6.0.redhat-4" ) ) {
        message++
    }
}

assert message == 1
