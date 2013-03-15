# Miscellaneous Donkey Endpoints

## Verifying that Donkey is Running

Unsecured Endpoint: GET /

The root path in Donkey can be used to verify that Donkey is actually running
and is responding. Currently, the response to this URL contains only a welcome
message. Here's an example:

```
$ curl -s http://by-tor:8888/
Welcome to Donkey!  I've mastered the stairs!
```

## Initializing a User's Workspace

Secured Endpoint: GET /secured/bootstrap

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Saving User Session Data

Secured Endpoint: POST /secured/sessions

This service can be used to save arbitrary user session information. The post
body is stored as-is and can be retrieved by sending an HTTP GET request to the
same URL.

Here's an example:

```
$ curl -sd data "http://by-tor:8888/secured/sessions?proxyToken=$(cas-ticket)"
```

## Retrieving User Session Data

Secured Endpoint: GET /secured/sessions

This service can be used to retrieve user session information that was
previously saved by sending a POST request to the same service.

Here's an example:

```
$ curl "http://by-tor:8888/secured/sessions?proxyToken=$(cas-ticket)"
data
```

## Removing User Seession Data

Secured Endpoint: DELETE /secured/sessions

This service can be used to remove saved user session information. This is
helpful in cases where the user's session is in an unusable state and saving the
session information keeps all of the user's future sessions in an unusable
state.

Here's an example:

```
$ curl -XDELETE "http://by-tor:8888/secured/sessions?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "success": true
}
```

An attempt to remove session data that doesn't already exist will be silently
ignored.

## Saving User Preferences

Secured Endpoint: POST /secured/preferences

This service can be used to save arbitrary user preferences. The POST body is
stored without modification and can be retrieved by sending a GET request to the
same URL.

Example:

```
$ curl -sd data "http://by-tor:8888/secured/preferences?proxyToken=$(cas-ticket)"
data
```

## Retrieving User Preferences

Secured Endpoint: GET /secured/preferences

This service can be used to retrieve a user's preferences.

Example:

```
$ curl -s "http://by-tor:8888/secured/preferences?proxyToken=$(cas-ticket)"
data
```

## Removing User Preferences

Secured Endpoint: DELETE /secured/preferences

This service can be used to remove a user's preferences.

Example:

```
$ curl -X DELETE "http://by-tor:8888/secured/preferences?proxyToken=$(cas-ticket)"
{
    "success" : true
}
```

An attempt to remove preference data that doesn't already exist will be silently
ignored.

## Saving User Search History

Secured Endpoint: POST /secured/search-history

This service can be used to save arbitrary user search history information. The
POST body is stored without modification and be retrieved by sending a GET
request to the same URL.

Example:

```
$ curl -sd data "http://by-tor:8888/secured/search-history?proxyToken=$(cas-ticket)"
data
```

## Retrieving User Search History

Secured Endpoint: GET /secured/search-history

This service can be used to retrieve a user's search history.

Example:

```
$ curl -s "http://by-tor:8888/secured/search-history?proxyToken=$(cas-ticket)"
data
```

## Deleting User Search History

This service can be used to delete a user's search history.

Example:

```
$ curl -XDELETE -s "http://by-tor:8888/secured/search-history?proxyToken=$(cas-ticket)"
{
    "success" : true
}
```
