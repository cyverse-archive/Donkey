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

# Metadactyl connection settings
donkey.metadactyl.base-url = http://hostname.iplantcollaborative.org:8888/metadactyl/secured
donkey.metadactyl.unprotected-base-url = http://hostname.iplantcollaborative.org:8888/metadactyl

# Notification agent connection settings.
donkey.notificationagent.base-url = http://hostname.iplantcollaborative.org:8888/notificationagent

# CAS Settings
donkey.cas.cas-server  = https://hostname.iplantcollaborative.org/cas/
donkey.cas.server-name = http://hostname.iplantcollaborative.org:8888

# The domain name to append to the user id to get the fully qualified user id.
donkey.uid.domain = iplantcollaborative.org

# User session settings
donkey.sessions.base-url = http://hostname.iplantcollaborative.org:8888/sessions/
donkey.sessions.bucket = sessions
```

Generally, the service connection settings will have to be updated for each
deployment.

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

See the [log4j documentation](http://logging.apache.org/log4j/1.2/manual.html)
for additional logging configuration instructions.

## Services

Of course, the primary reason for the existence of Donkey is to act as a
platform for hosting services.  Donkey services are defined using Compojure, a
framework for creating web services in Clojure.  Each service matches a
specific HTTP method and URL pattern, which causes one or more function calls
to be performed.  The services themselves are defined in the file `core.clj`
and, in most cases, Donkey forwards these requests to services defined in files
named for their corresponding service.

### Security

Several services in Donkey require user authentication, which is managed by
CAS service tickets that are passed to the service in the `proxyToken` query
parameter.  For example, the first service that the Discovery Environment hits
when a user logs in is the bootstrap service, which does require user
authentication.  This service can be accessed using the URL,
`/bootstrap?proxyToken={some-service-ticket}` where {some-service-ticket}
refers to a service ticket string that has been obtained from CAS.

Secured services can be distinguished from unsecured services by looking at
the path in the URL.  The paths for all secured endpoints begin with
`/secured` whereas the paths for all other endpoints do not.  In the
documentation below, services that are not secured will be labeled as
unsecured endpoints and services that are secured will be labeled as secured
endpoints.

If authentication or authorization fails for a secured service then an HTTP
401 (unauthorized) status will result, and there will be no response body,
even if the service normally has a response body.

### Errors

If a service call causes an exception that is not caught by the service itself
then Donkey will respond with a standardized error message:

```json
{
    "success": false,
    "reason": reason-for-error
}
```

The HTTP status code that is returned will either be a 400 or a 500, depending
on which type of exception is caught.  In either case, the reason for the
error should be examined.  If the logging level is set to _error_ or lower
then the exception will be logged in Donkey's log file along with a stack
trace.  This can be helpful in cases where the true cause of the error isn't
obvious at first.

### Endpoints

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

#### Verifying that Donkey is Running

Unsecured Endpoint: GET /

The root path in Donkey can be used to verify that Donkey is actually running
and is responding.  Currently, the response to this URL contains only a
welcome message.  Here's an example:

```
$ curl -s http://by-tor:8888/
Welcome to Donkey!  I've mastered the stairs!
```

#### Listing Workflow Elements

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

#### Listing Analysis Identifiers

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

#### Deleting Categories

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

#### Valiating Analyses for Pipelines

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

#### Listing Data Objects in an Analysis

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

#### Categorizing Analyses

Unsecured Endpoint: POST /categorize-analyses

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

#### Listing Analysis Categorizations

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

#### Determining if an Analysis Can be Exported

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

#### Adding Analyses to Analysis Groups

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

#### Getting Analyses in the JSON Format Required by the DE

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

#### Listing Analysis Groups

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
$ curl -s http://by-tor:8888/get-only-analysis-groups/nobody@iplantcollaborative.org | python -mjson.tool
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

#### Exporting a Template

Unsecured Endpoint: GET /export-template/{template-id}

This service exports a template in a format similar to the format required by
Tito.  This service is not used by the DE and has been superceded by the
secured `/edit-template` and `/copy-template` endpoints.  The response body
for this service is fairly large, so it will not be documented in this file.
For all of the gory details, see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

#### Exporting an Analysis

Unsecured Endpoint: GET /export-workflow/{analysis-id}

This service exports an analysis in the format used to import multi-step
analyses into the DE.  Note that this format will work for both single- and
multi-step analyses.  This service is used by the export script to export
analyses from the DE.  The response body for this service is fairly large, so
it will not be documented in this file.  For all of the gory details, see the
(Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

#### Exporting Selected Deployed Components

Unsecured Endpoint: POST /export-deployed-components

This service exports deployed components matching search criteria that are
provided in the request body.  Searches can be performed by identifier, name,
location, or name and location combined.  If no search criteria are provided
then all existing deployed components will be provided.

The request body should be a string representing a JSON object.  The keys are:
`id` for the identifier, `name` for the name and `location` for the location.
The `name` and `location` fields may be specified either together or by
themselves.  If the `id` field is specified then it must be the only field
that is specified.  An empty JSON object indicates that no search criteria are
specified, meaning that all deployed components will be exported.

The response for this service is in the following format:

```json
{
    "components": [
        {
            "location": location,
            "version": version,
            "attribution": attribution,
            "name": name,
            "description": description,
            "implementation": {
                "test": {
                    "input_files": [
                        input-file-1,
                        input-file-2,
                        ...,
                        input-file-n
                    ],
                    "output_files": [
                        output-file-1,
                        output-file-2,
                        ...,
                        output-file-n
                    ]
                },
                "implementor": {
                    "implementor": implementor-name,
                    "implementor_email": implementor-email
                }
            }
            "id": id,
            "type": type
        },
        ...
    ]
}
```

Here are some examples:

```
$ curl -sd '
{
    "name":"printargs"
}
' http://by-tor:8888/export-deployed-components | python -mjson.tool
{
    "components": [
        {
            "attribution": "Insane Membranes, Inc.", 
            "description": "Print command-line arguments.", 
            "id": "c49bccf303e7f46e0bbf4c05fd4b2d9a7", 
            "implementation": {
                "implementor": "Nobody", 
                "implementor_email": "nobody@iplantcollaborative.org", 
                "test": {
                    "input_files": [], 
                    "output_files": []
                }
            }, 
            "location": "/usr/local2/bin", 
            "name": "printargs", 
            "type": "executable", 
            "version": "0.0.1"
        }
    ]
}
```

```
$ curl -sd '
{
    "name":"printargs",
    "location": "/usr/local2/bin"
}
' http://by-tor:8888/export-deployed-components | python -mjson.tool
{
    "components": [
        {
            "attribution": "Insane Membranes, Inc.", 
            "description": "Print command-line arguments.", 
            "id": "c49bccf303e7f46e0bbf4c05fd4b2d9a7", 
            "implementation": {
                "implementor": "Nobody", 
                "implementor_email": "nobody@iplantcollaborative.org", 
                "test": {
                    "input_files": [], 
                    "output_files": []
                }
            }, 
            "location": "/usr/local2/bin", 
            "name": "printargs", 
            "type": "executable", 
            "version": "0.0.1"
        }
    ]
}
```

#### Permanently Deleting an Analysis

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

#### Logically Deleting an Analysis

Unsecured Endpoint: POST /delete-workflow

This service works in exactly the same way as the
`/permanently-delete-workflow` service except that, instead of permanently
deleting the analysis, it merely marks the analysis as deleted.  This prevents
the analysis from being displayed in the DE, but retains its definition so
that it can be restored later if necessary.  For more information about this
service, please see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

#### Previewing Templates

Unsecured Endpoint: POST /preview-template

Tito uses this service (indirectly) to allow users to preview the UI for a
template that is being edited.  The request body for this service is in the
format required by the `/import-template` service.  The response body for this
service is the in the format produced by the `/get-analysis` service.  For
more information about this service, please see the (Tool Integration Services
wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

#### Previewing Analyses

Unsecured Endpoint: POST /preview-workflow

The purpose of this service is to preview the JSON that would be fed to the UI
for an analysis.  The request body for this service is in the format required
by the `/import-workflow` service.  The response body for this service is in
the format produced by the `/get-analysis` service.  For more information
about this service, please see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

#### Updating an Existing Template

Unsecured Endpoint: POST /update-template

This service either imports a new template or updates an existing template in
the database.  For more information about this service, please see the (Tool
Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

#### Updating an Analysis

Unsecured Endpoint: POST /update-workflow

This service either imports a new analysis or updates an existing analysis in
the database (as long as the analysis has not been submitted for public use).
The difference between this service and the `/update-template` service is that
this service can support multi-step analyses.  For more information about this
service, please see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

#### Forcing an Analysis to be Updated

Unsecured Endpoint: POST /force-update-workflow

The `/update-workflow` service only allows private analyses to be updated.
Analyses that have been submitted for public use must be updated using this
service.  The analysis import script uses this service to import analyses that
have previously been exported.  For more information about this service,
please see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

#### Importing a Template

Unsecured Endpoint: POST /import-template

This service imports a new template into the DE; it will not overwrite an
existing template.  To overwrite an existing template, please use the
`/update-template` service.  For more information about this service, please
see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

#### Importing an Analysis

Unsecured Endpoint: POST /import-workflow

This service imports a new analysis into the DE; it will not overwrite an
existing analysis.  To overwrite an existing analysis, please use the
`/update-workflow` service.  For more information about this service, please
see the (Tool Integration Services wiki
page)[https://pods.iplantcollaborative.org/wiki/display/coresw/Tool+Integration+Services].

### Importing Deployed Components

Unsecured Endpoint: POST /import-tools

This service is an extension of the /import-workflow endpoint that also sends
a notification for every deployed component that is imported provided that a
username and e-mail address is provided for the notification.  The request
body should be in the following format:

```json
{
    "components": [
        {
            "name": component-name,
            "location": component-location,
            "implementation": {
                "implementor_email": e-mail-address-of-implementor,
                "implementor": name-of-implementor,
                "test": {
                    "params": [
                        param-1,
                        param-2,
                        ...,
                        param-n
                    ],
                    "input_files": [
                        input-file-1,
                        input-file-2,
                        ...,
                        input-file-n
                    ],
                    "output_files": [
                        output-file-1,
                        output-file-2,
                        ...,
                        output-file-n
                    ]
                }
            },
            "type": deployed-component-type,
            "description": deployed-component-description,
            "version": deployed-component-version,
            "attribution": deployed-component-attribution,
            "user": username-for-notification,
            "email": e-mail-address-for-notification
        }
    ]
}
```

Note that this format is identical to the one used by the /import-workflow
endpoint except that the `user` and `email` fields have been added to allow
notifications to be generated automatically.  If either of these fields is
missing or empty, a notification will not be sent even if the deployed
component is imported successfully.

The response body for this service contains a success flag along with a brief
description of the reason for the failure if the deployed components can't be
imported.

Here's an example of a successful import:

```
$ curl -sd '
{
    "components": [
        {
            "name": "foo",
            "location": "/usr/local/bin",
            "implementation": {
                "implementor_email": "nobody@iplantcollaborative.org",
                "implementor": "Nobody",
                "test": {
                    "params": [],
                    "input_files": [],
                    "output_files": []
                }
            },
            "type": "executable",
            "description": "the foo is in the bar",
            "version": "1.2.3",
            "attribution": "the foo needs no attribution",
            "user": "nobody",
            "email": "nobody@iplantcollaborative.org"
        }
    ]
}
' http://by-tor:8888/import-tools | python -mjson.tool
{
    "success": true
}
```

Here's an example of an unsuccessful import:

```
$ curl -sd '
{
    "components": [
        {
            "name": "foo",
            "location": "/usr/local/bin",
            "implementation": {
                "implementor_email": "nobody@iplantcollaborative.org",
                "implementor": "Nobody"
            },
            "type": "executable",
            "description": "the foo is in the bar",
            "version": "1.2.3",
            "attribution": "the foo needs no attribution",
            "user": "nobody",
            "email": "nobody@iplantcollaborative.org"
        }
    ]
}
' http://by-tor:8888/import-tools | python -mjson.tool
{
    "reason": "org.json.JSONException: JSONObject[\"test\"] not found.", 
    "success": false
}
```

Though it is possible to import analyses using this endpoint, this practice is
not recommended because it can cause spurious notifications to be sent.

#### Obtaining Property Values for a Previously Executed Job

Unsecured Endpoint: GET /get-property-values/{job-id}

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
            "data_format": data-format-name,
            "is_default_value": default-value-flag,
            "is_visible": visibility-flag
        },
        ...
    ]
}
```

