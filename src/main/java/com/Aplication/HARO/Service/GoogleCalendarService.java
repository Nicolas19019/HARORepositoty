package com.Aplication.HARO.Service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.EntryPoint;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GoogleCalendarService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String AUTH_MODE_OAUTH_USER = "oauth_user";
    private static final String AUTH_MODE_SERVICE_ACCOUNT = "service_account";
    private static final String AUTH_MODE_AUTO = "auto";

    private final String authMode;
    private final String oauthClientSecretsJson;
    private final String oauthClientSecretsPath;
    private final String oauthTokensDir;
    private final String oauthUserId;
    private final int oauthLocalReceiverPort;
    private final String serviceAccountJson;
    private final String serviceAccountPath;
    private final String serviceAccountUser;
    private final String defaultCalendarId;
    private final String applicationName;
    private final String defaultTimezone;

    public GoogleCalendarService(
            @Value("${google.calendar.auth.mode:auto}") String authMode,
            @Value("${google.calendar.oauth.client-secrets.json:}") String oauthClientSecretsJson,
            @Value("${google.calendar.oauth.client-secrets.path:Secrets/google-oauth-client.json}") String oauthClientSecretsPath,
            @Value("${google.calendar.oauth.tokens.dir:.tokens/google-calendar}") String oauthTokensDir,
            @Value("${google.calendar.oauth.user-id:default}") String oauthUserId,
            @Value("${google.calendar.oauth.local-receiver-port:8888}") int oauthLocalReceiverPort,
            @Value("${google.calendar.service-account.json:}") String serviceAccountJson,
            @Value("${google.calendar.service-account.path:Secrets/google-service-account.json}") String serviceAccountPath,
            @Value("${google.calendar.service-account.user:}") String serviceAccountUser,
            @Value("${google.calendar.default-id:primary}") String defaultCalendarId,
            @Value("${google.calendar.application-name:HARO}") String applicationName,
            @Value("${google.calendar.default-timezone:America/Bogota}") String defaultTimezone
    ) {
        this.authMode = authMode;
        this.oauthClientSecretsJson = oauthClientSecretsJson;
        this.oauthClientSecretsPath = oauthClientSecretsPath;
        this.oauthTokensDir = oauthTokensDir;
        this.oauthUserId = oauthUserId;
        this.oauthLocalReceiverPort = oauthLocalReceiverPort;
        this.serviceAccountJson = serviceAccountJson;
        this.serviceAccountPath = serviceAccountPath;
        this.serviceAccountUser = serviceAccountUser;
        this.defaultCalendarId = defaultCalendarId;
        this.applicationName = applicationName;
        this.defaultTimezone = defaultTimezone;
    }

    public record ReunionCreada(
            String eventId,
            String calendarId,
            String estado,
            String htmlLink,
            String meetLink,
            Instant inicio,
            Instant fin
    ) {}

    public ReunionCreada crearReunion(
            String titulo,
            String descripcion,
            String inicioIso8601,
            String finIso8601,
            String zonaHoraria,
            List<String> asistentes,
            String calendarId
    ) {
        return crearReunion(
                titulo,
                descripcion,
                inicioIso8601,
                finIso8601,
                zonaHoraria,
                asistentes,
                calendarId,
                true
        );
    }

    public ReunionCreada crearReunion(
            String titulo,
            String descripcion,
            String inicioIso8601,
            String finIso8601,
            String zonaHoraria,
            List<String> asistentes,
            String calendarId,
            boolean crearMeet
    ) {
        OffsetDateTime inicio = parseFecha("inicio", inicioIso8601);
        OffsetDateTime fin = parseFecha("fin", finIso8601);
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("'fin' debe ser posterior a 'inicio'.");
        }

        String tz = StringUtils.hasText(zonaHoraria) ? zonaHoraria.trim() : defaultTimezone;
        String calId = StringUtils.hasText(calendarId) ? calendarId.trim() : defaultCalendarId;
        String summary = StringUtils.hasText(titulo) ? titulo.trim() : "Reunion HARO";
        String desc = StringUtils.hasText(descripcion) ? descripcion.trim() : "";

        Event event = new Event();
        event.setSummary(summary);
        event.setDescription(desc);
        event.setStart(new EventDateTime()
                .setDateTime(new DateTime(Date.from(inicio.toInstant())))
                .setTimeZone(tz));
        event.setEnd(new EventDateTime()
                .setDateTime(new DateTime(Date.from(fin.toInstant())))
                .setTimeZone(tz));

        List<EventAttendee> attendees = toAttendees(asistentes);
        if (!attendees.isEmpty()) {
            event.setAttendees(attendees);
        }

        if (crearMeet) {
            ConferenceData conf = new ConferenceData().setCreateRequest(
                    new CreateConferenceRequest()
                            .setRequestId(UUID.randomUUID().toString())
                            .setConferenceSolutionKey(new ConferenceSolutionKey().setType("hangoutsMeet"))
            );
            event.setConferenceData(conf);
        }

        try {
            Calendar client = buildCalendarClient();
            Calendar.Events.Insert insertRequest = client.events()
                    .insert(calId, event)
                    .setSendUpdates("all");
            if (crearMeet) {
                insertRequest.setConferenceDataVersion(1);
            }
            Event created = insertRequest.execute();

            String meetLink = crearMeet ? extractMeetLink(created) : null;
            return new ReunionCreada(
                    created.getId(),
                    calId,
                    created.getStatus(),
                    created.getHtmlLink(),
                    meetLink,
                    inicio.toInstant(),
                    fin.toInstant()
            );
        } catch (GoogleJsonResponseException e) {
            throw new IllegalStateException("No se pudo crear la reunion en Google Calendar: " + buildGoogleErrorMessage(e), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear la reunion en Google Calendar: " + e.getMessage(), e);
        }
    }

    private Calendar buildCalendarClient() {
        try {
            NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            String mode = resolveAuthMode();
            if (AUTH_MODE_SERVICE_ACCOUNT.equals(mode)) {
                return buildServiceAccountClient(transport);
            }
            return buildOAuthUserClient(transport);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo inicializar Google Calendar Client: " + e.getMessage(),
                    e
            );
        }
    }

    private Calendar buildServiceAccountClient(NetHttpTransport transport) {
        try (InputStream in = openServiceAccountInputStream()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(List.of(CalendarScopes.CALENDAR));

            if (!(credentials instanceof ServiceAccountCredentials serviceAccountCredentials)) {
                throw new IllegalStateException(
                        "El archivo configurado para Service Account no es valido."
                );
            }

            String delegatedUser = trim(serviceAccountUser);
            if (!delegatedUser.isBlank()) {
                credentials = serviceAccountCredentials.createDelegated(delegatedUser);
            }

            return new Calendar.Builder(transport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                    .setApplicationName(applicationName)
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo inicializar Google Calendar con Service Account. " +
                            "Verifica GOOGLE_CALENDAR_SERVICE_ACCOUNT_PATH/JSON y el acceso al calendario.",
                    e
            );
        }
    }

    private Calendar buildOAuthUserClient(NetHttpTransport transport) {
        String tokensDir = StringUtils.hasText(oauthTokensDir)
                ? oauthTokensDir.trim()
                : ".tokens/google-calendar";
        File tokensDirectory = new File(tokensDir);
        try (Reader reader = openOauthClientSecretsReader()) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
            GoogleClientSecrets.Details details = clientSecrets.getInstalled() != null
                    ? clientSecrets.getInstalled()
                    : clientSecrets.getWeb();
            if (details == null || !StringUtils.hasText(details.getClientId()) || !StringUtils.hasText(details.getClientSecret())) {
                throw new IllegalStateException(
                        "El archivo OAuth no es valido. Debe ser un JSON de cliente OAuth (tipo installed/web), no service account."
                );
            }
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    transport,
                    JSON_FACTORY,
                    clientSecrets,
                    List.of(CalendarScopes.CALENDAR)
            )
                    .setDataStoreFactory(new FileDataStoreFactory(tokensDirectory))
                    .setAccessType("offline")
                    .build();

            String userId = StringUtils.hasText(oauthUserId) ? oauthUserId.trim() : "default";
            LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                    .setPort(oauthLocalReceiverPort)
                    .build();
            Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(userId);

            return new Calendar.Builder(transport, JSON_FACTORY, credential)
                    .setApplicationName(applicationName)
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo completar OAuth de Google Calendar. " +
                            "Verifica client secrets y que el redirect local este habilitado.",
                    e
            );
        }
    }

    private String resolveAuthMode() {
        String mode = trim(authMode).toLowerCase();
        if (mode.isBlank()) {
            return AUTH_MODE_OAUTH_USER;
        }
        if (AUTH_MODE_AUTO.equals(mode)) {
            return hasServiceAccountConfigured() ? AUTH_MODE_SERVICE_ACCOUNT : AUTH_MODE_OAUTH_USER;
        }
        if (AUTH_MODE_OAUTH_USER.equals(mode) || AUTH_MODE_SERVICE_ACCOUNT.equals(mode)) {
            return mode;
        }
        throw new IllegalStateException(
                "google.calendar.auth.mode invalido. Usa: oauth_user, service_account o auto."
        );
    }

    private boolean hasServiceAccountConfigured() {
        if (StringUtils.hasText(serviceAccountJson)) {
            return true;
        }
        if (!StringUtils.hasText(serviceAccountPath)) {
            return false;
        }
        return new File(serviceAccountPath.trim()).exists();
    }

    private InputStream openServiceAccountInputStream() throws Exception {
        if (StringUtils.hasText(serviceAccountJson)) {
            String json = decodeJsonValue(serviceAccountJson, "GOOGLE_CALENDAR_SERVICE_ACCOUNT_JSON");
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        if (!StringUtils.hasText(serviceAccountPath)) {
            throw new IllegalStateException(
                    "Falta GOOGLE_CALENDAR_SERVICE_ACCOUNT_PATH o GOOGLE_CALENDAR_SERVICE_ACCOUNT_JSON."
            );
        }
        return new FileInputStream(serviceAccountPath.trim());
    }

    private Reader openOauthClientSecretsReader() throws Exception {
        if (StringUtils.hasText(oauthClientSecretsJson)) {
            String json = decodeJsonValue(oauthClientSecretsJson, "GOOGLE_CALENDAR_OAUTH_CLIENT_SECRETS_JSON");
            return new StringReader(json);
        }

        if (!StringUtils.hasText(oauthClientSecretsPath)) {
            throw new IllegalStateException(
                    "Falta GOOGLE_CALENDAR_OAUTH_CLIENT_SECRETS_PATH o GOOGLE_CALENDAR_OAUTH_CLIENT_SECRETS_JSON."
            );
        }

        InputStream in = new FileInputStream(oauthClientSecretsPath.trim());
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    private String decodeJsonValue(String rawValue, String envName) {
        String raw = trim(rawValue);
        String json = raw;
        if (!raw.startsWith("{")) {
            try {
                String decoded = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
                if (decoded.trim().startsWith("{")) {
                    json = decoded;
                }
            } catch (IllegalArgumentException ignored) {
                // Not base64, keep original value and validate below.
            }
        }
        if (!json.trim().startsWith("{")) {
            throw new IllegalStateException(envName + " no tiene formato JSON valido.");
        }
        return json;
    }

    private String buildGoogleErrorMessage(GoogleJsonResponseException e) {
        String detailMessage = e.getDetails() != null ? e.getDetails().getMessage() : null;
        if (StringUtils.hasText(detailMessage)) {
            return e.getStatusCode() + " " + detailMessage;
        }
        if (StringUtils.hasText(e.getStatusMessage())) {
            return e.getStatusCode() + " " + e.getStatusMessage();
        }
        return e.getMessage();
    }

    private OffsetDateTime parseFecha(String campo, String valor) {
        if (!StringUtils.hasText(valor)) {
            throw new IllegalArgumentException("El campo '" + campo + "' es obligatorio (ISO-8601).");
        }
        try {
            return OffsetDateTime.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Formato invalido en '" + campo + "'. Usa ISO-8601, ejemplo: 2026-02-20T10:00:00-05:00");
        }
    }

    private List<EventAttendee> toAttendees(List<String> asistentes) {
        List<EventAttendee> out = new ArrayList<>();
        if (asistentes == null) return out;

        asistentes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(mail -> out.add(new EventAttendee().setEmail(mail)));
        return out;
    }

    private String extractMeetLink(Event event) {
        if (event == null) return null;
        if (StringUtils.hasText(event.getHangoutLink())) {
            return event.getHangoutLink();
        }
        if (event.getConferenceData() == null || event.getConferenceData().getEntryPoints() == null) {
            return null;
        }
        for (EntryPoint ep : event.getConferenceData().getEntryPoints()) {
            if (ep != null && "video".equalsIgnoreCase(ep.getEntryPointType()) && StringUtils.hasText(ep.getUri())) {
                return ep.getUri();
            }
        }
        return null;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
