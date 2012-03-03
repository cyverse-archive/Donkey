# Donkey

Donkey is a platform for hosting Services written in Clojure; it's intended to
be the replacement for the Mule instance that used to run inside the Discovery
Environment web application.

*Important Note:* All of the services that used to run inside Mule now run
inside Donkey instead.  The Mule services no longer exist.

## Installing and Configuring Donkey

Donkey is packaged as an RPM and published in iPlant's YUM repositories.  It
can be installed using `yum install donkey` and upgraded using `yum upgrade
donkey`.

### Primary Configuration

Donkey gets most of its configuration settings from Apache Zookeeper.  These
configuration setting are uploaded to Zookeeper using Clavin, a command-line
tool maintained by iPlant that allows configuration properties and access
control lists to easily be uploaded to Zookeeper.  Please see the Clavin
documentation for information about how to upload configuration settings.
Here's an example configuration file:

```properties
# Connection details.
donkey.app.listen-port = 8888

# Database settings.
donkey.db.driver      = org.postgresql.Driver
donkey.db.subprotocol = postgresql
donkey.db.host        = hostname.iplantcollaborative.org
donkey.db.port        = 5432
donkey.db.name        = database
donkey.db.user        = user
donkey.db.password    = password

# Hibernate resource definition files.
donkey.hibernate.resources = template-mapping.hbm.xml, \
                             notifications.hbm.xml, \
                             workflow.hbm.xml

# Java packages containing classes with JPA Annotations.
donkey.hibernate.packages = org.iplantc.persistence.dto.step, \
                            org.iplantc.persistence.dto.transformation, \
                            org.iplantc.persistence.dto.data, \
                            org.iplantc.persistence.dto.workspace, \
                            org.iplantc.persistence.dto.user, \
                            org.iplantc.persistence.dto.components, \
                            org.iplantc.persistence.dto.listing, \
                            org.iplantc.workflow.core

# The Hibernate dialect to use.
donkey.hibernate.dialect = org.hibernate.dialect.PostgreSQLDialect

# Zoidberg connection settings.
donkey.zoidberg.base-url           = http://hostname.iplantcollaborative.org:8888
donkey.zoidberg.connection-timeout = 5000
donkey.zoidberg.encoding           = UTF-8

# OSM connection settings.
donkey.osm.base-url           = http://hostname.iplantcollaborative.org:8888
donkey.osm.connection-timeout = 5000
donkey.osm.encoding           = UTF-8
donkey.osm.jobs-bucket        = jobs
donkey.osm.job-request-bucket = job_requests

# JEX connection settings.
donkey.jex.base-url = http://hostname.iplantcollaborative.org:8888

# Notification agent connection settings.
donkey.notificationagent.base-url = http://hostname.iplantcollaborative.org:8888

# Workspace app group names.
donkey.workspace.root-app-group            = Workspace
donkey.workspace.default-app-groups        = [ "Applications Under Development", \
                                               "Favorite Applications" ]
donkey.workspace.dev-app-group-index       = 0
donkey.workspace.favorites-app-group-index = 1

# CAS Settings
donkey.cas.cas-server  = https://hostname.iplantcollaborative.org/cas/
donkey.cas.server-name = http://hostname.iplantcollaborative.org:8888

# The domain name to append to the user id to get the fully qualified user id.
donkey.uid.domain = iplantcollaborative.org
```

Generally, the database and service connection settings will have to be
updated for each deployment.

### Zookeeper Connection Information

One piece of information that can't be stored in Zookeeper is the information
required to connect to Zookeeper.  For Donkey and most other iPlant services,
this information is stored in a single file:
`/etc/iplant-services/zkhosts.properties`.  This file is automatically
installed when the iplant-service-configs RPM is installed.  You may have to
modify this file so that it points to the correct hosts.

### Logging Configuration

The logging settings are stored in `/etc/donkey/log4j.properties`.  The file
looks like this by default:

