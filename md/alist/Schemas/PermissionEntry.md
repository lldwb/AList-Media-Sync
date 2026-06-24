# PermissionEntry

## OpenAPI Specification

```yaml
openapi: 3.0.1
info:
  title: ''
  description: ''
  version: 1.0.0
paths: {}
components:
  schemas:
    PermissionEntry:
      type: object
      properties:
        path:
          type: string
          examples:
            - /admin
        permission:
          type: integer
          examples:
            - 7
      x-apifox-orders:
        - path
        - permission
      x-apifox-folder: ''
  securitySchemes: {}
servers: []
security: []

```
