# File Type Endpoints

NOTES: If a path appears in a query string parameter, URL encode it first.

Error code maps follow the general format of Nibblonian's errors:

    {
        err_code: "ERR_CODE_STRING",
 		...
    }

Currently Supported File Types
------------------------------

* "ace"
* "blast"
* "bowtie"
* "clustalw"
* "codata"
* "csv"
* "embl"
* "fasta"
* "fastq"
* "fastxy"
* "game"
* "gcg"
* "gcgblast"
* "gcgfasta"
* "gde"
* "genbank"
* "genscan"
* "gff"
* "hmmer"
* "nexus"
* "mase"
* "mega"
* "msf"
* "phrap"
* "pir"
* "pfam"
* "phylip"
* "prodom"
* "raw"
* "rsf"
* "selex"
* "stockholm"
* "swiss"
* "tab"
* "vcf"

Some endpoints may also return file types detected by Apache Tika.


Get the list of supported file types
---------------------------------------

__URL Path__: /secured/filetypes/type-list

__HTTP Method__: GET

__Error Codes__: None

__Request Query Parameters__:
* proxyToken - A valid CAS proxy token.

__Response Body__:

    {
        "types" : ["csv, "tsv"]
    }

__Curl Command__:

    curl http://donkey.example.org:31325/secured/filetypes/type-list?proxyToken=notARealOne


Get the file types associated with a file
------------------------------------------

__URL Path__: /secured/filetypes/type

__HTTP Method__: GET

__Error Codes__: ERR_DOES_NOT_EXIST, ERR_NOT_A_USER, ERR_NOT_READABLE

__Request Query Parameters__:
* proxyToken - A valid CAS proxyToken
* path - A path to a file in iRODS

__Response Body__:

	{
        "types" : ["csv", ...]
    }

__Curl Command__:

    curl 'http://donkey.example.org:31325/secured/filetypes/type?proxyToken=notARealOne&path=/path/to/irods/file'

This endpoint can also return any media type detectable by Apache Tika.

The values are determined by looking at the values associated with the ipc-filetype attribute in the AVUs
associated with the file.


Add a file type to a file
-------------------------

__URL Path__: /secured/filetypes/type

__HTTP Method__: POST

__Error Codes__: ERR_NOT_OWNER, ERR_BAD_OR_MISSING_FIELD,ERR_DOES_NOT_EXIST,ERR_NOT_A_USER

__Request Query Parameters__:
* proxyToken - A valid CAS proxy token.

__Request Body__:

    {
        "path" : "/path/to/irods/file",
        "type" : "csv"
    }

__Response Body__:

    {
        "path" : "/path/to/irods/file",
        "type" : "csv"
    }

__Curl Command__:

    curl -d '{"path" : "/path/to/irods/file","type":"csv"}' 'http://donkey.example.org:31325/secured/filetypes/type?proxyToken=notARealOne'


Delete a file type from a file
------------------------------

__URL Path__: /secured/filetypes/type

__HTTP Method__: DELETE

__Error Codes__: ERR_NOT_OWNER, ERR_BAD_OR_MISSING_FIELD, ERR_DOES_NOT_EXIST, ERR_NOT_A_USER

__Request Query Parameters__:
* proxyToken - A valid CAS proxy token.
* path - A path to a file in iRODS.
* type - The type to delete

__Response Body__:

    {
        "path" : "/path/to/irods/file",
        "type" : "csv"
    }

__Curl Command__:

    curl -X DELETE 'http://donkey.example.org:31325/secured/filetypes/type?proxyToken=notARealOne&type=csv&path=/path/to/irods/file'


Look up paths in a user's home directory based on file type
-----------------------------------------------------------

__URL Path__: /secured/filetypes/type/paths

__HTTP Method__: GET

__Error Codes__: ERR_NOT_A_USER

__Request Parameters__:
* proxyToken - A valid CAS proxy token.
* type - A valid file type.

__Response Body__:

    {
        "paths" : ["/path/to/irods/file"]
    }

__Curl Command__:

    curl 'http://donkey.example.org:31325/secured/filetypes/type/paths?proxyToken=notARealOne&type=csv'


