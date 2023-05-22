Agent Client Authorisation
==========================

This application serves as the backend for [agent-invitations-frontend](https://github.com/hmrc/agent-invitations-frontend) containing the logic for creating and updating invitations.

[![Build Status](https://travis-ci.org/hmrc/agent-client-authorisation.svg)](https://travis-ci.org/hmrc/agent-client-authorisation)

### API docs
Refer to [RAML documentation](https://github.com/hmrc/agent-client-authorisation/blob/master/resources/public/api/conf/0.0/application.raml) for further details on each API.
   

## Table of Contents
*   [Supported Regimes / Services](#supportedRegimes)
*   [Invitation Status](#invitationStatus)
*   [Agent APIs](#agentApis)
    *   [Create Invitation](#createInvitation)
    *   [GET a Specific Agent's Sent Invitation](#agentSpecificInvitation)
    *   [GET all of Agent's Sent Invitation](#agentInvitations)
    *   [GET Known Fact for VAT](#vatKnownFact)
*   [Client APIs](#clientApis)
    * [Client Accepts Invitation](#acceptInvitation)
    * [Client Rejects Invitation](#rejectInvitation)
    * [GET Client Specific Invitation](#clientSpecificInvitation)
    * [GET All Client Invitations](#clientAllInvitations)
*   [Running the tests](#runningTests)    
*   [Running the application locally](#runningLocal)  

### Supported Regimes / Services <a name="supportedRegimes"></a>
This supports Agent and Client authorisation processes for the following regimes (aka services):

|Regime|Service|
|--------|--------|
|Self Assessment|HMRC-MTD-IT|
|Income-Record-Viewer for Individuals|PERSONAL-INCOME-RECORD|
|Value-Added-Tax|HMRC-MTD-VAT|
|For Trust|HMRC-TRUST-ORG|
|Capital Gains Tax| HMRC-CGT-PD|


### Invitation Status <a name="invitationStatus"></a>
Invitations can have one of the following status:

|Invitation Status|Description|
|--------|---------|
|Pending|Default status when an invitation has been created|
|PartialAuth|For ITSA only - allows agent to sign-up client to ITSA for a limited time
|Accepted|Allows Agent to be authorised to act on behalf of a client|
|Rejected|Prevents Agent being authorised to act on a client's behalf|
|Expired|Client did not respond to the Agent's Invitation within 21 days|
|Cancelled|Agent cancels the invitation they sent out, preventing a client from responding|

Note: Invitations with "Pending" or "PartialAuth" statuses are the only editable status.
  

## Agent APIs <a name="agentApis"></a>
The following APIs require agent authentication. 

Any unauthorised access could receive one of the following responses:

|Response|Description|
|--------|---------|
|401|Unauthorised. Not logged In|
|403|The Agent is not subscribed to Agent Services.|
|403|The logged in user is not permitted to access invitations for the specified agency.|


#### Create Invitation <a name="createInvitation"></a>
Validates the service, clientIdentifier and type, then creates an invitation.

```
POST  /agencies/:arn/invitations/sent
```

Request:
```
http://localhost:9432/agent-client-authorisation/agencies/TARN0000001/invitations/sent

```
Example Body of ITSA:
```json
{
  "service": "HMRC-MTD-IT",
  "clientType":"personal",
  "clientIdType": "ni",
  "clientId": "AB123456A"
}
```

Example Body of VAT:
```json
{
  "service": "HMRC-MTD-VAT",
  "clientType":"personal / business",
  "clientIdType": "vrn",
  "clientId": "101747696"
}
```

Example Body of IRV:
```json
{
  "service": "PERSONAL-INCOME-RECORD",
  "clientType":"personal",
  "clientIdType": "ni",
  "clientId": "AE123456C"
}
```

Example Body of Trust
```json
{
  "service": "HMRC-TERS-ORG",
  "clientType":"business",
  "clientIdType": "utr",
  "clientId": "2134514321"
}

```

Example of CGT:
```json
{
  "service": "HMRC-CGT-PD",
  "clientType":"personal / business",
  "clientIdType": "CGTPDRef",
  "clientId": "XMCGTP123456789"
}
```

|Response|Description|
|--------|---------|
|201|Successfully created invitation. (In Headers) Location → "/agencies/:arn/invitations/sent/:invitationdId"|
|400|Received Valid Json but incorrect data|
|400|Received Invalid Json|
|403|Client Registration Not Found|
|501|Unsupported Service|

Note: The link returned from a successful create invitation response is "GET a Specific Agent's Sent Invitation"

#### Create Multi-Invitation Link <a name="createMultiInvitationLink"></a>
Create a link to represent multi-invitation

```
POST  /agencies/:arn/multi-invitations/
```

Request:
```
http://localhost:9432/agent-client-authorisation/agenices/TARN0000001/multi-invitations/

```
Example Body:
```json
{
  "clientType": "personal",
  "invitationIds": ["FOO123BAR"]
}
```

|Response|Description|
|--------|---------|
|201|Successfully created invitation link. (In Headers) Location → "/invitations/:clientType/:uid/:normalisedAgencyName"|
|400|Received Valid Json but incorrect data|
|400|Received Invalid Json|


#### GET a Specific Agent's Sent Invitation <a name="agentSpecificInvitation"></a>
Retrieves a specific invitation by its InvitationId
```
GET   /agencies/:arn/invitations/sent/:invitationId
```

Request:
```
http://localhost:9432/agent-client-authorisation/agenices/TARN0000001/invitations/sent/CS5AK7O8FPC43
```

|Response|Description|
|--------|---------|
|200|Returns an invitation in json|
|403|The specified invitation is not accessible for this ARN.|
|404|The specified invitation was not found.|

Example Response: 200 with Body:
```json
{
   "arn" : "TARN0000001",
   "service" : "HMRC-MTD-VAT",
   "lastUpdated" : "2018-05-04T13:51:35.278Z",
   "created" : "2018-04-16T15:05:54.029Z",
   "clientIdType" : "vrn",
   "clientId" : "101747641",
   "expiryDate" : "2018-04-26",
   "suppliedClientIdType" : "vrn",
   "suppliedClientId" : "101747641",
   "_links" : {
      "self" : {
         "href" : "/agent-client-authorisation/agencies/TARN0000001/invitations/sent/CS5AK7O8FPC43"
      }
   },
   "status" : "Expired"
}
```

#### GET all of Agent's Sent Invitation <a name="agentInvitations"></a>
Retrieves all invitations sent by the Agent. 
Returned list if sorted by `created` field descending, newest invitations first.
```
GET   /agencies/:arn/invitations/sent
```

Optional query filters:

|query param|format|description|default|
|--------|---------|---------|---------|
|service|enum|returns only invitations for the specified service|none|
|status|enum|returns only invitations having specified status|none|
|createdOnOrAfter|yyy-MM-dd|returns only invitations created on or after the specified date|none|

Request:
```
http://localhost:9432/agent-client-authorisation/agenices/TARN0000001/invitations/sent
http://localhost:9432/agent-client-authorisation/agenices/TARN0000001/invitations/sent?status=Pending
http://localhost:9432/agent-client-authorisation/agenices/TARN0000001/invitations/sent?service=HMRC-MTD-VAT
http://localhost:9432/agent-client-authorisation/agenices/TARN0000001/invitations/sent?status=Pending&service=HMRC-MTD-VAT
http://localhost:9432/agent-client-authorisation/agenices/TARN0000001/invitations/sent?createdOnOrAfter=2018-01-01
http://localhost:9432/agent-client-authorisation/agenices/TARN0000001/invitations/sent?createdOnOrAfter=2018-01-01&status=Accepted
```

|Response|Description|
|--------|---------|
|200|Returns all invitations in json array|

Example Response: 200 with Body:
```json
[{
   "arn" : "TARN0000001",
   "service" : "HMRC-MTD-VAT",
   "lastUpdated" : "2018-05-04T13:51:35.278Z",
   "created" : "2018-04-16T15:05:54.029Z",
   "clientIdType" : "vrn",
   "clientId" : "101747641",
   "expiryDate" : "2018-04-26",
   "suppliedClientIdType" : "vrn",
   "suppliedClientId" : "101747641",
   "_links" : {
      "self" : {
         "href" : "/agent-client-authorisation/agencies/TARN0000001/invitations/sent/CS5AK7O8FPC43"
      }
   },
   "status" : "Expired"
}]
```
#### GET Known Fact for ITSA <a name="itsaKnownFact"></a>
Checks a known fact for a given Postcode.

```
GET   /known-facts/individuals/nino/:nino/sa/postcode/:postcode
```

Request
```
http://localhost:9432/agent-client-authorisation//known-facts/individuals/nino/AB123456A/sa/postcode/DH14EJ
```

|Response|Description|
|--------|---------|
|204|There is a record found for given nino and postcode|
|403|There is a record for given nino but with a different postcode|
|404|There is no record found for given nino|

#### GET Known Fact for IRV <a name="irvKnownFact"></a>
Checks a known fact for a given Date of Birth.

```
GET   /known-facts/individuals/:nino/dob/:dob
```

Request
```
http://localhost:9432/agent-client-authorisation/known-facts/individuals/AB123456A/dob/1993-09-21 
```

|Response|Description|
|--------|---------|
|204|There is a record found for given nino and date|
|403|There is a record for given nino but with a different date|
|404|There is no record found for given nino|


#### GET Known Fact for VAT <a name="vatKnownFact"></a>
Checks a known fact for a given Vat Registration Number.

```
GET   /known-facts/organisations/vat/:vrn/registration-date/:vatRegistrationDate
```

Request
```
http://localhost:9432/agent-client-authorisation/known-facts/organisations/vat/101747641/registration-date/2010-04-01
```

|Response|Description|
|--------|---------|
|204|There is a record found for given vrn and date|
|403|There is a record for given vrn but with a different date|
|404|There is no record found for given vrn|


## Client APIs <a name="clientApis"></a>
The following APIs require client authentication. Any requests to access without authentication will be redirected to login page. 


|Regime|Auth Service|Service|Service-Api|Client-Identifier-Type|
|--------|--------|---------|---------|---------|
|Self Assessment|HMRC-MTD-IT|Same|MTDITID|ni|
|Income-Record-Viewer for Individuals|HMRC-NI|PERSONAL-INCOME-RECORD|NI|ni|
|Value-Added-Tax|HMRC-MTD-VAT|Same|VAT|vrn|

Any unauthorised access could receive one of the following responses:

|Response|Description|
|--------|---------|
|401|Unauthorised. Not logged In|
|403|The Client Identifier is not found in the user's login profile|


#### Client Accepts Invitation <a name="acceptInvitation"></a>
Changes the status of a "Pending" Invitation to "Accepted". As a result of accepting an invitation, a relationship record is established to allow an agent to act on their behalf. See [agent-client-relationships](https://github.com/hmrc/agent-client-relationships) for further details.

For HMRC-MTD-IT, a client may successfully accept an invitation without having the ITSA enrolment. In this case the status moves from Pending to PartialAuth and for a limited time allows the agent to sign up the client to ITSA. The creation of a full relationship is deferred until the client has acquired the ITSA enrolment.  
```
PUT   /clients/(service-api)/(associated-clientIdentifier)/invitations/received/:invitationId/accept
```

Example Requests
```
http://localhost:9432/agent-client-authorisation/clients/MTDITID/ABCDEF123456789/invitations/received/ANRJ9OCEGZR1T/accept
http://localhost:9432/agent-client-authorisation/clients/NI/AB12456A/invitations/received/B31ZD93X6RYCF/accept
http://localhost:9432/agent-client-authorisation/clients/VRN/101747696/invitations/received/CPB6KM1NHT446/accept
```

|Response|Description|
|--------|---------|
|204|Invitation is accepted and the status is updated in Mongo|
|403|Invalid Status: Invitation status is not "Pending"|
|403|Client is not authorised to accept this invitation|
|404|Cannot find invitation to accept|


#### Client Rejects Invitation <a name="rejectInvitation"></a>
Changes the status a "Pending" Invitation to "Rejected". 
```
PUT   /clients/(service-api)/(service-api)/invitations/received/:invitationId/reject
```

Example Requests:
```
http://localhost:9432/agent-client-authorisation/clients/MTDITID/ABCDEF123456789/invitations/received/ANRJ9OCEGZR1T/reject
http://localhost:9432/agent-client-authorisation/clients/NI/AB12456A/invitations/received/B31ZD93X6RYCF/reject
http://localhost:9432/agent-client-authorisation/clients/VRN/101747696/invitations/received/CPB6KM1NHT446/reject
```

|Response|Description|
|--------|---------|
|204|Invitation is rejected and the status is updated in Mongo|
|403|Invalid Status: Invitation status is not "Pending"|
|403|Client is not authorised to accept this invitation|
|404|Cannot find invitation to reject|


#### GET Client Specific Invitation <a name="clientSpecificInvitation"></a>
Retrieve a specific invitation by its invitationId 

```
GET   /clients/(service-api)/(associated-clientIdentifier)/invitations/received/:invitationId
```

Example Requests:
```
http://localhost:9432/agent-client-authorisation/clients/MTDITID/ABCDEF123456789/invitations/received/ANRJ9OCEGZR1T
http://localhost:9432/agent-client-authorisation/clients/NI/AB12456A/invitations/received/B31ZD93X6RYCF
http://localhost:9432/agent-client-authorisation/clients/VRN/101747696/invitations/received/CPB6KM1NHT446
```

|Response|Description|
|--------|---------|
|200|Returns a specific Invitations for a given client identifier|
|403|Client is not authorised to view this invitation|
|404|Cannot find specific invitation for given client identifier|

Example Response, 200 with Body:
```json
{
   "arn" : "TARN0000001",
   "service" : "HMRC-MTD-VAT",
   "lastUpdated" : "2018-05-04T11:55:29.954Z",
   "suppliedClientId" : "101747696",
   "_links" : {
      "accept" : {
         "href" : "/agent-client-authorisation/clients/VRN/101747696/invitations/received/CPB6KM1NHT446/accept"
      },
      "self" : {
         "href" : "/agent-client-authorisation/clients/VRN/101747696/invitations/received/CPB6KM1NHT446"
      },
      "reject" : {
         "href" : "/agent-client-authorisation/clients/VRN/101747696/invitations/received/CPB6KM1NHT446/reject"
      }
   },
   "created" : "2018-05-04T11:55:29.954Z",
   "status" : "Pending",
   "expiryDate" : "2018-05-14",
   "suppliedClientIdType" : "vrn",
   "clientIdType" : "vrn",
   "clientId" : "101747696"
}
```
#### GET All Client Invitation <a name="clientAllInvitations"></a>
Retrieve all invitations by client identifier, used by agent-client-management (manage your tax agent) and STRIDE users with the correct roles

|Response|Description|
|--------|---------|
|200|Returns all Invitations for a given client identifier, returns empty if no invitations|
|403|User is not authorised to view this invitation|

```
GET   /clients/(service-api)/(associated-clientIdentifier)/invitations/received
```

Example Requests:
```
http://localhost:9432/agent-client-authorisation/clients/NI/AB12456A/invitations/received
http://localhost:9432/agent-client-authorisation/clients/MTDITID/EP849172B/invitations/received?status=Partialauth
http://localhost:9432/agent-client-authorisation/clients/VRN/101747696/invitations/received?status=Pending
```
Example Response for partialAuth query, 200 Body:
```json
{
    "_links": {
        "self": {
            "href": "/agent-client-authorisation/clients/NI/EP849172B/invitations/received?status=Partialauth"
        },
        "invitations": {
            "href": "/agent-client-authorisation/clients/MTDITID/EP849172B/invitations/received/AJKFPB2XJCZXA"
        }
    },
    "_embedded": {
        "invitations": [
            {
                "_links": {
                    "self": {
                        "href": "/agent-client-authorisation/clients/MTDITID/EP849172B/invitations/received/AJKFPB2XJCZXA"
                    }
                },
                "clientType": "personal",
                "service": "HMRC-MTD-IT",
                "clientIdType": "ni",
                "clientId": "EP849172B",
                "arn": "KARN0762398",
                "suppliedClientId": "EP849172B",
                "suppliedClientIdType": "ni",
                "created": "2021-10-19T10:49:46.048+01:00",
                "lastUpdated": "2021-10-19T16:25:24.614+01:00",
                "expiryDate": "2021-11-09",
                "status": "Partialauth",
                "invitationId": "AJKFPB2XJCZXA",
                "detailsForEmail": {
                    "agencyEmail": "z7osda@pekas.me",
                    "agencyName": "Booth Professional Services",
                    "clientName": "Elijah Thompson"
                },
                "isRelationshipEnded": false,
                "relationshipEndedBy": null,
                "clientActionUrl": null,
                "origin": "agent-invitations-frontend"
            }
        ]
    }
}
```
### Cancel a client specific invitation
Cancel a specific invitation by its invitationId

```
PUT   /agencies/:arn/invitations/sent/:invitationId/cancel 
```

Example Requests:
```
http://localhost:9432/agent-client-authorisation/agenices/TARN0000001/invitations/sent/CPB6KM1NHT446/cancel
```

|Response|Description|
|--------|---------|
|204|Invitation is successfully cancelled|
|403|This invitation cannot be cancelled because it's status is not Pending|
|403|This user has no permissions|
|404|Client is not authorised to view this invitation|

### Get client status
Returns status of an authorised client's with regard to authorisations of agents.

```
GET /status
```

|Response|Description|
|--------|---------|
|200|application/json content|
|401|Missing or expired authorisation token|
|403|This user has no permissions|

```
 { 
   "hasPendingInvitations": true|false, 
   "hasInvitationsHistory": true|false, 
   "hasExistingRelationships": true|false 
 }
```

* hasPendingInvitations - there are pending authorisations requests from Agent(s) for this client
* hasInvitationsHistory - there were other authorisations requests in the past, which can be accepted, rejected or expired
* hasExistingRelationships - there exist active client's authorisations for HMRC-MTD-IT, HMRC-MTD-VAT, PERSONAL-INCOME-RECORD or any other supported service

### Replace URN relationship with UTR
Replaces URN clientID of pending or active relationships with UTR

```
POST /invitations/:urn/replace/utr/:utr
```
|Response|Description|
|--------|---------|
|201|A new relationship has been created|
|204|Latest relationship isn't Pending or Active, so didn't create any new relationship|
|404|No relationships found|

### Running the tests <a name="runningTests"></a>

    sbt test it:test


### Running the application locally <a name="runningLocal"></a>
To run application requires the following prerequisites:
* Service Manager (See: [Installation Guidance](https://github.com/hmrc/service-manager/wiki/Install#install-service-manager))
* MongoDB 3.2

The command to use is:

    sm --start AGENT_MTD -f


Alternatively run from source alone:

    sm --start AGENT_CLIENT_AUTHORISATION


or

    sbt run


However, it is advised to run AGENT_MTD profile which comes with authentication applications because each api requires authentication for use.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

