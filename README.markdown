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

# Either comment these lines out or change the appender to B when running
# Donkey in the foreground.
log4j.logger.JsonLogger=debug, JSON
log4j.additivity.JsonLogger=false

# Use this appender for logging JSON when running Donkey in the background.
log4j.appender.JSON=org.apache.log4j.RollingFileAppender
log4j.appender.JSON.File=/var/log/donkey/json.log
log4j.appender.JSON.layout=org.apache.log4j.PatternLayout
log4j.appender.JSON.layout.ConversionPattern=%d{MM-dd@HH:mm:ss} %-5p (%13F:%L) %3x - %m%n
log4j.appender.JSON.MaxFileSize=10MB
log4j.appender.JSON.MaxBackupIndex=1

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

## Listing Analysis Identifiers

Unsecured Endpoint: GET /get-all-analysis-ids

The export script needs to have a way to obtain the identifiers of all of the
analyses in the Discovery Environment, deleted or not.  This service provides
that information.  Here's an example listing:

```
$ curl -s http://by-tor:8888/get-all-analysis-ids | python -mjson.tool
{
    "analysis_ids": [
        "19F78CC1-7E14-481B-9D80-85EBCCBFFCAF", 
        "C5FF73E8-157F-47F0-978C-D4FAA12C2D58",
        ...
    ]
}
```

## Deleting Categories

Unsecured Endpoint: POST /delete-categories

Analysis categories can be deleted using the `/delete-categories` entpoint.
This service accepts a list of analysis category identifiers and deletes all
corresponding analysis categories.  The request body is in the following
format:

```json
{
    "category_ids": [
        category-id-1,
        category-id-2,
        ...
        category-id-n
    ]
}
```

The response contains a list of category ids for which the deletion failed in
the following format:

```json
{
    "failures": [
        category-id-1,
        category-id-2,
        ...
        category-id-n
    ]
}
```

Here's an example:

```
$ curl -sd '
{
    "category_ids": [
        "D901F356-D33E-4AE9-8F92-0A07CE9AD70E"
    ]
}
' http://by-tor:8888/delete-categories | python -mjson.tool
{
    "failures": []
}
```

## Valiating Analyses for Pipelines

Unsecured Endpoint: GET /validate-analysis-for-pipeline/{analysis-id}

Multistep analyses and empty analyses can't currently be included in
pipelines, so the UI needs a way to determine whether or not an analysis can
be included in a pipeline.  This service provides that information.  The
response body contains a flag indicating whether or not the analysis can be
included in a pipeline along with the reason.  If the analysis can be included
in a pipeline then the reason string will be empty.  The response format is:

```json
{
    "is_valid": flag,
    "reason", reason
}
```

Here are some examples:

```
$ curl -s http://by-tor:8888/validate-analysis-for-pipelines/9A39F7FA-4025-40E2-A720-489FA93C6A93 | python -mjson.tool
{
    "is_valid": true, 
    "reason": ""
}
```

```
$ curl -s http://by-tor:8888/validate-analysis-for-pipelines/BDB011B6-1F6B-443E-B94E-400930619978 | python -mjson.tool
{
    "is_valid": false, 
    "reason": "analysis, BDB011B6-1F6B-443E-B94E-400930619978, has too many steps for a pipeline"
}
```

## Listing Data Objects in an Analysis

Unsecured Endpoint: GET /analysis-data-objects/{analysis-id}

When a pipeline is being created, the UI needs to know what types of files are
consumed by and what types of files are produced by each analysis in the
pipeline.  This service provides that information.  The response body contains
the analysis identifier, the analysis name, a list of inputs (types of files
consumed by the service) and a list of outputs (types of files produced by the
service).  The response format is:

