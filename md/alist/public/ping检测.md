# ping检测

## OpenAPI Specification

```yaml
openapi: 3.0.1
info:
  title: ''
  description: ''
  version: 1.0.0
paths:
  /ping:
    get:
      summary: ping检测
      deprecated: false
      description: 连通性ping检测
      tags:
        - public
        - public
        - alist Copy/public
      parameters: []
      responses:
        '200':
          description: ''
          content:
            application/json:
              schema:
                type: object
                properties: {}
                x-apifox-orders: []
              example: pong
          headers: {}
          x-apifox-name: 成功
      security: []
      x-apifox-folder: public
      x-apifox-status: released
      x-run-in-apifox: https://app.apifox.com/web/project/6849786/apis/api-327955431-run
components:
  schemas: {}
  securitySchemes: {}
servers: []
security: []

```