```properties
log4j.rootLogger=WARN, A

# Uncomment these lines to enable debugging for iPlant classes.
# log4j.category.org.iplantc=DEBUG, A
# log4j.additivity.org.iplantc=false

# Uncomment these lines to enable debugging in Donkey itself.
# log4j.category.donkey=DEBUG, A
# log4j.additivity.donkey=false

# Uncomment these lines to enable debugging in iPlant Clojure Commons.
# log4j.category.clojure-commons=DEBUG, A
# log4j.additivity.clojure-commons=false

# Use this appender when running Donkey in the foreground.
log4j.appender.B=org.apache.log4j.ConsoleAppender
log4j.appender.B.layout=org.apache.log4j.PatternLayout
log4j.appender.B.layout.ConversionPattern=%d{MM-dd@HH:mm:ss} %-5p (%13F:%L) %3x - %m%n

# Use this appender when running Donkey in the background.
log4j.appender.A=org.apache.log4j.RollingFileAppender
log4j.appender.A.File=/var/log/donkey/donkey.log
log4j.appender.A.layout=org.apache.log4j.PatternLayout
log4j.appender.A.layout.ConversionPattern=%d{MM-dd@HH:mm:ss} %-5p (%13F:%L) %3x - %m%n
log4j.appender.A.MaxFileSize=10MB
log4j.appender.A.MaxBackupIndex=1
```

The most useful configuration change here is to enable debugging for iPlant
classes, which can be done by uncommenting two lines.  In rare cases, it may
be helpful to enable debugging in Donkey and iPlant Clojure Commons.  Most of
the logic in Donkey is implemented in Java classes that are underneath the
org.iplantc package, however, so enabling debugging for those classes will be
the most helpful.

