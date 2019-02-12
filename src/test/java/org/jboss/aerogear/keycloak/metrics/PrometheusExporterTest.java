package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.Counter;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@SuppressWarnings("unchecked")
public class PrometheusExporterTest {

    private static final String DEFAULT_REALM = "myrealm";
    public static final String CLIENT_ID = "clientId";

    @Before
    public void before() {
        for (Counter counter : PrometheusExporter.instance().counters.values()) {
            counter.clear();
        }
        PrometheusExporter.instance().totalLogins.clear();
        PrometheusExporter.instance().totalFailedLoginAttempts.clear();
        PrometheusExporter.instance().totalRegistrations.clear();
    }

    @Test
    public void shouldRegisterCountersForAllKeycloakEvents() {
        int userEvents = EventType.values().length;
        int adminEvents = OperationType.values().length;

        MatcherAssert.assertThat(
            "All events registered",
            userEvents + adminEvents - 3,                             // -3 comes from the events that
            is(PrometheusExporter.instance().counters.size()));       // have their own counters outside the counter map

    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsDefined() throws IOException {
        final Event login1 = createEvent(EventType.LOGIN, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", false, 1, tuple("provider", "THE_ID_PROVIDER"));

        final Event login2 = createEvent(EventType.LOGIN, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", false, 2, tuple("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldCorrectlyCountLoginWhenIdentityProviderIsNotDefined() throws IOException {
        final Event login1 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", false, 1, tuple("provider", "keycloak"));

        final Event login2 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", false, 2, tuple("provider", "keycloak"));
    }

    @Test
    public void shouldCorrectlyCountLoginsFromDifferentProviders() throws IOException {
        // with id provider defined
        final Event login1 = createEvent(EventType.LOGIN, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1);
        assertMetric("keycloak_logins", false, 1, tuple("provider", "THE_ID_PROVIDER"));

        // without id provider defined
        final Event login2 = createEvent(EventType.LOGIN);
        PrometheusExporter.instance().recordLogin(login2);
        assertMetric("keycloak_logins", false, 1, tuple("provider", "keycloak"));
        assertMetric("keycloak_logins", false, 1, tuple("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldRecordLoginsPerRealm() throws IOException {
        // realm 1
        final Event login1 = createEvent(EventType.LOGIN, DEFAULT_REALM, CLIENT_ID, null, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1);

        // realm 2
        final Event login2 = createEvent(EventType.LOGIN, "OTHER_REALM", CLIENT_ID, null, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login2);

        assertMetric("keycloak_logins", false, 1, DEFAULT_REALM, CLIENT_ID, tuple("provider", "THE_ID_PROVIDER"));
        assertMetric("keycloak_logins", false, 1, "OTHER_REALM", CLIENT_ID, tuple("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldRecordLoginsPerClient() throws IOException {
        // realm 1
        final Event login1 = createEvent(EventType.LOGIN, DEFAULT_REALM, CLIENT_ID, null, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login1);

        // realm 2
        final Event login2 = createEvent(EventType.LOGIN, DEFAULT_REALM, "OTHER_CLIENT", null, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLogin(login2);

        assertMetric("keycloak_logins", false, 1, DEFAULT_REALM, CLIENT_ID, tuple("provider", "THE_ID_PROVIDER"));
        assertMetric("keycloak_logins", false, 1, DEFAULT_REALM, "OTHER_CLIENT", tuple("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldCorrectlyCountLoginError() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.LOGIN_ERROR, DEFAULT_REALM, CLIENT_ID, "user_not_found", tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordLoginError(event1);
        assertMetric("keycloak_failed_login_attempts", false, 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"));

        // without id provider defined
        final Event event2 = createEvent(EventType.LOGIN_ERROR, DEFAULT_REALM, CLIENT_ID, "user_not_found");
        PrometheusExporter.instance().recordLoginError(event2);
        assertMetric("keycloak_failed_login_attempts", false, 1, tuple("provider", "keycloak"), tuple("error", "user_not_found"));
        assertMetric("keycloak_failed_login_attempts", false, 1, tuple("provider", "THE_ID_PROVIDER"), tuple("error", "user_not_found"));
    }

    @Test
    public void shouldCorrectlyCountRegister() throws IOException {
        // with id provider defined
        final Event event1 = createEvent(EventType.REGISTER, tuple("identity_provider", "THE_ID_PROVIDER"));
        PrometheusExporter.instance().recordRegistration(event1);
        assertMetric("keycloak_registrations", false, 1, tuple("provider", "THE_ID_PROVIDER"));

        // without id provider defined
        final Event event2 = createEvent(EventType.REGISTER);
        PrometheusExporter.instance().recordRegistration(event2);
        assertMetric("keycloak_registrations", false, 1, tuple("provider", "keycloak"));
        assertMetric("keycloak_registrations", false, 1, tuple("provider", "THE_ID_PROVIDER"));
    }

    @Test
    public void shouldCorrectlyRecordGenericEvents() throws IOException {
        final Event event1 = createEvent(EventType.UPDATE_EMAIL);
        PrometheusExporter.instance().recordGenericEvent(event1);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", false, 1);
        PrometheusExporter.instance().recordGenericEvent(event1);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", false, 2);


        final Event event2 = createEvent(EventType.REVOKE_GRANT);
        PrometheusExporter.instance().recordGenericEvent(event2);
        assertMetric("keycloak_user_event_REVOKE_GRANT", false, 1);
        assertMetric("keycloak_user_event_UPDATE_EMAIL", false, 2);
    }

    @Test
    public void shouldCorrectlyRecordGenericAdminEvents() throws IOException {
        final AdminEvent event1 = new AdminEvent();
        event1.setOperationType(OperationType.ACTION);
        event1.setResourceType(ResourceType.AUTHORIZATION_SCOPE);
        event1.setRealmId(DEFAULT_REALM);
        PrometheusExporter.instance().recordGenericAdminEvent(event1);
        assertMetric("keycloak_admin_event_ACTION", true, 1, tuple("resource", "AUTHORIZATION_SCOPE"));
        PrometheusExporter.instance().recordGenericAdminEvent(event1);
        assertMetric("keycloak_admin_event_ACTION", true, 2, tuple("resource", "AUTHORIZATION_SCOPE"));


        final AdminEvent event2 = new AdminEvent();
        event2.setOperationType(OperationType.UPDATE);
        event2.setResourceType(ResourceType.CLIENT);
        event2.setRealmId(DEFAULT_REALM);
        PrometheusExporter.instance().recordGenericAdminEvent(event2);
        assertMetric("keycloak_admin_event_UPDATE", true, 1, tuple("resource", "CLIENT"));
        assertMetric("keycloak_admin_event_ACTION", true, 2, tuple("resource", "AUTHORIZATION_SCOPE"));
    }

    private void assertMetric(String metricName, boolean adminEventMetric, double metricValue, String realm, String client, Tuple<String, String>... labels) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            PrometheusExporter.instance().export(stream);
            String result = new String(stream.toByteArray());

            final StringBuilder builder = new StringBuilder();

            builder.append(metricName).append("{");
            builder.append("realm").append("=\"").append(realm).append("\",");

            if (!adminEventMetric) {
                builder.append("client").append("=\"").append(client).append("\",");
            }

            for (Tuple<String, String> label : labels) {
                builder.append(label.left).append("=\"").append(label.right).append("\",");
            }

            builder.append("} ").append(metricValue);

            MatcherAssert.assertThat(result, containsString(builder.toString()));
        }
    }

    private void assertMetric(String metricName, boolean adminEventMetric, double metricValue, Tuple<String, String>... labels) throws IOException {
        this.assertMetric(metricName, adminEventMetric, metricValue, DEFAULT_REALM, CLIENT_ID, labels);
    }

    private Event createEvent(EventType type, String realm, String client, String error, Tuple<String, String>... tuples) {
        final Event event = new Event();
        event.setType(type);
        event.setRealmId(realm);
        event.setClientId(client);
        if (tuples != null) {
            event.setDetails(new HashMap<>());
            for (Tuple<String, String> tuple : tuples) {
                event.getDetails().put(tuple.left, tuple.right);
            }
        } else {
            event.setDetails(Collections.emptyMap());
        }

        if (error != null) {
            event.setError(error);
        }
        return event;
    }

    private Event createEvent(EventType type, Tuple<String, String>... tuples) {
        return this.createEvent(type, DEFAULT_REALM, CLIENT_ID, null, tuples);
    }

    private Event createEvent(EventType type) {
        return createEvent(type, DEFAULT_REALM, CLIENT_ID, (String) null);
    }

    private static <L, R> Tuple<L, R> tuple(L left, R right) {
        return new Tuple<>(left, right);
    }

    private static final class Tuple<L, R> {
        final L left;
        final R right;

        private Tuple(L left, R right) {
            this.left = left;
            this.right = right;
        }
    }
}
