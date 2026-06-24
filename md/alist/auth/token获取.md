# token获取

## OpenAPI Specification

```yaml
openapi: 3.0.1
info:
  title: ''
  description: ''
  version: 1.0.0
paths:
  /api/auth/login:
    post:
      summary: token获取
      deprecated: false
      description: 获取某个用户的临时JWt token，默认48小时过期
      tags:
        - auth
        - auth
        - alist Copy/auth
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                username:
                  type: string
                  title: 用户名
                  description: 用户名
                password:
                  type: string
                  title: 密码
                  description: 密码
                otp_code:
                  type: string
                  title: 二步验证码
                  description: 二步验证码
              required:
                - username
                - password
              x-apifox-orders:
                - username
                - password
                - otp_code
            example:
              username: '{{alist_username}}'
              password: '{{alist_password}}'
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
                    description: 状态码
                  message:
                    type: string
                    description: 信息
                  data:
                    type: object
                    properties:
                      token:
                        type: string
                        description: token
                    required:
                      - token
                    description: data
                    x-apifox-orders:
                      - token
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
                data:
                  token: abcd
          headers: {}
          x-apifox-name: 成功
      security: []
      x-apifox-folder: auth
      x-apifox-status: released
      x-run-in-apifox: https://app.apifox.com/web/project/6849786/apis/api-327955409-run
components:
  schemas: {}
  securitySchemes: {}
servers: []
security: []

```
