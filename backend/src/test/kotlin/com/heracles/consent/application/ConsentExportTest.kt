package com.heracles.consent.application

import com.heracles.consent.api.ConsentType
import com.heracles.consent.domain.Client
import com.heracles.consent.domain.ConsentAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ConsentExportTest {
    @Test
    fun `the export lists only the history of the person asked, in order, with identifiers and no text`() {
        val events = FakeConsentEvents()
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val version = UUID.randomUUID()
        events.append(a, ConsentType.TERMS_OF_USE, ConsentAction.GRANT, version, Client.WEB)
        events.append(b, ConsentType.TERMS_OF_USE, ConsentAction.GRANT, version, Client.MOBILE)
        events.append(a, ConsentType.COMMUNITY_CONSENT, ConsentAction.REVOKE, version, Client.WEB)

        val export = ConsentExport(events)
        assertThat(export.section).isEqualTo("consents")
        @Suppress("UNCHECKED_CAST")
        val lines = export.export(a) as List<Map<String, Any>>
        assertThat(lines.map { it["type"] }).containsExactly("TERMS_OF_USE", "COMMUNITY_CONSENT")
        assertThat(lines.first()).containsEntry("policyVersionId", version.toString()).containsEntry("client", "WEB")
    }
}