Note that the information type and data format only apply to input
files.  For other types of parameters, these fields will be blank.
The `is_default_value` flag indicates whether or not the default value
was used in the job submission.  The value of this flag is determined
by comparing the actual property value listed in the job submission to
the default property value in the application definition.  If the
default value in the application definition is not blank and the
actual value equals the default value then this flag will be set to
`true`.  Otherwise, this flag will be set to `false`.  The
`is_visible` flag indicates whether or not the property is visible in
the user interface for the application.  This value is copied directly
from the application definition.

Here's an example:

```
$ curl -s http://by-tor:8888/get-property-values/jebf8120d-0ccb-45d1-bae6-849620f31553 | python -mjson.tool
{
    "parameters": [
        {
            "data_format": "Unspecified", 
            "info_type": "File", 
            "is_default_value": false, 
            "is_visible": true, 
            "param_id": "38950035-8F31-0A27-1BE1-8E55F5C30B54", 
            "param_name": "Select an SRA or SRAlite file:", 
            "param_type": "Input", 
            "param_value": "/iplant/home/nobody/SRR001355.lite.sra"
        }, 
        {
            "data_format": "", 
            "info_type": "", 
            "is_default_value": true, 
            "is_visible": true, 
            "param_id": "B962E548-4023-E40C-48E5-6484AF55E5DD", 
            "param_name": "Optional accession override", 
            "param_type": "Text", 
            "param_value": ""
        }, 
        {
            "data_format": "", 
            "info_type": "", 
            "is_default_value": true, 
            "is_visible": true, 
            "param_id": "DCFC3CD9-FB31-E0F8-C4CB-78F66FF368D2", 
            "param_name": "File contains paired-end data", 
            "param_type": "Flag", 
            "param_value": "true"
        }, 
        {
            "data_format": "", 
            "info_type": "", 
            "is_default_value": true, 
            "is_visible": true, 
            "param_id": "0E21A202-EC8A-7BFD-913B-FA73FE86F58E", 
            "param_name": "Offset to use for quality scale conversion", 
            "param_type": "Number", 
            "param_value": "33"
        }, 
        {
            "data_format": "", 
            "info_type": "", 
            "is_default_value": true, 
            "is_visible": true, 
            "param_id": "F9AD602D-38E3-8C90-9DD7-E1BB4971CD70", 
            "param_name": "Emit only FASTA records without quality scores", 
            "param_type": "Flag", 
            "param_value": "false"
        }, 
        {
            "data_format": "", 
            "info_type": "", 
            "is_default_value": true, 
            "is_visible": false, 
            "param_id": "6BAD8D7F-3EE2-A52A-93D1-1329D1565E4F", 
            "param_name": "Verbose", 
            "param_type": "Flag", 
            "param_value": "true"
        }
    ]
}
```

