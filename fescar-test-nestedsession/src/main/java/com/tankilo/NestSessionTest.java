package com.tankilo;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ComponentScan
@ImportResource("classpath:fescar-client-context.xml")
public class NestSessionTest {

    private static JdbcTemplate jdbcTemplate1 = null;
    private static JdbcTemplate jdbcTemplate2 = null;


    public static void main(String[] args) throws InterruptedException {
        ApplicationContext context = new AnnotationConfigApplicationContext(
            NestSessionTest.class);

        jdbcTemplate1 = (JdbcTemplate) context.getBean("jdbcTemplate1");
        jdbcTemplate2 = (JdbcTemplate) context.getBean("jdbcTemplate2");
        //清空账户
        jdbcTemplate1.update("truncate user_money_a");
        jdbcTemplate2.update("truncate user_money_b");
        //每个账户初始值为10000元
        jdbcTemplate1.update("insert into user_money_a(money) values(10000)");
        jdbcTemplate2.update("insert into user_money_b(money) values(10000)");

        Service1 service1 = context.getBean(Service1.class);
        service1.transfer(1);
    }

}
