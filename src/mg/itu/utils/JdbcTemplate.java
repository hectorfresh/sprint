package mg.itu.utils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcTemplate {

    private final DataSource dataSource;

    public JdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int update(String sql, Object... params) throws SQLException {
        try (Connection cn = dataSource.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    public <T> T queryForObject(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        List<T> list = query(sql, mapper, params);
        return list.isEmpty() ? null : list.get(0);
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        List<T> result = new ArrayList<>();
        try (Connection cn = dataSource.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                int row = 0;
                while (rs.next()) {
                    result.add(mapper.map(rs, row++));
                }
            }
        }
        return result;
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs, int rowNum) throws SQLException;
    }
}