#### Initializing a User's Workspace

Secured Endpoint: GET /secured/bootstrap

The DE calls this service as soon as the user logs in.  If the user has never
logged in before then the service initializes the user's workspace and returns
the user's workspace ID.  If the user has logged in before then the service
merely returns the user's workspace ID.  The response body for this service is
in the following format:

```json
{
    "workspaceId": workspace-id
}
```

Here's an example:

```
$ curl -s "http://by-tor:8888/secured/bootstrap?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "workspaceId": "4"
}
```

Note that the `cas-ticket` command is an alias to a command that produces a
CAS service ticket.  All secured services in Donkey require the CAS service
ticket to be sent to the service in the `proxyToken` query-string parameter.

#### Obtaining Notifications

Secured Endpoint: GET /secured/notifications/messages

Notifications in the DE are used to inform users when some event (for example
a job status change or the completion of a file upload) has occurred.  This
service provides a way for the DE to retrieve notifications that the user may
or may not have seen before.  This service accepts five different query-string
parameters (in addition to the `proxyToken` parameter, which is required for
all secured services):

<table>
    <thead>
        <tr><th>Name</th><th>Description</th><th>Required/Optional</th></tr>
    </thead>
    <tbody>
        <tr>
            <td>limit</td>
            <td>The maximum number of notifications to return at a time.</td>
            <td>Required</td>
        </tr>
        <tr>
            <td>offset</td>
            <td>The index of the starting message.</td>
            <td>Required</td>
        </tr>
        <tr>
            <td>sortField</td>
            <td>
                The field to use when sorting messages.  Currently, the only
                supported value for this field is `timestamp`.
            </td>
            <td>Optional (defaults to `timestamp`)</td>
        </tr>
        <tr>
            <td>sortDir</td>
            <td>
                The sorting direction, which can be `asc` (ascending) or `desc`
                (descending).
            </td>
            <td>Optional (defaults to `des`)</td>
        </tr>
        <tr>
            <td>filter</td>
            <td>
                Specifies the type of notification messages to return, which
                can be `data`, `analysis` or `tool`.  Other types of
                notifications may be added in the future.  If this parameter
                it not specified then all types of notifications will be
                returned.
            </td>
            <td>Optional</td>
        </tr>
    </tbody>
</table>

The response body for this service is in the following format:

```json
{
    "messages": [
        {
            "deleted": deleted-flag,
            "message": {
                "id": message-id,
                "text": message-text,
                "timestamp": milliseconds-since-epoch,
            }
            "outputDir": output-directory-path,
            "outputManifest": list-of-output-files,
            "payload": {
                ...
            }
            "seen": seen-flag,
            "type": notification-type-code,
            "user": username,
            "workspaceId": workspace-identifier-if-available
        },
        ...
    ]
}
```

The payload object in each message is a JSON object with a format that is
specific to the notification type, and its format will vary.  There are
currently three types of notifications that we support: `data`, `analysis` and
`tool`.  The `data` and `analysis` notification types have the same payload
format:

```json
{
    "action": action-code,
    "analysis-details": analysis-description,
    "analysis_id": analysis-id,
    "analyis_name": analysis-name,
    "description": job-description,
    "enddate": end-date-in-milliseconds-since-epoch,
    "id": job-id,
    "name": job-name,
    "resultfolderid": result-folder-path,
    "startdate": start-date-in-milliseconds-since-epoch,
    "status": job-status-code,
    "user": username
}
```

The payload format for the `tool` notification type is a little simpler:

```json
{
    "email_address": email-address,
    "toolname": tool-name,
    "tooldirectory": tool-directory,
    "tooldescription": tool-description,
    "toolattribution": tool-attribution,
    "toolversion": tool-version
}
```

Here's an example:

```
$ curl -s "http://by-tor:8888/secured/notifications/get-messages?proxyToken=$(cas-ticket)&limit=1&offset=0" | python -mjson.tool
{
    "messages": [
        {
            "deleted": false, 
            "message": {
                "id": "C15763CF-A5C9-48F5-BE4F-9FB3CB1897EB", 
                "text": "URL Import of somefile.txt from http://snow-dog.iplantcollaborative.org/somefile.txt completed", 
                "timestamp": 1331068427000
            }, 
            "outputDir": "/iplant/home/nobody", 
            "outputManifest": [], 
            "payload": {
                "action": "job_status_change", 
                "analysis-details": "", 
                "analysis_id": "", 
                "analysis_name": "", 
                "description": "URL Import of somefile.txt from http://snow-dog.iplantcollaborative.org/somefile.txt", 
                "enddate": 1331068427000, 
                "id": "40115C19-AFBC-4CAE-9738-324DD8B18FDC", 
                "name": "URL Import of somefile.txt from http://snow-dog.iplantcollaborative.org/somefile.txt", 
                "resultfolderid": "/iplant/home/nobody", 
                "startdate": "1331068414712", 
                "status": "Completed", 
                "user": "nobody"
            }, 
            "seen": true, 
            "type": "data", 
            "user": "nobody", 
            "workspaceId": null
        }
    ]
}
```

#### Obtaining Unseen Notifications

Secured Endpoint: GET /secured/notifications/unseen-messages

This service is used to obtain notifications that the user hasn't seen yet.
This service takes no query-string parameters other than the `proxyToken`
parameter that is required by all secured services.  Here's an example:

```
$ curl -s "http://by-tor:8888/secured/notifications/unseen-messages?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "messages": []
}
```

#### Marking Notifications as Seen

Secured Endpoint: POST /secured/notifications/seen

Once a user has seen a notification, the notification should be marked as seen
to prevent it from being returned by the `/notifications/unseen-messages`
endpoint.  This service provides a way to mark notifications as seen.  The
request body for this service is in the following format:

```
{
    "uuids": [
        uuid-1,
        uuid-2,
        ...,
        uuid-n
    ]
}
```

The response body for this service is a simple JSON object that indicates
whether or not the service call succeeded.  Here's an example:

```
$ curl -sd '
{
    "uuids": [
        "C15763CF-A5C9-48F5-BE4F-9FB3CB1897EB"
    ]
}
' http://by-tor:8888/secured/notifications/seen | python -mjson.tool
{
    "success": true
}
```

Note that the UUIDs provided in the request body must be obtained from the
`message` -> `id` element of the notification the user wishes to mark as seen.

#### Marking Notifications as Deleted

Secured Endpoint: POST /secured/notifications/delete

Users may wish to dismiss notifications that they've already seen.  This
service marks one or more notifications as deleted so that neither the
`/notfications/messages` endpoint nor the `/notifications/unseen-messages`
endpoint will return them.  The request body for this service is in the
following format:

```
{
    "uuids": [
        uuid-1,
        uuid-2,
        ...,
        uuid-n
    ]
}
```

The response body for this service is a simple JSON object that indicates
whether or not the service call succeeded.  Here's an example:

```
$ curl -sd '
{
    "uuids": [
        "C15763CF-A5C9-48F5-BE4F-9FB3CB1897EB"
    ]
}
' http://by-tor:8888/secured/notifications/delete | python -mjson.tool
{
    "success": true
}
```

Note that the UUIDs provided in the request body must be obtained from the
`message` -> `id` element of the notification the user wishes to delete.

#### Getting Analyses in the JSON Format Required by the DE

Secured Endpoint: GET /secured/template/{analysis-id}

This service is the secured version of the `/get-analyis` endpoint.  The
response body for this service is in the following format:

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
curl -s "http://by-tor:8888/secured/template/9BCCE2D3-8372-4BA5-A0CE-96E513B2693C?proxyToken=$(cas-ticket)" | python -mjson.tool
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

#### Submitting a Job for Execution

Secured Endpoint: PUT /secured/workspaces/{workspace-id}/newexperiment

The DE uses this service to submit jobs for execution on behalf of the user.
The request body is in the following format:

```json
{
    "config": {
        property-id-1: property-value-1,
        property-id-2: property-value-2,
        ...,
        property-id-n: property-value-n
    },
    "analysis_id": analysis-id,
    "name": job-name,
    "type": job-type,
    "debug": debug-flag,
    "workspace_id": workspace-id,
    "notify": email-notifications-enabled-flag,
    "output_dir": output-directory-path,
    "create_output_subdir": auto-create-subdir-flag,
    "description": job-description
}
```

The property identifiers deserve some special mention here because they're not
obtained directly from the database.  If you examine the output from the
`/get-analysis/{analysis-id}` endpoint or the `/template/{analysis-id}`
endpoint then these property identifiers are the ones that show up in the
service output.  If you're looking in the database (or in the output from the
`/export-workflow/{analysis-id}` endpoint) then you can obtain the property ID
used in this service by combining the step name, a literal underscore and the
actual property identifier.

This service produces no response body.  Here's an example:

