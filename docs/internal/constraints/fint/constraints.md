# FINT System Constraints

[Back: FINT Constraints](README.md) · [Documentation overview](../../../README.md)

This document defines rules that apply to the FINT realm.

## User

-   A user is a member of exactly one organization
-   A user may link multiple IDPs, but only within their organization

## Role

-   Roles are stored as user attributes
-   Native Keycloak roles are not used in applications

## Client access

-   Clients can filter access to organizations
-   Organizations without access will not show up on login
