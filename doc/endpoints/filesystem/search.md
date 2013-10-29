
# _These endpoints have not been implemented yet._

This document describes the endpoints used to performing searches of user data.

# Table of Contents

* [Indexed Information](#indexed-information)
* [Basic Usage](#basic-usage)
    * [Search Requests](#search-requests)
    * [Index Status Request](#index-status-request)
* [Administration](#administration)
    * [Search Requests by Proxy](#search-requests-by-proxy)
    * [Update Index Request](#update-index-request)

# Indexed Information

For each file and folder stored in the iPlant data store, its ACL, system metadata, and user
metadata are indexed as a JSON document. For files, [file records](../../schema.md#file-record) are
indexed, and for folders, [folder records](../../schema.md#folder-record) are indexed.

# Basic Usage

For the client without administrative privileges, Donkey provides endpoints for performing searches
and for checking the status of the indexer.

## Search Requests

Donkey provides search endpoints that allow callers to search the data by name and various pieces of
system metadata. It supports the full [ElasticSearch query string DSL]
(http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax)
for searching.

Each field in an indexed document may be explicitly used in a search query. If the field is an
object, i.e. an aggregate of fields, the object's fields may be explicitly referenced as well using
dot notation, e.g. `acl.access`.

### Endpoints

* `GET /secured/filesystem/index`
* `GET /secured/filesystem/{folder-path}/index`

These endpoints search the data index and retrieve a set of files and folders matching the terms
provided in the query string. If a `folder-path` is provided, only the entries belonging to the
folder with the logical path specified by `folder-path` will be retrieved.

### Request Parameters

The following additional URI parameters are recognized.

| Parameter | Required? | Default | Description |
| --------- | --------- | ------- | ----------- |
| q         | yes       |         | This parameter holds the search query. See [query string syntax](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax) for a description of the syntax. |
| type      | no        | any     | This parameter restricts the search to either files or folders. It can take the values `any`, meaning files and folders, `file`, only files, and `folders`, only folders. |
| offset    | no        | 0       | This parameter indicates the number of matches to skip before including any in the result set. When combined with `limit`, it allows for paging results. |
| limit     | no        | 0       | This parameter limits the number of matches in the result set to be a most a certain amount. A `0` indicates there is no limit. When combined with `offset`, it allows for paging results. |

### Response

When the search succeeds the response document has these additional fields.

| Field   | Type    | Description |
| ------- | ------- | ----------- |
| total   | number  | This is the total number of matches found, not the number of elements in the `matches` array. |
| offset  | number  | This is the value of the `offset` parameter in the query string. |
| matches | array   | This is the set or partial set of matches found, each entry being a **match record**. It contains at most `limit` entries and is sorted by descending score. |

**Match Record**

| Field  | Type   | Description |
| ------ | ------ | ----------- |
| score  | number | an indication of how well this entity matches the query compared to other matches |
| type   | string | the entity is this type of filesystem entry, either `"file"` or `"folder"` |
| entity | object | the [file record](../../schema.md#file-record) or [folder record](../../schema.md#folder-record) matched |

### Error Codes

__TODO__ _Document error codes._

### Example

```
$ curl \
> "http://localhost:8888/secured/filesystem/search/iplant/home?proxyToken=$(cas-ticket)&q=name:?e*&type=file&offset=1&limit=2" \
> | python -mjson.tool
{
    "matches": [
        {
            "entity": {
                "creator": {
                    "name": "rods",
                    "zone": "iplant"
                },
                "date-created": 1381350424,
                "date-modified": 1381350424,
                "file-size": 13225,
                "id": "/iplant/home/rods/analyses/fc_01300857-2013-10-09-13-27-04.090/read1_10k.fq",
                "label": "read1_10k.fq",
                "media-type": null,
                "metadata": [],
                "user-permissions": [
                    {
                        "permission": "read",
                        "user": {
                            "username": "rods",
                            "zone": "iplant"
                        }
                    },
                    {
                        "permission": "own",
                        "user": {
                            "username": "rodsadmin",
                            "zone": "iplant"
                        }
                    }
                ]
            },
            "score": 1.0,
            "type": "file"
        },
        {
            "entity": {
                "creator": {
                    "name": "rods",
                    "zone": "iplant"
                },
                "date-created": 1381350485,
                "date-modified": 1381350485,
                "file-size": 14016,
                "id": "/iplant/home/rods/analyses/ft_01251621-2013-10-09-13-28-05.602/read1_10k.fq",
                "label": "read1_10k.fq",
                "media-type": null,
                "metadata": [
                    {
                        "attribute": "color",
                        "unit": null,
                        "value": "red"
                    }
                ],
                "user-permissions": [
                    {
                        "permission": "read",
                        "user": {
                            "username": "rods",
                            "zone": "iplant"
                        }
                    },
                    {
                        "permission": "write",
                        "user": {
                            "username": "rodsadmin",
                            "zone": "iplant"
                        }
                    }
                ]
            },
            "score": 1.0,
            "type": "file"
        }
    ],
    "offset": 1,
    "success": true,
    "total": 7
}
```

## Index Status Request

A client may request the status of the indexer.

### Endpoint

* Endpoint: `GET /secured/filesystem/index/status`

### Request Parameters

There are no additional request parameters.

### Response

| Field                 | Type   | Description |
| --------------------- | ------ | ----------- |
| lag                   | number | This is the estimated number of seconds the index state lags the data store state. |
| size                  | number | This is the number of entries in the index. |
| last-sync-time        | number | This is the time when the last successful synchronization was requested in seconds since the POSIX epoch. |
| sync-state            | string | This is the current synchronizer state of the index. It must be `"idle"`, `"indexing"` or `"pruning"`.\* |
| sync-processing-count | number | This is the total number of elements to consider for indexing or pruning during the current synchronizer state. |
| sync-processed-count  | number | This is the total number of elements that have be indexed or pruned during the current synchronizer state. |

\* Unless a synchronization has been requested, the `sync-state` will be `"idle"`. When a
synchronization has been requested, `sync-state` is transitioned to `"indexing`". This means that
the data store is being crawled and entries missing from the search index are being added. When
indexing has been completed, `sync-state` is transitioned to `"pruning"`. This means that the search
index is scanned and entries that are no longer in the data store are removed. Finally, when pruning
has completed, `sync-state` is transitioned back to `"idle"`.

### Error Codes

__TODO__ _Document error codes._

### Example

```
$ curl http://localhost:8888/secured/filesystem/index/status?proxyToken=$(cas-ticket) \
> | python -mjson.tool
{
    "lag": 11,
    "last-sync-time": 1209923323,
    "size": 110432665,
    "success": true,
    "sync-processed-count": 0,
    "sync-processing-count": 0,
    "sync-state": "idle"
}
```

# Administration

For clients with administrative privileges, Donkey provides additional endpoints for performing
search requests as a specific user and controlling the indexer.

## Search Requests by Proxy

An administrator can perform any search as a specific user.

### Endpoints:

* Admin Endpoint: `GET /admin/filesystem/index`
* Admin Endpoint: `GET /admin/filesystem/{folder-path}/index`

These endpoints search the data index and retrieve a set of files and folders matching the terms
provided in the query string. If a `folder-path` is provided, only the entries belonging to the
folder with the logical path specified by `folder-path` will be retrieved.

### Request Parameters

The request is encoded as a query string. It supports all of the parameters of a
[normal search request](#search-request) with one additional parameter. The  `as-user` parameter
identifies the user the administrator is performing the search as. This allows the administrator to
reproduce a query the user has complained about. The parameter value takes the form
`{username}#{zone}` where `username` and `zone` are the fields from the
[user's identity record](../../schema.md#user-identity-record).

### Response

The response body is the same as a [normal response body](#response-body).

### Error Codes

__TODO__ _Document error codes_

### Example

```
$ curl \
> "http://localhost:8888/admin/filesystem/search/iplant/home?proxyToken=$(cas-ticket)&as-user=rods#iplant&q=name:?e*&type=file&offset=1&limit=2" \
> | python -mjson.tool
{
    "matches": [
        {
            "entity": {
                "creator": {
                    "name": "rods",
                    "zone": "iplant"
                },
                "date-created": 1381350424,
                "date-modified": 1381350424,
                "file-size": 13225,
                "id": "/iplant/home/rods/analyses/fc_01300857-2013-10-09-13-27-04.090/read1_10k.fq",
                "label": "read1_10k.fq",
                "media-type": null,
                "metadata": [],
                "user-permissions": [
                    {
                        "permission": "read",
                        "user": {
                            "username": "rods",
                            "zone": "iplant"
                        }
                    },
                    {
                        "permission": "own",
                        "user": {
                            "username": "rodsadmin",
                            "zone": "iplant"
                        }
                    }
                ]
            },
            "score": 1.0,
            "type": "file"
        },
        {
            "entity": {
                "creator": {
                    "name": "rods",
                    "zone": "iplant"
                },
                "date-created": 1381350485,
                "date-modified": 1381350485,
                "file-size": 14016,
                "id": "/iplant/home/rods/analyses/ft_01251621-2013-10-09-13-28-05.602/read1_10k.fq",
                "label": "read1_10k.fq",
                "media-type": null,
                "metadata": [
                    {
                        "attribute": "color",
                        "unit": null,
                        "value": "red"
                    }
                ],
                "user-permissions": [
                    {
                        "permission": "read",
                        "user": {
                            "username": "rods",
                            "zone": "iplant"
                        }
                    },
                    {
                        "permission": "write",
                        "user": {
                            "username": "rodsadmin",
                            "zone": "iplant"
                        }
                    }
                ]
            },
            "score": 1.0,
            "type": "file"
        }
    ],
    "offset": 1,
    "success": true,
    "total": 7
}
```


## Update Index Request

Donkey provides an endpoint for updating the index used by search.

### Endpoint

* Endpoint: `POST /admin/filesystem/index`

### Request Parameter

An indexing request has one additional parameter. The `sync` parameter indicates what operation the
synchronizer should perform on the index. If the parameter is set to `start-full`, a full
synchronization of the index with the data store will be performed. If the parameter is set to
`start-incremental`, only the items in the data store that have been created or modified since the
last completed synchronization will be indexed during this synchronization. Finally, if the
parameter is set to `stop`, if a synchronization is currently in progress, it will be terminated.

### Response

A successful response has no additional fields.

### Error Codes

__TODO__ _Document error codes._

### Example

```
$ curl -X POST http://localhost:8888/admin/filesystem/index?proxyToken=$(cas-ticket) \
> | python -mjson.tool
{
    "success": true
}
```