```
$ curl -X PUT -sd '
{
    "config": {
        "FastxQualityFilter_4654B648-676C-2B9E-A92B-6A9F22B64AE1": "/iplant/home/nobody/somefile.fq",
        "FastxQualityFilter_51B9AD98-B0EF-7FE4-74B9-7ADEBADE6024": "20",
        "FastxQualityFilter_BCCB23E7-02A5-8F7A-A4FA-EE49249D6FC0": "50"
    },
    "analysis_id": "a4ce6a7961e1f4aafabfce922fd00810f",
    "name": "some_job",
    "type": "",
    "debug": false,
    "workspace_id": "4",
    "notify": true,
    "output_dir": "/iplant/home/nobody/analyses",
    "create_output_subdir": true,
    "description": ""
}
'
"http://by-tor:8888/secured/workspaces/4/newexperiment?proxyToken=$(cas-ticket)"
```

#### Listing Jobs

Secured Endpoint: GET /secured/workspaces/{workspace-id}/executions/list

Information about the status of jobs that have previously been submitted for
execution can be obtained using this service.  The DE uses this service to
populate the _Analyses_ window.  The response body for this service is in the
following format:

```json
{
    "analyses": [
        {
            "analysis_details": analysis-description,
            "analysis_id": analysis-id,
            "analysis_name": analysis-name,
            "description": job-description,
            "enddate": end-date-as-milliseconds-since-epoch,
            "id": job-id,
            "name": job-name,
            "resultfolderid": path-to-result-folder,
            "startdate": start-date-as-milliseconds-since-epoch,
            "status": job-status-code,
            "wiki_url": analysis-documentation-link
        },
        ...
    ]
}
```

Here's an example:

```
$ curl -s http://by-tor:8888/secured/workspaces/4/executions/list?proxyToken=$(cas-ticket) | python -mjson.tool
{
    "analyses": [
        {
            "analysis_details": "Find significant changes in transcript expression, splicing, and promoter use across RNAseq alignment data files", 
            "analysis_id": "516ED301-E250-40BC-B2BC-31DD7B64D3BA", 
            "analysis_name": "CuffDiff", 
            "description": "Selecting a non-default file for output. ", 
            "enddate": "1329252482000", 
            "id": "BD421AF3-2C6E-4A92-A215-D380CD6FECC8", 
            "name": "CuffDiffTest1", 
            "resultfolderid": "/iplant/home/nobody/analyses/CuffDiff/", 
            "startdate": "1329252412998", 
            "status": "Failed", 
            "wiki_url": "https://pods.iplantcollaborative.org/wiki/some/doc/link/CuffDiff"
        }, 
        ...
    ]
}
```

#### Deleting Jobs

Secured Endpoint: PUT /secured/workspaces/{workspace-id}/executions/delete

After a job has completed, a user may not want to view the job status
information in the _Analyses_ window any longer.  This service provides a way
to mark job status information as deleted so that it no longer shows up.  The
request body for this service is in the following format:

```json
{
    "workspace_id": workspace-id,
    "executions": [
        job-id-1,
        job-id-2,
        ...,
        job-id-n
    ]
}
```

This service produces no response body.

It should be noted that this service does not fail if any of the job
identifiers refers to a non-existent or deleted job.  If the identifier refers
to a deleted job then the update is essentially a no-op.  If a job with the
identifier can't be found then a warning message is logged in Donkey's log
file, but the service does not indicate that a failure has occurred.

Here's an example:

```
$ curl -X PUT -sd '
{
    "workspace_id": 4,
    "executions": [
        "84DFCC0E-03B9-4DF4-8484-55BFBD6FE841",
        "FOO"
    ]
}
' "http://by-tor:8888/secured/workspaces/4/executions/delete?proxyToken=$(cas-ticket)"
```

#### Rating Analyses

Secured Endpoint: POST /secured/rate-analysis

Users have the ability to rate an analysis for its usefulness, and this
service provides the means to store the analysis rating.  This service accepts
an analysis identifier a rating level between one and five, inclusive, and a
comment identifier that refers to a comment in iPlant's Confluence wiki.  The
rating is stored in the database and associated with the authenticated user.
The request body for this service is in the following format:

```json
{
    "analysis_id": analysis-id,
    "rating": selected-rating,
    "comment_id": comment-identifier
}
```

The response body for this service contains only the average rating for the
analysis, and is in this format:

```json
{
    "avg": average-rating,
}
```

Here's an example:

```
$ curl -sd '
{
    "analysis_id": "72AA400D-6945-463E-A18D-09513C2381D7",
    "rating": 4,
    "comment_id": 27
}
' "http://by-tor:8888/secured/rate-analysis?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "avg": 4
}
```

#### Deleting Analysis Ratings

Secured Endpoint: POST /secured/delete-rating

The DE uses this service to remove a rating that a user has previously made.
This service accepts an analysis identifier in a JSON request body and deletes
the authenticated user's rating for the corresponding analysis.  The request
body for this service is in the following format:

```json
{
    "analysis_id": analysis-id,
}
```

The response body for this service contains only the new average rating for
the analysis and is in the following format:

```json
{
    "avg": average-rating,
}
```

Here's an example:

```
$ curl -sd '                       
{
    "analysis_id": "a65fa62bcebc0418cbb947485a63b30cd"
}
' "http://by-tor:8888/secured/delete-rating?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "avg": 0
}
```

#### Searching for Analyses

Secured Endpoint: GET /secured/search-analyses/{search-term}

This service allows users to search for analyses based on a part of the
analysis name.  The response body is in the following format:

```json
{
    "templates": [
        {
            "deleted": deleted-flag,
            "description": analysis-description,
            "disabled": disabled-flag,
            "group_id": analysis-group-id,
            "group_name": analysis-group-name,
            "id": analysis-id,
            "integrator_name": integrator-name,
            "is_favorite": is-favorite-flag,
            "is_public": is-public-flag,
            "name": analysis-name,
            "rating": {
                "average": average-rating,
            }
        },
        ...
    ]
}
```

Here's an example:

```
$ curl -s "http://by-tor:8888/secured/search-analyses/ranger?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "templates": [
        {
            "deleted": false, 
            "description": "Some Description", 
            "disabled": false, 
            "group_id": "99F2E2FE-9931-4154-ADDB-28386027B19F", 
            "group_name": "Some Group Name", 
            "id": "9D221848-1D12-4A31-8E93-FA069EEDC151", 
            "integrator_name": "Nobody", 
            "is_favorite": false, 
            "is_public": false, 
            "name": "Ranger", 
            "rating": {
                "average": 4
            }
        }
    ]
}
```