```json
{
    "id": analysis-id,
    "inputs": [
        {
            "data_object": {
                "cmdSwitch": command-line-switch,
                "description": description,
                "file_info_type": info-type-name,
                "format": data-format-name,
                "id": data-object-id,
                "multiplicity": multiplicity-name,
                "name": data-object-name,
                "required": required-data-object-flag,
                "retain": retain-file-flag,
            },
            "description": property-description,
            "id": property-id,
            "isVisible": visibility-flag,
            "label": property-label,
            "name": property-name,
            "type": "Input",
            "value": default-property-value
        },
        ...
    ]
    "name": analysis-name,
    "outputs": [
        {
            "data_object": {
                "cmdSwitch": command-line-switch,
                "description": description,
                "file_info_type": info-type-name,
                "format": data-format-name,
                "id": data-object-id,
                "multiplicity": multiplicity-name,
                "name": data-object-name,
                "required": required-data-object-flag,
                "retain": retain-file-flag,
            },
            "description": property-description,
            "id": property-id,
            "isVisible": visibility-flag,
            "label": property-label,
            "name": property-name,
            "type": "Output",
            "value": default-property-value
        },
        ...
    ]
}
```

Here's an example:

```
$ curl -s http://by-tor:8888/analysis-data-objects/19F78CC1-7E14-481B-9D80-85EBCCBFFCAF | python -mjson.tool
{
    "id": "19F78CC1-7E14-481B-9D80-85EBCCBFFCAF", 
    "inputs": [
        {
            "data_object": {
                "cmdSwitch": "", 
                "description": "", 
                "file_info_type": "File", 
                "format": "Unspecified", 
                "id": "A6210636-E3EC-4CD3-97B4-CAD15CAC0913", 
                "multiplicity": "One", 
                "name": "Input File", 
                "order": 1, 
                "required": true, 
                "retain": false
            }, 
            "description": "", 
            "id": "A6210636-E3EC-4CD3-97B4-CAD15CAC0913", 
            "isVisible": true, 
            "label": "Input File", 
            "name": "", 
            "type": "Input", 
            "value": ""
        }
    ], 
    "name": "Jills Extract First Line", 
    "outputs": [
        {
            "data_object": {
                "cmdSwitch": "", 
                "description": "", 
                "file_info_type": "File", 
                "format": "Unspecified", 
                "id": "FE5ACC01-0B31-4611-B81E-26E532B459E3", 
                "multiplicity": "One", 
                "name": "head_output.txt", 
                "order": 3, 
                "required": true, 
                "retain": true
            }, 
            "description": "", 
            "id": "FE5ACC01-0B31-4611-B81E-26E532B459E3", 
            "isVisible": false, 
            "label": "head_output.txt", 
            "name": "", 
            "type": "Output", 
            "value": ""
        }
    ]
}
```

## Categorizing Analyses

Unsecured Endpoint: POST /categorize_analyses

When services are exported and re-imported, the analysis categorization
information also needs to be exported and re-imported.  This service allows
the categorization information to be imported.  Strictly speaking, this
service can also be used to move analyses to new categories, but this service
hasn't been used for that purpose since Belphegor and Conrad were created.
This service is documented in detail in the Analysis Categorization Services
section of the [tool integration services wiki
page](https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services).

The request body for this service is in this format:

```json
{
    "categories": [
        {
            "category_path": {
                "path": [
                    root-category-name,
                    first-subcategory-name,
                    ...,
                    nth-subcategory-name
                ],
                "username": username
            }
            "analysis": {
                "name": analysis-name,
                "id": analysis-id
            }
        },
        ...
    ]
}
```

The response body format is identical to the request body format except that
only failed categorizations are listed and each categorization contains the
reason for the categorization failure.  Here's the format:

```json
{
    "failed_categorizations": [
        {
            "reason": reason-for-failure,
            "category_path": {
                "path": [
                    root-category-name,
                    first-subcategory-name,
                    ...,
                    nth-subcategory-name
                ],
                "username": username
            }
            "analysis": {
                "name": analysis-name,
                "id": analysis-id
            }
        },
        ...
    ]
}
```

