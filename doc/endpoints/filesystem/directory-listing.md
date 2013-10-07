Directory List (Non-Recursive)
------------------------------

The following characters are considered invalid since they cause issues:

    =!\"#$'%*+,\\:?@[]^{}|&;<>`~\n\t\\

__URL Path__: /secured/filesystem/directory

__HTTP Method__: GET

__Error Codes__: ERR_NOT_A_USER, ERR_NOT_READABLE

__Request Query Params__:

* proxyToken - A valid CAS ticket.
* includefiles - If set to a value, returns the files in the directory. Optional.
* path - The path to list. Optional. If ommitted, the user's home directory is used.

__Response Body__:

    {
    "date-created": "1369778522000",
    "date-modified": "1381177547000",
    "file-size": 0,
    "files": [
        {
            "date-created": "1380726835000",
            "date-modified": "1380726835000",
            "file-size": "4814",
            "id": "/iplant/home/wregglej/Ara_Pheno.txt",
            "label": "Ara_Pheno.txt",
            "permissions": {
                "own": true,
                "read": true,
                "write": true
            }
        },
        {
            "date-created": "1369848875000",
            "date-modified": "1380922646000",
            "file-size": "35",
            "id": "/iplant/home/wregglej/asdfasdfasdfadsfasdfasdfasdfadsfa",
            "label": "asdfasdfasdfadsfasdfasdfasdfadsfa",
            "permissions": {
                "own": true,
                "read": true,
                "write": true
            }
        },
        {
            "date-created": "1379519492000",
            "date-modified": "1379520049000",
            "file-size": "196903039",
            "id": "/iplant/home/wregglej/centos-5.8-x86-64-minimal.box",
            "label": "centos-5.8-x86-64-minimal.box",
            "permissions": {
                "own": true,
                "read": true,
                "write": true
            }
        },
    ],
    "folders": [
        {
            "date-created": "1373927956000",
            "date-modified": "1374015533000",
            "file-size": 0,
            "hasSubDirs": true,
            "id": "/iplant/home/wregglej/acsxfdqswfrdafds",
            "label": "acsxfdqswfrdafds",
            "permissions": {
                "own": true,
                "read": true,
                "write": true
            }
        },
        {
            "date-created": "1371157127000",
            "date-modified": "1380909580000",
            "file-size": 0,
            "hasSubDirs": true,
            "id": "/iplant/home/wregglej/analyses",
            "label": "analyses",
            "permissions": {
                "own": true,
                "read": true,
                "write": true
            }
        },
        {
            "date-created": "1380814985000",
            "date-modified": "1380814985000",
            "file-size": 0,
            "hasSubDirs": true,
            "id": "/iplant/home/wregglej/analyses3",
            "label": "analyses3",
            "permissions": {
                "own": true,
                "read": true,
                "write": true
            }
        },

    ],
    "hasSubDirs": true,
    "id": "/iplant/home/wregglej",
    "label": "wregglej",
    "permissions": {
        "own": true,
        "read": true,
        "write": true
    },
    "success": true
}

__Curl Command__:

    curl http://127.0.0.1:3000/secured/filesystem/directory?proxyToken=notReal&includefiles=1

If the "includefiles" is 0 or left out of the params, then the files section will be empty.
If you want to list a directory other than rods' home directory, then include a URL encoded
path parameter, like so:

    curl http://127.0.0.1:3000/secured/filesystem/directory?proxyToken=notReal&includefiles=1&path=/iplant/home/wregglej/ugh


Paged Directory Listing
-----------------------

Provides a paged directory listing for large directories. Always includes files (unless the directory doesn't contain any).

__URL Path__: /secured/filesystem/paged-directory

__HTTP Method__: GET

__Error Codes__:

* ERR_NOT_A_USER
* ERR_NOT_READABLE
* ERR_NOT_A_FOLDER
* ERR_NOT_READABLE
* ERR_DOES_NOT_EXIST
* ERR_INVALID_SORT_COLUMN
* ERR_INVALID_SORT_ORDER

__Request Query Params__:

* proxyToken - A valid CAS ticket.
* path - The path to list. Must be a directory.
* limit - The total number of results to return in a page. This is the number of folders and files combined.
* offset - The offset into the directory listing result set to begin the listing at.
* sort-col - The column to sort the result set by. Sorting is done in iRODS's ICAT database, not at the application level. Accepted values are NAME, PATH, LASTMODIFIED, DATESUBMITTED, SIZE. The values are case-insensitive.
* sort-order - The order to sort the result set in. Accepted values are ASC and DESC. The values are case-insensitive.

__Response Body__:

    {
        "date-created": "1369778522000",
        "date-modified": "1379520049000",
        "file-size": "0",
        "files": [
            {
                "date-created": "1379519492000",
                "date-modified": "1379520049000",
                "file-size": "196903039",
                "id": "/home/irods/iRODS/Vault/home/wregglej/centos-5.8-x86-64-minimal.box",
                "label": "centos-5.8-x86-64-minimal.box",
                "permissions": {
                    "own": true,
                    "read": true,
                    "write": true
                }
            }
        ],
        "folders": [
            {
                "date-created": "1374080225000",
                "date-modified": "1374080225000",
                "file-size": "0",
                "hasSubDirs": true,
                "id": "/iplant/home/wregglej/asdfafa",
                "label": "asdfafa",
                "permissions": {
                    "own": true,
                    "read": true,
                    "write": true
                }
            },
            {
                "date-created": "1377814242000",
                "date-modified": "1377814242000",
                "file-size": "0",
                "hasSubDirs": true,
                "id": "/iplant/home/wregglej/asdf bar",
                "label": "asdf bar",
                "permissions": {
                    "own": true,
                    "read": true,
                    "write": true
                }
            },
            {
                "date-created": "1373397344000",
                "date-modified": "1377558112000",
                "file-size": "0",
                "hasSubDirs": true,
                "id": "/iplant/home/wregglej/Find_Unique_Values_analysis1-2013-07-09-12-15-37.024",
                "label": "Find_Unique_Values_analysis1-2013-07-09-12-15-37.024",
                "permissions": {
                    "own": true,
                    "read": true,
                    "write": true
                }
            },
            {
                "date-created": "1374080529000",
                "date-modified": "1374080529000",
                "file-size": "0",
                "hasSubDirs": true,
                "id": "/iplant/home/wregglej/zaaaaaaaa",
                "label": "zaaaaaaaa",
                "permissions": {
                    "own": true,
                    "read": true,
                    "write": true
                }
            }
        ],
        "hasSubDirs": true,
        "id": "/iplant/home/wregglej",
        "label": "wregglej",
        "permissions": {
            "own": true,
            "read": true,
            "write": true
        },
        "success": true,
        "total" : 218
    }

__Curl Command__:

    curl "http://127.0.0.1:31325/secured/filesystem/paged-directory?proxyToken=asdfadsfa&path=/iplant/home/wregglej&sort-col=SIZE&sort-order=DESC&limit=5&offset=10"