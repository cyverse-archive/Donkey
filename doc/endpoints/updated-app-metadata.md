# Table of Contents

* [Updated Application Metadata Endpoints](#updated-application-metadata-endpoints)
    * [Updating or Importing a Single-Step App](#updating-or-importing-a-single-step-app)
    * [Exporting a Single-Step App](#exporting-a-single-step-app)
    * [Obtaining an App Representation for Editing](#obtaining-an-app-representation-for-editing)
    * [Obtaining App Information for Job Submission](#obtaining-app-information-for-job-submission)

# Updated Application Metadata Endpoints

## Updating or Importing a Single-Step App

*Secured Endpoint:* POST /secured/update-app

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Exporting a Single-Step App

*Unsecured Endpoint:* GET /export-app/{app-id}

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Obtaining an App Representation for Editing

*Secured Endpoint:* GET /secured/edit-app/{app-id}

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.

## Obtaining App Information for Job Submission

*Secured Endpoint:* GET /secured/app/{app-id}

This endpoint is a passthrough to the metadactyl endpoint using the same
path. Please see the metadactyl documentation for more information.