See the [log4j documentation](http://logging.apache.org/log4j/1.2/manual.html)
for additional logging configuration instructions.

## Services

All URLs referenced below are listed as relative URLs with value names
enclosed in braces.  For example, the service to get a list of workflow
elements is accessed using the URL, `/get-workflow-elements/{element-type}`.
Where `{element-type}` refers to the type of workflow element that is being
retrieved.  For example, to get a list of known property types, you can access
the URL, `/get-workflow-elements/property-types`.  On the other hand, all
examples use fully qualified URLs.

Request and response bodies are in JSON format unless otherwise noted.  to
avoid confusion between the braces used to denote JSON objects and the braces
used to denote example values, example values in JSON bodies are not enclosed
in braces, but instead listed as hyphen-separated names without enclosing
quotes.

Several services in Donkey require user authentication, which is managed by
CAS service tickets that are passed to the service in the `proxyToken` query
parameter.  For example, the first service that the Discovery Environment hits
when a user logs in is the bootstrap service, which does require user
authentication.  This service can be accessed using the URL,
`/bootstrap?proxyToken={some-service-ticket}` where {some-service-ticket}
refers to a service ticket string that has been obtained from CAS.

Secured services can be distinguished from unsecured services by looking at
the source code (or this document).  All endpoints are defined in the file,
`core.clj`.  Endpoints defined with GET, POST, PUT, DELETE, HEAD and ANY
macros are not secured.  Endpoints defined with FILTERED-GET, FILTERED-POST,
FILTERED-PUT, FILTERED-DELETE, FILTERED-HEAD and FILTERED-ANY macros are
secured.  In the documentation below, services that are not secured will be
labeled as unsecured endpoints and services that are secured will be labeled
as secured endpoints.

### Verifying that Donkey is Running

Unsecured Endpoint: GET /

The root path in Donkey can be used to verify that Donkey is actually running
and is responding.  Currently, the response to this URL contains only a
welcome message.  Here's an example:

```
$ curl -s http://by-tor:8888/
Welcome to Donkey!  I've mastered the stairs!
```

### Listing Workflow Elements

Unsecured Endpoint: GET /get-workflow-elements/{element-type}

The `/get-workflow-elements/{element-type}` endpoint is used by Tito to obtain
lists of elements that may be included in an app.  The following element types
are currently supported:

<table "border=1">
    <tr><th>Element Type</th><th>Description</th></tr>
    <tr><td>components</td><td>Registered deployed components</td></tr>
    <tr><td>formats</td><td>Known file formats</td></tr>
    <tr><td>info-types</td><td>Known types of data</td></tr>
    <tr><td>property-types</td><td>Known types of parameters</td></tr>
    <tr><td>rule-types</td><td>Known types of validation rules</td></tr>
    <tr><td>value-types</td><td>Known types of parameter values</td></tr>
    <tr><td>all</td><td>All workflow element types</td></tr>
</table>

The response format varies depending on the type of information that is being
returned.

Deployed components represent tools (usually, command-line tools) that can be
executed from within the discovery environment.  Here's an example deployed
components listing:

```
$ curl -s http://by-tor:8888/get-workflow-elements/components | python -mjson.tool
{
    "components": [
        {
            "attribution": "Insane Membranes, Inc.", 
            "description": "You'll find out!", 
            "hid": 320, 
            "id": "c718a4715484949a1bf0892e28324f64f", 
            "location": "/usr/blah/bin", 
            "name": "foo.pl", 
            "type": "executable", 
            "version": "0.0.1"
        }, 
        ...
    ]
}
```

The known file formats can be used to describe supported input or output
formats for a deployed component.  For example, tools in the FASTX toolkit may
support FASTA files, several different varieties of FASTQ files and Barcode
files, among others.  Here's an example listing:

```
$ curl -s http://by-tor:8888/get-workflow-elements/formats | python -mjson.tool
{
    "formats": [
        {
            "hid": 1, 
            "id": "E806880B-383D-4AD6-A4AB-8CDD88810A33", 
            "label": "Unspecified Data Format", 
            "name": "Unspecified"
        }, 
        {
            "hid": 3, 
            "id": "6C4D09B3-0108-4DD3-857A-8225E0645A0A", 
            "label": "FASTX toolkit barcode file", 
            "name": "Barcode-0"
        }, 
        ...
    ]
}
```

The known information types can be used to describe the type of information
consumed or produced by a deployed component.  This is distinct from the data
format because some data formats may contain multiple types of information and
some types of information can be described using multiple data formats.  For
example, the Nexus format can contain multiple types of information, including
phylogenetic trees.  And phylogenetic trees can also be represented in
PhyloXML format, and a large number of other formats.  The file format and
information type together identify the type of input consumed by a deployed
component or the type of output produced by a deployed component.  here's an
example information type listing:

```
$ curl -s http://by-tor:8888/get-workflow-elements/info-types | python -mjson.tool
{
    "info_types": [
        {
            "hid": 3, 
            "id": "0900E992-3BBD-4F4B-8D2D-ED289CA4E4F1", 
            "label": "Unspecified", 
            "name": "File"
        }, 
        {
            "hid": 6, 
            "id": "0E3343E3-C59A-44C4-B5EE-D4501EC3A898", 
            "label": "Reference Sequence and Annotations", 
            "name": "ReferenceGenome"
        }, 
        ...
    ]
}
```

Property types represent the types of information that can be passed to a
deployed component.  For command-line tools, a property generally represents a
command-line option and the property type represents the type of data required
by the command-line option.  For example a `Boolean` property generally
corresponds to a single command-line flag that takes no arguments.  A `Text`
property, on the other hand, generally represents some sort of textual
information.  Here's an example listing:

```
$ curl -s http://by-tor:8888/get-workflow-elements/property-types | python -mjson.tool
{
    "property_types": [
        {
            "description": "A text box (no caption or number check)", 
            "hid": 12, 
            "id": "ptffeca61a-f1b9-43ba-b6ff-fa77bb34f396", 
            "name": "Text", 
            "value_type": "String"
        }, 
        {
            "description": "A text box that checks for valid number input", 
            "hid": 1, 
            "id": "ptd2340f11-d260-41b4-93fd-c1d695bf6fef", 
            "name": "Number", 
            "value_type": "Number"
        }, 
        ...
    ]
}
```

Rule types represent types of validation rules that may be defined to validate
user input.  For example, if a property value must be an integer between 1 and
10 then the `IntRange` rule type may be used.  Similarly, if a property value
must contain data in a specific format, such as a phone number, then the
`Regex` rule type may be used.  Here's an example listing:

```
$ curl -s http://by-tor:8888/get-workflow-elements/rule-types | python -mjson.tool
{
    "rule_types": [
        {
            "description": "Has a range of integers allowed", 
            "hid": 3, 
            "id": "rte04fb2c6-d5fd-47e4-ae89-a67390ccb67e", 
            "name": "IntRange", 
            "rule_description_format": "Value must be between: {Number} and {Number}.", 
            "subtype": "Integer", 
            "value_types": [
                "Number"
            ]
        }, 
        {
            "description": "Has a range of values allowed (non-integer)", 
            "hid": 6, 
            "id": "rt58cd8b75-5598-4490-a9c9-a6d7a8cd09dd", 
            "name": "DoubleRange", 
            "rule_description_format": "Value must be between: {Number} and {Number}.", 
            "subtype": "Double", 
            "value_types": [
                "Number"
            ]
        },
    ]
}
```

If you look closely at the example property type and rule type listings then
you'll notice that each property type has a single value type assocaited with
it and each rule type has one or more value types associated with it.  The
purpose of value types is specifically to link property types and rule types.
Tito uses the value type to determine which types of rules can be applied to a
property that is being defined by the user.  Here's an example value type
listing:

```
$ curl -s http://by-tor:8888/get-workflow-elements/value-types | python -mjson.tool
{
    "value_types": [
        {
            "description": "Arbitrary text", 
            "hid": 1, 
            "id": "0115898A-F81A-4598-B1A8-06E538F1D774", 
            "name": "String"
        }, 
        {
            "description": "True or false value", 
            "hid": 2, 
            "id": "E8E05E6C-5002-48C0-9167-C9733F0A9716", 
            "name": "Boolean"
        }, 
        ...
    ]
}
```

As a final option, it is possible to get all types of workflow elements at
once using an element type of `all`.  Here's an example listing:

```
$ curl -s http://by-tor:8888/get-workflow-elements/all | python -mjson.tool
{
    "components": [
        {
            "attribution": "Insane Membranes, Inc.", 
            "description": "You'll find out!", 
            "hid": 320, 
            "id": "c718a4715484949a1bf0892e28324f64f", 
            "location": "/usr/local2/bin", 
            "name": "foo.pl", 
            "type": "executable", 
            "version": "0.0.1"
        },
        ...
    ], 
    "formats": [
        {
            "hid": 1, 
            "id": "E806880B-383D-4AD6-A4AB-8CDD88810A33", 
            "label": "Unspecified Data Format", 
            "name": "Unspecified"
        },
        ...
    ], 
    "info_types": [
        {
            "hid": 3, 
            "id": "0900E992-3BBD-4F4B-8D2D-ED289CA4E4F1", 
            "label": "Unspecified", 
            "name": "File"
        },
        ...
    ], 
    "property_types": [
        {
            "description": "A text box (no caption or number check)", 
            "hid": 12, 
            "id": "ptffeca61a-f1b9-43ba-b6ff-fa77bb34f396", 
            "name": "Text", 
            "value_type": "String"
        },
        ...
    ], 
    "rule_types": [
        {
            "description": "Has a range of integers allowed", 
            "hid": 3, 
            "id": "rte04fb2c6-d5fd-47e4-ae89-a67390ccb67e", 
            "name": "IntRange", 
            "rule_description_format": "Value must be between: {Number} and {Number}.", 
            "subtype": "Integer", 
            "value_types": [
                "Number"
            ]
        },
        ...
    ], 
    "value_types": [
        {
            "description": "Arbitrary text", 
            "hid": 1, 
            "id": "0115898A-F81A-4598-B1A8-06E538F1D774", 
            "name": "String"
        },
        ...
    ]
}
```
