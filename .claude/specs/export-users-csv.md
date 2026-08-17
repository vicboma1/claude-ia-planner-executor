## Export Active Users CSV

### Objetivo
Permitir exportar usuarios activos en formato CSV.

### Endpoint
GET /api/users/export

### Requisitos
- Streaming obligatorio
- UTF-8
- Soportar grandes volúmenes (1M usuarios)

### Casos borde
- Emails nulos
- Caracteres Unicode
