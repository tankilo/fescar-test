package com.tankilo;

import com.alibaba.fescar.spring.annotation.GlobalTransactional;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class Service1 {
    @Autowired
    private JdbcTemplate jdbcTemplate1;
    @Autowired
    private JdbcTemplate jdbcTemplate2;

    @Autowired
    private Service2 service2;

    @GlobalTransactional(timeoutMills = 30000, name = "simple-demo")
    public void transfer(int money) throws InterruptedException {
        //A账户从db1减钱
        jdbcTemplate1.update("update user_money_a set money = money - ? where 1=1", money);
        //B账户从db2加钱
        jdbcTemplate2.update("update user_money_b  set money = money + ? where 1=1", money);
        service2.doSomething();
        throw new RuntimeException("Balance is not enough.");
    }
}
