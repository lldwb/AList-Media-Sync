# Role

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
    Role:
      type: object
      properties:
        id:
          type: integer
          examples:
            - 1
        name:
          type: string
          examples:
            - admin
        description:
          type: string
          examples:
            - Administrator role
        permission_scopes:
          type: array
          items:
            $ref: '#/components/schemas/PermissionEntry'
        raw_permission:
          type: string
          examples:
            - '[{"path":"/admin","permission":7}]'
      required:
        - name
      x-apifox-orders:
        - id
        - name
        - description
        - permission_scopes
        - raw_permission
      x-apifox-folder: ''
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
