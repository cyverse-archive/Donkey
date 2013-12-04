# Table of Contents

* [Miscellaneous Donkey Endpoints](#miscellaneous-donkey-endpoints)
    * [Verifying that Donkey is Running](#verifying-that-donkey-is-running)
    * [Initializing a User's Workspace](#initializing-a-users-workspace)
    * [Saving User Preferences](#saving-user-preferences)
    * [Retrieving User Preferences](#retrieving-user-preferences)
    * [Removing User Preferences](#removing-user-preferences)
    * [Determining a User's Default Output Directory](#determining-a-users-default-output-directory)
    * [Resetting a user's default output directory.](#resetting-a-users-default-output-directory.)
    * [Obtaining Identifiers](#obtaining-identifiers)
    * [Submitting User Feedback](#submitting-user-feedback)
    * [Secured /user-data](#secured-user-data)
        * [POST](#post)
        * [GET](#get)
        * [DELETE](#delete)

# Miscellaneous Donkey Endpoints

Note that secured endpoints in Donkey and metadactyl are a little different from
each other. Please see [Donkey Vs. Metadactyl](donkey-v-metadactyl.md) for more
information.

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

Delegates to metadactyl: GET /secured/bootstrap

This endpoint is a passthrough to the metadactyl endpoint using the same path.
Please see the metadactyl documentation for more information.

Note that the `ip-address` query parameter that has to be passed to the
metadactyl service cannot be obtained automatically in most cases. Because of
this, the `ip-address` parameter must be passed to this service in addition to
the `proxyToken` parameter. Here's an example:

```
$ curl "http://by-tor:8888/secured/bootstrap?proxyToken=$(cas-ticket)&ip-address=127.0.0.1" | python -mjson.tool
{
    "action": "bootstrap",
    "loginTime": "1374190755304",
    "newWorkspace": false,
    "status": "success",
    "workspaceId": "4",
    "username": "snow-dog",
    "email": "sd@example.org",
    "firstName": "Snow",
    "lastName": "Dog"
}
```

## Recording when a User Logs Out

Secured Endpoint: GET /secured/logout

Delegates to metadactyl: GET /secured/logout

This endpoint is a passthrough to the metadactyl endpoint using the same path.
Please see the metadactyl documentation for more information.

Note that the `ip-address` and `login-time` query parameters that have to be
passed to the metadactyl service cannot be obtained automatically in most cases.
Because of this, these parameters must be passed to this service in addition to
the `proxyToken` parameter. Here's an example:

```
$ curl -s "http://by-tor:8888/secured/logout?proxyToken=$(cas-ticket)&ip-address=127.0.0.1&login-time=1374190755304" | python -mjson.tool
{
    "action": "logout",
    "status": "success"
}
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


## Determining a User's Default Output Directory

Secured Endpoint: GET /secured/default-output-dir

This endoint determines the default output directory in iRODS for the currently
authenticated user. Aside from the `proxyToken` parameter, this endpoint
requires no query-string parameters. The default default output directory name
is passed to Donkey in the `donkey.job-exec.default-output-folder` configuration
parameter.

This service works in conjunction with user preferences. If a default output
directory has been selected already (either by the user or automatically) then
this service will attempt to use that directory. If that directory exists
already then this service will just return the full path to the directory. If
the path exists and refers to a regular file then the service will fail with an
error code of `REGULAR-FILE-SELECTED-AS-OUTPUT-FOLDER`. Otherwise, this service
will create the directory and return the path.

If the default output directory has not been selected yet then this service will
automatically generate the path to the directory based on the name that was
given to Donkey in the `donkey.job-exec.default-output-folder` configuration
setting. The value of this configuration setting is treated as being relative to
the user's home directory in iRODS. If the path exists and is a directory then
the path is saved in the user's preferences and returned. If the path does not
exist then the directory is created and the path is saved in the user's
preferences and returned. If the path exists and is a regular file then the
service will generate a unique path (by repeatedly trying the same name with a
hyphen and an integer appended to it) and update the preferences and return the
path when a unique path is found.

Upon success, the JSON object returned in the response body contains a flag
indicating that the service call was successfull along with the full path to the
default output directory. Upon failure, the response body contains a flag
indicating that the service call was not successful along with some information
about why the service call failed.

Here's an example:

```
$ curl -s "http://by-tor:8888/secured/default-output-dir?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "path": "/iplant/home/ipctest/analyses",
    "success": true
}
```

## Resetting a user's default output directory.

Secured Endpoint: POST /secured/default-output-dir

This endpoint resets a user's default output directory to its default value even
if the user has already chosen a different default output directory.  Since this
is a POST request, this request requires a message body. The message body in
this case is a JSON object containing the path relative to the user's home
directory in the `path` attribute. Here are some examples:

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

## Obtaining Identifiers

Unsecured Endpoint: GET /uuid

In some cases, it's difficult for the UI client code to generate UUIDs for
objects that require them. This service returns a single UUID in the response
body. The UUID is returned as a plain text string.

## Submitting User Feedback

Secured Endpoint: PUT /secured/feedback

This endpoint submits feedback from the user to a configurable iPlant email
address. The destination email address is stored in the configuration settting,
`donkey.email.feedback-dest`. The request body is a simple JSON object with the
question text in the keys and the answer or answers in the values. The answers
can either be strings or lists of strings:

```json
{
    "question 1": "question 1 answer 1",
    "question 2": [
        "question 2 answer 1",
        "question 2 answer 2"
    ]
}
```

Here's an example:

```
$ curl -XPUT -s "http://by-tor:8888/secured/feedback?proxyToken=$(cas-ticket)" -d '
{
    "What is the circumference of the Earth?": "Roughly 25000 miles.",
    "What are your favorite programming languages?": [ "Clojure", "Scala", "Perl" ]
}
' | python -mjson.tool
{
    "success": true
}
```

## Secured /user-data

Supported HTTP methods: POST, GET, DELETE

This endpoint provides access to a key/value store, and can be used to store any
arbitrary data under a specified key.

All HTTP methods require a user authentication token, and a key. A unique key 
will be constructed from a combination of the user's unique identifying information
and the specified key.

There are some keys which are used explicitly by the DE, and are listed below.
User's should use these keys at their own risk.

<table>
   <thead>
	<tr><th>Key</th><th>Description</th></tr>
   </thead>
   <tbody>
        <tr>
          <td>sessions</td>
          <td>
              User session data
          </td>
        </tr>
        <tr>
           <td>dataQueryTemplates</td>
           <td>
              Saved search filters from the data window
           </td>
        </tr>
   </tbody>
</table>


### POST

Used to store data under the specified key.

Here's an example:

```
$ curl -sd data "http://by-tor:8888/secured/user-data?proxyToken=$(cas-ticket)&key=uniqKey"
````

### GET

Used to retrieve the data which is stored under the specified key.
If no data exists under the specified key, then an HTTP `204 No Content` code will be returned.


Here's an example:

```
$ curl -s "http://by-tor:8888/secured/user-data?proxyToken=$(cas-ticket)&key=uniqKey" 
[stored data]
````

### DELETE

Used to clear any data which is stored under the specified key.
