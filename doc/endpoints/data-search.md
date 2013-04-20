# Table of Contents

* [Searching User Data](#searching-user-data)
    * [Endpoints](#endpoints)
    * [Search Request](#search-request)
    * [Response Body](#response-body)
        * [Successful Response](#successful-response)
        * [Failed Response](#failed-response)

# Searching User Data

Donkey provides a search endpoint that allow callers to search the data by name.
It allows for partial matching, restricting to folders or files, and paging of
results.

## Endpoints

Secured Endpoint: GET /secured/search

## Search Request

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

## Response Body

### Successful Response

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

### Failed Response

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
