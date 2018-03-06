
# Tradle Corda Adapter

This CorDapp is an adapter for [Tradle](https://github.com/tradle). It is used for sealing authenticated pieces of data in time, and sharing them with third parties.

*Forked from the [CorDapp Java template](https://github.com/corda/cordapp-template-java)*

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**

- [Configuration](#configuration)
- [Moving Parts Overview](#moving-parts-overview)
  - [Flows](#flows)
  - [Contract](#contract)
  - [State](#state)
  - [Vault Client](#vault-client)
  - [REST API](#rest-api)
    - [GET /api/share/items](#get-apishareitems)
    - [GET /api/share/unresolved](#get-apishareunresolved)
    - [POST /api/share/item](#post-apishareitem)
    - [POST /api/share/resolveparty](#post-apishareresolveparty)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Configuration

build.gradle is configured to launch 3 nodes (Party A, B, C)

the webservers for these nodes live at 10007, 10010, 10013 respectively

## Moving Parts Overview

### Flows

- create "shared item" [initiator flow](https://github.com/tradle/tradle-cordapp/blob/master/cordapp/src/main/java/com/template/SharedItemCreateFlow.java): If the counterparty doesn't have a Corda node yet, saves a toTmpId (Tradle identity hash), to be resolved later. Otherwise creates state with two parties (see responder flow)

- create "shared item" [responder flow](https://github.com/tradle/tradle-cordapp/blob/master/cordapp/src/main/java/com/template/CreateFlowResponder.java) (if counterparty already has a corda node): checks if has data identified by "link", e.g. in some external database. If so, confirms state creation

- resolve identity [initiator flow](https://github.com/tradle/tradle-cordapp/blob/master/cordapp/src/main/java/com/template/ResolveToIdentityFlow.java): resolve toTmpId to Corda party in all shared items, i.e. updates state objects with `toTmpId == [given toTmpId]` with `to = [given Corda Party]`

### Contract

(state change validation)

[Contract code](./cordapp-contracts-states/src/main/java/com/template/SharedItemContract.java)

### State

[Code](https://github.com/tradle/tradle-cordapp/blob/master/cordapp-contracts-states/src/main/java/com/template/SharedItemState.java)

### Vault Client

(RPC calls, used by REST API module)

[Code](https://github.com/tradle/tradle-cordapp/blob/master/cordapp/src/main/java/com/template/SharedItemClient.java)

### REST API

[Code](https://github.com/tradle/tradle-cordapp/blob/master/cordapp/src/main/java/com/template/SharedItemApi.java)

#### GET /api/share/items
  @HeaderParam("Authorization")  
  @QueryParam("link")  
  @QueryParam("from")  
  @QueryParam("to")  
  @QueryParam("toTmpId")  
  @QueryParam("timestamp")

List shared items. Optionally filter by adding conditions via query parameters

examples: 

```sh
curl -H "Authorization: abc" http://localhost:10007/api/share/items
curl -H "Authorization: abc" http://localhost:10007/api/share/items?link=link1
```

#### GET /api/share/unresolved
  @HeaderParam("Authorization")  
  @QueryParam("partyTmpId")

List items with unresolved counterparties

examples: 

```sh
curl -H "Authorization: abc" http://localhost:10007/api/share/unresolved
curl -H "Authorization: abc" "http://localhost:10007/api/share/unresolved?partyTmpId=joe"
```

#### POST /api/share/item
  @HeaderParam("Authorization")  
  @FormParam("link")  
  @FormParam("partyName")  
  @FormParam("partyTmpId")  

Create a record for an item with an unresolved counterparty

For ease of testing, API keys currently reside in a text file in resources/certificates/apikeys.txt (and is "abc" per the examples)

examples: 

```sh
curl -X POST -H 'Authorization: abc' --data "partyName=O%3DPartyA%2CL%3DLondon%2CC%3DGB&partyTmpId=b57ed7f459ea6d0438de3841802110dfd1ce881d78909c9f0f69e19614cf574f" http://localhost:10007/api/share/item
```

#### POST /api/share/resolveparty
  @HeaderParam("Authorization")  
  @FormParam("partyTmpId")  
  @FormParam("partyName")  

Resolve a partyTmpId to a partyName

examples: 

```sh
curl -X POST -H "Authorization: abc" --data "partyName=O%3DPartyB%2CL%3DNew%20York%2CC%3DUS&partyTmpId=b57ed7f459ea6d0438de3841802110dfd1ce881d78909c9f0f69e19614cf574f" http://localhost:10007/api/share/resolveparty
```
