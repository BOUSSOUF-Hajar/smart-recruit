package com.smartrecruit.audit;

import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.shared.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-52 : traçabilité des actions sensibles, sur un vrai PostgreSQL.
 * Chaque test utilise une action unique pour ne lire que ses propres lignes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuditRecorderIT {

    @Autowired
    private AuditRecorder recorder;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    private String action;

    @BeforeEach
    void setUp() {
        action = "TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // Table « métier » factice, pour prouver que l'action et sa trace partagent la même transaction
        jdbc.sql("create table if not exists audit_probe (marker varchar(64))").update();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void writesDateActorActionResourceIpAndResult() {
        UUID userId = insertUser("recruteur-" + action.toLowerCase() + "@acme.ma");
        UUID resumeId = UUID.randomUUID();
        simulateHttpRequest("203.0.113.7", "corr-test-0001");

        UUID id = transaction.execute(status -> recorder.record(
                AuditEvent.success(action).by(userId, "recruteur@acme.ma").on("RESUME", resumeId)
                        .with("applicationId", "app-42")));

        Map<String, Object> row = jdbc.sql("""
                        select occurred_at, actor_id, actor_email, action, resource_type, resource_id, result,
                               host(ip_address) as ip, correlation_id, details ->> 'applicationId' as application_id
                        from audit_log where id = :id""")
                .param("id", id).query().singleRow();

        assertThat(row.get("occurred_at")).isNotNull();
        assertThat(row.get("actor_id")).isEqualTo(userId);
        assertThat(row.get("actor_email")).isEqualTo("recruteur@acme.ma");
        assertThat(row.get("action")).isEqualTo(action);
        assertThat(row.get("resource_type")).isEqualTo("RESUME");
        assertThat(row.get("resource_id")).isEqualTo(resumeId);
        assertThat(row.get("result")).isEqualTo("SUCCESS");
        assertThat(row.get("ip")).isEqualTo("203.0.113.7");
        assertThat(row.get("correlation_id")).isEqualTo("corr-test-0001");
        assertThat(row.get("application_id")).isEqualTo("app-42");
    }

    @Test
    void failuresAreRecordedToo() {
        transaction.executeWithoutResult(status ->
                recorder.record(AuditEvent.failure(action).by(null, "inconnu@example.com")));

        assertThat(resultsFor(action)).containsExactly("FAILURE");
    }

    @Test
    void recordRequiresTheTransactionOfTheBusinessAction() {
        assertThatThrownBy(() -> recorder.record(AuditEvent.success(action)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void whenAuditWriteFailsTheBusinessActionIsCancelled() {
        String tooLongResourceType = "X".repeat(40);   // colonne limitée à 32 caractères

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.sql("insert into audit_probe (marker) values (:m)").param("m", action).update();
            recorder.record(AuditEvent.success(action).on(tooLongResourceType, UUID.randomUUID()));
        })).isInstanceOf(DataAccessException.class);

        assertThat(probeCount(action)).isZero();
        assertThat(resultsFor(action)).isEmpty();
    }

    @Test
    void whenBusinessActionFailsItsTraceIsCancelledToo() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            recorder.record(AuditEvent.success(action));
            throw new IllegalStateException("échec de l'action métier");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(resultsFor(action)).isEmpty();
    }

    @Test
    void independentRecordSurvivesTheRollbackOfTheAction() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            recorder.recordIndependently(AuditEvent.failure(action).by(null, "attaquant@example.com"));
            throw new IllegalStateException("connexion refusée");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(resultsFor(action)).containsExactly("FAILURE");
    }

    @Test
    void sensitiveDataIsRefused() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                recorder.record(AuditEvent.success(action).with("password", "secret123"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                recorder.record(AuditEvent.success(action).with("request", Map.of("refreshToken", "abc")))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(resultsFor(action)).isEmpty();
    }

    private java.util.List<String> resultsFor(String auditAction) {
        return jdbc.sql("select result from audit_log where action = :a")
                .param("a", auditAction).query(String.class).list();
    }

    private int probeCount(String marker) {
        return jdbc.sql("select count(*) from audit_probe where marker = :m")
                .param("m", marker).query(Integer.class).single();
    }

    private static void simulateHttpRequest(String remoteAddr, String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
    }

    /** Depuis V3, actor_id référence app_user : l'auteur doit exister. */
    private UUID insertUser(String email) {
        return jdbc.sql("""
                        insert into app_user (email, password_hash, first_name, last_name, role, created_at, updated_at)
                        values (:email, '{noop}inutilise', 'Test', 'Audit', 'RECRUITER', now(), now())
                        returning id""")
                .param("email", email)
                .query(UUID.class)
                .single();
    }
}
