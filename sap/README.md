# SAP Integration Flow

Disclaimer: the integration flow has been tested ad-hoc against SAP's Sandbox
API as we don't have a SAP license. If you notice any bugs or suggestions for
improvements, feel free to report them here.

This project is a proof of concept and a guidance for customers wanting to
interface SAP to Factbird.

Common use cases include:

- Import process orders into Factbird

- Import products into Factbird (TBA)

- Manage process order execution from within Factbird (executing and terminating of a process order) (TBA)

## Getting Started

Download a released integration flow from
https://github.com/FactbirdHQ/integrations/releases and upload that to SAP
Integration Suite as a new API.

![Adding a new API integration in SAP Integration Suite.](assets/add-new-api-integration.png)

The integration process consists of a number of subprocesses:
1. Setup (a test setup which should be substituted for an actual event source,
   i.e. [ProcessOrder.Changed](https://api.sap.com/event/OP_PROCESSORDEREVENTS/resource))
2. Factbird request dispatching
3. Process order fetching

The integration example targets SAP's sandbox API, this needs to be adjusted in
order to target an S4/HANA instance or some other endpoint that provides the
Process Order OData v2 API in (3).

Factbird-related configurations are to be done in the Factbird Request Dispatch
(2) process, the endpoint needs to be adjusted to the Factbird instance you are
using, e.g. api.cloud.factbird.com with a long-lived API key issued by support
that is privileged on behalf of a specific user.


## References

- https://api.sap.com/api/API_PROCESS_ORDER_2_SRV/overview

- https://api.sap.com/event/OP_PROCESSORDEREVENTS/resource
