# FlameNews architecture

#### XMPP server

TCP connections are modeled as `XMPPPhase`s: `ConnectedPhase`, `HandshakePhase`...

`ConnectedPhase` connects XMPP business logic with transport layer.

Each user is represented as `UserAgent`. There is at most one `UserAgent` actor alive in the system per user.

There is exactly one `UserAgent.Courier` per connected resource (JID resource, device). 
Courier's key role is to reliably deliver messages to connection.

`XMPP` is a key dispatcher. All not yet dispatched messages should be sent through `XMPP` actor. 
If destination actor is already known one could sent it directly, e.g., `Delivered` messages from connection are sent directly. 
It routes messages to services such as Roster, messages to users, etc.

#### Caveats

If CPU utilization suddenly becomes 100%, almost surely there is some insidious message that is running in cycles. 
Take thread dump several times to find hot method, then put breakpoint there. 
Trace this message with breakpoints, this should help.

In current architecture you should always label outgoing stanzas with `to` attribute to differentiate between incoming and outgoing stanzas.

#### How to run

1. In `application.yml` change domain to proper domain (`$ hostname` will help to know your local domain)
2. Change `put_any_hostname_here.p12` filename to proper domain name
3. Run `XMPPServerApplication`, it should be fine

