package com.alphagraph.reference.securitymaster;

import com.alphagraph.reference.api.SecurityMasterEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Powers the "Add Instrument" autocomplete - symbol-prefix or company-name substring match. */
@Component
public class SecurityMasterReader {

    private static final int MAX_RESULTS = 20;

    private final JdbcTemplate jdbcTemplate;

    public SecurityMasterReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SecurityMasterEntry> search(String query) {
        String likeQuery = "%" + query.trim() + "%";
        String prefixQuery = query.trim() + "%";
        return jdbcTemplate.query(
            """
            SELECT id, symbol, company_name, isin, listing_date, face_value
            FROM reference.security_master
            WHERE symbol ILIKE ? OR company_name ILIKE ?
            ORDER BY (symbol ILIKE ?) DESC, symbol ASC
            LIMIT ?
            """,
            ROW_MAPPER, likeQuery, likeQuery, prefixQuery, MAX_RESULTS
        );
    }

    /** Server-side re-verification that a symbol is real, independent of client-supplied name/ISIN - the whole point of the master list is that company name and ISIN are never trusted from a form field. */
    public Optional<SecurityMasterEntry> findBySymbol(String symbol) {
        return jdbcTemplate.query(
            "SELECT id, symbol, company_name, isin, listing_date, face_value FROM reference.security_master WHERE symbol = ?",
            ROW_MAPPER, symbol
        ).stream().findFirst();
    }

    private static final org.springframework.jdbc.core.RowMapper<SecurityMasterEntry> ROW_MAPPER = SecurityMasterReader::mapRow;

    private static SecurityMasterEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Date listingDate = rs.getDate("listing_date");
        BigDecimal faceValue = rs.getBigDecimal("face_value");
        return new SecurityMasterEntry(
            (UUID) rs.getObject("id"), rs.getString("symbol"), rs.getString("company_name"), rs.getString("isin"),
            listingDate == null ? null : listingDate.toLocalDate(), faceValue
        );
    }
}
