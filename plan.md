# SCIM URI fix plan

- Completed: SCIM URL generation now derives the org-aware base from the actual request and wraps it in `ScimUriInfo` before it reaches `ResourcePreparer`.
- Current task: align the remaining SCIM tests with the real route format: `http://localhost/realms/{realm}/scim/v2/{orgId}/...`.
- Next step: run the focused SCIM test subset and fix any remaining contract mismatches.
