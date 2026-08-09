package com.alphagraph.reference.securitymaster;

import com.alphagraph.common.etl.Loader;
import com.alphagraph.reference.api.SecurityMasterEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Upserts on symbol - idempotent per docs/002_Engine_Architecture.md §2. */
@Component
public class SecurityMasterLoader implements Loader<SecurityMasterEntry> {

    private final JdbcTemplate jdbcTemplate;

    public SecurityMasterLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void load(SecurityMasterEntry entry) {
        jdbcTemplate.update(
            """
            INSERT INTO reference.security_master (id, symbol, company_name, series, isin, listing_date, face_value)
            VALUES (?, ?, ?, 'EQ', ?, ?, ?)
            ON CONFLICT (symbol) DO UPDATE SET
                company_name = EXCLUDED.company_name, isin = EXCLUDED.isin,
                listing_date = EXCLUDED.listing_date, face_value = EXCLUDED.face_value
            """,
            entry.id(), entry.symbol(), entry.companyName(), entry.isin(), entry.listingDate(), entry.faceValue()
        );
    }
}
