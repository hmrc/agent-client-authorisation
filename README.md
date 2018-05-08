Agent Client Authorisation
==========================

This application serves as the backend for [agent-invitations-frontend](https://github.com/hmrc/agent-invitations-frontend) containing the logic for creating and updating invitations.

[![Build Status](https://travis-ci.org/hmrc/agent-client-authorisation.svg?branch=master)](https://travis-ci.org/hmrc/agent-client-authorisation) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-client-authorisation/images/download.svg) ](https://bintray.com/hmrc/releases/agent-client-authorisation/_latestVersion)

### API docs
Refer to [RAML documentation](https://github.com/hmrc/agent-client-authorisation/blob/master/resources/public/api/conf/0.0/application.raml) for further details on each API.
   

### Supported Regimes / Services
This supports Agent and Client authorisation processes for the following regimes (aka services):
|regime|Service|
|--------|--------|
|Self Assessment|HMRC-MTD-IT|
|Income-Record-Viewer for Individuals|PERSONAL-INCOME-RECORD|
|Value-Added-Tax|HMRC-MTD-VAT|


### Invitation Statuses
Invitations can have one of the following status:

|Invitation Status|Description|
|--------|---------|
|Pending|Default status when an invitation has been created|
|Accepted|Allows Agent to be authorised to act on behalf of a client|
|Rejected|Prevents Agent being authorised to act on a client's behalf|
|Expired|Client did not respond to the Agent's Invitation within 10 days|
|Cancelled|Agent cancels the invitation they sent out, preventing a client from responding|

Note: Invitations with "Pending" status is the only editable status.

## Agent APIs
The following APIs require agent authentication. 

Any unauthorised access could receive one of the following responses:

|Response|Description|
|--------|---------|
|401|Unauthorised. Not logged In|
|403|The Agent is not subscribed to Agent Services.|
|403|The logged in user is not permitted to access invitations for the specified agency.|

#### Create Invitation
Validates the service, clientIdentifier, type and optionally postcode (only applicable for Self-Assessment) and creates an invitation.

```
POST  /agencies/:arn/invitations/sent
```

Request:
```
http://localhost:9432/agent-client-authorisation/agenices/TARN0000001/invitations/sent

```
Example Body of ITSA:
```json
{
  "service": "HMRC-MTD-IT",
  "clientIdType": "ni",
  "clientId": "AB123456A",
  "clientPostcode": "DHJ4EJ"
}
```

Example Body of VAT:
```json
{
  "service": "HMRC-MTD-VAT",
  "clientIdType": "vrn",
  "clientId": "101747696",
  "clientPostcode": null
}
```

Example Body of IRV:
```json
{
  "service": "PERSONAL-INCOME-RECORD",
  "clientIdType": "ni",
  "clientId": "AE123456C",
  "clientPostcode": null
}
```

|Response|Description|
|--------|---------|
|204|Successfully created invitation. (In Headers) Location → "/agencies/:arn/invitations/sent/:invitationdId"|
|400|Received Valid Json but incorrect data|
|400|Received Invalid Json|
|403|Client Registration Not Found|
|403|(HMRC-MTD-IT Only) Post Code does not match|
|501|Unsupported Service|

Note: The link returned from a successful create invitation response is "GET a Specific Agent's Sent Invitation"

#### GET All Agent's Sent Invitations
Retrieves all invitations created by the Agent. 

```
GET   /agencies/:arn/invitations/sent
```
Request 
```
http://localhost:9432/agent-client-authorisation/agenices/:arn/invitations/sent
```

|Response|Description|
|--------|---------|
|200|Returns an invitation in json|

Response 200 with Body:
```json
{
	"_links": {
		"invitations": [{
				"href": "/agent-client-authorisation/agencies/TARN0000001/invitations/sent/CG49BZ8X6B8DC"
			},
			{
				"href": "/agent-client-authorisation/agencies/TARN0000001/invitations/sent/B31ZD93X6RYCF"
			}
		],
		"self": {
			"href": "/agent-client-authorisation/agencies/TARN0000001/invitations/sent"
		}
	},
	"_embedded": {
		"invitations": [{
				"status": "Accepted",
				"suppliedClientId": "101747696",
				"service": "HMRC-MTD-VAT",
				"lastUpdated": "2018-01-31T13:55:02.820Z",
				"clientIdType": "vrn",
				"expiryDate": "2018-02-10",
				"_links": {
					"self": {
						"href": "/agent-client-authorisation/agencies/TARN0000001/invitations/sent/CG49BZ8X6B8DC"
					}
				},
				"postcode": null,
				"suppliedClientIdType": "vrn",
				"arn": "TARN0000001",
				"clientId": "101747696",
				"created": "2018-01-31T13:54:09.017Z"
			},
			{
				"status": "Accepted",
				"suppliedClientId": "AE123456C",
				"service": "PERSONAL-INCOME-RECORD",
				"_links": {
					"self": {
						"href": "/agent-client-authorisation/agencies/TARN0000001/invitations/sent/B31ZD93X6RYCF"
					}
				},
				"lastUpdated": "2018-02-02T11:33:09.567Z",
				"clientIdType": "ni",
				"expiryDate": "2018-02-12",
				"suppliedClientIdType": "ni",
				"arn": "TARN0000001",
				"postcode": null,
				"clientId": "AE123456C",
				"created": "2018-02-02T11:32:27.616Z"
			}
		]
	}
}
```

#### GET a Specific Agent's Sent Invitation
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
   "status" : "Expired",
   "postcode" : null
}
```

#### GET Known Fact for VAT
Checks a known fact for a given Vat Registration Number.

```
GET   /agencies/check-vat-known-fact/:vrn/registration-date/:vatRegistrationDate
```

Request
```
http://localhost:9432/agent-client-authorisation/agencies/check-vat-known-fact/101747641/registration-date/2010-04-01
```

|Response|Description|
|--------|---------|
|204|There is a record found for given vrn and date|
|403|There is a record for given vrn but with a different date|
|404|There is no record found for given vrn|


## Client APIs
The following APIs require client authentication. Any requests to access without authentication will be redirected to login page. 


|regime|Auth Service|Service|Service-Api|Client-Identifier-Type|
|--------|--------|---------|---------|
|Self Assessment|HMRC-MTD-IT|Same|MTDITID|ni|
|Income-Record-Viewer for Individuals|HMRC-NI|PERSONAL-INCOME-RECORD|NI|ni|
|Value-Added-Tax|HMRC-MTD-VAT|Same|VAT|vrn|

Any unauthorised access could receive one of the following responses:

|Response|Description|
|--------|---------|
|401|Unauthorised. Not logged In|
|403|The Client Identifier is not found in the user's login profile|


#### Accept Invitation
Changes the status of a "Pending" Invitation to "Accepted". As a result of accepting an invitation, a relationship record is established to allow an agent to act on their behalf. See [agent-client-relationships](https://github.com/hmrc/agent-client-relationships) for further details.
```
PUT   /clients/(service-api)/(associated-clientIdentifier)/invitations/received/:invitationId/accept
```

Example Requests
```
http://localhost:9432/agent-client-authorisation/clients/MTDITID/ABCDEF123456789/invitations/received/ANRJ9OCEGZR1T/accept
http://localhost:9432/agent-client-authorisation/clients/NI/AB12456A/invitations/received/B31ZD93X6RYCF/accept
http://localhost:9432/agent-client-authorisation/clients/VAT/101747696/invitations/received/CPB6KM1NHT446/accept
```

|Response|Description|
|--------|---------|
|204|Invitation is accepted and the status is updated in Mongo|
|403|Invalid Status: Invitation status is not "Pending"|
|403|Client is not authorised to accept this invitation|
|404|Cannot find invitation to accept|


#### Reject Invitation
Changes the status a "Pending" Invitation to "Rejected". 
```
PUT   /clients/(service-api)/(service-api)/invitations/received/:invitationId/reject
```

Example Requests:
```
http://localhost:9432/agent-client-authorisation/clients/MTDITID/ABCDEF123456789/invitations/received/ANRJ9OCEGZR1T/reject
http://localhost:9432/agent-client-authorisation/clients/NI/AB12456A/invitations/received/B31ZD93X6RYCF/reject
http://localhost:9432/agent-client-authorisation/clients/VAT/101747696/invitations/received/CPB6KM1NHT446/reject
```

|Response|Description|
|--------|---------|
|204|Invitation is rejected and the status is updated in Mongo|
|403|Invalid Status: Invitation status is not "Pending"|
|403|Client is not authorised to accept this invitation|
|404|Cannot find invitation to reject|


#### GET All Invitations
```
GET   /clients/(service-api)/(service-api)/invitations/received
```

Example Requests:
```
http://localhost:9432/agent-client-authorisation/clients/MTDITID/ABCDEF123456789/invitations/received
http://localhost:9432/agent-client-authorisation/clients/NI/AB12456A/invitations/received
http://localhost:9432/agent-client-authorisation/clients/VAT/101747696/invitations/received
```

|Response|Description|
|--------|---------|
|200|Returns All Invitations for given client identifier|
|403|Client is not authorised to view these invitations|
|404|Cannot find invitations for given client identifier|

Example Response, 200 with Body:
```json
{
	"_links": {
		"invitations": [{
				"href": "/agent-client-authorisation/clients/VAT/101747696/invitations/received/CG49BZ8X6B8DC"
			},
			{
				"href": "/agent-client-authorisation/clients/VAT/101747696/invitations/received/C7DACRCSCUO9A"
			}
		],
		"self": {
			"href": "/agent-client-authorisation/clients/VAT/101747696/invitations/received"
		}

	},
	"_embedded": {
		"invitations": [{
				"postcode": null,
				"status": "Accepted",
				"_links": {
					"self": {
						"href": "/agent-client-authorisation/clients/VAT/101747696/invitations/received/CG49BZ8X6B8DC"
					}
				},
				"created": "2018-01-31T13:54:09.017Z",
				"suppliedClientIdType": "vrn",
				"expiryDate": "2018-02-10",
				"suppliedClientId": "101747696",
				"clientId": "101747696",
				"arn": "TARN0000001",
				"clientIdType": "vrn",
				"service": "HMRC-MTD-VAT",
				"lastUpdated": "2018-01-31T13:55:02.820Z"
			},
			{
				"status": "Pending",
				"postcode": null,
				"suppliedClientIdType": "vrn",
				"created": "2018-05-04T11:55:29.954Z",
				"_links": {
					"reject": {
						"href": "/agent-client-authorisation/clients/VAT/101747696/invitations/received/CPB6KM1NHT446/reject"
					},
					"accept": {
						"href": "/agent-client-authorisation/clients/VAT/101747696/invitations/received/CPB6KM1NHT446/accept"
					},
					"self": {
						"href": "/agent-client-authorisation/clients/VAT/101747696/invitations/received/CPB6KM1NHT446"
					}
				},
				"arn": "TARN0000001",
				"clientIdType": "vrn",
				"clientId": "101747696",
				"expiryDate": "2018-05-14",
				"suppliedClientId": "101747696",
				"lastUpdated": "2018-05-04T11:55:29.954Z",
				"service": "HMRC-MTD-VAT"
			}
		]
	}
}
```


#### GET Specific Invitation
Retrieve a specific invitation by its invitationId 

```
GET   /clients/(service-api)/(associated-clientIdentifier)/invitations/received/:invitationId
```

Example Requests:
```
http://localhost:9432/agent-client-authorisation/clients/MTDITID/ABCDEF123456789/invitations/received/ANRJ9OCEGZR1T
http://localhost:9432/agent-client-authorisation/clients/NI/AB12456A/invitations/received/B31ZD93X6RYCF
http://localhost:9432/agent-client-authorisation/clients/VAT/101747696/invitations/received/CPB6KM1NHT446
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
         "href" : "/agent-client-authorisation/clients/VAT/101747696/invitations/received/CPB6KM1NHT446/accept"
      },
      "self" : {
         "href" : "/agent-client-authorisation/clients/VAT/101747696/invitations/received/CPB6KM1NHT446"
      },
      "reject" : {
         "href" : "/agent-client-authorisation/clients/VAT/101747696/invitations/received/CPB6KM1NHT446/reject"
      }
   },
   "created" : "2018-05-04T11:55:29.954Z",
   "status" : "Pending",
   "expiryDate" : "2018-05-14",
   "suppliedClientIdType" : "vrn",
   "clientIdType" : "vrn",
   "postcode" : null,
   "clientId" : "101747696"
}
```


### Running the tests

    sbt test it:test


### Running the application locally
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
