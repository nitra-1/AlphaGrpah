package com.alphagraph.decision.portfolio;

import com.alphagraph.decision.api.PortfolioHolding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class PortfolioReader {

    private static final String SELECT_COLUMNS = "id, instrument_id, symbol, quantity, avg_buy_price, updated_at";

    private final JdbcTemplate jdbcTemplate;

    PortfolioReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Every current holding for this user, largest position value first is an api-layer concern (needs a live price) - here, most recently updated first. */
    List<PortfolioHolding> findAll(UUID userId) {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM decision.portfolio_holdings WHERE user_id = ? ORDER BY updated_at DESC",
            PortfolioReader::mapRow, userId
        );
    }

    Optional<PortfolioHolding> findByInstrument(UUID userId, UUID instrumentId) {
        List<PortfolioHolding> rows = jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM decision.portfolio_holdings WHERE user_id = ? AND instrument_id = ?",
            PortfolioReader::mapRow, userId, instrumentId
        );
        return rows.stream().findFirst();
    }

    private static PortfolioHolding mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PortfolioHolding(
            (UUID) rs.getObject("id"), (UUID) rs.getObject("instrument_id"), rs.getString("symbol"),
            rs.getBigDecimal("quantity"), rs.getBigDecimal("avg_buy_price"), rs.getTimestamp("updated_at").toInstant()
        );
    }
}
