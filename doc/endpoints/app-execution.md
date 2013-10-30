# Table of Contents

* [Application Execution Endpoints](#application-execution-endpoints)
    * [Obtaining Property Values for a Previously Executed Job](#obtaining-property-values-for-a-previously-executed-job)
    * [Obtaining Information to Rerun a Job](#obtaining-information-to-rerun-a-job)
    * [Obtaining Information to Rerun a Job in the New Format](#obtaining-information-to-rerun-a-job-in-the-new-format)
    * [Submitting a Job for Execution](#submitting-a-job-for-execution)
    * [Listing Jobs](#listing-jobs)
    * [Deleting Jobs](#deleting-jobs)
    * [Stopping a Running Analysis](#stopping-a-running-analysis)

# Application Execution Endpoints

Note that secured endpoints in Donkey and metadactyl are a little different from
each other. Please see [Donkey Vs. Metadactyl](donkey-v-metadactyl.md) for more
information.

## Obtaining Property Values for a Previously Executed Job

Secured Endpoint: GET /secured/get-property-values/{job-id}

TODO: Rewrite the documentation for this endpoint.

## Obtaining Information to Rerun a Job

Unsecured Endpoint: GET /analysis-rerun-info/{job-id}

Delegates to metadactyl: GET /analysis-rerun-info/{job-id}

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Obtaining Information to Rerun a Job in the New Format

Unsecured Endpoint: GET /app-rerun-info/{job-id}

Delegates to metadactyl: GET /app-rerun-info/{job-id}

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Submitting a Job for Execution

Secured Endpoint: PUT /secured/workspaces/{workspace-id}/newexperiment

Delegates to metadactyl: PUT /secured/workspaces/{workspace-id}/newexperiment

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Listing Jobs

*Secured Endpoint:* GET /secured/workspaces/{workspace-id}/executions/list

Information about the status of jobs that have previously been submitted for
execution can be obtained using this service. The DE uses this service to
populate the _Analyses_ window. The response body for this service is in the
following format:

```json
{
    "analyses": [
        {
            "analysis_details": "analysis-description",
            "analysis_id": "analysis-id",
            "analysis_name": "analysis-name",
            "app_disabled": false,
            "description": "job-description",
            "enddate": "end-date-as-milliseconds-since-epoch",
            "id": "job-id",
            "name": "job-name",
            "resultfolderid": "path-to-result-folder",
            "startdate": "start-date-as-milliseconds-since-epoch",
            "status": "job-status-code",
            "wiki_url": "analysis-documentation-link"
        },
        ...
    ],
    "success": true,
    "timestamp": "timestamp",
    "total": "total"
}
```

With no query string parameters aside from `user` and `email`, this service
returns information about all jobs ever run by the user that haven't been marked
as deleted in descending order by start time (that is, the `startdate` field in
the result). Several query-string parameters are available to alter the way this
service behaves:

<table border="1">
     <thead>
        <tr>
            <th>Name</th>
            <th>Description</th>
            <th>Default</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>limit</td>
            <td>
                The maximum number of results to return.  If this value is zero
                or negative then all results will be returned.
            </td>
            <td>0</td>
        </tr>
        <tr>
            <td>offset</td>
            <td>The index of the first result to return.</td>
            <td>0</td>
        </tr>
        <tr>
            <td>sort-field</td>
            <td>
                The name of the field that results are sorted by. Valid values
                for this parameter are `name`, `analysis_name`, `startdate`,
                `enddate`, and `status`.
            </td>
            <td>startdate</td>
        </tr>
        <tr>
            <td>sort-order</td>
            <td>
                `asc` or `ASC` for ascending and `desc` or `DESC` for descending.
            </td>
            <td>desc</td>
        </tr>
    </tbody>
</table>

Here's an example using no parameters:

```
$ curl -s "http://by-tor:8888/secured/workspaces/4/executions/list?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "analyses": [
        {
            "analysis_details": "Count words in a file",
            "analysis_id": "wc-1.00u1",
            "analysis_name": "Word Count",
            "app-disabled": false,
            "description": "",
            "enddate": "1382979411000",
            "id": "31499",
            "name": "wc_10280954",
            "resultfolderid": "/iplant/home/snow-dog/analyses/wc_10280954",
            "startdate": "1382979299935",
            "status": "Completed",
            "wiki_url": ""
        },
        ...
    ],
    "success": true,
    "timestamp": "1383000130668",
    "total": 23
}
```

Here's an example of a search with a limit of one result:

```
$ curl -s "http://by-tor:8888/secured/workspaces/4/executions/list?proxyToken=$(cas-ticket)&limit=1" | python -mjson.tool
{
    "analyses": [
        {
            "analysis_details": "Count words in a file",
            "analysis_id": "wc-1.00u1",
            "analysis_name": "Word Count",
            "app-disabled": false,
            "description": "",
            "enddate": "1382979411000",
            "id": "31499",
            "name": "wc_10280954",
            "resultfolderid": "/iplant/home/snow-dog/analyses/wc_10280954",
            "startdate": "1382979299935",
            "status": "Completed",
            "wiki_url": ""
        }
    ],
    "success": true,
    "timestamp": "1383000130668",
    "total": 23
}
```

## Deleting Jobs

*Secured Endpoint:* PUT /secured/workspaces/{workspace-id}/executions/delete

After a job has completed, a user may not want to view the job status
information in the _Analyses_ window any longer. This service provides a way to
mark job status information as deleted so that it no longer shows up. The
request body for this service is in the following format:

```json
{
    "executions": [
        "job-id-1",
        "job-id-2",
        ...,
        "job-id-n"
    ]
}
```

The response body for this endpoint contains only a status flag if the service
succeeds.

It should be noted that this service does not fail if any of the job identifiers
refers to a non-existent or deleted job. If the identifier refers to a deleted
job then the update is essentially a no-op. If a job with the identifier can't
be found then a warning message is logged in metadactyl-clj's log file, but the
service does not indicate that a failure has occurred.

Here's an example:

```
$ curl -X PUT -sd '
{
    "executions": [
        "84DFCC0E-03B9-4DF4-8484-55BFBD6FE841",
        "FOO"
    ]
}
' "http://by-tor:8888/secured/workspaces/4/executions/delete?proxyToken=$(cas-ticket)" | python -mjson.tool
{
    "success": true
}
```

## Stopping a Running Analysis

Secured Endpoint: DELETE /secured/stop-analysis/{job-id}

Delegates to JEX: DELETE /stop/{job-id}

This endpoint is a passthrough to the JEX endpoint, `/stop/{job-id}`. Please see
the JEX documentation for more details.
