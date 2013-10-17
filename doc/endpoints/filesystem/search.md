This document describes the endpoints used to performing searches of user data.

# Table of Contents

* [Searching User Data](#searching-user-data)
    * [Indexed Fields](#indexed_fields)
    * [Endpoints](#endpoints)
    * [Search Request](#search-request)
    * [Response Body](#response-body)
        * [Successful Response](#successful-response)
        * [Failed Response](#failed-response)

# Searching User Data

Donkey provides search endpoints that allow callers to search the data by name and various pieces of
system metadata. It supports the full [ElasticSearch query string DSL]
(http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax)
for searching.

## Indexed Fields

For each file and folder stored in the iPlant data store, its system metadata and ACL are indexed as
a JSON document. Each field may be explicitly used in a search query. If the field is an object,
i.e. an aggregate of fields, the object's fields may be explicitly referenced as well using dot
notation, e.g. `acl.access`. For files, [file records](../../schema.md#file-record) are indexed, and
for folders, [folder records](../../schema.md#folder-record) are indexed.

## Endpoints

Secured Endpoint: `GET /secured/filesystem/search`
Secured Endpoint: `GET /secured/filesystem/search/{container-path}`

These endpoints search the data index and retrieve a set of files and folders matching the terms
provided in the query string. If a `container-path` is provided, only the entries belonging to the
folder with the logical path specified by `container-path` will be retrieved.

## Search Request

The request is encoded as a query string. The following URI parameters are recognized.

| Parameter | Required? | Default | Description |
| --------- | --------- | ------- | ----------- |
| q         | yes       |         | This parameter holds the search query. See [query string syntax](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax) for a description of the syntax. |
| type      | no        | any     | This parameter restricts the search to either files or folders. It can take the values `any`, meaning files and folders, `file`, only files, and `folders`, only folders. |
| offset    | no        | 0       | This parameter indicates the number of matches to skip before including any in the result set. When combined with `limit`, it allows for paging results. |
| limit     | no        | 0       | This parameter limits the number of matches in the result set to be a most a certain amount. A `0` indicates there is no limit. When combined with `offset`, it allows for paging results. |

## Response Body

All search responses contain a JSON document with a `"success"` field. This is a boolean flag
indicating whether or not the search request succeeded.

### Successful Response

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

**Example**

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
                "user-permissions": [
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

### Failed Response

When a request fails, a response document has `"code"` field that a string identifying the error
that occurred. Depending on the error code, there may be other fields in the response as well.
Please note that not finding any matches is not a failure.

__TODO__ _Document error codes._


**Example**

```
$ curl "http://localhost:8888/secured/filesystem/search?proxyToken=$(cas-ticket)" \
> | python -mjson.tool
{
    "arg": "q",
    "code": "MISSING-REQUIRED-ARGUMENT",
    "success": false
}
```

