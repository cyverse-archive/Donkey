Setting a Tree URL for a File
-----------------------------
Multiple tree URLs can be associated with a file. To support this, multiple POSTs of tree-urls for the same file will append the URLs to the list stored in iRODS. Multiple tree URLs can be associated with a file in a single request. You still need to place a URL in a list even if you're only associated one URL with a file.

Something to note is that we're not checking to make sure that the strings in the 'tree-urls' list are actually URLs. This is intentional and will hopefully make migrating to a token based tree retrieval a little simpler.

__URL Path__: /secured/filesystem/file/tree-urls

__HTTP Method__: POST

__Error Codes__: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

__Request Query Parameters__:

* proxyToken - A valid CAS ticket.

__Request Body__:

    {
        "tree-urls" : [
            {
                "label" : "google",
                "url" : "http://www.google.com"
            },
            {
                "label" : "yahoo",
                "url" : "http://www.yahoo.com"
            }
        ]
    }

__Response Body__:

    {
        "status":"success",
        "path":"\/iplant\/home\/johnw\/LICENSE.txt",
        "user":"johnw"
    }

__Curl Command__:

    curl -d '{"tree-urls" : [{"label" : "google", "url" : "http://www.google.com"}, {"label" : "yahoo", "url" : "http://www.yahoo.com"}]}' 'http://127.0.0.1:3000/secured/filesystem/file/tree-urls?proxyToken=&path=/iplant/home/johnw/LICENSE.txt'



Getting a Tree URL for a File
-----------------------------

__URL Path__: /secured/filesystem/file/tree-urls

__HTTP Method__: GET

__Error Codes__: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

__Request Query Parameters__:

* proxyToken - A valid CAS ticket.
* path - Path to a file in iRODS.

__Response Body__:

    [
        {
            "label" : "google",
            "url" : "http://www.google.com"
        },
        {
            "label" : "yahoo",
            "url" : "http://www.yahoo.com"
        }
    ]

__Curl Command__:

    curl 'http://127.0.0.1:3000/file/tree-urls?user=rods&path=/iplant/home/johnw/LICENSE.txt'



Deleting a Tree URL
-------------------

See the instructions for deleting metadata associated with a file. The attribute name for the tree URLs is 'tree-urls'.