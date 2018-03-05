
# Tradle Corda Adapter

This CorDapp is an adapter for [Tradle](https://github.com/tradle). It is used for sealing authenticated pieces of data in time, and sharing them with third parties.

*Forked from the [CorDapp Java template](https://github.com/corda/cordapp-template-java)*

## Usage

build.gradle is configured to launch 3 nodes (Party A, B, C)

the webservers for these nodes live at 10007, 10010, 10013 respectively

### REST API

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
