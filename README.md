Agent Client Authorisation
==========================

[![Build Status](https://travis-ci.org/hmrc/agent-client-authorisation.svg?branch=master)](https://travis-ci.org/hmrc/agent-client-authorisation) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-client-authorisation/images/download.svg) ](https://bintray.com/hmrc/releases/agent-client-authorisation/_latestVersion)

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

API Overview
===

###Agent Oriented API

Verb | Path | Description
---|---|---|---
```GET```| ```/requests```| Retrieve the authorisation requests for the logged-in agency
```POST```| ```/requests```|  Created a new authorisation request for the designated client
```POST```| ```/requests/:request-id/cancel```| Cancel an previous authorisation request
```GET```| ```/clients/:regime/:regime-id?postcode=:postcode```| Look up a clients full name


###Client Oriented API

Verb | Path | Description
---|---|---|---
```GET```| ```/requests```| Retrieve the authorisation requests for the client
```GET```| ```/requests/:request-id```| Retrieve the authorisation request
```POST```| ```/requests/:request-id/:status```| Accept or Reject an authorisation request where status can be [accept, reject]


###Authorisation Request Statuses
TODO: Check the language used for these

Statuses  | Meaning
------- | -------
```Pending``` | The request has been created, it has not yet Expired nor been Accepted or Rejected by the client. Only the service can set this status
```Accepted``` | The client has accepted the reuqest. Only the client can set this status
```Rejected``` | The client has rejected the reuqest. Only the client can set this status
```Cancelled``` | The agent has cancelled the request. Only the agent can set this status (can an agent do this after the request has been accepted)
```Expired```| The request has not been actioned and the request is no longer available for decisioning


###Authorisation Request Model
Only valid state transitions for the user viewing the request (accept, reject, cancel) will be present.

```
{
  "_links": {
    "self": {"href": "/url/of/this/request"},
    "accept": {"href": "/url/to/accept/this/request"},
    "reject": {"href": "/url/to/reject/this/request"},
    "cancel": {"href": "/url/to/cancel/this/request"}
  },
  "status": "Accepted",
  "created": "2016-06-27T01:55:28+00.00",
  "lastUpdated": "2016-07-02T01:55:28+00.00",
  "regime" : "sa",
  "agentCode" : "123456789",  
  "agentFriendlyName" : "Sally Huges Accountants",
  "agentName" : "Bob McCratchet",  
  "clientRegimeId" : "123456789",  
  "clientFullName" : "John Smith"
}
```

Agent Oriented API Detail
---
###GET /requests

Status | Meaning
---|---
200 | Ok, return 0 or more client request (0 being an empty list of clients)
401 | The agent must be authenticated and authorised (logged-in) to use this resource
500 | All 5xx reponses are transposed to 500

####request parameters
*Note: This is just indicative - until we have some verified customer need*

Parameter | Meaning
---|---
status | filter requests by status == 'Accepted'
clientRegimeId | filter requests by regimeId
regime | filter requests by regime
agentCode | filter requests by regime
created | filter requests by time created


```
Example Response
TODO add a curl axample
{
	"requests": [{RequestModel}, {RequestModel}]}]
}
```

####POST /requests

Body Parameters | Example Value
---|---
```regime``` | 'sa'
```clientRegimeId``` | "1234567890"
```agentCode``` | "123456789"
```postcode``` | "N167PJ"

Status | Meaning
---|---
201 | Created, creation of duplicate requests is not prohibited
400 | Missing data from the post request body
401 | The agent must be authenticated and authorised (logged-in) to use this resource
403 | The request will not be fulfilled (the agent must have the requisite active enrolments and the client must be registered for that tax regime)
403 | Postcode matching fails
500 | All 5xx reponses are transposed to 500

```
Example Response
TODO add a curl axample
add location headers etc
```

####POST /requests/:request-id/:status
Used to cancel the agents request, canceling an 'Expired' request has no effect

Path Parameters | Example Value
---|---
```requestId``` | '213456tde45'
```status``` | cancel (this is the only valid status change the agent can make)

Status | Meaning
---|---
200 | Ok
401 | The agent must be authenticated and authorised (logged-in) to use this resource
403 | The request will not be fulfilled (see below)
404 | The status was not a valid value
500 | All 5xx reponses are transposed to 500

Reasons for 403 response may include
 
 * The agent does not have the requisite active enrolments 
 * The client is not registered for that tax regime
 * The agent has tried to cancel a request that is not in the 'Pending' state
 * The agent has tried to update the status of a request to anything other than 'Cancelled'
 * The cancel request has been made by anyone other than the originating agentCode


####GET /clients/:regime/:regime-id?postcode=:postcode

Path Parameter | Example Value
---|---
```regime``` | 'sa'
```regime id``` | "SA_UTR"
```postcode``` | "BN29UH"

Status | Meaning
---|---
200 | Ok
401 | The agent must be authenticated and authorised (logged-in) to use this resource
403 | The request will not be fulfilled. The combination regime, regime-id and postcode did not result in a valid match
500 | All 5xx reponses are transposed to 500

```
Example Response
TODO add a curl axample
{
    "fullName": "John Smith"
}
```

Client API Details
---

####GET /requests
*Note: query parameters could be included if needed by clients*

Status | Meaning
---|---
200 | Ok
401 | The client must be logged-in
500 | All 5xx reponses are transposed to 500

```
Example Response
TODO add a curl axample
{
	"requests": [{RequestModel}, {RequestModel}]}]
}
```

####POST /requests/:request-id/:status
Used to accept and reject an authorisation request. Accepting an already 'accepted' request has no effect, any other status update to a decisioned request is Forbidden.

Path Parameters | Example Value
---|---
```request id``` | '213456tde45'
```status``` | accept or reject

Status | Meaning
---|---
200 | Ok
401 | The client must be logged-in
403 | The request will be not fulfilled or is no longer available for decisioning (see below)
500 | All 5xx reponses are transposed to 500

Reasons for 403 response my include
 
 * The client is not registered for that tax regime
 * The client has tried to update the status of a request to anything other than 'Accepted' or 'Rejected'
 * The request is not in the 'Pending' state
 * The request has either been 'Cancelled' or has 'Expired'
 * The accept request was not made for the designated client of the request
