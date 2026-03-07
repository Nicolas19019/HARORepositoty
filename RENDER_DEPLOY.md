# Deploy de HARO en Render (Docker)

## 1) Probar local con Docker

```bash
docker build -t haro-api .
docker run --rm -p 8080:8080 --env-file .env haro-api
```

La API quedara en `http://localhost:8080`.

## 2) Subir cambios al repositorio

Asegura que estos archivos esten versionados:

- `Dockerfile`
- `.dockerignore`
- `render.yaml`

## 3) Crear servicio en Render

Opcion A (recomendada): Blueprint

1. En Render, `New +` -> `Blueprint`.
2. Conecta tu repositorio.
3. Render leera `render.yaml` y creara el servicio `haro-api`.

Opcion B: Web Service manual

1. `New +` -> `Web Service`.
2. Selecciona repo.
3. Environment: `Docker`.
4. Dockerfile path: `./Dockerfile`.

## 4) Variables de entorno en Render

Configura en el servicio (NO subir secretos al repo):

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`
- `JWT_SECRET`
- `AUTH_PEPPER`
- `EPAYCO_P_CUST_ID_CLIENTE`
- `EPAYCO_P_KEY`
- `AWS_REGION`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `S3_BUCKET`
- Cualquier otra variable que hoy tengas en `.env`.

Render define `PORT` automaticamente y la app ya lo toma con `server.port=${PORT:8083}`.

## 5) Verificar deploy

1. Revisa logs de build y start.
2. Prueba un endpoint, por ejemplo:
   - `/swagger-ui/index.html`
   - o uno publico que uses en produccion.
