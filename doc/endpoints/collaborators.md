# Collaborator List Management Endpoints

## Listing Collaborators

Secured Endpoint: GET /secured/collaborators

This endpoint calls the metadactyl endpoint with the same path. The response
from the metadactyl endpoint returns just a list of usernames, however. This
service takes that response and performs a lookup for all of the usernames in
the list of collaborators.

This service can be used to retrieve the list of collaborators for the
authenticated user. The response body is in the following format:

```json
{
    "success": true,
    "users": [
        {
            "email": "email-1",
            "firstname": "firstname-1",
            "id": "id-1",
            "lastname": "lastname-1",
            "useranme": "username-1"
        }
    ]
}
```

Here's an example:

```
$ curl -s "http://by-tor:8888/secured/collaborators?proxyToken=$(cas-ticket)" | python -mjson.tool
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

## Adding Collaborators

Secured Endpoint: POST /secured/collaborators

This service calls the metadactyl service with the same path. The request body
is altered to match the format required by the metadactyl service before the
request is forwarded, however.

This service can be used to add users to the list of collaborators for the
current user. The request body is in the following format:

```json
{
    "users": [
        {
            "email": "email-1",
            "firstname": "firstname-1",
            "id": "id-1",
            "lastname": "lastname-1",
            "username": "username-1"
        }
    ]
}
```

Note that the only field that is actually required for each user is the
`username` field. The rest of the fields may be included if desired,
however. This feature is provided as a convenience to the caller, who may be
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
' "http://by-tor:8888/secured/collaborators?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "success": true
}
```

## Removing Collaborators

Secured Endpoint: POST /secured/remove-collaborators

This service calls the metadactyl service with the same path. The request body
is altered to match the format required by the metadactyl service before the
request is forwarded, however.

This service can be used to remove users from the list of collaborators for the
current user. The request body is in the following format:

```json
{
    "users": [
        {
            "email": "email-1",
            "firstname": "firstname-1",
            "id": "id-1",
            "lastname": "lastname-1",
            "username": "username-1"
        }
    ]
}
```

Note that the only field that is actually required for each user is the
`username` field. The rest of the fields may be included if desired,
however. This feature is provided as a convenience to the caller, who may be
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
' "http://by-tor:8888/secured/remove-collaborators?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "success": true
}
```
