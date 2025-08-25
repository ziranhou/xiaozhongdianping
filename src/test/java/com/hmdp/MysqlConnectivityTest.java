package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MysqlConnectivityTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void selectOneShouldWork() {
        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
        assertThat(one).isEqualTo(1);
    }

    @Test
    void realQueryCount() {
        // 换成你真实的表名
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM tb_user", Integer.class);
        assertThat(cnt).isNotNull();
    }
    @Test
    void createInsertSelect_shouldWork() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_probe(id INT PRIMARY KEY, v VARCHAR(20))");
        jdbc.update("REPLACE INTO t_probe(id, v) VALUES(1, 'ok')");
        String v = jdbc.queryForObject("SELECT v FROM t_probe WHERE id=1", String.class);
        assertThat(v).isEqualTo("ok");
        // 可选：清理
        jdbc.execute("DROP TABLE t_probe");
    }

}
