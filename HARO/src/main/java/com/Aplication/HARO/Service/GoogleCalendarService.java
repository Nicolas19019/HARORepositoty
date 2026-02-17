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
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.EntryPoint;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GoogleCalendarService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String AUTH_MODE_SERVICE_ACCOUNT = "service_account";
    private static final String AUTH_MODE_OAUTH_USER = "oauth_user";

    private final String authMode;
    private final String credentialsPath;
    private final String oauthClientSecretsPath;
    private final String oauthTokensDir;
    private final String oauthUserId;
    private final int oauthLocalReceiverPort;
    private final String defaultCalendarId;
    private final String applicationName;
    private final String defaultTimezone;

    public GoogleCalendarService(
            @Value("${google.calendar.auth-mode:service_account}") String authMode,
            @Value("${google.calendar.credentials.path:}") String credentialsPath,
            @Value("${google.calendar.oauth.client-secrets.path:}") String oauthClientSecretsPath,
            @Value("${google.calendar.oauth.tokens.dir:.tokens/google-calendar}") String oauthTokensDir,
            @Value("${google.calendar.oauth.user-id:default}") String oauthUserId,
            @Value("${google.calendar.oauth.local-receiver-port:8888}") int oauthLocalReceiverPort,
            @Value("${google.calendar.default-id:primary}") String defaultCalendarId,
            @Value("${google.calendar.application-name:HARO}") String applicationName,
            @Value("${google.calendar.default-timezone:America/Bogota}") String defaultTimezone
    ) {
        this.authMode = authMode;
        this.credentialsPath = credentialsPath;
        this.oauthClientSecretsPath = oauthClientSecretsPath;
        this.oauthTokensDir = oauthTokensDir;
        this.oauthUserId = oauthUserId;
        this.oauthLocalReceiverPort = oauthLocalReceiverPort;
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

        ConferenceData conf = new ConferenceData().setCreateRequest(
                new CreateConferenceRequest()
                        .setRequestId(UUID.randomUUID().toString())
                        .setConferenceSolutionKey(new ConferenceSolutionKey().setType("hangoutsMeet"))
        );
        event.setConferenceData(conf);

        try {
            Calendar client = buildCalendarClient();
            Event created = client.events()
                    .insert(calId, event)
                    .setConferenceDataVersion(1)
                    .setSendUpdates("all")
                    .execute();

            String meetLink = extractMeetLink(created);
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
            if (isForbiddenForServiceAccounts(e)) {
                throw new IllegalStateException(
                        "Google rechazo invitar asistentes con cuenta de servicio. " +
                                "Usa GOOGLE_CALENDAR_AUTH_MODE=oauth_user para trabajar con tu cuenta Gmail.",
                        e
                );
            }
            throw new IllegalStateException("No se pudo crear la reunion en Google Calendar: " + buildGoogleErrorMessage(e), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear la reunion en Google Calendar: " + e.getMessage(), e);
        }
    }

    private Calendar buildCalendarClient() {
        String mode = normalizeAuthMode(authMode);
        try {
            NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            if (AUTH_MODE_OAUTH_USER.equals(mode)) {
                return buildOAuthUserClient(transport);
            }
            return buildServiceAccountClient(transport);
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
        if (!StringUtils.hasText(credentialsPath)) {
            throw new IllegalStateException("Falta configurar GOOGLE_CALENDAR_CREDENTIALS_PATH.");
        }

        try (InputStream in = new FileInputStream(credentialsPath.trim())) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in);
            if (credentials.createScopedRequired()) {
                credentials = credentials.createScoped(List.of(CalendarScopes.CALENDAR));
            }
            credentials.refreshIfExpired();

            return new Calendar.Builder(transport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                    .setApplicationName(applicationName)
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Credenciales de Google invalidas o inaccesibles. Verifica el JSON y la ruta configurada.",
                    e
            );
        }
    }

    private Calendar buildOAuthUserClient(NetHttpTransport transport) {
        if (!StringUtils.hasText(oauthClientSecretsPath)) {
            throw new IllegalStateException(
                    "Falta GOOGLE_CALENDAR_OAUTH_CLIENT_SECRETS_PATH para modo oauth_user."
            );
        }

        String tokensDir = StringUtils.hasText(oauthTokensDir)
                ? oauthTokensDir.trim()
                : ".tokens/google-calendar";
        File tokensDirectory = new File(tokensDir);
        try (InputStream in = new FileInputStream(oauthClientSecretsPath.trim());
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
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

    private String normalizeAuthMode(String rawMode) {
        if (!StringUtils.hasText(rawMode)) {
            return AUTH_MODE_SERVICE_ACCOUNT;
        }
        String mode = rawMode.trim().toLowerCase();
        return switch (mode) {
            case "service_account", "service", "serviceaccount" -> AUTH_MODE_SERVICE_ACCOUNT;
            case "oauth_user", "oauth", "oauth2_user", "user_oauth" -> AUTH_MODE_OAUTH_USER;
            default -> throw new IllegalStateException(
                    "google.calendar.auth-mode invalido: '" + rawMode +
                            "'. Usa 'service_account' u 'oauth_user'."
            );
        };
    }

    private boolean isForbiddenForServiceAccounts(GoogleJsonResponseException e) {
        if (e.getDetails() == null || e.getDetails().getErrors() == null) {
            return false;
        }
        for (var error : e.getDetails().getErrors()) {
            if ("forbiddenForServiceAccounts".equalsIgnoreCase(error.getReason())) {
                return true;
            }
        }
        return false;
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
}
