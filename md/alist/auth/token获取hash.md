# token获取hash

## OpenAPI Specification

```yaml
openapi: 3.0.1
info:
  title: ''
  description: ''
  version: 1.0.0
paths:
  /api/auth/login/hash:
    post:
      summary: token获取hash
      deprecated: false
      description: >-
        获取某个用户的临时JWt
        token，传入的密码需要在添加-https://github.com/alist-org/alist后缀后再进行sha256
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
                  description: hash后密码，获取方式为`sha256(密码-https://github.com/alist-org/alist)`
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
      x-run-in-apifox: https://app.apifox.com/web/project/6849786/apis/api-327955410-run
components:
  schemas: {}
  securitySchemes: {}
servers: []
security: []

```
