# PostgreSQL hosting

[Back: Architecture Decision Records](README.md)

## Status

Accepted

## Context

Keycloak relies on a highly available and reliable relational database to store realms, clients, users, sessions, and configuration data. PostgreSQL is the recommended database for running Keycloak in production.

Key points when selecting a hosting option for PostgreSQL:

- High availability to support Keycloak.
- Automated backups and recovery to protect configuration and data.
- Scalability to handle spikes in traffic.
- Security and compliance aligned with organizational and regulatory needs.
- Operational simplicity to minimize DBA overhead and reduce the risk of misconfiguration.
- Integration with Azure, our primary cloud provider.

## Alternatives

| Option            | Pros                                                                                                                                                   | Cons                                                                                                                                                                                                              |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Self-hosted**   | Full control over configuration, versioning, and performance tuning. Potentially lower infrastructure cost.                                            | High operational burden (failover, patching, scaling). Requires in-house DBA expertise.                                                                                                                           |
| **Azure Managed** | Native Azure integration (IAM, VNet, monitoring). Automatic patching, backups, and high availability. Enterprise-grade SLA. Strong compliance posture. | Limited PostgreSQL extensions compared to self-hosted. Azure lock-in. Higher cost compared to self-hosting.                                                                                                       |
| **Aiven Managed** | We already use Aiven today. Strong SLA and migration flexibility. Backups and disaster recovery are handled.                                           | External vendor dependency. Additional cost layer. Less seamless integration with Azure networking and IAM compared to Azure Managed. Experience has shown that features are slow and customizability is limited. |

## Decision

We have chosen Azure Managed PostgreSQL as the hosting solution for Keycloak. This decision is based on:

- Seamless integration with our Azure environment.
- Support for infrastructure as code (IaC).
- High availability and automated backup and restore features critical for identity infrastructure.
- Reduced operational complexity compared to self-hosting, allowing the team to focus on Keycloak itself rather than database administration.
- Strong SLA and compliance alignment.

## Consequences

### Positive

- High reliability and availability for Keycloak, reducing the risk of outages.
- Simplified operations (automatic backups, patching, scaling).
- Strong security posture via Azure-native identity, networking, and compliance.
- Alignment with the existing Azure-first strategy, consolidating vendors.

### Trade-offs

- Limited flexibility with certain PostgreSQL extensions.
- Higher ongoing cost compared to self-hosted alternatives.
- Azure lock-in, reducing portability to non-Azure environments.
