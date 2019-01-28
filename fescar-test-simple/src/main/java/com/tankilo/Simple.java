package com.tankilo;

import com.alibaba.fescar.spring.annotation.GlobalTransactional;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Random;

public class Simple {

    private static JdbcTemplate jdbcTemplate1 = null;
    private static JdbcTemplate jdbcTemplate2 = null;

    @GlobalTransactional(timeoutMills = 30000, name = "simple-demo")
    public void transfer(int money) throws InterruptedException {
        //A账户从db1减钱
        jdbcTemplate1.update("update user_money_a set money = money - ?", money);
        if (new Random().nextInt(100) < 2) {
            throw new RuntimeException("error");
        }

        //B账户从db2加钱
        jdbcTemplate2.update("update user_money_b  set money = money + ?", money);
        if (new Random().nextInt(100) < 2) {
            throw new RuntimeException("error");
        }

        //余额不足时抛异常，GTS会回滚前两次操作
        int money_a = getMoney(jdbcTemplate1, "user_money_a");
        if (money_a < 0) {
            Thread.sleep(20);
            throw new RuntimeException("Balance is not enough.");
        }
    }

    public static void main(String[] args) throws SQLException {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("fescar-client-context.xml");
        Simple simple = (Simple) context.getBean("clientTest");

        jdbcTemplate1 = (JdbcTemplate) context.getBean("jdbcTemplate1");
        jdbcTemplate2 = (JdbcTemplate) context.getBean("jdbcTemplate2");
        //清空账户
        jdbcTemplate1.update("truncate user_money_a");
        jdbcTemplate2.update("truncate user_money_b");
        //每个账户初始值为10000元
        jdbcTemplate1.update("insert into user_money_a(money) values(10000)");
        jdbcTemplate2.update("insert into user_money_b(money) values(10000)");

        for (int i = 0; i < 100; ++i) {
            System.out.println("transfer" + i);
            try {
                simple.transfer(2);
            } catch (Exception e) {
                System.out.println("Tranfer failed，transaction is rollbacked.");
                e.printStackTrace();
            }
            int money1 = getMoney(jdbcTemplate1, "user_money_a");
            int money2 = getMoney(jdbcTemplate2, "user_money_b");
            System.out.println("A account：" + money1 + "    B account：" + money2 + "    total：" + (money1 + money2));
        }
        System.exit(0);
    }

    private static int getMoney(JdbcTemplate jdbcTemplate, String tableName) {
        String sql = "select money from " + tableName;
        List<Integer> moneyList = jdbcTemplate.queryForList(sql, java.lang.Integer.class);
        if (moneyList.size() > 0) {
            return moneyList.get(0);
        }
        return 0;
    }
}