#### Listing Analyses in an Analysis Group

Secured Endpoint: GET /secured/get-analyses-in-group/{group-id}

This service lists all of the analyses within an analysis group or any of its
descendents.  The DE uses this service to obtain the list of analyses when a
user clicks on a group in the _Apps_ window.  The response body for this
service is in the following format:

```json
{
    "description": analysis-group-description,
    "id": analysis-group-id,
    "is_public": public-group-flag,
    "name": analysis-group-name,
    "template_count": number-of-analyses-in-group-and-descendents,
    "templates": [
        {
            "deleted": analysis-deleted-flag,
            "description": analysis-description,
            "disabled": analysis-disabled-flag,
            "id": analysis-id,
            "integrator_email": integrator-email-address,
            "integrator_name": integrator-name,
            "is_favorite": favorite-analysis-flag,
            "is_public": public-analysis-flag,
            "name": analysis-name,
            "pipeline_eligibility": {
                "is_valid": valid-for-pipelines-flag,
                "reason": reason-for-exclusion-from-pipelines-if-applicable,
            },
            "rating": {
                "average": average-rating,
                "comment-id": comment-id,
                "user": user-rating
            },
            "wiki_url": documentation-link
        },
        ...
    ]
}
```

Here's an example:

```
$ curl -s "http://by-tor:8888/secured/get-analyses-in-group/6A1B9EBD-4950-4F3F-9CAB-DD12A1046D9A?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "description": "", 
    "id": "C3DED4E2-EC99-4A54-B0D8-196112D1BB7B", 
    "is_public": true, 
    "name": "Some Group", 
    "template_count": 1, 
    "templates": [
        {
            "deleted": false, 
            "description": "Some app description.", 
            "disabled": false, 
            "id": "81C0CCEE-439C-4516-805F-3E260E336EE4", 
            "integrator_email": "nobody@iplantcollaborative.org", 
            "integrator_name": "Nobody", 
            "is_favorite": false, 
            "is_public": true, 
            "name": "SomeAppName", 
            "pipeline_eligibility": {
                "is_valid": true, 
                "reason": ""
            }, 
            "rating": {
                "average": 4, 
                "comment_id": 27, 
                "user": 4
            }, 
            "wiki_url": "https://pods.iplantcollaborative.org/wiki/some/doc/link"
        }
    ]
}
```

#### Listing Analyses that may be Included in a Pipeline

Secured Endpoint: GET /secured/list-analyses-for-pipeline/{group-id}

This service is an alias for the `/get-analyses-in-group/{group-id}` service.
At one time, this was a different service that returned additional information
that was normally omitted for the sake of efficiency.  Some recent efficiency
improvements have eliminated the need to omit this information from the more
commonly used endpoint, however.  This endpoint is currently being retained
for backward compatibility.

#### Listing Deployed Components in an Analysis

Secured Endpoint: GET /secured/get-components-in-analysis/{analysis-id}

This service can be used to list all of the deployed components in an
analysis.  The response body is in the following format:

```json
{
    "deployed_components": [
        {
            "attribution": attribution-1,
            "description": description-1,
            "id": id-1,
            "location": location-1,
            "name": name-1,
            "type": type-1,
            "version": version-1
        },
        ...
    ]
}
```

Here's an example:

```
$ curl -s http://by-tor:8888/secured/get-components-in-analysis/0BA04303-F0CB-4A34-BACE-7090F869B332?proxyToken=$(cas-ticket) | python -mjson.tool
{
    "deployed_components": [
        {
            "attribution": "", 
            "description": "", 
            "id": "c73ef66158ef94f1bb90689ff813629f5", 
            "location": "/usr/local2/muscle3.8.31", 
            "name": "muscle", 
            "type": "executable", 
            "version": ""
        }, 
        {
            "attribution": "", 
            "description": "", 
            "id": "c2d79e93d83044a659b907764275248ef", 
            "location": "/usr/local2/phyml-20110304", 
            "name": "phyml", 
            "type": "executable", 
            "version": ""
        }
    ]
}
```


#### Updating the Favorite Analyses List

Secured Endpoint: POST /secured/update-favorites

Analyses can be marked as favorites in the DE, which allows users to access
them without having to search.  This service is used to add or remove analyses
from a user's favorites list.  The request body is in the following format:

```json
{
    "workspace_id": workspace-id,
    "analysis_id": analysis-id,
    "user_favorite": favorite-flag
}
```

The action performed by this service is controlled by the `user_favorite`
field value.  If the field value is `false` then the analysis will be added to
the user's favorites list.  If the field value is `true` then the analysis
will be removed from the user's favorites list.  If this service fails then
the response will be in the usual format for failed service calls.  If the
service succeeds then the response conntains only a success flag:

```json
{
    "success": true
}
```
Here are some examples:

```
$ curl -sd '
{
    "workspace_id": 4,
    "analysis_id": "F99526B9-CC88-46DA-84B3-0743192DCB7B",
    "user_favorite": true
}
' "http://by-tor:8888/secured/update-favorites?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "success": true
}
```

```
$ curl -sd '
{
    "workspace_id": 4,
    "analysis_id": "F99526B9-CC88-46DA-84B3-0743192DCB7B",
    "user_favorite": true
}
' "http://by-tor:8888/secured/update-favorites?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "reason": "analysis, F99526B9-CC88-46DA-84B3-0743192DCB7B, is already a favorite", 
    "success": false
}
```

```
$ curl -sd '
{
    "workspace_id": 4,
    "analysis_id": "F99526B9-CC88-46DA-84B3-0743192DCB7B",
    "user_favorite": false
}
' "http://by-tor:8888/secured/update-favorites?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "success": true
}
```

```
$ curl -sd '
{
    "workspace_id": 4,
    "analysis_id": "FOO",          
    "user_favorite": false
}
' "http://by-tor:8888/secured/update-favorites?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "reason": "analysis, FOO not found", 
    "success": false
}
```

#### Making an Analysis Available for Editing in Tito

Secured Endpoint: GET /secured/edit-template/{analysis-id}

