# Database selection

[Back: Architecture Decision Records](README.md)

## Status

Accepted

## Context

Keycloak requires a relational database. The server has built-in support for different databases. Keycloak provides a table of supported databases and their tested versions.

Our requirements:

- Supported by Keycloak.
- Aligns well with our existing infrastructure.
- Available as both managed and self-hosted solutions.
- Supports backup and disaster recovery.

## Alternatives

| Option                            | Pros                                                                                                        | Cons                                                                                                                                         |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| **MariaDB 11.4**                  | Open source, broad adoption, similar to MySQL. Lower cost to operate.                                       | Less momentum in the Keycloak community. Fewer enterprise-grade high availability and scaling features compared to PostgreSQL.               |
| **Microsoft SQL Server 2022**     | Mature enterprise database. Rich tooling in the Microsoft ecosystem.                                        | Licensing cost, proprietary technology, and limited community support for Keycloak.                                                          |
| **MySQL 8.4**                     | Popular and widely available. Open source.                                                                  | Keycloak's documentation and community favor PostgreSQL. Transactional and feature limitations compared to PostgreSQL (indexes, JSON, CTEs). |
| **Oracle Database 23.5**          | Enterprise-grade with advanced features and strong performance.                                             | Very high licensing cost with strong vendor lock-in. Less community guidance for Keycloak.                                                   |
| **PostgreSQL 17**                 | Advanced SQL and JSONB support. Open source and cloud-agnostic. Fits well with our existing infrastructure. | Managed versions may restrict extensions. Requires connection pooling for high concurrency.                                                  |
| **Amazon Aurora PostgreSQL 16.8** | Cloud-managed high availability and scaling. PostgreSQL-compatible. Automated backups and monitoring.       | AWS lock-in does not align well with our cloud-agnostic approach. Some compatibility edge cases with certain PostgreSQL extensions.          |

## Decision

We have chosen PostgreSQL as the database type for Keycloak. This decision is based on:

- PostgreSQL is the most widely recommended and used database for Keycloak.
- It can be self-hosted, hosted on Azure Database for PostgreSQL, or hosted through other managed services as needed.
- It has the largest body of knowledge, documentation, and Keycloak production deployments.

## Consequences

### Positive

- Stable, widely tested foundation for Keycloak.
- Balance of flexibility and cost with a strong open-source ecosystem.
- Cloud and provider portability compared to vendor-specific options.
- Fits well with our existing infrastructure.

### Trade-offs

- Managed PostgreSQL may restrict certain extensions.
- **Might** require tuning and connection pooling to handle large concurrent login volumes.
