# Donkey

Donkey is a platform for hosting Services written in Clojure; it's intended to
be the replacement for the Mule instance that used to run inside the Discovery
Environment web application.

*Important Note:* All of the services that used to run inside Mule now run
inside Donkey instead.  The Mule services no longer exist.

## Documentation Links

* [Installation and Configuration](doc/install.md)
* [Security](doc/security.md)
* [Errors](doc/errors.md)
* [Endpoints](doc/endpoints.md)
** [Miscellaneous Endpoints](doc/endpoints/misc.md)
** [App Metadata Endpoints](doc/endpoints/app-metadata.md)
** [App Execution Endpoints](doc/endpoints/app-execution.md)
** [Phylogenetic Tree Rendering Endpoints](doc/endpoints/tree-viewing.md)
** [Notification Endpoints](doc/endpoints/notifications.md)
** [Collaborator List Management Endpoints](doc/endpoints/collaborators.md)

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
            "user": "shared-with-user1",
            "paths": [
                {
                    "path": "/path/to/shared/file",
                    "permissions": {
                        "read": true,
                        "write": true,
                        "own": false
                    }
                },
                {
                    "path": "/path/to/shared/folder",
                    "permissions": {
                        "read": true,
                        "write": false,
                        "own": false
                    }
                }
            ]
        },
        {
            "user": "shared-with-user2",
            "paths": [
                {
                    "path": "/path/to/shared/file",
                    "permissions": {
                        "read": true,
                        "write": true,
                        "own": true
                    }
                },
                {
                    "path": "/path/to/shared/folder",
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
' "http://by-tor:8888/secured/share?proxyToken=$(cas-ticket)"
```

The service will respond with a success or failure message per user and resource:

```
{
    "sharing": [
        {
            "user": "shared-with-user1",
            "sharing": [
                {
                    "success": true,
                    "path": "/path/to/shared/file",
                    "permissions": {
                        "read": true,
                        "write": true,
                        "own": false
                    }
                },
                {
                    "success": false,
                    "error": {
                        "status": "failure",
                        "action": "share",
                        "error_code": "ERR_DOES_NOT_EXIST",
                        "paths": [
                            "/path/to/shared/folder"
                        ]
                    },
                    "path": "/path/to/shared/folder",
                    "permissions": {
                        "read": true,
                        "write": false,
                        "own": false
                    }
                }
            ]
        },
        {
            "user": "shared-with-user2",
            "sharing": [
                {
                    "success": true,
                    "path": "/path/to/shared/file",
                    "permissions": {
                        "read": true,
                        "write": true,
                        "own": true
                    }
                },
                {
                    "success": false,
                    "error": {
                        "status": "failure",
                        "action": "share",
                        "error_code": "ERR_DOES_NOT_EXIST",
                        "paths": [
                            "/path/to/shared/folder"
                        ]
                    },
                    "path": "/path/to/shared/folder",
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
            "user": "shared-with-user1",
            "paths": [
                "/path/to/shared/file",
                "/path/to/shared/foo"
            ]
        },
        {
            "user": "shared-with-user2",
            "paths": [
                "/path/to/shared/file",
                "/path/to/shared/folder"
            ]
        }
    ]
}
' "http://by-tor:8888/secured/unshare?proxyToken=$(cas-ticket)"
```

The service will respond with a success or failure message per user:

```
{
    "unshare": [
        {
            "user": "shared-with-user1",
            "unshare": [
                {
                    "success": true,
                    "path": "/path/to/shared/file"
                },
                {
                    "success": false,
                    "error": {
                        "status": "failure",
                        "action": "unshare",
                        "error_code": "ERR_DOES_NOT_EXIST",
                        "paths": [
                            "/path/to/shared/foo"
                        ]
                    },
                    "path": "/path/to/shared/foo"
                }
            ]
        },
        {
            "user": "shared-with-user2",
            "unshare": [
                {
                    "success": true,
                    "path": "/path/to/shared/file"
                },
                {
                    "success": true,
                    "path": "/path/to/shared/folder"
                }
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
the default name of the output directory.

This service works in conjunction with user preferences.  If a default output
directory has been selected already (either by the user or automatically) then
this service will attempt to use that directory.  If that directory exists
already then this service will just return the full path to the directory.  If
the path exists and refers to a regular file then the service will fail with
an error code of `REGULAR-FILE-SELECTED-AS-OUTPUT-FOLDER`.  Otherwise, this
service will create the directory and return the path.

If the default output directory has not been selected yet then this service
will automatically generate the path to the directory based on the name that
was given to it in the `name` query-string parameter.  The value of this
parameter is treated as being relative to the user's home directory in iRODS.
If the path exists and is a directory then the path is saved in the user's
preferences and returned.  If the path does not exist then the directory is
created and the path is saved in the user's preferences and returned.  If the
path exists and is a regular file then the service will generate a unique path
(by repeatedly trying the same name with a hyphen and an integer appended to
it) and update the preferences and return the path when a unique path is
found.

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

### Resetting a user's default output directory.

Secured Endpoint: POST /secured/default-output-dir

This endpoint resets a user's default output directory to its default value
even if the user has already chosen a different default output directory.
Since this is a POST request, this request requires a message body.  The
message body in this case is a JSON object containing the path relative to the
user's home directory in the `path` attribute.  Here are some examples:

```
$ curl -sd '
{
    "path":"foon"
}' "http://by-tor:8888/secured/default-output-dir?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "path": "/iplant/home/ipctest/foon",
    "success": true
}
```

```
$ curl -sd '
{
    "inv":"foon"
}' "http://by-tor:8888/secured/default-output-dir?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "arg": "path",
    "code": "MISSING-REQUIRED-ARGUMENT",
    "success": false
}
```

### Stopping a Running Analysis

Secured Endpoint: DELETE /secured/stop-analysis/<uuid>

This is a pass-through endpoint to the JEX that stops a running analysis. The <uuid>
should be replaced with the UUID of a running analysis.

For more information, see the docs for the /stop endpoint in the JEX, available here:

    https://github.com/iPlantCollaborativeOpenSource/JEX#stopping-a-running-analysis

#### Exporting Reference Genomes

Secured Endpoint: GET /secured/reference-genomes

This service can be used to export reference genomes from the discovery
environment, presumably in order to import them into another deployment of the
discovery environment.

Here's an example:

```
$ curl -s "http://by-tor:8888/secured/reference-genomes?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "genomes": [
        {
            "created_by": "<public>",
            "created_on": 1345848226895,
            "deleted": false,
            "last_modified_by": "<public>",
            "last_modified_on": "",
            "name": "Arabidopsis lyrata (Ensembl 14)",
            "path": "/path/to/Arabidopsis_lyrata.1.0/de_support/",
            "uuid": "4bb9856a-43da-4f67-bdf9-f90916b4c11f"
        },
        ...
    ],
    success: true
}
```

#### Importing Reference Genomes

Secured Endpoint: PUT /secured/reference-genomes

This service can be used to import reference genomes into the discovery
environment.  The request body for this service should be in the same format
as the response body for the endpoint to export the reference genomes.  Note
that the success flag, if present in the request body, will be ignored.

Note that this service replaces *all* reference genomes; it doesn't just add
reference genomes to the list.  Use the services in Conrad to import individual
reference genomes.

Here's an example:

```
$ curl -X PUT -sd '
{
    "genomes": [
        {
            "created_by": "<public>",
            "created_on": 1345848226895,
            "deleted": false,
            "last_modified_by": "<public>",
            "last_modified_on": "",
            "name": "Arabidopsis lyrata (Ensembl 14)",
            "path": "/path/to/Arabidopsis_lyrata.1.0/de_support/",
            "uuid": "4bb9856a-43da-4f67-bdf9-f90916b4c11f"
        }
    ]
}
' "http://by-tor:8888/secured/reference-genomes?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "success": true
}
```

#### Obtaining Tree Viewer URLs

Secured Endpoint: GET /secured/tree-viewer-urls

This service is used to obtain tree viewer URLs for a single file in the iPlant
data store.  This URL requires one query-string parameter, `path`, in addition
to the usual `proxyToken` parameter that is required by all secured endpoints.
The `path` query-string parameter should contain the path to the file.  If the
service call is successful then the response body will look something like this:

```json
{
    "action": "tree_manifest",
    "success": true,
    "tree-urls": [
        {
            "label": tree-label-1,
            "url": tree-url-1
        },
        {
            "label": tree-label-2,
            "url": tree-url-2
        },
        ...,
        {
            "label": tree-label-n,
            "url": tree-url-n
        }
    ]
}
```

Otherwise, the response body will contain information about the cause of the
failure.

Here's an example of a successful service call:

```
$ curl -s "http://by-tor:8888/secured/tree-viewer-urls?proxyToken=$(cas-ticket)&path=/iplant/home/nobody/sample1.newick" | python -mjson.tool
{
    "action": "tree_manifest",
    "success": true,
    "tree-urls": [
        {
            "label": "tree_0",
            "url": "http://by-tor/view/tree/d0f44d9cc8cd27ad060fbc2616ba2247"
        }
    ]
}
```

#### Obtaining User Info

Secured Endpoint: /secured/user-info

This endpoint allows the caller to search for information about users with
specific usernames.  Each username is specified using the `username` query
string parameter, which can be specified multiple times to search for
information about more than one user.  The response body is in the following
format:

```json
{
    username-1: {
        "email": email-address-1,
        "firstname": first-name-1,
        "id": id-1,
        "institution": institution-1,
        "lastname": last-name-1,
        "position": position-1,
        "username": username-1
    },
    ...,
    username-n: {
        "email": email-address-n,
        "firstname": first-name-n,
        "id": id-n,
        "institution": institution-n,
        "lastname": last-name-n,
        "position": position-n,
        "username": username-n
    }
}
```

Assuming the service doesn't encounter an error, the status code will be 200 and
the response body will contain the information for all of the users who were
found.  If none of the users were found then the response body will consist of
an empty JSON object.

Here's an example with a match:

```
$ curl -s "http://by-tor:8888/secured/user-info?proxyToken=$(cas-ticket)&username=nobody" | python -mjson.tool
{
    "nobody": {
        "email": "nobody@iplantcollaborative.org",
        "firstname": "Nobody",
        "id": "3618",
        "institution": "iplant collaborative",
        "lastname": "Inparticular",
        "position": null,
        "username": "nobody"
    }
}
```

Here's an example with no matches:

```
$ curl -s "http://by-tor:8888/secured/user-info?proxyToken=$(cas-ticket)&username=foo" | python -mjson.tool
{}
```

#### Obtaining Identifiers

Unsecured Endpoint: /uuid

In some cases, it's difficult for the UI client code to generate UUIDs for
objects that require them.  This service returns a single UUID in the response
body.  The UUID is returned as a plain text string.

### Searching User Data

Donkey provides a search endpoint that allow callers to search the data by name.
It allows for partial matching, restricting to folders or files, and paging of
results.

#### Endpoints

Secured Endpoint: GET /secured/search

#### Search Request

The request is encoded as query string.  The following parameters are recognized.

`search-term=NAME-GLOB` is the search condition.  `NAME-GLOB` is a glob pattern
indicating what entry names should be matched.  If the pattern has no wildcards
(`*` or `?`), then an `*` wildcard will be appended to `NAME-GLOB` causing all
entries with names beginning with `NAME-GLOB` to be matched.  *This parameter is
required*.

`type=folder|file` limits the search results to a certain type of entry.  This
may be set to `folder` for only matching folder names and `file` for only
matching file names.  *This parameter is optional.  When it isn't provided, all
types of entries are matched.*

`from=N` causes the first `N` results to be skipped.  When combined with `size`
it allows for paging results.  *This parameter is optional.  When it isn't
provided, no results will be skipped.*

`size=N` limits the number of results to `N`.  When combined with `from` it
allows for paging results.  *This parameter is optional.  When it isn't provided,
the number of results will be at most 10.*

#### Response Body

##### Successful Response

When the search succeeds or partially succeeds a JSON document of the following
form will be returned.

```json
{
    "success" : true,
    "total" : #-matches,
    "max_score" : max-score,
    "hits" : [
        {
            "_index" : "iplant",
            "_type" : mapping-type-of-match,
            "_id" : id-of-match,
            "_score" : score,
            "name" : matched-name,
            "viewers" : viewer-array
        },
        ...
    ]
}
```

The matches are in the array `hits`.  The field `total` is not the number of
elements in this array; it is the total number of matches that could be
returned.

The fields in an element of the `hits` array are as follows.  The `_type` field
indicates the mapping type of the match.  Infosquito indexes files and folders
with different mapping types.  It uses the `file` mapping type for files and
`folder` for folders.  The `_id` field holds the unique identifier relative to
the mapping type for the match.  Infosquito identifies all files and folders
with their absolute paths in iRODS.  The `name` field holds the name being
matched.  Finally, the `viewers` field holds an array of user and group names
that have at least read access to the matched file or folder.

Here's an example of a successful response.

```
$ curl -XGET "http://by-tor:8888/secured/search?proxyToken=$(cas-ticket)&search-term=?e*&type=file&from=1&size=2" | python -mjson.tool
{
    "hits": [
        {
            "viewers": [
                "ipctest",
                "rodsadmin"
            ],
            "name": "read1_10k.fq",
            "_index": "iplant",
            "_type": "file",
            "_id": "\/iplant\/home\/ipctest\/analyses\/fc_01300857-2012-01-30-08-58-00.090\/read1_10k.fq",
            "_score": 1.0
        },
        {
            "viewers": [
                "ipctest",
                "rodsadmin"
            ],
            "name": "read1_10k.fq",
            "_index": "iplant",
            "_type": "file",
            "_id": "\/iplant\/home\/ipctest\/analyses\/ft_01251621-2012-01-26-16-21-46.602\/read1_10k.fq",
            "_score": 1.0
        }
    ],
    "max_score": 1.0,
    "total": 7,
    "success": true
}
```

##### Failed Response

When a request fails, a JSON document of the following form is returned.

```json
{
     "success": false,
     "code": error-code,
     other-fields
}
```

*Finding no matches is not a failure.*  The `code` field has a short message
identifying the problem.  The `other-fields` depend on the error.

Here's an example of an unsuccessful response.

```
curl -XGET "http://by-tor:8888/secured/search?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "success": false,
    "code": "MISSING-REQUIRED-ARGUMENT",
    "arg": "search-term"
}
```