Here's an example:

```
$ curl -sd '
{
    "categories": [
        {
            "analysis": {
                "id": "Foo", 
                "name": "Foo"
            }, 
            "category_path": {
                "username": "nobody@iplantcollaborative.org", 
                "path": [
                    "Public Applications", 
                    "Foo"
                ]
            }
        }
    ]
}
' http://by-tor:8888/categorize-analyses | python -mjson.tool
{
    "failed_categorizations": [
        {
            "categorization": {
                "analysis": {
                    "id": "Foo", 
                    "name": "Foo"
                }, 
                "category_path": {
                    "path": [
                        "Public Applications", 
                        "Foo"
                    ], 
                    "username": "nobody@iplantcollaborative.org"
                }
            }, 
            "reason": "analysis Foo not found"
        }
    ]
}
```

## Listing Analysis Categorizations

Unsecured Endpoint: GET /get-analysis-categories/{category-set}

This is the counterpart to the /categorize-analyses endpoint; it loads
categorizations from the database and produces output in the format required
by the /categorize-analyes endpoint.  The response body is in this format:

```json
{
    "categories": [
        {
            "category_path": {
                "path": [
                    root-category-name,
                    first-subcategory-name,
                    ...,
                    nth-subcategory-name
                ],
                "username": username
            }
            "analysis": {
                "name": analysis-name,
                "id": analysis-id
            }
        },
        ...
    ]
}
```

This service can export the categorizations for two different sets of
analyses as described in the following table:

<table>
    <tr><th>Category Set</th><th>Description</th></tr>
    <tr><td>all</td><td>All analysis categorizations</td></tr>
    <tr><td>public</td><td>Only public analysis categorizations</td></tr>
</table>

Note that when only public analysis categorizations are exported, private
categorizations for public analyses are not included in the service output.
This means that if an analysis happens to be both in the user's private
workspace and in a public workspace then only the categorization in the public
workspace will be included in the output from this service.  Here's an
example:

```
$ curl -s http://by-tor:8888/get-analysis-categories/public | python -mjson.tool
{
    "categories": [
        {
            "analysis": {
                "id": "839E7AFA-031E-4DB8-82A6-AEBD56E9E0B9", 
                "name": "hariolf-test-12"
            }, 
            "category_path": {
                "path": [
                    "Public Applications", 
                    "Beta"
                ], 
                "username": "<public>"
            }
        },
        ...
    ]
}
```

## Determining if an Analysis Can be Exported

Unsecured Endpoint: POST /can-export-analysis

Some analyses can't be exported to Tito because they contain no steps, contain
multiple steps or contain types of properties that have been deprecated and
are no longer supported in Tito.  The UI uses this service to determine
whether or not an analysis can be exported to Tito before attempting to do
so.  The request body for this service is in this format:

```json
{
    "analysis_id": analysis-id
}
```

If the analysis can be exported then the response body will be in this format:

```json
{
    "can-export": true
}
```

If the analysis can't be exported then the response body will be in this
format:

```json
{
    "can-export": false,
    "cause": reason
}
```

Here are some examples:

```
$ curl -sd '{"analysis_id": "BDB011B6-1F6B-443E-B94E-400930619978"}' http://by-tor:8888/can-export-analysis | python -mjson.tool
{
    "can-export": false, 
    "cause": "Multi-step applications cannot be copied or modified at this time."
}
```

```
$ curl -sd '{"analysis_id": "19F78CC1-7E14-481B-9D80-85EBCCBFFCAF"}' http://by-tor:8888/can-export-analysis | python -mjson.tool
{
    "can-export": true
}
```

## Adding Analyses to Analysis Groups

Unsecured Endpoint: POST /add-analyses-to-group

Users in the Discovery Environment can add analyses to an analysis groups in
some cases.  The most common use case for this feature is when the user wants
to add an existing analysis to his or her favorites.  The request body for
this service is in this format:

```json
{
    "analysis_id": analysis-id,
    "groups": [
        group-id-1,
        group-id-2,
        ...,
        group-id-n
    ]
}
```

If the service succeeds then the response body is an empty JSON object.
Here's an example:

```
$ curl -sd '
{
    "analysis_id": "9BCCE2D3-8372-4BA5-A0CE-96E513B2693C",
    "groups": [
        "028fce65-2504-4497-a20c-45e3cf8583b8"
    ]
}
' http://by-tor:8888/add-analysis-to-group | python -mjson.tool
{}
```

## Getting Apps in the JSON Format Required by the DE

Unsecured Endpoint: GET /get-analysis/{analysis-id}

The purpose of this endpoint is to provide a way to determine what the JSON
for an analysis will look like when it is obtained by the DE.  The DE itself
uses a secured endpoint that performs the same task, but there was no reason
to require a user to be authenticated in order to obtain this information.  We
left this endpoint in place despite the fact that it's not used by the DE
because it's convenient for debugging.

The response body for this service is in the following format:

```json
{
    "groups": [
        {
            "id": property-group-id,
            "label": property-group-label,
            "name": property-group-name,
            "properties": [
                {
                    "description": property-description,
                    "id": unique-property-id,
                    "isVisible": visibility-flag,
                    "label": property-label,
                    "name": property-name,
                    "type": property-type-name,
                    "validator": {
                        "id": validator-id,
                        "label": validator-label,
                        "name": validator-name,
                        "required": required-flag,
                        "rules": [
                            {
                                rule-type: [
                                    rule-arg-1,
                                    rule-arg-2,
                                    ...,
                                    rule-arg-n
                                ],
                            },
                            ...
                        ]
                    },
                    "value": default-property-value
                },
                ...
            ],
            "type": property-group-type
        },
        ...
    ]
    "id": analysis-id,
    "label": analysis-label,
    "name": analysis-name,
    "type": analysis-type
}
```

Here's an example:

```
$ curl -s http://by-tor:8888/get-analysis/9BCCE2D3-8372-4BA5-A0CE-96E513B2693C | python -mjson.tool
{
    "groups": [
        {
            "id": "idPanelData1", 
            "label": "Select FASTQ file", 
            "name": "FASTX Trimmer - Select data:", 
            "properties": [
                {
                    "description": "", 
                    "id": "step_1_ta2eed78a0e924e6ba4fec03d929d905b_DE79E631-A10A-9C36-8764-506E3B2D59BD", 
                    "isVisible": true, 
                    "label": "Select FASTQ file:", 
                    "name": "-i ", 
                    "type": "FileInput", 
                    "validator": {
                        "label": "", 
                        "name": "", 
                        "required": true
                    }
                }
            ], 
            "type": "step"
        },
        ...
    ], 
    "id": "9BCCE2D3-8372-4BA5-A0CE-96E513B2693C", 
    "label": "FASTX Workflow", 
    "name": "FASTX Workflow", 
    "type": ""
}
```

## Listing Analysis Groups

Unsecured Endpoint: GET /get-only-analysis-groups/{workspace-token}

This service is used by the DE and (indirectly) by Tito to obtain the list of
analysis groups that are visible to the user.  This list includes analysis
groups that are in the user's workspace along with any analysis groups that
are in a workspace that is marked as public in the database.  The
`workspace-token` argument can either be the workspace ID or the user's fully
qualified username.  (The DE sends the workspace ID; Tito sends the username.)
The response is in the following format:

```json
{
    "groups": [
        {
            "description": analysis-group-description,
            "groups": [
               ...
            ],
            "id": analysis-group-id,
            "is_public": public-flag,
            "name": analysis-group-name,
            "template_count": template-count
        }
    ]
}
```

Note that this data structure is recursive; each analysis group may contain
zero or more other analysis groups.

Here's an example using a workspace ID:

