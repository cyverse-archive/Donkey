
# Callback Endpoints

These endpoints listed in this document accept callbacks from other services
indicating that some event has occurred.

## Receiving DE Notifications

Unsecured Endpoint: POST /notification

This endpoint accepts notifications from the notification agent. Please see the
notification agent documentation for information about the post body. This
service is currently used to update the statuses of DE jobs in the database.
