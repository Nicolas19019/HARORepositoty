package com.Aplication.HARO.Service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
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
    private static final String AUTH_MODE_AUTO = "auto";

    private final String authMode;
    private final boolean fallbackToTemplateLink;
    private final String defaultCalendarId;
    private final String applicationName;
    private final String defaultTimezone;
    private final int httpConnectTimeoutMs;
    private final int httpReadTimeoutMs;

    private volatile Calendar calendarClient;

    public GoogleCalendarService(
            @Value("${google.calendar.auth.mode:auto}") String authMode,
            @Value("${google.calendar.fallback.to-template-link:true}") boolean fallbackToTemplateLink,
            @Value("${google.calendar.default-id:primary}") String defaultCalendarId,
            @Value("${google.calendar.application-name:HARO}") String applicationName,
            @Value("${google.calendar.default-timezone:America/Bogota}") String defaultTimezone,
            @Value("${google.calendar.http.connect-timeout-ms:5000}") int httpConnectTimeoutMs,
            @Value("${google.calendar.http.read-timeout-ms:15000}") int httpReadTimeoutMs
    ) {
        this.authMode = authMode;
        this.fallbackToTemplateLink = fallbackToTemplateLink;
        this.defaultCalendarId = defaultCalendarId;
        this.applicationName = applicationName;
        this.defaultTimezone = defaultTimezone;
        this.httpConnectTimeoutMs = Math.max(1000, httpConnectTimeoutMs);
        this.httpReadTimeoutMs = Math.max(1000, httpReadTimeoutMs);
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
            if (!AUTH_MODE_SERVICE_ACCOUNT.equals(mode)) {
                throw new IllegalStateException(
                        "OAuth de usuario (oauth_user) no esta soportado en Cloud Run. " +
                        "Configura GOOGLE_CALENDAR_AUTH_MODE=service_account y comparte el calendario con la cuenta de servicio."
                );
            }
            synchronized (this) {
                cached = calendarClient;
                if (cached != null) return cached;
                Calendar built = buildServiceAccountClient(transport);
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
    private String resolveAuthMode() {
        String mode = trim(authMode).toLowerCase();
        if (mode.isBlank() || AUTH_MODE_AUTO.equals(mode)) {
            // En produccion (Cloud Run) el modo seguro es service_account con ADC.
            return AUTH_MODE_SERVICE_ACCOUNT;
        }
        if (AUTH_MODE_SERVICE_ACCOUNT.equals(mode)) {
            return mode;
        }
        if ("oauth_user".equals(mode)) {
            throw new IllegalStateException(
                    "google.calendar.auth.mode=oauth_user no es soportado en produccion (Cloud Run). " +
                    "Usa service_account (ADC) y comparte el calendario con la cuenta de servicio."
            );
        }
        throw new IllegalStateException("google.calendar.auth.mode invalido. Usa: service_account o auto.");
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
