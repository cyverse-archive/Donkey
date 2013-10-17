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
| label            | string | the logical name of the entry |
| user-permissions | array  | an array of [permission records](#permission_record) identifying the permissions users have on this entry |

**Example**

```json
{
    "id"               : "/iplant/home/tedgin/an-entry",
    "label"            : "an-entry",
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
    ]
}
```

## File Record

A file extends a [filesystem entry](#filesystem_entry_record). Here are the additional fields that
describe a file.

| Field       | Type   | Description |
| ----------- | ------ | ----------- |
| creator     | object | a [user identity record](#user_identity_record) identifying the creator of the file |
| create-time | number | the time when the file was created in seconds since the POSIX epoch |
| modify-time | number | the time when the file was last modified in seconds since the POSIX epoch |
| media-type  | string | the media type of the file, `null` if unknown |
| size        | number | the size of the file in octets |

**Example**

```json
{
    "id"               : "/iplant/home/tedgin/a.file",
    "label"            : "a.file",
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
    "create-time"      : 1381350485,
    "modify-time"      : 1381350485,
    "media-type"       : null,
    "size"             : 14016
}
```

## Folder Record

A folder extends a [filesystem entry](#filesystem_entry_record). Here are the additional fields that
describe a folder.

| Field       | Type   | Description |
| ----------- | ------ | ----------- |
| creator     | object | a [user identity record](#user_identity_record) identifying the creator of the folder |
| create-time | number | the time when the folder was created in seconds since the POSIX epoch |
| modify-time | number | the time when the folder was last modified in seconds since the POSIX epoch\* |

\* A folder is modified when a file or sub-folder is added to or removed from it.

**Example**

```json
{
    "id"               : "/iplant/home/tedgin/a-folder",
    "label"            : "a-folder",
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
    "create-time"      : 1381350485,
    "modify-time"      : 1381350485
}
```

# Permission Record

Here are the fields that describe a permission.

| Field      | Type   | Description |
| ---------- | ------ | ----------- |
| permission | string | the access level (`read`\|`modify`\|`own`)\* |
| user       | object | a [user identity record](#user_identity_record) identifying the user having the given permission |

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

User details extend a [user identity](#user_identity_record). Here are the additional fields that
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
