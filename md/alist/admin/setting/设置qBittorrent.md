# 设置qBittorrent

## OpenAPI Specification

```yaml
openapi: 3.0.1
info:
  title: ''
  description: ''
  version: 1.0.0
paths:
  /api/admin/setting/set_qbit:
    post:
      summary: 设置qBittorrent
      deprecated: false
      description: ''
      tags:
        - admin/setting
        - admin/setting
        - alist Copy/admin/setting
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
                url:
                  type: string
                  title: qBittorrent链接
                seedtime:
                  type: string
                  title: 做种时间
              required:
                - url
                - seedtime
              x-apifox-orders:
                - url
                - seedtime
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
                    type: string
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
                data: 1.36.0
          headers: {}
          x-apifox-name: 成功
      security: []
      x-apifox-folder: admin/setting
      x-apifox-status: released
      x-run-in-apifox: https://app.apifox.com/web/project/6849786/apis/api-327955461-run
components:
  schemas: {}
  securitySchemes: {}
servers: []
security: []

```