```
$ curl -s http://by-tor:8888/get-only-analysis-groups/4 | python -mjson.tool
{
    "groups": [
        {
            "description": "", 
            "groups": [
                {
                    "description": "", 
                    "id": "b9a1a3b8-fef6-4576-bbfe-9ad17eb4c2ab", 
                    "is_public": false, 
                    "name": "Applications Under Development", 
                    "template_count": 0
                }, 
                {
                    "description": "", 
                    "id": "2948ed96-9564-489f-ad73-e099b171a9a5", 
                    "is_public": false, 
                    "name": "Favorite Applications", 
                    "template_count": 0
                }
            ], 
            "id": "57a39832-3577-4ee3-8ff4-3fc9d1cf9e34", 
            "is_public": false, 
            "name": "Workspace", 
            "template_count": 0
        },
        ...
    ]
}
```

Here's an example using a username:

```
$ curl -s http://by-tor:8888/get-only-analysis-groups/ipctest@iplantcollaborative.org | python -mjson.tool
{
    "groups": [
        {
            "description": "", 
            "groups": [
                {
                    "description": "", 
                    "id": "b9a1a3b8-fef6-4576-bbfe-9ad17eb4c2ab", 
                    "is_public": false, 
                    "name": "Applications Under Development", 
                    "template_count": 0
                }, 
                {
                    "description": "", 
                    "id": "2948ed96-9564-489f-ad73-e099b171a9a5", 
                    "is_public": false, 
                    "name": "Favorite Applications", 
                    "template_count": 0
                }
            ], 
            "id": "57a39832-3577-4ee3-8ff4-3fc9d1cf9e34", 
            "is_public": false, 
            "name": "Workspace", 
            "template_count": 0
        },
        ...
    ]
}
```

## Exporting a Template

Unsecured Endpoint: GET /export-template/{template-id}

This service exports a template in a format similar to the format required by
Tito.  This service is not used by the DE and has been superceded by the
secured `/edit-template` and `/copy-template` endpoints.  The response body
for this service is fairly large, so it will not be documented in this file.
For all of the gory details, see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Exporting an Analysis

Unsecured Endpoint: GET /export-workflow/{analysis-id}

