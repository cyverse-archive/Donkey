# Application Execution Endpoints

## Obtaining Property Values for a Previously Executed Job

Unsecured Endpoint: GET /get-property-values/{job-id}

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Obtaining Information to Rerun a Job

Unsecured Endpoint: GET /analysis-rerun-info/{job-id}

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Submitting a Job for Execution

Secured Endpoint: PUT /secured/workspaces/{workspace-id}/newexperiment

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Listing Jobs

Secured Endpoint: GET /secured/workspaces/{workspace-id}/executions/list

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Getting Status Information for Selected Jobs

Secured Endpoint: POST /secured/workspaces/{workspace-id}/executions/list

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Deleting Jobs

Secured Endpoint: PUT /secured/workspaces/{workspace-id}/executions/delete

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Stopping a Running Analysis

Secured Endpoint: DELETE /secured/stop-analysis/{job-id}

This endpoint is a passthrough to the JEX endpoint, `/stop/{job-id}`. Please see
the JEX documentation for more details.
