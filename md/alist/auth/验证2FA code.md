# 验证2FA code

## OpenAPI Specification

```yaml
openapi: 3.0.1
info:
  title: ''
  description: ''
  version: 1.0.0
paths:
  /api/auth/2fa/verify:
    post:
      summary: 验证2FA code
      deprecated: false
      description: ''
      tags:
        - auth
        - auth
        - alist Copy/auth
      parameters:
        - name: Authorization
          in: header
          description: ''
          required: true
          example: '{{alist_token}}'
          schema:
            type: string
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                code:
                  type: string
                  title: 2FA验证码
                secret:
                  type: string
                  title: 2FA密钥
              required:
                - code
                - secret
              x-apifox-orders:
                - code
                - secret
      responses:
        '200':
          description: ''
          content:
            application/json:
              schema:
                type: object
                properties:
                  code:
                    type: integer
                    title: 状态码
                  message:
                    type: string
                    title: 信息
                  data:
                    type: 'null'
                required:
                  - code
                  - message
                  - data
                x-apifox-orders:
                  - code
                  - message
                  - data
              example:
                code: 200
                message: success
                data: null
          headers: {}
          x-apifox-name: 成功
      security: []
      x-apifox-folder: auth
      x-apifox-status: released
      x-run-in-apifox: https://app.apifox.com/web/project/6849786/apis/api-327955412-run
components:
  schemas: {}
  securitySchemes: {}
servers: []
security: []

```
