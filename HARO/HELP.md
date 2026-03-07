# Getting Started

## Local Secrets Setup

This project now reads sensitive values from environment variables (and supports a local `.env` file through `spring.config.import`).

1. Create a local file from the template:
   - Windows PowerShell: `Copy-Item .env.example .env`
2. Replace placeholder values in `.env`.
3. Run the app normally (`mvn spring-boot:run`).

Important:
- `.env` is ignored by git.
- Only `.env.example` should be committed.

## Run Locally With Docker Compose (No VPN DB)

This setup runs:
- `postgres` container (local DB)
- `api` container (this Spring Boot app)

The API is forced to use the local Postgres service via:
- `DB_URL=jdbc:postgresql://postgres:5432/<db>`
- `DB_USERNAME` and `DB_PASSWORD` from local compose variables

Optional local variables in `.env`:
- `LOCAL_PG_DB` (default: `haro`)
- `LOCAL_PG_USER` (default: `haro`)
- `LOCAL_PG_PASSWORD` (default: `haro123`)
- `LOCAL_PG_PORT` (default: `5432`)
- `API_PORT` (default: `8080`)

Commands:

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f api
```

Stop and remove:

```bash
docker compose down
```

Stop and remove including DB volume:

```bash
docker compose down -v
```

## Google Calendar (Meet)

This project exposes `POST /api/calendar/reuniones` to create a Calendar event with Google Meet link.

Required `.env` variables:
- `GOOGLE_CALENDAR_CREDENTIALS_PATH`
- `GOOGLE_CALENDAR_ID` (default `primary`)
- `GOOGLE_CALENDAR_APP_NAME`
- `GOOGLE_CALENDAR_TIMEZONE`

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.5/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.5/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/3.5.5/reference/web/servlet.html)
* [Spring Security](https://docs.spring.io/spring-boot/3.5.5/reference/web/spring-security.html)
* [OAuth2 Authorization Server](https://docs.spring.io/spring-boot/3.5.5/reference/web/spring-security.html#web.security.oauth2.authorization-server)

### Guides
The following guides illustrate how to use some features concretely:

* [Accessing data with MySQL](https://spring.io/guides/gs/accessing-data-mysql/)
* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Securing a Web Application](https://spring.io/guides/gs/securing-web/)
* [Spring Boot and OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
* [Authenticating a User with LDAP](https://spring.io/guides/gs/authenticating-ldap/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