This service exports an analysis in the format used to import multi-step
analyses into the DE.  Note that this format will work for both single- and
multi-step analyses.  This service is used by the export script to export
analyses from the DE.  The response body for this service is fairly large, so
it will not be documented in this file.  For all of the gory details, see the
(Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Permanently Deleting an Analysis

Unsecured Endpoint: POST /permanently-delete-workflow

This service physically removes an analysis from the database, which allows
administrators to completely remove analyses that are causing problems.  As
far as I know, this service hasn't been used in quite a while, and it can
probably be removed at some point in the near future.  The request body is in
the following format for the deletion of a private analysis:

```json
{
    "analysis_id": analysis-id,
    "full_username": username
}
```

This service also supports deleting analyses by name, but this practice isn't
recommended because analysis names are not guaranteed to be unique.  When
deletion by name is requested, the request body is in this format for the
deletion of a private analysis:

```json
{
    "analysis_name": analysis-name,
    "full_username": username
}
```

Public analyses may be deleted by this service as well, but the service has to
be explicitly told that a public analysis is being deleted.  The request body
for the deletion of a public analysis by ID is in this format:

```json
{
    "analysis_id": analysis-id,
    "root_deletion_request": true
}
```

Similarly, the request body for the deletion of a public analysis by name is
in this format:

```json
{
    "analysis_name": analysis-name,
    "root_deletion_request": true
}
```

This service has no response body.

For more information about this service, please see the (Tool Integration
Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Logically Deleting an Analysis

Unsecured Endpoint: POST /delete-workflow

This service works in exactly the same way as the
`/permanently-delete-workflow` service except that, instead of permanently
deleting the analysis, it merely marks the analysis as deleted.  This prevents
the analysis from being displayed in the DE, but retains its definition so
that it can be restored later if necessary.  For more information about this
service, please see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Previewing Templates

Unsecured Endpoint: POST /preview-template

Tito uses this service (indirectly) to allow users to preview the UI for a
template that is being edited.  The request body for this service is in the
format required by the `/import-template` service.  The response body for this
service is the in the format produced by the `/get-analysis` service.  For
more information about this service, please see the (Tool Integration Services
wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Previewing Analyses

Unsecured Endpoint: POST /preview-workflow

The purpose of this service is to preview the JSON that would be fed to the UI
for an analysis.  The request body for this service is in the format required
by the `/import-workflow` service.  The response body for this service is in
the format produced by the `/get-analysis` service.  For more information
about this service, please see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Updating an Existing Template

Unsecured Endpoint: POST /update-template

This service either imports a new template or updates an existing template in
the database.  For more information about this service, please see the (Tool
Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Updating an Analysis

Unsecured Endpoint: POST /update-workflow

This service either imports a new analysis or updates an existing analysis in
the database (as long as the analysis has not been submitted for public use).
The difference between this service and the `/update-template` service is that
this service can support multi-step analyses.  For more information about this
service, please see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Forcing an Analysis to be Updated

Unsecured Endpoint: POST /force-update-workflow

The `/update-workflow` service only allows private analyses to be updated.
Analyses that have been submitted for public use must be updated using this
service.  The analysis import script uses this service to import analyses that
have previously been exported.  For more information about this service,
please see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Importing a Template

Unsecured Endpoint: POST /import-template

This service imports a new template into the DE; it will not overwrite an
existing template.  To overwrite an existing template, please use the
`/update-template` service.  For more information about this service, please
see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Importing an Analysis

Unsecured Endpoint: POST /import-workflow

This service imports a new analysis into the DE; it will not overwrite an
existing analysis.  To overwrite an existing analysis, please use the
`/update-workflow` service.  For more information about this service, please
see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

## Obtaining Property Values for a Previously Executed Job

Unsecured Endpoint: GET "/get-property-values/{job-id}"

This service obtains the property values that were passed to a job that has
already been executed so that the user can see which values were passed to the
job.  The response body is in the following format:

```json
{
    "parameters": [
        {
            "param_id": parameter-id,
            "param_name": parameter-name,
            "param_value": parameter-value,
            "param_type": parameter-type,
            "info_type": info-type-name,
            "data_format": data-format-name
        },
        ...
    ]
}
```

Note that the information type and data format only apply to input files.  For
other types of parameters, these fields will be blank.

Here's an example:

```
$ curl -s http://by-tor:8888/get-property-values/j10abd1e2-5a13-4cfc-8092-b23632dd18c7 | python -mjson.tool
{
    "parameters": [
        {
            "data_format": "", 
            "info_type": "", 
            "param_id": "51B9AD98-B0EF-7FE4-74B9-7ADEBADE6024", 
            "param_name": "Minimum quality score to keep", 
            "param_type": "Number", 
            "param_value": "20"
        }, 
        {
            "data_format": "", 
            "info_type": "", 
            "param_id": "BCCB23E7-02A5-8F7A-A4FA-EE49249D6FC0", 
            "param_name": "Minimum percent of bases that must have this quality", 
            "param_type": "Number", 
            "param_value": "50"
        }, 
        {
            "data_format": "", 
            "info_type": "", 
            "param_id": "85BDEF8F-CD54-F5DB-4A86-51174790D1AD", 
            "param_name": "FASTQ data is in Sanger (PHRED33) format", 
            "param_type": "Flag", 
            "param_value": ""
        }, 
        {
            "data_format": "Unspecified", 
            "info_type": "File", 
            "param_id": "4654B648-676C-2B9E-A92B-6A9F22B64AE1", 
            "param_name": "Select FASTQ file:", 
            "param_type": "Input", 
            "param_value": "/iplant/home/dennis/read1_10k.fq"
        }
    ]
}
```
