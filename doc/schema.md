Here are the definitions of the data records used to communicate through the endpoints.

# Table of Contents

* [Filesystem Entry Record](#filesystem_entry_record)
    * [File Record](#file_record)
    * [Folder Record](#folder_record)
* [Permission Record](#permission_record)
* [User Identity Record](#user_identity_record)
    * [User Details Record](#user_details_record)

# Filesystem Entry Record

Here are the fields that describe a filesystem entry.

| Field            | Type   | Description |
| ---------------- | ------ | ----------- |
| id               | string | the logical path to the entry |
| user-permissions | array  | an array of [permission records](#permission-record) identifying the permissions users have on this entry |
| date-created     | number | the time when the file was created in seconds since the POSIX epoch |
| date-modified    | number | the time when the file was last modified in seconds since the POSIX epoch |
| label            | string | the logical name of the entry |

**Example**

```json
{
    "id"               : "/iplant/home/tedgin/an-entry",
    "user-permissions" : [
        {
            "permission" : "own",
            "user"       : {
                "username" : "tedgin",
                "zone"     : "iplant"
            }
        },
        {
            "permission" : "own",
            "user"       : {
                "username" : "rodsadmin",
                "zone"     : "iplant"
            }
        }
    ],
    "date-created"     : 1381350485,
    "date-modified"    : 1381350485,
    "label"            : "an-entry"
}
```

## File Record

A file extends a [filesystem entry](#filesystem-entry-record). Here are the additional fields that
describe a file.

| Field       | Type   | Description |
| ----------- | ------ | ----------- |
| creator     | object | a [user identity record](#user-identity-record) identifying the creator of the file |
| file-size   | number | the size of the file in octets |
| media-type  | string | the media type of the file, `null` if unknown |

**Example**

```json
{
    "id"               : "/iplant/home/tedgin/a.file",
    "user-permissions" : [
        {
            "permission" : "own",
            "user"       : {
                "username" : "tedgin",
                "zone"     : "iplant"
            }
        },
        {
            "permission" : "own",
            "user"       : {
                "username" : "rodsadmin",
                "zone"     : "iplant"
            }
        }
    ],
    "creator"          : {
        "username" : "tedgin",
        "zone"     : "iplant"
    },
    "date-created"     : 1381350485,
    "date-modified"    : 1381350485,
    "file-size"        : 14016,
    "label"            : "a.file",
    "media-type"       : null
}
```

## Folder Record

A folder extends a [filesystem entry](#filesystem-entry-record). It has the additional field
`"creator"` that holds a [user identity record](#user-identity-record) identifying the creator of
the folder.

**Example**

```json
{
    "id"               : "/iplant/home/tedgin/a-folder",
    "user-permissions" : [
        {
            "permission" : "own",
            "user"       : {
                "username" : "tedgin",
                "zone"     : "iplant"
            }
        },
        {
            "permission" : "own",
            "user"       : {
                "username" : "rodsadmin",
                "zone"     : "iplant"
            }
        }
    ],
    "creator"          : {
        "username" : "tedgin",
        "zone"     : "iplant"
    },
    "date-created"     : 1381350485,
    "date-modified"    : 1381350485,
    "label"            : "a-folder"
}
```

# Permission Record

Here are the fields that describe a permission.

| Field      | Type   | Description |
| ---------- | ------ | ----------- |
| permission | string | the access level, `read`, `modify` or `own`\* |
| user       | object | a [user identity record](#user-identity-record) identifying the user having the given permission |

\* The `read` access level means the user can download a file or folder, read it, and read its
metadata. The `modify` access level gives the user `read` access level plus the ability to create,
modify and delete file or folder metadata. For a file, this access level also gives the user the
ability to modify the file. For a folder, this access level gives the ability to upload files and
folders into the folder. The `own` access level gives the user complete control over the file or
folder.

**Example**

```json
{
    "permission" : "own",
    "user" : {
        "username" : "tedgin",
        "zone"     : "iplant"
    }
}
```

# User Identity Record

Here are the fields that define a user identity.

| Field    | Type   | Description |
| -------- | ------ | ----------- |
| username | string | the authenticated name of the identified user |
| zone     | string | the iRODS zone where the user has been authenticated |

**Example**

```json
{
    "username" : "tedgin",
    "zone"     : "iplant"
}
```

## User Details Record

User details extend a [user identity](#user-identity-record). Here are the additional fields that
define a user's details.

| Field       | Type   | Description |
| ----------- | ------ | ----------- |
| email       | string | the user's email address |
| firstName   | string | the given name of the user |
| lastName    | string | the family name of the user |
| workspaceId | number | the internal identifier of the user's DE workspace |

**Example**

```json
{
    "username"    : "tedgin",
    "zone"        : "iplant",
    "email"       : "you.wish@keep-guessing.xxx",
    "firstName"   : "tony",
    "lastName"    : "tedgin",
    "workspaceId" : 4
}
```
