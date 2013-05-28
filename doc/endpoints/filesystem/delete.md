Deleting Files and/or Directories
---------------------------------
__URL Path__: /secured/filesystem/delete

__HTTP Method__: POST

__Action__: "delete"

__Error Codes__: ERR_NOT_A_FOLDER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

__Request Parameters__:

* proxyToken - A valid CAS ticket.

__Request Body__:

    {
        "paths" : ["/tempZone/home/rods/test2"]
    }

"paths" can take a mix of files and directories.

__Response__:

    {
        "action":"delete-dirs",
        "paths":["/tempZone/home/rods/test2"]
        "status" : "success"
    }

__Curl Command__:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/tempZone/home/rods/test2"]}' http://127.0.0.1:3000/secured/filesystem/delete?user=rods




