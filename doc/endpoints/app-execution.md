# Table of Contents

* [Application Execution Endpoints](#application-execution-endpoints)
    * [Obtaining Property Values for a Previously Executed Job](#obtaining-property-values-for-a-previously-executed-job)
    * [Obtaining Information to Rerun a Job](#obtaining-information-to-rerun-a-job)
    * [Obtaining Information to Rerun a Job in the New Format](#obtaining-information-to-rerun-a-job-in-the-new-format)
    * [Submitting a Job for Execution](#submitting-a-job-for-execution)
    * [Listing Jobs](#listing-jobs)
    * [Getting Status Information for Selected Jobs](#getting-status-information-for-selected-jobs)
    * [Deleting Jobs](#deleting-jobs)
    * [Stopping a Running Analysis](#stopping-a-running-analysis)

# Application Execution Endpoints

## Obtaining Property Values for a Previously Executed Job

Unsecured Endpoint: GET /get-property-values/{job-id}

Delegates to metadactyl: GET /get-property-values/{job-id}

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

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

Secured Endpoint: GET /secured/workspaces/{workspace-id}/executions/list

Delegates to metadactyl: GET /secured/workspaces/{workspace-id}/executions/list

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Getting Status Information for Selected Jobs

Secured Endpoint: POST /secured/workspaces/{workspace-id}/executions/list

Delegates to metadactyl: POST /secured/workspaces/{workspace-id}/executions/list

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Deleting Jobs

Secured Endpoint: PUT /secured/workspaces/{workspace-id}/executions/delete

Delegates to metadactyl: PUT /secured/workspaces/{workspace-id}/executions/delete.

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Stopping a Running Analysis

Secured Endpoint: DELETE /secured/stop-analysis/{job-id}

Delegates to JEX: DELETE /stop/{job-id}

This endpoint is a passthrough to the JEX endpoint, `/stop/{job-id}`. Please see
the JEX documentation for more details.
