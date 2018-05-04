Agent Client Authorisation
==========================

This application serves as the backend for [agent-invitations-frontend](https://github.com/hmrc/agent-invitations-frontend) containing the logic for creating and updating invitations.

[![Build Status](https://travis-ci.org/hmrc/agent-client-authorisation.svg?branch=master)](https://travis-ci.org/hmrc/agent-client-authorisation) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-client-authorisation/images/download.svg) ](https://bintray.com/hmrc/releases/agent-client-authorisation/_latestVersion)

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

### API docs
Refer to [RAML documentation](https://github.com/hmrc/agent-client-authorisation/blob/master/resources/public/api/conf/0.0/application.raml) for further details on each API.

### Running the tests

    sbt test it:test

## Agent APIs

#### GET Details for Agent
```
GET   /agencies
```

#### GET Details for Specific Agent
```
GET   /agencies/:arn
```

Alternatively

```
GET   /agencies/:arn/invitations
```

#### Create Invitation
```
POST  /agencies/:arn/invitations/sent
```

#### GET All Agent's Sent Invitations
```
GET   /agencies/:arn/invitations/sent
```

#### GET a Specific Agent's Sent Invitation
```
GET   /agencies/:arn/invitations/sent:invitationId
```

#### Cancel a Specific Agent's Sent Invitation
```
PUT   /agencies/:arn/invitations/sent:invitationId/cancel
```

## Client APIs

### ITSA API

#### GET Details for Client

```
GET   /clients/MTDITID/:mtdItId
```

Alternatively

```
GET   /clients/MTDITID/:mtdItId/invitations
```

#### Accept Invitation
```
Put   /clients/MTDITID/:mtdItId/invitations/received/:invitationId/accept
```

#### Reject Invitation
```
Put   /clients/MTDITID/:mtdItId/invitations/received/:invitationId/reject
```

#### GET All Invitations
```
GET   /clients/MTDITID/:mtdItId/invitations/received
```

#### GET Specific Invitation
```
GET   /clients/MTDITID/:mtdItId/invitations/received/:invitationId
```

### IRV API 


#### GET Details for Client

```
GET   /clients/NI/:nino
```

Alternatively

```
GET   /clients/NI/:nino/invitations
```

#### Accept Invitation
```
Put   /clients/NI/:nino/invitations/received/:invitationId/accept
```

#### Reject Invitation
```
Put   /clients/NI/:nino/invitations/received/:invitationId/reject
```

#### GET All Invitations
```
GET   /clients/NI/:nino/invitations/received
```

#### GET Specific Invitation
```
GET   /clients/NI/:nino/invitations/received/:invitationId
```

### VAT API

#### GET Details for Client

```
GET   /clients/VAT/:vrn
```

Alternatively

```
GET   /clients/VAT/:vrn/invitations
```

#### Accept Invitation
```
Put   /clients/VAT/:vrn/invitations/received/:invitationId/accept
```

#### Reject Invitation
```
Put   /clients/VAT/:vrn/invitations/received/:invitationId/reject
```

#### GET All Invitations
```
GET   /clients/VAT/:vrn/invitations/received
```

#### GET Specific Invitation
```
GET   /clients/VAT/:vrn/invitations/received/:invitationId
```
