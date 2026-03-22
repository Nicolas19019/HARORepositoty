package com.Aplication.HARO.Service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.EntryPoint;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GoogleCalendarService {
    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String AUTH_MODE_SERVICE_ACCOUNT = "service_account";
    private static final String AUTH_MODE_OAUTH_REFRESH_TOKEN = "oauth_refresh_token";
    private static final String AUTH_MODE_AUTO = "auto";

    private final String authMode;
    private final boolean fallbackToTemplateLink;
    private final String defaultCalendarId;
    private final String applicationName;
    private final String defaultTimezone;
    private final int httpConnectTimeoutMs;
    private final int httpReadTimeoutMs;

    private final String oauthClientId;
    private final String oauthClientSecret;
    private final String oauthRefreshToken;

    private volatile Calendar calendarClient;

    public GoogleCalendarService(
            @Value("${google.calendar.auth.mode:auto}") String authMode,
            @Value("${google.calendar.fallback.to-template-link:true}") boolean fallbackToTemplateLink,
            @Value("${google.calendar.default-id:primary}") String defaultCalendarId,
            @Value("${google.calendar.application-name:HARO}") String applicationName,
            @Value("${google.calendar.default-timezone:America/Bogota}") String defaultTimezone,
            @Value("${google.calendar.http.connect-timeout-ms:5000}") int httpConnectTimeoutMs,
            @Value("${google.calendar.http.read-timeout-ms:15000}") int httpReadTimeoutMs,
            @Value("${google.calendar.oauth.client-id:}") String oauthClientId,
            @Value("${google.calendar.oauth.client-secret:}") String oauthClientSecret,
            @Value("${google.calendar.oauth.refresh-token:}") String oauthRefreshToken
    ) {
        this.authMode = authMode;
        this.fallbackToTemplateLink = fallbackToTemplateLink;
        this.defaultCalendarId = defaultCalendarId;
        this.applicationName = applicationName;
        this.defaultTimezone = defaultTimezone;
        this.httpConnectTimeoutMs = Math.max(1000, httpConnectTimeoutMs);
        this.httpReadTimeoutMs = Math.max(1000, httpReadTimeoutMs);
        this.oauthClientId = trim(oauthClientId);
        this.oauthClientSecret = trim(oauthClientSecret);
        this.oauthRefreshToken = trim(oauthRefreshToken);
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
        String mode = resolveAuthMode();
        OffsetDateTime inicio = parseFecha("inicio", inicioIso8601);
        OffsetDateTime fin = parseFecha("fin", finIso8601);
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("'fin' debe ser posterior a 'inicio'.");
        }

        String tz = StringUtils.hasText(zonaHoraria) ? zonaHoraria.trim() : defaultTimezone;
        String calId = StringUtils.hasText(calendarId) ? calendarId.trim() : defaultCalendarId;
        if (!StringUtils.hasText(calId) || "primary".equalsIgnoreCase(calId.trim())) {
            throw new IllegalStateException(
                    "Google Calendar calendarId invalido para service accounts. " +
                    "Configura GOOGLE_CALENDAR_ID con el ID/email del calendario compartido (no uses 'primary')."
            );
        }
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
            // Restriccion real del API: con service accounts sin Domain-Wide Delegation no se pueden enviar invitaciones.
            if (AUTH_MODE_SERVICE_ACCOUNT.equals(mode)) {
                throw new IllegalStateException(
                        "No se pueden invitar asistentes usando service_account sin Domain-Wide Delegation. " +
                        "Cambia GOOGLE_CALENDAR_AUTH_MODE a oauth_refresh_token (usuario) o configura delegacion en Google Workspace."
                );
            }
            event.setAttendees(attendees);
        }

        if (crearMeet) {
            // En nuestra experiencia con calendarios compartidos a service accounts (sin delegacion) esto falla.
            // Mejor dar un error claro para no ocultar el problema.
            if (AUTH_MODE_SERVICE_ACCOUNT.equals(mode)) {
                throw new IllegalStateException(
                        "No se pudo crear Google Meet en modo service_account. " +
                        "Usa oauth_refresh_token (usuario) o delegacion de dominio (Google Workspace) para crear Meet automaticamente."
                );
            }
            ConferenceData conf = new ConferenceData().setCreateRequest(
                    new CreateConferenceRequest()
                            .setRequestId(UUID.randomUUID().toString())
                            .setConferenceSolutionKey(new ConferenceSolutionKey().setType("hangoutsMeet"))
            );
            event.setConferenceData(conf);
        }

        try {
            Calendar client = buildCalendarClient();
            Event created = executeInsert(client, calId, event, crearMeet);

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
            return fallbackOrThrow(
                    calId,
                    summary,
                    desc,
                    inicio,
                    fin,
                    tz,
                    attendees,
                    crearMeet,
                    buildGoogleErrorMessage(e),
                    e
            );
        } catch (Exception e) {
            return fallbackOrThrow(
                    calId,
                    summary,
                    desc,
                    inicio,
                    fin,
                    tz,
                    attendees,
                    crearMeet,
                    e.getMessage(),
                    e
            );
        }
    }

    private Event executeInsert(Calendar client, String calId, Event event, boolean crearMeet) throws Exception {
        try {
            Calendar.Events.Insert insertRequest = client.events()
                    .insert(calId, event)
                    .setSendUpdates("all");
            if (crearMeet) {
                insertRequest.setConferenceDataVersion(1);
            }
            return insertRequest.execute();
        } catch (GoogleJsonResponseException e) {
            // Si falla la conferencia, reintentamos sin Meet para no bloquear el agendamiento.
            String detailMessage = e.getDetails() != null ? e.getDetails().getMessage() : "";
            boolean conferenceInvalid = e.getStatusCode() == 400 &&
                    (detailMessage != null && detailMessage.toLowerCase().contains("conference"));
            if (crearMeet && conferenceInvalid) {
                log.warn("No se pudo crear Meet automaticamente (calendarId={}). Se crea el evento sin Meet. Detalle: {}",
                        calId, detailMessage);
                Event clone = event.clone();
                clone.setConferenceData(null);
                Calendar.Events.Insert retry = client.events()
                        .insert(calId, clone)
                        .setSendUpdates("all");
                return retry.execute();
            }
            throw e;
        }
    }

    private ReunionCreada fallbackOrThrow(String calId,
                                          String summary,
                                          String desc,
                                          OffsetDateTime inicio,
                                          OffsetDateTime fin,
                                          String tz,
                                          List<EventAttendee> attendees,
                                          boolean crearMeet,
                                          String errorMessage,
                                          Exception cause) {
        String message = "No se pudo crear la reunion en Google Calendar: " + safe(errorMessage);
        log.warn("{} (calendarId={}, authMode={})", message, calId, authMode);
        if (!fallbackToTemplateLink) {
            throw new IllegalStateException(message, cause);
        }

        String templateLink = buildTemplateLink(summary, desc, inicio, fin, tz, attendees, crearMeet);
        return new ReunionCreada(
                null,
                calId,
                "PENDIENTE_MANUAL",
                templateLink,
                null,
                inicio.toInstant(),
                fin.toInstant()
        );
    }

    private Calendar buildCalendarClient() {
        try {
            Calendar cached = calendarClient;
            if (cached != null) return cached;

            NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            String mode = resolveAuthMode();
            synchronized (this) {
                cached = calendarClient;
                if (cached != null) return cached;
                Calendar built;
                if (AUTH_MODE_SERVICE_ACCOUNT.equals(mode)) {
                    built = buildServiceAccountClient(transport);
                } else if (AUTH_MODE_OAUTH_REFRESH_TOKEN.equals(mode)) {
                    built = buildOAuthRefreshTokenClient(transport);
                } else {
                    throw new IllegalStateException("google.calendar.auth.mode invalido. Usa: service_account, oauth_refresh_token o auto.");
                }
                calendarClient = built;
                return built;
            }
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
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(List.of(CalendarScopes.CALENDAR));

            HttpCredentialsAdapter adapter = new HttpCredentialsAdapter(credentials);
            return new Calendar.Builder(
                    transport,
                    JSON_FACTORY,
                    request -> {
                        adapter.initialize(request);
                        request.setConnectTimeout(httpConnectTimeoutMs);
                        request.setReadTimeout(httpReadTimeoutMs);
                    }
            ).setApplicationName(applicationName).build();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo inicializar Google Calendar con ADC. " +
                    "Verifica que Cloud Run use la cuenta de servicio correcta y que el calendario este compartido con ella.",
                    e
            );
        }
    }

    private Calendar buildOAuthRefreshTokenClient(NetHttpTransport transport) {
        if (!StringUtils.hasText(oauthClientId) || !StringUtils.hasText(oauthClientSecret) || !StringUtils.hasText(oauthRefreshToken)) {
            throw new IllegalStateException(
                    "Faltan credenciales OAuth para Google Calendar. " +
                            "Configura GOOGLE_CALENDAR_OAUTH_CLIENT_ID, GOOGLE_CALENDAR_OAUTH_CLIENT_SECRET y GOOGLE_CALENDAR_OAUTH_REFRESH_TOKEN."
            );
        }
        try {
            GoogleCredentials credentials = UserCredentials.newBuilder()
                    .setClientId(oauthClientId)
                    .setClientSecret(oauthClientSecret)
                    .setRefreshToken(oauthRefreshToken)
                    .build()
                    .createScoped(List.of(CalendarScopes.CALENDAR));

            HttpCredentialsAdapter adapter = new HttpCredentialsAdapter(credentials);
            return new Calendar.Builder(
                    transport,
                    JSON_FACTORY,
                    request -> {
                        adapter.initialize(request);
                        request.setConnectTimeout(httpConnectTimeoutMs);
                        request.setReadTimeout(httpReadTimeoutMs);
                    }
            ).setApplicationName(applicationName).build();

        } catch (Exception e) {
            throw new IllegalStateException("No se pudo inicializar Google Calendar con OAuth refresh token: " + e.getMessage(), e);
        }
    }
    private String resolveAuthMode() {
        String mode = trim(authMode).toLowerCase();
        if (mode.isBlank() || AUTH_MODE_AUTO.equals(mode)) {
            // En produccion (Cloud Run) el modo seguro es service_account con ADC.
            return AUTH_MODE_SERVICE_ACCOUNT;
        }
        if (AUTH_MODE_SERVICE_ACCOUNT.equals(mode)) {
            return mode;
        }
        if (AUTH_MODE_OAUTH_REFRESH_TOKEN.equals(mode) || "oauth_user".equals(mode)) {
            // "oauth_user" se acepta como alias para migraciones.
            return AUTH_MODE_OAUTH_REFRESH_TOKEN;
        }
        throw new IllegalStateException("google.calendar.auth.mode invalido. Usa: service_account, oauth_refresh_token o auto.");
    }

    private String buildTemplateLink(String summary,
                                     String description,
                                     OffsetDateTime inicio,
                                     OffsetDateTime fin,
                                     String timezone,
                                     List<EventAttendee> attendees,
                                     boolean crearMeet) {
        StringBuilder details = new StringBuilder(safe(description));
        if (crearMeet) {
            if (!details.isEmpty()) {
                details.append("\n\n");
            }
            details.append("Nota: no se pudo crear automaticamente el enlace de Google Meet.");
        }

        String dates = toGoogleDate(inicio) + "/" + toGoogleDate(fin);
        StringBuilder url = new StringBuilder("https://calendar.google.com/calendar/render?action=TEMPLATE");
        url.append("&text=").append(encodeQuery(safe(summary)));
        url.append("&details=").append(encodeQuery(details.toString()));
        url.append("&dates=").append(encodeQuery(dates));
        url.append("&ctz=").append(encodeQuery(safe(timezone)));

        String add = attendees == null ? "" :
                attendees.stream()
                        .map(EventAttendee::getEmail)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .collect(Collectors.joining(","));
        if (!add.isBlank()) {
            url.append("&add=").append(encodeQuery(add));
        }

        return url.toString();
    }

    private String toGoogleDate(OffsetDateTime dt) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        return dt.withOffsetSameInstant(ZoneOffset.UTC).format(fmt);
    }

    private String encodeQuery(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8);
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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
