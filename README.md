Agent Client Authorisation
==========================

[![Build Status](https://travis-ci.org/hmrc/agent-client-authorisation.svg?branch=master)](https://travis-ci.org/hmrc/agent-client-authorisation) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-client-authorisation/images/download.svg) ](https://bintray.com/hmrc/releases/agent-client-authorisation/_latestVersion)

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

API Overview (Iteration 1 - Agent Led)
===


Verb | Path | Description
---|---|---|---
```GET```| ```/agent-client-authorisation/invitations```| Retrieve the authorisation requests for the logged-in agency or client
```POST```| ```/agent-client-authorisation/invitations```|  Created a new authorisation request for the designated recipient
```POST```| ```/agent-client-authorisation/invitations/:request-id/:status```| Attempt to alter the request status to a new state
```GET```| ```/agent-client-authorisation```| ??? Sensible entry point TBC agent-details or next steps. Informtion about the agency and the options that are available to the agency  ???


###Authorisation Request Statuses
TODO: Check the language used for these

Statuses  | Meaning
------- | -------
```pending``` | The request has been created, it has not been accepted or rejected by the recipient. Only the service can set this status
```accepted``` | The recipient has accepted the request. Only the recipient can set this status
```rejected``` | The recipient has rejected the request. Only the recipient can set this status
```cancelled``` | The agency has cancelled the request. Only the agency can set this status 

###Authorisation Invitation Model
Only valid state transitions for the user viewing the request (accept, reject, cancel) will be present.

```
{
  "_links": {
    "self": {"href": "/url/of/this/request"},
    "accept": {"href": "/url/to/accept/this/request"},
    "reject": {"href": "/url/to/reject/this/request"},
    "cancel": {"href": "/url/to/cancel/this/request"}
  },
  "created": "2016-06-27T01:55:28+00.00",
  "lastUpdated": "2016-07-02T01:55:28+00.00",
  "regime" : "sa",
  "agencyName" : "Sally Hughes Accountants",
  "agentName" : "Bob McCratchet",  
  "clientRegimeId" : "123456789"
}
```

###GET /agent-client-authorisation/agencies/:id/invitations
###GET /agent-client-authorisation/customer;sa=utr

Status | Meaning
---|---
200 | Ok, return 0 or more client request (0 being an empty list of clients)
401 | The agent must be authenticated and authorised (logged-in) to use this resource
500 | All 5xx responses are transposed to 500

####request parameters
*Note: This is just indicative - until we have some verified customer need*
*ATTENTION!! Does auth convert ggAgentCodes to ARNs??*
Parameter | Meaning
---|---
status | filter requests by status == 'accepted'
clientRegimeId | filter requests by regimeId
regime | filter requests by regime


```
Example Response
TODO add a curl axample
{
	"requests": [{RequestModel}, {RequestModel}]}]
}
```

####POST /agent-client-authorisation/agencies/:id/invitations

Body Parameters | Example Value
---|---
```regime``` | 'sa'
```clientRegimeId``` | "1234567890"

Status | Meaning
---|---
201 | Created, creation of duplicate requests is allowed
400 | Missing data from the post request body (JSON)
401 | The agent must be authenticated and authorised (logged-in) to use this resource
403 | The request will not be fulfilled (TBC: the agent must have the requisite active enrolments and/or the client must be registered for that tax regime)
500 | All 5xx responses are transposed to 500

```
Example Request
{
  "regime" : "sa",
  "clientRegimeId" : "123456789"
}
```

```
Example Response
TODO add a curl axample
add location headers etc
```

####PUT /agent-client-authorisation/agencies/:id/invitations/:invitation-id/:status
Used to cancel the request

Path Parameters | Example Value
---|---
```requestId``` | '213456tde45'
```status``` | cancelled (this is the only valid status change the agent can make)

Status | Meaning
---|---
200 | Ok
401 | The agent must be authenticated and authorised (logged-in) to use this resource
403 | The request will not be fulfilled (see below)
404 | The status was not a valid value
500 | All 5xx responses are transposed to 500

Reasons for 403 response may include
 
 * The agency does not have the requisite active enrolments 
 * The client is not registered for that tax regime
 * The agency has tried to update the status of a request to anything other than 'Cancelled'
 * The client has tried to update the status of a request to anything other than 'Accepted' or 'Rejected'
 * The cancel request has been made by anyone other than the originating agentCode
