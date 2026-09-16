# Parking Operations Context

This context defines the business language for the multi-garage parking
application and keeps garage ownership and access rules consistent across the
domain, services, and user interface.

## Garages and access

**Garage**:
A physical parking facility with its own configuration, parking capacity,
operational lifecycle, tickets, payments, reservations, and reports.
_Avoid_: Site, branch, location

**Garage access**:
An assignment that allows a user to operate within a specific garage.
Access may be active or revoked, and one user may have access to multiple
garages.
_Avoid_: Garage membership, location permission

**Operational access**:
Permission to perform a parking operation in an active garage. Global
administrators have operational access to every active garage; other users
require active garage access.
_Avoid_: Login access, general permission

**Archived garage**:
A garage retained for historical records but closed to new operational work.
Its tickets, payments, reservations, and reports remain available to authorized
administrators.
_Avoid_: Deleted garage, inactive site

**Active garage**:
The one physical garage currently selected for a user's operational actions.
It is a user-interface context and never replaces service-level authorization.
_Avoid_: Current site, default location

## Parking records

**Ticket**:
The durable record of one vehicle parking session, owned by exactly one
garage from entry through closeout.
_Avoid_: Visit, booking

**Active parking session**:
A ticket state indicating that a vehicle is still considered inside the
system, regardless of which garage it occupies.
_Avoid_: Open visit, active reservation

