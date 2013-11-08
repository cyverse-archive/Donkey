CSV/TSV Parsing
-------------------------

__URL Path__: /secured/filesystem/read-csv-chunk

__HTTP Method__: POST

__Request Query Parameters__:

* proxyToken - A valid CAS ticket.

__Request Body__:

    {
    	"path" : "/iplant/home/wregglej/download.csv",
    	"position" : "0",
    	"chunk-size" : "4096",
    	"separator" : ","
    }

__Response Body__:

    {
	    "chunk-size": "3669",
	    "csv": [
	        [
	            "87c781b2d32993898bf7532c1d78a823",
	            "phi84",
	            "Fri Apr 26 21:33:40 CEST 2013",
	            "didaktik pr\u00fcfungsliteratur",
	            "public",
	            "besonders lesenswert: \"Erster Teil\"",
	            "Neue Studien zur Bildungstheorie und Didaktik: Zeitgemaesse Allgemeinbildung und kritisch-	konstruktive Didaktik",
	            "klafki1993",
	            "",
	            "",
	            "Klafki, Wolfgang",
	            "book",
	            "Beltz",
	            "1993",
	            "",
	            "",
	            ""
	        ],
	        [
	            "fc447593b5d0f42b67e45b9d1cf2655d",
	            "miki",
	            "Fri Apr 26 19:35:35 CEST 2013",
	            "mfp",
	            "public",
	            "[1304.6719] The Column Density Distribution and Continuum Opacity of the Intergalactic and 	Circumgalactic Medium at Redshift &lt;z&gt;=2.4",
	            "The Column Density Distribution and Continuum Opacity of the\n  Intergalactic and Circumgalactic 	Medium at Redshift <z>=2.4",
	            "rudie2013column",
	            "",
	            "",
	            "Rudie, Gwen C. and Steidel, Charles C. and Shapley, Alice E. and Pettini, Max",
	            "misc",
	            "cite arxiv:1304.6719Comment: Accepted to ApJ",
	            "2013",
	            "",
	            "",
	            "We present new high-precision measurements of the opacity of the\nintergalactic and circumgalactic 	medium (IGM, CGM) at <z>=2.4. Using Voigt\nprofile fits to the full Lyman alpha and Lyman beta 	forests in 15\n"
	        ]
	    ],
	    "end": "3668",
	    "file-size": "9502",
	    "max-cols" : "17",
	    "path": "/iplant/home/wregglej/download.csv",
	    "start": "352",
	    "success": true,
	    "user": "wregglej"
	}

__path__ is the path to the file in iRODS that should be parsed as a CSV. Note that there isn't any checking in place to make sure that the file is actually a CSV or TSV. This is because we can't depend on the filetype detection to detect all possible types of CSV files (i.e. tab-delimited, pipe-delimited, hash-delimited, etc.).

__position__ is the starting position, in bytes, that we'll attempt to start parsing from. The parser may not actually start at this position, however, since it will search out the first available '\n' and begin the parsing from that point. The actual parsing position is reported back in the response body as the __start__ field in the JSON response body.

__chunk-size__ is the max-size of the chunk to read. The actual size of the chunk may be smaller than the chunk requested. This is because the parsing logic will seek backwards from the end of the chunk until it hits a '\n'. This should be the last line ending in the file. The actual end point of the chunk is reported back as the __end__ field in the JSON response body.

__separator__ is a single character that the CSV parser uses to split fields. Common values are "," and "\t". We don't do any validation on this field so that we can support a wider-range of parsing options. The only constraints on this field is that it needs to be readable as a single char and it must be URL encoded.

__Notes and Limitations__:

This *should* work fine with files that use \r\n as the line ending, but it will not work correctly with files that use \r alone. It that case every page returned will be blank.

If the chunk-size is set so that it doesn't cover an entire line, a blank page will be returned.

The code that trims and resizes the pages to end on line breaks will detect '\n' that are embedded in double quoted cells as a line break. This is because we're not tracking opening and closing double quotes across pages. We're looking into ways of doing this, but it isn't in yet.

The URL encoded value for \t characters is '%09', without the quotes. If you aren't sending '%09' for the separator when you're trying to parse a TSV, then you're going to have a bad time.