This service can be used to make an analysis available for editing in Tito.
If the user already owns the analysis then this service merely makes ensures
that it is not marked as deleted.  If the user does not own the analysis then
this service makes a copy of the analysis available in Tito so that the user
may edit the copy.  The response body contains only the analysis identifier,
which may be different from the analysis identifier that was provided:

```json
{
    "analysis_id": analysis-id
}
```

Here are some examples:

```
$ curl -s "http://by-tor:8888/secured/edit-template/C720C42D-531A-164B-38CC-D2D6A337C5A5?proxyToken=$(cas-ticket)" | python -m json.tool
{
    "analysis_id": "DED7E03E-B011-4F3E-8750-3F903FB28137"
}
```

```
$ curl -s "http://by-tor:8888/secured/edit-template/DED7E03E-B011-4F3E-8750-3F903FB28137?proxyToken=$(cas-ticket)" | python -m json.tool
{
    "analysis_id": "DED7E03E-B011-4F3E-8750-3F903FB28137"
}
```

#### Making a Copy of an Analysis Available for Editing in Tito

Secured Endpoint: GET /secured/copy-template/{analysis-id}

This service can be used to make a copy of an analysis available for editing
in Tito.  The only difference between this service and the
`/edit-template/{analysis-id}` service is that this service will always make a
copy of an existing analysis, even if the user already owns the analysis.
Here's an example:

```
$ curl -s "http://by-tor:8888/secured/copy-template/C720C42D-531A-164B-38CC-D2D6A337C5A5?proxyToken=$(cas-ticket)" | python -m json.tool
{
    "analysis_id": "13FF6D0C-F6F7-4ACE-A6C7-635A17826383"
}
```

#### Submitting an Analysis for Public Use

Secured Endpoint: POST /secured/make-analysis-public

This service can be used to submit a private analysis for public use.  The
user supplies basic information about the analysis and a suggested location
for it.  The service records the information and suggested location then
places the analysis in the Beta category.  A Tito administrator can
subsequently move the analysis to the suggested location at a later time if
it proves to be useful.  The request body is in the following format:

```json
{
    "analysis_id": analysis-id,
    "email": integrator-email-address,
    "integrator": integrator-name,
    "references": [
        reference-link-1,
        reference-link-2,
        ...,
        reference-link-n
    ],
    "groups": [
        suggested-group-1,
        suggested-group-2,
        ...,
        suggested-group-n
    ],
    "desc": analysis-description,
    "wiki_url": documentation-link
}
```

The response body is just an empty JSON object if the service call succeeds.

Making an analysis public entails recording the additional inforamtion
provided to the service, removing the analysis from all of its current
analysis groups, adding the analysis to the _Beta_ group, and marking the
analysis as public in Tito, which prevents future modification.

Here's an example:

```
$ curl -sd '
{
    "analysis_id": "F771A215-4809-4683-87C0-A899C0732AF3",
    "email": "nobody@iplantcollaborative.org",
    "integrator": "Nobody",
    "references": [
        "http://foo.bar.baz.org" 
    ],
    "groups": [
        "0A687324-099B-4EEF-A82C-C1A60B970487"
    ],
    "desc": "The foo is in the bar.",
    "wiki_url": "https://wiki.iplantcollaborative.org/docs/Foo+Foo"
}
' http://by-tor:8888/secured/make-analysis-public?proxyToken=$(cas-ticket)
{}
```

### Saving User Session Data

Secured Endpoint: POST /secured/sessions

This service can be used to save arbitrary user session information.  The post
body is stored as-is and can be retrieved by sending an HTTP GET request to
the same URL.

Here's an example:

```
$ curl -sd data http://by-tor:8888/secured/sessions?proxyToken=$(cas-ticket)
```

### Retrieving User Session Data

Secured Endpoint: GET /secured/sessions

This service can be used to retrieve user session information that was
previously saved by sending a POST request to the same service.

Here's an example:

```
$ curl http://by-tor:8888/secured/sessions?proxyToken=$(cas-ticket)
data
```

### Removing User Seession Data

Secured Endpoint: DELETE /secured/sessions

This service can be used to remove saved user session information.  This is
helpful in cases where the user's session is in an unusable state and saving
the session information keeps all of the user's future sessions in an unusable
state.

Here's an example:

```
$ curl -XDELETE http://by-tor:8888/secured/sessions?proxyToken=$(cas-ticket) | python -mjson.tool
{
    "success": true
}
```

An attempt to remove session data that doesn't already exist will be silently
ignored.

### Saving User Preferences

Secured Endpoint: POST /secured/preferences

This service can be used to save arbitrary user preferences. The POST
body is stored without modification and can be retrieved by sending a GET
request to the same URL.

Example:

```
$ curl -sd data http://by-tor:8888/secured/preferences?proxyToken=$(cas-ticket)
data
```

### Removing User Preferences

Secured Endpoint: DELETE /secured/preferences

This service can be used to remove a user's preferences.

Example:

```
$ curl -X DELETE http://by-tor:8888/secured/preferences?proxyToken=$(cas-ticket)
{
    "success" : true
}
```

An attempt to remove preference data that doesn't already exist will be silently ignored.

### Listing Collaborators

Secured Endpoint: GET /secured/collaborators

This service can be used to retrieve the list of collaborators for the current
user.  The response body is in the following format:

```json
{
    "success": true,
    "users": [
        {
            "email": email-1,
            "firstname": firstname-1,
            "id": id-1,
            "lastname": lastname-1,
            "useranme": username-1
        },
        ...
    ]
}
```

Here's an example:

```
$ curl -s http://by-tor:8888/secured/collaborators?proxyToken=$(cas-ticket) | python -mjson.tool
{
    "success": true, 
    "users": [
        {
            "email": "foo@iplantcollaborative.org", 
            "firstname": "The", 
            "id": 123, 
            "lastname": "Foo", 
            "username": "foo"
        }, 
        {
            "email": "bar@iplantcollaborative.org", 
            "firstname": "The", 
            "id": 456, 
            "lastname": "Bar", 
            "username": "bar"
        }
    ]
}
```

### Adding Collaborators

Secured Endpoint: POST /secured/collaborators

This service can be used to add users to the list of collaborators for the
current user.  The request body is in the following format:

