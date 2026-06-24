# 生成2FA密钥

## OpenAPI Specification

```yaml
openapi: 3.0.1
info:
  title: ''
  description: ''
  version: 1.0.0
paths:
  /api/auth/2fa/generate:
    post:
      summary: 生成2FA密钥
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
                    type: object
                    properties:
                      qr:
                        type: string
                        title: 二维码
                        description: 二维码图片的data url
                      secret:
                        type: string
                        title: 密钥
                    required:
                      - qr
                      - secret
                    title: 数据
                    x-apifox-orders:
                      - qr
                      - secret
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
                  qr: data:image/png;base64,iVBORw0KGgoAAAANSUhE
                  secret: RPQZG4MDS3
          headers: {}
          x-apifox-name: 成功
      security: []
      x-apifox-folder: auth
      x-apifox-status: released
      x-run-in-apifox: https://app.apifox.com/web/project/6849786/apis/api-327955411-run
components:
  schemas: {}
  securitySchemes: {}
servers: []
security: []

```