```json
{
    "users": [
        {
            "email": email-1,
            "firstname": firstname-1,
            "id": id-1,
            "lastname": lastname-1,
            "username": username-1
        },
        ...
    ]
}
```

Note that the only field that is actually required for each user is the
`username` field.  The rest of the fields may be included if desired,
however.  This feature is provided as a convenience to the caller, who may be
forwarding results from the user search service to this service.

Here's an example:

```
$ curl -sd '
{
    "users": [
        {
            "username": "baz"
        }
    ]
}
' http://by-tor:8888/secured/collaborators?proxyToken=$(cas-ticket) | python -mjson.tool
{
    "success": true
}
```

### Removing Collaborators

Secured Endpoint: POST /secured/remove-collaborators

This service can be used to remove users from the list of collaborators for
the current user.  The request body is in the following format:

```json
{
    "users": [
        {
            "email": email-1,
            "firstname": firstname-1,
            "id": id-1,
            "lastname": lastname-1,
            "username": username-1
        },
        ...
    ]
}
```

Note that the only field that is actually required for each user is the
`username` field.  The rest of the fields may be included if desired,
however.  This feature is provided as a convenience to the caller, who may be
forwarding results from the user search service to this service.

Here's an example:

```
$ curl -sd '
{
    "users": [
        {
            "username": "baz"
        }
    ]
}
' http://by-tor:8888/secured/remove-collaborators?proxyToken=$(cas-ticket) | python -mjson.tool
{
    "success": true
}
```

### Sharing User Data

Secured Endpoint: POST /secured/share

This service can be used to share a user's files and folders with other users,
and with specific permissions for each user and resource.

Here's an example:

```
$ curl -sd '
{
    "sharing": [
        {
            "path": "/path/to/shared/file",
            "users": [
                {
                    "user": "shared-with-user1",
                    "permissions": {
                        "read": true,
                        "write": false,
                        "own": false
                    }
                },
                {
                    "user": "shared-with-user2",
                    "permissions": {
                        "read": true,
                        "write": true,
                        "own": true
                    }
                }
            ]
        },
        {
            "path": "/path/to/shared/folder",
            "users": [
                {
                    "user": "shared-with-user1",
                    "permissions": {
                        "read": true,
                        "write": false,
                        "own": false
                    }
                },
                {
                    "user": "shared-with-user2",
                    "permissions": {
                        "read": true,
                        "write": true,
                        "own": true
                    }
                }
            ]
        }
    ]
}
' http://by-tor:8888/secured/share?proxyToken=$(cas-ticket)
```

The service will response with a success or failure message per user-resource pair:

```
{
    "sharing": [
        {
            "path": "/path/to/shared/file",
            "users": [
                {
                    "permissions": {
                        "own": false,
                        "read": true,
                        "write": false
                    },
                    "success": true,
                    "user": "shared-with-user1"
                },
                {
                    "error": {
                        "action": "share",
                        "error_code": "ERR_NOT_A_USER",
                        "status": "failure",
                        "users": [
                            "shared-with-user2"
                        ]
                    },
                    "permissions": {
                        "own": true,
                        "read": true,
                        "write": true
                    },
                    "success": false,
                    "user": "shared-with-user2"
                }
            ]
        },
        {
            "path": "/path/to/shared/folder",
            "users": [
                {
                    "permissions": {
                        "own": false,
                        "read": true,
                        "write": false
                    },
                    "success": true,
                    "user": "shared-with-user1"
                },
                {
                    "error": {
                        "action": "share",
                        "error_code": "ERR_NOT_A_USER",
                        "status": "failure",
                        "users": [
                            "shared-with-user2"
                        ]
                    },
                    "permissions": {
                        "own": true,
                        "read": true,
                        "write": true
                    },
                    "success": false,
                    "user": "shared-with-user2"
                }
            ]
        }
    ]
}
```

### Unsharing User Data

Secured Endpoint: POST /secured/unshare

This service can be used to unshare a user's files and folders with other users.

Here's an example:

```
$ curl -sd '
{
    "unshare": [
        {
            "path": "/path/to/shared/file1",
            "users": [
                "shared-with-user"
            ]
        },
        {
            "path": "/path/to/shared/folder1",
            "users": [
                "shared-with-user"
            ]
        }
    ]
}
' http://by-tor:8888/secured/unshare?proxyToken=$(cas-ticket)
```

The service will respond with a success or failure message per resource:

```
{
    "unshare": [
        {
            "path": "/path/to/shared/file1",
            "success": true,
            "users": [
                "shared-with-user"
            ]
        },
        {
            "error": {
                "action": "unshare",
                "error_code": "ERR_DOES_NOT_EXIST",
                "paths": [
                    "/path/to/shared/folder1"
                ],
                "status": "failure"
            },
            "path": "/path/to/shared/folder1",
            "success": false,
            "users": [
                "shared-with-user"
            ]
        }
    ]
}
```

### Determining a User's Default Output Directory

Secured Endpoint: GET /secured/default-output-dir

This endoint determines the default output directory in iRODS for the
currently authenticated user.  Aside from the `proxyToken` parameter, this
endpoint requires one other query-string parameter: `name`, which specifies
the name of the output directory.  If the directory exists already then this
service will just return the full path to the directory.  If the directory
does not exist already then this service will create it _then_ return the full
path to the directory.

Upon success, the JSON object returned in the response body contains a flag
indicating that the service call was successfull along with the full path to
the default output directory.  Upon failure, the response body contains a flag
indicating that the service call was not successful along with some
information about why the service call failed.

Here are some examples:

```
$ curl -s "http://by-tor:8888/secured/default-output-dir?proxyToken=$(cas-ticket)&name=analyses" | python -mjson.tool
{
    "path": "/iplant/home/ipctest/analyses",
    "success": true
}
```

```
$ curl -s "http://by-tor:8888/secured/default-output-dir?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "arg": "name",
    "code": "MISSING-REQUIRED-ARGUMENT",
    "success": false
}
```

At the time of this writing, if the path exists but points to a regular file
rather than a directory, then the directory will not be created and no error
will be logged.  This will be fixed when a service exists that determines
whether a path points to a file or a directory.
